package com.example.photocad.report

import com.example.photocad.data.PointColors
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAlbumRulesTest {
    @Test fun persistedPaletteKeepsTheExistingPointColors() {
        assertEquals(0xFFF44336.toInt(), PointColors.argb(0))
        assertEquals(0xFF2196F3.toInt(), PointColors.argb(1))
        assertEquals(0xFF9E9E9E.toInt(), PointColors.argb(5))
    }

    @Test fun onlyPointsWithEveryCurrentPhotoInTheSavedAlbumAreEligible() {
        val attached = mapOf(7L to setOf(1L, 2L, 3L), 8L to setOf(4L), 9L to emptySet())
        assertEquals(setOf(8L), eligibleArchivePointIds(attached, setOf(1L, 2L, 4L)))
        assertEquals(setOf(7L, 8L), eligibleArchivePointIds(attached, setOf(1L, 2L, 3L, 4L)))
    }

    @Test fun blankIndividualDescriptionFallsBackToPointComment() {
        assertEquals("Комментарий", ReportRules.description("  ", "Комментарий"))
        assertEquals("Личное", ReportRules.description("Личное", "Комментарий"))
    }
}
