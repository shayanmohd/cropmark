package com.mohdshayan.cropmark.ui.custom

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.cropmark.core.spec.BackgroundKind
import com.mohdshayan.cropmark.core.spec.CustomField
import com.mohdshayan.cropmark.core.spec.CustomSpecInput
import com.mohdshayan.cropmark.core.spec.CustomSpecRules
import com.mohdshayan.cropmark.core.spec.FieldError
import com.mohdshayan.cropmark.core.spec.SizeUnit
import com.mohdshayan.cropmark.data.db.CustomSpec
import com.mohdshayan.cropmark.di.ServiceLocator
import com.mohdshayan.cropmark.ui.components.CropmarkTopBar
import com.mohdshayan.cropmark.ui.components.PrimaryButton
import com.mohdshayan.cropmark.ui.components.SectionLabel
import com.mohdshayan.cropmark.ui.components.SelectChip
import com.mohdshayan.cropmark.ui.home.fieldColors
import com.mohdshayan.cropmark.ui.spec.backgroundName
import com.mohdshayan.cropmark.ui.theme.Cropmark
import com.mohdshayan.cropmark.ui.theme.RadiusMd
import kotlinx.coroutines.launch

class CustomSizeViewModel(app: Application) : AndroidViewModel(app) {
    fun save(input: CustomSpecInput, background: BackgroundKind, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val mm = input.unit == SizeUnit.Mm
            val id = ServiceLocator.specs.addCustom(
                CustomSpec(
                    name = input.name.trim(),
                    widthMm = if (mm) input.width else null,
                    heightMm = if (mm) input.height else null,
                    widthPx = if (!mm) input.width?.toInt() else null,
                    heightPx = if (!mm) input.height?.toInt() else null,
                    dpi = input.dpi,
                    headMinPct = input.headMinPct!!, headMaxPct = input.headMaxPct!!,
                    eyeMinPct = input.eyeMinPct, eyeMaxPct = input.eyeMaxPct,
                    minKb = input.minKb, maxKb = input.maxKb,
                    backgroundArgb = background.argb,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            onSaved("custom-$id")
        }
    }
}

@Composable
fun CustomSizeScreen(onBack: () -> Unit, onSaved: (String) -> Unit, viewModel: CustomSizeViewModel = viewModel()) {
    val c = Cropmark.colors
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(SizeUnit.Mm) }
    var width by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var dpi by rememberSaveable { mutableStateOf("300") }
    var headMin by rememberSaveable { mutableStateOf("70") }
    var headMax by rememberSaveable { mutableStateOf("80") }
    var eyeMin by rememberSaveable { mutableStateOf("") }
    var eyeMax by rememberSaveable { mutableStateOf("") }
    var minKb by rememberSaveable { mutableStateOf("") }
    var maxKb by rememberSaveable { mutableStateOf("") }
    var background by rememberSaveable { mutableStateOf(BackgroundKind.White) }
    var errors by androidx.compose.runtime.remember { mutableStateOf<List<Pair<CustomField, String>>>(emptyList()) }
    fun err(f: CustomField) = errors.firstOrNull { it.first == f }?.second

    Scaffold(containerColor = c.backdrop, topBar = { CropmarkTopBar("Custom size", onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp),
            ) {
                if (width.isBlank() && height.isBlank()) {
                    Text("Enter a width and height", style = MaterialTheme.typography.headlineMedium, color = c.ink, modifier = Modifier.padding(top = 8.dp))
                    Text("Use the numbers from your form's photo instructions.", style = MaterialTheme.typography.bodyLarge, color = c.slate)
                }
                Field("Name", name, { name = it }, err(CustomField.Name), KeyboardType.Text)
                SectionLabel("Size")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("Millimetres", unit == SizeUnit.Mm, { unit = SizeUnit.Mm })
                    SelectChip("Pixels", unit == SizeUnit.Px, { unit = SizeUnit.Px })
                }
                val u = if (unit == SizeUnit.Mm) "mm" else "px"
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Width, $u", width, { width = it }, err(CustomField.Width), KeyboardType.Decimal, Modifier.weight(1f))
                    Field("Height, $u", height, { height = it }, err(CustomField.Height), KeyboardType.Decimal, Modifier.weight(1f))
                }
                if (unit == SizeUnit.Mm) Field("Print resolution, dpi", dpi, { dpi = it }, err(CustomField.Dpi), KeyboardType.Number)
                SectionLabel("Head, chin to crown, percent of the height")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Smallest", headMin, { headMin = it }, err(CustomField.Head), KeyboardType.Decimal, Modifier.weight(1f))
                    Field("Largest", headMax, { headMax = it }, null, KeyboardType.Decimal, Modifier.weight(1f))
                }
                SectionLabel("Eye line from the bottom, percent, optional")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Lowest", eyeMin, { eyeMin = it }, err(CustomField.Eye), KeyboardType.Decimal, Modifier.weight(1f))
                    Field("Highest", eyeMax, { eyeMax = it }, null, KeyboardType.Decimal, Modifier.weight(1f))
                }
                SectionLabel("File size, KB, optional")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Smallest", minKb, { minKb = it }, err(CustomField.Kb), KeyboardType.Number, Modifier.weight(1f))
                    Field("Largest", maxKb, { maxKb = it }, null, KeyboardType.Number, Modifier.weight(1f))
                }
                SectionLabel("Background")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(BackgroundKind.White, BackgroundKind.LightBlue, BackgroundKind.LightGrey).forEach { k ->
                        SelectChip(backgroundName(k), background == k, { background = k })
                    }
                }
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Save size", {
                    val input = CustomSpecInput(
                        name, unit, width.toFloatOrNull(), height.toFloatOrNull(), dpi.toIntOrNull() ?: 0,
                        headMin.toFloatOrNull(), headMax.toFloatOrNull(), eyeMin.toFloatOrNull(), eyeMax.toFloatOrNull(),
                        minKb.toIntOrNull(), maxKb.toIntOrNull(),
                    )
                    val found: List<FieldError> = CustomSpecRules.validate(if (unit == SizeUnit.Px) input.copy(dpi = 300) else input)
                    errors = found.map { it.field to it.message }
                    if (found.isEmpty()) {
                        viewModel.save(if (unit == SizeUnit.Px) input.copy(dpi = 300) else input, background) {
                            Toast.makeText(context, "Size saved", Toast.LENGTH_SHORT).show()
                            onSaved(it)
                        }
                    }
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    type: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val c = Cropmark.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it, color = c.magenta) } },
        keyboardOptions = KeyboardOptions(keyboardType = type),
        shape = RoundedCornerShape(RadiusMd),
        colors = fieldColors(),
        modifier = modifier.padding(top = 8.dp),
    )
}
