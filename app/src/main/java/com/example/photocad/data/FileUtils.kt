package com.example.photocad.data

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

suspend fun copyUriToAppStorage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val outFile = File(context.filesDir, "${UUID.randomUUID()}.jpg")
    try {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Не удалось открыть выбранное изображение")
        input.use { source -> outFile.outputStream().use { source.copyTo(it) } }
        if (outFile.length() == 0L) throw IOException("Выбран пустой файл")
        outFile.absolutePath
    } catch (failure: Throwable) {
        outFile.delete()
        throw failure
    }
}

suspend fun saveBitmapToAppStorage(context: Context, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
    val outFile = File(context.filesDir, "${UUID.randomUUID()}.jpg")
    try {
        outFile.outputStream().use {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) throw IOException("Не удалось сохранить фотографию")
        }
        outFile.absolutePath
    } catch (failure: Throwable) {
        outFile.delete()
        throw failure
    }
}

fun photoFileExtension(displayName: String?): String {
    val extension = displayName?.substringAfterLast('.', "")?.lowercase()
    return when (extension) {
        "png", "jpg", "jpeg", "webp", "heic", "heif", "gif", "bmp" -> extension
        else -> "jpg"
    }
}

fun photoMimeType(path: String): String = when (File(path).extension.lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "gif" -> "image/gif"
    "bmp" -> "image/bmp"
    else -> "image/jpeg"
}

private fun photoDirectory(context: Context): File = File(context.filesDir, "photos").apply {
    if (!isDirectory && !mkdirs()) throw IOException("Не удалось создать папку фотографий")
}

// Фотографии хранятся как есть: отчёту нужны исходные кадры без потерь от пережатия.
suspend fun importPhoto(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment
    val output = File(photoDirectory(context), "${UUID.randomUUID()}.${photoFileExtension(displayName)}")
    try {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Не удалось открыть выбранную фотографию")
        input.use { source -> output.outputStream().use { source.copyTo(it) } }
        if (output.length() == 0L) throw IOException("Выбрана пустая фотография")
        output.absolutePath
    } catch (failure: Throwable) {
        output.delete()
        throw failure
    }
}

// Кадр с камеры уже лежит в filesDir/camera, поэтому остаётся только проверить его пригодность.
suspend fun storeCameraPhoto(sourcePath: String): String = withContext(Dispatchers.IO) {
    val source = File(sourcePath)
    if (!source.isFile || source.length() == 0L) throw IOException("Файл фотографии с камеры недоступен")
    source.absolutePath
}

suspend fun copyPhotoToGallery(context: Context, path: String): Uri = withContext(Dispatchers.IO) {
    val file = File(path)
    if (!file.isFile || file.length() == 0L) throw IOException("Файл фотографии недоступен для галереи")
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Images.Media.MIME_TYPE, photoMimeType(file.path))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PhotoCAD")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val uri = resolver.insert(collection, values) ?: throw IOException("Не удалось создать запись в галерее")
    try {
        val output = resolver.openOutputStream(uri) ?: throw IOException("Не удалось открыть файл галереи")
        file.inputStream().use { source -> output.use { source.copyTo(it) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val publish = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            if (resolver.update(uri, publish, null, null) != 1) throw IOException("Не удалось опубликовать фотографию в галерее")
        }
        uri
    } catch (failure: Throwable) {
        resolver.delete(uri, null, null)
        throw failure
    }
}
