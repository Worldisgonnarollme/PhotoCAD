package com.example.photocad.data

import androidx.room.withTransaction

/** Freeze the point's saved comment when the photo is inserted. */
suspend fun AppDatabase.insertPhotoWithComment(pointId: Long, filePath: String): Long = withTransaction {
    val point = pointDao().getById(pointId) ?: error("Точка больше недоступна")
    photoDao().insert(Photo(pointId = pointId, filePath = filePath, description = point.comment))
}
