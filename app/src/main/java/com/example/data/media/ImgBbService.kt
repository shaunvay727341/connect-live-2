package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

data class ImgBbUploadResult(
    val url: String,
    val thumbUrl: String,
    val usedLocalFallback: Boolean = false
)

class ImgBbService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    fun hasAnyCloudUploadConfigured(overrideApiKey: String = ""): Boolean {
        return deriveBackendBaseUrl(BuildConfig.TURN_BACKEND_URL) != null ||
            isKeyConfigured(BuildConfig.IMGBB_API_KEY.trim()) ||
            isKeyConfigured(overrideApiKey.trim())
    }

    suspend fun uploadImage(
        context: Context,
        uri: Uri,
        namePrefix: String = "connect_live",
        overrideApiKey: String = "",
        allowLocalAvatarFallback: Boolean = false
    ): Result<ImgBbUploadResult> = withContext(Dispatchers.IO) {
        try {
            val appCtx = context.applicationContext
            val base64Image = encodeUriToJpegBase64(appCtx, uri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Unable to read or decode selected image.")
                )

            val backendBase = deriveBackendBaseUrl(BuildConfig.TURN_BACKEND_URL)
            if (backendBase != null) {
                val proxyRes = uploadViaBackendProxy(backendBase, base64Image, namePrefix)
                if (proxyRes.isSuccess) return@withContext proxyRes
            }

            val effectiveKey = BuildConfig.IMGBB_API_KEY.trim().takeIf { isKeyConfigured(it) }
                ?: overrideApiKey.trim().takeIf { isKeyConfigured(it) }

            if (effectiveKey != null) {
                val directRes = uploadDirectToImgBb(effectiveKey, base64Image, namePrefix)
                if (directRes.isSuccess) return@withContext directRes
                if (!allowLocalAvatarFallback) return@withContext directRes
            }

            if (allowLocalAvatarFallback) {
                val compactDataUri = encodeCompactAvatarDataUri(appCtx, uri)
                if (compactDataUri != null) {
                    return@withContext Result.success(
                        ImgBbUploadResult(
                            url = compactDataUri,
                            thumbUrl = compactDataUri,
                            usedLocalFallback = true
                        )
                    )
                }
            }

            Result.failure(
                IllegalStateException(
                    "ImgBB API key not set yet. Add your key in Profile -> Media & ImgBB Settings (or AI Studio Secrets)."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Image upload failed.", e))
        }
    }

    private fun uploadDirectToImgBb(
        apiKey: String,
        base64Image: String,
        namePrefix: String
    ): Result<ImgBbUploadResult> {
        val formBody = FormBody.Builder()
            .add("key", apiKey)
            .add("image", base64Image)
            .add("name", "${namePrefix}_${System.currentTimeMillis()}")
            .build()

        val request = Request.Builder()
            .url("https://api.imgbb.com/1/upload")
            .post(formBody)
            .build()

        return client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful || json?.optBoolean("success", false) != true) {
                val errMsg = json?.optJSONObject("error")?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: "ImgBB upload failed (HTTP ${response.code})."
                return Result.failure(IllegalStateException(errMsg))
            }
            val data = json.getJSONObject("data")
            val url = data.optString("display_url").ifBlank { data.optString("url") }
            val thumb = data.optJSONObject("thumb")?.optString("url")
                ?.takeIf { it.isNotBlank() } ?: url
            if (url.isBlank()) {
                return Result.failure(IllegalStateException("ImgBB returned empty image URL."))
            }
            Result.success(ImgBbUploadResult(url = url, thumbUrl = thumb, usedLocalFallback = false))
        }
    }

    private suspend fun uploadViaBackendProxy(
        backendBase: String,
        imageBase64: String,
        namePrefix: String
    ): Result<ImgBbUploadResult> {
        return try {
            val currentUser = Firebase.auth.currentUser
                ?: return Result.failure(IllegalStateException("Sign in required before uploading images."))
            val idToken = currentUser.getIdToken(false).await().token
                ?: return Result.failure(IllegalStateException("Missing Firebase ID token."))

            val payload = JSONObject()
                .put("imageBase64", imageBase64)
                .put("name", "${namePrefix}_${System.currentTimeMillis()}")
                .toString()

            val request = Request.Builder()
                .url("$backendBase/api/upload-image")
                .header("Authorization", "Bearer $idToken")
                .header("Accept", "application/json")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                if (!response.isSuccessful) {
                    val err = json?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: "Upload proxy HTTP ${response.code}"
                    return Result.failure(IllegalStateException(err))
                }
                val url = json?.optString("url").orEmpty()
                val thumb = json?.optString("thumbUrl").orEmpty().ifBlank { url }
                if (url.isBlank()) {
                    return Result.failure(IllegalStateException("Proxy returned empty image URL."))
                }
                Result.success(ImgBbUploadResult(url = url, thumbUrl = thumb, usedLocalFallback = false))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deriveBackendBaseUrl(turnUrl: String): String? {
        val trimmed = turnUrl.trim()
        if (trimmed.isBlank() || trimmed == "UNSET" || trimmed.contains("example.com")) return null
        val cleaned = trimmed.removeSuffix("/")
        return when {
            cleaned.endsWith("/api/turn-credentials") -> cleaned.removeSuffix("/api/turn-credentials")
            else -> {
                val uri = runCatching { java.net.URI(cleaned) }.getOrNull() ?: return null
                if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) return null
                val portPart = if (uri.port > 0) ":${uri.port}" else ""
                "${uri.scheme}://${uri.host}$portPart"
            }
        }
    }

    fun isKeyConfigured(key: String): Boolean {
        return key.isNotBlank() && key != "UNSET" && !key.startsWith("MY_") && !key.contains("your_imgbb")
    }

    private fun encodeCompactAvatarDataUri(context: Context, uri: Uri): String? {
        val decoded = decodeOrientedBitmap(context, uri, 256) ?: return null
        var resultUri: String? = null
        val sizes = listOf(88, 72, 56, 44)
        for (edge in sizes) {
            val scaled = scaleDownIfNeeded(decoded, edge)
            val qualities = listOf(60, 44, 30)
            for (q in qualities) {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
                val dataUri = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                if (dataUri.length <= 950) {
                    resultUri = dataUri
                    break
                }
            }
            if (scaled !== decoded) scaled.recycle()
            if (resultUri != null) break
        }
        decoded.recycle()
        return resultUri
    }

    private fun decodeOrientedBitmap(context: Context, uri: Uri, targetMaxDim: Int): Bitmap? {
        val cr = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: return null

        val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > targetMaxDim) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = cr.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val oriented = applyExifRotationIfNeeded(context, uri, decoded)
        if (oriented !== decoded) decoded.recycle()
        return oriented
    }

    private fun encodeUriToJpegBase64(context: Context, uri: Uri): String? {
        val oriented = decodeOrientedBitmap(context, uri, 1600) ?: return null
        val scaled = scaleDownIfNeeded(oriented, 1440)

        val out = ByteArrayOutputStream()
        var quality = 86
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > 2_500_000 && quality > 50) {
            out.reset()
            quality -= 12
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }

        if (scaled !== oriented) scaled.recycle()
        oriented.recycle()

        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun applyExifRotationIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: return bitmap

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
