package com.example.photocad.report

import com.example.photocad.data.Point
import org.junit.Assert.*
import org.junit.Test

class ReportDraftTest {
    @Test fun temporaryEditSelectionAndOrderOnlyAffectSnapshot() {
        val original = listOf(DraftPhoto(1, 10, "/one.jpg", "Первое"), DraftPhoto(2, 10, "/two.jpg", "Второе"), DraftPhoto(3, 11, "/three.jpg", "Третье"))
        val changed = original.map { if (it.photoId == 1L) it.copy(description = "Временное") else it }
        val moved = movePhoto(changed, 3, -1).map { if (it.photoId == 2L) it.copy(selected = false) else it }
        assertEquals(listOf("/one.jpg", "/three.jpg"), reportInput(moved).map { it.filePath })
        assertEquals("Временное", reportInput(moved).first().description)
        assertEquals("Первое", original.first().description)
        assertEquals(2, ReportRules.pageCount(reportInput(moved).size))
        assertEquals(1, reportInput(original).first().pointNumber)
        assertEquals(1, reportInput(original).first().drawingPage)
    }
    @Test fun pointNumbersRestartOnEachSheetAndCountPhotolessPoints() {
        val points = listOf(
            Point(id = 8, drawingId = 1, x = 0f, y = 0f, pageNumber = 1),
            Point(id = 9, drawingId = 1, x = 0f, y = 0f, pageNumber = 1),
            // No photos of its own, but it still occupies number 3 on the drawing.
            Point(id = 10, drawingId = 1, x = 0f, y = 0f, pageNumber = 1),
            Point(id = 11, drawingId = 1, x = 0f, y = 0f, pageNumber = 1),
            Point(id = 12, drawingId = 1, x = 0f, y = 0f, pageNumber = 2)
        )
        val photos = listOf(
            DraftPhoto(1, 8, "a", "", drawingPage = 1),
            DraftPhoto(2, 8, "b", "", drawingPage = 1),
            DraftPhoto(3, 9, "c", "", drawingPage = 1),
            DraftPhoto(4, 12, "d", "", drawingPage = 2),
            DraftPhoto(5, 11, "e", "", drawingPage = 1)
        )
        assertEquals(listOf(1, 1, 2, 1, 4), numberPointsForReport(points, photos).map { it.pointNumber })
    }

    @Test fun photoPositionsCountOnlySelectedPhotosOfTheSamePoint() {
        val photos = listOf(
            DraftPhoto(1, 8, "a", "Раз"),
            DraftPhoto(2, 8, "b", "Два"),
            DraftPhoto(3, 8, "c", "Три", selected = false),
            DraftPhoto(4, 9, "d", "Одно")
        )
        val input = reportInput(photos)
        assertEquals(listOf(1, 2, 1), input.map { it.photoIndexInPoint })
        // The excluded photo is not counted: the album says «из 2», not «из 3».
        assertEquals(listOf(2, 2, 1), input.map { it.photoCountInPoint })
    }
    @Test fun captionsIncludePhotoPointAndDrawingPageAndOmitBlankDescription() {
        assertEquals("Фото №2 • План этажа • Страница 3 • Точка №17", ReportRules.caption(2, "План этажа", 3, 17, "  "))
        assertEquals("Фото №2 • План этажа • Страница 3 • Точка №17 — Трещина",
            ReportRules.caption(2, "План этажа", 3, 17, " Трещина "))
    }
    @Test fun reportSnapshotCarriesDrawingAndPointLocation() {
        val photo = DraftPhoto(1, 4, "/photo.jpg", "", drawingPage = 2, pointNumber = 3,
            drawingPath = "/floor.pdf", drawingName = "Второй этаж", pointX = 0.25f, pointY = 0.75f)
        val input = reportInput(listOf(photo)).single()
        assertEquals("/floor.pdf", input.drawingPath)
        assertEquals("Второй этаж", input.drawingName)
        assertEquals(2, input.drawingPage)
        assertEquals(3, input.pointNumber)
        assertEquals(0.25f, input.pointX)
        assertEquals(0.75f, input.pointY)
    }
    @Test fun moveAtBoundaryDoesNotDropItems() {
        val rows = listOf(DraftPhoto(1, 1, "a", ""), DraftPhoto(2, 1, "b", ""))
        assertEquals(rows, movePhoto(rows, 1, -1))
        assertEquals(rows, movePhoto(rows, 2, 1))
        assertEquals(listOf(2L, 1L), movePhoto(rows, 2, -1).map { it.photoId })
    }
}
