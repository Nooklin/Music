package org.nooklin.music.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.nooklin.music.MainActivity
import org.nooklin.music.R
import org.nooklin.music.playback.PlaybackService

/** What the widget shows. The player service sets it through [NowPlayingWidget.publish]. */
private object WidgetState {
    var title = ""
    var artist = ""
    var playing = false
    var cover: Bitmap? = null
}

private val SMALL = DpSize(110.dp, 70.dp)
private val MEDIUM = DpSize(250.dp, 70.dp)
private val LARGE = DpSize(250.dp, 180.dp)

/**
 * The home screen widget. Small: cover and play. Wide: cover, title, artist and three buttons. Tall: a large cover
 * with the title and the buttons below it. The buttons send media keys to the player service.
 */
class NowPlayingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content(context) }
    }

    companion object {
        /** Shows the state of the player on every widget that is on the home screen. */
        suspend fun publish(context: Context, title: String, artist: String, playing: Boolean, cover: Bitmap?) {
            WidgetState.title = title
            WidgetState.artist = artist
            WidgetState.playing = playing
            WidgetState.cover = cover
            NowPlayingWidget().updateAll(context)
        }
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

private val WHITE = ColorProvider(Color.White)
private val SOFT = ColorProvider(Color(0xB3FFFFFF))

@Composable
private fun Content(context: Context) {
    val size = LocalSize.current
    val open = actionStartActivity(ComponentName(context, MainActivity::class.java))
    val title = WidgetState.title.ifEmpty { "Nothing playing" }
    val tall = size.height >= LARGE.height
    val wide = size.width >= MEDIUM.width

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xE61C1C1E)))
            .cornerRadius(24.dp)
            .padding(10.dp)
            .clickable(open),
        contentAlignment = Alignment.Center
    ) {
        if (tall) {
            Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(96.dp)
                Spacer(GlanceModifier.size(8.dp))
                Text(title, maxLines = 1, style = TextStyle(color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Text(WidgetState.artist, maxLines = 1, style = TextStyle(color = SOFT, fontSize = 12.sp))
                Spacer(GlanceModifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Controls(context, all = true)
                }
            }
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Cover(52.dp)
                if (wide) {
                    Column(modifier = GlanceModifier.defaultWeight().padding(horizontal = 10.dp)) {
                        Text(title, maxLines = 1, style = TextStyle(color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        Text(WidgetState.artist, maxLines = 1, style = TextStyle(color = SOFT, fontSize = 12.sp))
                    }
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
                Controls(context, all = wide)
            }
        }
    }
}

@Composable
private fun Cover(size: androidx.compose.ui.unit.Dp) {
    val bitmap = WidgetState.cover
    Image(
        provider = if (bitmap != null) ImageProvider(bitmap) else ImageProvider(R.mipmap.ic_launcher),
        contentDescription = null,
        modifier = GlanceModifier.size(size).cornerRadius(10.dp)
    )
}

/** Previous, play or pause and next; with [all] false only play or pause. */
@Composable
private fun Controls(context: Context, all: Boolean) {
    if (all) {
        ControlButton(context, android.R.drawable.ic_media_previous, "Previous", KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }
    ControlButton(
        context,
        if (WidgetState.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        "Play or pause",
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    )
    if (all) {
        ControlButton(context, android.R.drawable.ic_media_next, "Next", KeyEvent.KEYCODE_MEDIA_NEXT)
    }
}

@Composable
private fun ControlButton(context: Context, icon: Int, description: String, keyCode: Int) {
    // The address differs per key, so the system does not treat the three buttons as the same intent
    val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
        .setClass(context, PlaybackService::class.java)
        .setData(Uri.parse("nooklin-widget://key/$keyCode"))
        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        modifier = GlanceModifier.size(40.dp).clickable(actionStartService(intent, isForegroundService = true))
    )
}
