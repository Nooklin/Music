# Nooklin Music

An open-source, privacy-first music player for Android that plays from your own Navidrome server.

Part of the Nooklin FOSS Android suite. License: GPL-3.0.

## Features (1.0.1)

- Browse your Navidrome library: artists, albums, covers, favourites, recently added, recently played, most played, playlists, genres and search. A tap on a song plays it; albums and artists have their own screen, and a "..." menu behind every album.
- Home: AutoMix card, Discovery, weekly mixes ("Playlists made for you") and Radio Stations, round pictures of the artists you play most. A tap on a radio station plays only songs of that artist: an AutoMix of the artist's downloaded songs when AutoMix is on (a normal AutoMix when fewer than 5 of them are downloaded), otherwise the artist's songs from the server, shuffled.
- Playback of the original files (FLAC, WAV, MP3, M4A/AAC, OGG, Opus) with background playback, queue, shuffle, repeat, speed, skip silence, sleep timer, ReplayGain and crossfade.
- AutoMix: songs are mixed into each other on the beat, with several mixes to start from on Home. With AutoMix on, a tap on a song starts a new mix around that song.
- Player screen coloured from the cover, star ratings, lyrics, and favourites and ratings that sync with the server.
- Downloads for offline listening (song, album or playlist).
- AirPlay output to AirPlay receivers (AirPlay 2 and AirPort Express). Chromecast is not supported. Sound starts about two seconds after pressing play on the speakers.
- Settings for server, appearance (with a Colorful display switch for coloured icon tiles), playback, AutoMix, downloads, home layout and an encrypted backup. A short tour at the first start (and under Settings > Tour) walks through the app, starting with adding your server. See SCOPE.md for the history of every version.

## Principles

- No Google Play Services, no telemetry, no analytics, no advertising.
- Only features that are finished are in the app.
- The app only talks to the server address you enter yourself. The password is stored encrypted (Android Keystore)
  and is never sent: requests carry a token made from it. http is refused unless you switch on Allow http.

## Building

Open the folder in Android Studio and let Gradle sync. `local.properties` (your SDK path) is machine-specific and is
not part of the project. Release signing is optional: copy `keystore.properties.example` to `keystore.properties`.

Requirements: Android 13 (API 33) or newer.

The debug build installs next to the release app: it has the id `org.nooklin.music.dev` and the name "Nooklin Music Dev", with its own data.

## Third-party code

See the `THIRD-PARTY-LICENSES` folder. The stem separation of AutoMix needs four model files in `app/src/main/assets/stems`,
see the README.txt there. Without them AutoMix uses its normal filters.
