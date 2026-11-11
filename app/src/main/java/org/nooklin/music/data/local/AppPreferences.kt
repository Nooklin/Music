package org.nooklin.music.data.local

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import org.nooklin.music.data.backup.BackupJson

private val Context.appSettingsDataStore by preferencesDataStore(name = "nooklin_music_settings")

enum class AppThemeMode { LIGHT, DARK, BLACK, SYSTEM }

// DEFAULT has no tint (null)
enum class ContrastColor(val storageKey: String, val label: String, val color: Color?) {
    DEFAULT("default", "Default", null),
    BLUE("blue", "Blue", Color(0xFF0A84FF)),
    RED("red", "Red", Color(0xFFFF3B30)),
    ORANGE("orange", "Orange", Color(0xFFFF9500)),
    YELLOW("yellow", "Yellow", Color(0xFFFFCC00)),
    GREEN("green", "Green", Color(0xFF34C759)),
    TEAL("teal", "Teal", Color(0xFF30B0C7)),
    PURPLE("purple", "Purple", Color(0xFFAF52DE)),
    PINK("pink", "Pink", Color(0xFFFF2D55));

    companion object {
        fun fromKey(key: String): ContrastColor? = entries.find { it.storageKey == key }
    }
}

// Two fonts only: Helvetica has no free license to bundle
enum class AppFont(val storageKey: String, val label: String) {
    SYSTEM_DEFAULT("system_default", "Stock Android Font"),
    WORK_SANS("work_sans", "Work Sans"),
    INTER("inter", "Inter");

    companion object {
        fun fromKey(key: String): AppFont? = entries.find { it.storageKey == key }
    }
}

// The blocks of the Home screen. Only blocks the app can fill are listed.
enum class HomeSection(val label: String) {
    DISCOVERY("Discovery"),
    MIXES("Playlists made for you"),
    UP_NEXT("Up Next"),
    RECENT("Recently played"),
    NEWEST("Recently added"),
    FREQUENT("Frequently played"),
    STARRED("Favourite albums"),
    RANDOM("Random albums"),
    TOP_PLAYED("Top played"),
    HISTORY("Song history")
}

data class HomeSectionSetting(val section: HomeSection, val enabled: Boolean)

private val DEFAULT_HOME_SECTIONS = listOf(
    HomeSectionSetting(HomeSection.DISCOVERY, true),
    HomeSectionSetting(HomeSection.MIXES, true),
    HomeSectionSetting(HomeSection.UP_NEXT, true),
    HomeSectionSetting(HomeSection.RECENT, true),
    HomeSectionSetting(HomeSection.NEWEST, true),
    HomeSectionSetting(HomeSection.FREQUENT, true),
    HomeSectionSetting(HomeSection.STARRED, true),
    HomeSectionSetting(HomeSection.RANDOM, true),
    HomeSectionSetting(HomeSection.TOP_PLAYED, false),
    HomeSectionSetting(HomeSection.HISTORY, false)
)

// Stored as "RECENT:1,NEWEST:0,...": the order is the order on screen
private fun parseHomeSections(stored: String?): List<HomeSectionSetting> {
    val saved = stored.orEmpty().split(",").mapNotNull { part ->
        val pieces = part.split(":")
        val section = HomeSection.entries.find { it.name == pieces.getOrNull(0) } ?: return@mapNotNull null
        HomeSectionSetting(section, pieces.getOrNull(1) == "1")
    }.distinctBy { it.section }
    // A block that was added in a later version shows up at its default place, switched on
    val result = saved.toMutableList()
    DEFAULT_HOME_SECTIONS.forEachIndexed { index, default ->
        if (result.none { it.section == default.section }) result.add(index.coerceAtMost(result.size), default)
    }
    return result
}

// The rows of the Library screen
enum class LibrarySection(val label: String, val key: String) {
    ARTISTS("Artists", "artists"),
    ALBUMS("Albums", "albums"),
    SONGS("Songs", "songs"),
    FAVORITES("Favorites", "favorites"),
    GENRES("Genres", "genres"),
    PLAYLISTS("Playlists", "playlists"),
    DOWNLOADS("Downloaded Songs", "downloads")
}

data class LibrarySectionSetting(val section: LibrarySection, val visible: Boolean)

// Stored as "ARTISTS:1,ALBUMS:0,...": the order is the order on screen
private fun parseLibrarySections(stored: String?): List<LibrarySectionSetting> {
    val saved = stored.orEmpty().split(",").mapNotNull { part ->
        val pieces = part.split(":")
        val section = LibrarySection.entries.find { it.name == pieces.getOrNull(0) } ?: return@mapNotNull null
        LibrarySectionSetting(section, pieces.getOrNull(1) == "1")
    }.distinctBy { it.section }
    // A row that was added in a later version shows up at the end
    return saved + LibrarySection.entries.filter { e -> saved.none { it.section == e } }
        .map { LibrarySectionSetting(it, true) }
}

private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
private val CONTRAST_COLOR_KEY = stringPreferencesKey("contrast_color")
private val APP_FONT_KEY = stringPreferencesKey("app_font")
private val WIFI_ONLY_DOWNLOADS_KEY = booleanPreferencesKey("wifi_only_downloads")
private val AUTO_DOWNLOAD_FAVORITES_KEY = booleanPreferencesKey("auto_download_favorites")
private val DOWNLOAD_LIMIT_MB_KEY = intPreferencesKey("download_limit_mb")
private val PLAYLIST_GRID_KEY = booleanPreferencesKey("playlist_grid")
private val PLAYLIST_ASCENDING_KEY = booleanPreferencesKey("playlist_ascending")
private val LIBRARY_SECTIONS_KEY = stringPreferencesKey("library_sections")
private val HOME_SECTIONS_KEY = stringPreferencesKey("home_sections")
private val COLORFUL_KEY = booleanPreferencesKey("colorful")

private val BACKUP_NAMES = setOf(
    "theme_mode", "contrast_color", "app_font", "wifi_only_downloads", "auto_download_favorites",
    "playlist_grid", "playlist_ascending", "library_sections", "home_sections", "colorful"
)

class AppPreferences(private val context: Context) {

    // The settings for the encrypted backup. Server details are not in here.
    suspend fun exportBackup(): JSONObject = BackupJson.dump(context.appSettingsDataStore.data.first(), BACKUP_NAMES)

    suspend fun importBackup(json: JSONObject) {
        context.appSettingsDataStore.edit { BackupJson.apply(it, json, BACKUP_NAMES) }
    }

    val themeMode: Flow<AppThemeMode> = context.appSettingsDataStore.data.map { prefs ->
        when (prefs[THEME_MODE_KEY]) {
            "light" -> AppThemeMode.LIGHT
            "dark" -> AppThemeMode.DARK
            "black" -> AppThemeMode.BLACK
            else -> AppThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name.lowercase()
        }
    }

    val contrastColor: Flow<ContrastColor> = context.appSettingsDataStore.data.map { prefs ->
        prefs[CONTRAST_COLOR_KEY]?.let { ContrastColor.fromKey(it) } ?: ContrastColor.DEFAULT
    }

    suspend fun setContrastColor(color: ContrastColor) {
        context.appSettingsDataStore.edit { prefs -> prefs[CONTRAST_COLOR_KEY] = color.storageKey }
    }

    // Settings icons on coloured tiles (on by default)
    val colorful: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs -> prefs[COLORFUL_KEY] ?: true }

    suspend fun setColorful(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[COLORFUL_KEY] = value }
    }

    // Playlists overview: list or grid, and the direction of the name sort
    val playlistGrid: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs -> prefs[PLAYLIST_GRID_KEY] ?: false }
    val playlistAscending: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs -> prefs[PLAYLIST_ASCENDING_KEY] ?: true }

    suspend fun setPlaylistGrid(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[PLAYLIST_GRID_KEY] = value }
    }

    suspend fun setPlaylistAscending(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[PLAYLIST_ASCENDING_KEY] = value }
    }

    // Downloads wait for an unmetered connection unless the user switches this off
    val wifiOnlyDownloads: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[WIFI_ONLY_DOWNLOADS_KEY] ?: true
    }

    // Songs with a heart are downloaded by themselves
    val autoDownloadFavorites: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[AUTO_DOWNLOAD_FAVORITES_KEY] ?: false
    }

    suspend fun setAutoDownloadFavorites(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[AUTO_DOWNLOAD_FAVORITES_KEY] = value }
    }

    // 0.0.140: the most the downloads may take, in MB; 0 is no limit
    val downloadLimitMb: Flow<Int> = context.appSettingsDataStore.data.map { prefs -> prefs[DOWNLOAD_LIMIT_MB_KEY] ?: 0 }

    suspend fun setDownloadLimitMb(value: Int) {
        context.appSettingsDataStore.edit { prefs -> prefs[DOWNLOAD_LIMIT_MB_KEY] = value }
    }

    suspend fun setWifiOnlyDownloads(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[WIFI_ONLY_DOWNLOADS_KEY] = value }
    }

    val librarySections: Flow<List<LibrarySectionSetting>> = context.appSettingsDataStore.data.map { prefs ->
        parseLibrarySections(prefs[LIBRARY_SECTIONS_KEY])
    }

    suspend fun setLibrarySections(sections: List<LibrarySectionSetting>) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[LIBRARY_SECTIONS_KEY] = sections.joinToString(",") { "${it.section.name}:${if (it.visible) 1 else 0}" }
        }
    }

    val homeSections: Flow<List<HomeSectionSetting>> = context.appSettingsDataStore.data.map { prefs ->
        parseHomeSections(prefs[HOME_SECTIONS_KEY])
    }

    suspend fun setHomeSections(sections: List<HomeSectionSetting>) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[HOME_SECTIONS_KEY] = sections.joinToString(",") { "${it.section.name}:${if (it.enabled) 1 else 0}" }
        }
    }

    val appFont: Flow<AppFont> = context.appSettingsDataStore.data.map { prefs ->
        prefs[APP_FONT_KEY]?.let { AppFont.fromKey(it) } ?: AppFont.INTER
    }

    suspend fun setAppFont(font: AppFont) {
        context.appSettingsDataStore.edit { prefs -> prefs[APP_FONT_KEY] = font.storageKey }
    }
}
