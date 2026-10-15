package com.example.photocad.report

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.*
import com.example.photocad.ui.report.ReportViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReportArchiveWorkflowTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Test fun partialPointStaysActiveAndCompletePointArchivesOnlyAfterSave() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val drawingFile = drawing()
        val photoFile = photo()
        val destination = File.createTempFile("saved-album", ".pdf", app.cacheDir)
        try {
            val drawingId = db.drawingDao().insert(Drawing(name = "План", filePath = drawingFile.path, siteId = 1))
            val partial = db.pointDao().insert(Point(drawingId = drawingId, x = 0.3f, y = 0.4f, colorIndex = 1))
            val complete = db.pointDao().insert(Point(drawingId = drawingId, x = 0.7f, y = 0.5f, colorIndex = 2))
            val partialPhotos = List(3) { db.photoDao().insert(Photo(pointId = partial, filePath = photoFile.path, description = "Фото $it")) }
            db.photoDao().insert(Photo(pointId = complete, filePath = photoFile.path, description = "Все фото"))

            val model = ReportViewModel(app, db, drawingId)
            model.open()
            withTimeout(10000) { model.state.first { it.initialized } }
            model.next(); model.next()
            model.select(partialPhotos.last(), false)
            model.next()
            model.generate()
            withTimeout(30000) { model.state.first { it.generated != null && !it.busy } }
            assertFalse(model.state.value.saved)
            model.archiveUsedPoints()
            assertFalse(db.pointDao().getById(complete)!!.isArchived)

            model.savePdf(Uri.fromFile(destination))
            withTimeout(30000) { model.state.first { it.saved && !it.busy } }
            assertTrue(destination.length() > 0)
            model.archiveUsedPoints()
            withTimeout(10000) { model.state.first { it.archiveDecisionDone && !it.busy } }
            assertFalse(db.pointDao().getById(partial)!!.isArchived)
            assertTrue(db.pointDao().getById(complete)!!.isArchived)
            assertEquals(2, db.pointDao().getById(complete)!!.colorIndex)
            assertEquals(3, db.photoDao().getByPoint(partial).first().size)
        } finally { db.close(); drawingFile.delete(); photoFile.delete(); destination.delete() }
    }

    @Test fun failedSaveCannotArchiveThePoint() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val drawingFile = drawing()
        val photoFile = photo()
        try {
            val drawingId = db.drawingDao().insert(Drawing(name = "План", filePath = drawingFile.path, siteId = 1))
            val point = db.pointDao().insert(Point(drawingId = drawingId, x = 0.5f, y = 0.5f))
            db.photoDao().insert(Photo(pointId = point, filePath = photoFile.path, description = "Фото"))
            val model = ReportViewModel(app, db, drawingId)
            model.open()
            withTimeout(10000) { model.state.first { it.initialized } }
            model.next(); model.next(); model.next(); model.generate()
            withTimeout(30000) { model.state.first { it.generated != null && !it.busy } }
            model.savePdf(Uri.parse("content://missing.photocad.provider/unwritable"))
            withTimeout(10000) { model.state.first { it.message?.startsWith("Не удалось сохранить PDF") == true && !it.busy } }
            model.archiveUsedPoints()
            assertFalse(db.pointDao().getById(point)!!.isArchived)
        } finally { db.close(); drawingFile.delete(); photoFile.delete() }
    }

    @Test fun checkedDeleteRemovesOnlyFullyIncludedPointsAfterSave() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val drawingFile = drawing()
        val photoFile = photo()
        val destination = File.createTempFile("saved-album", ".pdf", app.cacheDir)
        try {
            val drawingId = db.drawingDao().insert(Drawing(name = "План", filePath = drawingFile.path, siteId = 1))
            val partial = db.pointDao().insert(Point(drawingId = drawingId, x = 0.3f, y = 0.4f))
            val complete = db.pointDao().insert(Point(drawingId = drawingId, x = 0.7f, y = 0.5f))
            val partialPhotos = List(2) { db.photoDao().insert(Photo(pointId = partial, filePath = photoFile.path, description = "Фото $it")) }
            db.photoDao().insert(Photo(pointId = complete, filePath = photoFile.path, description = "Все фото"))

            val model = ReportViewModel(app, db, drawingId)
            model.open()
            withTimeout(10000) { model.state.first { it.initialized } }
            model.next(); model.next()
            model.select(partialPhotos.last(), false)
            model.next()
            model.setDeletePointsAfterSave(true)
            assertTrue(model.state.value.deletePointsAfterSave)
            model.generate()
            withTimeout(30000) { model.state.first { it.generated != null && !it.busy } }
            // Nothing is removed until the file is actually written.
            assertNotNull(db.pointDao().getById(complete))

            model.savePdf(Uri.fromFile(destination))
            withTimeout(30000) { model.state.first { it.saved && !it.busy } }
            assertTrue(destination.length() > 0)
            assertNull(db.pointDao().getById(complete))
            assertTrue(db.photoDao().getByPoint(complete).first().isEmpty())
            assertNotNull(db.pointDao().getById(partial))
            assertEquals(2, db.photoDao().getByPoint(partial).first().size)
            // The archive prompt is pointless once the points are gone.
            assertTrue(model.state.value.archiveDecisionDone)
        } finally { db.close(); drawingFile.delete(); photoFile.delete(); destination.delete() }
    }

    @Test fun checkedDeleteKeepsPointsWhenSaveFails() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val drawingFile = drawing()
        val photoFile = photo()
        try {
            val drawingId = db.drawingDao().insert(Drawing(name = "План", filePath = drawingFile.path, siteId = 1))
            val point = db.pointDao().insert(Point(drawingId = drawingId, x = 0.5f, y = 0.5f))
            db.photoDao().insert(Photo(pointId = point, filePath = photoFile.path, description = "Фото"))
            val model = ReportViewModel(app, db, drawingId)
            model.open()
            withTimeout(10000) { model.state.first { it.initialized } }
            model.next(); model.next(); model.next()
            model.setDeletePointsAfterSave(true)
            model.generate()
            withTimeout(30000) { model.state.first { it.generated != null && !it.busy } }
            model.savePdf(Uri.parse("content://missing.photocad.provider/unwritable"))
            withTimeout(10000) { model.state.first { it.message?.startsWith("Не удалось сохранить PDF") == true && !it.busy } }
            assertNotNull(db.pointDao().getById(point))
            assertEquals(1, db.photoDao().getByPoint(point).first().size)
        } finally { db.close(); drawingFile.delete(); photoFile.delete() }
    }

    private fun drawing(): File {
        val file = File.createTempFile("album-drawing", ".pdf", app.cacheDir)
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 400, 1).create())
            page.canvas.drawColor(Color.WHITE)
            pdf.finishPage(page)
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        return file
    }

    private fun photo(): File {
        val file = File.createTempFile("album-photo", ".png", app.cacheDir)
        val bitmap = Bitmap.createBitmap(160, 200, Bitmap.Config.ARGB_8888)
        try { bitmap.eraseColor(Color.BLUE); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        return file
    }
}
