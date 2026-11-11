package org.nooklin.music.airplay.raop.auth

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ChaCha20-Poly1305 AEAD with HAP's fixed-label nonce convention: the standard
 * cipher needs a 12-byte nonce, but HAP pairing messages use an 8-byte ASCII
 * label (e.g. "PV-Msg02") left-padded with 4 zero bytes to reach 12 bytes.
 *
 * Padding direction verified against pyatv's chacha20.py
 * (Chacha20Cipher._pad_nonce: zero bytes are prepended, not appended) rather
 * than assumed - getting this backwards would silently produce wrong
 * ciphertext with no error until the receiver rejects it.
 *
 * Uses Android's built-in javax.crypto "ChaCha20-Poly1305" transformation
 * (available well below our minSdk 36 - Android's Conscrypt provider has
 * supported it since Android 9/API 28).
 */
object HapChaCha20Poly1305 {
    private const val TRANSFORMATION = "ChaCha20-Poly1305"

    private fun nonceFor(label: String): ByteArray {
        val labelBytes = label.toByteArray(Charsets.US_ASCII)
        require(labelBytes.size == 8) { "HAP nonce labels are always 8 ASCII bytes" }
        return ByteArray(4) + labelBytes // 4 zero bytes prepended, per verified reference
    }

    fun encrypt(key: ByteArray, nonceLabel: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonceFor(nonceLabel)))
        return cipher.doFinal(plaintext) // ciphertext || 16-byte Poly1305 tag
    }

    fun decrypt(key: ByteArray, nonceLabel: String, ciphertextWithTag: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonceFor(nonceLabel)))
        return cipher.doFinal(ciphertextWithTag)
    }
}
