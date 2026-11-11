package org.nooklin.music.playback.mix

import android.content.Context
import android.util.Log
import java.io.File
import org.nooklin.music.playback.taste.TasteStore
import org.nooklin.music.playback.beat.BeatMixStore
import kotlin.math.abs
import kotlin.math.ln

/**
 * Puts a grid right that follows the wrong pulse (0.0.95). The analysis keeps how well a grid at each candidate tempo fits the
 * strong onsets (the tempo evidence). When the grid and the older analysis of the whole song disagree (for example 3/4 or 3/2) and
 * the candidate at the older tempo fits the onsets better than the grid does, the grid is built again at that tempo.
 * The same rule as the doubt check of [MixConductor]; there the grid is only mistrusted, here it is made again.
 */
object MixTempo {
    private const val TAG = "NooklinMix"
    private const val AGREE = 0.04  // how far (in ln) a tempo may be from 1, 2 or 1/2 times another

    private fun agrees(a: Double, b: Double): Boolean {
        val r = a / b
        return listOf(0.5, 1.0, 2.0).any { abs(ln(r / it)) < AGREE }
    }

    /** The tempo a grid should be built at, or null when the grid stands (or nothing says better). */
    fun betterTempo(map: DoubleArray, old: Double?): Double? {
        val grid = MixMap.bpm(map)
        if (old == null || grid <= 0.0 || old <= 0.0 || agrees(grid, old)) return null
        val ev = MixMap.tempoEvidence(map)
        if (ev.size < 2) return null
        val own = ev[0].second
        val rival = ev.drop(1).filter { agrees(it.first, old) && !agrees(it.first, grid) }.maxByOrNull { it.second } ?: return null
        return if (rival.second > own) rival.first else null
    }

    /**
     * The grid of the decoded song [handle] with [id], made again at the older tempo when [betterTempo] says so and the new grid
     * agrees with the older analysis; otherwise [map] itself.
     */
    fun fixed(id: String, handle: Long, map: DoubleArray, genre: String? = null): DoubleArray = doubled(id, handle, regridded(id, handle, map), genre)

    // ---- 0.0.105: the octave of fast music ----------------------------------------------------------------------------------
    // The tempo search covers 70 to 180 bpm with a preference for 125, and a grid at half the tempo covers the onsets as well as one at the
    // full tempo (every second beat is simply left out), so the measurement cannot tell the octaves apart. Music at 170 to 200 bpm
    // (drum and bass, hardcore) therefore came out at 85 to 100 (log 0.0.103/104: 87 and 96.8 bpm for songs of about 174 and 194). The planner
    // folds octaves, so it did not see the error, but the modes (Energy from 120 bpm, Chill under 100, the tempo zones) and the size of the
    // tempo change did. Octave information in the sound is not reliable (snare and hat patterns overlap between genres), so the genre tag
    // decides, and only for genres whose tempo convention is known to lie above 150 bpm.
    private val FAST_GENRES = listOf("drum", "dnb", "d&b", "jungle", "neurofunk", "liquid funk", "hardcore", "gabber", "frenchcore", "uptempo", "breakcore")
    const val DOUBLE_FROM = 78.0
    const val DOUBLE_TO = 105.0

    /** True for a genre whose tempo is normally given at 150 bpm or more. */
    fun fastGenre(genre: String?): Boolean {
        val g = genre?.lowercase().orEmpty()
        return g.isNotEmpty() && FAST_GENRES.any { g.contains(it) }
    }

    /** True when the grid of a song of [genre] is probably at half its tempo. */
    fun suspect(bpm: Double?, genre: String?): Boolean = bpm != null && bpm >= DOUBLE_FROM && bpm < DOUBLE_TO && fastGenre(genre)

    // 0.0.109: fast music with no (or a wrong) genre tag. Log 0.0.108: a song with an unknown genre came out at 76 bpm with cue confidence 0.32, a 4 beat
    // intro and loud edges (energy 0.86 to 1.00), between songs of 156 and 74 bpm; it was most likely a 152 bpm song. A grid in the half range with
    // unclear structure and loud edges on both sides is tried again at twice the tempo, unless the genre says slow music. The doubled grid must still
    // fit the onsets about as well as the half one did, as for the genre rule. Songs that are really slow and loud at both edges stay a risk (the
    // modes and the tempo zones would see twice their tempo); the planner folds octaves, so the blends themselves are not affected.
    private val SLOW_GENRES = listOf("hip hop", "hip-hop", "rap", "reggae", "dub", "ballad", "downtempo", "chill", "lofi", "lo-fi", "soul", "r&b", "jazz", "blues", "ambient", "folk", "country", "trip hop", "trap", "classical")
    const val UNSURE_FROM = 72.0
    const val UNSURE_TO = 90.0

    fun slowGenre(genre: String?): Boolean {
        val g = genre?.lowercase().orEmpty()
        return g.isNotEmpty() && SLOW_GENRES.any { g.contains(it) }
    }

    /** True when the grid lies in the half range, the structure is unclear and both edges are loud, and the genre does not say slow. */
    fun unsureHalf(map: DoubleArray, genre: String?): Boolean {
        val bpm = MixMap.bpm(map)
        if (bpm < UNSURE_FROM || bpm >= UNSURE_TO || slowGenre(genre)) return false
        val c = MixMap.cues(map) ?: return false
        return c.cueConfidence < 0.5 && c.energyStart >= 0.8 && c.energyEnd >= 0.8
    }

    /** The grid made again at twice the tempo when [genre] says the song is fast and the grid lies in the half-tempo range; otherwise [map]. */
    private fun doubled(id: String, handle: Long, map: DoubleArray, genre: String?): DoubleArray {
        if (!MixMap.valid(map) || !(suspect(MixMap.bpm(map), genre) || unsureHalf(map, genre))) return map
        val bpm = MixMap.bpm(map)
        val own = MixMap.tempoEvidence(map).firstOrNull()?.second ?: return map
        val again = NativeMix.analyzeAt(handle, 2.0 * bpm)
        // The doubled grid must really be at twice the tempo and fit the strong onsets about as well as the half one did
        val ev = MixMap.tempoEvidence(again).firstOrNull()?.second
        val ok = MixMap.analysed(again) && MixMap.valid(again) && abs(ln(MixMap.bpm(again) / (2.0 * bpm))) < AGREE && ev != null && ev >= own - 0.1
        return if (ok) again else map
    }

    /**
     * Songs analysed before this rule whose genre says fast and whose grid lies in the half-tempo range are forgotten (facts and grid on
     * disk), once each, so the next scan or play analyses them again with [doubled]. Returns how many.
     */
    @Synchronized
    fun recheckOctaves(context: Context): Int {
        val app = context.applicationContext
        MixFacts.preload(app)
        val doneFile = File(app.filesDir, "automix_octave_checked.txt")
        val done = try { if (doneFile.exists()) doneFile.readLines().toHashSet() else HashSet() } catch (e: Exception) { HashSet<String>() }
        val taste = TasteStore.get(app)
        var n = 0
        for (id in MixFacts.knownIds()) {
            if (id in done) continue
            if (!suspect(MixFacts.bpm(id), taste.entry(id)?.genre)) continue
            MixFacts.forget(app, id)
            File(File(app.filesDir, "automix_maps"), id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".map").delete()
            try { doneFile.appendText(id + "\n") } catch (e: Exception) { Log.w(TAG, "saving the octave check failed: ${e.javaClass.simpleName}") }
            n++
        }
        return n
    }

    /** 0.0.109: once, songs of 72 to 90 bpm without a slow genre are forgotten so the next scan applies [unsureHalf] to them. Returns how many. */
    @Synchronized
    fun recheckUnsure(context: Context): Int {
        val app = context.applicationContext
        MixFacts.preload(app)
        val doneFile = File(app.filesDir, "automix_octave_checked2.txt")
        val done = try { if (doneFile.exists()) doneFile.readLines().toHashSet() else HashSet() } catch (e: Exception) { HashSet<String>() }
        val taste = TasteStore.get(app)
        var n = 0
        for (id in MixFacts.knownIds()) {
            if (id in done) continue
            val bpm = MixFacts.bpm(id) ?: continue
            if (bpm < UNSURE_FROM || bpm >= UNSURE_TO || slowGenre(taste.entry(id)?.genre)) continue
            MixFacts.forget(app, id)
            File(File(app.filesDir, "automix_maps"), id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".map").delete()
            try { doneFile.appendText(id + "\n") } catch (e: Exception) { Log.w(TAG, "saving the octave check failed: ${e.javaClass.simpleName}") }
            n++
        }
        return n
    }

    private fun regridded(id: String, handle: Long, map: DoubleArray): DoubleArray {
        val old = BeatMixStore.entries(id)?.firstOrNull()?.bpm
        val bpm = betterTempo(map, old) ?: return map
        val again = NativeMix.analyzeAt(handle, bpm)
        val ok = MixMap.analysed(again) && MixMap.valid(again) && old != null && agrees(MixMap.bpm(again), old)
        return if (ok) again else map
    }
}
