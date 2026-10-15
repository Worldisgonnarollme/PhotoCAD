package com.example.photocad.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID

enum class DrawingFormat(val mimeType: String, val defaultExtension: String) {
    PDF("application/pdf", "pdf"),
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg")
}

data class ImportedDrawing(val path: String, val format: DrawingFormat)

fun detectDrawingFormat(mimeType: String?, displayName: String?): DrawingFormat {
    val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
    val extension = displayName?.substringAfterLast('.', "")?.lowercase()
    val byMime = when (mime) {
        "application/pdf" -> DrawingFormat.PDF
        "image/png" -> DrawingFormat.PNG
        "image/jpeg" -> DrawingFormat.JPEG
        else -> null
    }
    val byExtension = when (extension) {
        "pdf" -> DrawingFormat.PDF
        "png" -> DrawingFormat.PNG
        "jpg", "jpeg" -> DrawingFormat.JPEG
        else -> null
    }
    if (byMime != null && byExtension != null && byMime != byExtension)
        throw IllegalArgumentException("Тип файла не совпадает с его расширением")
    return byMime ?: byExtension ?: throw IllegalArgumentException("Выберите файл PDF, PNG, JPG или JPEG")
}

fun drawingExtension(format: DrawingFormat, displayName: String?): String {
    val original = displayName?.substringAfterLast('.', "")?.lowercase()
    return when {
        format == DrawingFormat.JPEG && original == "jpeg" -> "jpeg"
        format == DrawingFormat.JPEG && original == "jpg" -> "jpg"
        else -> format.defaultExtension
    }
}

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

private fun fileFormat(file: File): DrawingFormat {
    val header = ByteArray(8)
    val read = file.inputStream().use { it.read(header) }
    return when {
        read >= 5 && header.copyOfRange(0, 5).decodeToString() == "%PDF-" -> DrawingFormat.PDF
        read >= PNG_SIGNATURE.size && header.contentEquals(PNG_SIGNATURE) -> DrawingFormat.PNG
        read >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> DrawingFormat.JPEG
        else -> throw IOException("Файл пустой, повреждён или имеет неподдерживаемый формат")
    }
}

suspend fun importDrawing(context: Context, uri: Uri): ImportedDrawing = withContext(Dispatchers.IO) {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment
    val format = try {
        detectDrawingFormat(context.contentResolver.getType(uri), name)
    } catch (failure: IllegalArgumentException) {
        throw IOException(failure.message, failure)
    }
    val directory = File(context.filesDir, "drawings")
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Не удалось создать папку чертежей")
    val target = File(directory, "${UUID.randomUUID()}.${drawingExtension(format, name)}")
    try {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Не удалось открыть выбранный файл")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        if (target.length() == 0L) throw IOException("Выбран пустой файл")
        val actual = fileFormat(target)
        if (actual != format) throw IOException("Содержимое файла не соответствует его типу")
        validateDrawingFile(target, format)
        ImportedDrawing(target.absolutePath, format)
    } catch (failure: Throwable) {
        target.delete()
        throw failure
    }
}

private fun validateDrawingFile(file: File, format: DrawingFormat) {
    when (format) {
        DrawingFormat.PDF -> ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount < 1) throw IOException("PDF не содержит страниц")
                renderer.openPage(0).use { page ->
                    if (page.width <= 0 || page.height <= 0) throw IOException("Первая страница PDF повреждена")
                }
            }
        }
        DrawingFormat.PNG, DrawingFormat.JPEG -> {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Изображение повреждено")
            val preview = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 16 })
                ?: throw IOException("Не удалось прочитать изображение")
            preview.recycle()
        }
    }
}

interface DrawingDocument : Closeable {
    val pageCount: Int
    fun pageSize(pageNumber: Int): Size
    suspend fun renderPage(pageNumber: Int, maxWidth: Int): Bitmap
}

suspend fun openDrawingDocument(path: String): DrawingDocument = withContext(Dispatchers.IO) {
    val file = File(path)
    if (!file.isFile || !file.canRead() || file.length() == 0L) throw IOException("Файл чертежа недоступен")
    when (fileFormat(file)) {
        DrawingFormat.PDF -> PdfDrawingDocument(file)
        DrawingFormat.PNG, DrawingFormat.JPEG -> ImageDrawingDocument(file)
    }
}

private class ImageDrawingDocument(private val file: File) : DrawingDocument {
    private val dimensions: Size

    init {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Изображение чертежа повреждено")
        dimensions = Size(options.outWidth, options.outHeight)
        // Validate decodability without holding a full-resolution bitmap.
        val sample = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 16 })
            ?: throw IOException("Не удалось прочитать изображение чертежа")
        sample.recycle()
    }

    override val pageCount: Int = 1
    override fun pageSize(pageNumber: Int): Size {
        require(pageNumber == 1) { "У изображения только одна страница" }
        return dimensions
    }

    override suspend fun renderPage(pageNumber: Int, maxWidth: Int): Bitmap = withContext(Dispatchers.IO) {
        require(pageNumber == 1) { "У изображения только одна страница" }
        require(maxWidth > 0)
        var sample = 1
        while (maxOf(dimensions.width, dimensions.height) / sample > maxWidth) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw IOException("Не удалось отрисовать изображение чертежа")
    }

    override fun close() = Unit
}

private class PdfDrawingDocument(private val file: File) : DrawingDocument {
    private val rendererLock = Any()

    private fun <T> withRenderer(block: (PdfRenderer) -> T): T = synchronized(rendererLock) {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (failure: Throwable) {
            descriptor.close()
            throw failure
        }
        // PdfRenderer owns the descriptor and closes it when the renderer is closed.
        renderer.use(block)
    }

    override val pageCount: Int = withRenderer { it.pageCount }

    override fun pageSize(pageNumber: Int): Size = withRenderer { renderer ->
        require(pageNumber in 1..pageCount) { "Страница PDF вне диапазона" }
        renderer.openPage(pageNumber - 1).use { Size(it.width, it.height) }
    }

    override suspend fun renderPage(pageNumber: Int, maxWidth: Int): Bitmap = withContext(Dispatchers.IO) {
        require(pageNumber in 1..pageCount) { "Страница PDF вне диапазона" }
        require(maxWidth > 0)
        withRenderer { renderer ->
            renderer.openPage(pageNumber - 1).use { page ->
                // PDF is vector, so rendering above the page's own point size keeps zoom sharp.
                val scale = maxWidth.toFloat() / maxOf(page.width, page.height)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, Rect(0, 0, width, height), null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    // Each PDF operation owns a short-lived renderer, so disposal of a screen cannot
    // close a renderer that a page-render coroutine is still using.
    override fun close() = Unit
}
