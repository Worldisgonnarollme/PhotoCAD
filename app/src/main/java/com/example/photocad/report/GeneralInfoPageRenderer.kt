package com.example.photocad.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.example.photocad.R

/** The copy is a UTF-8 resource; PdfDocument receives real text drawing commands. */
internal class GeneralInfoPageRenderer(context: Context) {
    private val content = context.resources.openRawResource(R.raw.general_information)
        .bufferedReader(Charsets.UTF_8).use { it.readText().trim() }

    fun render(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        val titleBottom = AlbumText.draw(canvas, "Общая информация", 65f, 37f, 465, 14f, bold = true, centered = true)
        val end = AlbumText.draw(canvas, content, 65f, titleBottom + 21f, 465, 9.5f)
        if (end > 800f) throw ReportException("Текст общей информации не помещается на страницу A4")
    }
}
