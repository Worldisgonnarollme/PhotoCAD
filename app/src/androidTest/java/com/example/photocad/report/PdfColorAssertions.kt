package com.example.photocad.report

import android.graphics.Color
import org.junit.Assert.assertTrue
import kotlin.math.abs

/** Android PDF rasterization can round a channel by one unit. */
internal fun assertPdfColorNear(expected: Int, actual: Int, tolerance: Int = 2) {
    assertTrue("red: ${Color.red(actual)} vs ${Color.red(expected)}", abs(Color.red(actual) - Color.red(expected)) <= tolerance)
    assertTrue("green: ${Color.green(actual)} vs ${Color.green(expected)}", abs(Color.green(actual) - Color.green(expected)) <= tolerance)
    assertTrue("blue: ${Color.blue(actual)} vs ${Color.blue(expected)}", abs(Color.blue(actual) - Color.blue(expected)) <= tolerance)
}
