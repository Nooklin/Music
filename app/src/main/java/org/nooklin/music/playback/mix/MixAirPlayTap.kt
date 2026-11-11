package org.nooklin.music.playback.mix

import org.nooklin.music.airplay.AirPlayAudio
import kotlin.concurrent.thread

/**
 * 0.0.110: carries what the mix engine plays to AirPlay. While the engine owns the song ([AirPlayAudio.mixOwns]) and AirPlay is on, this
 * thread takes the audio out of the engine's ring (16-bit stereo at 44.1 kHz, the format AirPlay wants) and puts it into [AirPlayAudio], where
 * the sender of the AirPlay controller picks it up as before. Otherwise it only empties the ring.
 */
object MixAirPlayTap {
    @Volatile private var started = false
    private val lock = Any()

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        thread(name = "nooklin-mix-airplay-tap", isDaemon = true) {
            val shorts = ShortArray(2 * 2048)
            val bytes = ByteArray(4 * 2048)
            while (true) {
                try {
                    val frames = NativeMix.tapRead(shorts, 2048)
                    if (frames <= 0) {
                        Thread.sleep(if (AirPlayAudio.tapping && AirPlayAudio.mixOwns) 4L else if (!AirPlayAudio.tapping) 250L else 40L)
                        continue
                    }
                    if (!AirPlayAudio.tapping || !AirPlayAudio.mixOwns) continue
                    for (i in 0 until frames * 2) {
                        val v = shorts[i].toInt()
                        bytes[2 * i] = v.toByte()
                        bytes[2 * i + 1] = (v shr 8).toByte()
                    }
                    AirPlayAudio.write(bytes, frames * 4)
                } catch (e: InterruptedException) {
                    return@thread
                } catch (e: Exception) {
                    try { Thread.sleep(100L) } catch (_: InterruptedException) { return@thread }
                }
            }
        }
    }
}
