package com.example.photocad.report

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoNumberingTest {
    @Test fun serviceLineKeepsPointNumberAndGlobalPhotoNumber() {
        val photo = PdfPhoto("photo", "caption", pointNumber = 7, drawingPage = 2,
            drawingName = "План", photoIndexInPoint = 1, photoCountInPoint = 1)
        assertEquals("План • лист 2 • Точка №7 • Фото №3", photoServiceLine(photo, 3))
    }
}
