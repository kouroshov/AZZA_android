package com.azza.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/** The web's default is dark (`:root`); light is opt-in there and follows the system here. */
@Composable
fun AzzaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
    CompositionLocalProvider(LocalAzzaColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AzzaTypography,
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
