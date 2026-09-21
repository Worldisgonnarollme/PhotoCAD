// экран просмотра чертежа (тёмный hero-экран) и переход к редактору точки

package com.example.photocad.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photocad.data.*
import com.example.photocad.ui.theme.ViewerColors
import kotlinx.coroutines.launch

// красный/синий/зелёный/оранжевый/фиолетовый/серый; индекс 0 — цвет по умолчанию для старых точек
val pointColors = listOf(
    Color(0xFFF44336), Color(0xFF2196F3), Color(0xFF4CAF50),
    Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF9E9E9E)
)

// экран одного чертежа
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
    val points by db.pointDao().getByDrawing(drawingId).collectAsState(initial = emptyList())
    var selectedPointId by rememberSaveable(drawingId) { mutableStateOf<Long?>(null) }
    var newlyCreatedId by rememberSaveable(drawingId) { mutableStateOf<Long?>(null) }
    var showPoints by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    val bitmap = remember(drawing) { drawing?.let { BitmapFactory.decodeFile(it.filePath)?.asImageBitmap() } }

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

    MaterialTheme(colorScheme = ViewerColors) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopAppBar(
                title = {
                    Column {
                        Text(drawing?.name ?: "", color = MaterialTheme.colorScheme.onBackground)
                        site?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground) }
                },
                actions = {
                    IconButton(onClick = { showPoints = !showPoints }) {
                        Icon(Icons.Default.Layers, contentDescription = "Показать/скрыть точки",
                            tint = if (showPoints) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onReport) {
                        Icon(Icons.Default.Share, contentDescription = "Сформировать отчёт", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                bitmap?.let { bmp ->
                    var boxSize by remember { mutableStateOf(IntSize.Zero) }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = scale, scaleY = scale)
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
                                        scope.launch {
                                            val id = db.pointDao().insert(Point(drawingId = drawingId, x = fx, y = fy))
                                            newlyCreatedId = id
                                            selectedPointId = id
                                        }
                                    }
                                }
                            }
                    ) {
                        Image(bitmap = bmp, contentDescription = null, modifier = Modifier.fillMaxSize())
                        if (showPoints) {
                            points.forEachIndexed { index, p ->
                                val density = LocalDensity.current
                                var dragOffsetPx by remember(p.id) { mutableStateOf<Offset?>(null) }
                                val currentPx = dragOffsetPx ?: Offset(p.x * boxSize.width, p.y * boxSize.height)
                                Box(
                                    Modifier
                                        .offset(
                                            x = with(density) { currentPx.x.toDp() } - 14.dp,
                                            y = with(density) { currentPx.y.toDp() } - 14.dp
                                        )
                                        .size(28.dp)
                                        .background(pointColors[p.colorIndex.coerceIn(0, pointColors.lastIndex)], CircleShape)
                                        .pointerInput(p.id, boxSize) {
                                            detectDragGestures(
                                                onDragStart = { dragOffsetPx = Offset(p.x * boxSize.width, p.y * boxSize.height) },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val base = dragOffsetPx ?: Offset(p.x * boxSize.width, p.y * boxSize.height)
                                                    dragOffsetPx = Offset(
                                                        (base.x + dragAmount.x).coerceIn(0f, boxSize.width.toFloat()),
                                                        (base.y + dragAmount.y).coerceIn(0f, boxSize.height.toFloat())
                                                    )
                                                },
                                                onDragEnd = {
                                                    val final = dragOffsetPx
                                                    dragOffsetPx = null
                                                    if (final != null && boxSize.width > 0 && boxSize.height > 0) {
                                                        val fx = (final.x / boxSize.width).coerceIn(0f, 1f)
                                                        val fy = (final.y / boxSize.height).coerceIn(0f, 1f)
                                                        scope.launch { db.pointDao().updatePosition(p.id, fx, fy) }
                                                    }
                                                },
                                                onDragCancel = { dragOffsetPx = null }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${index + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Column(
                    Modifier.align(Alignment.TopEnd).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { scale = (scale + 0.25f).coerceAtMost(3f) },
                        modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(10.dp))
                    ) { Icon(Icons.Default.ZoomIn, contentDescription = "Приблизить", tint = Color(0xFF374151)) }
                    IconButton(
                        onClick = { scale = (scale - 0.25f).coerceAtLeast(1f) },
                        modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(10.dp))
                    ) { Icon(Icons.Default.ZoomOut, contentDescription = "Отдалить", tint = Color(0xFF374151)) }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(16.dp).clickable {
                    scope.launch {
                        val id = db.pointDao().insert(Point(drawingId = drawingId, x = 0.5f, y = 0.5f))
                        newlyCreatedId = id
                        selectedPointId = id
                    }
                }.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                Text("Добавить точку", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
