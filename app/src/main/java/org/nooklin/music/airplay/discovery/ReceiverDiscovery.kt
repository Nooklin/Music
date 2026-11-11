package org.nooklin.music.airplay.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "ReceiverDiscovery"

// AirPlay receivers advertise two SEPARATE mDNS services, each with its OWN port:
//   _raop._tcp    -> the RTSP/RTP audio endpoint we stream to
//   _airplay._tcp -> the pairing/control HTTP endpoint (NOT the same port as RAOP -
//                    confirmed against pyatv's protocols/airplay/__init__.py, which
//                    resolves this service's own mdns_service.port independently
//                    rather than reusing the RAOP port or a fixed constant like 7000)
// We resolve both and correlate them by host IP, since a single physical receiver
// advertises both under the same address.
private const val SERVICE_TYPE_RAOP = "_raop._tcp"
private const val SERVICE_TYPE_AIRPLAY = "_airplay._tcp"

data class AirPlayReceiver(
    val name: String,
    val host: String,
    val raopPort: Int,
    // Null until the matching _airplay._tcp record for this host has also been
    // resolved. Pairing needs this -- it will not work over raopPort.
    val airplayPort: Int? = null,
    val txtRecords: Map<String, String> = emptyMap()
)

/**
 * Emits a running, deduplicated list of discovered AirPlay receivers on the local network.
 * Caller must hold a MulticastLock while this flow is active (mDNS relies on multicast,
 * which is asleep by default on Android to save battery).
 */
class ReceiverDiscovery(private val context: Context) {

    /**
     * The resolved host address.
     *
     * getHost() was deprecated in API 34 in favour of getHostAddresses(), which returns
     * every valid address for the service (all sharing the same port). minSdk here is
     * 33, so the old call is still needed as a fallback.
     */
    private fun hostAddressOf(info: NsdServiceInfo): String? {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            info.hostAddresses.firstOrNull()?.hostAddress?.let { return it }
        }
        @Suppress("DEPRECATION")
        return info.host?.hostAddress
    }

    /** Decodes the mDNS TXT attributes into readable strings. */
    private fun readTxtRecords(info: NsdServiceInfo): Map<String, String> {
        return try {
            info.attributes.mapValues { (_, v) ->
                v?.toString(Charsets.UTF_8) ?: ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the speaker records")
            emptyMap()
        }
    }

    /** The Wi-Fi network that is not a VPN, or null to let Android choose. */
    private fun wifiNetwork(): android.net.Network? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.allNetworks.firstOrNull { n ->
                val c = cm.getNetworkCapabilities(n)
                c != null &&
                    c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) &&
                    !c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not find the Wi-Fi network")
            null
        }
    }

    fun discover(): Flow<List<AirPlayReceiver>> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        // _raop._tcp entries, keyed by service name (there can be several receivers).
        val raopByName = LinkedHashMap<String, AirPlayReceiver>()
        // _airplay._tcp ports, keyed by host IP, used to fill in airplayPort above.
        val airplayPortByHost = HashMap<String, Int>()
        // _airplay._tcp TXT records, also keyed by host. These carry deviceid, model,
        // features and flags - the RAOP record uses different, abbreviated keys and has
        // no deviceid at all, so discarding these left credentials keyed on a
        // renameable service name rather than stable hardware identity.
        val airplayTxtByHost = HashMap<String, Map<String, String>>()

        fun emitCurrent() {
            val combined = raopByName.values.map { r ->
                // Merge both record sets. RAOP keys (am/ft/sf) and AirPlay keys
                // (model/features/flags/deviceid) are both useful; AirPlay wins on
                // collision since its values are the fuller form.
                val merged = r.txtRecords + (airplayTxtByHost[r.host] ?: emptyMap())
                r.copy(
                    airplayPort = airplayPortByHost[r.host] ?: r.airplayPort,
                    txtRecords = merged
                )
            }
            trySend(combined)
        }

        // Android resolves only one service at a time: a second resolveService() while one is busy fails with
        // "already active" and that service would be lost. So the resolves go through a queue, one by one.
        val resolveQueue = ArrayDeque<Pair<NsdServiceInfo, (NsdServiceInfo) -> Unit>>()
        var resolving = false
        var closed = false

        fun resolveNext() {
            val next: Pair<NsdServiceInfo, (NsdServiceInfo) -> Unit>
            synchronized(resolveQueue) {
                if (closed || resolving) return
                next = resolveQueue.removeFirstOrNull() ?: return
                resolving = true
            }
            val (service, onResolved) = next
            fun done() {
                synchronized(resolveQueue) { resolving = false }
                resolveNext()
            }
            try {
                @Suppress("DEPRECATION")
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "Resolving a speaker failed: $errorCode")
                        // 3 = already active: try this one again later
                        if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                            synchronized(resolveQueue) { resolveQueue.addLast(service to onResolved) }
                        }
                        done()
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        onResolved(serviceInfo)
                        done()
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "Resolving a speaker threw an exception")
                done()
            }
        }

        fun enqueueResolve(service: NsdServiceInfo, onResolved: (NsdServiceInfo) -> Unit) {
            synchronized(resolveQueue) { resolveQueue.addLast(service to onResolved) }
            resolveNext()
        }

        fun makeDiscoveryListener(
            onFound: (NsdServiceInfo) -> Unit,
            onLost: (NsdServiceInfo) -> Unit
        ) = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                enqueueResolve(service, onFound)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                onLost(service)
            }

            override fun onDiscoveryStopped(serviceType: String) {
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed for $serviceType: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed for $serviceType: $errorCode")
            }
        }

        val raopListener = makeDiscoveryListener(
            onFound = { info ->
                val txt = readTxtRecords(info)
                if (txt.isNotEmpty()) {
                    // These declare what the device actually supports -- cn = codecs
                    // (0=PCM, 1=ALAC, 2=AAC, 3=AAC-ELD), et = encryption types,
                    // ft = feature bits, am = model. Decisions should key off these
                    // rather than assumptions about the hardware.
                }
                raopByName[info.serviceName] = AirPlayReceiver(
                    name = info.serviceName,
                    host = hostAddressOf(info) ?: "",
                    raopPort = info.port,
                    txtRecords = txt
                )
                emitCurrent()
            },
            onLost = { service ->
                raopByName.remove(service.serviceName)
                emitCurrent()
            }
        )

        val airplayListener = makeDiscoveryListener(
            onFound = { info ->
                val host = hostAddressOf(info)
                if (host != null) {
                    val txt = readTxtRecords(info)
                    if (txt.isNotEmpty()) {
                    }
                    airplayPortByHost[host] = info.port
                    if (txt.isNotEmpty()) airplayTxtByHost[host] = txt
                    emitCurrent()
                }
            },
            onLost = { /* port stays cached; harmless if stale between scans */ }
        )

        // Search on the Wi-Fi network itself: with a VPN or another default network the speakers are not on the
        // network Android would otherwise pick
        val wifi = wifiNetwork()
        // The main executor never shuts down: a pool closed while Android still has a callback queued crashed the app
        val executor = context.mainExecutor
        nsdManager.discoverServices(SERVICE_TYPE_RAOP, NsdManager.PROTOCOL_DNS_SD, wifi, executor, raopListener)
        nsdManager.discoverServices(SERVICE_TYPE_AIRPLAY, NsdManager.PROTOCOL_DNS_SD, wifi, executor, airplayListener)

        awaitClose {
            synchronized(resolveQueue) { closed = true; resolveQueue.clear() }
            for (listener in listOf(raopListener, airplayListener)) {
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (e: Exception) {
                    Log.w(TAG, "stopServiceDiscovery failed")
                }
            }
        }
    }
}
