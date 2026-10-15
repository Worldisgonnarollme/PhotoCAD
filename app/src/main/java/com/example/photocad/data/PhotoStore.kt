package com.example.photocad.data

import androidx.room.withTransaction

/** A photo starts without a description: every snapshot gets its own text in the editor. */
suspend fun AppDatabase.insertPhoto(pointId: Long, filePath: String): Long = withTransaction {
    pointDao().getById(pointId) ?: error("Точка больше недоступна")
    photoDao().insert(Photo(pointId = pointId, filePath = filePath, description = null))
}
