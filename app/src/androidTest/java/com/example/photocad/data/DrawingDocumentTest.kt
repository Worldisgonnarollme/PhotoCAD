package com.example.photocad.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DrawingDocumentTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun imageIsOnePageAndRendersWithinRequestedSize() {
        val file = File.createTempFile("drawing", ".png", context.cacheDir)
        try {
            Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(Color.WHITE)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            val document = kotlinx.coroutines.runBlocking { openDrawingDocument(file.path) }
            document.use {
                assertEquals(1, it.pageCount)
                assertEquals(320, it.pageSize(1).width)
                assertEquals(160, it.pageSize(1).height)
                val rendered = kotlinx.coroutines.runBlocking { it.renderPage(1, 96) }
                assertTrue(maxOf(rendered.width, rendered.height) <= 96)
                rendered.recycle()
            }
        } finally {
            file.delete()
        }
    }

    @Test fun pdfRendersOnlyTheRequestedPage() {
        val file = File.createTempFile("drawing", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            repeat(2) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(120 + index * 20, 240, index + 1).create())
                page.canvas.drawColor(if (index == 0) Color.WHITE else Color.LTGRAY)
                pdf.finishPage(page)
            }
            file.outputStream().use { pdf.writeTo(it) }

            val document = kotlinx.coroutines.runBlocking { openDrawingDocument(file.path) }
            document.use {
                assertEquals(2, it.pageCount)
                assertEquals(120, it.pageSize(1).width)
                assertEquals(140, it.pageSize(2).width)
                assertEquals(240, it.pageSize(2).height)
                assertThrows(IllegalArgumentException::class.java) { it.pageSize(3) }
                val rendered = kotlinx.coroutines.runBlocking { it.renderPage(2, 80) }
                assertTrue(maxOf(rendered.width, rendered.height) <= 80)
                rendered.recycle()
            }
        } finally {
            pdf.close()
            file.delete()
        }
    }

    @Test fun largePdfCanRenderFirstPageAgainAfterRenderingLastPage() {
        val file = File.createTempFile("drawing-72-pages", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            repeat(72) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(120, 240, index + 1).create())
                page.canvas.drawColor(if (index == 0) Color.WHITE else Color.LTGRAY)
                pdf.finishPage(page)
            }
            file.outputStream().use { pdf.writeTo(it) }

            val document = kotlinx.coroutines.runBlocking { openDrawingDocument(file.path) }
            document.use {
                assertEquals(72, it.pageCount)
                listOf(1, 72, 1).forEach { pageNumber ->
                    val rendered = kotlinx.coroutines.runBlocking { it.renderPage(pageNumber, 120) }
                    try { assertTrue(rendered.width > 0 && rendered.height > 0) }
                    finally { rendered.recycle() }
                }
            }
        } finally {
            pdf.close()
            file.delete()
        }
    }
}
