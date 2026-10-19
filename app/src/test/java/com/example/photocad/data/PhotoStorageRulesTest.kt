package com.example.photocad.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoStorageRulesTest {
    @Test fun storedPhotoKeepsSupportedExtension() {
        assertEquals("png", photoFileExtension("photo.PNG"))
        assertEquals("jpeg", photoFileExtension("photo.JPEG"))
        assertEquals("jpg", photoFileExtension("camera"))
        // Неизвестное расширение сводится к jpg, иначе MediaStore не подберёт mime-тип.
        assertEquals("jpg", photoFileExtension("scan.tiff"))
    }

    @Test fun mediaStoreMimeTypeMatchesStoredExtension() {
        assertEquals("image/png", photoMimeType("plan.png"))
        assertEquals("image/jpeg", photoMimeType("photo.jpeg"))
    }
}
