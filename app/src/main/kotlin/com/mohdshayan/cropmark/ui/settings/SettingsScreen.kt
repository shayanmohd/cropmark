package com.mohdshayan.cropmark.ui.settings

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.cropmark.core.backup.NotABackupException
import com.mohdshayan.cropmark.data.prefs.LocalCount
import com.mohdshayan.cropmark.data.prefs.Settings
import com.mohdshayan.cropmark.data.prefs.ThemeMode
import com.mohdshayan.cropmark.di.ServiceLocator
import com.mohdshayan.cropmark.ui.components.CropmarkTopBar
import com.mohdshayan.cropmark.ui.components.Hairline
import com.mohdshayan.cropmark.ui.components.SelectChip
import com.mohdshayan.cropmark.ui.theme.Cropmark
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ServiceLocator.appPrefs
    val settings: StateFlow<Settings> = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val busy = MutableStateFlow<String?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 2)

    fun set(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun backup(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            busy.value = "Writing backup"
            try {
                ServiceLocator.backups.write(uri)
                prefs.increment(LocalCount.Backups)
                messages.tryEmit("Backup saved")
            } catch (e: Exception) {
                messages.tryEmit("Could not write the backup. Choose another location.")
            } finally {
                busy.value = null
            }
        }
    }

    fun restore(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            busy.value = "Restoring backup"
            try {
                val r = ServiceLocator.backups.restore(uri)
                messages.tryEmit(
                    "Backup restored: ${plural(r.photos, "photo")}" + (if (r.skipped > 0) ", ${r.skipped} already here" else "") +
                        (if (r.sizes > 0) ", ${plural(r.sizes, "size")}" else ""),
                )
            } catch (e: NotABackupException) {
                messages.tryEmit("This file is not a Cropmark backup.")
            } catch (e: Exception) {
                messages.tryEmit("This file is not a Cropmark backup.")
            } finally {
                busy.value = null
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            ServiceLocator.photos.deleteAll()
            messages.tryEmit("All photos deleted")
        }
    }
}

private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

@Composable
fun SettingsScreen(onBack: () -> Unit, onPage: (String) -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val c = Cropmark.colors
    val context = LocalContext.current
    val prefs = ServiceLocator.appPrefs
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { viewModel.backup(it) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.restore(it) }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Scaffold(containerColor = c.backdrop, topBar = { CropmarkTopBar("Settings", onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Group("Camera")
                SwitchRow("Volume keys take the photo", "Also works with Bluetooth selfie remotes", s.volumeShutter) { v -> viewModel.set { prefs.setVolumeShutter(v) } }
                ChipRow("Timer") {
                    listOf(0 to "Off", 3 to "3 s", 10 to "10 s").forEach { (v, l) ->
                        SelectChip(l, s.timerSeconds == v, { viewModel.set { prefs.setTimerSeconds(v) } })
                    }
                }
                SwitchRow("Counter mode", "After a save, the camera opens again on the same document", s.counterMode) { v -> viewModel.set { prefs.setCounterMode(v) } }
                Hairline(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Group("Photos")
                ChipRow("Starting background") {
                    listOf("spec" to "Document's own", "white" to "White", "light_blue" to "Light blue", "light_grey" to "Light grey").forEach { (v, l) ->
                        SelectChip(l, s.defaultBackground == v, { viewModel.set { prefs.setDefaultBackground(v) } })
                    }
                }
                ChipRow("Theme") {
                    ThemeMode.entries.forEach { m ->
                        SelectChip(m.name, s.theme == m, { viewModel.set { prefs.setTheme(m) } })
                    }
                }
                SwitchRow("Keep counts on this phone", "Photos taken and files saved, shown here only and never sent anywhere", s.localCountsEnabled) { v ->
                    viewModel.set { prefs.setLocalCountsEnabled(v) }
                }
                if (s.localCountsEnabled) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        LocalCount.entries.forEach { k ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(k.label, style = MaterialTheme.typography.bodyLarge, color = c.slate, modifier = Modifier.weight(1f))
                                Text("${s.counts[k] ?: 0}", style = MaterialTheme.typography.titleLarge, color = c.ink)
                            }
                        }
                    }
                }
                Hairline(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Group("Your data")
                busy?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = c.magenta, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
                ActionRow("Back up photos", "One zip file with every photo, edit and custom size", busy == null) {
                    backupLauncher.launch(ServiceLocator.backups.suggestedName())
                }
                ActionRow("Restore backup", "Adds photos from a Cropmark backup; photos already here are skipped", busy == null) {
                    restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                }
                ActionRow("Delete all photos", "Removes every photo, edit and export record from this phone", busy == null, danger = true) {
                    confirmDelete = true
                }
                Hairline(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Group("About")
                ActionRow("Privacy policy", "Nothing leaves your phone", true) { onPage("privacy") }
                ActionRow("Licences", "Fonts and on-device models", true) { onPage("licences") }
                Text(
                    "Cropmark is not affiliated with any government. The issuing office decides whether a photo is accepted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.slate,
                    modifier = Modifier.padding(20.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.backdrop,
            title = { Text("Delete all photos?", color = c.ink) },
            text = { Text("Every photo, edit and export record is deleted from this phone. Files you saved to Pictures or Documents stay. Back up first if you may need them.", color = c.slate) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteAll() }) { Text("Delete all photos", color = c.magenta) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep them", color = c.ink) } },
        )
    }
}

@Composable
private fun Group(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Cropmark.colors.ink, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Cropmark.colors
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.slate)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.magenta, checkedThumbColor = c.backdrop, uncheckedTrackColor = c.panel, uncheckedBorderColor = c.slate, uncheckedThumbColor = c.slate),
        )
    }
}

@Composable
private fun ChipRow(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Cropmark.colors.ink)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun ActionRow(title: String, body: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val c = Cropmark.colors
    Column(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (danger) c.magenta else c.ink)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.slate)
    }
}
