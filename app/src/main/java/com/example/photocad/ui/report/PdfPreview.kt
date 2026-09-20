package com.example.photocad.ui.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Render one page at a time, using the actual completed PDF. */
@Composable
fun PdfPreview(file: File, pageCount: Int) {
    var pageIndex by remember(file.path) { mutableIntStateOf(0) }
    var error by remember(file.path, pageIndex) { mutableStateOf<String?>(null) }
    val bitmap by produceState<Bitmap?>(null, file.path, pageIndex) {
        value = null
        var allocated: Bitmap? = null
        var published = false
        try {
            withContext(Dispatchers.IO) {
                PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    renderer.openPage(pageIndex).use { page ->
                        val width = 1000
                        val target = Bitmap.createBitmap(width, (width.toFloat() * page.height / page.width).toInt(), Bitmap.Config.ARGB_8888)
                        allocated = target
                        target.eraseColor(Color.WHITE)
                        page.render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
            value = allocated
            published = true
            awaitDispose { /* Published bitmaps are owned by Compose/GC. */ }
        } catch (cancelled: CancellationException) {
            if (!published) allocated?.recycle()
            throw cancelled
        } catch (failure: Exception) {
            if (!published) allocated?.recycle()
            error = "Не удалось показать PDF: ${failure.message}"
        } catch (_: OutOfMemoryError) {
            if (!published) allocated?.recycle()
            error = "Недостаточно памяти для просмотра страницы"
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Text("Предварительный просмотр PDF", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) { Text("Назад") }
            Text("${pageIndex + 1} / $pageCount", modifier = Modifier.padding(12.dp))
            TextButton(onClick = { pageIndex++ }, enabled = pageIndex + 1 < pageCount) { Text("Далее") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val current = bitmap
        if (current != null) {
            Image(current.asImageBitmap(), contentDescription = "Страница ${pageIndex + 1} PDF",
                modifier = Modifier.fillMaxWidth().aspectRatio(595f / 842f))
        } else if (error == null) { CircularProgressIndicator() }
    }
}
