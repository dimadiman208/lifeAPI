package com.lifeapi.app.data.fatsecret

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** What went wrong, in terms the UI can act on. */
sealed class FsError(message: String) : Exception(message) {
    /** No network / server unreachable. */
    class Offline(msg: String) : FsError(msg)
    /** FatSecret rejected the user's access token (code 9) — needs Reconnect. */
    class Expired(msg: String) : FsError(msg)
    /** Proxy misconfigured: missing consumer key/secret, wrong app key, bad URL. */
    class Config(msg: String) : FsError(msg)
    /** Any other FatSecret / proxy error. */
    class Api(msg: String) : FsError(msg)
}

/** Talks to the user's own proxy (server/fatsecret-proxy). The consumer secret never touches the phone. */
class FatSecretClient(private val baseUrl: String, private val appKey: String) {

    data class Tokens(val token: String, val secret: String)
    data class Start(val authorizeUrl: String, val token: String, val secret: String)

    suspend fun start(): Start {
        val o = post("/oauth/start", JSONObject())
        return Start(o.getString("authorize_url"), o.getString("token"), o.optString("token_secret"))
    }

    suspend fun access(requestToken: String, requestSecret: String, verifier: String): Tokens {
        val o = post("/oauth/access", JSONObject().put("token", requestToken).put("token_secret", requestSecret).put("verifier", verifier))
        return Tokens(o.getString("token"), o.getString("token_secret"))
    }

    /** Signed FatSecret call through the proxy (method-based server.api, format=json). */
    suspend fun api(access: Tokens, params: Map<String, String>): JSONObject {
        val p = JSONObject()
        params.forEach { (k, v) -> p.put(k, v) }
        p.put("format", "json")
        return post("/api", JSONObject().put("token", access.token).put("token_secret", access.secret).put("params", p))
    }

    /** Public food database call (foods.search, food.get…) — no user token; the proxy signs 2-legged. */
    suspend fun public(params: Map<String, String>): JSONObject {
        val p = JSONObject()
        params.forEach { (k, v) -> p.put(k, v) }
        p.put("format", "json")
        return post("/public", JSONObject().put("params", p))
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val url = try { URL(baseUrl.trimEnd('/') + path) } catch (e: Exception) { throw FsError.Config("Server URL is not valid") }
        val c = (url.openConnection() as HttpURLConnection)
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json; charset=utf-8")
            c.setRequestProperty("accept", "application/json")
            if (appKey.isNotBlank()) c.setRequestProperty("x-app-key", appKey)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream ?: c.inputStream).bufferedReader().use { it.readText() }
            val json = try { JSONObject(text) } catch (e: Exception) {
                throw if (code in 200..299) FsError.Api("Unexpected reply: ${text.take(120)}")
                else FsError.Config("Server replied HTTP $code${if (code == 404) " — check the server URL" else ""}")
            }
            json.optJSONObject("error")?.let { throw mapError(it, code) }
            if (code !in 200..299) throw FsError.Api("HTTP $code")
            json
        } catch (e: FsError) {
            throw e
        } catch (e: UnknownHostException) {
            throw FsError.Offline("No connection")
        } catch (e: ConnectException) {
            throw FsError.Offline("Server unreachable")
        } catch (e: SocketTimeoutException) {
            throw FsError.Offline("Timed out")
        } catch (e: IOException) {
            throw FsError.Offline(e.message ?: "Network error")
        } finally {
            c.disconnect()
        }
    }

    private fun mapError(e: JSONObject, http: Int): FsError {
        val msg = e.optString("message").ifBlank { "FatSecret error" }
        return when (val code = e.opt("code")) {
            // FatSecret OAuth 1.0 errors: 9 = invalid/revoked access token. 8 = invalid consumer key.
            9, "9" -> FsError.Expired(msg)
            8, "8" -> FsError.Config("Server's FatSecret key was rejected: $msg")
            "app_key" -> FsError.Config("Wrong app key")
            "server_config" -> FsError.Config(msg)
            else -> if (http == 401) FsError.Config(msg) else FsError.Api(if (code != null) "$msg (code $code)" else msg)
        }
    }
}

/** FatSecret JSON returns single objects instead of arrays in some places, and "" / null for empty lists. */
internal fun JSONObject.arrayOrSingle(name: String): List<JSONObject> {
    val v = opt(name) ?: return emptyList()
    return when (v) {
        is JSONArray -> (0 until v.length()).mapNotNull { v.optJSONObject(it) }
        is JSONObject -> listOf(v)
        else -> emptyList()
    }
}

internal fun JSONObject.num(name: String): Double = optString(name).replace(',', '.').toDoubleOrNull() ?: 0.0
