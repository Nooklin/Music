package org.nooklin.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import org.nooklin.music.playback.mix.MixMode
import org.nooklin.music.ui.library.GenreAlbumsScreen
import org.nooklin.music.ui.library.GenresScreen
import org.nooklin.music.ui.playback.PlayerSheet
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.data.library.Song
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nooklin.music.data.library.AlbumListKind
import org.nooklin.music.data.local.AppThemeMode
import kotlinx.coroutines.flow.map
import org.nooklin.music.data.server.ServerStore
import org.nooklin.music.ui.common.LocalBottomOverlay
import org.nooklin.music.ui.common.LocalOpenArtist
import org.nooklin.music.ui.common.LocalQueueActions
import org.nooklin.music.ui.common.QueueActions
import org.nooklin.music.ui.home.HomeScreen
import org.nooklin.music.ui.home.LibraryScreen
import org.nooklin.music.ui.library.SearchScreen
import org.nooklin.music.ui.navigation.AppBottomBar
import org.nooklin.music.ui.navigation.SearchButton
import org.nooklin.music.ui.navigation.Tab
import org.nooklin.music.ui.playback.UpNextScreen
import org.nooklin.music.ui.library.AlbumScreen
import org.nooklin.music.ui.library.AlbumsScreen
import org.nooklin.music.ui.library.ArtistScreen
import org.nooklin.music.ui.library.ArtistsScreen
import org.nooklin.music.ui.library.FavoritesScreen
import org.nooklin.music.ui.library.LibraryViewModel
import org.nooklin.music.ui.library.DownloadsScreen
import org.nooklin.music.ui.library.PlayedSongsScreen
import org.nooklin.music.ui.library.PlaylistScreen
import org.nooklin.music.ui.library.SongsScreen
import org.nooklin.music.ui.library.PLAY_ALBUM_LIMIT
import org.nooklin.music.ui.library.PlaylistsScreen
import org.nooklin.music.ui.playback.MiniPlayer
import org.nooklin.music.ui.playback.NowPlayingScreen
import org.nooklin.music.ui.playback.PlaybackViewModel
import org.nooklin.music.ui.settings.LocalColorful
import org.nooklin.music.ui.tour.TourActions
import org.nooklin.music.ui.tour.TourContext
import org.nooklin.music.ui.tour.TourOverlay
import org.nooklin.music.ui.tour.tourTarget
import org.nooklin.music.ui.settings.SettingsAboutScreen
import org.nooklin.music.ui.settings.SettingsAppearanceScreen
import org.nooklin.music.ui.settings.ServerViewModel
import org.nooklin.music.ui.settings.SettingsScreen
import org.nooklin.music.ui.settings.SettingsServerScreen
import org.nooklin.music.ui.settings.SettingsBackupScreen
import org.nooklin.music.ui.settings.SettingsDownloadsScreen
import org.nooklin.music.ui.settings.SettingsPlaybackScreen
import org.nooklin.music.ui.settings.SettingsAutoMixScreen
import org.nooklin.music.ui.settings.SettingsHomeScreen
import org.nooklin.music.ui.settings.SettingsViewModel
import org.nooklin.music.ui.theme.DesignStyle
import org.nooklin.music.ui.theme.NooklinTheme

private enum class Route {
    HOME, SEARCH, LIBRARY, ARTISTS, ALBUMS, SONGS, FAVORITES, PLAYLISTS, NEWEST, RECENT, FREQUENT, STARRED, RANDOM, TOPPLAYED, HISTORY, UPNEXT, DOWNLOADS, GENRES, LIBPLAYLISTS,
    SETTINGS, SERVER, APPEARANCE, HOMESETTINGS, DOWNLOADSSETTINGS, PLAYBACKSETTINGS, AUTOMIXSETTINGS, BACKUP, ABOUT
}

private fun parentOf(route: Route): Route = when (route) {
    Route.HOME -> Route.HOME
    Route.SEARCH -> Route.HOME
    Route.LIBRARY, Route.PLAYLISTS, Route.SETTINGS -> Route.HOME
    Route.NEWEST, Route.RECENT, Route.FREQUENT, Route.STARRED, Route.RANDOM, Route.TOPPLAYED, Route.HISTORY, Route.UPNEXT -> Route.HOME
    Route.ARTISTS, Route.ALBUMS, Route.SONGS, Route.FAVORITES, Route.DOWNLOADS, Route.GENRES, Route.LIBPLAYLISTS -> Route.LIBRARY
    Route.SERVER, Route.APPEARANCE, Route.HOMESETTINGS, Route.DOWNLOADSSETTINGS, Route.PLAYBACKSETTINGS, Route.AUTOMIXSETTINGS, Route.BACKUP, Route.ABOUT -> Route.SETTINGS
}

// The place in the bottom bar that a screen belongs to
private fun tabOf(route: Route): Tab = when (route) {
    Route.HOME, Route.SEARCH, Route.NEWEST, Route.RECENT, Route.FREQUENT, Route.STARRED, Route.RANDOM, Route.TOPPLAYED, Route.HISTORY, Route.UPNEXT -> Tab.HOME
    Route.LIBRARY, Route.ARTISTS, Route.ALBUMS, Route.SONGS, Route.FAVORITES, Route.DOWNLOADS, Route.GENRES, Route.LIBPLAYLISTS -> Tab.LIBRARY
    Route.PLAYLISTS -> Tab.PLAYLISTS
    Route.SETTINGS, Route.SERVER, Route.APPEARANCE, Route.HOMESETTINGS, Route.DOWNLOADSSETTINGS, Route.PLAYBACKSETTINGS, Route.AUTOMIXSETTINGS, Route.BACKUP, Route.ABOUT -> Tab.SETTINGS
}

// How far into the Library a screen is: 0 is Library itself, 1 a screen opened from it. Null for everything else.
private fun libraryDepth(route: Route, detailText: String): Int? = when {
    detailText.isNotEmpty() -> null
    route == Route.LIBRARY -> 0
    route in listOf(Route.ARTISTS, Route.ALBUMS, Route.SONGS, Route.FAVORITES, Route.DOWNLOADS, Route.GENRES, Route.LIBPLAYLISTS) -> 1
    else -> null
}

@Composable
fun NooklinMusicApp() {
    val settingsViewModel: SettingsViewModel = viewModel()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val contrastColor by settingsViewModel.contrastColor.collectAsStateWithLifecycle()
    val colorful by settingsViewModel.colorful.collectAsStateWithLifecycle()
    val tourDone by settingsViewModel.tourDone.collectAsStateWithLifecycle()
    val appFont by settingsViewModel.appFont.collectAsStateWithLifecycle()
    val homeSections by settingsViewModel.homeSections.collectAsStateWithLifecycle()
    val librarySections by settingsViewModel.librarySections.collectAsStateWithLifecycle()
    val serverViewModel: ServerViewModel = viewModel()
    val serverState by serverViewModel.state.collectAsStateWithLifecycle()

    val systemIsDark = isSystemInDarkTheme()
    val resolvedDarkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.BLACK -> true
        AppThemeMode.SYSTEM -> systemIsDark
    }
    val isBlackMode = themeMode == AppThemeMode.BLACK

    // Status bar icons follow our theme, not the system one
    val context = LocalContext.current
    val view = LocalView.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        val insetsController = WindowCompat.getInsetsController(window, view)
        // Dark icons for a light background, the inverse of resolvedDark
        insetsController.isAppearanceLightStatusBars = !resolvedDarkTheme
        insetsController.isAppearanceLightNavigationBars = !resolvedDarkTheme
    }

    val libraryViewModel: LibraryViewModel = viewModel()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { libraryViewModel.refreshAll() }

    var route by rememberSaveable { mutableStateOf(Route.HOME) }
    // Artist and album pages opened from a list, newest last, joined by new lines (plain text survives a rotation).
    // Entries look like "artist:ID" or "album:ID".
    var detailText by rememberSaveable { mutableStateOf("") }
    val details = if (detailText.isEmpty()) emptyList() else detailText.split("\n")
    // Where the search was opened from, so closing it goes back there
    var searchReturn by rememberSaveable { mutableStateOf(Route.HOME) }
    val openSearch = {
        searchReturn = route
        route = Route.SEARCH
    }
    BackHandler(enabled = details.isNotEmpty() || route != Route.HOME) {
        if (details.isNotEmpty()) {
            detailText = details.dropLast(1).joinToString("\n")
        } else if (route == Route.SEARCH) {
            route = searchReturn
        } else {
            route = parentOf(route)
        }
    }
    val openArtist = { id: String -> detailText = (details + "artist:$id").joinToString("\n") }
    val openAlbum = { id: String -> detailText = (details + "album:$id").joinToString("\n") }
    val openPlaylist = { id: String -> detailText = (details + "playlist:$id").joinToString("\n") }
    val openGenre = { name: String -> detailText = (details + "genre:$name").joinToString("\n") }
    val closeDetail = { detailText = details.dropLast(1).joinToString("\n") }

    val serverName by remember { ServerStore(context).config.map { it.name } }.collectAsStateWithLifecycle("")

    // Creating the download manager starts the check for new favourites when that option is on
    LaunchedEffect(Unit) { DownloadManager.get(context) }
    val playbackViewModel: PlaybackViewModel = viewModel()
    val playerState by playbackViewModel.state.collectAsStateWithLifecycle()
    val playbackSettings by playbackViewModel.settings.collectAsStateWithLifecycle()
    val mixBusy by playbackViewModel.mixBusy.collectAsStateWithLifecycle()
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = nowPlayingOpen) { nowPlayingOpen = false }

    // Android 13 and up asks before a notification may show. Playing goes on whatever the answer is.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val startPlayback: (() -> Unit) -> Unit = { action ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        action()
    }
    val playSongs = { songs: List<Song>, index: Int, shuffle: Boolean ->
        startPlayback { playbackViewModel.playSongs(songs, index, shuffle) }
    }
    // One instance for the whole app: the menus read it, and it reads the latest player state when it is used
    val hasMediaNow by rememberUpdatedState(playerState.hasMedia)
    val queueActions = remember {
        QueueActions(
            playNext = { songs ->
                // An empty queue starts playing, so that needs the same notification question as any other start
                if (hasMediaNow) playbackViewModel.playNext(songs) else startPlayback { playbackViewModel.playNext(songs) }
            },
            addToQueue = { songs ->
                if (hasMediaNow) playbackViewModel.addToQueue(songs) else startPlayback { playbackViewModel.addToQueue(songs) }
            },
            play = { songs -> playSongs(songs, 0, false) },
            playSong = { songs, index -> startPlayback { playbackViewModel.playSongFrom(songs, index) } }
        )
    }

    NooklinTheme(
        style = DesignStyle.IOS,
        darkTheme = resolvedDarkTheme,
        contrastColor = contrastColor,
        appFont = appFont,
        blackMode = isBlackMode
    ) {
        Box(Modifier.fillMaxSize()) {
            // The screens fill the whole window; the mini player and the bottom bar float on top of them
            var overlayHeight by remember { mutableStateOf(0.dp) }
            val density = LocalDensity.current
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                CompositionLocalProvider(LocalBottomOverlay provides overlayHeight, LocalQueueActions provides queueActions, LocalColorful provides colorful, LocalOpenArtist provides openArtist) {
                Box(Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.navigationBars)) {
            // Screens opened from Library slide in from the right and out to the right; all other changes are instant
            AnimatedContent(
                targetState = route to detailText,
                transitionSpec = {
                    val from = libraryDepth(initialState.first, initialState.second)
                    val to = libraryDepth(targetState.first, targetState.second)
                    if (targetState.first == Route.UPNEXT && initialState.first != Route.UPNEXT) {
                        // Up Next rises from the bottom over the screen below it
                        (slideInVertically { it } togetherWith ExitTransition.None).apply { targetContentZIndex = 1f }
                    } else if (initialState.first == Route.UPNEXT && targetState.first != Route.UPNEXT) {
                        (EnterTransition.None togetherWith slideOutVertically { it }).apply { targetContentZIndex = -1f }
                    } else if (from != null && to != null && from != to) {
                        if (to > from) {
                            (slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }).apply { targetContentZIndex = 1f }
                        } else {
                            (slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }).apply { targetContentZIndex = -1f }
                        }
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "screens"
            ) { (shownRoute, shownDetailText) ->
            val detail = shownDetailText.split("\n").lastOrNull { it.isNotEmpty() }
            if (detail != null) {
                val id = detail.substringAfter(':')
                if (detail.startsWith("artist:")) {
                    ArtistScreen(id, libraryViewModel, onBack = closeDetail, onOpenAlbum = openAlbum, onPlay = playSongs)
                } else if (detail.startsWith("genre:")) {
                    GenreAlbumsScreen(id, libraryViewModel, onBack = closeDetail, onOpenAlbum = openAlbum)
                } else if (detail.startsWith("playlist:")) {
                    PlaylistScreen(id, libraryViewModel, onBack = closeDetail, onPlay = playSongs)
                } else {
                    AlbumScreen(id, libraryViewModel, onBack = closeDetail, onPlay = playSongs)
                }
            } else when (shownRoute) {
                Route.HOME -> HomeScreen(
                    library = libraryViewModel,
                    sections = homeSections,
                    player = playerState,
                    serverName = serverName,
                    onJump = { index -> startPlayback { playbackViewModel.jumpTo(index) } },
                    onOpenUpNext = { route = Route.UPNEXT },
                    onOpenAlbum = openAlbum,
                    onOpenPlayed = { byCount -> route = if (byCount) Route.TOPPLAYED else Route.HISTORY },
                    onOpenPlaylist = openPlaylist,
                    onPlaySongs = playSongs,
                    onStartRadio = { artist ->
                        if (playbackSettings.autoMix && MixMode.of(playbackSettings.autoMixMode) != null) {
                            startPlayback { playbackViewModel.startArtistMix(artist.id) }
                        } else {
                            // AutoMix off: the songs of this artist from the server, shuffled; downloaded is not needed
                            libraryViewModel.songsOfArtists(listOf(artist), PLAY_ALBUM_LIMIT) { songs ->
                                startPlayback { playbackViewModel.playSongs(songs, 0, true) }
                            }
                        }
                    },
                    discoveryOn = playbackSettings.discovery,
                    autoMixOn = playbackSettings.autoMix,
                    autoMixMode = playbackSettings.autoMixMode,
                    autoMixBusy = mixBusy,
                    onStartAutoMix = {
                        val mode = MixMode.of(playbackSettings.autoMixMode) ?: MixMode.MIXED
                        startPlayback { playbackViewModel.chooseAutoMixMode(mode, announce = true) }
                    },
                    onStartDiscovery = {
                        libraryViewModel.startDiscovery { songs ->
                            startPlayback { playbackViewModel.playSongs(songs, 0, false, discovery = true) }
                        }
                    },
                    onStopDiscovery = playbackViewModel::stopDiscovery,
                    onOpenList = { kind ->
                        route = when (kind) {
                            AlbumListKind.NEWEST -> Route.NEWEST
                            AlbumListKind.RECENT -> Route.RECENT
                            AlbumListKind.FREQUENT -> Route.FREQUENT
                            AlbumListKind.STARRED -> Route.STARRED
                            AlbumListKind.RANDOM -> Route.RANDOM
                            else -> Route.ALBUMS
                        }
                    }
                )
                Route.SEARCH -> SearchScreen(
                    vm = libraryViewModel,
                    onClose = { route = searchReturn },
                    onOpenArtist = { id -> route = searchReturn; openArtist(id) },
                    onOpenAlbum = { id -> route = searchReturn; openAlbum(id) },
                    onPlay = playSongs
                )
                Route.LIBRARY -> LibraryScreen(
                    sections = librarySections,
                    onSave = settingsViewModel::setLibrarySections,
                    onNavigate = { target ->
                        route = when (target) {
                            "artists" -> Route.ARTISTS
                            "albums" -> Route.ALBUMS
                            "songs" -> Route.SONGS
                            "downloads" -> Route.DOWNLOADS
                            "genres" -> Route.GENRES
                            "playlists" -> Route.LIBPLAYLISTS
                            else -> Route.FAVORITES
                        }
                    }
                )
                Route.UPNEXT -> UpNextScreen(
                    player = playerState,
                    library = libraryViewModel,
                    onBack = { route = Route.HOME },
                    onJump = { index -> startPlayback { playbackViewModel.jumpTo(index) } },
                    onShuffle = { startPlayback { playbackViewModel.shuffleAndPlayNext() } }
                )
                Route.ARTISTS -> ArtistsScreen(libraryViewModel, onBack = { route = Route.LIBRARY }, onOpenArtist = openArtist, onPlay = playSongs)
                Route.ALBUMS -> AlbumsScreen(AlbumListKind.ALL, libraryViewModel, { route = Route.LIBRARY }, openAlbum, playSongs)
                Route.NEWEST -> AlbumsScreen(AlbumListKind.NEWEST, libraryViewModel, { route = Route.HOME }, openAlbum, playSongs)
                Route.RECENT -> AlbumsScreen(AlbumListKind.RECENT, libraryViewModel, { route = Route.HOME }, openAlbum, playSongs)
                Route.FREQUENT -> AlbumsScreen(AlbumListKind.FREQUENT, libraryViewModel, { route = Route.HOME }, openAlbum, playSongs)
                Route.STARRED -> AlbumsScreen(AlbumListKind.STARRED, libraryViewModel, { route = Route.HOME }, openAlbum, playSongs)
                Route.RANDOM -> AlbumsScreen(AlbumListKind.RANDOM, libraryViewModel, { route = Route.HOME }, openAlbum, playSongs)
                Route.TOPPLAYED -> PlayedSongsScreen(true, libraryViewModel, { route = Route.HOME }, playSongs)
                Route.HISTORY -> PlayedSongsScreen(false, libraryViewModel, { route = Route.HOME }, playSongs)
                Route.GENRES -> GenresScreen(libraryViewModel, { route = Route.LIBRARY }, openGenre, playSongs)
                Route.LIBPLAYLISTS -> PlaylistsScreen(libraryViewModel, { route = Route.LIBRARY }, openPlaylist)
                Route.DOWNLOADS -> DownloadsScreen({ route = Route.LIBRARY }, playSongs)
                Route.SONGS -> SongsScreen(libraryViewModel, { route = Route.LIBRARY }, playSongs)
                Route.PLAYLISTS -> PlaylistsScreen(libraryViewModel, { route = Route.HOME }, openPlaylist)
                Route.FAVORITES -> FavoritesScreen(libraryViewModel, { route = Route.LIBRARY }, openArtist, openAlbum, playSongs)
                Route.SETTINGS -> SettingsScreen(
                    onBack = { route = parentOf(Route.SETTINGS) },
                    onNavigate = { target ->
                        route = when (target) {
                            "server" -> Route.SERVER
                            "appearance" -> Route.APPEARANCE
                            "home" -> Route.HOMESETTINGS
                            "downloads" -> Route.DOWNLOADSSETTINGS
                            "playback" -> Route.PLAYBACKSETTINGS
                            "automix" -> Route.AUTOMIXSETTINGS
                            "backup" -> Route.BACKUP
                            "tour" -> { settingsViewModel.setTourDone(false); Route.HOME }
                            "about" -> Route.ABOUT
                            else -> Route.SETTINGS
                        }
                    }
                )
                Route.SERVER -> SettingsServerScreen(
                    state = serverState,
                    onNameChange = serverViewModel::onNameChange,
                    onPrimaryUrlChange = serverViewModel::onPrimaryUrlChange,
                    onSecondaryUrlChange = serverViewModel::onSecondaryUrlChange,
                    onUsernameChange = serverViewModel::onUsernameChange,
                    onPasswordChange = serverViewModel::onPasswordChange,
                    onAllowHttpChange = serverViewModel::onAllowHttpChange,
                    onSave = serverViewModel::save,
                    onTest = serverViewModel::test,
                    onRefresh = serverViewModel::refreshLive,
                    onBack = { route = parentOf(Route.SERVER) }
                )
                Route.APPEARANCE -> SettingsAppearanceScreen(
                    themeMode = themeMode,
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    contrastColor = contrastColor,
                    onContrastColorChange = settingsViewModel::setContrastColor,
                    appFont = appFont,
                    onAppFontChange = settingsViewModel::setAppFont,
                    colorful = colorful,
                    onColorfulChange = settingsViewModel::setColorful,
                    onBack = { route = parentOf(Route.APPEARANCE) }
                )
                Route.HOMESETTINGS -> SettingsHomeScreen(
                    sections = homeSections,
                    onChange = settingsViewModel::setHomeSections,
                    onBack = { route = parentOf(Route.HOMESETTINGS) }
                )
                Route.DOWNLOADSSETTINGS -> SettingsDownloadsScreen(onBack = { route = parentOf(Route.DOWNLOADSSETTINGS) })
                Route.PLAYBACKSETTINGS -> SettingsPlaybackScreen(onBack = { route = parentOf(Route.PLAYBACKSETTINGS) })
                Route.AUTOMIXSETTINGS -> SettingsAutoMixScreen(onBack = { route = parentOf(Route.AUTOMIXSETTINGS) })
                Route.BACKUP -> SettingsBackupScreen(onBack = { route = parentOf(Route.BACKUP) })
                Route.ABOUT -> SettingsAboutScreen(onBack = { route = parentOf(Route.ABOUT) })
            }
            }

                }
                }
                // The search screen has the whole window for the keyboard
                if (route != Route.SEARCH) Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                ) {
                if (playerState.hasMedia) {
                    MiniPlayer(
                        modifier = Modifier.tourTarget("mini_player"),
                        state = playerState,
                        onOpen = { nowPlayingOpen = true },
                        onTogglePlay = { startPlayback { playbackViewModel.togglePlay() } },
                        onNext = { startPlayback { playbackViewModel.next() } }
                    )
                }
                Row(Modifier.navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                AppBottomBar(
                    modifier = Modifier.weight(1f),
                    selected = tabOf(route),
                    onSelect = { tab ->
                        detailText = ""
                        route = when (tab) {
                            Tab.HOME -> Route.HOME
                            Tab.LIBRARY -> Route.LIBRARY
                            Tab.PLAYLISTS -> Route.PLAYLISTS
                            Tab.SETTINGS -> Route.SETTINGS
                        }
                    }
                )
                SearchButton(onClick = openSearch)
                }
                }
            }
            PlayerSheet(open = nowPlayingOpen, onClosed = { nowPlayingOpen = false }) { onDrag, onDragEnd ->
            NowPlayingScreen(
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                vm = playbackViewModel,
                library = libraryViewModel,
                onBack = { nowPlayingOpen = false },
                onOpenAlbum = { id ->
                    nowPlayingOpen = false
                    openAlbum(id)
                },
                onOpenArtist = { id ->
                    nowPlayingOpen = false
                    openArtist(id)
                }
            )
            }
            // The tour of the first start, over everything
            // Not before the saved server is known: the tour decides from it which steps it needs
            if (tourDone == false && serverState.loaded) {
                TourOverlay(
                    context = TourContext(
                        settingsOpen = route == Route.SETTINGS,
                        serverOpen = route == Route.SERVER,
                        libraryOpen = route == Route.LIBRARY,
                        playerOpen = nowPlayingOpen,
                        serverSaved = serverState.hasSavedPassword
                    ),
                    playerAvailable = playerState.hasMedia,
                    actions = TourActions(
                        showHome = { nowPlayingOpen = false; detailText = ""; route = Route.HOME },
                        finish = { nowPlayingOpen = false; route = Route.HOME; settingsViewModel.setTourDone(true) }
                    )
                )
            }
        }
    }
}
