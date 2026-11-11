@file:OptIn(UnstableApi::class)

package org.nooklin.music.playback.beat

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Takes tones out of a song, as the three knobs of a DJ mixer do: the bass (below about 180 Hz), the mids (180 Hz to
 * 3 kHz, where the melody and the voice are) and the highs (above 3 kHz). For each, 0 leaves the song as it is and 1
 * removes that part. Every amount moves to the wanted value in a quarter of a second, so there is no click.
 * Used during a mix: the song that comes in has no bass and no mids at first, the old song loses its mids early, then
 * the bass swaps, then the old song loses its highs.
 */
class BassCutProcessor : BaseAudioProcessor() {
    @Volatile private var wanted = 0f
    @Volatile private var wantedMid = 0f
    @Volatile private var wantedHigh = 0f
    @Volatile private var jump = false

    private var amount = 0f
    private var amountMid = 0f
    private var amountHigh = 0f
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var bytes = 2
    private var slew = 1.0 / 11025.0

    // The echo: 0 off, 1 the sound goes into the echo and plays on, 2 the sound stops and only the echo rings on
    @Volatile private var echoWanted = 0
    @Volatile private var echoDelayMs = 0.0
    private var echoRunning = false
    private var echoBuffer = FloatArray(0)
    private var echoFrames = 1
    private var echoPos = 0
    private var echoGate = 1f
    private var sampleRate = 44100

    // Four filter stages per channel: two for the 180 Hz low pass and two for the 3 kHz low pass (b0, b1, b2, a1, a2
    // for each, and the two memories of each stage)
    private val coef = Array(4) { DoubleArray(5) }
    private var memory = DoubleArray(16)

    /** The amount to move to, from 0 to 1. */
    fun set(value: Float) {
        wanted = value.coerceIn(0f, 1f)
    }

    /** The amounts of the mids and the highs to move to, from 0 to 1. */
    fun setBands(mid: Float, high: Float) {
        wantedMid = mid.coerceIn(0f, 1f)
        wantedHigh = high.coerceIn(0f, 1f)
    }

    /** Goes to all three amounts at once. */
    fun snapAll(bass: Float, mid: Float, high: Float) {
        wanted = bass.coerceIn(0f, 1f)
        wantedMid = mid.coerceIn(0f, 1f)
        wantedHigh = high.coerceIn(0f, 1f)
        jump = true
    }

    /** Starts an echo with a delay of [delayMs] (one beat of the song). The sound itself plays on as it is. */
    fun echoStart(delayMs: Double) {
        echoDelayMs = delayMs
        echoWanted = 1
    }

    /** The sound itself and what goes into the echo stop (in 15 ms); what is in the echo rings on. */
    fun echoCut() {
        echoWanted = 2
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        bytes = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount < 1 || inputAudioFormat.sampleRate <= 0) return AudioProcessor.AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        sampleRate = inputAudioFormat.sampleRate
        // A running echo is made again for the new format at the next buffer
        echoRunning = false
        val rate = inputAudioFormat.sampleRate.toDouble()
        slew = 1.0 / (0.25 * rate)
        // A low pass of the fourth order (two stages of the second order, Butterworth)
        design(coef[0], rate, 0.5412, CUTOFF_HZ)
        design(coef[1], rate, 1.3066, CUTOFF_HZ)
        design(coef[2], rate, 0.5412, MID_CUTOFF_HZ)
        design(coef[3], rate, 1.3066, MID_CUTOFF_HZ)
        memory = DoubleArray(channels * 8)
        return inputAudioFormat
    }

    private fun design(target: DoubleArray, rate: Double, q: Double, cutoff: Double) {
        val w = 2.0 * PI * cutoff / rate
        val alpha = sin(w) / (2.0 * q)
        val c = cos(w)
        val a0 = 1.0 + alpha
        target[0] = (1.0 - c) / 2.0 / a0
        target[1] = (1.0 - c) / a0
        target[2] = (1.0 - c) / 2.0 / a0
        target[3] = -2.0 * c / a0
        target[4] = (1.0 - alpha) / a0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (jump) {
            amount = wanted
            amountMid = wantedMid
            amountHigh = wantedHigh
            jump = false
        }
        if (echoWanted != 0 && !echoRunning) startEcho()
        if (!echoRunning && amount == 0f && wanted == 0f && amountMid == 0f && wantedMid == 0f && amountHigh == 0f && wantedHigh == 0f) {
            replaceOutputBuffer(size).put(inputBuffer).flip()
            return
        }
        val gateGoal = if (echoWanted == 2) 0f else 1f
        val gateStep = 1f / (0.015f * sampleRate)
        val src = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        val out = replaceOutputBuffer(size)
        val frameBytes = bytes * channels
        val frames = size / frameBytes
        val base = src.position()
        for (f in 0 until frames) {
            amount = approach(amount, wanted)
            amountMid = approach(amountMid, wantedMid)
            amountHigh = approach(amountHigh, wantedHigh)
            if (echoRunning) {
                echoGate = if (echoGate < gateGoal) minOf(gateGoal, echoGate + gateStep) else maxOf(gateGoal, echoGate - gateStep)
            }
            for (c in 0 until channels) {
                val x = read(src, base + f * frameBytes + c * bytes).toDouble()
                val low = lowPass(x, c, 0)
                val upToMid = lowPass(x, c, 2)
                // Bass: below 180 Hz. Mids: between the two. Highs: above 3 kHz.
                var y = (x - amount * low - amountMid * (upToMid - low) - amountHigh * (x - upToMid)).toFloat()
                if (echoRunning) {
                    // What was heard one delay ago comes back, and goes on to come back again, each time quieter
                    val at = echoPos * channels + c
                    val wet = echoBuffer[at]
                    val dry = y * echoGate
                    echoBuffer[at] = dry * ECHO_SEND + wet * ECHO_FEEDBACK
                    y = dry + wet
                }
                write(out, y)
            }
            if (echoRunning) {
                echoPos++
                if (echoPos >= echoFrames) echoPos = 0
            }
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    /** Makes the echo ready: an empty delay line as long as the delay. */
    private fun startEcho() {
        echoFrames = Math.round(echoDelayMs * sampleRate / 1000.0).toInt().coerceIn(1, sampleRate * 2)
        echoBuffer = FloatArray(echoFrames * channels)
        echoPos = 0
        echoGate = 1f
        echoRunning = true
    }

    private fun approach(value: Float, goal: Float): Float =
        if (value < goal) minOf(goal, value + slew.toFloat()) else if (value > goal) maxOf(goal, value - slew.toFloat()) else value

    /** One low pass of the fourth order (two stages) over one sample; [first] is the first of its two stages. */
    private fun lowPass(x: Double, channel: Int, first: Int): Double {
        var v = x
        for (s in first..first + 1) {
            val k = coef[s]
            val m = channel * 8 + s * 2
            val y = k[0] * v + memory[m]
            memory[m] = k[1] * v - k[3] * y + memory[m + 1]
            memory[m + 1] = k[2] * v - k[4] * y
            v = y
        }
        return v
    }

    private fun read(b: ByteBuffer, index: Int): Float = when (encoding) {
        C.ENCODING_PCM_16BIT -> b.getShort(index) / 32768f
        C.ENCODING_PCM_24BIT -> {
            val raw = (b.get(index).toInt() and 0xFF) or
                ((b.get(index + 1).toInt() and 0xFF) shl 8) or
                (b.get(index + 2).toInt() shl 16)
            raw / 8388608f
        }
        C.ENCODING_PCM_32BIT -> b.getInt(index) / 2147483648f
        else -> b.getFloat(index)
    }

    private fun write(out: ByteBuffer, value: Float) {
        when (encoding) {
            C.ENCODING_PCM_16BIT -> out.putShort((value * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
            C.ENCODING_PCM_24BIT -> {
                val v = (value * 8388608f).roundToInt().coerceIn(-8388608, 8388607)
                out.put(v.toByte())
                out.put((v shr 8).toByte())
                out.put((v shr 16).toByte())
            }
            C.ENCODING_PCM_32BIT -> out.putInt((value.toDouble() * 2147483648.0).toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
            else -> out.putFloat(value)
        }
    }

    private companion object {
        const val CUTOFF_HZ = 180.0
        const val MID_CUTOFF_HZ = 3000.0

        // The first repeat of the echo is this loud, and each next repeat is this part of the one before
        const val ECHO_SEND = 0.55f
        const val ECHO_FEEDBACK = 0.55f
    }
}
