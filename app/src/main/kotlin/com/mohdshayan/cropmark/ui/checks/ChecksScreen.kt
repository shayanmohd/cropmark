package com.mohdshayan.cropmark.ui.checks

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.cropmark.core.check.CheckId
import com.mohdshayan.cropmark.core.check.CheckResult
import com.mohdshayan.cropmark.core.check.ComplianceChecker
import com.mohdshayan.cropmark.core.check.Guide
import com.mohdshayan.cropmark.core.crop.FrameGuides
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.di.ServiceLocator
import com.mohdshayan.cropmark.render.PhotoRenderer
import com.mohdshayan.cropmark.ui.components.CropmarkTopBar
import com.mohdshayan.cropmark.ui.components.ErrorPanel
import com.mohdshayan.cropmark.ui.components.PhotoWithFrame
import com.mohdshayan.cropmark.ui.components.SkeletonBlock
import com.mohdshayan.cropmark.ui.components.fmtMm
import com.mohdshayan.cropmark.ui.editor.Pipeline
import com.mohdshayan.cropmark.ui.theme.Cropmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface ChecksUi {
    data object Loading : ChecksUi
    data object Failed : ChecksUi
    data class Ready(
        val spec: DocSpec,
        val preview: Bitmap,
        val guides: FrameGuides,
        val checks: List<CheckResult>,
        val cropHeightPx: Int,
        val outputHeightPx: Int,
    ) : ChecksUi
}

class ChecksViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow<ChecksUi>(ChecksUi.Loading)
    val ui: StateFlow<ChecksUi> = _ui
    private var key: Pair<Long, String>? = null

    fun bind(captureId: Long, specId: String) {
        if (key == captureId to specId) return
        key = captureId to specId
        viewModelScope.launch {
            try {
                val spec = ServiceLocator.specs.get(specId) ?: throw IllegalStateException()
                val session = ServiceLocator.photos.openSession(captureId)
                val edit = Pipeline.defaultEdit(captureId, spec, ServiceLocator.photos.edit(captureId))
                val ready = withContext(Dispatchers.Default) {
                    val solved = PhotoRenderer.solve(session, spec, edit) ?: throw IllegalStateException()
                    val (w, h) = Pipeline.previewSize(spec)
                    ChecksUi.Ready(
                        spec, PhotoRenderer.render(session, solved.crop, edit, w / 2, h / 2),
                        FrameGuides.fromCrop(session.face, solved.crop, spec), solved.checks,
                        solved.crop.height.roundToInt(), com.mohdshayan.cropmark.core.spec.SpecMath.formPixels(spec).second,
                    )
                }
                _ui.value = ready
            } catch (e: Throwable) {
                _ui.value = ChecksUi.Failed
            }
        }
    }
}

@Composable
fun ChecksScreen(captureId: Long, specId: String, onBack: () -> Unit, viewModel: ChecksViewModel = viewModel()) {
    viewModel.bind(captureId, specId)
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = Cropmark.colors
    var highlight by rememberSaveable { mutableStateOf(Guide.None) }
    Scaffold(containerColor = c.backdrop, topBar = { CropmarkTopBar("Checks", onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = ui) {
                ChecksUi.Loading -> Column(Modifier.padding(20.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth().height(220.dp))
                    repeat(6) { SkeletonBlock(Modifier.padding(top = 14.dp).fillMaxWidth().height(40.dp)) }
                }
                ChecksUi.Failed -> ErrorPanel(
                    "No face found.", "Use a photo showing head and shoulders.",
                    Modifier.padding(20.dp), primary = "Back to the photo" to onBack,
                )
                is ChecksUi.Ready -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 600.dp
                    val passed = s.checks.count { it.passed }
                    val list: @Composable (Modifier) -> Unit = { m ->
                        LazyColumn(m) {
                            item {
                                Text(
                                    "$passed of ${s.checks.size} passed",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = c.ink,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                )
                            }
                            // Fixes first, so what needs doing is at the top.
                            items(s.checks.sortedBy { it.passed }, key = { it.id }) { r ->
                                CheckRow(r, s, highlight == r.guide && !r.passed) {
                                    highlight = if (highlight == r.guide) Guide.None else r.guide
                                }
                            }
                            item {
                                Text(
                                    "Checks measure the photo against the rules. The issuing office makes the final call.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.slate,
                                    modifier = Modifier.padding(20.dp),
                                )
                            }
                        }
                    }
                    val photo: @Composable (Modifier) -> Unit = { m ->
                        PhotoWithFrame(s.spec, s.preview, s.guides, m, highlight = highlight)
                    }
                    if (wide) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                            photo(Modifier.weight(1f).fillMaxHeight().padding(vertical = 16.dp))
                            Spacer(Modifier.width(16.dp))
                            list(Modifier.width(380.dp).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            photo(Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 20.dp, vertical = 8.dp))
                            list(Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(r: CheckResult, s: ChecksUi.Ready, highlighted: Boolean, onClick: () -> Unit) {
    val c = Cropmark.colors
    val (title, detail) = describe(r, s)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !r.passed && r.guide != Guide.None, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (r.passed) Icons.Outlined.Check else Icons.Outlined.Close,
            contentDescription = if (r.passed) "Passed" else "Needs a fix",
            tint = if (r.passed) c.slate else c.magenta,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title + if (r.advisory) " (advisory)" else "",
                style = MaterialTheme.typography.titleMedium,
                color = if (r.passed) c.ink else c.magenta,
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (highlighted) c.ink else c.slate)
        }
    }
}

private fun describe(r: CheckResult, s: ChecksUi.Ready): Pair<String, String> {
    val sp = s.spec
    fun mm(f: Float) = fmtMm(f * sp.heightMm)
    return when (r.id) {
        CheckId.OneFace -> "One face" to if (r.passed) "One person in the photo" else "Use a photo of one person."
        CheckId.HeadHeight -> "Head height" to run {
            val range = sp.headFraction
            val v = if (sp.headMm != null) "${mm(r.measured)} mm, allowed ${mm(range.min)} to ${mm(range.max)} mm"
            else "${(r.measured * 100).roundToInt()} percent, allowed ${(range.min * 100).roundToInt()} to ${(range.max * 100).roundToInt()} percent"
            if (r.passed) v else "$v. Tap to see the brackets, then adjust the crop."
        }
        CheckId.EyeLine -> "Eye line" to run {
            val band = sp.eyeFraction ?: ComplianceChecker.DEFAULT_EYE_BAND
            fun pct(f: Float) = (f * 100).roundToInt()
            val v = if (sp.print != null) "Eyes ${mm(r.measured)} mm up, band ${mm(band.min)} to ${mm(band.max)} mm"
            else "Eyes ${pct(r.measured)} percent up, band ${pct(band.min)} to ${pct(band.max)} percent"
            if (r.passed) v else "$v. Move the photo so the eyes sit in the band."
        }
        CheckId.Centred -> "Face centred" to if (r.passed) "Midline within 5 percent of centre" else "Move the photo sideways until the face is centred."
        CheckId.Level -> "Head level" to run {
            val v = "Tilted ${fmtDeg(r.measured)} degrees, limit ${ComplianceChecker.MAX_ROLL.roundToInt()}"
            if (r.passed) v else "$v. Tilt your head level and take it again."
        }
        CheckId.FacingCamera -> "Facing the camera" to run {
            val v = if (r.pitchAxis) "Tipped ${fmtDeg(r.measured)} degrees up or down, limit ${ComplianceChecker.MAX_PITCH.roundToInt()}"
            else "Turned ${fmtDeg(r.measured)} degrees sideways, limit ${ComplianceChecker.MAX_YAW_PITCH.roundToInt()}"
            if (r.passed) v else "$v. Look straight at the lens."
        }
        CheckId.EyesOpen -> "Eyes open" to if (r.passed) "Both eyes open" else "Keep both eyes open and take it again."
        CheckId.MouthClosed -> "Mouth closed" to if (r.passed) "Neutral expression" else "Close your mouth. Some issuers refuse a smile."
        CheckId.Exposure -> "Exposure" to when {
            r.passed -> "Face brightness ${(r.measured * 100).roundToInt()} percent"
            r.measured < ComplianceChecker.EXPOSURE_MIN ->
                if (sp.editsAllowed) "The face is too dark. Raise exposure or add light." else "The face is too dark. Add light and take it again."
            else ->
                if (sp.editsAllowed) "The face is too bright. Lower exposure or move away from direct light." else "The face is too bright. Move away from direct light and take it again."
        }
        CheckId.Lighting -> "Even lighting" to if (r.passed) "Both sides of the face match" else "One side of the face is darker. Face a window or a lamp."
        CheckId.Background -> "Plain background" to if (r.passed) "Even, with no pattern" else if (r.measured == ComplianceChecker.OVERHANG) { if (sp.editsAllowed) "The photo ends inside the frame. Choose a background colour to fill it, or take it again from further back." else "The photo ends inside the frame. Take it again from further back, with wall around your head and shoulders." } else if (sp.editsAllowed) "The background is dark or patterned. Choose a plain background colour." else "The background is dark or patterned. Stand in front of a plain light wall and take it again."
        CheckId.Resolution -> "Pixels for ${sp.dpi} dpi" to run {
            val v = "${s.cropHeightPx} px tall for ${s.outputHeightPx} px"
            if (r.passed) v else "$v. Move closer or use the back camera."
        }
    }
}

private fun fmtDeg(v: Float) = String.format(java.util.Locale.ROOT, "%.1f", abs(v))
