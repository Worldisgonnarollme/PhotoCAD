package com.example.photocad.report

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
    @Test fun pointOrdinalsAreStableWithinEachDrawingPage() {
        val photos = listOf(
            DraftPhoto(1, 8, "a", "", drawingPage = 1),
            DraftPhoto(2, 8, "b", "", drawingPage = 1),
            DraftPhoto(3, 9, "c", "", drawingPage = 1),
            DraftPhoto(4, 12, "d", "", drawingPage = 2),
            DraftPhoto(5, 10, "e", "", drawingPage = 1)
        )
        assertEquals(listOf(1, 1, 2, 1, 3), reportPointOrdinals(photos).map { it.pointNumber })
    }
    @Test fun captionsIncludePhotoPointAndDrawingPageAndOmitBlankDescription() {
        assertEquals("Фотография №2 • Точка №17 • Страница 3", ReportRules.caption(2, 17, 3, "  "))
        assertEquals("Фотография №2 • Точка №17 • Страница 3 — Трещина", ReportRules.caption(2, 17, 3, " Трещина "))
    }
    @Test fun moveAtBoundaryDoesNotDropItems() {
        val rows = listOf(DraftPhoto(1, 1, "a", ""), DraftPhoto(2, 1, "b", ""))
        assertEquals(rows, movePhoto(rows, 1, -1))
        assertEquals(rows, movePhoto(rows, 2, 1))
        assertEquals(listOf(2L, 1L), movePhoto(rows, 2, -1).map { it.photoId })
    }
}
