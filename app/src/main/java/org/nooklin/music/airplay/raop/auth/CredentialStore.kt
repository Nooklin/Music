package org.nooklin.music.airplay.raop.auth

import android.content.Context
import android.util.Base64
import android.util.Log

/**
 * Persists HAP pairing credentials per device.
 *
 * PIN pairing is meant to happen ONCE. Without storage the user would be asked for the
 * code on every connection, which is exactly the friction the long-term keys exist to
 * avoid.
 *
 * Stored per device id (the mDNS deviceid, stable across IP changes).
 */
class CredentialStore(context: Context) {

    companion object {
        private const val TAG = "AirSyncCreds"
        private const val PREFS = "nooklin_airplay_credentials"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun enc(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun dec(s: String) = Base64.decode(s, Base64.NO_WRAP)

    fun save(deviceKey: String, creds: HapPinPairing.Credentials, name: String? = null) {
        val editor = prefs.edit()
            .putString("$deviceKey.ltpk", enc(creds.devicePublicKey))
            .putString("$deviceKey.ltsk", enc(creds.clientPrivateKey))
            .putString("$deviceKey.pub", enc(creds.clientPublicKey))
            .putString("$deviceKey.devid", creds.deviceIdentifier)
            .putString("$deviceKey.clientid", creds.clientIdentifier)
        if (name != null) editor.putString("$deviceKey.name", name)
        editor.apply()
    }

    fun load(deviceKey: String): HapPinPairing.Credentials? {
        val ltpk = prefs.getString("$deviceKey.ltpk", null) ?: return null
        val ltsk = prefs.getString("$deviceKey.ltsk", null) ?: return null
        val pub = prefs.getString("$deviceKey.pub", null) ?: return null
        val devId = prefs.getString("$deviceKey.devid", null) ?: return null
        val clientId = prefs.getString("$deviceKey.clientid", null) ?: return null
        return HapPinPairing.Credentials(
            devicePublicKey = dec(ltpk),
            clientPrivateKey = dec(ltsk),
            clientPublicKey = dec(pub),
            deviceIdentifier = devId,
            clientIdentifier = clientId
        )
    }

    fun has(deviceKey: String): Boolean = prefs.contains("$deviceKey.ltpk")

    /** Used when a device rejects stored credentials, so the user can re-pair. */
    fun clear(deviceKey: String) {
        prefs.edit()
            .remove("$deviceKey.ltpk").remove("$deviceKey.ltsk").remove("$deviceKey.pub")
            .remove("$deviceKey.devid").remove("$deviceKey.clientid").remove("$deviceKey.name")
            .apply()
    }
}
