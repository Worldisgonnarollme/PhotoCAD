package com.example.photocad.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PointCoordinateTransformTest {
    @Test fun screenAndPageCoordinatesRoundTripAtScaleOne() {
        val transform = PageTransform(PageRect(100f, 50f, 200f, 100f), Offset(200f, 150f), 1f, Offset.Zero)
        val page = Offset(0.25f, 0.75f)

        assertEquals(Offset(150f, 125f), transform.pageToScreen(page))
        assertEquals(page, transform.screenToPage(Offset(150f, 125f)))
    }

    @Test fun inverseAccountsForZoomAndPan() {
        val transform = PageTransform(PageRect(100f, 50f, 200f, 100f), Offset(200f, 150f), 2f, Offset(30f, -10f))
        val page = Offset(0.25f, 0.75f)

        assertEquals(Offset(130f, 90f), transform.pageToScreen(page))
        assertEquals(page, transform.screenToPage(Offset(130f, 90f)))
    }

    @Test fun rejectsLetterboxAndClampsStoredCoordinates() {
        val transform = PageTransform(PageRect(100f, 50f, 200f, 100f), Offset(200f, 150f), 1f, Offset.Zero)

        assertNull(transform.screenToPage(Offset(99f, 100f)))
        assertNull(transform.screenToPage(Offset(150f, 151f)))
        assertEquals(Offset(0f, 1f), normalizedPagePoint(Offset(-0.2f, 1.4f)))
    }

    @Test fun clampsPanToKeepTheScaledPageInsideTheViewport() {
        val clamped = clampPagePan(
            pan = Offset(200f, -200f),
            scale = 3f,
            contentRect = PageRect(100f, 50f, 200f, 100f),
            viewportSize = Offset(400f, 250f)
        )

        assertEquals(Offset(100f, 50f), clamped)
        assertEquals(Offset.Zero, clampPagePan(Offset(30f, -20f), 1f,
            PageRect(100f, 50f, 200f, 100f), Offset(400f, 250f)))
    }
}
