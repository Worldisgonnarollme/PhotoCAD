package com.example.photocad.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.photocad.data.*
import com.example.photocad.report.ReportImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PointPhotosState(val busy: Boolean = false, val message: String? = null, val completedCameraPath: String? = null)

/** Retains file attachment/writes across Activity recreation; never owned by a dialog coroutine. */
class PointPhotosViewModel(application: Application, private val db: AppDatabase, private val pointId: Long) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(PointPhotosState())
    val state = mutable.asStateFlow()
    private var activeCameraPath: String? = null

    private fun perform(success: String, cameraPath: String? = null, operation: suspend () -> String?) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val detail = operation()
                mutable.update { it.copy(message = detail ?: success) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(message = failure.message ?: "Не удалось выполнить операцию") } }
            catch (_: OutOfMemoryError) { mutable.update { it.copy(message = "Недостаточно памяти для чтения фотографии") } }
            finally {
                activeCameraPath = null
                mutable.update { it.copy(busy = false, completedCameraPath = cameraPath ?: it.completedCameraPath) }
            }
        }
    }

    private suspend fun attach(path: String, preferences: PhotoPreferences, allowGallery: Boolean, cameraSource: String? = null): String? {
        withContext(Dispatchers.IO) { ReportImageLoader.load(path, 320).recycle() }
        db.insertPhotoWithComment(pointId, path)
        var galleryWarning: String? = null
        if (preferences.saveToGallery) {
            if (!allowGallery) {
                galleryWarning = "Фотография добавлена в PhotoCAD. Разрешение на сохранение в галерею не выдано."
            } else {
                try { copyPhotoToGallery(getApplication(), path) }
                catch (failure: Exception) {
                    galleryWarning = "Фотография добавлена, но не скопирована в галерею: ${failure.message}"
                }
            }
        }
        if (cameraSource != null && cameraSource != path) withContext(Dispatchers.IO) { File(cameraSource).delete() }
        return galleryWarning
    }

    fun importGallery(uri: Uri, allowGallery: Boolean = true) = perform("Фотография добавлена") {
        val preferences = UserPreferences.photoPreferencesFlow(getApplication()).first()
        val storedPath = importPhoto(getApplication(), uri, preferences)
        try {
            attach(storedPath, preferences, allowGallery)
        } catch (cancelled: CancellationException) {
            val referenced = withContext(NonCancellable + Dispatchers.IO) { db.photoDao().countByFilePath(storedPath) > 0 }
            if (!referenced) withContext(NonCancellable + Dispatchers.IO) { File(storedPath).delete() }
            throw cancelled
        } catch (failure: Exception) {
            withContext(Dispatchers.IO) { File(storedPath).delete() }
            throw failure
        }
    }

    fun cameraResult(path: String, success: Boolean, allowGallery: Boolean = true) {
        if (mutable.value.busy || path == activeCameraPath || path == mutable.value.completedCameraPath) return
        activeCameraPath = path
        perform(if (success) "Фотография добавлена" else "Съёмка отменена", path) {
            if (success) {
                val preferences = UserPreferences.photoPreferencesFlow(getApplication()).first()
                val storedPath = storeCameraPhoto(getApplication(), path, preferences)
                try { attach(storedPath, preferences, allowGallery, cameraSource = path) }
                catch (cancelled: CancellationException) {
                    val referenced = withContext(NonCancellable + Dispatchers.IO) { db.photoDao().countByFilePath(storedPath) > 0 }
                    withContext(NonCancellable + Dispatchers.IO) {
                        if (!referenced && storedPath != path) File(storedPath).delete()
                        if (referenced || storedPath != path) File(path).delete()
                    }
                    throw cancelled
                }
                catch (failure: Exception) {
                    withContext(Dispatchers.IO) {
                        if (storedPath != path) File(storedPath).delete()
                        File(path).delete()
                    }
                    throw failure
                }
            } else {
                withContext(Dispatchers.IO) { File(path).delete() }
                null
            }
        }
    }

    fun saveComment(comment: String) = perform("Комментарий точки сохранён. Описания существующих фото не изменены.") {
        if (db.pointDao().updateComment(pointId, comment) != 1) error("Точка недоступна")
        null
    }

    fun saveColor(colorIndex: Int) = perform("Цвет точки изменён") {
        if (db.pointDao().updateColor(pointId, colorIndex) != 1) error("Точка недоступна")
        null
    }

    fun saveDescription(photoId: Long, description: String) = perform("Описание фотографии сохранено") {
        if (db.photoDao().updateDescription(photoId, description) != 1) error("Фотография недоступна")
        null
    }

    class Factory(private val application: Application, private val db: AppDatabase, private val pointId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PointPhotosViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return PointPhotosViewModel(application, db, pointId) as T
        }
    }
}
