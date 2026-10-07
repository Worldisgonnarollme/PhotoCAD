package com.example.photocad.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

@Composable
fun PhotoDescriptionEditor(photoId: Long, savedDescription: String?, pointComment: String, busy: Boolean, onSave: (String) -> Unit) {
    var description by rememberSaveable(photoId) { mutableStateOf(savedDescription ?: pointComment) }
    var dirty by rememberSaveable(photoId) { mutableStateOf(false) }
    LaunchedEffect(savedDescription, pointComment) {
        if (!dirty) description = savedDescription ?: pointComment
        if (savedDescription == description) dirty = false
    }
    OutlinedTextField(value = description, onValueChange = { description = it; dirty = true }, enabled = !busy,
        label = { Text("Описание фотографии") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    TextButton(enabled = !busy, onClick = { onSave(description) }) { Text("Сохранить описание фотографии") }
}
