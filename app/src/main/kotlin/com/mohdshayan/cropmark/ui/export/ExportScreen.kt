package com.mohdshayan.cropmark.ui.export

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.cropmark.core.sheet.Paper
import com.mohdshayan.cropmark.ui.components.CropmarkTopBar
import com.mohdshayan.cropmark.ui.components.ErrorPanel
import com.mohdshayan.cropmark.ui.components.Hairline
import com.mohdshayan.cropmark.ui.components.PrimaryButton
import com.mohdshayan.cropmark.ui.components.SecondaryButton
import com.mohdshayan.cropmark.ui.components.SelectChip
import com.mohdshayan.cropmark.ui.components.SkeletonBlock
import com.mohdshayan.cropmark.ui.theme.Cropmark
import com.mohdshayan.cropmark.ui.theme.PaperShape
import kotlin.math.roundToInt

@Composable
fun ExportScreen(
    captureId: Long,
    specId: String,
    onBack: () -> Unit,
    onCounterMode: (String) -> Unit,
    viewModel: ExportViewModel = viewModel(),
) {
    viewModel.bind(captureId, specId)
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = Cropmark.colors
    val context = LocalContext.current
    val activity = context as? Activity
    val jpegPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { viewModel.writePending(it, activity) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { viewModel.writePending(it, activity) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is ExportEvent.Toast -> Toast.makeText(context, e.text, Toast.LENGTH_SHORT).show()
                is ExportEvent.Share -> runCatching { context.startActivity(e.intent) }
                is ExportEvent.PickLocation -> if (e.file.mime == "application/pdf") pdfPicker.launch(e.file.fileName) else jpegPicker.launch(e.file.fileName)
                is ExportEvent.CounterMode -> onCounterMode(e.specId)
            }
        }
    }

    Scaffold(containerColor = c.backdrop, topBar = { CropmarkTopBar("Export", onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> Column(Modifier.padding(20.dp)) {
                    SkeletonBlock(Modifier.size(160.dp, 200.dp), PaperShape)
                    SkeletonBlock(Modifier.padding(top = 16.dp).fillMaxWidth().height(48.dp))
                    SkeletonBlock(Modifier.padding(top = 32.dp).fillMaxWidth().height(260.dp), PaperShape)
                }
                ui.failed -> ErrorPanel(
                    "No face found.", "Use a photo showing head and shoulders.",
                    Modifier.padding(20.dp), primary = "Back to the photo" to onBack,
                )
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 600.dp
                    if (wide) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { FormSection(ui, viewModel, activity) }
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                if (ui.spec?.print != null) SheetSection(ui, viewModel, activity) else UploadOnlyNote()
                            }
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                        ) {
                            FormSection(ui, viewModel, activity)
                            if (ui.spec?.print != null) {
                                Spacer(Modifier.height(12.dp))
                                Hairline()
                                SheetSection(ui, viewModel, activity)
                            } else {
                                UploadOnlyNote()
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormSection(ui: ExportUi, vm: ExportViewModel, activity: Activity?) {
    val c = Cropmark.colors
    val spec = ui.spec ?: return
    Text("Form file", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.width(128.dp).aspectRatio(spec.aspect).background(c.panel, PaperShape)) {
            ui.formPreview?.let {
                Image(it.asImageBitmap(), contentDescription = "Form photo preview", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            when (val f = ui.form) {
                FormState.Computing -> {
                    SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(30.dp))
                    SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.6f).height(16.dp))
                    Text("Fitting the file size", style = MaterialTheme.typography.bodySmall, color = c.slate, modifier = Modifier.padding(top = 8.dp))
                }
                is FormState.Ready -> {
                    val kb = (f.file.bytes.size / 1000f).roundToInt()
                    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${f.file.widthPx} by ${f.file.heightPx} pixels, $kb kilobytes" }) {
                        Text("${f.file.widthPx} x ${f.file.heightPx} px", style = MaterialTheme.typography.headlineSmall, color = c.ink)
                        Text("$kb KB", style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    }
                    Text(limitLine(spec.digital?.minKb, spec.digital?.maxKb), style = MaterialTheme.typography.bodyMedium, color = c.slate)
                    Text("${spec.dpi} dpi, JPEG, no location or camera data", style = MaterialTheme.typography.bodySmall, color = c.slate, modifier = Modifier.padding(top = 4.dp))
                }
                is FormState.TooLarge -> Text(
                    "This photo will not fit under ${f.maxKb} KB at ${f.widthPx} px. Choose a plain background and save again.",
                    style = MaterialTheme.typography.bodyLarge, color = c.magenta,
                )
                is FormState.TooSmall -> Text(
                    "This photo stays under ${f.minKb} KB even at ${f.widthPx} px. Turn off Keep background or raise exposure.",
                    style = MaterialTheme.typography.bodyLarge, color = c.magenta,
                )
            }
        }
    }
    if (ui.widths.size > 1) {
        Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.widths.forEach { w -> SelectChip("$w px", ui.width == w, { vm.setWidth(w) }) }
        }
    }
    val ready = ui.form is FormState.Ready
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("Share", { vm.shareForm() }, Modifier.weight(1f), enabled = ready, icon = Icons.Outlined.Share)
        PrimaryButton("Save photo", { vm.saveForm(activity) }, Modifier.weight(1f), enabled = ready)
    }
}

@Composable
private fun UploadOnlyNote() {
    Text(
        "This document takes an upload only, so there is no print sheet. For printed photos, change the document to one with a size in millimetres or inches.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cropmark.colors.slate,
        modifier = Modifier.padding(top = 20.dp, bottom = 24.dp),
    )
}

private fun limitLine(min: Int?, max: Int?): String = when {
    min != null && max != null -> "Form limit $min to ${if (max >= 1024 && max % 1024 == 0) "${max / 1024} MB" else "$max KB"}"
    max != null -> "Form limit $max KB"
    min != null -> "Form minimum $min KB"
    else -> "No file size limit"
}

@Composable
private fun SheetSection(ui: ExportUi, vm: ExportViewModel, activity: Activity?) {
    val c = Cropmark.colors
    val plan = ui.plan
    Text("Print sheet", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Paper.entries.forEach { p -> SelectChip(p.label, ui.paper == p, { vm.setPaper(p) }) }
    }
    Spacer(Modifier.height(12.dp))
    if (plan != null && plan.capacity == 0) {
        Text(
            "A ${fmtSize(ui.spec)} photo does not fit on ${plan.paper.label} paper. Choose a larger paper.",
            style = MaterialTheme.typography.bodyLarge, color = c.magenta, modifier = Modifier.padding(vertical = 8.dp),
        )
    } else if (plan != null) {
        val capacity = plan.capacity
        val count = plan.cells.size
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Copies", style = MaterialTheme.typography.labelMedium, color = c.slate)
                Text("$count of $capacity", style = MaterialTheme.typography.titleLarge, color = c.ink)
            }
            IconButton(onClick = { vm.setCopies((count - 1).coerceAtLeast(1)) }, enabled = count > 1) {
                Icon(Icons.Outlined.Remove, contentDescription = "Fewer copies", tint = c.ink)
            }
            IconButton(onClick = { vm.setCopies((count + 1).coerceAtMost(capacity)) }, enabled = count < capacity) {
                Icon(Icons.Outlined.Add, contentDescription = "More copies", tint = c.ink)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Cut lines", style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
            Switch(
                checked = ui.cutLines,
                onCheckedChange = vm::setCutLines,
                colors = SwitchDefaults.colors(checkedTrackColor = c.magenta, checkedThumbColor = c.backdrop, uncheckedTrackColor = c.panel, uncheckedBorderColor = c.slate, uncheckedThumbColor = c.slate),
            )
        }
        if (plan.tight) {
            Text("Print with no borders: these photos run to the edge of the paper.", style = MaterialTheme.typography.bodyMedium, color = c.magenta, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (plan.paper.isPdf) {
            Text("Print at actual size and measure the 50 mm ruler before cutting.", style = MaterialTheme.typography.bodyMedium, color = c.slate, modifier = Modifier.padding(bottom = 8.dp))
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            val aspect = plan.pageWidthMm / plan.pageHeightMm
            Box(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(if (aspect > 1f) 1f else 0.72f)
                    .aspectRatio(aspect)
                    .border(1.dp, c.slate, PaperShape),
            ) {
                val preview = ui.sheetPreview
                if (preview != null) {
                    Image(
                        preview.asImageBitmap(),
                        contentDescription = "${plan.paper.label} sheet with $count photos",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else SkeletonBlock(Modifier.fillMaxSize(), PaperShape)
            }
        }
    }
    val canMake = plan != null && plan.capacity > 0 && !ui.busy
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("Share", { vm.shareSheet() }, Modifier.weight(1f), enabled = canMake, icon = Icons.Outlined.Share)
        PrimaryButton(if (ui.busy) "Making sheet" else "Save sheet", { vm.saveSheet(activity) }, Modifier.weight(1f), enabled = canMake)
    }
}

private fun fmtSize(spec: com.mohdshayan.cropmark.core.spec.DocSpec?): String =
    spec?.let { "${com.mohdshayan.cropmark.ui.components.fmtMm(it.widthMm)} x ${com.mohdshayan.cropmark.ui.components.fmtMm(it.heightMm)} mm" } ?: "photo this size"
