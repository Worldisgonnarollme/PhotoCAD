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
        assertEquals(1, ReportRules.pageCount(reportInput(moved).size))
    }
    @Test fun moveAtBoundaryDoesNotDropItems() {
        val rows = listOf(DraftPhoto(1, 1, "a", ""), DraftPhoto(2, 1, "b", ""))
        assertEquals(rows, movePhoto(rows, 1, -1))
        assertEquals(rows, movePhoto(rows, 2, 1))
        assertEquals(listOf(2L, 1L), movePhoto(rows, 2, -1).map { it.photoId })
    }
}
