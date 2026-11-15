package com.lifeapi.app.data.binance

import android.content.Context
import androidx.room.withTransaction
import com.lifeapi.app.LifeApp
import com.lifeapi.app.data.Account
import com.lifeapi.app.data.CryptoHolding
import com.lifeapi.app.data.Hue
import com.lifeapi.app.data.LifeRepository
import com.lifeapi.app.data.bank.BankClient
import com.lifeapi.app.data.money.MoneyOps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject

enum class BinancePhase { NotConfigured, Ready, Syncing, Synced, Offline, RateLimited, Error }

data class BinanceStatus(
    val phase: BinancePhase,
    val serverUrl: String = "",
    val appKey: String = "",
    val lastSync: Long = 0,
    val totalCents: Long = 0,
    val dust: Int = 0,
    val unpriced: String = "",
    val message: String = "",
) {
    val configured: Boolean get() = phase != BinancePhase.NotConfigured
}

/**
 * Read-only Binance balances through the user's own proxy (server/binance-proxy).
 *
 * Spot + Funding + Simple Earn are merged per asset and valued in EUR (X/EUR pair, else X/USDT ÷ EUR/USDT).
 * Holdings are mirrored into the crypto table with source = "Binance" (manual holdings untouched).
 * Net worth: one Account row "Binance" (source "Binance", balance = total EUR) — net worth stays "sum of accounts",
 * so Binance counts once; manual crypto holdings stay outside net worth, as before.
 */
object Binance {
    const val SOURCE = "Binance"
    const val ACCOUNT_EXT = "binance"
    const val AUTO_SYNC_MS = 5 * 60_000L
    private const val DUST_CENTS = 50L
    private val STABLE_USD = setOf("USDT", "USDC", "FDUSD", "TUSD", "BUSD", "DAI", "USDP")

    private lateinit var app: Context
    private lateinit var store: BinanceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _status = MutableStateFlow(BinanceStatus(BinancePhase.NotConfigured))
    val status: StateFlow<BinanceStatus> = _status.asStateFlow()

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        store = BinanceStore(app)
        publish()
    }

    private val repo: LifeRepository get() = (app as LifeApp).container.repo
    private fun snack(text: String) = (app as LifeApp).container.snack(text)
    private fun client() = BinanceClient(store.serverUrl, store.appKey)

    private fun publish(phase: BinancePhase? = null) {
        val base = when {
            store.serverUrl.isBlank() || store.appKey.isBlank() -> BinancePhase.NotConfigured
            store.errorKind == "offline" -> BinancePhase.Offline
            store.errorKind == "rate" -> BinancePhase.RateLimited
            store.errorKind.isNotBlank() -> BinancePhase.Error
            store.lastSync > 0 -> BinancePhase.Synced
            else -> BinancePhase.Ready
        }
        _status.value = BinanceStatus(
            phase ?: base, store.serverUrl, store.appKey, store.lastSync, store.totalCents, store.dustCount, store.unpriced, store.errorText,
        )
    }

    fun configure(serverUrl: String, appKey: String) {
        store.serverUrl = BankClient.normalizeUrl(serverUrl)
        store.appKey = appKey.trim()
        store.errorKind = ""; store.errorText = ""
        publish()
    }

    suspend fun testServer(): String = runCatching {
        val h = client().health()
        if (h.optBoolean("configured")) "Server OK" else "Server reachable, but its Binance secrets aren't set"
    }.getOrElse { e -> if (BinanceClient.isNetwork(e)) "Can't reach ${store.serverUrl}: ${e.javaClass.simpleName} ${e.message ?: ""}" else "Server error: ${e.message}" }

    /** Called on app resume / Money open: syncs when configured and the last attempt is ≥ 5 min old. */
    fun maybeAutoSync() {
        if (!::store.isInitialized) return
        if (_status.value.phase == BinancePhase.NotConfigured) return
        if (System.currentTimeMillis() - store.lastAttempt < AUTO_SYNC_MS) return
        sync(user = false)
    }

    fun sync(user: Boolean = true) {
        if (!::store.isInitialized || store.serverUrl.isBlank()) return
        scope.launch {
            if (!mutex.tryLock()) return@launch
            try { syncLocked(user) } finally { mutex.unlock() }
        }
    }

    private fun num(s: String?): Double = s?.toDoubleOrNull() ?: 0.0

    private suspend fun syncLocked(user: Boolean) {
        store.lastAttempt = System.currentTimeMillis()
        publish(BinancePhase.Syncing)
        try {
            val c = client()
            val qty = HashMap<String, Double>()
            fun add(asset: String, q: Double) { if (q > 0) qty[asset] = (qty[asset] ?: 0.0) + q }

            val earn = runCatching { c.earn() }.getOrNull()
            val earnOk = earn != null
            earn?.let { e ->
                for (k in listOf("flexible", "locked")) {
                    val a = e.optJSONArray(k) ?: JSONArray()
                    for (i in 0 until a.length()) a.getJSONObject(i).let { add(it.optString("asset"), num(it.optString("amount"))) }
                }
            }
            val spot = c.account().optJSONArray("balances") ?: JSONArray()
            for (i in 0 until spot.length()) {
                val b = spot.getJSONObject(i)
                var asset = b.optString("asset")
                // "LDBTC" = BTC in Simple Earn flexible; counted from /earn when that worked
                if (asset.startsWith("LD") && asset.length > 3) { if (earnOk) continue else asset = asset.removePrefix("LD") }
                add(asset, num(b.optString("free")) + num(b.optString("locked")))
            }
            runCatching { c.funding() }.getOrNull()?.optJSONArray("balances")?.let { f ->
                for (i in 0 until f.length()) f.getJSONObject(i).let {
                    add(it.optString("asset"), num(it.optString("free")) + num(it.optString("locked")) + num(it.optString("freeze")) + num(it.optString("withdrawing")))
                }
            }

            val prices = if (qty.isEmpty()) JSONObject() else c.prices(qty.keys).optJSONObject("prices") ?: JSONObject()
            fun px(sym: String, key: String) = prices.optJSONObject(sym)?.optString(key)?.toDoubleOrNull()?.takeIf { it > 0 }
            val eurUsdt = px("EURUSDT", "last"); val eurUsdtOpen = px("EURUSDT", "open") ?: eurUsdt
            /** (price now, price 24h ago) in EUR. */
            fun eurPrice(a: String): Pair<Double, Double>? = when {
                a == "EUR" -> 1.0 to 1.0
                px("${a}EUR", "last") != null -> px("${a}EUR", "last")!! to (px("${a}EUR", "open") ?: px("${a}EUR", "last")!!)
                a in STABLE_USD && eurUsdt != null -> 1 / eurUsdt to 1 / (eurUsdtOpen ?: eurUsdt)
                px("${a}USDT", "last") != null && eurUsdt != null ->
                    px("${a}USDT", "last")!! / eurUsdt to (px("${a}USDT", "open") ?: px("${a}USDT", "last")!!) / (eurUsdtOpen ?: eurUsdt)
                else -> null
            }

            val now = System.currentTimeMillis()
            val keep = mutableListOf<CryptoHolding>()
            var dust = 0
            val unpriced = mutableListOf<String>()
            for ((asset, q) in qty) {
                val p = eurPrice(asset)
                if (p == null) { unpriced += asset; continue }
                val cents = Math.round(p.first * 100)
                if (q * p.first * 100 < DUST_CENTS) { dust++; continue }
                keep += CryptoHolding(sym = asset, qty = q, avgBuyCents = 0, priceCents = cents, prevPriceCents = Math.round(p.second * 100), source = SOURCE, updatedTs = now)
            }
            val total = keep.sumOf { Math.round(it.qty * it.priceCents) }

            val money = repo.money
            val dao = repo.dao
            repo.db.withTransaction {
                val existing = money.crypto().first().filter { it.source == SOURCE }
                for (h in keep) {
                    val old = existing.firstOrNull { it.sym == h.sym }
                    money.upsertCrypto(if (old != null) h.copy(id = old.id) else h)
                }
                existing.filter { e -> keep.none { it.sym == e.sym } }.forEach { money.deleteCrypto(it) }
                val acc = repo.db.bank().accountByExt(ACCOUNT_EXT)
                if (acc == null) dao.insertAccount(Account(name = "Binance", balanceCents = total, hue = Hue.GROW, sort = 200, extId = ACCOUNT_EXT, source = SOURCE, currency = "EUR", lastSync = now, kind = "Crypto"))
                else dao.updateAccount(acc.copy(balanceCents = total, source = SOURCE, lastSync = now))
            }
            runCatching { MoneyOps(repo).snapshot() }

            store.totalCents = total; store.dustCount = dust; store.unpriced = unpriced.sorted().joinToString(", ")
            store.lastSync = now; store.errorKind = ""; store.errorText = ""
            publish()
            if (user) snack("Binance synced · ${keep.size} asset${if (keep.size == 1) "" else "s"}")
        } catch (e: Throwable) {
            when {
                e is BinanceApiException && e.badAppKey -> { store.errorKind = "config"; store.errorText = "Server rejected the app key — check it in Binance settings" }
                e is BinanceApiException && e.badKey -> { store.errorKind = "key"; store.errorText = e.message ?: "Binance rejected the API key" }
                e is BinanceApiException && e.rateLimited -> { store.errorKind = "rate"; store.errorText = "Binance rate limit — try again in a few minutes" }
                e is BinanceApiException && e.restricted -> { store.errorKind = "region"; store.errorText = e.message ?: "Binance refused the server's location" }
                e is BinanceApiException -> { store.errorKind = "api"; store.errorText = e.message ?: "Binance error ${e.code}" }
                BinanceClient.isNetwork(e) -> { store.errorKind = "offline"; store.errorText = "No connection — will retry" }
                else -> { store.errorKind = "api"; store.errorText = e.message ?: e.toString() }
            }
            publish()
            if (user) snack("Binance: ${store.errorText}")
        }
    }

    /** Removes Binance holdings + the Binance account and forgets the server settings. */
    fun disconnect() = scope.launch {
        mutex.lock()
        try {
            val money = repo.money
            repo.db.withTransaction {
                money.crypto().first().filter { it.source == SOURCE }.forEach { money.deleteCrypto(it) }
                repo.db.bank().accountByExt(ACCOUNT_EXT)?.let { repo.dao.deleteAccount(it) }
            }
            runCatching { MoneyOps(repo).snapshot() }
            store.clear()
            publish()
            snack("Binance disconnected")
        } finally { mutex.unlock() }
    }
}
