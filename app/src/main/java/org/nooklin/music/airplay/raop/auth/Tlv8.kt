package org.nooklin.music.airplay.raop.auth

/**
 * TLV8 encode/decode as used by HAP (HomeKit Accessory Protocol) pairing messages.
 * Format per entry: [1-byte tag][1-byte length][value bytes]. Values longer than
 * 255 bytes are split across multiple consecutive entries with the same tag and
 * reassembled on read (this implementation does not need >255-byte values for the
 * transient pairing flow, but supports it for correctness).
 *
 * Ported from the pyatv project's hap_tlv8.py (github.com/postlund/pyatv), a
 * verified working AirPlay/HomeKit sender implementation - not written from memory.
 */
object Tlv8 {

    object Tag {
        const val METHOD: Int = 0x00
        const val IDENTIFIER: Int = 0x01
        const val SALT: Int = 0x02
        const val PUBLIC_KEY: Int = 0x03
        const val PROOF: Int = 0x04
        const val ENCRYPTED_DATA: Int = 0x05
        const val SEQ_NO: Int = 0x06
        const val ERROR: Int = 0x07
        const val BACK_OFF: Int = 0x08
        const val SIGNATURE: Int = 0x0A
        const val FLAGS: Int = 0x13
    }

    object Flags {
        const val TRANSIENT_PAIRING: Int = 0x10
    }

    fun read(data: ByteArray): Map<Int, ByteArray> {
        val result = LinkedHashMap<Int, ByteArray>()
        var pos = 0
        while (pos < data.size) {
            val tag = data[pos].toInt() and 0xFF
            val length = data[pos + 1].toInt() and 0xFF
            val value = data.copyOfRange(pos + 2, pos + 2 + length)
            val existing = result[tag]
            result[tag] = if (existing != null) existing + value else value
            pos += 2 + length
        }
        return result
    }

    fun write(entries: List<Pair<Int, ByteArray>>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for ((tag, value) in entries) {
            var offset = 0
            var remaining = value.size
            // A single logical value >255 bytes is written as repeated chunks
            // with the same tag, per spec; the loop below still executes once
            // for empty values (remaining == 0) so zero-length TLVs are valid.
            do {
                val chunkSize = minOf(remaining, 255)
                out.write(tag and 0xFF)
                out.write(chunkSize)
                out.write(value, offset, chunkSize)
                offset += chunkSize
                remaining -= chunkSize
            } while (remaining > 0)
        }
        return out.toByteArray()
    }
}
