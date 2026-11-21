package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

object QrCodeUtils {

    private val connectNumberRegex = Regex("^01[0-9]{7}$")
    private val connectNumberSearchRegex = Regex("(?:num=|^|\\b)(01[0-9]{7})(?:\\b|$)")

    fun isValidConnectNumber(input: String): Boolean =
        connectNumberRegex.matches(input.trim())

    fun sanitizeConnectNumberInput(input: String): String =
        input.filter { it.isDigit() }.take(9)

    fun encodeConnectQrPayload(connectNumber: String): String =
        "connectlive://add?num=${connectNumber.trim()}"

    fun extractConnectNumberFromQr(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val cleaned = rawText.trim()
        val uriMatch = runCatching {
            val uri = Uri.parse(cleaned)
            uri.getQueryParameter("num")?.trim()?.takeIf { isValidConnectNumber(it) }
        }.getOrNull()
        if (uriMatch != null) return uriMatch

        val digitsOnly = cleaned.filter { it.isDigit() }
        if (isValidConnectNumber(digitsOnly)) return digitsOnly

        return connectNumberSearchRegex.find(cleaned)?.groupValues?.getOrNull(1)
            ?.takeIf { isValidConnectNumber(it) }
    }

    /**
     * Generates an iconic iOS White & Blue stylized QR bitmap with ErrorCorrectionLevel.H,
     * rounded dots, Azure Blue finder patterns, and a centered Connect Live emblem.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 720): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val qrCode = Encoder.encode(content, ErrorCorrectionLevel.H, hints)
        val matrix = qrCode.matrix
        val modCount = matrix.width
        val quietModules = 2
        val totalModules = modCount + quietModules * 2
        val cellSize = sizePx.toFloat() / totalModules.toFloat()

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f,
                0f,
                sizePx.toFloat(),
                sizePx.toFloat(),
                Color.parseColor("#0B2A5B"),
                Color.parseColor("#0A84FF"),
                Shader.TileMode.CLAMP
            )
        }

        val finderOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0A84FF")
        }
        val finderWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val finderCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0B1E3D")
        }

        // Center logo exclusion area (~21% of matrix modules so Level H redundancy decodes cleanly)
        val centerIdx = modCount / 2f
        val clearHalfModules = (modCount * 0.135f)

        fun isInsideFinder(mx: Int, my: Int): Boolean {
            val inTopLeft = mx < 7 && my < 7
            val inTopRight = mx >= modCount - 7 && my < 7
            val inBottomLeft = mx < 7 && my >= modCount - 7
            return inTopLeft || inTopRight || inBottomLeft
        }

        fun isInsideLogoZone(mx: Int, my: Int): Boolean {
            val dx = mx + 0.5f - centerIdx
            val dy = my + 0.5f - centerIdx
            return kotlin.math.abs(dx) <= clearHalfModules && kotlin.math.abs(dy) <= clearHalfModules
        }

        // Draw stylized data modules
        val radius = cellSize * 0.36f
        for (my in 0 until modCount) {
            for (mx in 0 until modCount) {
                if (isInsideFinder(mx, my) || isInsideLogoZone(mx, my)) continue
                if (matrix[mx, my].toInt() == 1) {
                    val left = (mx + quietModules) * cellSize + cellSize * 0.08f
                    val top = (my + quietModules) * cellSize + cellSize * 0.08f
                    val right = (mx + quietModules + 1) * cellSize - cellSize * 0.08f
                    val bottom = (my + quietModules + 1) * cellSize - cellSize * 0.08f
                    canvas.drawRoundRect(left, top, right, bottom, radius, radius, dotPaint)
                }
            }
        }

        // Draw 3 rounded iconic Azure finder eyes
        val finderPositions = listOf(
            0 to 0,
            (modCount - 7) to 0,
            0 to (modCount - 7)
        )
        for ((fx, fy) in finderPositions) {
            val x0 = (fx + quietModules) * cellSize
            val y0 = (fy + quietModules) * cellSize
            val outerSize = 7f * cellSize
            canvas.drawRoundRect(
                x0,
                y0,
                x0 + outerSize,
                y0 + outerSize,
                cellSize * 1.8f,
                cellSize * 1.8f,
                finderOuterPaint
            )
            canvas.drawRoundRect(
                x0 + cellSize,
                y0 + cellSize,
                x0 + outerSize - cellSize,
                y0 + outerSize - cellSize,
                cellSize * 1.15f,
                cellSize * 1.15f,
                finderWhitePaint
            )
            canvas.drawRoundRect(
                x0 + 2f * cellSize,
                y0 + 2f * cellSize,
                x0 + outerSize - 2f * cellSize,
                y0 + outerSize - 2f * cellSize,
                cellSize * 0.85f,
                cellSize * 0.85f,
                finderCorePaint
            )
        }

        // Draw centered Connect Live Emblem Badge
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val badgeHalf = (clearHalfModules * cellSize) * 1.05f
        val badgeRect = RectF(cx - badgeHalf, cy - badgeHalf, cx + badgeHalf, cy + badgeHalf)

        val badgeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#E3EEFF")
        }
        canvas.drawRoundRect(
            RectF(
                badgeRect.left - 4f,
                badgeRect.top - 4f,
                badgeRect.right + 4f,
                badgeRect.bottom + 4f
            ),
            badgeHalf * 0.45f,
            badgeHalf * 0.45f,
            badgeShadowPaint
        )

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                badgeRect.left,
                badgeRect.top,
                badgeRect.right,
                badgeRect.bottom,
                Color.parseColor("#0A84FF"),
                Color.parseColor("#1D4ED8"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(badgeRect, badgeHalf * 0.42f, badgeHalf * 0.42f, badgeBgPaint)

        // Draw crisp white Connect Live camera-pulse mark inside the badge
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val bodyW = badgeHalf * 0.78f
        val bodyH = badgeHalf * 0.56f
        val bodyRect = RectF(
            cx - bodyW * 0.65f,
            cy - bodyH * 0.5f,
            cx + bodyW * 0.28f,
            cy + bodyH * 0.5f
        )
        canvas.drawRoundRect(bodyRect, badgeHalf * 0.14f, badgeHalf * 0.14f, iconPaint)

        val lensWing = Path().apply {
            moveTo(cx + bodyW * 0.25f, cy - bodyH * 0.14f)
            lineTo(cx + bodyW * 0.66f, cy - bodyH * 0.40f)
            lineTo(cx + bodyW * 0.66f, cy + bodyH * 0.40f)
            lineTo(cx + bodyW * 0.25f, cy + bodyH * 0.14f)
            close()
        }
        canvas.drawPath(lensWing, iconPaint)

        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#38BDF8")
        }
        canvas.drawCircle(
            cx - bodyW * 0.19f,
            cy,
            badgeHalf * 0.13f,
            pulsePaint
        )

        return bitmap
    }

    fun decodeQrFromImageProxy(image: ImageProxy): String? {
        if (image.format != ImageFormat.YUV_420_888 &&
            image.format != ImageFormat.YUV_422_888 &&
            image.format != ImageFormat.YUV_444_888
        ) {
            return null
        }

        val yPlane = image.planes.firstOrNull() ?: return null
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val width = image.width
        val height = image.height

        val yBytes = ByteArray(buffer.remaining())
        buffer.get(yBytes)

        val tightBytes = if (rowStride == width) {
            yBytes
        } else {
            ByteArray(width * height).also { dst ->
                for (row in 0 until height) {
                    val srcPos = row * rowStride
                    val copyLen = minOf(width, yBytes.size - srcPos)
                    if (copyLen > 0) {
                        System.arraycopy(yBytes, srcPos, dst, row * width, copyLen)
                    }
                }
            }
        }

        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }

        return runCatching {
            val source = PlanarYUVLuminanceSource(
                tightBytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        }.getOrNull() ?: runCatching {
            val rotated = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    rotated[x * height + height - y - 1] = tightBytes[y * width + x]
                }
            }
            val source = PlanarYUVLuminanceSource(
                rotated,
                height,
                width,
                0,
                0,
                height,
                width,
                false
            )
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        }.getOrNull()
    }

    fun decodeQrFromUri(context: Context, uri: Uri): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxSide / sample > 1200) sample *= 2

        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } ?: return null

        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val reader = MultiFormatReader().apply {
                setHints(
                    mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.TRY_HARDER to true
                    )
                )
            }
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }
}
