package com.example.photocad.report

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photocad.data.AppDatabase
import com.example.photocad.data.AppThemeMode
import com.example.photocad.data.Drawing
import com.example.photocad.data.Photo
import com.example.photocad.data.Point
import com.example.photocad.data.Site
import com.example.photocad.ui.PointEditorScreen
import com.example.photocad.ui.theme.PhotoCADTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

class PointPhotoPagerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun existingPhotosAppearAsSeparateCardsWithDirectGalleryAdd() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        fun image(color: Int): File = File.createTempFile("pager", ".png", context.cacheDir).also { file ->
            val bitmap = Bitmap.createBitmap(180, 220, Bitmap.Config.ARGB_8888)
            try { bitmap.eraseColor(color); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
        val firstFile = image(Color.GREEN)
        val secondFile = image(Color.YELLOW)
        try {
            val point = runBlocking {
                val siteId = db.siteDao().insert(Site(name = "Объект"))
                val drawingId = db.drawingDao().insert(Drawing(name = "План", filePath = "missing-drawing", siteId = siteId))
                val pointId = db.pointDao().insert(Point(drawingId = drawingId, x = 0.5f, y = 0.5f))
                db.photoDao().insert(Photo(pointId = pointId, filePath = firstFile.path, description = "Первое"))
                db.photoDao().insert(Photo(pointId = pointId, filePath = secondFile.path, description = "Второе"))
                db.pointDao().getById(pointId)!!
            }
            compose.setContent { PhotoCADTheme(AppThemeMode.DARK) { PointEditorScreen(db, point, 1, true) {} } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Фотография 1").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Фотография").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Фотография 2").assertExists()
            compose.onAllNodesWithText("Переснять").assertCountEquals(2)
            compose.onNodeWithText("Добавить из галереи").assertExists()
        } finally { db.close(); firstFile.delete(); secondFile.delete() }
    }
}
