package com.example.photocad.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserSettingsStoreTest {
    private val store = MemoryPreferencesStore()

    @Test fun themeAndPhotoSettingsRoundTrip() = runBlocking {
        UserPreferences.saveThemeModeTo(store, AppThemeMode.DARK)
        UserPreferences.savePhotoPreferencesTo(store, PhotoPreferences(saveToGallery = false, compressPhotos = false))

        assertEquals(AppThemeMode.DARK, UserPreferences.themeModeFlowFrom(store).first())
        assertEquals(PhotoPreferences(false, false), UserPreferences.photoPreferencesFlowFrom(store).first())
    }

    @Test fun clearProfilePreservesSettings() = runBlocking {
        UserPreferences.saveProfileTo(store, "Иванов Иван", "/tmp/avatar.jpg")
        UserPreferences.saveThemeModeTo(store, AppThemeMode.LIGHT)
        UserPreferences.savePhotoPreferencesTo(store, PhotoPreferences(saveToGallery = false, compressPhotos = true))

        UserPreferences.clearProfileFrom(store)

        assertNull(UserPreferences.profileFlowFrom(store).first())
        assertEquals(AppThemeMode.LIGHT, UserPreferences.themeModeFlowFrom(store).first())
        assertEquals(PhotoPreferences(false, true), UserPreferences.photoPreferencesFlowFrom(store).first())
    }
}

private class MemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = mutex.withLock {
        transform(state.value).also { state.value = it }
    }
}
