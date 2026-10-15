package com.example.photocad.report

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.AppDatabase
import com.example.photocad.data.Drawing
import com.example.photocad.data.Point
import com.example.photocad.ui.DrawingScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DrawingPointBulkDeleteTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun drawingFile(pages: Int): File {
        val file = File.createTempFile("bulk-drawing", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            repeat(pages) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(400, 300, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        return file
    }

    @Test fun singlePageDrawingAsksOnceAndRemovesEveryPoint() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val file = drawingFile(1)
        try {
            val drawingId = runBlocking {
                val id = db.drawingDao().insert(Drawing(name = "План", filePath = file.path, siteId = 1))
                db.pointDao().insert(Point(drawingId = id, x = 0.2f, y = 0.2f))
                db.pointDao().insert(Point(drawingId = id, x = 0.6f, y = 0.6f))
                id
            }
            compose.setContent { MaterialTheme { DrawingScreen(db, drawingId, {}, {}) } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Удалить точки").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Удалить точки").performClick()
            // One page: no scope to choose, so the confirmation comes straight away.
            compose.onNodeWithText("Удалить точки на текущей странице").assertDoesNotExist()
            compose.onNodeWithText("Вы точно хотите удалить все точки? Вместе с ними удалятся их фотографии. Это действие нельзя будет отменить.")
                .assertExists()
            compose.onNodeWithText("Удалить").performClick()
            compose.waitUntil(10000) { runBlocking { db.pointDao().getActiveByDrawing(drawingId).isEmpty() } }
        } finally { db.close(); file.delete() }
    }

    @Test fun cancellingTheConfirmationKeepsThePoints() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val file = drawingFile(1)
        try {
            val drawingId = runBlocking {
                val id = db.drawingDao().insert(Drawing(name = "План", filePath = file.path, siteId = 1))
                db.pointDao().insert(Point(drawingId = id, x = 0.2f, y = 0.2f))
                id
            }
            compose.setContent { MaterialTheme { DrawingScreen(db, drawingId, {}, {}) } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Удалить точки").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Удалить точки").performClick()
            compose.onNodeWithText("Отмена").performClick()
            compose.waitForIdle()
            assertTrue(runBlocking { db.pointDao().getActiveByDrawing(drawingId).size } == 1)
        } finally { db.close(); file.delete() }
    }

    @Test fun multiPageDrawingOffersScopeAndCurrentPageSparesTheOtherSheet() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val file = drawingFile(2)
        try {
            val drawingId = runBlocking {
                val id = db.drawingDao().insert(Drawing(name = "План", filePath = file.path, siteId = 1))
                db.pointDao().insert(Point(drawingId = id, x = 0.2f, y = 0.2f, pageNumber = 1))
                db.pointDao().insert(Point(drawingId = id, x = 0.4f, y = 0.4f, pageNumber = 1))
                db.pointDao().insert(Point(drawingId = id, x = 0.6f, y = 0.6f, pageNumber = 2))
                id
            }
            compose.setContent { MaterialTheme { DrawingScreen(db, drawingId, {}, {}) } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Страница 1 из 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Удалить точки").performClick()
            compose.onNodeWithText("Удалить точки на всех страницах").assertExists()
            compose.onNodeWithText("Удалить точки на текущей странице").performClick()
            compose.waitUntil(10000) { runBlocking { db.pointDao().getActiveByDrawing(drawingId).size } == 1 }
            val left = runBlocking { db.pointDao().getActiveByDrawing(drawingId) }
            assertEquals(2, left.single().pageNumber)
        } finally { db.close(); file.delete() }
    }
}
