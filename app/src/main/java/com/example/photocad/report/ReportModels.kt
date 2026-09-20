package com.example.photocad.report

import java.io.File
import java.io.IOException

/** Ordered input. No database, UI bitmap or Android context is required. */
data class PdfPhoto(val filePath: String, val description: String)
data class GeneratedReport(val file: File, val photoCount: Int, val pageCount: Int)
class ReportException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class DraftPhoto(
    val photoId: Long,
    val pointId: Long,
    val filePath: String,
    val description: String,
    val selected: Boolean = true
)

fun movePhoto(photos: List<DraftPhoto>, id: Long, delta: Int): List<DraftPhoto> {
    val from = photos.indexOfFirst { it.photoId == id }
    if (from < 0 || delta !in listOf(-1, 1)) return photos
    val to = from + delta
    if (to !in photos.indices) return photos
    return photos.toMutableList().apply { add(to, removeAt(from)) }
}

fun reportInput(photos: List<DraftPhoto>): List<PdfPhoto> =
    photos.filter { it.selected }.map { PdfPhoto(it.filePath, it.description) }
