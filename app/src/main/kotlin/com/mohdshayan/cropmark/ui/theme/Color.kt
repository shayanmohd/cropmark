package com.mohdshayan.cropmark.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Colour tokens from BLUEPRINT section 7. Booth grey neutrals and one registration magenta accent.
 * Rule only decorates (dividers, skeletons) and is never used for text. Output photo backgrounds
 * are spec pixel values in core/, not theme tokens.
 */

@Immutable
data class CropmarkColors(
    val backdrop: Color,
    val panel: Color,
    val ink: Color,
    val slate: Color,
    val rule: Color,
    val magenta: Color,
)

// Light mode
val LightBackdrop = Color(0xFFEEF0F3)
val LightPanel = Color(0xFFDFE3E9)
val LightInk = Color(0xFF1A1F27)
val LightSlate = Color(0xFF525C69)
val LightRule = Color(0xFFB7BFCA)
val LightMagenta = Color(0xFFB0276A)

// Dark mode
val DarkBackdrop = Color(0xFF15181D)
val DarkPanel = Color(0xFF20252C)
val DarkInk = Color(0xFFE6E9EE)
val DarkSlate = Color(0xFF9BA5B2)
val DarkRule = Color(0xFF3A424D)
val DarkMagenta = Color(0xFFE5639F)

// The two background tokens the generator wires into colors.xml.
val LightBackground = LightBackdrop
val DarkBackground = DarkBackdrop

val LightTokens = CropmarkColors(LightBackdrop, LightPanel, LightInk, LightSlate, LightRule, LightMagenta)
val DarkTokens = CropmarkColors(DarkBackdrop, DarkPanel, DarkInk, DarkSlate, DarkRule, DarkMagenta)

val LocalCropmarkColors = staticCompositionLocalOf { LightTokens }
