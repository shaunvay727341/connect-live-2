package com.example.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CloudinaryUploadResult(
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long
)

class CloudinaryService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun isConfigured(overrideCloudName: String = "", overridePreset: String = ""): Boolean {
        val cloud = effectiveCloudName(overrideCloudName)
        val preset = effectiveUploadPreset(overridePreset)
        return cloud.isNotBlank() && preset.isNotBlank()
    }

    fun effectiveCloudName(overrideCloudName: String = ""): String {
        val buildCloud = BuildConfig.CLOUDINARY_CLOUD_NAME.trim()
        return buildCloud.takeIf { isValidConfigValue(it) }
            ?: overrideCloudName.trim().takeIf { isValidConfigValue(it) }
            ?: ""
    }

    fun effectiveUploadPreset(overridePreset: String = ""): String {
        val buildPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim()
        return buildPreset.takeIf { isValidConfigValue(it) }
            ?: overridePreset.trim().takeIf { isValidConfigValue(it) }
            ?: ""
    }

    suspend fun uploadVideo(
        context: Context,
        videoUri: Uri,
        overrideCloudName: String = "",
        overridePreset: String = "",
        folderName: String = "connect_live_videos",
        onProgress: (Float) -> Unit = {}
    ): Result<CloudinaryUploadResult> = withContext(Dispatchers.IO) {
        val cloudName = effectiveCloudName(overrideCloudName)
        val uploadPreset = effectiveUploadPreset(overridePreset)
        if (cloudName.isBlank() || uploadPreset.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Cloudinary Cloud Name & Unsigned Upload Preset are not configured. Set them in Profile -> App Settings -> Media, ImgBB & Cloudinary (or in Secrets)."
                )
            )
        }

        try {
            val appCtx = context.applicationContext
            val mimeType = appCtx.contentResolver.getType(videoUri) ?: "video/mp4"
            val fileName = resolveFileName(appCtx, videoUri) ?: "video_${System.currentTimeMillis()}.mp4"
            val fileSize = resolveFileSize(appCtx, videoUri)
            val durationMs = extractVideoDurationMs(appCtx, videoUri)

            val fileRequestBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaTypeOrNull()
                override fun contentLength(): Long = fileSize
                override fun writeTo(sink: BufferedSink) {
                    val stream = appCtx.contentResolver.openInputStream(videoUri)
                        ?: throw IOException("Unable to read selected video.")
                    stream.source().use { source ->
                        var totalRead = 0L
                        val buffer = okio.Buffer()
                        while (true) {
                            val read = source.read(buffer, 16_384L)
                            if (read == -1L) break
                            sink.write(buffer, read)
                            totalRead += read
                            if (fileSize > 0) {
                                onProgress((totalRead.toFloat() / fileSize.toFloat()).coerceIn(0.05f, 0.95f))
                            }
                        }
                    }
                }
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", uploadPreset)
                .addFormDataPart("folder", folderName)
                .addFormDataPart("resource_type", "video")
                .addFormDataPart("file", fileName, fileRequestBody)
                .build()

            val endpoint = "https://api.cloudinary.com/v1_1/$cloudName/video/upload"
            val request = Request.Builder()
                .url(endpoint)
                .post(multipartBody)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
                    }.getOrDefault("")
                    return@withContext Result.failure(
                        IllegalStateException(
                            errMsg.ifBlank { "Cloudinary upload failed (HTTP ${response.code})." }
                        )
                    )
                }
                val json = JSONObject(raw)
                val secureUrl = json.optString("secure_url")
                if (secureUrl.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Cloudinary response missing secure_url."))
                }
                val durationFromServer = (json.optDouble("duration", 0.0) * 1000.0).toLong()
                val finalDuration = if (durationFromServer > 0L) durationFromServer else durationMs
                val thumbUrl = deriveCloudinaryThumbnail(secureUrl)
                onProgress(1f)
                Result.success(
                    CloudinaryUploadResult(
                        videoUrl = secureUrl.take(1000),
                        thumbnailUrl = thumbUrl.take(1000),
                        durationMs = finalDuration.coerceIn(0L, 3_600_000L)
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deriveCloudinaryThumbnail(videoSecureUrl: String): String {
        val uploadToken = "/video/upload/"
        val idx = videoSecureUrl.indexOf(uploadToken)
        if (idx == -1) return ""
        val prefix = videoSecureUrl.substring(0, idx + uploadToken.length)
        val suffix = videoSecureUrl.substring(idx + uploadToken.length)
        val dot = suffix.lastIndexOf('.')
        val basePath = if (dot > 0) suffix.substring(0, dot) else suffix
        return "${prefix}so_0,w_480,c_fill/${basePath}.jpg"
    }

    private fun extractVideoDurationMs(context: Context, uri: Uri): Long {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durStr?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }

    private fun resolveFileSize(context: Context, uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else -1L
                } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun isValidConfigValue(raw: String): Boolean {
        return raw.isNotBlank() &&
            !raw.equals("UNSET", ignoreCase = true) &&
            !raw.equals("null", ignoreCase = true)
    }
}
