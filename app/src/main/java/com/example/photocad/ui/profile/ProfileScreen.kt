package com.example.photocad.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.photocad.BuildConfig
import com.example.photocad.data.UserProfile
import com.example.photocad.ui.PhotoThumbnail

@Composable
fun ProfileScreen(profile: UserProfile, busy: Boolean, message: String?, onDismissMessage: () -> Unit,
                   onUpdateFullName: (String) -> Unit, onUpdateAvatarPath: (String) -> Unit) {
    var editingName by remember { mutableStateOf(false) }
    var pickingAvatar by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    (error ?: message)?.let { text ->
        AlertDialog(onDismissRequest = { error = null; onDismissMessage() }, text = { Text(text) },
            confirmButton = { TextButton(onClick = { error = null; onDismissMessage() }) { Text("Закрыть") } })
    }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        profile.avatarPath?.let { path ->
            PhotoThumbnail(path, Modifier.size(96.dp).clip(CircleShape))
            Spacer(Modifier.height(12.dp))
        }
        Text(profile.fullName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))

        ListItem(
            headlineContent = { Text("Изменить ФИО") },
            modifier = Modifier.clickable { editingName = true }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Изменить аватарку") },
            modifier = Modifier.clickable { pickingAvatar = true }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("О приложении") },
            modifier = Modifier.clickable { aboutOpen = true }
        )
        HorizontalDivider()
    }

    if (editingName) {
        var name by remember { mutableStateOf(profile.fullName) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Изменить ФИО") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                    onUpdateFullName(name.trim()); editingName = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Отмена") } }
        )
    }

    if (pickingAvatar) {
        AlertDialog(
            onDismissRequest = { pickingAvatar = false },
            title = { Text("Изменить аватарку") },
            text = {
                AvatarPickerButtons(
                    enabled = !busy,
                    onPicked = { onUpdateAvatarPath(it); pickingAvatar = false },
                    onError = { error = it }
                )
            },
            confirmButton = { TextButton(onClick = { pickingAvatar = false }) { Text("Закрыть") } }
        )
    }

    if (aboutOpen) {
        AlertDialog(
            onDismissRequest = { aboutOpen = false },
            title = { Text("О приложении") },
            text = { Text("PhotoCAD\nВерсия ${BuildConfig.VERSION_NAME}") },
            confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text("Закрыть") } }
        )
    }
}
