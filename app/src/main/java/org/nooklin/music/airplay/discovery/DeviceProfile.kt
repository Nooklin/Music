package org.nooklin.music.airplay.discovery

/**
 * Works out what a discovered receiver actually IS, from its mDNS TXT records -
 * before any connection is made.
 *
 * Two things matter to the user:
 *  - what kind of device it is (icon)
 *  - which AirPlay generation it speaks, because that sets expectations: an
 *    AirPlay 1-only device cannot be driven by this app at all, and the Apple TV
 *    needs a PIN where the AirPort Expresses do not.
 */
object DeviceProfile {

    /** AirPlay capability, derived from the `ft` feature bits. */
    enum class Generation(val label: String) {
        AIRPLAY_2("AirPlay 2"),
        AIRPLAY_1("AirPlay 1"),
        UNKNOWN("AirPlay")
    }

    /** How this device expects to be paired, from the `sf`/`flags` status bits. */
    enum class PairingStyle {
        /** Pairs without user interaction - the AirPort Expresses (sf=0x4). */
        TRANSIENT,
        /** Shows a PIN on screen that must be typed in - Apple TV (sf contains 0x644). */
        PIN_REQUIRED,
        UNKNOWN
    }

    /** What sort of device it is, for the icon. */
    enum class Kind { GENERIC, AIRPORT, APPLE_TV, HOMEPOD, COMPUTER }

    data class Profile(
        val kind: Kind,
        val generation: Generation,
        val pairing: PairingStyle,
        val modelName: String
    ) {
        /** Short line shown under the device name. */
        fun subtitle(): String = buildString {
            append(generation.label)
            if (pairing == PairingStyle.PIN_REQUIRED) append(" · needs PIN")
        }
    }

    /**
     * `ft` is two comma-separated 32-bit halves, low first: "0x445D0A00,0x1C340".
     * Bit 38 (SupportsUnifiedMediaControl) or bit 48
     * (SupportsCoreUtilsPairingAndEncryption) mean the device speaks AirPlay 2.
     * The model string is NOT reliable for this - an AirPort Express on 7.8.1
     * firmware is an AirPlay 2 device despite being from 2012.
     */
    private fun generationFrom(features: String?): Generation {
        if (features.isNullOrBlank()) return Generation.UNKNOWN
        return try {
            val parts = features.split(",")
            val low = java.lang.Long.parseLong(parts[0].removePrefix("0x").trim(), 16)
            val high = if (parts.size > 1)
                java.lang.Long.parseLong(parts[1].removePrefix("0x").trim(), 16) else 0L
            val full = (high shl 32) or (low and 0xFFFFFFFFL)
            val unifiedMediaControl = (full shr 38) and 1L == 1L
            val coreUtilsPairing = (full shr 48) and 1L == 1L
            if (unifiedMediaControl || coreUtilsPairing) Generation.AIRPLAY_2
            else Generation.AIRPLAY_1
        } catch (e: Exception) {
            Generation.UNKNOWN
        }
    }

    /**
     * `sf` (RAOP) / `flags` (AirPlay) are status flags. A device requiring on-screen
     * PIN pairing carries 0x644; the AirPort Expresses report plain 0x4 and accept
     * transient (PIN-less) pairing.
     */
    private fun pairingFrom(statusFlags: String?): PairingStyle {
        if (statusFlags.isNullOrBlank()) return PairingStyle.UNKNOWN
        return try {
            val value = java.lang.Long.parseLong(statusFlags.removePrefix("0x").trim(), 16)
            if ((value and 0x644L) == 0x644L) PairingStyle.PIN_REQUIRED
            else PairingStyle.TRANSIENT
        } catch (e: Exception) {
            PairingStyle.UNKNOWN
        }
    }

    private fun kindFor(model: String?): Kind = when {
        model == null -> Kind.GENERIC
        model.startsWith("AirPort", true) -> Kind.AIRPORT
        model.startsWith("AppleTV", true) -> Kind.APPLE_TV
        model.startsWith("AudioAccessory", true) -> Kind.HOMEPOD
        model.startsWith("Mac", true) ||
            model.startsWith("iMac", true) ||
            model.startsWith("Book", true) -> Kind.COMPUTER
        else -> Kind.GENERIC
    }

    /** Friendly name for the hardware, since raw model strings are opaque. */
    private fun friendlyModel(model: String?): String = when {
        model == null -> "AirPlay speaker"
        model.startsWith("AirPort10", true) -> "AirPort Express"
        model.startsWith("AirPort", true) -> "AirPort"
        model.startsWith("AppleTV", true) -> "Apple TV"
        model.startsWith("AudioAccessory", true) -> "HomePod"
        model.startsWith("Mac", true) -> "Mac"
        else -> model
    }

    /**
     * Stable key for storing per-device state (credentials, volume).
     *
     * Prefers the hardware deviceid from the _airplay._tcp record; falls back to the
     * service name only if that is missing. Defined in ONE place so the save path and
     * the load path can never disagree - they previously each computed it inline.
     */
    fun keyFor(receiver: AirPlayReceiver): String =
        receiver.txtRecords["deviceid"]
            ?: receiver.txtRecords["pk"]?.take(32)
            ?: receiver.name

    fun of(receiver: AirPlayReceiver): Profile {
        val txt = receiver.txtRecords
        // The RAOP record uses am/ft/sf; the _airplay record uses model/features/flags.
        val model = txt["am"] ?: txt["model"]
        val features = txt["ft"] ?: txt["features"]
        val statusFlags = txt["sf"] ?: txt["flags"]

        return Profile(
            kind = kindFor(model),
            generation = generationFrom(features),
            pairing = pairingFrom(statusFlags),
            modelName = friendlyModel(model)
        )
    }
}
