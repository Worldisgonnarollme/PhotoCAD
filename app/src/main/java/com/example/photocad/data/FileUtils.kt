// взять файл по пути и сохранить внутри приложения

package com.example.photocad.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.util.UUID

fun copyUriToAppStorage(context: Context, uri: Uri): String {
    val outFile = File(context.filesDir, "${UUID.randomUUID()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        outFile.outputStream().use { output -> input.copyTo(output) }
    }
    return outFile.absolutePath
}

fun saveBitmapToAppStorage(context: Context, bitmap: Bitmap): String {
    val outFile = File(context.filesDir, "${UUID.randomUUID()}.jpg")
    outFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return outFile.absolutePath
}