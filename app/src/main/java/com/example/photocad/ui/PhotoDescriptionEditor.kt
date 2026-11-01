package com.example.photocad.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The point screen owns the restorable draft and saves all descriptions together.
 * Keeping the field controlled prevents the visible text from diverging from the saved draft.
 */
@Composable
fun PhotoDescriptionEditor(description: String, busy: Boolean, onDraftChange: (String) -> Unit) {
    OutlinedTextField(value = description, onValueChange = onDraftChange,
        enabled = !busy, label = { Text("Описание фотографии") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
}
