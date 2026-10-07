package com.example.photocad.report

import android.app.Application
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.*
import com.example.photocad.ui.PointPhotosViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PhotoAttachmentTest {
    @Test fun retakeKeepsPhotoIdAndCaptionAndCancellationKeepsTheOriginal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        fun image(): File = File.createTempFile("retake", ".png", app.cacheDir).also { file ->
            val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
            try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
        val original = image()
        val replacement = image()
        val cancelled = image()
        val previousPreferences = runBlocking { UserPreferences.photoPreferencesFlow(app).first() }
        runBlocking { UserPreferences.savePhotoPreferences(app, PhotoPreferences(saveToGallery = false, compressPhotos = false)) }
        try {
            val (pointId, photoId) = runBlocking {
                val drawingId = db.drawingDao().insert(Drawing(name = "Чертёж", filePath = "drawing", siteId = 1))
                val point = db.pointDao().insert(Point(drawingId = drawingId, x = 0.5f, y = 0.5f))
                point to db.photoDao().insert(Photo(pointId = point, filePath = original.path, description = "Моя подпись"))
            }
            lateinit var model: PointPhotosViewModel
            instrumentation.runOnMainSync {
                model = PointPhotosViewModel(app, db, pointId)
                model.cameraResult(replacement.path, true, replacePhotoId = photoId)
            }
            runBlocking { model.state.first { it.completedCameraPath == replacement.path && !it.busy } }
            val photo = runBlocking { db.photoDao().getById(photoId)!! }
            assertEquals(replacement.path, photo.filePath)
            assertEquals("Моя подпись", photo.description)
            assertEquals(1, runBlocking { db.photoDao().getByPoint(pointId).first().size })
            assertFalse(original.exists())
            instrumentation.runOnMainSync { model.cameraResult(cancelled.path, false, replacePhotoId = photoId) }
            runBlocking { model.state.first { it.completedCameraPath == cancelled.path && !it.busy } }
            assertEquals(replacement.path, runBlocking { db.photoDao().getById(photoId)!!.filePath })
            assertFalse(cancelled.exists())
        } finally {
            db.close(); original.delete(); replacement.delete(); cancelled.delete()
            runBlocking { UserPreferences.savePhotoPreferences(app, previousPreferences) }
        }
    }

    @Test fun attachmentBelongsToRetainedModelNotDialogCoroutine() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val file = File.createTempFile("camera", ".png", app.cacheDir)
        val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val previousPreferences = runBlocking { UserPreferences.photoPreferencesFlow(app).first() }
        runBlocking { UserPreferences.savePhotoPreferences(app, PhotoPreferences(saveToGallery = false, compressPhotos = false)) }
        try {
            val pointId = runBlocking {
                val drawingId = db.drawingDao().insert(Drawing(name = "Чертёж", filePath = file.path, siteId = 1))
                db.pointDao().insert(Point(drawingId = drawingId, x = 0.25f, y = 0.75f, pageNumber = 2, comment = "Копия"))
            }
            lateinit var model: PointPhotosViewModel
            instrumentation.runOnMainSync {
                model = PointPhotosViewModel(app, db, pointId)
                model.cameraResult(file.path, true)
            }
            // No composable scope participates; completion can outlive the original dialog.
            runBlocking { model.state.first { it.completedCameraPath == file.path && !it.busy } }
            val photo = runBlocking { db.photoDao().getByPoint(pointId).first().single() }
            assertEquals(file.path, photo.filePath)
            assertEquals("Копия", photo.description)
            val reportRow = runBlocking {
                val drawingId = db.pointDao().getById(pointId)!!.drawingId
                db.photoDao().getReportRows(drawingId).single()
            }
            assertEquals(2, reportRow.pageNumber)
            assertEquals(0.25f, reportRow.x, 0f)
            assertEquals(0.75f, reportRow.y, 0f)
            runBlocking { db.pointDao().updateComment(pointId, "Изменённый комментарий") }
            assertEquals("Копия", runBlocking { db.photoDao().getByPoint(pointId).first().single().description })
            instrumentation.runOnMainSync { model.cameraResult(file.path, true) }
            assertEquals(1, runBlocking { db.photoDao().getByPoint(pointId).first().size })
        } finally {
            db.close()
            file.delete()
            runBlocking { UserPreferences.savePhotoPreferences(app, previousPreferences) }
        }
    }
}
