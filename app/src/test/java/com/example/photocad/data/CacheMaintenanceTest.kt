package com.example.photocad.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CacheMaintenanceTest {

    @Test fun emptyCacheReadsAsText() {
        assertEquals("пусто", formatCacheSize(0L))
        assertEquals("пусто", formatCacheSize(-1L))
    }

    @Test fun bytesAndKilobytesStayWhole() {
        assertEquals("512 Б", formatCacheSize(512L))
        assertEquals("1 КБ", formatCacheSize(1024L))
        // 1536 Б — ровно половина килобайта сверху, округление вверх
        assertEquals("2 КБ", formatCacheSize(1536L))
    }

    @Test fun megabytesKeepOneDecimalInsteadOfTruncating() {
        val decimal = java.text.DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
        assertEquals("2${decimal}9 МБ", formatCacheSize(3_072_000L))
        assertEquals("1${decimal}0 МБ", formatCacheSize(1024L * 1024L))
    }

    @Test fun gigabytesSwitchUnit() {
        val decimal = java.text.DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
        assertEquals("1${decimal}5 ГБ", formatCacheSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }
}
