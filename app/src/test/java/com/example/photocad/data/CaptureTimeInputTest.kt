package com.example.photocad.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureTimeInputTest {
    @Test fun dateSeparatorsAppearWhileTypingDigits() {
        assertEquals("1", formatDateInput("1"))
        assertEquals("18.", formatDateInput("18"))
        assertEquals("18.1", formatDateInput("18.1"))
        assertEquals("18.10.", formatDateInput("18.10"))
        assertEquals("18.10.2026", formatDateInput("18.10.2026"))
    }

    @Test fun typedSeparatorsAndExtraDigitsAreIgnored() {
        // The user may type the dots anyway, or paste a longer string.
        assertEquals("18.10.2026", formatDateInput("18102026"))
        assertEquals("18.10.2026", formatDateInput("18.10.20269999"))
        assertEquals("18.10.2026", formatDateInput("18-10-2026"))
    }

    @Test fun deletingDoesNotPutTheSeparatorBack() {
        assertEquals("18", formatDateInput("18.", deleting = true))
        assertEquals("12", formatTimeInput("12:", deleting = true))
    }

    @Test fun timeSeparatorAppearsAfterTheHour() {
        assertEquals("1", formatTimeInput("1"))
        assertEquals("12:", formatTimeInput("12"))
        assertEquals("12:2", formatTimeInput("12:2"))
        assertEquals("12:25", formatTimeInput("1225"))
    }

    @Test fun validInputBecomesIsoValue() {
        assertEquals("2026-10-18T12:25:00", parseManualCaptureTime("18.10.2026", "12:25"))
        assertEquals("2026-01-01T00:00:00", parseManualCaptureTime("01.01.2026", "00:00"))
    }

    @Test fun impossibleOrIncompleteValuesAreRejected() {
        assertNull(parseManualCaptureTime("31.02.2026", "12:25"))
        assertNull(parseManualCaptureTime("18.13.2026", "12:25"))
        assertNull(parseManualCaptureTime("18.10.2026", "25:00"))
        assertNull(parseManualCaptureTime("18.10.2026", "12:60"))
        assertNull(parseManualCaptureTime("18.10.26", "12:25"))
        assertNull(parseManualCaptureTime("", ""))
    }

    @Test fun storedIsoValueFillsTheDateField() {
        assertEquals("18.10.2026", isoDateToInput("2026-10-18T12:25:00"))
        assertEquals("", isoDateToInput(null))
        assertEquals("", isoDateToInput("мусор"))
    }
}
