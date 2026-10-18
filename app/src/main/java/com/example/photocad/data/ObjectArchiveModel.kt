package com.example.photocad.data

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

data class ArchiveSite(val name: String, val address: String, val description: String)
data class ArchiveDetails(
    val organizationName: String, val organizationAddress: String, val phone: String,
    val email: String, val inn: String, val kpp: String, val ogrn: String,
    val customer: String, val city: String, val year: String, val albumNumber: String
)
data class ArchiveDrawing(val id: Long, val name: String, val description: String, val file: String)
data class ArchivePoint(
    val id: Long, val drawingId: Long, val x: Float, val y: Float, val pageNumber: Int,
    val displayNumber: Int, val comment: String, val colorIndex: Int, val isArchived: Boolean, val isFixed: Boolean
)
data class ArchivePhoto(
    val id: Long, val pointId: Long, val file: String, val description: String?,
    val capturedAtLocal: String?, val capturedOffsetMinutes: Int?
)
data class ArchiveFile(val path: String, val size: Long, val sha256: String)
data class ArchiveManifest(
    val version: Int, val site: ArchiveSite, val details: ArchiveDetails?,
    val drawings: List<ArchiveDrawing>, val points: List<ArchivePoint>,
    val photos: List<ArchivePhoto>, val files: List<ArchiveFile>
)

fun validateArchivePath(path: String): String {
    require(path.startsWith("attachments/") && !path.contains('\\') && !path.contains('\u0000')) { "Недопустимый путь вложения" }
    val parts = path.split('/')
    require(parts.size == 2 && parts.all { it.isNotBlank() && it != "." && it != ".." }) { "Недопустимый путь вложения" }
    return path
}

fun validateArchiveManifest(manifest: ArchiveManifest) {
    require(manifest.version == 1) { "Версия архива не поддерживается" }
    require(manifest.site.name.isNotBlank()) { "Название объекта отсутствует" }
    require(manifest.drawings.size <= 10_000 && manifest.points.size <= 100_000 && manifest.photos.size <= 100_000) { "Архив содержит слишком много записей" }
    require(manifest.drawings.map { it.id }.distinct().size == manifest.drawings.size && manifest.drawings.all { it.id > 0 }) { "Повторяющиеся чертежи" }
    require(manifest.points.map { it.id }.distinct().size == manifest.points.size && manifest.points.all { it.id > 0 }) { "Повторяющиеся точки" }
    require(manifest.photos.map { it.id }.distinct().size == manifest.photos.size && manifest.photos.all { it.id > 0 }) { "Повторяющиеся фотографии" }
    val drawingIds = manifest.drawings.map { it.id }.toSet()
    val pointIds = manifest.points.map { it.id }.toSet()
    require(manifest.points.all { it.drawingId in drawingIds && it.pageNumber > 0 && it.displayNumber >= 0 && it.x in 0f..1f && it.y in 0f..1f && it.colorIndex in 0 until PointColors.size }) { "Неверные координаты или связь точки с чертежом" }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply { isLenient = false }
    require(manifest.photos.all { photo ->
        photo.pointId in pointIds && (photo.capturedOffsetMinutes == null || photo.capturedOffsetMinutes in -840..840) &&
            (photo.capturedAtLocal == null || (photo.capturedAtLocal.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) &&
                ParsePosition(0).let { pos -> dateFormat.parse(photo.capturedAtLocal, pos) != null && pos.index == photo.capturedAtLocal.length }))
    }) { "Неверная связь или дата фотографии" }
    require(manifest.files.map { it.path }.distinct().size == manifest.files.size) { "Повторяющиеся вложения" }
    require(manifest.files.all { validateArchivePath(it.path) == it.path && it.size > 0 && it.size <= 512L * 1024 * 1024 && it.sha256.matches(Regex("[0-9a-f]{64}")) }) { "Неверное описание вложения" }
    val referenced = (manifest.drawings.map { it.file } + manifest.photos.map { it.file }).toSet()
    require(referenced == manifest.files.map { it.path }.toSet()) { "Вложения не соответствуют записям" }
}
