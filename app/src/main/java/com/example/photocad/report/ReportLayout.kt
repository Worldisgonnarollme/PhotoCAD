package com.example.photocad.report

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** Dimensions in PDF points. A4 rounded to integer points as PdfDocument requires. */
object ReportLayout {
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val MARGIN = 28f
    const val GAP = 16f
    const val WIDTH = 539
    const val CONTENT_HEIGHT = PAGE_HEIGHT - 2 * MARGIN
    const val DRAWING_PREVIEW_HEIGHT = 250f
    const val CAPTION_GAP = 6f
    const val IMAGE_PADDING = 8f
    const val MIN_FRAME_HEIGHT = 150f

    data class Slot(val caption: StaticLayout, val drawingFrameHeight: Float, val frameHeight: Float)

    fun prepare(number: Int, description: String): Slot {
        return prepareCaption(ReportRules.caption(number, description), drawingFrameHeight = 0f)
    }

    fun prepare(number: Int, pointNumber: Int, drawingPage: Int, description: String): Slot =
        prepareCaption(ReportRules.caption(number, pointNumber, drawingPage, description), drawingFrameHeight = 0f)

    fun prepare(number: Int, drawingName: String, drawingPage: Int, pointNumber: Int, description: String): Slot =
        prepareCaption(ReportRules.caption(number, drawingName, drawingPage, pointNumber, description), DRAWING_PREVIEW_HEIGHT)

    private fun prepareCaption(text: String, drawingFrameHeight: Float): Slot {
        // No maxLines or ellipsize: text either fits in full or export reports an error.
        for (size in listOf(11f, 10f, 9f, 8f)) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textSize = size
                typeface = Typeface.create("serif", Typeface.NORMAL)
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(true)
                .setLineSpacing(2f, 1f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .build()
            val gapHeight = if (drawingFrameHeight > 0f) GAP else 0f
            val frameHeight = CONTENT_HEIGHT - drawingFrameHeight - gapHeight - CAPTION_GAP - layout.height
            if (frameHeight >= MIN_FRAME_HEIGHT) return Slot(layout, drawingFrameHeight, frameHeight)
        }
        throw ReportException("Подпись слишком длинная для страницы A4. Сократите её только для отчёта: исходное описание не изменится.")
    }
}
