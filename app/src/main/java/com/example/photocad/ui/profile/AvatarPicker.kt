package com.example.photocad.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.photocad.data.copyUriToAppStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Camera/gallery buttons for picking an avatar photo. Mirrors the picker pattern in DrawingScreen's PhotoDialog. */
@Composable
fun AvatarPickerButtons(enabled: Boolean, onPicked: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    onPicked(copyUriToAppStorage(context, uri))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    onError(failure.message ?: "Не удалось добавить фото")
                }
            }
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingCameraPath
        pendingCameraPath = null
        if (path != null) {
            if (success) onPicked(path)
            else scope.launch { withContext(Dispatchers.IO) { File(path).delete() } }
        }
    }

    Row {
        Button(enabled = enabled, onClick = {
            try {
                pickImage.launch("image/*")
            } catch (failure: Exception) {
                onError("Галерея недоступна: ${failure.message}")
            }
        }) { Text("Галерея") }
        Spacer(Modifier.width(8.dp))
        Button(enabled = enabled && pendingCameraPath == null, onClick = {
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        val dir = File(context.filesDir, "camera")
                        if (!dir.isDirectory && !dir.mkdirs()) error("Не удалось создать папку камеры")
                        File(dir, "${UUID.randomUUID()}.jpg").apply { createNewFile() }
                    }
                    pendingCameraPath = file.path
                    takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    pendingCameraPath?.let { path -> withContext(Dispatchers.IO) { File(path).delete() } }
                    pendingCameraPath = null
                    onError("Камера недоступна: ${failure.message}")
                }
            }
        }) { Text("Камера") }
    }
}
