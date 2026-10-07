package com.example.photocad.report

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.AppDatabase
import com.example.photocad.data.AppThemeMode
import com.example.photocad.data.Drawing
import com.example.photocad.data.Site
import com.example.photocad.ui.report.ReportScreen
import com.example.photocad.ui.theme.PhotoCADTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class ReportTypeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportStartsWithCoverAndHasNoTypeSelection() {
        runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val siteId = db.siteDao().insert(Site(name = "Объект"))
            val drawingId = db.drawingDao().insert(Drawing(name = "План", filePath = "not-read-yet", siteId = siteId))
            compose.setContent { PhotoCADTheme(AppThemeMode.DARK) { ReportScreen(db, drawingId, onBack = {}) } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Титульный лист").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Выберите тип документа").assertDoesNotExist()
            compose.onNodeWithText("Продолжить").performClick()
            compose.onNodeWithText("Состав фотоотчёта").assertExists()
            compose.onNodeWithText("К предыдущему шагу").performClick()
            compose.onNodeWithText("Титульный лист").assertExists()
        } finally { db.close() }
        }
    }
}
