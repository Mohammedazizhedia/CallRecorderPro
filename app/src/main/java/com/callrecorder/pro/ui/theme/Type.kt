
package com.callrecorder.pro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.callrecorder.pro.R

/**
 * Bundled fonts: Cairo (Latin+Arabic), IBM Plex Sans Arabic (best Arabic coverage),
 * Basseet and The Year of the Camel (Arabic display), plus system default.
 */
val CairoFamily = FontFamily(
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_bold, FontWeight.Bold),
    Font(R.font.plex_arabic, FontWeight.Normal),
    Font(R.font.plex_arabic, FontWeight.Bold)
)

fun fontFamilyFor(key: String): FontFamily = when (key) {
    "ibm" -> FontFamily(
        Font(R.font.plex_arabic, FontWeight.Normal),
        Font(R.font.plex_arabic, FontWeight.Bold)
    )
    "basseet" -> FontFamily(Font(R.font.basseet))
    "camel" -> FontFamily(Font(R.font.camel))
    "system" -> FontFamily.Default
    else -> CairoFamily
}

fun appTypography(family: FontFamily, bold: Boolean = false): Typography {
    val wNormal = if (bold) FontWeight.Bold else FontWeight.Normal
    val wSemi = if (bold) FontWeight.Bold else FontWeight.SemiBold
    val wBold = FontWeight.Bold
    return Typography(
    displaySmall = TextStyle(fontFamily = family, fontWeight = wBold, fontSize = 36.sp),
    headlineMedium = TextStyle(fontFamily = family, fontWeight = wBold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = family, fontWeight = wBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = family, fontWeight = wBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = family, fontWeight = wSemi, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = wNormal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = family, fontWeight = wNormal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = family, fontWeight = wSemi, fontSize = 14.sp)
    )
}

val CairoTypography = appTypography(CairoFamily)
