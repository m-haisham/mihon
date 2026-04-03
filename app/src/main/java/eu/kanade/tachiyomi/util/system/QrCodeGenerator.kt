package eu.kanade.tachiyomi.util.system

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {
    fun generate(content: String, size: Int = 1024): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Generates a QR code bitmap with a label text drawn below it for easier identification.
     */
    fun generateWithLabel(content: String, label: String, size: Int = 1024): Bitmap {
        val qrBitmap = generate(content, size)
        val labelPadding = (size * 0.04f).toInt()
        val textSize = (size * 0.045f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        // Measure text to calculate label area height
        val lineHeight = (paint.fontMetrics.bottom - paint.fontMetrics.top).toInt()
        val labelAreaHeight = lineHeight + labelPadding * 2

        val resultBitmap = Bitmap.createBitmap(size, size + labelAreaHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(qrBitmap, 0f, 0f, null)

        // Draw label centered below QR
        val xPos = size / 2f
        val yPos = size + labelPadding - paint.fontMetrics.top
        // Clip label to fit
        val maxWidth = size - labelPadding * 2
        val displayLabel = clipTextToWidth(label, paint, maxWidth)
        canvas.drawText(displayLabel, xPos, yPos, paint)

        qrBitmap.recycle()
        return resultBitmap
    }

    private fun clipTextToWidth(text: String, paint: Paint, maxWidth: Int): String {
        if (paint.measureText(text) <= maxWidth) return text
        var clipped = text
        while (clipped.isNotEmpty() && paint.measureText("$clipped…") > maxWidth) {
            clipped = clipped.dropLast(1)
        }
        return "$clipped…"
    }
}
