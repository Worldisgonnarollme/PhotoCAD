package com.example.photocad.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DrawingDocumentRulesTest {
    @Test fun detectsPdfByMimeAndKeepsJpgExtensionsDistinct() {
        assertEquals(DrawingFormat.PDF, detectDrawingFormat("application/pdf", "plan.PDF"))
        assertEquals(DrawingFormat.JPEG, detectDrawingFormat("image/jpeg", "photo.JPEG"))
        assertEquals("jpeg", drawingExtension(DrawingFormat.JPEG, "photo.JPEG"))
        assertEquals("jpg", drawingExtension(DrawingFormat.JPEG, null))
    }

    @Test fun rejectsUnknownAndConflictingFormats() {
        assertThrows(IllegalArgumentException::class.java) { detectDrawingFormat("image/svg+xml", "plan.svg") }
        assertThrows(IllegalArgumentException::class.java) { detectDrawingFormat("application/pdf", "plan.png") }
    }
}
