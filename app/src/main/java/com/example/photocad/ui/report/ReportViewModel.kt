package com.example.photocad.ui.report

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.photocad.data.AppDatabase
import com.example.photocad.report.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val session: Long = 0,
    val initialized: Boolean = false,
    val loading: Boolean = false,
    val drawingName: String = "",
    val photos: List<DraftPhoto> = emptyList(),
    val operation: String? = null,
    val progress: Int = 0,
    val total: Int = 0,
    val generated: GeneratedReport? = null,
    val message: String? = null
) {
    val busy get() = loading || operation != null
    val selectedCount get() = photos.count { it.selected }
    val pageCount get() = ReportRules.pageCount(selectedCount)
}

class ReportViewModel(application: Application, private val db: AppDatabase, private val drawingId: Long) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(ReportUiState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    private fun updateSession(session: Long, update: (ReportUiState) -> ReportUiState) {
        mutable.update { if (it.session == session) update(it) else it }
    }

    fun open() {
        if (mutable.value.initialized || mutable.value.loading) return
        mutable.update { it.copy(loading = true, message = null) }
        val session = mutable.value.session
        job = viewModelScope.launch {
            try {
                ReportFileManager.cleanOldCache(getApplication())
                val (drawing, rows) = db.withTransaction {
                    val drawing = db.drawingDao().getById(drawingId) ?: throw ReportException("Чертёж недоступен")
                    drawing to db.photoDao().getReportRows(drawingId)
                }
                updateSession(session) { it.copy(initialized = true, loading = false, drawingName = drawing.name,
                    photos = reportPointOrdinals(rows.map { row -> DraftPhoto(row.photoId, row.pointId, row.filePath,
                        ReportRules.description(row.description, row.pointComment), drawingPage = row.pageNumber,
                        drawingPath = drawing.filePath, drawingName = drawing.name, pointX = row.x, pointY = row.y) })) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { updateSession(session) { it.copy(loading = false, message = failure.message ?: "Не удалось загрузить фотографии") } }
        }
    }

    private fun change(transform: (List<DraftPhoto>) -> List<DraftPhoto>) {
        if (mutable.value.busy) return
        mutable.update { it.copy(photos = transform(it.photos), generated = null, message = null) }
    }
    fun edit(id: Long, text: String) = change { rows -> rows.map { if (it.photoId == id) it.copy(description = text) else it } }
    fun select(id: Long, selected: Boolean) = change { rows -> rows.map { if (it.photoId == id) it.copy(selected = selected) else it } }
    fun move(id: Long, delta: Int) = change { movePhoto(it, id, delta) }

    fun saveDescription(id: Long) {
        if (mutable.value.busy) return
        val photo = mutable.value.photos.firstOrNull { it.photoId == id } ?: return
        mutable.update { it.copy(operation = "Сохранение описания", message = null) }
        val session = mutable.value.session
        job = viewModelScope.launch {
            try {
                if (db.photoDao().updateDescription(id, photo.description) != 1) throw ReportException("Фотография недоступна")
                updateSession(session) { it.copy(message = "Описание фотографии сохранено в базе") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { updateSession(session) { it.copy(message = failure.message ?: "Не удалось сохранить описание") } }
            finally { updateSession(session) { it.copy(operation = null) } }
        }
    }

    fun generate() {
        if (mutable.value.busy) return
        val input = reportInput(mutable.value.photos)
        if (input.isEmpty()) { showMessage("Выберите хотя бы одну фотографию"); return }
        mutable.update { it.copy(operation = "Формирование PDF", progress = 0, total = input.size, generated = null, message = null) }
        val session = mutable.value.session
        job = viewModelScope.launch {
            try {
                val result = PdfReportGenerator().generate(input, ReportFileManager.directory(getApplication())) { done, total ->
                    updateSession(session) { it.copy(progress = done, total = total) }
                }
                updateSession(session) { it.copy(generated = result, message = "PDF сформирован. Доступен предварительный просмотр.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { updateSession(session) { it.copy(message = failure.message ?: "Ошибка формирования PDF") } }
            finally { updateSession(session) { it.copy(operation = null) } }
        }
    }

    fun savePdf(uri: Uri) {
        if (mutable.value.busy) return
        val result = mutable.value.generated
        if (result == null) { showMessage("Сформируйте PDF повторно"); return }
        mutable.update { it.copy(operation = "Сохранение PDF", message = null) }
        val session = mutable.value.session
        job = viewModelScope.launch {
            try {
                ReportFileManager.save(getApplication(), result.file, uri)
                updateSession(session) { it.copy(message = "PDF сохранён") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                updateSession(session) { it.copy(message = "Не удалось сохранить PDF: ${failure.message}. Готовый PDF сохранён временно для повтора. Проверьте выбранную папку: провайдер мог оставить незавершённый файл.") }
            } finally { updateSession(session) { it.copy(operation = null) } }
        }
    }

    fun cancelGeneration() {
        if (mutable.value.operation != "Формирование PDF") return
        job?.cancel()
        mutable.update { it.copy(message = "Формирование отменено") }
    }
    fun showMessage(text: String?) { mutable.update { it.copy(message = text) } }
    fun discard() {
        job?.cancel()
        mutable.update { ReportUiState(session = it.session + 1) }
    }

    class Factory(private val application: Application, private val db: AppDatabase, private val drawingId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReportViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ReportViewModel(application, db, drawingId) as T
        }
    }
}
