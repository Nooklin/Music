package org.nooklin.music.playback.beat

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A tempo and the place of one bar line. [anchorMs] is the time in the song (milliseconds) of a first beat of a bar;
 * the other beats lie at multiples of 60000 / [bpm] before and after it, and a bar is four beats.
 */
class BeatEdge(val bpm: Double, val anchorMs: Double, val key: Int = -1, val keyInfo: String = "")

/** What the analysis of a whole song found: bar lines to come in at and bar lines to leave from, best first. */
class SongMap(
    val entries: List<BeatEdge>,
    val exits: List<BeatEdge>,
    /** Where the music starts and ends (ms), after and before the silence; null when this is not known for sure */
    val cueInMs: Long?,
    val cueOutMs: Long?,
    /** How loud the song is in general, in the units of the loudness curve; low is calm, high is busy */
    val loudness: Double,
    /**
     * How much melody (a held sound in the middle of the music, in the centre of the stereo image: a voice or a lead)
     * there is, one digit per second: 0 is among the quietest tenth of this song, 9 among the busiest tenth.
     * Empty when not known.
     */
    val lead: String = "",
    /** The key of the song (see [KeyDetector]), or -1 when not known */
    val key: Int = -1,
    /** What the key detection found, for the log only (not saved) */
    val keyInfo: String = ""
)

/**
 * Finds the tempo, the bars and the best place to come in, on this phone. The audio is decoded with the platform
 * decoder and turned into a curve of how loud the low and the high part are (per 10 ms); the tempo is the spacing at
 * which the rises of that curve repeat best. Only that curve is kept, never the audio. Nothing leaves the phone.
 */
object BeatAnalyzer {
    private const val MIN_BPM = 70.0
    private const val MAX_BPM = 190.0

    // A section must rise at least this much (in the units of the loudness curve) to count as a good place to come in
    private const val MIN_RISE = 0.25
    private const val MAX_ENTRIES = 5
    private const val MAX_EXITS = 5
    private const val MIN_EXIT_FRACTION = 0.4

    // Silence is a stretch of 300 ms whose loudness stays under this part of the median loudness of the song
    private const val SILENCE_PART = 0.1
    private const val SILENCE_FRAMES = 30

    /** The end of a song: the tempo and the last bar line of the stretch from [startMs]. */
    fun analyzeEnd(source: (MediaExtractor) -> Unit, startMs: Long, maxMs: Long): BeatEdge? {
        val extractor = MediaExtractor()
        return try {
            source(extractor)
            val env = decode(extractor, startMs, maxMs, 30_000L) ?: return null
            val onset = onsetOf(env)
            val grid = findGrid(onset, env) ?: return null
            val period = 60000.0 / grid.bpm / env.hopMs
            val bar = barPhase(onset, env.count, grid.offset, period)
            val lastN = floor((env.count - 2 - grid.offset) / period).toInt()
            var n = lastN
            while (n > 0 && Math.floorMod(n, 4) != bar) n--
            BeatEdge(grid.bpm, env.startMs + (grid.offset + n * period) * env.hopMs, env.key, env.keyInfo)
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    /**
     * The whole song: the places where it is good to come in and the places where it is good to leave, best first.
     * A place to come in is a bar line where a section starts that is louder than what came just before; the beginning
     * of the song is always the last choice. A place to leave is a bar line where a loud section ends and a quieter
     * one follows (often the outro), so that the next song can take over while the music is still full.
     */
    fun analyzeSong(source: (MediaExtractor) -> Unit, durationMs: Long): SongMap? {
        val extractor = MediaExtractor()
        return try {
            source(extractor)
            val env = decode(extractor, 0L, durationMs + 10_000L, 120_000L) ?: return null
            val onset = onsetOf(env)
            val medianLoud = env.values.copyOf(env.count).sorted()[env.count / 2].toDouble()
            // The edges of the silence are only trusted when the whole song was read
            val whole = env.count * env.hopMs >= durationMs - 2_000.0
            val lead = leadOf(env)
            val (cueIn, cueOut) = if (whole) silenceEdges(env, medianLoud) else Pair(null, null)
            // A song without a clear beat still has a start, an end and a loudness
            val grid = findGrid(onset, env) ?: return SongMap(emptyList(), emptyList(), cueIn, cueOut, medianLoud, lead, env.key, env.keyInfo)
            val period = 60000.0 / grid.bpm / env.hopMs
            val bar = barPhase(onset, env.count, grid.offset, period)
            val beatsTotal = floor((env.count - 2 - grid.offset) / period).toInt()

            // The loudness of the 16 beats before and after each bar line
            val window = (16 * period).toInt()
            val sections = ArrayList<Section>()
            var n = bar
            while (n <= beatsTotal) {
                val at = (grid.offset + n * period).toInt()
                if (at - window >= 0 && at + window < env.count) {
                    sections.add(Section(n, mean(env.values, at - window, at), mean(env.values, at, at + window)))
                }
                n += 4
            }
            if (sections.isEmpty()) return SongMap(emptyList(), emptyList(), cueIn, cueOut, medianLoud, lead, env.key, env.keyInfo)
            val minGap = (10_000.0 / env.hopMs).toInt()
            val seconds = { frame: Double -> frame * env.hopMs / 1000.0 }
            val totalSeconds = env.count * env.hopMs / 1000.0

            // Where to come in: sections start on multiples of 16 beats from one place, the place with the most rises
            val riseSums = DoubleArray(4)
            for (sec in sections) if (sec.after - sec.before > 0) riseSums[Math.floorMod(sec.beat - bar, 16) / 4] += sec.after - sec.before
            val risePhrase = riseSums.indices.maxByOrNull { riseSums[it] } ?: 0
            val medianAfter = sections.map { it.after }.sorted().let { it[it.size / 2] }
            val enterScored = sections.mapNotNull { sec ->
                val frame = grid.offset + sec.beat * period
                val rise = sec.after - sec.before
                // Come in with energy, not in the last part of the song, and not too deep into it
                if (rise < MIN_RISE || sec.after < medianAfter) return@mapNotNull null
                if (frame > env.count * 0.6 || totalSeconds - seconds(frame) < 45.0) return@mapNotNull null
                val onPhrase = if (Math.floorMod(sec.beat - bar, 16) / 4 == risePhrase) 1.3 else 1.0
                Pair(frame, rise * onPhrase / (1.0 + seconds(frame) / 60.0))
            }
            val entries = ArrayList<BeatEdge>()
            for (frame in pick(enterScored, minGap, MAX_ENTRIES)) entries.add(refine(onset, env, frame, grid.bpm))
            // The song start as the last choice: the first bar line
            val first = grid.offset + bar * period
            entries.add(refine(onset, env, if (first < 0) first + 4 * period else first, grid.bpm))

            // Where to leave: a loud section followed by a quieter one, in the last part of the song
            val dropSums = DoubleArray(4)
            for (sec in sections) if (sec.before - sec.after > 0) dropSums[Math.floorMod(sec.beat - bar, 16) / 4] += sec.before - sec.after
            val dropPhrase = dropSums.indices.maxByOrNull { dropSums[it] } ?: 0
            val medianBefore = sections.map { it.before }.sorted().let { it[it.size / 2] }
            val exitScored = sections.mapNotNull { sec ->
                val frame = grid.offset + sec.beat * period
                val drop = sec.before - sec.after
                if (drop < MIN_RISE || sec.before < medianBefore) return@mapNotNull null
                if (frame < env.count * MIN_EXIT_FRACTION) return@mapNotNull null
                val onPhrase = if (Math.floorMod(sec.beat - bar, 16) / 4 == dropPhrase) 1.3 else 1.0
                // The later in the song, the better: the music up to there is played in full
                Pair(frame, drop * onPhrase * (frame / env.count))
            }
            val exits = ArrayList<BeatEdge>()
            for (frame in pick(exitScored, minGap, MAX_EXITS)) exits.add(refine(onset, env, frame, grid.bpm))
            SongMap(entries, exits, cueIn, cueOut, medianLoud, lead, env.key, env.keyInfo)
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private class Section(val beat: Int, val before: Double, val after: Double)

    /**
     * The melody curve: per second the level of a held sound in the mid band (drum hits are short, so the lowest level
     * within 100 ms is kept), times how much of it sits in the centre of the stereo image. Then ranked within the song
     * (not against a fixed value) into digits 0 to 9, because songs differ a lot in level.
     */
    private fun leadOf(env: Env): String {
        val seconds = (env.count * env.hopMs / 1000.0).toInt()
        if (seconds < 10) return ""
        val perSecond = max(1, (1000.0 / env.hopMs).roundToInt())
        val raw = DoubleArray(seconds)
        for (sec in 0 until seconds) {
            val from = sec * perSecond
            val to = min(env.count, from + perSecond)
            if (to - from < perSecond / 2) continue
            var held = 0.0
            var mid = 0.0
            var side = 0.0
            for (t in from until to) {
                var low = Float.MAX_VALUE
                for (k in max(0, t - 5)..min(env.count - 1, t + 5)) if (env.mid[k] < low) low = env.mid[k]
                held += low
                mid += env.mid[t]
                side += env.side[t]
            }
            held /= (to - from)
            val centre = if (mid + side > 1e-9) mid / (mid + side) else 0.0
            raw[sec] = held * centre
        }
        val order = raw.indices.sortedBy { raw[it] }
        val digits = CharArray(seconds)
        for ((rank, sec) in order.withIndex()) digits[sec] = ('0' + min(9, rank * 10 / seconds))
        return String(digits)
    }

    /** The first and the last moment (ms) that the loudness is above the silence level, or null for none. */
    private fun silenceEdges(env: Env, median: Double): Pair<Long?, Long?> {
        val level = median * SILENCE_PART * SILENCE_FRAMES
        if (env.count < SILENCE_FRAMES * 2) return Pair(null, null)
        val sums = DoubleArray(env.count + 1)
        for (i in 0 until env.count) sums[i + 1] = sums[i] + env.values[i]
        var first = -1
        var last = -1
        for (i in 0..env.count - SILENCE_FRAMES) {
            if (sums[i + SILENCE_FRAMES] - sums[i] > level) {
                if (first < 0) first = i
                last = i + SILENCE_FRAMES
            }
        }
        if (first < 0) return Pair(null, null)
        return Pair((first * env.hopMs).toLong(), (last * env.hopMs).toLong())
    }

    /** The best-scoring frames, at least [minGap] frames apart, at most [max] of them. */
    private fun pick(scored: List<Pair<Double, Double>>, minGap: Int, max: Int): List<Double> {
        val chosen = ArrayList<Double>()
        for ((frame, _) in scored.sortedByDescending { it.second }) {
            if (chosen.size >= max) break
            if (chosen.none { abs(it - frame) < minGap }) chosen.add(frame)
        }
        return chosen
    }

    /** The grid at the place [frame]: tempo and the bar line nearest to it, measured on the 12 seconds that follow. */
    private fun refine(onset: FloatArray, env: Env, frame: Double, bpm: Double): BeatEdge {
        val from = max(0, (frame - 100).toInt())
        val to = min(env.count - 2, (frame + 1200).toInt())
        val local = if (to - from > 600) fineSearch(onset, from, to, bpm, 0.015, 0.0005, env.hopMs) else null
        if (local == null || local.score < 0.3 * stdev(onset, from, to)) {
            return BeatEdge(bpm, env.startMs + frame * env.hopMs)
        }
        val period = 60000.0 / local.bpm / env.hopMs
        val m = ((frame - local.offset) / period).roundToInt()
        return BeatEdge(local.bpm, env.startMs + (local.offset + m * period) * env.hopMs)
    }

    private fun mean(values: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += values[i]
        return sum / (to - from)
    }

    private class Env(
        val values: FloatArray, val count: Int, val hopMs: Double, val startMs: Double,
        val mid: FloatArray, val side: FloatArray, val key: Int = -1, val keyInfo: String = ""
    )

    private class EnvBuilder(sampleRate: Int) {
        val hop = sampleRate / 100
        val hopMs = hop * 1000.0 / sampleRate
        var values = FloatArray(6000)
        var mids = FloatArray(6000)
        var sides = FloatArray(6000)
        var count = 0
        val keys = KeyDetector(sampleRate)

        // The mid band (200 Hz to 4 kHz) of the centre and of the sides: two one-pole filters each
        private val lowA = (1.0 - Math.exp(-2.0 * Math.PI * 200.0 / sampleRate)).toFloat()
        private val highA = (1.0 - Math.exp(-2.0 * Math.PI * 4000.0 / sampleRate)).toFloat()
        private var cLow = 0f
        private var cBand = 0f
        private var sLow = 0f
        private var sBand = 0f
        private var midSum = 0f
        private var sideSum = 0f
        private var low = 0f
        private var previous = 0f
        private var lowSum = 0f
        private var highSum = 0f
        private var inFrame = 0

        fun add(x: Float, side: Float) {
            keys.add(x)
            cLow += lowA * (x - cLow)
            cBand += highA * ((x - cLow) - cBand)
            sLow += lowA * (side - sLow)
            sBand += highA * ((side - sLow) - sBand)
            midSum += cBand * cBand
            sideSum += sBand * sBand
            low += 0.03f * (x - low)
            lowSum += low * low
            val d = x - previous
            highSum += d * d
            previous = x
            if (++inFrame == hop) {
                if (count == values.size) {
                    values = values.copyOf(count * 2)
                    mids = mids.copyOf(count * 2)
                    sides = sides.copyOf(count * 2)
                }
                mids[count] = sqrt(midSum / hop)
                sides[count] = sqrt(sideSum / hop)
                values[count++] = ln(1f + 50f * sqrt(lowSum / hop)) + ln(1f + 50f * sqrt(highSum / hop))
                midSum = 0f
                sideSum = 0f
                lowSum = 0f
                highSum = 0f
                inFrame = 0
            }
        }
    }

    private fun decode(extractor: MediaExtractor, startMs: Long, maxMs: Long, limitMs: Long): Env? {
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                extractor.selectTrack(i)
                format = f
                break
            }
        }
        if (format == null) return null
        if (startMs > 0) extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var builder: EnvBuilder? = null
            var firstUs = -1L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val deadline = SystemClock.elapsedRealtime() + limitMs
            while (!outputDone && SystemClock.elapsedRealtime() < deadline) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(5_000L)
                    if (i >= 0) {
                        val buffer = codec.getInputBuffer(i)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 5_000L)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    if (info.size > 0) {
                        if (firstUs < 0) firstUs = info.presentationTimeUs
                        if (builder == null) builder = EnvBuilder(sampleRate)
                        val buffer = codec.getOutputBuffer(o)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.nativeOrder())
                        val frames = info.size / (channels * if (floatPcm) 4 else 2)
                        if (floatPcm) {
                            val view = buffer.asFloatBuffer()
                            for (f in 0 until frames) {
                                var sum = 0f
                                var left = 0f
                                var right = 0f
                                for (c in 0 until channels) {
                                    val v = view.get()
                                    sum += v
                                    if (c == 0) left = v else if (c == 1) right = v
                                }
                                builder.add(sum / channels, if (channels >= 2) (left - right) / 2f else 0f)
                            }
                        } else {
                            val view = buffer.asShortBuffer()
                            for (f in 0 until frames) {
                                var sum = 0f
                                var left = 0f
                                var right = 0f
                                for (c in 0 until channels) {
                                    val v = view.get() / 32768f
                                    sum += v
                                    if (c == 0) left = v else if (c == 1) right = v
                                }
                                builder.add(sum / channels, if (channels >= 2) (left - right) / 2f else 0f)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(o, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    if (builder != null && builder.count * builder.hopMs >= maxMs) outputDone = true
                }
            }
            val b = builder ?: return null
            if (firstUs < 0 || b.count < 800) return null
            val key = b.keys.key()
            return Env(b.values, b.count, b.hopMs, firstUs / 1000.0, b.mids, b.sides, key, b.keys.report)
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
        }
    }

    /** The rise of the loudness, a little smoothed, with the mean taken away. */
    private fun onsetOf(env: Env): FloatArray {
        val frames = env.count
        val raw = FloatArray(frames)
        for (t in 3 until frames) {
            raw[t] = max(0f, env.values[t] - (env.values[t - 1] + env.values[t - 2] + env.values[t - 3]) / 3f)
        }
        val onset = FloatArray(frames)
        for (t in 1 until frames - 1) onset[t] = 0.25f * raw[t - 1] + 0.5f * raw[t] + 0.25f * raw[t + 1]
        var mean = 0f
        for (v in onset) mean += v
        mean /= frames
        for (t in 0 until frames) onset[t] -= mean
        return onset
    }

    private class Grid(val bpm: Double, val offset: Double, val score: Double)

    /** The tempo of the whole curve and the first beat: null when there is no clear beat. */
    private fun findGrid(onset: FloatArray, env: Env): Grid? {
        val frames = env.count
        val hopMs = env.hopMs
        val minLag = floor(60000.0 / MAX_BPM / hopMs).toInt()
        val maxLag = (60000.0 / MIN_BPM / hopMs).toInt()
        var bestLag = -1
        var bestAcf = 0.0
        for (lag in minLag..maxLag) {
            var sum = 0.0
            for (t in 0 until frames - lag) sum += onset[t] * onset[t + lag]
            val bpm = 60000.0 / (lag * hopMs)
            // A preference for tempos around 120
            val prior = Math.exp(-0.5 * (ln(bpm / 120.0) / ln(2.0)).pow(2))
            val score = sum / (frames - lag) * prior
            if (score > bestAcf) {
                bestAcf = score
                bestLag = lag
            }
        }
        if (bestLag < 0) return null
        val rough = 60000.0 / (bestLag * hopMs)
        val grid = fineSearch(onset, 0, frames, rough, 0.03, 0.0005, hopMs) ?: return null
        // Too weak a pattern: this is not a song with a clear beat
        if (grid.score < 0.35 * stdev(onset, 0, frames)) return null
        return grid
    }

    /** Tempo within [span] of [rough] and the first beat from [from] that put the most rises on the beats. */
    private fun fineSearch(
        onset: FloatArray, from: Int, to: Int, rough: Double, span: Double, stepSize: Double, hopMs: Double
    ): Grid? {
        var bestScore = -1.0
        var bestBpm = rough
        var bestOffset = 0.0
        var step = -span
        while (step <= span) {
            val bpm = rough * (1 + step)
            val period = 60000.0 / bpm / hopMs
            var offset = 0.0
            while (offset < period) {
                var sum = 0.0
                var n = 0
                var position = from + offset
                while (position < to - 2) {
                    val i = position.toInt()
                    val frac = position - i
                    sum += onset[i] * (1 - frac) + onset[i + 1] * frac
                    n++
                    position += period
                }
                val score = if (n > 0) sum / n else 0.0
                if (score > bestScore) {
                    bestScore = score
                    bestBpm = bpm
                    bestOffset = from + offset
                }
                offset += 0.5
            }
            step += stepSize
        }
        return if (bestScore < 0) null else Grid(bestBpm, bestOffset, bestScore)
    }

    /** Which of the four beats (0 to 3, counted from the first beat of the grid) is the first of a bar. */
    private fun barPhase(onset: FloatArray, frames: Int, offset: Double, period: Double): Int {
        val sums = DoubleArray(4)
        var n = 0
        var position = offset
        while (position < frames - 2) {
            sums[n and 3] += onset[position.toInt()]
            n++
            position += period
        }
        var best = 0
        for (i in 1..3) if (sums[i] > sums[best]) best = i
        return best
    }

    private fun stdev(values: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += values[i] * values[i]
        return sqrt(sum / max(1, to - from))
    }
}
