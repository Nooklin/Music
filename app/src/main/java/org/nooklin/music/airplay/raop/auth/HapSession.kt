package org.nooklin.music.airplay.raop.auth

import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * HAP session encryption for the control (RTSP) connection.
 *
 * After transient pairing completes, the receiver expects EVERY subsequent byte on
 * the connection to be encrypted. Sending plaintext makes it read garbage and drop
 * the connection with no error response.
 *
 * Wire format (HAP spec section 5.2.2), matching pyatv's HAPSession:
 *   [2-byte little-endian plaintext length][ChaCha20-Poly1305 ciphertext][16-byte tag]
 * The 2-byte length prefix is also the AAD for that frame. Plaintext is split into
 * frames of at most 1024 bytes. Send and receive directions have independent keys and
 * independent frame counters, each starting at 0.
 *
 * Nonce is 12 bytes: four zero bytes followed by the 8-byte LITTLE-endian counter
 * (pyatv chacha20.py, _PACK_NONCE_WITH_4_BYTE_PAD / _pad_nonce).
 */
class HapSession(outputKey: ByteArray, inputKey: ByteArray) {

    companion object {
        private const val FRAME_LENGTH = 1024
        private const val AUTH_TAG_LENGTH = 16
        private const val TRANSFORMATION = "ChaCha20-Poly1305"
    }

    private val outKey = SecretKeySpec(outputKey, "ChaCha20")
    private val inKey = SecretKeySpec(inputKey, "ChaCha20")
    private var outCounter = 0L
    private var inCounter = 0L

    private fun nonce(counter: Long): ByteArray {
        val n = ByteArray(12) // first 4 bytes stay zero
        for (i in 0 until 8) n[4 + i] = ((counter shr (8 * i)) and 0xFF).toByte()
        return n
    }

    private fun lengthPrefix(len: Int): ByteArray =
        byteArrayOf((len and 0xFF).toByte(), ((len shr 8) and 0xFF).toByte())

    /** Encrypts a whole message into one or more framed blocks, ready to write. */
    fun encrypt(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var offset = 0
        while (offset < data.size) {
            val frameLen = minOf(FRAME_LENGTH, data.size - offset)
            val frame = data.copyOfRange(offset, offset + frameLen)
            val prefix = lengthPrefix(frameLen)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, outKey, IvParameterSpec(nonce(outCounter)))
            cipher.updateAAD(prefix)
            val sealed = cipher.doFinal(frame)

            out.write(prefix)
            out.write(sealed)
            outCounter += 1
            offset += frameLen
        }
        return out.toByteArray()
    }

    /**
     * Decrypts one framed block. [prefix] is the raw 2-byte length header (also the
     * AAD); [blockWithTag] is the ciphertext plus its 16-byte auth tag.
     */
    fun decryptFrame(prefix: ByteArray, blockWithTag: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, inKey, IvParameterSpec(nonce(inCounter)))
        cipher.updateAAD(prefix)
        val plain = cipher.doFinal(blockWithTag)
        inCounter += 1
        return plain
    }

    /** Total wire size of a frame whose length prefix says [plaintextLength]. */
    fun frameBodySize(plaintextLength: Int): Int = plaintextLength + AUTH_TAG_LENGTH
}
