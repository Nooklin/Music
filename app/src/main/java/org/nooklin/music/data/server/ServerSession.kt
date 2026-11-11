package org.nooklin.music.data.server

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.sync.withPermit
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.nooklin.music.data.library.CoverDisk
import org.json.JSONArray
import org.json.JSONObject

/**
 * The saved server as the rest of the app uses it. Reads the settings, decrypts the password once, and picks the
 * address that works: the last working one first, then the other. The main address is tried first again after the
 * settings change and whenever the app starts.
 */
class ServerSession private constructor(context: Context) {
    private val store = ServerStore(context)
    private val covers = CoverDisk.get(context)
    private val lock = Mutex()

    private var config: ServerConfig? = null
    private var password: String? = null
    private var clients: Pair<Boolean, SubsonicClient>? = null

    // Index into the list of addresses: 0 is the main address
    @Volatile
    private var active = 0

    suspend fun call(endpoint: String, params: List<Pair<String, String>> = emptyList()): Reply<JSONObject> {
        return withContext(Dispatchers.IO) {
            attempt { client, base, user, pw -> client.request(base, user, pw, endpoint, params) }
        }
    }

    /** A page of songs from Navidrome's own API, see [SubsonicClient.nativeSongs]. */
    suspend fun nativeSongs(query: List<Pair<String, String>>): Reply<JSONArray> {
        return withContext(Dispatchers.IO) {
            attempt { client, base, user, pw -> client.nativeSongs(base, user, pw, query) }
        }
    }

    /** Cover art as a bitmap, or null when there is none. [sizePx] is the longest edge asked from the server. */
    // 0.0.148: at most 4 covers are decoded at the same time (a fast scroll no longer starts dozens of decodes at once)
    private val decodeSlots = kotlinx.coroutines.sync.Semaphore(4)

    suspend fun cover(id: String, sizePx: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            // A saved copy first: no network, and the decode is the only work
            covers.get(id, sizePx)?.let { bytes ->
                decodeSlots.withPermit { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }?.let { return@withContext it }
            }
            val reply = attempt { client, base, user, pw ->
                client.image(base, user, pw, "getCoverArt", listOf("id" to id, "size" to sizePx.toString()))
            }
            if (reply is Reply.Ok) {
                val bitmap = decodeSlots.withPermit { BitmapFactory.decodeByteArray(reply.value, 0, reply.value.size) }
                if (bitmap != null) covers.put(id, sizePx, reply.value)
                bitmap
            } else {
                null
            }
        }
    }

    /**
     * A temporary address for the original file of a song, on the address that worked last. The address holds a login
     * token, so it is made when the player asks for it and is never saved.
     */
    suspend fun streamUrl(songId: String): String? {
        return withContext(Dispatchers.IO) {
            val setup = lock.withLock { prepare() }
            if (setup !is Setup.Ready) {
                null
            } else {
                val base = setup.addresses[active.coerceIn(0, setup.addresses.lastIndex)]
                setup.client.streamUrl(base, setup.username, setup.password, songId).toString()
            }
        }
    }

    /** Called after a playback failure on the network: switches to the other address. False when there is none. */
    fun tryOtherAddress(): Boolean {
        if (addressCount < 2) return false
        active = if (active == 0) 1 else 0
        return true
    }

    @Volatile
    private var addressCount = 1

    private suspend fun <T> attempt(block: (SubsonicClient, HttpUrl, String, String) -> Reply<T>): Reply<T> {
        val setup = lock.withLock { prepare() }
        if (setup is Setup.Problem) return Reply.Fail(setup.message, false)
        setup as Setup.Ready

        val order = setup.addresses.indices.sortedBy { if (it == active) 0 else 1 }
        val failures = mutableListOf<String>()
        for (index in order) {
            val reply = block(setup.client, setup.addresses[index], setup.username, setup.password)
            if (reply is Reply.Ok) {
                active = index
                return reply
            }
            reply as Reply.Fail
            // A real answer from the server (wrong password, not found) would be the same on the other address
            if (!reply.retryable) return reply
            failures += if (setup.addresses.size > 1) "${if (index == 0) "Main" else "Second"} address: ${reply.message}" else reply.message
        }
        return Reply.Fail(failures.joinToString(" "), true)
    }

    private sealed interface Setup {
        data class Problem(val message: String) : Setup
        data class Ready(
            val client: SubsonicClient,
            val addresses: List<HttpUrl>,
            val username: String,
            val password: String
        ) : Setup
    }

    private suspend fun prepare(): Setup {
        val saved = store.config.first()
        if (saved.primaryUrl.isBlank() || saved.username.isBlank() || !saved.hasPassword) {
            return Setup.Problem("No server set up yet. Open Settings, Server.")
        }
        if (saved != config) {
            config = saved
            password = PasswordVault.decrypt(saved.encryptedPassword.orEmpty())
            active = 0
        }
        val pw = password
            ?: return Setup.Problem("The saved password could not be read. Enter it again in Settings, Server.")

        val addresses = mutableListOf<HttpUrl>()
        val main = SubsonicClient.parseAddress(saved.primaryUrl, saved.allowHttp)
        if (main is AddressCheck.Invalid) return Setup.Problem("Main address: ${main.reason}")
        addresses += (main as AddressCheck.Valid).url
        if (saved.secondaryUrl.isNotBlank()) {
            val second = SubsonicClient.parseAddress(saved.secondaryUrl, saved.allowHttp)
            if (second is AddressCheck.Invalid) return Setup.Problem("Second address: ${second.reason}")
            addresses += (second as AddressCheck.Valid).url
        }

        addressCount = addresses.size
        val existing = clients
        val client = if (existing != null && existing.first == saved.allowHttp) {
            existing.second
        } else {
            SubsonicClient(saved.allowHttp).also { clients = saved.allowHttp to it }
        }
        return Setup.Ready(client, addresses, saved.username, pw)
    }

    companion object {
        @Volatile
        private var instance: ServerSession? = null

        fun get(context: Context): ServerSession {
            return instance ?: synchronized(this) {
                instance ?: ServerSession(context.applicationContext).also { instance = it }
            }
        }
    }
}
