package com.example.photocad.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.example.photocad.data.PointColors
import com.example.photocad.data.openDrawingDocument
import java.io.Closeable

/** Keeps at most one decoded drawing page and one photograph in memory. */
internal class PhotoPointPageRenderer : Closeable {
    private var drawingKey: String? = null
    private var drawingBitmap: Bitmap? = null
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }

    suspend fun render(canvas: Canvas, photo: PdfPhoto, pageNumber: Int, totalPages: Int) {
        canvas.drawColor(Color.WHITE)
        val drawingFrame = RectF(28f, 28f, 567f, 285f)
        val photoFrame = RectF(28f, 300f, 567f, 695f)
        val key = "${photo.drawingPath}\u0000${photo.drawingPage}"
        if (drawingKey != key) {
            drawingBitmap?.recycle()
            drawingBitmap = null
            val reader = openDrawingDocument(photo.drawingPath)
            try {
                if (photo.drawingPage !in 1..reader.pageCount)
                    throw ReportException("В чертеже «${photo.drawingName}» нет страницы ${photo.drawingPage}")
                drawingBitmap = reader.renderPage(photo.drawingPage, 2400)
                drawingKey = key
            } finally { reader.close() }
        }
        val drawing = drawingBitmap ?: throw ReportException("Не удалось отрисовать чертёж")
        val crop = pointCrop(photo.pointX, photo.pointY, drawing.width, drawing.height, drawingFrame.width(), drawingFrame.height())
        val source = Rect(
            (crop.left * drawing.width).toInt(), (crop.top * drawing.height).toInt(),
            (crop.right * drawing.width).toInt().coerceAtMost(drawing.width),
            (crop.bottom * drawing.height).toInt().coerceAtMost(drawing.height)
        )
        canvas.drawBitmap(drawing, source, drawingFrame, imagePaint)
        val markerX = drawingFrame.left + (photo.pointX.coerceIn(0f, 1f) - crop.left) / (crop.right - crop.left) * drawingFrame.width()
        val markerY = drawingFrame.top + (photo.pointY.coerceIn(0f, 1f) - crop.top) / (crop.bottom - crop.top) * drawingFrame.height()
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PointColors.argb(photo.colorIndex) }
        canvas.drawCircle(markerX, markerY, 9f, outline)
        canvas.drawCircle(markerX, markerY, 7f, fill)
        canvas.drawRect(drawingFrame, framePaint)

        val bitmap = ReportImageLoader.load(photo.filePath)
        try {
            val scale = ReportRules.fitScale(bitmap.width, bitmap.height, photoFrame.width() - 16f, photoFrame.height() - 16f)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val fitted = RectF(photoFrame.centerX() - width / 2f, photoFrame.centerY() - height / 2f,
                photoFrame.centerX() + width / 2f, photoFrame.centerY() + height / 2f)
            canvas.drawBitmap(bitmap, null, fitted, imagePaint)
        } finally { bitmap.recycle() }
        canvas.drawRect(photoFrame, framePaint)

        AlbumText.draw(canvas, "Точка №${photo.pointNumber} • ${photo.drawingName} • лист ${photo.drawingPage}", 28f, 704f, 539, 10f, bold = true)
        val caption = photo.description.trim()
        val size = listOf(11f, 10f, 9f, 8f).firstOrNull { AlbumText.height(caption, 539, it) <= 87 }
            ?: throw ReportException("Описание фотографии слишком длинное для страницы A4")
        AlbumText.draw(canvas, caption, 28f, 723f, 539, size)
        AlbumText.draw(canvas, "Страница $pageNumber из $totalPages", 28f, 812f, 539, 9f, centered = true)
    }

    override fun close() {
        drawingBitmap?.recycle()
        drawingBitmap = null
        drawingKey = null
    }
}
