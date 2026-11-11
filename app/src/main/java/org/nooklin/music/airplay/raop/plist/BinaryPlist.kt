package org.nooklin.music.airplay.raop.plist

import java.io.ByteArrayOutputStream

/**
 * Minimal Apple binary property list (bplist00) ENCODER.
 *
 * AirPlay 2's RTSP SETUP/RECORD bodies are binary plists; Android has no built-in
 * encoder, so this implements the subset of the format those messages need:
 * dictionaries, arrays, strings (ASCII + UTF-16), integers, booleans, and raw data.
 *
 * Format reference: Apple's CoreFoundation CFBinaryPList.c layout --
 *   [8-byte magic "bplist00"]
 *   [object table: each object tagged by a marker byte]
 *   [offset table: offset of every object, each `offsetSize` bytes]
 *   [32-byte trailer: sortVersion, offsetIntSize, objectRefSize, numObjects,
 *    topObjectRef, offsetTableOffset]
 *
 * Verified byte-for-byte against Python's plistlib (FMT_BINARY) -- see the
 * cross-check script used during development -- rather than trusted from memory.
 */
object BinaryPlist {

    /** Anything encodable: Map, List, String, Int/Long, Boolean, ByteArray, Double. */
    fun encode(root: Any?): ByteArray {
        val objects = ArrayList<Any?>()
        val indexOf = HashMap<Key, Int>()

        fun intern(value: Any?): Int {
            val key = Key(value)
            indexOf[key]?.let { return it }
            val idx = objects.size
            objects.add(value)
            indexOf[key] = idx
            // Recurse so children are assigned object indices too.
            when (value) {
                is Map<*, *> -> {
                    for (k in value.keys) intern(k)
                    for (v in value.values) intern(v)
                }
                is List<*> -> for (v in value) intern(v)
            }
            return idx
        }
        intern(root)

        val objectRefSize = byteWidthFor(objects.size)
        val encoded = ArrayList<ByteArray>(objects.size)
        for (obj in objects) encoded.add(encodeObject(obj, objects, objectRefSize))

        val out = ByteArrayOutputStream()
        out.write("bplist00".toByteArray(Charsets.US_ASCII))
        val offsets = IntArray(objects.size)
        var pos = 8
        for (i in encoded.indices) {
            offsets[i] = pos
            out.write(encoded[i])
            pos += encoded[i].size
        }

        val offsetTableOffset = pos
        val offsetSize = byteWidthFor(offsetTableOffset)
        for (off in offsets) out.write(writeSizedInt(off, offsetSize))

        // 32-byte trailer: 5 unused + sortVersion + offsetIntSize + objectRefSize
        //                  + 8-byte numObjects + 8-byte topObject + 8-byte offsetTableOffset
        out.write(ByteArray(5))
        out.write(0) // sortVersion
        out.write(offsetSize)
        out.write(objectRefSize)
        out.write(writeSizedInt(objects.size.toLong(), 8))
        out.write(writeSizedInt(0L, 8)) // root object is index 0
        out.write(writeSizedInt(offsetTableOffset.toLong(), 8))
        return out.toByteArray()
    }

    /** Wrapper giving value-equality semantics for dedup, incl. ByteArray content. */
    private class Key(val value: Any?) {
        override fun hashCode(): Int = when (value) {
            is ByteArray -> value.contentHashCode()
            null -> 0
            else -> value.hashCode()
        }
        override fun equals(other: Any?): Boolean {
            if (other !is Key) return false
            val a = value
            val b = other.value
            // Distinguish Boolean from Int (true != 1) -- types must match.
            if (a != null && b != null && a::class != b::class) return false
            return when {
                a is ByteArray && b is ByteArray -> a.contentEquals(b)
                else -> a == b
            }
        }
    }

    private fun byteWidthFor(maxValue: Int): Int = when {
        maxValue < 256 -> 1
        maxValue < 65536 -> 2
        else -> 4
    }

    private fun writeSizedInt(value: Int, size: Int): ByteArray = writeSizedInt(value.toLong(), size)

    private fun writeSizedInt(value: Long, size: Int): ByteArray {
        val out = ByteArray(size)
        for (i in 0 until size) {
            out[size - 1 - i] = ((value shr (8 * i)) and 0xFF).toByte()
        }
        return out
    }

    /** Marker byte + length, using the 0x0F extended-length form when count >= 15. */
    private fun writeMarkerWithLength(out: ByteArrayOutputStream, marker: Int, count: Int) {
        if (count < 15) {
            out.write(marker or count)
        } else {
            out.write(marker or 0x0F)
            // Extended length is itself an int object: 0x1_ marker where _ = log2(byteWidth)
            val (intMarker, width) = when {
                count < 256 -> 0x10 to 1
                count < 65536 -> 0x11 to 2
                else -> 0x12 to 4
            }
            out.write(intMarker)
            out.write(writeSizedInt(count, width))
        }
    }

    private fun encodeObject(obj: Any?, objects: List<Any?>, refSize: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val indexOf = { v: Any? ->
            objects.indexOfFirst { candidate ->
                if (candidate is ByteArray && v is ByteArray) candidate.contentEquals(v)
                else if (candidate != null && v != null && candidate::class != v::class) false
                else candidate == v
            }
        }

        when (obj) {
            null -> out.write(0x00)
            is Boolean -> out.write(if (obj) 0x09 else 0x08)

            is Int, is Long -> {
                val v = (obj as Number).toLong()
                // Integers are stored in 1/2/4/8-byte forms; negatives always use 8.
                val width = when {
                    v < 0 -> 8
                    v < 256 -> 1
                    v < 65536 -> 2
                    v < 4294967296L -> 4
                    else -> 8
                }
                val marker = when (width) {
                    1 -> 0x10; 2 -> 0x11; 4 -> 0x12; else -> 0x13
                }
                out.write(marker)
                out.write(writeSizedInt(v, width))
            }

            is Double -> {
                out.write(0x23) // 8-byte IEEE754 double
                out.write(writeSizedInt(java.lang.Double.doubleToRawLongBits(obj), 8))
            }

            is ByteArray -> {
                writeMarkerWithLength(out, 0x40, obj.size)
                out.write(obj)
            }

            is String -> {
                val isAscii = obj.all { it.code < 128 }
                if (isAscii) {
                    writeMarkerWithLength(out, 0x50, obj.length)
                    out.write(obj.toByteArray(Charsets.US_ASCII))
                } else {
                    // UTF-16BE; length is in CHARACTERS (UTF-16 code units), not bytes
                    val bytes = obj.toByteArray(Charsets.UTF_16BE)
                    writeMarkerWithLength(out, 0x60, bytes.size / 2)
                    out.write(bytes)
                }
            }

            is List<*> -> {
                writeMarkerWithLength(out, 0xA0, obj.size)
                for (item in obj) out.write(writeSizedInt(indexOf(item), refSize))
            }

            is Map<*, *> -> {
                writeMarkerWithLength(out, 0xD0, obj.size)
                // All keys first, then all values, in matching order.
                for (k in obj.keys) out.write(writeSizedInt(indexOf(k), refSize))
                for (v in obj.values) out.write(writeSizedInt(indexOf(v), refSize))
            }

            else -> throw IllegalArgumentException("Unsupported plist type: ${obj::class}")
        }
        return out.toByteArray()
    }
}
