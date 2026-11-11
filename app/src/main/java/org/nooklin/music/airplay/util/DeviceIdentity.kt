package org.nooklin.music.airplay.util

import android.content.Context
import android.provider.Settings

/**
 * The name this phone presents to a receiver during pairing, so the receiver's own
 * confirmation UI shows something meaningful instead of a blank field - e.g. macOS's
 * "<name> wants to AirPlay. Accept?" prompt when its AirPlay Receiver is set to
 * "Anyone on the Same Network".
 *
 * Settings.Global.DEVICE_NAME is the same name shown in this phone's Bluetooth and
 * Wi-Fi Direct pairing screens, readable without any special permission since API 25,
 * which makes it the closest match for what a receiver's own pairing prompt expects.
 * Falls back to the model string on OEM builds that strip or don't set it.
 */
object DeviceIdentity {
    // Settings.Global.DEVICE_NAME exists in the framework but is marked @SystemApi on
    // some API levels, so it isn't safe to reference as a compile-time constant here -
    // using the underlying key string directly avoids a build failure if it's hidden
    // on a given SDK, at the cost of not getting a compiler-checked constant.
    private const val DEVICE_NAME_KEY = "device_name"

    fun name(context: Context): String {
        val deviceName = runCatching {
            Settings.Global.getString(context.contentResolver, DEVICE_NAME_KEY)
        }.getOrNull()
        return if (!deviceName.isNullOrBlank()) deviceName else (android.os.Build.MODEL ?: "Android")
    }
}
