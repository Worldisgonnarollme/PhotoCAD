package com.example.photocad.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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
