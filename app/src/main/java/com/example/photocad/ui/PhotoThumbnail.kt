package com.example.photocad.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.example.photocad.report.ReportImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhotoThumbnail(path: String, modifier: Modifier = Modifier, maxDecodeDimension: Int = 320) {
    var failed by remember(path) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, path, maxDecodeDimension) {
        value = null
        var loaded: Bitmap? = null
        var published = false
        try {
            withContext(Dispatchers.IO) { loaded = ReportImageLoader.load(path, maxDecodeDimension) }
            value = loaded
            published = true
            awaitDispose { /* Published bitmaps are owned by Compose/GC. */ }
        } catch (cancelled: CancellationException) {
            if (!published) loaded?.recycle()
            throw cancelled
        } catch (_: Exception) { if (!published) loaded?.recycle(); failed = true }
        catch (_: OutOfMemoryError) { if (!published) loaded?.recycle(); failed = true }
    }
    bitmap?.let { Image(it.asImageBitmap(), "Фотография", modifier) }
        ?: Text(if (failed) "Фото недоступно" else "Загрузка…", modifier)
}
