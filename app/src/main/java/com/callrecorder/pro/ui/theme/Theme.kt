
package com.callrecorder.pro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.callrecorder.pro.R

private val BlueLight = lightColorScheme(primary = Color(0xFF0B57D0), secondary = Color(0xFF006A6A), tertiary = Color(0xFF984061))
private val BlueDark = darkColorScheme(primary = Color(0xFFA8C7FA), secondary = Color(0xFF4CDADB), tertiary = Color(0xFFFFB0C9))
private val GreenLight = lightColorScheme(primary = Color(0xFF146C2E), secondary = Color(0xFF526600), tertiary = Color(0xFF984061))
private val GreenDark = darkColorScheme(primary = Color(0xFF8FD88A), secondary = Color(0xFFC0CE7E), tertiary = Color(0xFFFFB0C9))
private val PurpleLight = lightColorScheme(primary = Color(0xFF6750A4), secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260))
private val PurpleDark = darkColorScheme(primary = Color(0xFFD0BCFF), secondary = Color(0xFFCCC2DC), tertiary = Color(0xFFEFB8C8))
private val Amoled = darkColorScheme(
    primary = Color(0xFF8AB4F8), secondary = Color(0xFF81C995), tertiary = Color(0xFFF28B82),
    background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF161616)
)

@Composable
fun CallRecorderTheme(
    fontKey: String = "cairo",
    fontScale: Float = 1f,
    bold: Boolean = false,
    themeKey: String = "dynamic",
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeKey) {
        "blue" -> if (darkTheme) BlueDark else BlueLight
        "green" -> if (darkTheme) GreenDark else GreenLight
        "purple" -> if (darkTheme) PurpleDark else PurpleLight
        "amoled" -> Amoled
        else -> when {
            dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> BlueDark
            else -> BlueLight
        }
    }
    // Real bold: bundled Cairo has no bold cut (cairo_bold is a copy of regular),
    // so swap to IBM Plex Arabic which IS a true bold font file.
    val family = if (bold && fontKey != "system") {
        FontFamily(
            Font(R.font.plex_arabic, FontWeight.Normal),
            Font(R.font.plex_arabic, FontWeight.Bold)
        )
    } else fontFamilyFor(fontKey)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * fontScale)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(family, bold),
            content = content
        )
    }
}
