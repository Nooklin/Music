package org.nooklin.music.airplay.raop.plist

/**
 * Apple binary property list (bplist00) DECODER -- the counterpart to [BinaryPlist].
 *
 * Needed to read RTSP responses: GET /info reports the receiver's model and feature
 * flags (which determine whether it speaks AirPlay 1 or 2), and SETUP responses carry
 * the event/control ports. Replaces the earlier key-scanning heuristic.
 *
 * Returns plain Kotlin types: Map<String, Any?>, List<Any?>, String, Long, Boolean,
 * Double, ByteArray.
 */
object BinaryPlistDecoder {

    class PlistFormatException(message: String) : Exception(message)

    fun decode(data: ByteArray): Any? {
        if (data.size < 40) throw PlistFormatException("Too short to be a bplist (${data.size} bytes)")
        val magic = String(data, 0, 8, Charsets.US_ASCII)
        if (magic != "bplist00") throw PlistFormatException("Bad magic: '$magic'")

        // Trailer is the last 32 bytes.
        val t = data.size - 32
        val offsetSize = data[t + 6].toInt() and 0xFF
        val objectRefSize = data[t + 7].toInt() and 0xFF
        val numObjects = readBigEndian(data, t + 8, 8).toInt()
        val topObject = readBigEndian(data, t + 16, 8).toInt()
        val offsetTableOffset = readBigEndian(data, t + 24, 8).toInt()

        val offsets = IntArray(numObjects) { i ->
            readBigEndian(data, offsetTableOffset + i * offsetSize, offsetSize).toInt()
        }

        return parseObject(data, offsets, topObject, objectRefSize)
    }

    private fun readBigEndian(data: ByteArray, pos: Int, size: Int): Long {
        var v = 0L
        for (i in 0 until size) v = (v shl 8) or (data[pos + i].toLong() and 0xFF)
        return v
    }

    private fun parseObject(
        data: ByteArray,
        offsets: IntArray,
        index: Int,
        refSize: Int
    ): Any? {
        if (index < 0 || index >= offsets.size) throw PlistFormatException("Object ref $index out of range")
        var pos = offsets[index]
        val marker = data[pos].toInt() and 0xFF
        val objType = marker and 0xF0
        val objInfo = marker and 0x0F
        pos += 1

        /** Reads the element count, following the 0x0F extended-length form when present. */
        fun readCount(): Int {
            if (objInfo != 0x0F) return objInfo
            val intMarker = data[pos].toInt() and 0xFF
            pos += 1
            val width = 1 shl (intMarker and 0x0F)
            val count = readBigEndian(data, pos, width).toInt()
            pos += width
            return count
        }

        return when (objType) {
            0x00 -> when (marker) {
                0x00 -> null
                0x08 -> false
                0x09 -> true
                else -> throw PlistFormatException("Unknown singleton marker 0x%02x".format(marker))
            }

            0x10 -> { // integer, width = 2^objInfo bytes
                val width = 1 shl objInfo
                readBigEndian(data, pos, width)
            }

            0x20 -> { // real
                val width = 1 shl objInfo
                val bits = readBigEndian(data, pos, width)
                if (width == 4) java.lang.Float.intBitsToFloat(bits.toInt()).toDouble()
                else java.lang.Double.longBitsToDouble(bits)
            }

            0x40 -> { // raw data
                val count = readCount()
                data.copyOfRange(pos, pos + count)
            }

            0x50 -> { // ASCII string
                val count = readCount()
                String(data, pos, count, Charsets.US_ASCII)
            }

            0x60 -> { // UTF-16BE string; count is in code units, so byte length is 2x
                val count = readCount()
                String(data, pos, count * 2, Charsets.UTF_16BE)
            }

            0xA0 -> { // array
                val count = readCount()
                (0 until count).map { i ->
                    val ref = readBigEndian(data, pos + i * refSize, refSize).toInt()
                    parseObject(data, offsets, ref, refSize)
                }
            }

            0xD0 -> { // dictionary: all key refs, then all value refs
                val count = readCount()
                val result = LinkedHashMap<String, Any?>()
                for (i in 0 until count) {
                    val keyRef = readBigEndian(data, pos + i * refSize, refSize).toInt()
                    val valRef = readBigEndian(data, pos + (count + i) * refSize, refSize).toInt()
                    val key = parseObject(data, offsets, keyRef, refSize)
                    result[key.toString()] = parseObject(data, offsets, valRef, refSize)
                }
                result
            }

            else -> throw PlistFormatException("Unsupported object type 0x%02x".format(objType))
        }
    }

    /** Renders a decoded plist for logging, abbreviating long byte arrays. */
    fun describe(value: Any?, indent: String = ""): String = when (value) {
        is Map<*, *> -> value.entries.joinToString("\n") { (k, v) ->
            if (v is Map<*, *> || v is List<*>) "$indent$k:\n${describe(v, "$indent  ")}"
            else "$indent$k = ${describe(v)}"
        }
        is List<*> -> value.joinToString("\n") { "$indent- ${describe(it, "$indent  ").trimStart()}" }
        is ByteArray -> "<${value.size} bytes>"
        else -> value.toString()
    }
}
