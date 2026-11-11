# Nooklin Music: SCOPE

Status: 1.0.1 geschreven en niet gecompileerd of op Android gedraaid in de omgeving waar het is geschreven (zie de secties 1.0.0 en 1.0.1 onderaan; de gebruiker bouwt zelf). Eerdere status: 0.0.28 geschreven en niet gecompileerd (AirPlay-scherm). 0.0.1 en 0.0.2 door de gebruiker bevestigd (bouwt, verbindingstest geslaagd). 0.0.3 bevestigd (muziek zichtbaar). 0.0.4 bevestigd (werkt). 0.0.4b geschreven en niet gecompileerd, 0.0.4c geschreven en niet gecompileerd, 0.0.4d, 0.0.4e, 0.0.5 (bevestigd door de gebruiker: geen problemen), 0.0.5b, 0.0.5c en 0.0.5d geschreven en niet gecompileerd (geen Android-toolchain in de omgeving waar het is geschreven). 0.0.9, 0.0.9b, 0.0.10 (door de gebruiker getest: lijkt te werken) , 0.0.11, 0.0.11b, 0.0.12, 0.0.12b, 0.0.13, 0.0.14, 0.0.15, 0.0.16, 0.0.16b, 0.0.17, 0.0.18, 0.0.19, 0.0.20, 0.0.21, 0.0.22, 0.0.23, 0.0.24, 0.0.25, 0.0.26 en 0.0.27 geschreven en niet gecompileerd. Bijgewerkt op 2026-10-14.

## Doel
Open-source muziekspeler voor Android binnen de Nooklin-familie. Speelt muziek van je eigen Navidrome-server, advertentievrij, met offline toegang, Navidrome-afspeellijsten, een aanpasbare interface en AirPlay-uitvoer op basis van de AirSync-code.

## Uitgangspunten
- Package: `org.nooklin.music`.
- Toolchain gelijk aan Photos v1.0.0: AGP 9.3.3, Kotlin 2.2.10, Gradle 9.5.0, JVM-toolchain 25, compileSdk 37, minSdk 33, targetSdk 36, compose-bom 2026.02.01.
- Liquid Glass-stijl, componenten uit Photos hergebruikt (`GlassPill`, `GlassPillContainer`, `NooklinTheme`). Geen nieuwe componentstijlen.
- Geen Google Play Services, geen telemetrie, geen analytics, geen advertentie-ID, geen externe diensten.
- Netwerk alleen naar het ene (of tweede) serveradres dat de gebruiker zelf invult. INTERNET is toegevoegd in 0.0.2.
- Alleen functies die af zijn komen in de app. Geen "coming soon".
- ZIP per stap met oplopend versienummer (`0.0.1.zip`, enzovoort). Voor elke ZIP draait de pre-package sweep. Elke stap is los te testen op een toestel.
- ERRORS.md en SCOPE.md in de projectroot.

## Besluiten
- Bron: alleen Navidrome (Subsonic API). Lokale map later.
- Streamen: voor nu het originele bestand, geen transcodering.
- Afspeellijsten: alleen bekijken en bewerken van Navidrome-afspeellijsten. Geen lokale of slimme afspeellijsten.
- Formaten: FLAC, WAV, MP3, M4A/AAC, OGG en Opus.
- Verbinding: http op het lokale netwerk is nodig. Het manifest staat cleartext toe, de OkHttp-client weigert http tenzij de gebruiker de optie inschakelt (met waarschuwing). https blijft de standaard. Te testen in 0.0.2.
- Featureset: "Uitgebreid" (zie stappen).
- AirPlay-proef vooraan: niet nodig. De test met de AirSync-code is volgens de gebruiker geslaagd. Het resterende risico (PCM uit de Media3-speler, tempo, pauze) wordt in 0.0.8 zelf met een proef met een nummer afgedekt.
- Doel voor AirPlay-kwaliteit: zie "Open keuzes".

## Stappen

| Versie | Inhoud | Status | Testbaar als |
|---|---|---|---|
| 0.0.1 | Projectskelet, designsysteem uit Photos, home met Instellingen, Uiterlijk (thema, lettertype, accentkleur) en Over | Gebouwd en bevestigd | App start in de juiste stijl, thema, lettertype en kleur blijven na herstart |
| 0.0.2 | Server instellen: URL, gebruikersnaam, wachtwoord (versleuteld met Android Keystore), "Test verbinding". Optie voor http op lokaal netwerk met waarschuwing. Tweede serveradres (thuis en buiten), de test toont per adres of het werkt | Gebouwd en bevestigd (test geslaagd) | Verbinding slaagt of geeft duidelijke fout, beide adressen worden apart getest |
| 0.0.3 | Bibliotheek: artiesten, albums, hoezen, favorieten (hartje, gesynchroniseerd met Navidrome), recent toegevoegd, recent afgespeeld, meest afgespeeld. Sorteren zonder lidwoorden. Automatisch omschakelen tussen hoofd- en tweede adres. Nog niet: lijst met alle nummers, sterbeoordeling 1 tot 5 (zie Open vragen) | Gebouwd en bevestigd | Collectie verschijnt, favoriet in app zie je terug in Navidrome |
| 0.0.4 | Afspelen van FLAC, WAV, MP3, M4A/AAC, OGG en Opus (origineel bestand, geen transcodering). Achtergrondservice, notificatie en vergrendelscherm, wachtrij, shuffle en herhaal, wachtrij bewaren en herstellen, gapless, afspeelsnelheid, stilte overslaan, sleeptimer, volumetoetsen. Tikken op een nummer speelt het album vanaf dat nummer | Gebouwd en bevestigd | Elk formaat speelt door met scherm uit, wachtrij komt terug na herstart |
| 0.0.4b | ReplayGain (uit, per nummer of per album; alleen zachter, nooit luider) en crossfade (3, 6, 9 of 12 seconden) | Gebouwd, nog niet gecompileerd | Volume gelijk tussen nummers, overgang vloeit |
| 0.0.4c | Spelerlayout volgens het voorbeeld van de gebruiker (zie "Spelerlayout"). Vervangt het huidige Nu-spelend-scherm. Inclusief sterbeoordeling 1 tot 5, bitrate en bron in de infolijn, achtergrond in een kleur uit de hoes | Gebouwd, nog niet gecompileerd | Scherm lijkt op het voorbeeld, sterren en hartje komen terug in Navidrome |
| 0.0.4d | Songteksten: knop in de speler, tekst tonen, meescrollen als de server regels met tijden geeft | Gebouwd, nog niet gecompileerd | Songtekst van een nummer verschijnt, en scrolt mee als hij tijden heeft |
| 0.0.5 | Navidrome-afspeellijsten bekijken en bewerken | Gebouwd, nog niet gecompileerd | Wijziging in de app zie je terug in Navidrome |
| 0.0.5b | Songs-overzicht met zoeken en meerdere nummers kiezen, "..." met Add to playlist bij nummers | Gebouwd, nog niet gecompileerd | |
| 0.0.5c | Onderste balk (Home, Library, Playlists, Settings), nieuw Home (servernaam, Up Next, Recently played, Recently added, Most played), Library-tab, Up Next-scherm, veld Server name | Gebouwd, nog niet gecompileerd | Zoekknop en zoekbalk volgen in 0.0.5d, de pil met Download all en Songs-wisselaar in 0.0.6 |
| 0.0.5d | Zoekscherm over artiesten, albums en nummers, met recente zoekopdrachten (alleen op het apparaat) en Clear | Gebouwd, nog niet gecompileerd | Zie screenshot van de gebruiker |
| 0.0.5e | Home-secties Frequently played, Favourite albums, Random albums erbij; aan/uit en volgorde instelbaar via Settings, Home (schakelaars en pijltjes) | Gebouwd, nog niet gecompileerd | Top played en Song history niet gebouwd: bron nog niet bepaald |
| 0.0.5f | Top played en Song history op Home (uit) en als eigen lijst; bron: Navidrome eigen API (`/auth/login`, `/api/song`, sorteren op playCount en playDate), niet Subsonic | Gebouwd, nog niet gecompileerd | Veldnamen en endpoint niet getest; history toont per nummer alleen de laatste afspeeldatum, geen log |
| 0.0.6 | Offline, kern: download per nummer, album en afspeellijst, origineel bestand in de privé-opslag van de app, alleen via Wi-Fi als optie (standaard aan), wachtrij met pauzeren en annuleren, scherm Downloads (Library) met afspelen en wissen, "Downloaded" in de infolijn | Gebouwd, nog niet gecompileerd | Rollende opslag, automatische regels en de Up Next-pil volgen later; geen foreground-service voor downloads |
| 0.0.7 | Aanpasbare interface in de omvang van Photos: hoezen met afgeronde hoeken, extra stijlen voor het nu-spelend-scherm naast de layout uit 0.0.4c. Versleutelde back-up en herstel van instellingen. (Thema, lettertype en accentkleur zitten al in 0.0.1.) | Gepland | Instellingen blijven na herstart, back-up herstelt op een ander toestel |
| 0.0.8 | AirPlay-uitvoer op basis van de AirSync-code, met een eerste proef met een nummer | Gebouwd, niet getest | Nummers in alle formaten spelen op AirPort Express en Apple TV |

## Spelerlayout (referentie van de gebruiker)

Voorbeeldbeeld: `docs/player-layout-reference.png`. Het is een screenshot van een andere speler die de gebruiker mooi vindt. Alleen de indeling wordt overgenomen, geen code en geen naam of icoon van die app.

Indeling van boven naar beneden:
1. Hele scherm in een kleur afgeleid van de hoes (hier teal). Bovenaan een greep: omlaag vegen sluit het scherm.
2. Grote hoes met afgeronde hoeken, gecentreerd, met een lichte schaduw.
3. Titel (vet, gecentreerd) en eronder de artiest(en) in een lichtere kleur.
4. Rij: hartje links, vijf sterren in het midden, menu "..." rechts.
5. Dunne afgeronde voortgangsbalk. Eronder links de verstreken tijd, in het midden een infolijn (bron, formaat, bitrate, bijvoorbeeld "Stream · FLAC · 1778 kbps"), rechts de resterende tijd met een min ervoor.
6. Bedieningsrij: shuffle links, vorige, grote afspeel/pauzeknop, volgende, herhalen rechts. Een actieve schakelaar krijgt een gevulde cirkel.
7. Onderste rij met pictogrammen: uitvoer (AirPlay), instellingen, sluiten (pijl omlaag), songtekst, wachtrij, sleeptimer.

Wat elk onderdeel nodig heeft en wanneer het erin komt (regel uit het project: alleen opnemen als het werkt):

| Onderdeel | Nodig | Status |
|---|---|---|
| Kleur uit de hoes, greep, grote hoes, titel en artiest | Kleurberekening uit het hoes-plaatje met AndroidX Palette, veeggebaar | In 0.0.4c |
| Hartje | Bestaat al (favorieten) | In 0.0.4c |
| Vijf sterren | Nieuwe aanroep `setRating` en lezen van `userRating` | In 0.0.4c |
| Menu "..." | Alleen wat werkt: naar album, naar artiest. "Toevoegen aan afspeellijst" komt in 0.0.5, "Downloaden" in 0.0.6 | In 0.0.4c met alleen de werkende items |
| Voortgangsbalk en tijden | Bestaat al | In 0.0.4c |
| Infolijn | Formaat bestaat al. Bitrate (`bitRate`) moet erbij in het model en de wachtrij. Bron toont "Stream", en "Gedownload" zodra 0.0.6 er is | In 0.0.4c, alleen "Stream" |
| Shuffle, vorige, afspelen, volgende, herhalen | Bestaat al | In 0.0.4c |
| Instellingen (tandwiel) | Opent een paneel met snelheid, stilte overslaan, crossfade en ReplayGain (nu losse knoppen op het scherm) | In 0.0.4c |
| Sluiten | Bestaat al | In 0.0.4c |
| Wachtrij | Opent een eigen paneel in plaats van onder de speler te staan | In 0.0.4c |
| Sleeptimer | Bestaat al | In 0.0.4c |
| Uitvoer (AirPlay) | Komt met 0.0.8 | Niet zichtbaar tot 0.0.8 |
| Songtekst | Eigen stap 0.0.4d (zie Stappen). De knop verschijnt pas als die stap werkt | Niet zichtbaar tot 0.0.4d |

Dus de eerste versie van het scherm heeft in de onderste rij vier pictogrammen (instellingen, sluiten, wachtrij, sleeptimer). Songtekst komt erbij met 0.0.4d, uitvoer met 0.0.8.

## Bevindingen uit de analyse van AirSync en Photos

**AirSync**
- De map `raop` (pairing, versleutelde RTSP, timing, sync-pakketten, versleutelde RTP, event-kanaal, now-playing) is bijna onafhankelijk van de rest van de app. Een sessieklasse per ontvanger houdt alle toestand, de gedeelde klok en tijdlijn zitten bij de aansturing. Dat maakt multiroom mogelijk.
- Metadata (titel, artiest, album, hoes, voortgang) wordt al naar de ontvanger gestuurd.
- Audiobron in AirSync is schermopname (MediaProjection), vast 44,1 kHz/16-bit stereo, 352 samples per pakket. Music krijgt geluid uit zijn eigen speler: geen schermopname-dialoog nodig.
- De "ALAC-encoder" schrijft ongecomprimeerde ALAC-frames (lossless, ongeveer 1,4 Mbit/s per ontvanger).
- Het tempo komt in AirSync van de opname (realtime). Een speler levert niet vanzelf in realtime, dus de aansturing moet in Music opnieuw het tempo regelen.
- Pauze, seek en FLUSH bestaan niet in AirSync.
- Er is geen resampler of hogere kwaliteit dan 44,1 kHz/16-bit getest. De enige bewezen configuratie is ALAC 44100/16/2.
- AirSync heeft geen `LICENSE`-bestand. Een Apache-2.0-project (akustikrausch/airplay2-sender-cpp) wordt in ERRORS.md genoemd als hergebruikbaar met naamsvermelding. Controleer voor publicatie wat is overgenomen.
- AirSync gebruikt View-XML-schermen. Alleen de logica is bruikbaar voor Music (Compose).

**Photos**
- `DesignSystem.kt`, `GlassPillContainer`, de instellingenstructuur en het scherm Uiterlijk zijn zonder herontwerp te hergebruiken (gebeurd in 0.0.1).
- `GlassBlock` bestaat als privé kopie in twee schermen van Photos. In Music wordt het bij eerste gebruik een gedeelde composable.
- Photos belooft "geen internetrecht". Music heeft INTERNET nodig voor Navidrome, dus de privacytekst moet bij 0.0.2 worden aangepast.

## Open keuzes (nog niet beantwoord, met voorstel)

| Keuze | Voorstel | Alternatief |
|---|---|---|
| PCM uit de speler | De audioverwerking van de ExoPlayer aftappen en de lokale uitvoer stil zetten tijdens AirPlay | Eigen audio-uitgang in de speler (meer controle, veel meer werk) |
| Pauze, seek, skip | Stilte blijven sturen zolang de speler pauzeert (simpelst, past bij AirSync) | FLUSH implementeren (sneller, nieuw protocolwerk) |
| AirPlay-code delen | De map `raop` kopiëren naar Music (AirSync onaangeroerd, twee kopieën) | Gedeelde module (vereist wijziging van AirSync) |
| Doelkwaliteit AirPlay | 44,1 kHz/16-bit stereo (bewezen) | Hoger per ontvanger (nieuw onderzoek en hardwaretests) |

Geen van deze beïnvloedt 0.0.1 tot en met 0.0.7. Ze moeten beslist zijn voor 0.0.8.

## Besluiten spelerlayout
- Sterbeoordeling 1 tot 5 (`setRating`): bevestigd door de gebruiker, zit in 0.0.4c.
- Songtekst: de gebruiker zegt dat de server songteksten doorgeeft, dus de knop blijft. Eigen stap 0.0.4d. Eerst controleren welke aanroep jouw Navidrome gebruikt (OpenSubsonic `getLyricsBySongId` of het oudere `getLyrics`), en of er meegescrolde regels (synced) zijn. Niet geverifieerd.
- Kleur uit de hoes: AndroidX Palette (advies). Versie nog opzoeken bij het bouwen.

## Open vragen voor de gebruiker (0.0.3)
- Sterbeoordeling (1 tot 5, `setRating`) naast favorieten: wel of niet? Nu alleen favorieten (hartje).
- Lijst met alle nummers: Subsonic heeft hiervoor geen eigen aanroep. Nummers staan nu in albums en favorieten. Zoeken komt later en maakt een volledige lijst mogelijk.

## Onzekerheden
- 0.0.5d (playlistscherm): kop met collage van de eerste vier covers (bij minder dan vier verschillende covers de eerste cover alleen), naam, eigenaar en "Updated ..." uit de velden `owner` en `changed` van de server, Shuffle en Play, achtergrond: de covers (2 bij 2, of de eerste cover) vergroot, 60 dp vervaagd en voor 55% afgedekt met de themakleur. Hernoemen en verwijderen zitten nu in het "..."-menu rechtsboven, verwijderen uit de lijst in de "..." achter elk nummer. Niet in het scherm: het streepjesicoon, de downloadknop en de downloadstatus (0.0.6).
- 0.0.5d: of Navidrome `owner` en `changed` meestuurt en in welk tijdformaat is niet getest. Ontbreekt het, dan blijft de regel weg of toont alleen het aantal nummers.
- 0.0.5d: zoeken gebruikt `search3` met de zoekterm (15 artiesten, 20 albums, 40 nummers). De resultaatindeling in secties is mijn keuze, het screenshot toont alleen het scherm voor het typen.
- 0.0.5d: recente zoekopdrachten (maximaal 10) staan alleen op het apparaat en worden bewaard bij toetsenbord-zoeken of een tik op een resultaat. De privacytekst bij About noemt dit.
- 0.0.5d: een tik op een artiest of album sluit het zoekscherm en opent dat scherm. Terug gaat dan naar het scherm waar je zocht, niet naar de zoekresultaten.
- 0.0.5d: de iconen `Search`, `Close` en `History` (Rounded) zijn niet gecompileerd.
- 0.0.5c: de onderste balk en de miniplayer zijn los zwevende pillen met `GlassPillContainer` en `GlassPill.adaptiveGlassBackground`, de stijl die de rest van de app al uit Nooklin Photos overneemt. De Photos-code zelf heb ik niet, dus de maten (hoeken, ruimte, hoogte) zijn niet vergeleken. Alles zit in `ui/navigation/AppBottomBar.kt` en `ui/playback/MiniPlayer.kt`.
- 0.0.5c: de schermen vullen het hele venster en de pills liggen erboven; lijsten en scrollende schermen laten onderaan ruimte vrij ter hoogte van de pills (`LocalBottomOverlay`, gemeten). Nieuwe schermen met een lijst moeten dat ook doen. Snackbars zijn omhoog geschoven. De pills hebben 80 procent dekking (`GlassPill.adaptiveFloatingBackground`) en de tekst erop gebruikt de themakleur `onSurface`.
- 0.0.5c: Up Next volgt de afspeelvolgorde via `Timeline.getNextWindowIndex` (dus ook bij shuffle). Niet getest. Shuffle op het Up Next-scherm zet shuffle aan en gaat naar een volgend nummer.
- 0.0.5c: de pil rechtsboven op het Up Next-scherm (Download all, wisselen tussen All Songs en Downloaded Songs) ontbreekt bewust tot downloads bestaan (0.0.6).
- 0.0.5c: de iconen `Home`, `LibraryMusic`, `PlaylistPlay` (Rounded) zijn niet gecompileerd.
- 0.0.5b: het Songs-overzicht gebruikt `search3` met een lege zoekterm en `songOffset`/`songCount` (200 per pagina). Of jouw Navidrome dat met paginering ondersteunt is niet getest. Als de eerste pagina leeg is, probeer ik eenmalig `""` (twee aanhalingstekens). De volgorde is die van de server, niet door mij gesorteerd.
- 0.0.5b: het einde van de lijst herken ik aan een lege pagina, dus er volgt altijd nog een extra aanroep.
- 0.0.5b: "..." met "Add to playlist" staat bij nummers in albums, favorieten, afspeellijsten en het Songs-overzicht. Meerdere nummers kiezen kan alleen in het Songs-overzicht ("Select"). De selectie blijft staan na toevoegen tot je op "Done" tikt.
- 0.0.5: afspeellijsten via `getPlaylists`, `getPlaylist`, `createPlaylist`, `updatePlaylist` (naam wijzigen, nummers toevoegen en verwijderen op positie) en `deletePlaylist`. Standaard Subsonic, niet getest op jouw Navidrome.
- 0.0.5: toevoegen kan vanuit het "..."-menu van de speler en met "Add to playlist" op een albumpagina (hele album). Niet vanuit losse nummers in lijsten. Slepen om de volgorde te wijzigen is niet gebouwd.
- 0.0.5: het pictogram `PlaylistPlay` is niet gecontroleerd.
- 0.0.4e: afgeronde iconen via `Icons.Rounded.*` (alleen speler en miniplayer). Dat is alleen zachter, niet gelijk aan jouw voorbeeld. Of alle gebruikte Rounded-namen bestaan is niet gecompileerd.
- 0.0.4e: wachtrij is een paneel in de speler, songtekst gebruikt dezelfde compacte kop, slaaptimer is nu een sheet. "Clear" haalt alles na het huidige nummer weg (mijn keuze, niet bevestigd).
- 0.0.4d: songteksten komen eerst via `getLyricsBySongId` (OpenSubsonic, met tijden), en als dat niets geeft via het oudere `getLyrics` op artiest en titel. De Navidrome-documentatie noemt alleen `getLyrics` als ondersteund, `getLyricsBySongId` staat daar niet vermeld. Niet getest op jouw server.
- 0.0.4d: `start` van een regel lees ik als milliseconden (de documentatiepagina zegt dat niet uitdrukkelijk, de voorbeelden wel). `offset` uit het antwoord negeer ik, omdat het teken onbekend is. Kleine tijdverschuivingen zijn mogelijk.
- 0.0.4d: het oudere `getLyrics` zoekt op de artiestnaam zoals die in de titel staat. Bij meerdere artiesten ("A, B, C") kan het niets vinden.
- 0.0.4d: het pictogram `FormatQuote` is niet gecontroleerd.
- 0.0.4c: AndroidX Palette 1.0.0 is de nieuwste stabiele versie volgens de releasepagina. De coordinaat `androidx.palette:palette:1.0.0` is afgeleid, niet gelezen. Niet gebouwd.
- 0.0.4c: de kleur uit de hoes wordt donkerder gemaakt tot wit leesbaar is (contrast 4,5). Of dat op jouw hoezen goed oogt, is niet gezien.
- 0.0.4c: favoriet en sterren worden opgehaald via `getSong` zodra een nummer speelt. `setRating` en `getSong` zijn niet getest op jouw Navidrome.
- 0.0.4c: de infolijn toont alleen "Stream". De bitrate komt uit `bitRate` van de server (kbps). Bij een teruggezette wachtrij uit 0.0.4b ontbreekt de bitrate tot het nummer opnieuw is gestart vanuit een lijst.
- 0.0.4c: sheets (instellingen, wachtrij) gebruiken de themakleuren, niet de kleur uit de hoes.
- 0.0.4b crossfade: tweede, kortstondige speler speelt het einde van het oude nummer terwijl de hoofdspeler het volgende nummer start. Niets hiervan is getest. Risico's: kleine verschuiving tussen de twee (starttijd van audio), zoeken in een stream zonder exacte zoektabel (de overlap kan dan haperen), dubbele netwerkbelasting tijdens de overgang.
- 0.0.4b: geen crossfade bij herhaal-een, bij sleeptimer "einde van dit nummer", bij het laatste nummer zonder herhalen, of als het nummer niet op tijd gebufferd is (dan gewoon gapless).
- 0.0.4b ReplayGain: gebruikt `replayGain` uit het nummer (OpenSubsonic, in dB). Volume gaat alleen omlaag en wordt begrensd door de piek. Opus `baseGain` wordt genegeerd (onbekend of de decoder dat al toepast). Of jouw Navidrome `replayGain` meestuurt, is niet getest.
- 0.0.4: Media3 1.11.1 komt van de releasepagina via een samenvatting, niet met een build bevestigd. De Media3-API's (ResolvingDataSource, BitmapLoader, MediaSessionService) zijn uit mijn kennis van oudere versies geschreven en niet gecontroleerd tegen 1.11.1.
- 0.0.4: `stream?format=raw` zou transcodering uitzetten. Niet getest op Navidrome. Of FLAC zonder zoektabel goed spoelt, is niet getest.
- 0.0.4: FLAC wordt door Android zelf gedecodeerd (Android 10 of nieuwer). Opus in Ogg en WAV via de standaard-extractors van Media3. Geen enkel formaat is hier getest.
- 0.0.4: het stream-adres met login-token wordt op het moment van openen gemaakt en nooit bewaard. In de wachtrij staan alleen id's en tekst.
- 0.0.4: bij een netwerkfout probeert de service één keer het andere serveradres. Foutcodes 2000 tot 2999 gelden als netwerkfout (uit mijn geheugen).
- 0.0.4: de sleeptimer en snelheid leven in een DataStore die de service volgt, dus ze werken ook met het scherm dicht.
- 0.0.4: het wachtrijscherm kan nummers aantikken, niet herschikken of verwijderen.
- 0.0.3 is niet gecompileerd. Iconen (Album, NewReleases, History, TrendingUp, Person, Dns) zijn niet gecontroleerd tegen de icon-bibliotheek.
- Hoofdadres heeft voorrang: bij het starten van de app en na het wijzigen van instellingen. Na uitvallen blijft de app op het tweede adres tot dat ook uitvalt (geen netwerk-wijzigingsmelding).
- Recent en meest afgespeeld tonen wat de server weet. Music meldt zelf geen afspeelacties (dat komt met afspelen, 0.0.4).
- Hoezen staan alleen in het geheugen, niet op schijf (offline-opslag komt in 0.0.6).
- Sorteren gebruikt `ignoredArticles` van de server (anders een vaste lijst) en `sortName` als de server die geeft. Niet getest op jouw bibliotheek.
- (0.0.2) Dit is niet gecompileerd. De eerste Gradle-uitvoer van de gebruiker is de eerste echte controle.
- OkHttp 5.3.1 is alleen uit de changelog als nieuwste stabiele versie gehaald, niet met een build bevestigd.
- Adressen moeten met http:// of https:// beginnen, er wordt niets gegokt.
- Zelfondertekende certificaten worden niet geaccepteerd (bewuste keuze, nog niet gevraagd).
- JSON wordt met `org.json` gelezen (simpelst). Retrofit kan bij 0.0.3 komen als dat nodig blijkt.
- Sweep meldt `getValue`/`setValue` als ongebruikt in twee bestanden: valse melding, ze zijn nodig voor `by`.
- AGP 9.3.3 is overgenomen uit Photos. Eerdere notities noemden 9.3.1.
- Of Media3 het omrekenen naar 44,1 kHz/16-bit zelf kan, is niet geverifieerd. Opzoeken voor 0.0.8.
- Welke samplefrequentie en bitdiepte AirPort Express en Apple TV 4K accepteren, is alleen uit forumberichten bekend. Meten op de hardware.
- Of kleuren uit een hoes zonder Google-onderdelen kunnen, is niet geverifieerd (verwacht van wel).
- Of Navidrome songteksten, internetradio of een API-sleutel via de Subsonic API aanbiedt, is niet geverifieerd.
- Of Media3 de volumetoetsen voor een AirPlay-uitvoer zelf kan routeren (via apparaat-informatie van de speler) is niet getest. AirSync heeft hiervoor een complexe oplossing nodig gehad (zie ERRORS.md daar).

## Uitgesteld (backlog, nog niet in de app)
- Songteksten, ook meelopend (eerst controleren of Navidrome dit via de API aanbiedt).
- Equalizer en parametrische EQ.
- Widgets, Android Auto, Bluetooth-autostart.
- Lokale map als tweede bron.
- Transcodering door Navidrome (streamkwaliteit per verbindingstype).
- Internetradio (eerst controleren of Navidrome dit via de API aanbiedt).

## Bewust niet
- Slimme afspeellijsten en lokale afspeellijsten.
- Scrobbelen naar derden (Last.fm en dergelijke).
- Chromecast (vereist Google Play Services). AirPlay vervangt dit.
- Cloud-bronnen, USB-DAC exclusief, DSD en audioboeken.
- Advertentie-ID, analytics, crashrapportage naar derden.

## Bouwnotities voor 0.0.1
- Placeholder-icoon (vector, muzieknoot). Te vervangen door eindontwerp.
- Geen Room, KSP, navigation-compose, Coil of Media3 in 0.0.1: alleen wat nodig is. Ze komen erbij wanneer een stap ze nodig heeft.
- Navigatie is een eenvoudige toestand met `BackHandler`, zonder navigatiebibliotheek.
- De pre-package sweep meldt `getValue` en `setValue` in `NooklinMusicApp.kt` als ongebruikt. Dat zijn valse alarmen: Kotlin heeft die imports nodig voor `by`.


- 0.0.6 (downloads): bestanden staan in `files/downloads` met een `index.json` voor de nummergegevens. De speler opent `nooklin://song/ID` als bestand wanneer het nummer gedownload is, anders als stream. Pauzeren gooit het halve bestand weg, dat nummer begint opnieuw. Downloads lopen zolang het proces van de app leeft; Android kan ze stoppen als de app lang op de achtergrond staat. Offline bladeren door de bibliotheek werkt niet, alleen afspelen vanuit Library, Downloads of een bewaarde wachtrij.
- 0.0.6b: de Wi-Fi-schakelaar staat in Settings, Downloads (niet meer in Library, Downloads). Onderbalk 56 dp hoog, gelijk aan de zoekknop. Pills 90% dekking. Schermtitels 34 sp. De speler sluit ook met een veeg omlaag op de cover. Playlistscherm: witte tekst op een altijd donkere achtergrond (zwart 55%), achtergrond 1,9 keer vergroot, downloadknop naast Shuffle en Play, zoekpill (omlaag trekken bovenaan de lijst, of "Search in playlist" in het menu; zoekt in titel, artiest en album). Home: afspeelknop in de kop van Recently added en Random albums, speelt de eerste 10 van de getoonde albums in die volgorde.
- 0.0.6c: downloadknop rechts van Play in het playlistscherm. Cirkel om de afspeelknop in de kop van Recently added en Random albums. Playlistcovers in het tabblad Playlists 72 dp (was 52). Library: rij "Downloaded Songs", nieuwe rijen Genres (getGenres, albums per genre via getAlbumList2 type byGenre) en Playlists (zelfde scherm als het tabblad), schermen vanuit Library schuiven van rechts naar links in en weer naar rechts uit (AnimatedContent). "Edit Sections" in het "..."-menu van Library: oog (contrastkleur, rij wordt doorzichtig en verdwijnt uit het normale scherm), drie strepen om te slepen, vinkje om op te slaan, terug slaat niet op, minstens één rij blijft zichtbaar. De speler ligt als laag over het scherm eronder en zakt met de vinger mee (afgeronde hoeken 36 dp), loslaten voorbij 1/3 sluit met een animatie, anders veert hij terug. Niet gebouwd: sluiten met een snelle veeg onder 1/3, een verschuivende rij tijdens het slepen in Edit Sections (de rij wisselt van plaats zodra je er voorbij bent).

## 0.0.6d
- Playlists overview: total duration after the song count (from `duration` of getPlaylists, omitted when absent), search field (focus mode hides title and menu, X in a circle cancels), "..." menu with New Playlist, View (List, Grid) and Sort by Name (A-Z / Z-A); view and sort order remembered.
- Library Edit Sections: rows slide smoothly when the order changes, the dragged row follows the finger.
- Bottom bar and now-playing pill 97 percent opaque.
- Not built: "..." menus in the style of Nooklin Photos (needs a screenshot of an open menu there).

## 0.0.6e
- All "..." menus (Library, Playlists, playlist screen, song rows, player) use one `NooklinMenu` (ui/common/NooklinMenu.kt), copied from the Nooklin Photos code: GlassPill shape, `adaptivePopupBackground`, text in `onSurface`, check marks as trailing icon, groups separated by a divider. Playlists menu: the "View" and "Sort by" labels became dividers, as in Photos.
- Not copied: the round glass button of Photos for the "..." button itself (only the menu was asked). Not compiled.

## 0.0.7
- Mini player: a Next button right of Play/Pause (hidden when no song follows).
- Settings, Backup: "Back up settings" writes a file (nooklin-music-settings.nkbackup, system file picker) encrypted with a password of at least 8 characters (PBKDF2-HMAC-SHA256, 210000 rounds, AES-256-GCM); "Restore settings" reads it back. Contents: theme, font, contrast colour, Home and Library sections, Wi-Fi-only downloads, playlist view and sort, playback options (speed, skip silence, crossfade, ReplayGain). Not in it: server details and server password, queue, sleep timer, recent searches, downloaded files. Only fixed setting names are restored, so a file can set nothing else.
- Decided with the user: no extra Appearance options (Photos has the same ones), no extra now-playing styles beyond the Next button in the mini player.
- Not compiled. Unverified: the file picker flow (CreateDocument / OpenDocument), whether the player service applies restored playback options without a restart.

## 0.0.7b
- Shared top for list screens: `SearchableFrame` (ui/library/SearchableFrame.kt), same layout and focus-mode animation as Playlists (the Playlists screen itself was not changed).
- Albums (all album lists): search pill (name or artist, filters the loaded list) and "..." menu with sorting: Server order, Name (without articles), Artist, Year; tapping the chosen option flips ascending and descending.
- Songs: the old text field is replaced by the same search pill (still searches on the server, 300 ms delay); "..." menu with Select / Done selecting and sorting: Server order, Title, Artist, Album, Duration. Sorting only orders the songs that are loaded so far (pages of 200), not the whole library.
- Sort choices are not remembered after leaving the screen. Not compiled.

## 0.0.7c
- Artists: search pill (name) and "..." menu with sorting (Name, Number of albums), via `SearchableFrame`.
- Search pill the same on every list screen (Playlists, Artists, Albums, Songs): `SearchPill`, 44 dp high (was 56 dp), my reading of "iets lager". Built on BasicTextField.
- Sorting the same everywhere (`SortMenu`): heading "Sort by", options with a check mark, then Ascending / Descending with a check mark; under the search pill a line "Sorted by Name, ascending" (not shown for Server order). Playlists got the same menu (only option: Name; the View choice List/Grid stays as it was). Playlists now also uses `SearchableFrame`.
- Not compiled.

## 0.0.7d
- Downloads in a foreground service (`DownloadService`, type dataSync, permission FOREGROUND_SERVICE_DATA_SYNC): a notification "Downloading n songs" with the title and progress, or "Waiting for Wi-Fi" / "Waiting for a connection". The service only keeps the process alive; `DownloadManager` still does the work. It stops when the queue is empty or paused. On Android 15 the system limits dataSync services to a few hours a day (`onTimeout` stops the service; downloads then go on only while the app lives). Notification permission is asked when playback starts; without it the service still runs but the notification is hidden.
- Settings, Downloads: "Download favourite songs" (off by default). With it on, the songs with a heart are queued when the option is switched on, when the app starts and when a heart is added to a song. Follows the Wi-Fi-only option. A download removed by the user comes back at the next check while the song keeps its heart. Unstarring a song does not delete its download. Added to the settings backup.
- Decided with the user: no rolling storage (maximum storage), no per-playlist automatic download.
- Not compiled. Unverified: the service type and the Android 15 timeout; the 2-argument `onTimeout`.

## 0.0.8
- AirPlay output. Bottom row of the player, far left, AirPlay icon (lit while connected): a dialog lists the speakers found on the Wi-Fi network (name, kind, "needs PIN"); tap to connect or disconnect, a volume slider for each connected speaker (remembered per speaker, default 20), "Stop AirPlay". Several speakers at once share one timeline.
- Audio: `PcmTapProcessor` sits in the main player's audio sink and copies the audio as 44.1 kHz/16-bit stereo into a ring buffer (`AirPlayAudio`); while AirPlay is on the phone's own output is silent. A sender thread (`AirPlayController`) sends one ALAC packet (352 frames) every 7.98 ms, silence when nothing plays (pause, buffering), so the stream never stops. Sync packet every second, health check every 2 s (3 misses drop the speaker). Song title, artist and album are sent to the speaker display.
- Pairing: AirPort Express connects directly; Apple TV asks a code (PIN) shown on the TV, up to 5 tries; credentials are stored. A Mac always uses the code flow.
- Code: `airplay/` folder, copied from AirSync (raop, discovery, ReceiverSession, DeviceIdentity); AirSync itself is untouched. New dependency: BouncyCastle `bcprov-jdk18on:1.79`. New permissions: ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE.
- Not carried to AirPlay: ReplayGain (the tap sits before the player volume), playback speed and skip silence (applied after the tap), crossfade (switched off while AirPlay is on). Sample rates other than 44.1 kHz are converted with linear interpolation. About 2 seconds of delay on the speakers. The volume buttons of the phone do not control the speakers.
- Not compiled and not tested with real speakers. Unverified: the `buildAudioSink` override and `DefaultAudioSink.Builder` calls in Media3 1.11.1, `BaseAudioProcessor` behaviour for the tap, whether service discovery needs more permissions on Android 16, whether an Apple TV accepts this (AirPlay 1 style) session, pause/seek behaviour (queue is cleared on seek and skip).

## 0.0.8b
- AirPlay moved out of the "..." menu to the bottom row of the player, far left (before Settings). The icon is lit while a speaker is connected. Same dialog. Not compiled. Unverified: `Icons.Rounded.Airplay` exists in the extended icon set.

## 0.0.8c
- AirPlay found no speakers. Likely cause (not confirmed): Android allows one service resolve at a time, and the speakers advertise two services each, so parallel resolves failed with "already active" and the service was dropped. Discovery now resolves one by one through a queue and retries those that fail with "already active". Same permissions as AirSync, which were already present. Not compiled, not tested with speakers.

## 0.0.8d
- Log from the user: Android refused the speaker search at once ("Start discovery failed", error 0, internal error) for both _raop._tcp and _airplay._tcp. Not the local network permission (that applies from target 37; this app targets 36). Cause not known. Change: the search now runs explicitly on the Wi-Fi network (not a VPN), through `discoverServices` with a network and executor. Not compiled. If it still fails, the next step is to check whether AirSync finds the speakers on the same phone, and to write an own mDNS search.

## 0.0.8e
- Cause of "no speakers" found by the user: a VPN setting that blocked LAN discovery. With that fixed, the speakers are found (AirPort Express "Workshop" and "Kitchen", Apple TV 4K, a MacBook Pro), but connecting timed out: the log shows the connection leaving from the VPN address (10.2.0.2) instead of the Wi-Fi.
- Fix: `AirPlayNetwork` binds every AirPlay socket (pairing/RTSP TCP, event channel TCP, audio UDP, control and timing UDP) to the Wi-Fi network that is not a VPN, with the system's `Network.bindSocket`. Falls back to the default route when no such network exists. Only AirPlay traffic bypasses the VPN; server streaming is untouched.
- The 0.0.8c queue and 0.0.8d Wi-Fi discovery changes stay in; they were not needed for the VPN case (not proven useful).
- Not compiled. Unverified: whether `bindSocket` on an already bound UDP socket (control, timing) works on this phone, and everything after the TCP connect (pairing, playback).

## 0.0.8f
- Log with the VPN off: pairing (transient), RTSP setup, audio stream setup and the 2-second health checks to the AirPort Express "Kitchen" all work. With the VPN on, `bindSocket` to the Wi-Fi was refused (EPERM), so the 0.0.8e binding does not help against that VPN; AirPlay needs the VPN to allow LAN traffic (or be off). The binding stays and falls back silently.
- Crash fixed (my mistake in 0.0.8d): the discovery executor was shut down when the dialog closed while Android still had a callback queued (RejectedExecutionException). Discovery callbacks now run on the main executor, which never shuts down.
- Not compiled. Not confirmed: that audio is heard, volume, pause and seek, Apple TV PIN flow.

## 0.0.8g
- Volume keys control AirPlay (chosen by the user: through the media session, all connected speakers together). `AirPlayVolumePlayer` (playback/) wraps the player given to the MediaSession: while AirPlay is on it reports a remote device with volume 0 to 100 (the average of the connected speakers), the keys move every connected speaker by 5 and a set volume sets them all equal. Without AirPlay it passes everything to the real player. The sliders in the AirPlay dialog stay per speaker.
- Not compiled. Unverified: that Media3 1.11.1 routes the hardware volume keys to a player that reports a remote device (I believe it makes a remote volume provider for the session); that the deprecated non-flag `setDeviceVolume`, `increaseDeviceVolume`, `decreaseDeviceVolume` and `setDeviceMuted` still exist to override (compile error "overrides nothing" if not); that the listener notifications I send by hand (device info, commands, volume) are enough for the session to switch over.

## 0.0.9
- Chosen with the user: queue actions and a volume limit. Not in this step: Auto DJ, radio, shuffled add, priority queue, playlist from the queue (all on the to-do list).
- Play next and Add to queue: in the "..." menu behind every song that has a full song record (albums, favourites, playlists, Songs, search) and as two buttons on the album page (the whole album). Play next puts the songs right after the one that plays now, Add to queue puts them at the end. With an empty queue they start playing. A short message confirms. The app gives `QueueActions` to every screen through `LocalQueueActions` (ui/common/QueueActions.kt). Not in the menu: the Up Next screen (its rows have no full song record).
- Queue edit: in the queue panel of the player, "Edit" (next to "Clear") shows a cross per song to take it out and, when shuffle is off, three lines to drag it (same drag as Edit Sections). "Done" applies the changes to the player in one go (remove, then move), "Cancel" drops them. At least one song stays. When the queue changed while editing (a song was added or removed), "Done" applies nothing. Not on the Up Next screen.
- Volume limit: Playback sheet, "Volume limit" (Off, 90, 80, 70, 60, 50 percent). It scales the player volume of every song (together with ReplayGain, also during crossfade), so it keeps the volume of this phone below that share of the maximum. It is not a limit on the system volume, so at lower volume settings the sound is also quieter by that share. It does not apply to AirPlay speakers (the tap sits before the player volume). Added to the settings backup (`volume_limit_percent`).
- Not compiled. Unverified: with shuffle on, "Play next" lands on a random place in the shuffled order, not as the next song (ExoPlayer adds new items at random places in the shuffle order); the drag in the queue panel while the list scrolls (no automatic scrolling while dragging, so a song can only be moved within what is on screen); `moveMediaItem` and `removeMediaItem` through the media controller and the `AirPlayVolumePlayer` wrapper; the icons `Rounded.Close` and `Rounded.Menu`.

## 0.0.9b
- Up Next: pil rechtsboven met Download (wordt "Remove downloads" als alles binnen is; haalt per nummer `getSong` op), Opslaan als afspeellijst (de getoonde nummers, na filter en zoekterm) en Filter (All Songs / Downloaded Songs, niet onthouden).
- Albumpagina: "Shuffle next" en "Shuffle to queue" (nummers in willekeurige volgorde toegevoegd).
- Artiestpagina: sectie "Favorite songs" (uit de favorietenlijst van de server, gefilterd op artiest-id) boven de albums.
- Bibliotheek ververst stil bij terugkeer in de app (hoogstens eens per minuut): afspeellijsten, artiesten, albumlijsten, favorieten die al eens geopend waren.
- Niet gebouwd: knop "Scan now" (startScan, adminrechten onzeker), zelf verversen van geopende detailpagina's.
- Niet gecompileerd. Onzeker: `LifecycleEventEffect` in de gebruikte lifecycle-versie, `Icons.Rounded.PlaylistAdd` (kan deprecated zijn, geeft dan een waarschuwing), pil-layout in de TopAppBar, met shuffle aan landt "Shuffle next" willekeurig.

## 0.0.10
- Artiestpagina: bovenaan de artiestafbeelding (cover van de server via `getCoverArt`, geen externe adressen) en de biografie uit `getArtistInfo2` als platte tekst (HTML weggehaald, "Read more on Last.fm" weggehaald), 5 regels, tikken klapt open. Geen biografie op de server: niets getoond.
- Scrobbelen: een nummer langer dan 30 seconden wordt na de helft of 4 minuten (wat eerder is) met `scrobble` (submission=true) bij de eigen server gemeld. Staat aan; schakelaar "Private mode" in het afspeelblad zet het uit. Alleen verbinding met de eigen server.
- Niet gebouwd: "now playing"-melding, melden van nummers die offline zijn gespeeld (mislukte meldingen gaan verloren).
- Niet gecompileerd. Onzeker: of de biografie van jouw server gevuld is (hangt af van de Navidrome-instelling voor Last.fm of Spotify), `Html.fromHtml`-uitvoer, scrobbelen telt ook na vooruitspoelen, een na herstart teruggezet nummer wordt pas gemeld na de volgende nummerwissel, de player wordt vanaf de service-scope uitgelezen zoals de bestaande lussen.

## 0.0.11
- Radio: knop "Radio" op de artiestpagina en "Start radio" in het nummermenu. Haalt tot 50 vergelijkbare nummers op met `getSimilarSongs2` (bij een nummer komt dat nummer eerst) en speelt ze af. Zegt het als de server niets vergelijkbaars vindt. Albumradio is niet gebouwd (de server kent geen album-id voor deze aanroep, onzeker).
- Auto DJ: schakelaar in het afspeelblad (standaard uit). Speelt het laatste nummer van de wachtrij, dan worden tot 10 vergelijkbare nummers toegevoegd (anders willekeurige uit `getRandomSongs`), zonder dubbelen. Werkt alleen met shuffle en herhalen uit. Controle elke 5 seconden.
- Niet gebouwd: Mixes (persoonlijke en instant Mix) en het aantal nummers instelbaar; "willekeurig" of "albums" als Auto DJ-keuze.
- Niet gecompileerd. Onzeker: `getSimilarSongs2` met artiest-id op jouw Navidrome (hangt af van Last.fm/Spotify-instelling), spelen vanuit de service-scope, vertraging tot 5 seconden voor Auto DJ bij een kort laatste nummer.
- Fix na compileren van 0.0.11: in PlaybackSettings.kt stond de naam "auto_dj" per ongeluk in de aanroep van `booleanPreferencesKey("private_mode")`; nu alleen in BACKUP_NAMES. Verder meldde de compiler geen fouten in dit bestand (andere fouten waren niet zichtbaar, de compiler stopte hier niet noodzakelijk na de eerste).

## 0.0.11b
- Nieuw instellingenscherm Settings, Playback: Volume limit als slider (20% tot 100% in stappen van 10, 100% = uit) en Private mode. Beide zijn uit het afspeelblad van de speler gehaald; de opgeslagen waarden blijven dezelfde.
- Afspeelblad (en slaaptimer-blad) openen nu volledig (geen halve stand) en gebruiken de themakleur voor tekst (was zwart op donker). Het afspeelblad scrolt als het niet past.
- Up Next schuift van onderaf in en weer naar beneden uit (slideInVertically / slideOutVertically, boven het scherm eronder).
- Niet gecompileerd. Onzeker: de sliderstappen (`steps = 7` voor 20..100), de animatie met `targetContentZIndex`, de kleur van de tekst op het gekozen donkere blad.

## 0.0.12
- Widget voor het startscherm "Nooklin Music" (4 bij 1, horizontaal schaalbaar): cover, titel, artiest en vorige, afspelen of pauzeren, volgende. Tikken op de widget opent de app. Gebouwd met `RemoteViews` en `AppWidgetProvider`, zonder extra bibliotheek (Glance is niet gebruikt: geen nieuwe afhankelijkheid, eenvoudiger).
- De spelerservice stuurt bij elke nummerwissel en bij start of pauze de gegevens naar de widget; de cover komt van de eigen server (`getCoverArt`, 256 px).
- Knoppen sturen mediatoetsen naar de service (`ACTION_MEDIA_BUTTON` met `getForegroundService`).
- Niet gebouwd: widgetinstellingen, andere maten, voortgangsbalk, wachtrij in de widget.
- Niet gecompileerd en niet gezien. Onzeker: of Media3 `MediaSessionService` de mediatoets-intent van de widget zelf afhandelt (zo niet, werken de knoppen niet), of de service een afgesloten wachtrij herstelt bij een tik op afspelen, uiterlijk van de widget, `R.mipmap.ic_launcher` (adaptief icoon) als lege cover. Na het sluiten van de app blijft de laatst bekende titel staan.

## 0.0.12b
- De widget is overgezet naar Jetpack Glance (`androidx.glance:glance-appwidget` 1.2.0, de laatste stabiele versie volgens developer.android.com/jetpack/androidx/releases/glance, geraadpleegd op 2026-10-11). De RemoteViews-versie uit 0.0.12 is vervangen.
- Drie maten: klein (cover en afspelen), breed (cover, titel, artiest, vorige/afspelen/volgende), hoog (grote cover met titel en de drie knoppen eronder). Vrij schaalbaar in beide richtingen.
- Zelfde gedrag als 0.0.12: de service stuurt de gegevens, de knoppen sturen mediatoetsen, tikken opent de app.
- Niet gebouwd: kleuren van het thema (vaste donkere achtergrond), glance-material3, voortgangsbalk.
- Niet gecompileerd. Onzeker: de exacte Glance-API's (`actionStartService(intent, isForegroundService)`, `cornerRadius`, `SizeMode.Responsive`, `LocalSize`), of Glance 1.2.0 een nieuwere compileSdk of Kotlin vraagt dan het project heeft (de releasepagina zegt het niet), of de drie knoppen elk hun eigen actie krijgen (de intents verschillen per toets via het adres), `R.mipmap.ic_launcher` (adaptief icoon) als lege cover, en of Media3 de mediatoets-intent zelf afhandelt.
- Fix na compileren van 0.0.12b: `actionStartActivity` in Glance 1.2.0 kreeg een `Intent` maar vraagt een `ComponentName` (in dit pakket geen `Intent`-variant); nu `actionStartActivity(ComponentName(context, MainActivity::class.java))`. Andere fouten kunnen nog verborgen zijn.

## 0.0.13
- Smart Fades: schakelaar in het afspeelblad (standaard uit, in de instellingenback-up). Met een crossfade aan en Smart Fades aan faden twee nummers van hetzelfde album (zelfde album-id) niet in elkaar, ze volgen elkaar zonder gat. Andere nummers faden zoals voorheen.
- Gekozen door de gebruiker op 2026-10-12 uit drie opties; de andere twee zijn niet gebouwd: de fade inkorten bij korte nummers en stiltedetectie aan het einde van een nummer.
- Beperking: er is geen tracknummer in de wachtrij, dus ook nummers van een geschud album die toevallig op elkaar volgen krijgen geen fade.
- AutoMix blijft apart in de to-do lijst. Android Auto staat laatst.
- Niet gecompileerd en niet beluisterd. Onzeker: of het echt gapless klinkt (hangt af van ExoPlayer en de bestanden), `nextMediaItemIndex` met shuffle.

## 0.0.14
- AutoMix (eenvoudige versie): schakelaar in het afspeelblad (standaard uit, in de instellingenback-up). Aan: elke overgang tussen twee nummers is een overlap van 10 seconden (of de ingestelde crossfade als die langer is), ook met de crossfade uit. De overlap is hoogstens een derde van het huidige en een derde van het volgende nummer. Dezelfde bestaande crossfade-motor (tweede speler, gelijk-vermogen-curve); Smart Fades (geen fade tussen albumnummers) werkt er ook mee.
- Niet gebouwd en niet haalbaar zonder audioanalyse: tempo- en maatdetectie, tempo aanpassen, beat-matching zoals Apple AutoMix. De gebruiker koos "Bouw AutoMix" na de vraag; ik heb de eenvoudige variant gebouwd en dat zo gemeld.
- Werkt niet met AirPlay (zoals de crossfade: de tweede speler wordt niet doorgestuurd) en niet bij herhalen van één nummer of een slaaptimer bij nummereinde.
- Niet gecompileerd en niet beluisterd. Onzeker: hoe de 10 seconden klinken, extra netwerkverbruik door de tweede speler, `durationMs` ontbreekt soms (dan geldt de duur van het huidige nummer).

## 0.0.15
- AutoMix met beat matching (op verzoek van de gebruiker, 2026-10-13; bron: gedownloade en gestreamde nummers; omvang: tempo gelijktrekken en op de tel overlappen). Schakelaar "Beat matching" in het afspeelblad (standaard uit, in de back-up); werkt alleen met AutoMix aan. Nummers zonder duidelijke beat, te ver uit tempo of te kort krijgen de gewone AutoMix van 0.0.14.
- Analyse (`playback/beat/BeatAnalyzer.kt`), alleen op het toestel: het einde van het huidige en het begin van het volgende nummer (35 s) worden met MediaExtractor en MediaCodec gedecodeerd, omgezet naar een onset-curve (lage en hoge delen), tempo via autocorrelatie met voorkeur rond 120 BPM (70 tot 190), daarna fijn zoeken (tempo en eerste tel). Nummers uit de downloadmap worden lokaal gelezen; de andere worden vanaf je server gestreamd met MediaExtractor (een stuk van het einde via een bereikverzoek, ongeveer 1 tot 6 MB per kant, afhankelijk van het formaat). Resultaten blijven in het geheugen zolang de service draait.
- Plan (`BeatMixPlanner.kt`): tempo van B (ook op half of dubbel geteld) binnen 8% van A, de overlap is 4, 8, 16 of 32 tellen van A (hoogstens 12 s, en een derde van beide nummers), start op een tel van A, B begint op zijn eerste tel na een tel.
- Uitvoering (`CrossfadeController.kt`): de tweede speler speelt het einde van A op de snelheid van het tempo van B (ExoPlayer met behoud van toonhoogte); B start in de hoofdspeler op zijn tel. Zodra B speelt, wordt de fase van beide tellen vergeleken: eenmalig een sprong vooruit binnen het geladen stuk (nooit terug), daarna kleine snelheidscorrecties (hoogstens 3%) tot de tellen samenvallen. Volumes lopen op de positie van A (pauze stopt dus ook de fade).
- Niet gebouwd: toonsoort (harmonisch mixen), maatwisselingen, tempo-aanpassing van B, bewaren van de analyse op schijf, instelbare fadelengte.
- Niet gecompileerd en niet beluisterd; dit is de riskantste versie tot nu toe. Onzeker: of de onset-drempel (0.35 maal de standaardafwijking) de juiste nummers afwijst, nauwkeurigheid van de tellen bij tempowisselingen of rubato, MediaExtractor met een http(s)-adres van je server (eigen certificaat of `http` wordt door het platform, niet door de app-instellingen, afgehandeld) en bereikverzoeken bij Navidrome, hoe goed de snelheidsaanpassing klinkt, hoe lang het opstarten van B duurt (de sprong vooruit moet in het geladen stuk blijven), batterij en verkeer door de analyse, `main.isPlaying` met volume 0.

## 0.0.16
- DJ-mix (op verzoek van de gebruiker, 2026-10-13, na een logboek van 0.0.15). Gekozen: B begint op het beste punt van het hele nummer, bass swap, gewone AutoMix als de analyse niets vindt, hele nummer altijd analyseren (ook gestreamd).
- Analyse (`BeatAnalyzer.kt`): het hele volgende nummer wordt gedecodeerd, alleen de loudness-curve (10 ms) blijft in het geheugen. Tempo en eerste tel als in 0.0.15, nu ook de eerste tel van de maat (de tel van de vier met de meeste onset). Instappunten: maatgrenzen waar de 16 tellen erna luider zijn dan de 16 ervoor (minstens 0.25 stijging, ten minste even luid als de mediaan, niet na 60% van het nummer, minstens 45 s over), gewogen naar vroeg in het nummer en naar de frasegrens (veelvoud van 16 tellen met de meeste stijgingen); ten minste 10 s uit elkaar, hoogstens 5, daarna als laatste keuze de eerste maatgrens. Per instappunt wordt tempo en tel opnieuw gemeten op de 12 s erna. Het einde van A: laatste maatgrens in de laatste 35 s.
- Plan (`BeatMixPlanner.kt`): eerste instappunt waarvoor tempo binnen 8% valt en de overlap past: 16 tellen (anders 8), hoogstens 16 s, op een maatgrens van A.
- Uitvoering (`CrossfadeController.kt`): B springt naar het instappunt, A speelt verder op de tweede speler. Het omschakelen begint `leadMs` voor de maatgrens (begin 600 ms, daarna het gemiddelde van de gemeten opstarttijd van B), zodat B op de maatgrens klinkt. De fout wordt nu absoluut gemeten (waar B hoort te zijn volgens de positie van A), tot drie sprongen vooruit, daarna kleine snelheidscorrecties van A. Volume: B komt tot 0.8, op de helft (maatgrens) wisselt de bas (B krijgt hem, A verliest hem), daarna B naar 1 en A naar 0.
- Bas (`BassCutProcessor.kt`): AudioProcessor met een vierde-orde laagdoorlaat van 180 Hz; uitvoer = invoer min hoeveelheid maal laag. De hoeveelheid loopt in 0,25 s, geen klik. Zit in de hoofdspeler (voor de AirPlay-tap) en in de tweede speler.
- Logboek: tag `NooklinMix` (alleen logcat op het toestel) met plan, leadtijd, opstarttijd van B en sprongen.
- Onzeker: of de maatdetectie de echte eerste tel vindt (bij nummers zonder duidelijke kick kan de bas-swap naast de maat vallen), of de drempels (0.25, mediaan, weging) goede instappunten geven, bereikverzoeken van Navidrome voor een sprong midden in een gestreamd nummer (zonder die is de opstart van B traag), vertraging van de audio-uitvoerbuffer (de bas-swap kan tot enkele honderden ms laat vallen), datagebruik (een volledig bestand per gestreamd nummer), batterij, of 24-bit en andere PCM-vormen door de AudioProcessor-keten gaan.
- Niet gebouwd: andere uitgangspunt in A (A gaat tot het einde), toonsoort, opslag van de analyse op schijf.

## 0.0.16b
- Alleen logging, geen gedragsverandering (op verzoek van de gebruiker na een log van 0.0.16 waarin alle vier de overgangen "no plan" gaven en de muziek haperde bij het wisselen van de cover). Tag `NooklinMix` in logcat, alleen op het toestel.
- Planner: per kandidaat-instappunt waarom het is afgewezen (tempo te ver uit elkaar, geen ruimte, start voor het midden van A, overlap past niet in B) of aangenomen.
- Analyse: hoe lang elke analyse duurde en wat eruit kwam (bpm, maatgrens, instappunten).
- Gewone AutoMix: wanneer de tweede speler is klaargezet, de posities van beide spelers bij de start en 12 keer daarna (status, speelt, positie, voortgang), of de tweede speler te laat klaar was.
- De verklaring voor de hapering (overdracht van de hoofdspeler naar de tweede speler) is een hypothese uit de code, niet bewezen; dit logboek moet dat laten zien.

## 0.0.17
- Op verzoek van de gebruiker na het logboek van 0.0.16b: optie 1 (geen sprongen meer in een nummer dat al klinkt) en optie 2 (zachte overdracht), en het oude nummer mag iets sneller of trager (tempo-aanpassing blijft tot 8%, de correctie tijdens de mix is nu hoogstens 6% in plaats van 3%).
- Bevindingen uit het log van 0.0.16b: de tweede speler begon 104 en 229 ms eerder in het nummer dan waar de hoofdspeler werd stilgezet (herhaling), de positie van de tweede speler bewoog pas 150 tot 250 ms na `play()`, en in twee van de drie DJ-mixen sprong de hoofdspeler drie keer vooruit (68 tot 388 ms) omdat het nieuwe nummer na elke sprong 120 tot 190 ms opnieuw opstartte; de gemeten opstarttijd werd daardoor ook vervuild (600 naar 173 ms).
- Zachte overdracht (gewone AutoMix en DJ-mix, `CrossfadeController.kt`): de tweede speler start stil, wordt (hoogstens 3 keer) op dezelfde positie als de hoofdspeler gezet, daarna een crossfade van 150 ms van het oude nummer tussen beide spelers; pas daarna springt de hoofdspeler naar het volgende nummer. Geeft de tweede speler het binnen 2,5 s niet op, dan blijft het oude nummer gewoon op de hoofdspeler en volgt de gewone overgang. Bij een fade korter dan 3 s (alleen de gewone crossfade) blijft het oude gedrag. De gewone fade volgt nu de positie van de tweede speler, zodat het oude nummer precies aan het eind van de fade stil is.
- DJ-mix: het oude nummer klinkt na de overdracht op de tweede speler en het nieuwe nummer start pas als de tweede speler `leadMs` voor de maatgrens is. Het nieuwe nummer is stil tot het in de maat is (fout kleiner dan 25 ms); alleen in die stille fase mag de hoofdspeler springen (hoogstens 4 keer), naar waar het nummer na het herstarten moet zijn (verwachte positie plus de gemeten herstarttijd). Daarna komt het in 1 s binnen. Is het na 4 sprongen of na 30% van de overlap nog niet in de maat, dan komt het toch binnen en corrigeert alleen de snelheid van het oude nummer. De opstarttijd (`leadMs`) wordt alleen nog bij de eerste start gemeten, de herstarttijd na een sprong apart.
- Een mislukte mix voor een nummer (tweede speler niet klaar, overdracht opgegeven) wordt voor dat nummer niet opnieuw geprobeerd.
- Logboek `NooklinMix`: overdracht (afwijking, pogingen, duur), sprongen met de fout, "in step" of "comes in anyway".
- Niet gecompileerd en niet beluisterd. Onzeker: of `isPlaying` en de positie van een tweede speler na `seekTo` betrouwbaar genoeg zijn om binnen 25 ms uit te lijnen; of de 200 ms stilstand van de posities bij de start (log 0.0.16b) een echte audiodropout was; of een correctie van 6% hoorbaar is; een deel van de mixen blijft vallen buiten 8% tempoverschil en krijgt de gewone AutoMix.

## 0.0.18
- Op verzoek van de gebruiker (opties 1, 2 en 3 na het logboek van 0.0.17): uitlijnen herschreven, eerder uit het huidige nummer, tempogrens verhoogd. Het eerder stoppen van het huidige nummer is gebouwd; een loop van de laatste maten is NIET gebouwd (een naadloze loop met een knip uit een FLAC-bestand is niet gegarandeerd klikvrij; eerst testen of dit werkt).
- Bevindingen uit het log van 0.0.17: de overdracht zocht in de tweede speler en las daarna een verkeerde positie (stale `READY`/`isPlaying` direct na `seekTo`, positie gelijk aan het zoekdoel): afwijkingen -73, +206, -579 ms, crossfade toch met 215 tot 531 ms afwijking in 4 van de 5 overdrachten. De hoofdspeler had hetzelfde probleem (sprongen met fouten -346, +55, +264, -759 ms) en een herstart na een sprong in een gestreamd nummer duurt ongeveer 850 ms, niet 150 tot 200 ms. Met een aangeleerde lead (800 ms) lag het nieuwe nummer wel direct in de maat (fout -5 en -19 ms).
- Uitlijnen: de tweede speler wordt nu niet meer gezocht, maar `secondaryStartMs` eerder gestart (begin 280 ms, daarna bijgeleerd uit de gemeten afwijking: bij een afwijking `d` wordt de volgende keer `0,7 x d` eerder of later gestart); de afwijking wordt pas gemeten als de tweede speler echt loopt. Meer dan 150 ms afwijking: de soft handover wordt opgegeven en het nummer gaat gewoon gapless door (voor dat nummer niet opnieuw geprobeerd). Hoofdspeler (nieuw nummer): de positie wordt alleen gelezen als hij na de laatste seek echt 20 ms is doorgelopen; beginwaarde van `leadMs` 850 ms en van de herstarttijd 700 ms; een sprong alleen bij een fout boven 60 ms, hoogstens 2 sprongen (alleen in de stille fase); fouten tussen 8 en 60 ms worden met de snelheid van het oude nummer gecorrigeerd (hoogstens 6%); komt het nieuwe nummer na 40% van de overlap nog niet in de maat, of na 60% helemaal niet, dan komt het toch binnen.
- Eerder uit het huidige nummer (`BeatAnalyzer.analyzeSong`, `BeatMixPlanner`): de analyse van het hele nummer geeft nu ook uitstappunten: maatgrenzen waar een luide sectie eindigt en een rustigere volgt (daling van minstens 0,25 over 16 tellen, ervoor luider dan de mediaan, na 40% van het nummer, hoogstens 3, minstens 10 s uit elkaar, gewogen naar de frasegrens). De planner probeert eerst die uitstappunten, daarna het natuurlijke einde (de analyse van de laatste 35 s). De rest van het oude nummer wordt dan niet gespeeld; de overlap moet nog in het oude nummer passen. Het nummer waarvan alleen het einde is geanalyseerd (bijvoorbeeld het eerste nummer na het opstarten) heeft geen uitstappunten.
- Tempogrens van 8% naar 12% (de tijdrek van ExoPlayer behoudt de toonhoogte; bij zulke verschillen kan het hoorbaar zijn).
- Niet gecompileerd en niet beluisterd. Onzeker: of `secondaryStartMs` snel genoeg convergeert (de eerste mix van een sessie kan nog een echo van enkele tientallen ms hebben), of de drempels voor uitstappunten goede plekken geven, hoe 12% tijdrek klinkt, of een uitstappunt midden in het nummer natuurlijk klinkt.

## 0.0.19
- Op verzoek van de gebruiker, na het logboek van 0.0.18: de drie voorgestelde verbeteringen (uitlijnen met snelheid, uitstappunten alleen laat in het nummer, aparte lead per bron). Niet gebouwd: langere overlap (32 tellen), kiesbare curves, loop van de laatste maten.
- Bevindingen uit het log van 0.0.18: de starttijd van de tweede speler schommelde tussen ongeveer 60 en 200 ms, dus een vaste voorsprong die bijleert (280, 221, 106, 188, 100 ms) miste regelmatig; bij een afwijking van 164 en 246 ms gaf de overdracht het op (geen mix, bij een uitstappunt speelde het oude nummer door tot zijn natuurlijke einde, bijna 2 minuten later); een uitstap op 52% van het nummer was waarschijnlijk een breakdown; de beginwaarde van `leadMs` (850 ms) was voor een gedownload nummer te hoog (heard after 208 ms).
- Uitlijnen met snelheid (`CrossfadeController.stepHandover`): de tweede speler start altijd stil en een stukje eerder (begin 200 ms, bijgeleerd met de helft van de eerste gemeten afwijking). Zolang de tweede speler meer dan 30 ms van de hoofdspeler af zit, wordt zijn snelheid aangepast (hoogstens 30%, om de 80 ms, evenredig met de afwijking); daarna start de crossfade van 150 ms. Opgeven pas bij meer dan 1 s afwijking of na 3 s. In de DJ-mix krijgt het oude nummer pas na de overdracht het tempo van het nieuwe nummer.
- Uitstappunten alleen na 60% van het nummer (analyse en planner), gewogen naar de grootte van de daling en naar hoe laat in het nummer (hoe later, hoe beter).
- Lead per bron: beginwaarden gedownload 250 ms en gestreamd 850 ms voor de opstarttijd van het nieuwe nummer, en 250 en 800 ms voor de herstarttijd na een sprong; elk wordt apart bijgeleerd. De controller krijgt een functie die zegt of een nummer gedownload is (`DownloadManager.fileFor`).
- Niet gecompileerd en niet beluisterd. Onzeker: of de positie van de tweede speler tijdens de snelheidsaanpassing betrouwbaar genoeg is om op te regelen (in het vorige log stonden de posities bij de start soms 100 tot 250 ms stil); of een snelheidsaanpassing van maximaal 30% van een stille speler geen bijwerkingen heeft; of 60% voor uitstappunten genoeg is.

## 0.0.20
- Op verzoek van de gebruiker, naar aanleiding van de analyse van autocue, AUTOMIX en Automated-DJ-Mixing-System: opties A (stilte en luidheid aan begin en einde) en C (rustig en druk worden niet op elkaar gemixt, automatisch bepaald uit de audio).
- Analyse (`BeatAnalyzer.analyzeSong`): uit dezelfde luidheidscurve komen nu ook `cueInMs`, `cueOutMs` (eerste en laatste moment waarop de luidheid van 300 ms boven 10% van de mediaan ligt; alleen als het hele nummer gelezen is) en `loudness` (mediaan van de curve). Een nummer zonder duidelijke beat geeft nu ook een kaart met alleen deze waarden (lege in- en uitstappunten) in plaats van niets.
- Stille staart: de overlap (DJ-mix en gewone AutoMix) rekent met het einde van de muziek in plaats van het einde van het bestand, als daar minstens 1 s stilte voor zit. Stil begin: bij de gewone AutoMix begint het volgende nummer bij het begin van de muziek (tussen 0,3 en 20 s). De DJ-mix gebruikt al maatgrenzen in de muziek en blijft zoals hij was.
- Rustig en druk (`CrossfadeController.sameKind`): is het ene nummer rustig (loudness onder 1,6) en het andere druk (boven 2,4), dan geen DJ-mix maar de gewone AutoMix. Ertussenin of onbekend: toegestaan. De waarden staan in het logboek (`loudness ... =`).
- Niet gecompileerd en niet beluisterd. Onzeker: de drempels 1,6 en 2,4 en de stiltegrens zijn geschat zonder meetdata en moeten met de logs worden bijgesteld; of `seekTo(volgend, cueIn)` in de gewone AutoMix de voortgang van de overlap niet verstoort; of de tijd van een stil begin en einde klopt bij nummers met een zachte fade.

## 0.0.21
- Op verzoek van de gebruiker, na het logboek van 0.0.20 en het onderzoek naar DJ-technieken (MusicRadar, MixGraph): de melodie van het nieuwe nummer moet op de beat van het oude lopen, daarna wisselt de beat; en het nieuwe nummer moet betrouwbaar in de maat komen.
- Bevindingen uit het log van 0.0.20 (twee DJ-mixen, beide met een gestreamd nieuw nummer): de zachte overdracht van het oude nummer werkte (afwijking -18 en -17 ms). Het nieuwe nummer kwam niet in de maat: na de `seekTo` midden in een gestreamd bestand duurde het 1087 tot 1638 ms voor het klonk (nieuwe HTTP-range), de sprongen overschoten (fout -414 naar +599 en -218 naar +681), en het nummer kwam met een fout van -445 ms en 85 ms binnen. Dat de oorzaak de variabele opstarttijd van een stream is, is een hypothese uit de tijden. De `IllegalStateException` van `MediaCodec$EventHandler` in het log zijn waarschuwingen (W) bij het vrijgeven van een speler, geen crash. Alle vier de gemeten loudness-waarden lagen tussen 3,2 en 4,4, dus de drempels 1,6 en 2,4 van 0.0.20 sluiten niets uit (geen rustig nummer getest).
- Voorlopen (preroll, `CrossfadeController.stepHold`, `alignMain`): als de plek in het nieuwe nummer niet voor het begin ligt, start de hoofdspeler het nieuwe nummer al stil 1,5 s (gedownload) of 3,5 s (gestreamd) voor de overlap, op de verwachte plek plus de geleerde lead. Terwijl niemand het hoort, wordt zijn snelheid tot 30% aangepast tot het binnen 25 ms van de maat zit (dezelfde regeling als bij de overdracht van het oude nummer, die in het log werkte). Tijdens de overlap alleen nog kleine aanpassingen (tot 3%). Staat het instappunt te dicht bij het begin, dan geldt de oude manier (starten op de maatgrens en bijsturen met sprongen).
- Melodie eerst, dan de beat (`BassCutProcessor` heeft nu drie banden: bas onder 180 Hz, mids 180 Hz tot 3 kHz, highs boven 3 kHz): het nieuwe nummer begint zonder bas en zonder mids, het oude nummer verliest zijn mids tussen 5% en 30% van de overlap (de melodie en zang wisselen), op 50% wisselt de bas, en daarna verliest het oude nummer zijn highs (50% tot 90%).
- Gevolg: de hoofdspeler staat enkele seconden voor de hoorbare mix al op het nieuwe nummer, dus de cover, notificatie en eventuele scrobble springen eerder dan de muziek.
- Niet gecompileerd en niet beluisterd. Onzeker: of de positie van de hoofdspeler tijdens de snelheidsregeling betrouwbaar is, of 3,5 s voorlopen genoeg is voor een stream, of de grenzen 180 Hz en 3 kHz en de verdeling van de overlap goed klinken (de mids van het oude nummer weghalen haalt ook de snare weg), en de drempels voor rustig en druk.

## 0.0.22
- Op verzoek van de gebruiker na het logboek van 0.0.21 ("de overgangen gaan iets beter, maar de melodie loopt nog door of over de andere heen") en de punten A, B en C uit de analyse van het Automix-protocoldocument.
- Bevindingen uit het log van 0.0.21: het voorlopen werkte (een DJ-mix op een gestreamd nieuw nummer: `preroll`, het nummer liep na 813 ms, `in step, error 12 ms, progress 0.0`). Er was in dat log maar een DJ-mix; de andere wissels kregen `no plan` door een te groot tempoverschil (116 naar 132 BPM, 132 naar 87 BPM, dus ratio 1,14 en 1,32) en vielen terug op de gewone overlap van 10 s.
- EQ-schema (`CrossfadeController.stepBeat`): de langzame melodiewissel (5% tot 30%) is vervangen door twee harde wissels op maatgrenzen. Voor 25% heeft het nieuwe nummer alleen zijn highs. Op 25% verliest het oude nummer zijn mids en highs en krijgt het nieuwe nummer zijn mids, dus de melodie van het nieuwe nummer loopt op de kick en de bas van het oude. Op 75% wisselt de bas en is het nieuwe nummer alleen. Volumes: nieuw 0,8 tot 25% (sinus), 0,8 tot 75%, dan naar 1; oud 1 tot 75%, dan cosinus naar 0. De melodie van twee nummers wordt zo niet meer in elkaar geblend, maar het blijft een filter op een gemengd bestand: wat in de bas van het oude nummer meezingt (basloop) en wat onder 180 Hz of boven 3 kHz zit van het nieuwe nummer, klinkt nog door. Echt scheiden vraagt stems.
- Punt C: overlap van 32 beats (acht maten) als er ruimte is, anders 16 of 8 (`BeatMixPlanner`, bovengrens 24 s in plaats van 16 s). Bij 87 BPM is 32 beats ongeveer 22 s.
- Punt A: snede op de maatgrens (`BeatMixPlanner.planCut`) als het tempo niet past: het oude nummer wordt op een uitstappunt of zijn laatste maatgrens verlaten en het nieuwe nummer komt op een instappunt binnen, met een wissel van twee beats (800 tot 2500 ms), zonder tempoaanpassing. Het nieuwe nummer loopt dezelfde voorloop en snelheidsregeling als bij de DJ-mix, zodat het precies op de maatgrens van het oude klinkt. Alleen bas wisselt halverwege. Een rustig nummer naast een druk nummer krijgt nog steeds de gewone AutoMix.
- Punt B: de analyse wordt bewaard in `beatmix.json` in de app-map (`BeatMixStore`), met controle op de lengte van het nummer (2 s) en op een versienummer. Een nummer wordt zo maar een keer geanalyseerd; na een herstart is het uitstappunt van het eerste nummer ook bekend als het eerder is geanalyseerd. Nummers zonder duidelijke beat worden niet bewaard. Er gaat niets naar buiten.
- Niet gecompileerd en niet beluisterd. Onzeker: of de wissels hard genoeg zijn om de melodie echt apart te houden (de bas van het oude nummer bevat ook melodie), of 24 s overlap te lang klinkt, of de snede met twee beats niet te abrupt is, en of een snede tussen bijvoorbeeld 132 en 87 BPM goed klinkt.

## 0.0.23
- Op verzoek van de gebruiker na het logboek van 0.0.22 ("vocals gaan nog dwars door elkaar"), optie 1 uit het onderzoek naar herkenning van zang en melodie: alleen rekenen, geen model.
- Melodiecurve (`BeatAnalyzer.leadOf`): de analyse meet per 10 ms de middenband (200 Hz tot 4 kHz) van het midden en van de zijkanten van het stereobeeld (twee eenpolige filters). Per seconde: het laagste niveau binnen 100 ms (slagwerk is kort, een aangehouden klank niet) maal het aandeel van het midden. Dit is een maat voor "aangehouden geluid in het midden" (zang of lead), geen echte zangdetectie. De seconden van een nummer worden onderling gerangschikt in de cijfers 0 tot 9 (relatief per nummer, geen vaste drempel), bewaard als tekst in `SongMap.lead` en in `beatmix.json` (`VERSION` 2, dus alle bewaarde analyses worden eenmalig opnieuw gemaakt).
- Planner (`BeatMixPlanner.plan`): alle paren van uitstappunt en instappunt die passen worden beoordeeld op `clash`: het gemiddelde van (melodie A x melodie B) over de overlap, per seconde. Het paar met de laagste clash wint; de oude volgorde beslist alleen bij bijna gelijke scores (0,01 per plaats). Zonder curve (oude of mislukte analyse) geldt de oude volgorde. Meer uitstappunten (5 in plaats van 3).
- Melodiewissel op data (`swapOf`): de wissel valt op de eerste maatgrens tussen 15% en 50% van de overlap waarvan de melodie van A in de twee maten daarna stil is (niveau 3 of lager); anders ongeveer een kwart. `BeatPlan.swap` vervangt de vaste `MELODY_SWAP`.
- Planner-fix uit het log van 0.0.22: een uitstappunt kiest nu de langste overlap (32, 16, 8) die nog in het restant van A past, in plaats van afgekeurd te worden omdat 32 beats niet pasten. Daardoor werden eerder uitstappunten op 63 tot 73% gekozen.
- Fix: een plan dat te laat komt (de maatgrens ligt al achter ons) gaf geen mix; nu volgt de gewone AutoMix aan het einde.
- Het logboek toont per paar `clash=` en `swap=` en per nummer de curve (`lead ...`), zodat de drempels met echte data te beoordelen zijn.
- Niet gecompileerd en niet beluisterd. Onzeker: of de curve echt zang volgt (aangehouden instrumenten tellen ook mee), of een lage clash ook beter klinkt, of de wissel van B's hoge tonen (die blijven vanaf het begin) nog zang laat doorklinken. De kalibratie van rustig en druk is nog niet gedaan.

## 0.0.24
- Op verzoek van de gebruiker na het logboek van 0.0.23 (optie 3: stems): "qua vocals en melodie gaat het nog niet goed. Kan dit gesplitst worden van de rest van de muziek?" plus drie opmerkingen: de fade-in duurt te lang met een periode van gek klinkende muziek, het eerste nummer loopt vaak tot het einde, en de zang botst nog.
- Instelling "Separate vocals (stems)" bij Beat matching (uit als standaard, vraagt Beat matching). Het scheidingsmodel is Spleeter (4 stems: vocals, drums, bass, other) als ONNX (`app/src/main/assets/stems/*.fp16.onnx`, 4 x 19,7 MB, van `Best-Practice/spleeter-4stems-onnx` op Hugging Face, Apache-2.0 voor de ONNX-bestanden, de gewichten van Deezer zijn MIT volgens het JOSS-artikel, maar de repository zegt dat zelf onopgelost te zijn: deezer/spleeter#898) en ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android` 1.29.0, MIT). Alles draait op de telefoon; het model zit in de app, er wordt niets gedownload.
- Werking (`playback/stems/`): zodra een beat-mix gepland is en er minstens 25 s voor de overlap is, maakt een achtergrondthread met de laagste prioriteit (`StemTransitions`) de overlap: `PcmReader` leest alleen die stukken van beide nummers (A: 8 s ervoor tot einde overlap; B: overlap), `StemSeparator` splitst ze (STFT 4096/hop 1024, model per stem, ratiomasker op het originele spectrum, dus de stems tellen op tot het origineel), `StemMix` mengt de stems tot een WAV-bestand van circa 20 s in de tijd van nummer A (8 s oorspronkelijk A, dan de overlap, dan 0,6 s alleen B), in `cacheDir/stems`. Volumes (ReplayGain) zitten in het bestand.
- Volgorde in de overlap (p van 0 tot 1): de zang en melodie van het oude nummer gaan eerst weg, op de eerste rustige plek van zijn zang tussen p 0,06 en 0,25 (anders 0,125, uitfaden over 0,125); het nieuwe nummer komt met zijn melodie (other) binnen vanaf de helft van die uitfade en met zijn zang pas als die van het oude weg is; op p 0,75 wisselt de bas (een halve ramp van 0,06) en de drums van het oude nummer gaan weg (tot 0,95) terwijl die van het nieuwe binnenkomen (tot 0,875). B is dus tot p 0,75 zonder eigen beat, de melodie loopt op de beat van A.
- Afspelen: de tweede speler speelt dit bestand in plaats van een kopie van A; de bestaande zachte overdracht van de hoofdspeler naar de tweede speler blijft, alleen met de tijd van het bestand (`songTime`). Het volgende nummer loopt stil op de hoofdspeler vanaf een moment dat het nog 3 s plus de voorloop voor het einde van de overlap heeft, wordt met de snelheid in de maat gebracht (`alignMain`, stil tot 2,5 s voor het einde, daarna alleen kleine veranderingen) en neemt het over van het bestand in een lineaire crossfade van 300 ms (beide zijn dan alleen B). Hierdoor werken instappunten vlak na het begin van B nu ook (geen voorloop nodig aan het begin).
- Gebruikt alleen als: stems aan, het model aanwezig, tempoverschil 1% of minder (de stems worden anders met toonhoogteverschil gelezen), overlap van minstens 7 s, een gewone beat-mix (geen snede), en het bestand klaar voor de overdracht. Anders volgt de bestaande filtermix. Het logboek toont `stems ...` regels met leestijd, scheidingstijd en de keuzes.
- Overige wijzigingen op verzoek: de overlap is nu hoogstens 16 beats (was 32, de "te lange fade-in"); een nummer mag vanaf 40% van zijn lengte verlaten worden (was 60%; het analyseformaat is naar versie 3 gegaan, dus de bewaarde analyses worden eenmalig opnieuw gemaakt); zonder stems worden instappunten binnen 5 s na het begin van B niet gebruikt (daar kon de voorloop niet, en de logs van 0.0.23 toonden mixes 0,4 tot 0,7 s naast de maat).
- Gecontroleerd in deze omgeving (geen telefoon): het Python-voorbeeld van het model en de Kotlin-code geven op 25 s testaudio dezelfde stems (verschil tussen -73 en -107 dB), de stems tellen op tot het origineel (-140 dB); de clip van `StemMix` sluit op het einde exact aan op B (-154 dB) en op het begin op A; `CrossfadeController` en de stems-bestanden zijn gecompileerd tegen android.jar (Android 16), Media3 1.11.1 en ONNX Runtime, met stubs voor de rest. Scheiding van 25 s duurde 2,1 s (modellen) op twee kernen van een server.
- Niet bekend en niet getest: de snelheid op de telefoon (het log toont `stems ... separating X ms`), of ONNX Runtime de fp16-bestanden op ARM goed draait, het geheugengebruik (circa 80 MB werkgeheugen per stuk plus de modellen), de kwaliteit van Spleeter op echte muziek (klank van de zang kan "onderwater" klinken, een deel van de melodie blijft in de andere stems), een verschuiving tussen de decoder van `MediaCodec` en die van ExoPlayer (een paar tientallen ms bij mp3/aac) die bij de overdracht een korte flanger kan geven, de APK-grootte (circa 100 MB), en de batterij. Nog niet gedaan: tempoverschillen groter dan 1% met stems (vraagt tijdrek zonder toonhoogteverschil).

## 0.0.25
- Op verzoek van de gebruiker na het logboek van 0.0.24 ("bouw alles", opties 1 tot 4), getest met Drum & Bass en PsyTrance. Vier opmerkingen: het vertragen of versnellen van A gaat te abrupt, de melodie en de bas van B zijn te laat, haperingen bij het wisselen van nummer (soms ruim voor het einde), en de zang van A stopt abrupt.
- Tempo van A glijdt nu: zonder stems loopt A na de overdracht naar de tweede speler in ongeveer 4 s (smoothstep, stappen van minstens 0,2%) naar `snelheid x ratio`, en pas daarna start B stil (`stepHold`, `rampStartA`/`rampEndA`); de tweede speler wordt daarvoor 4 s eerder gezet (`TEMPO_GLIDE_MS`). Bij een tempoverschil onder 0,2% of een snede is er geen glijbaan.
- Stems bij 2 tot 8% tempoverschil (was 1%): de clip is nu in het tempo van B gemaakt en wordt op normale snelheid afgespeeld (geen tijdrek meer). A wordt in `StemMix.mix` met lineaire interpolatie gelezen: 4 s op eigen tempo, dan 4 s waarin de leessnelheid met een smoothstep van 1 naar de ratio gaat (de toonhoogte volgt, zoals bij een pitch fader; 8% is circa 1,3 halve toon), daarna de overlap met de stems van A op de ratio en die van B op eigen snelheid. De tweede speler start 1 s in de clip (`HANDOVER_AT_MS`), de overdracht gebeurt dus op eigen tempo, en `progress`, de plek van B en de uitlijning komen uit de klokpositie van de clip (`overlapMs`, `fadeWallMs`). Gemeten met een testtoon van 1000 Hz en 5% verschil (zuivere toon, zonder het model): 1000 Hz tot 4 s, 1010 Hz op 5 s, 1030 Hz op 6 s, 1045 Hz op 7 s.
- Snelle tempo's: met stems wordt de kortste overlap 5 s (was 7 s); is 16 beats korter dan 5 s dan kiest de planner 32 beats.
- Melodie en bas van B eerder: bij 16 of meer beats wisselt de bas en de drums op p 0,5 (was 0,75; bij 8 beats blijft 0,75), de melodiewissel van het filterpad ligt dan tussen 15% en 37,5%. De melodie van B komt vanaf een kwart van de uitfade van A binnen, de zang van B bij 60% daarvan (was na de uitfade).
- Zang van A: de uitfade duurt nu 2,5 s (minimaal 0,125 en maximaal 0,3 van de overlap; was 0,125, circa 1 s) en staat op de rustigste plek van zijn zang in het eerste kwart (de eerste onder 30% van het gemiddelde, anders de rustigste).
- Stems eerder en betrouwbaarder: de tijd die het maken van de clip nodig heeft wordt nu geleerd (laatste meting x 1,25 plus 3 s, eerste keer 30 s) in plaats van vast 25 s; 4 draden voor het scheiden (was 2); een beat-mix met een instappunt vlak na het begin van B (onder 5 s) wordt alleen gemaakt als stems mogelijk zijn, anders wordt opnieuw gepland zonder zulke punten, en als de clip dan toch niet op tijd klaar is volgt een gewone AutoMix in plaats van de preroll die 0,4 tot 0,7 s naast de maat viel (de fout van het log van 0.0.24).
- Covers: kopie op schijf (`CoverDisk`, de bytes zoals de server ze stuurde, `cacheDir/covers`, hoogstens 150 MB, de oudste gaan eerst), geheugencache is nu een achtste van het maximale geheugen van de app (16 tot 64 MB), en `largeHeap` staat aan. Onder Instellingen, Downloads staat een rij om de covers te wissen.
- Gecontroleerd in deze omgeving: compileren van `CrossfadeController`, `BeatMixPlanner`, `BeatAnalyzer`, `BassCutProcessor` en de stems-bestanden tegen android.jar, Media3 en ONNX Runtime (met stubs); `StemMix.mix` op 25 s echte testaudio met 5% verschil: geen NaN, de eerste 4 s van de clip zijn identiek aan A (-253 dB, plekken boven 0,85 uitgesloten omdat de limiter die afrondt), en een testtoon liep zoals verwacht.
- Niet bekend en niet getest: de oorzaak van de haperingen bij het wisselen (het log toonde geen onderlopers; `largeHeap`, de covercache en minder aanvragen zijn mogelijke oplossingen, geen bewezen), of elke snelheidsstap van de tweede speler (ook die van de glijbaan, elke 0,2% of meer) in Media3 een hoorbare onderbreking geeft (de verwerking wordt bij een verandering opnieuw ingesteld), de snelheid van het scheiden met 4 draden op de telefoon (het log toont `separating X ms`) en de batterij daarbij, of de tonale verschuiving van A bij 5 tot 8% storend klinkt naast B, of Navidrome het cover-id verandert als een cover wordt vervangen (zo niet, wis dan de covers via de nieuwe rij).
- Gevonden tijdens het testen: het model geeft NaN voor een stuk digitale stilte of een zuivere toon, waardoor de hele overlap daarna stil werd. `StemSeparator.maskAt` telt een niet-eindige schatting nu als 0 (de maskers blijven dan 1/4 per stem); de test met stilte en met een toon geeft nu geen NaN, de test met echte muziek is ongewijzigd.

## 0.0.26
- Op verzoek van de gebruiker na het logboek van 0.0.25 ("nog steeds niet perfect"; keuze A en B) plus Play en Shuffle in de bibliotheek.
- Uit het log van 0.0.25: stems draaiden bij 8 van de 10 wissels met een uitlijning van 2 tot 12 ms bij 6 van de 8; twee keer (11:59:29 en 12:28:36) kwam B diep in het nummer binnen via streamen, liep pas na 1,4 s en haalde de maat niet (`aligned=false`); een snede met instap op 473 ms mislukte de uitlijning (fouten 999 en 377 ms); 4 draden scheiden niet sneller dan 2 (18 tot 20 s in beide gevallen). Geen onderlopers, GC-regels of `Skipped frames` in het log.
- A, snede: `planCut` gebruikt geen instappunten onder 5 s van B meer (`MIN_START_B_MS`), omdat die niet voorbereid kunnen worden.
- A, log: zolang B niet in de maat is schrijft `alignMain` elke 400 ms `aligning: error X ms, position Y`, zodat de oorzaak van een mislukte uitlijning te zien is.
- B, stems: B start tot 2 s voor het begin van de overlap (`STEM_EARLY_MS`, in `stepHold`) in plaats van na het begin, voor zover zijn plaats dan niet voor het begin van het nummer ligt. De clip eindigt nu met 3 s alleen B (was 0,6 s). B neemt over van de clip (crossfade van 300 ms) zodra hij in de maat is en de overlap voorbij is, en uiterlijk aan het einde van de clip; tot dan wordt hij stil bijgestuurd (tot 30%). Daardoor krijgt een traag gestreamd nummer tot ongeveer 5 s extra om in de maat te komen. De clip kost circa 2,4 s B-audio extra om te scheiden.
- Bibliotheek: Play en Shuffle boven de lijsten Songs, Albums (alle soorten albumlijsten: alle, nieuwste, recent, vaakst, favorieten, willekeurig) en Artists. Songs speelt de nummers die geladen zijn (de lijst laadt per pagina; een melding zegt hoeveel als niet alles geladen is), met de zoekopdracht en de sortering als die er zijn. Albums en Artists laden de nummers van de (gefilterde) lijst: albums in volgorde, artiesten met hun albums, hoogstens 50 albums (`PLAY_ALBUM_LIMIT`); bij Shuffle worden de albums of artiesten eerst gehusseld zodat niet steeds dezelfde 50 komen; een melding zegt dat het er maximaal 50 zijn. Terwijl de nummers geladen worden toont de rij een draaiende indicator en zijn de knoppen uit.
- Gecontroleerd: controller, planner en stems gecompileerd tegen android.jar, Media3 en ONNX Runtime (met stubs); `StemMix.mix` opnieuw op echte testaudio (geen NaN, begin identiek). De Compose-schermen (Songs, Albums, Artists, `PlayShuffleRow`) zijn niet gecompileerd omdat de Compose-bibliotheken hier niet beschikbaar zijn; ze zijn met de hand nagelezen.
- Niet bekend en niet getest: of B nu in de maat komt bij diep gestreamde nummers (de nieuwe logregels `aligning:` en `takeover` laten het zien), de volgorde van de nummers bij Shuffle in de albumlijst (de afspeelwachtrij wordt door `playSongs` met shuffle=true gehusseld), en de duur van het laden bij 50 albums (50 aanvragen achter elkaar; bij artiesten tot ongeveer 50 plus één per artiest).

## 0.0.27
- Op verzoek van de gebruiker ("bouw alles") na de logboeken van 13:02 tot 13:39 (psytrance, daarna een genre-mix) en 13:59 tot 14:34 (vooral sneden, tempo's van 75 tot 168 BPM). Gevonden: een stems-clip werd gekozen op het nummerpaar en niet op het plan; om 14:34 werd een oude clip (van een ander plan, bestand al opgeruimd) afgespeeld: `Playback error`, `beat mix skipped: second player state=1`, en dus een overgang zonder fade. Verder: een plan dat een uitgang kiest die al te dichtbij of voorbij is (12:59: uitgang op 182 s terwijl A op 212 s stond; 14:20: `only 37002 ms left, 47018 needed`); twee mislukte uitlijningen bij gestreamde diepe instappunten (snede 14:03, filtermix 14:13 met 9,3%) en een filtermix op 13:31 met 10,1% die stems weigerden (limiet 8%).
- E, clip bij het plan: de sleutel van een clip is nu het nummerpaar plus startpunten en lengte (`stemKey`), en `StemTransitions.clip` geeft null (en vergeet de clip) als het bestand niet meer bestaat.
- A, geen uitgangen te dichtbij: de planner kreeg `minStartA`. Met stems moet de overlap minstens `schatting x snelheid + 9 s` na de huidige positie beginnen (de tijd om de clip te maken plus de aanloop van de clip), zonder stems 9 s (de tijd voor de zachte overdracht); het plan zonder stems wordt gemaakt als er met stems niets past. `planCut` kreeg dezelfde ondergrens. Een plan dat te vroeg komt kiest zo een latere uitgang of het einde van A.
- B, stems tot 12% (was 8%): `STEM_MAX_RATIO_DEVIATION = 0.12`, gelijk aan de limiet van de planner. A wordt dan tot twee halve tonen verschoven (pitch-bend, zie 0.0.25); de tekst in Now Playing zegt 12%.
- C, B eerder bij sneden en filtermixen: de voorloop van B is nu 6 s voor een gestreamd nummer (was 3,5 s) en 2,5 s voor een gedownload (was 1,5 s); een trage start van B telt direct mee in de geleerde aanloop (bij een snelle start nog de helft); instappunten onder 8 s van B worden zonder stems niet gebruikt (was 5 s, omdat de voorloop langer is).
- D, schatting: de schatting voor het maken van een clip is hoogstens 50 s.
- Gecontroleerd: controller, planner en stems zonder compilerfouten (met stubs). Niet getest op de telefoon.
- Niet bekend: of de langere voorloop de mislukte uitlijningen bij gestreamde nummers oplost (de `aligning:` regels laten het zien), hoe een toonverschuiving van 8 tot 12% klinkt, en of het niet meer gebruiken van instappunten onder 8 s (zonder stems) in sommige liedjes tot een gewone AutoMix leidt.

## 0.0.28
- AirPlay-scherm in de stijl van het iOS-blad (`ui/playback/AirPlayDialog.kt`): afgeronde kaart (28 dp), rij "This phone" bovenaan en de gevonden speakers eronder, een vinkje bij wat actief is, een draaiend rondje tijdens verbinden. "This phone" is gekozen zolang er geen speaker verbonden is; tikken erop verbreekt alle speakers (`stopAll`), een speaker kiezen maakt de telefoon stil zoals eerder. Het volume van een verbonden speaker staat in een afgeronde pil die van links vult (tikken of slepen), per speaker onthouden zoals eerder. De knop "Stop AirPlay" is vervallen (dat doet "This phone"). De PIN-dialoog is niet gewijzigd.
- Niet gecompileerd en niet op de telefoon gezien: de Compose-code (Dialog, Surface, `detectTapGestures`, `detectHorizontalDragGestures`, de icoon `Rounded.PhoneAndroid`).
- Projectdocs Music/SCOPE.md en Music/TODO.md bijgewerkt tot 0.0.28.
- "Andere functies" (loop, curves, harmonisch mixen, equalizer, Hi-Res, Mixes, prioriteitswachtrij, Android Auto) zijn niet gebouwd: de eisen zijn niet vastgelegd en de projectregel staat alleen afgeronde functies toe. Zie TODO.

## 0.0.29
- Op verzoek van de gebruiker, na een lijst met opties (antwoorden: 1 ook onderliggend, 2 pill, 3 optie A, 4 pills goed leesbaar, 5 Shuffle en Loop bij vorige, afspelen en volgende).
- AutoPlay: "Auto DJ" heet overal AutoPlay, ook in de code (`autoPlay`, `setAutoPlay`, `AUTO_PLAY_KEY`). De opgeslagen sleutel blijft `auto_dj`, anders raakt een bestaande instelling of back-up kwijt. AutoPlay aanzetten zet shuffle en herhalen uit (het werkt alleen zo).
- Library: Play en Shuffle staan als pil met twee iconen in de kop van Songs, Albums en Artists, links van Refresh (`PlayShufflePill`, `PlayTarget`, parameter `actions` in `SearchableFrame`). De rij onder de zoekbalk is vervallen. De pil is gedimd zolang er niets te spelen is; tijdens zoeken is de kop weg en dus ook de pil.
- AutoMix: in het blad van de speler blijft alleen de schakelaar AutoMix. Beat matching en Separate vocals (stems) staan in Settings, Playback, Advanced AutoMix. Standaard staan beide aan (waar de gebruiker ze nog nooit instelde). Met beide uit is AutoMix de oude gewone overlap van ten hoogste 10 s (optie A).
- Zwart: in de modus Black zijn alle surface-rollen zwart (surface, dim, bright en alle containers); alleen `surfaceVariant` (pillen, velden) is 0xFF1C1C1E voor leesbaarheid.
- Up Next: onder de zoekbalk een rij met Shuffle, vorige, afspelen of pauze, volgende en herhalen (uit, alles, een) en daaronder pillen AutoPlay en AutoMix. De knoppen Play en Shuffle van dat scherm zijn vervallen; `shuffleAndPlayNext` in de ViewModel wordt nu niet meer gebruikt (niet verwijderd).
- Niet gecompileerd (Compose). Niet bekend: hoe de kop eruitziet met titel, pil, Refresh en "..." op een smal scherm; of er in Black mode nog grijze vlakken zijn die niet via het kleurschema lopen (vaste kleuren in code zijn niet doorzocht); of AutoPlay met shuffle en herhalen terug aan niets doet (de gebruiker kan ze weer aanzetten).

## 0.0.30
- Log van 14:44 tot 15:23: elke mix met stems of filters lag op 7 tot 12% tempoverschil (verhoudingen 0,88 tot 1,116); de laatste overgang was 75 naar 83 BPM, dus A werd 11,6% versneld (bijna twee halve tonen hoger). De uitlijning en overdracht werkten zoals bedoeld (`takeover`, `aligned=true`).
- Op keuze van de gebruiker (6%, de gewone bereik van een draaitafel): `MAX_TEMPO_DIFF` in de planner en `STEM_MAX_RATIO_DEVIATION` in de controller zijn 0,06 (was 0,12). Paren met meer verschil worden een snede op de maat. De tekst in Settings zegt 6%.
- Niet bekend: hoeveel paren nu een snede worden; of 6% goed klinkt; of een verdubbeld of gehalveerd tempo (bijvoorbeeld 75 en 150 BPM) wordt herkend (niet onderzocht).

## 0.0.31
- Correctie op 0.0.29: ik had de knoppen in het verkeerde Up Next gezet. Het scherm Up Next (Home, `UpNextScreen`) is hersteld zoals in 0.0.28 (Play en Shuffle, zoekveld, pil met Download, Save as playlist en Filter).
- Het Up next-paneel in de speler heeft nu onder de kop een rij met Shuffle, herhalen (uit, alles, een, op dezelfde knop), en de pillen AutoPlay en AutoMix. Shuffle en herhalen zijn uit de bedieningsrij van de speler gehaald; daar staan alleen vorige, afspelen of pauze en volgende.
- Niet gecompileerd. Niet bekend: hoe het paneel er in de hoes-kleuren uitziet.

## 0.0.32
- Toonsoort (harmonisch mixen, alleen afkeuren): `KeyDetector` (playback/beat) maakt uit elk nummer een toonsoort: van elk derde blok van 16384 samples (onderdeel van de bestaande analyse, geen extra decodering) wordt het spectrum van 100 Hz tot 2 kHz op de 12 noten gezet, opgeteld, en vergeleken met de profielen van Krumhansl voor groot en klein. Alleen 12 getallen, nooit audio. Een toonsoort geldt alleen als hij goed past (0,45) en duidelijk beter dan de volgende (0,03); anders onbekend (-1) en dan wordt er niets afgekeurd.
- Bewaard in de analyse: bij het einde van A (de toonsoort van het stuk dat meespeelt) en bij het hele nummer (voor B). De versie van het opgeslagen bestand is 4: de analyse van eerder gespeelde nummers wordt opnieuw gedaan als ze weer aan de beurt zijn.
- Planner: de afstand op de kwintencirkel (0 tot 6; groot en zijn relatieve klein tellen als een plek). Met stems volgt de toonhoogte van A de tempoglide, dus de toonsoort van A wordt eerst met de afgeronde halve tonen van de verhouding verschoven. Afstand 4 of meer: dat paar wordt niet gemengd (een ander instappunt of een snede). Kleinere afstanden krijgen een straf in de score (0,1 per stap) naast de botsing van de melodieen.
- Test (hier, niet op de telefoon): synthetische akkoordreeksen in 24 toonsoorten: 22 goed, 1 onbekend, 1 relatieve toonsoort (afstand 0, ongevaarlijk). Op het echte 25 s-fragment uit de stems-tests: twee keer dezelfde uitkomst (A groot); de juiste toonsoort is mij niet bekend.
- Up next-paneel in de speler opgeruimd: vier gelijke knoppen (Shuffle, Repeat, AutoPlay, AutoMix) met een klein icoon (20 dp) boven een korte tekst, 52 dp hoog, 8 dp ruimte ertussen en 16 dp ruimte onder de rij voor de lijst.
- Niet bekend: hoe vaak de toonsoort op jouw muziek goed is (elektronische muziek zonder akkoorden geeft vaak onbekend, wat veilig is); hoeveel paren door afstand 4 nu een snede worden; het uiterlijk van de vier knoppen (Compose niet gecompileerd). Controller, planner en analyse zijn met stubs zonder fouten gecompileerd; `BeatMixStore` zelf niet (alleen nagelezen).

## 0.0.33
- Op verzoek van de gebruiker: Now Playing Auto-Radio (punt 1) en Discovery Station (punt 3). Radio (More Like This, punt 4) is overgeslagen op verzoek: de bestaande Radio (0.0.11) blijft zoals hij is. Opdracht: gebruik alle beschikbare data van de server en meld terug aan de server bij like, skip enzovoort.
- Smaakprofiel op de telefoon (`playback/taste/TasteStore`, bestand `taste.json`, hoogstens 3000 nummers): per nummer hoe vaak het uitgespeeld of geskipt is, met artiest en genre. Een nummer dat vanzelf eindigt, of na meer dan de helft (hoogstens 4 min) wordt overgeslagen, telt als uitgespeeld; een skip eerder telt als skip. Nummers onder 30 s en zoeken tellen niet. Niet bij de Privémodus. Het genre komt via `getSong` (nieuw: `Song.genre`, `Song.plays`).
- Wat de server wel en niet kan: harten, sterren (`setRating`), afspelen (scrobble na de helft) gaan al naar de server; nieuw: het nummer dat nu speelt wordt gemeld (`scrobble` met `submission=false`). Een skip kent Subsonic of Navidrome niet; skips blijven alleen op de telefoon. Er wordt niets automatisch gehart of beoordeeld.
- AutoPlay (`TasteEngine.forAutoPlay`): als het laatste nummer speelt, worden 10 nummers toegevoegd uit `getSimilarSongs2` van dat nummer en van het laatst uitgespeelde nummer, geordend op hart (+3), sterren (4 of 5 +2, 1 of 2 min 2), uitgespeeld en geskipt per artiest en per nummer, met een beetje toeval, hoogstens twee per artiest, niet wat de laatste 50 keer speelde, niet artiesten die minstens twee keer en vaker geskipt dan uitgespeeld zijn. Willekeurig als de server niets vergelijkbaars heeft.
- Discovery Station (balk op Home onder de zoekbalk, tik om te starten of te stoppen): nummers uit je eigen bibliotheek die je nog niet speelde (`playCount` van de server als dat meekomt, anders het smaakprofiel), uit de drie genres met de meeste uitgespeelde nummers en hartjes, plus een naburig genre (het genre dat het vaakst voorkomt bij de nummers die lijken op de laatste uitgespeelde). Zonder geschiedenis: drie genres van de server. Een skip verlaagt genre en artiest, uitgespeeld, hart verhogen. Als er nog twee of minder nummers volgen, komen er 8 bij (hoogstens elke 30 s een poging). Elk ander afspelen vanuit de app zet het station uit. AutoPlay doet niets zolang het station aan staat. Muziek van buiten je server kan het niet vinden.
- Settings, Playback: tekst over wat er bewaard wordt en "Forget what was learned".
- Niet gecompileerd (service en UI). TasteStore en TasteEngine zijn met stubs gecompileerd. Niet bekend: of Navidrome `playCount` en `genre` in `getRandomSongs` meestuurt; of de reden `DISCONTINUITY_REASON_SKIP` en `AUTO_TRANSITION` ook kloppen bij de overgangen van AutoMix (kijk in het log naar `NooklinTaste: played through` en `skipped`); of de `genre`-parameter van `getRandomSongs` in jouw Navidrome werkt; hoe goed de keuze op jouw muziek is.

## 0.0.34
- Speler opgeruimd, op verzoek van de gebruiker (lijst, daarna "doe in het ... menu, bouw alles"): onderste rij heeft nu drie iconen: Lyrics links, AirPlay in het midden, Up next rechts. Weg zijn de sluitpijl (sluiten kan met omlaag vegen en met terug), het tandwiel en het maantje.
- Het "..." menu van de speler (op de hoes en bij de songteksten) heeft er twee items bij: Playback speed (blad met de snelheden) en Sleep timer (hetzelfde blad als voorheen).
- Het instellingenblad van de speler is vervallen. Naar Settings, Playback verhuisd: Skip silence, Crossfade, Smart Fades en ReplayGain (zelfde opslag, dus de waarden blijven). AutoMix en AutoPlay staan in het Up next-paneel van de speler; Advanced AutoMix staat al in Settings, Playback.
- Niet gecompileerd (Compose). Niet bekend: hoe het "..." menu er met twee extra items uitziet, en of iemand de sluitpijl mist (het sluiten met een veeg blijft bestaan).

## 0.0.35
- Home, op verzoek van de gebruiker (plan, daarna antwoorden: 1 per week met een eigen dag, 2 kleurvlak met icoon, 3 losse nieuwe nummers, 4 eerst server dan analyse; "alles bouwen"). De zoekbalk is weg (de zoekknop rechtsonder blijft). De Discovery Station-balk is een kaart geworden.
- Bovenste rij (`DiscoveryRow`, zijwaarts scrollend, kaarten van 150 dp met een gekleurd vlak, icoon en korte tekst): Discovery Station (tik om te starten of te stoppen), Favourite songs, Featuring Artist, New Release. Daaronder "Playlists made for you" (`MixesRow`): Heavy Rotation, Your Essentials, Get Up!, Chill, New Music. Een kaart verschijnt pas als de lijst gebouwd is. Tik speelt de lijst (zonder shuffle); er is geen eigen lijstscherm.
- Elke lijst wordt een keer per week opnieuw gemaakt op zijn eigen dag (`HomeMixes`, bestand `mixes.json`): maandag Favourite songs, dinsdag Featuring Artist, woensdag New Release, donderdag Heavy Rotation, vrijdag New Music, zaterdag Get Up!, zondag Chill en Your Essentials (acht lijsten, zeven dagen). Een lijst is aan de beurt als zijn dag sinds het bouwen is gepasseerd; tussendoor blijft hij staan. Opgeslagen met ReplayGain, zodat het volume klopt.
- Favourite songs: 25 willekeurige hartjes (`getStarred2`). Featuring Artist: de artiest met de meeste uitgespeelde nummers van de afgelopen zeven dagen (smaakprofiel, zonder meer skips dan plays), anders een artiest van je hartjes, anders willekeurig; 10 nummers van die artiest en 4 van elk van 3 vergelijkbare artiesten (`getArtistInfo2`), de eerste 3 van de artiest zelf. New Release: een willekeurig nummer uit de 10 laatst toegevoegde nummers (Navidrome `/api/song` gesorteerd op `createdAt`; werkt dat niet dan de nummers van de nieuwste albums).
- Heavy Rotation: de 30 meest gespeelde nummers (Navidrome `playCount`). Your Essentials: hartjes plus nummers met 4 of 5 sterren, op hart, sterren en afspeelaantal. New Music: de 100 laatst toegevoegde nummers, de nog niet gespeelde eerst, 30 stuks. Get Up! en Chill: uit de genres van de server met passende namen (dance, house, rock, metal enzovoort tegenover ambient, jazz, classical enzovoort); nummers die al geanalyseerd zijn (tempo en luidheid) worden daarop gesorteerd en een nummer dat de verkeerde kant op is geanalyseerd valt af, dus de lijsten worden beter naarmate meer nummers geanalyseerd zijn.
- Niet gecompileerd (Compose en LibraryViewModel). `HomeMixes`, `TasteStore` en `TasteEngine` zijn met stubs gecompileerd. Niet bekend: of `/api/song` sorteert op `createdAt` in jouw Navidrome; of de server vergelijkbare artiesten geeft (anders blijft Featuring Artist bij die ene artiest); de genre-lijsten voor Get Up! en Chill zijn mijn keuze en passen mogelijk niet bij jouw bibliotheek; de iconen `Bolt`, `Spa`, `LocalFireDepartment`, `NewReleases` zijn niet gecontroleerd; nog geen instelling in Settings, Home om de nieuwe rijen uit te zetten.

## 0.0.36
- Home: de bovenste rij en "Playlists made for you" zijn nu twee blokken (`HomeSection.DISCOVERY` en `MIXES`) in Settings, Home. Ze hebben een schakelaar en pijltjes omhoog en omlaag, net als de andere blokken, en kunnen overal tussen de albumrijen staan. Bij een bestaande opslag komt een nieuw blok op zijn standaardplek (bovenaan), aan.
- Opgeruimd in het hele project: ongebruikte imports (NowPlayingScreen, UpNextScreen, HomeScreen, StemSeparator, TransientPairVerify) en ongebruikte functies en waarden (`GlassPill.fixedPopupBackground` en `destructiveText`, `CROSSFADES`, `BassCutProcessor.snap`, `AirPlayController.clearMessage`, `CredentialStore.pairedCount`, `pairedDevices` en `clearAll`, `TransientPairVerify.establishSharedSecret`). Bewust laten staan: `Srp6aClient.verifyServerProof` (beveiligingsstap) en `RtspSession.rawResponse` (veld van een datatype). Niet gecompileerd.

## 0.0.37
- De weekmixes zijn nu echte afspeellijsten op de server (op verzoek, optie A). `HomeMixes.publish` schrijft elke mix na het bouwen naar Navidrome met `createPlaylist` (een bestaand `playlistId` vervangt de nummers, het id blijft) en zet daarna met `updatePlaylist` het commentaar `nooklin-mix:HEAVY` (na een `|` een regel voor onder de titel: de artiest bij Featuring Artist, het nummer bij New Release). Zo wordt een lijst teruggevonden, ook na hernoemen. Is de lijst verwijderd, dan wordt hij de volgende keer opnieuw gemaakt. Een lijst wordt alleen opnieuw geschreven als de mix nieuwer is dan de vorige schrijfbeurt (`published` in `mixes.json`: id en bouwtijd). Lukt het niet (offline), dan volgt een nieuwe poging bij de volgende keer.
- Home: de kaarten zijn de afspeellijsten zelf, met de cover van de server, 150 dp vierkant zoals de albumkaarten, de titel op een regel en daaronder maximaal twee regels (artiest, nummer of aantal nummers). Een tik opent het gewone playlist-scherm. Alleen Discovery Station blijft een kaart met icoon, want dat is een radio. De uitleg-teksten, kleuren en iconen per mix zijn weg.
- Playlists: twee groepen. "Made for you" bovenaan in vaste volgorde, met onder de naam de dag van de update; daaronder "Playlists" volgens de gekozen sortering, in lijst en raster. Zoeken werkt in beide groepen.
- Let op: de app schrijft nu naar je server (acht lijsten, elke week overschreven). Wijzig je zo'n lijst zelf, dan wordt dat bij de volgende weekupdate overschreven.
- Niet bekend: of Navidrome bij `createPlaylist` met `playlistId` de nummers vervangt (gelezen in de broncode via een samenvatting, niet gedraaid); of `getPlaylists` het commentaar teruggeeft voor een client als deze (anders val ik terug op het bewaarde id); of de cover meteen meeverandert na vervangen. Niet gecompileerd: Compose en `LibraryViewModel`; `HomeMixes` is met stubs gecompileerd.

## 0.0.38
- De afspeellijsten die voor je gemaakt zijn (de weekmixes) tonen een eigen afbeelding in plaats van de cover van de server: op Home en in de Playlists-lijst en het raster. Het scherm van de lijst zelf toont nog de achtergrond uit de hoezen van de nummers. Vijf afbeeldingen (paars, rood/zwart, oranje/geel, blauw/groen, roze) staan als `res/drawable-nodpi/mix_*.jpg` (JPEG, rond 28 KB per stuk), de keuze per lijst in `ui/library/PlaylistCover.kt`: Favourite songs roze, Featuring Artist paars, New Release oranje, Heavy Rotation rood, Your Essentials roze, Get Up! oranje, Chill blauw/groen, New Music paars. Buren in een rij op Home hebben nooit dezelfde afbeelding. Andere afspeellijsten houden hun servercover.
- Niet gecompileerd (Compose).

## 0.0.39
- Favourite songs toont een eigen afbeelding (het hart, `mix_heart.jpg`), Featuring Artist een andere (`mix_artist.jpg`) en New Release heeft geen afbeelding meer: die toont de cover van de server. De rest is zoals in 0.0.38. `mix_pink.jpg` blijft in gebruik voor Your Essentials. Niet gecompileerd (Compose).

## 0.0.40
- Op verzoek van de gebruiker, opties 1 en 2 uit de lijst over de zang in de overgang (optie 3 was het advies, optie 4 niet gebouwd).
- Optie 1, zang van A op een zinseinde (`stems/VocalGate.kt`, `chooseOut`): de energie van A's zang-stem wordt per frame gemiddeld over 5 frames. Een pauze is een stuk van minstens 0,4 s onder 4% van het gewone zangniveau (gemiddelde van het luidste kwart). Van alle pauzes (die niet na 85% van de overlap beginnen) wint de pauze het dichtst bij 12,5% van de overlap (de oude vaste plek); de zang van A fade daar in 0,3 s weg, vanaf het begin van de pauze. Is er geen pauze, dan dezelfde fade van 0,3 s op de maatgrens het dichtst bij 12,5% (niet de eerste maatgrens; de lengte in tellen komt uit het plan, `StemClipSpec.beats`). Staat de lengte niet vast: op 12,5%.
- Optie 2, zang van B op een zinsbegin (`chooseIn`): de zang van B blijft stil tot A's zang helemaal weg is (plus 20 ms) en dan tot het eerste einde van een pauze van minstens 0,3 s in B's zang, gevolgd door zang (boven 15% van het niveau binnen 0,4 s). De zang van B fade daar in 0,15 s in, 0,1 s voor het gedetecteerde begin. Is er geen zinsbegin in de overlap, dan komt B's zang aan het einde van de overlap binnen (uiterlijk, zodat de overname door het echte nummer geen sprong in de zang geeft).
- Niet veranderd: de melodie (`other`) van A en B volgt het oude schema (uitfade van 2,5 s op de rustigste plek van A's zang in het eerste kwart, `chooseOut` in `StemMix`). De bas, drums en tempo-glijbaan zijn niet aangeraakt. De volgorde bij het maken van de clip is gelijk gebleven (A scheiden, A renderen, B scheiden, B renderen), dus het geheugengebruik ook.
- Gecontroleerd in deze omgeving (geen telefoon): de hele `StemMix.mix` met het echte Spleeter-model (alle vier de ONNX-bestanden) en ONNX Runtime op de JVM, op een fragment van 11 s uit `audio_example.mp3` van het Spleeter-project (drie keer achter elkaar, A en B op andere plekken). Twee overgangen: A met zang tot 76% van de overlap en B midden in een zin (geen pauze voor het einde): zang van A weg op 77% (pauze), zang van B op 98% (uiterlijk). A met zang tot 26%, B met een pauze en daarna een zin: zang van A weg op 27%, zang van B op 65%. Het ontstane bestand is daarna opnieuw gescheiden: zang van A klinkt tot de pauze, daarna stilte, dan de zang van B op 13,3 s in het bestand (verwacht 13,2 s). Achttien eenheidstesten van `VocalGate` op synthetische zangcurves (geen pauze, korte pauze, pauze die al loopt, te late pauze, ruis, geen zang, lege reeksen): alles goed.
- Niet bekend en niet beluisterd: of het klinkt zoals bedoeld; of de drempels (4%, 15%, 0,4 s en 0,3 s) op jouw muziek goede pauzes geven (Spleeter lekt andere klanken in de zang-stem, dat kan een pauze verbergen of een zinsbegin te vroeg melden); of A's zang, die nu langer blijft staan dan voorheen, over de melodie van B heen botst (A's melodie gaat nog op het oude schema weg); of een zinsbegin na de wissel van de bas en drums (50%) te laat voor B klinkt. Het logboek `NooklinMix` toont per overgang `its vocals at ... (pause|bar|target)` en `its vocals come in at ... (phrase|latest|gate)`.
- De tekst in Settings, Playback bij Separate vocals ("The vocals of the old song go first") is niet aangepast en klopt nu niet precies meer (aangepast in 0.0.45).

## 0.0.41
- Op verzoek van de gebruiker: Music-icoon (plaatje van de gebruiker, twee kaarten op geel) en Settings in vier blokken.
- Icoon: het plaatje staat als `res/drawable-nodpi/ic_launcher_art.png` en is de voorgrond van het adaptieve icoon, met 12% rand (`ic_launcher_foreground.xml`, een inset) zodat de kaarten niet door het masker worden afgesneden; achtergrondkleur #E4AF28 (gemiddelde van de plaatjesrand). Het monochrome (thema-)icoon is weggelaten: een kleurenplaatje geeft daar een vlek. Niet gezien op een toestel en niet gecompileerd.
- Settings (`SettingsScreen.kt`): vier blokken zonder kopjes: Server; Appearance, Home; Playback, Downloads; Backup, About.

## 0.0.42
- Icoon vervangen door de nieuwe versie van de gebruiker (zelfde bestand `ic_launcher_art.png`, rest van het icoon ongewijzigd). Niet gezien op een toestel.

## 0.0.43
- Icoon op de telefoon te ver ingezoomd: de rand rond het plaatje in `ic_launcher_foreground.xml` is 22% (was 12%). De kaarten nemen nu ongeveer 45% van het icoon in. Niet gezien op een toestel.

## 0.0.44
- Op verzoek van de gebruiker: opties 1 en 4 uit het onderzoek naar mixen op toonsoort (optie 2 en 3 niet gebouwd).
- Optie 1, betere herkenning (`KeyDetector`): de profielen van Essentia voor elektronische dansmuziek (`edma`: groot, klein en een derde profiel voor nummers die geen van beide zijn, gerapporteerd als de kleine toonsoort van die noot; waarden overgenomen uit `key.cpp` van Essentia) beslissen nu, in plaats van die van Krumhansl. De toonsoort van een noot telt het beste van zijn profielen, zodat klein en het derde profiel elkaar niet als tweede keuze in de weg zitten. De drempels (pasvorm 0,45, voorsprong 0,03) zijn niet veranderd: er waren geen meetwaarden om ze op te baseren. De chroma-berekening is ook niet veranderd (die verschilt van die van Essentia).
- Optie 4, meten: elke analyse (einde van een nummer en heel nummer) schrijft in het log `key ... (edm X fit F margin M next Y, krumhansl Z fit F margin M, N blocks)`, dus de pasvorm en voorsprong van beide profielensets en de tweede keuze. De planner schrijft per planning `key summary: A ..., B ..., n of m pairs not used because of the key, chosen pair is d apart` (of dat een toonsoort onbekend is).
- Het opslagformaat van de analyses is naar versie 5 gegaan: opgeslagen analyses worden eenmalig opnieuw gemaakt (een hele analyse duurde in het laatste log 50 tot 75 s).
- Gecontroleerd in deze omgeving: synthetische akkoordenreeksen (I IV I V) in 24 toonsoorten: beide profielensets 23 van 24 goed (een keer onbekend bij edm, een keer fout bij Krumhansl); met een reeks I IV V I vinden ze meestal de dominant (dat is de reeks, geen fout van de profielen), waarbij edm 10 van 24 goed had en 7 keer onbekend gaf, Krumhansl 14 van 24 goed. Dit zegt niets over echte dansmuziek. Op het echte fragment van 11 s (drie keer achter elkaar) geven beide A klein. Detector gecompileerd en gedraaid op de JVM; `BeatAnalyzer`, `BeatMixStore` en `BeatMixPlanner` zijn niet gecompileerd (alleen aangepast met kleine toevoegingen).
- Niet bekend: of de EDM-profielen op jouw muziek beter zijn dan die van Krumhansl (de claim komt uit de Essentia-documentatie, niet van een vergelijking met mijn chroma); of de drempels bij de nieuwe profielen meer of minder nummers onbekend laten. Het log laat dat zien.

## 0.0.45
- Op verzoek van de gebruiker: toonsoortdrempel verlaagd en de tekst bij Separate vocals aangepast. ALAC is bewust niet gebouwd (de app speelt .m4a met ALAC niet af: Android heeft er geen decoder voor; het log van 17:25 tot 17:31 toont voor dat nummer geen decoder).
- `KeyDetector`: pasvorm minstens 0,35 (was 0,45) en voorsprong minstens 0,05 (was 0,03). Gebaseerd op vier metingen uit het log (heel nummer: 7zFJ pasvorm 0,39 voorsprong 0,10 en 55n0 pasvorm 0,39 voorsprong 0,08 worden nu herkend; 1JTi pasvorm 0,55 voorsprong 0,01 blijft onbekend). Er zijn geen echte toonsoorten om mee te vergelijken, dus of de uitkomsten kloppen is niet gecontroleerd; een lagere drempel geeft meer herkende maar ook meer foute toonsoorten.
- `BeatMixStore.VERSION` 6: opgeslagen analyses worden eenmalig opnieuw gemaakt, anders houden bestaande nummers hun oude (onbekende) toonsoort.
- Settings, Playback: de tekst zegt nu dat de zang van het oude nummer op het einde van een zin stopt en die van het volgende nummer op het begin van een zin komt.
- Niet gecompileerd (alleen getallen en een tekst aangepast).

## 0.0.46
- Op verzoek van de gebruiker: Shuffle in Songs echt willekeurig, en Play en Shuffle in Favorites en Genres.
- Oorzaak van het oude gedrag: Songs laadt per pagina van 200 in de volgorde van de server (`search3`), en Shuffle schudde alleen die geladen nummers, dus 200 nummers uit het begin van de bibliotheek, album na album.
- Songs (`SongsScreen`): zonder zoekterm vraagt Shuffle de server om 200 willekeurige nummers (`getRandomSongs`, `LibraryViewModel.randomSongs`, `SHUFFLE_SONG_COUNT` 200). Met een zoekterm schudt Shuffle nog steeds de gevonden nummers. Play is niet veranderd.
- Favorites: Play en Shuffle in de kop spelen de favoriete songs (de nummers met een hartje, niet de nummers van favoriete albums of artiesten). Grijs zolang er geen favoriete songs zijn.
- Genres (lijst met genres): Play speelt de nummers van de albums van de genres in de volgorde van de lijst, tot 50 albums (`songsOfGenres`, `PLAY_ALBUM_LIMIT`; bij een grote eerste genre dus alleen dat genre). Shuffle speelt 200 willekeurige nummers uit de hele bibliotheek (niet beperkt tot genres: een nummer zonder genre kan ook komen). Het scherm van een enkel genre (de albums van een genre) heeft geen knop.
- `LibraryScaffold` kreeg een optionele `actions` voor knoppen links van Refresh; `GenresScreen` kreeg `onPlay`.
- Niet gecompileerd en niet op een toestel geprobeerd (Compose en Android). Niet bekend: of jouw Navidrome `getRandomSongs` met 200 aankan zonder te falen (de app gebruikt het al voor mixen met kleinere aantallen).

## 0.0.47
- Op verzoek van de gebruiker: Shuffle in Songs en Genres vraagt 50 willekeurige nummers (`SHUFFLE_SONG_COUNT`, was 200); AutoPlay vult daarna zelf aan. Verder niets veranderd. Niet gecompileerd.

## 0.0.48
- Op verzoek van de gebruiker: overgangsrapport in het log (optie 3 uit de analyse van het mixsysteem). Er is verder niets aan het mixen veranderd.
- `CrossfadeController`: als een overgang eindigt (`endFade`) staat er één regel `transition report A>B: <soort>, <plan>, <toonsoorten>, handover ..., <uitlijning>, outcome ... after N ms`. Soort: stems, cut, beat mix with filters of plain crossfade. Plan: tempoverhouding, lengte van de overlap in ms en in tellen. Toonsoorten: naam en afstand op de kwintencirkel (of `not judged` als er een onbekend is). Handover: hoe lang de tweede speler erover deed, de eerste afwijking en wanneer hij in step was. Uitlijning: of het volgende nummer in step kwam, met de fout in ms, het aantal sprongen en de voortgang, of dat het toch binnenkwam zonder in step te zijn. Outcome: complete, handover gave up, handover too far apart, song changed from outside, second player gone or failed, no plan, second player ended early, clip ended early.
- De bestaande logregels zijn niet veranderd (de uitlijningsregels gaan nu via `alignNote`, met dezelfde tekst).
- Geen regel voor een overgang die niet start (bijvoorbeeld `plain mix skipped`): die staan al apart in het log. Niet gecompileerd en niet op een toestel geprobeerd.

## 0.0.49
- Op verzoek van de gebruiker, na het onderzoek naar automatische DJ-overgangen (Ishizaki e.a. ISMIR 2009, Bittner e.a. ISMIR 2017, Cliff HP Labs 2005, DJ-praktijk): "bouw alles wat nodig is voor de beste DJ mix". Drie onderdelen, alle drie niet gecompileerd en niet op een toestel geprobeerd.
- A, volgorde (`MixOrder`, nieuw): met AutoMix en beat matching aan is Shuffle (Songs, Genres, Favorites, alle `playSongs` met shuffle) geen willekeurige volgorde van de speler meer maar een volgorde waarin elk nummer past bij het vorige: willekeurige start, dan telkens een van de beste drie volgende nummers op tempo (half en dubbel tellen mee, 10% verschil telt 1), toonsoort (afstand op de kwintencirkel / 6), luidheid en een beetje genre. De speler zelf staat dan niet op shuffle (anders zou die de volgorde ongedaan maken); de shuffleknop staat dus uit. AutoPlay sorteert de 10 toegevoegde nummers op dezelfde manier, beginnend bij het nummer dat nu speelt. Wat niet bekend is (geen analyse, geen tempotag) telt als neutraal: een bibliotheek die weinig is geanalyseerd blijft dicht bij willekeurig, en wordt beter naarmate je meer speelt. Het log schrijft `order: N songs, tempo known for X, key for Y, loudness for Z`.
- A, tempotag: `Song.bpm` uit het veld `bpm` van de server (OpenSubsonic en Navidrome-API), 0 als dat ontbreekt. Wordt alleen gebruikt als er geen eigen analyse is. Niet bekend: of jouw bestanden een BPM-tag hebben en of Navidrome die teruggeeft.
- C, tempo: de planner mag het tempo van het oude nummer nu tot 10% aanpassen (was 6%). Reden: in de luistertest van Ishizaki e.a. (96 personen) zit het ongemak bij 0,90 en 1,10, gemiddeld pas bij 0,852 en 1,227; vertragen hindert meer dan versnellen. Stems blijven bij 6% (daar verandert niets aan); een plan met stems dat meer dan 6% afwijkt wordt opnieuw gepland zonder stems. Niet bekend: hoe Sonic (de tijdrekker van Media3) klinkt bij 10%, en of de uitlijning daar even goed werkt als bij 6%; het transitierapport in het log laat dat zien. Niet gedaan: de aanpassing over beide nummers verdelen (Ishizaki): het nieuwe nummer wordt niet versneld of vertraagd, dat vraagt een tweede ramp op de hoofdspeler die ik niet kan testen.
- B, echo-uitgang (vervangt de snede van 2 tellen): voor paren die niet passen gaat het oude nummer een tel voor de maatlijn in een echo (vertraging een tel van A, eerste herhaling 0,55, elke herhaling 0,55 van de vorige, in `BassCutProcessor`), stopt zelf op de maatlijn (15 ms) zodat alleen de echo doorklinkt (vier tellen, 2 tot 4,5 s, laatste 30% zacht uit), en het volgende nummer komt op die maatlijn in met volle sterkte (40 ms), zodra het in step is. Geen tempo-overlap meer. Zonder filters. Log: `echo: old song goes into the echo ...`, `echo: old song stops ...`, soort `echo cut` in het transitierapport.
- B, begin van het volgende nummer (`introEntries`, alleen voor de echo-uitgang): eerst de eerste twee frasegrenzen (elke vier maten vanaf de eerste maatlijn van de muziek) tussen 8 s en 60 s, daarna pas de plekken waar het nummer opkomt. 8 s blijft het minimum omdat het volgende nummer eerder moet starten om in step te komen (preroll); een eerdere plek zou een andere, niet geteste route in de engine nodig hebben. De maatlijnen zijn teruggerekend uit de eerste ingang met het tempo van de analyse; een klein tempofoutje telt dus mee (tot enkele tientallen ms bij 50 s).
- Tekst bij Settings, Playback aangepast.
- Niet gedaan en bewust niet: LUFS-normalisatie, true-peak limiter, tijdrekken met Signalsmith of Rubber Band (NDK nodig, hier niet te bouwen of testen; Rubber Band is GPL), Beat This! (190 MB, snelheid op een telefoon onbekend), vooraf renderen van overgangen. Discovery Station sorteert niet.
- Onzeker: het geluid van de echo (niveaus 0,55 en 0,55 zijn gekozen, niet beluisterd); of 'het volgende nummer is in step' op tijd gebeurt bij een kort begin (dan blijft het oude nummer doorspelen tot de maatlijn, en daarna komt het binnen); of de shuffleknop die uit staat verwarrend is.

## 0.0.50
- Op verzoek van de gebruiker, naar aanleiding van het testlog van 16 oktober (drie echo-overgangen, alle drie klaar, uitlijning -20, 22 en -5 ms, instap van het volgende nummer op 14,1 s, 8,6 s en 12,4 s; alle paren net buiten 10%: 0,834, 0,894 en 1,217). Niet gecompileerd en niet beluisterd.
- Echo: gaat nu twee tellen voor de maatlijn aan (was een). In het log zat tussen aan en stop 613, 249 en 642 ms bij een vertraging van 705, 422 en 472 ms; bij de tweede zat er dus maar 249 ms van het oude nummer in de echo. De echo is een tel lang, dus met twee tellen is hij altijd vol. Oorzaak van de spreiding (ongeveer 170 ms) is vermoed (de positie die de speler meldt), niet gemeten.
- Tempo: het oude nummer mag nu 0,88 tot 1,15 aangepast worden (was 0,90 tot 1,10); vertragen hindert meer dan versnellen (Ishizaki). Met deze band waren de laatste twee paren uit het log (0,894 en 1,217) een beat mix met filters geweest. Stems blijven bij 6%. Niet bekend hoe Sonic klinkt bij 15% sneller of 12% langzamer; het transitierapport laat de uitlijning zien.
- Tekst bij Settings, Playback aangepast. Test 3 van de gebruiker (Shuffle met AutoMix en beat matching aan, zoeken op `order:`) staat nog open.

## 0.0.51
- Op verzoek van de gebruiker: Up Next toont wat er echt aan komt. Niet gecompileerd en niet beluisterd.
- Werkwijze: de service (`PlaybackService.publishUpcoming`) rekent met de timeline van de echte speler uit welke nummers volgen (ook met shuffle, tot 300) en publiceert dat als sessie-extra (`EXTRA_UPCOMING`, met `EXTRA_UPCOMING_FOR`: positie en lengte van de wachtrij waarvoor het geldt). `PlaybackViewModel` luistert met `onExtrasChanged` en gebruikt die volgorde in `upcomingIndices`; klopt positie of lengte niet (extra nog niet bijgewerkt), dan geldt de oude berekening.
- Geldt altijd, niet alleen met AutoMix, omdat het een juistheidsfix is.
- Onzeker: dat de oorzaak de shuffle-volgorde van de controller is (niet geverifieerd in Media3 1.11.1); de exacte signatuur van `MediaController.Listener.onExtrasChanged`; of de Bundle-extra tijdig aankomt na een wisseling.
- Niet opgelost: tijdens een beat mix springt de hoofdspeler ongeveer 7 s voor het hoorbare moment naar het volgende nummer (preroll), dus Now Playing en Up Next tonen het volgende nummer iets te vroeg.

## 0.0.52 (met 0.0.52b: ANDROID_STL=c++_shared voor Oboe)
- Op verzoek van de gebruiker ("Bouw de koppeling en integreer in Nooklin Music", route A: native engine, Auto Mix vervangen, Gradle aanpassen). Gebaseerd op de audit van de Auto Mix (Docs-artifact) en de fasen 0 tot 2 van het plan. De gebruiker compileert zelf; niet gecompileerd als app en niet op een toestel geprobeerd.
- Nieuwe native kern (`app/src/main/cpp`, C++17, geen Android-headers in `mix/`): `analyzer` (beat-grid per nummer, tempo, maat, frase, cue in en uit), `planner` (overgang op frasegrenzen, tempo van A glijdt naar B, half en dubbel tempo tellen, anders een korte nette fade), `deck` (bit-exacte doorgave bij snelheid 1, anders Signalsmith Stretch, MIT), `engine` (twee decks op een klok, EQ-wissels op maatgrenzen: melodie eerst, dan de bas; pauze en gain met korte fade; late beat-grid; stop; overdracht zonder gat), `resampler` (alle bronnen naar 44,1 kHz). Android-laag: `android/mix_jni.cpp` (Oboe-uitgang, JNI), `android/pcm_buffer.h`, `CMakeLists.txt`.
- Getest op de computer (g++, synthetische nummers): analyzer, deck, engine (drie paren: 124>128, 128>124, 120>126 bpm; fasefout tussen de decks op de gevonden grids onder 0,4 ms; late grid; pauze; stop) en resampler (48, 32, 22,05 en 96 kHz naar 44,1 kHz, fout onder 4 van 32767). Alle C++-bestanden zijn ook gecompileerd voor arm64 met NDK r28 en Oboe 1.11.0 zonder waarschuwingen. Niet getest: echte muziek, echte toestellen, de JNI-brug draaiend.
- Kotlin (`playback/mix/`): `NativeMix` (JNI), `PcmDecoder` (MediaExtractor en MediaCodec naar een native buffer, mono wordt stereo, float-PCM ondersteund, hoogstens 15 minuten), `MixLibrary` (bestand: download, anders een kopie in `cacheDir/automix` van de stream, hoogstens 300 MB; decoderen; analyse bewaard in `filesDir/automix_maps`), `MixConductor` (de brug).
- Werkwijze: de ExoPlayer blijft de wachtrij, de sessie, de notificatie en de klok, maar speelt stil (volume 0) zolang de engine een nummer speelt. De engine neemt een nummer over vanaf het begin (of na een skip of zoeken) zodra het gedecodeerd is; tot dan speelt de speler zelf. Tijdens een nummer wordt het volgende opgehaald, gedecodeerd, geanalyseerd en met een plan in de engine gezet; de engine doet de overlap en gaat zelf naar het volgende nummer, daarna springt de speler naar het volgende item (`onAdvance` telt als uitgespeeld in het smaakprofiel). De speler volgt de engine (gelijkgezet bij meer dan 0,4 s verschil). Pauze, hervatten, snelheid, ReplayGain en volumebegrenzing worden doorgegeven.
- Verandert in gedrag: Auto Mix met beat matching gebruikt de engine voor alle nummers van hoogstens 15 minuten (ook gestreamde, die eerst helemaal naar de cache gaan: dataverbruik). De tempogrens is 10% (`MAX_TEMPO_CHANGE` in `MixConductor`); daarboven, of zonder duidelijke beat, een fade van 3 s. Met AirPlay aan, herhaal-een, nummers van meer dan 15 minuten of een mislukte decodering speelt de speler zelf, met de gewone AutoMix van 10 s. Skip silence werkt niet op nummers die de engine speelt. Smart Fades (zelfde album) volgen elkaar zonder overlap.
- Verwijderd uit de app: de schakelaar "Separate vocals (stems)" in Settings, Playback en de uitleg daarbij (de engine gebruikt geen stems). `CrossfadeController` krijgt `beatMix = false` en `stemMix = false`; de oude beat-mix- en stems-code staat nog in de bronnen maar wordt niet meer uitgevoerd (niet verwijderd: groot en zonder compiler niet veilig te snoeien). De instelling `stemMix` en het model in de assets staan er nog.
- Gradle: `externalNativeBuild` (CMake 3.22.1), `prefab = true`, `com.google.oboe:oboe:1.11.0` (Apache-2.0), `abiFilters` arm64-v8a, armeabi-v7a, x86_64. Versie 0.0.52 (versionCode 80). Licenties: Signalsmith Stretch en Signalsmith Linear (MIT) staan in `third_party/` met hun licentiebestanden; `THIRD-PARTY-LICENSES` moet nog worden aangevuld voor de release.
- Meetlog (fase 0): `filesDir/automix_log.jsonl` en logcat-tag `NooklinMix` krijgen een JSON-regel per start, plan (bpm, soort, verhouding), overdracht (grootste fasefout in ms) en einde.
- Onzeker en niet getest: de hele Android-keten (JNI-namen van `@JvmStatic external` in een `object`, Oboe-stream en heropenen na een routewissel, MediaCodec-uitvoerformaten, OkHttp-download), de klank (EQ-wissels, Signalsmith bij 10%), de naad waar de speler het geluid aan de engine geeft en terug (kort overlappend, uitlijning niet gemeten), de analyse-duur op een telefoon, het geheugen (twee nummers van 4 minuten is circa 85 MB native), het stroomverbruik (de speler decodeert stil mee), de CPU-belasting van Signalsmith op de audio-thread, de geluidsgrens bij het opstarten van de stretcher midden in een blok.
- Niet gedaan: MixOrder (volgorde bij shuffle en AutoPlay) gebruikt nog de oude analyse in `BeatMixStore`, die daarom nog meeloopt op de achtergrond; die kan naar de nieuwe kaarten; Beat This! als tweede beat-tracker; de bekende beperking dat de positie van de speler tijdens een tempoglide iets afwijkt (de speler wordt dan bijgezet).

## 0.0.53
- Op verzoek van de gebruiker: Up Next in de speler (`QueuePanel`, `NowPlayingScreen.kt`). Niet gecompileerd.
- Buiten bewerken toont het paneel het huidige nummer bovenaan en eronder de nummers die komen, in echte speelvolgorde (`state.upcoming`, dezelfde lijst als het Up Next-scherm; de speler publiceert die volgorde). Daarboven staan de gespeelde nummers onder de kop "Played"; een lijn en de kop "Now and next" scheiden ze van het huidige nummer. De lijst opent op die kop, naar boven scrollen toont de gespeelde nummers. Zonder gespeelde nummers staan er geen koppen.
- Gespeelde nummers: de nummers die eerder in de wachtrij staan (en met shuffle de nummers die niet in de upcoming-lijst zitten). Dit is niet de echte afspeelgeschiedenis; die wordt niet bijgehouden.
- Ongewijzigd: Edit (positie in de wachtrij, hele wachtrij zoals voorheen), Clear en tikken om te springen (springt naar de wachtrijpositie van het nummer).
- Niet gedaan: het vroege springen van het scherm naar het volgende nummer (preroll). Met de nieuwe engine springt de speler pas na de overdracht (`MixConductor.onHandoff`); dit punt hoort daarom bij het testen van 0.0.52.
- Onzeker: `upcoming` bevat hoogstens `MAX_UPCOMING` nummers; langere wachtrijen tonen achter die grens niets meer in dit paneel (wel in Edit). Met herhalen-alles na het laatste nummer begint de lijst niet opnieuw.

## 0.0.54
- Op verzoek van de gebruiker: de kop "Played" in het Up Next-paneel van de speler heet nu "History". Verder niets veranderd. Niet gecompileerd.

## 0.0.55 (onderlaag van de overgang)
Doel: een echte DJ-overgang. Uit het log van 0.0.54 bleek dat de beats van de twee nummers in de overlap 100 en 267 ms uit elkaar liepen. Oorzaak aangetoond: de planner berekende de tempoverhouding uit het lokale tempo van één beat-interval (ruis, bijvoorbeeld 1,0059 in plaats van 1,0143 en 1,0423 voor twee nummers van 155,07 en 155,13 bpm), en de fasevergrendeling (maximaal 0,4%) kon die fout niet meer rechtzetten. De tests misten dit omdat ze de fout op de eigen rasters meten en de testnummers een perfect raster hadden.
- Analyzer: bij een vast tempo (minstens 85% van de beats binnen 25 ms van één rechte lijn) wordt het raster één rechte lijn over het hele nummer, zonder lokale ruis. Anders blijft de lokale fit.
- Planner: de tempoverhouding is de tijd die A nodig heeft gedeeld door de tijd die B nodig heeft voor dezelfde muziek, gemeten over minstens 16 beats (de overlap en iets meer), niet meer uit één beat.
- Engine: fasevergrendeling met proportioneel en integrerend deel (tot 3% totaal, in ongeveer een seconde dicht), reset bij het klaarzetten van B. Nieuw in de status: positie van B (`bPositionSec`).
- Opgeslagen rasters van de oude analyse worden niet meer gebruikt (kaartversie 2); nummers worden opnieuw geanalyseerd.
- Spelericoon dat snel wisselt: de stille speler werd na elke afwijking van 0,4 s teruggezet (een seek laat de speler stoppen en starten). Nu: alleen terugzetten bij meer dan 1,5 s verschil, alleen als één nummer alleen speelt, hoogstens 3 keer per nummer en niet vaker dan eens per 20 s; elke terugzetting komt als `follow` in het log. Eigen seeks worden exact geteld, zodat ze niet voor een seek van de gebruiker worden aangezien (dat zou de engine herstarten).
- Overdracht: het nummer dat de engine verlaat wordt nu uit de engine zelf genomen, niet uit de speler (die kon al naar het volgende nummer zijn gegaan; in het log stond daardoor `from` gelijk aan `to`, en de smaakregistratie ging naar het verkeerde nummer).
- ALAC: telefoons zonder ALAC-codec (zoals die van het log) speelden die nummers met de gewone speler. Nu decodeert een ingebouwde decoder (Apple's referentiedecoder, Apache-2.0, ongewijzigd in `third_party/alac`) ze. Mono en stereo; surround blijft bij de gewone speler. Getest tegen ffmpeg: 16-bit identiek, 24-bit binnen 1 stap.
- Log: `grid of <id> in <ms>`: duur en tempo van de nieuwe analyse per nummer.
- Licenties aangevuld in `THIRD-PARTY-LICENSES` (Signalsmith, Oboe, ALAC).
- Niet gedaan: de oude Kotlin-analyse (BeatMixStore, 7 tot 29 s per nummer) draait nog voor MixOrder; sleutel- en energiekeuze van het uitstappunt.
- Tests op de computer: analyzer, deck, engine (nu ook met nummers met timingruis van 6 tot 8 ms, tempo's als 152,88 en 155,07, een driftend nummer, en een met opzet 1,5% foute verhouding; gemeten tegen de ware beats), resampler, ALAC. C++ gecompileerd met de NDK voor arm64, armv7 en x86_64. Kotlin niet gecompileerd (de gebruiker compileert zelf).

## 0.0.56 (het beste moment om te mixen)
Tot nu toe was het uitstappunt vast: de laatste zinsstart van A waar de overlap nog past, over de eerste zinsstart van B. Nu kiest de planner uit meerdere paren en neemt energie en toonsoort mee.
- Analyse: per beat de relatieve luidheid (de mediaan van de muziek is 1) en per blok van 4 beats de toonhoogteverdeling (12 toonklassen, alleen over de eerste 100 s en de laatste 140 s van de muziek, waar de planner kijkt). Analyse duurt ongeveer 25% langer (1,0 naar 1,3 s voor 150 s audio op een computer). Kaartversie 3: alle nummers worden opnieuw geanalyseerd.
- Planner: kandidaten zijn alle zinsstarten in de laatste 120 s van A (waar de overlap nog past) en de zinsstarten in de eerste 60 s van B, voor elke lengte (32 bij snelle nummers, 16, 8). De kosten per paar: gemiddelde luidheid van de twee overlappende stukken (stil gaat over stil), de verschillen daarin, botsende toonsoorten (zie hieronder), vroeg uit A gaan (0,4 per minuut die niet gespeeld wordt), het begin van B overslaan (0,8 per minuut) en een lichte voorkeur voor lange overlap. Laagste kosten wint.
- Toonsoort: per stuk (A: 16 beats voor tot het einde van de overlap, B: de overlap en 16 beats erna) een toonsoort volgens Krumhansl-Schmuckler, en een botsing op het Camelot-wiel: dezelfde, relatieve of buur-toonsoort 0, een hele toon verder 0,3, verder 0,7 tot 1. Gewogen met hoe zeker beide toonsoorten zijn, en bij een botsing een kortere overlap (kosten stijgen met de lengte). Een stuk zonder toonhoogte (een intro met alleen drums) telt als onbekend en botst nooit; zo mixt een botsend paar over de drums.
- Log (`plan`): `energyA`, `energyB`, `keyA`, `keyB`, `keyClash`, `cost`.
- Grenzen: de drempels (hoe zeker een toonsoort moet zijn, de kostenverhoudingen) zijn afgesteld op synthetische nummers, niet op echte muziek. Een afwijkende stemming (niet 440 Hz) wordt niet gecorrigeerd. Zang tegen zang wordt niet herkend. Energie is luidheid, niet "stem of instrument".
- Nieuw: `host/test_planner.cpp` (toonsoort van vier synthetische nummers, uitstap in een breakdown tegenover het einde, fade-out, botsing geeft kortere overlap).


## 0.0.57 (DJ-technieken)
Waarom dit er is: in 0.0.52 verving ik Auto Mix door de nieuwe engine en liet daarbij echo-uit, stems, knippen en filters weg en maakte de tempoband smaller, zonder dat te melden. Dat is hier hersteld, op de nieuwe onderlaag.
- Technieken (planner kiest per paar): lange blend (melodie eerst, dan bas-swap), korte blend (kick-swap), echo uit, filter uit (lowpass-sweep over 4 beats) en knippen op een maatlijn. Een uitstap komt op een zinsstart van A; B komt in op een zinsstart of op een drop (energie 1,4x hoger dan de 16 beats ervoor). Echo en filter beginnen 2 en 4 beats voor de maatlijn, knippen fadet A 15 ms.
- Volgorde van keuze: eerst de beste beatgematchte blend. Is de toonsoortbotsing hoger dan 0,55 (of past geen blend), dan de beste uitstap. Uitstapkosten: echo 0,30, filter 0,35 (stil einde +0,15, luid einde -0,10), knippen 0,40 (-0,25 bij twee luide nummers met gelijke sterkte, -0,15 op een drop), plus energiesprong, vroeg vertrekken en overslaan van B.
- Gedeeld tempo: wijkt het tempo meer dan 8% af, dan krijgen beide nummers de wortel van de verhouding (samen tot 22%). B glijdt na de overlap in 8 beats terug naar zijn eigen tempo. Kosten voor rekken: 1,5 per eenheid log-tempo.
- Engine: toestand 6 (outro), echo-effect achteraan de keten (blijft tot 8 s na de overgang klinken), sweepfilter, korte intrede van B (5 ms) bij knippen, limiter na het echo-effect.
- Volgorde van nummers: `MixFacts` bewaart tempo en toonsoort uit de nieuwe analyse; `MixOrder` gebruikt die eerst en de oude analyse als terugval.
- Log (`plan`): `kind` bevat de techniek, `tempoRatioB`.
- Tests: `host/test_techniques.cpp` (knippen, echo-staart sterft uit, filter verlaagt hoge tonen, gedeeld tempo faseert binnen 12 ms op de echte beats, glijden terug naar tempo 1) en de planner-test voor botsende toonsoorten is aangepast (nu: schone uitstap). Alle host-tests slagen; NDK-syntaxis gecontroleerd voor arm64, armv7 en x86_64.
- Niet gedaan / grenzen: alle drempels en kosten zijn met de hand gezet en alleen met synthetische nummers getoetst; hoe het klinkt heb ik niet kunnen beoordelen. Kotlin is niet gecompileerd. De oude Kotlin-analyse (BeatMixStore) draait nog: CrossfadeController, stems en de luidheid voor MixOrder/HomeMixes hangen eraan; de nieuwe analyse heeft geen absolute luidheid, dus uitzetten had die functies stukgemaakt. Het toonsoortgeloof drempel (0,5) is niet afgesteld op echte muziek. Tempo-octaafverschillen tussen oude en nieuwe analyse (139 tegen 92,8; 174 tegen 87) zijn niet opgelost.

## 0.0.58 (waarom er niet gemixt werd)
Log 5bb6fbe6: vier overgangen gepland (cut, lange blend, cut op een drop, korte swap met gedeeld tempo), geen enkele uitgevoerd; elk nummer speelde tot het einde.
- Oorzaak 1: een nummer dat zonder raster in de engine start (`analysed:false`) kreeg zijn raster later via `setCurrentMap(map, epoch)`. De epoch werd direct na `play` gelezen, maar de audiothread telt die pas bij het uitvoeren van het commando op: de waarde was een te oud, de engine wees het raster stil af, de planning (die een geldig raster van A nodig heeft) startte nooit. Nu wordt de epoch gelezen op het moment van doorgeven.
- Oorzaak 2: bij een overdracht aan het einde van een nummer was de stille speler al zelf naar het volgende nummer gegaan; `onHandoff` vond dat nummer niet als "ander" item, gaf de engine op (`disown`) en startte 5 s later opnieuw (`start` met `at` rond 5 s, de speler had het nummer zelf gespeeld). Nu wordt het huidige item geaccepteerd.
- Niet geverifieerd op een toestel: Kotlin niet gecompileerd; de oorzaak is afgeleid uit het logboek (alle vier de starts `analysed:false`, overdrachten ver na het geplande punt).

## 0.0.59 (muzikale logica: secties)
- Analyse: elk nummer krijgt secties (rustig en luid, met de verhouding bij het begin van de sectie). Kaartversie 4, dus nummers worden opnieuw geanalyseerd.
- Planner op secties: relaxed (A zakt, B begint, intro over de uitloop), rolling (B valt in terwijl A zakt), double drop (beide vallen tegelijk, tempo maximaal 5% verschil), anders de blend van 0.0.57, anders een uitgang (echo, filter, cut). Cut alleen vanuit een luide A op een drop van B.
- Tempogrens 10% in totaal, een nummer alleen maximaal 5%.
- Engine: volumebalans, samen nooit luider dan een nummer.
- Wachtrij: dezelfde artiest niet binnen 3 nummers, tenzij er niets anders is.
- Log: plan bevat entrySecB en style.
- Niet gebouwd: tempo-octaaf (tag-bpm niet doorgegeven), vocal-heuristiek, lift of noise riser, jaar en toonsoortreeks in de wachtrij, telemetrie van het werkelijke instappunt.
- Niet gecompileerd of getest na de laatste wijzigingen. Bekend: de planner-hosttest gaf een segfault in de nieuwe sectiecode, oorzaak niet gevonden. Drempels zijn met de hand gezet op synthetische muziek.

## 0.0.60 (diagnose)
- Het planbericht in het log bevat nu `why`: per nummer bpm, aantal beats, cueIn, einde, secties (beat, H of L, verhouding), aantal chromablokken en toonsoort met zekerheid, plus per bloktype (relaxed, rolling, double) hoeveel kandidaten er waren en waarom ze afvielen (geen ruimte, tempo, te vroeg, duurder, geaccepteerd).
- Geen gedragswijziging in de planner.

## 0.0.61 (crash)
- Crash fixed: `planLegacy` called itself without end (my "1b" step landed in the wrong function), so any song without sections overflowed the stack (SIGSEGV in libnooklinmix, repeating frame). The step now sits in `planTransition`. This was also the segfault of the host planner test.
- Host tests planner, engine, techniques, sections, analyzer all pass again.

## 0.0.62 (zang, genre, techniek op situatie, loudness)
Op verzoek van de gebruiker na nieuw onderzoek (Vibes, DJ.Studio Harmonize, EDM Ghost Production): de regels uit het onderzoek staan nu in de planner.
- **Zang (`MixVocals`)**: met het Spleeter-model dat al in de app zit (`assets/stems/*.fp16.onnx`, de vier bestanden moeten er staan; zonder model wordt niets gemeten en geldt de oude gang van zaken) wordt per seconde gemeten hoeveel van het geluid zang is (zang-stem tegen de som van de vier stems). Alleen de eerste 100 s en de laatste 100 s van een nummer, in stukken van 20 s, op een draad met de laagste prioriteit, een nummer tegelijk. Het resultaat staat in `filesDir/automix_vocals/<id>.txt` en wordt per beat in de kaart gezet voor de planner (kaartlay-out: ..., sections, loudness, vocalCount, vocal...). Zodra de zang van A of B binnen is, komt er een nieuw plan.
- **Planner met zang**: een overlap waarin beide nummers zingen (boven 12% van het geluid) kost 4 maal de fractie beats waarin dat gebeurt, dus de planner kiest liever een venster zonder zang aan een kant (de breakdown of outro van A, de intro van B). Blijft er zang aan beide kanten, dan krijgt het plan `kFlagVocalCut`: de engine haalt de middenband van A weg in de eerste twee beats van de overlap. Exits vlak na een zangstuk krijgen een straf (cut 0,5, echo en filter 0,25).
- **Genre** (uit het smaakprofiel `TasteStore`, genreklasse 0 tot 3): 1 hip-hop en pop (een cut ook zonder drop mits B sterk begint, en een cut mag een middelmatige blend verslaan), 2 house en techno (lange blends krijgen een bonus, een cut kost 1,5 extra), 3 bass en drum and bass (double drop krijgt een bonus). Zonder genre geldt klasse 0 (geen voorkeur).
- **Echo en filter als noodgreep**: +0,5 op hun kosten, dus een blend of een cut wint zodra die past.
- **Langere overlap**: korte overlaps kosten extra (0,1 maal log2 van 32 gedeeld door de lengte), zodat de uitloop van A en de intro van B beter benut worden.
- **Fade-out**: eindigt een nummer luid maar zakken de laatste 8 maten of meer onder 65% van het gewone niveau, dan telt dat stuk als een eigen rustige sectie.
- **Loudness**: de analyse meet het niveau van de muziek (dB). De engine zet het volgende nummer op het niveau waarop het huidige nu klinkt, hoogstens 6 dB zachter en 4 dB harder (`Slot.trim`).
- **Log**: het planbericht heeft `reasons` (leesbare zin: waarom die techniek, lengte, tempo, zang, toonsoort), `loudnessA` en `loudnessB`, en `why` (secties, toonsoort, afgewezen kandidaten).
- Kaartversie 5, dus de nummers worden opnieuw geanalyseerd (de analyse duurt iets langer door de loudness).
- Host-tests: planner, engine, techniques, sections, analyzer en nieuw `test_vocals` (zangkosten, mid cut, genre, loudness).
- **Niet gebouwd of niet bewezen**: de cue-in staat al op ruwe RMS (10% van de mediaan van een afgevlakte curve), dus daar is niets veranderd. De fade-outregel is niet op een echte fade getest (de synthetische testnummers hebben een stap, geen glijdende afbouw). De drempels voor zang (12%), de verhoudingen en de genrewoorden zijn met de hand gezet en niet op echte muziek getoetst. Spleeter-zang kan lekken; de tijd per nummer op de telefoon is niet gemeten (het log toont `vocals <id>: measured in X ms`). Niet gebouwd: lift (noise riser, stutter), disguise-effecten, sleutelverschuiving, tempo-octaaf.

## 0.0.63
- Instap in B bij cut/exit maximaal 24 s (was 120 s), zodat een overgang niet meer ver in een nummer springt. Drops binnen 8 tot 24 s tellen nog als instap.
- `reasons` voor exits zonder structuur zegt nu "cut/echo/filter" in plaats van "phrase based blend".
- Tempo scan: 20 s na start van de mix worden gedownloade songs zonder bekend tempo een voor een geanalyseerd (tempo, toonsoort, grid opgeslagen) zodat de wachtrij-volgorde ze kent. Gestreamde songs worden niet gescand (kost data).
- Zangmeting: alleen het vocals-model (een kwart van het werk), 60 s per kant, 3 threads. Aandeel = vocals-schatting over totaal van het geluid. Nieuwe map automix_vocals2 (oude waarden hadden een andere schaal). Drempel kSinging 0.12 is nog niet opnieuw afgesteld.
- Niet gebouwd: scan van gestreamde songs, geleidelijke HP/LP ramp, fallback-teller in de log.

## 0.0.64
- Temposcan ook voor gestreamde songs: songs in de wachtrij en gedownloade songs worden 20 s na start gescand; een gestreamde song gaat daarvoor naar de cache (dezelfde kopie die de engine gebruikt als hij speelt, dus voor songs die gespeeld worden niets dubbel).
- AutoPlay: de 10 gekozen songs worden eerst gescand (hoogstens 40 s wachten, dan toch op wat bekend is ordenen) voor `MixOrder.after`.
- Niet gedaan: de eerste shuffle (in de app, buiten de service) kan niet wachten op de scan, dus daar blijft de volgorde afhankelijk van wat al bekend is. Geen controle op Wi-Fi: een scan van gestreamde songs gebruikt ook mobiele data. Cache is 300 MB met oudste eerst weg.

## 0.0.65 (diagnose tempo)
- Het planbericht in het log heeft nu `tagBpmA`/`tagBpmB` (het BPM-veld van de server, 0 als er geen is), `oldBpmA`/`oldBpmB` (de oudere analyse van het hele nummer, null als nog niet gedaan) naast `bpmA`/`bpmB` (het grid van de engine). Doel: uitmaken of 87 of 116 bpm klopt voor de nummers waar die twee uiteenlopen (factor 4/3).
- `QueueEntry` en de wachtrij-items dragen het BPM-tag mee (`EXTRA_BPM`). Een bewaarde wachtrij die van schijf wordt teruggezet heeft geen tag (0) tot de nummers opnieuw worden gekozen. Geen gedragswijziging.
- Niet gecompileerd. Niet bekend: of jouw Navidrome het veld `bpm` meestuurt (anders staat er overal 0).

## 0.0.66 (test: groter tempoverschil)
- Op verzoek om te testen: de gedeelde tempogrens voor een blend is 20% (was 10%, `MAX_TEMPO_CHANGE`), en B glijdt na de overlap in 16 beats terug naar zijn eigen tempo (was 8, `glideBeats`). Een nummer alleen blijft maximaal 5%; wat meer is wordt gedeeld (A sneller, B langzamer, bij 20% elk ongeveer 9,5%). Een gat van 33% valt er nog buiten.
- De rampe van A (8 beats voor de overlap) is niet veranderd en kan bij 9,5% abrupt klinken.
- Host-tests (planner, techniques, engine, sections) slagen; een planner-proef met 87 en 104 bpm (19,4%) kiest nu een relaxed blend. Hoe het klinkt is niet beluisterd. Terugzetten: `MAX_TEMPO_CHANGE = 0.10` en `glideBeats = 8`.

## 0.0.67
- Log 0.0.66: tempo-tag overal 0 (de server stuurt het veld waarschijnlijk niet), oude analyse en grid spreken elkaar in beide richtingen tegen (87 of 116 bpm), geen blend met 10 tot 20% verschil voorgekomen (dus de 20%-grens is nog niet getest), faseafwijking groeit met het tempoverschil van A (2,0 ms bij 1,001; 15,1 ms bij 1,010).
- Temposcan: eigen logregel per song (`tempo scan <id>: bpm=... in N ms`, of `no file`, `could not decode`, `no clear beat`). De scan start nu ook zodra AutoMix aan gaat en bij elke nummerwissel (een scan tegelijk), niet meer eenmalig 20 s na de start. Van de wachtrij worden de volgende 10 nummers bekeken (`SCAN_AHEAD`), plus de gedownloade nummers; een gestreamd nummer wordt daarvoor binnengehaald.
- Exits laten A later vallen: `kMaxEarlyExitSec` 45 s (was 120 s). Dit geldt ook voor de blend van de oude fraselogica. Blends op secties (A zakt) zijn er niet door beperkt.
- Dubbele handoff (09:07:27, het nummer begon opnieuw na ongeveer een halve seconde): oorzaak NIET gevonden. De engine telt een handoff maar een keer en de host-test met `entrySecB` 0 geeft een enkele handoff. Het handoff-bericht in het log heeft nu `changes`, `engineState`, `positionSec` en `sinceStartMs`, zodat een volgende keer te zien is wat er gebeurde. Geen gedragswijziging.
- Host-tests (planner, techniques, engine, sections, vocals) slagen. Niet gecompileerd (Kotlin).

## 0.0.68 (test: tempogrens 40%)
- Op verzoek om te testen: de gedeelde tempogrens voor een blend is 40% (was 20%, `MAX_TEMPO_CHANGE` in `MixConductor.kt`). Reden: in het log van 17 oktober waren alle drie de overgangen een noodexit (echo of filter) omdat het tempoverschil na vouwen (0,5 / 1 / 2) boven 20% lag: 140 naar 87 bpm (ongeveer 24%), 87 naar 127 bpm en 127 naar 87 bpm (ongeveer 37%). Een nummer alleen blijft maximaal 5%; de rest wordt gedeeld (bij 37% elk ongeveer 15 tot 17%).
- Proef met synthetische nummers op 139,9 / 86,8 / 127,0 / 87,1 bpm (planner, geen sections): bij 0,20 een exit, bij 0,40 een blend met snelheden 1,114 en 0,898 (140 naar 87), 0,855 en 1,170 (87 naar 127) en 1,171 en 0,854 (127 naar 87). Echte nummers hebben secties, dus de planner kan daar een andere stijl kiezen.
- Niet gewijzigd: de rampe van A, `glideBeats`, de 5%-grens voor een nummer alleen, de grens van 5% voor een double drop.
- Niet gecompileerd en niet beluisterd. Niet bekend: hoe de time-stretcher (Signalsmith) klinkt bij 15 tot 17% per nummer; of de glijdende terugkeer van B naar zijn eigen tempo (16 beats) bij zo'n verschil te merken is. Terugzetten: `MAX_TEMPO_CHANGE = 0.20`.

## 0.0.69 (dubbele handoff)
- Log 0.0.68: de tempogrens van 40% werkt, bijna alle paren krijgen nu een blend met gedeeld tempo (faseafwijking 0 tot 11 ms). Vier van de tien overgangen hadden de dubbele handoff: een eerste handoff-regel met `changes 0`, `engineState 0`, `positionSec 0` (een status met alleen nullen), 170 ms later een tweede met de echte waarden (`engineState 4`, `changes` van daarvoor), en een `start` van B op 0. B begon dus opnieuw terwijl de blend nog liep.
- Waarschijnlijke oorzaak (niet bewezen): `nativeStatus` (`mix_jni.cpp`) gaf nullen terug als de seqlock-lus in 50 pogingen geen consistente meting kreeg. De host zag `changes` 0 tegenover `lastChanges` 3 als een handoff.
- Fix: de lus probeert tot 2000 keer (met `yield` na 50), en lukt dat niet dan krijgt de host de laatste goede meting terug in plaats van nullen. De laatste goede meting wordt gewist bij `nativeRelease`. Is er nog geen goede meting, dan blijft het gedrag zoals het was. Bij een mislukte meting komt `status read failed, ...` in logcat (tag `NooklinMix`, niveau W).
- Gecontroleerd met een los testbestand op de echte `Engine`-telemetrie: met een schrijver die blijft hangen (seq oneven) geeft de functie de laatste goede meting (state 4, positie 148,4, changes 3) en niet nullen. De syntaxis van `mix_jni.cpp` zelf is met stubs voor `jni.h` en het logbestand gecontroleerd; niet met Oboe en niet gecompileerd voor Android.
- Niet bekend: of dit echt de oorzaak is. Staat `status read failed` niet in het log en komt de dubbele handoff toch, dan ligt het ergens anders (bijvoorbeeld `g_engine` leeg of een andere bron van nullen). Terugzetten: de oude lus van 50 pogingen.
- Opgemerkt, niet aangepast: `nativeStatus` leest `g_engine` zonder `g_lock`, terwijl `nativeRelease` hem onder `g_lock` wist.

## 0.0.70 (tempofout 4/3 en te vroeg weggaan)
- Log 0.0.69, op verzoek van de gebruiker ("te snel gewisseld en te snel gepitched, het volgende nummer klinkt niet zo snel"): bij 5lAh naar 1f9U (14:21:24) hadden beide nummers ongeveer 87 bpm (scan A 86,66, volledige analyse A en B 87,0), maar het raster van B stond op 116,23 (4/3 te hoog, uit de snelle temposcan). De planner deelde een verschil van 34%: A x 1,155 (ongeveer 100 bpm), B x 0,866 (ongeveer 75 bpm echt). De volledige analyse van B was klaar na het plan en gaf geen nieuw plan. Hetzelfde bij 3hLY in het vorige log (115,8 tegen 87,0).
- Fix 1 (Kotlin, `MixConductor.kt`): `tempoDoubt` vergelijkt het raster-tempo met het tempo van de oudere analyse van het hele nummer (`BeatMixStore.entries(...).firstOrNull().bpm`). Past dat niet op 1, 2 of 1/2 maal (marge `TEMPO_AGREE` 0,04 in ln), dan wordt de kaart van dat nummer niet gebruikt voor het plan: de planner maakt een gewone fade van 3 s zonder beat match. De plansleutel bevat nu of de oude analyse van A en van B bekend is, zodat er opnieuw wordt gepland als die later binnenkomt. Het planbericht heeft `tempoDoubtA` en `tempoDoubtB`.
- Fix 2 (C++, `planner.cpp`, `evalBlend`): een blend (relaxed, rolling en double drop) mag A niet verlaten als het einde van de overlap meer dan `kMaxEarlyExitSec` (45 s) voor het einde van de muziek ligt. Zo verlieten 5lAh, 0xaD en 1pjp hun nummer 76 tot 103 s te vroeg. Het `why`-veld heeft een teller `too early`.
- Host-tests (planner, techniques, sections, engine) slagen. Proef met synthetische nummers (126 bpm, 220 s): een rustig stuk op 80 tot 100 s wordt door alle 3 relaxed-pogingen afgewezen als `too early` en het plan wordt de oude fraseblend aan het einde (13 s voor het einde); een rustig stuk op 170 tot 190 s (34 s voor het einde) blijft toegestaan.
- Kotlin niet gecompileerd en niet beluisterd.
- Niet bekend: of het raster van 116 bpm echt fout is of dat het nummer beide pulsen heeft (zonder audio niet te beslissen; de fix kiest het veilige pad). Als de oude analyse pas laat binnenkomt (minder dan 6 s voor de start van de overlap) blijft het eerste plan staan. Meer exits en gewone fades verwacht bij nummers met een lang luid slot.
- Niet gewijzigd: de rampe van A (8 beats), de glijtijd van B (16 beats), de tempogrens van 40%, het raster zelf (de analyse van het tempo).
- Terugzetten: `tempoDoubt` altijd false laten teruggeven, en de regel over `kMaxEarlyExitSec` in `evalBlend` weghalen.

## 0.0.71 (planfout hersteld, tempo-prior breder, Next-lijst)
- Log 0.0.70 (14:31 tot 14:48): vier nummers werden na 11, 12, 16 en 51 s verlaten (7cii, 5Mvj, 61U5, 5OKH). Oorzaak was mijn eigen wijziging in 0.0.70: een nummer in twijfel kreeg een lege kaart in het plan; bij A kent de planner dan het einde van de muziek niet en start de fade op `nowA + 1,0` s. Zie ook `ERRORS.md`.
- Fix: een nummer in twijfel houdt zijn kaart; bij twijfel (A of B) is `maxTempoChange` 0, dus een exit of fade aan het einde van A zonder tempoverandering. De grens voor een double drop is nu `min(kDoubleDropMaxTempo, maxTempoChange)`. Proef met synthetische nummers (nowA 8 s): grens 0,40 geeft een blend op 198,5 s van 219,3 s, grens 0,0 een exit op 206,1 s, een lege A een fade op 9,0 s.
- Tempo (`estimateTempo` in `analyzer.cpp`): de voorkeur voor 125 bpm is breder (1,2 octaaf, was 0,8). Gemeten met de echte analyzer op acht nummers van de gebruiker (flac, 44,1 kHz, zelfde halvering als `decimatedMono`): alleen "1991 - Castles In The Sky" verandert (116,03 naar 86,93 bpm; de twee kandidaten waren bijna gelijk: 0,970 tegen 0,964 met de oude prior, daarna 1,086 tegen 0,975). De andere zeven blijven gelijk: Attention 159,9, Ghost Rider 138,0, Crawling 105,1, Better Off Alone 136,7, Feed The Flame 155,0, I Do 85,0, Fugue State 124,0. Onafhankelijke controle met onsetafstanden (kick, middenband, hats) ondersteunt die zeven; voor Castles In The Sky wijst de afstand van ongeveer 175 bpm tussen middenband- en hat-onsets op achtsten bij 87 bpm.
- Niet bewezen: of 87 voor Castles In The Sky juist is. Een test van het contrast tussen onsetsterkte op de beats en halverwege gaf voor het raster van 116 een hoger contrast (2,17 tegen 1,37), dus de meting is niet doorslaggevend. Het oor van de gebruiker beslist (tik de maat mee). De oudere Kotlin-analyse gaf bij vergelijkbare nummers 87,0.
- Niet getest: de nummers uit het log met 116,4, 146,9, 129,0 en 150,2 bpm (oude analyse 87,0, 109,9, 94,2 en 100,2) zijn niet meegeleverd, dus of de bredere prior die rechtzet is een verwachting.
- Next-lijst (`NowPlayingScreen.kt`, Up next-paneel): de kop "Now and next" heet nu "Next" en het nummer dat speelt staat niet meer in de lijst (alleen de komende nummers). Geldt niet voor de bewerkstand, die de hele wachtrij toont. De lijst scrolt nog naar de kop; staat er geen geschiedenis boven, dan is er geen kop, zoals eerder.
- Host-tests (analyzer, planner, techniques, sections, engine) slagen. Kotlin niet gecompileerd en niet beluisterd.
- Terugzetten: `kTempoPriorOctaves = 0.8`.

## 0.0.72 (tempo: de juiste pulslaag kiezen met de beattracker)
- Probleem: `estimateTempo` geeft een gok die op 3/4 of 3/2 van de echte pulslaag kan liggen (Blank Space 128, echt 96; School 123, echt ongeveer 90). De beattracker legt daarna een net raster op die verkeerde laag, en de planner mixt op dat raster. In het log van 15:09 tot 15:39 viel hierdoor 4 van 8 overgangen terug op een cut, filter of echo.
- Nieuw: `chooseTempo` in `analyzer.cpp`. De 6 sterkste autocorrelatiepieken (zonder voorkeur voor 125 bpm) worden elk door `trackBeats` gehaald. Een raster krijgt als score het aandeel sterke onsets binnen 30 ms van een tik of halve tik, gecorrigeerd voor toeval (`gridCoverage`). De beste laag wint als hij 1,3 keer beter is dan de beste kandidaat buiten zijn familie (0,5, 1 of 2 keer); anders blijft de oude gok. Binnen de winnende familie kiest dezelfde formule als voorheen het octaaf (70 tot 180 bpm).
- Meting (15 nummers, bpm uit VirtualDJ en andere bronnen, 4 daarvan geopend en gecontroleerd): in de goede pulslaag 14 van 15, was 13 van 15. Verbeterd: Blank Space (96) en School (90, nu 91,6). Niet veranderd: de andere 13, waaronder Nooit Meer Slapen (110) dat zonder de drempel van 1,3 verslechterde (zekerheid 1,17). Die drempel is gekozen op basis van dat ene nummer.
- Niet getest: de host-tests (op verzoek niet gedraaid), de Kotlin en de nummers 7uxn, 3b1dw en 0r1tq uit het log (niet beschikbaar). De waarheidswaarden zijn deels van bronnen die zelf twijfelden.
- Bekend open: het afgekapte bestand 0YmaZ (16,7 s decoder-uitkomst van een nummer van 294 s) is niet opgelost in deze versie.
- Terugzetten: in `analyzeTrack` `chooseTempo(f)` weer `estimateTempo(f)`.

## 0.0.73 (AutoMix-melding, AutoMix-pagina met scan van de bibliotheek)
- Op verzoek van de gebruiker (2026-10-17), terwijl hij 0.0.72 test. Niet gecompileerd, niet getest op een telefoon, host-tests niet gedraaid.
- Melding bij het aanzetten van AutoMix (in het spelerblad en op de nieuwe pagina): "AutoMix is made for downloaded songs. A song that is not downloaded is first read in full from your server (this uses data) before it can be mixed." Met een vinkje "Don't show this again" (sleutel `auto_mix_notice_hidden`, niet in de back-up). De gebruiker schreef "AutoMix werkt alleen bij gedownloade bestanden"; de code laat gestreamde nummers ook toe (ze gaan eerst naar de cache), dus de melding zegt dat, niet "alleen".
- Nieuwe pagina Instellingen, AutoMix (`SettingsAutoMixScreen`): schakelaars AutoMix en Beat matching, de uitleg die eerst bij Playback stond (daar verwijderd: sectie "Advanced AutoMix"), en "Scan library": "X of Y downloaded songs scanned", voortgangsbalk, knop Scan library / Stop. De stems-schakelaar heeft geen scherm en blijft zo (er is geen knop voor).
- De scan (`MixLibraryScan`) gebruikt dezelfde `MixLibrary.scan` als de scan tijdens het afspelen, met `localOnly` (alleen gedownloade bestanden, niets van de server). Eén decodering en analyse tegelijk voor de hele app (`gate` staat nu in de companion). Nummers die nog niet gescand zijn, worden tijdens het afspelen nog steeds vanzelf gescand. De scan loopt zolang de app draait (geen service).
- "Gescand" = in `MixFacts` bekend, ook als er geen duidelijke beat was. De scan tijdens het afspelen filtert nu ook op `known` in plaats van `bpm == null`; nummers zonder beat werden voorheen bij elke start opnieuw geprobeerd (log: `tempo scan 0kDSh: no clear beat` drie keer).
- `MAP_VERSION` van 5 naar 6: de nieuwe tempokeuze van 0.0.72 geldt alleen voor nummers die opnieuw worden geanalyseerd. De feitenlijst heet nu `automix_facts_6.tsv` en volgt de versie; de oude bestanden worden bij het laden verwijderd. Gevolg: elk nummer wordt één keer opnieuw geanalyseerd (de scan van de bibliotheek doet dat in een keer).
- Niet gedaan: het afgekapte bestand (0YmaZ) en AirPlay-optie B.
- Terugzetten: `MAP_VERSION = 5`, de pagina en de dialoog verwijderen.

## 0.0.74 (planner: regels voor wat een DJ nooit doet)
- Op verzoek van de gebruiker (2026-10-17): "druk gaat niet in rustig" en meer van dit soort logische regels, universeel (geen instelling per nummer). Alleen `cpp/mix/planner.cpp` en `host/test_planner.cpp`. Host-test van de planner gedraaid (alles ok, nieuwe test faalt op 0.0.73 en slaagt nu). Kotlin niet gecompileerd, niets beluisterd.
- Regel 1, druk naar rustig: energie A >= 0,8 en energie B <= 0,6 keer A is geen blend meer. Geldt voor alle blendsoorten (secties en fraseplanner). De uitgangen (echo, filter, cut) krijgen voorrang; is er geen uitgang, dan blijft de blend als laatste redmiddel (in plaats van de fade van 3 s). Oorzaak: de fraseplanner (stap 1b) gaf een blend terug voordat de uitgangen bekeken werden (log cc3da4ff, 1F4k naar 7ovX, 1,17 naar 0,56).
- Regel 2, energiesprong kost altijd: 0,8 per eenheid omlaag, 0,3 omhoog (opbouw is goedkoper). Vervangt de oude 0,25 keer het verschil in de fraseplanner en komt nieuw in de sectieblends.
- Regel 4, toonsoortbotsing weegt dubbel: 0,8 naar 1,6 per 16 beats in beide planners. `maxBlendClash` (0,55) is niet aangepast.
- Regel 5, lange blend over een rustig begin: is energie B < 0,4 en energie A >= 0,5, dan maximaal 16 beats (niet als de gebruiker zelf een lengte koos). Afwijking van het voorstel: bij rustig A en rustig B (outro in intro) blijven 32 en 64 beats mogelijk, want dat is de bedoeling van de relaxed blend.
- Niet gedaan: regel 3 (tempoplafond 10%), op verzoek van de gebruiker. De drempels 0,8 / 0,6 / 0,4 / 0,5 en de gewichten zijn schattingen op 49 plannen uit de logs, niet getoetst op oren.
- Terugzetten: de constanten en helpers bovenaan `planner.cpp` (`busyIntoCalm`, `energyJumpCost`, `longOverCalm`, `kKeyWeight`) en hun aanroepen verwijderen.

## 0.0.75 (extra logging voor planwissels)
- Op verzoek van de gebruiker (2026-10-17). Alleen logging, het gedrag van de planner is niet veranderd. Host-test van de planner draait nog zonder fouten; Kotlin en JNI niet gecompileerd.
- Event `plan` heeft erbij: `vocalA`, `vocalB` (aandeel zang over de overlap), `vocalOverlap` (aandeel beats waar beide zingen; de planner rekent 4 keer dit als kost; -1 = onbekend of een uitgang), `vocalKnownA`, `vocalKnownB` (zangmeting aanwezig in de kaart) en `playGen`. De afgevallen kandidaten staan al in `why`.
- Nieuw event `seek`: `id`, `fromSec`, `toSec`, bij een seek van de gebruiker binnen het nummer dat de engine speelt. Na zo'n seek wordt het plan opnieuw gemaakt (zie `restart`).
- Het planarray naar Kotlin is 25 lang (was 24; index 24 is `vocalOverlap`); oude arrays van 24 worden nog gelezen.
- Terugzetten: de velden en het event verwijderen, array weer 24.

## 0.0.76 (planner: twee regels bijgesteld op het log van 0.0.75)
- Op verzoek van de gebruiker (2026-10-18: "pas alles aan wat bijdraagt aan een betere mix"). Alleen `cpp/mix/planner.cpp`. Host-tests planner, sections en vocals gedraaid zonder fouten (een eerste versie van de zangregel liet `test_vocals` falen en is daarom op beats in plaats van aandeel gezet). Kotlin niet gecompileerd, niets beluisterd.
- Druk naar rustig: drempel voor "druk" van energie A 0,8 naar 0,7. Reden: in het log stond een korte swap 0,74 naar 0,12 (3Dw8Q naar 5AkV3) die er net onderdoor viel. Neveneffect: ook relaxed blends van 0,7 tot 0,8 die naar minder dan 60% gaan, worden een uitgang als die er is.
- Nieuw: een blend waarin beide nummers samen méér dan 16 beats zingen (`vocalOverlap` keer lengte) is een "slechte blend" en geeft voorrang aan een uitgang; de blend blijft als laatste redmiddel. Reden: 6qulV naar 5kqJf, 64 beats, 62% = 40 beats samen, kost 3,28, won toch van elke uitgang. Korte overlappen blijven mogelijk (A's mids gaan eruit; de test in `test_vocals` eist dat). De grens 16 is een schatting op dit ene geval (het volgende geval in het log: 1AEui, 25% van 64 = 16 beats, blijft net toegestaan).
- Niet gedaan, bewust: tempoplafond. Het log laat zien dat de grote fasefouten bij handoff (15,0 ms en 10,5 ms) bij de twee lange blends zaten met de grootste tempokloof (138 tegen 95 bpm en 87 tegen 125 bpm, elk nummer 15 tot 18% versneld of vertraagd); bij 24% kloof was de fasefout 1,4 ms. `MAX_TEMPO_CHANGE` is 0,40 in `MixConductor.kt` (eerder bewust verhoogd van 0,20). De gebruiker heeft het tempoplafond eerder uitgesloten; niet aangeraakt.
- Terugzetten: `kBusyEnergy = 0.8`, `poorBlend` weer `busyIntoCalm`.

## 0.0.77 (planner: duur blend wijkt voor uitgang, rekken kost meer, uitleg in het log)
- Op verzoek van de gebruiker (2026-10-18: "veel rare overgangen en filter out"), na analyse van het log van 0.0.76 (17 plannen). Alleen `cpp/mix/planner.cpp` en de test van de zang. Host-tests planner, sections en vocals gedraaid zonder fouten (de zangtest is aangepast: bij zang over zang mag nu een uitgang winnen). Kotlin niet gecompileerd, niets beluisterd.
- Kostenregel: een sectieblend die meer dan 0,5 duurder is dan de beste uitgang verliest van die uitgang, en een blend boven kost 1,5 wint niet meer vanzelf (de uitgangen worden dan wel bekeken). Reden: 4zIE naar 7GYy was een relaxed blend van kost 3,9 met 12 beats zang over zang en won omdat blends altijd wonnen. Blends in de oude fraseplanner (kost 1,4 tot 2,3 op een andere schaal) zijn niet veranderd.
- Rekken: `kStretchCost` van 1,5 naar 3,0. Reden: in de logs van 0.0.75 en 0.0.76 had elke blend met een gedeelde tempowijziging van 6% of meer per nummer 3,7 tot 15 ms faseverschil bij de handoff (8,2 ms bij 91 naar 140 bpm), de andere ongeveer 0. Dit is een zachte versie van het tempoplafond (geen harde grens). Terugzetten: 1,5.
- Log: in `why` staat nu een regel "blend turned down: ..." (stijl, lengte, energie, kost, toonsoortbotsing, zang over zang, kost van de uitgang) als een blend wel gevonden is maar de uitgang won. Dat ontbrak: bij de eerste overgang in het log (159 naar 97 bpm) was `relaxed ... accepted 1` en toch een filter-uit, zonder te zien waarom.
- Niet opgelost, wel gezien: veel "filter out" komen van nummers waarvan A in een luid stuk eindigt (geen uitloop) en B rustig begint (alle relaxed-kandidaten `too early`, rolling zonder drop). Daar is een uitgang de enige optie; de bijtekst "tempo gap above 20%" in `reasons` is dan misleidend (het staat er altijd bij een noodUitgang).
- Terugzetten: `kStretchCost = 1.5`, `kBlendExitMargin`/`kBlendCostCeiling` en de `turnedDown`-regel verwijderen.

## 0.0.78 (DJ-uitgangen met effecten en DJ-volgorde in de wachtrij)
- Op verzoek van de gebruiker (2026-10-18: richting 1 en 2 uit de analyse). Host-tests planner, sections, vocals en techniques gedraaid zonder fouten (techniques is uitgebreid met alle nieuwe uitgangen). Kotlin en de Android-build niet gecompileerd (de gebruiker compileert zelf), niets beluisterd; alle effectparameters (galm, riser, impact, remsnelheid) zijn schattingen die oren nodig hebben.
- Nieuwe uitgangen (Template 6 tot 9): reverb out (galm rinkelt door onder B, 9 s), brake out (plaat remt af over 2 beats, toonhoogte zakt), spin back out (plaat stopt en draait terug), beat roll out (laatste beat van A herhaald in 1/4, 1/8, 1/16). Vlaggen: riser (ruis stijgt over de laatste 4 beats) en impact (lage dreun op de eerste beat van B). DSP in `dsp.h` (`Reverb`, `AudioRing`, `RingFx`, `Riser`, `Impact`), gebruik in `Engine::step` (kOutro).
- Planner: `refineExit` maakt van de gewone uitgang (echo, filter, cut) de uitgang die bij het moment past, vast per nummerpaar (hash), dus niet willekeurig bij herplannen. Cut op een drop krijgt impact en soms riser (soms roll plus riser). Zachte uitgangen kiezen met gewichten naar genre, energie aan het eind van A en zang: bij zang geen spin of roll, brake sterk verminderd; roll vraagt energie >= 0,4; geen brake of spin bij house of techno. Alleen effecten die in de resterende tijd passen.
- Log: `exitName` toont bijvoorbeeld "beat roll out + riser + impact onto a drop"; de misleidende tekst "tempo gap above 20%" voor uitgangen is vervangen.
- (Vervangen in 0.0.79: de knop "DJ order" is weg, de volgorde wordt automatisch gemaakt.) Shuffle en auto-fill gebruiken dezelfde `MixOrder`.
- Slot tegen steeds dezelfde muziek in `MixOrder`: dezelfde artiest wacht 3 nummers, een genre maximaal 3 keer achter elkaar, na 4 nummers op één tempo gaat een ander tempo voor (plateau, +1,0 kost) en de keuze is uit de beste 4 (was 3). Drempels zijn schattingen.
- Niet aangeraakt, wel gezien: tempoalarm 4/3, drempel 0,7 of 0,8, schuivende relaxed blends.
- Terugzetten: Template 6 tot 9 en `refineExit` verwijderen, `CHOICES = 3`, knop en `djOrderUpcoming` verwijderen.

## 0.0.79 (DJ-volgorde automatisch bij AutoMix)
- Op verzoek van de gebruiker (2026-10-18). Kotlin niet gecompileerd (de gebruiker compileert zelf), niets beluisterd.
- De knop "DJ order" is verwijderd. Bij het aanzetten van AutoMix (met beat mixing aan, zonder shuffle) worden de nummers na het huidige nummer in DJ-volgorde gezet, maar alleen de gedownloade. Niet gedownloade nummers blijven op hun plek; de gedownloade wisselen onderling van plek.
- Handmatig toegevoegde nummers ('Play next', 'Add to queue') krijgen de markering `EXTRA_PINNED` en blijven staan waar de gebruiker ze zette. Bij elke handmatige toevoeging met AutoMix aan wordt de rest opnieuw geordend, met de vaste nummers als buren: het nummer vlak voor een vast nummer wordt gekozen om ook daarbij te passen (`MixOrder.queueAround`).
- Niet veranderd: shuffle (`playSongs`) en auto-fill ordenen nog alle nummers, ook niet-gedownloade. Dat was niet gevraagd.
- Beperking: de markering zit in de extras van het mediaitem; of die een herstart van de app overleeft hangt af van `QueueStore` (niet gecontroleerd).
- Terugzetten: `djOrderUpcoming` aanroepen in `setAutoMix`, `playNext` en `addToQueue` verwijderen.

## 0.0.80 (tempo: de nummers zelf laten beslissen i.p.v. blokkeren)
- Op verzoek van de gebruiker (2026-10-18: "zorg dat de tempo analyse betere resultaten geeft", optie "Beslissen i.p.v. blokkeren"), na het log van 0.0.78: 3 van 5 plannen waren een uitgang door `tempoDoubt` (4/3 tussen grid en oude analyse), terwijl de grids van A en B onderling 0,03% tot 2,4% uit elkaar lagen. Kotlin niet gecompileerd (de gebruiker compileert zelf), niets beluisterd; nieuwe host-test `test_tempo` en de bestaande tests gedraaid.
- Analyse (`analyzer.cpp`): `chooseTempo` bewaart per kandidaat-tempo (de 6 sterkste) hoeveel van de sterke onsets het grid dekt (`gridCoverage`, dezelfde maat als de keuze zelf), plus de dekking van het gekozen grid. Staat in `TrackMap::tempoEvidence`, als blok achter de zangwaarden in de kaart (JNI pack en unpack, `MixMap.tempoEvidence`, `withVocal` bewaart het blok).
- Plan (`MixConductor.tempoCheck`): bij verschil tussen grid en oude analyse beslist de dekking. Het grid blijft staan als zijn dekking minstens die van de kandidaat bij het oude tempo is, of als het oude tempo geen kandidaat was. Alleen als het oude tempo beter past, blijft de twijfel (en dus een uitgang). Een kaart zonder bewijs houdt de twijfel zoals voorheen. Het log heeft nu `tempoCheckA` en `tempoCheckB` met de reden en de getallen.
- Beperking: past het oude tempo beter, dan kan het grid zonder audio niet opnieuw gelegd worden; het blijft bij een uitgang. Of het oordeel van de dekking klopt met de echte bpm is niet gemeten (geen bpm van de echte nummers). De analyse duurt per nummer iets langer (een extra dekkingsmeting als het gekozen tempo geen kandidaat was).
- `MAP_VERSION` van 6 naar 7: alle kaarten worden opnieuw gemaakt (ook de feiten voor de ordening), want de oude kaarten hebben het bewijs niet. Dat kost eenmalig analysetijd voor de hele bibliotheek.
- Terugzetten: `MAP_VERSION = 6`, `tempoCheck` terug naar de oude `tempoDoubt` (alleen vergelijken), bewijsblok uit pack en unpack halen.

## 0.0.81 (Up Next: slepen zonder bewerkmodus, lang indrukken, scrollgedrag)
- Op verzoek van de gebruiker (2026-10-18), na het log van 0.0.80. Kotlin niet gecompileerd (de gebruiker compileert zelf), niets beluisterd of getest op een toestel.
- De knop Edit is weg uit de Up Next in de speler (`QueuePanel`). De drie streepjes staan standaard achter elk volgend nummer (alleen zonder shuffle) en slepen verplaatst het nummer direct in de speler (`PlaybackViewModel.moveQueueItem`) zodra de vinger loslaat.
- Lang indrukken op een nummer (ook in de geschiedenis) opent een menu: Download (alleen als het nog niet gedownload is), Add to playlist, Move to top (direct na het huidige nummer, alleen zonder shuffle en bij volgende nummers), Go to album (als het album bekend is; `QueueRow.albumId`), sterren (via `library.setRating`, op de server) en Share (tekst "titel - artiest" via het deelmenu van Android).
- Scrollgedrag: "Next" staat bij openen bovenaan met de geschiedenis erboven. Een snelle veeg omhoog vanuit de volgende nummers stopt bij de Next-kop; de volgende veeg gaat de geschiedenis in. Langzaam slepen met de vinger wordt niet begrensd. De rest van de UI is niet veranderd.
- Open: een nummer uit de wachtrij halen kon alleen in de bewerkmodus en kan nu niet meer (Clear haalt alles na het huidige nummer weg). Verplaatste nummers worden niet vastgezet voor de DJ-volgorde. Of de stop bij Next op een toestel goed voelt is niet geprobeerd; de aanpak (nested scroll op de fling) is niet gecontroleerd.
- Terugzetten: `QueuePanel`, `QueueSongRow`, `QueueRow` en `moveQueueItem` uit 0.0.80 halen.

## 0.0.82 (Up Next: Remove from queue in het menu)
- Op verzoek van de gebruiker ("deze optie moet ook in het menu zitten", gelezen als de optie om een nummer uit de wachtrij te halen). Kotlin niet gecompileerd, niets beluisterd.
- Lang-indrukmenu in de Up Next heeft nu "Remove from queue" (`PlaybackViewModel.removeQueueItem`). Het nummer dat nu speelt kan niet verwijderd worden. Geldt ook voor nummers in de geschiedenis.
- Terugzetten: het menu-item en `removeQueueItem` verwijderen.

## 0.0.83 (compileerfout in de Up Next)
- Build van 0.0.82 faalde bij `compileDebugKotlin`. Het log bevatte alleen de Gradle-stack, niet de `e:`-regels. Gevonden door de code na te lezen: `Modifier.animateItem()` stond in een lokale composable buiten de lazy-lijst (`QueuePanel.Entry`), waar die niet bestaat. Nu geeft de lijst de modifier door. Niet gecompileerd; of dit de enige fout was is niet zeker.

## 0.0.84 (fade-uitgang, latere instap van B, Up Next met vaste kop)
- Op verzoek van de gebruiker (2026-10-18: "brake out klinkt raar, waarom niet een fade out, fade in bij afwijkende energy", "zelfde voor spin back out", "B wordt te vaak te vroeg ingemixed", "optie 3", "bouw alles"), na het log van 0.0.83. Kotlin niet gecompileerd (de gebruiker compileert zelf), niets beluisterd; de C++ kant is met de host-tests gedraaid (`test_techniques`, `test_planner`, `test_vocals`, `test_tempo`, `test_engine`, `test_sections`, nieuw `test_fade`).
- Het log van 0.0.83 liet zien: alle 4 uitgangen waren brake of spin, steeds met een vol A (energie 0,87 tot 0,99) en een rustig begin van B (0,07 tot 0,55), en vrijwel elk plan liet B bij zijn eerste seconde beginnen.
- Nieuwe uitgang `FadeOut` (Template 10): A faded over vier beats uit (cosinus, gladde curve), B komt op de maatlijn binnen met een fade-in van één beat. Gekozen door de planner (in beide planners) alleen als de energie van A aan het eind en die van B aan het begin minstens 0,35 verschillen (`kFadeGap`). B kan dan ook op een latere sterke sectie instappen: de kosten tellen het energieverschil dubbel, dus de sectie die bij A past wint.
- Brake out en spin back out worden niet meer gekozen (gewicht 0 in `refineExit`; de code blijft staan). Roll, echo, filter, reverb en cut blijven.
- Latere instap: `kMaxDropSec` van 24 naar 90 s, voor de drops van B die als instap voor een uitgang gelden. Rolling en double drop keken al naar alle drops van B (mijn eerdere uitleg dat die op 24 s begrensd waren klopte niet); de overslagkosten daarvan zijn van 0,5 naar 0,15 per minuut (`kSkipCostDrop`), omdat instappen op een latere drop daar juist het doel is.
- Beperking: wat een blend in de weg zit is vooral "too early" (A moet binnen 45 s van zijn eind weg); dat is niet veranderd. Of de fade goed klinkt, en of een latere instap in de praktijk past, is niet gehoord. De drempel 0,35 en de fade-lengte van 4 beats zijn gekozen, niet gemeten.
- Log: `exitName` heet nu "fade out and in"; Kotlin `MixPlan.FADE_OUT = 10`.
- Up Next (`QueuePanel`): de kop "History" blijft vastgeplakt bovenaan terwijl je door de geschiedenis scrolt; bij de Next-kop wordt die vervangen door "Next". Een snelle veeg stopt nu bij Next vanuit beide kanten (van Next naar de geschiedenis en van de geschiedenis naar Next); de volgende veeg gaat door. Het begin van de lijst en de rest van de UI zijn niet veranderd. De positie wordt gemeten op het eerste nummer na de Next-kop. Onzeker: of `stickyHeader` en de fling-stop op een toestel goed aanvoelen is niet geprobeerd.
- Terugzetten: `kMaxDropSec = 24`, `kSkipCostDrop` weg, `k < 4` terug naar `k < 3` in beide uitgang-lussen, gewichten van brake en spin terug, `stickyHeader` terug naar `item`.

## 0.0.85 (Up Next: grotere kopteksten)
- Op verzoek van de gebruiker ("maak de next en history text iets groter"). De teksten "History" en "Next" in de Up Next gaan van 13 sp naar 16 sp. Verder niets veranderd; Kotlin niet gecompileerd.
- Terugzetten: `fontSize = 13.sp` in `QueueHeader`.

## 0.0.86 (zang scheiden met stems, minder zang over elkaar, latere en betere instap van B)
- Op verzoek van de gebruiker (keuzes: terugval = zo min mogelijk zang over elkaar, stems alleen voor gedownloade muziek, schakelaar in de instellingen, modelbestanden staan in zijn project).
- **Stems (nieuw):** `MixStems.kt` splitst het stuk van A en van B rond de overlap met Spleeter (4 stems), zet het weer samen met de zang op een eigen volume en legt het als overlay over het gedecodeerde nummer (`PcmBuffer` overlay, `NativeMix.copy/setOverlay/clearOverlay`). De zang van A gaat in 1,5 s uit aan het begin van de overlap, de zang van B komt na 60% van de overlap in (1,5 s). Alleen bij een blend waar beide nummers zingen (`vocalOverlap` > 0,05) en alleen als beide nummers gedownload zijn. Is het niet op tijd klaar of mislukt het, dan wordt de overgang opnieuw gepland zonder stems (planner: zo min mogelijk zang over elkaar).
- **Schakelaar:** "Separate vocals" in Instellingen > AutoMix (bestaande instelling `stemMix`, standaard aan), doorgegeven via `MixConductor.stems`.
- **Planner:** oude fase-planner alleen nog voor nummers met minder dan 3 secties; stap 1b (legacy blend als geen sectieblend past) verwijderd. Zang telt zwaar zonder stems (gewicht 12, blends met meer dan 4 beats zang over elkaar worden afgewezen), licht met stems (1,5).
- **Instap B:** meer instappunten (start of een drop binnen 90 s), B komt maximaal 40% van zijn lengte binnen, sprongkosten voor de fade terug op 0,5. Fade in op een drop is 20 ms, anders een halve beat.
- Niet gebouwd: "pitch up en down is bijna niet aanwezig" (de gebruiker heeft niet gezegd wat hij bedoelt).
- Kotlin niet gecompileerd, niets beluisterd. Snelheid van de stem-scheiding op de telefoon is niet gemeten. Host-tests (techniques, planner, vocals, tempo, fade, engine, sections) slagen.
- Terugzetten: `MixStems.kt` weg, `stems`-regels in `MixConductor` en `PlaybackService`, de schakelaar in `SettingsAutoMixScreen`, en de overlay-API in C++/`NativeMix`.

## 0.0.87 (geen cut bij groot energieverschil)
- Op verzoek van de gebruiker (optie 1): in `planner.cpp`, in beide uitgang-lussen, wordt een cut overgeslagen als het energieverschil tussen A en de instap van B 0,35 (`kFadeGap`) of meer is. Echo en filter worden ook overgeslagen bij zo'n verschil als een fade mogelijk is (verschil met het begin van B ook 0,35 of meer); dat was nodig omdat `test_fade` anders een reverb-uitgang koos in plaats van een fade. Daardoor wordt het een fade.
- Reden: log 17:09:30, "cut + impact onto a drop" met energie 1,75 naar 0,96 klonk slecht.
- Host-tests (planner, fade, sections, vocals, tempo, techniques, engine) slagen. Kotlin niet aangeraakt, niets beluisterd.
- Terugzetten: de twee regels "A big difference in energy is never a cut" in `planner.cpp` weghalen.

## 0.0.88 (cut alleen bij bijna gelijk tempo)
- Op verzoek van de gebruiker (optie 1): `planner.cpp` kent `cutTempoOk()`: een cut is alleen toegestaan als de tempo's van A en B, gevouwen met factor 1, 2 of 1/2, binnen 3% (`kCutMaxTempoGap`) liggen. Een tempo in twijfel (geen tempoverandering toegestaan, `maxTempoChange` 0) of onbekend telt als niet dichtbij. Geldt in beide uitgang-lussen (sectieplanner en oude planner).
- Reden: log 17:38:20, cut + impact van 151 (feitelijk 76) naar 80 bpm klonk slecht.
- Test: `test_fade` controleert dat er geen cut komt tussen tempo's 24% uit elkaar. Host-tests (planner, fade, sections, vocals, tempo, techniques, engine) slagen. Kotlin niet aangeraakt, niets beluisterd.
- Terugzetten: `cutTempoOk` en de twee regels `if (k == 2 && !cutTempoOk(a, b, s)) continue;` weghalen.

## 0.0.89 (fade alleen bij echt verschil, echo en filter op een drop)
- Op verzoek van de gebruiker (optie 2): in `planner.cpp` mag een fade alleen nog als het energieverschil tussen A en de gekozen instap van B (ook een drop) minstens 0,35 (`kFadeGap`) is; tot nu toe keek de regel alleen naar het begin van B. Echo en filter mogen nu ook op een drop van B (sectieplanner).
- Reden: log 0.0.88, 14 van 31 fades hadden een energieverschil onder 0,35 (bijvoorbeeld 1,00 naar 1,04); door de cut-tempoafspraak van 0.0.88 bleef daar vaak alleen een fade over.
- Test: `test_fade` controleert dat er geen fade is bij gelijke energie. Host-tests (planner, fade, sections, vocals, tempo, techniques, engine) slagen. Kotlin niet aangeraakt, niets beluisterd.
- Terugzetten: de twee regels `k == 3 && ... < kFadeGap` in de kostenlussen terugzetten en `else if (e.drop && k != 3) { continue; }` in de sectieplanner terugplaatsen.

## 0.0.90 (stap 1 van 4: B komt eerder op volume in een long blend)
- Op verzoek van de gebruiker (opmerking bij 0.0.89: "bij een long blend komt vaak B te laat op volume"). In `engine.cpp`, `automate()`, long blend: de melodie (mids) van B komt na één maat (beat 4) binnen in plaats van na 25% van de blend (beat 8 bij 32 beats, beat 16 bij 64); als de mids van A weg moeten voor de zang (`kFlagVocalCut`) wisselen de mids direct (beat 0 tot 2), zodat er geen gat in de melodie valt. De bas wisselt op 37,5% van de blend in plaats van 50% (beat 12 in plaats van 16 bij 32 beats; bij 16 beats ongewijzigd op beat 8).
- Reden: B had tot beat 8 alleen zijn hoge tonen en tot beat 16 geen bas; dat is wat je hoorde als laag volume.
- Nieuwe test `host/test_blend_levels.cpp` (compileren met `-I mix host/test_blend_levels.cpp mix/analyzer.cpp mix/planner.cpp`; hij neemt `engine.cpp` zelf mee). Host-tests (planner, techniques, engine, blend_levels) slagen. Kotlin niet aangeraakt, niets beluisterd.
- Terugzetten: in `automate()` `midIn = max(4, round(L*0.25/4)*4)`, `midLen = 1` en `bassSwap = max(8, round(L*0.5/4)*4)`.

## 0.0.91 (stap 2 van 4: AutoMix-menu met standen)
- Op verzoek van de gebruiker. Kort tikken op de AutoMix-knop zet AutoMix aan of uit; lang drukken opent een menu: Mixed | Energy, Build-Up | Chill, Cooldown | Tempozone, Harmonic | Genres. Tempozone opent de zones (onder 90, 90-110, 110-130, 130-150, boven 150 bpm), Genres een tweede menu met de genres van de gedownloade nummers (meerdere kiezen, daarna Apply).
- Een stand vervangt de komende nummers (het huidige nummer blijft) door een nieuwe lijst van 40 nummers; alleen gedownloade en gescande nummers. De laatste stand blijft bewaard (`auto_mix_mode`, `auto_mix_zone`, `auto_mix_genres`). Bij minder dan 10 nummers voor de boeg worden er 30 bijgevuld (lus in `PlaybackService`). AutoPlay doet niets zolang een stand actief is. Past er niets, dan komt er een melding.
- Standen: Energy = tempo minimaal 120 bpm. Chill = tempo hoogstens 100 bpm en energie hoogstens 0,45 (drempels zijn een gok, af te stellen). Build-Up en Cooldown = energie stijgend of dalend. Harmonic = ketting van passende toonsoorten (`MixOrder` met zwaardere toonsoortweging). Mixed = alles. Tempo: het raster-tempo als het met de oudere analyse overeenkomt (factor 1/2/2), anders de oudere analyse.
- Energie per nummer (nieuw in de scan, `MixMap.energyIndex`: 0,6 volume + 0,4 aandeel beats in hoge secties) staat in `automix_facts` (vierde kolom, MAP_VERSION ongewijzigd); oude nummers krijgen hem via `backfillEnergy` uit de opgeslagen kaarten.
- Kotlin niet gecompileerd, niets beluisterd. Tekst in Instellingen > AutoMix uitgebreid met een zin over het menu.
- Terugzetten: `MixModes.kt` weg, `chooseAutoMixMode`, de bijvul-lus, het menu in `NowPlayingScreen` en de drie instellingen weghalen.

## 0.0.92 (stap 3 van 4: stem-mix als eigen overgang)
- Op verzoek van de gebruiker ("echte mixes"). Is "Separate vocals" aan (stems) en wordt een long blend van minstens 16 beats gekozen waarvan de overlap in beide nummers hoogstens 34 s duurt, dan krijgt het plan de vlag `kFlagStemMix` (planner, `withStemMix`). Het is geen nieuw plansoort: de blend, het moment en de instap blijven zoals de planner ze koos.
- Werking: `MixStems` splitst het stuk van A en van B in zang, drums, bas en melodie (Spleeter) en geeft elke stem een eigen volumecurve in de overlay. B: melodie komt binnen op 10% tot 40% van de overlap (over de beat en bas van A), drums en bas van B komen op 50% binnen (6% breed) terwijl die van A weggaan, de melodie van A gaat weg van 55% tot 85%; zang zoals in 0.0.86 (A uit in de eerste 1,5 s, B erin vanaf 60%).
- Engine: met de vlag en een overlay op beide nummers (vastgelegd bij het begin van de blend, `stemLive`) blijft de EQ vlak en doet de engine alleen een korte fade-in van B (halve beat) en haalt A in de laatste beat weg. Ontbreekt een overlay, dan speelt de gewone blend (EQ-swaps). Komt de overlay te laat of mislukt het splitsen, dan wordt opnieuw gepland zonder stems (bestaande terugval).
- Kosten: 25 tot 55 s rekentijd per overgang (gemeten in logs bij 0.0.88), alleen op de telefoon, lage prioriteit. De log toont "(stem mix)" bij het plan.
- Tests: `test_blend_levels` (vlakke EQ, B stil bij start, A weg aan het eind, gewone blend zonder overlay) en `test_vocals` (vlag alleen bij long blend met stems aan). Alle host-tests slagen. Kotlin niet gecompileerd, niets beluisterd; de percentages van de curves zijn een eerste gok.
- Terugzetten: `kFlagStemMix`, `withStemMix`, `stemLive`, `hasOverlay` en de curves in `MixStems.build` weghalen.

## 0.0.93 (stap 4 van 4: beat loops)
- Op verzoek van de gebruiker ("er wordt totaal geen gebruik gemaakt van beat loops"). Zou de planner anders een uitgang kiezen (terugval-fade, echo, filter, reverb, brake, spin of roll; een cut en een fade out blijven staan), dan probeert hij eerst een long blend van 16 beats waarin de laatste 8 beats (2 maten) van A zich herhalen: de eerste helft van de overlap is die 8 beats, de tweede helft herhaalt ze. Het lus-punt is de laatste maatlijn van A (`loopBeat`); de overlap loopt daardoor tot of voorbij het einde van A (planner: `planLoop`, vlag `kFlagLoop`, plan-stijl 5, velden `loopBeat` en `loopBeats`).
- Voorwaarden (geen aannames over meer): raster van A en B met betrouwbaarheid van minstens 0,5 (`kLoopMinConfidence`, een eerste gok), tempo niet in twijfel (maxTempoChange > 0), tempo's aanpasbaar, sleutels botsen niet te veel, geen te rustige B na een drukke A, zang zoals bij gewone blends.
- Geluid: `MixLoops.build` maakt een overlay op A vanaf het lus-punt die de 8 beats herhaalt (3 ms in- en uitfade bij elke naad tegen klikken) en voorbij het einde van A doorloopt; `PcmBuffer` laat een overlay tot 30 s voorbij het einde toe (`frames()` is dan langer, `baseFrames()` de gedecodeerde lengte). Lukt dat niet of is het te laat, dan wordt opnieuw gepland zonder lus (`loopFailedFor`). Geen stems nodig, snel.
- Geen aparte instelling. Het log toont "(beat loop)" en `loops` bij het plan, plus een `loop`-gebeurtenis.
- Tests: nieuwe `host/test_loop.cpp` (lus alleen met loops aan, blend van 16 beats, lus-punt 8 beats na de start op maatlijnen, overlap tot voorbij het einde van A, geen lus bij onbetrouwbaar raster of tempo in twijfel). Alle host-tests slagen. Kotlin niet gecompileerd, de C++ van `pcm_buffer.h` en de JNI zijn niet op de host gebouwd, niets beluisterd.
- Terugzetten: `planLoop` en de omhulling `planTransition` (terug naar `planNoLoop`), `MixLoops.kt`, `startLoop` in `MixConductor`, de `loops`-parameter in JNI en `NativeMix`, de uitbreiding in `pcm_buffer.h`.

## 0.0.94 (scandata wissen, namen in het log, handmatig toegevoegde nummers blijven)
- Op verzoek van de gebruiker. Instellingen > AutoMix > Scan library: knop "Delete scan data" met bevestiging (`MixLibraryScan.wipe`): stopt de scan en verwijdert de beat-rasters (`automix_maps`), de songfacts (tempo, toonsoort, energie; `MixFacts.clear`) en de gemeten zang (`MixVocals.clear`). De oudere hele-nummer-analyse (`beatmix.json`, nodig voor de DJ-volgorde en de tempocontrole) blijft staan; dat is een eigen keuze, niet gevraagd. Een nummer dat op dat moment decodeert of speelt houdt zijn kaart in het geheugen tot het vrijkomt.
- Een nummer dat niet te decoderen is, komt in het log met artiest en titel (`decode failed: ...`, `tempo scan ...: could not decode (...)`), niet alleen met de id.
- Kiest de gebruiker een AutoMix-stand, dan blijven nummers die hij zelf aan de wachtrij toevoegde (gepind, bestond al voor de DJ-volgorde) staan; de nieuwe lijst komt erna. Ze doen niet mee in de nieuwe lijst, dus komen niet dubbel voor.
- Kotlin niet gecompileerd, niets beluisterd. C++ ongewijzigd.
- Terugzetten: `wipe`/`clear`-functies en de knop met dialoog weg, de `nameOf`-logregels weg, in `chooseAutoMixMode` de pinned-regels terug naar `removeMediaItems(first, count)`.

## 0.0.95 (raster herstellen bij het verkeerde tempo, lus alleen over stabiele maten)
- Op verzoek van de gebruiker (ja op het herstellen van het raster bij de 3/4-, 4/3- en 3/2-conflicten; beat loops "alleen waar het goed is, automatisch").
- Raster: `analyzeTrack` kent `forcedBpm` (0 = zoals voorheen) en bouwt het raster op dat tempo. `MixTempo.fixed` (nieuw) draait na de analyse, zowel in de scan als bij de analyse tijdens het spelen: vallen raster en oudere hele-nummer-analyse (`BeatMixStore`) niet samen (factor 1, 2 of 1/2) en past een kandidaat op het oudere tempo beter bij de onsets dan het raster (dezelfde regel als de twijfelcontrole in `MixConductor`, die ongewijzigd is), dan wordt het raster opnieuw opgebouwd op dat tempo. Het nieuwe raster wordt alleen gebruikt als het daarna wel met de oudere analyse overeenkomt; anders blijft het oude. Het log meldt "grid of ... made again at ...".
- Bestaande opgeslagen rasters worden niet aangepast. Wil je ze herstellen: Instellingen > AutoMix > Delete scan data en daarna opnieuw scannen. De huidige en volgende nummers in een lopende sessie houden hun raster tot dan.
- Lus: de herhaalde 8 beats moeten minstens 80% van de energie hebben van de 16 beats ervoor (`kLoopSteady`, een gok), zodat een uitfade of breakdown niet wordt herhaald.
- Tests: nieuwe `host/test_regrid.cpp` (forcedBpm 0 verandert niets; raster op 90 bpm wordt gebouwd); `test_loop` uitgebreid (de testmuziek met uitfade koos al een gewone blend, dus de steady-regel zelf is daar niet echt geraakt). Alle host-tests slagen. Kotlin niet gecompileerd, niets beluisterd.
- Terugzetten: `MixTempo.kt` en de twee aanroepen in `MixLibrary` weg, `forcedBpm` uit analyzer/JNI/`NativeMix.analyze`, de steady-regel uit `planLoop`.
- Gerepareerd na compilefout (NowPlayingScreen, regel 936): de lokale functie `item` in het AutoMix-menu is nu `@Composable`. Zelfde versie 0.0.95.

## 0.0.96 (AutoMix-paneel in plaats van het menu)
- Op verzoek van de gebruiker (keuze 1A inline uitklappen, 2A direct toepassen). Lang drukken op de AutoMix-knop opent een paneel in de stijl van het AirPlay-venster (afgeronde hoeken 28 dp, zelfde achtergrond, rijen met naam en ondertitel, vinkje). Het paneel opent in de rechterbovenhoek van de knop, ligt over de knop en groeit naar links en omlaag (`AutoMixPanel`, nieuw bestand; alleen een instap-animatie, sluiten is meteen).
- Bovenaan: "AutoMix" met een aan/uit-schakelaar (dezelfde melding als de knop). Daaronder de standen in jouw volgorde met koppen General, Energy, Calm, Match, Filter en dividers; elke stand heeft een ondertitel die zegt wat hij doet. De actieve stand heeft een gekleurde achtergrond en een vinkje.
- Tempozone en Genres hebben een pijltje en tonen hun huidige keuze als ondertitel; ze klappen in het paneel open (zones als rijen, genres als aanvinkvakjes met een knop Apply). Een gewone stand, een zone of Apply past direct toe en sluit het paneel.
- Het oude `NooklinMenu` met twee pagina's op deze knop is vervangen. Kort tikken blijft AutoMix aan of uit zetten.
- Kotlin niet gecompileerd, niets gezien of beluisterd. Of het paneel op de goede plek en in de goede vorm opent, kan ik niet controleren.
- Terugzetten: `AutoMixPanel.kt` weg en in `NowPlayingScreen` het blok `if (modeMenu) { AutoMixPanel(...) }` terug naar het `NooklinMenu` van 0.0.95.

## 0.0.97 (betere overgangen: sleutelcontrole en ruimer zoekvenster)
- Aanleiding: log 44dbab6f en 7e7b961a. Twee nummers die goed te mixen waren (C majeur en A mineur, relatieve toonsoorten) kregen toch een fade.
- Sleutel: de sleutel van een kort stuk (8 tot 32 beats) is onbetrouwbaar; een outro met alleen drums en een kale intro gaven "key clash 1.00" (kosten 0.4 van de 0.86). Nu moet een clash ook door de sleutels van de hele nummers bevestigd worden: `clash = min(clash van het stuk, clash van de hele nummers)` (`g_whole` in `planner.cpp`, alleen in de sectieplanner `evalBlend`). De oude planner voor nummers zonder secties is niet aangepast.
- Zoekvenster in A: een blend mag nu eindigen tot 90 s voor het einde van de muziek van A (`kMaxEarlyBlendSec`, was 45 s, een gok), zodat een breakdown halverwege ook een kandidaat is. De straf van 0.4 per minuut vroeg vertrekken blijft gelden, dus zo'n blend wint alleen als hij duidelijk beter past. Exits houden de 45 s.
- Niet gewijzigd, bewust: de energieregels (sprongstraf, loud-into-calm weigering). Het eerste voorbeeld werd door de sleutel beslist, niet door energie. Het tweede voorbeeld (A luid 0.99 naar B kalm 0.13) wordt nog steeds geweigerd door de regel luid-in-kalm; dat is een bewuste regel van 0.0.74.
- Scantelling: nummers langer dan 15 minuten (de limiet van `PcmDecoder.MAX_SECONDS`, nu `const val`) tellen niet meer mee, dus het scherm kan "N van N" tonen. Het nummer zelf wordt nog steeds niet gescand.
- Tests: planner, fade, sections, tempo, loop, vocals en techniques slagen. Geen nieuwe test voor het ruimere venster of de sleutelcontrole; dus niet bewezen dat het juiste voorbeeld nu een blend geeft. Kotlin niet gecompileerd, niets beluisterd.
- Terugzetten: `g_whole` en de `std::min` in `evalBlend` weg, `kMaxEarlyBlendSec` terug naar `kMaxEarlyExitSec`, de filter in `MixLibraryScan.progress` weg.

## 0.0.98 (drie technieken uitgezet, venster 60 s)
- Op verzoek van de gebruiker na log 73f0bb14: "fade out and in onto a drop", "relaxed: long blend 64 beats" en de rolling blend (stijl 2, met of zonder stem mix) worden niet meer gepland. Niets is verwijderd: drie schakelaars bovenin `planner.cpp` (`kFadeOntoDrop`, `kRelaxed64`, `kRollingBlend`, alle `false`).
- Een gewone fade aan het begin van B blijft; echo, filter en cut op een drop blijven. Relaxed blijft op 32, 16 en 8 beats. Double drop en de andere stem mixes (relaxed 16 en 32) blijven.
- Venster voor blends in A: `kMaxEarlyBlendSec` van 90 s (0.0.97) naar 60 s, de keuze van de gebruiker (tussenweg, een gok). De rolling uit het log verliet A 50 s te vroeg; zo'n geval zou bij 60 s nog kunnen voor andere stijlen.
- Als een relaxed-lengte van 64 is ingesteld via `lengthBeats` (ik heb niet gecontroleerd of er een instelling voor bestaat), vindt relaxed dan niets en volgt een exit.
- Tests: planner, fade, sections, loop, vocals en techniques slagen. Kotlin niet gecompileerd, niets beluisterd.
- Terugzetten: de drie schakelaars op `true`, venster op 90 s.

## 0.0.99 (stems op tijd, geen nieuw plan midden in de overlap, grens luid-in-kalm)
- Aanleiding: log e293e0e1. Na een seek lag de overlap van een stem mix 12 s verder; het splitsen duurt 25 tot 55 s en kwam te laat. B was al bij het begin van de overlap gaan spelen (zonder stems) en het nieuwe plan na de mislukking overschreef dat (`maxPhaseErrMs` 69992, B op 75,9 s). Bevestigd door de gebruiker.
- Optie A. Planner: nieuw veld `PlannerSettings.stemLeadSeconds` (JNI zet 60 s, `0` = geen controle, zoals in de host-tests). Een plan dat stems nodig heeft (stem mix, of lange blend met meer dan 5% zang op zang) en waarvan de overlap minder dan 60 s verderop begint, wordt opnieuw gepland met stems uit (`planTransition` roept `planOnce` twee keer aan). De 60 s is een gok op basis van de gemeten 19 tot 38 s plus marge.
- Optie A. Conductor: `startStems` start niet meer als de overlap minder dan `STEM_LEAD_SEC` (60 s) ver weg is; na een mislukte stem-run wordt alleen opnieuw gepland als de overlap nog niet begonnen is (positie van A minder dan 1 s voor het begin). Daarna blijft het huidige plan staan, zodat B niet wordt onderbroken.
- Optie B. `kCalmShare` van 0,6 naar 0,45 in de regel luid-in-kalm: 0,91 naar 0,51 (log: kosten 0,39 tegen de exit 0,44) wordt nu toegestaan; 0,74 naar 0,12 (aanleiding van 0.0.76) blijft geweigerd. De 0,45 is een gok.
- Tests: nieuwe check in `test_vocals` (stems aan maar geen tijd: geen stem mix); planner, fade, sections, loop, vocals en techniques slagen. Het gedrag van de engine na een mislukte stem-run is niet getest (alleen Kotlin). Kotlin niet gecompileerd, niets beluisterd.
- Terugzetten: `stemLeadSeconds` op 0 in de JNI, de `STEM_LEAD_SEC`-controle en `overlapBegun` weg, `kCalmShare` op 0,6.

## 0.0.100 (cut onto a drop uit)
- Op verzoek van de gebruiker ("heeft nog nooit goed geklonken"; gekozen: alle varianten): een cut op een drop van B wordt niet meer gepland, dus ook niet als "cut + impact", "cut + riser + impact" en de "beat roll out + riser + impact onto a drop" die in `refineExit` uit zo'n cut voortkomt. Schakelaar `kCutOntoDrop = false` bovenin `planner.cpp` (in beide exit-lussen); de code is niet verwijderd.
- Een cut zonder drop blijft mogelijk: nu alleen nog in het hip-hop-genre bij een luid begin van B (de andere voorwaarde, luide A op een drop, vervalt). Echo, filter, reverb en roll op een drop (zonder cut) blijven.
- Tests: planner, fade, sections, loop, vocals en techniques slagen. Kotlin niet aangepast. Niets beluisterd.
- Terugzetten: `kCutOntoDrop = true`.

## 0.0.101

- Nieuwe stijl "breakdown swap" (stijl 6): A is in een rustig stuk vlak voor zijn eigen drop, B valt in waar de overlap eindigt en A stopt daar. 32 of 16 tellen, tempoverschil hooguit 5%. Eigen ontwerp, niet getest en niet beluisterd; het kost net zoveel als een dubbele drop (0.25).
- Bass swap staat weer in het midden van de blend (op een maatgrens), niet bij drie achtsten.
- Geheugen: na een rolling of dubbele drop volgt geen dubbele drop; dezelfde uitgang twee keer achter elkaar kost 0.5 extra. De conductor geeft de vorige stijl en uitgang door aan de planner.
- Log: bij "why" staat per stijl de goedkoopste geprijsde blend met alle kostenonderdelen (ook als hij verloor).
- Niet gebouwd: gelijke energieschaal over nummers (vraagt opnieuw scannen), harde wissel als vaste terugval, de host-tool.

## 0.0.102

- Energie op een gelijkere schaal: per beat wordt de luidheid nu gedeeld door het 95e percentiel van het nummer (het luide deel), niet door de mediaan. Met de mediaan las een nummer met een lang rustig stuk daar 1.0 en op de drop 3, dus hetzelfde getal betekende per nummer iets anders. Bron: Mixed In Key energy-patent US 8,865,993 (schaalt elk nummer met zijn eigen 95e percentiel RMS-luidheid, zodat zacht gemasterde nummers niet worden benadeeld); meetmethode luidheid: EBU R 128 / ITU-R BS.1770 (K-gewogen, korte termijn 3 s). De factor 1.4 (houdt de getallen dicht bij de oude, zodat de drempels bruikbaar blijven) is mijn schatting, niet gemeten. **MAP_VERSION 8: de bibliotheek wordt opnieuw geanalyseerd.**
- Blend van 64 tellen terug, maar alleen als beide nummers het volledig eens zijn: sleutelbotsing hooguit 0.05 (heel nummer bevestigt), geen zang in beide (onder 10%, zang moet gemeten zijn), tempoverschil hooguit 3%, en de bestaande regel "lang over rustig" blijft gelden. Bron: mixgraph.io melodic techno gids: 32 tot 64 maten, bij dezelfde sleutel en gelijke arpeggio's zo lang mogelijk, pads mengen mooier dan leads of zangstukken. 64 tellen is 16 maten, korter dan die bron. Er is geen bron die zegt dat het hierbij precies 64 moet zijn.
- Niet getest met echte muziek en niet beluisterd.

## 0.0.103 (structurele verbetering van de automix, in een keer gebouwd)

Aanleiding: de audit en het onderzoek in dit gesprek (stap 1 tot 4). Doel: uitgangen (exits) plus blends van 8 tellen of korter onder de 15% van de overgangen. De conductor logt daar nu per sessie een teller van (`plans this session`).

- **Kaartformaat en analyse** (`MAP_VERSION` 9, alles wordt opnieuw geanalyseerd; schatting 3,7 uur, een schatting): `TrackMap` heeft nu `cueConfidence`, `phraseBeats` (8, 16 of 32 uit de sectiegrenzen), `introEnd`, `outroStart`, `usableIntroBeats`, `usableOutroBeats`, `energyStart`, `energyEnd` en `swing` (plaats van de sterkste off-beat onset tussen twee tellen, 0,5 is recht). Intro en outro worden twee keer gevonden (energiecurve en secties); als ze niet overeenkomen is de zekerheid laag. De velden staan achteraan in de gepakte kaart (telwoord 9, daarna negen waarden), een oude kaart zonder ze blijft leesbaar. Kotlin: `MixMap.cues(map)`, `MixFacts.edge(id)` (tsv met zes extra kolommen).
- **Planner**: `genre` is nu een familie: 1 D (hip-hop, pop, dancehall, rock: kort, zang), 2 A (house, techno, trance, disco, afro house, amapiano: lang), 3 B (drum and bass, UKG, dubstep: 16 tot 32), 4 C (hardstyle, hard techno, psytrance: harde kickwissel). De JNI-signatuur is gelijk gebleven. `genreOf` in `MixConductor` wijst de families toe op genre-tekst (een voorafgaande kans; de gemeten eigenschappen beslissen).
  - Lengtekosten per familie (`lengthCost`), vervangt `0.1*log2(32/L)`: 8 tellen kost 0,5 tot 0,6 voor A, B en onbekend, 0 voor D, 0,15 voor C. Dit is de belangrijkste hefboom voor het doel.
  - Extra kosten: frase (`phraseCost`), gevoel/swing (`feelCost`: 3 maal het swingverschil boven 0,06, maximaal 0,6), half-time bij korte blends (`halfTimeCost`), blend langer dan het minimum van bruikbare outro van A en intro van B (`cueBoundCost`, alleen bij zekerheid 0,5 of meer, dat is het Mixxx-principe), twijfel over de cues (`cDoubt`, 0,3).
  - Bas-swap: `bassSwapBeat` in het plan (index 27 van de plan-array, nu 28 lang). Familie C: harde wissel op een maatgrens in het midden (`kFlagHardSwap`, wissel over een kwart tel). De rest: op de maat waar de harmonie van A het sterkst wisselt in het middelste halve deel van de overlap, als de chroma daar gemeten is (chroma bestaat alleen aan begin en eind van een nummer); anders de standaard.
  - Lage cue-zekerheid: een blend op sectiegrenzen van zo'n A maakt plaats voor een beat loop als die hooguit 0,5 duurder is. `explain` toont per beat-loop-poging waarom hij mislukte (tellers) en de cue-velden van beide nummers.
- **Engine/DSP**: `BlendLeveler` (dsp.h): meet vermogen van A en B en hun kruisterm (mid, tijdconstante 0,4 s) en schaalt de som zodat het vermogen gelijk is aan dat zonder correlatie; begrensd op 0,7 tot 1,5, traag, geen allocatie. Alle blends krijgen `kFlagLevel` (standaard aan). Gebaseerd op Fink, Holters en Zölzer (2016): equal-power klopt alleen voor ongecorreleerde signalen.
- **Volgorde** (`MixOrder`): vensterzoek over 3 nummers (de beste 8 kandidaten op de eerstvolgende wissel krijgen elk hun beste vervolg van 2 nummers erbij), en een randkost (`edgeCost`): luid einde in stil begin (zelfde gewichten als `energyJumpCost`), te weinig intro of outro om op te blenden, ander gevoel. Een nummer zonder cue-velden (oude analyse) krijgt geen randkost.
- **Meetinstrument**: `host/render_pair.cpp` (rapport van cue-velden voor wav-bestanden, en een paar renderen met afgedwongen lengtes 8, 16, 32, 64 en vrij naar wav, om op het gehoor te vergelijken). Nieuwe test `host/test_cues.cpp`.
- **Niet gedaan, bewust**: geen gesynthetiseerde secties als standaard (lage zekerheid gaat naar loop, echo of cut); geen zachte boogstraf in de volgorde (eenvoudigste eerst); geen vervangend paar-kostenmodel in Kotlin.
- **Correctie op eerder in dit gesprek**: `TEMPO_AGREE` (4%) in `MixModes` en `MixConductor` beslist alleen welke analyse vertrouwd wordt (twijfelvlag), niet de tempostraf van een paar; die is al continu via `kStretchCost`. Daar is niets aan veranderd.
- **Wat is gecontroleerd**: de C++ host-tests (analyzer, deck, engine, planner, techniques, fade, loop, regrid, sections, tempo, vocals en de nieuwe cues) slagen. `test_blend_levels` had en heeft een mislukte controle ("B bass has swapped by beat 14 (was beat 17)"); die faalde al in 0.0.102 omdat de bas-swap in 0.0.101 terug naar het midden ging en de test niet is bijgewerkt. **Niet gecontroleerd**: de Kotlin-code is niet gecompileerd, de JNI is niet gebouwd, er is niets beluisterd, en niets is op echte muziek getest.
- **Onzeker/ongemeten**: de lengtebereiken per familie komen uit gidsen en forums (en die spreken elkaar soms tegen, bijvoorbeeld 8 tot 16 bars tegen 16 tot 32 bars voor DnB naar UKG), niet uit luistertests. De regel "harde kickwissel voor familie C" en "bas-swap op nootwissel" zijn niet gemeten. De cue-drempels (0,8 voor volle energie, tolerantie van 4, 8 en 16 tellen) en de swing-meting zijn mijn schattingen. De niveauregelaar is getest op synthetische signalen, niet op muziek.
- Terugzetten: `MAP_VERSION` terug naar 8 laat de oude kaarten weer toe; `kFlagLevel` niet zetten in `planTransition` zet de niveauregeling uit; `lengthCost` vervangen door `0.1*log2(32/L)` geeft de oude lengtekosten.

## 0.0.104 (na het log van 0.0.103: 9 van 13 overgangen waren exits, 8 daarvan "fade out and in")
- Oorzaak uit het log: de nummers eindigen luid (energie 0,6 tot 1,3, bijna geen outro) en het volgende nummer begint stil (0,17 tot 0,6). Bij zo'n verschil (vanaf 0,35) waren echo en filter verboden en bleef alleen de fade over. De beat loop werd nooit geprobeerd (`tried 0`): `planOnce` probeert hem niet na een fade. Dat laatste is bewust niet veranderd, omdat de loop waarschijnlijk op dezelfde luid-in-kalm-regel afvalt.
- Volgorde (`MixOrder`): `EDGE_DOWN` van 0,8 naar 2,4, en 1,0 extra als een einde boven 0,8 volgt op een begin onder 60% daarvan.
- Planner: echo en filter zijn weer toegestaan bij een groot energieverschil (alleen de cut niet); de fade kost 0,7 extra (`kFadePenalty`), in `planNoLoop` en `planLegacy`.
- Tests: `test_fade` had drie controles voor het oude gedrag (fade bij groot verschil); die accepteren nu ook echo of filter. De rest slaagt. Niet getest met echte muziek en niet beluisterd; of echo/filter naar een stil begin goed klinkt is onbekend.
- Terugzetten: `kFadePenalty` op 0 en de twee `k == 2`-voorwaarden terug naar de oude `k < 2`-voorwaarde; `EDGE_DOWN` 0,8 en de `LOUD_END`-straf weg.

## 0.0.105 (octaaf van snelle muziek, loop zonder stems)
- **Tempo-octaaf (bron).** De tempozoeker kijkt van 70 tot 180 bpm met een voorkeur voor 125, en een raster op het halve tempo dekt de onsets even goed als dat op het hele tempo (`test_cues`: gesynthetiseerde nummers van 170 tot 200 bpm komen als 85 tot 100 uit, dekking 0,99 tegen 1,00). Daardoor stonden snelle nummers (drum and bass, hardcore) op de helft: in het log 87 en 96,8 bpm voor nummers van ongeveer 174 en 194. De planner vouwt octaven en zag het dus niet, maar de modi (Energy vanaf 120, Chill onder 100, tempozones) en de grootte van de tempowissel wel.
- Oplossing: alleen de genre-tag beslist (een octaaf uit het geluid is niet betrouwbaar: snare- en hi-hat-patronen overlappen tussen genres, en ik kon het zonder echte muziek niet valideren). `MixTempo.fixed` bouwt het raster opnieuw op het dubbele tempo als het genre `drum`, `dnb`, `d&b`, `jungle`, `neurofunk`, `liquid funk`, `hardcore`, `gabber`, `frenchcore`, `uptempo` of `breakcore` bevat, het raster tussen 78 en 105 bpm ligt, het dubbele raster echt op het dubbele tempo uitkomt (4%) en zijn dekking hooguit 0,1 lager is. `MixTempo.recheckOctaves` vergeet eenmalig de al geanalyseerde verdachte nummers (feiten en kaart op schijf) zodat de volgende scan alleen die opnieuw doet; geen nieuwe `MAP_VERSION`, dus geen volledige herscan. Nummers zonder genre-tag of met een ander genre worden niet aangepast.
- **Afgekapte mix** (log 0.0.104: A=108 naar B=155 met beat loop): `MixStems.needed` was ook waar voor een loop-plan met zang op zang (`vocalOverlap` 0,44), dus zowel de loop als de stems werden gebouwd. Beide zijn een overlay op A en een nieuwe vervangt de oude: de stem-overlay (klaar na 18,7 s) verving de loop die voorbij het einde van A doorliep, A bleef dus zonder geluid aan het einde van de blend, en B werd opnieuw gestart (`start` op 0,818 s in plaats van een `handoff`). Dit is een afleiding uit het log; ik heb de mix niet kunnen horen of herhalen.
  - Oplossing: een loop-plan gebruikt nooit stems (`MixStems.needed` is onwaar voor een loop, de planner prijst de loop met de regels zonder stems: hooguit 4 tellen zang op zang en het zwaardere gewicht).
- Tests: `test_cues` (octaafcontrole met geforceerd raster op 174 en 190 bpm) en `test_loop` (loop is geen stem mix) uitgebreid; alle host-tests slagen. Kotlin niet gecompileerd, niets beluisterd.
- **Niet opgelost, wel gezien**: de loop-blend in dat log verdeelde 40% tempoverschil (A 0,846, B 1,182) en de twee eerdere mixen 24 tot 28%. Het voorstel om de wissel te begrenzen (rond 10%) wacht op een besluit.
- Terugzetten: `MixTempo.doubled` niet aanroepen (in `fixed`), de loop-regel in `MixStems.needed` en `poorBlend(p, false)` in `planLoop` terug.

## 0.0.106 (tempowissel 15%, eerlijke teller)
- Na het log van 0.0.105 (octaafmeting werkt: 87 naar 174 bpm; 21 van 21 overgangen netjes overgenomen; fades 1 van 31 plannen): `MAX_TEMPO_CHANGE` van 0,40 naar 0,15 op verzoek van de gebruiker (voorstel was 10%, de gebruiker koos 15%). Dit is de totale wissel tussen A en B; bij meer dan 5% wordt hij gedeeld (hooguit ongeveer 7% per nummer). Boven 15% volgt een exit. Alleen de Kotlin-constante veranderd; de planner gebruikt `maxTempoChange` ongewijzigd.
- Teller in het log (`changes this session (pairs of songs)`) telt nu per paar nummers, het laatste plan van het paar telt. Een nieuw plan voor hetzelfde paar (seek, te late stems) is geen nieuwe overgang. In het vorige log gaf dat 35% waar het per uniek paar 8 van 22 (ongeveer 36%) was, dus het verschil zat niet in de teller maar in de exits zelf.
- Niet aangepast: het plafond en de exit-marge voor de legacy-planner (voorstel 2 van eerder).
- Gemeld door de gebruiker, nog niet onderzocht: sommige overgangen en zang-overgangen klinken nog raar.
- Niet getest: alleen Kotlin gewijzigd, niet gecompileerd.

## 0.0.107
- `MAX_TEMPO_CHANGE` van 0,15 naar 0,30 op verzoek van de gebruiker (totale wissel, bij meer dan 5% gedeeld door beide nummers, dus ongeveer 14% per nummer bij het maximum). Alleen die constante; niet gecompileerd.
- Gemeld, nog niet onderzocht: "volume van B komt soms nog erg laat terug naar het juiste volume". Hypothese (niet bevestigd): in een lange blend heeft B pas bas vanaf de bas-swap in het midden (`automate`), dus B klinkt tot dan dun en zacht. Vraag aan de gebruiker gesteld welk moment en welke soort overgang.

## 0.0.108
- Volume van B in een blend: gemeten met `render_pair pair ... trace` (nieuw: B's gain, balans, leveler per beat). Voor: B zat de hele blend rond 0,62 tot 0,70 (balans 1/sqrt(2) telde alleen de gains, niet dat de EQ al delen van A weghaalt) en sprong aan het eind naar 0,99.
- Fix: `bal` telt het vermogen per band (bas 0,5, mids 0,35, hoog 0,15) met de EQ; `gainB` volledig na 2 beats (was 0,85 tot de bas-swap); `BlendLeveler` clamp 0,85 tot 1,2 (was 0,7 tot 1,5). Na: B ca. 0,8 tot 1,1 tijdens de blend.
- Testen aangepast: 2 leveler-checks in test_cues (nieuwe clamp). Alle hosttests ok. Kotlin niet gecompileerd, niet beluisterd; synthetische muziek.

## 0.0.109
- Log 0.0.108 (12 wissels, 5 exits): 4 van de 5 exits kwamen van nummers die luid tot het einde doorlopen (uitloop 4 tot 8 beats, energie 1,26 naar 0,27 enz.). Er lag geen sectiegrens in de buurt, dus kon geen blend worden gemaakt. Nieuw in `planNoLoop`: als de laatste sectie van A hoog is, overlappen de laatste 16 of 8 beats van A met het begin van een drop van B (stijl 3, dezelfde kosten en limieten als een double drop; niet direct na stijl 2 of 3). Nieuwe test in test_cues.
- Tempo-octaaf zonder genre (`MixTempo.unsureHalf`): raster van 72 tot 90 bpm, cue-zekerheid onder 0,5, begin- en eindenergie minstens 0,8, genre niet traag, wordt opnieuw op dubbel tempo geprobeerd (zelfde controle als de genreregel). Oorzaak: een nummer van 76 bpm tussen 156 en 74 bpm met zekerheid 0,32. Eenmalig (`recheckUnsure`, markerbestand `automix_octave_checked2.txt`) worden bestaande nummers van 72 tot 90 bpm zonder traag genre vergeten en opnieuw geanalyseerd. Risico: echt trage, harde nummers lijken dan op dubbel tempo.
- Niet gecompileerd (Kotlin), niet beluisterd; C++ hosttests ok.

## 0.0.110
- Auto Mix werkt nu ook over AirPlay. Oorzaak: de motor speelt via Oboe naar de eigen speaker, AirPlay haalde zijn audio uit ExoPlayer (`PcmTapProcessor`), en `MixConductor` zette de mix uit zodra `AirPlayAudio.tapping` aan was.
- Nieuw: `android/tap_ring.h` (lock-vrije ring, float naar 16-bit stereo 44,1 kHz), in de Oboe-callback gevuld; JNI `nativeTapEnable/TapFlush/TapRead`; `MixAirPlayTap` (Kotlin-draad) zet de audio in `AirPlayAudio`, de bestaande zender blijft ongewijzigd. `AirPlayAudio.mixOwns`: zolang de motor het nummer speelt kopieert `PcmTapProcessor` de (stille) ExoPlayer niet. Bij `muteLocal` speelt de motor stil op de telefoon en loopt de tap door. `engineId`-setter leegt de wachtrij bij overname en teruggave. `!AirPlayAudio.tapping` is uit `step()` verwijderd.
- Test: host/test_tap.cpp (aan/uit, 16-bit, mute, flush, volle ring, twee draden). Kotlin en JNI niet gecompileerd, niet getest met een AirPlay-ontvanger.
- Bekend risico: de motor loopt op de Oboe-klok en de zender op een eigen klok; drift over lange tijd is niet gemeten.

## 0.0.111
Op verzoek van de gebruiker (21 okt): "afstemming" en emotie, en een zin van A moet worden afgemaakt.
- **Beloningen in de planner** (`evalBlend`, `planner.cpp`): `cMoment` (B's drop valt aan het einde van de overlap: -0,35; in het midden: -0,2, geschaald met de dropsterkte) en `cHand` (A is klaar met zingen minstens 2 beats voor B begint, binnen de overlap: -0,2). Alleen bij een schone overlap (geen zang op zang, toonsoortbotsing hoogstens 0,3). De kosten blijven geklemd op minimaal 1e-6, dus een beloning kan een blend niet onder nul brengen. Het logveld `why` toont `moment` en `voice`. De rolling blend blijft uit (`kRollingBlend=false`, eerdere keuze van de gebruiker), dus de beloning werkt via double drop en breakdown swap.
- **Zin afmaken** (`MixStems.kt`, `VocalGate.kt`, `engine.cpp`): de zang van A ging altijd in de eerste 1,5 s van de overlap weg (en bij een zang-overlap sneed de engine A's mids in de eerste 2 beats). Nu kiest `VocalGate.chooseOut` (nieuw argument `targetFrac` = 0) de eerste pauze in de zangstem van A binnen de overlap (zinseinde), met 0,3 s fade; zonder pauze de eerste maatlijn. B's zang start via `VocalGate.chooseIn` bij zijn eerste frase na A's zang, niet voor 30% van de overlap. Voor blends zonder stem mix (alleen zangstems) zet de engine `kFlagVoiceLive` (beide overlays aanwezig): A's mids blijven tot de bas-swap, de korte swap houdt A's stem de eerste helft. Het log toont "singing of A out at +x s (pause/bar/target), singing of B in at +y s".
- **Boog over meerdere nummers** (`MixOrder.kt`, `arcCost`): na 3 nummers die elk energieker werden is een rustiger nummer welkom, na 2 rustigere een energiekere (gewicht 0,5, alleen naast tempo, toonsoort en randkosten). Gebruikt de energie van het hele nummer (`MixFacts.energy`).
- Tests: test_cues (beloning drop, beloning stem-overdracht, overig), nieuw `test_voice.cpp`. Kotlin niet gecompileerd en niets beluisterd; zinseinde-detectie op de zangstem is niet op echte muziek gemeten.

## 0.0.112
Op verzoek van de gebruiker (21 okt): exits van nummers die luid tot het einde doorlopen.
- **Eind-blend zonder drop** (`planner.cpp`, na het eind-blok van 0.0.109): als A's laatste sectie hoog is, overlappen de laatste 16 of 8 beats van A met het begin van B (relaxed, stijl 1), ook als B geen drop heeft. De bestaande kosten, klemgrens en `kBlendCostCeiling` beslissen. Octaafparen waren geen probleem: `tempoOf` probeert al factor 0,5/1/2; de exits kwamen door "too early"/"too deep".
- Tests: nieuwe test in test_cues, maar die haalt het nieuwe pad niet aan (een drop-blend wint); het nieuwe pad is dus niet door een test bewezen. Kotlin niet aangeraakt, niets beluisterd.

## 0.0.113
Splitsing van 0.0.112: de loop-wijziging hieronder zat per ongeluk onder hetzelfde versienummer als de eind-blend (twee zips met de naam 0.0.112).
- **Loop-punt in A zelf kiezen** (`planLoop`, `planOnce`): behalve de laatste maatlijn zijn nu tot 6 frasestarts in de laatste 40% van A kandidaat-looppunt (minstens 16 beats voor de laatste maatlijn). De loop is een overlay op A (`MixLoops.kt`), dus de rest van A wordt overgeslagen zonder wijziging in engine of Kotlin. Overgeslagen muziek kost 0,4 per minuut (gok). De loop doet nu ook mee naast een gewone blend als B in de overlap zingt (`vocalB` >= 0,12) en de loop-kosten min 0,25 (gok) lager zijn dan die van de blend; tot nu toe werd een loop alleen gekozen bij een exit of zwakke cues. De loop gebruikt nog geen stems.
- Test (`test_loop.cpp`, "loop inside A"): in de synthetische case worden de kandidaten gemeten (1 geaccepteerd, 19 te duur) maar wint de blend (kosten 0,41); de keuze van een loop midden in A is dus niet door een test bewezen. De schaal van de loop-kosten en die van de blendkosten is niet gelijk, dus de bonus 0,25 is een gok.

## 0.0.114
Op verzoek van de gebruiker (21 okt): in log 0.0.113 sneed een loop 70 s van A af (kosten 2,15 en 2,30 tegen een exit van 1,08), met onbekende zang van A (`vocalA` -1).
- **Loop-regels** (`planner.cpp`): een loop vervangt een exit alleen als hij hoogstens 0,3 duurder is (`kLoopOverExit`); een loop-punt in A slaat hoogstens 20 s van A over (`kLoopMaxSkipSec`; verving `kLoopMinShare`); een loop-punt midden in A alleen als A's zang bekend is (`a.vocal` niet leeg). De laatste maatlijn blijft altijd kandidaat. Waarden zijn gokken.
- **Log** (`MixConductor.kt`): het plan-event heeft `zoneBpmA/B` (`MixModes.tempoOf`, wat de tempozone ziet) en `factBpmA/B` (het opgeslagen tempo in `MixFacts`).
- Tests: alles groen, maar het eerste loop-paar in `test_loop.cpp` kiest nu een exit (de goedkope exit wint van de loop), dus de loop-controles op maatlijnen daar worden niet meer uitgevoerd. Kotlin niet gecompileerd, niets beluisterd.

## 0.0.115
Op verzoek van de gebruiker (22 okt): een uitgebreid log om te analyseren of de mix goed is; het gaat er later weer uit (zet `kAnalysisLog` in `planner.cpp` op false, of verwijder `analysisLines`, de velden `byLen`, `exitCost`, `loopCost`, `loopPointBeat`, `loopCands` in `Why` en de drie plekken die ze vullen).
- Het veld `why` van het plan-event krijgt extra regels: de goedkoopste kandidaat per stijl bij 8, 16 en 32 beats; exit-kosten en loop-kosten met het loop-punt; timing in seconden (waar A en B starten, hoeveel van A niet gespeeld wordt, hoe diep B is ingestapt, lokaal bpm op die plek naast het gemiddelde); bij een loop welke beats herhalen; energie- en zangcurves van A en B in stappen van 4 beats rond de overlap.
- Niet gelogd: wat de engine tijdens de overlap doet (gain van B, balans, leveler, clipping, drift), want dat vraagt JNI-wijzigingen die ik hier niet kan compileren.
- Tests: alles groen. Geen gedragswijziging.

## 0.0.116
- Logcat knipt een regel af bij ongeveer 4000 tekens; de plan-regels van 0.0.115 (4155 tekens) misten daardoor de energiecurves en de zang van B. `logEvent` (`MixConductor.kt`) stuurt een lange regel nu in delen van 3000 tekens met het voorvoegsel "[part i/n] " naar logcat. Het bestand `automix_log.jsonl` bevat de hele regel. Alleen logging, geen gedragswijziging; Kotlin niet gecompileerd.

## 0.0.117
Op verzoek van de gebruiker (22 okt): korte overlaps in seconden (log 0.0.115/116: 8 beats = 2,8 tot 3,6 s bij 150 tot 175 bpm).
- **Minimale overlap in seconden** (`evalBlend`, `planner.cpp`): een overlap korter dan 5 s (`kMinOverlapSec`) kost extra, lineair tot 1,0 (`kShortOverlapCost`) bij 0 s. 8 beats bij 155 bpm (3,1 s) krijgen dus ongeveer +0,38, 16 beats (6,2 s) niets. De meerkost zit in `len` van het log. Beide waarden zijn gokken.
- Niet aangepast: de legacy planner ("phrase:") heeft eigen kosten en krijgt deze straf niet (2 van de 9 korte swaps in log 0.0.115/116 kwamen daarvandaan). Een overlap die verplicht kort is (geen 16-beat kandidaat, "-" in `by length`) blijft kort.
- Tests: alles groen; geen test op dit effect. Niets beluisterd.

## 0.0.118
Op verzoek van de gebruiker (22 okt, "bouw alles"): opties 1 tot 5 uit de analyse van log 0.0.117. Alle gewichten zijn gokken; niets beluisterd; geen test op het effect van de nieuwe regels in echte nummers.
- **Log**: nieuwe regel "relaxed candidates dropped for length" met per lengte (8/16/32/64) hoeveel kandidaten afvielen omdat de sectie van A of de intro van B te kort was.
- **16 beats mogen doorlopen** voorbij een korte laatste sectie van A (het outro in) en voorbij een korte intro van B (`planNoLoop`, relaxed). De kosten beslissen.
- **Straf voor weggegooid deel van A** (`unplayedCost`): zang in het deel van A dat niet meer klinkt (0.8, schaalt met zangaandeel en tot 20 s) en een drop van A die nooit klinkt (0.6, schaalt tot 40 s). Alleen als meer dan 4 s van A overblijft. Geldt voor alle exits (nieuwe en legacy planner) en voor de blends van de legacy planner; bij blends van de nieuwe planner alleen de zang (de drop van B komt in de plaats van die van A).
- **Legacy planner**: een blend boven kostengrens 1,5 geeft een exit de kans, en een blend verliest van een exit die meer dan 0,5 goedkoper is (zoals bij de nieuwe planner).
- **Gelijkspel op de kostenbodem**: `TransitionPlan::rawCost` bewaart de kosten vóór de ondergrens van 1e-6; kandidaten worden daarmee vergeleken.
- De loopkosten stonden al in het log (`loop cost ... at beat`).
- Test aangepast: `test_planner.cpp` "loudness of the overlap". Een exit in de breakdown betaalt nu voor A's weggegooide slotdrop, waardoor een blend die 16 s voor het einde eindigt wint. De test eist nu: mix in de breakdown over een rustig stuk, of hooguit 20 s van A ongespeeld. Dat is een verruiming van de test omdat het gedrag bewust veranderd is.
- Overige tests groen.

## 0.0.119
Op verzoek van de gebruiker (23 okt): na 40 nummers komt dezelfde mix terug; er moet altijd een nieuwe mix komen binnen dezelfde categorie. De oorzaak is niet in het log te zien (het bevat geen regels over de wachtrij); twee plekken in de aanvulling (`PlaybackService.kt`, "A mode of the AutoMix menu") konden dit veroorzaken, beide zijn aangepast. Kotlin niet gecompileerd, niets beluisterd.
- **Herhaal-alles stopte het aanvullen**: de aanvulling sloeg over bij elke herhaalstand behalve uit, dus met "Repeat all" begon de wachtrij van 40 opnieuw. Nu stopt alleen "Repeat one" (en shuffle) het aanvullen.
- **Alle passende nummers zaten al in de wachtrij**: de aanvulling sloot alle nummers uit de wachtrij uit, ook die lang geleden gespeeld waren; was de categorie klein, dan bleef er niets over. Nu begint dan een nieuwe ronde: alleen de 20 laatst gespeelde nummers (dan 8, dan alleen het huidige en de komende) worden uitgesloten. De volgorde komt uit een willekeurige greep, dus de mix verschilt.
- Nieuw logregel: `refill: queue N, playing K, added M (new round)`.
- Niet aangepast: bij shuffle aan blijft de aanvulling uit (de lijst wordt dan door de speler geschud en herhaald).

## 0.0.120
Op verzoek van de gebruiker (23 okt): tijdens AutoMix moet shuffle uit staan (anders volgen te veel willekeurige nummers met slechte overgangen). Gekozen: uit en vergrendeld. Buiten AutoMix werkt shuffle zoals voorheen. Kotlin niet gecompileerd.
- `PlaybackService.kt`: zet de speler shuffle aan terwijl AutoMix aan staat, dan gaat het direct weer uit met de melding "Shuffle is off while AutoMix is on" (de shuffle-knop doet dus niets). Staat shuffle al aan op het moment dat AutoMix aan gaat, of komt een bewaarde wachtrij met shuffle terug, dan zet de lus die de AutoMix-lijst aanvult (elke 5 s) het uit.
- Niet aangepast: de knop zelf toont nog "aan" tot de speler hem terugzet; geen aparte uitgeschakelde weergave.

## 0.0.121
Op verzoek van de gebruiker (23 okt): "exit: reverb out" voorlopig uit. In `refineExit` (`planner.cpp`) krijgt de reverb-uitgang gewicht 0 via `kReverbExit = false`; zet die op true om hem terug te brengen. Echo, filter en roll blijven. Geen andere wijziging; niets beluisterd.

## 0.0.122
Op verzoek van de gebruiker (23 okt, optie A en B): in log 0.0.119 waren 11 van 34 plannen van de legacy planner (nummers met minder dan 3 secties) en 9 daarvan werden een exit (totaal exits 59%).
- **A**: de marge van 0,5 uit 0.0.118 is uit de legacy planner. Een blend tot de grens van 1,5 wordt direct gekozen; erboven wordt een exit gezocht en die wint alleen als hij goedkoper is.
- **B**: de legacy blend heeft geen loudnessterm (`0,5 × (eA + eB)`) meer in de kosten, zodat blends en exits op dezelfde schaal staan als in de planner op secties.
- Dit maakt legacy blends veel vaker de keuze; hoe ze klinken is niet beluisterd. De sectiedetectie bij nummers met 1 tot 2 secties is niet onderzocht.
- Bevat ook 0.0.119 (aanvulling), 0.0.120 (shuffle uit tijdens AutoMix) en 0.0.121 (reverb uit).

## 0.0.123
Op verzoek van de gebruiker (23 okt, optie A, B en C uit de analyse van log 0.0.122). Tests: alles groen, maar er is geen test op het effect van deze regels op echte nummers en niets is beluisterd. Alle waarden zijn gokken.
- **A, luid naar rustig** (`planNoLoop`, blok van 0.0.112): als A luid eindigt, mag B nu ook instappen aan het begin van elk van zijn luide secties (16 of 8 beats), niet alleen aan zijn begin. Zo hoeft een luid einde van A niet meer in een rustige intro van B te lopen (6 van 14 exits in log 0.0.122). De skipkosten en de dieptegrens van B tellen mee.
- **B, tempogrens per nummer**: `kMaxSongStretch = 0.08`. Een blend waarbij A of B meer dan 8% wordt versneld of vertraagd (ook bij gedeeld tempo) wordt niet gemaakt, in `evalBlend`, de legacy planner en `planLoop`. Wissels met een grotere tempostap worden een exit of een andere kandidaat; de instelling voor de maximale tempoverandering blijft gelden daarboven.
- **C, weggegooid deel van A** (`unplayedCost`): (1) het luide deel van A dat niet gespeeld wordt kost 0,5 per minuut, ongeacht hoe lang het nummer is (alleen als het resterende deel gemiddeld energie 0,7 of meer heeft); (2) bij blends van de planner op secties telt een drop van A die nooit klinkt nu mee, tenzij B een eigen drop landt waar de overlap eindigt. Geldt ook voor exits en de legacy planner.
- Niet gedaan: dubbele plan-regel (plan 41 en 42, optie D) en tempo in de volgorde van de Energy-modus (optie E).

## 0.0.124
Op verzoek van de gebruiker (24 okt, advies A, D en C uit de analyse van log 0.0.123). Tests groen; geen test op het effect, niets beluisterd. Alle waarden zijn gokken.
- **A, 8 beats als laatste redmiddel** (`evalBlend`): 8 beats kosten +0,8 (`kShortLastResort`, in `len` van het log), tenzij de lengte is vastgezet. In log 0.0.123 hadden 6 van de 11 korte swaps een 16-beat kandidaat die 0,1 tot 0,5 duurder was. Gevolg: meer 16-beat blends, soms boven de kostengrens van 1,5 (dan wordt een exit gezocht).
- **D, legacy planner**: dezelfde +0,8 voor 8 beats.
- **C, wanneer B gaat zingen**: kost tot 0,3 als B's eerste zang pas na 60% van de overlap valt (lineair tot 100%), en 0,3 als B in de overlap niet zingt maar in de even lange stretch daarna wel. Zit in `voice` van het log. B kan niet later instappen om zijn zang in de overlap te krijgen (dat is niet gebouwd), alleen een andere kandidaat kiezen. De poort (`VocalGate`) is niet aangepast.
- **Log**: nieuwe regel "singing in the overlap of N beats: A last sings at beat X, B first sings at beat Y (Z%)", zodat te controleren is of B's zang eerder komt.
- Niet gedaan: de toonsoortbotsing (grootste oorzaak van exits, 10 van 17) en eerder laten beginnen van B's zang in `VocalGate`.

## 0.0.125
Op verzoek van de gebruiker (25 okt, optie 2 en 3 uit de analyse van log 0.0.124). Tests groen; geen test op het effect, niets beluisterd. Waarden zijn gokken.
- **Optie 2, wanneer B echt gaat zingen** (`evalBlend`): de regel van 0.0.124 (B's eerste zang) is vervangen door een schatting van wat `VocalGate` doet. A's zang is weg na ongeveer 15% van de overlap (`kVoiceGate`); daarna wacht B op een frasestart (zang na een pauze). Komt die op meer dan 50% van de overlap, dan kost het tot 0,5 (lineair tot 100%). Zingt B de hele overlap door zonder pauze, of pas erna, dan kost het de volle 0,5 (zang aan het einde). Een B zonder zang in en na de overlap, of zonder zangmeting, kost niets. Zit in `voice` van het log.
- **Optie 3, toonsoortbotsing meten**: alleen logging. Nieuwe regel "keys (code, confidence): overlap A .. B .., clash ..; whole songs A .., B .., clash .." in elk plan, ook bij exits (waarvan het plan-event zelf keyA, keyB en keyClash niet vult: ze staan daar op "unknown" en 0). Geen gedragswijziging.
- Uit log 0.0.124 al af te leiden: botsingen van 0,63 tot 0,70 zijn de waarde 0,7 uit de tabel in `keyClash` (afstand 3 op de kwintencirkel) maal de zekerheid, en de grens `maxBlendClash` is 0,55. Of dat voor stems te streng is, is niet gemeten.

## 0.0.126
Op verzoek van de gebruiker (25 okt, "doe wat de beste mix creëert": advies A, B en C uit de analyse van log 0.0.125). C++-tests groen; de Kotlin-wijzigingen zijn niet gecompileerd; niets beluisterd; alle waarden zijn gokken.
- **A, zangregel alleen als keuze tussen blends** (`evalBlend`): de straf voor late zang van B (0.0.125, tot 0,5) bepaalt nog welke blend wint (`rawCost`), maar telt niet meer mee in de kosten die met een exit en de grens van 1,5 worden vergeleken (`cost` is zonder die straf). In log 0.0.125 maakte hij van 2 blends (plan 13 en 26) een exit.
- **B, poort** (`VocalGate.chooseIn`, aangeroepen door `MixStems.kt` en `StemMix.kt`): heeft B in de overlap geen frasestart, of ligt die na 70% van de overlap, dan komt B's zang op de eerste maatstreep na A's zang waar B al zingt (`how` = "bar"), met een fade-in van 0,4 s (`B_BAR_FADE_SEC`, was 0,15). Anders blijft het zoals het was. De nieuwe parameter `beats` (overlap in beats) is nodig voor de maatstreep; zonder (0) geldt het oude gedrag. Risico: B kan midden in een zin binnenkomen.
- **C, parallelle toonsoort** (`keyClash` in `harmony.h`): dezelfde grondtoon in majeur en mineur geeft 0,3 in plaats van 1,0. In log 0.0.125 werd van vier nummers de kleur op de ene plek majeur en op de andere mineur gezien.
- Beïnvloedt ook de loop-planner en de legacy planner (die gebruiken `keyClash`).

## 0.0.127
Op verzoek van de gebruiker (25 okt, "bouw alles wat nodig is", na log 0.0.126 in twee stukken). C++ ongewijzigd, tests groen; de Kotlin-wijzigingen zijn niet gecompileerd; niets beluisterd. Geen verandering aan de mix zelf.
- **Mislukte decodes minder vaak herhalen** (`MixLibrary.kt`): een nummer dat niet te decoderen is ("Oscar and the Wolf") werd bij elke wissel opnieuw geprobeerd (16 keer in een log van 16 wissels; de wachttijd was 60 s). Nu verdubbelt de wachttijd bij elke nieuwe mislukking (60 s, 2, 4, 8, 16, 32 min). `failedCount` naast `failedAt`.
- **Stems-log uitgebreid** (`MixStems.kt`): de regel "stems: ready in .. ms" noemt nu ook de tijd voor A en de telefoonstatus (thermische status, batterijbesparing, scherm aan, aantal cores). In log 0.0.126 duurden de stems 68 tot 92 s, in de eerste test van dezelfde versie 22 tot 24 s; dezelfde code, dus de oorzaak ligt vermoedelijk bij de telefoon. Dit is een vermoeden, geen meting; de nieuwe regel moet het laten zien. Geen gedragswijziging (de prioriteit van de thread is niet aangepast).
- Bewust niet gebouwd: een marge die blends bevoordeelt boven exits (in log 0.0.126 redt die één wissel, 1,32 tegen 1,30; de andere exits hadden blends van 2,0 tot 2,35 tegen 1,0 tot 1,4).

## 0.0.128
Op verzoek van de gebruiker (25 okt, optie 1 uit de analyse van log 0.0.127). C++ ongewijzigd; de Kotlin-wijzigingen zijn niet gecompileerd; niets beluisterd.
- **Stems-thread** (`MixStems.kt`): prioriteit van `MIN_PRIORITY` naar `NORM_PRIORITY - 1`. Reden: in log 0.0.127 duurden de stems 78 tot 126 s tegen 22 s in de eerste test van 0.0.126, met thermisch 0, batterijbesparing uit, scherm aan en 8 cores, zonder andere activiteit in het log. Dat de lage prioriteit de oorzaak is, is een gok.
- **Tijdsverdeling in het log** (`StemSeparator.kt`, `MixStems.kt`): de regel "stems: ready" noemt nu fft, laden van de modellen (4 per aanroep, 2 aanroepen per bouw) en rekenen apart. Als het laden de hoofdmoot is, is een volgende stap de sessies delen tussen A en B.
- Eén stem-bouw in dat log was te laat (`ok:false`, 147 s voor de overgang): of de snellere thread dat verhelpt, is niet bekend.

## 0.0.129
Op verzoek van de gebruiker (28 okt: "bouw A, B, C", na de analyse van de log met 21 stem-bouwen). C++ getest op de computer: alle host-tests groen, behalve `test_blend_levels` (2 FAIL, ook in 0.0.128 met identieke uitvoer). De Kotlin-wijzigingen zijn niet gecompileerd; niets beluisterd.
- **Log: waarom een blend wordt afgewezen** (`planner.cpp`): de regel "blend turned down" noemt nu de regel die de blend afwees (`keyGate`, `loudToCalm`, `singingOnSinging`, `exitCheaper`), ook in de legacy-planner, die tot nu toe stil bleef. De eerste regel die van toepassing is wordt genoemd; er kunnen er meer gelden. `Why` staat daarvoor vóór `planLegacy`. In de log van 28 okt (21 stem-bouwen, 7 unieke afwijzingen) was de toonsoort bij 2 van de 7 de reden, 4 waren 0,64 tot 1,00 duurder dan de exit, 1 onbekend.
- **Log: stems-herplan** (`planner.cpp`): als een plan met stems te dichtbij komt en zonder stems opnieuw wordt gepland, staat dat in het log met de ruimte die er was en de vereiste 60 s.
- **A, log van de stem-bouw** (`StemSeparator.kt`, `MixStems.kt`): de regel "stems: ready" noemt nu ook seconden audio, aantal splits, laadtijd en rekentijd per stem (vocals/drums/bass/other), aantal geladen sessies en de rendertijd.
- **B, sessies delen** (`StemSeparator.kt`, `MixStems.kt`): een bouw laadt de vier modellen één keer voor A en B samen (was 4 per nummer, dus 8) en sluit ze in een `finally`. Andere gebruikers van `StemSeparator` (`MixVocals`, `StemTransitions`, `StemMix`) gedragen zich ongewijzigd (`keepSessions` staat standaard uit). Piekgeheugen: tot 4 sessies tegelijk in plaats van 1; hoeveel dat is, is niet gemeten. De winst is ook niet gemeten.
- **C, loop-planner** (`planner.cpp`): een toonsoortbotsing van de laatste maten van A met B moet nu door de hele nummers bevestigd worden (`min(lokaal, heel nummer)`), zoals de section-planner al deed. Dit verandert gedrag: er kunnen meer loops gekozen worden. Test in `test_loop.cpp`: op de 0.0.128-planner worden 6 loopkandidaten afgewezen, nu 0. De loop-planner heeft een eigen berekening, omdat `g_whole` voor songs met de legacy-planner niet gevuld wordt.
- Niet gebouwd: de whole-song-controle in `planLegacy`, een gemeten waarde in plaats van de vaste 60 s (`stemLeadSeconds`), en alle drempels en gewichten.
- **Wegvegen stopt alles** (`PlaybackService.kt`): als je de app uit de app switcher veegt, stopt nu de muziek (de service stopt altijd; voorheen ging spelende muziek door) en worden de downloads gepauzeerd (`DownloadManager.pause()`: de download die bezig is wordt afgebroken, een half bestand blijft niet bewaard). `onDestroy` bewaart de wachtrij en de positie zoals tot nu toe. Op verzoek van de gebruiker (28 okt). Niet gecompileerd en niet op een toestel geprobeerd.

## 0.0.130
Op verzoek van de gebruiker (28 okt, na de log met de tijdsverdeling van 6 stem-bouwen: fft 13%, render 43%, de rest 32%, model draaien 9%). C++ getest op de computer (nieuwe `test_stem_dsp`, alle bestaande tests ongewijzigd groen behalve `test_blend_levels`, die ook in 0.0.128 faalt); de Kotlin- en JNI-koppeling zijn niet gecompileerd en niet op een toestel geprobeerd.
- **Stem-DSP in C++** (`mix/stem_dsp.cpp`, `android/stem_dsp_jni.cpp`, `NativeStemDsp.kt`, `StemSeparator.kt`): de spectrogram-analyse, de energie per frame en de render lopen in native code, op 3 threads, met dezelfde formules en dubbele precisie als de Kotlin-code. Het model (ONNX) blijft in Kotlin. De render verdeelt de frames over threads met een eigen buffer per thread, die daarna in vaste volgorde wordt opgeteld.
- **Terug naar Kotlin**: `StemSeparator.NATIVE_DSP = false` en opnieuw bouwen. De Kotlin-DSP staat nog in `StemSeparator.kt`. Er is geen terugval tijdens het draaien: als de native bibliotheek niet laadt, geeft de eerste aanroep een foutmelding.
- **Log**: de regel "stems: ready" noemt ook `energy .. ms` en `dsp native` of `dsp kotlin`.
- **Test op de computer**: het spectrum komt overeen met een directe DFT, met alle volumes op 1 komt de muziek terug (afwijking 6e-8), de render komt overeen met een directe inverse DFT voor willekeurige volumes (4e-8), NaN en oneindig in de modelschattingen geven geen NaN in de muziek, en 1 thread geeft hetzelfde als 3. Snelheid op de computer (2 cores) voor 20 s muziek: spectrum 62 ms, energie 35 ms, render 140 ms op 1 thread. Dat zegt niets over de telefoon; de verwachting is dat het veel sneller wordt dan de 9 + 30 + 23 s uit de log, maar dat is pas bekend uit de volgende log.
- Niet gebouwd: `shareOf` (MixVocals) blijft Kotlin; de vaste 60 s ruimte van de planner is niet aangepast (het mislukken van een 32-beats bouw in de laatste log blijft mogelijk).


## 0.0.131
Op verzoek van de gebruiker (28 okt: menu met pills zoals het voorbeeld van iOS, Bluetooth erbij, categorieën, Phone Speaker bovenaan; routing: "als er 1 item gekozen is dan moet de audio daar naartoe"). C++ is syntaxis-gecontroleerd tegen een nagemaakte Oboe-header (geen echte Oboe hier); de Kotlin-wijzigingen zijn niet gecompileerd; niets op een toestel geprobeerd.
- **Menu** (`AirPlayDialog.kt`): elke uitvoer is een eigen pill. Bovenaan Phone Speaker (hernoemd van "This phone"), dan het kopje Bluetooth, dan het kopje AirPlay. De titel is "Play on" (was "AirPlay"). De geselecteerde pill is gevuld met het volume (slepen om te wijzigen) en heeft een vinkje; de losse volumebalk onder AirPlay-apparaten is vervallen. Een tik op een geselecteerde AirPlay-pill schakelt die uit, zoals voorheen; een tik om het volume te zetten werkt niet meer, alleen slepen.
- **Bluetooth** (`AudioRoute.kt`): het menu toont de verbonden Bluetooth-audioapparaten (A2DP, BLE headset, BLE speaker, hoortoestel), live bijgewerkt. Koppelen of verbinden doet Android; een gekoppeld apparaat dat niet verbonden is staat er niet in. De namen vragen de toestemming `BLUETOOTH_CONNECT` (manifest), eenmalig bij het openen van het menu.
- **Routing**: de keuze gaat naar de hoofdspeler en de crossfade-speler (`setPreferredAudioDevice`) en naar de Oboe-stream van de Auto Mix engine (`nativeSetOutputDevice` in `mix_jni.cpp`: een draaiende stream wordt gesloten en op het nieuwe apparaat geopend, een slapende stream gebruikt het apparaat bij de volgende start; is het apparaat weg, dan terug naar standaard). Phone Speaker kiezen met Bluetooth verbonden houdt het geluid op de speaker. Een keuze van Phone Speaker of Bluetooth schakelt AirPlay uit; een AirPlay-speaker kiezen dempt de telefoon zoals voorheen (meerdere AirPlay-speakers tegelijk blijft kunnen).
- **Volume van Phone Speaker en Bluetooth** is het muziekvolume van Android (`STREAM_MUSIC`), zonder systeem-UI.
- **Beginstand**: zonder keuze beslist Android (zoals voorheen). Het menu toont dan het apparaat dat Android voor muziek kiest (`getAudioDevicesForAttributes`). De keuze wordt niet bewaard na een herstart van de app. Verdwijnt het gekozen apparaat, dan vergeet de app de keuze.
- Niet gebouwd: Bluetooth koppelen of verbinden vanuit de app, een keuze die een herstart overleeft, een kopje boven Phone Speaker.

## 0.0.132
Op verzoek van de gebruiker (28 okt, na het testen van 0.0.131: "paarse contrast weer terug, hou dezelfde stijl als de rest van de app", "pills hebben geen contrast tov de achtergrond", zwart thema, "voeg iets contrast toe"). Alleen `AirPlayDialog.kt`. Niet gecompileerd, niet op een toestel gezien.
- **Kleuren in app-stijl**: de kaart gebruikt `GlassPill.adaptivePopupBackground` en `GlassPill.shape` zoals de andere dialogen (1C1C1E in zwarte modus; in 0.0.131 was dat `surfaceContainerHigh`, zwart in zwarte modus). Een pill is `onSurface` op 16% met de rand `GlassPill.adaptiveBorder`; in 0.0.131 was dat `surfaceContainerHighest`, ook zwart in zwarte modus, dus zonder contrast met de kaart. Het volumedeel van een geselecteerde pill is `primary` op 35% (neutraal, of de gekozen contrastkleur); `primaryContainer` was het Material-paars. De 16% en 35% zijn een inschatting, niet op een scherm gezien.
- Niet veranderd: onderkaart, kop met nummer, pillhoogte, Show More, kopjes.

## 0.0.133
Op verzoek van de gebruiker (28 okt, na log 0.0.132: kies 1 en 3). Alleen Kotlin; C++ ongewijzigd. Niet gecompileerd, niet op een toestel geprobeerd.
- **Fasefout-log** (`MixConductor.kt`): de handoff-regel noemt nu ook `phaseSamples`, `firstPhaseErrMs`, `worstPhaseErrMs` (met teken: positief is B voor op het raster van A), `worstAtBeat` (beat van de overlap, negatief is vóór het begin van de blend), `worstWhileArmed`, `lastPhaseErrMs`, en van het plan `planStyle`, `planKind`, `planBeats`, `tempoRatioA`, `tempoRatioB` en `stemMixPlanned` (gepland, niet of de stems echt klaar waren). De meting loopt vanaf het armed-deel (B is gecued) tot en met de blend; `maxPhaseErrMs` blijft zoals het was (alleen de blend). Er verandert niets aan het geluid. In log 0.0.132 had 7 van 26 wissels meer dan 4 ms en 3 meer dan 10 ms; waardoor, is nog niet bekend.
- **Lange nummers overslaan** (`MixLibrary.kt`): de tempo-scan slaat gedownloade nummers over waarvan de bekende duur boven `PcmDecoder.MAX_SECONDS` (15 minuten) ligt, zonder te decoderen, en logt dat één keer per nummer per sessie ("tempo scan <id>: skipped, longer than 15 minutes (naam)"). Er wordt niets opgeslagen: na een herstart van de app komt de regel één keer terug. Een nummer dat niet gedownload is heeft hier geen bekende duur; dat blijft zoals het was (de bestaande oplopende wachttijd na een mislukte decode). Het decoden-pad van de engine (`decoded()`) is niet veranderd.

## 0.0.134
Op verzoek van de gebruiker (29 okt: bij Auto Mix, Genres, staat "No genres in the downloaded songs"; alleen genre). Alleen Kotlin; niet gecompileerd, niet op een toestel geprobeerd.
- **Oorzaak** (`DownloadManager.kt`): `toJson` en `fromJson` bewaarden het genre van een download niet, dus elk gedownload nummer had na het inlezen een leeg genre en de lijst in het Auto Mix-paneel bleef leeg.
- **Genre opslaan**: het veld `genre` staat nu in de downloadlijst (`index.json`); nieuwe downloads hebben het direct.
- **Bestaande downloads aanvullen**: bij het starten van `DownloadManager` leest een achtergrondtaak eenmalig de genre-tag (`MediaMetadataRetriever`, `METADATA_KEY_GENRE`) uit de bestanden zonder genre en slaat die op. Geen internet nodig, geen verzoeken aan de server. Een bestand zonder tag, of een formaat dat Android niet leest, houdt een leeg genre. Meerdere genres in één tag worden niet gesplitst (de tekst blijft zoals hij is). `bpm`, `plays` en `playCount` worden bewust niet bewaard.

## 0.0.135
Op verzoek van de gebruiker (29 okt: een mix speelde nummers van dezelfde artiest en van hetzelfde album, bij een album met meerdere artiesten, achter elkaar; en het AirPlay-menu). Alleen Kotlin; niet gecompileerd, niet op een toestel geprobeerd.
- **Albumregel** (`MixOrder.kt`): een nummer van hetzelfde album wacht 3 nummers (`ALBUM_COOLDOWN`), net als de artiest. Het album wordt herkend aan `albumId`, anders aan de albumnaam; een leeg album telt niet mee. De regel is zacht: blijft er alleen een nummer van dat album over, dan is de albumregel de eerste die vervalt, daarna de artiestregel. Daardoor kan een lijst die vooral uit één album bestaat nog steeds achter elkaar komen.
- **Het nummer dat nu speelt telt mee** (A): `MixModes.build` geeft de seed aan de ordening door; ook de volgordebepaling van de wachtrij (`djOrderUpcoming`) doet dat. Daar is het album de albumtitel uit de metadata (geen `albumId`).
- **Build-up en Cooldown** (C): de wachttijd loopt door over de blokken (de laatste 3 nummers van de vorige blokken gaan mee).
- **Niet gedaan:** vergelijken van artiesten op `artistId` (B); de artiestregel blijft op naam.
- **AirPlay-menu**: de tekst volgt nu `onSurface` (de Surface kreeg geen `contentColor`, daardoor was de tekst zwart in zwarte modus) en "Play on" heeft 16 dp ruimte onder zich.

## 0.0.136
Op verzoek van de gebruiker (29 okt: de artiest- en albumregels van Auto Mix moeten ook gelden voor de andere varianten, nu speelt een heel album door). Alleen Kotlin; niet gecompileerd, niet op een toestel geprobeerd. De wijzigingen uit de Files-app zijn niet meegenomen, ook Inter niet.
- **Het album wordt nu aan de naam herkend** (`MixOrder.itemOf`), niet meer aan `albumId`: de wachtrij kent alleen de albumtitel, dus alleen een naam is overal gelijk. Twee albums met dezelfde naam tellen als één (alleen wat meer afstand). Dit vervangt de zin over `albumId` in 0.0.135.
- **Gedeelde regel** (`MixOrder.spaced`): behoudt de volgorde van een lijst, maar neemt een nummer van dezelfde artiest of hetzelfde album pas na 3 andere nummers. Zacht: blijft er niets anders over, dan vervalt eerst de albumregel, daarna de artiestregel.
- **Bijvullen van een mode** (`PlaybackService`, refill): de laatste 3 nummers van de wachtrij tellen mee (voorheen alleen de seed). `MixModes.build` heeft hiervoor de parameter `recent`.
- **AutoPlay**: met Auto Mix en beat mixing wordt de ordening uitgevoerd met de laatste 3 nummers van de wachtrij ervoor; zonder beat mixing worden de gekozen nummers met `spaced` geordend.
- **Discovery Station**: de nieuwe nummers gaan door `spaced`, met de laatste 3 nummers van de wachtrij ervoor.
- **Home-mixes**: alleen Favourites (willekeurig) en Essentials (rangschikking met toeval) gaan door `spaced`. Niet aangepast: Heavy Rotation (volgorde is het aantal keer afgespeeld), New Music (nieuwste eerst), Featuring Artist (bewust één artiest), Get Up en Chill (volgorde op energie) en New Release (één nummer).
- **Niet veranderd**: de regels blijven zacht (zie hierboven); een filter dat bijna alleen één album overlaat (Genres, Tempozone, Chill) kan dus nog steeds een heel album opleveren.

## 0.0.137
Op verzoek van de gebruiker (29 okt, "1": alleen punt 1 van het voorstel). Alleen Kotlin; niet gecompileerd, niet op een toestel geprobeerd.
- **Oorzaak** (log 0.0.135, 21:50 tot 21:52): bij het kiezen van een mode in het AutoMix-menu werden alle nummers uitgesloten die voor het huidige nummer in de wachtrij staan. In een wachtrij van 341 nummers (speelt nummer 334) is dat bijna de hele bibliotheek: Tempozone 24 van 226 nummers, Chill 7, Energy 26, Build-Up 59 (aan het begin van de sessie gaf Mixed nog 96). De mode koos dus uit de nummers die in deze wachtrij nog niet gespeeld waren. Dat de overgebleven nummers vooral van één album zijn, is een aanname: het log noemt geen albums.
- **Wijziging** (`PlaybackViewModel.chooseAutoMixMode`): alleen de laatste 20 gespeelde nummers (met het huidige) en de vastgezette nummers worden uitgesloten (`RECENT_PLAYED = 20`, het getal dat de uitwijkregel van het bijvullen al gebruikt). Een nummer van eerder in de wachtrij kan dus opnieuw in een nieuwe lijst komen.
- **Niet veranderd**: het automatisch bijvullen (refill) sluit nog steeds de hele wachtrij uit en gebruikt pas bij een lege lijst de uitwijkregel; de logregel per lijst (albums, afvallers) is niet gebouwd.

## 0.0.138
Op verzoek van de gebruiker (29 okt, AutoMix-menu). Alleen Kotlin; niet gecompileerd, niet op een toestel geprobeerd.
- **Hoeken** (`AutoMixPanel.kt`): het menu heeft 16 dp (was 28 dp), gelijk aan de rijen erin. Gekozen door de gebruiker uit 16 dp en 24 dp.
- **Geen paars meer**: de achtergrond is `GlassPill.adaptivePopupBackground` (zoals het AirPlay-menu), de tekst volgt `onSurface`, de gekozen rij heeft `primary` met alpha 0,35 (was `primaryContainer`, het paarse) en de andere rijen hebben geen eigen achtergrond meer (waren `surfaceContainerHigh`, in zwarte modus zwart op donkergrijs).
- **Genres met hoofdletter**: de eerste letter van elk genre is een hoofdletter in de lijst en in de regel onder "Genres" (alleen de weergave; het filter blijft op kleine letters).
- **Niet gebouwd**: de trage vertoning van de nieuwe lijst en de covers (de gebruiker voelt meerdere seconden; het log toont 0,06 tot 0,5 s voor het maken van de lijst zelf). Er is nog niets gemeten voor het bijwerken van de wachtrij en het laden van de covers.

## 0.0.139
Op verzoek van de gebruiker (30 okt, "bouw alles" na de controle tegen de App Ontwerp-Handleiding). Alleen Kotlin; niet gecompileerd, niet op een toestel geprobeerd.
- **AutoMix-modi zonder lang indrukken** (`NowPlayingScreen.kt`): de AutoMix-tegel is 1,5 keer zo breed en heeft rechts een pijltje (48 dp breed) dat het modemenu opent. Lang indrukken blijft werken.
- **Volume met knoppen** (`AirPlayDialog.kt`): een gekozen uitvoer heeft − en + (stappen van 10 procent) naast het slepen.
- **Mixen laat zien dat het werkt**: de tegel toont "Mixing..." en een kleine voortgangscirkel tot de lijst in de wachtrij staat (`PlaybackViewModel.mixBusy`).
- **Tijdlog** (`MixTiming`): `listReadyMs`, `queueUpdatedMs` en `coversReadyMs` per gekozen modus.
- **Covers vooraf laden**: de eerste 12 covers van een nieuwe modus-lijst worden op de achtergrond in het geheugen gezet (128 px, zoals de wachtrij ze vraagt).
- **Lege staten met één knop** (`EmptyText`): "New playlist" (geen playlists), "Refresh" (lege serverlijsten: genres, artiesten, albums, gespeeld), "Clear search" (zoekopdracht zonder resultaat).
- **Sterren**: raakvlak 48 dp, het icoon blijft 28 dp.
- **Discovery-kaart** op de startpagina volgt de themakleur in plaats van vast groen.
- **Spacing op het raster**: 6, 10 en 14 dp in `padding` zijn 8, 12 en 16 dp (33 plekken). Rijen in lijsten zijn daardoor 4 dp hoger.
- **Haptiek, alleen per bedoeling**: lichte tik bij de vier schakelaars boven de wachtrij, middel bij het kiezen van een modus en bij het hart.
- **Skeleton**: lijsten tonen 8 grijze rijen die zacht pulseren in plaats van een draaiende cirkel (`LoadBody`).
- **Gecontroleerd en niet aangepast**: de 23 `contentDescription = null` zijn allemaal versiering naast tekst of een knop met eigen label (tabicoon met tekst, pijltjes, covers in rijen met titel). Geen wijziging nodig.
- **Niet gebouwd**: landscape en schermgroottes (keuze van de gebruiker nodig), zwart in zwart-modus (bewuste optie), zoekvelden met label, lettertype Inter.
- **Onzeker**: haptiek gebruikt `TextHandleMove` en `LongPress` omdat ik de Compose-versie niet kan nalopen; de Discovery-icoonkleur (`surface` op `primary`) is niet op contrast gemeten.

## 0.0.140
Op verzoek van de gebruiker (30 okt). Alleen Kotlin en een lettertype; niet gecompileerd, niet op een toestel geprobeerd.
- **Geen paars meer** (`DesignSystem.kt`): het paars zat in de standaardkleuren van Material 3 (duim van de schakelaar, chips, vinkjes, oppervlakken, randen). Nu zijn alle kleurrollen neutraal grijs ingesteld en volgen primary, secondary, tertiary en hun containers de gekozen contrastkleur (of wit/zwart bij Default). Zwarte modus blijft zwart. De oppervlakken in licht en donker zijn daardoor iets anders van tint dan voorheen (donker 121212 in plaats van het paarsige 141218).
- **Onderbalk** (`AppBottomBar.kt`, `GlassPillContainer.kt`): de selectie heeft hoek 20 dp, de balk 24 dp, en de selectie ligt 4 dp van elke rand (balk zonder eigen zijrand), dus ook bij het eerste en laatste tabblad liggen de bogen concentrisch.
- **Inter** (`AppFonts.kt`, `res/font/inter.ttf`, licentie in `THIRD-PARTY-LICENSES/inter`): variabel lettertype in vier gewichten, nu de standaard. Wie zelf een lettertype koos houdt dat; wie nooit koos krijgt Inter. Het bestand komt uit de Files-app (dezelfde latin-subset, 0,9 MB), op uitdrukkelijk verzoek.
- **Rijen 44 dp** zoals Photos (alle Settings-rijen en About). Schakelaars en radioknoppen in een Settings-groep voegen geen 48 dp aanraakvlak toe (de hele rij is het aanraakvlak).
- **Teksten korter**: AutoMix, Playback, Appearance, Backup, Server, melding bij AutoMix aan. De lange uitleg van AutoMix staat nu onder About > How to Use. Nieuw hulpmiddel `SettingsNote`.
- **Playback**: de sectie "Discovery and AutoPlay" heet nu "Learned taste" met één korte regel. De knop "Forget what was learned" bleef, die werkt wel (wist `TasteStore`).
- **Server**: bovenaan "Connection" met stip (groen verbonden, rood niet bereikbaar, grijs bezig), reactietijd, servertype/versie en API-versie; tik om opnieuw te controleren. Daaronder "Library" (nummers, albums, artiesten, afspeellijsten) en "Your hearts". Wat de server niet levert wordt weggelaten. Onzeker: het aantal nummers komt uit `getScanStatus` (`count`) en kan afwijken; albums zijn de som van `albumCount` van alle artiesten.
- **Downloads**: Overview (nummers, grootte, speelduur, albums, artiesten, vrije ruimte), opslaglimiet (Off, 1, 2, 5, 10, 20, 50 GB), Manage (verwijder nummers zonder hartje, verwijder een album uit de 8 grootste, verwijder alles; telkens met bevestiging). Bij de limiet verdwijnen de oudste nummers zonder hartje (bestandsdatum als downloaddatum); nummers met een hartje blijven en als die alleen de limiet al vullen, stopt het downloaden met "Storage limit reached." Onzeker: `starred` van een nummer is de waarde van het moment van downloaden.
- **About**: Links (GitHub `github.com/Nooklin/music`, info@nooklin.org) en een inklapbare How to Use (Get started, Find your music, The player, Downloads, AutoMix, Backup). Het GitHub-adres is door de gebruiker gekozen uit mijn gok en niet geverifieerd.
- **Niet gebouwd**: landscape, zoekvelden met label.

## 0.0.141
Op verzoek van de gebruiker (31 okt). Alleen Kotlin; niet gecompileerd, niet op een toestel geprobeerd.
- **Volumebalken in het uitvoermenu volgen de fysieke knoppen** (`AirPlayDialog.kt`): een AirPlay-pill start opnieuw vanuit `row.volume` zodra dat verandert; het telefoon- en Bluetoothvolume wordt opnieuw gelezen bij `android.media.VOLUME_CHANGED_ACTION` (ontvanger wordt bij sluiten van het menu afgemeld). Onzeker: of de fysieke knoppen tijdens AirPlay altijd via `AirPlayVolumePlayer` (`adjustAll`, per speaker +5 of -5) lopen; dat blijkt op het toestel.
- **Het schuifje van het systeem trekt de speakers niet meer gelijk** (`AirPlayController.setAll`): alle speakers worden met dezelfde factor geschaald (25 en 40 bij 50 gemiddeld worden 38 en 62). Bij 0 en 100 gaat de verhouding verloren. `adjustAll` (knoppen) en de − en + per pill veranderden al alleen per speaker.
- **Mixer, alleen meten** (`MixConductor.kt`): de handoff-regel in het log heeft `phaseTrack` met de fasefout elke 2 beats van de overlap (bijvoorbeeld `-2:0.1 0:0.2 2:1.5 ... 14:13.1`). Daarmee is te zien of een fout vlak na het begin ontstaat of groeit, en of hij terugkomt. Er is bewust niets aan de fasevergrendeling in `engine.cpp` veranderd: uit de logs is de oorzaak niet te halen en een host-simulatie met een gelijkmatig driftend raster gaf eerder minder dan 0,6 ms.
- **How to Use, AutoMix**: de zin dat AutoMix niet werkt tijdens AirPlay klopte niet (sinds 0.0.110 wel) en is eruit; de tempoverandering is 30% in totaal (`MAX_TEMPO_CHANGE`), de getallen 22% en 8% uit de oude tekst zijn weg.

## 0.0.142

Only one change: the phase lock in the mix engine (`engine.cpp`) is faster and better damped.

- Evidence: in the 0.0.141 log the `phaseTrack` of blends with a tempo change or stem mix shows an error that appears at beat 0 and then swings with sign flips. A model of the old loop (P 0.83, I 0.69, trim limited to 3%) explains this: damping about 0.5 and one swing of about 7 s.
- Change: P 6.0, I 9.0 (integral scaled by block time). Trim limits unchanged (3% total, 1.5% integral).
- Host test `host/test_lock.cpp` (worst phase error, old to new): 0.5% tempo mismatch 3.3 to 0.25 ms, 1.5% 9.9 to 0.75 ms, 3% 19.6 to 3.3 ms. 0% mismatch unchanged.
- Not verified: how audible the faster trim is (pitch wobble), and behaviour on a device. At 3% mismatch the trim cap is still reached (end error about -2.4 ms).
- Not compiled for Android. The C++ is tested on the host only.

## 0.0.143

Only a log line, no change in behaviour. Log 0.0.142 showed `listReadyMs=16085` for the first AutoMix mode choice and 198 to 559 ms for the next three. The new line `MixTiming prepareMs=.. buildMs=..` splits that time into `MixModes.prepare` (preload from disk, energy backfill) and `MixModes.build`. The cause of the 16 s is not known yet; a busy processor at that moment (a song start and a mix plan ran at the same time) is a guess.

## 0.0.144

Log 0.0.143 showed where the 15 s went: `prepareMs` 2 to 6 ms, `buildMs` 15 023 ms (energy) and 15 633 ms (mixed), against 75 and 144 ms for build_up and chill. The slow modes order 120 places (3 times the list size) and keep 40.

- Change: `MixOrder.fill` got `maxPlaces`; the DJ order in `MixModes.build` stops after `size` places. The choice for a place only looks at the places before it, so the first 40 are the same as before (the choice among the best few is random, as before).
- Also applies to the refill in `PlaybackService`, which calls the same `build`.
- Expected gain: about 3 times faster. Not measured; not compiled for Android.
- Not changed: the repeated `first()` in the sort, the look-ahead depth, the pool size.

## 0.0.145

Diagnostics only, no change in behaviour. Log 0.0.144 (3.5 minutes after choosing a mode) showed three threads at 80 to 100% of a core the whole time: the main thread, `ExoPlayer:Loader` and `DefaultDispatch` (the list build, about 155 s of processor time), with 34.7% janky frames, no thermal throttling and no memory shortage. The cause is not known.

- New `StackSampler` (started in `MainActivity.onCreate`): every 2 s it logs, under the tag `NooklinProf`, the top 12 frames of at most 8 threads that are running (not waiting). Read it with `adb logcat -s NooklinProf`.
- To be removed again once the cause is found. Not compiled for Android.
- The sampler thread is a daemon and sleeps between samples; its cost is not measured.

## 0.0.146

Smoother main thread. The stack samples of logs 0.0.144 and 0.0.145 (270 samples of the main thread) put about a quarter of its work in `MixConductor.step` (`MixMap.withVocal` 29, `step` 23, `tempoCheck` 3), the rest mostly in Compose layout and semantics. The graphics statistics of that process showed 56% janky frames (median 57 ms), with the GPU at 1 to 2 ms, so the main thread is the slow part.

- `MixConductor.step` runs every 100 ms. It copied the maps of both songs (`withVocal`) and ran `tempoCheck` twice (with text formatting) each time, also when nothing had changed. The result is now kept (`PlanInputs`) and worked out again only when one of its inputs changes (compared as objects). The plan itself is unchanged.
- `QueueStore.save` is called on the main thread by the service; the text of the queue is now built on `Dispatchers.Default`. The queue itself is read from the player on the main thread as before.
- Not changed, found in the samples: Compose semantics and the accessibility delegate take about a quarter of the main thread samples (40 and 26 of 270). That is work Compose does when an accessibility service is switched on; whether one is on on this phone is not known yet.
- Not measured on a device; not compiled for Android. The effect on the 56% is unknown.

## 0.0.147

The queue no longer grows for hours, and the position no longer redraws the whole app. Source: the log of 8 November 2026 (queue with very many songs in Up Next after a long AutoMix session; frame statistics 2.6% janky after switching the accessibility service off, with about 12 frames over 100 ms in 34 s; main thread not waiting for the processor: 0.13 s of waiting in 7.96 s of running) and the static audit `PERFORMANCE-AUDIT.md` (points P1, P2, P4).

- Queue limit (new, `PlaybackService.trimPlayed`, called every 5 s from the refill loop): while an AutoMix mode, AutoPlay or the Discovery Station fills the queue, the played songs in front of the song that plays are taken off once there are 30 of them, down to 20 (`KEEP_PLAYED` 20, `TRIM_STEP` 10). The songs that come next are not touched. Not with shuffle on, not while a crossfade runs (`CrossfadeController.busy`), and not for a list the user made: repeat all with AutoPlay or Discovery only (no AutoMix mode) is left alone, because the loop would get shorter. `CrossfadeController.frontRemoved` moves the index of an armed song.
- The ids of the songs taken off are kept (`playedAway`, in memory, at most 2000, oldest first) and count as played: the AutoMix refill, AutoPlay and Discovery leave them out, as they left out the songs in the queue before. When a new round starts (everything that fits was played) the memory is emptied; the queue still holds the last 20 played songs, which are left out. The memory is also emptied when a new queue is started. It is not saved: after the app restarts, the songs taken off earlier can come back at once.
- Difference from before: earlier, after the first full round every refill was a "new round" that left out only the last 20 played songs and the songs that come next. Now a round runs until everything that fits was played, then a new round starts. The mix does not repeat a song earlier than before, and usually later.
- P1: `positionMs` is no longer part of `PlayerState`; `PlaybackViewModel.positionMs` is its own flow. The slider and the two clocks moved to `SeekBar`, the lyrics read the flow themselves (`LyricsLines`). Before, every tick made a new `PlayerState`, so everything that reads the state (mini player, now playing, the queue panel) was drawn again twice a second.
- P4: `refresh()` builds the rows of the queue and the order of the songs that follow only on the events that change them (timeline, song change, metadata, shuffle) and when the service publishes new extras; other events (play/pause, buffering) keep the old rows.
- P2: `StackSampler` is removed (0.0.145 diagnostics). The tag `NooklinProf` no longer exists. For a new measurement, use `dumpsys gfxinfo` and `top -H`.
- Not done, on purpose: P3 (loading files on the main thread at start, needs a choice and 226 downloads is small), P5 to P11, a stall detector.
- Risks, not tested: `removeMediaItems` on the playing player while the native engine (MixConductor) plays. The conductor looks the songs up by id and reads the next index again each step, but I did not run it. If a mix breaks right after the queue was cut (look in the log for "took ... played songs off the front"), tell me.
- Not compiled for Android; not run on a device. The effect on the stutter is unknown; it is a hypothesis from the audit.

## 0.0.148

Performance, second part (points of PERFORMANCE-AUDIT.md).

- P6: the queue is saved at most once per 1.5 s (newest state); onDestroy still saves directly.
- P7: lists in NowPlayingScreen and UpNextScreen are computed in `remember`.
- P8: covers: the disk read is no longer under the lock, at most 4 decodes at once, `asImageBitmap()` remembered.
- P5: the AutoMix loop waits 500 ms when nothing plays and no engine runs (was 250 ms); the AirPlay tap thread sleeps 250 ms when AirPlay does not tap (was 40 ms). Checked in tap_ring.h: with the tap off, push() does nothing and enabling the tap flushes the ring, so nothing overflows.
- P11: AutoMix log file is written on its own thread; PlayerSheet reads the drag offset only through two derived booleans in composition (translation stays in graphicsLayer).
- P3: new `NooklinApp` (Application) opens DownloadManager, TasteStore and HomeMixes on a background thread; DownloadManager merges removals and genre updates into one index write a second later (a finished download and pause() still write at once).
- Left as is on purpose: `key = index` in SongsScreen and PlayedSongsScreen, armeabi-v7a, runBlocking in onDestroy.
- Not done: P9 and P10 (advice only).
- Risks: the 500 ms idle wait can delay the start of the AutoMix engine by up to 0.5 s after pressing play (not measured). A crash within 1 s after a removal can bring a removed song back in the list. Not compiled or run on Android here.

## 0.0.149

AutoMix more visible; the AutoMix button on the Now playing screen is unchanged and stays the place to switch off and to pick a mode.

- A: a card "AutoMix" at the top of Home (above the sections, not part of the Home settings). One tap = `chooseAutoMixMode` with the last used mode (Mixed the first time): AutoMix on, a list built from downloaded, analysed songs. Shows "On, <mode>. Tap for a new mix" when AutoMix is on.
- B: a line "AutoMix, <mode>. Next: <title>" in the mini player and on Now playing (both layouts), only while AutoMix is on. The next title is the first song of the upcoming list.
- Not done on purpose: a countdown to the next transition (the conductor does not pass that time to the UI; nothing half-built was added).
- Risks: the Home card does not show the AutoMix notice about downloaded songs; with no fitting songs a toast explains. The mini player is one line taller while AutoMix is on. Not compiled or run on Android here.

## 0.0.150

Fix: 0.0.149 did not compile (`autoMixLine` was not imported in NooklinMusicApp.kt). Import added; nothing else changed. Not compiled here; the other new references (AutoMixBadge, AutoMixCard) are in the same package as their callers.

## 0.0.151

- Home AutoMix card: while the mix is made the icon turns into a spinner and the text says "Making your mix..." (tap disabled); when it is ready a toast says "New AutoMix started: <mode>, N songs" (only for the Home card, not for the mode menu).
- AutoMix badge (mini player and Now playing) now has two lines: "AutoMix, Mode: Mixed" and "Next: Artist - Title" (second line in the softer colour; centred on the big-cover layout).
- Now playing queue panel: the header "Up Next" with Clear now sits under the four buttons (Shuffle, Repeat, AutoPlay, AutoMix); was above them. Text changed from "Up next" to "Up Next".
- Read this as: the request "Up Next en Clear onder de knoppen Bar" meant the queue panel in Now playing. If the Up Next screen opened from Home was meant, say so.
- Not compiled or run on Android here.

## 0.0.152

- Mini player: the AutoMix lines are removed again; it shows what plays, as before 0.0.149.
- Now playing (big cover layout): the AutoMix badge is centred (the lines themselves were centred in a text box that sat at the left; the column is now centred). The compact layout (small cover on the left) stays left aligned on purpose.
- Home: the AutoMix card and the Discovery Station tile have their own colours (indigo to teal, orange to pink gradient) with white icon and text; before they used the app accent, which is white when no accent is chosen.
- Fixed colours do not follow a chosen accent; say so if they should.
- Not compiled or run on Android here.

## 0.0.153

- Home AutoMix card: when a song was paused (or the queue had run out), the music now starts after the new mix is made; a song that plays keeps playing. When nothing was queued it already started (startQueue plays). Only the Home card does this; the mode menu in Now playing is unchanged (its user is already in the player and chooses to play).
- Risk: after the queue ran out (STATE_ENDED) the code jumps to the next item before playing; not tested. Not compiled or run on Android here.

## 0.0.154

Only logging, to find why a Denon (AirPlay 2) shows no sound although pairing, SETUP, RECORD and /feedback all succeed (log of 2026-11-11: no sign of audio leaving the phone).

- AirPlayController: once per second (every 125 packets) one line with the tag NooklinAirPlay: `STATS packets with audio=N silent=N peak=X/32768 queue=N frames tapping=.. mixOwns=.. muteLocal=.. sessions=N [name: audio=<RTP packets sent> sync=<sync packets sent>]`.
- PcmTap: `available()` (frames in the queue), used only for that line. ReceiverSession: `stats()`.
- How to read it: audio > 0 and peak > 0 = sound is delivered to the sender; silent = all packets = nothing reaches the tap (tapping false, or mixOwns true with nothing from the engine); peak 0 with audio > 0 = silence in the data; audio and sync counters not rising = packets are not sent; "NO SENDER" = session without sender.
- Nothing else changed. Not compiled or run on Android here.

## 0.0.155

- Now playing: the AutoMix line says whether AutoMix is on or off. On: a filled green dot and "AutoMix On  ·  Mode: Mixed" in the normal text colour, with "Next: Artist - Title" under it (soft colour). Off: an empty ring and "AutoMix Off" in the soft colour, nothing under it. It shows whenever something is loaded. Centred on the big-cover layout, left in the compact layout (as before). The mini player still shows nothing of it.
- The green is a fixed colour (does not follow the app accent).
- Not compiled or run on Android here.

## 0.0.156

Only logging (option 1 for the Denon without sound). Findings of the logs of 2026-11-11: the phone sends audio and sync packets correctly to the Denon AVR-X2400H (125 audio packets/s, sync every second, no send errors) and the same code makes sound on an AirPort Express (AirPlay 2.0.2) with the same RTSP flow, so the difference is in what the Denon does with the stream.

- RtspSession.feedback(): the body of the /feedback answer (85 bytes, the Denon reports `keepAliveSendStatsAsBody=true`) is decoded and logged with tag AirSyncRtsp as `FEEDBACK body #N`, when the content changes and at least every 15th answer (about 30 s). An empty body is logged once.
- Nothing else changed. Not compiled or run on Android here.

## 0.0.157

Fix for no sound on the Denon AVR-X2400H (AirPlay 2.0.10) after research (OwnTone rtp_common.c, the AirPlay spec page "RTP streams", other projects).

- Cause found in the code: the sync packet (control port, once per second) had the stream START time (always 0) in bytes 16..19. The spec says "RTP timestamp for the next audio packet" and OwnTone writes the running sample position there. Receivers derive their latency from bytes 16..19 minus bytes 4..7, so with 0 the Denon saw a nonsense latency and played nothing; an AirPort Express ignores the field and played. This is the best explanation found, it is not proven for the Denon.
- Now: bytes 4..7 = position minus 11025 (wrapping at the start like a real sender, no clamp to 0), bytes 16..19 = next packet. Same for the sync that is sent when a second speaker joins. SyncPacket.build(currentRtpPos, nextRtpPos, isFirst) (was startRtpTime).
- Log: one line `SYNC #N first=.. playingNow=.. nextPacket=.. latency=11025` for the first sync and every 30th. The STATS and FEEDBACK lines of 0.0.154 and 0.0.156 stay.
- Not changed: latency 11025/88200 in SETUP, order of RECORD and SETUP, volume.
- Risk: the AirPort Express worked with the old value; it should work with the correct one, not tested. Not compiled or run on Android here.

## 0.0.158

Second fix for no sound on the Denon AVR-X2400H (0.0.157 did not help: sync packets were correct in the log, still no sound; AirPort Express still fine).

- Found in research: Music Assistant's airplay-cli issue #94 says that Apple uses the RTP SSRC to tell the receiver what the audio is, and that Shairport Sync drops packets whose SSRC is 0 ("SSRC_NONE"); the same code base sets `ssrc = session_id` for NTP sessions. Our sender always used SSRC 0. A Denon with the same Apple AirPlay SDK can do the same: decrypt fine and play nothing. Not proven for the Denon.
- Now the SSRC of every audio packet is the RTSP session id, the number that is also sent as `streamConnectionID` in the stream SETUP (32 bit). AudioSender got an `ssrc` parameter (default 0); ReceiverSession passes `session.sessionId.toInt()`. The ChaCha20 AAD already contains the SSRC (bytes 4..11), so nothing else changes. Logged on the "ready:" line as `ssrc=`.
- Not changed: latency, order of RECORD and SETUP, volume, the 0.0.157 sync fix, the STATS, FEEDBACK and SYNC logging.
- Risk: the AirPort Express worked with SSRC 0; it should also work with the session id (airplay-cli does that), not tested. Not compiled or run on Android here.

## 0.0.159

Test build for the Denon AVR-X2400H without sound (0.0.157 sync fix and 0.0.158 SSRC fix did not help; AirPort Express works with both). Two changes at once, on purpose, to find out quickly whether one of them is the cause; if sound comes, the two will be tried apart.

- Latency: the audio now plays 88200 frames (2 s) after it is sent (was 11025, 250 ms). The sync packets use this value (bytes 4..7 = position minus 88200, bytes 16..19 = next packet); the range announced in the stream SETUP is unchanged (min 11025, max 88200). Apple senders use about 2 s. Effect for every receiver: sound starts about 2 s after pressing play, and the AirPlay progress on the receiver may be about 2 s off.
- Order: RECORD is sent after the audio stream SETUP (was before), then the MediaRemote commands. This is the order of the AirPlay spec.
- Logging unchanged: STATS, FEEDBACK, SYNC (now `latency=88200`), ssrc on the ready line.
- Risk: the AirPort Express could refuse the new order or the larger buffer (not tested). Not compiled or run on Android here.

## 0.0.160

- AirPlay menu ("Play on"): wider (the dialog no longer uses the standard dialog width; it is 12 dp from the screen edge on both sides) and the pills (Phone Speaker, Bluetooth, AirPlay speakers) are 52 dp high instead of 60 dp. Nothing else in the menu changed.
- Contains the Denon test changes of 0.0.159 (2 s buffer, RECORD after the audio SETUP), unchanged.
- Not compiled or run on Android here; I did not see the menu on a screen, so the width and height are a guess of "a bit": say if it should be more.

## 0.0.161

- AirPlay menu: as wide as the cover on the Now playing screen. The cover is the width of the screen minus 24 dp on each side (when the height allows, which it does on the phone in portrait), so the menu now is 24 dp from the screen edge on both sides (was 12 dp in 0.0.160). On a screen so low that the cover is limited by its height (landscape) the menu stays wider than the cover.
- Denon AVR-X2400H: sound confirmed by the user with 0.0.159 (log of 2026-11-11 09:19: 2 s latency, RECORD after the audio SETUP; the SSRC and sync fixes of 0.0.157 and 0.0.158 are in too). Which of the two changes of 0.0.159 is the real fix is not known yet.
- Not compiled or run on Android here.

## 0.0.162

Test build to find the real fix for the Denon AVR-X2400H: the latency is back to 11025 frames (250 ms), as before 0.0.159. RECORD after the audio stream SETUP stays, and so do the sync fix (0.0.157) and the SSRC (0.0.158).

- If the Denon plays: the RECORD order was the fix and the 2 s buffer is not needed (so no extra delay for other receivers).
- If the Denon is silent again: the 2 s buffer is needed; then it is put back (0.0.159) and the delay either stays for every receiver or only for receivers that report themselves as Denon.
- The log line SYNC shows `latency=11025`.
- Not compiled or run on Android here.

## 0.0.163

Final state for the Denon AVR-X2400H, following the AirPlay standard for every receiver (decision of the user: a universal solution).

- Test of 0.0.162 (RECORD after the audio SETUP, latency 11025): Denon silent. So the 2 s buffer is needed. 0.0.161 (2 s plus RECORD after SETUP) played.
- Now: latency 88200 frames (2 s) for every receiver, like Apple senders; RECORD after the audio stream SETUP (the spec order); sync packet with the next packet in bytes 16..19 (0.0.157); SSRC = session id (0.0.158). Same behaviour as 0.0.161, comments cleaned up, no test text left.
- Effect: sound starts about 2 s after pressing play on every AirPlay receiver (the AirPlay menu already says so).
- Not tested whether the RECORD order or the SSRC or the sync fix are needed on top of the 2 s; they follow the spec, so they stay.
- Logging stays (STATS, FEEDBACK, SYNC, ssrc on the ready line).
- Not compiled or run on Android here.

## 1.0.0

First public release. Logging cleanup, no functional changes.

- All Verbose, Debug and Info log lines are removed (182), including the HTTP header dump and the AirPlay STATS, FEEDBACK and SYNC diagnostics.
- The file `automix_log.jsonl` and the JSON event log of the AutoMix engine are gone, so nothing about the songs played is written to the app folder or to logcat.
- The Warn and Error lines that remain no longer contain speaker names, addresses, ports, song ids or exception texts (only the exception type), and no stack traces. Nothing in the logs can be traced back to a user.
- Version is now 1.0.0 (versionCode 192).
- Not compiled or run on Android here. The change was made by script, so the first build must be checked for compile errors.

## 1.0.1

- Colorful display: Settings > Appearance has a switch, on by default, that shows the icons in the settings on coloured rounded tiles (iOS settings look, same tile colours as Nooklin Calendar). Off gives the plain icons as before. The setting is part of the encrypted backup. Not compiled or run on Android here.
- Tour: a card tour of eight steps over Home at the first start and under Settings > Tour, as in Nooklin Calendar. Skip and Done close it.
- Nooklin top bar: the Material TopAppBar is replaced by `NooklinTopBar` on every screen. Titles shrink from 34 sp down to 20 sp until they fit on one line, so "Recently played" and other long titles no longer get cut off next to the Play, Shuffle, Refresh and "..." buttons. `FitTitle` does the same in `SearchableFrame`.
- Dev build: the debug build now has the id `org.nooklin.music.dev`, version name `1.0.1-dev` and the name "Nooklin Music Dev", so it installs next to the release app. It has its own data, so server and settings are set up again (or restored from a backup).
- Songs have no screen of their own. A tap on a song starts it. With AutoMix on (and a mode chosen) a new mix is made around that song: the song plays first, then the mode list that suits it. Without AutoMix the list plays from that song, as before. Play and Shuffle buttons are unchanged.
- Album screen as in the design reference: big cover, title, artist, genre, year and format, a row with Shuffle, Play and the favourite heart, the songs with their number and a "..." each, and "N Songs, X min" under them. The background takes its colour from the cover. Back and the album "..." menu float at the top. This replaces the button rows of 1.0.1.
- Albums have a "..." menu behind the heart in every album list (Albums, Recently added/played, Favourites, Artist, Genre, Search): Play, Play next, Add to queue, Add to playlist, Download, Share, Favourite and Go to artist.
- Not compiled or run on Android here.
- AutoMix badge: the dot is red when AutoMix is off (it was an empty ring), green when it is on.
- Tour as in Nooklin Calendar 0.0.32: the steps that point at a button make it pulse, the rest of the screen is dimmed and blocked, and the tour moves on by itself when the person taps the button and the screen opens (Settings, Server, Library, the player). Steps with explanations have bullet points and Next; Skip ends the tour at any time, also from the Back gesture. The player steps are left out when nothing is playing.
- Tour and the server: without a saved server the tour makes the person add one first (Settings, Server, Save) and does not go on before it is saved. Those steps have no Skip and the Back gesture does nothing there. With a server already saved they are left out.
- Artist screen: round picture, name, album and song count, a row with Shuffle, Play and the favourite heart, Start radio, an About card with the biography (More/Less), Favorite songs and Albums with a count-free title each. The top takes a soft colour from the picture. Back and a Refresh menu float at the top.
- Home block "Radio Stations": round pictures of the artists you play most (random artists when there is no play history). A tap plays only songs of that artist. AutoMix on: an AutoMix mix of the downloaded songs of that artist, started with one of them (the artist spacing rule of the ordering gives way when only one artist is left). With fewer than 5 downloaded songs of the artist a notice says so and a normal AutoMix of the downloaded songs starts instead. AutoMix off: the songs of the artist from the server, shuffled; downloaded is not needed. It can be hidden or moved in Settings > Home.
- Library screen: the rows follow Colorful display (coloured icon tile per row, also in Edit Sections), like the settings rows.
- Docs: README (features, dev build), the tour text (Radio Stations) and the status line of this file brought up to 1.0.1.
