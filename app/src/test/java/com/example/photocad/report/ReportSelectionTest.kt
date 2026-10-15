package com.example.photocad.report

import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class ReportSelectionTest {
    private val rows = listOf(
        DraftPhoto(1, 11, "a", "", drawingId = 1, capturedAtLocal = "2026-10-15T23:59:59", drawingPage = 2, pointNumber = 4),
        DraftPhoto(2, 12, "b", "", drawingId = 1, capturedAtLocal = "2026-10-16T00:00:00", drawingPage = 1, pointNumber = 1),
        DraftPhoto(3, 13, "c", "", drawingId = 2, capturedAtLocal = "2026-10-15T08:00:00"),
        DraftPhoto(4, 14, "d", "", drawingId = 1, capturedAtLocal = null)
    )

    @Test fun filtersBothPeriodBoundariesUsingLocalCaptureDayAndDrawing() {
        val selected = filterReportPhotos(rows, ReportSelection(setOf(1), "2026-10-15", "2026-10-15"))
        assertEquals(listOf(1L), selected.map { it.photoId })
        assertEquals("2026-10-15T23:59:59", selected.single().capturedAtLocal)
    }

    @Test fun undatedGroupMayBeIncludedWithoutAssigningDateOrGivenDateAndTime() {
        val plain = filterReportPhotos(rows, ReportSelection(setOf(1), includeUndated = true))
        assertEquals(4L, plain.last().photoId)
        assertNull(plain.last().capturedAtLocal)
        assertNull(selectionError(ReportSelection(setOf(1), includeUndated = true)))
        val assigned = filterReportPhotos(rows, ReportSelection(setOf(1), includeUndated = true,
            undatedGroupDateTime = "2026-10-14T12:30:45"))
        assertEquals(4L, assigned.first().photoId)
        assertEquals("2026-10-14T12:30:45", assigned.first().capturedAtLocal)
        assertNotNull(selectionError(ReportSelection(setOf(1), includeUndated = true,
            undatedGroupDateTime = "2026-02-30T12:30:45")))
    }

    @Test fun fileNameUsesFormationDayAndCleanReadableName() {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT)
            .parse("2026-10-16T11:00:00") ?: Date(0)
        assertEquals("16.10 План этажа.pdf", reportFileName("План этажа.pdf", timestamp))
    }

    @Test fun pointNumbersAndTiesAreStable() {
        val sameTime = listOf(
            DraftPhoto(9, 9, "a", "", drawingId = 2, drawingPage = 1, pointNumber = 1, capturedAtLocal = "2026-10-15T10:00:00"),
            DraftPhoto(3, 3, "a", "", drawingId = 1, drawingPage = 2, pointNumber = 2, capturedAtLocal = "2026-10-15T10:00:00"),
            DraftPhoto(2, 2, "a", "", drawingId = 1, drawingPage = 1, pointNumber = 7, capturedAtLocal = "2026-10-15T10:00:00")
        )
        assertEquals(listOf(2L, 3L, 9L), filterReportPhotos(sameTime, ReportSelection(setOf(1, 2))).map { it.photoId })
    }

    @Test fun knownOffsetsOrderPhotosByCaptureInstant() {
        val zoned = listOf(
            DraftPhoto(1, 11, "a", "", drawingId = 1, capturedAtLocal = "2026-10-15T09:30:00", capturedOffsetMinutes = 60),
            DraftPhoto(2, 12, "b", "", drawingId = 1, capturedAtLocal = "2026-10-15T10:00:00", capturedOffsetMinutes = 180)
        )
        assertEquals(listOf(2L, 1L), filterReportPhotos(zoned, ReportSelection(setOf(1))).map { it.photoId })
        assertEquals("15.10.2026 10:00:00 UTC+03:00", readableReportDateTime(zoned[1].capturedAtLocal, zoned[1].capturedOffsetMinutes))
    }
}
