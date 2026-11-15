package com.lifeapi.app.data.bank

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** HTTP error from the proxy or Enable Banking. [code] = HTTP status, [error] = Enable Banking error code if any. */
class BankApiException(val code: Int, val error: String?, message: String) : Exception(message) {
    /** Consent gone: session expired / revoked / unknown → user must reconnect. */
    val consentGone: Boolean
        get() = error != null && Regex("EXPIRED|REVOKED|SESSION|ACCESS_DENIED|UNAUTHORIZED|CONSENT|NOT_AUTHORIZED", RegexOption.IGNORE_CASE).containsMatchIn(error) ||
            code == 401
    val rateLimited: Boolean get() = code == 429 || (error?.contains("RATE_LIMIT", true) == true)
    val badAppKey: Boolean get() = code == 403 && error == null && message?.contains("Forbidden") == true
}

/** Talks only to the user's own bank proxy (server/bank-proxy). No Enable Banking secrets on the phone. */
class BankClient(baseUrl: String, private val appKey: String) {
    private val base = normalizeUrl(baseUrl)

    private suspend fun call(method: String, path: String, body: JSONObject? = null, present: Boolean = false): String = withContext(Dispatchers.IO) {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 45_000
            c.setRequestProperty("accept", "application/json")
            c.setRequestProperty("x-app-key", appKey)
            if (present) c.setRequestProperty("x-psu-present", "1")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("content-type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code >= 400) c.errorStream else c.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code >= 400) {
                val j = runCatching { JSONObject(text) }.getOrNull()
                val err = j?.optString("error")?.takeIf { it.isNotBlank() && it != "null" }
                val msg = j?.optString("message")?.takeIf { it.isNotBlank() } ?: "HTTP $code"
                throw BankApiException(code, err, msg)
            }
            text
        } finally {
            c.disconnect()
        }
    }

    suspend fun health(): JSONObject = JSONObject(call("GET", "/health"))

    suspend fun aspsps(country: String = "LV"): JSONArray =
        JSONObject(call("GET", "/aspsps?country=${enc(country)}")).optJSONArray("aspsps") ?: JSONArray()

    suspend fun auth(aspspName: String, country: String, validDays: Int, state: String): JSONObject =
        JSONObject(call("POST", "/auth", JSONObject().put("aspsp_name", aspspName).put("country", country).put("valid_days", validDays).put("state", state), present = true))

    suspend fun createSession(code: String): JSONObject = JSONObject(call("POST", "/session", JSONObject().put("code", code), present = true))

    suspend fun session(id: String): JSONObject = JSONObject(call("GET", "/session/${enc(id)}"))

    suspend fun deleteSession(id: String) { call("DELETE", "/session/${enc(id)}", present = true) }

    suspend fun details(uid: String, present: Boolean): JSONObject = JSONObject(call("GET", "/accounts/${enc(uid)}/details", present = present))

    suspend fun balances(uid: String, present: Boolean): JSONArray =
        JSONObject(call("GET", "/accounts/${enc(uid)}/balances", present = present)).optJSONArray("balances") ?: JSONArray()

    /** One page of transactions. Returns (transactions, continuation_key or null). */
    suspend fun transactions(uid: String, from: String, to: String, cont: String?, present: Boolean): Pair<JSONArray, String?> {
        val q = buildString {
            append("?date_from=").append(enc(from)).append("&date_to=").append(enc(to))
            if (!cont.isNullOrBlank()) append("&continuation_key=").append(enc(cont))
        }
        val j = JSONObject(call("GET", "/accounts/${enc(uid)}/transactions$q", present = present))
        val next = j.optString("continuation_key").takeIf { it.isNotBlank() && it != "null" }
        return (j.optJSONArray("transactions") ?: JSONArray()) to next
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        fun isNetwork(e: Throwable) = e is IOException

        /** Accepts "lifeapi-bank.x.workers.dev", "https://…/health", stray spaces etc. → "https://host[/path]". */
        fun normalizeUrl(raw: String): String {
            var u = raw.trim().replace(Regex("\\s+"), "")
            if (u.isEmpty()) return u
            if (!Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(u)) u = "https://$u"
            u = u.trimEnd('/')
            for (suffix in listOf("/health", "/callback")) if (u.endsWith(suffix, ignoreCase = true)) u = u.dropLast(suffix.length)
            return u.trimEnd('/')
        }
    }
}
