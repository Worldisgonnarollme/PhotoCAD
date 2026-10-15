package com.example.photocad.data

import org.junit.Assert.*
import org.junit.Test

class CaptureMetadataTest {
    @Test fun parsesValidExifLocalTimeAndOffset() {
        assertEquals("2021-02-28T23:59:58", parseExifLocalDateTime("2021:02:28 23:59:58"))
        assertEquals(180, parseExifOffsetMinutes("+03:00"))
        assertEquals(-330, parseExifOffsetMinutes("-05:30"))
    }

    @Test fun rejectsMissingAndInvalidCaptureMetadata() {
        assertNull(parseExifLocalDateTime(null))
        assertNull(parseExifLocalDateTime("2021:02:29 10:00:00"))
        assertNull(parseExifLocalDateTime("0000:00:00 00:00:00"))
        assertNull(parseExifOffsetMinutes("+15:00"))
        assertNull(parseExifOffsetMinutes("junk"))
    }
}
