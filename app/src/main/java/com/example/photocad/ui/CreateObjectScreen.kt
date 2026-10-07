// полноэкранная форма создания/редактирования объекта

package com.example.photocad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.room.withTransaction
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
import androidx.compose.runtime.saveable.listSaver
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

private val reportDetailsSaver = listSaver<SiteReportDetails, String>(
    save = { listOf(it.siteId.toString(), it.organizationName, it.organizationAddress, it.phone, it.email,
        it.inn, it.kpp, it.ogrn, it.customer, it.city, it.year, it.albumNumber) },
    restore = { SiteReportDetails(it[0].toLong(), it[1], it[2], it[3], it[4], it[5], it[6], it[7], it[8], it[9], it[10], it[11]) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateObjectScreen(db: AppDatabase, editing: Site?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var address by rememberSaveable { mutableStateOf(editing?.address ?: "") }
    var description by rememberSaveable { mutableStateOf(editing?.description ?: "") }
    var reportDetails by rememberSaveable(editing?.id, stateSaver = reportDetailsSaver) {
        mutableStateOf(SiteReportDetails(editing?.id ?: 0))
    }
    var showReportDetails by rememberSaveable(editing?.id) { mutableStateOf(editing == null) }
    var skipReportDetails by rememberSaveable(editing?.id) { mutableStateOf(false) }
    var detailsLoaded by rememberSaveable(editing?.id) { mutableStateOf(editing == null) }
    var pendingBlueprints by rememberSaveable { mutableStateOf(listOf<String>()) }
    var importError by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(editing?.id) {
        if (!detailsLoaded) editing?.let { site ->
            db.siteDao().getReportDetails(site.id)?.let { saved ->
                reportDetails = saved
                showReportDetails = true
            }
            detailsLoaded = true
        }
    }

    val pickBlueprint = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    pendingBlueprints = pendingBlueprints + importDrawing(context, it).path
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    importError = "Не удалось добавить чертёж: ${failure.message}"
                }
            }
        }
    }

    fun discardDraftAndBack() {
        pendingBlueprints.forEach { File(it).delete() }
        pendingBlueprints = emptyList()
        onBack()
    }

    importError?.let { message ->
        AlertDialog(onDismissRequest = { importError = null }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("Закрыть") } })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "Новый объект" else "Изменить объект") },
                navigationIcon = { IconButton(onClick = ::discardDraftAndBack) { Icon(Icons.Default.ArrowBack, null) } }
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

            item {
                if (showReportDetails) {
                    SectionCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Данные для титульного листа", style = MaterialTheme.typography.titleMedium)
                            Text("Сохраняются у объекта как значения по умолчанию. В каждом фотоотчёте их можно изменить.",
                                style = MaterialTheme.typography.bodySmall)
                            if (editing == null) {
                                TextButton(onClick = { skipReportDetails = true; showReportDetails = false }) {
                                    Text("Пропустить, заполнить в отчёте")
                                }
                            } else {
                                TextButton(onClick = { showReportDetails = false }) { Text("Свернуть") }
                            }
                            @Composable fun field(label: String, value: String, change: (String) -> SiteReportDetails) {
                                OutlinedTextField(value = value, onValueChange = { reportDetails = change(it) },
                                    label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            }
                            field("Название организации", reportDetails.organizationName) { reportDetails.copy(organizationName = it) }
                            field("Адрес организации", reportDetails.organizationAddress) { reportDetails.copy(organizationAddress = it) }
                            field("Телефон", reportDetails.phone) { reportDetails.copy(phone = it) }
                            field("E-mail", reportDetails.email) { reportDetails.copy(email = it) }
                            field("ИНН", reportDetails.inn) { reportDetails.copy(inn = it) }
                            field("КПП", reportDetails.kpp) { reportDetails.copy(kpp = it) }
                            field("ОГРН", reportDetails.ogrn) { reportDetails.copy(ogrn = it) }
                            field("Заказчик", reportDetails.customer) { reportDetails.copy(customer = it) }
                            field("Город", reportDetails.city) { reportDetails.copy(city = it) }
                            field("Год", reportDetails.year) { reportDetails.copy(year = it) }
                            field("Номер фотоотчёта", reportDetails.albumNumber) { reportDetails.copy(albumNumber = it) }
                        }
                    }
                } else {
                    OutlinedButton(onClick = { skipReportDetails = false; showReportDetails = true }) {
                        Text(if (editing == null) "Заполнить данные титульного листа" else "Изменить данные титульного листа")
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
                            DashedAddCard(label = "Добавить чертёж", onClick = {
                                pickBlueprint.launch(arrayOf("application/pdf", "image/png", "image/jpeg"))
                            })
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
                                val paths = pendingBlueprints
                                try {
                                    db.withTransaction {
                                        val siteId = db.siteDao().insert(Site(name = name.trim(), address = address.trim(), description = description.trim()))
                                        if (!skipReportDetails) db.siteDao().saveReportDetails(reportDetails.copy(siteId = siteId))
                                        paths.forEachIndexed { index, path ->
                                            db.drawingDao().insert(Drawing(name = "Чертёж ${index + 1}", filePath = path, siteId = siteId))
                                        }
                                    }
                                    pendingBlueprints = emptyList()
                                    onSaved()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    paths.forEach { File(it).delete() }
                                    pendingBlueprints = emptyList()
                                    importError = "Не удалось создать объект: ${failure.message}"
                                }
                            } else {
                                try {
                                    db.withTransaction {
                                        db.siteDao().update(editing.id, name.trim(), address.trim(), description.trim())
                                        if (showReportDetails) db.siteDao().saveReportDetails(reportDetails.copy(siteId = editing.id))
                                    }
                                    onSaved()
                                } catch (failure: Exception) {
                                    importError = "Не удалось сохранить объект: ${failure.message}"
                                }
                            }
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
