package com.example.photocad.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CaptureTime(val localDateTime: String?, val offsetMinutes: Int?)

fun parseExifLocalDateTime(value: String?): String? {
    if (value == null || !Regex("\\d{4}:\\d{2}:\\d{2} \\d{2}:\\d{2}:\\d{2}").matches(value)) return null
    if (value.substring(0, 4).toInt() == 0) return null
    val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return try {
        parser.parse(value) ?: return null
        "${value.substring(0, 4)}-${value.substring(5, 7)}-${value.substring(8, 10)}T${value.substring(11)}"
    } catch (_: Exception) { null }
}

fun parseExifOffsetMinutes(value: String?): Int? {
    val match = Regex("([+-])(\\d{2}):(\\d{2})").matchEntire(value ?: return null) ?: return null
    val hours = match.groupValues[2].toInt()
    val minutes = match.groupValues[3].toInt()
    if (hours > 14 || minutes > 59 || (hours == 14 && minutes != 0)) return null
    return (hours * 60 + minutes) * if (match.groupValues[1] == "-") -1 else 1
}

private fun exifCapture(exif: ExifInterface): CaptureTime {
    val local = parseExifLocalDateTime(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    val offset = if (local == null) null else parseExifOffsetMinutes(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
    return CaptureTime(local, offset)
}

fun readCaptureTime(path: String): CaptureTime = try {
    exifCapture(ExifInterface(path))
} catch (_: Exception) { CaptureTime(null, null) }

fun readCaptureTime(context: Context, uri: Uri): CaptureTime = try {
    context.contentResolver.openInputStream(uri)?.use { exifCapture(ExifInterface(it)) }
        ?: CaptureTime(null, null)
} catch (_: Exception) { CaptureTime(null, null) }

fun currentCameraCaptureTime(): CaptureTime {
    val now = Date()
    val zone = TimeZone.getDefault()
    return CaptureTime(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(now),
        zone.getOffset(now.time) / 60_000
    )
}

/** Read preserved files once after an upgrade; uncertain dates remain null. */
suspend fun AppDatabase.backfillCaptureMetadata() {
    photoDao().getUncheckedCaptureMetadata().forEach { photo ->
        val capture = if (File(photo.filePath).isFile) readCaptureTime(photo.filePath) else CaptureTime(null, null)
        photoDao().backfillCaptureMetadata(photo.id, capture.localDateTime, capture.offsetMinutes)
    }
}
