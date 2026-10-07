package com.example.photocad.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import java.io.File

internal fun isFileUnreferenced(drawingReferences: Int, photoReferences: Int): Boolean =
    drawingReferences == 0 && photoReferences == 0

/** Cascading deletes: DB rows first (transactional), then best-effort file cleanup. */
private suspend fun AppDatabase.deleteFilesWhenUnreferenced(paths: Iterable<String>) {
    paths.toSet().forEach { path ->
        if (isFileUnreferenced(drawingDao().countByFilePath(path), photoDao().countByFilePath(path))) File(path).delete()
    }
}

suspend fun AppDatabase.deletePhoto(photoId: Long) {
    val path = withTransaction {
        val photo = photoDao().getById(photoId) ?: return@withTransaction null
        photoDao().deleteById(photoId)
        photo.filePath
    } ?: return
    deleteFilesWhenUnreferenced(listOf(path))
}

/** Retake keeps the photo row and caption; the old file is removed only after the new path is stored. */
suspend fun AppDatabase.replacePhotoFile(photoId: Long, pointId: Long, newPath: String) {
    val oldPath = withTransaction {
        val photo = photoDao().getById(photoId)?.takeIf { it.pointId == pointId }
            ?: error("Фотография недоступна")
        if (photoDao().updateFilePath(photoId, pointId, newPath) != 1) error("Не удалось заменить фотографию")
        photo.filePath
    }
    if (oldPath != newPath) deleteFilesWhenUnreferenced(listOf(oldPath))
}

suspend fun AppDatabase.deletePoint(pointId: Long) {
    val files = photoDao().getByPoint(pointId).first().map { it.filePath }
    withTransaction {
        photoDao().deleteByPoint(pointId)
        pointDao().delete(pointId)
    }
    deleteFilesWhenUnreferenced(files)
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
    deleteFilesWhenUnreferenced(photoFiles + listOfNotNull(drawing?.filePath))
}

suspend fun AppDatabase.deleteSite(siteId: Long) {
    drawingDao().getBySite(siteId).first().forEach { deleteDrawing(it.id) }
    siteDao().delete(siteId)
}
