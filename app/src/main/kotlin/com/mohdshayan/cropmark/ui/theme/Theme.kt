package com.mohdshayan.cropmark.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun scheme(t: CropmarkColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = t.ink,
        onPrimary = t.backdrop,
        primaryContainer = t.panel,
        onPrimaryContainer = t.ink,
        inversePrimary = t.magenta,
        secondary = t.magenta,
        onSecondary = t.backdrop,
        secondaryContainer = t.panel,
        onSecondaryContainer = t.ink,
        tertiary = t.magenta,
        onTertiary = t.backdrop,
        tertiaryContainer = t.panel,
        onTertiaryContainer = t.ink,
        background = t.backdrop,
        onBackground = t.ink,
        surface = t.backdrop,
        onSurface = t.ink,
        surfaceVariant = t.panel,
        onSurfaceVariant = t.slate,
        surfaceTint = t.backdrop,
        inverseSurface = t.ink,
        inverseOnSurface = t.backdrop,
        error = t.magenta,
        onError = t.backdrop,
        errorContainer = t.panel,
        onErrorContainer = t.ink,
        outline = t.slate,
        outlineVariant = t.rule,
        scrim = t.ink,
        surfaceBright = t.backdrop,
        surfaceDim = t.panel,
        surfaceContainerLowest = t.backdrop,
        surfaceContainerLow = t.backdrop,
        surfaceContainer = t.panel,
        surfaceContainerHigh = t.panel,
        surfaceContainerHighest = t.panel,
    )
}

private val LightColors = scheme(LightTokens, dark = false)
private val DarkColors = scheme(DarkTokens, dark = true)

/**
 * The app theme. Dynamic colour is off: registration magenta is the product's identity and must
 * look the same on every device. System bar icons follow the mode, and LocalReducedMotion is
 * provided for every animation to consult.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalReducedMotion provides rememberReducedMotion(),
        LocalCropmarkColors provides if (darkTheme) DarkTokens else LightTokens,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object Cropmark {
    val colors: CropmarkColors
        @Composable @ReadOnlyComposable get() = LocalCropmarkColors.current
}
