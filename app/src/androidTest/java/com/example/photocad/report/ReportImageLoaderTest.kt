package com.example.photocad.report

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ReportImageLoaderTest {
    @Test fun allExifOrientationsAndOriginalBytesPreserved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // TL red, TR green, BL blue, BR yellow. Literal expected transformed quadrants.
        val quadrants = mapOf(
            1 to listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
            2 to listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE),
            3 to listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
            4 to listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN),
            5 to listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW),
            6 to listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
            7 to listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED),
            8 to listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE)
        )
        for (orientation in 1..8) {
            val file = File.createTempFile("exif", ".jpg", context.cacheDir)
            val original = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
            for (y in 0 until 40) for (x in 0 until 80) original.setPixel(x, y,
                if (y < 20) { if (x < 40) Color.RED else Color.GREEN } else { if (x < 40) Color.BLUE else Color.YELLOW })
            file.outputStream().use { original.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            original.recycle()
            try {
                ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
                val before = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                val decoded = ReportImageLoader.load(file.path)
                try {
                    assertEquals(if (orientation >= 5) 40 else 80, decoded.width)
                    val positions = listOf(1 to 1, 3 to 1, 1 to 3, 3 to 3)
                    positions.forEachIndexed { index, (x, y) ->
                        val actual = decoded.getPixel(decoded.width * x / 4, decoded.height * y / 4)
                        val expected = quadrants.getValue(orientation)[index]
                        assertTrue("Orientation $orientation quadrant $index", kotlin.math.abs(Color.red(actual)-Color.red(expected)) < 25 &&
                            kotlin.math.abs(Color.green(actual)-Color.green(expected)) < 25 && kotlin.math.abs(Color.blue(actual)-Color.blue(expected)) < 25)
                    }
                    assertArrayEquals(before, MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
                } finally { decoded.recycle() }
            } finally { file.delete() }
        }
    }
}
