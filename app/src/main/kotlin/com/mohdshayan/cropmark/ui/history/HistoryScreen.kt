package com.mohdshayan.cropmark.ui.history

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.cropmark.data.db.CaptureWithEdit
import com.mohdshayan.cropmark.di.ServiceLocator
import com.mohdshayan.cropmark.ui.components.CropmarkTopBar
import com.mohdshayan.cropmark.ui.components.EmptyState
import com.mohdshayan.cropmark.ui.components.SkeletonBlock
import com.mohdshayan.cropmark.ui.components.rememberFileBitmap
import com.mohdshayan.cropmark.ui.theme.Cropmark
import com.mohdshayan.cropmark.ui.theme.PaperShape
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HistoryUi(val loading: Boolean, val groups: List<Pair<String, List<CaptureWithEdit>>>, val names: Map<String, String>)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    val ui: StateFlow<HistoryUi> = combine(ServiceLocator.photos.observeHistory(), ServiceLocator.specs.all) { rows, specs ->
        HistoryUi(false, rows.groupBy { dayLabel(it.createdAt) }.toList(), specs.associate { it.id to it.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi(true, emptyList(), emptyMap()))
}

private val emptyStateSpec = com.mohdshayan.cropmark.core.spec.DocSpec(
    id = "empty", name = "", country = "", countryCode = "",
    print = com.mohdshayan.cropmark.core.spec.PrintSize(35f, 45f, ""),
    headMm = com.mohdshayan.cropmark.core.spec.Range(29f, 34f),
)

fun dayLabel(ms: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(day, today)) return "Today"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (same(day, today)) return "Yesterday"
    return SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(ms))
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onMakePhoto: () -> Unit, viewModel: HistoryViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = Cropmark.colors
    Scaffold(containerColor = c.backdrop, topBar = { CropmarkTopBar("History", onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> LazyVerticalGrid(GridCells.Adaptive(104.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(9) { SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(0.78f), PaperShape) }
                }
                ui.groups.isEmpty() -> EmptyState(
                    "No photos yet.",
                    "Every photo you make is kept here with its edits, so you can save it in another size later.",
                    Modifier.fillMaxSize(),
                    "Make a photo",
                    onMakePhoto,
                    art = {
                        Box(Modifier.size(96.dp, 120.dp).background(c.panel, PaperShape)) {
                            com.mohdshayan.cropmark.ui.components.SpecFrame(
                                com.mohdshayan.cropmark.core.crop.FrameGuides.canonical(emptyStateSpec),
                                Modifier.fillMaxSize(),
                            )
                        }
                    },
                )
                else -> LazyVerticalGrid(
                    GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ui.groups.forEach { (label, rows) ->
                        item(key = "h-$label", span = { GridItemSpan(maxLineSpan) }) {
                            Text(label, style = MaterialTheme.typography.titleMedium, color = c.slate, modifier = Modifier.padding(top = 12.dp))
                        }
                        items(rows, key = { it.id }) { r ->
                            Thumb(r, ui.names[r.specId]) { onOpen(r.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(r: CaptureWithEdit, specName: String?, onClick: () -> Unit) {
    val c = Cropmark.colors
    val bmp by rememberFileBitmap(ServiceLocator.photos.fileFor(r.originalFile), 360)
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.78f).background(c.panel, PaperShape)) {
            bmp?.let { Image(it.asImageBitmap(), "Photo for ${specName ?: "no document yet"}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.height(4.dp))
        Text(specName ?: "Not sized yet", style = MaterialTheme.typography.bodySmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (r.exportCount == 1) "1 export" else "${r.exportCount} exports",
            style = MaterialTheme.typography.labelSmall,
            color = c.slate,
        )
    }
}
