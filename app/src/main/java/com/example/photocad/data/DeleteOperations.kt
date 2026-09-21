package com.example.photocad.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import java.io.File

/** Cascading deletes: DB rows first (transactional), then best-effort file cleanup. */
suspend fun AppDatabase.deletePoint(pointId: Long) {
    val files = photoDao().getByPoint(pointId).first().map { it.filePath }
    withTransaction {
        photoDao().deleteByPoint(pointId)
        pointDao().delete(pointId)
    }
    files.forEach { File(it).delete() }
}

suspend fun AppDatabase.deleteDrawing(drawingId: Long) {
    val pointIds = pointDao().getByDrawing(drawingId).first().map { it.id }
    val photoFiles = pointIds.flatMap { photoDao().getByPoint(it).first() }.map { it.filePath }
    val drawing = drawingDao().getById(drawingId)
    withTransaction {
        pointIds.forEach { photoDao().deleteByPoint(it) }
        pointDao().deleteByDrawing(drawingId)
        drawingDao().delete(drawingId)
    }
    photoFiles.forEach { File(it).delete() }
    drawing?.let { File(it.filePath).delete() }
}

suspend fun AppDatabase.deleteSite(siteId: Long) {
    drawingDao().getBySite(siteId).first().forEach { deleteDrawing(it.id) }
    siteDao().delete(siteId)
}
