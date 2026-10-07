package com.example.photocad.report

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DrawingEditingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun updatingDrawingChangesNameAndDescription() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val id = db.drawingDao().insert(Drawing(name = "Старое название", filePath = "path", siteId = 1, description = "Старое описание"))
            val updated = db.drawingDao().update(id, "Новое название", "Новое описание")
            assertEquals(1, updated)
            val drawing = db.drawingDao().getById(id)!!
            assertEquals("Новое название", drawing.name)
            assertEquals("Новое описание", drawing.description)
        } finally { db.close() }
    }

    @Test fun updatingPointPositionKeepsComment() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val drawingId = db.drawingDao().insert(Drawing(name = "Чертёж", filePath = "path", siteId = 1))
            val pointId = db.pointDao().insert(Point(drawingId = drawingId, x = 0.1f, y = 0.2f, comment = "Комментарий"))
            val updated = db.pointDao().updatePosition(pointId, 0.6f, 0.8f)
            assertEquals(1, updated)
            val point = db.pointDao().getByDrawing(drawingId).first().single()
            assertEquals(0.6f, point.x)
            assertEquals(0.8f, point.y)
            assertEquals("Комментарий", point.comment)
        } finally { db.close() }
    }

    @Test fun updatingPointColorKeepsPositionAndComment() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val drawingId = db.drawingDao().insert(Drawing(name = "Чертёж", filePath = "path", siteId = 1))
            val pointId = db.pointDao().insert(Point(drawingId = drawingId, x = 0.1f, y = 0.2f, comment = "Комментарий"))
            val updated = db.pointDao().updateColor(pointId, 3)
            assertEquals(1, updated)
            val point = db.pointDao().getByDrawing(drawingId).first().single()
            assertEquals(3, point.colorIndex)
            assertEquals(0.1f, point.x)
            assertEquals(0.2f, point.y)
            assertEquals("Комментарий", point.comment)
        } finally { db.close() }
    }

    @Test fun pointColorAndArchiveStateSurviveReopenWithoutChangingOtherPoints() = runBlocking {
        val name = "point-color-${System.nanoTime()}.db"
        try {
            var firstId = 0L
            var secondId = 0L
            val firstDb = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                val db = firstDb
                val drawingId = db.drawingDao().insert(Drawing(name = "План", filePath = "drawing", siteId = 1))
                firstId = db.pointDao().insert(Point(drawingId = drawingId, x = 0.3f, y = 0.4f, comment = "Исходный"))
                secondId = db.pointDao().insert(Point(drawingId = drawingId, x = 0.6f, y = 0.7f))
                db.pointDao().updateColor(firstId, 1)
                db.pointDao().updateComment(firstId, "Новый комментарий")
                db.photoDao().insert(Photo(pointId = firstId, filePath = "photo", description = "Личное описание"))
                db.pointDao().setArchived(firstId, true)
            } finally { firstDb.close() }
            val secondDb = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                val db = secondDb
                val first = db.pointDao().getById(firstId)!!
                val second = db.pointDao().getById(secondId)!!
                assertEquals(1, first.colorIndex)
                assertTrue(first.isArchived)
                assertEquals("Новый комментарий", first.comment)
                assertEquals("Личное описание", db.photoDao().getByPoint(firstId).first().single().description)
                assertEquals(0, second.colorIndex)
                assertFalse(second.isArchived)
            } finally { secondDb.close() }
        } finally { context.deleteDatabase(name) }
    }
}
