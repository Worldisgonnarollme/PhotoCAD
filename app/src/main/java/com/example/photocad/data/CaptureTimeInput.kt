package com.example.photocad.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Typing masks for the capture time dialog: the user types digits only and the separators appear
 * on their own. `deleting` stops backspace from re-adding the separator just removed.
 */
fun formatDateInput(raw: String, deleting: Boolean = false): String = mask(raw, 8, setOf(2, 4), '.', deleting)

fun formatTimeInput(raw: String, deleting: Boolean = false): String = mask(raw, 4, setOf(2), ':', deleting)

private fun mask(raw: String, maxDigits: Int, breaks: Set<Int>, separator: Char, deleting: Boolean): String {
    val digits = raw.filter { it.isDigit() }.take(maxDigits)
    val text = buildString {
        digits.forEachIndexed { index, digit ->
            if (index in breaks) append(separator)
            append(digit)
        }
    }
    // A group was just completed while typing forward, so show its separator right away.
    return if (!deleting && digits.length in breaks && digits.length < maxDigits) text + separator else text
}

/** Dialog shows dd.MM.yyyy and HH:mm; the stored value stays ISO. Null means the input is not valid. */
fun parseManualCaptureTime(dateText: String, timeText: String): String? {
    val date = dateText.trim()
    val time = timeText.trim()
    if (!Regex("\\d{2}\\.\\d{2}\\.\\d{4}").matches(date) || !Regex("\\d{2}:\\d{2}").matches(time)) return null
    val input = "$date $time"
    val parser = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val instant = runCatching { parser.parse(input) }.getOrNull() ?: return null
    // Rejects things SimpleDateFormat would silently roll over, such as 31.02.2026.
    if (parser.format(instant) != input) return null
    return "${date.substring(6)}-${date.substring(3, 5)}-${date.substring(0, 2)}T$time:00"
}

/** ISO value from the database as it should appear in the date field. */
fun isoDateToInput(iso: String?): String {
    val date = iso?.take(10) ?: return ""
    if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) return ""
    return "${date.substring(8)}.${date.substring(5, 7)}.${date.substring(0, 4)}"
}
