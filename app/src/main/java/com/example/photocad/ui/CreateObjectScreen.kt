// полноэкранная форма создания/редактирования объекта

package com.example.photocad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.photocad.data.*
import com.example.photocad.ui.components.DashedAddCard
import com.example.photocad.ui.components.PrimaryButton
import com.example.photocad.ui.components.SectionCard
import com.example.photocad.ui.theme.FieldShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateObjectScreen(db: AppDatabase, editing: Site?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var address by rememberSaveable { mutableStateOf(editing?.address ?: "") }
    var description by rememberSaveable { mutableStateOf(editing?.description ?: "") }
    var pendingBlueprints by rememberSaveable { mutableStateOf(listOf<String>()) }
    var importError by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val pickBlueprint = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    pendingBlueprints = pendingBlueprints + copyUriToAppStorage(context, it)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    importError = "Не удалось добавить чертёж: ${failure.message}"
                }
            }
        }
    }

    importError?.let { message ->
        AlertDialog(onDismissRequest = { importError = null }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("Закрыть") } })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "Новый объект" else "Изменить объект") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Название *", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                            shape = FieldShape, modifier = Modifier.fillMaxWidth())
                        Text("Адрес (опционально)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = address, onValueChange = { address = it }, singleLine = true,
                            shape = FieldShape, modifier = Modifier.fillMaxWidth())
                        Text("Описание (опционально)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = description, onValueChange = { description = it }, minLines = 3,
                            shape = FieldShape, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (editing == null) {
                item {
                    SectionCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Чертежи", style = MaterialTheme.typography.titleMedium)
                                Text("PDF / PNG / JPEG", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            pendingBlueprints.forEachIndexed { index, path ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(File(path).name, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                                    IconButton(onClick = {
                                        File(path).delete()
                                        pendingBlueprints = pendingBlueprints.toMutableList().also { it.removeAt(index) }
                                    }) { Icon(Icons.Default.Close, contentDescription = "Убрать чертёж") }
                                }
                            }
                            DashedAddCard(label = "Добавить чертёж", onClick = { pickBlueprint.launch("image/*") })
                        }
                    }
                }
            }

            if (editing != null) {
                item {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = { confirmingDelete = true }
                    ) { Text("Удалить объект") }
                }
            }

            item {
                PrimaryButton(
                    text = if (editing == null) "Создать" else "Сохранить",
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            if (editing == null) {
                                val siteId = db.siteDao().insert(Site(name = name.trim(), address = address.trim(), description = description.trim()))
                                pendingBlueprints.forEachIndexed { index, path ->
                                    db.drawingDao().insert(Drawing(name = "Чертёж ${index + 1}", filePath = path, siteId = siteId))
                                }
                            } else {
                                db.siteDao().update(editing.id, name.trim(), address.trim(), description.trim())
                            }
                            onSaved()
                        }
                    }
                )
            }
        }
    }

    if (confirmingDelete && editing != null) {
        ConfirmDialog(
            text = "Удалить объект со всеми чертежами, точками и фото? Это необратимо.",
            onConfirm = {
                confirmingDelete = false
                scope.launch { db.deleteSite(editing.id); onSaved() }
            },
            onDismiss = { confirmingDelete = false }
        )
    }
}
