package com.fadcam.ui.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val JustCamDark = darkColorScheme(
    primary = Color(0xFF6DD5C6),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF8CF2E2),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCDE8E2),
    tertiary = Color(0xFFADC9E4),
    onTertiary = Color(0xFF133349),
    tertiaryContainer = Color(0xFF2C4A61),
    onTertiaryContainer = Color(0xFFCEE5FF),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF161D1B),
    surfaceContainer = Color(0xFF1A211F),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF2F3635),
)

private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0F0E),
    surfaceContainer = Color(0xFF101514),
    surfaceContainerHigh = Color(0xFF1A201F),
    surfaceContainerHighest = Color(0xFF242A29),
)

/** JustCam Material 3 theme: Material You on Android 12+, teal fallback, optional true black. */
@Composable
fun JustCamTheme(amoled: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var scheme = if (!amoled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        JustCamDark
    }
    if (amoled) scheme = scheme.amoled()
    MaterialTheme(colorScheme = scheme, content = content)
}
