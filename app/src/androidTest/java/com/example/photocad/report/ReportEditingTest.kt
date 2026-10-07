package com.example.photocad.report

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.*
import com.example.photocad.ui.report.ReportScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReportEditingTest {
    @get:Rule val compose = createComposeRule()
    @Test fun twoPhotosOnOnePointAppearAsTwoAlbumPages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val drawingId = runBlocking {
                val siteId = db.siteDao().insert(Site(name = "Объект"))
                val drawing = db.drawingDao().insert(Drawing(name = "План", filePath = "drawing", siteId = siteId))
                val point = db.pointDao().insert(Point(drawingId = drawing, x = 0.5f, y = 0.5f))
                db.photoDao().insert(Photo(pointId = point, filePath = "photo-one", description = "Первое"))
                db.photoDao().insert(Photo(pointId = point, filePath = "photo-two", description = "Второе"))
                drawing
            }
            compose.setContent { MaterialTheme { ReportScreen(db, drawingId) {} } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Выберите тип документа").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Продолжить").performClick()
            compose.onNodeWithText("Продолжить").performClick()
            compose.onNodeWithText("Выбрано: 2 • Страниц: 4").assertExists()
        } finally { db.close() }
    }

    @Test fun temporaryTextIsNotSavedUntilExplicitActionAndSurvivesReportExitOnlyIfSaved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val ids = runBlocking {
                val drawing = db.drawingDao().insert(Drawing(name = "Проверка", filePath = "drawing", siteId = 1))
                val point = db.pointDao().insert(Point(drawingId = drawing, x = 0f, y = 0f, comment = "Исходное"))
                db.insertPhotoWithComment(point, "missing-thumbnail-is-allowed-in-editor")
                drawing to point
            }
            var exited = false
            compose.setContent { MaterialTheme { ReportScreen(db, ids.first) { exited = true } } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Выберите тип документа").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Продолжить").performClick()
            compose.onNodeWithText("Продолжить").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Описание для текущего PDF").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Описание для текущего PDF").performTextReplacement("Временно")
            assertEquals("Исходное", runBlocking { db.photoDao().getByPoint(ids.second).first().single().description })
            compose.onNodeWithText("Сохранить описание фотографии").performScrollTo().performClick()
            compose.waitUntil(10000) { runBlocking { db.photoDao().getByPoint(ids.second).first().single().description == "Временно" } }
            compose.onNodeWithText("Описание для текущего PDF").performTextReplacement("Несохранённое")
            compose.onNodeWithText("Назад", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Закрыть", useUnmergedTree = true).performClick()
            compose.runOnIdle { assertTrue(exited) }
            assertEquals("Временно", runBlocking { db.photoDao().getByPoint(ids.second).first().single().description })
        } finally { db.close() }
    }

    @Test fun missingCaptionIsVisibleBeforeGeneration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val drawingId = runBlocking {
                val drawing = db.drawingDao().insert(Drawing(name = "Без подписи", filePath = "drawing", siteId = 1))
                val point = db.pointDao().insert(Point(drawingId = drawing, x = 0.5f, y = 0.5f))
                db.photoDao().insert(Photo(pointId = point, filePath = "image", description = ""))
                drawing
            }
            compose.setContent { MaterialTheme { ReportScreen(db, drawingId) {} } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Выберите тип документа").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Продолжить").performClick()
            compose.onNodeWithText("Продолжить").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Описание отсутствует").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(compose.onAllNodesWithText("Описание отсутствует").fetchSemanticsNodes().isNotEmpty())
        } finally { db.close() }
    }
}
