package com.example.photocad.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.photocad.ui.PhotoThumbnail

@Composable
fun RegistrationScreen(busy: Boolean, message: String?, onDismissMessage: () -> Unit, onSubmit: (fullName: String, avatarPath: String?) -> Unit) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var avatarPath by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    (error ?: message)?.let { text ->
        AlertDialog(
            onDismissRequest = { error = null; onDismissMessage() },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { error = null; onDismissMessage() }) { Text("Закрыть") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Регистрация", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        avatarPath?.let { path ->
            PhotoThumbnail(path, Modifier.size(96.dp).clip(CircleShape))
            Spacer(Modifier.height(12.dp))
        }
        AvatarPickerButtons(
            enabled = !busy,
            onPicked = { avatarPath = it },
            onError = { error = it }
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Ваше ФИО") },
            enabled = !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Button(
            enabled = !busy && fullName.isNotBlank(),
            onClick = { onSubmit(fullName.trim(), avatarPath) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Продолжить") }
    }
}
