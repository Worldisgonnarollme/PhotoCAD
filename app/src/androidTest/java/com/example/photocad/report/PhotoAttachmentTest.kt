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
                db.pointDao().insert(Point(drawingId = drawingId, x = 0f, y = 0f, comment = "Копия"))
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
