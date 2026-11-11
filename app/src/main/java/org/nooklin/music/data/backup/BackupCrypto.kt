package org.nooklin.music.data.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts the settings backup with a password: PBKDF2 (HMAC-SHA256) makes the key, AES-256-GCM encrypts and
 * authenticates. File layout: magic, salt (16), iv (12), ciphertext with tag. A wrong password or a changed file fails
 * the GCM check and gives null.
 */
object BackupCrypto {
    private val MAGIC = "NOOKLINMUSICBK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val ITERATIONS = 210_000

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray? {
        if (data.size < MAGIC.size + SALT_SIZE + IV_SIZE + 16) return null
        if (!data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_SIZE)
        val iv = data.copyOfRange(MAGIC.size + SALT_SIZE, MAGIC.size + SALT_SIZE + IV_SIZE)
        val body = data.copyOfRange(MAGIC.size + SALT_SIZE + IV_SIZE, data.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC)
            cipher.doFinal(body)
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(PBEKeySpec(password, salt, ITERATIONS, 256)).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
