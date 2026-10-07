package com.example.photocad.data

/** Stable indices are stored in Room. Keep this order for existing points. */
object PointColors {
    private val values = intArrayOf(
        0xFFF44336.toInt(), // red: existing default
        0xFF2196F3.toInt(), // blue
        0xFF4CAF50.toInt(), // green
        0xFFFF9800.toInt(), // orange
        0xFF9C27B0.toInt(), // purple
        0xFF9E9E9E.toInt()  // grey
    )

    val size: Int get() = values.size
    fun argb(index: Int): Int = values[index.coerceIn(0, values.lastIndex)]
}
