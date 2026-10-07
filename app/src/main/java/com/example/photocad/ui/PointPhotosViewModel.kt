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

    private suspend fun attach(path: String, preferences: PhotoPreferences, allowGallery: Boolean,
                               cameraSource: String? = null, replacePhotoId: Long? = null): String? {
        withContext(Dispatchers.IO) { ReportImageLoader.load(path, 320).recycle() }
        if (replacePhotoId == null) db.insertPhotoWithComment(pointId, path)
        else db.replacePhotoFile(replacePhotoId, pointId, path)
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

    private suspend fun discardUnreferenced(path: String, cameraSource: String? = null) {
        withContext(NonCancellable + Dispatchers.IO) {
            if (db.photoDao().countByFilePath(path) == 0) File(path).delete()
            if (cameraSource != null && cameraSource != path) File(cameraSource).delete()
        }
    }

    fun importGallery(uri: Uri, allowGallery: Boolean = true, replacePhotoId: Long? = null) =
        perform(if (replacePhotoId == null) "Фотография добавлена" else "Фотография заменена") {
        val preferences = UserPreferences.photoPreferencesFlow(getApplication()).first()
        val storedPath = importPhoto(getApplication(), uri, preferences)
        try {
            attach(storedPath, preferences, allowGallery, replacePhotoId = replacePhotoId)
        } catch (cancelled: CancellationException) {
            discardUnreferenced(storedPath)
            throw cancelled
        } catch (failure: Exception) {
            discardUnreferenced(storedPath)
            throw failure
        }
    }

    fun cameraResult(path: String, success: Boolean, allowGallery: Boolean = true, replacePhotoId: Long? = null) {
        if (mutable.value.busy || path == activeCameraPath || path == mutable.value.completedCameraPath) return
        activeCameraPath = path
        perform(if (!success) "Съёмка отменена" else if (replacePhotoId == null) "Фотография добавлена" else "Фотография переснята", path) {
            if (success) {
                val preferences = UserPreferences.photoPreferencesFlow(getApplication()).first()
                var storedPath: String? = null
                try {
                    val ready = storeCameraPhoto(getApplication(), path, preferences)
                    storedPath = ready
                    attach(ready, preferences, allowGallery, cameraSource = path, replacePhotoId = replacePhotoId)
                }
                catch (cancelled: CancellationException) {
                    if (storedPath == null) withContext(NonCancellable + Dispatchers.IO) { File(path).delete() }
                    else discardUnreferenced(storedPath, path)
                    throw cancelled
                }
                catch (failure: Exception) {
                    if (storedPath == null) withContext(NonCancellable + Dispatchers.IO) { File(path).delete() }
                    else discardUnreferenced(storedPath, path)
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

    fun deletePhoto(photoId: Long) = perform("Фотография удалена") {
        if (db.photoDao().getById(photoId)?.pointId != pointId) error("Фотография недоступна")
        db.deletePhoto(photoId)
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
