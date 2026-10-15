package com.example.photocad.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ObjectArchiveRulesTest {
    @Test fun rejectsTraversalAndUnexpectedPaths() {
        listOf("../photo.jpg", "attachments/../photo.jpg", "/attachments/photo.jpg", "attachments\\photo.jpg", "attachments/a//b.jpg")
            .forEach { path -> assertThrows(IllegalArgumentException::class.java) { validateArchivePath(path) } }
        assertEquals("attachments/photo.jpg", validateArchivePath("attachments/photo.jpg"))
    }

    @Test fun rejectsBrokenRelationsAndRepeatedIds() {
        val drawing = ArchiveDrawing(1, "D", "", "attachments/d.pdf")
        val point = ArchivePoint(2, 99, 1f, 2f, 1, 1, "", 0, false, false)
        val file = ArchiveFile("attachments/d.pdf", 2, "a".repeat(64))
        val base = ArchiveManifest(1, ArchiveSite("S", "", ""), null, listOf(drawing), listOf(point), emptyList(), listOf(file))
        assertThrows(IllegalArgumentException::class.java) { validateArchiveManifest(base) }
        assertThrows(IllegalArgumentException::class.java) {
            validateArchiveManifest(base.copy(points = emptyList(), drawings = listOf(drawing, drawing)))
        }
    }

    @Test fun rejectsMissingAttachmentAndUnsupportedVersion() {
        val drawing = ArchiveDrawing(1, "D", "", "attachments/d.pdf")
        val base = ArchiveManifest(1, ArchiveSite("S", "", ""), null, listOf(drawing), emptyList(), emptyList(), emptyList())
        assertThrows(IllegalArgumentException::class.java) { validateArchiveManifest(base) }
        assertThrows(IllegalArgumentException::class.java) { validateArchiveManifest(base.copy(version = 2)) }
    }

    @Test fun rejectsInvalidCaptureTimestampAndOffset() {
        val file = ArchiveFile("attachments/p.jpg", 10, "a".repeat(64))
        val base = ArchiveManifest(1, ArchiveSite("S", "", ""), null,
            listOf(ArchiveDrawing(1, "D", "", "attachments/d.pdf")),
            listOf(ArchivePoint(2, 1, 0.5f, 0.5f, 1, 1, "", 0, false, false)),
            listOf(ArchivePhoto(3, 2, file.path, null, "2026-02-30T10:00:00", 180)),
            listOf(file, ArchiveFile("attachments/d.pdf", 10, "b".repeat(64))))
        assertThrows(IllegalArgumentException::class.java) { validateArchiveManifest(base) }
        assertThrows(IllegalArgumentException::class.java) {
            validateArchiveManifest(base.copy(photos = listOf(base.photos.single().copy(capturedAtLocal = "2026-02-28T10:00:00", capturedOffsetMinutes = 841))))
        }
    }
}
