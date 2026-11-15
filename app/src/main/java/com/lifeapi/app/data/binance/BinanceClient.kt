package com.lifeapi.app.data.binance

import com.lifeapi.app.data.bank.BankClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Error from the proxy or Binance. [error]: BAD_KEY, RATE_LIMIT, RESTRICTED_LOCATION, CONFIG, … */
class BinanceApiException(val code: Int, val error: String?, message: String) : Exception(message) {
    val badKey: Boolean get() = error == "BAD_KEY"
    val badAppKey: Boolean get() = code == 403 && error == null
    val rateLimited: Boolean get() = error == "RATE_LIMIT" || code == 429 || code == 418
    val restricted: Boolean get() = error == "RESTRICTED_LOCATION" || code == 451
}

/** Talks only to the user's own read-only proxy (server/binance-proxy). No Binance secrets on the phone. */
class BinanceClient(baseUrl: String, private val appKey: String) {
    private val base = BankClient.normalizeUrl(baseUrl)

    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 45_000
            c.setRequestProperty("accept", "application/json")
            c.setRequestProperty("x-app-key", appKey)
            val code = c.responseCode
            val text = (if (code >= 400) c.errorStream else c.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code >= 400) {
                val j = runCatching { JSONObject(text) }.getOrNull()
                throw BinanceApiException(
                    code, j?.optString("error")?.takeIf { it.isNotBlank() && it != "null" },
                    j?.optString("message")?.takeIf { it.isNotBlank() } ?: "HTTP $code",
                )
            }
            JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    suspend fun health() = get("/health")
    suspend fun account() = get("/account")
    suspend fun funding() = get("/funding")
    suspend fun earn() = get("/earn")
    suspend fun prices(assets: Collection<String>) = get("/prices?assets=" + URLEncoder.encode(assets.joinToString(","), "UTF-8"))

    companion object {
        fun isNetwork(e: Throwable) = e is IOException
    }
}
