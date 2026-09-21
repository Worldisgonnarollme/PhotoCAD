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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private fun perform(success: String, cameraPath: String? = null, operation: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                operation()
                mutable.update { it.copy(message = success) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(message = failure.message ?: "Не удалось выполнить операцию") } }
            catch (_: OutOfMemoryError) { mutable.update { it.copy(message = "Недостаточно памяти для чтения фотографии") } }
            finally {
                activeCameraPath = null
                mutable.update { it.copy(busy = false, completedCameraPath = cameraPath ?: it.completedCameraPath) }
            }
        }
    }

    private suspend fun attach(path: String) {
        withContext(Dispatchers.IO) { ReportImageLoader.load(path, 320).recycle() }
        // Do not remove the file on cancellation: Room may have committed already.
        db.insertPhotoWithComment(pointId, path)
    }

    fun importGallery(uri: Uri) = perform("Фотография добавлена") {
        attach(copyUriToAppStorage(getApplication(), uri))
    }

    fun cameraResult(path: String, success: Boolean) {
        if (mutable.value.busy || path == activeCameraPath || path == mutable.value.completedCameraPath) return
        activeCameraPath = path
        perform(if (success) "Фотография добавлена" else "Съёмка отменена", path) {
            if (success) attach(path) else withContext(Dispatchers.IO) { File(path).delete(); Unit }
        }
    }

    fun saveComment(comment: String) = perform("Комментарий точки сохранён. Описания существующих фото не изменены.") {
        if (db.pointDao().updateComment(pointId, comment) != 1) error("Точка недоступна")
    }

    fun saveColor(colorIndex: Int) = perform("Цвет точки изменён") {
        if (db.pointDao().updateColor(pointId, colorIndex) != 1) error("Точка недоступна")
    }

    fun saveDescription(photoId: Long, description: String) = perform("Описание фотографии сохранено") {
        if (db.photoDao().updateDescription(photoId, description) != 1) error("Фотография недоступна")
    }

    class Factory(private val application: Application, private val db: AppDatabase, private val pointId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PointPhotosViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return PointPhotosViewModel(application, db, pointId) as T
        }
    }
}
