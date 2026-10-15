package com.example.photocad.report

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Dates are local calendar dates; capture offsets are retained in storage but never used to move a photo to another day. */
data class ReportSelection(
    val drawingIds: Set<Long> = emptySet(),
    val fromDate: String = "",
    val throughDate: String = "",
    val includeUndated: Boolean = false,
    val undatedGroupDateTime: String = ""
)

fun validReportDate(value: String): Boolean {
    if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) return false
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    return runCatching { parser.parse(value) != null }.getOrDefault(false)
}

fun validReportDateTime(value: String): Boolean {
    if (!Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}").matches(value)) return false
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return runCatching { parser.parse(value) != null }.getOrDefault(false)
}

fun selectionError(selection: ReportSelection): String? = when {
    selection.drawingIds.isEmpty() -> "Выберите хотя бы один чертёж"
    selection.fromDate.isNotBlank() && !validReportDate(selection.fromDate) -> "Укажите начало периода в формате ГГГГ-ММ-ДД"
    selection.throughDate.isNotBlank() && !validReportDate(selection.throughDate) -> "Укажите конец периода в формате ГГГГ-ММ-ДД"
    selection.fromDate.isNotBlank() && selection.throughDate.isNotBlank() && selection.fromDate > selection.throughDate -> "Начало периода позже конца"
    selection.includeUndated && selection.undatedGroupDateTime.isNotBlank() &&
        !validReportDateTime(selection.undatedGroupDateTime) -> "Укажите дату и время группы в формате ГГГГ-ММ-ДДTЧЧ:ММ:СС"
    else -> null
}

private fun captureSortMillis(photo: DraftPhoto): Long {
    val value = photo.capturedAtLocal ?: return Long.MAX_VALUE
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val localMillis = runCatching { parser.parse(value)?.time }.getOrNull() ?: return Long.MAX_VALUE
    return localMillis - (photo.capturedOffsetMinutes ?: 0) * 60_000L
}

fun filterReportPhotos(photos: List<DraftPhoto>, selection: ReportSelection): List<DraftPhoto> = photos.filter { photo ->
    if (photo.drawingId !in selection.drawingIds) return@filter false
    val capture = photo.capturedAtLocal
    if (capture.isNullOrBlank()) return@filter selection.includeUndated
    val day = capture.take(10)
    (selection.fromDate.isBlank() || day >= selection.fromDate) &&
        (selection.throughDate.isBlank() || day <= selection.throughDate)
}.map { photo ->
    if (photo.capturedAtLocal.isNullOrBlank()) photo.copy(capturedAtLocal = selection.undatedGroupDateTime.takeIf { it.isNotBlank() })
    else photo
}.sortedWith(compareBy<DraftPhoto> { it.capturedAtLocal.isNullOrBlank() }
    .thenBy { captureSortMillis(it) }.thenBy { it.drawingId }.thenBy { it.drawingPage }
    .thenBy { it.pointNumber }.thenBy { it.photoId })

fun readableReportDateTime(value: String?, offsetMinutes: Int? = null): String = when {
    value.isNullOrBlank() -> "Дата съёмки не определена"
    value.length >= 19 -> "${value.substring(8, 10)}.${value.substring(5, 7)}.${value.substring(0, 4)} ${value.substring(11, 19)}" +
        (offsetMinutes?.let { offset ->
            val sign = if (offset < 0) "-" else "+"
            val absolute = kotlin.math.abs(offset)
            " UTC$sign%02d:%02d".format(Locale.ROOT, absolute / 60, absolute % 60)
        } ?: "")
    value.length >= 10 -> "${value.substring(8, 10)}.${value.substring(5, 7)}.${value.substring(0, 4)}"
    else -> "Дата съёмки не определена"
}

fun reportFileName(objectName: String, generatedAt: Date = Date()): String {
    val date = SimpleDateFormat("dd.MM", Locale.ROOT).format(generatedAt)
    val safeName = objectName.trim().replace(Regex("(?i)\\.(pdf|jpe?g|png|webp)$"), "")
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_")
        .trim('.', ' ', '_').take(60).ifBlank { "Объект" }
    return "$date $safeName.pdf"
}
