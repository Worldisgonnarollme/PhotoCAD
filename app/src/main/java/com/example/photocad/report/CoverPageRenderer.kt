package com.example.photocad.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Draws a cover modeled on the supplied example, with editable per-report fields. */
internal class CoverPageRenderer {
    fun render(canvas: Canvas, cover: AlbumCover) {
        canvas.drawColor(Color.WHITE)
        val width = ReportLayout.WIDTH
        val left = ReportLayout.MARGIN
        val org = cover.organizationName.ifBlank { "Организация" }
        AlbumText.draw(canvas, org, left, 10f, width, 27f, centered = true)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; strokeWidth = 0.8f }
        canvas.drawLine(8f, 53f, 587f, 53f, line)
        val details = listOf(
            cover.organizationAddress,
            listOfNotNull(cover.ogrn.takeIf(String::isNotBlank)?.let { "ОГРН $it" },
                cover.inn.takeIf(String::isNotBlank)?.let { "ИНН $it" },
                cover.kpp.takeIf(String::isNotBlank)?.let { "КПП $it" }).joinToString(" / "),
            listOfNotNull(cover.phone.takeIf(String::isNotBlank)?.let { "тел.: $it" },
                cover.email.takeIf(String::isNotBlank)?.let { "эл. почта: $it" }).joinToString(", ")
        ).filter(String::isNotBlank).joinToString("\n")
        AlbumText.draw(canvas, details, left, 57f, width, 8f, centered = true)

        AlbumText.draw(canvas, "Заказчик: ${cover.customer}", left, 168f, width, 11f, centered = true)
        var y = AlbumText.draw(canvas, "Объект: ${cover.objectName}", left, 202f, width, 12f, bold = true, centered = true)
        y = AlbumText.draw(canvas, "Адрес: ${cover.objectAddress}", left, maxOf(254f, y + 18f), width, 11f, centered = true)
        if (y > 355f) throw ReportException("Данные объекта слишком длинные для титульного листа")

        AlbumText.draw(canvas, "ФОТОМАТЕРИАЛ", left, 378f, width, 23f, bold = true, centered = true)
        AlbumText.draw(canvas, "с места производства работ", left, 414f, width, 16f, centered = true)
        if (cover.albumNumber.isNotBlank())
            AlbumText.draw(canvas, "Фотоальбом № ${cover.albumNumber}", left, 448f, width, 11f, centered = true)
        AlbumText.draw(canvas, cover.city, left, 722f, width, 11f, centered = true)
        AlbumText.draw(canvas, "${cover.year} г.", left, 739f, width, 11f, centered = true)
    }
}
