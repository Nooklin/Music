package org.nooklin.music.airplay.raop.metadata

import java.io.ByteArrayOutputStream

/**
 * DMAP (Digital Media Access Protocol) encoder - the format AirPlay receivers expect
 * for track text, sent as `application/x-dmap-tagged`.
 *
 * The format is very simple: each entry is a 4-character ASCII tag, a 4-byte
 * big-endian length, then the value. Containers nest the same way, their length
 * covering all children.
 *
 * Tags used here match what OwnTone sends (src/dmap_common.c):
 *   mlit  container ("listing item")
 *   minm  item name        -> track title
 *   asar  song artist
 *   asal  song album
 *   astn  track number
 *   astm  total time in ms
 */
object DmapEncoder {

    private fun tag(out: ByteArrayOutputStream, name: String, payload: ByteArray) {
        require(name.length == 4) { "DMAP tags are always 4 characters, got '$name'" }
        out.write(name.toByteArray(Charsets.US_ASCII))
        val len = payload.size
        out.write((len ushr 24) and 0xFF)
        out.write((len ushr 16) and 0xFF)
        out.write((len ushr 8) and 0xFF)
        out.write(len and 0xFF)
        out.write(payload)
    }

    private fun string(out: ByteArrayOutputStream, name: String, value: String) {
        tag(out, name, value.toByteArray(Charsets.UTF_8))
    }

    private fun int32(out: ByteArrayOutputStream, name: String, value: Int) {
        tag(out, name, byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        ))
    }

    private fun int64(out: ByteArrayOutputStream, name: String, value: Long) {
        val b = ByteArray(8)
        for (i in 0 until 8) b[i] = ((value ushr (8 * (7 - i))) and 0xFF).toByte()
        tag(out, name, b)
    }

    private fun int8(out: ByteArrayOutputStream, name: String, value: Int) {
        tag(out, name, byteArrayOf((value and 0xFF).toByte()))
    }

    private fun int16(out: ByteArrayOutputStream, name: String, value: Int) {
        tag(out, name, byteArrayOf(
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        ))
    }

    /**
     * Builds the `mlit` container for one track, matching the field set OwnTone sends
     * (dmap_common.c, dmap_encode_queue_metadata).
     *
     * TWO THINGS ARE LOAD-BEARING and were missing from the first version, which is why
     * an Apple TV accepted the metadata with RTSP 200 and displayed nothing:
     *
     *  - `mikd` (item kind = 2, music) MUST BE FIRST. OwnTone's source says so outright:
     *    "dmap.itemkind must come first". Without it the receiver has no reason to treat
     *    the payload as a music track at all.
     *  - `asac` (song artwork count = 1) and `ased`. OwnTone: "Required for artwork" -
     *    artwork sent without them is accepted and ignored.
     *
     * `miid`/`mper` give the track an identity so a receiver can tell one from the next.
     */
    fun encodeTrack(
        title: String?,
        artist: String?,
        album: String?,
        trackNumber: Int? = null,
        durationMs: Int? = null,
        trackId: Int = 1
    ): ByteArray {
        val inner = ByteArrayOutputStream()

        int8(inner, "mikd", 2)      // item kind: music. MUST come first.
        int8(inner, "asdk", 0)      // data kind
        int32(inner, "miid", trackId)

        if (!title.isNullOrBlank()) string(inner, "minm", title)
        int64(inner, "mper", trackId.toLong() and 0xFFFFFFFFL)
        if (!album.isNullOrBlank()) string(inner, "asal", album)
        if (!artist.isNullOrBlank()) {
            string(inner, "asar", artist)
            string(inner, "asaa", artist)   // album artist, for receivers that prefer it
        }
        durationMs?.takeIf { it > 0 }?.let { int32(inner, "astm", it) }
        trackNumber?.takeIf { it > 0 }?.let { int16(inner, "astn", it) }

        // Describes the stream itself; some receivers show or rely on these.
        string(inner, "asfm", "wav")
        int16(inner, "asbr", 1411)

        int16(inner, "ased", 1)     // extradata flag
        int16(inner, "asac", 1)     // song artwork count - required for artwork display

        val out = ByteArrayOutputStream()
        tag(out, "mlit", inner.toByteArray())
        return out.toByteArray()
    }
}
