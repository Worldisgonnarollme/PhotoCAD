package com.example.photocad.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

object ReportFileManager {
    fun directory(context: Context): File = File(context.cacheDir, "reports")

    fun contentUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun shareIntent(context: Context, file: File): Intent {
        val uri = contentUri(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun openIntent(context: Context, file: File): Intent {
        val uri = contentUri(context, file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            clipData = ClipData.newRawUri("PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** destination must be the new URI returned by CreateDocument for this operation. */
    suspend fun save(context: Context, source: File, destination: Uri) {
        try {
            withContext(Dispatchers.IO) {
                if (!source.isFile) throw ReportException("Временный PDF недоступен. Сформируйте его повторно.")
                val stream = context.contentResolver.openOutputStream(destination, "wt")
                    ?: throw ReportException("Не удалось открыть место сохранения")
                stream.use { output ->
                    source.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                    output.flush()
                }
                currentCoroutineContext().ensureActive()
            }
        } catch (failure: Throwable) {
            // Best effort, only the document just created by the user. Never remove source PDF.
            withContext(NonCancellable + Dispatchers.IO) {
                try { DocumentsContract.deleteDocument(context.contentResolver, destination) }
                catch (_: Exception) { /* Provider may not support removal; UI explains partial output. */ }
            }
            throw failure
        }
    }

    suspend fun cleanOldCache(context: Context) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        directory(context).listFiles()?.filter {
            it.isFile && it.lastModified() < cutoff && (it.extension == "pdf" || it.extension == "part")
        }?.forEach { it.delete() }
    }
}
