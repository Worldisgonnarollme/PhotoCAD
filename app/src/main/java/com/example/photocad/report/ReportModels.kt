package com.example.photocad.report

import com.example.photocad.data.Point
import java.io.File
import java.io.IOException

/** Ordered input. No database, UI bitmap or Android context is required. */
data class PdfPhoto(
    val filePath: String,
    val description: String,
    val pointNumber: Int = 1,
    val drawingPage: Int = 1,
    val drawingPath: String = filePath,
    val drawingName: String = "Чертёж",
    val pointX: Float = 0.5f,
    val pointY: Float = 0.5f,
    val colorIndex: Int = 0,
    val capturedAtLocal: String? = null,
    // Position among the photos of this point that actually made it into the album.
    val photoIndexInPoint: Int = 1,
    val photoCountInPoint: Int = 1,
    val capturedOffsetMinutes: Int? = null
)
data class GeneratedReport(val file: File, val photoCount: Int, val pageCount: Int)
class ReportException(message: String, cause: Throwable? = null) : IOException(message, cause)

fun photoServiceLine(photo: PdfPhoto, globalPhotoNumber: Int): String =
    "${photo.drawingName} • лист ${photo.drawingPage} • Точка №${photo.pointNumber} • Фото №$globalPhotoNumber"

data class DraftPhoto(
    val photoId: Long,
    val pointId: Long,
    val filePath: String,
    val description: String,
    val selected: Boolean = true,
    val drawingPage: Int = 1,
    val pointNumber: Int = 1,
    val drawingPath: String = filePath,
    val drawingName: String = "Чертёж",
    val pointX: Float = 0.5f,
    val pointY: Float = 0.5f,
    val colorIndex: Int = 0,
    val captionSource: CaptionSource = CaptionSource.MISSING,
    val drawingId: Long = 0,
    val capturedAtLocal: String? = null,
    val capturedOffsetMinutes: Int? = null
)

enum class CaptionSource { PHOTO_DESCRIPTION, MISSING }

fun captionSource(individual: String?): CaptionSource =
    if (individual.isNullOrBlank()) CaptionSource.MISSING else CaptionSource.PHOTO_DESCRIPTION

fun movePhoto(photos: List<DraftPhoto>, id: Long, delta: Int): List<DraftPhoto> {
    val from = photos.indexOfFirst { it.photoId == id }
    if (from < 0 || delta !in listOf(-1, 1)) return photos
    val to = from + delta
    if (to !in photos.indices) return photos
    return photos.toMutableList().apply { add(to, removeAt(from)) }
}

/** Counts «фото k из n» over the selected photos only, so the album never promises a missing page. */
fun reportInput(photos: List<DraftPhoto>): List<PdfPhoto> {
    val selected = photos.filter { it.selected }
    val countByPoint = selected.groupingBy { it.pointId }.eachCount()
    val seenByPoint = mutableMapOf<Long, Int>()
    return selected.map {
        val position = (seenByPoint[it.pointId] ?: 0) + 1
        seenByPoint[it.pointId] = position
        PdfPhoto(it.filePath, it.description, it.pointNumber, it.drawingPage,
            it.drawingPath, it.drawingName, it.pointX, it.pointY, it.colorIndex, it.capturedAtLocal,
            position, countByPoint.getValue(it.pointId), it.capturedOffsetMinutes)
    }
}

/**
 * Numbers points the way the drawing screen does: the count restarts on every sheet and follows
 * the order of the points themselves, so a point without photos still consumes its number.
 */
fun numberPointsForReport(points: List<Point>, photos: List<DraftPhoto>): List<DraftPhoto> {
    val nextByPage = mutableMapOf<Int, Int>()
    val ordinals = points.associate { point ->
        val next = (nextByPage[point.pageNumber] ?: 0) + 1
        nextByPage[point.pageNumber] = next
        point.id to point.displayNumber.takeIf { it > 0 }?.also { number ->
            nextByPage[point.pageNumber] = maxOf(next, number)
        }.let { it ?: next }
    }
    return photos.map { it.copy(pointNumber = ordinals[it.pointId] ?: 1) }
}
