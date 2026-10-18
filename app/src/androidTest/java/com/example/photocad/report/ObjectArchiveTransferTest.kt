package com.example.photocad.report

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ObjectArchiveTransferTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun imageFile(extension: String): File {
        val file = File.createTempFile("archive-test-", ".$extension", context.filesDir)
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        return file
    }

    @Test fun transfersMultipleDrawingsAndPhotosIntoDistinctSite() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val drawingFile1 = imageFile("png")
        val drawingFile2 = imageFile("png")
        val photoFile1 = imageFile("png")
        val photoFile2 = imageFile("png")
        val archive = File.createTempFile("object-transfer-", ".photocad", context.cacheDir)
        try {
            val site = db.siteDao().insert(Site(name = "Корпус", address = "Москва", description = "Осмотр"))
            db.siteDao().saveReportDetails(SiteReportDetails(siteId = site, organizationName = "Организация", inn = "123"))
            val drawing1 = db.drawingDao().insert(Drawing(name = "План 1", filePath = drawingFile1.path, siteId = site, description = "Первый"))
            val drawing2 = db.drawingDao().insert(Drawing(name = "План 2", filePath = drawingFile2.path, siteId = site, description = "Второй"))
            val point1 = db.pointDao().insert(Point(drawingId = drawing1, x = 0.25f, y = 0.75f, pageNumber = 2,
                colorIndex = 2, isFixed = true, displayNumber = 4, comment = "Узел"))
            val point2 = db.pointDao().insert(Point(drawingId = drawing2, x = 0.7f, y = 0.3f, displayNumber = 1))
            db.photoDao().insert(Photo(pointId = point1, filePath = photoFile1.path, description = "Дефект",
                capturedAtLocal = "2022-03-04T11:22:33", capturedOffsetMinutes = 180, captureMetadataChecked = true))
            db.photoDao().insert(Photo(pointId = point2, filePath = photoFile2.path, description = "Обзор",
                capturedAtLocal = null, captureMetadataChecked = true))

            exportObjectArchive(context, db, site, Uri.fromFile(archive))
            val prepared = prepareObjectArchive(context, Uri.fromFile(archive))
            assertEquals(2, prepared.preview.drawingCount)
            assertEquals(2, prepared.preview.pointCount)
            assertEquals(2, prepared.preview.photoCount)
            val importedId = prepared.import(context, db)
            assertNotEquals(site, importedId)
            assertEquals("Корпус (импорт 2)", db.siteDao().getById(importedId)?.name)
            assertEquals("Москва", db.siteDao().getById(importedId)?.address)
            assertEquals("123", db.siteDao().getReportDetails(importedId)?.inn)
            val drawings = db.drawingDao().getAllBySite(importedId)
            assertEquals(2, drawings.size)
            assertEquals(listOf("Первый", "Второй"), drawings.map { it.description })
            assertTrue(drawings.all { File(it.filePath).isFile && it.filePath != drawingFile1.path && it.filePath != drawingFile2.path })
            val points = drawings.flatMap { db.pointDao().getAllByDrawing(it.id) }
            assertEquals(listOf(4, 1), points.map { it.displayNumber })
            assertEquals(2, points[0].pageNumber)
            assertEquals(2, points[0].colorIndex)
            assertTrue(points[0].isFixed)
            assertEquals("Узел", points[0].comment)
            val photos = points.flatMap { db.photoDao().getAllByPoint(it.id) }
            assertEquals(listOf("Дефект", "Обзор"), photos.map { it.description })
            assertEquals("2022-03-04T11:22:33", photos[0].capturedAtLocal)
            assertEquals(180, photos[0].capturedOffsetMinutes)
            assertNull(photos[1].capturedAtLocal)
            assertTrue(photos.all { File(it.filePath).isFile && it.captureMetadataChecked })
            db.deleteSite(importedId)
        } finally {
            db.close()
            listOf(drawingFile1, drawingFile2, photoFile1, photoFile2, archive).forEach(File::delete)
        }
    }

    @Test fun rejectsIncompleteArchiveAndCleansStaging() = runBlocking {
        fun stagingCount() = context.cacheDir.listFiles()?.count { it.name.startsWith("photocad-import-") } ?: 0
        val before = stagingCount()
        val archive = File.createTempFile("incomplete-", ".photocad", context.cacheDir)
        val manifest = ArchiveManifest(1, ArchiveSite("X", "", ""), null,
            listOf(ArchiveDrawing(1, "План", "", "attachments/drawing-1.png")), emptyList(), emptyList(),
            listOf(ArchiveFile("attachments/drawing-1.png", 4, "a".repeat(64))))
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toJson().toByteArray())
                zip.closeEntry()
            }
            assertTrue(runCatching { prepareObjectArchive(context, Uri.fromFile(archive)) }.isFailure)
            assertEquals(before, stagingCount())
        } finally { archive.delete() }
    }

    @Test fun corruptZipHasUnderstandableError() = runBlocking {
        val archive = File.createTempFile("corrupt-", ".photocad", context.cacheDir)
        try {
            archive.writeText("broken")
            val failure = runCatching { prepareObjectArchive(context, Uri.fromFile(archive)) }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(failure!!.message.orEmpty().contains("Архив"))
        } finally { archive.delete() }
    }
}
