package org.nooklin.music.airplay.raop.auth

import java.math.BigInteger

/**
 * The 3072-bit MODP group from RFC 5054 ("Using SRP for TLS Authentication"),
 * Appendix A. This is a published IETF standard constant - not a
 * reverse-engineered value - used by HAP (HomeKit/AirPlay 2) pairing.
 *
 * Value verified against the srptools open-source library
 * (github.com/idlesign/srptools, srptools/constants.py: PRIME_3072 / PRIME_3072_GEN)
 * rather than transcribed from memory.
 */
object SrpGroup3072 {
    val N: BigInteger = BigInteger(
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA6" +
            "3B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
            "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F2411" +
            "7C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
            "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08" +
            "CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
            "DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D" +
            "04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
            "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D8760273" +
            "3EC86A64521F2B18177B200CBBE117577A615D6C770988C0BAD946E208E24FA074E5AB31" +
            "43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF",
        16
    )
    val g: BigInteger = BigInteger.valueOf(5)

    // Byte width of N, used for zero-padding values before hashing (PAD()) -
    // required by the SRP spec so H(PAD(A)|PAD(B)) etc. are computed consistently.
    val byteLength: Int = (N.bitLength() + 7) / 8
}
