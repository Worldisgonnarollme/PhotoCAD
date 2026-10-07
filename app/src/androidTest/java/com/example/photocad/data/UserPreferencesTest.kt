package com.example.photocad.data

import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UserPreferencesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Return type is spelled out: JUnit rejects the whole class unless @Before/@After are void,
    // and `edit {}` returns Preferences, so an inferred expression body would not be.
    @Before fun clear(): Unit = runBlocking { context.userDataStore.edit { it.clear() } }
    @After fun cleanup(): Unit = runBlocking { context.userDataStore.edit { it.clear() } }

    @Test fun noProfileUntilSaved() = runBlocking {
        assertNull(UserPreferences.profileFlow(context).first())
    }

    @Test fun saveProfilePersistsNameAndAvatar() = runBlocking {
        UserPreferences.saveProfile(context, "Иванов Иван", "/tmp/avatar.jpg")
        assertEquals(UserProfile("Иванов Иван", "/tmp/avatar.jpg"), UserPreferences.profileFlow(context).first())
    }

    @Test fun saveProfileWithoutAvatarLeavesItNull() = runBlocking {
        UserPreferences.saveProfile(context, "Без фото", null)
        assertEquals(UserProfile("Без фото", null), UserPreferences.profileFlow(context).first())
    }

    @Test fun updateFullNameKeepsAvatar() = runBlocking {
        UserPreferences.saveProfile(context, "Старое имя", "/tmp/avatar.jpg")
        UserPreferences.updateFullName(context, "Новое имя")
        assertEquals(UserProfile("Новое имя", "/tmp/avatar.jpg"), UserPreferences.profileFlow(context).first())
    }

    @Test fun updateAvatarPathKeepsName() = runBlocking {
        UserPreferences.saveProfile(context, "Имя", "/tmp/old.jpg")
        UserPreferences.updateAvatarPath(context, "/tmp/new.jpg")
        assertEquals(UserProfile("Имя", "/tmp/new.jpg"), UserPreferences.profileFlow(context).first())
    }

    @Test fun settingsSurviveProfileClear() = runBlocking {
        UserPreferences.saveThemeMode(context, AppThemeMode.DARK)
        UserPreferences.savePhotoPreferences(context, PhotoPreferences(saveToGallery = false, compressPhotos = false))
        UserPreferences.saveProfile(context, "Временный профиль", "/tmp/avatar.jpg")

        UserPreferences.clearProfile(context)

        assertNull(UserPreferences.profileFlow(context).first())
        assertEquals(AppThemeMode.DARK, UserPreferences.themeModeFlow(context).first())
        assertEquals(PhotoPreferences(false, false), UserPreferences.photoPreferencesFlow(context).first())
    }
}
