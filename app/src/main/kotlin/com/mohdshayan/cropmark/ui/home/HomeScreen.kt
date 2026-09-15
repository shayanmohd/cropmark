package com.mohdshayan.cropmark.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.di.ServiceLocator
import com.mohdshayan.cropmark.ui.components.CropmarkTopBar
import com.mohdshayan.cropmark.ui.components.EmptyState
import com.mohdshayan.cropmark.ui.components.SkeletonBlock
import com.mohdshayan.cropmark.ui.components.rememberFileBitmap
import com.mohdshayan.cropmark.ui.theme.Condensed
import com.mohdshayan.cropmark.ui.theme.Cropmark
import com.mohdshayan.cropmark.ui.theme.PaperShape
import com.mohdshayan.cropmark.ui.theme.RadiusMd

@Composable
fun HomeScreen(
    onSpec: (String) -> Unit,
    onSharedPhoto: (String, String) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onCustomSize: () -> Unit,
    onRecent: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Cropmark.colors
    val pick: (DocSpec) -> Unit = { spec ->
        val shared = state.sharedPhoto
        if (shared != null) {
            viewModel.consumeShared()
            onSharedPhoto(shared.toString(), spec.id)
        } else onSpec(spec.id)
    }

    Scaffold(
        containerColor = c.backdrop,
        topBar = {
            CropmarkTopBar(title = "Cropmark", onBack = null, actions = {
                IconButton(onClick = onHistory) { Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Photo history") }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            })
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(8.dp))
                        Text("What is the photo for?", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Search documents or sizes") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setQuery("") }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(RadiusMd),
                            colors = fieldColors(),
                        )
                    }
                }
                if (state.sharedPhoto != null) {
                    item {
                        Text(
                            "Photo received. Pick what it is for and Cropmark will size it.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.ink,
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .fillMaxWidth()
                                .background(c.panel, RoundedCornerShape(RadiusMd))
                                .padding(16.dp),
                        )
                    }
                }
                if (state.query.isNotBlank()) {
                    if (state.results.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No document matches",
                                body = "Make a custom size with your form's width, height and head range.",
                                actionLabel = "Custom size",
                                onAction = onCustomSize,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            )
                        }
                    } else {
                        items(state.results, key = { "r-" + it.id }) { SpecRow(it) { pick(it) } }
                    }
                } else {
                    if (state.groups.isEmpty()) {
                        items(6) {
                            SkeletonBlock(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(44.dp))
                        }
                    }
                    if (state.suggestions.isNotEmpty()) {
                        item { GroupLabel("Suggested") }
                        items(state.suggestions, key = { "s-" + it.id }) { SpecRow(it, prominent = true) { pick(it) } }
                    }
                    item {
                        if (state.recents.isEmpty()) {
                            Text(
                                "Pick a document to start",
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.slate,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp),
                            )
                        } else {
                            Column {
                                GroupLabel("Recent photos")
                                LazyRow(
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(state.recents, key = { it.id }) { r ->
                                        RecentThumb(r.originalFile, state.specNames[r.specId] ?: "Not sized yet") { onRecent(r.id) }
                                    }
                                }
                            }
                        }
                    }
                    state.groups.forEach { (country, specs) ->
                        item(key = "g-$country") { GroupLabel(country) }
                        items(specs, key = { it.id }) { SpecRow(it) { pick(it) } }
                    }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onCustomSize)
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, tint = c.slate)
                            Spacer(Modifier.width(12.dp))
                            Text("Custom size", style = MaterialTheme.typography.titleMedium, color = c.ink)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Cropmark.colors.slate,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SpecRow(spec: DocSpec, prominent: Boolean = false, onClick: () -> Unit) {
    val c = Cropmark.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (prominent) 14.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(spec.name, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (prominent) {
                Text(spec.country, style = MaterialTheme.typography.bodyMedium, color = c.slate)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            spec.sizeLabel,
            fontFamily = Condensed,
            fontSize = if (prominent) 22.sp else 18.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = if (prominent) c.ink else c.slate,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun RecentThumb(file: String, label: String, onClick: () -> Unit) {
    val c = Cropmark.colors
    val bmp by rememberFileBitmap(ServiceLocator.photos.fileFor(file), 320)
    Column(Modifier.width(96.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(96.dp, 120.dp).background(c.panel, PaperShape)) {
            bmp?.let {
                Image(it.asImageBitmap(), contentDescription = "Photo for $label", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.slate, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cropmark.colors.magenta,
    unfocusedBorderColor = Cropmark.colors.slate,
    focusedLabelColor = Cropmark.colors.magenta,
    unfocusedLabelColor = Cropmark.colors.slate,
    cursorColor = Cropmark.colors.magenta,
    focusedTextColor = Cropmark.colors.ink,
    unfocusedTextColor = Cropmark.colors.ink,
    focusedLeadingIconColor = Cropmark.colors.slate,
    unfocusedLeadingIconColor = Cropmark.colors.slate,
    errorBorderColor = Cropmark.colors.magenta,
    errorLabelColor = Cropmark.colors.magenta,
    errorSupportingTextColor = Cropmark.colors.magenta,
    focusedContainerColor = Cropmark.colors.backdrop,
    unfocusedContainerColor = Cropmark.colors.backdrop,
)
