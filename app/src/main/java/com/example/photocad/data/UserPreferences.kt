package com.example.photocad.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.userDataStore by preferencesDataStore(name = "user_profile")

data class UserProfile(val fullName: String, val avatarPath: String?)

/** Local-only user profile: no login/password, no sync — just what's saved on this device. */
object UserPreferences {
    private val FULL_NAME = stringPreferencesKey("full_name")
    private val AVATAR_PATH = stringPreferencesKey("avatar_path")

    // null means registration hasn't happened yet on this device.
    fun profileFlow(context: Context): Flow<UserProfile?> =
        context.userDataStore.data.map { prefs ->
            prefs[FULL_NAME]?.let { name -> UserProfile(name, prefs[AVATAR_PATH]) }
        }

    suspend fun saveProfile(context: Context, fullName: String, avatarPath: String?) {
        context.userDataStore.edit { prefs ->
            prefs[FULL_NAME] = fullName
            if (avatarPath != null) prefs[AVATAR_PATH] = avatarPath else prefs.remove(AVATAR_PATH)
        }
    }

    suspend fun updateFullName(context: Context, fullName: String) {
        context.userDataStore.edit { prefs -> prefs[FULL_NAME] = fullName }
    }

    suspend fun updateAvatarPath(context: Context, avatarPath: String) {
        context.userDataStore.edit { prefs -> prefs[AVATAR_PATH] = avatarPath }
    }

    suspend fun clearProfile(context: Context) {
        context.userDataStore.edit { prefs -> prefs.clear() }
    }
}
