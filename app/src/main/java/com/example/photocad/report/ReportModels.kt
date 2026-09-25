package com.example.photocad.report

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
    val pointY: Float = 0.5f
)
data class GeneratedReport(val file: File, val photoCount: Int, val pageCount: Int)
class ReportException(message: String, cause: Throwable? = null) : IOException(message, cause)

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
    val pointY: Float = 0.5f
)

fun movePhoto(photos: List<DraftPhoto>, id: Long, delta: Int): List<DraftPhoto> {
    val from = photos.indexOfFirst { it.photoId == id }
    if (from < 0 || delta !in listOf(-1, 1)) return photos
    val to = from + delta
    if (to !in photos.indices) return photos
    return photos.toMutableList().apply { add(to, removeAt(from)) }
}

fun reportInput(photos: List<DraftPhoto>): List<PdfPhoto> =
    photos.filter { it.selected }.map {
        PdfPhoto(it.filePath, it.description, it.pointNumber, it.drawingPage,
            it.drawingPath, it.drawingName, it.pointX, it.pointY)
    }

fun reportPointOrdinals(photos: List<DraftPhoto>): List<DraftPhoto> {
    val ordinalByPageAndPoint = linkedMapOf<Pair<Int, Long>, Int>()
    val nextByPage = mutableMapOf<Int, Int>()
    return photos.map { photo ->
        val key = photo.drawingPage to photo.pointId
        val ordinal = ordinalByPageAndPoint.getOrPut(key) {
            val next = (nextByPage[photo.drawingPage] ?: 0) + 1
            nextByPage[photo.drawingPage] = next
            next
        }
        photo.copy(pointNumber = ordinal)
    }
}
