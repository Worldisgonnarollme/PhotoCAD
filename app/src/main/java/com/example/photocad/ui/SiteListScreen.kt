// список объектов

package com.example.photocad.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.photocad.data.*
import com.example.photocad.ui.components.DashedAddCard
import com.example.photocad.ui.components.ObjectCard
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

@Composable
fun SiteListScreen(db: AppDatabase, onOpenSite: (Long) -> Unit, onSiteReport: (Long) -> Unit = {}) {
    var creating by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<Site?>(null) }
    val summaries by db.siteDao().getAllSummaries().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportSite by remember { mutableStateOf<SiteSummary?>(null) }
    var preparedImport by remember { mutableStateOf<PreparedObjectArchive?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var completedExport by remember { mutableStateOf<Uri?>(null) }
    DisposableEffect(Unit) { onDispose { preparedImport?.close() } }
    val createArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val selected = exportSite
        exportSite = null
        if (uri != null && selected != null) operation = scope.launch {
            busy = "Подготовка архива"
            try {
                exportObjectArchive(context, db, selected.id, uri) { busy = it }
                completedExport = uri
                message = "Объект экспортирован"
            } catch (_: CancellationException) { message = "Экспорт отменён" }
            catch (failure: Exception) { message = failure.message ?: "Не удалось экспортировать объект" }
            finally { busy = null; operation = null }
        }
    }
    val openArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) operation = scope.launch {
            busy = "Проверка архива"
            try { preparedImport = prepareObjectArchive(context, uri) { busy = it } }
            catch (_: CancellationException) { message = "Импорт отменён" }
            catch (failure: Exception) { message = failure.message ?: "Не удалось открыть архив" }
            finally { busy = null; operation = null }
        }
    }

    exportSite?.let { selected ->
        AlertDialog(
            onDismissRequest = { exportSite = null },
            title = { Text("Экспорт объекта") },
            text = { Text("${selected.name}\nЧертежей: ${selected.blueprintCount}, точек: ${selected.pointCount}. Фотографии и данные отчёта тоже войдут в архив.") },
            confirmButton = { TextButton(onClick = {
                val filename = selected.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "_").trim().ifBlank { "object" }.take(60)
                createArchive.launch("$filename.photocad")
            }) { Text("Выбрать файл") } },
            dismissButton = { TextButton(onClick = { exportSite = null }) { Text("Отмена") } }
        )
    }
    preparedImport?.let { prepared ->
        val preview = prepared.preview
        AlertDialog(
            onDismissRequest = { prepared.close(); preparedImport = null },
            title = { Text("Импорт объекта") },
            text = { Text("${preview.name}\nЧертежей: ${preview.drawingCount}, точек: ${preview.pointCount}, фотографий: ${preview.photoCount}. Объект будет добавлен как новый.") },
            confirmButton = { TextButton(onClick = {
                preparedImport = null
                operation = scope.launch {
                    busy = "Создание объекта"
                    try { prepared.import(context, db) { busy = it }; message = "Объект импортирован" }
                    catch (_: CancellationException) { message = "Импорт отменён" }
                    catch (failure: Exception) { message = failure.message ?: "Не удалось импортировать объект" }
                    finally { busy = null; operation = null }
                }
            }) { Text("Импортировать") } },
            dismissButton = { TextButton(onClick = { prepared.close(); preparedImport = null }) { Text("Отмена") } }
        )
    }
    busy?.let { status -> AlertDialog(onDismissRequest = {}, title = { Text("Подождите") }, text = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp)); Text(status)
        }
    }, confirmButton = { TextButton(onClick = { operation?.cancel() }) { Text("Отмена") } }) }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null; completedExport = null }, title = { Text("PhotoCAD") }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { message = null; completedExport = null }) { Text("OK") } },
        dismissButton = { completedExport?.let { uri -> TextButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Отправить объект"))
            completedExport = null
            message = null
        }) { Text("Отправить") } } }
    ) }

    when {
        creating -> CreateObjectScreen(db = db, editing = null, onBack = { creating = false }, onSaved = { creating = false })
        editingSite != null -> CreateObjectScreen(
            db = db, editing = editingSite,
            onBack = { editingSite = null },
            onSaved = { editingSite = null }
        )
        else -> Column(Modifier.statusBarsPadding().fillMaxSize()) {
            Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp))
                        }
                        Text("PhotoCAD", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { openArchive.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) { Text("Загрузить архив объекта", style = MaterialTheme.typography.labelLarge) }
                        IconButton(
                            onClick = { creating = true },
                            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Добавить объект", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Мои объекты", style = MaterialTheme.typography.titleMedium)
                Text("${summaries.size} объекта", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(summaries, key = { it.id }) { summary ->
                    ObjectCard(
                        summary = summary,
                        onClick = { onOpenSite(summary.id) },
                        onSiteReport = { onSiteReport(summary.id) },
                        onExport = { exportSite = summary },
                        onEdit = {
                            editingSite = Site(summary.id, summary.name, summary.address, summary.description)
                        }
                    )
                }
                item {
                    DashedAddCard(label = "Добавить объект", onClick = { creating = true })
                }
            }
        }
    }
}
