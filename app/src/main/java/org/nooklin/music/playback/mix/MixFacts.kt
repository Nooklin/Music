package org.nooklin.music.playback.mix

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the beat analysis ([NativeMix]) found per song and what the ordering of songs ([org.nooklin.music.playback.beat.MixOrder])
 * needs: the tempo and the key of the whole song. Written when a song has been analysed, read without decoding anything.
 */
object MixFacts {
    /** 0.0.103: where the song can be mixed in and out, from the cue fields of the map (see [MixMap.cues]). */
    class Edge(
        val energyStart: Double, val energyEnd: Double, val usableIntroBeats: Int, val usableOutroBeats: Int,
        val confidence: Double, val swing: Double
    )

    private class Fact(val bpm: Double, val key: Int, val energy: Double, val edge: Edge? = null)

    private val facts = HashMap<String, Fact>()
    private var loaded = false
    private var file: File? = null

    // Counts up with every song that is recorded, for a screen that shows how many songs were scanned
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    @Synchronized
    fun preload(context: Context) {
        if (loaded) return
        val dir = context.applicationContext.filesDir
        // Facts are kept per analysis version: when the analysis changes (MixLibrary.MAP_VERSION) they are made again
        File(dir, "automix_facts.tsv").delete()
        for (v in 1 until MixLibrary.MAP_VERSION) File(dir, "automix_facts_$v.tsv").delete()
        val f = File(dir, "automix_facts_${MixLibrary.MAP_VERSION}.tsv")
        file = f
        loaded = true
        try {
            if (f.exists()) f.forEachLine { line ->
                val p = line.split('\t')
                // The fourth column (the energy of the whole song, 0.0.91) is missing in older lines; later lines of a song win.
                // 0.0.103: six more columns with the edge of the song (energy at the start and the end, usable intro and outro in beats, confidence, swing)
                if (p.size == 3 || p.size == 4 || p.size == 10) {
                    val edge = if (p.size == 10) {
                        val es = p[4].toDoubleOrNull(); val ee = p[5].toDoubleOrNull(); val ui = p[6].toIntOrNull(); val uo = p[7].toIntOrNull()
                        val cf = p[8].toDoubleOrNull(); val sw = p[9].toDoubleOrNull()
                        if (es != null && ee != null && ui != null && uo != null && cf != null && sw != null) Edge(es, ee, ui, uo, cf, sw) else null
                    } else null
                    facts[p[0]] = Fact(p[1].toDoubleOrNull() ?: 0.0, p[2].toIntOrNull() ?: -1, p.getOrNull(3)?.toDoubleOrNull() ?: -1.0, edge)
                }
            }
        } catch (e: Exception) {
            Log.w("NooklinMix", "reading the song facts failed: ${e.javaClass.simpleName}")
        }
    }

    /** Forgets every song: the saved file is deleted, so all songs count as not scanned. */
    @Synchronized
    fun clear(context: Context) {
        facts.clear()
        try {
            File(context.applicationContext.filesDir, "automix_facts_${MixLibrary.MAP_VERSION}.tsv").delete()
        } catch (e: Exception) {
            Log.w("NooklinMix", "deleting the song facts failed: ${e.javaClass.simpleName}")
        }
        _version.value = _version.value + 1
    }

    /** The tempo of the song as analysed, or null when not known (or when no clear beat was found). */
    @Synchronized
    fun bpm(id: String): Double? = facts[id]?.bpm?.takeIf { it > 0.0 }

    /** True when the song was analysed (also when no clear beat was found). [preload] must have run. */
    @Synchronized
    fun known(id: String): Boolean = facts.containsKey(id)

    /** The key of the song (root + 12 when minor), -1 when not known or not clear. */
    @Synchronized
    fun key(id: String): Int = facts[id]?.key ?: -1

    /** How energetic the whole song is, 0 to 1 (see [MixMap.energyIndex]), or null when not known. */
    @Synchronized
    fun energy(id: String): Double? = facts[id]?.energy?.takeIf { it >= 0.0 }

    /** Forgets one song (also in the saved file), so it counts as not analysed and is analysed again (0.0.105). */
    @Synchronized
    fun forget(context: Context, id: String) {
        preload(context)
        if (facts.remove(id) == null) return
        try {
            file?.writeText(facts.entries.joinToString("") { (k, v) -> rowOf(k, v.bpm, v.key, v.energy, v.edge) })
        } catch (e: Exception) {
            Log.w("NooklinMix", "saving the song facts failed: ${e.javaClass.simpleName}")
        }
        _version.value += 1
    }

    /** The edge of the song (0.0.103), or null when not known (also for a song without cue fields). */
    @Synchronized
    fun edge(id: String): Edge? = facts[id]?.edge

    /** Every song that is known by id, for the modes of the AutoMix menu. */
    @Synchronized
    fun knownIds(): List<String> = facts.keys.toList()

    /**
     * Gives the songs that were analysed before the energy was noted their energy, from the grids saved on disk (nothing is
     * decoded). Returns how many songs got one.
     */
    @Synchronized
    fun backfillEnergy(context: Context): Int {
        preload(context)
        val dir = File(context.applicationContext.filesDir, "automix_maps")
        var done = 0
        for ((id, fact) in facts.toList()) {
            if (fact.energy >= 0.0) continue
            val map = MixLibrary.readMapFile(dir, id) ?: continue
            val e = MixMap.energyIndex(map)
            if (e < 0.0) continue
            facts[id] = Fact(fact.bpm, fact.key, e, fact.edge)
            try {
                file?.appendText(rowOf(id, fact.bpm, fact.key, e, fact.edge))
            } catch (ex: Exception) {
                Log.w("NooklinMix", "saving the song energy failed: ${ex.javaClass.simpleName}")
            }
            done++
        }
        if (done > 0) _version.value += 1
        return done
    }

    private fun rowOf(id: String, bpm: Double, key: Int, energy: Double, e: Edge?): String =
        if (e == null) "$id\t$bpm\t$key\t$energy\n"
        else "$id\t$bpm\t$key\t$energy\t${e.energyStart}\t${e.energyEnd}\t${e.usableIntroBeats}\t${e.usableOutroBeats}\t${e.confidence}\t${e.swing}\n"

    /** Notes the facts of an analysed song [map] (once per song). */
    @Synchronized
    fun record(context: Context, id: String, map: DoubleArray) {
        preload(context)
        if (facts.containsKey(id) || !MixMap.analysed(map)) return
        val bpm = if (MixMap.valid(map)) MixMap.bpm(map) else 0.0
        val key = if (MixMap.valid(map)) NativeMix.keyOf(map) else -1
        val energy = MixMap.energyIndex(map)
        val edge = MixMap.cues(map)?.takeIf { it.energyStart >= 0.0 && it.energyEnd >= 0.0 }
            ?.let { Edge(it.energyStart, it.energyEnd, it.usableIntroBeats, it.usableOutroBeats, it.cueConfidence, it.swing) }
        facts[id] = Fact(bpm, key, energy, edge)
        _version.value += 1
        try {
            file?.appendText(rowOf(id, bpm, key, energy, edge))
        } catch (e: Exception) {
            Log.w("NooklinMix", "saving the song facts failed: ${e.javaClass.simpleName}")
        }
    }
}
