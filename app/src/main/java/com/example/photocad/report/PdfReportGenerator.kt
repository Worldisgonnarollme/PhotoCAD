package com.example.photocad.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.photocad.data.openDrawingDocument
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
                        ReportLayout.prepare(index + 1, photo.drawingName, photo.drawingPage, photo.pointNumber, photo.description)
                        if (!File(photo.filePath).isFile) throw ReportException("Фотография №${index + 1}: исходный файл отсутствует")
                        if (!File(photo.drawingPath).isFile) throw ReportException("Чертёж «${photo.drawingName}» для фотографии №${index + 1} недоступен")
                    }
                    val document = PdfDocument()
                    var cachedPreviewKey: String? = null
                    var cachedPreview: Bitmap? = null
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
                                val index = pageIndex
                                currentCoroutineContext().ensureActive()
                                val photo = snapshot[index]
                                val slot = ReportLayout.prepare(index + 1, photo.drawingName, photo.drawingPage, photo.pointNumber, photo.description)
                                val previewKey = "${photo.drawingPath}\u0000${photo.drawingPage}"
                                if (previewKey != cachedPreviewKey) {
                                    cachedPreview?.recycle()
                                    cachedPreview = loadDrawingPreview(photo, index + 1)
                                    cachedPreviewKey = previewKey
                                }

                                val drawingFrame = RectF(
                                    ReportLayout.MARGIN, ReportLayout.MARGIN,
                                    ReportLayout.MARGIN + ReportLayout.WIDTH,
                                    ReportLayout.MARGIN + slot.drawingFrameHeight
                                )
                                val previewBounds = drawFittedBitmap(canvas, cachedPreview!!, drawingFrame, imagePaint)
                                val pointX = photo.pointX.coerceIn(0f, 1f)
                                val pointY = photo.pointY.coerceIn(0f, 1f)
                                val markerCenterX = previewBounds.left + previewBounds.width() * pointX
                                val markerCenterY = previewBounds.top + previewBounds.height() * pointY
                                val markerOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = Color.WHITE
                                    style = Paint.Style.STROKE
                                    strokeWidth = 3f
                                }
                                val markerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
                                canvas.drawCircle(markerCenterX, markerCenterY, 8f, markerOutline)
                                canvas.drawCircle(markerCenterX, markerCenterY, 6f, markerFill)
                                canvas.drawRect(drawingFrame, framePaint)

                                val photoFrameTop = drawingFrame.bottom + ReportLayout.GAP
                                val photoFrame = RectF(
                                    ReportLayout.MARGIN, photoFrameTop,
                                    ReportLayout.MARGIN + ReportLayout.WIDTH,
                                    photoFrameTop + slot.frameHeight
                                )
                                val bitmap = try { ReportImageLoader.load(photo.filePath) }
                                catch (failure: Exception) { throw ReportException("Фотография №${index + 1}: ${failure.message}", failure) }
                                try { drawFittedBitmap(canvas, bitmap, photoFrame, imagePaint) }
                                finally { bitmap.recycle() }
                                canvas.drawRect(photoFrame, framePaint)
                                canvas.save()
                                try {
                                    canvas.translate(ReportLayout.MARGIN, photoFrame.bottom + ReportLayout.CAPTION_GAP)
                                    slot.caption.draw(canvas)
                                } finally { canvas.restore() }
                                onProgress(index + 1, snapshot.size)
                            } finally { document.finishPage(page) }
                        }
                        currentCoroutineContext().ensureActive()
                        temporary.outputStream().use { document.writeTo(it) }
                    } finally {
                        cachedPreview?.recycle()
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

    private suspend fun loadDrawingPreview(photo: PdfPhoto, photoNumber: Int): Bitmap {
        val reader = try { openDrawingDocument(photo.drawingPath) }
        catch (failure: Exception) {
            throw ReportException("Не удалось открыть чертёж «${photo.drawingName}» для фотографии №$photoNumber: ${failure.message}", failure)
        }
        return try {
            if (photo.drawingPage !in 1..reader.pageCount)
                throw ReportException("В чертеже «${photo.drawingName}» нет страницы ${photo.drawingPage}")
            reader.renderPage(photo.drawingPage, maxOf(ReportLayout.WIDTH, ReportLayout.DRAWING_PREVIEW_HEIGHT.toInt()) * 2)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: ReportException) {
            throw failure
        } catch (failure: Exception) {
            throw ReportException("Не удалось прочитать страницу ${photo.drawingPage} чертежа «${photo.drawingName}»: ${failure.message}", failure)
        } finally {
            reader.close()
        }
    }

    private fun drawFittedBitmap(canvas: android.graphics.Canvas, bitmap: Bitmap, frame: RectF, paint: Paint): RectF {
        val innerWidth = frame.width() - 2 * ReportLayout.IMAGE_PADDING
        val innerHeight = frame.height() - 2 * ReportLayout.IMAGE_PADDING
        val scale = ReportRules.fitScale(bitmap.width, bitmap.height, innerWidth, innerHeight)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = frame.centerX() - width / 2
        val top = frame.centerY() - height / 2
        val bounds = RectF(left, top, left + width, top + height)
        canvas.drawBitmap(bitmap, null, bounds, paint)
        return bounds
    }
}
