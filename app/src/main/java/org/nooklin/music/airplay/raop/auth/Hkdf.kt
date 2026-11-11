package org.nooklin.music.airplay.raop.auth

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF (RFC 5869) using HMAC-SHA512, matching the salt/info convention HAP uses
 * (see pyatv hap_srp.py: hkdf_expand). This is a standard, publicly specified
 * two-step "extract-then-expand" KDF - not protocol-specific reverse-engineering,
 * safe to implement directly from the RFC rather than needing a verified-source port.
 */
object Hkdf {
    private const val ALGO = "HmacSHA512"
    private const val OUTPUT_LEN = 32 // HAP derives 32-byte keys

    fun deriveKey(salt: String, info: String, sharedSecret: ByteArray): ByteArray {
        val prk = hmac(salt.toByteArray(Charsets.UTF_8), sharedSecret) // extract
        val infoBytes = info.toByteArray(Charsets.UTF_8)
        val t1 = hmac(prk, infoBytes + byteOf(1)) // expand, single block covers 32 bytes (< hash len)
        return t1.copyOf(OUTPUT_LEN)
    }

    private fun byteOf(i: Int) = byteArrayOf(i.toByte())

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALGO)
        mac.init(SecretKeySpec(key, ALGO))
        return mac.doFinal(data)
    }
}
