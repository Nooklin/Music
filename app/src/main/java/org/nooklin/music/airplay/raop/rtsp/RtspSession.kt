package org.nooklin.music.airplay.raop.rtsp

import android.util.Log
import org.nooklin.music.airplay.raop.auth.AirPlayHttpClient
import org.nooklin.music.airplay.raop.plist.BinaryPlist
import org.nooklin.music.airplay.raop.plist.BinaryPlistDecoder
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

/**
 * RTSP session for AirPlay 2 stream negotiation, running over the SAME persistent
 * socket used for pairing.
 *
 * Note: after transient pairing the RTSP connection stays PLAINTEXT -- only the
 * separate event channel is encrypted. Verified against pyatv, where
 * enable_encryption() is called for the MRP and Companion protocols but never for
 * the RAOP/RTSP connection.
 *
 * Header conventions (CSeq, DACP-ID, Active-Remote, Client-Instance) and the SETUP
 * body shape follow pyatv's support/rtsp.py and protocols/raop/protocols/airplayv2.py.
 */
class RtspSession(
    private val http: AirPlayHttpClient,
    private val localIp: String
) {
    companion object {
        private const val TAG = "AirSyncRtsp"
        private const val USER_AGENT = "AirPlay/550.10"

        /**
         * User-Agent sent on METADATA requests only.
         *
         * EXPERIMENT: our metadata requests are header-for-header identical to the
         * Unofficial AirPlay Specification's worked example and are answered with
         * HTTP 200 by the Apple TV, yet nothing is displayed. The one remaining
         * difference is this header - the spec's example comes from iTunes 10.6.
         *
         * The theory is that modern tvOS honours the legacy DMAP display path only for
         * senders that identify as legacy iTunes clients, routing everything else
         * through the newer (MRP-over-AirPlay-2) channel. That would also explain why
         * native iOS apps hit the same wall, since they do not claim to be iTunes.
         *
         * Deliberately scoped to metadata: pairing, SETUP, RECORD and streaming all
         * work with the AirPlay agent, and changing what they advertise risks breaking
         * a working path to test a hunch.
         */
        private const val METADATA_USER_AGENT =
            "iTunes/10.6 (Macintosh; Intel Mac OS X 10.7.3) AppleWebKit/535.18.5"

    }

    // Reference starts CSeq at 0 for the first request, not 1.
    private var cseq = -1

    /**
     * Session id sent on SET_PARAMETER requests.
     *
     * This is OUR OWN id - the same value that appears in the RTSP URI - not something
     * the receiver hands back. An earlier version parsed it from the SETUP response;
     * the Apple TV never sends one, so no Session header was emitted at all.
     * Confirmed against pyatv (`self.session_id = randrange(2**32)`, then
     * `headers={"Session": rtsp_session}`) and the Unofficial AirPlay Specification's
     * worked example, which shows `Session:` on the metadata request.
     *
     * If a receiver DOES return its own id we prefer that, since it is authoritative.
     */
    private var rtspSessionId: String? = null

    private val effectiveSessionId: String
        get() = rtspSessionId ?: sessionId.toString()
    val sessionId: Long = Random.nextLong(0, 1L shl 32)
    private val dacpId: String = String.format(Locale.US, "%016X", Random.nextLong())
    private val activeRemote: Long = Random.nextLong(0, 1L shl 32)
    private val sessionUuid: String = UUID.randomUUID().toString().uppercase(Locale.US)

    /** Stream-negotiation results returned by the receiver. */
    data class SetupResult(
        val eventPort: Int,
        val rawResponse: ByteArray
    )

    private fun uri(): String = "rtsp://$localIp/$sessionId"

    private fun headers(contentType: String?): Map<String, String> {
        cseq += 1
        // Header ORDER also follows the reference: User-Agent, Content-Length (added by
        // the client), then CSeq/DACP-ID/Active-Remote/Client-Instance, then Content-Type.
        val h = linkedMapOf(
            "CSeq" to cseq.toString(),
            "User-Agent" to USER_AGENT,
            "DACP-ID" to dacpId,
            "Active-Remote" to activeRemote.toString(),
            "Client-Instance" to dacpId
        )
        if (contentType != null) h["Content-Type"] = contentType
        return h
    }

    /**
     * Queries the receiver's capabilities. The reference sends this BEFORE pairing and
     * stream setup; it reports model and supported features (and is how one detects an
     * AirPort Express needing the extra /auth-setup step).
     *
     * Failures are tolerated -- not every receiver implements /info.
     */
    fun info(): ByteArray? {
        return try {
            val resp = http.exchange(
                method = "GET",
                path = "/info",
                body = ByteArray(0),
                headers = headers(null),
                protocol = "RTSP/1.0",
                includeHost = false
            )
            if (resp.statusCode in 200..299) {
                try {
                    val decoded = BinaryPlistDecoder.decode(resp.body)
                    (decoded as? Map<*, *>)?.let { logCapabilities(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not decode /info bplist: ${e.javaClass.simpleName}")
                }
                resp.body
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "GET /info failed (continuing anyway): ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * First SETUP: negotiates the session itself and returns the port the receiver
     * wants us to open the event channel on. No audio stream is created yet.
     *
     * @param timingPort the local UDP port our NTP timing server will listen on.
     */
    fun setupSession(timingPort: Int): SetupResult {
        // OwnTone's NTP session SETUP body is exactly these four keys
        // (payload_make_setup_session_ntp). We previously sent pyatv's 15-key body;
        // it was accepted, but the minimal form is what the known-good sender uses.
        // timingProtocol must be "NTP" -- OwnTone notes that with "None" an ATV4 will
        // not respond to the stream SETUP request at all.
        val body = BinaryPlist.encode(
            linkedMapOf(
                "deviceID" to "AA:BB:CC:DD:EE:FF",
                "sessionUUID" to sessionUuid,
                "timingPort" to timingPort,
                "timingProtocol" to "NTP"
            )
        )

        if (localIp.startsWith("127.") || localIp == "0.0.0.0") {
            // The receiver silently drops the connection on a loopback session URI,
            // which looks like an unexplained disconnect rather than a clear error.
            throw RtspException(
                "Local IP resolved to '$localIp' -- the RTSP session URI must use the " +
                    "Wi-Fi interface address. Derive it from the connected socket."
            )
        }
        val resp = http.exchange(
            method = "SETUP",
            path = uri(),
            body = body,
            headers = headers("application/x-apple-binary-plist"),
            protocol = "RTSP/1.0",
            includeHost = false
        )

        if (resp.statusCode !in 200..299) {
            throw RtspException("SETUP (session) failed with RTSP ${resp.statusCode}")
        }

        // Headers are lower-cased by the parser.
        resp.headers["session"]?.let {
            rtspSessionId = it.substringBefore(';').trim()
        }

        val decoded = BinaryPlistDecoder.decode(resp.body)
        val eventPort = ((decoded as? Map<*, *>)?.get("eventPort") as? Long)?.toInt()
            ?: throw RtspException("SETUP response did not contain eventPort")

        return SetupResult(eventPort, resp.body)
    }


    /**
     * `/auth-setup` - tells the receiver we intend to stream unencrypted audio.
     *
     * The reference sends this only for AirPort Express hardware that advertises
     * MFiSAP encryption support, noting some receivers won't play audio unless the
     * setup process is completed (pyatv stream_client._requires_auth_setup, which
     * cites github.com/postlund/pyatv issues/1134).
     *
     * The payload is a marker byte 0x01 ("unencrypted") followed by a fixed 32-byte
     * Curve25519 public key, taken verbatim from the reference implementation. Errors
     * are tolerated: receivers that don't implement it should simply be skipped.
     */
    fun authSetup(): Boolean {
        val unencryptedMarker = byteArrayOf(0x01)
        val curve25519PubKey = byteArrayOf(
            0x59, 0x02, 0xed.toByte(), 0xe9.toByte(), 0x0d, 0x4e, 0xf2.toByte(), 0xbd.toByte(),
            0x4c, 0xb6.toByte(), 0x8a.toByte(), 0x63, 0x30, 0x03, 0x82.toByte(), 0x07,
            0xa9.toByte(), 0x4d, 0xbd.toByte(), 0x50, 0xd8.toByte(), 0xaa.toByte(), 0x46, 0x5b,
            0x5d, 0x8c.toByte(), 0x01, 0x2a, 0x0c, 0x7e, 0x1d, 0x4e
        )
        return try {
            val resp = http.exchange(
                method = "POST",
                path = "/auth-setup",
                body = unencryptedMarker + curve25519PubKey,
                headers = headers("application/octet-stream"),
                protocol = "RTSP/1.0",
                includeHost = false
            )
            resp.statusCode in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "/auth-setup failed (continuing anyway): ${e.javaClass.simpleName}")
            false
        }
    }

    /** Ports and key the receiver assigned for the audio stream. */
    data class AudioStreamResult(
        val dataPort: Int,
        val controlPort: Int,
        val audioKey: ByteArray
    )

    /**
     * Second SETUP: creates the actual audio stream.
     *
     * Format is ALAC/44100/16/2 (`ct` = 2, `audioFormat` = 0x40000), 352 samples per
     * frame, RTP payload type 96 (realtime). CONFIRMED ACCEPTED by AirPort Express
     * hardware; matches OwnTone's payload_make_setup_stream. The pyatv PCM variant
     * (ct=1, audioFormat=0x800) was rejected by this hardware.
     *
     * `shk` is the key the RTP audio payload is encrypted with (ChaCha20-Poly1305,
     * separate from the control-channel encryption).
     *
     * NOTE: a running timing server is a PREREQUISITE -- the receiver queries the
     * advertised timingPort while processing this request and returns 400 without it.
     *
     * @param controlPort the LOCAL UDP port our control client will listen on.
     * @param audioKey 32-byte key for encrypting the audio payload.
     */
    fun setupAudioStream(
        controlPort: Int,
        audioKey: ByteArray,
        latencyMin: Int = 11025,
        latencyMax: Int = 88200
    ): AudioStreamResult {
        require(audioKey.size == 32) { "Audio stream key must be 32 bytes, got ${audioKey.size}" }

        val body = BinaryPlist.encode(
            linkedMapOf(
                "streams" to listOf(
                    linkedMapOf(
                        "audioFormat" to 0x40000,        // ALAC/44100/16/2
                        "audioMode" to "default",
                        "controlPort" to controlPort,
                        "ct" to 2,                       // 2 = ALAC
                        "isMedia" to true,
                        "latencyMax" to latencyMax,
                        "latencyMin" to latencyMin,
                        "shk" to audioKey,
                        "spf" to 352,                    // samples per frame
                        "sr" to 44100,                   // sample rate
                        "type" to 0x60,
                        "supportsDynamicStreamID" to false,
                        "streamConnectionID" to sessionId
                    )
                )
            )
        )

        val resp = http.exchange(
            method = "SETUP",
            path = uri(),
            body = body,
            headers = headers("application/x-apple-binary-plist"),
            protocol = "RTSP/1.0",
            includeHost = false
        )
        if (resp.statusCode !in 200..299) {
            throw RtspException("SETUP (audio stream) failed with RTSP ${resp.statusCode}")
        }

        val decoded = BinaryPlistDecoder.decode(resp.body)

        val streams = (decoded as? Map<*, *>)?.get("streams") as? List<*>
            ?: throw RtspException("SETUP (audio stream) response had no 'streams' array")
        val stream = streams.firstOrNull() as? Map<*, *>
            ?: throw RtspException("SETUP (audio stream) response had an empty 'streams' array")

        val dataPort = (stream["dataPort"] as? Long)?.toInt()
            ?: throw RtspException("Stream response missing dataPort")
        val remoteControlPort = (stream["controlPort"] as? Long)?.toInt()
            ?: throw RtspException("Stream response missing controlPort")

        return AudioStreamResult(dataPort, remoteControlPort, audioKey)
    }

    /** One candidate stream configuration for [probeAudioStream]. */
    data class StreamConfig(
        val label: String,
        val ct: Int,
        val type: Int,
        val audioFormat: Int,
        val includeStreamConnectionID: Boolean = true,
        val includeLatency: Boolean = true
    )

    /**
     * Tries a series of audio-stream configurations and reports which the receiver
     * accepts.
     *
     * The AirPort Express rejects the reference implementation's exact parameter set
     * with RTSP 400 while accepting the session SETUP that precedes it, and its TXT
     * records (cn=0,1) say both PCM and ALAC are supported -- so the mismatch is in
     * some other field. Rather than guess again, this walks candidates and logs the
     * status of each. RTSP 400 does not close the connection, so retries are safe.
     *
     * Returns the first configuration that succeeds, or null if all fail.
     */
    fun probeAudioStream(controlPort: Int, audioKey: ByteArray): Pair<StreamConfig, AudioStreamResult>? {
        val candidates = listOf(
            // OwnTone's exact payload (src/outputs/airplay.c payload_make_setup_stream),
            // a sender verified against AirPort Express hardware:
            // ALAC/44100/16/2, compression type 2, RTP payload type 0x60.
            StreamConfig("OwnTone ALAC (known-good on AirPort Express)", ct = 2, type = 96,
                audioFormat = 0x40000),
            StreamConfig("PCM realtime (pyatv default)", ct = 1, type = 96, audioFormat = 0x800),
            StreamConfig("ALAC realtime", ct = 2, type = 96, audioFormat = 0x800),
            StreamConfig("PCM buffered", ct = 1, type = 103, audioFormat = 0x800),
            StreamConfig("ALAC buffered", ct = 2, type = 103, audioFormat = 0x800),
            StreamConfig("PCM realtime, no streamConnectionID", ct = 1, type = 96,
                audioFormat = 0x800, includeStreamConnectionID = false),
            StreamConfig("PCM realtime, no latency hints", ct = 1, type = 96,
                audioFormat = 0x800, includeLatency = false),
            StreamConfig("ALAC realtime, ALAC audioFormat bit", ct = 2, type = 96, audioFormat = 0x40000),
            StreamConfig("PCM realtime, audioFormat 0x400000", ct = 1, type = 96, audioFormat = 0x400000)
        )

        for (cfg in candidates) {
            val stream = linkedMapOf<String, Any>(
                "audioFormat" to cfg.audioFormat,
                "audioMode" to "default",
                "controlPort" to controlPort,
                "ct" to cfg.ct,
                "isMedia" to true,
                "shk" to audioKey,
                "spf" to 352,
                "sr" to 44100,
                "type" to cfg.type,
                "supportsDynamicStreamID" to false
            )
            if (cfg.includeLatency) {
                stream["latencyMax"] = 88200
                stream["latencyMin"] = 11025
            }
            if (cfg.includeStreamConnectionID) {
                stream["streamConnectionID"] = sessionId
            }

            val body = BinaryPlist.encode(linkedMapOf("streams" to listOf(stream)))
            val resp = try {
                http.exchange(
                    method = "SETUP",
                    path = uri(),
                    body = body,
                    headers = headers("application/x-apple-binary-plist"),
                    protocol = "RTSP/1.0",
                    includeHost = false
                )
            } catch (e: Exception) {
                Log.w(TAG, "PROBE [${cfg.label}] -> transport error: ${e.javaClass.simpleName}")
                return null // connection is gone; no point continuing
            }

            if (resp.statusCode !in 200..299) {
                continue
            }

            val decoded = BinaryPlistDecoder.decode(resp.body)

            val streams = (decoded as? Map<*, *>)?.get("streams") as? List<*>
            val first = streams?.firstOrNull() as? Map<*, *>
            val dataPort = (first?.get("dataPort") as? Long)?.toInt()
            val remoteControl = (first?.get("controlPort") as? Long)?.toInt()
            if (dataPort == null || remoteControl == null) {
                Log.w(TAG, "Accepted but response lacked dataPort/controlPort; continuing probe")
                continue
            }
            return cfg to AudioStreamResult(dataPort, remoteControl, audioKey)
        }

        Log.w(TAG, "=== PROBE COMPLETE: no configuration accepted ===")
        return null
    }

    /** Tells the receiver to start accepting audio on the negotiated stream. */
    fun record(): Int {
        val resp = http.exchange(
            method = "RECORD",
            path = uri(),
            body = ByteArray(0),
            headers = headers(null),
            protocol = "RTSP/1.0",
            includeHost = false
        )
        if (resp.statusCode !in 200..299) {
            throw RtspException("RECORD failed with RTSP ${resp.statusCode}")
        }
        return resp.statusCode
    }

    /**
     * Sets the receiver's volume via SET_PARAMETER.
     *
     * AirPlay volume is in dB: -30.0 is the minimum audible level and 0.0 the maximum;
     * -144.0 means muted. Format ported from OwnTone's payload_make_set_volume, which
     * formats the number manually to avoid locale decimal separators - a comma instead
     * of a point would be rejected, and that is exactly the sort of bug that only
     * appears on some users' phones.
     *
     * @param percent 0..100, where 0 mutes
     */
    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        val db = if (clamped == 0) -144.0 else -30.0 + (clamped * 30.0 / 100.0)

        // Locale.US guarantees a '.' decimal separator regardless of device locale -
        // a comma would be rejected by the receiver, and that bug would only surface
        // on some users' phones. (OwnTone formats this by hand because C's snprintf
        // respects the locale; Java does not need that, and doing it by hand here
        // dropped the sign for values between -1 and 0.)
        val volstr = String.format(java.util.Locale.US, "%.6f", db)

        val body = "volume: $volstr\r\n".toByteArray(Charsets.US_ASCII)
        try {
            // Carry the session id here too: the receiver needs to know which session
            // the volume change applies to.
            val h = headers("text/parameters").toMutableMap()
            h["Session"] = effectiveSessionId
            val resp = http.exchange(
                method = "SET_PARAMETER",
                path = uri(),
                body = body,
                headers = h,
                protocol = "RTSP/1.0",
                includeHost = false
            )
        } catch (e: Exception) {
            Log.w(TAG, "Volume change failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Sends POST /feedback - the sender's periodic "am I still the active session?"
     * ping.
     *
     * AirPlay receivers serve ONE sender at a time. When another phone or Mac takes
     * over the speaker, our session is discarded server-side; feedback then fails with
     * a non-2xx (typically 453 "Not enough bandwidth" or 455) or the connection drops.
     * Without this check the app would happily keep transmitting RTP into a session
     * that no longer exists, showing "Connected" while the speaker plays someone else.
     *
     * Must be RTSP/1.0 - sending it as HTTP is rejected.
     *
     * @return true if the session is still ours
     */
    fun feedback(): Boolean {
        return try {
            val resp = http.exchange(
                method = "POST",
                path = "/feedback",
                body = ByteArray(0),
                headers = headers(null),
                protocol = "RTSP/1.0",
                includeHost = false
            )
            if (resp.statusCode !in 200..299) {
                Log.w(TAG, "Feedback rejected with RTSP ${resp.statusCode} - session lost")
                false
            } else {
                logFeedbackBody(resp.body)
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Feedback failed (${e.javaClass.simpleName}) - session lost")
            false
        }
    }

    // 0.0.156: the receiver can send its own statistics in the feedback answer (the Denon says keepAliveSendStatsAsBody).
    // Logged when the content changes and at least every 15th answer (about 30 s), to see what the receiver reports about the stream.
    private var lastFeedbackText = ""
    private var feedbackCount = 0

    private fun logFeedbackBody(body: ByteArray?) {
        feedbackCount += 1
        if (body == null || body.isEmpty()) {
            return
        }
        val text = try {
            BinaryPlistDecoder.describe(BinaryPlistDecoder.decode(body))
        } catch (e: Exception) {
            "not a plist (${e.message}), hex " + body.joinToString("") { "%02x".format(it) }
        }
        if (text != lastFeedbackText || feedbackCount % 15 == 0) {
            lastFeedbackText = text
        }
    }

    // ---- Now-playing metadata ---------------------------------------------------
    //
    // Three separate SET_PARAMETER requests, each carrying an RTP-Info header that
    // anchors it to the audio timeline so the receiver shows the right text at the
    // right moment. Format follows OwnTone (payload_make_send_text / _progress /
    // _artwork in src/outputs/airplay.c).
    //
    // All three are best-effort: metadata failing must never disturb playback.

    /** Track title/artist/album, DMAP-encoded. */
    fun sendTrackInfo(dmap: ByteArray, rtpTime: Long, rtpSeq: Int): Boolean =
        sendMetadata("text", dmap, "application/x-dmap-tagged", rtpTime, rtpSeq)

    /**
     * Playback progress, as RTP timestamps: `progress: display/current/end`.
     *
     * @param startRtp when the track began on our timeline
     * @param currentRtp where playback is now
     * @param endRtp where the track will end
     */
    fun sendProgress(
        startRtp: Long,
        currentRtp: Long,
        endRtp: Long,
        rtpTime: Long,
        rtpSeq: Int
    ): Boolean {
        val body = "progress: $startRtp/$currentRtp/$endRtp\r\n"
            .toByteArray(Charsets.US_ASCII)
        return sendMetadata("progress", body, "text/parameters", rtpTime, rtpSeq)
    }

    /** Album art, as JPEG or PNG bytes. */
    fun sendArtwork(image: ByteArray, mimeType: String, rtpTime: Long, rtpSeq: Int): Boolean =
        sendMetadata("artwork", image, mimeType, rtpTime, rtpSeq)

    private fun sendMetadata(
        what: String,
        body: ByteArray,
        contentType: String,
        rtpTime: Long,
        @Suppress("UNUSED_PARAMETER") rtpSeq: Int  // see RTP-Info note below
    ): Boolean {
        return try {
            val h = headers(contentType).toMutableMap()
            // Ties this metadata to a point in the audio timeline. The Unofficial
            // AirPlay Specification's worked example carries rtptime ALONE - an earlier
            // version added `seq=` from pyatv, which the spec does not use.
            h["RTP-Info"] = "rtptime=$rtpTime"
            h["Session"] = effectiveSessionId
            // See METADATA_USER_AGENT: identify as legacy iTunes for this request only.
            h["User-Agent"] = METADATA_USER_AGENT

            val resp = http.exchange(
                method = "SET_PARAMETER",
                path = uri(),
                body = body,
                headers = h,
                protocol = "RTSP/1.0",
                includeHost = false
            )
            val ok = resp.statusCode in 200..299
            ok
        } catch (e: Exception) {
            // Never let metadata disturb playback.
            Log.w(TAG, "Metadata ($what) failed: ${e.javaClass.simpleName}")
            false
        }
    }

    class RtspException(message: String) : Exception(message)

    // ---- MediaRemote now-playing (modern tvOS path) ------------------------------
    //
    // Confirmed across three independent live captures (Apple Music, NaviMus, and
    // Arpeggi -- iOS 17 through 26, proxied with a patched atvproxy): modern tvOS's
    // Now Playing display no longer reads the legacy DMAP SET_PARAMETER path above.
    // Those requests get a clean HTTP 200 back but nothing is ever shown -- the RTSP
    // server still accepts them for backward compatibility, but the UI has been
    // rewired to read only from this MediaRemote path instead. Real senders push
    // plain POST /command requests on this SAME RTSP connection, shaped as
    // {'type': 'updateMR...', 'params': {...}}.
    //
    // playbackState / supportedCommands / nowPlayingClient below are reproduced
    // byte-for-byte from a real Arpeggi (Navidrome client) capture -- not guesses.
    // nowPlayingInfo's field names are NOT independently confirmed: every capture
    // that got far enough crashed or was rejected before a POPULATED example
    // appeared on the wire. Those keys are the standard public MediaRemote framework
    // constants, used here as a best-effort pending confirmation on real hardware.

    /** Identifies this app to the receiver's Now Playing / remote-control UI. Send once, right after RECORD. */
    fun updateMrNowPlayingClient(bundleId: String, displayName: String): Boolean =
        sendCommand(
            "updateMRNowPlayingClient",
            linkedMapOf("mrNowPlayingClient" to encodeNowPlayingClientProtobuf(bundleId, displayName))
        )

    /**
     * @param playing true = actively playing, false = paused/stopped.
     *
     * Values (1 = playing, 3 = stopped) are the two seen on the wire. The full
     * MRPlaybackState enum (Playing/Paused/Stopped/Interrupted/Seeking) is publicly
     * documented, but only these two states were exercised in our captures.
     */
    fun updateMrPlaybackState(playing: Boolean): Boolean =
        sendCommand("updateMRPlaybackState", linkedMapOf("mrPlaybackState" to (if (playing) 1 else 3)))

    /**
     * Declares which transport commands we support. Body reproduced byte-for-byte
     * (command numbers, options, ordering) from a real Arpeggi capture, not a guess
     * at the MRCommand enum. Send once, right after RECORD.
     */
    fun updateMrSupportedCommands(): Boolean {
        val commands = listOf(
            commandInfo(26, true, "kMRMediaRemoteCommandInfoShuffleMode", 1),
            commandInfo(25, true, "kMRMediaRemoteCommandInfoRepeatMode", 1),
            commandInfo(24, true, "kMRMediaRemoteCommandInfoCanBeControlledByScrubbingKey", false),
            commandInfo(21, true, "kMRMediaRemoteCommandInfoIsActiveKey", false),
            commandInfo(10, true), commandInfo(11, true),
            commandInfo(8, true), commandInfo(9, true),
            commandInfo(5, true), commandInfo(4, true),
            commandInfo(2, true), commandInfo(1, true), commandInfo(0, true)
        )
        return sendCommand("updateMRSupportedCommands", linkedMapOf("mrSupportedCommandsFromSender" to commands))
    }

    /**
     * EXPERIMENTAL -- see section note above. Field names are the standard public
     * MediaRemote NowPlayingInfo dictionary keys, not confirmed against a populated
     * real-world capture. Safe to call regardless: failures here must never disturb
     * playback, same as the DMAP methods above.
     *
     * Deliberately does NOT carry artwork. A first attempt embedded the raw JPEG
     * bytes here and it broke the receiver connection -- real senders only ever put
     * small control-plane messages on this channel (the biggest one captured,
     * updateMRSupportedCommands, was under 2KB), and a large payload here is
     * suspected to have either blocked the shared rtspLock long enough to fail the
     * 2-second health check, or been rejected/dropped by the receiver's /command
     * handler outright. DMAP's sendArtwork() already delivers artwork successfully
     * on its own channel -- no need to duplicate it here until this is understood.
     *
     * @param elapsedSeconds playback position; durationSeconds total track length
     */
    fun updateMrNowPlayingInfo(
        title: String?,
        artist: String?,
        album: String?,
        durationSeconds: Double?,
        elapsedSeconds: Double?,
        playing: Boolean
    ): Boolean {
        val info = linkedMapOf<String, Any?>()
        title?.let { info["kMRMediaRemoteNowPlayingInfoTitle"] = it }
        artist?.let { info["kMRMediaRemoteNowPlayingInfoArtist"] = it }
        album?.let { info["kMRMediaRemoteNowPlayingInfoAlbum"] = it }
        durationSeconds?.let { info["kMRMediaRemoteNowPlayingInfoDuration"] = it }
        elapsedSeconds?.let { info["kMRMediaRemoteNowPlayingInfoElapsedTime"] = it }
        info["kMRMediaRemoteNowPlayingInfoPlaybackRate"] = if (playing) 1.0 else 0.0

        val params = linkedMapOf<String, Any?>(
            "type" to "npi-text",
            "params" to info,
            "mergePolicy" to "replace"
        )
        return sendCommand("updateMRNowPlayingInfo", params)
    }

    /** POST /command with a {'type', 'params'} envelope -- the shape every MR command above uses. */
    private fun sendCommand(type: String, params: Map<String, Any?>): Boolean {
        return try {
            val body = BinaryPlist.encode(linkedMapOf("type" to type, "params" to params))
            val resp = http.exchange(
                method = "POST",
                path = "/command",
                body = body,
                headers = headers("application/x-apple-binary-plist"),
                protocol = "RTSP/1.0",
                includeHost = false
            )
            val ok = resp.statusCode in 200..299
            ok
        } catch (e: Exception) {
            Log.w(TAG, "MR command ($type) failed: ${e.javaClass.simpleName}")
            false
        }
    }

    /** One CommandInfo entry for updateMrSupportedCommands -- each is its own nested bplist00 document. */
    private fun commandInfo(
        command: Int,
        enabled: Boolean,
        optionsKey: String? = null,
        optionsValue: Any? = null
    ): ByteArray {
        val dict = linkedMapOf<String, Any?>()
        if (optionsKey != null) dict["kCommandInfoOptionsKey"] = linkedMapOf(optionsKey to optionsValue)
        dict["kCommandInfoCommandKey"] = command
        dict["kCommandInfoEnabledKey"] = enabled
        return BinaryPlist.encode(dict)
    }

    /**
     * Tiny hand-rolled protobuf writer for the mrNowPlayingClient blob -- this field is
     * raw protobuf, NOT a nested plist, unlike everything else in this section.
     * Reproduced from a real capture: field 2 (bundle id) and field 7 (display name)
     * are the only fields with observed semantic meaning; fields 1 and 4 looked like
     * arbitrary process/session identifiers on the wire, so placeholder values are
     * used here rather than guessed constants.
     */
    private fun encodeNowPlayingClientProtobuf(bundleId: String, displayName: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun writeVarint(value: Long) {
            var v = value
            while (true) {
                val b = (v and 0x7F).toInt()
                v = v ushr 7
                if (v == 0L) { out.write(b); break } else out.write(b or 0x80)
            }
        }
        fun writeTag(field: Int, wireType: Int) = writeVarint(((field shl 3) or wireType).toLong())
        fun writeString(field: Int, s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            writeTag(field, 2)
            writeVarint(bytes.size.toLong())
            out.write(bytes)
        }
        writeTag(1, 0); writeVarint(1L)      // field 1: placeholder id (observed value not semantic)
        writeString(2, bundleId)             // field 2: bundle identifier
        writeTag(4, 0); writeVarint(1L)      // field 4: placeholder id (observed value not semantic)
        writeString(7, displayName)          // field 7: display name
        return out.toByteArray()
    }


    /**
     * Summarises the fields that decide how we must talk to this receiver.
     *
     * The `features` bitfield is authoritative for protocol selection, NOT the model
     * name: bit 38 (SupportsUnifiedMediaControl) or bit 48
     * (SupportsCoreUtilsPairingAndEncryption) mean the device speaks AirPlay 2.
     * An AirPort Express on 7.8.1 firmware sets both, so it takes the AirPlay 2 path
     * despite its age -- an earlier version of this code wrongly warned otherwise
     * based on the model string.
     */
    private fun logCapabilities(info: Map<*, *>) {
        // Key names as actually returned by /info (an earlier version used the mDNS
        // TXT-record abbreviations srcvers/protovers here, which are absent from the
        // plist and always logged null).
        val model = info["model"]
        val sourceVersion = info["sourceVersion"]
        val protocolVersion = info["protocolVersion"]
        val sdk = info["sdk"]
        val features = (info["features"] as? Long) ?: 0L

        val unifiedMediaControl = (features shr 38) and 1L == 1L
        val coreUtilsPairing = (features shr 48) and 1L == 1L
        val isAirPlay2 = unifiedMediaControl || coreUtilsPairing

    }
}
