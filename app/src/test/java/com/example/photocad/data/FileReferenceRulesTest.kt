package com.example.photocad.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileReferenceRulesTest {
    @Test fun onlyDeletesFilesWithNoRemainingDrawingOrPhotoReferences() {
        assertTrue(isFileUnreferenced(0, 0))
        assertFalse(isFileUnreferenced(1, 0))
        assertFalse(isFileUnreferenced(0, 1))
        assertFalse(isFileUnreferenced(2, 3))
    }
}
