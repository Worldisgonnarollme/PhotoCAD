package com.example.photocad.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.photocad.BuildConfig
import com.example.photocad.data.AppDatabase
import com.example.photocad.data.UserProfile
import com.example.photocad.ui.PhotoThumbnail
import com.example.photocad.ui.theme.CardShape

@Composable
fun ProfileScreen(
    db: AppDatabase,
    profile: UserProfile,
    busy: Boolean,
    message: String?,
    onDismissMessage: () -> Unit,
    onUpdateFullName: (String) -> Unit,
    onUpdateAvatarPath: (String) -> Unit,
    onLogout: () -> Unit
) {
    var editingName by remember { mutableStateOf(false) }
    var pickingAvatar by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var confirmingLogout by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val siteCount by db.siteDao().siteCount().collectAsState(initial = 0)
    val drawingCount by db.drawingDao().drawingCount().collectAsState(initial = 0)
    val pointCount by db.pointDao().pointCount().collectAsState(initial = 0)

    (error ?: message)?.let { text ->
        AlertDialog(onDismissRequest = { error = null; onDismissMessage() }, text = { Text(text) },
            confirmButton = { TextButton(onClick = { error = null; onDismissMessage() }) { Text("Закрыть") } })
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    val avatar = profile.avatarPath
                    if (avatar != null) {
                        PhotoThumbnail(avatar, Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)))
                    } else {
                        Text(profile.fullName.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Text(profile.fullName, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(siteCount, "Объекта", Modifier.weight(1f))
                StatTile(drawingCount, "Чертежей", Modifier.weight(1f))
                StatTile(pointCount, "Точек", Modifier.weight(1f))
            }
        }

        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(text = "Изменить ФИО", icon = Icons.Default.Edit, onClick = { editingName = true }, modifier = Modifier.weight(1f))
                PillButton(text = "Аватарка", icon = Icons.Default.CameraAlt, onClick = { pickingAvatar = true }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(text = "О приложении", icon = Icons.Default.Info, onClick = { aboutOpen = true }, modifier = Modifier.weight(1f))
                PillButton(
                    text = "Выйти", icon = Icons.Default.Logout, onClick = { confirmingLogout = true },
                    modifier = Modifier.weight(1f), tint = MaterialTheme.colorScheme.error
                )
            }
        }
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

    if (confirmingLogout) {
        AlertDialog(
            onDismissRequest = { confirmingLogout = false },
            title = { Text("Выйти из профиля?") },
            text = { Text("Данные объектов и чертежей останутся на устройстве, но потребуется зарегистрироваться заново.") },
            confirmButton = { TextButton(onClick = { confirmingLogout = false; onLogout() }) { Text("Выйти") } },
            dismissButton = { TextButton(onClick = { confirmingLogout = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun StatTile(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$count", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, color = tint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
    }
}
