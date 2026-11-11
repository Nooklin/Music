package org.nooklin.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.nooklin.music.R
import org.nooklin.music.data.local.AppFont

// Variable font used as one Font() entry, license in THIRD-PARTY-LICENSES/work-sans/OFL.txt
private val WorkSansFontFamily = FontFamily(Font(R.font.work_sans))

// Inter is a variable font with a weight and an optical size axis, license in THIRD-PARTY-LICENSES/inter/OFL.txt.
// One entry per weight, each fixed to its weight on the axis, so bold text is really bold.
@OptIn(ExperimentalTextApi::class)
private val InterFontFamily = FontFamily(
    listOf(
        FontWeight.Normal to 400,
        FontWeight.Medium to 500,
        FontWeight.SemiBold to 600,
        FontWeight.Bold to 700
    ).map { (weight, value) ->
        Font(R.font.inter, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(value)))
    }
)

// SYSTEM_DEFAULT is FontFamily.Default, not hardcoded Roboto: some OEM skins substitute their own
fun fontFamilyFor(font: AppFont): FontFamily = when (font) {
    AppFont.SYSTEM_DEFAULT -> FontFamily.Default
    AppFont.WORK_SANS -> WorkSansFontFamily
    AppFont.INTER -> InterFontFamily
}

fun typographyFor(font: AppFont): Typography {
    val family = fontFamilyFor(font)
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family)
    )
}
