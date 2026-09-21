package com.example.photocad.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.photocad.data.UserPreferences
import com.example.photocad.data.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val busy: Boolean = false,
    val message: String? = null
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(ProfileUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            UserPreferences.profileFlow(getApplication()).collect { profile ->
                mutable.update { it.copy(loading = false, profile = profile) }
            }
        }
    }

    private fun perform(operation: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(message = failure.message ?: "Не удалось сохранить профиль") }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    fun register(fullName: String, avatarPath: String?) = perform {
        UserPreferences.saveProfile(getApplication(), fullName, avatarPath)
    }

    fun updateFullName(fullName: String) = perform {
        UserPreferences.updateFullName(getApplication(), fullName)
    }

    fun updateAvatarPath(avatarPath: String) = perform {
        UserPreferences.updateAvatarPath(getApplication(), avatarPath)
    }

    fun dismissMessage() = mutable.update { it.copy(message = null) }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(application) as T
        }
    }
}
