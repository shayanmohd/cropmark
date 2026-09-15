package com.mohdshayan.cropmark.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mohdshayan.cropmark.R

/*
 * Sofia Sans Condensed SemiBold for measurements and titles, Public Sans for everything read.
 * Both are static instances cut from the Google Fonts variable masters, SIL OFL 1.1, licences in docs/.
 */
val Condensed = FontFamily(
    Font(R.font.sofia_sans_condensed_medium, FontWeight.Medium),
    Font(R.font.sofia_sans_condensed_semibold, FontWeight.SemiBold),
)

val PublicSans = FontFamily(
    Font(R.font.public_sans_regular, FontWeight.Normal),
    Font(R.font.public_sans_medium, FontWeight.Medium),
    Font(R.font.public_sans_semibold, FontWeight.SemiBold),
)

/** Tabular figures so measurements do not jitter as they change. */
private const val TNUM = "tnum"

/** "35 x 45 mm" on the spec sheet and export readouts. */
val MeasureStyle = TextStyle(
    fontFamily = Condensed,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 32.sp,
    fontFeatureSettings = TNUM,
)

/** Ruler numerals and small measurement labels. */
val RulerStyle = TextStyle(
    fontFamily = Condensed,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 14.sp,
    fontFeatureSettings = TNUM,
)

val AppTypography = Typography(
    displaySmall = MeasureStyle,
    headlineLarge = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        fontFeatureSettings = TNUM,
    ),
    headlineMedium = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontFeatureSettings = TNUM,
    ),
    headlineSmall = MeasureStyle,
    titleLarge = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontFeatureSettings = TNUM,
    ),
    titleMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = TNUM,
    ),
)
