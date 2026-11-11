package org.nooklin.music.data.server

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// Separate from the appearance settings: this holds the account for the user's own server
private val Context.serverDataStore by preferencesDataStore(name = "nooklin_music_server")

private val NAME_KEY = stringPreferencesKey("server_name")
private val PRIMARY_URL_KEY = stringPreferencesKey("primary_url")
private val SECONDARY_URL_KEY = stringPreferencesKey("secondary_url")
private val USERNAME_KEY = stringPreferencesKey("username")
private val ALLOW_HTTP_KEY = booleanPreferencesKey("allow_http")
private val PASSWORD_KEY = stringPreferencesKey("password_encrypted")

data class ServerConfig(
    // The name the user gives the server, shown as the title of the home screen
    val name: String = "",
    val primaryUrl: String = "",
    val secondaryUrl: String = "",
    val username: String = "",
    val allowHttp: Boolean = false,
    // Stays encrypted here. Decrypted only when a request is signed.
    val encryptedPassword: String? = null
) {
    val hasPassword: Boolean get() = !encryptedPassword.isNullOrEmpty()
}

class ServerStore(private val context: Context) {

    val config: Flow<ServerConfig> = context.serverDataStore.data.map { prefs ->
        ServerConfig(
            name = prefs[NAME_KEY] ?: "",
            primaryUrl = prefs[PRIMARY_URL_KEY] ?: "",
            secondaryUrl = prefs[SECONDARY_URL_KEY] ?: "",
            username = prefs[USERNAME_KEY] ?: "",
            allowHttp = prefs[ALLOW_HTTP_KEY] ?: false,
            encryptedPassword = prefs[PASSWORD_KEY]
        )
    }

    /** [newPassword] null keeps the password that is already saved. */
    suspend fun save(
        name: String,
        primaryUrl: String,
        secondaryUrl: String,
        username: String,
        allowHttp: Boolean,
        newPassword: String?
    ) {
        // Keystore work off the main thread
        val encrypted = if (newPassword != null) {
            withContext(Dispatchers.IO) { PasswordVault.encrypt(newPassword) }
        } else {
            null
        }
        context.serverDataStore.edit { prefs ->
            prefs[NAME_KEY] = name
            prefs[PRIMARY_URL_KEY] = primaryUrl
            prefs[SECONDARY_URL_KEY] = secondaryUrl
            prefs[USERNAME_KEY] = username
            prefs[ALLOW_HTTP_KEY] = allowHttp
            if (encrypted != null) prefs[PASSWORD_KEY] = encrypted
        }
    }
}
