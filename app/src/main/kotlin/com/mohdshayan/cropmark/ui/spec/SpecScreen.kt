package com.mohdshayan.cropmark.ui.spec

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.cropmark.core.backup.ImportPlanner
import com.mohdshayan.cropmark.core.spec.BackgroundKind
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.core.spec.SpecCatalog
import com.mohdshayan.cropmark.di.ServiceLocator
import com.mohdshayan.cropmark.ui.components.CropmarkTopBar
import com.mohdshayan.cropmark.ui.components.EmptyState
import com.mohdshayan.cropmark.ui.components.PrimaryButton
import com.mohdshayan.cropmark.ui.components.SecondaryButton
import com.mohdshayan.cropmark.ui.components.SizeDiagram
import com.mohdshayan.cropmark.ui.components.fmtMm
import com.mohdshayan.cropmark.ui.theme.Cropmark
import com.mohdshayan.cropmark.ui.theme.RadiusMd
import com.mohdshayan.cropmark.ui.theme.RadiusSm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed interface SpecState {
    data object Loading : SpecState
    data object Missing : SpecState
    data class Ready(val spec: DocSpec) : SpecState
}

class SpecViewModel(app: Application) : AndroidViewModel(app) {
    private var id: String = ""
    lateinit var state: StateFlow<SpecState>

    fun bind(specId: String) {
        if (id == specId) return
        id = specId
        state = ServiceLocator.specs.all
            .map { all -> all.firstOrNull { it.id == specId }?.let { SpecState.Ready(it) } ?: SpecState.Missing }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpecState.Loading)
        viewModelScope.launch { ServiceLocator.appPrefs.setLastSpecId(specId) }
    }

    fun deleteCustom(onDone: () -> Unit) {
        val customId = id.removePrefix(ImportPlanner.CUSTOM_PREFIX).toLongOrNull() ?: return
        viewModelScope.launch {
            ServiceLocator.specs.deleteCustom(customId)
            onDone()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecScreen(
    specId: String,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onGalleryPicked: (String) -> Unit,
    viewModel: SpecViewModel = viewModel(),
) {
    viewModel.bind(specId)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Cropmark.colors
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) onGalleryPicked(uri.toString())
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val spec = (state as? SpecState.Ready)?.spec

    Scaffold(
        containerColor = c.backdrop,
        topBar = {
            CropmarkTopBar(spec?.name ?: "", onBack, actions = {
                if (spec?.custom == true) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete this size")
                    }
                }
            })
        },
        bottomBar = {
            if (spec != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(c.backdrop)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PrimaryButton("Take photo", onTakePhoto, Modifier.fillMaxWidth().widthIn(max = 480.dp), icon = Icons.Outlined.CameraAlt)
                    SecondaryButton(
                        "Choose from gallery",
                        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        Modifier.fillMaxWidth().widthIn(max = 480.dp),
                        icon = Icons.Outlined.PhotoLibrary,
                    )
                }
            }
        },
    ) { padding ->
        when (val s = state) {
            SpecState.Loading -> Box(Modifier.fillMaxSize().padding(padding))
            SpecState.Missing -> EmptyState(
                "This size is gone",
                "It was deleted. Pick another document.",
                Modifier.fillMaxSize().padding(padding),
                "Back to documents",
                onBack,
            )
            is SpecState.Ready -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    val sp = s.spec
                    Spacer(Modifier.height(8.dp))
                    Text(sp.sizeLabel, style = MaterialTheme.typography.displaySmall, color = c.ink)
                    Text(sp.country, style = MaterialTheme.typography.bodyMedium, color = c.slate)
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SizeDiagram(sp, 230.dp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Fact("Head, chin to crown", headText(sp))
                    sp.eyeMm?.let { Fact("Eye line from the bottom", "${fmtMm(it.min)} to ${fmtMm(it.max)} mm") }
                    sp.eyePct?.let { Fact("Eye line from the bottom", "${fmtMm(it.min)} to ${fmtMm(it.max)} percent of the height") }
                    sp.digital?.let { d ->
                        val px = if (d.minPx == d.maxPx) "${d.minPx} x ${d.heightFor(d.minPx)} px"
                        else "${d.minPx} x ${d.heightFor(d.minPx)} to ${d.maxPx} x ${d.heightFor(d.maxPx)} px"
                        Fact("Form file", px + kbText(d.minKb, d.maxKb))
                    }
                    if (!sp.editsAllowed) {
                        Fact("Editing", "The issuer rejects photos changed with software. Cropmark crops and sizes this photo but keeps the background and exposure as shot.")
                    }
                    Text("Background", style = MaterialTheme.typography.labelMedium, color = c.slate, modifier = Modifier.padding(top = 14.dp))
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        sp.backgrounds.forEach { key ->
                            val kind = BackgroundKind.fromKey(key)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(width = 30.dp, height = 20.dp)
                                        .background(Color(kind.argb), RoundedCornerShape(RadiusSm))
                                        .border(1.dp, c.slate, RoundedCornerShape(RadiusSm)),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(backgroundName(kind), style = MaterialTheme.typography.bodyLarge, color = c.ink)
                            }
                        }
                    }
                    if (sp.notes.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(sp.notes, style = MaterialTheme.typography.bodyLarge, color = c.ink)
                    }
                    Spacer(Modifier.height(16.dp))
                    if (!sp.custom) {
                        val date = prettyDate(sp.verifiedOn)
                        Text("Rules last checked $date", style = MaterialTheme.typography.bodyMedium, color = c.slate)
                        val now = Calendar.getInstance()
                        val months = SpecCatalog.monthsSince(sp.verifiedOn, now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
                        if (months != null && months > 18) {
                            Text(
                                "Check the issuer's page before you print.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.magenta,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        val host = runCatching { Uri.parse(sp.sourceUrl).host }.getOrNull()
                        if (host != null) {
                            Text(
                                "Source: $host",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.ink,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sp.sourceUrl))) }
                                    }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                    Text(
                        "Cropmark is not affiliated with any government. The issuing office decides whether a photo is accepted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.slate,
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 24.dp)
                            .background(c.panel, RoundedCornerShape(RadiusMd))
                            .padding(14.dp),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this size?") },
            text = { Text("Photos already made with it stay in history.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteCustom(onBack) }) { Text("Delete size", color = c.magenta) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it", color = c.ink) } },
            containerColor = c.backdrop,
        )
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Cropmark.colors.slate)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Cropmark.colors.ink)
    }
}

fun headText(sp: DocSpec): String = when {
    sp.headMm != null -> "${fmtMm(sp.headMm.min)} to ${fmtMm(sp.headMm.max)} mm"
    sp.headPct != null -> "${fmtMm(sp.headPct.min)} to ${fmtMm(sp.headPct.max)} percent of the height"
    else -> "70 to 80 percent of the height. The issuer gives no head size; using the common ICAO range."
}

fun kbText(min: Int?, max: Int?): String = when {
    min != null && max != null -> ", ${kb(min)} to ${kb(max)}"
    max != null -> ", ${kb(max)} or less"
    min != null -> ", at least ${kb(min)}"
    else -> ""
}

private fun kb(v: Int) = if (v >= 1024 && v % 1024 == 0) "${v / 1024} MB" else "$v KB"

fun backgroundName(kind: BackgroundKind) = when (kind) {
    BackgroundKind.White -> "White"
    BackgroundKind.LightBlue -> "Light blue"
    BackgroundKind.LightGrey -> "Light grey"
    BackgroundKind.Custom -> "Custom"
    BackgroundKind.Keep -> "Keep"
}

fun prettyDate(iso: String): String = runCatching {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(iso)!!
    SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(d)
}.getOrDefault(iso)
