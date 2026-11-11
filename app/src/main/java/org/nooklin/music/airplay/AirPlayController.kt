package org.nooklin.music.airplay

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.nooklin.music.airplay.discovery.AirPlayReceiver
import org.nooklin.music.airplay.discovery.DeviceProfile
import org.nooklin.music.airplay.discovery.ReceiverDiscovery
import org.nooklin.music.airplay.raop.audio.AlacEncoder
import org.nooklin.music.airplay.raop.audio.SyncPacket
import org.nooklin.music.airplay.raop.auth.AirPlayHttpClient
import org.nooklin.music.airplay.raop.auth.CredentialStore
import org.nooklin.music.airplay.raop.auth.HapPinPairing
import org.nooklin.music.airplay.raop.auth.PairingProbe
import org.nooklin.music.airplay.raop.metadata.DmapEncoder
import org.nooklin.music.airplay.raop.timing.TimingServer
import org.nooklin.music.airplay.util.DeviceIdentity

/** One speaker in the list. */
data class AirPlayRow(
    val key: String,
    val name: String,
    val kind: DeviceProfile.Kind,
    val subtitle: String,
    val connected: Boolean,
    val connecting: Boolean,
    val volume: Int
)

/** The speaker shows a code that has to be typed in. */
data class PinPrompt(val key: String, val name: String, val attempt: Int)

data class AirPlayUiState(
    val rows: List<AirPlayRow> = emptyList(),
    val scanning: Boolean = false,
    val message: String? = null,
    val pin: PinPrompt? = null,
    val active: Boolean = false,
    /** Average volume of the connected speakers, 0 to 100 */
    val master: Int = 0
)

/**
 * Plays what the player plays on AirPlay speakers. The audio comes from [PcmTapProcessor] through [AirPlayAudio]; this
 * class finds the speakers, pairs and connects with them, and sends the audio in real time to all connected speakers
 * (one shared timeline, so they stay in step). While the player is paused it keeps sending silence.
 *
 * The protocol code in the raop folder is copied from AirSync, which has been used with AirPort Express and Apple TV.
 */
class AirPlayController private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = CredentialStore(app)

    init {
        AirPlayNetwork.init(app)
    }
    private val prefs = app.getSharedPreferences("nooklin_airplay", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(AirPlayUiState())
    val state: StateFlow<AirPlayUiState> = _state.asStateFlow()

    private var discovered: List<AirPlayReceiver> = emptyList()
    private var scanners = 0
    private var scanJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val connecting = mutableSetOf<String>()
    private var message: String? = null
    private var pin: PinPrompt? = null
    private var pinClient: AirPlayHttpClient? = null
    private var pinPairing: HapPinPairing? = null
    private var pinReceiver: AirPlayReceiver? = null

    private val sessions = CopyOnWriteArrayList<ReceiverSession>()
    private var timing: TimingServer? = null
    private var timingPort = 0
    @Volatile private var running = false
    @Volatile private var currentRtp = 0L
    @Volatile private var currentSeq = 0
    private var healthJob: Job? = null
    private val failures = HashMap<String, Int>()

    // The song on the display of the speakers
    private var title: String? = null
    private var artist: String? = null
    private var album: String? = null
    private var durationSeconds = 0.0

    // ---- Finding speakers ---------------------------------------------------------------------------------------

    /** Starts looking for speakers while a screen shows the list. Every call needs a [stopScan]. */
    @Synchronized
    fun startScan() {
        scanners += 1
        if (scanJob != null) return
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("nooklin-airplay").apply {
            setReferenceCounted(false)
            acquire()
        }
        scanJob = scope.launch {
            ReceiverDiscovery(app).discover().collect {
                discovered = it
                publish()
            }
        }
        publish()
    }

    @Synchronized
    fun stopScan() {
        scanners = (scanners - 1).coerceAtLeast(0)
        if (scanners > 0) return
        scanJob?.cancel()
        scanJob = null
        try { multicastLock?.release() } catch (_: Exception) {}
        multicastLock = null
        publish()
    }

    private fun shortName(name: String) = name.substringAfter('@')

    private fun volumeFor(key: String) = prefs.getInt("vol.$key", 20).coerceIn(0, 100)

    private fun publish() {
        val rows = discovered.map { r ->
            val key = DeviceProfile.keyFor(r)
            val profile = DeviceProfile.of(r)
            val session = sessions.firstOrNull { it.deviceKey == key }
            AirPlayRow(
                key = key,
                name = shortName(r.name),
                kind = profile.kind,
                subtitle = profile.subtitle(),
                connected = session != null,
                connecting = key in connecting,
                volume = session?.currentVolume ?: volumeFor(key)
            )
        }.sortedBy { it.name.lowercase() }
        _state.value = AirPlayUiState(
            rows = rows,
            scanning = scanJob != null,
            message = message,
            pin = pin,
            active = sessions.isNotEmpty(),
            master = if (sessions.isEmpty()) 0 else sessions.sumOf { it.currentVolume } / sessions.size
        )
    }

    // ---- Connecting ---------------------------------------------------------------------------------------------

    fun toggle(key: String) {
        val receiver = discovered.firstOrNull { DeviceProfile.keyFor(it) == key } ?: return
        if (sessions.any { it.deviceKey == key }) disconnect(key) else connect(receiver)
    }

    private fun connect(receiver: AirPlayReceiver) {
        val key = DeviceProfile.keyFor(receiver)
        scope.launch {
            val added = synchronized(connecting) { connecting.add(key) }
            if (!added) return@launch
            message = null
            publish()
            try {
                val port = receiver.airplayPort
                if (port != null) {
                    // A Mac accepts pairing without a code but then nothing can be stored, so it always gets the code flow
                    val forcePin = DeviceProfile.of(receiver).kind == DeviceProfile.Kind.COMPUTER
                    val outcome = PairingProbe.check(app, receiver.host, port, key, forcePin)
                    if (outcome == PairingProbe.Outcome.NEEDS_PIN) {
                        startPin(receiver, key, 1)
                        return@launch
                    }
                }
                join(receiver, key)
            } catch (e: Exception) {
                Log.e(TAG, "Could not connect to the speaker")
                message = "${shortName(receiver.name)}: ${e.message ?: "could not connect"}"
            } finally {
                synchronized(connecting) { connecting -= key }
                publish()
            }
        }
    }

    private fun join(receiver: AirPlayReceiver, key: String) {
        if (timing == null) {
            timing = TimingServer().also { timingPort = it.start() }
        }
        val session = ReceiverSession(app, receiver.name, receiver.host, receiver.raopPort, key)
        try {
            // 11025 and 88200 samples: about 2 seconds of buffering in the speaker, the AirSync default
            session.connect(
                timingPort = timingPort,
                latencyMin = 11025,
                latencyMax = 88200,
                initialVolume = volumeFor(key)
            ) { reason -> onLost(session, reason) }
        } catch (e: Exception) {
            session.close()
            if (sessions.isEmpty()) {
                timing?.close()
                timing = null
            }
            throw e
        }
        sessions.add(session)
        if (!running) {
            startStream()
        } else {
            // Anchor the new speaker to the running timeline at once
            try {
                session.sendSync(SyncPacket.build(currentRtp - LATENCY_FRAMES, currentRtp, true))
            } catch (e: Exception) {
                Log.w(TAG, "First sync failed: ${e.javaClass.simpleName}")
            }
        }
        AirPlayAudio.muteLocal = true
        AirPlayAudio.tapping = true
        startHealth()
        sendNowPlaying(session)
        publish()
    }

    fun disconnect(key: String) {
        val session = sessions.firstOrNull { it.deviceKey == key } ?: return
        sessions.remove(session)
        scope.launch { session.close() }
        if (sessions.isEmpty()) stopAll() else publish()
    }

    /** Ends AirPlay: the phone plays again. */
    fun stopAll() {
        running = false
        AirPlayAudio.tapping = false
        AirPlayAudio.muteLocal = false
        healthJob?.cancel()
        healthJob = null
        val old = sessions.toList()
        sessions.clear()
        scope.launch {
            old.forEach { try { it.close() } catch (_: Exception) {} }
            timing?.close()
            timing = null
        }
        AirPlayAudio.clear()
        publish()
    }

    private fun onLost(session: ReceiverSession, reason: String) {
        if (!sessions.remove(session)) return
        message = reason
        scope.launch { session.close() }
        if (sessions.isEmpty()) stopAll() else publish()
    }

    private fun startHealth() {
        if (healthJob != null) return
        healthJob = scope.launch {
            while (isActive) {
                delay(2000)
                for (session in sessions.toList()) {
                    if (session.isAlive()) {
                        failures.remove(session.deviceKey)
                    } else {
                        val count = (failures[session.deviceKey] ?: 0) + 1
                        failures[session.deviceKey] = count
                        if (count >= 3) {
                            failures.remove(session.deviceKey)
                            onLost(session, "Lost the connection to ${shortName(session.name)}")
                        }
                    }
                }
            }
        }
    }

    // ---- Pairing with a code (Apple TV) ---------------------------------------------------------------------------

    private fun startPin(receiver: AirPlayReceiver, key: String, attempt: Int) {
        val port = receiver.airplayPort ?: throw IllegalStateException("This speaker has no pairing port.")
        pinClient?.close()
        val client = AirPlayHttpClient(receiver.host, port)
        val pairing = HapPinPairing(client, DeviceIdentity.name(app))
        try {
            // The speaker keeps the pairing on this connection, so it stays open while the user types the code
            pairing.start()
        } catch (e: Exception) {
            client.close()
            throw IllegalStateException("Could not start pairing: ${e.message}")
        }
        pinClient = client
        pinPairing = pairing
        pinReceiver = receiver
        pin = PinPrompt(key, shortName(receiver.name), attempt)
        publish()
    }

    fun submitPin(code: String) {
        val prompt = pin ?: return
        val pairing = pinPairing ?: return
        val receiver = pinReceiver ?: return
        scope.launch {
            try {
                val credentials = pairing.finish(code.trim())
                store.save(prompt.key, credentials, name = prompt.name)
                pinClient?.close()
                clearPin()
                connect(receiver)
            } catch (e: Exception) {
                pinClient?.close()
                // This try is spent and the speaker shows a new code: start over
                if (prompt.attempt < 5) {
                    message = "${e.message ?: "Pairing failed"}. A new code is shown."
                    try {
                        startPin(receiver, prompt.key, prompt.attempt + 1)
                    } catch (e2: Exception) {
                        clearPin()
                        message = e2.message
                        publish()
                    }
                } else {
                    clearPin()
                    message = e.message ?: "Pairing failed"
                    publish()
                }
            }
        }
    }

    fun cancelPin() {
        pinClient?.close()
        clearPin()
        publish()
    }

    private fun clearPin() {
        pin = null
        pinClient = null
        pinPairing = null
        pinReceiver = null
    }

    // ---- Volume and display ---------------------------------------------------------------------------------------

    fun setVolume(key: String, percent: Int) {
        val value = percent.coerceIn(0, 100)
        prefs.edit().putInt("vol.$key", value).apply()
        val session = sessions.firstOrNull { it.deviceKey == key }
        if (session != null) {
            session.currentVolume = value
            scope.launch {
                try { session.setVolume(value) } catch (e: Exception) { Log.w(TAG, "Volume failed: ${e.javaClass.simpleName}") }
            }
        }
        publish()
    }

    /** Volume keys: every connected speaker moves by the same step. */
    fun adjustAll(delta: Int) {
        for (session in sessions.toList()) setVolume(session.deviceKey, session.currentVolume + delta)
    }

    /** The volume slider of the system: every connected speaker gets this volume. */
    fun setAll(percent: Int) {
        val list = sessions.toList()
        if (list.isEmpty()) return
        // 0.0.141: the speakers keep their distance: all are scaled by the same factor (25 and 40 become 38 and 62 for 50 on
        // average); at 0 or 100 the distance is lost because the volume stops there
        val average = list.sumOf { it.currentVolume } / list.size.toDouble()
        val target = percent.coerceIn(0, 100)
        if (average <= 0.0 || target == 0) {
            for (session in list) setVolume(session.deviceKey, if (average <= 0.0) target else 0)
            return
        }
        val factor = target / average
        for (session in list) setVolume(session.deviceKey, Math.round(session.currentVolume * factor).toInt())
    }

    /** The song to show on the speakers. Called by the player when the song changes. */
    fun updateNowPlaying(title: String?, artist: String?, album: String?, durationSeconds: Double) {
        this.title = title
        this.artist = artist
        this.album = album
        this.durationSeconds = durationSeconds
        scope.launch { sessions.toList().forEach { sendNowPlaying(it) } }
    }

    private fun sendNowPlaying(session: ReceiverSession) {
        if (title == null && artist == null) return
        try {
            session.sendMetadata(
                dmap = DmapEncoder.encodeTrack(title, artist, album, durationMs = (durationSeconds * 1000).toInt()),
                artwork = null,
                progress = null,
                rtpTime = currentRtp,
                rtpSeq = currentSeq,
                title = title,
                artist = artist,
                album = album,
                durationSeconds = durationSeconds,
                elapsedSeconds = 0.0
            )
        } catch (e: Exception) {
            Log.w(TAG, "Metadata failed: ${e.javaClass.simpleName}")
        }
    }

    // ---- The stream ---------------------------------------------------------------------------------------------------

    private fun startStream() {
        if (running) return
        running = true
        AirPlayAudio.clear()
        thread(name = "nooklin-airplay-send", isDaemon = true) {
            val frames = FRAMES_PER_PACKET
            val pcm = ByteArray(frames * 4)
            val packetNanos = frames * 1_000_000_000L / AirPlayAudio.SAMPLE_RATE
            val packetsPerSync = AirPlayAudio.SAMPLE_RATE / frames
            val fadePackets = (AirPlayAudio.SAMPLE_RATE / 2) / frames
            var sequence = 0
            var rtp = 0L
            var counter = 0
            // 0.0.154: one line per second about what is sent (the tag NooklinAirPlay, "STATS")
            var withAudio = 0
            var silent = 0
            var peak = 0
            broadcastSync(rtp, true)
            var next = System.nanoTime()
            while (running) {
                // Without waiting audio (paused, buffering) the speakers get silence, so the stream never stops
                if (AirPlayAudio.read(pcm, frames)) {
                    withAudio += 1
                    var i = 0
                    while (i < pcm.size - 1) {
                        val v = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
                        val a = if (v < 0) -v else v
                        if (a > peak) peak = a
                        i += 8
                    }
                } else {
                    silent += 1
                    java.util.Arrays.fill(pcm, 0.toByte())
                }
                val gain = if (counter < fadePackets) (counter + 1).toFloat() / fadePackets else 1f
                val payload = AlacEncoder.encodeUncompressedStereo(pcm, frames, gain)
                for (session in sessions) {
                    try {
                        session.send(payload, sequence, rtp, counter == 0)
                    } catch (e: Exception) {
                        Log.w(TAG, "Sending audio failed: ${e.javaClass.simpleName}")
                    }
                }
                sequence = (sequence + 1) and 0xFFFF
                rtp += frames
                currentRtp = rtp
                currentSeq = sequence
                counter += 1
                if (counter % packetsPerSync == 0) {
                    broadcastSync(rtp, false)
                    withAudio = 0
                    silent = 0
                    peak = 0
                }

                next += packetNanos
                val now = System.nanoTime()
                // If the thread fell far behind, do not rush to catch up
                if (next < now - 100_000_000L) next = now
                if (next > now) LockSupport.parkNanos(next - now)
            }
        }
    }

    private var syncCount = 0

    private fun broadcastSync(rtp: Long, first: Boolean) {
        // 0.0.157: [4..7] = what plays now (rtp minus the latency, wrapping like a real sender at the start), [16..19] = the next packet
        val packet = SyncPacket.build(rtp - LATENCY_FRAMES, rtp, first)
        syncCount += 1
        if (first || syncCount % 30 == 0) {
        }
        for (session in sessions) {
            try {
                session.sendSync(packet)
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed: ${e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        private const val TAG = "NooklinAirPlay"
        private const val FRAMES_PER_PACKET = 352

        /**
         * The audio plays this many frames after it is sent: 88200 = 2 s, what Apple senders use. With 11025 (250 ms) a Denon
         * AVR-X2400H plays nothing (tested in 0.0.162); an AirPort Express plays with both.
         */
        private const val LATENCY_FRAMES = 88200

        @Volatile
        private var instance: AirPlayController? = null

        fun get(context: Context): AirPlayController {
            return instance ?: synchronized(this) {
                instance ?: AirPlayController(context.applicationContext).also { instance = it }
            }
        }
    }
}
