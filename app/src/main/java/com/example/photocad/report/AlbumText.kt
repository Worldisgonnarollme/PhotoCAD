package com.example.photocad.report

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

internal object AlbumText {
    private fun layout(text: String, width: Int, size: Float, bold: Boolean, centered: Boolean): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = size
            typeface = Typeface.create("serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setLineSpacing(2f, 1f)
            .build()
    }

    fun height(text: String, width: Int, size: Float, bold: Boolean = false, centered: Boolean = false): Int =
        layout(text, width, size, bold, centered).height

    fun draw(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Int,
        size: Float,
        bold: Boolean = false,
        centered: Boolean = false
    ): Float {
        val layout = layout(text, width, size, bold, centered)
        canvas.save()
        try { canvas.translate(x, y); layout.draw(canvas) }
        finally { canvas.restore() }
        return y + layout.height
    }
}
