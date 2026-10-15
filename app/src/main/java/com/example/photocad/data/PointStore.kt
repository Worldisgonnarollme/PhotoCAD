package com.example.photocad.data

import androidx.room.withTransaction

/** Number allocation and insert must stay in one transaction for rapid taps. */
suspend fun AppDatabase.insertNumberedPoint(point: Point): Long = withTransaction {
    val number = pointDao().nextDisplayNumber(point.drawingId, point.pageNumber)
    pointDao().insert(point.copy(displayNumber = number))
}
