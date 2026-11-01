package com.example.photocad.report

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.*
import com.example.photocad.MainApp
import com.example.photocad.ui.PointEditorScreen
import com.example.photocad.ui.report.ReportViewModel
import com.example.photocad.ui.profile.ProfileViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PointDescriptionPersistenceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private inner class Fixture(twoPageDrawing: Boolean = false) : AutoCloseable {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val image = File.createTempFile("caption", ".png", app.cacheDir).also { file ->
            val bitmap = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.GREEN)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
        val drawingFile = if (twoPageDrawing) File.createTempFile("caption-plan", ".pdf", app.cacheDir).also { file ->
            val document = PdfDocument()
            try {
                for (number in 1..2) {
                    val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, number).create())
                    page.canvas.drawColor(Color.WHITE)
                    document.finishPage(page)
                }
                file.outputStream().use(document::writeTo)
            } finally { document.close() }
        } else image
        val point = runBlocking {
            val siteId = db.siteDao().insert(Site(name = "Проверка описаний"))
            val drawingId = db.drawingDao().insert(Drawing(name = "План", filePath = drawingFile.path, siteId = siteId))
            val pointId = db.pointDao().insert(Point(drawingId = drawingId, x = 0.5f, y = 0.5f,
                pageNumber = if (twoPageDrawing) 2 else 1, displayNumber = 1))
            db.photoDao().insert(Photo(pointId = pointId, filePath = image.path, description = null))
            db.pointDao().getById(pointId)!!
        }
        val photoId = runBlocking { db.photoDao().getAllByPoint(point.id).single().id }
        fun description() = runBlocking { db.photoDao().getById(photoId)!!.description }
        override fun close() {
            // Stop Compose/Room collectors before closing the in-memory database.
            compose.activityRule.scenario.close()
            db.close()
            drawingFile.delete()
            image.delete()
        }
    }

    private fun waitForEditor() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Описание фотографии").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun restoredDescriptionIsSavedAndLoadedIntoTheReport() {
        Fixture().use { f ->
            var left = false
            val restoration = StateRestorationTester(compose)
            restoration.setContent { MaterialTheme { PointEditorScreen(f.db, f.point, 1, true) { left = true } } }
            waitForEditor()
            val caption = "Описание после восстановления экрана"
            compose.onNodeWithText("Описание фотографии").performTextReplacement(caption)
            restoration.emulateSavedInstanceStateRestore()
            waitForEditor()
            compose.onNodeWithText("Описание фотографии").assertTextContains(caption)
            compose.onNodeWithText("Сохранить").performClick()
            compose.waitUntil(10000) { left }
            assertEquals(caption, f.description())

            lateinit var report: ReportViewModel
            compose.runOnIdle { report = ReportViewModel(f.app, f.db, f.point.drawingId); report.open() }
            compose.waitUntil(10000) { report.state.value.initialized }
            assertEquals(caption, reportInput(report.state.value.photos).single().description)
        }
    }

    @Test fun changingTabsKeepsTheVisibleDraft() {
        Fixture().use { f ->
            var left = false
            compose.setContent { MaterialTheme { PointEditorScreen(f.db, f.point, 1, true) { left = true } } }
            waitForEditor()
            val caption = "Описание перед переключением вкладок"
            compose.onNodeWithText("Описание фотографии").performTextReplacement(caption)
            compose.onNodeWithText("Точка").performClick()
            compose.onNodeWithText("Фото", substring = false).performClick()
            compose.onNodeWithText("Описание фотографии").assertTextContains(caption)
            compose.onNodeWithText("Сохранить").performClick()
            compose.waitUntil(10000) { left }
            assertEquals(caption, f.description())
        }
    }

    @Test fun systemBackSavesBeforeLeavingThePoint() {
        Fixture().use { f ->
            var left = false
            var fallbackCalled = false
            compose.runOnIdle {
                compose.activity.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() { fallbackCalled = true }
                })
            }
            compose.setContent {
                MaterialTheme { PointEditorScreen(f.db, f.point, 1, true) { left = true } }
            }
            waitForEditor()
            val caption = "Описание перед системной кнопкой назад"
            compose.onNodeWithText("Описание фотографии").performTextReplacement(caption)
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(10000) { left || fallbackCalled }
            assertTrue("System Back must save and return to the drawing", left)
            assertEquals(caption, f.description())
        }
    }

    @Test fun severalRestoredDraftsKeepIndividualTextAndAnIntentionalEmptyDescription() {
        Fixture().use { f ->
            val secondId = runBlocking {
                f.db.photoDao().insert(Photo(pointId = f.point.id, filePath = f.image.path, description = "Очистить это описание"))
            }
            var left = false
            val restoration = StateRestorationTester(compose)
            restoration.setContent { MaterialTheme { PointEditorScreen(f.db, f.point, 1, true) { left = true } } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Описание фотографии").fetchSemanticsNodes().size == 2 }
            compose.onAllNodesWithText("Описание фотографии")[0].performScrollTo().performTextReplacement("Первое фото")
            compose.onAllNodesWithText("Описание фотографии")[1].performScrollTo().performTextClearance()
            restoration.emulateSavedInstanceStateRestore()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Описание фотографии").fetchSemanticsNodes().size == 2 }
            compose.onNodeWithText("Сохранить").performClick()
            compose.waitUntil(10000) { left }
            assertEquals("Первое фото", f.description())
            assertEquals("", runBlocking { f.db.photoDao().getById(secondId)!!.description })
        }
    }

    @Test fun photoPreviewShowsTheTextBeingEdited() {
        Fixture().use { f ->
            compose.setContent { MaterialTheme { PointEditorScreen(f.db, f.point, 1, true) {} } }
            waitForEditor()
            val caption = "Описание в просмотре фотографии"
            compose.onNodeWithText("Описание фотографии").performTextReplacement(caption)
            compose.onAllNodesWithContentDescription("Фотография")[0].performClick()
            compose.onNode(hasText(caption) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        }
    }

    @Test fun bottomNavigationKeepsTheOpenPointAndItsDraft() {
        Fixture(twoPageDrawing = true).use { f ->
            lateinit var profile: ProfileViewModel
            compose.runOnIdle {
                profile = ViewModelProvider(compose.activity, ProfileViewModel.Factory(f.app))[ProfileViewModel::class.java]
            }
            compose.setContent { MaterialTheme { MainApp(f.db, profile) } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Проверка описаний").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Проверка описаний").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("План", substring = false).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("План", substring = false).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Страница 1 из 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Следующая").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("1", substring = false).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("1", substring = false).performTouchInput { click() }
            compose.onNodeWithText("Фото", substring = false).performClick()
            waitForEditor()
            val caption = "Описание после перехода в настройки"
            compose.onNodeWithText("Описание фотографии").performScrollTo().performTextReplacement(caption)
            compose.onNodeWithText("Настройки", substring = false).performClick()
            compose.onNodeWithText("Объекты", substring = false).performClick()
            compose.onNodeWithText("Точка 1", substring = false).assertExists()
            compose.onNodeWithText("Описание фотографии").assertTextContains(caption)
            compose.onNodeWithText("Сохранить", substring = false).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Страница 2 из 2").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(10000) { f.description() == caption }
        }
    }
}
