// список чертежей

package com.example.photocad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.photocad.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun DrawingListScreen(db: AppDatabase, onOpenDrawing: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importError by remember { mutableStateOf<String?>(null) }
    val drawings by db.drawingDao().getAll().collectAsState(initial = emptyList())

    // считывает все чертежи
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val path = copyUriToAppStorage(context, it)
                    db.drawingDao().insert(Drawing(name = "Чертёж ${drawings.size + 1}", filePath = path))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { importError = "Не удалось добавить чертёж: ${failure.message}" }
            }
        }
    }

    importError?.let { message ->
        AlertDialog(onDismissRequest = { importError = null }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("Закрыть") } })
    }
    // кнопка добавления чертежа
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { pickImage.launch("image/*") }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить чертёж")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(drawings) { drawing ->
                ListItem(
                    headlineContent = { Text(drawing.name) },
                    modifier = Modifier.clickable { onOpenDrawing(drawing.id) } // переход на экран самого чертежа
                )
                HorizontalDivider()
            }
        }
    }
}