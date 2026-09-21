package com.example.photocad.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.photocad.ui.PhotoThumbnail
import com.example.photocad.ui.components.PrimaryButton
import com.example.photocad.ui.theme.FieldShape
import com.example.photocad.ui.theme.Primary
import com.example.photocad.ui.theme.PrimaryLight

@Composable
fun RegistrationScreen(busy: Boolean, message: String?, onDismissMessage: () -> Unit, onSubmit: (fullName: String, avatarPath: String?) -> Unit) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var avatarPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingAvatar by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    (error ?: message)?.let { text ->
        AlertDialog(
            onDismissRequest = { error = null; onDismissMessage() },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { error = null; onDismissMessage() }) { Text("Закрыть") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Добро пожаловать", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            "Простая фотофиксация для реальных задач",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        Box {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(PrimaryLight)
                    .border(2.dp, Primary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val path = avatarPath
                if (path != null) {
                    PhotoThumbnail(path, Modifier.size(80.dp).clip(CircleShape))
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Primary, modifier = Modifier.size(32.dp))
                }
            }
            IconButton(
                onClick = { pickingAvatar = true },
                modifier = Modifier.align(Alignment.BottomEnd).size(28.dp).background(Primary, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Изменить аватар", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(24.dp))

        Text("Ваше ФИО", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            enabled = !busy,
            singleLine = true,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Данные хранятся локально на устройстве", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Spacer(Modifier.height(24.dp))

        PrimaryButton(text = "Продолжить", enabled = !busy && fullName.isNotBlank(), onClick = { onSubmit(fullName.trim(), avatarPath) })
    }

    if (pickingAvatar) {
        AlertDialog(
            onDismissRequest = { pickingAvatar = false },
            title = { Text("Фото профиля") },
            text = {
                AvatarPickerButtons(
                    enabled = !busy,
                    onPicked = { avatarPath = it; pickingAvatar = false },
                    onError = { error = it }
                )
            },
            confirmButton = { TextButton(onClick = { pickingAvatar = false }) { Text("Закрыть") } }
        )
    }
}
