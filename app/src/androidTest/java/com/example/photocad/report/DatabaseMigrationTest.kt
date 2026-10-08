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
    @Test fun versionSixUpgradePreservesColorAndPhotosAndAddsActiveState() = runBlocking {
        val name = "migration-v6-${System.nanoTime()}.db"
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE sites (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '')")
                old.execSQL("CREATE TABLE drawings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, filePath TEXT NOT NULL, siteId INTEGER NOT NULL, description TEXT NOT NULL DEFAULT '')")
                old.execSQL("CREATE TABLE points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, drawingId INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, pageNumber INTEGER NOT NULL DEFAULT 1, comment TEXT NOT NULL DEFAULT '', colorIndex INTEGER NOT NULL DEFAULT 0)")
                old.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, pointId INTEGER NOT NULL, filePath TEXT NOT NULL, description TEXT)")
                old.execSQL("CREATE INDEX index_drawings_siteId ON drawings(siteId)")
                old.execSQL("CREATE INDEX index_points_drawingId ON points(drawingId)")
                old.execSQL("CREATE INDEX index_photos_pointId ON photos(pointId)")
                old.execSQL("INSERT INTO sites VALUES(1, 'Объект', '', '')")
                old.execSQL("INSERT INTO drawings VALUES(2, 'План', '/drawing.pdf', 1, '')")
                old.execSQL("INSERT INTO points VALUES(3, 2, 0.3, 0.7, 2, 'Комментарий', 1)")
                old.execSQL("INSERT INTO photos VALUES(4, 3, '/photo.jpg', 'Описание')")
                old.version = 6
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(DatabaseMigrations.MIGRATION_6_7, DatabaseMigrations.MIGRATION_7_8, DatabaseMigrations.MIGRATION_8_9).build()
            try {
                val point = db.pointDao().getById(3)!!
                assertEquals(1, point.colorIndex)
                assertEquals(2, point.pageNumber)
                assertEquals("Комментарий", point.comment)
                assertFalse(point.isArchived)
                assertEquals("Описание", db.photoDao().getByPoint(3).first().single().description)
                db.pointDao().setArchived(3, true)
                assertTrue(db.pointDao().getById(3)!!.isArchived)
                assertTrue(db.pointDao().getByDrawingPage(2, 2).first().isEmpty())
                assertEquals(1, db.pointDao().getByDrawing(2).first().size)
                assertNull(db.siteDao().getReportDetails(1))
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
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
                .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4,
                    DatabaseMigrations.MIGRATION_4_5, DatabaseMigrations.MIGRATION_5_6,
                    DatabaseMigrations.MIGRATION_6_7, DatabaseMigrations.MIGRATION_7_8, DatabaseMigrations.MIGRATION_8_9).build()
            try {
                val point = db.pointDao().getById(10)!! // opening invokes migration + generated Room schema validation
                assertEquals("", point.comment)
                assertEquals(0.25f, point.x)
                assertEquals(0.75f, point.y)
                assertEquals(0, point.colorIndex)
                assertEquals(1, point.pageNumber)
                val oldPhoto = db.photoDao().getByPoint(10).first().single()
                assertEquals(100L, oldPhoto.id)
                assertEquals(photoFile.path, oldPhoto.filePath)
                assertNull(oldPhoto.description)
                val migratedDrawing = db.drawingDao().getById(1)!!
                assertEquals("", migratedDrawing.description)
                assertEquals(1L, migratedDrawing.siteId)
                assertEquals("Мои чертежи", db.siteDao().getById(1)!!.name)
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
                .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4,
                    DatabaseMigrations.MIGRATION_4_5, DatabaseMigrations.MIGRATION_5_6,
                    DatabaseMigrations.MIGRATION_6_7, DatabaseMigrations.MIGRATION_7_8, DatabaseMigrations.MIGRATION_8_9).build()
            try { assertEquals("Явно сохранено", reopened.photoDao().getByPoint(10).first().first { it.id == 100L }.description) }
            finally { reopened.close() }
        } finally { context.deleteDatabase(name); photoFile.delete() }
    }

    @Test fun versionFiveUpgradeAddsPageOneWithoutChangingPointData() = runBlocking {
        val name = "migration-v5-${System.nanoTime()}.db"
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE sites (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '')")
                old.execSQL("CREATE TABLE drawings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, filePath TEXT NOT NULL, siteId INTEGER NOT NULL, description TEXT NOT NULL DEFAULT '')")
                old.execSQL("CREATE TABLE points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, drawingId INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, comment TEXT NOT NULL DEFAULT '', colorIndex INTEGER NOT NULL DEFAULT 0)")
                old.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, pointId INTEGER NOT NULL, filePath TEXT NOT NULL, description TEXT)")
                old.execSQL("CREATE INDEX index_drawings_siteId ON drawings(siteId)")
                old.execSQL("CREATE INDEX index_points_drawingId ON points(drawingId)")
                old.execSQL("CREATE INDEX index_photos_pointId ON photos(pointId)")
                old.execSQL("INSERT INTO sites VALUES(1, 'Объект', '', '')")
                old.execSQL("INSERT INTO drawings VALUES(2, 'План', '/drawing.pdf', 1, '')")
                old.execSQL("INSERT INTO points VALUES(3, 2, 0.3, 0.7, 'Сохранённый комментарий', 4)")
                old.execSQL("INSERT INTO photos VALUES(4, 3, '/photo.jpg', 'Описание')")
                old.version = 5
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(DatabaseMigrations.MIGRATION_5_6, DatabaseMigrations.MIGRATION_6_7,
                    DatabaseMigrations.MIGRATION_7_8, DatabaseMigrations.MIGRATION_8_9).build()
            try {
                val point = db.pointDao().getById(3)!!
                assertEquals(2L, point.drawingId)
                assertEquals(0.3f, point.x)
                assertEquals(0.7f, point.y)
                assertEquals("Сохранённый комментарий", point.comment)
                assertEquals(4, point.colorIndex)
                assertEquals(1, point.pageNumber)
                assertEquals("/photo.jpg", db.photoDao().getByPoint(3).first().single().filePath)
                assertEquals("Описание", db.photoDao().getByPoint(3).first().single().description)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun versionSevenUpgradeAddsOptionalCoverDetailsWithoutChangingExistingSite() = runBlocking {
        val name = "migration-v7-${System.nanoTime()}.db"
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE sites (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '')")
                old.execSQL("CREATE TABLE drawings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, filePath TEXT NOT NULL, siteId INTEGER NOT NULL, description TEXT NOT NULL DEFAULT '')")
                old.execSQL("CREATE TABLE points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, drawingId INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, pageNumber INTEGER NOT NULL DEFAULT 1, comment TEXT NOT NULL DEFAULT '', colorIndex INTEGER NOT NULL DEFAULT 0, isArchived INTEGER NOT NULL DEFAULT 0)")
                old.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, pointId INTEGER NOT NULL, filePath TEXT NOT NULL, description TEXT)")
                old.execSQL("CREATE INDEX index_drawings_siteId ON drawings(siteId)")
                old.execSQL("CREATE INDEX index_points_drawingId ON points(drawingId)")
                old.execSQL("CREATE INDEX index_photos_pointId ON photos(pointId)")
                old.execSQL("INSERT INTO sites VALUES(1, 'Старый объект', 'Старый адрес', 'Описание')")
                old.execSQL("INSERT INTO drawings VALUES(2, 'План', '/drawing.pdf', 1, '')")
                old.execSQL("INSERT INTO points VALUES(3, 2, 0.3, 0.7, 1, 'Комментарий', 1, 0)")
                old.execSQL("INSERT INTO photos VALUES(4, 3, '/photo.jpg', 'Подпись')")
                old.version = 7
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(DatabaseMigrations.MIGRATION_7_8, DatabaseMigrations.MIGRATION_8_9).build()
            try {
                assertEquals("Старый объект", db.siteDao().getById(1)!!.name)
                assertNull(db.siteDao().getReportDetails(1))
                assertEquals(1, db.pointDao().getById(3)!!.colorIndex)
                assertEquals("Подпись", db.photoDao().getByPoint(3).first().single().description)
                db.siteDao().saveReportDetails(SiteReportDetails(siteId = 1, organizationName = "Организация", inn = "123"))
                assertEquals("123", db.siteDao().getReportDetails(1)!!.inn)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun versionEightUpgradeLeavesExistingPointsMovable() = runBlocking {
        val name = "migration-v8-${System.nanoTime()}.db"
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE sites (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '')")
                old.execSQL("CREATE TABLE drawings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, filePath TEXT NOT NULL, siteId INTEGER NOT NULL, description TEXT NOT NULL DEFAULT '')")
                old.execSQL("CREATE TABLE points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, drawingId INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, pageNumber INTEGER NOT NULL DEFAULT 1, comment TEXT NOT NULL DEFAULT '', colorIndex INTEGER NOT NULL DEFAULT 0, isArchived INTEGER NOT NULL DEFAULT 0)")
                old.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, pointId INTEGER NOT NULL, filePath TEXT NOT NULL, description TEXT)")
                old.execSQL("""CREATE TABLE site_report_details (
                    siteId INTEGER NOT NULL PRIMARY KEY,
                    organizationName TEXT NOT NULL DEFAULT '', organizationAddress TEXT NOT NULL DEFAULT '',
                    phone TEXT NOT NULL DEFAULT '', email TEXT NOT NULL DEFAULT '',
                    inn TEXT NOT NULL DEFAULT '', kpp TEXT NOT NULL DEFAULT '', ogrn TEXT NOT NULL DEFAULT '',
                    customer TEXT NOT NULL DEFAULT '', city TEXT NOT NULL DEFAULT '',
                    year TEXT NOT NULL DEFAULT '', albumNumber TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY(siteId) REFERENCES sites(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
                old.execSQL("CREATE INDEX index_drawings_siteId ON drawings(siteId)")
                old.execSQL("CREATE INDEX index_points_drawingId ON points(drawingId)")
                old.execSQL("CREATE INDEX index_photos_pointId ON photos(pointId)")
                old.execSQL("INSERT INTO sites VALUES(1, 'Объект', '', '')")
                old.execSQL("INSERT INTO drawings VALUES(2, 'План', '/drawing.pdf', 1, '')")
                old.execSQL("INSERT INTO points VALUES(3, 2, 0.3, 0.7, 1, 'Комментарий', 1, 0)")
                old.version = 8
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(DatabaseMigrations.MIGRATION_8_9).build()
            try {
                val migrated = db.pointDao().getById(3)!!
                assertEquals(0.3f, migrated.x, 0.0001f)
                assertEquals(0.7f, migrated.y, 0.0001f)
                assertEquals("Комментарий", migrated.comment)
                assertFalse(migrated.isFixed)

                db.pointDao().setFixed(3, true)
                assertTrue(db.pointDao().getById(3)!!.isFixed)
                db.pointDao().setFixed(3, false)
                assertFalse(db.pointDao().getById(3)!!.isFixed)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun freshDatabaseFiltersByDrawing() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val one = db.drawingDao().insert(Drawing(name = "Один", filePath = "one", siteId = 1))
            val two = db.drawingDao().insert(Drawing(name = "Два", filePath = "two", siteId = 1))
            val p1 = db.pointDao().insert(Point(drawingId = one, x = 0f, y = 0f, comment = "Первый"))
            val p2 = db.pointDao().insert(Point(drawingId = two, x = 0f, y = 0f))
            val p3 = db.pointDao().insert(Point(drawingId = one, x = 0.5f, y = 0.5f, pageNumber = 2))
            db.insertPhotoWithComment(p2, "other")
            db.insertPhotoWithComment(p1, "selected")
            assertEquals(listOf("selected"), db.photoDao().getReportRows(one).map { it.filePath })
            assertEquals(listOf(p1), db.pointDao().getByDrawingPage(one, 1).first().map { it.id })
            assertEquals(listOf(p3), db.pointDao().getByDrawingPage(one, 2).first().map { it.id })
        } finally { db.close() }
    }
}
