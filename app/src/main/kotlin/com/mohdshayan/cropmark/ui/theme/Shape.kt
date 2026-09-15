package com.mohdshayan.cropmark.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/*
 * The whole radius scale, and the rule for using it:
 *   PaperShape (0)  anything standing for paper: the photo, a print sheet, the size diagram
 *   RadiusSm 6dp    chips and colour swatches
 *   RadiusMd 12dp   buttons and fields
 *   RadiusLg 20dp   bottom sheet tops and dialogs
 * Nothing in the app uses a corner radius that is not one of these.
 */
val RadiusSm = 6.dp
val RadiusMd = 12.dp
val RadiusLg = 20.dp

val PaperShape = RectangleShape

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(RadiusSm),
    small = RoundedCornerShape(RadiusSm),
    medium = RoundedCornerShape(RadiusMd),
    large = RoundedCornerShape(RadiusLg),
    extraLarge = RoundedCornerShape(RadiusLg),
)
