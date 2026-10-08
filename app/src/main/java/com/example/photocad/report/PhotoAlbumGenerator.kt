package com.example.photocad.report

import android.content.Context
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Builds the PDF from final immutable values; it never reads or writes Room. */
class PhotoAlbumGenerator(private val context: Context) {
    suspend fun generate(
        input: PhotoAlbumInput,
        outputDirectory: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): GeneratedReport = withContext(Dispatchers.IO) {
        val snapshot = input.copy(photos = input.photos.toList())
        if (snapshot.photos.isEmpty()) throw ReportException("Выберите хотя бы одну фотографию")
        snapshot.photos.forEachIndexed { index, photo ->
            // A blank description is allowed: the user confirms it before generation starts.
            if (!File(photo.filePath).isFile) throw ReportException("Фотография №${index + 1}: исходный файл отсутствует")
            if (!File(photo.drawingPath).isFile) throw ReportException("Чертёж для фотографии №${index + 1} недоступен")
        }
        if (!outputDirectory.isDirectory && !outputDirectory.mkdirs()) throw ReportException("Не удалось создать папку отчётов")
        val token = UUID.randomUUID().toString()
        val temporary = File(outputDirectory, "Album-$token.part")
        val target = File(outputDirectory, "Album-$token.pdf")
        val document = PdfDocument()
        val pointRenderer = PhotoPointPageRenderer()
        try {
            fun page(number: Int, draw: (android.graphics.Canvas) -> Unit) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(
                    ReportLayout.PAGE_WIDTH, ReportLayout.PAGE_HEIGHT, number).create())
                try { draw(page.canvas) } finally { document.finishPage(page) }
            }
            currentCoroutineContext().ensureActive()
            page(1) { CoverPageRenderer().render(it, snapshot.cover) }
            page(2) { GeneralInfoPageRenderer(context).render(it) }
            snapshot.photos.forEachIndexed { index, photo ->
                currentCoroutineContext().ensureActive()
                val page = document.startPage(PdfDocument.PageInfo.Builder(
                    ReportLayout.PAGE_WIDTH, ReportLayout.PAGE_HEIGHT, index + 3).create())
                try { pointRenderer.render(page.canvas, photo, index + 3, snapshot.pageCount) }
                finally { document.finishPage(page) }
                onProgress(index + 1, snapshot.photos.size)
            }
            currentCoroutineContext().ensureActive()
            temporary.outputStream().use(document::writeTo)
            currentCoroutineContext().ensureActive()
            if (temporary.length() == 0L || !temporary.renameTo(target)) throw ReportException("Не удалось завершить запись PDF")
            GeneratedReport(target, snapshot.photos.size, snapshot.pageCount)
        } catch (failure: Throwable) {
            target.delete()
            when (failure) {
                is CancellationException -> throw failure
                is ReportException -> throw failure
                is OutOfMemoryError -> throw ReportException("Недостаточно памяти для фотоотчёта", failure)
                is Exception -> throw ReportException("Не удалось сформировать фотоотчёт: ${failure.message}", failure)
                else -> throw failure
            }
        } finally {
            pointRenderer.close()
            document.close()
            temporary.delete()
        }
    }
}
