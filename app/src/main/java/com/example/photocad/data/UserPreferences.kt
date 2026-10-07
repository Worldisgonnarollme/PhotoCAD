package com.example.photocad.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.userDataStore by preferencesDataStore(name = "user_profile")

data class UserProfile(val fullName: String, val avatarPath: String?)
enum class AppThemeMode { SYSTEM, LIGHT, DARK }
data class PhotoPreferences(val saveToGallery: Boolean = true, val compressPhotos: Boolean = true)

/** Local-only user profile: no login/password, no sync — just what's saved on this device. */
object UserPreferences {
    private val FULL_NAME = stringPreferencesKey("full_name")
    private val AVATAR_PATH = stringPreferencesKey("avatar_path")
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val SAVE_TO_GALLERY = booleanPreferencesKey("save_photos_to_gallery")
    private val COMPRESS_PHOTOS = booleanPreferencesKey("compress_photos")

    // null means registration hasn't happened yet on this device.
    fun profileFlow(context: Context): Flow<UserProfile?> =
        profileFlowFrom(context.userDataStore)

    fun profileFlowFrom(store: DataStore<Preferences>): Flow<UserProfile?> =
        store.data.map { prefs ->
            prefs[FULL_NAME]?.let { name -> UserProfile(name, prefs[AVATAR_PATH]) }
        }

    fun themeModeFlow(context: Context): Flow<AppThemeMode> = themeModeFlowFrom(context.userDataStore)

    fun themeModeFlowFrom(store: DataStore<Preferences>): Flow<AppThemeMode> = store.data.map { prefs ->
        prefs[THEME_MODE]?.let { value -> AppThemeMode.entries.firstOrNull { it.name == value } } ?: AppThemeMode.SYSTEM
    }

    fun photoPreferencesFlow(context: Context): Flow<PhotoPreferences> = photoPreferencesFlowFrom(context.userDataStore)

    fun photoPreferencesFlowFrom(store: DataStore<Preferences>): Flow<PhotoPreferences> = store.data.map { prefs ->
        PhotoPreferences(
            saveToGallery = prefs[SAVE_TO_GALLERY] ?: true,
            compressPhotos = prefs[COMPRESS_PHOTOS] ?: true
        )
    }

    suspend fun saveThemeMode(context: Context, mode: AppThemeMode) = saveThemeModeTo(context.userDataStore, mode)

    suspend fun saveThemeModeTo(store: DataStore<Preferences>, mode: AppThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun savePhotoPreferences(context: Context, preferences: PhotoPreferences) =
        savePhotoPreferencesTo(context.userDataStore, preferences)

    suspend fun savePhotoPreferencesTo(store: DataStore<Preferences>, preferences: PhotoPreferences) {
        store.edit {
            it[SAVE_TO_GALLERY] = preferences.saveToGallery
            it[COMPRESS_PHOTOS] = preferences.compressPhotos
        }
    }

    suspend fun saveProfile(context: Context, fullName: String, avatarPath: String?) {
        saveProfileTo(context.userDataStore, fullName, avatarPath)
    }

    suspend fun saveProfileTo(store: DataStore<Preferences>, fullName: String, avatarPath: String?) {
        store.edit { prefs ->
            prefs[FULL_NAME] = fullName
            if (avatarPath != null) prefs[AVATAR_PATH] = avatarPath else prefs.remove(AVATAR_PATH)
        }
    }

    suspend fun updateFullName(context: Context, fullName: String) {
        updateFullNameIn(context.userDataStore, fullName)
    }

    suspend fun updateFullNameIn(store: DataStore<Preferences>, fullName: String) {
        store.edit { prefs -> prefs[FULL_NAME] = fullName }
    }

    suspend fun updateAvatarPath(context: Context, avatarPath: String) {
        updateAvatarPathIn(context.userDataStore, avatarPath)
    }

    suspend fun updateAvatarPathIn(store: DataStore<Preferences>, avatarPath: String) {
        store.edit { prefs -> prefs[AVATAR_PATH] = avatarPath }
    }

    suspend fun clearProfile(context: Context) {
        clearProfileFrom(context.userDataStore)
    }

    suspend fun clearProfileFrom(store: DataStore<Preferences>) {
        store.edit { prefs ->
            prefs.remove(FULL_NAME)
            prefs.remove(AVATAR_PATH)
        }
    }
}
