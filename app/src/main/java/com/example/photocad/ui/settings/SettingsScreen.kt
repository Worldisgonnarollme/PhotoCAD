// экран настроек: переключатели "Фото" визуальные, остальное — статичные строки

package com.example.photocad.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.photocad.BuildConfig
import com.example.photocad.data.AppThemeMode
import com.example.photocad.data.PhotoPreferences
import com.example.photocad.data.UserPreferences
import com.example.photocad.ui.components.SectionCard
import com.example.photocad.ui.components.SettingsRow
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode by UserPreferences.themeModeFlow(context).collectAsState(initial = AppThemeMode.SYSTEM)
    val photoPreferences by UserPreferences.photoPreferencesFlow(context)
        .collectAsState(initial = PhotoPreferences())
    var themeMenuOpen by remember { mutableStateOf(false) }

    Column(Modifier.statusBarsPadding().fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsSection(title = "Фото") {
                SettingsRow(label = "Сохранять фото в галерею", trailing = {
                    Switch(checked = photoPreferences.saveToGallery, onCheckedChange = { enabled ->
                        scope.launch {
                            UserPreferences.savePhotoPreferences(context, photoPreferences.copy(saveToGallery = enabled))
                        }
                    })
                })
                HorizontalDivider()
                SettingsRow(label = "Сжимать фото", trailing = {
                    Switch(checked = photoPreferences.compressPhotos, onCheckedChange = { enabled ->
                        scope.launch {
                            UserPreferences.savePhotoPreferences(context, photoPreferences.copy(compressPhotos = enabled))
                        }
                    })
                })
            }
            SettingsSection(title = "Отчёт") {
                SettingsRow(label = "Формат отчёта", value = "PDF")
            }
            SettingsSection(title = "Интерфейс") {
                SettingsRow(label = "Язык", value = "Русский")
                HorizontalDivider()
                Box {
                    SettingsRow(label = "Тема", value = themeMode.label(), onClick = { themeMenuOpen = true })
                    DropdownMenu(expanded = themeMenuOpen, onDismissRequest = { themeMenuOpen = false }) {
                        AppThemeMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label()) },
                                onClick = {
                                    themeMenuOpen = false
                                    scope.launch { UserPreferences.saveThemeMode(context, mode) }
                                }
                            )
                        }
                    }
                }
            }
            SettingsSection(title = "Данные") {
                SettingsRow(label = "Очистить кэш")
                HorizontalDivider()
                SettingsRow(label = "Экспорт данных")
            }
            SettingsSection(title = "О приложении") {
                SettingsRow(label = "Версия", value = BuildConfig.VERSION_NAME)
                HorizontalDivider()
                SettingsRow(label = "Лицензии")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun AppThemeMode.label(): String = when (this) {
    AppThemeMode.SYSTEM -> "Системная"
    AppThemeMode.LIGHT -> "Светлая"
    AppThemeMode.DARK -> "Тёмная"
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
        SectionCard { Column { content() } }
    }
}
