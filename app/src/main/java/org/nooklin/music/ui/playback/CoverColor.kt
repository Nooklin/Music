package org.nooklin.music.ui.playback

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A background color taken from the cover, dark enough for white text. Null when the cover gives no usable color.
 * Palette runs on the device only: no network and no Google services.
 */
suspend fun coverBackground(bitmap: Bitmap): Color? = withContext(Dispatchers.Default) {
    val palette = Palette.from(bitmap).maximumColorCount(16).generate()
    val swatch = palette.darkMutedSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.vibrantSwatch
        ?: palette.dominantSwatch
        ?: return@withContext null
    var color = Color(swatch.rgb)
    // Contrast with white must be at least 4.5 for readable text, darken step by step until it is
    var steps = 0
    while (1.05f / (color.luminance() + 0.05f) < 4.5f && steps < 10) {
        color = lerp(color, Color.Black, 0.15f)
        steps++
    }
    color
}
