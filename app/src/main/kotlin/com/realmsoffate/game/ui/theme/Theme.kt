package com.realmsoffate.game.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Brand fallback palette (Material 3 tonal) for devices < S (no dynamic color)
private val Gold = Color(0xFFE2B84A)
private val GoldDark = Color(0xFFB8891A)
private val Purple = Color(0xFFB197FF)
private val Crimson = Color(0xFFFF6B5C)

private val FallbackDark = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1D1B20),
    primaryContainer = Color(0xFF4A3A10),
    onPrimaryContainer = Color(0xFFFFE3A0),
    secondary = Purple,
    onSecondary = Color(0xFF1D1B20),
    secondaryContainer = Color(0xFF3A2C55),
    onSecondaryContainer = Color(0xFFE4D7FF),
    tertiary = Crimson,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3F1712),
    onTertiaryContainer = Color(0xFFFFDAD4),
    background = Color(0xFF121015),
    onBackground = Color(0xFFE8E1F0),
    surface = Color(0xFF1D1B20),
    onSurface = Color(0xFFE8E1F0),
    surfaceVariant = Color(0xFF2A262F),
    onSurfaceVariant = Color(0xFFC1BBD0),
    surfaceTint = Gold,
    error = Crimson,
    onError = Color.White,
    errorContainer = Color(0xFF3F1712),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = Color(0xFF6A6378),
    outlineVariant = Color(0xFF3F3A4A),
    scrim = Color.Black,
    // Material 3 surface hierarchy (fallback when dynamic color unavailable)
    surfaceDim = Color(0xFF121015),
    surfaceBright = Color(0xFF3C383F),
    surfaceContainerLowest = Color(0xFF0F0E12),
    surfaceContainerLow = Color(0xFF1A181E),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343A)
)

private val FallbackLight = lightColorScheme(
    primary = GoldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3A0),
    onPrimaryContainer = Color(0xFF2A1E08),
    secondary = Color(0xFF6750A4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF21005D),
    tertiary = Color(0xFFC72B1E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD4),
    onTertiaryContainer = Color(0xFF410002),
    background = Color(0xFFFDFAF2),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFF6F1E3),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE8E1CF),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceTint = GoldDark,
    error = Color(0xFFC72B1E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color.Black,
    surfaceDim = Color(0xFFDCD6C8),
    surfaceBright = Color(0xFFF9F6ED),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F3E9),
    surfaceContainer = Color(0xFFF1EDE3),
    surfaceContainerHigh = Color(0xFFEBE7DD),
    surfaceContainerHighest = Color(0xFFE5E1D7)
)

@Composable
fun RealmsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val debugTheme by com.realmsoffate.game.debug.DebugHook.themeOverride.collectAsState()
    val effectiveDarkTheme = debugTheme ?: darkTheme

    val context = LocalContext.current
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        effectiveDarkTheme -> FallbackDark
        else -> FallbackLight
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !effectiveDarkTheme
                isAppearanceLightNavigationBars = !effectiveDarkTheme
            }
        }
    }

    CompositionLocalProvider(LocalRealmsColors provides extendedColorsFor(effectiveDarkTheme)) {
        MaterialTheme(
            colorScheme = colors,
            typography = RealmsTypography,
            shapes = RealmsShapes,
            content = content
        )
    }
}
