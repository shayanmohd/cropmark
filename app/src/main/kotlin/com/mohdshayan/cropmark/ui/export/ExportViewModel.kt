package com.mohdshayan.cropmark.ui.export

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.cropmark.core.sheet.Paper
import com.mohdshayan.cropmark.core.sheet.SheetPlan
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.core.spec.SpecMath
import com.mohdshayan.cropmark.data.db.PhotoEdit
import com.mohdshayan.cropmark.data.prefs.LocalCount
import com.mohdshayan.cropmark.data.repo.ExportFile
import com.mohdshayan.cropmark.data.repo.FormFileResult
import com.mohdshayan.cropmark.data.repo.PhotoSession
import com.mohdshayan.cropmark.di.ServiceLocator
import com.mohdshayan.cropmark.render.PhotoRenderer
import com.mohdshayan.cropmark.render.SheetRenderer
import com.mohdshayan.cropmark.render.Solved
import com.mohdshayan.cropmark.review.ReviewPrompter
import com.mohdshayan.cropmark.ui.editor.Pipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface FormState {
    data object Computing : FormState
    data class Ready(val file: ExportFile) : FormState
    data class TooLarge(val widthPx: Int, val maxKb: Int) : FormState
    data class TooSmall(val widthPx: Int, val minKb: Int) : FormState
}

data class ExportUi(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val spec: DocSpec? = null,
    val formPreview: Bitmap? = null,
    val widths: List<Int> = emptyList(),
    val width: Int = 0,
    val form: FormState = FormState.Computing,
    val paper: Paper = Paper.FourBySix,
    val copies: Int? = null,
    val cutLines: Boolean = true,
    val plan: SheetPlan? = null,
    val sheetPreview: Bitmap? = null,
    val busy: Boolean = false,
    val passed: Int = 0,
    val total: Int = 0,
)

sealed interface ExportEvent {
    data class Toast(val text: String) : ExportEvent
    data class Share(val intent: Intent) : ExportEvent
    data class PickLocation(val file: ExportFile) : ExportEvent
    data class CounterMode(val specId: String) : ExportEvent
}

class ExportViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(ExportUi())
    val ui: StateFlow<ExportUi> = _ui
    private val _events = MutableSharedFlow<ExportEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ExportEvent> = _events

    private var key: Pair<Long, String>? = null
    private var captureId = -1L
    private var session: PhotoSession? = null
    private var edit: PhotoEdit? = null
    private var solved: Solved? = null
    private var formJob: Job? = null
    private var sheetJob: Job? = null
    private var pending: ExportFile? = null

    fun bind(id: Long, specId: String) {
        if (key == id to specId) return
        key = id to specId
        captureId = id
        viewModelScope.launch {
            try {
                val spec = ServiceLocator.specs.get(specId) ?: throw IllegalStateException()
                val s = ServiceLocator.photos.openSession(id)
                val e = Pipeline.defaultEdit(id, spec, ServiceLocator.photos.edit(id))
                val sv = withContext(Dispatchers.Default) { PhotoRenderer.solve(s, spec, e) } ?: throw IllegalStateException()
                session = s; edit = e; solved = sv
                val settings = ServiceLocator.appPrefs.settings.first()
                val widths = SpecMath.widthChoices(spec)
                _ui.value = ExportUi(
                    loading = false, spec = spec, widths = widths, width = widths.first(),
                    paper = Paper.fromKey(settings.sheetPaper), cutLines = settings.cutLines,
                    passed = sv.passed, total = sv.total,
                )
                computeForm()
                computeSheet()
            } catch (ex: Throwable) {
                _ui.value = ExportUi(loading = false, failed = true)
            }
        }
    }

    fun setWidth(w: Int) {
        _ui.update { it.copy(width = w) }
        computeForm()
    }

    fun setPaper(p: Paper) {
        _ui.update { it.copy(paper = p, copies = null) }
        viewModelScope.launch { ServiceLocator.appPrefs.setSheetPaper(p.key) }
        computeSheet()
    }

    fun setCopies(n: Int) {
        _ui.update { it.copy(copies = n) }
        computeSheet()
    }

    fun setCutLines(on: Boolean) {
        _ui.update { it.copy(cutLines = on) }
        viewModelScope.launch { ServiceLocator.appPrefs.setCutLines(on) }
        computeSheet()
    }

    private fun computeForm() {
        val s = session ?: return
        val e = edit ?: return
        val sv = solved ?: return
        val spec = _ui.value.spec ?: return
        formJob?.cancel()
        _ui.update { it.copy(form = FormState.Computing) }
        formJob = viewModelScope.launch {
            val result = ServiceLocator.exports.formFile(s, spec, e, sv, _ui.value.width)
            val preview = withContext(Dispatchers.Default) {
                val (w, h) = Pipeline.previewSize(spec)
                PhotoRenderer.render(s, sv.crop, e, w / 2, h / 2)
            }
            _ui.update {
                it.copy(
                    formPreview = preview,
                    form = when (result) {
                        is FormFileResult.Ready -> FormState.Ready(result.file)
                        is FormFileResult.TooLarge -> FormState.TooLarge(result.widthPx, result.maxKb)
                        is FormFileResult.TooSmall -> FormState.TooSmall(result.widthPx, result.minKb)
                    },
                )
            }
        }
    }

    private fun computeSheet() {
        val s = session ?: return
        val e = edit ?: return
        val sv = solved ?: return
        val ui = _ui.value
        val spec = ui.spec ?: return
        sheetJob?.cancel()
        sheetJob = viewModelScope.launch {
            val plan = ServiceLocator.exports.sheetPlan(spec, ui.paper, ui.copies)
            _ui.update { it.copy(plan = plan) }
            val preview = withContext(Dispatchers.Default) {
                val photo = PhotoRenderer.render(s, sv.crop, e, 180, (180 / spec.aspect).toInt())
                SheetRenderer.preview(plan, photo, ui.cutLines, 900).also { photo.recycle() }
            }
            _ui.update { it.copy(sheetPreview = preview) }
        }
    }

    fun saveForm(activity: Activity?) {
        val file = (_ui.value.form as? FormState.Ready)?.file ?: return
        save(file, activity, LocalCount.FormFiles, "Photo saved")
    }

    fun shareForm() {
        val file = (_ui.value.form as? FormState.Ready)?.file ?: return
        share(file)
    }

    fun saveSheet(activity: Activity?) = withSheet { save(it, activity, LocalCount.Sheets, "Sheet saved") }

    fun shareSheet() = withSheet { share(it) }

    private fun withSheet(block: (ExportFile) -> Unit) {
        val s = session ?: return
        val e = edit ?: return
        val sv = solved ?: return
        val ui = _ui.value
        val spec = ui.spec ?: return
        val plan = ui.plan?.takeIf { it.capacity > 0 } ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                block(ServiceLocator.exports.sheetFile(s, spec, e, sv, plan, ui.cutLines))
            } catch (ex: Exception) {
                _events.tryEmit(ExportEvent.Toast("Could not make the sheet. Try a smaller paper size."))
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private fun save(file: ExportFile, activity: Activity?, count: LocalCount, done: String) {
        if (Build.VERSION.SDK_INT < 29) {
            pending = file
            _events.tryEmit(ExportEvent.PickLocation(file))
            return
        }
        viewModelScope.launch {
            try {
                val uri = ServiceLocator.exports.saveToMediaStore(file)
                afterSave(file, uri, activity, count, done)
            } catch (ex: Exception) {
                _events.tryEmit(ExportEvent.Toast("Could not save. Choose another location."))
            }
        }
    }

    /** Android 8 and 9 come back here from the system document picker. */
    fun writePending(uri: Uri?, activity: Activity?) {
        val file = pending ?: return
        pending = null
        if (uri == null) return
        viewModelScope.launch {
            try {
                ServiceLocator.exports.writeTo(uri, file)
                val isForm = file.kind == com.mohdshayan.cropmark.data.repo.ExportRepository.KIND_FORM
                afterSave(file, uri, activity, if (isForm) LocalCount.FormFiles else LocalCount.Sheets, if (isForm) "Photo saved" else "Sheet saved")
            } catch (ex: Exception) {
                _events.tryEmit(ExportEvent.Toast("Could not save. Choose another location."))
            }
        }
    }

    private suspend fun afterSave(file: ExportFile, uri: Uri, activity: Activity?, count: LocalCount, done: String) {
        val spec = _ui.value.spec ?: return
        solved?.let { ServiceLocator.exports.record(captureId, spec, file, uri, it) }
        ServiceLocator.appPrefs.increment(count)
        _events.tryEmit(ExportEvent.Toast(done))
        if (ServiceLocator.appPrefs.settings.first().counterMode) {
            _events.tryEmit(ExportEvent.CounterMode(spec.id))
        } else {
            ReviewPrompter.onSaved(activity)
        }
    }

    private fun share(file: ExportFile) {
        viewModelScope.launch {
            val spec = _ui.value.spec ?: return@launch
            _events.tryEmit(ExportEvent.Share(ServiceLocator.exports.shareIntent(file)))
            solved?.let { ServiceLocator.exports.record(captureId, spec, file, null, it) }
        }
    }
}
