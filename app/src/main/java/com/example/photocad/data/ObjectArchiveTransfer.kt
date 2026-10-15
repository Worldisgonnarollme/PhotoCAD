package com.example.photocad.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

private const val MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024
private const val MAX_MANIFEST_BYTES = 4L * 1024 * 1024

data class ObjectArchivePreview(val name: String, val drawingCount: Int, val pointCount: Int, val photoCount: Int)

private data class ArchiveSnapshot(
    val site: Site, val details: SiteReportDetails?, val drawings: List<Drawing>,
    val points: List<Point>, val photos: List<Photo>
)

class PreparedObjectArchive internal constructor(
    val manifest: ArchiveManifest, private val stagingDirectory: File
) : AutoCloseable {
    val preview = ObjectArchivePreview(manifest.site.name, manifest.drawings.size, manifest.points.size, manifest.photos.size)

    suspend fun import(context: Context, db: AppDatabase, progress: (String) -> Unit = {}): Long = withContext(Dispatchers.IO) {
        val jobContext = currentCoroutineContext()
        val target = File(context.filesDir, "object-imports/${UUID.randomUUID()}")
        try {
            if (!target.parentFile!!.isDirectory && !target.parentFile!!.mkdirs()) throw IOException("Не удалось создать папку объекта")
            if (!target.mkdir()) throw IOException("Не удалось создать папку объекта")
            manifest.files.forEachIndexed { index, item ->
                progress("Сохранение файлов ${index + 1}/${manifest.files.size}")
                val source = File(stagingDirectory, item.path)
                val destination = File(target, item.path)
                if (!destination.parentFile!!.isDirectory && !destination.parentFile!!.mkdirs()) throw IOException("Не удалось создать папку вложений")
                val copied = source.inputStream().use { input -> destination.outputStream().use { output -> copyLimited(input, output, item.size) { jobContext.ensureActive() } } }
                if (copied.first != item.size || copied.second != item.sha256) throw IOException("Вложение изменилось перед импортом")
            }
            progress("Создание объекта")
            currentCoroutineContext().ensureActive()
            db.withTransaction {
                val existing = db.siteDao().getAll().first().map { it.name }.toSet()
                var name = manifest.site.name
                var copyNumber = 2
                while (name in existing) { name = "${manifest.site.name} (импорт $copyNumber)"; copyNumber++ }
                val siteId = db.siteDao().insert(Site(name = name, address = manifest.site.address, description = manifest.site.description))
                manifest.details?.let { d -> db.siteDao().saveReportDetails(SiteReportDetails(siteId, d.organizationName, d.organizationAddress, d.phone, d.email, d.inn, d.kpp, d.ogrn, d.customer, d.city, d.year, d.albumNumber)) }
                val drawings = mutableMapOf<Long, Long>()
                val points = mutableMapOf<Long, Long>()
                manifest.drawings.forEach { d ->
                    drawings[d.id] = db.drawingDao().insert(Drawing(name = d.name, filePath = File(target, d.file).absolutePath, siteId = siteId, description = d.description))
                }
                manifest.points.forEach { p ->
                    points[p.id] = db.pointDao().insert(Point(drawingId = drawings.getValue(p.drawingId), x = p.x, y = p.y, pageNumber = p.pageNumber, comment = p.comment, colorIndex = p.colorIndex, isArchived = p.isArchived, isFixed = p.isFixed, displayNumber = p.displayNumber))
                }
                manifest.photos.forEach { p ->
                    db.photoDao().insert(Photo(pointId = points.getValue(p.pointId), filePath = File(target, p.file).absolutePath, description = p.description, capturedAtLocal = p.capturedAtLocal, capturedOffsetMinutes = p.capturedOffsetMinutes, captureMetadataChecked = true))
                }
                currentCoroutineContext().ensureActive()
                siteId
            }
        } catch (failure: Throwable) {
            target.deleteRecursively()
            throw failure
        } finally { close() }
    }

    override fun close() { stagingDirectory.deleteRecursively() }
}

private fun MessageDigest.hexDigest(): String = digest().joinToString("") { "%02x".format(it) }
private fun digestFile(file: File, check: () -> Unit): ArchiveFile {
    if (!file.isFile || file.length() == 0L) throw IOException("Файл объекта недоступен: ${file.name}")
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            check()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return ArchiveFile("", file.length(), digest.hexDigest())
}

private fun copyLimited(input: InputStream, output: java.io.OutputStream, limit: Long, check: () -> Unit = {}): Pair<Long, String> {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        check()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > limit) throw IOException("Архив или вложение слишком велико")
        digest.update(buffer, 0, count)
        output.write(buffer, 0, count)
    }
    return total to digest.hexDigest()
}

suspend fun exportObjectArchive(context: Context, db: AppDatabase, siteId: Long, destination: Uri, progress: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
    val jobContext = currentCoroutineContext()
    val temp = File.createTempFile("photocad-export-", ".zip", context.cacheDir)
    try {
        progress("Сбор данных объекта")
        val (site, details, drawings, points, photos) = db.withTransaction {
            val site = db.siteDao().getById(siteId) ?: throw IOException("Объект не найден")
            val details = db.siteDao().getReportDetails(siteId)
            val drawings = db.drawingDao().getAllBySite(siteId)
            val points = drawings.flatMap { db.pointDao().getAllByDrawing(it.id) }
            val photos = points.flatMap { db.photoDao().getAllByPoint(it.id) }
            ArchiveSnapshot(site, details, drawings, points, photos)
        }
        val archiveDetails = details?.let { ArchiveDetails(it.organizationName, it.organizationAddress, it.phone, it.email, it.inn, it.kpp, it.ogrn, it.customer, it.city, it.year, it.albumNumber) }
        val files = mutableListOf<Pair<String, File>>()
        fun addFile(prefix: String, id: Long, path: String): String {
            val extension = File(path).extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "bin"
            val archivePath = "attachments/$prefix-$id.$extension"
            files += archivePath to File(path)
            return archivePath
        }
        val archiveDrawings = drawings.map { ArchiveDrawing(it.id, it.name, it.description, addFile("drawing", it.id, it.filePath)) }
        val archivePhotos = photos.map { ArchivePhoto(it.id, it.pointId, addFile("photo", it.id, it.filePath), it.description, it.capturedAtLocal, it.capturedOffsetMinutes) }
        val fileEntries = files.mapIndexed { index, (path, file) ->
            progress("Проверка файлов ${index + 1}/${files.size}")
            digestFile(file) { jobContext.ensureActive() }.copy(path = path)
        }
        val manifest = ArchiveManifest(1, ArchiveSite(site.name, site.address, site.description), archiveDetails,
            archiveDrawings, points.map { ArchivePoint(it.id, it.drawingId, it.x, it.y, it.pageNumber, it.displayNumber, it.comment, it.colorIndex, it.isArchived, it.isFixed) }, archivePhotos, fileEntries)
        validateArchiveManifest(manifest)
        ZipOutputStream(temp.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toJson().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            files.forEachIndexed { index, (path, file) ->
                progress("Упаковка файлов ${index + 1}/${files.size}")
                zip.putNextEntry(ZipEntry(path))
                val copied = file.inputStream().use { copyLimited(it, zip, 512L * 1024 * 1024) { jobContext.ensureActive() } }
                val expected = fileEntries[index]
                if (copied.first != expected.size || copied.second != expected.sha256) throw IOException("Файл изменился во время экспорта")
                zip.closeEntry()
            }
        }
        if (temp.length() > MAX_ARCHIVE_BYTES) throw IOException("Архив слишком велик")
        progress("Сохранение архива")
        val output = context.contentResolver.openOutputStream(destination, "w") ?: throw IOException("Не удалось открыть выбранное место")
        temp.inputStream().use { input -> output.use { copyLimited(input, it, MAX_ARCHIVE_BYTES) { jobContext.ensureActive() } } }
    } catch (failure: Throwable) {
        runCatching { context.contentResolver.delete(destination, null, null) }
        throw failure
    } finally { temp.delete() }
}

suspend fun prepareObjectArchive(context: Context, source: Uri, progress: (String) -> Unit = {}): PreparedObjectArchive = withContext(Dispatchers.IO) {
    val jobContext = currentCoroutineContext()
    val staging = File(context.cacheDir, "photocad-import-${UUID.randomUUID()}")
    if (!staging.mkdir()) throw IOException("Не удалось подготовить импорт")
    try {
        val archive = File(staging, "source.zip")
        progress("Чтение архива")
        val input = context.contentResolver.openInputStream(source) ?: throw IOException("Не удалось открыть архив")
        input.use { archive.outputStream().use { output -> copyLimited(it, output, MAX_ARCHIVE_BYTES) { jobContext.ensureActive() } } }
        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().toList()
            if (entries.map { it.name }.distinct().size != entries.size) throw IOException("Повторяющиеся файлы в архиве")
            val manifestEntry = zip.getEntry("manifest.json") ?: throw IOException("В архиве нет manifest.json")
            if (manifestEntry.isDirectory) throw IOException("Неверный manifest.json")
            val manifestBytes = zip.getInputStream(manifestEntry).use { inputStream ->
                val bytes = ByteArrayOutputStream()
                copyLimited(inputStream, bytes, MAX_MANIFEST_BYTES) { jobContext.ensureActive() }
                bytes.toByteArray()
            }
            if (manifestBytes.size > MAX_MANIFEST_BYTES) throw IOException("Описание архива слишком велико")
            val manifest = parseArchiveManifest(manifestBytes.toString(Charsets.UTF_8))
            val expectedPaths = manifest.files.map { it.path }.toSet()
            if (entries.any { it.isDirectory || (it.name != "manifest.json" && it.name !in expectedPaths) } || entries.size != manifest.files.size + 1) throw IOException("Неожиданные файлы в архиве")
            val total = manifest.files.sumOf { it.size }
            if (total > MAX_ARCHIVE_BYTES) throw IOException("Вложения слишком велики")
            manifest.files.forEachIndexed { index, file ->
                progress("Проверка файлов ${index + 1}/${manifest.files.size}")
                val entry = zip.getEntry(file.path) ?: throw IOException("Отсутствует вложение: ${file.path}")
                val output = File(staging, file.path)
                if (!output.parentFile!!.isDirectory && !output.parentFile!!.mkdirs()) throw IOException("Не удалось подготовить вложения")
                val actual = zip.getInputStream(entry).use { inputStream -> output.outputStream().use { copyLimited(inputStream, it, file.size) { jobContext.ensureActive() } } }
                if (actual.first != file.size || actual.second != file.sha256) throw IOException("Хэш или размер вложения не совпадает: ${file.path}")
            }
            archive.delete()
            PreparedObjectArchive(manifest, staging)
        }
    } catch (failure: Throwable) {
        staging.deleteRecursively()
        when (failure) {
            is ZipException -> throw IOException("Архив повреждён или не является файлом .photocad", failure)
            is JSONException -> throw IOException("Архив содержит повреждённый manifest.json", failure)
            else -> throw failure
        }
    }
}
