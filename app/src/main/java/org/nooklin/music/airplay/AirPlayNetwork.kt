package org.nooklin.music.airplay

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import java.net.DatagramSocket
import java.net.Socket

/**
 * Sends all AirPlay traffic over the Wi-Fi network itself. With a VPN on, Android otherwise routes a new connection
 * through the VPN, which cannot reach the speakers on the local network (the connection times out).
 */
object AirPlayNetwork {
    private const val TAG = "NooklinAirPlay"

    @Volatile
    private var connectivity: ConnectivityManager? = null

    fun init(context: Context) {
        if (connectivity == null) {
            connectivity = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        }
    }

    /** The Wi-Fi network that is not a VPN, or null when there is none. */
    fun wifi(): Network? {
        val cm = connectivity ?: return null
        return try {
            cm.allNetworks.firstOrNull { n ->
                val c = cm.getNetworkCapabilities(n)
                c != null &&
                    c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not find the Wi-Fi network: ${e.javaClass.simpleName}")
            null
        }
    }

    fun bind(socket: Socket) {
        try { wifi()?.bindSocket(socket) } catch (e: Exception) { Log.w(TAG, "Could not bind socket to Wi-Fi: ${e.javaClass.simpleName}") }
    }

    fun bind(socket: DatagramSocket) {
        try { wifi()?.bindSocket(socket) } catch (e: Exception) { Log.w(TAG, "Could not bind socket to Wi-Fi: ${e.javaClass.simpleName}") }
    }
}
