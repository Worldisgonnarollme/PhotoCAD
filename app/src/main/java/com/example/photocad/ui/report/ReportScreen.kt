package com.example.photocad.ui.report

import android.app.Application
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photocad.data.AppDatabase
import com.example.photocad.report.ReportFileManager
import com.example.photocad.ui.PhotoThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(db: AppDatabase, drawingId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val model: ReportViewModel = viewModel(key = "report-$drawingId",
        factory = ReportViewModel.Factory(context.applicationContext as Application, db, drawingId))
    val state by model.state.collectAsState()
    var confirmExit by remember { mutableStateOf(false) }
    LaunchedEffect(model) { model.open() }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) model.savePdf(uri) else model.showMessage("Сохранение отменено. PDF доступен для повторного сохранения.")
    }
    BackHandler { confirmExit = true }
    if (confirmExit) {
        AlertDialog(onDismissRequest = { confirmExit = false }, title = { Text("Закрыть подготовку отчёта?") },
            text = { Text("Временные подписи, выбор и порядок будут сброшены. Описания, явно сохранённые в базе, останутся.") },
            confirmButton = { TextButton(onClick = { model.discard(); onBack() }) { Text("Закрыть") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Продолжить") } })
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Подготовка отчёта") }, navigationIcon = {
            TextButton(onClick = { confirmExit = true }) { Text("Назад") }
        })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(state.drawingName, style = MaterialTheme.typography.titleMedium)
                Text("Фотографий: ${state.selectedCount} • Страниц: ${state.pageCount}")
                Text("Правки подписи действуют только для текущего PDF. Для изменения базы нажмите «Сохранить описание фотографии».")
            }
            if (state.loading) item { CircularProgressIndicator() }
            state.message?.let { message -> item {
                Text(message)
                TextButton(onClick = { model.showMessage(null) }) { Text("Скрыть сообщение") }
            } }
            if (!state.initialized && !state.loading) item {
                Button(onClick = model::open) { Text("Повторить загрузку") }
            }
            if (state.initialized && state.photos.isEmpty()) item { Text("У точек этого чертежа пока нет фотографий.") }
            itemsIndexed(state.photos, key = { _, photo -> photo.photoId }) { index, photo ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(checked = photo.selected, enabled = !state.busy, onCheckedChange = { model.select(photo.photoId, it) })
                            PhotoThumbnail(photo.filePath, Modifier.size(80.dp))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                val number = state.photos.take(index + 1).count { it.selected }
                                Text(if (photo.selected) "Фотография №$number" else "Исключена из PDF")
                                Text("Точка ${photo.pointId}")
                            }
                        }
                        Row {
                            TextButton(onClick = { model.move(photo.photoId, -1) }, enabled = !state.busy && index > 0) { Text("↑ Выше") }
                            TextButton(onClick = { model.move(photo.photoId, 1) }, enabled = !state.busy && index < state.photos.lastIndex) { Text("↓ Ниже") }
                        }
                        OutlinedTextField(value = photo.description, onValueChange = { model.edit(photo.photoId, it) },
                            enabled = !state.busy, label = { Text("Описание для текущего PDF") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        TextButton(onClick = { model.saveDescription(photo.photoId) }, enabled = !state.busy) {
                            Text("Сохранить описание фотографии")
                        }
                    }
                }
            }
            item {
                Button(onClick = model::generate, enabled = !state.busy && state.selectedCount > 0, modifier = Modifier.fillMaxWidth()) {
                    Text("Сформировать PDF")
                }
                state.operation?.let { operation ->
                    Text(operation)
                    if (operation == "Формирование PDF") {
                        Text("${state.progress} / ${state.total}")
                        TextButton(onClick = model::cancelGeneration) { Text("Отменить формирование") }
                    }
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            state.generated?.let { generated ->
                item { PdfPreview(generated.file, generated.pageCount) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            try { createDocument.launch("PhotoCAD-${drawingId}-${System.currentTimeMillis()}.pdf") }
                            catch (failure: Exception) { model.showMessage("Не удалось открыть выбор места сохранения: ${failure.message}") }
                        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Сохранить PDF") }
                        OutlinedButton(onClick = {
                            try { context.startActivity(Intent.createChooser(ReportFileManager.shareIntent(context, generated.file), "Поделиться PDF")) }
                            catch (failure: Exception) { model.showMessage("Не удалось отправить PDF: ${failure.message}") }
                        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Поделиться") }
                        OutlinedButton(onClick = {
                            try { context.startActivity(ReportFileManager.openIntent(context, generated.file)) }
                            catch (_: Exception) { model.showMessage("Не найдено приложение для открытия PDF или файл недоступен") }
                        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Открыть в другом приложении") }
                    }
                }
            }
        }
    }
}
