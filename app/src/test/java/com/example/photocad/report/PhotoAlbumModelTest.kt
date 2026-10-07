package com.example.photocad.report

import org.junit.Assert.*
import org.junit.Test

class PhotoAlbumModelTest {
    @Test fun finalCaptionAndPointColorTravelToTheRenderer() {
        val draft = DraftPhoto(10, 7, "/photo.jpg", "Изменено только для альбома", colorIndex = 1)
        val input = reportInput(listOf(draft)).single()
        assertEquals("Изменено только для альбома", input.description)
        assertEquals(1, input.colorIndex)
    }

    @Test fun captionSourceDistinguishesStoredPhotoTextFromFallback() {
        assertEquals(CaptionSource.PHOTO_DESCRIPTION, captionSource("Личное", "Общее"))
        assertEquals(CaptionSource.POINT_COMMENT, captionSource("  ", "Общее"))
        assertEquals(CaptionSource.MISSING, captionSource(null, " "))
    }

    @Test fun pointCropKeepsTheMarkerVisibleAtTheSourceEdge() {
        val crop = pointCrop(0.99f, 0.01f, 1000, 500, 539f, 250f)
        assertTrue(crop.left <= 0.99f && crop.right >= 0.99f)
        assertTrue(crop.top <= 0.01f && crop.bottom >= 0.01f)
        assertEquals(539f / 250f, (crop.right - crop.left) * 1000f / ((crop.bottom - crop.top) * 500f), 0.02f)
        assertTrue(crop.left >= 0f && crop.right <= 1f)
    }
}
