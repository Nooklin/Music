package org.nooklin.music.airplay

import android.content.Context
import android.util.Log
import org.nooklin.music.airplay.raop.audio.AudioSender
import org.nooklin.music.airplay.raop.auth.AirPlayHttpClient
import org.nooklin.music.airplay.raop.auth.CredentialStore
import org.nooklin.music.airplay.raop.auth.HapPairVerify
import org.nooklin.music.airplay.raop.auth.Hkdf
import org.nooklin.music.airplay.raop.auth.TransientPairVerify
import org.nooklin.music.airplay.raop.event.EventChannel
import org.nooklin.music.airplay.raop.rtsp.RtspSession
import org.nooklin.music.airplay.raop.timing.ControlServer

/**
 * Everything belonging to ONE receiver: its RTSP session, event channel, control
 * socket, encryption keys and RTP sender.
 *
 * Deliberately does NOT own the audio timeline or the timing server. For several
 * speakers to play in sync they must share:
 *   - one clock - a single NTP timing server, whose port every receiver is given
 *   - one RTP timeline - identical sequence numbers and timestamps
 *   - byte-identical sync packets mapping that timeline to wall-clock time
 * Those live in [StreamingService]; each session here only encrypts and transmits the
 * common stream to its own device.
 */
class ReceiverSession(
    private val context: Context,
    val name: String,
    val host: String,
    private val port: Int,
    val deviceKey: String
) : AutoCloseable {

    companion object {
        private const val TAG = "NooklinAirPlay"
    }

    private var http: AirPlayHttpClient? = null
    private var rtsp: RtspSession? = null
    private var events: EventChannel? = null
    private var control: ControlServer? = null
    private var sender: AudioSender? = null

    val rtspLock = Any()
    /**
     * Last volume applied to this receiver.
     *
     * NOT named `volume`: a Kotlin `var volume: Int` compiles to a JVM setVolume(int),
     * which clashes with the explicit setVolume() below - "Platform declaration clash".
     */
    @Volatile var currentVolume: Int = 20
    @Volatile var connected = false
        private set

    /**
     * Negotiates the full session up to the point where audio can be sent.
     *
     * @param timingPort the SHARED timing server port - every receiver in the group is
     *        given the same one, which is what makes a common clock possible
     * @param latencyMin/[latencyMax] receiver buffering from settings
     * @param onLost invoked if this receiver drops out (e.g. another sender takes it)
     */
    fun connect(
        timingPort: Int,
        latencyMin: Int,
        latencyMax: Int,
        initialVolume: Int,
        onLost: (String) -> Unit
    ) {
        val client = AirPlayHttpClient(host, port)
        http = client

        val session = RtspSession(client, client.localIp)
        rtsp = session
        session.info()

        val store = CredentialStore(context)
        val stored = store.load(deviceKey)

        val secret = if (stored != null) {
            try {
                HapPairVerify(client, stored).verify()
            } catch (e: Exception) {
                Log.w(TAG, "Pair-verify failed, stored pairing cleared")
                store.clear(deviceKey)
                throw Exception("Pair-verify failed: ${e.message}")
            }
        } else {
            TransientPairVerify(host, port, org.nooklin.music.airplay.util.DeviceIdentity.name(context)).pairOn(client)
        }

        session.authSetup()

        control = ControlServer()
        val controlPort = control!!.start()

        val setup = session.setupSession(timingPort = timingPort)

        val eventOut = Hkdf.deriveKey(
            TransientPairVerify.EVENTS_SALT, TransientPairVerify.EVENTS_READ_INFO, secret
        )
        val eventIn = Hkdf.deriveKey(
            TransientPairVerify.EVENTS_SALT, TransientPairVerify.EVENTS_WRITE_INFO, secret
        )
        events = EventChannel(host, setup.eventPort, eventOut, eventIn) {
            onLost("$name dropped the connection")
        }
        events!!.connect()

        // 0.0.159: RECORD comes AFTER the audio stream SETUP (it came before), the order of the AirPlay spec.
        val audioKey = Hkdf.deriveKey(
            TransientPairVerify.EVENTS_SALT, TransientPairVerify.EVENTS_WRITE_INFO, secret
        ).copyOf(32)

        val audio = session.setupAudioStream(
            controlPort = controlPort,
            audioKey = audioKey,
            latencyMin = latencyMin,
            latencyMax = latencyMax
        )

        session.record()

        // MediaRemote handshake -- sent once, after RECORD. See the MediaRemote section in RtspSession for why this exists alongside DMAP.
        synchronized(rtspLock) {
            session.updateMrNowPlayingClient(context.packageName, "Nooklin Music")
            session.updateMrSupportedCommands()
            session.updateMrPlaybackState(playing = true)
        }

        sender = AudioSender(
            host = host,
            dataPort = audio.dataPort,
            audioKey = audioKey,
            receiverControlPort = audio.controlPort,
            controlSocket = control!!.socket,
            // 0.0.158: same as streamConnectionID; an SSRC of 0 means "no stream" to some receivers, which then drop the audio
            ssrc = session.sessionId.toInt()
        )

        currentVolume = initialVolume
        synchronized(rtspLock) { session.setVolume(initialVolume) }
        connected = true
    }

    /** Sends one packet of the shared stream to this receiver. */
    fun send(payload: ByteArray, sequence: Int, timestamp: Long, isFirst: Boolean) {
        sender?.sendFrame(payload, sequence, timestamp, isFirst)
    }

    /** 0.0.154: for the log: what this receiver was sent so far. */
    fun stats(): String {
        val s = sender ?: return "$name: NO SENDER"
        return "$name: audio=${s.packetsSent} sync=${s.syncPacketsSent}"
    }

    /** Forwards the group's common sync packet to this receiver. */
    fun sendSync(packet: ByteArray) {
        sender?.sendSyncPacket(packet)
    }

    fun setVolume(percent: Int) {
        val session = rtsp ?: return
        currentVolume = percent
        synchronized(rtspLock) { session.setVolume(percent) }
    }

    /**
     * Pushes now-playing metadata to this receiver. Best-effort throughout: a receiver
     * that ignores or rejects metadata must still play audio.
     *
     * Pushes BOTH the legacy DMAP fields and the MediaRemote fields on every call --
     * see the MediaRemote section in RtspSession for why both exist. `title`/`artist`/
     * `album`/`durationSeconds`/`elapsedSeconds` are null-safe no-ops for
     * updateMrNowPlayingInfo when there's nothing to report.
     */
    fun sendMetadata(
        dmap: ByteArray?,
        artwork: ByteArray?,
        progress: Triple<Long, Long, Long>?,
        rtpTime: Long,
        rtpSeq: Int,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        durationSeconds: Double? = null,
        elapsedSeconds: Double? = null
    ) {
        val session = rtsp ?: return
        synchronized(rtspLock) {
            dmap?.let { session.sendTrackInfo(it, rtpTime, rtpSeq) }
            progress?.let { (start, current, end) ->
                session.sendProgress(start, current, end, rtpTime, rtpSeq)
            }
            artwork?.let { session.sendArtwork(it, "image/jpeg", rtpTime, rtpSeq) }

            if (title != null || artist != null) {
                // AirSync has no real pause/resume concept -- it streams whatever audio
                // the device is producing, so "playing" is simplified to true whenever
                // there's a track to report.
                session.updateMrNowPlayingInfo(
                    title, artist, album, durationSeconds, elapsedSeconds, playing = true
                )
            }
        }
    }

    /** Health probe; false means this receiver is gone. */
    fun isAlive(): Boolean {
        val session = rtsp ?: return false
        return try {
            synchronized(rtspLock) { session.feedback() }
        } catch (e: Exception) {
            Log.w(TAG, "Health check failed: ${e.javaClass.simpleName}")
            false
        }
    }

    val packetsSent: Int get() = sender?.packetsSent ?: 0

    override fun close() {
        connected = false
        try { sender?.close() } catch (_: Exception) {}
        try { events?.close() } catch (_: Exception) {}
        try { control?.close() } catch (_: Exception) {}
        try { http?.close() } catch (_: Exception) {}
        sender = null; events = null; control = null; rtsp = null; http = null
    }
}
