// список объектов

package com.example.photocad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.photocad.data.*
import kotlinx.coroutines.launch

@Composable
fun SiteListScreen(db: AppDatabase, onOpenSite: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<Site?>(null) }
    val sites by db.siteDao().getAll().collectAsState(initial = emptyList())

    if (creating) {
        SiteDetailsDialog(
            title = "Новый объект",
            initialName = "",
            initialAddress = "",
            initialDescription = "",
            confirmLabel = "Создать",
            onDismiss = { creating = false },
            onConfirm = { name, address, description ->
                scope.launch { db.siteDao().insert(Site(name = name, address = address, description = description)) }
                creating = false
            },
            onDelete = null
        )
    }

    editingSite?.let { site ->
        SiteDetailsDialog(
            title = "Изменить объект",
            initialName = site.name,
            initialAddress = site.address,
            initialDescription = site.description,
            confirmLabel = "Сохранить",
            onDismiss = { editingSite = null },
            onConfirm = { name, address, description ->
                scope.launch { db.siteDao().update(site.id, name, address, description) }
                editingSite = null
            },
            onDelete = {
                scope.launch { db.deleteSite(site.id) }
                editingSite = null
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить объект")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(sites) { site ->
                ListItem(
                    headlineContent = { Text(site.name) },
                    trailingContent = {
                        IconButton(onClick = { editingSite = site }) {
                            Icon(Icons.Default.Edit, contentDescription = "Изменить объект")
                        }
                    },
                    modifier = Modifier.clickable { onOpenSite(site.id) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SiteDetailsDialog(
    title: String,
    initialName: String,
    initialAddress: String,
    initialDescription: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, address: String, description: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var address by rememberSaveable { mutableStateOf(initialAddress) }
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
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Адрес (опционально)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Описание (опционально)") },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = { confirmingDelete = true }
                    ) { Text("Удалить объект") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim(), address.trim(), description.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (confirmingDelete && onDelete != null) {
        ConfirmDialog(
            text = "Удалить объект со всеми чертежами, точками и фото? Это необратимо.",
            onConfirm = { confirmingDelete = false; onDelete() },
            onDismiss = { confirmingDelete = false }
        )
    }
}
