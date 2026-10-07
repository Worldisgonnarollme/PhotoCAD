package com.example.photocad.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoStorageRulesTest {
    @Test fun compressionChoosesJpegButOriginalModeKeepsSupportedExtension() {
        assertEquals("jpg", photoFileExtension("blueprint.PNG", compress = true))
        assertEquals("png", photoFileExtension("photo.PNG", compress = false))
        assertEquals("jpeg", photoFileExtension("photo.JPEG", compress = false))
        assertEquals("jpg", photoFileExtension("camera", compress = false))
    }

    @Test fun mediaStoreMimeTypeMatchesStoredExtension() {
        assertEquals("image/png", photoMimeType("plan.png"))
        assertEquals("image/jpeg", photoMimeType("photo.jpeg"))
    }
}
