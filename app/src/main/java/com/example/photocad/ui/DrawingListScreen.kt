// список чертежей

package com.example.photocad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.photocad.data.*
import com.example.photocad.ui.components.BlueprintRowCard
import com.example.photocad.ui.components.DashedAddCard
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingListScreen(db: AppDatabase, siteId: Long, onBack: () -> Unit, onOpenDrawing: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importError by remember { mutableStateOf<String?>(null) }
    var pendingImportPath by rememberSaveable { mutableStateOf<String?>(null) }
    var editingDrawing by remember { mutableStateOf<Drawing?>(null) }
    var site by remember { mutableStateOf<Site?>(null) }
    val drawings by db.drawingDao().getBySiteWithPointCount(siteId).collectAsState(initial = emptyList())

    LaunchedEffect(siteId) { site = db.siteDao().getById(siteId) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    pendingImportPath = importDrawing(context, it).path
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { importError = "Не удалось добавить чертёж: ${failure.message}" }
            }
        }
    }

    importError?.let { message ->
        AlertDialog(onDismissRequest = { importError = null }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("Закрыть") } })
    }

    when {
        pendingImportPath != null -> CreateDrawingScreen(
            title = "Новый чертёж",
            initialName = "Чертёж ${drawings.size + 1}",
            initialDescription = "",
            confirmLabel = "Создать",
            onBack = { File(pendingImportPath!!).delete(); pendingImportPath = null },
            onConfirm = { name, description ->
                scope.launch {
                    val importedPath = pendingImportPath ?: return@launch
                    try {
                        db.drawingDao().insert(Drawing(name = name, filePath = importedPath, siteId = siteId, description = description))
                        pendingImportPath = null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        File(importedPath).delete()
                        pendingImportPath = null
                        importError = "Не удалось добавить чертёж: ${failure.message}"
                    }
                }
            },
            onDelete = null
        )
        editingDrawing != null -> CreateDrawingScreen(
            title = "Изменить чертёж",
            initialName = editingDrawing!!.name,
            initialDescription = editingDrawing!!.description,
            confirmLabel = "Сохранить",
            onBack = { editingDrawing = null },
            onConfirm = { name, description ->
                scope.launch { db.drawingDao().update(editingDrawing!!.id, name, description); editingDrawing = null }
            },
            onDelete = { scope.launch { db.deleteDrawing(editingDrawing!!.id); editingDrawing = null } }
        )
        else -> Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(site?.name ?: "")
                            if (!site?.address.isNullOrBlank()) {
                                Text(site!!.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                    actions = {
                        IconButton(
                            onClick = { pickImage.launch(arrayOf("application/pdf", "image/png", "image/jpeg")) },
                            modifier = Modifier.padding(end = 8.dp).size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) { Icon(Icons.Default.Add, contentDescription = "Добавить чертёж", tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("ЧЕРТЕЖИ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(drawings, key = { it.id }) { drawing ->
                    BlueprintRowCard(
                        name = drawing.name,
                        subtitle = "${drawing.pointCount} точек",
                        onClick = { onOpenDrawing(drawing.id) },
                        onEdit = { editingDrawing = Drawing(drawing.id, drawing.name, drawing.filePath, drawing.siteId, drawing.description) }
                    )
                }
                item {
                    DashedAddCard(label = "Добавить чертёж", onClick = {
                        pickImage.launch(arrayOf("application/pdf", "image/png", "image/jpeg"))
                    })
                }
            }
        }
    }
}
