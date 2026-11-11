package org.nooklin.music.data.server

import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** What a successful `ping` tells us about the server. */
data class ServerInfo(
    val apiVersion: String,
    val serverType: String,
    val serverVersion: String
)

sealed interface PingResult {
    data class Success(val info: ServerInfo) : PingResult
    data class Failure(val message: String) : PingResult
}

/** The outcome of checking an address the user typed. */
sealed interface AddressCheck {
    data class Valid(val url: HttpUrl) : AddressCheck
    data class Invalid(val reason: String) : AddressCheck
}

/** Answer to a library request. [retryable] means another server address may still work. */
sealed interface Reply<out T> {
    data class Ok<T>(val value: T) : Reply<T>
    data class Fail(val message: String, val retryable: Boolean) : Reply<Nothing>
}

/**
 * Talks to a Subsonic compatible server (Navidrome). GET requests only.
 *
 * Login uses the token scheme from the Subsonic API: a random salt is sent as `s` and md5(password + salt) as `t`.
 * The password itself is never sent. [allowHttp] is the user's own switch: when it is off, the client refuses any
 * address that is not https, whatever the manifest allows.
 */
class SubsonicClient(private val allowHttp: Boolean) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(
            if (allowHttp) {
                listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT)
            } else {
                listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS)
            }
        )
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Blocking, call from a background thread. */
    fun ping(address: String, username: String, password: String): PingResult {
        val checked = parseAddress(address, allowHttp)
        if (checked is AddressCheck.Invalid) return PingResult.Failure(checked.reason)
        val base = (checked as AddressCheck.Valid).url

        val salt = newSalt()
        val url = base.newBuilder()
            .addPathSegments("rest/ping.view")
            .addQueryParameter("u", username)
            .addQueryParameter("t", token(password, salt))
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
            .build()
        return execute(url)
    }

    private fun execute(url: HttpUrl): PingResult {
        return try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    PingResult.Failure(
                        "The server answered HTTP ${response.code}. Check the address, " +
                            "including any sub-path when it runs behind a reverse proxy."
                    )
                } else {
                    parseResponse(body)
                }
            }
        } catch (e: UnknownHostException) {
            PingResult.Failure("Could not find that host. Check the spelling of the address.")
        } catch (e: InterruptedIOException) {
            // Covers connect, read and whole-call timeouts
            PingResult.Failure("The server did not answer in time.")
        } catch (e: SSLException) {
            PingResult.Failure(
                "The secure connection failed (${e.message ?: "TLS error"}). A self-signed certificate is not accepted."
            )
        } catch (e: UnknownServiceException) {
            PingResult.Failure("That address uses http. Switch on Allow http, or use https.")
        } catch (e: IOException) {
            PingResult.Failure("Connection failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun parseResponse(body: String): PingResult {
        val root = try {
            JSONObject(body).getJSONObject("subsonic-response")
        } catch (e: JSONException) {
            return PingResult.Failure("That address did not answer like a Subsonic server.")
        }
        if (root.optString("status") == "ok") {
            return PingResult.Success(
                ServerInfo(
                    apiVersion = root.optString("version"),
                    serverType = root.optString("type"),
                    serverVersion = root.optString("serverVersion")
                )
            )
        }
        val error = root.optJSONObject("error")
        val code = error?.optInt("code", 0) ?: 0
        val message = error?.optString("message").orEmpty()
        return PingResult.Failure(errorText(code, message))
    }

    private fun errorText(code: Int, message: String): String = when (code) {
        40 -> "Wrong username or password."
        41, 42, 43 -> "The server does not accept this login method (error $code)."
        50 -> "This user is not allowed to do that."
        70 -> "Not found on the server."
        else -> "The server refused the request (error $code${if (message.isNotEmpty()) ": $message" else ""})."
    }

    private fun buildUrl(
        base: HttpUrl,
        username: String,
        password: String,
        endpoint: String,
        params: List<Pair<String, String>>
    ): HttpUrl {
        val salt = newSalt()
        val builder = base.newBuilder()
            .addPathSegments("rest/$endpoint.view")
            .addQueryParameter("u", username)
            .addQueryParameter("t", token(password, salt))
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
        params.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }

    /** Blocking, call from a background thread. Returns the `subsonic-response` object when its status is ok. */
    fun request(
        base: HttpUrl,
        username: String,
        password: String,
        endpoint: String,
        params: List<Pair<String, String>> = emptyList()
    ): Reply<JSONObject> {
        return transport(buildUrl(base, username, password, endpoint, params)) { response ->
            val root = try {
                JSONObject(response.body.string()).getJSONObject("subsonic-response")
            } catch (e: JSONException) {
                return@transport Reply.Fail("That address did not answer like a Subsonic server.", true)
            }
            if (root.optString("status") == "ok") {
                Reply.Ok(root)
            } else {
                val error = root.optJSONObject("error")
                // An answer from the server itself, so another address would give the same answer
                Reply.Fail(errorText(error?.optInt("code", 0) ?: 0, error?.optString("message").orEmpty()), false)
            }
        }
    }

    /**
     * Navidrome's own API (not Subsonic): logs in with the same user, then reads a list of songs. Blocking.
     * [query] holds the paging and sorting, for example `_sort=playCount`.
     */
    fun nativeSongs(
        base: HttpUrl,
        username: String,
        password: String,
        query: List<Pair<String, String>>
    ): Reply<JSONArray> {
        val login = base.newBuilder().addPathSegments("auth/login").build()
        val body = JSONObject().put("username", username).put("password", password).toString()
            .toRequestBody("application/json".toMediaType())
        val token = transport<String>(login, Request.Builder().url(login).post(body).build()) { response ->
            val value = try {
                JSONObject(response.body.string()).optString("token")
            } catch (e: JSONException) {
                ""
            }
            if (value.isEmpty()) Reply.Fail("Navidrome did not give a login token.", false) else Reply.Ok(value)
        }
        if (token !is Reply.Ok) return token as Reply.Fail

        val url = base.newBuilder().addPathSegments("api/song").also { b ->
            query.forEach { (name, value) -> b.addQueryParameter(name, value) }
        }.build()
        val request = Request.Builder().url(url).get().header("x-nd-authorization", "Bearer ${token.value}").build()
        return transport<JSONArray>(url, request) { response ->
            try {
                Reply.Ok(JSONArray(response.body.string()))
            } catch (e: JSONException) {
                Reply.Fail("Navidrome's own API gave an answer the app does not understand.", false)
            }
        }
    }

    /** The address a player can open for the original file of a song. Nothing is requested here. */
    fun streamUrl(base: HttpUrl, username: String, password: String, songId: String): HttpUrl {
        // format=raw asks the server not to transcode
        return buildUrl(base, username, password, "stream", listOf("id" to songId, "format" to "raw"))
    }

    /** Blocking. Fetches an image such as cover art. */
    fun image(
        base: HttpUrl,
        username: String,
        password: String,
        endpoint: String,
        params: List<Pair<String, String>>
    ): Reply<ByteArray> {
        return transport(buildUrl(base, username, password, endpoint, params)) { response ->
            val type = response.header("Content-Type").orEmpty()
            if (type.startsWith("image/")) {
                Reply.Ok(response.body.bytes())
            } else {
                // The server answers a failed image request with an error document instead
                Reply.Fail("No image available.", false)
            }
        }
    }

    private fun <T> transport(url: HttpUrl, onSuccess: (okhttp3.Response) -> Reply<T>): Reply<T> =
        transport(url, Request.Builder().url(url).get().build(), onSuccess)

    private fun <T> transport(url: HttpUrl, request: Request, onSuccess: (okhttp3.Response) -> Reply<T>): Reply<T> {
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Reply.Fail(
                        "The server answered HTTP ${response.code}. Check the address, " +
                            "including any sub-path when it runs behind a reverse proxy.",
                        true
                    )
                } else {
                    onSuccess(response)
                }
            }
        } catch (e: UnknownHostException) {
            Reply.Fail("Could not find that host. Check the spelling of the address.", true)
        } catch (e: InterruptedIOException) {
            Reply.Fail("The server did not answer in time.", true)
        } catch (e: SSLException) {
            Reply.Fail("The secure connection failed (${e.message ?: "TLS error"}).", true)
        } catch (e: UnknownServiceException) {
            Reply.Fail("That address uses http. Switch on Allow http, or use https.", false)
        } catch (e: IOException) {
            Reply.Fail("Connection failed: ${e.message ?: e.javaClass.simpleName}", true)
        }
    }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "NooklinMusic"

        private val random = SecureRandom()

        fun newSalt(): String {
            val bytes = ByteArray(8)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        // md5(password + salt) as lowercase hex, as the Subsonic API defines it
        fun token(password: String, salt: String): String {
            val digest = MessageDigest.getInstance("MD5").digest((password + salt).toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        /** Checks what the user typed. Needs an explicit http:// or https://, nothing is guessed. */
        fun parseAddress(input: String, allowHttp: Boolean): AddressCheck {
            val text = input.trim()
            if (text.isEmpty()) return AddressCheck.Invalid("Enter an address.")
            val url = text.toHttpUrlOrNull()
                ?: return AddressCheck.Invalid("Start with https:// or http://, for example https://music.example.com")
            if (!url.isHttps && !allowHttp) {
                return AddressCheck.Invalid("http is switched off. Use https, or switch on Allow http below.")
            }
            return AddressCheck.Valid(url.newBuilder().query(null).fragment(null).build())
        }
    }
}
