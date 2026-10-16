package com.azza.core.designsystem.theme

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * Dark by default, like the web (`:root` is dark; light is the visitor's own
 * choice there, kept in a cookie). The in-app theme switch — dark / light /
 * system — arrives with the settings menu (docs/ROADMAP.md); pass
 * `isSystemInDarkTheme()` for "system".
 */
@Composable
fun AzzaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkAzzaColors else LightAzzaColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.textOnPrimary,
            secondary = colors.secondary,
            onSecondary = colors.textPrimary,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.bg,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.navPopover,
            outline = colors.border,
            outlineVariant = colors.navDivider,
            error = colors.error,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.textOnPrimary,
            secondary = colors.secondary,
            onSecondary = colors.textPrimary,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.bg,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.navPopover,
            outline = colors.border,
            outlineVariant = colors.navDivider,
            error = colors.error,
        )
    }
    // The app's language (per-app locale) decides the font family.
    val family = fontFamilyFor(LocalConfiguration.current.locales[0].language)
    val typography = remember(family) { azzaTypography(family) }
    CompositionLocalProvider(LocalAzzaColors provides colors, LocalAzzaFontFamily provides family) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = AzzaShapes,
            content = content,
        )
    }
}

private val AzzaShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/** `AzzaTheme.colors.textMuted` etc. — the tokens Material's scheme has no slot for. */
object AzzaTheme {
    val colors: AzzaColors
        @Composable @ReadOnlyComposable get() = LocalAzzaColors.current
}
