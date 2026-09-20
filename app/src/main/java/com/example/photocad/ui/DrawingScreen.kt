// два экрана - для чертежа и для точки

package com.example.photocad.ui

import android.graphics.BitmapFactory
import android.app.Application
import androidx.lifecycle.viewmodel.compose.viewModel
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
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
fun DrawingScreen(db: AppDatabase, drawingId: Long, onBack: () -> Unit, onReport: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var drawing by remember { mutableStateOf<Drawing?>(null) }
    LaunchedEffect(drawingId) {
        drawing = db.drawingDao().getAll().first().find { it.id == drawingId }
    }
    val points by db.pointDao().getByDrawing(drawingId).collectAsState(initial = emptyList())
    var selectedPointId by rememberSaveable(drawingId) { mutableStateOf<Long?>(null) }
    val bitmap = remember(drawing) { drawing?.let { BitmapFactory.decodeFile(it.filePath)?.asImageBitmap() } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(drawing?.name ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = { TextButton(onClick = onReport) { Text("Сформировать отчёт") } }
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
                                selectedPointId = nearby.id
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

    points.firstOrNull { it.id == selectedPointId }?.let { point ->
        PhotoDialog(db = db, point = point, onDismiss = { selectedPointId = null })
    }
}


// Point comment and individually saved photo descriptions.
@Composable
private fun PhotoDialog(db: AppDatabase, point: Point, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos by db.photoDao().getByPoint(point.id).collectAsState(initial = emptyList())
    val model: PointPhotosViewModel = viewModel(key = "point-photos-${point.id}",
        factory = PointPhotosViewModel.Factory(context.applicationContext as Application, db, point.id))
    val attachmentState by model.state.collectAsState()
    var comment by rememberSaveable(point.id) { mutableStateOf(point.comment) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    var launchingCamera by remember { mutableStateOf(false) }
    val busy = launchingCamera || attachmentState.busy
    var pendingCameraPath by rememberSaveable(point.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(attachmentState.completedCameraPath) {
        if (pendingCameraPath == attachmentState.completedCameraPath) pendingCameraPath = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { localMessage = null; model.importGallery(uri) }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        pendingCameraPath?.let { path -> localMessage = null; model.cameraResult(path, success) }
    }

    AlertDialog(
        onDismissRequest = { if (!busy && pendingCameraPath == null) onDismiss() },
        title = { Text("Фото точки") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = comment, onValueChange = { comment = it }, enabled = !busy,
                    label = { Text("Комментарий точки") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                TextButton(enabled = !busy, onClick = { localMessage = null; model.saveComment(comment) }) {
                    Text("Сохранить комментарий точки")
                }
                Text("Новые фото получают сохранённый комментарий точки.")
                Row {
                    Button(enabled = !busy && pendingCameraPath == null, onClick = {
                        try { pickImage.launch("image/*") }
                        catch (failure: Exception) { localMessage = "Галерея недоступна: ${failure.message}" }
                    }) { Text("Галерея") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = !busy && pendingCameraPath == null, onClick = {
                        scope.launch {
                            launchingCamera = true
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    val dir = File(context.filesDir, "camera")
                                    if (!dir.isDirectory && !dir.mkdirs()) error("Не удалось создать папку камеры")
                                    File(dir, "${UUID.randomUUID()}.jpg").apply { createNewFile() }
                                }
                                pendingCameraPath = file.path
                                takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) {
                                pendingCameraPath?.let { path -> withContext(Dispatchers.IO) { File(path).delete() } }
                                pendingCameraPath = null
                                localMessage = "Камера недоступна: ${failure.message}"
                            } finally { launchingCamera = false }
                        }
                    }) { Text("Камера") }
                }
                (localMessage ?: attachmentState.message)?.let { Text(it) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(photos, key = { it.id }) { photo ->
                        Column(Modifier.width(240.dp)) {
                            PhotoThumbnail(photo.filePath, Modifier.size(120.dp))
                            PhotoDescriptionEditor(photo.id, photo.description, point.comment, busy) { description ->
                                localMessage = null
                                model.saveDescription(photo.id, description)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && pendingCameraPath == null, onClick = onDismiss) { Text("Готово") } }
    )
}
