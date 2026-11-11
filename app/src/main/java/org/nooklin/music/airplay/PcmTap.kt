@file:OptIn(UnstableApi::class)

package org.nooklin.music.airplay

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Where the audio of the player waits for the AirPlay sender: 16-bit stereo at 44.1 kHz, as the AirPlay stream wants it.
 * The player thread writes, the sender thread reads. The queue holds a few seconds; when it is full the oldest audio
 * is dropped.
 */
object AirPlayAudio {
    const val SAMPLE_RATE = 44100
    private const val BYTES_PER_FRAME = 4
    private const val CAPACITY_FRAMES = SAMPLE_RATE * 6

    /** True while AirPlay is on: the processor then copies the audio into the queue. */
    @Volatile var tapping = false

    /**
     * 0.0.110: True while the mix engine plays the song (Auto Mix). The audio for AirPlay then comes from the engine (see MixAirPlayTap) and the
     * processor below does not copy the audio of the (silent) player, so the speakers do not hear it twice.
     */
    @Volatile var mixOwns = false

    /** True while the audio is only for the speakers: the phone itself then plays silence. */
    @Volatile var muteLocal = false

    private val lock = Any()
    private val ring = ByteArray(CAPACITY_FRAMES * BYTES_PER_FRAME)
    private var head = 0
    private var size = 0

    fun write(data: ByteArray, length: Int) {
        synchronized(lock) {
            var offset = 0
            var left = length
            if (left > ring.size) {
                offset = left - ring.size
                left = ring.size
            }
            val overflow = size + left - ring.size
            if (overflow > 0) {
                head = (head + overflow) % ring.size
                size -= overflow
            }
            val tail = (head + size) % ring.size
            val first = minOf(left, ring.size - tail)
            System.arraycopy(data, offset, ring, tail, first)
            if (left > first) System.arraycopy(data, offset + first, ring, 0, left - first)
            size += left
        }
    }

    /** Fills [target] with [frames] frames when that many are waiting. Returns false, taking nothing, when not. */
    fun read(target: ByteArray, frames: Int): Boolean {
        val bytes = frames * BYTES_PER_FRAME
        synchronized(lock) {
            if (size < bytes) return false
            val first = minOf(bytes, ring.size - head)
            System.arraycopy(ring, head, target, 0, first)
            if (bytes > first) System.arraycopy(ring, 0, target, first, bytes - first)
            head = (head + bytes) % ring.size
            size -= bytes
            return true
        }
    }

    /** 0.0.154: frames waiting in the queue (for the log only). */
    fun available(): Int = synchronized(lock) { size / BYTES_PER_FRAME }

    fun clear() {
        synchronized(lock) {
            head = 0
            size = 0
        }
    }
}

/**
 * Sits in the audio chain of the player. While AirPlay is on it copies everything that passes into [AirPlayAudio]
 * (converted to 16-bit stereo at 44.1 kHz) and, when [AirPlayAudio.muteLocal] is set, lets silence of the same length go on
 * to the phone's own output, so the player keeps its normal pace. Otherwise the audio passes through unchanged.
 *
 * Mono is played on both sides, more than two channels are cut down to the first two, and other sample rates are
 * converted with straight linear interpolation (not the highest quality; files at 44.1 kHz are not touched).
 */
class PcmTapProcessor : BaseAudioProcessor() {
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var bytesPerSample = 2
    private var step = 1.0
    private var phase = 0.0
    private var previousLeft = 0f
    private var previousRight = 0f
    private var zeros = ByteArray(0)
    private var frameBuffer = FloatArray(0)
    private var pcmOut = ByteArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val sampleBytes = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount < 1 || inputAudioFormat.sampleRate <= 0) return AudioProcessor.AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        bytesPerSample = sampleBytes
        step = inputAudioFormat.sampleRate / AirPlayAudio.SAMPLE_RATE.toDouble()
        phase = 0.0
        previousLeft = 0f
        previousRight = 0f
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (!AirPlayAudio.tapping) {
            replaceOutputBuffer(size).put(inputBuffer).flip()
            return
        }
        if (!AirPlayAudio.mixOwns) feed(inputBuffer.duplicate().order(ByteOrder.nativeOrder()))
        val out = replaceOutputBuffer(size)
        if (AirPlayAudio.muteLocal) {
            if (zeros.size < size) zeros = ByteArray(size)
            out.put(zeros, 0, size)
            inputBuffer.position(inputBuffer.limit())
        } else {
            out.put(inputBuffer)
        }
        out.flip()
    }

    private fun sampleAt(buffer: ByteBuffer, index: Int): Float = when (encoding) {
        C.ENCODING_PCM_16BIT -> buffer.getShort(index) / 32768f
        C.ENCODING_PCM_24BIT -> {
            val raw = (buffer.get(index).toInt() and 0xFF) or
                ((buffer.get(index + 1).toInt() and 0xFF) shl 8) or
                (buffer.get(index + 2).toInt() shl 16)
            raw / 8388608f
        }
        C.ENCODING_PCM_32BIT -> buffer.getInt(index) / 2147483648f
        else -> buffer.getFloat(index)
    }

    private fun feed(src: ByteBuffer) {
        val frameBytes = bytesPerSample * channels
        val frames = src.remaining() / frameBytes
        if (frames == 0) return
        val base = src.position()
        if (frameBuffer.size < (frames + 1) * 2) frameBuffer = FloatArray((frames + 1) * 2)
        frameBuffer[0] = previousLeft
        frameBuffer[1] = previousRight
        for (f in 0 until frames) {
            val at = base + f * frameBytes
            val left = sampleAt(src, at)
            val right = if (channels >= 2) sampleAt(src, at + bytesPerSample) else left
            frameBuffer[(f + 1) * 2] = left
            frameBuffer[(f + 1) * 2 + 1] = right
        }
        val maxOut = (frames / step).toInt() + 4
        if (pcmOut.size < maxOut * 4) pcmOut = ByteArray(maxOut * 4)
        var written = 0
        while (phase < frames) {
            val i = phase.toInt()
            val fraction = (phase - i).toFloat()
            val left = frameBuffer[i * 2] * (1f - fraction) + frameBuffer[(i + 1) * 2] * fraction
            val right = frameBuffer[i * 2 + 1] * (1f - fraction) + frameBuffer[(i + 1) * 2 + 1] * fraction
            val l = (left * 32767f).toInt().coerceIn(-32768, 32767)
            val r = (right * 32767f).toInt().coerceIn(-32768, 32767)
            pcmOut[written] = l.toByte()
            pcmOut[written + 1] = (l shr 8).toByte()
            pcmOut[written + 2] = r.toByte()
            pcmOut[written + 3] = (r shr 8).toByte()
            written += 4
            phase += step
        }
        phase -= frames
        previousLeft = frameBuffer[frames * 2]
        previousRight = frameBuffer[frames * 2 + 1]
        AirPlayAudio.write(pcmOut, written)
    }
}
