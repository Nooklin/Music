package org.nooklin.music.data.library

data class Artist(
    val id: String,
    val name: String,
    val sortName: String,
    val albumCount: Int,
    val coverArt: String?,
    val starred: Boolean
)

data class Album(
    val id: String,
    val name: String,
    val sortName: String,
    val artist: String,
    val artistId: String?,
    val year: Int,
    val songCount: Int,
    val coverArt: String?,
    val starred: Boolean
)

/** Loudness data from the server (OpenSubsonic). Gains are in dB, peaks are linear. Every value can be missing. */
data class ReplayGain(
    val trackGain: Double?,
    val albumGain: Double?,
    val trackPeak: Double?,
    val albumPeak: Double?,
    val fallbackGain: Double?
)

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String?,
    val artistId: String?,
    val track: Int,
    val disc: Int,
    val durationSeconds: Int,
    val suffix: String,
    // In kbps, 0 when the server does not say
    val bitRate: Int,
    val coverArt: String?,
    val starred: Boolean,
    // 0 for no rating, otherwise 1 to 5
    val userRating: Int,
    val replayGain: ReplayGain?,
    // Only filled for songs that come from Navidrome's own song list
    val playCount: Int = 0,
    // Genre of the song, empty when the server does not say
    val genre: String = "",
    // Times played by this user according to the server, -1 when the answer has no count
    val plays: Int = -1,
    // Tempo from the tags of the song on the server, 0 when the server does not say
    val bpm: Int = 0
)

data class Genre(val name: String, val songCount: Int, val albumCount: Int)

data class ArtistDetail(val artist: Artist, val albums: List<Album>)

data class AlbumDetail(val album: Album, val songs: List<Song>)

data class Favorites(val artists: List<Artist>, val albums: List<Album>, val songs: List<Song>)

data class SearchResults(val artists: List<Artist>, val albums: List<Album>, val songs: List<Song>)

/** [owner] and [changed] (the time of the last change, as the server writes it) can be empty. */
data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int,
    val coverArt: String?,
    val durationSeconds: Int = 0,
    val owner: String = "",
    val changed: String? = null,
    val comment: String = ""
)

data class PlaylistDetail(val playlist: Playlist, val songs: List<Song>)

/** One line of a song text. [startMs] is null for text without timing. */
data class LyricLine(val startMs: Long?, val text: String)

data class Lyrics(val synced: Boolean, val lines: List<LyricLine>)

enum class AlbumListKind(val apiType: String, val title: String, val sortLocally: Boolean) {
    ALL("alphabeticalByName", "Albums", true),
    NEWEST("newest", "Recently added", false),
    RECENT("recent", "Recently played", false),
    FREQUENT("frequent", "Frequently played", false),
    STARRED("starred", "Favourite albums", false),
    RANDOM("random", "Random albums", false)
}

enum class StarKind(val param: String) { ARTIST("artistId"), ALBUM("albumId"), SONG("id") }
