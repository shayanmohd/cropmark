package com.mohdshayan.cropmark.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mohdshayan.cropmark.ui.components.CropmarkTopBar
import com.mohdshayan.cropmark.ui.components.SkeletonBlock
import com.mohdshayan.cropmark.ui.theme.Cropmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TextPageScreen(kind: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = Cropmark.colors
    val title = if (kind == "privacy") "Privacy policy" else "Licences"
    val text by produceState<String?>(null, kind) {
        value = withContext(Dispatchers.IO) {
            val files = if (kind == "privacy") listOf("privacy.txt")
            else listOf("licences/NOTICE.txt", "licences/APACHE-2.0.txt", "licences/OFL-SofiaSansCondensed.txt", "licences/OFL-PublicSans.txt")
            files.joinToString("\n\n") { f -> context.assets.open(f).bufferedReader().use { it.readText() } }
        }
    }
    Scaffold(containerColor = c.backdrop, topBar = { CropmarkTopBar(title, onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val t = text
            if (t == null) {
                SkeletonBlock(Modifier.padding(20.dp).fillMaxWidth().widthIn(max = 600.dp))
            } else {
                Text(
                    t,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink,
                    modifier = Modifier.widthIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                )
            }
        }
    }
}
