package com.azza.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.azza.core.designsystem.R

// The web's type scale (globals.css `--text-*`) at its phone-width end
// (the clamp() minimums), 1rem = 16sp.
//
// Fonts: the web's own — Geist for Latin, Noto Sans Arabic for Persian and
// Kurdish (next/font/google there). Bundled, not downloaded at run time:
// Android's downloadable fonts go through Google Play Services, which many
// phones in Iran do not have. Both are variable fonts (licences in
// docs/licenses); each weight below is an instance of the one file. Android
// cannot fall back from one bundled family to another per glyph, so the
// family follows the app's language: Arabic script for fa/ckb, Geist for
// en — a word in the other script then falls back to the system font.

private val WEIGHTS = listOf(400, 500, 600, 700, 800)

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int) = FontFamily(
    WEIGHTS.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

val GeistFamily: FontFamily = variable(R.font.geist)
val NotoSansArabicFamily: FontFamily = variable(R.font.noto_sans_arabic)

/** The family for the current language, provided by AzzaTheme. */
val LocalAzzaFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

fun fontFamilyFor(language: String): FontFamily = if (language == "fa" || language == "ckb") NotoSansArabicFamily else GeistFamily

private fun style(size: Float, lineHeight: Float, weight: Int, letterSpacingEm: Float = 0f) = TextStyle(
    fontSize = (size * 16).sp,
    lineHeight = (size * 16 * lineHeight).sp,
    fontWeight = FontWeight(weight),
    letterSpacing = letterSpacingEm.em,
)

private object Base {
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

/** The scale, in the current language's family (read inside a composition). */
object AzzaTextStyles {
    private val family: FontFamily @Composable @ReadOnlyComposable get() = LocalAzzaFontFamily.current
    val display: TextStyle @Composable @ReadOnlyComposable get() = Base.display.copy(fontFamily = family)
    val h1: TextStyle @Composable @ReadOnlyComposable get() = Base.h1.copy(fontFamily = family)
    val h2: TextStyle @Composable @ReadOnlyComposable get() = Base.h2.copy(fontFamily = family)
    val h3: TextStyle @Composable @ReadOnlyComposable get() = Base.h3.copy(fontFamily = family)
    val h4: TextStyle @Composable @ReadOnlyComposable get() = Base.h4.copy(fontFamily = family)
    val bodyLg: TextStyle @Composable @ReadOnlyComposable get() = Base.bodyLg.copy(fontFamily = family)
    val body: TextStyle @Composable @ReadOnlyComposable get() = Base.body.copy(fontFamily = family)
    val bodySm: TextStyle @Composable @ReadOnlyComposable get() = Base.bodySm.copy(fontFamily = family)
    val caption: TextStyle @Composable @ReadOnlyComposable get() = Base.caption.copy(fontFamily = family)
    val label: TextStyle @Composable @ReadOnlyComposable get() = Base.label.copy(fontFamily = family)
    val button: TextStyle @Composable @ReadOnlyComposable get() = Base.button.copy(fontFamily = family)
    val productTitle: TextStyle @Composable @ReadOnlyComposable get() = Base.productTitle.copy(fontFamily = family)
    val productPrice: TextStyle @Composable @ReadOnlyComposable get() = Base.productPrice.copy(fontFamily = family)
    val productMeta: TextStyle @Composable @ReadOnlyComposable get() = Base.productMeta.copy(fontFamily = family)
}

internal fun azzaTypography(family: FontFamily) = Typography(
    displayLarge = Base.display.copy(fontFamily = family),
    headlineLarge = Base.h1.copy(fontFamily = family),
    headlineMedium = Base.h2.copy(fontFamily = family),
    headlineSmall = Base.h3.copy(fontFamily = family),
    titleLarge = Base.h3.copy(fontFamily = family),
    titleMedium = Base.h4.copy(fontFamily = family),
    titleSmall = Base.productTitle.copy(fontFamily = family),
    bodyLarge = Base.bodyLg.copy(fontFamily = family),
    bodyMedium = Base.body.copy(fontFamily = family),
    bodySmall = Base.bodySm.copy(fontFamily = family),
    labelLarge = Base.button.copy(fontFamily = family),
    labelMedium = Base.label.copy(fontFamily = family),
    labelSmall = Base.caption.copy(fontFamily = family),
)
