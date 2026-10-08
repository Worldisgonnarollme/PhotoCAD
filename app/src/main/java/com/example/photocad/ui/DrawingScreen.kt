package com.example.photocad.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photocad.data.*
import kotlinx.coroutines.launch

val pointColors = (0 until PointColors.size).map { Color(PointColors.argb(it)) }

// Shared with PointEditorScreen so both views of the drawing zoom the same way.
const val MIN_SCALE = 1f
const val MAX_SCALE = 8f

// Markers keep this on-screen size at every zoom level, so they cover less of the
// drawing the closer you get.
val MARKER_SIZE = 20.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(db: AppDatabase, drawingId: Long, onBack: () -> Unit, onReport: () -> Unit) {
    val scope = rememberCoroutineScope()
    var drawing by remember { mutableStateOf<Drawing?>(null) }
    var site by remember { mutableStateOf<Site?>(null) }
    LaunchedEffect(drawingId) {
        drawing = db.drawingDao().getById(drawingId)
        drawing?.let { site = db.siteDao().getById(it.siteId) }
    }

    var activePage by rememberSaveable(drawingId) { mutableIntStateOf(1) }
    val points by db.pointDao().getByDrawingPage(drawingId, activePage).collectAsState(initial = emptyList())
    var selectedPointId by rememberSaveable(drawingId) { mutableStateOf<Long?>(null) }
    var newlyCreatedId by rememberSaveable(drawingId) { mutableStateOf<Long?>(null) }
    var draggedPointId by remember { mutableStateOf<Long?>(null) }
    var menuPointId by remember { mutableStateOf<Long?>(null) }
    var confirmingDeleteId by remember { mutableStateOf<Long?>(null) }
    var showPoints by rememberSaveable(drawingId) { mutableStateOf(true) }
    var scale by rememberSaveable(drawingId) { mutableFloatStateOf(1f) }
    var pan by remember(drawingId) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var renderSize by remember { mutableStateOf(IntSize.Zero) }
    val drawingPath = drawing?.filePath
    var document by remember(drawingPath) { mutableStateOf<DrawingDocument?>(null) }
    var openError by remember(drawingPath) { mutableStateOf<String?>(null) }
    var pageError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(drawingPath, document) { onDispose { document?.close() } }
    LaunchedEffect(drawingPath) {
        document = null
        openError = null
        activePage = 1
        if (drawingPath != null) {
            try { document = openDrawingDocument(drawingPath) }
            catch (failure: Exception) { openError = "Не удалось открыть чертёж: ${failure.message}" }
        }
    }

    val pageCount = document?.pageCount ?: 1
    LaunchedEffect(pageCount) { activePage = activePage.coerceIn(1, pageCount) }
    LaunchedEffect(activePage) { scale = 1f; pan = Offset.Zero; pageError = null }
    val pageSize = remember(document, activePage) {
        document?.let { reader -> runCatching { reader.pageSize(activePage) }.getOrNull() }
    }
    val pageRect = remember(pageSize, viewportSize) {
        if (pageSize == null || viewportSize.width <= 0 || viewportSize.height <= 0) null
        else fitPageRect(viewportSize.width.toFloat(), viewportSize.height.toFloat(), pageSize.width.toFloat(), pageSize.height.toFloat())
    }
    val transform = remember(pageRect, viewportSize, scale, pan) {
        pageRect?.let { PageTransform(it, Offset(viewportSize.width / 2f, viewportSize.height / 2f), scale, pan) }
    }
    val latestTransform by rememberUpdatedState(transform)
    val pageBitmap by produceState<Bitmap?>(null, document, activePage, renderSize) {
        value = null
        val reader = document ?: return@produceState
        if (renderSize.width <= 0 || renderSize.height <= 0) return@produceState
        try {
            // Capped so a big viewport cannot ask for a bitmap large enough to exhaust memory.
            value = reader.renderPage(activePage, (maxOf(renderSize.width, renderSize.height) * 2).coerceAtMost(3200))
        } catch (failure: Exception) {
            pageError = "Не удалось показать страницу $activePage: ${failure.message}"
        }
    }

    val selectedPoint = points.firstOrNull { it.id == selectedPointId }
    if (selectedPoint != null) {
        PointEditorScreen(
            db = db,
            point = selectedPoint,
            displayIndex = points.indexOf(selectedPoint) + 1,
            startOnPhotoTab = selectedPoint.id == newlyCreatedId,
            onBack = { selectedPointId = null; newlyCreatedId = null }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = {
                Column {
                    Text(drawing?.name ?: "")
                    site?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = {
                IconButton(onClick = { showPoints = !showPoints }) {
                    Icon(Icons.Default.Layers, contentDescription = "Показать/скрыть точки",
                        tint = if (showPoints) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onReport) { Icon(Icons.Default.Share, contentDescription = "Сформировать отчёт") }
            }
        )

        Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged {
                viewportSize = it
                renderSize = it
            }
        ) {
            val bitmap = pageBitmap
            if (bitmap != null && transform != null && pageRect != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Страница $activePage",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    }
                )
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(transform, points, pageRect) {
                            fun pointAt(screen: Offset) = points.firstOrNull { point ->
                                val marker = transform.pageToScreen(Offset(point.x, point.y))
                                val dx = marker.x - screen.x
                                val dy = marker.y - screen.y
                                dx * dx + dy * dy < 32f * 32f
                            }
                            detectTapGestures(
                                // Long press opens the point menu; a plain tap keeps opening the card.
                                onLongPress = { screen -> pointAt(screen)?.let { menuPointId = it.id } },
                                onTap = { screen ->
                                    val nearby = pointAt(screen)
                                    if (nearby != null) {
                                        selectedPointId = nearby.id
                                    } else {
                                        val normalized = transform.screenToPage(screen) ?: return@detectTapGestures
                                        scope.launch {
                                            val id = db.pointDao().insert(Point(
                                                drawingId = drawingId,
                                                x = normalized.x,
                                                y = normalized.y,
                                                pageNumber = activePage
                                            ))
                                            newlyCreatedId = id
                                            selectedPointId = id
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(pageRect, viewportSize) {
                            detectTransformGestures { centroid, drag, zoom, _ ->
                                if (draggedPointId != null) return@detectTransformGestures
                                val rect = pageRect ?: return@detectTransformGestures
                                val nextScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                                val d = centroid - center
                                val nextPan = d - (d - pan) / scale * nextScale + drag
                                scale = nextScale
                                pan = clampPagePan(nextPan, nextScale, rect, Offset(viewportSize.width.toFloat(), viewportSize.height.toFloat()))
                            }
                        }
                ) {
                    if (showPoints) {
                        points.forEachIndexed { index, point ->
                            val density = LocalDensity.current
                            var dragScreen by remember(point.id, activePage) { mutableStateOf<Offset?>(null) }
                            val position = dragScreen ?: transform.pageToScreen(Offset(point.x, point.y))
                            Box(
                                Modifier.offset(
                                    x = with(density) { position.x.toDp() } - MARKER_SIZE / 2,
                                    y = with(density) { position.y.toDp() } - MARKER_SIZE / 2
                                )
                                    .size(MARKER_SIZE)
                                    .background(pointColors[point.colorIndex.coerceIn(0, pointColors.lastIndex)], CircleShape)
                                    .pointerInput(point.id, point.isFixed) {
                                        // A fixed point attaches no drag detector at all, so it
                                        // cannot be nudged and the gesture falls through to panning.
                                        if (point.isFixed) return@pointerInput
                                        detectDragGestures(
                                            onDragStart = {
                                                draggedPointId = point.id
                                                dragScreen = latestTransform?.pageToScreen(Offset(point.x, point.y))
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                val current = dragScreen ?: latestTransform?.pageToScreen(Offset(point.x, point.y)) ?: Offset.Zero
                                                dragScreen = current + amount
                                            },
                                            onDragEnd = {
                                                val final = dragScreen
                                                dragScreen = null
                                                draggedPointId = null
                                                if (final != null) {
                                                    val normalized = latestTransform?.screenToPageClamped(final) ?: return@detectDragGestures
                                                    scope.launch { db.pointDao().updatePosition(point.id, normalized.x, normalized.y) }
                                                }
                                            },
                                            onDragCancel = { dragScreen = null; draggedPointId = null }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${index + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                DropdownMenu(expanded = menuPointId == point.id, onDismissRequest = { menuPointId = null }) {
                                    DropdownMenuItem(
                                        text = { Text("Закрепить") },
                                        enabled = !point.isFixed,
                                        onClick = { menuPointId = null; scope.launch { db.pointDao().setFixed(point.id, true) } }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Переместить") },
                                        enabled = point.isFixed,
                                        onClick = { menuPointId = null; scope.launch { db.pointDao().setFixed(point.id, false) } }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Удалить") },
                                        onClick = { menuPointId = null; confirmingDeleteId = point.id }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                (openError ?: pageError)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).padding(24.dp)) }
                    ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            Column(Modifier.align(Alignment.TopEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = {
                    val next = (scale * 1.5f).coerceAtMost(MAX_SCALE)
                    scale = next
                    pageRect?.let { pan = clampPagePan(pan, next, it, Offset(viewportSize.width.toFloat(), viewportSize.height.toFloat())) }
                }, modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Приблизить")
                }
                IconButton(onClick = {
                    val next = (scale / 1.5f).coerceAtLeast(MIN_SCALE)
                    scale = next
                    pageRect?.let { pan = clampPagePan(pan, next, it, Offset(viewportSize.width.toFloat(), viewportSize.height.toFloat())) }
                }, modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Отдалить")
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { activePage-- }, enabled = activePage > 1 && pageCount > 1) { Text("Предыдущая") }
            Text("Страница $activePage из $pageCount", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { activePage++ }, enabled = activePage < pageCount) { Text("Следующая") }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp).clickable {
                scope.launch {
                    val id = db.pointDao().insert(Point(drawingId = drawingId, x = 0.5f, y = 0.5f, pageNumber = activePage))
                    newlyCreatedId = id
                    selectedPointId = id
                }
            }.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            Text("Добавить точку", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(start = 8.dp))
        }
    }

    confirmingDeleteId?.let { id ->
        ConfirmDialog(
            text = "Вы уверены, что хотите удалить точку?",
            onConfirm = { confirmingDeleteId = null; scope.launch { db.deletePoint(id) } },
            onDismiss = { confirmingDeleteId = null }
        )
    }
}
