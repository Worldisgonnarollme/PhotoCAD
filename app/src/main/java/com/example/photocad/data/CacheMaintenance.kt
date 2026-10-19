// обслуживание кэша: сгенерированные отчёты, временные архивы объектов и промежуточные файлы импорта фото

package com.example.photocad.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

suspend fun cacheSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
    sizeOf(context.cacheDir) + (context.externalCacheDir?.let { sizeOf(it) } ?: 0L)
}

// Сами каталоги кэша остаются на месте: Android создаёт их один раз, а код пишет туда без проверок.
suspend fun clearCache(context: Context): Unit = withContext(Dispatchers.IO) {
    clearContents(context.cacheDir)
    context.externalCacheDir?.let { clearContents(it) }
}

private fun sizeOf(file: File): Long = when {
    !file.exists() -> 0L
    file.isDirectory -> file.listFiles()?.sumOf { sizeOf(it) } ?: 0L
    else -> file.length()
}

private fun clearContents(directory: File) {
    directory.listFiles()?.forEach { it.deleteRecursively() }
}

private const val KB = 1024L
private const val MB = KB * 1024L
private const val GB = MB * 1024L

fun formatCacheSize(bytes: Long): String = when {
    bytes <= 0L -> "пусто"
    bytes < KB -> "$bytes Б"
    bytes < MB -> "${(bytes + KB / 2) / KB} КБ"
    bytes < GB -> String.format(Locale.getDefault(), "%.1f МБ", bytes.toDouble() / MB)
    else -> String.format(Locale.getDefault(), "%.1f ГБ", bytes.toDouble() / GB)
}
