package com.mohdshayan.cropmark.ui.history

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.cropmark.data.db.ExportRecord
import com.mohdshayan.cropmark.data.db.PhotoCapture
import com.mohdshayan.cropmark.data.db.PhotoEdit
import com.mohdshayan.cropmark.di.ServiceLocator
import com.mohdshayan.cropmark.ui.components.CropmarkTopBar
import com.mohdshayan.cropmark.ui.components.EmptyState
import com.mohdshayan.cropmark.ui.components.PrimaryButton
import com.mohdshayan.cropmark.ui.components.SecondaryButton
import com.mohdshayan.cropmark.ui.components.rememberFileBitmap
import com.mohdshayan.cropmark.ui.editor.SpecPickerSheet
import com.mohdshayan.cropmark.ui.theme.Cropmark
import com.mohdshayan.cropmark.ui.theme.PaperShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class DetailUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val capture: PhotoCapture? = null,
    val edit: PhotoEdit? = null,
    val exports: List<ExportRecord> = emptyList(),
    val names: Map<String, String> = emptyMap(),
)

class HistoryDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(DetailUi())
    val ui: StateFlow<DetailUi> = _ui
    private var id = -1L

    fun bind(captureId: Long) {
        if (id == captureId) return
        id = captureId
        viewModelScope.launch {
            val capture = ServiceLocator.photos.get(captureId)
            if (capture == null) {
                _ui.value = DetailUi(loading = false, missing = true)
                return@launch
            }
            val edit = ServiceLocator.photos.edit(captureId)
            ServiceLocator.exports.observeFor(captureId).collectLatest { list ->
                val names = ServiceLocator.specs.bundled.associate { it.id to it.name } +
                    ServiceLocator.database.customSpecDao().all().associate { "custom-${it.id}" to it.name }
                _ui.value = DetailUi(false, false, capture, ServiceLocator.photos.edit(captureId) ?: edit, list, names)
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            ServiceLocator.photos.delete(id)
            onDone()
        }
    }
}

@Composable
fun HistoryDetailScreen(captureId: Long, onBack: () -> Unit, onEdit: (String) -> Unit, viewModel: HistoryDetailViewModel = viewModel()) {
    viewModel.bind(captureId)
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = Cropmark.colors
    var confirm by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val capture = ui.capture
    Scaffold(
        containerColor = c.backdrop,
        topBar = {
            CropmarkTopBar(capture?.let { dayLabel(it.createdAt) } ?: "Photo", onBack, actions = {
                if (capture != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete photo") }
            })
        },
        bottomBar = {
            if (capture != null) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SecondaryButton("Edit photo", { onEdit(ui.edit?.specId ?: "us-passport") }, Modifier.weight(1f), enabled = ui.edit != null)
                    PrimaryButton("Export another size", { picking = true }, Modifier.weight(1.4f))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                ui.loading -> Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                    com.mohdshayan.cropmark.ui.components.SkeletonBlock(Modifier.fillMaxWidth().height(300.dp), PaperShape)
                    com.mohdshayan.cropmark.ui.components.SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth(0.6f).height(22.dp))
                    com.mohdshayan.cropmark.ui.components.SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.8f).height(18.dp))
                }
                ui.missing || capture == null -> EmptyState("This photo was deleted.", "Pick another from history.", Modifier.fillMaxSize(), "Back to history", onBack)
                else -> LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 20.dp)) {
                    item {
                        val bmp by rememberFileBitmap(ServiceLocator.photos.fileFor(capture.originalFile), 900)
                        // The box takes the photo's own proportions, so nothing but the photo shows.
                        val ratio = (capture.widthPx.toFloat() / capture.heightPx.coerceAtLeast(1)).coerceIn(0.3f, 3f)
                        Box(Modifier.fillMaxWidth().heightIn(max = 300.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.aspectRatio(ratio).background(c.panel, PaperShape)) {
                                bmp?.let { Image(it.asImageBitmap(), "The original photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            ui.edit?.let { "Last sized for ${ui.names[it.specId] ?: "a deleted size"}" } ?: "Not sized yet",
                            style = MaterialTheme.typography.titleMedium, color = c.ink,
                        )
                        Text(
                            "${sourceLabel(capture.source)}, ${capture.widthPx} x ${capture.heightPx} px",
                            style = MaterialTheme.typography.bodyMedium, color = c.slate,
                        )
                        Text("Past exports", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
                        if (ui.exports.isEmpty()) {
                            Text("Nothing saved from this photo yet.", style = MaterialTheme.typography.bodyMedium, color = c.slate)
                        }
                    }
                    items(ui.exports, key = { it.id }) { x ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text("${ui.names[x.specId] ?: x.specId}, ${kindLabel(x)}", style = MaterialTheme.typography.titleMedium, color = c.ink)
                            Text(
                                "${x.widthPx} x ${x.heightPx} px, ${(x.bytes / 1000f).roundToInt()} KB, ${x.checksPassed} of ${x.checksTotal} passed, " +
                                    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(x.createdAt)),
                                style = MaterialTheme.typography.bodyMedium, color = c.slate,
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = c.backdrop,
            title = { Text("Delete this photo?", color = c.ink) },
            text = { Text("The photo, its edits and its export history are deleted from this phone. Files you already saved stay where you saved them.", color = c.slate) },
            confirmButton = { TextButton(onClick = { confirm = false; viewModel.delete(onBack) }) { Text("Delete photo", color = c.magenta) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep it", color = c.ink) } },
        )
    }
    if (picking) {
        SpecPickerSheet(current = ui.edit?.specId ?: "", onDismiss = { picking = false }) { id ->
            picking = false
            onEdit(id)
        }
    }
}

private fun sourceLabel(s: String) = when (s) {
    "camera" -> "Taken with the camera"
    "share" -> "Shared from another app"
    else -> "Chosen from the gallery"
}

private fun kindLabel(x: ExportRecord) = (if (x.savedUri == null) "shared " else "") + when (x.kind) {
    "form_file" -> "form file"
    else -> "${com.mohdshayan.cropmark.core.sheet.Paper.fromKey(x.paper).label} sheet of ${x.copies}"
}
