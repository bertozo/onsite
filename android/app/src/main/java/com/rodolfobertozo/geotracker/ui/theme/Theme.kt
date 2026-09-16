package com.rodolfobertozo.geotracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val md_theme_light_primary = Color(0xFF0061A4)
private val md_theme_light_onPrimary = Color(0xFFFFFFFF)
private val md_theme_light_primaryContainer = Color(0xFFD1E4FF)
private val md_theme_light_onPrimaryContainer = Color(0xFF001D36)
private val md_theme_light_secondary = Color(0xFF535F70)
private val md_theme_light_onSecondary = Color(0xFFFFFFFF)
private val md_theme_light_secondaryContainer = Color(0xFFD7E3F7)
private val md_theme_light_onSecondaryContainer = Color(0xFF101C2B)
private val md_theme_light_tertiary = Color(0xFF6B5778)
private val md_theme_light_onTertiary = Color(0xFFFFFFFF)
private val md_theme_light_tertiaryContainer = Color(0xFFF2DAFF)
private val md_theme_light_onTertiaryContainer = Color(0xFF251431)
private val md_theme_light_error = Color(0xFFBA1A1A)
private val md_theme_light_onError = Color(0xFFFFFFFF)
private val md_theme_light_errorContainer = Color(0xFFFFDAD6)
private val md_theme_light_onErrorContainer = Color(0xFF410002)
private val md_theme_light_background = Color(0xFFFAFBFF)
private val md_theme_light_onBackground = Color(0xFF1A1C1E)
private val md_theme_light_surface = Color(0xFFFAFBFF)
private val md_theme_light_onSurface = Color(0xFF1A1C1E)
private val md_theme_light_surfaceVariant = Color(0xFFDFE2EB)
private val md_theme_light_onSurfaceVariant = Color(0xFF43474E)
private val md_theme_light_outline = Color(0xFF73777F)
private val md_theme_light_outlineVariant = Color(0xFFC3C7CF)

private val md_theme_dark_primary = Color(0xFF9ECAFF)
private val md_theme_dark_onPrimary = Color(0xFF003258)
private val md_theme_dark_primaryContainer = Color(0xFF00497D)
private val md_theme_dark_onPrimaryContainer = Color(0xFFD1E4FF)
private val md_theme_dark_secondary = Color(0xFFBBC7DB)
private val md_theme_dark_onSecondary = Color(0xFF253140)
private val md_theme_dark_secondaryContainer = Color(0xFF3B4858)
private val md_theme_dark_onSecondaryContainer = Color(0xFFD7E3F7)
private val md_theme_dark_tertiary = Color(0xFFD6BEE4)
private val md_theme_dark_onTertiary = Color(0xFF3B2948)
private val md_theme_dark_tertiaryContainer = Color(0xFF523F5F)
private val md_theme_dark_onTertiaryContainer = Color(0xFFF2DAFF)
private val md_theme_dark_error = Color(0xFFFFB4AB)
private val md_theme_dark_onError = Color(0xFF690005)
private val md_theme_dark_errorContainer = Color(0xFF93000A)
private val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
private val md_theme_dark_background = Color(0xFF1A1C1E)
private val md_theme_dark_onBackground = Color(0xFFE2E2E6)
private val md_theme_dark_surface = Color(0xFF1A1C1E)
private val md_theme_dark_onSurface = Color(0xFFE2E2E6)
private val md_theme_dark_surfaceVariant = Color(0xFF43474E)
private val md_theme_dark_onSurfaceVariant = Color(0xFFC3C7CF)
private val md_theme_dark_outline = Color(0xFF8D9199)
private val md_theme_dark_outlineVariant = Color(0xFF43474E)

private val LightColors = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant
)

private val DarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun GeoTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? Activity
        androidx.compose.runtime.SideEffect {
            activity?.window?.let { window ->
                window.statusBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        content = content
    )
}
