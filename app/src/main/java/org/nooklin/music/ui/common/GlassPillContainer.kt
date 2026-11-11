package org.nooklin.music.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nooklin.music.ui.theme.LocalContrastColorState

// Shared glass pill styling, fixed and adaptive backgrounds use different opacity
object GlassPill {
    val shape: Shape = RoundedCornerShape(24.dp)

    val fixedGlassBackground: Color = Color.White.copy(alpha = 0.1f)

    val adaptiveGlassBackground: Color
        @Composable get() {
            val contrast = LocalContrastColorState.current
            if (contrast.isBlackMode) {
                // Dark grey: true black would hide the edge of a subtle card
                return Color(0xFF1C1C1E).copy(alpha = 0.97f)
            }
            return if (contrast.color != null) {
                // Tinted glass in both themes, low alpha because it sits over content
                contrast.color.copy(alpha = 0.18f)
            } else {
                // DEFAULT: no tint
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        }

    val adaptivePopupBackground: Color
        @Composable get() {
            val contrast = LocalContrastColorState.current
            if (contrast.isBlackMode) {
                // Dark grey, near opaque for readable rows and dialogs
                return Color(0xFF1C1C1E).copy(alpha = 0.97f)
            }
            return if (contrast.color != null) {
                // Popups stay near opaque: blend the color into the surface, a low-alpha tint looks washed out
                lerp(MaterialTheme.colorScheme.surface, contrast.color, 0.12f).copy(alpha = 0.97f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
            }
        }

    // For pills that float over scrolling content: the popup color at 97 percent, so the text on it stays readable
    val adaptiveFloatingBackground: Color
        @Composable get() = adaptivePopupBackground.copy(alpha = 0.97f)

    val adaptiveBorder: BorderStroke
        @Composable get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}

@Composable
fun GlassPillContainer(
    modifier: Modifier = Modifier,
    backgroundColor: Color = GlassPill.fixedGlassBackground,
    border: BorderStroke? = null,
    // Editor mode bar uses 10dp, the viewer toolbar passes 0dp so its 48dp buttons set the height
    verticalContentPadding: Dp = 10.dp,
    // 0.0.140: the bottom bar passes 0 dp so its selection sits the same 4 dp from every edge
    horizontalContentPadding: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(GlassPill.shape)
            .background(backgroundColor)
            .then(if (border != null) Modifier.border(border, GlassPill.shape) else Modifier)
            .padding(horizontal = horizontalContentPadding, vertical = verticalContentPadding)
    ) {
        content()
    }
}
