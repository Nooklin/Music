package org.nooklin.music.data.playback

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import org.nooklin.music.data.backup.BackupJson

private val Context.playbackDataStore by preferencesDataStore(name = "nooklin_music_playback")

private val SPEED_KEY = floatPreferencesKey("speed")
private val SKIP_SILENCE_KEY = booleanPreferencesKey("skip_silence")
private val CROSSFADE_KEY = intPreferencesKey("crossfade_seconds")
private val REPLAY_GAIN_KEY = intPreferencesKey("replay_gain_mode")
private val VOLUME_LIMIT_KEY = intPreferencesKey("volume_limit_percent")
private val PRIVATE_KEY = booleanPreferencesKey("private_mode")
private val AUTO_PLAY_KEY = booleanPreferencesKey("auto_dj")
private val DISCOVERY_KEY = booleanPreferencesKey("discovery_station")
private val SMART_FADES_KEY = booleanPreferencesKey("smart_fades")
private val AUTO_MIX_KEY = booleanPreferencesKey("auto_mix")
private val BEAT_MIX_KEY = booleanPreferencesKey("beat_mix")
private val STEM_MIX_KEY = booleanPreferencesKey("stem_mix")
private val AUTO_MIX_NOTICE_KEY = booleanPreferencesKey("auto_mix_notice_hidden")
// The mode picked in the AutoMix menu (empty: none), the tempo zone and the genres that go with it
private val AUTO_MIX_MODE_KEY = stringPreferencesKey("auto_mix_mode")
private val AUTO_MIX_ZONE_KEY = intPreferencesKey("auto_mix_zone")
private val AUTO_MIX_GENRES_KEY = stringSetPreferencesKey("auto_mix_genres")
private val SLEEP_END_KEY = longPreferencesKey("sleep_end_at")
private val SLEEP_TRACK_KEY = booleanPreferencesKey("sleep_at_track_end")

private val BACKUP_NAMES = setOf("speed", "skip_silence", "crossfade_seconds", "replay_gain_mode", "volume_limit_percent", "private_mode", "auto_dj", "smart_fades", "auto_mix", "beat_mix")

data class PlaybackSettingsValues(
    val speed: Float = 1f,
    val skipSilence: Boolean = false,
    // Wall clock time in milliseconds when playback should pause, 0 for no timer
    val sleepEndAt: Long = 0L,
    val sleepAtTrackEnd: Boolean = false,
    // 0 means off
    val crossfadeSeconds: Int = 0,
    // See ReplayGainMode
    val replayGainMode: Int = ReplayGainMode.OFF,
    // Highest volume of this phone in percent of full, 100 means no limit
    val volumeLimitPercent: Int = 100,
    // True: plays are not reported to the server
    val privateMode: Boolean = false,
    // True: when the queue runs out, similar songs are added
    val autoPlay: Boolean = false,
    // True: the Discovery Station keeps adding songs you have not played
    val discovery: Boolean = false,
    // True: no crossfade between songs of the same album
    val smartFades: Boolean = false,
    // True: long overlap between all songs
    val autoMix: Boolean = false,
    // True (with autoMix): tempo matched, overlap on the beat
    val beatMix: Boolean = true,
    // True (with beatMix): the overlap is built from the stems of both songs
    val stemMix: Boolean = true,
    // True: the notice about downloaded songs is not shown when AutoMix is switched on
    val autoMixNoticeHidden: Boolean = false,
    // The mode of the AutoMix menu that was picked last ("" for none), see MixMode
    val autoMixMode: String = "",
    // Index of the tempo zone for the Tempozone mode
    val autoMixZone: Int = 2,
    // Genres (lower case) for the Genres mode
    val autoMixGenres: Set<String> = emptySet()
)

object ReplayGainMode {
    const val OFF = 0
    const val TRACK = 1
    const val ALBUM = 2
}

/**
 * Settings the player service follows. The screen writes them here and the service reacts, so they also work when
 * the screen is closed.
 */
class PlaybackSettings(private val context: Context) {

    // For the encrypted backup. The sleep timer is left out: it only makes sense at the moment it was set.
    suspend fun exportBackup(): JSONObject = BackupJson.dump(context.playbackDataStore.data.first(), BACKUP_NAMES)

    suspend fun importBackup(json: JSONObject) {
        context.playbackDataStore.edit { BackupJson.apply(it, json, BACKUP_NAMES) }
    }

    val values: Flow<PlaybackSettingsValues> = context.playbackDataStore.data.map { prefs ->
        PlaybackSettingsValues(
            speed = prefs[SPEED_KEY] ?: 1f,
            skipSilence = prefs[SKIP_SILENCE_KEY] ?: false,
            sleepEndAt = prefs[SLEEP_END_KEY] ?: 0L,
            sleepAtTrackEnd = prefs[SLEEP_TRACK_KEY] ?: false,
            crossfadeSeconds = prefs[CROSSFADE_KEY] ?: 0,
            replayGainMode = prefs[REPLAY_GAIN_KEY] ?: ReplayGainMode.OFF,
            volumeLimitPercent = prefs[VOLUME_LIMIT_KEY] ?: 100,
            privateMode = prefs[PRIVATE_KEY] ?: false,
            autoPlay = prefs[AUTO_PLAY_KEY] ?: false,
            discovery = prefs[DISCOVERY_KEY] ?: false,
            smartFades = prefs[SMART_FADES_KEY] ?: false,
            autoMix = prefs[AUTO_MIX_KEY] ?: false,
            beatMix = prefs[BEAT_MIX_KEY] ?: true,
            stemMix = prefs[STEM_MIX_KEY] ?: true,
            autoMixNoticeHidden = prefs[AUTO_MIX_NOTICE_KEY] ?: false,
            autoMixMode = prefs[AUTO_MIX_MODE_KEY] ?: "",
            autoMixZone = prefs[AUTO_MIX_ZONE_KEY] ?: 2,
            autoMixGenres = prefs[AUTO_MIX_GENRES_KEY] ?: emptySet()
        )
    }

    suspend fun setSpeed(speed: Float) {
        context.playbackDataStore.edit { it[SPEED_KEY] = speed }
    }

    suspend fun setSkipSilence(enabled: Boolean) {
        context.playbackDataStore.edit { it[SKIP_SILENCE_KEY] = enabled }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.playbackDataStore.edit { it[CROSSFADE_KEY] = seconds }
    }

    suspend fun setReplayGainMode(mode: Int) {
        context.playbackDataStore.edit { it[REPLAY_GAIN_KEY] = mode }
    }

    suspend fun setVolumeLimitPercent(percent: Int) {
        context.playbackDataStore.edit { it[VOLUME_LIMIT_KEY] = percent }
    }

    suspend fun setStemMix(enabled: Boolean) {
        context.playbackDataStore.edit { it[STEM_MIX_KEY] = enabled }
    }

    suspend fun setAutoMixMode(mode: String, zone: Int, genres: Set<String>) {
        context.playbackDataStore.edit {
            it[AUTO_MIX_MODE_KEY] = mode
            it[AUTO_MIX_ZONE_KEY] = zone
            it[AUTO_MIX_GENRES_KEY] = genres
        }
    }

    suspend fun setBeatMix(enabled: Boolean) {
        context.playbackDataStore.edit { it[BEAT_MIX_KEY] = enabled }
    }

    suspend fun setAutoMix(enabled: Boolean) {
        context.playbackDataStore.edit { it[AUTO_MIX_KEY] = enabled }
    }

    suspend fun setAutoMixNoticeHidden(hidden: Boolean) {
        context.playbackDataStore.edit { it[AUTO_MIX_NOTICE_KEY] = hidden }
    }

    suspend fun setSmartFades(enabled: Boolean) {
        context.playbackDataStore.edit { it[SMART_FADES_KEY] = enabled }
    }

    suspend fun setAutoPlay(enabled: Boolean) {
        context.playbackDataStore.edit { it[AUTO_PLAY_KEY] = enabled }
    }

    suspend fun setDiscovery(enabled: Boolean) {
        context.playbackDataStore.edit { it[DISCOVERY_KEY] = enabled }
    }

    suspend fun setPrivateMode(enabled: Boolean) {
        context.playbackDataStore.edit { it[PRIVATE_KEY] = enabled }
    }

    suspend fun setSleepAfterMinutes(minutes: Int) {
        context.playbackDataStore.edit {
            it[SLEEP_END_KEY] = System.currentTimeMillis() + minutes * 60_000L
            it[SLEEP_TRACK_KEY] = false
        }
    }

    suspend fun setSleepAtTrackEnd() {
        context.playbackDataStore.edit {
            it[SLEEP_END_KEY] = 0L
            it[SLEEP_TRACK_KEY] = true
        }
    }

    suspend fun clearSleep() {
        context.playbackDataStore.edit {
            it[SLEEP_END_KEY] = 0L
            it[SLEEP_TRACK_KEY] = false
        }
    }
}
