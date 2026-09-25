package com.example.photocad.report

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Local renderer. Receives final descriptions/order; never reads or writes Room. */
class PdfReportGenerator {
    suspend fun generate(
        photos: List<PdfPhoto>,
        outputDirectory: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): GeneratedReport {
        var completed: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val snapshot = photos.toList()
                if (snapshot.isEmpty()) throw ReportException("Выберите хотя бы одну фотографию")
                if (!outputDirectory.isDirectory && !outputDirectory.mkdirs())
                    throw ReportException("Не удалось создать папку отчётов")
                val token = UUID.randomUUID().toString()
                val temporary = File(outputDirectory, "$token.part")
                val target = File(outputDirectory, "PhotoCAD-$token.pdf")
                try {
                    // Preflight before any final output exists; no full-resolution bitmap list.
                    snapshot.forEachIndexed { index, photo ->
                        currentCoroutineContext().ensureActive()
                        ReportLayout.prepare(index + 1, photo.pointNumber, photo.drawingPage, photo.description)
                        if (!File(photo.filePath).isFile) throw ReportException("Фотография №${index + 1}: исходный файл отсутствует")
                    }
                    val document = PdfDocument()
                    try {
                        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.BLACK
                            style = Paint.Style.STROKE
                            strokeWidth = 0.8f
                        }
                        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        for (pageIndex in snapshot.indices) {
                            currentCoroutineContext().ensureActive()
                            val page = document.startPage(PdfDocument.PageInfo.Builder(
                                    ReportLayout.PAGE_WIDTH, ReportLayout.PAGE_HEIGHT, pageIndex + 1
                            ).create())
                            try {
                                val canvas = page.canvas
                                canvas.drawColor(Color.WHITE)
                                run {
                                    val index = pageIndex
                                    currentCoroutineContext().ensureActive()
                                    val photo = snapshot[index]
                                    val slot = ReportLayout.prepare(index + 1, photo.pointNumber, photo.drawingPage, photo.description)
                                    val top = ReportLayout.MARGIN
                                    val frame = RectF(ReportLayout.MARGIN, top, ReportLayout.MARGIN + ReportLayout.WIDTH, top + slot.frameHeight)
                                    val bitmap = try { ReportImageLoader.load(photo.filePath) }
                                    catch (failure: Exception) { throw ReportException("Фотография №${index + 1}: ${failure.message}", failure) }
                                    try {
                                        val scale = ReportRules.fitScale(bitmap.width, bitmap.height,
                                            frame.width() - 2 * ReportLayout.IMAGE_PADDING,
                                            frame.height() - 2 * ReportLayout.IMAGE_PADDING)
                                        val width = bitmap.width * scale
                                        val height = bitmap.height * scale
                                        val left = frame.centerX() - width / 2
                                        val imageTop = frame.centerY() - height / 2
                                        canvas.drawBitmap(bitmap, null, RectF(left, imageTop, left + width, imageTop + height), imagePaint)
                                    } finally { bitmap.recycle() }
                                    canvas.drawRect(frame, framePaint)
                                    canvas.save()
                                    try {
                                        canvas.translate(ReportLayout.MARGIN, frame.bottom + ReportLayout.CAPTION_GAP)
                                        slot.caption.draw(canvas)
                                    } finally { canvas.restore() }
                                    onProgress(index + 1, snapshot.size)
                                }
                            } finally { document.finishPage(page) }
                        }
                        currentCoroutineContext().ensureActive()
                        temporary.outputStream().use { document.writeTo(it) }
                    } finally {
                        document.close()
                    }
                    currentCoroutineContext().ensureActive()
                    if (temporary.length() == 0L || !temporary.renameTo(target))
                        throw ReportException("Не удалось завершить запись PDF")
                    completed = target
                    GeneratedReport(target, snapshot.size, ReportRules.pageCount(snapshot.size))
                } finally { temporary.delete() }
            }
        } catch (failure: Throwable) {
            completed?.delete()
            when (failure) {
                is CancellationException -> throw failure
                is ReportException -> throw failure
                is OutOfMemoryError -> throw ReportException("Недостаточно памяти для отчёта. Уменьшите количество выбранных фотографий.", failure)
                is Exception -> throw ReportException("Не удалось сформировать PDF: ${failure.message ?: "ошибка чтения или записи"}", failure)
                else -> throw failure
            }
        }
    }
}
