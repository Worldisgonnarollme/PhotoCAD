// список чертежей

package com.example.photocad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.photocad.data.*
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
    var siteName by remember { mutableStateOf("") }
    val drawings by db.drawingDao().getBySite(siteId).collectAsState(initial = emptyList())

    LaunchedEffect(siteId) { siteName = db.siteDao().getById(siteId)?.name ?: "" }

    // считывает все чертежи
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    pendingImportPath = copyUriToAppStorage(context, it)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { importError = "Не удалось добавить чертёж: ${failure.message}" }
            }
        }
    }

    importError?.let { message ->
        AlertDialog(onDismissRequest = { importError = null }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("Закрыть") } })
    }

    pendingImportPath?.let { path ->
        DrawingDetailsDialog(
            title = "Новый чертёж",
            initialName = "Чертёж ${drawings.size + 1}",
            initialDescription = "",
            confirmLabel = "Создать",
            onDismiss = {
                pendingImportPath = null
                File(path).delete()
            },
            onConfirm = { name, description ->
                scope.launch { db.drawingDao().insert(Drawing(name = name, filePath = path, siteId = siteId, description = description)) }
                pendingImportPath = null
            },
            onDelete = null
        )
    }

    editingDrawing?.let { drawing ->
        DrawingDetailsDialog(
            title = "Изменить чертёж",
            initialName = drawing.name,
            initialDescription = drawing.description,
            confirmLabel = "Сохранить",
            onDismiss = { editingDrawing = null },
            onConfirm = { name, description ->
                scope.launch { db.drawingDao().update(drawing.id, name, description) }
                editingDrawing = null
            },
            onDelete = {
                scope.launch { db.deleteDrawing(drawing.id) }
                editingDrawing = null
            }
        )
    }

    // кнопка добавления чертежа
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(siteName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickImage.launch("image/*") }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить чертёж")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(drawings) { drawing ->
                ListItem(
                    headlineContent = { Text(drawing.name) },
                    trailingContent = {
                        IconButton(onClick = { editingDrawing = drawing }) {
                            Icon(Icons.Default.Edit, contentDescription = "Изменить чертёж")
                        }
                    },
                    modifier = Modifier.clickable { onOpenDrawing(drawing.id) } // переход на экран самого чертежа
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DrawingDetailsDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var confirmingDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Описание (опционально)") },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = { confirmingDelete = true }
                    ) { Text("Удалить чертёж") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim(), description.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (confirmingDelete && onDelete != null) {
        ConfirmDialog(
            text = "Удалить чертёж со всеми точками и фото? Это необратимо.",
            onConfirm = { confirmingDelete = false; onDelete() },
            onDismiss = { confirmingDelete = false }
        )
    }
}