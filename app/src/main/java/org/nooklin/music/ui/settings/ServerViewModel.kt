package org.nooklin.music.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.music.data.server.AddressCheck
import org.nooklin.music.data.server.PasswordVault
import org.nooklin.music.data.server.PingResult
import org.nooklin.music.data.server.Reply
import org.nooklin.music.data.server.ServerSession
import org.nooklin.music.data.server.ServerStore
import org.nooklin.music.data.server.SubsonicClient

data class AddressTestResult(val label: String, val result: PingResult)

/** What the saved server answers right now: whether it is reachable, how fast, and some numbers about it. */
data class ServerLive(
    val checking: Boolean = false,
    val online: Boolean = false,
    val responseMs: Long = 0,
    val type: String = "",
    val version: String = "",
    val apiVersion: String = "",
    val error: String? = null,
    // null: the server did not tell
    val songs: Int? = null,
    val albums: Int? = null,
    val artists: Int? = null,
    val playlists: Int? = null,
    val heartSongs: Int? = null,
    val heartAlbums: Int? = null,
    val heartArtists: Int? = null,
    val checkedAt: Long = 0
)

data class ServerUiState(
    val live: ServerLive = ServerLive(),
    val loaded: Boolean = false,
    val name: String = "",
    val primaryUrl: String = "",
    val secondaryUrl: String = "",
    val username: String = "",
    // Only what the user typed this time. The saved password is never put back into the field.
    val password: String = "",
    val allowHttp: Boolean = false,
    val hasSavedPassword: Boolean = false,
    val testing: Boolean = false,
    val results: List<AddressTestResult> = emptyList(),
    val error: String? = null,
    val info: String? = null
)

class ServerViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ServerStore(application)
    private var savedEncryptedPassword: String? = null

    private val _state = MutableStateFlow(ServerUiState())
    val state: StateFlow<ServerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = store.config.first()
            savedEncryptedPassword = saved.encryptedPassword
            _state.update {
                it.copy(
                    loaded = true,
                    name = saved.name,
                    primaryUrl = saved.primaryUrl,
                    secondaryUrl = saved.secondaryUrl,
                    username = saved.username,
                    allowHttp = saved.allowHttp,
                    hasSavedPassword = saved.hasPassword
                )
            }
            if (saved.hasPassword && saved.primaryUrl.isNotBlank()) refreshLive()
        }
    }

    /** Asks the saved server for its state and a few numbers. Each number that fails is simply left out. */
    fun refreshLive() {
        if (_state.value.live.checking) return
        _state.update { it.copy(live = it.live.copy(checking = true)) }
        viewModelScope.launch {
            val session = ServerSession.get(getApplication<Application>())
            val started = android.os.SystemClock.elapsedRealtime()
            val ping = session.call("ping")
            val ms = android.os.SystemClock.elapsedRealtime() - started
            if (ping !is Reply.Ok) {
                _state.update { it.copy(live = ServerLive(online = false, error = (ping as Reply.Fail).message, checkedAt = System.currentTimeMillis())) }
                return@launch
            }
            val root = ping.value
            var live = ServerLive(
                online = true, responseMs = ms,
                type = root.optString("type"), version = root.optString("serverVersion"), apiVersion = root.optString("version"),
                checkedAt = System.currentTimeMillis()
            )
            _state.update { it.copy(live = live.copy(checking = true)) }
            (session.call("getArtists") as? Reply.Ok)?.let { r ->
                val indexes = r.value.optJSONObject("artists")?.optJSONArray("index")
                var artists = 0
                var albums = 0
                for (i in 0 until (indexes?.length() ?: 0)) {
                    val list = indexes!!.getJSONObject(i).optJSONArray("artist") ?: continue
                    for (j in 0 until list.length()) { artists++; albums += list.getJSONObject(j).optInt("albumCount") }
                }
                live = live.copy(artists = artists, albums = albums.takeIf { it > 0 })
            }
            (session.call("getScanStatus") as? Reply.Ok)?.let { r ->
                val count = r.value.optJSONObject("scanStatus")?.optInt("count", 0) ?: 0
                if (count > 0) live = live.copy(songs = count)
            }
            (session.call("getPlaylists") as? Reply.Ok)?.let { r ->
                live = live.copy(playlists = r.value.optJSONObject("playlists")?.optJSONArray("playlist")?.length() ?: 0)
            }
            (session.call("getStarred2") as? Reply.Ok)?.let { r ->
                val json = r.value.optJSONObject("starred2")
                live = live.copy(
                    heartSongs = json?.optJSONArray("song")?.length() ?: 0,
                    heartAlbums = json?.optJSONArray("album")?.length() ?: 0,
                    heartArtists = json?.optJSONArray("artist")?.length() ?: 0
                )
            }
            _state.update { it.copy(live = live.copy(checking = false)) }
        }
    }

    fun onNameChange(value: String) = edit { it.copy(name = value) }
    fun onPrimaryUrlChange(value: String) = edit { it.copy(primaryUrl = value) }
    fun onSecondaryUrlChange(value: String) = edit { it.copy(secondaryUrl = value) }
    fun onUsernameChange(value: String) = edit { it.copy(username = value) }
    fun onPasswordChange(value: String) = edit { it.copy(password = value) }
    fun onAllowHttpChange(value: Boolean) = edit { it.copy(allowHttp = value) }

    // Any edit makes earlier messages and test results stale
    private fun edit(change: (ServerUiState) -> ServerUiState) {
        _state.update { change(it).copy(error = null, info = null, results = emptyList()) }
    }

    fun save() {
        val current = _state.value
        val problem = validate(current)
        if (problem != null) {
            _state.update { it.copy(error = problem, info = null) }
            return
        }
        viewModelScope.launch {
            store.save(
                name = current.name.trim(),
                primaryUrl = current.primaryUrl.trim(),
                secondaryUrl = current.secondaryUrl.trim(),
                username = current.username.trim(),
                allowHttp = current.allowHttp,
                newPassword = current.password.ifEmpty { null }
            )
            val saved = store.config.first()
            savedEncryptedPassword = saved.encryptedPassword
            _state.update {
                it.copy(password = "", hasSavedPassword = saved.hasPassword, error = null, info = "Saved.")
            }
            refreshLive()
        }
    }

    /** Tests what is in the form, saved or not. Every filled-in address is tested on its own. */
    fun test() {
        val current = _state.value
        if (current.testing) return
        val problem = validate(current)
        if (problem != null) {
            _state.update { it.copy(error = problem, info = null) }
            return
        }
        _state.update { it.copy(testing = true, results = emptyList(), error = null, info = null) }
        viewModelScope.launch {
            val results: List<AddressTestResult>? = withContext(Dispatchers.IO) {
                val password = current.password.ifEmpty { savedEncryptedPassword?.let { PasswordVault.decrypt(it) } }
                if (password.isNullOrEmpty()) {
                    null
                } else {
                    val client = SubsonicClient(current.allowHttp)
                    val username = current.username.trim()
                    buildList {
                        add(AddressTestResult(MAIN_LABEL, client.ping(current.primaryUrl, username, password)))
                        if (current.secondaryUrl.isNotBlank()) {
                            add(AddressTestResult(SECOND_LABEL, client.ping(current.secondaryUrl, username, password)))
                        }
                    }
                }
            }
            _state.update {
                if (results == null) {
                    it.copy(testing = false, error = "The saved password could not be read. Enter it again.")
                } else {
                    it.copy(testing = false, results = results)
                }
            }
        }
    }

    private fun validate(s: ServerUiState): String? {
        val main = SubsonicClient.parseAddress(s.primaryUrl, s.allowHttp)
        if (main is AddressCheck.Invalid) return "Main address: ${main.reason}"
        if (s.secondaryUrl.isNotBlank()) {
            val second = SubsonicClient.parseAddress(s.secondaryUrl, s.allowHttp)
            if (second is AddressCheck.Invalid) return "Second address: ${second.reason}"
        }
        if (s.username.isBlank()) return "Enter a username."
        if (s.password.isEmpty() && !s.hasSavedPassword) return "Enter a password."
        return null
    }

    companion object {
        const val MAIN_LABEL = "Main address"
        const val SECOND_LABEL = "Second address"
    }
}
