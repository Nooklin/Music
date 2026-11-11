package org.nooklin.music.airplay.raop.auth

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * SRP-6a client-side session, used for HAP "transient" pairing (RFC 2945 / RFC 5054).
 *
 * Algorithm ported directly from the srptools open-source library
 * (github.com/idlesign/srptools, srptools/context.py + client.py) - formulas and
 * hash-input ordering match that verified reference exactly:
 *   k = H(N | PAD(g))
 *   x = H(s | H(I : P))
 *   A = g^a % N
 *   u = H(PAD(A) | PAD(B))
 *   S = (B - k*g^x) ^ (a + u*x) % N
 *   K = H(S)
 *   M = H(H(N) XOR H(g) | H(I) | s | A | B | K)
 *   HAMK = H(A | M | K)
 *
 * HAP pairing (per pyatv's hap_srp.py) uses username "Pair-Setup", SHA-512 as H,
 * and the RFC 5054 3072-bit group (see SrpGroup3072).
 */
class Srp6aClient(
    private val username: String,
    private val password: String,
    private val clientPrivate: BigInteger = BigInteger(256, SecureRandom())
) {
    private val N = SrpGroup3072.N
    private val g = SrpGroup3072.g
    // k = H(N | PAD(g)) -- N minimal-encoded, g zero-padded to prime width (srptools:
    // self.hash(self._prime, self.pad(self._gen))). Note the asymmetry: it is real.
    private val k: BigInteger = hashToInt(toUnsignedBytes(N), padded(g))

    val clientPublic: BigInteger = g.modPow(clientPrivate, N) // A

    lateinit var sessionKey: ByteArray // K
        private set
    lateinit var clientProof: ByteArray // M
        private set
    private lateinit var expectedServerProof: ByteArray // H(A|M|K)

    /** Processes the server's salt + public key (B) and derives the session key + proof. */
    fun process(serverSalt: ByteArray, serverPublic: BigInteger) {
        require(serverPublic.mod(N) != BigInteger.ZERO) { "Invalid server public key (B mod N == 0)" }

        val x = hashToInt(serverSalt, sha512(usernameColonPassword()))
        val u = hashToInt(padded(clientPublic), padded(serverPublic))

        val base = serverPublic.subtract(k.multiply(g.modPow(x, N))).mod(N)
        val exponent = clientPrivate.add(u.multiply(x))
        val premasterSecret = base.modPow(exponent, N) // S

        sessionKey = sha512(toUnsignedBytes(premasterSecret)) // K = H(S)

        // CRITICAL ENCODING NOTE: the reference implementation (srptools) converts
        // integers to bytes with int_to_bytes(), which is MINIMAL-LENGTH big-endian
        // (no zero padding). Only k = H(N | PAD(g)) and u = H(PAD(A) | PAD(B)) use the
        // zero-PADDED form. The proof below must use the MINIMAL form throughout, and
        // intermediate hash values are treated as integers before re-encoding.
        // Padding these uniformly produces a valid-looking but wrong proof, which the
        // receiver rejects with HAP error code 2 (authentication failure).
        val hN = BigInteger(1, sha512(toUnsignedBytes(N)))
        val hg = BigInteger(1, sha512(toUnsignedBytes(g))) // H(0x05) -- a single byte, NOT padded
        val hXor = toUnsignedBytes(hN.xor(hg))
        val hUser = toUnsignedBytes(BigInteger(1, sha512(username.toByteArray(Charsets.UTF_8))))

        val md = MessageDigest.getInstance("SHA-512")
        md.update(hXor)
        md.update(hUser)
        md.update(serverSalt)
        md.update(toUnsignedBytes(clientPublic))
        md.update(toUnsignedBytes(serverPublic))
        md.update(sessionKey)
        clientProof = md.digest() // M

        val md2 = MessageDigest.getInstance("SHA-512")
        md2.update(toUnsignedBytes(clientPublic))
        md2.update(clientProof)
        md2.update(sessionKey)
        expectedServerProof = md2.digest() // HAMK = H(A | M | K)
    }

    fun verifyServerProof(serverProof: ByteArray): Boolean =
        MessageDigest.isEqual(serverProof, expectedServerProof)

    private fun usernameColonPassword(): ByteArray =
        "$username:$password".toByteArray(Charsets.UTF_8)

    private fun padded(v: BigInteger): ByteArray {
        val raw = toUnsignedBytes(v)
        if (raw.size >= SrpGroup3072.byteLength) return raw
        val out = ByteArray(SrpGroup3072.byteLength)
        System.arraycopy(raw, 0, out, out.size - raw.size, raw.size)
        return out
    }

    private fun toUnsignedBytes(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
    }

    private fun sha512(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-512")
        parts.forEach { md.update(it) }
        return md.digest()
    }

    private fun hashToInt(vararg parts: ByteArray): BigInteger =
        BigInteger(1, sha512(*parts))
}
