package com.example.photocad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import com.example.photocad.ui.report.ReportScreen
import com.example.photocad.data.AppDatabase
import com.example.photocad.data.AppThemeMode
import com.example.photocad.data.UserPreferences
import com.example.photocad.ui.DrawingListScreen
import com.example.photocad.ui.DrawingScreen
import com.example.photocad.ui.SiteListScreen
import com.example.photocad.ui.profile.ProfileScreen
import com.example.photocad.ui.profile.ProfileViewModel
import com.example.photocad.ui.profile.RegistrationScreen
import com.example.photocad.ui.settings.SettingsScreen
import com.example.photocad.ui.theme.PhotoCADTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getInstance(this) // создание бд при старте
        setContent {
            val themeMode by UserPreferences.themeModeFlow(this@MainActivity)
                .collectAsState(initial = AppThemeMode.SYSTEM)
            PhotoCADTheme(mode = themeMode) {
                val profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory(application))
                val profileUi by profileViewModel.state.collectAsState()

                when {
                    profileUi.loading -> Box(Modifier.fillMaxSize())
                    profileUi.profile == null -> RegistrationScreen(
                        busy = profileUi.busy,
                        message = profileUi.message,
                        onDismissMessage = { profileViewModel.dismissMessage() },
                        onSubmit = { fullName, avatarPath -> profileViewModel.register(fullName, avatarPath) }
                    )
                    else -> MainApp(db = db, profileViewModel = profileViewModel)
                }
            }
        }
    }
}

private enum class MainTab(val label: String) { OBJECTS("Объекты"), PROFILE("Профиль"), SETTINGS("Настройки") }

@Composable
private fun MainApp(db: AppDatabase, profileViewModel: ProfileViewModel) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.OBJECTS) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.OBJECTS,
                    onClick = { selectedTab = MainTab.OBJECTS },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(MainTab.OBJECTS.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.PROFILE,
                    onClick = { selectedTab = MainTab.PROFILE },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text(MainTab.PROFILE.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.SETTINGS,
                    onClick = { selectedTab = MainTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(MainTab.SETTINGS.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                MainTab.OBJECTS -> ObjectsTab(db)
                MainTab.PROFILE -> {
                    val profileUi by profileViewModel.state.collectAsState()
                    profileUi.profile?.let { profile ->
                        ProfileScreen(
                            db = db,
                            profile = profile,
                            busy = profileUi.busy,
                            message = profileUi.message,
                            onDismissMessage = { profileViewModel.dismissMessage() },
                            onUpdateFullName = { profileViewModel.updateFullName(it) },
                            onUpdateAvatarPath = { profileViewModel.updateAvatarPath(it) },
                            onLogout = { profileViewModel.logout() }
                        )
                    }
                }
                MainTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun ObjectsTab(db: AppDatabase) {
    var selectedSiteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedDrawingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reportOpen by rememberSaveable { mutableStateOf(false) }
    when {
        selectedSiteId == null -> SiteListScreen(db = db, onOpenSite = { selectedSiteId = it })
        selectedDrawingId != null && reportOpen -> ReportScreen(db, selectedDrawingId!!, onBack = { reportOpen = false })
        selectedDrawingId == null -> DrawingListScreen(
            db = db,
            siteId = selectedSiteId!!,
            onBack = { selectedSiteId = null },
            onOpenDrawing = { selectedDrawingId = it }
        )
        else -> DrawingScreen(db = db, drawingId = selectedDrawingId!!, onBack = { selectedDrawingId = null }, onReport = { reportOpen = true })
    }
}
