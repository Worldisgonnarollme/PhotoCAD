package com.example.photocad.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class DatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun realV1UpgradePreservesPhotosAndValidatesRoomSchema() = runBlocking {
        val name = "migration-${System.nanoTime()}.db"
        val photoFile = File.createTempFile("legacy", ".jpg", context.filesDir)
        val bitmap = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888)
        photoFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        val originalBytes = MessageDigest.getInstance("SHA-256").digest(photoFile.readBytes())
        try {
            // Exact v1 columns from the supplied entities. Original archive exported no Room schemas.
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE drawings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, filePath TEXT NOT NULL)")
                old.execSQL("CREATE TABLE points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, drawingId INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL)")
                old.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, pointId INTEGER NOT NULL, filePath TEXT NOT NULL)")
                old.execSQL("INSERT INTO drawings VALUES(1, 'Чертёж', ?)", arrayOf(photoFile.path))
                old.execSQL("INSERT INTO points VALUES(10, 1, 0.25, 0.75)")
                old.execSQL("INSERT INTO photos VALUES(100, 10, ?)", arrayOf(photoFile.path))
                old.version = 1
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(DatabaseMigrations.MIGRATION_1_2).build()
            try {
                val point = db.pointDao().getById(10)!! // opening invokes migration + generated Room schema validation
                assertEquals("", point.comment)
                assertEquals(0.25f, point.x)
                assertEquals(0.75f, point.y)
                val oldPhoto = db.photoDao().getByPoint(10).first().single()
                assertEquals(100L, oldPhoto.id)
                assertEquals(photoFile.path, oldPhoto.filePath)
                assertNull(oldPhoto.description)
                assertNotNull(db.drawingDao().getById(1))
                val decoded = BitmapFactory.decodeFile(oldPhoto.filePath)
                assertNotNull(decoded)
                decoded?.recycle()
                assertArrayEquals(originalBytes, MessageDigest.getInstance("SHA-256").digest(photoFile.readBytes()))
                db.pointDao().updateComment(10, "Первый комментарий")
                val newId = db.insertPhotoWithComment(10, photoFile.path)
                assertTrue(newId > 100)
                db.pointDao().updateComment(10, "Новый комментарий")
                val photos = db.photoDao().getByPoint(10).first()
                assertEquals("Первый комментарий", photos.first { it.id == newId }.description)
                assertEquals("Новый комментарий", ReportRules.description(photos.first { it.id == 100L }.description, db.pointDao().getById(10)!!.comment))
                db.photoDao().updateDescription(100, "Явно сохранено")
            } finally { db.close() }
            val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(DatabaseMigrations.MIGRATION_1_2).build()
            try { assertEquals("Явно сохранено", reopened.photoDao().getByPoint(10).first().first { it.id == 100L }.description) }
            finally { reopened.close() }
        } finally { context.deleteDatabase(name); photoFile.delete() }
    }

    @Test fun freshDatabaseFiltersByDrawing() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val one = db.drawingDao().insert(Drawing(name = "Один", filePath = "one"))
            val two = db.drawingDao().insert(Drawing(name = "Два", filePath = "two"))
            val p1 = db.pointDao().insert(Point(drawingId = one, x = 0f, y = 0f, comment = "Первый"))
            val p2 = db.pointDao().insert(Point(drawingId = two, x = 0f, y = 0f))
            db.insertPhotoWithComment(p2, "other")
            db.insertPhotoWithComment(p1, "selected")
            assertEquals(listOf("selected"), db.photoDao().getReportRows(one).map { it.filePath })
        } finally { db.close() }
    }
}
