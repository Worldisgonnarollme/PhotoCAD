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

    @Test fun deletingOnePhotoKeepsThePointAndItsOtherPhoto() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val sharedFile = File.createTempFile("shared-photo", ".jpg", context.filesDir)
        try {
            val siteId = db.siteDao().insert(Site(name = "Объект"))
            val drawingId = db.drawingDao().insert(Drawing(name = "Чертёж", filePath = "drawing", siteId = siteId))
            val pointId = db.pointDao().insert(Point(drawingId = drawingId, x = 0.5f, y = 0.5f))
            val firstId = db.photoDao().insert(Photo(pointId = pointId, filePath = sharedFile.path, description = "Первое"))
            val secondId = db.photoDao().insert(Photo(pointId = pointId, filePath = sharedFile.path, description = "Второе"))
            db.deletePhoto(firstId)
            assertNotNull(db.pointDao().getById(pointId))
            assertNull(db.photoDao().getById(firstId))
            assertEquals("Второе", db.photoDao().getById(secondId)!!.description)
            assertTrue(sharedFile.exists())
            db.deletePhoto(secondId)
            assertFalse(sharedFile.exists())
        } finally { db.close(); sharedFile.delete() }
    }

    @Test fun deletePointKeepsPhotoFileWhileAnotherPointStillReferencesIt() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val siteId = db.siteDao().insert(Site(name = "Объект"))
            val drawingId = db.drawingDao().insert(Drawing(name = "Чертёж", filePath = "drawing.jpg", siteId = siteId))
            val keptPointId = db.pointDao().insert(Point(drawingId = drawingId, x = 0f, y = 0f))
            val deletedPointId = db.pointDao().insert(Point(drawingId = drawingId, x = 0f, y = 0f))
            val photoFile = File.createTempFile("photo", ".jpg", context.filesDir)
            db.photoDao().insert(Photo(pointId = deletedPointId, filePath = photoFile.path))
            db.photoDao().insert(Photo(pointId = keptPointId, filePath = photoFile.path))

            db.deletePoint(deletedPointId)

            assertNull(db.pointDao().getById(deletedPointId))
            assertNotNull(db.pointDao().getById(keptPointId))
            assertTrue(db.photoDao().getByPoint(deletedPointId).first().isEmpty())
            assertTrue(photoFile.exists())
            db.deletePoint(keptPointId)
            assertFalse(photoFile.exists())
        } finally { db.close() }
    }

    @Test fun deleteDrawingKeepsSharedDrawingFileUntilLastDrawingIsDeleted() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val siteId = db.siteDao().insert(Site(name = "Объект"))
            val drawingFile = File.createTempFile("drawing", ".jpg", context.filesDir)
            val deletedDrawingId = db.drawingDao().insert(Drawing(name = "Удаляемый", filePath = drawingFile.path, siteId = siteId))
            val keptDrawingId = db.drawingDao().insert(Drawing(name = "Оставшийся", filePath = drawingFile.path, siteId = siteId))
            val pointId = db.pointDao().insert(Point(drawingId = deletedDrawingId, x = 0f, y = 0f))
            val photoFile = File.createTempFile("photo", ".jpg", context.filesDir)
            db.photoDao().insert(Photo(pointId = pointId, filePath = photoFile.path))

            db.deleteDrawing(deletedDrawingId)

            assertNull(db.drawingDao().getById(deletedDrawingId))
            assertNotNull(db.drawingDao().getById(keptDrawingId))
            assertTrue(db.pointDao().getByDrawing(deletedDrawingId).first().isEmpty())
            assertTrue(drawingFile.exists())
            assertFalse(photoFile.exists())
            db.deleteDrawing(keptDrawingId)
            assertFalse(drawingFile.exists())
        } finally { db.close() }
    }

    @Test fun deleteSiteRemovesAllItsDrawingsButKeepsOtherSite() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val deletedSiteId = db.siteDao().insert(Site(name = "Удаляемый объект"))
            val keptSiteId = db.siteDao().insert(Site(name = "Оставшийся объект"))
            db.siteDao().saveReportDetails(SiteReportDetails(siteId = deletedSiteId, organizationName = "Организация"))
            val drawingFile = File.createTempFile("drawing", ".jpg", context.filesDir)
            db.drawingDao().insert(Drawing(name = "Чертёж", filePath = drawingFile.path, siteId = deletedSiteId))
            val keptDrawingId = db.drawingDao().insert(Drawing(name = "Чужой чертёж", filePath = drawingFile.path, siteId = keptSiteId))

            db.deleteSite(deletedSiteId)

            assertNull(db.siteDao().getById(deletedSiteId))
            assertNull(db.siteDao().getReportDetails(deletedSiteId))
            assertNotNull(db.siteDao().getById(keptSiteId))
            assertTrue(db.drawingDao().getBySite(deletedSiteId).first().isEmpty())
            assertNotNull(db.drawingDao().getById(keptDrawingId))
            assertTrue(drawingFile.exists())
            db.deleteSite(keptSiteId)
            assertFalse(drawingFile.exists())
        } finally { db.close() }
    }
}
