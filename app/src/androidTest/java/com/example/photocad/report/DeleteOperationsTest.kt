package com.example.photocad.report

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DeleteOperationsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun deletePointRemovesItsPhotosAndFilesButKeepsOtherPoints() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val siteId = db.siteDao().insert(Site(name = "Объект"))
            val drawingId = db.drawingDao().insert(Drawing(name = "Чертёж", filePath = "drawing.jpg", siteId = siteId))
            val keptPointId = db.pointDao().insert(Point(drawingId = drawingId, x = 0f, y = 0f))
            val deletedPointId = db.pointDao().insert(Point(drawingId = drawingId, x = 0f, y = 0f))
            val photoFile = File.createTempFile("photo", ".jpg", context.filesDir)
            db.photoDao().insert(Photo(pointId = deletedPointId, filePath = photoFile.path))

            db.deletePoint(deletedPointId)

            assertNull(db.pointDao().getById(deletedPointId))
            assertNotNull(db.pointDao().getById(keptPointId))
            assertTrue(db.photoDao().getByPoint(deletedPointId).first().isEmpty())
            assertFalse(photoFile.exists())
        } finally { db.close() }
    }

    @Test fun deleteDrawingRemovesItsPointsPhotosAndFileButKeepsOtherDrawing() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val siteId = db.siteDao().insert(Site(name = "Объект"))
            val drawingFile = File.createTempFile("drawing", ".jpg", context.filesDir)
            val deletedDrawingId = db.drawingDao().insert(Drawing(name = "Удаляемый", filePath = drawingFile.path, siteId = siteId))
            val keptDrawingId = db.drawingDao().insert(Drawing(name = "Оставшийся", filePath = "other.jpg", siteId = siteId))
            val pointId = db.pointDao().insert(Point(drawingId = deletedDrawingId, x = 0f, y = 0f))
            val photoFile = File.createTempFile("photo", ".jpg", context.filesDir)
            db.photoDao().insert(Photo(pointId = pointId, filePath = photoFile.path))

            db.deleteDrawing(deletedDrawingId)

            assertNull(db.drawingDao().getById(deletedDrawingId))
            assertNotNull(db.drawingDao().getById(keptDrawingId))
            assertTrue(db.pointDao().getByDrawing(deletedDrawingId).first().isEmpty())
            assertFalse(drawingFile.exists())
            assertFalse(photoFile.exists())
        } finally { db.close() }
    }

    @Test fun deleteSiteRemovesAllItsDrawingsButKeepsOtherSite() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val deletedSiteId = db.siteDao().insert(Site(name = "Удаляемый объект"))
            val keptSiteId = db.siteDao().insert(Site(name = "Оставшийся объект"))
            val drawingFile = File.createTempFile("drawing", ".jpg", context.filesDir)
            db.drawingDao().insert(Drawing(name = "Чертёж", filePath = drawingFile.path, siteId = deletedSiteId))
            val keptDrawingId = db.drawingDao().insert(Drawing(name = "Чужой чертёж", filePath = "other.jpg", siteId = keptSiteId))

            db.deleteSite(deletedSiteId)

            assertNull(db.siteDao().getById(deletedSiteId))
            assertNotNull(db.siteDao().getById(keptSiteId))
            assertTrue(db.drawingDao().getBySite(deletedSiteId).first().isEmpty())
            assertNotNull(db.drawingDao().getById(keptDrawingId))
            assertFalse(drawingFile.exists())
        } finally { db.close() }
    }
}
