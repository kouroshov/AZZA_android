package com.azza.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// The web's type scale (globals.css `--text-*`) at its phone-width end
// (the clamp() minimums), 1rem = 16sp. Font family: the system default for
// now; the web uses Geist + Noto Sans Arabic — see docs/ROADMAP.md.

private fun style(size: Float, lineHeight: Float, weight: Int, letterSpacingEm: Float = 0f) = TextStyle(
    fontSize = (size * 16).sp,
    lineHeight = (size * 16 * lineHeight).sp,
    fontWeight = FontWeight(weight),
    letterSpacing = letterSpacingEm.em,
)

object AzzaTextStyles {
    val display = style(1.875f, 1.1f, 800, -0.02f)
    val h1 = style(1.5f, 1.18f, 800, -0.015f)
    val h2 = style(1.25f, 1.2f, 700, -0.01f)
    val h3 = style(1.125f, 1.25f, 700)
    val h4 = style(1.0625f, 1.3f, 600)
    val bodyLg = style(1.0625f, 1.6f, 400)
    val body = style(0.9375f, 1.6f, 400)
    val bodySm = style(0.8125f, 1.5f, 400)
    val caption = style(0.75f, 1.4f, 500)
    val label = style(0.75f, 1.3f, 600, 0.04f)
    val button = style(0.875f, 1.2f, 600)
    val productTitle = style(0.9375f, 1.35f, 700)
    val productPrice = style(1.125f, 1.2f, 800)
    val productMeta = style(0.75f, 1.4f, 500)
}

internal val AzzaTypography = Typography(
    displayLarge = AzzaTextStyles.display,
    headlineLarge = AzzaTextStyles.h1,
    headlineMedium = AzzaTextStyles.h2,
    headlineSmall = AzzaTextStyles.h3,
    titleLarge = AzzaTextStyles.h3,
    titleMedium = AzzaTextStyles.h4,
    titleSmall = AzzaTextStyles.productTitle,
    bodyLarge = AzzaTextStyles.bodyLg,
    bodyMedium = AzzaTextStyles.body,
    bodySmall = AzzaTextStyles.bodySm,
    labelLarge = AzzaTextStyles.button,
    labelMedium = AzzaTextStyles.label,
    labelSmall = AzzaTextStyles.caption,
)
