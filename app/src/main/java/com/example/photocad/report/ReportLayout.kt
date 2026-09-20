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
    const val GAP = 18f
    const val WIDTH = 539
    const val SLOT_HEIGHT = 384f
    const val CAPTION_GAP = 6f
    const val IMAGE_PADDING = 8f
    const val MIN_FRAME_HEIGHT = 120f

    data class Slot(val caption: StaticLayout, val frameHeight: Float)

    fun prepare(number: Int, description: String): Slot {
        val text = ReportRules.caption(number, description)
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
            val frameHeight = SLOT_HEIGHT - CAPTION_GAP - layout.height
            if (frameHeight >= MIN_FRAME_HEIGHT) return Slot(layout, frameHeight)
        }
        throw ReportException("Подпись фотографии №$number слишком длинная для половины A4. Сократите её только для отчёта: исходное описание не изменится.")
    }
}
