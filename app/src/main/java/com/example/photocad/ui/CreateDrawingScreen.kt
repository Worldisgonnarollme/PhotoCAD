// полноэкранная форма создания/редактирования чертежа

package com.example.photocad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.photocad.ui.components.PrimaryButton
import com.example.photocad.ui.components.SectionCard
import com.example.photocad.ui.theme.FieldShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDrawingScreen(
    title: String,
    initialName: String,
    initialDescription: String,
    confirmLabel: String,
    onBack: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                        Text("Описание (опционально)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = description, onValueChange = { description = it }, minLines = 3,
                            shape = FieldShape, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (onDelete != null) {
                item {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = { confirmingDelete = true }
                    ) { Text("Удалить чертёж") }
                }
            }
            item {
                PrimaryButton(text = confirmLabel, enabled = name.isNotBlank(), onClick = { onConfirm(name.trim(), description.trim()) })
            }
        }
    }

    if (confirmingDelete && onDelete != null) {
        ConfirmDialog(
            text = "Удалить чертёж со всеми точками и фото? Это необратимо.",
            onConfirm = { confirmingDelete = false; onDelete() },
            onDismiss = { confirmingDelete = false }
        )
    }
}
