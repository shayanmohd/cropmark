package com.mohdshayan.cropmark.ui.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.cropmark.camera.CameraController
import com.mohdshayan.cropmark.core.check.FrameRect
import com.mohdshayan.cropmark.core.check.LiveFace
import com.mohdshayan.cropmark.core.check.LiveGuide
import com.mohdshayan.cropmark.core.check.LiveHint
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.data.prefs.LocalCount
import com.mohdshayan.cropmark.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CameraProblem { InUse, NoCamera }

data class CaptureUi(
    val spec: DocSpec? = null,
    val front: Boolean = false,
    val timerSeconds: Int = 0,
    val cameraReady: Boolean = false,
    val problem: CameraProblem? = null,
    val faceCount: Int = 0,
    val face: LiveFace? = null,
    val hint: LiveHint = LiveHint.NoFace,
    val countdown: Int? = null,
    val taking: Boolean = false,
    val firstMoment: Boolean = false,
    val bindKey: Int = 0,
)

class CaptureViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(CaptureUi())
    val ui: StateFlow<CaptureUi> = _ui
    val camera = CameraController(app, ServiceLocator.faceAnalyzer)
    private var specId: String? = null
    private var frame: FrameRect? = null
    private var countdownJob: Job? = null

    fun bind(id: String) {
        if (specId == id) return
        specId = id
        viewModelScope.launch {
            val spec = ServiceLocator.specs.get(id)
            val s = ServiceLocator.appPrefs.settings.first()
            val firstMomentPending = !ServiceLocator.appPrefs.firstFrameMomentShown.first()
            _ui.update { it.copy(spec = spec, front = s.lensFacing == "front", timerSeconds = s.timerSeconds, firstMoment = firstMomentPending) }
        }
    }

    fun setViewAspect(aspect: Float) {
        val spec = _ui.value.spec ?: return
        frame = LiveGuide.frameFor(spec, aspect)
    }

    fun frameRect(aspect: Float): FrameRect? = _ui.value.spec?.let { LiveGuide.frameFor(it, aspect) }

    fun onFrame(count: Int, face: LiveFace?) {
        val spec = _ui.value.spec ?: return
        val f = frame ?: return
        val hint = LiveGuide.evaluate(count, face, f, spec)
        _ui.update { it.copy(faceCount = count, face = face, hint = hint) }
        if (face != null && _ui.value.firstMoment) {
            viewModelScope.launch { ServiceLocator.appPrefs.setFirstFrameMomentShown() }
        }
    }

    fun onBound() = _ui.update { it.copy(cameraReady = true, problem = null) }
    fun onProblem(p: CameraProblem) = _ui.update { it.copy(cameraReady = false, problem = p) }

    fun retry() = _ui.update { it.copy(problem = null, bindKey = it.bindKey + 1) }

    fun toggleLens() {
        val front = !_ui.value.front
        _ui.update { it.copy(front = front, cameraReady = false, face = null, faceCount = 0) }
        viewModelScope.launch { ServiceLocator.appPrefs.setLensFacing(if (front) "front" else "back") }
    }

    fun cycleTimer() {
        val next = when (_ui.value.timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
        _ui.update { it.copy(timerSeconds = next) }
        viewModelScope.launch { ServiceLocator.appPrefs.setTimerSeconds(next) }
    }

    /** Shutter, volume key or remote. A second press during the countdown cancels it. */
    fun shutter(rotation: Int, onCaptured: (Long) -> Unit, onFailed: () -> Unit) {
        val state = _ui.value
        if (state.taking) return
        if (state.countdown != null) {
            countdownJob?.cancel()
            _ui.update { it.copy(countdown = null) }
            return
        }
        countdownJob = viewModelScope.launch {
            for (n in state.timerSeconds downTo 1) {
                _ui.update { it.copy(countdown = n) }
                delay(1000)
            }
            _ui.update { it.copy(countdown = null, taking = true) }
            try {
                val file = camera.takePhoto(rotation)
                val id = ServiceLocator.photos.importFile(file, "camera", if (_ui.value.front) "front" else "back")
                ServiceLocator.appPrefs.increment(LocalCount.PhotosTaken)
                _ui.update { it.copy(taking = false) }
                onCaptured(id)
            } catch (e: Exception) {
                _ui.update { it.copy(taking = false) }
                onFailed()
            }
        }
    }

    override fun onCleared() {
        camera.shutdown()
        ServiceLocator.shutter.active = false
    }
}
