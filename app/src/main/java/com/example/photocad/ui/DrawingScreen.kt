// два экрана - для чертежа и для точки

package com.example.photocad.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.photocad.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// экран одного чертежа
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(db: AppDatabase, drawingId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var drawing by remember { mutableStateOf<Drawing?>(null) }
    LaunchedEffect(drawingId) {
        drawing = db.drawingDao().getAll().first().find { it.id == drawingId }
    }
    val points by db.pointDao().getByDrawing(drawingId).collectAsState(initial = emptyList())
    var selectedPoint by remember { mutableStateOf<Point?>(null) }
    val bitmap = remember(drawing) { drawing?.let { BitmapFactory.decodeFile(it.filePath)?.asImageBitmap() } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(drawing?.name ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
        )

        bitmap?.let { bmp ->
            var boxSize by remember { mutableStateOf(IntSize.Zero) }
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { boxSize = it }
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            if (boxSize.width == 0) return@detectTapGestures
                            val fx = offset.x / boxSize.width
                            val fy = offset.y / boxSize.height
                            val nearby = points.firstOrNull { p ->
                                val dx = p.x - fx; val dy = p.y - fy
                                (dx * dx + dy * dy) < 0.0015f
                            }
                            if (nearby != null) {
                                selectedPoint = nearby
                            } else {
                                scope.launch { db.pointDao().insert(Point(drawingId = drawingId, x = fx, y = fy)) }
                            }
                        }
                    }
            ) {
                Image(bitmap = bmp, contentDescription = null, modifier = Modifier.fillMaxSize())
                points.forEach { p ->
                    val density = LocalDensity.current
                    Box(
                        Modifier
                            .offset(
                                x = with(density) { (p.x * boxSize.width).toDp() } - 8.dp,
                                y = with(density) { (p.y * boxSize.height).toDp() } - 8.dp
                            )
                            .size(16.dp)
                            .background(Color.Red, CircleShape)
                    )
                }
            }
        }
    }

    selectedPoint?.let { point -> PhotoDialog(db = db, point = point, onDismiss = { selectedPoint = null }) }
}


// высплывающее окно для точки (показывает фото, привыязанные к точке)
@Composable
private fun PhotoDialog(db: AppDatabase, point: Point, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos by db.photoDao().getByPoint(point.id).collectAsState(initial = emptyList())

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch { db.photoDao().insert(Photo(pointId = point.id, filePath = copyUriToAppStorage(context, it))) } }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        bmp?.let { scope.launch { db.photoDao().insert(Photo(pointId = point.id, filePath = saveBitmapToAppStorage(context, it))) } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фото точки") },
        text = {
            Column {
                Row {
                    Button(onClick = { pickImage.launch("image/*") }) { Text("Галерея") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { takePhoto.launch(null) }) { Text("Камера") }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow {
                    items(photos) { photo ->
                        val bmp = remember(photo.filePath) { BitmapFactory.decodeFile(photo.filePath)?.asImageBitmap() }
                        bmp?.let { Image(it, null, modifier = Modifier.size(80.dp).padding(4.dp)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}