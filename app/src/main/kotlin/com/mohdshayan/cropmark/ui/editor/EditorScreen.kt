package com.mohdshayan.cropmark.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.cropmark.core.spec.BackgroundKind
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.di.ServiceLocator
import com.mohdshayan.cropmark.ui.components.CropmarkTopBar
import com.mohdshayan.cropmark.ui.components.ErrorPanel
import com.mohdshayan.cropmark.ui.components.Hairline
import com.mohdshayan.cropmark.ui.components.PhotoWithFrame
import com.mohdshayan.cropmark.ui.components.PrimaryButton
import com.mohdshayan.cropmark.ui.components.SecondaryButton
import com.mohdshayan.cropmark.ui.components.SelectChip
import com.mohdshayan.cropmark.ui.components.SkeletonBlock
import com.mohdshayan.cropmark.ui.nav.Editor
import com.mohdshayan.cropmark.ui.spec.backgroundName
import com.mohdshayan.cropmark.ui.theme.Cropmark
import com.mohdshayan.cropmark.ui.theme.PaperShape
import com.mohdshayan.cropmark.ui.theme.RadiusSm

private enum class Tool(val label: String) { Background("Background"), Feather("Feather"), Exposure("Exposure"), Crop("Crop") }

@Composable
fun EditorScreen(
    route: Editor,
    onBack: () -> Unit,
    onChecks: (Long, String) -> Unit,
    onExport: (Long, String) -> Unit,
    onSwitchSpec: (Long, String) -> Unit,
    onRetake: (String) -> Unit,
    onNewPhoto: (String, String) -> Unit,
    viewModel: EditorViewModel = viewModel(),
) {
    viewModel.bind(route)
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = Cropmark.colors
    var tool by rememberSaveable { mutableStateOf(Tool.Background) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) onNewPhoto(uri.toString(), route.specId)
    }
    val ready = ui as? EditorUi.Ready

    Scaffold(
        containerColor = c.backdrop,
        topBar = { CropmarkTopBar(ready?.spec?.name ?: "Your photo", onBack) },
        bottomBar = {
            if (ready != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SecondaryButton("Change document", { picking = true }, Modifier.weight(1f))
                    PrimaryButton("Export", { viewModel.flush { onExport(ready.captureId, ready.spec.id) } }, Modifier.weight(1f))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = ui) {
                is EditorUi.Loading -> LoadingView(s.specAspect)
                is EditorUi.Failed -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    val (title, body) = when (s.error) {
                        EditorError.NoFace -> "No face found." to "Use a photo showing head and shoulders, facing the camera."
                        EditorError.TwoFaces -> "Two faces found." to "Use a photo of one person."
                        EditorError.Unreadable -> "This photo could not be opened." to "Choose another photo or take a new one."
                        EditorError.SpecMissing -> "This size is gone." to "Pick another document for the photo."
                    }
                    ErrorPanel(
                        title, body, Modifier.widthIn(max = 480.dp),
                        primary = "Take photo" to { onRetake(s.specId) },
                        secondary = "Choose another" to { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                }
                is EditorUi.Ready -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 600.dp
                    val photo: @Composable (Modifier) -> Unit = { m -> EditablePhoto(s, tool == Tool.Crop || !s.spec.editsAllowed, viewModel, m) }
                    val controls: @Composable (Modifier) -> Unit = { m ->
                        Controls(s, tool, { tool = it }, viewModel, { viewModel.flush { onChecks(s.captureId, s.spec.id) } }, m)
                    }
                    if (wide) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                            photo(Modifier.weight(1f).fillMaxHeight().padding(vertical = 12.dp))
                            Spacer(Modifier.width(24.dp))
                            controls(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()))
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            photo(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
                            controls(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    if (picking && ready != null) {
        SpecPickerSheet(current = ready.spec.id, onDismiss = { picking = false }) { id ->
            picking = false
            viewModel.flush { onSwitchSpec(ready.captureId, id) }
        }
    }
}

@Composable
private fun LoadingView(aspect: Float) {
    val c = Cropmark.colors
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            SkeletonBlock(Modifier.fillMaxHeight(0.9f).aspectRatio(aspect), PaperShape)
        }
        Spacer(Modifier.height(16.dp))
        Text("Finding face and edges", style = MaterialTheme.typography.titleMedium, color = c.slate)
        Spacer(Modifier.height(16.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(44.dp))
        Spacer(Modifier.height(10.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(64.dp))
    }
}

@Composable
private fun EditablePhoto(s: EditorUi.Ready, adjusting: Boolean, vm: EditorViewModel, modifier: Modifier) {
    val density = LocalDensity.current
    val gesture = if (adjusting) {
        Modifier.pointerInput(s.spec.id) {
            detectTransformGestures { _, pan, zoom, _ ->
                val mmPerPx = s.spec.heightMm / size.height.coerceAtLeast(1)
                vm.nudge(pan.x * mmPerPx, pan.y * mmPerPx, zoom)
            }
        }
    } else Modifier
    PhotoWithFrame(
        spec = s.spec,
        preview = s.preview,
        guides = s.guides,
        modifier = modifier,
        fade = s.fade,
        photoModifier = gesture,
        description = "Photo sized for ${s.spec.name}, ${s.passed} of ${s.total} checks passed",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    s: EditorUi.Ready,
    tool: Tool,
    onTool: (Tool) -> Unit,
    vm: EditorViewModel,
    onChecks: () -> Unit,
    modifier: Modifier,
) {
    val c = Cropmark.colors
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onChecks)
                .heightIn(min = 52.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val allPass = s.passed == s.total
            Icon(
                if (allPass) Icons.Outlined.Check else Icons.Outlined.Close,
                contentDescription = null,
                tint = if (allPass) c.slate else c.magenta,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${s.passed} of ${s.total} passed", style = MaterialTheme.typography.titleLarge, color = c.ink)
                if (s.crownEstimated) Text("Crown estimated", style = MaterialTheme.typography.bodySmall, color = c.slate)
            }
            Text("See checks", style = MaterialTheme.typography.labelMedium, color = c.slate)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = c.slate)
        }
        Hairline(Modifier.padding(horizontal = 20.dp))
        if (s.spec.editsAllowed) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tool.entries.forEach { t -> SelectChip(t.label, t == tool, { onTool(t) }) }
            }
        } else {
            Text(
                "This issuer rejects edited photos, so Cropmark only crops and sizes this one. Shoot against a plain light wall.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            )
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
            when (if (s.spec.editsAllowed) tool else Tool.Crop) {
                Tool.Background -> BackgroundTool(s, vm)
                Tool.Feather -> FeatherTool(s, vm)
                Tool.Exposure -> ExposureTool(s, vm)
                Tool.Crop -> CropTool(vm)
            }
        }
    }
}

@Composable
private fun BackgroundTool(s: EditorUi.Ready, vm: EditorViewModel) {
    val c = Cropmark.colors
    var custom by rememberSaveable { mutableStateOf(false) }
    val options = listOf(BackgroundKind.White, BackgroundKind.LightBlue, BackgroundKind.LightGrey, BackgroundKind.Custom, BackgroundKind.Keep)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        options.forEach { kind ->
            val selected = s.edit.backgroundMode == kind.key
            Column(
                Modifier
                    .clickable { if (kind == BackgroundKind.Custom) custom = true else vm.setBackground(kind) }
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = backgroundName(kind) + if (selected) ", selected" else "" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fill = when (kind) {
                    BackgroundKind.Custom -> Color(s.edit.backgroundArgb)
                    BackgroundKind.Keep -> c.panel
                    else -> Color(kind.argb)
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .border(if (selected) 3.dp else 1.dp, if (selected) c.magenta else c.slate, RoundedCornerShape(RadiusSm))
                        .padding(if (selected) 5.dp else 3.dp)
                        .background(fill, RoundedCornerShape(RadiusSm - 2.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (kind == BackgroundKind.Keep) {
                        Text("Off", style = MaterialTheme.typography.labelSmall, color = c.slate)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(backgroundName(kind), style = MaterialTheme.typography.bodySmall, color = if (selected) c.ink else c.slate)
            }
        }
    }
    if (custom) {
        CustomColourDialog(
            initial = s.edit.backgroundArgb,
            onDismiss = { custom = false },
            onPick = { argb -> custom = false; vm.setBackground(BackgroundKind.Custom, argb) },
        )
    }
}

@Composable
private fun FeatherTool(s: EditorUi.Ready, vm: EditorViewModel) {
    val c = Cropmark.colors
    Column {
        Text("Hair edge, crisp to soft", style = MaterialTheme.typography.bodyMedium, color = c.slate)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..4).forEach { level ->
                SelectChip("${level + 1}", s.edit.featherLevel == level, { vm.setFeather(level) }, Modifier.width(52.dp))
            }
        }
        if (s.edit.backgroundMode == BackgroundKind.Keep.key) {
            Text("Feather works on a replaced background.", style = MaterialTheme.typography.bodySmall, color = c.slate, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun ExposureTool(s: EditorUi.Ready, vm: EditorViewModel) {
    val c = Cropmark.colors
    var value by rememberSaveable(s.captureId) { mutableFloatStateOf(s.edit.exposureEv) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Exposure", style = MaterialTheme.typography.bodyMedium, color = c.slate, modifier = Modifier.weight(1f))
            Text(String.format(java.util.Locale.ROOT, "%+.1f EV", value), style = MaterialTheme.typography.titleLarge, color = c.ink)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { vm.setExposure(value) },
            valueRange = -1f..1f,
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = c.magenta,
                activeTrackColor = c.magenta,
                inactiveTrackColor = c.rule,
                activeTickColor = c.magenta,
                inactiveTickColor = c.slate,
            ),
            modifier = Modifier.semantics { contentDescription = "Exposure" },
        )
    }
}

@Composable
private fun CropTool(vm: EditorViewModel) {
    val c = Cropmark.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Drag the photo to move it and pinch to resize. It stays inside the rules.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.slate,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        SecondaryButton("Reset", { vm.resetCrop() })
    }
}

@Composable
private fun CustomColourDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val c = Cropmark.colors
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) }
    var hue by rememberSaveable { mutableFloatStateOf(hsv[0]) }
    var depth by rememberSaveable { mutableFloatStateOf(hsv[1].coerceIn(0f, 0.4f)) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, depth, 0.97f - depth * 0.15f))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.backdrop,
        title = { Text("Custom background", color = c.ink) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color(argb), RoundedCornerShape(RadiusSm))
                        .border(1.dp, c.slate, RoundedCornerShape(RadiusSm)),
                )
                Spacer(Modifier.height(16.dp))
                Text("Hue", style = MaterialTheme.typography.labelMedium, color = c.slate)
                Slider(hue, { hue = it }, valueRange = 0f..360f, colors = SliderDefaults.colors(thumbColor = c.magenta, activeTrackColor = c.magenta, inactiveTrackColor = c.rule))
                Text("Tint", style = MaterialTheme.typography.labelMedium, color = c.slate)
                Slider(depth, { depth = it }, valueRange = 0f..0.4f, colors = SliderDefaults.colors(thumbColor = c.magenta, activeTrackColor = c.magenta, inactiveTrackColor = c.rule))
                Text("Most issuers want a plain, light background.", style = MaterialTheme.typography.bodySmall, color = c.slate)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(argb) }) { Text("Use colour", color = c.ink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.slate) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecPickerSheet(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = Cropmark.colors
    val specs by ServiceLocator.specs.all.collectAsStateWithLifecycle(initialValue = emptyList<DocSpec>())
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.backdrop) {
        Text("Size this photo for", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding()) {
            items(specs, key = { it.id }) { sp ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(sp.id) }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(sp.name, style = MaterialTheme.typography.titleMedium, color = if (sp.id == current) c.magenta else c.ink)
                        Text(sp.country, style = MaterialTheme.typography.bodySmall, color = c.slate)
                    }
                    Text(sp.sizeLabel, style = MaterialTheme.typography.titleLarge, color = c.slate, textAlign = TextAlign.End)
                }
            }
        }
    }
}
