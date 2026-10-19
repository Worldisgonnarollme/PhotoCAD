package com.example.photocad.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

@Composable
fun ConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmText: String = "Удалить") {
    AlertDialog(
        onDismissRequest = onDismiss,
        // Material would use the muted onSurfaceVariant here. These dialogs guard irreversible
        // deletions, so they take the strongest text colour and a larger size instead.
        text = { Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, lineHeight = 24.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, fontSize = 16.sp) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", fontSize = 16.sp) } }
    )
}
