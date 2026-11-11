@file:OptIn(UnstableApi::class)

package org.nooklin.music.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import org.nooklin.music.data.library.ReplayGain
import org.nooklin.music.data.playback.QueueEntry

private const val EXTRA_COVER = "cover"
private const val EXTRA_SUFFIX = "suffix"
const val EXTRA_ALBUM_ID = "album_id"
const val EXTRA_ARTIST_ID = "artist_id"
const val EXTRA_BITRATE = "bitrate"
const val EXTRA_BPM = "bpm"
// A song the user put in the queue by hand: the DJ order leaves it where it is and works around it
const val EXTRA_PINNED = "pinned"
const val RG_TRACK = "rg_track"
const val RG_ALBUM = "rg_album"
const val RG_TRACK_PEAK = "rg_track_peak"
const val RG_ALBUM_PEAK = "rg_album_peak"
const val RG_FALLBACK = "rg_fallback"

// Session extras: the positions in the queue of the songs that follow, in the order the player really plays them (the
// screen cannot see the shuffle order of the player), and the position and length of the queue they belong to
const val EXTRA_UPCOMING = "upcoming"
const val EXTRA_UPCOMING_FOR = "upcoming_for"

/**
 * The player never sees a real web address. A song is identified by `nooklin://song/ID`, and the address with the
 * login token is made at the moment the data is opened (see PlaybackService).
 */
fun QueueEntry.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString(EXTRA_COVER, coverArt ?: "")
        putString(EXTRA_SUFFIX, suffix)
        putString(EXTRA_ALBUM_ID, albumId ?: "")
        putString(EXTRA_ARTIST_ID, artistId ?: "")
        putInt(EXTRA_BITRATE, bitRate)
        putInt(EXTRA_BPM, bpm)
        replayGain?.let { g ->
            g.trackGain?.let { putDouble(RG_TRACK, it) }
            g.albumGain?.let { putDouble(RG_ALBUM, it) }
            g.trackPeak?.let { putDouble(RG_TRACK_PEAK, it) }
            g.albumPeak?.let { putDouble(RG_ALBUM_PEAK, it) }
            g.fallbackGain?.let { putDouble(RG_FALLBACK, it) }
        }
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setExtras(extras)
        .apply {
            if (durationMs > 0) setDurationMs(durationMs)
            if (coverArt != null) setArtworkUri(Uri.parse("nooklin://cover/$coverArt"))
        }
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri("nooklin://song/$id")
        .setMediaMetadata(metadata)
        .build()
}

fun MediaItem.toQueueEntry(): QueueEntry {
    val extras = mediaMetadata.extras
    return QueueEntry(
        id = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString().orEmpty(),
        coverArt = extras?.getString(EXTRA_COVER)?.ifEmpty { null },
        suffix = extras?.getString(EXTRA_SUFFIX).orEmpty(),
        durationMs = mediaMetadata.durationMs ?: 0L,
        albumId = extras?.getString(EXTRA_ALBUM_ID)?.ifEmpty { null },
        artistId = extras?.getString(EXTRA_ARTIST_ID)?.ifEmpty { null },
        bitRate = extras?.getInt(EXTRA_BITRATE) ?: 0,
        bpm = extras?.getInt(EXTRA_BPM) ?: 0,
        replayGain = if (extras != null && extras.keySet().any { it.startsWith("rg_") }) {
            fun get(key: String): Double? = if (extras.containsKey(key)) extras.getDouble(key) else null
            ReplayGain(get(RG_TRACK), get(RG_ALBUM), get(RG_TRACK_PEAK), get(RG_ALBUM_PEAK), get(RG_FALLBACK))
        } else {
            null
        }
    )
}
