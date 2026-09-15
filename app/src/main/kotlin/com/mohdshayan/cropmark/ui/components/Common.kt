package com.mohdshayan.cropmark.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohdshayan.cropmark.ui.theme.Cropmark
import com.mohdshayan.cropmark.ui.theme.LocalReducedMotion
import com.mohdshayan.cropmark.ui.theme.RadiusMd
import com.mohdshayan.cropmark.ui.theme.RadiusSm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropmarkTopBar(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Cropmark.colors.backdrop,
            scrolledContainerColor = Cropmark.colors.backdrop,
            titleContentColor = Cropmark.colors.ink,
            navigationIconContentColor = Cropmark.colors.ink,
            actionIconContentColor = Cropmark.colors.slate,
        ),
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(RadiusMd),
        colors = ButtonDefaults.buttonColors(
            containerColor = Cropmark.colors.ink,
            contentColor = Cropmark.colors.backdrop,
            disabledContainerColor = Cropmark.colors.rule,
            disabledContentColor = Cropmark.colors.slate,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(RadiusMd),
        border = BorderStroke(1.dp, Cropmark.colors.slate),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cropmark.colors.ink),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** A selectable chip: Slate outline, Magenta ring and Ink label when selected. */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cropmark.colors
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.magenta else c.slate, RoundedCornerShape(RadiusSm))
            .background(if (selected) c.panel else c.backdrop, RoundedCornerShape(RadiusSm))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink, maxLines = 1)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Cropmark.colors.ink,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Cropmark.colors.rule)
}

/** A still block in the shape of content that is on its way. No shimmer. */
@Composable
fun SkeletonBlock(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(RadiusSm)) {
    Box(modifier.background(Cropmark.colors.rule.copy(alpha = 0.55f), shape))
}

/** A row the whole width of which is the touch target. */
@Composable
fun ClickRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    art: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) {
            art()
            Spacer(Modifier.height(24.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Cropmark.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = Cropmark.colors.slate,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

/** An inline error in the app's voice: what happened, then what to do. */
@Composable
fun ErrorPanel(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    val c = Cropmark.colors
    Column(
        modifier = modifier
            .border(1.dp, c.magenta, RoundedCornerShape(RadiusMd))
            .background(c.panel, RoundedCornerShape(RadiusMd))
            .padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.slate)
        }
        if (primary != null || secondary != null) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                primary?.let { PrimaryButton(it.first, it.second) }
                secondary?.let { SecondaryButton(it.first, it.second) }
            }
        }
    }
}

/** Fades content in when it arrives, or shows it at once under reduced motion. */
@Composable
fun FadeIn(visible: Boolean, modifier: Modifier = Modifier, durationMs: Int = 150, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val a by animateFloatAsState(if (visible) 1f else 0f, if (reduced) snap() else tween(durationMs), label = "fade")
    Box(modifier.alpha(a)) { content() }
}
