package com.example.photocad.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

// onDraftChange lets the screen's own save button pick up text the user typed but never confirmed.
@Composable
fun PhotoDescriptionEditor(photoId: Long, savedDescription: String?, busy: Boolean,
                           onDraftChange: (String) -> Unit = {}, onSave: (String) -> Unit) {
    var description by rememberSaveable(photoId) { mutableStateOf(savedDescription.orEmpty()) }
    var dirty by rememberSaveable(photoId) { mutableStateOf(false) }
    LaunchedEffect(savedDescription) {
        if (!dirty) description = savedDescription.orEmpty()
        if (savedDescription == description) dirty = false
    }
    OutlinedTextField(value = description, onValueChange = { description = it; dirty = true; onDraftChange(it) }, enabled = !busy,
        label = { Text("Описание фотографии") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    TextButton(enabled = !busy, onClick = { onSave(description) }) { Text("Сохранить описание фотографии") }
}
