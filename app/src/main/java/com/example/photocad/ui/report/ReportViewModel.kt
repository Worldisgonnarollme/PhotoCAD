package com.example.photocad.ui.report

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.photocad.data.AppDatabase
import com.example.photocad.data.deletePoint
import com.example.photocad.report.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ReportStep { COVER, PHOTOS, PREVIEW, COMPLETE }

data class ReportUiState(
    val session: Long = 0,
    val initialized: Boolean = false,
    val loading: Boolean = false,
    val drawingName: String = "",
    val photos: List<DraftPhoto> = emptyList(),
    val step: ReportStep = ReportStep.COVER,
    val cover: AlbumCover = AlbumCover(),
    val operation: String? = null,
    val progress: Int = 0,
    val total: Int = 0,
    val generated: GeneratedReport? = null,
    val generatedPhotoIds: Set<Long> = emptySet(),
    val generatedPointIds: Set<Long> = emptySet(),
    val saved: Boolean = false,
    val archiveDecisionDone: Boolean = false,
    val deletePointsAfterSave: Boolean = false,
    val message: String? = null
) {
    val busy get() = loading || operation != null
    val selectedCount get() = photos.count { it.selected }
    val missingCaptionCount get() = photos.count { it.selected && it.description.isBlank() }
    val pageCount get() = selectedCount + 2
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
                val loaded = db.withTransaction {
                    val drawing = db.drawingDao().getById(drawingId) ?: throw ReportException("Чертёж недоступен")
                    val site = db.siteDao().getById(drawing.siteId)
                    val points = db.pointDao().getActiveByDrawing(drawingId)
                    val photos = numberPointsForReport(points, db.photoDao().getReportRows(drawingId).map { row ->
                        DraftPhoto(row.photoId, row.pointId, row.filePath,
                            ReportRules.description(row.description, row.pointComment),
                            drawingPage = row.pageNumber,
                            drawingPath = drawing.filePath, drawingName = drawing.name,
                            pointX = row.x, pointY = row.y, colorIndex = row.colorIndex,
                            captionSource = captionSource(row.description, row.pointComment))
                    })
                    val details = site?.let { db.siteDao().getReportDetails(it.id) }
                    Triple(drawing, site to details, photos)
                }
                val (drawing, siteAndDetails, photos) = loaded
                val (site, details) = siteAndDetails
                val cover = albumCoverForSite(site, drawing.name, details,
                    Calendar.getInstance().get(Calendar.YEAR).toString())
                updateSession(session) { it.copy(initialized = true, loading = false, drawingName = drawing.name,
                    photos = photos, cover = cover) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                updateSession(session) { it.copy(loading = false, message = failure.message ?: "Не удалось загрузить фотографии") }
            }
        }
    }

    private fun invalidate(state: ReportUiState): ReportUiState = state.copy(
        generated = null, generatedPhotoIds = emptySet(), generatedPointIds = emptySet(),
        saved = false, archiveDecisionDone = false, message = null)

    private fun change(transform: (List<DraftPhoto>) -> List<DraftPhoto>) {
        if (mutable.value.busy || mutable.value.saved) return
        mutable.update { invalidate(it.copy(photos = transform(it.photos))) }
    }
    fun edit(id: Long, text: String) = change { rows -> rows.map { if (it.photoId == id) it.copy(description = text) else it } }
    fun select(id: Long, selected: Boolean) = change { rows -> rows.map { if (it.photoId == id) it.copy(selected = selected) else it } }
    fun move(id: Long, delta: Int) = change { movePhoto(it, id, delta) }

    fun editCover(cover: AlbumCover) {
        if (mutable.value.busy || mutable.value.saved) return
        mutable.update { invalidate(it.copy(cover = cover)) }
    }
    fun next() {
        if (mutable.value.busy || !mutable.value.initialized) return
        mutable.update { current ->
            when (current.step) {
                ReportStep.COVER -> current.copy(step = ReportStep.PHOTOS)
                // A missing description no longer blocks the step: it is confirmed before generating.
                ReportStep.PHOTOS ->
                    if (current.selectedCount == 0) current.copy(message = "Выберите хотя бы одну фотографию")
                    else current.copy(step = ReportStep.PREVIEW, message = null)
                else -> current
            }
        }
    }
    fun previous() {
        if (mutable.value.busy || mutable.value.saved) return
        mutable.update { current -> current.copy(step = when (current.step) {
            ReportStep.PHOTOS -> ReportStep.COVER
            ReportStep.PREVIEW -> ReportStep.PHOTOS
            else -> current.step
        }, message = null) }
    }

    fun saveDescription(id: Long) {
        if (mutable.value.busy) return
        val photo = mutable.value.photos.firstOrNull { it.photoId == id } ?: return
        mutable.update { it.copy(operation = "Сохранение описания", message = null) }
        val session = mutable.value.session
        job = viewModelScope.launch {
            try {
                if (db.photoDao().updateDescription(id, photo.description) != 1) throw ReportException("Фотография недоступна")
                updateSession(session) { current -> current.copy(
                    photos = current.photos.map { if (it.photoId == id) it.copy(captionSource = CaptionSource.PHOTO_DESCRIPTION) else it },
                    message = "Описание фотографии сохранено в базе") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { updateSession(session) { it.copy(message = failure.message ?: "Не удалось сохранить описание") } }
            finally { updateSession(session) { it.copy(operation = null) } }
        }
    }

    fun generate() {
        val current = mutable.value
        if (current.busy || current.step != ReportStep.PREVIEW) return
        val input = reportInput(current.photos)
        if (input.isEmpty()) { showMessage("Выберите хотя бы одну фотографию"); return }
        val cover = current.cover
        val selected = current.photos.filter { it.selected }
        mutable.update { it.copy(operation = "Формирование PDF", progress = 0, total = input.size,
            generated = null, saved = false, message = null) }
        val session = mutable.value.session
        job = viewModelScope.launch {
            try {
                val progress: (Int, Int) -> Unit = { done, total ->
                    updateSession(session) { it.copy(progress = done, total = total) }
                }
                val result = PhotoAlbumGenerator(getApplication()).generate(
                    PhotoAlbumInput(cover, input), ReportFileManager.directory(getApplication()), progress)
                updateSession(session) { it.copy(generated = result,
                    generatedPhotoIds = selected.mapTo(mutableSetOf()) { photo -> photo.photoId },
                    generatedPointIds = selected.mapTo(mutableSetOf()) { photo -> photo.pointId },
                    message = "PDF сформирован. Проверьте страницы и сохраните файл.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { updateSession(session) { it.copy(message = failure.message ?: "Ошибка формирования PDF") } }
            finally { updateSession(session) { it.copy(operation = null) } }
        }
    }

    fun setDeletePointsAfterSave(enabled: Boolean) {
        if (mutable.value.busy || mutable.value.saved) return
        mutable.update { it.copy(deletePointsAfterSave = enabled, message = null) }
    }

    /** Same eligibility rule as archiving: a point goes only if every photo it still has is in the album. */
    private suspend fun deleteUsedPoints(pointIds: Set<Long>, includedPhotoIds: Set<Long>): Int {
        val eligible = db.withTransaction {
            eligibleArchivePointIds(pointIds.associateWith { db.photoDao().getIdsByPoint(it).toSet() }, includedPhotoIds)
        }
        // Outside the transaction: deletePoint also removes photo files from disk.
        eligible.forEach { db.deletePoint(it) }
        return eligible.size
    }

    fun savePdf(uri: Uri) {
        if (mutable.value.busy) return
        val current = mutable.value
        val result = current.generated
        if (result == null) { showMessage("Сформируйте PDF повторно"); return }
        mutable.update { it.copy(operation = "Сохранение PDF", message = null) }
        val session = current.session
        job = viewModelScope.launch {
            try {
                ReportFileManager.save(getApplication(), result.file, uri)
                // Deletion happens only after the file is written, so a failed save never costs points.
                val deleted = if (current.deletePointsAfterSave)
                    deleteUsedPoints(current.generatedPointIds, current.generatedPhotoIds) else null
                updateSession(session) { it.copy(saved = true, step = ReportStep.COMPLETE,
                    archiveDecisionDone = deleted != null || it.archiveDecisionDone,
                    message = if (deleted == null) "Фотоотчёт успешно сохранён"
                        else "Фотоотчёт сохранён. Удалено точек: $deleted. Точки с не включёнными фотографиями остались на чертеже.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                updateSession(session) { it.copy(message = "Не удалось сохранить PDF: ${failure.message}. Повторите сохранение.") }
            } finally { updateSession(session) { it.copy(operation = null) } }
        }
    }

    fun archiveUsedPoints() {
        val current = mutable.value
        if (current.busy || !current.saved || current.archiveDecisionDone) return
        val included = current.generatedPhotoIds
        val pointIds = current.generatedPointIds
        mutable.update { it.copy(operation = "Архивирование точек", message = null) }
        val session = current.session
        job = viewModelScope.launch {
            try {
                val count = db.withTransaction {
                    val attached = pointIds.associateWith { db.photoDao().getIdsByPoint(it).toSet() }
                    val eligible = eligibleArchivePointIds(attached, included)
                    eligible.sumOf { db.pointDao().setArchived(it, true) }
                }
                updateSession(session) { it.copy(archiveDecisionDone = true,
                    message = "Архивировано точек: $count. Точки с не включёнными фотографиями остались активными.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { updateSession(session) { it.copy(message = "Не удалось архивировать точки: ${failure.message}") } }
            finally { updateSession(session) { it.copy(operation = null) } }
        }
    }

    fun leavePointsActive() {
        if (mutable.value.saved && !mutable.value.busy) mutable.update { it.copy(archiveDecisionDone = true, message = "Точки оставлены активными") }
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
