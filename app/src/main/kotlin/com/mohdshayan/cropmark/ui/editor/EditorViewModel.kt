package com.mohdshayan.cropmark.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.cropmark.core.backup.ImportPlanner
import com.mohdshayan.cropmark.core.crop.FrameGuides
import com.mohdshayan.cropmark.core.spec.BackgroundKind
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.data.db.PhotoEdit
import com.mohdshayan.cropmark.data.repo.PhotoSession
import com.mohdshayan.cropmark.di.ServiceLocator
import com.mohdshayan.cropmark.render.PhotoRenderer
import com.mohdshayan.cropmark.render.Solved
import com.mohdshayan.cropmark.ui.nav.Editor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditorError { NoFace, TwoFaces, Unreadable, SpecMissing }

sealed interface EditorUi {
    data class Loading(val specAspect: Float) : EditorUi
    data class Failed(val error: EditorError, val specId: String) : EditorUi
    data class Ready(
        val captureId: Long,
        val spec: DocSpec,
        val edit: PhotoEdit,
        val preview: Bitmap,
        val guides: FrameGuides,
        val passed: Int,
        val total: Int,
        val crownEstimated: Boolean,
        /** True when the last change was a background swap, which cross-fades. */
        val fade: Boolean,
    ) : EditorUi
}

/** Loads a capture under a spec and turns an edit into crop, checks and pixels. */
object Pipeline {
    suspend fun defaultEdit(captureId: Long, spec: DocSpec, previous: PhotoEdit?): PhotoEdit {
        val edit = if (previous != null) {
            if (previous.specId == spec.id) {
                previous
            } else {
                val moved = previous.copy(specId = spec.id, nudgeScale = 1f, nudgeXmm = 0f, nudgeYmm = 0f)
                // Coming from a document that kept the photo as shot, the kept background was the rule, not a choice.
                val fromLocked = ServiceLocator.specs.get(previous.specId)?.editsAllowed == false
                if (fromLocked && spec.editsAllowed) moved.copy(backgroundMode = startingBackground(spec)) else moved
            }
        } else {
            PhotoEdit(captureId, spec.id, startingBackground(spec), BackgroundKind.White.argb, 2, 0f, 1f, 0f, 0f)
        }
        return forSpec(edit, spec)
    }

    private suspend fun startingBackground(spec: DocSpec): String =
        startingBackground(spec, ServiceLocator.appPrefs.settings.first().defaultBackground)

    /**
     * The colour a photo opens with: the global preference, unless the issuer's colour list is the
     * whole rule and that preference is not on it.
     */
    fun startingBackground(spec: DocSpec, pref: String): String =
        if (pref == "spec" || (spec.backgroundsExhaustive && pref !in spec.backgrounds)) spec.defaultBackground.key else pref

    /** An issuer that rejects edited photos gets the photo as shot: background kept, exposure untouched. */
    fun forSpec(edit: PhotoEdit, spec: DocSpec): PhotoEdit =
        if (spec.editsAllowed) edit else edit.copy(backgroundMode = BackgroundKind.Keep.key, exposureEv = 0f)

    fun previewSize(spec: DocSpec): Pair<Int, Int> {
        val h = 960
        return (h * spec.aspect).toInt().coerceAtLeast(1) to h
    }
}

@OptIn(FlowPreview::class)
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow<EditorUi>(EditorUi.Loading(1f))
    val ui: StateFlow<EditorUi> = _ui

    private var bound: Editor? = null
    private var session: PhotoSession? = null
    private var spec: DocSpec? = null
    private var edit: PhotoEdit? = null
    private var saveJob: Job? = null
    private val renders = Channel<Boolean>(Channel.CONFLATED)

    /** Set once the capture exists, so a rotation or process restore reopens the same photo. */
    var captureId: Long = -1
        private set

    init {
        viewModelScope.launch {
            renders.receiveAsFlow().collect { fade -> renderNow(fade) }
        }
    }

    fun bind(route: Editor) {
        if (bound == route) return
        bound = route
        captureId = route.captureId
        viewModelScope.launch { load(route) }
    }

    private suspend fun load(route: Editor) {
        val photos = ServiceLocator.photos
        val sp = ServiceLocator.specs.get(route.specId)
        if (sp == null) {
            _ui.value = EditorUi.Failed(EditorError.SpecMissing, route.specId)
            return
        }
        spec = sp
        _ui.value = EditorUi.Loading(sp.aspect)
        try {
            if (captureId < 0) {
                val uri = route.importUri ?: throw IllegalStateException("Nothing to open")
                captureId = photos.importUri(Uri.parse(uri), route.source)
            }
            val s = photos.openSession(captureId)
            session = s
            if (s.face.faceCount != 1) {
                // A photo that cannot be used is not kept in history unless the user already worked on it.
                if (photos.edit(captureId) == null) photos.delete(captureId)
                _ui.value = EditorUi.Failed(if (s.face.faceCount == 0) EditorError.NoFace else EditorError.TwoFaces, sp.id)
                return
            }
            val e = Pipeline.defaultEdit(captureId, sp, photos.edit(captureId))
            edit = e
            photos.saveEdit(e)
            ServiceLocator.appPrefs.setLastSpecId(sp.id)
            renderNow(fade = false)
        } catch (e: Throwable) {
            android.util.Log.w("Cropmark", "Could not open the photo", e)
            _ui.value = EditorUi.Failed(EditorError.Unreadable, sp.id)
        }
    }

    private suspend fun renderNow(fade: Boolean) {
        val s = session ?: return
        val sp = spec ?: return
        val e = edit ?: return
        val result = withContext(Dispatchers.Default) {
            val solved: Solved = PhotoRenderer.solve(s, sp, e) ?: return@withContext null
            val (w, h) = Pipeline.previewSize(sp)
            val bmp = PhotoRenderer.render(s, solved.crop, e, w, h)
            Triple(solved, bmp, FrameGuides.fromCrop(s.face, solved.crop, sp))
        } ?: return
        val (solved, bmp, guides) = result
        // Keep the stored nudge equal to what the spec allowed, so drags do not pile up past a limit.
        val clamped = solved.crop.nudge
        val newer = edit
        if (newer === e && (clamped.scale != e.nudgeScale || clamped.xMm != e.nudgeXmm || clamped.yMm != e.nudgeYmm)) {
            edit = e.copy(nudgeScale = clamped.scale, nudgeXmm = clamped.xMm, nudgeYmm = clamped.yMm)
        }
        _ui.value = EditorUi.Ready(
            captureId, sp, edit ?: e, bmp, guides, solved.passed, solved.total, s.face.crownEstimated, fade,
        )
    }

    private fun update(fade: Boolean = false, change: (PhotoEdit) -> PhotoEdit) {
        val e = edit ?: return
        val sp = spec
        edit = if (sp != null) Pipeline.forSpec(change(e), sp) else change(e)
        renders.trySend(fade)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            edit?.let { ServiceLocator.photos.saveEdit(it) }
        }
    }

    fun setBackground(mode: BackgroundKind, argb: Int? = null) = update(fade = true) {
        it.copy(backgroundMode = mode.key, backgroundArgb = argb ?: it.backgroundArgb)
    }

    fun setFeather(level: Int) = update { it.copy(featherLevel = level) }
    fun setExposure(ev: Float) = update { it.copy(exposureEv = ev) }

    fun nudge(dxMm: Float, dyMm: Float, zoom: Float) = update {
        it.copy(nudgeXmm = it.nudgeXmm + dxMm, nudgeYmm = it.nudgeYmm + dyMm, nudgeScale = it.nudgeScale * zoom)
    }

    fun resetCrop() = update { it.copy(nudgeScale = 1f, nudgeXmm = 0f, nudgeYmm = 0f) }

    /** Writes the edit before leaving for checks or export. */
    fun flush(then: () -> Unit) {
        viewModelScope.launch {
            saveJob?.cancel()
            edit?.let { ServiceLocator.photos.saveEdit(it) }
            then()
        }
    }

    fun isCustom(id: String) = id.startsWith(ImportPlanner.CUSTOM_PREFIX)
}
