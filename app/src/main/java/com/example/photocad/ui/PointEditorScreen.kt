// полноэкранный редактор точки: вкладки Точка / Фото / Комментарий

package com.example.photocad.ui

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
                            .border(width = if (index == point.colorIndex) 3.dp else 0.dp, color = MaterialTheme.colorScheme.onSurface, shape = CircleShape)
                            .clickable(enabled = !busy) {
                                model.saveColor(index)
                            }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Переместить точку", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            MiniDraggableMap(db = db, point = point, displayIndex = displayIndex, drawing = drawing, colorIndex = point.colorIndex, scope = scope)
        }
    }
}

@Composable
private fun MiniDraggableMap(db: AppDatabase, point: Point, displayIndex: Int, drawing: Drawing?, colorIndex: Int, scope: kotlinx.coroutines.CoroutineScope) {
    var preview by remember(drawing?.filePath, point.pageNumber) { mutableStateOf<Pair<Bitmap, android.util.Size>?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dragOffsetPx by remember(point.id) { mutableStateOf<Offset?>(null) }
    var scale by remember(point.id) { mutableFloatStateOf(1f) }
    var pan by remember(point.id) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    LaunchedEffect(drawing?.filePath, point.pageNumber) {
        preview = null
        val path = drawing?.filePath ?: return@LaunchedEffect
        var reader: DrawingDocument? = null
        try {
            reader = openDrawingDocument(path)
            val size = reader.pageSize(point.pageNumber)
            val bitmap = reader.renderPage(point.pageNumber, 2400)
            preview = bitmap to size
        } catch (_: Exception) {
            preview = null
        } finally {
            reader?.close()
        }
    }
    val pageRect = remember(preview, boxSize) {
        preview?.second?.let { page ->
            if (boxSize.width > 0 && boxSize.height > 0)
                fitPageRect(boxSize.width.toFloat(), boxSize.height.toFloat(), page.width.toFloat(), page.height.toFloat())
            else null
        }
    }
    val transform = pageRect?.let { rect ->
        PageTransform(rect, Offset(boxSize.width / 2f, boxSize.height / 2f), scale, pan)
    }
    val latestTransform by rememberUpdatedState(transform)

    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .clipToBounds()
            .background(Color(0xFFF8F6F0))
            .onSizeChanged { boxSize = it }
            .pointerInput(pageRect, boxSize) {
                detectTransformGestures { centroid, drag, zoom, _ ->
                    val rect = pageRect ?: return@detectTransformGestures
                    val nextScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                    val offset = centroid - center
                    val nextPan = offset - (offset - pan) / scale * nextScale + drag
                    scale = nextScale
                    pan = clampPagePan(nextPan, nextScale, rect, Offset(boxSize.width.toFloat(), boxSize.height.toFloat()))
                }
            }
    ) {
        preview?.first?.let { bitmap ->
            Image(bitmap.asImageBitmap(), contentDescription = "Чертёж для переноса точки", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = pan.x; translationY = pan.y
                })
        }
        val rect = pageRect
        val pageTransform = transform
        if (rect != null && pageTransform != null) {
            val currentPx = dragOffsetPx ?: pageTransform.pageToScreen(Offset(point.x, point.y))
            Box(
                Modifier
                    .offset(x = with(density) { currentPx.x.toDp() } - MARKER_SIZE / 2, y = with(density) { currentPx.y.toDp() } - MARKER_SIZE / 2)
                    .size(MARKER_SIZE)
                    .background(pointColors[colorIndex.coerceIn(0, pointColors.lastIndex)], CircleShape)
                    .pointerInput(point.id, rect) {
                        detectDragGestures(
                            onDragStart = { dragOffsetPx = latestTransform?.pageToScreen(Offset(point.x, point.y)) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val base = dragOffsetPx ?: latestTransform?.pageToScreen(Offset(point.x, point.y)) ?: Offset.Zero
                                dragOffsetPx = base + dragAmount
                            },
                            onDragEnd = {
                                val final = dragOffsetPx
                                dragOffsetPx = null
                                if (final != null) {
                                    val normalized = latestTransform?.screenToPageClamped(final) ?: return@detectDragGestures
                                    scope.launch { db.pointDao().updatePosition(point.id, normalized.x, normalized.y) }
                                }
                            },
                            onDragCancel = { dragOffsetPx = null }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { Text("$displayIndex", color = Color.White, style = MaterialTheme.typography.labelSmall) }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = {
                scale = (scale * 1.5f).coerceAtMost(MAX_SCALE)
                pageRect?.let { pan = clampPagePan(pan, scale, it, Offset(boxSize.width.toFloat(), boxSize.height.toFloat())) }
            }, modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Приблизить чертёж")
            }
            IconButton(onClick = {
                scale = (scale / 1.5f).coerceAtLeast(MIN_SCALE)
                pageRect?.let { pan = clampPagePan(pan, scale, it, Offset(boxSize.width.toFloat(), boxSize.height.toFloat())) }
            }, modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Отдалить чертёж")
            }
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
    val preferences by UserPreferences.photoPreferencesFlow(context).collectAsState(initial = PhotoPreferences())
    var pendingGalleryUri by remember(point.id) { mutableStateOf<android.net.Uri?>(null) }
    var pendingGalleryReplaceId by rememberSaveable(point.id) { mutableStateOf<Long?>(null) }
    var pendingCameraPermissionResult by remember(point.id) { mutableStateOf<Boolean?>(null) }
    var pendingCameraReplaceId by rememberSaveable(point.id) { mutableStateOf<Long?>(null) }
    var photoToDelete by remember(point.id) { mutableStateOf<Long?>(null) }
    var photoToPreview by remember(point.id) { mutableStateOf<Photo?>(null) }
    var showAddChoices by remember(point.id) { mutableStateOf(false) }

    photoToPreview?.let { photo ->
        Dialog(onDismissRequest = { photoToPreview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Просмотр фотографии", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { photoToPreview = null }) { Text("Закрыть") }
                    }
                    PhotoThumbnail(photo.filePath, Modifier.fillMaxWidth().weight(1f), maxDecodeDimension = 1800)
                    photo.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(description, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                }
            }
        }
    }

    photoToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("Удалить фотографию?") },
            text = { Text("Остальные фотографии этой точки сохранятся.") },
            confirmButton = { TextButton(onClick = { photoToDelete = null; model.deletePhoto(id) }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { photoToDelete = null }) { Text("Отмена") } }
        )
    }

    val requestGalleryPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingGalleryUri?.let { uri ->
            pendingGalleryUri = null
            val replaceId = pendingGalleryReplaceId
            pendingGalleryReplaceId = null
            onMessage(null)
            model.importGallery(uri, allowGallery = granted, replacePhotoId = replaceId)
        }
        val cameraResult = pendingCameraPermissionResult
        val cameraPath = pendingCameraPath
        if (cameraResult != null && cameraPath != null) {
            val replaceId = pendingCameraReplaceId
            pendingCameraPermissionResult = null
            pendingCameraPath = null
            pendingCameraReplaceId = null
            onMessage(null)
            model.cameraResult(cameraPath, cameraResult, allowGallery = granted, replacePhotoId = replaceId)
        }
    }

    fun needsGalleryPermission(): Boolean = preferences.saveToGallery && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (needsGalleryPermission()) {
                pendingGalleryUri = uri
                requestGalleryPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                val replaceId = pendingGalleryReplaceId
                pendingGalleryReplaceId = null
                onMessage(null)
                model.importGallery(uri, replacePhotoId = replaceId)
            }
        } else pendingGalleryReplaceId = null
    }
    fun launchGallery(replaceId: Long?) {
        pendingGalleryReplaceId = replaceId
        try { pickImage.launch("image/*") }
        catch (failure: Exception) {
            pendingGalleryReplaceId = null
            onMessage("Галерея недоступна: ${failure.message}")
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        pendingCameraPath?.let { path ->
            if (success && needsGalleryPermission()) {
                pendingCameraPermissionResult = true
                requestGalleryPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                val replaceId = pendingCameraReplaceId
                onMessage(null)
                model.cameraResult(path, success, replacePhotoId = replaceId)
                pendingCameraPath = null
                pendingCameraReplaceId = null
            }
        }
    }
    fun launchCamera(replaceId: Long?) {
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "camera")
                    if (!dir.isDirectory && !dir.mkdirs()) error("Не удалось создать папку камеры")
                    File(dir, "${UUID.randomUUID()}.jpg").apply { createNewFile() }
                }
                pendingCameraPath = file.path
                pendingCameraReplaceId = replaceId
                takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                pendingCameraPath?.let { path -> withContext(Dispatchers.IO) { File(path).delete() } }
                pendingCameraPath = null
                pendingCameraReplaceId = null
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
                    Button(enabled = !busy && pendingCameraPath == null, onClick = { launchCamera(null) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Сделать фото")
                    }
                    OutlinedButton(enabled = !busy && pendingCameraPath == null, onClick = { launchGallery(null) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Галерея")
                    }
                }
            }
        }
    } else {
        Text("Фото у точки: ${photos.size}. Нажмите на снимок для просмотра. Каждое фото станет отдельной страницей фотоотчёта.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        photos.forEachIndexed { index, photo ->
            key(photo.id) {
                SectionCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PhotoThumbnail(photo.filePath, Modifier.size(88.dp).clip(RoundedCornerShape(8.dp))
                                .clickable { photoToPreview = photo })
                            Column(Modifier.weight(1f)) {
                                Text("Фотография ${index + 1}", style = MaterialTheme.typography.titleMedium)
                                Text("Фото ${index + 1} из ${photos.size}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { photoToDelete = photo.id }, enabled = !busy) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить фотографию ${index + 1}",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(enabled = !busy && pendingCameraPath == null,
                                onClick = { launchCamera(photo.id) }, modifier = Modifier.weight(1f)) { Text("Переснять") }
                            OutlinedButton(enabled = !busy && pendingCameraPath == null,
                                onClick = { launchGallery(photo.id) }, modifier = Modifier.weight(1f)) { Text("Заменить из галереи") }
                        }
                        PhotoDescriptionEditor(photo.id, photo.description, point.comment, busy) { description ->
                            onMessage(null)
                            model.saveDescription(photo.id, description)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        OutlinedButton(onClick = { showAddChoices = !showAddChoices }, enabled = !busy && pendingCameraPath == null,
            modifier = Modifier.fillMaxWidth()) { Text("Добавить фото") }
        if (showAddChoices) {
            Spacer(Modifier.height(8.dp))
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Новая фотография", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { showAddChoices = false; launchCamera(null) }, modifier = Modifier.weight(1f)) {
                            Text("Сделать фото")
                        }
                        OutlinedButton(onClick = { showAddChoices = false; launchGallery(null) }, modifier = Modifier.weight(1f)) {
                            Text("Из галереи")
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
