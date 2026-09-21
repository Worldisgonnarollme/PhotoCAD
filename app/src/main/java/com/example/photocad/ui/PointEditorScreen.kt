// полноэкранный редактор точки: вкладки Точка / Фото / Комментарий

package com.example.photocad.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photocad.data.*
import com.example.photocad.ui.components.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private enum class PointTab(val label: String) { POINT("Точка"), PHOTO("Фото"), COMMENT("Комментарий") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointEditorScreen(db: AppDatabase, point: Point, displayIndex: Int, startOnPhotoTab: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable(point.id) { mutableStateOf(if (startOnPhotoTab) PointTab.PHOTO else PointTab.POINT) }

    var drawing by remember(point.drawingId) { mutableStateOf<Drawing?>(null) }
    LaunchedEffect(point.drawingId) { drawing = db.drawingDao().getById(point.drawingId) }

    val photos by db.photoDao().getByPoint(point.id).collectAsState(initial = emptyList())
    val model: PointPhotosViewModel = viewModel(key = "point-photos-${point.id}",
        factory = PointPhotosViewModel.Factory(context.applicationContext as Application, db, point.id))
    val attachmentState by model.state.collectAsState()
    var comment by rememberSaveable(point.id) { mutableStateOf(point.comment) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val busy = attachmentState.busy

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Точка $displayIndex") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    TextButton(onClick = { scope.launch { model.saveComment(comment) }; onBack() }) { Text("Сохранить") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab.ordinal) {
                PointTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
                }
            }

            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                when (tab) {
                    PointTab.POINT -> PointTabContent(db, point, displayIndex, scope, drawing, model, busy)
                    PointTab.PHOTO -> PhotoTabContent(context, scope, model, photos, point, busy) { localMessage = it }
                    PointTab.COMMENT -> CommentTabContent(comment, busy, photos, point) { comment = it }
                }
                (localMessage ?: attachmentState.message)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            Row(
                Modifier.fillMaxWidth().padding(16.dp).clickable { confirmingDelete = true }
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Text("Удалить точку", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            text = "Удалить точку и все её фото? Это необратимо.",
            onConfirm = {
                confirmingDelete = false
                scope.launch { db.deletePoint(point.id); onBack() }
            },
            onDismiss = { confirmingDelete = false }
        )
    }
}

@Composable
private fun PointTabContent(
    db: AppDatabase,
    point: Point,
    displayIndex: Int,
    scope: kotlinx.coroutines.CoroutineScope,
    drawing: Drawing?,
    model: PointPhotosViewModel,
    busy: Boolean
) {
    var colorIndex by remember(point.id) { mutableIntStateOf(point.colorIndex) }
    SectionCard {
        Column(Modifier.padding(16.dp)) {
            Text("Цвет точки", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pointColors.forEachIndexed { index, color ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(color, CircleShape)
                            .border(width = if (index == colorIndex) 3.dp else 0.dp, color = MaterialTheme.colorScheme.onSurface, shape = CircleShape)
                            .clickable(enabled = !busy) {
                                colorIndex = index
                                model.saveColor(index)
                            }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Переместить точку", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            MiniDraggableMap(db = db, point = point, displayIndex = displayIndex, drawing = drawing, colorIndex = colorIndex, scope = scope)
        }
    }
}

@Composable
private fun MiniDraggableMap(db: AppDatabase, point: Point, displayIndex: Int, drawing: Drawing?, colorIndex: Int, scope: kotlinx.coroutines.CoroutineScope) {
    val bitmap = remember(drawing) { drawing?.let { BitmapFactory.decodeFile(it.filePath)?.asImageBitmap() } }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dragOffsetPx by remember(point.id) { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8F6F0))
            .onSizeChanged { boxSize = it }
    ) {
        bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        val currentPx = dragOffsetPx ?: Offset(point.x * boxSize.width, point.y * boxSize.height)
        Box(
            Modifier
                .offset(
                    x = with(density) { currentPx.x.toDp() } - 14.dp,
                    y = with(density) { currentPx.y.toDp() } - 14.dp
                )
                .size(28.dp)
                .background(pointColors[colorIndex.coerceIn(0, pointColors.lastIndex)], CircleShape)
                .pointerInput(point.id, boxSize) {
                    detectDragGestures(
                        onDragStart = { dragOffsetPx = Offset(point.x * boxSize.width, point.y * boxSize.height) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val base = dragOffsetPx ?: Offset(point.x * boxSize.width, point.y * boxSize.height)
                            dragOffsetPx = Offset(
                                (base.x + dragAmount.x).coerceIn(0f, boxSize.width.toFloat()),
                                (base.y + dragAmount.y).coerceIn(0f, boxSize.height.toFloat())
                            )
                        },
                        onDragEnd = {
                            val final = dragOffsetPx
                            if (final != null && boxSize.width > 0 && boxSize.height > 0) {
                                val fx = (final.x / boxSize.width).coerceIn(0f, 1f)
                                val fy = (final.y / boxSize.height).coerceIn(0f, 1f)
                                scope.launch { db.pointDao().updatePosition(point.id, fx, fy) }
                            }
                        },
                        onDragCancel = { dragOffsetPx = null }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text("$displayIndex", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PhotoTabContent(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    model: PointPhotosViewModel,
    photos: List<Photo>,
    point: Point,
    busy: Boolean,
    onMessage: (String?) -> Unit
) {
    var pendingCameraPath by rememberSaveable(point.id) { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { onMessage(null); model.importGallery(uri) }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        pendingCameraPath?.let { path -> onMessage(null); model.cameraResult(path, success); pendingCameraPath = null }
    }
    fun launchCamera() {
        scope.launch {
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
                onMessage("Камера недоступна: ${failure.message}")
            }
        }
    }

    if (photos.isEmpty()) {
        SectionCard {
            Column(Modifier.padding(16.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C2535)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !busy && pendingCameraPath == null, onClick = { launchCamera() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Сделать фото")
                    }
                    OutlinedButton(enabled = !busy && pendingCameraPath == null, onClick = {
                        try { pickImage.launch("image/*") } catch (failure: Exception) { onMessage("Галерея недоступна: ${failure.message}") }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Галерея")
                    }
                }
            }
        }
    } else {
        SectionCard {
            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !busy && pendingCameraPath == null, onClick = { launchCamera() }, modifier = Modifier.weight(1f)) {
                        Text("Переснять")
                    }
                    OutlinedButton(enabled = !busy && pendingCameraPath == null, onClick = {
                        try { pickImage.launch("image/*") } catch (failure: Exception) { onMessage("Галерея недоступна: ${failure.message}") }
                    }, modifier = Modifier.weight(1f)) { Text("Галерея") }
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(photos, key = { it.id }) { photo ->
                        Column(Modifier.width(220.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            PhotoThumbnail(photo.filePath, Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)))
                            Spacer(Modifier.height(6.dp))
                            PhotoDescriptionEditor(photo.id, photo.description, point.comment, busy) { description ->
                                onMessage(null)
                                model.saveDescription(photo.id, description)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentTabContent(comment: String, busy: Boolean, photos: List<Photo>, point: Point, onChange: (String) -> Unit) {
    SectionCard {
        Column(Modifier.padding(16.dp)) {
            Text("Комментарий", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = comment, onValueChange = onChange, enabled = !busy, minLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Новые фото получают этот комментарий как описание по умолчанию.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Фотографий у точки: ${photos.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
