package com.example.photocad.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

/**
 * The field has no save button of its own: the point screen stores every description at once,
 * so onDraftChange is how the typed text reaches it.
 */
@Composable
fun PhotoDescriptionEditor(photoId: Long, savedDescription: String?, busy: Boolean, onDraftChange: (String) -> Unit) {
    var description by rememberSaveable(photoId) { mutableStateOf(savedDescription.orEmpty()) }
    var dirty by rememberSaveable(photoId) { mutableStateOf(false) }
    LaunchedEffect(savedDescription) {
        if (!dirty) description = savedDescription.orEmpty()
        if (savedDescription == description) dirty = false
    }
    OutlinedTextField(value = description, onValueChange = { description = it; dirty = true; onDraftChange(it) },
        enabled = !busy, label = { Text("Описание фотографии") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
}
