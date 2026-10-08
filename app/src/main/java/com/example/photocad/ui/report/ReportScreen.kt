package com.example.photocad.ui.report

import android.app.Application
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photocad.data.AppDatabase
import com.example.photocad.data.PointColors
import com.example.photocad.report.AlbumCover
import com.example.photocad.report.CaptionSource
import com.example.photocad.report.DraftPhoto
import com.example.photocad.report.ReportFileManager
import com.example.photocad.report.reportInput
import com.example.photocad.ui.PhotoThumbnail
import com.example.photocad.ui.components.PrimaryButton
import com.example.photocad.ui.components.SectionCard

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
        TopAppBar(title = { Text("Фотоотчёт: ${state.drawingName}") }, navigationIcon = {
            TextButton(onClick = { confirmExit = true }) { Text("Назад") }
        })
    }, bottomBar = {
        if (state.step == ReportStep.COVER || state.step == ReportStep.PHOTOS) {
            Surface(shadowElevation = 6.dp) {
                Box(Modifier.fillMaxWidth().padding(12.dp)) {
                    StepNavigation(!state.busy, state.step != ReportStep.COVER, model::previous, model::next)
                }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) item { CircularProgressIndicator() }
            state.message?.let { message -> item {
                Text(message, color = if (message.startsWith("Не удалось") || message.contains("отсутствует"))
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { model.showMessage(null) }) { Text("Скрыть сообщение") }
            } }
            if (!state.initialized && !state.loading) item { Button(onClick = model::open) { Text("Повторить загрузку") } }
            if (state.initialized) when (state.step) {
                ReportStep.COVER -> {
                    item {
                        Text("Титульный лист", style = MaterialTheme.typography.headlineSmall)
                        Text("Изменения относятся только к этому фотоотчёту и не меняют данные объекта.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    item { CoverFields(state.cover, !state.busy, model::editCover) }
                }
                ReportStep.PHOTOS -> {
                    item {
                        Text("Состав фотоотчёта",
                            style = MaterialTheme.typography.headlineSmall)
                        Text("Выберите фотографии и порядок. Описание фотографии будет показано в итоговом PDF.",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Выбрано: ${state.selectedCount} • Страниц: ${state.pageCount}",
                            style = MaterialTheme.typography.labelLarge)
                    }
                    if (state.photos.isEmpty()) item { Text("У точек этого чертежа пока нет фотографий.") }
                    itemsIndexed(state.photos, key = { _, photo -> photo.photoId }) { index, photo ->
                        PhotoDraftCard(photo, index, state.photos.size, state.busy,
                            onSelect = { model.select(photo.photoId, it) },
                            onMove = { model.move(photo.photoId, it) },
                            onEdit = { model.edit(photo.photoId, it) },
                            onSave = { model.saveDescription(photo.photoId) })
                    }
                }
                ReportStep.PREVIEW -> {
                    item {
                        Text("Предварительный просмотр", style = MaterialTheme.typography.headlineSmall)
                        Text("Последовательность страниц: ${state.pageCount}")
                        Text("1. Титульный лист")
                        Text("2. Общая информация")
                        // Built from the same function the PDF uses, so the list cannot drift from it.
                        reportInput(state.photos).forEachIndexed { index, photo ->
                            val position = if (photo.photoCountInPoint > 1)
                                " • фото ${photo.photoIndexInPoint} из ${photo.photoCountInPoint}" else ""
                            Text("${index + 3}. лист ${photo.drawingPage} • Точка №${photo.pointNumber}$position: ${photo.description}")
                        }
                    }
                    item { OutlinedButton(onClick = model::previous, enabled = !state.busy && !state.saved) { Text("К предыдущему шагу") } }
                    item { PrimaryButton(text = "Сформировать PDF", onClick = model::generate, enabled = !state.busy && !state.saved) }
                    state.operation?.let { operation -> item {
                        Text(operation)
                        if (operation == "Формирование PDF") {
                            Text("${state.progress} / ${state.total}")
                            TextButton(onClick = model::cancelGeneration) { Text("Отменить формирование") }
                        }
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } }
                    state.generated?.let { generated ->
                        item { PdfPreview(generated.file, generated.pageCount) }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton(text = "Сохранить PDF", enabled = !state.busy, onClick = {
                                    try { createDocument.launch("PhotoCAD-${drawingId}-${System.currentTimeMillis()}.pdf") }
                                    catch (failure: Exception) { model.showMessage("Не удалось открыть выбор места сохранения: ${failure.message}") }
                                })
                                OutlinedButton(onClick = {
                                    try { context.startActivity(Intent.createChooser(ReportFileManager.shareIntent(context, generated.file), "Поделиться PDF")) }
                                    catch (failure: Exception) { model.showMessage("Не удалось отправить PDF: ${failure.message}") }
                                }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(" Поделиться")
                                }
                            }
                        }
                    }
                }
                ReportStep.COMPLETE -> item {
                    SectionCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Фотоотчёт успешно сохранён", style = MaterialTheme.typography.headlineSmall)
                            if (!state.archiveDecisionDone) {
                                Text("Архивные точки останутся в базе с фотографиями и исчезнут с рабочего чертежа. Точки с не включёнными фотографиями останутся активными.")
                                PrimaryButton(text = "Архивировать использованные точки", onClick = model::archiveUsedPoints, enabled = !state.busy)
                                OutlinedButton(onClick = model::leavePointsActive, enabled = !state.busy) { Text("Оставить точки активными") }
                            }
                            state.operation?.let { Text(it); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverFields(cover: AlbumCover, enabled: Boolean, onChange: (AlbumCover) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        @Composable fun field(label: String, value: String, change: (String) -> AlbumCover) {
            OutlinedTextField(value, { onChange(change(it)) }, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, enabled = enabled)
        }
        field("Название организации", cover.organizationName) { cover.copy(organizationName = it) }
        field("Адрес организации", cover.organizationAddress) { cover.copy(organizationAddress = it) }
        field("Телефон", cover.phone) { cover.copy(phone = it) }
        field("E-mail", cover.email) { cover.copy(email = it) }
        field("ИНН", cover.inn) { cover.copy(inn = it) }
        field("КПП", cover.kpp) { cover.copy(kpp = it) }
        field("ОГРН", cover.ogrn) { cover.copy(ogrn = it) }
        field("Заказчик", cover.customer) { cover.copy(customer = it) }
        field("Название объекта", cover.objectName) { cover.copy(objectName = it) }
        field("Адрес объекта", cover.objectAddress) { cover.copy(objectAddress = it) }
        field("Город", cover.city) { cover.copy(city = it) }
        field("Год", cover.year) { cover.copy(year = it) }
        field("Номер фотоотчёта", cover.albumNumber) { cover.copy(albumNumber = it) }
    }
}

@Composable
private fun PhotoDraftCard(photo: DraftPhoto, index: Int, total: Int, busy: Boolean,
                           onSelect: (Boolean) -> Unit, onMove: (Int) -> Unit,
                           onEdit: (String) -> Unit, onSave: () -> Unit) {
    SectionCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = photo.selected, enabled = !busy, onCheckedChange = onSelect)
                PhotoThumbnail(photo.filePath, Modifier.size(72.dp))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(if (photo.selected) "Фотография ${index + 1}" else "Исключена из PDF", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(Color(PointColors.argb(photo.colorIndex)), CircleShape))
                        Text("  Точка №${photo.pointNumber} • лист ${photo.drawingPage}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(when (photo.captionSource) {
                        CaptionSource.PHOTO_DESCRIPTION -> "Источник: описание фотографии"
                        CaptionSource.POINT_COMMENT -> "Источник: комментарий точки"
                        CaptionSource.MISSING -> "Источник: не заполнен"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (photo.selected && photo.description.isBlank())
                        Text("Описание отсутствует", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Row {
                TextButton(onClick = { onMove(-1) }, enabled = !busy && index > 0) { Text("↑ Выше") }
                TextButton(onClick = { onMove(1) }, enabled = !busy && index < total - 1) { Text("↓ Ниже") }
            }
            OutlinedTextField(value = photo.description, onValueChange = onEdit, enabled = !busy,
                label = { Text("Описание фотографии") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            TextButton(onClick = onSave, enabled = !busy) { Text("Сохранить описание фотографии") }
        }
    }
}

@Composable
private fun StepNavigation(enabled: Boolean, hasPrevious: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasPrevious) OutlinedButton(onClick = onPrevious, enabled = enabled) { Text("К предыдущему шагу") }
        PrimaryButton(text = "Продолжить", onClick = onNext, enabled = enabled)
    }
}
