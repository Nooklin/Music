package org.nooklin.music.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import org.nooklin.music.R
import org.nooklin.music.data.library.Playlist
import org.nooklin.music.playback.taste.MixId
import org.nooklin.music.playback.taste.mixOf

// Neighbours in the rows on Home never share a picture
// New Release has none: it shows the cover of its song
private fun pictureOf(id: MixId): Int? = when (id) {
    MixId.FAVOURITES -> R.drawable.mix_heart
    MixId.FEATURING -> R.drawable.mix_artist
    MixId.NEW_RELEASE -> null
    MixId.HEAVY -> R.drawable.mix_red
    MixId.ESSENTIALS -> R.drawable.mix_pink
    MixId.GET_UP -> R.drawable.mix_orange
    MixId.CHILL -> R.drawable.mix_green
    MixId.NEW_MUSIC -> R.drawable.mix_purple
}

/** The picture of a playlist that is made for you, and the cover from the server for New Release and any other playlist. */
@Composable
fun PlaylistCover(playlist: Playlist, modifier: Modifier, corner: Dp) {
    val picture = mixOf(playlist)?.let { pictureOf(it) }
    if (picture == null) {
        CoverImage(playlist.coverArt, 400, modifier, corner)
    } else {
        Image(
            painterResource(picture),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(corner))
        )
    }
}
