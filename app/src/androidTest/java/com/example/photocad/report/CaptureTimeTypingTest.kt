package com.example.photocad.report

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.AppDatabase
import com.example.photocad.data.AppThemeMode
import com.example.photocad.data.Drawing
import com.example.photocad.data.Photo
import com.example.photocad.data.Point
import com.example.photocad.ui.PointEditorScreen
import com.example.photocad.ui.theme.PhotoCADTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class CaptureTimeTypingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun digitsTypedOneByOneKeepTheirOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val point = runBlocking {
                val drawingId = db.drawingDao().insert(Drawing(name = "План", filePath = "missing-drawing", siteId = 1))
                val pointId = db.pointDao().insert(Point(drawingId = drawingId, x = 0.5f, y = 0.5f))
                db.photoDao().insert(Photo(pointId = pointId, filePath = "missing-photo", description = null))
                db.pointDao().getById(pointId)!!
            }
            compose.setContent { PhotoCADTheme(AppThemeMode.LIGHT) { PointEditorScreen(db, point, 1, true) {} } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Изменить дату и время").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Изменить дату и время").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Дата (ДД.ММ.ГГГГ)").fetchSemanticsNodes().isNotEmpty() }

            // One digit at a time, exactly as a person types; the separators must not reverse the order.
            "18102026".forEach { digit -> compose.onNodeWithText("Дата (ДД.ММ.ГГГГ)").performTextInput(digit.toString()) }
            compose.onNodeWithText("Дата (ДД.ММ.ГГГГ)").assertTextContains("18.10.2026")
            "1225".forEach { digit -> compose.onNodeWithText("Время (ЧЧ:ММ)").performTextInput(digit.toString()) }
            compose.onNodeWithText("Время (ЧЧ:ММ)").assertTextContains("12:25")
            // Storing the typed value as ISO is covered by CaptureTimeInputTest on the JVM.
        } finally { db.close() }
    }
}
