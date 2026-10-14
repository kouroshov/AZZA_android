package com.azza.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The web's design tokens (AZZA/src/app/globals.css, `:root` = dark and
 * `:root[data-theme="light"]`), value for value. When a token changes
 * there, change it here — the two apps are meant to look like one product.
 *
 * The web paints surfaces as a low-alpha wash of `--color-surface` over the
 * background (`bg-surface/6`, `bg-surface/10`); the alphas below are those.
 */
@Immutable
data class AzzaColors(
    val bg: Color,
    val surfaceTint: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnPrimary: Color,
    val primary: Color,
    val primaryText: Color,
    val secondary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val link: Color,
    val disabled: Color,
    val navInk: Color,
    val navPopover: Color,
    val navDivider: Color,
    val isDark: Boolean,
) {
    /** `bg-surface/6` — cards. */
    val surface: Color get() = surfaceTint.copy(alpha = 0.06f)
    /** `bg-surface/10` — raised panels, inputs. */
    val surfaceRaised: Color get() = surfaceTint.copy(alpha = 0.10f)
    /** `border-border/10`. */
    val border: Color get() = surfaceTint.copy(alpha = 0.10f)
}

val DarkAzzaColors = AzzaColors(
    bg = Color(0xFF181A1B),
    surfaceTint = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFFF3F4F6).copy(alpha = 0.70f),
    textMuted = Color(0xFFF3F4F6).copy(alpha = 0.45f),
    textOnPrimary = Color(0xFF04141F),
    primary = Color(0xFF0EA5E9),
    primaryText = Color(0xFF0EA5E9),
    secondary = Color(0xFF1E293B),
    success = Color(0xFF10B981),
    warning = Color(0xFFF59E0B),
    error = Color(0xFFF43F5E),
    info = Color(0xFF22D3EE),
    link = Color(0xFF38BDF8),
    disabled = Color(0xFFF3F4F6).copy(alpha = 0.32f),
    navInk = Color(0xFFF5F5F5),
    navPopover = Color(0xFF232627),
    navDivider = Color(0xFFFFFFFF).copy(alpha = 0.10f),
    isDark = true,
)

val LightAzzaColors = AzzaColors(
    bg = Color(0xFFFFFFFF),
    surfaceTint = Color(0xFF0A0D12),
    textPrimary = Color(0xFF0A0D12),
    textSecondary = Color(0xFF0A0D12).copy(alpha = 0.70f),
    textMuted = Color(0xFF0A0D12).copy(alpha = 0.60f),
    textOnPrimary = Color(0xFF04141F),
    primary = Color(0xFF0EA5E9),
    primaryText = Color(0xFF075985),
    secondary = Color(0xFFE5E7EB),
    success = Color(0xFF065F46),
    warning = Color(0xFF92400E),
    error = Color(0xFF9F1239),
    info = Color(0xFF155E75),
    link = Color(0xFF075985),
    disabled = Color(0xFF0A0D12).copy(alpha = 0.32f),
    navInk = Color(0xFF000000),
    navPopover = Color(0xFFFFFFFF),
    navDivider = Color(0xFF000000).copy(alpha = 0.08f),
    isDark = false,
)

val LocalAzzaColors = staticCompositionLocalOf { DarkAzzaColors }
