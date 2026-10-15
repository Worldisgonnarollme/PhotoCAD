package com.example.photocad.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.Build
import android.os.Environment
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PhotoAlbumGeneratorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun twoPhotosOfOnePointGetSeparatePagesAfterCoverAndInformation() = runBlocking {
        val drawing = File.createTempFile("album-drawing", ".pdf", context.cacheDir)
        val photo = File.createTempFile("album-photo", ".png", context.cacheDir)
        val secondPhoto = File.createTempFile("album-photo-second", ".png", context.cacheDir)
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(600, 400, 1).create())
            page.canvas.drawColor(Color.WHITE)
            val grid = android.graphics.Paint().apply { color = Color.LTGRAY; strokeWidth = 2f }
            for (x in 0..600 step 50) page.canvas.drawLine(x.toFloat(), 0f, x.toFloat(), 400f, grid)
            for (y in 0..400 step 50) page.canvas.drawLine(0f, y.toFloat(), 600f, y.toFloat(), grid)
            document.finishPage(page)
            drawing.outputStream().use(document::writeTo)
            val image = Bitmap.createBitmap(300, 500, Bitmap.Config.ARGB_8888)
            try { image.eraseColor(Color.GREEN); photo.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { image.recycle() }
            val second = Bitmap.createBitmap(500, 300, Bitmap.Config.ARGB_8888)
            try { second.eraseColor(Color.YELLOW); secondPhoto.outputStream().use { second.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { second.recycle() }

            val input = PhotoAlbumInput(
                cover = AlbumCover(organizationName = "ООО Тест", objectName = "Объект", city = "Москва", year = "2026"),
                photos = listOf(photo, secondPhoto).mapIndexed { index, file ->
                    PdfPhoto(file.path, if (index == 0) "Русское описание 1" else
                        "Длинное описание выполненных работ и состояния объекта. ".repeat(4), pointNumber = 7,
                        drawingPath = drawing.path, pointX = 0.95f, pointY = 0.05f, colorIndex = 1)
                }
            )
            val output = PhotoAlbumGenerator(context).generate(input, context.cacheDir)
            try {
                assertEquals(4, output.pageCount)
                PdfRenderer(ParcelFileDescriptor.open(output.file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    assertEquals(4, renderer.pageCount)
                    renderer.openPage(2).use { pointPage ->
                        val preview = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                        try {
                            pointPage.render(preview, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            assertPdfColorNear(Color.GREEN, preview.getPixel(297, 500))
                            assertPdfColorNear(com.example.photocad.data.PointColors.argb(1), preview.getPixel(529, 53))
                        } finally { preview.recycle() }
                    }
                    renderer.openPage(3).use { pointPage ->
                        val preview = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                        try {
                            pointPage.render(preview, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            assertPdfColorNear(Color.YELLOW, preview.getPixel(297, 500))
                        } finally { preview.recycle() }
                    }
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "photo-album-visual-${System.currentTimeMillis()}.pdf")
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PhotoCADVerification")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                    context.contentResolver.openOutputStream(uri)!!.use { target ->
                        output.file.inputStream().use { source -> source.copyTo(target) }
                    }
                }
            } finally { output.file.delete() }
        } finally { document.close(); drawing.delete(); photo.delete(); secondPhoto.delete() }
    }

    @Test fun photoWithoutDescriptionStillProducesItsPage() = runBlocking {
        val drawing = File.createTempFile("blank-drawing", ".pdf", context.cacheDir)
        val photo = File.createTempFile("blank-photo", ".png", context.cacheDir)
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(600, 400, 1).create())
            page.canvas.drawColor(Color.WHITE)
            document.finishPage(page)
            drawing.outputStream().use(document::writeTo)
            val image = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
            try { image.eraseColor(Color.GREEN); photo.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { image.recycle() }

            val input = PhotoAlbumInput(
                cover = AlbumCover(objectName = "Объект"),
                photos = listOf(PdfPhoto(photo.path, "", drawingPath = drawing.path))
            )
            val output = PhotoAlbumGenerator(context).generate(input, context.cacheDir)
            try {
                assertEquals(3, output.pageCount)
                PdfRenderer(ParcelFileDescriptor.open(output.file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    assertEquals(3, renderer.pageCount)
                }
            } finally { output.file.delete() }
        } finally { document.close(); drawing.delete(); photo.delete() }
    }
}
