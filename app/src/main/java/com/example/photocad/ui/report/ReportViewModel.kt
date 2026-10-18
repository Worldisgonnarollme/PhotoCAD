package com.example.photocad.ui.report

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.photocad.data.AppDatabase
import com.example.photocad.data.Drawing
import com.example.photocad.data.deletePoint
import com.example.photocad.report.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReportStep { SCOPE, COVER, PHOTOS, PREVIEW, COMPLETE }

data class ReportUiState(
    val session: Long = 0,
    val initialized: Boolean = false,
    val loading: Boolean = false,
    val drawingName: String = "",
    val drawings: List<Drawing> = emptyList(),
    val allPhotos: List<DraftPhoto> = emptyList(),
    val selection: ReportSelection = ReportSelection(),
    val photos: List<DraftPhoto> = emptyList(),
    val step: ReportStep = ReportStep.SCOPE,
    val cover: AlbumCover = AlbumCover(),
    val includeCover: Boolean = true,
    val includeGeneralInfo: Boolean = true,
    val operation: String? = null,
    val progress: Int = 0,
    val total: Int = 0,
    val generated: GeneratedReport? = null,
    val generatedPhotoIds: Set<Long> = emptySet(),
    val generatedPointIds: Set<Long> = emptySet(),
    val saved: Boolean = false,
    val deletePointsAfterSave: Boolean = false,
    val message: String? = null
) {
    val busy get() = loading || operation != null
    val selectedCount get() = photos.count { it.selected }
    val missingCaptionCount get() = photos.count { it.selected && it.description.isBlank() }
    val pageCount get() = selectedCount + 2
}

class ReportViewModel(application: Application, private val db: AppDatabase,
                      private val siteId: Long?, private val initialDrawingId: Long?) : AndroidViewModel(application) {
    constructor(application: Application, db: AppDatabase, drawingId: Long) : this(application, db, null, drawingId)
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
                    val resolvedSiteId = siteId ?: db.drawingDao().getById(initialDrawingId ?: -1)?.siteId
                        ?: throw ReportException("Объект недоступен")
                    val site = db.siteDao().getById(resolvedSiteId)
                    if (siteId != null && site == null) throw ReportException("Объект недоступен")
                    val drawings = db.drawingDao().getAllBySite(resolvedSiteId)
                    val photos = drawings.flatMap { drawing ->
                        val points = db.pointDao().getAllByDrawing(drawing.id).filter { !it.isArchived }
                        numberPointsForReport(points, points.flatMap { point ->
                            db.photoDao().getAllByPoint(point.id).map { photo ->
                                DraftPhoto(photo.id, point.id, photo.filePath, photo.description.orEmpty(),
                                    drawingPage = point.pageNumber, pointNumber = point.displayNumber,
                                    drawingPath = drawing.filePath, drawingName = drawing.name,
                                    pointX = point.x, pointY = point.y, colorIndex = point.colorIndex,
                                    captionSource = captionSource(photo.description), drawingId = drawing.id,
                                    capturedAtLocal = photo.capturedAtLocal,
                                    capturedOffsetMinutes = photo.capturedOffsetMinutes)
                            }
                        })
                    }
                    val details = site?.let { db.siteDao().getReportDetails(it.id) }
                    Triple(site, drawings, details to photos)
                }
                val (site, drawings, detailsAndPhotos) = loaded
                val (details, allPhotos) = detailsAndPhotos
                val cover = albumCoverForSite(site, drawings.firstOrNull()?.name.orEmpty(), details,
                    Calendar.getInstance().get(Calendar.YEAR).toString())
                val selection = ReportSelection(drawingIds = if (initialDrawingId == null) drawings.mapTo(mutableSetOf()) { it.id }
                    else setOf(initialDrawingId), includeUndated = initialDrawingId != null)
                val displayName = if (initialDrawingId == null) site?.name.orEmpty()
                    else drawings.firstOrNull { it.id == initialDrawingId }?.name ?: site?.name.orEmpty()
                updateSession(session) { it.copy(initialized = true, loading = false, drawingName = displayName,
                    drawings = drawings, allPhotos = allPhotos, selection = selection,
                    photos = filterReportPhotos(allPhotos, selection), cover = cover,
                    step = if (initialDrawingId == null) ReportStep.SCOPE else ReportStep.PHOTOS) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                updateSession(session) { it.copy(loading = false, message = failure.message ?: "Не удалось загрузить фотографии") }
            }
        }
    }

    private fun invalidate(state: ReportUiState): ReportUiState = state.copy(
        generated = null, generatedPhotoIds = emptySet(), generatedPointIds = emptySet(),
        saved = false, message = null)

    fun editSelection(selection: ReportSelection) {
        if (mutable.value.busy || mutable.value.saved) return
        mutable.update { current ->
            val edits = current.photos.associateBy { it.photoId }
            val all = current.allPhotos.map { original -> edits[original.photoId]?.let { edited ->
                original.copy(description = edited.description, selected = edited.selected)
            } ?: original }
            invalidate(current.copy(selection = selection, allPhotos = all,
                photos = filterReportPhotos(all, selection)))
        }
    }

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
    fun openCover() {
        if (mutable.value.initialized && !mutable.value.busy && !mutable.value.saved)
            mutable.update { it.copy(step = ReportStep.COVER, message = null) }
    }
    fun next() {
        if (mutable.value.busy || !mutable.value.initialized) return
        mutable.update { current ->
            when (current.step) {
                ReportStep.SCOPE -> selectionError(current.selection)?.let { current.copy(message = it) }
                    ?: current.copy(step = ReportStep.PHOTOS, message = null)
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
            ReportStep.COVER -> if (initialDrawingId == null) ReportStep.SCOPE else ReportStep.PHOTOS
            ReportStep.PHOTOS -> if (initialDrawingId == null) ReportStep.SCOPE else ReportStep.PHOTOS
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
                val generatedTime = Date()
                val generatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(generatedTime)
                val name = reportFileName(current.drawingName, generatedTime)
                val result = PhotoAlbumGenerator(getApplication()).generate(
                    PhotoAlbumInput(cover, input, generatedAtLocal = generatedAt, displayName = name),
                    ReportFileManager.directory(getApplication()), progress)
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
                updateSession(session) { it.copy(saved = true, step = ReportStep.COMPLETE,
                    message = "Фотоотчёт успешно сохранён") }
                val assigned = current.selection.undatedGroupDateTime.takeIf { it.isNotBlank() }
                if (assigned != null) {
                    val undatedIds = current.allPhotos.filter { it.capturedAtLocal.isNullOrBlank() && it.photoId in current.generatedPhotoIds }
                        .map { it.photoId }
                    undatedIds.forEach { id ->
                        if (db.photoDao().updateCaptureTime(id, assigned, null) != 1)
                            throw ReportException("Не удалось записать дату и время фото №$id")
                    }
                }
                if (current.deletePointsAfterSave) {
                    val deleted = deleteUsedPoints(current.generatedPointIds, current.generatedPhotoIds)
                    updateSession(session) { it.copy(message = "Фотоотчёт сохранён. Удалено точек: $deleted.") }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                updateSession(session) { it.copy(message = if (it.saved)
                    "PDF сохранён, но не удалось завершить обновление фото: ${failure.message}"
                    else "Не удалось сохранить PDF: ${failure.message}. Повторите сохранение.") }
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

    class Factory(private val application: Application, private val db: AppDatabase,
                  private val siteId: Long?, private val drawingId: Long?) : ViewModelProvider.Factory {
        constructor(application: Application, db: AppDatabase, drawingId: Long) : this(application, db, null, drawingId)
        companion object {
            fun forSite(application: Application, db: AppDatabase, siteId: Long) = Factory(application, db, siteId, null)
        }
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReportViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ReportViewModel(application, db, siteId, drawingId) as T
        }
    }
}
