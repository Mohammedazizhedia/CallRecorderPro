
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
import androidx.compose.ui.unit.Density

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B57D0),
    secondary = Color(0xFF006A6A),
    tertiary = Color(0xFF984061)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    secondary = Color(0xFF4CDADB),
    tertiary = Color(0xFFFFB0C9)
)

@Composable
fun CallRecorderTheme(
    fontKey: String = "cairo",
    fontScale: Float = 1f,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    // fontScale scales every sp text in the app without touching dp layout
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * fontScale)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(fontFamilyFor(fontKey)),
            content = content
        )
    }
}
