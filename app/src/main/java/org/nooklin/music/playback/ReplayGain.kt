package org.nooklin.music.playback

import android.os.Bundle
import kotlin.math.min
import kotlin.math.pow
import org.nooklin.music.data.playback.ReplayGainMode

/**
 * The volume factor (0 to 1) for a song. Only lowers the volume: a quiet song is not made louder, because that could
 * clip. The factor is also limited so the song's peak stays under full scale.
 */
fun replayGainFactor(extras: Bundle?, mode: Int): Float {
    if (extras == null || mode == ReplayGainMode.OFF) return 1f
    fun get(key: String): Double? = if (extras.containsKey(key)) extras.getDouble(key) else null

    val gain: Double?
    val peak: Double?
    if (mode == ReplayGainMode.ALBUM) {
        gain = get(RG_ALBUM) ?: get(RG_TRACK)
        peak = get(RG_ALBUM_PEAK) ?: get(RG_TRACK_PEAK)
    } else {
        gain = get(RG_TRACK) ?: get(RG_ALBUM)
        peak = get(RG_TRACK_PEAK) ?: get(RG_ALBUM_PEAK)
    }
    // No data for this song: the server may say what to assume, otherwise leave the volume alone
    val gainDb = gain ?: get(RG_FALLBACK) ?: return 1f

    var factor = 10.0.pow(gainDb / 20.0)
    if (peak != null && peak > 0.0) factor = min(factor, 1.0 / peak)
    return min(factor, 1.0).coerceAtLeast(0.0).toFloat()
}
