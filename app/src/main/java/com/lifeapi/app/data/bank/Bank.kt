package com.lifeapi.app.data.bank

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lifeapi.app.LifeApp
import com.lifeapi.app.data.Account
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Hue
import com.lifeapi.app.data.LifeRepository
import com.lifeapi.app.data.Txn
import com.lifeapi.app.data.money.MoneyOps
import com.lifeapi.app.data.money.TRANSFER
import com.lifeapi.app.data.money.looksLikeTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

enum class BankPhase { NotConfigured, Disconnected, Authorizing, Connected, Syncing, Expired, Offline, RateLimited, Error }

data class BankStatus(
    val phase: BankPhase,
    val serverUrl: String = "",
    val appKey: String = "",
    val aspsp: String = "Swedbank",
    val country: String = "LV",
    val validUntil: Long = 0,
    val lastSync: Long = 0,
    val lastCount: Int = 0,
    val message: String = "",
) {
    val connected: Boolean get() = phase in setOf(BankPhase.Connected, BankPhase.Syncing, BankPhase.Offline, BankPhase.RateLimited, BankPhase.Error)
}

/**
 * Read-only bank sync through Enable Banking via the user's own proxy (server/bank-proxy).
 *
 * Accounts are keyed by a stable id ("eb:<IBAN>" or "eb:<identification hash>") because Enable Banking account uids
 * change with every new session — reconnecting never duplicates accounts. Transactions are upserted by extId;
 * pending ones are replaced on every sync, so pending → booked never duplicates. Bank balances overwrite local ones;
 * synced transactions never move balances themselves.
 */
object Bank {
    /** Unattended (background) refreshes are limited by banks (~4/day) — keep auto-sync well under that. */
    const val AUTO_SYNC_MS = 6 * 60 * 60_000L
    const val FIRST_SYNC_DAYS = 89
    private const val OVERLAP_DAYS = 3

    private lateinit var app: Context
    private lateinit var store: BankStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _status = MutableStateFlow(BankStatus(BankPhase.NotConfigured))
    val status: StateFlow<BankStatus> = _status.asStateFlow()

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        store = BankStore(app)
        publish()
    }

    private val repo: LifeRepository get() = (app as LifeApp).container.repo
    private fun snack(text: String) = (app as LifeApp).container.snack(text)
    private fun client() = BankClient(store.serverUrl, store.appKey)

    private fun publish(phase: BankPhase? = null, message: String? = null) {
        val now = System.currentTimeMillis()
        val base = when {
            store.serverUrl.isBlank() || store.appKey.isBlank() -> BankPhase.NotConfigured
            store.sessionId.isNotBlank() -> when {
                store.errorKind == "expired" || (store.validUntil in 1 until now) -> BankPhase.Expired
                store.errorKind == "offline" -> BankPhase.Offline
                store.errorKind == "rate" -> BankPhase.RateLimited
                store.errorKind in setOf("api", "config") -> BankPhase.Error
                else -> BankPhase.Connected
            }
            store.pendingState.isNotBlank() -> BankPhase.Authorizing
            else -> BankPhase.Disconnected
        }
        _status.value = BankStatus(
            phase ?: base, store.serverUrl, store.appKey, store.aspspName, store.country, store.validUntil,
            store.lastSync, store.lastCount, message ?: store.errorText,
        )
    }

    // ------------------------------------------------------------------ setup

    fun configure(serverUrl: String, appKey: String) {
        store.serverUrl = BankClient.normalizeUrl(serverUrl)
        store.appKey = appKey.trim()
        publish()
    }

    /** Checks the proxy is reachable and fully configured. Returns a human message. */
    suspend fun testServer(): String = runCatching {
        val h = client().health()
        if (h.optBoolean("configured")) "Server OK" else "Server reachable, but its Enable Banking secrets aren't set"
    }.getOrElse { e -> if (BankClient.isNetwork(e)) "Can't reach ${store.serverUrl}: ${e.javaClass.simpleName} ${e.message ?: ""}" else "Server error: ${e.message}" }

    suspend fun banks(country: String): List<Pair<String, String>> {
        val arr = client().aspsps(country)
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { it.optString("name") to it.optString("country") }
    }

    /** Max consent for this bank in days (from /aspsps maximum_consent_validity, seconds), capped at 180. */
    private suspend fun maxConsentDays(name: String, country: String): Int = runCatching {
        val arr = client().aspsps(country)
        (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.optString("name") == name }
            ?.optLong("maximum_consent_validity", 0L)?.takeIf { it > 0 }?.let { (it / 86_400).toInt() }
    }.getOrNull()?.coerceIn(1, 180) ?: 90

    /** Step 1: returns the bank login URL to open in the browser. */
    suspend fun startAuth(aspspName: String, country: String): String {
        store.aspspName = aspspName; store.country = country
        val days = maxConsentDays(aspspName, country)
        val state = UUID.randomUUID().toString()
        val r = client().auth(aspspName, country, days, state)
        store.pendingState = state
        store.pendingValidUntil = parseTs(r.optString("valid_until")) ?: (System.currentTimeMillis() + days * 86_400_000L)
        publish()
        return r.getString("url")
    }

    fun cancelAuth() { store.pendingState = ""; publish() }

    /** Deep link lifeapi://bank/callback?code=…&state=… — returns true when it was ours. */
    fun handleCallback(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "lifeapi" || uri.host != "bank") return false
        if (!::store.isInitialized) return false
        val err = uri.getQueryParameter("error")
        if (err != null) {
            store.pendingState = ""
            publish(BankPhase.Disconnected, "Bank access not granted: ${uri.getQueryParameter("error_description") ?: err}")
            return true
        }
        val code = uri.getQueryParameter("code") ?: return true
        val state = uri.getQueryParameter("state")
        if (store.pendingState.isNotBlank() && state != null && state != store.pendingState) {
            publish(message = "Ignored a sign-in that this phone didn't start (state mismatch)")
            return true
        }
        scope.launch { finish(code) }
        return true
    }

    /** Step 2: exchange the code for a session, create/link accounts, first sync. */
    suspend fun finish(code: String) = mutex.withLock {
        publish(BankPhase.Syncing, "Connecting…")
        try {
            val s = client().createSession(code.trim())
            store.sessionId = s.getString("session_id")
            store.validUntil = s.optJSONObject("access")?.optString("valid_until")?.let(::parseTs) ?: store.pendingValidUntil
            store.pendingState = ""
            store.errorKind = ""; store.errorText = ""
            linkAccounts(s.optJSONArray("accounts") ?: JSONArray())
        } catch (e: Throwable) {
            fail(e); return@withLock
        }
        syncLocked(present = true)
    }

    private suspend fun linkAccounts(accounts: JSONArray) {
        val dao = repo.db.bank()
        val map = mutableMapOf<String, String>()
        for (i in 0 until accounts.length()) {
            val a = accounts.getJSONObject(i)
            val uid = a.optString("uid")
            if (uid.isBlank()) continue
            val iban = a.optJSONObject("account_id")?.optString("iban")?.takeIf { it.isNotBlank() && it != "null" }
            val hash = a.optString("identification_hash").takeIf { it.isNotBlank() && it != "null" }
            val ext = "eb:" + (iban ?: hash ?: uid)
            map[uid] = ext
            // "XXX" = ISO "no currency" — Swedbank LV sends it on multi-currency accounts; the money is EUR
            val currency = a.optString("currency").takeIf { it.length == 3 && it != "null" && it != "XXX" } ?: "EUR"
            // the bank's "name" is often the holder ("JOHN SMITH") — prefer product, else "Swedbank ••1234"
            val tail = iban?.let { " ••" + it.takeLast(4) } ?: ""
            val product = a.optString("product").takeIf { it.isNotBlank() && it != "null" }
            val name = product?.let { "$it$tail" } ?: "${store.aspspName.ifBlank { "Bank" }}$tail"
            val existing = dao.accountByExt(ext)
            if (existing == null) {
                dao.insertAccount(Account(name = name, balanceCents = 0, hue = Hue.MONEY, sort = 100, extId = ext, source = "Bank", iban = iban, currency = currency))
            } else {
                val holderName = existing.name == existing.name.uppercase() && existing.name.any { it.isLetter() }
                dao.updateAccount(existing.copy(source = "Bank", iban = iban ?: existing.iban, currency = currency, name = if (holderName) name else existing.name))
            }
        }
        store.accountMap = map
    }

    // ------------------------------------------------------------------ sync

    /** Manual "Sync now" (user present — banks allow more). */
    fun syncNow() { scope.launch { mutex.withLock { syncLocked(present = true) } } }

    /** Called when Money opens: at most every 6 h, only while connected and consent valid. */
    fun maybeAutoSync() {
        if (!::store.isInitialized) return
        val st = _status.value.phase
        if (st !in setOf(BankPhase.Connected, BankPhase.Offline)) return
        if (System.currentTimeMillis() - store.lastAttempt < AUTO_SYNC_MS) return
        scope.launch { mutex.withLock { syncLocked(present = false) } }
    }

    private suspend fun syncLocked(present: Boolean) {
        if (store.sessionId.isBlank() || store.accountMap.isEmpty()) { publish(); return }
        if (store.validUntil in 1 until System.currentTimeMillis()) {
            store.errorKind = "expired"; store.errorText = "Bank consent ended — reconnect to keep syncing"
            publish(); return
        }
        store.lastAttempt = System.currentTimeMillis()
        publish(BankPhase.Syncing, "Syncing…")
        val c = client()
        val dao = repo.db.bank()
        var total = 0
        val failures = mutableListOf<String>()
        for ((uid, ext) in store.accountMap) {
            val acc = dao.accountByExt(ext) ?: continue
            try {
                // balance — a failing balance call must not block transactions
                var balErr: Throwable? = null
                val bal = try { pickBalance(c.balances(uid, present)) } catch (e: Throwable) {
                    if (e is BankApiException && (e.consentGone || e.rateLimited || e.badAppKey)) throw e
                    if (BankClient.isNetwork(e)) throw e
                    balErr = e; null
                }
                // transactions
                val today = Days.today()
                val last = dao.lastBookedDay(acc.id)
                val fromDay = if (last == null) today - FIRST_SYNC_DAYS else (last - OVERLAP_DAYS).coerceAtLeast(today - FIRST_SYNC_DAYS)
                val rows = mutableListOf<JSONObject>()
                var cont: String? = null
                var pages = 0
                // Some banks reject long or explicit ranges ("error interacting with ASPSP"): fall back to shorter windows.
                var from = fromDay
                val windows = listOf(fromDay, maxOf(fromDay, today - 30), maxOf(fromDay, today - 7)).distinct()
                var lastErr: Throwable? = null
                for (w in windows) {
                    rows.clear(); cont = null; pages = 0
                    try {
                        do {
                            val (arr, next) = c.transactions(uid, LocalDate.ofEpochDay(w).toString(), LocalDate.ofEpochDay(today).toString(), cont, present)
                            for (i in 0 until arr.length()) rows += arr.getJSONObject(i)
                            cont = next; pages++
                        } while (cont != null && pages < 50)
                        from = w; lastErr = null; break
                    } catch (e: Throwable) {
                        if (e is BankApiException && (e.consentGone || e.rateLimited || e.badAppKey)) throw e
                        if (BankClient.isNetwork(e)) throw e
                        lastErr = e
                    }
                }
                lastErr?.let { throw RuntimeException("transactions: ${it.message}", it) }
                total += apply(acc, ext, rows, from)
                balErr?.let { failures += "${acc.name} balance: ${it.message}" }
                val fresh = dao.accountByExt(ext) ?: acc
                dao.updateAccount(fresh.copy(balanceCents = bal ?: fresh.balanceCents, lastSync = System.currentTimeMillis()))
            } catch (e: Throwable) {
                if (e is BankApiException && (e.consentGone || e.rateLimited || e.badAppKey)) { fail(e); return }
                if (BankClient.isNetwork(e)) { fail(e); return }
                failures += "${acc.name}: ${e.message}"
            }
        }
        runCatching { MoneyOps(repo).snapshot() }
        store.lastSync = System.currentTimeMillis()
        store.lastCount = total
        if (failures.isEmpty()) { store.errorKind = ""; store.errorText = "" } else { store.errorKind = "api"; store.errorText = failures.joinToString("; ") }
        publish()
        if (present) snack(if (failures.isEmpty()) "Bank synced · $total transaction${if (total == 1) "" else "s"}" else "Bank sync: some accounts failed")
    }

    /** Upsert one account's transactions. Returns how many rows were new or changed. */
    private suspend fun apply(acc: Account, ext: String, rows: List<JSONObject>, fromDay: Long): Int {
        val dao = repo.db.bank()
        val cat = com.lifeapi.app.data.money.Categorizer.load(repo)
        val own = repo.dao.accountsNow()
        val ownIbans = own.mapNotNull { it.iban }.filter { it != acc.iban }
        var changed = 0
        val seen = HashMap<String, Int>()
        repo.db.withTransaction {
            dao.deletePending(acc.id, fromDay)
            for (t in rows) {
                val status = t.optString("status", "BOOK")
                if (status in setOf("CNCL", "RJCT")) continue
                val pending = status == "PDNG" || status == "HOLD" || status == "SCHD"
                val day = listOf("booking_date", "value_date", "transaction_date").firstNotNullOfOrNull { k ->
                    t.optString(k).takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)).toEpochDay() }.getOrNull() }
                } ?: continue
                val amt = t.optJSONObject("transaction_amount") ?: continue
                val abs = cents(amt.optString("amount")) ?: continue
                val debit = t.optString("credit_debit_indicator") == "DBIT"
                val signed = if (debit) -kotlin.math.abs(abs) else kotlin.math.abs(abs)
                val party = (if (debit) t.optJSONObject("creditor") else t.optJSONObject("debtor"))?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }
                val remit = t.optJSONArray("remittance_information")?.let { a -> (0 until a.length()).joinToString(" ") { a.optString(it) } }?.trim().orEmpty()
                val btc = t.optJSONObject("bank_transaction_code")?.optString("description")?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
                val extra = t.optString("note").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
                val mcc = t.optString("merchant_category_code").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
                val cleaned = com.lifeapi.app.data.money.Merchant.clean(party ?: remit)
                val name = (party?.let { p -> if (com.lifeapi.app.data.money.Recategorize.looksRaw(p)) cleaned.ifBlank { p } else p }
                    ?: cleaned.ifBlank { null }
                    ?: remit.ifBlank { null }
                    ?: if (debit) "Card / payment" else "Incoming").take(80)
                val merchantKey = com.lifeapi.app.data.money.Merchant.key(name)
                val rawText = listOf(party.orEmpty(), remit, btc, extra).filter { it.isNotBlank() }.joinToString(" · ")
                val counterIban = (if (debit) t.optJSONObject("creditor_account") else t.optJSONObject("debtor_account"))?.optString("iban")
                val ref = listOf("entry_reference", "transaction_id").firstNotNullOfOrNull { k -> t.optString(k).takeIf { it.isNotBlank() && it != "null" } }
                val key = if (ref != null && !pending) ref else {
                    val h = sha("$day|$signed|$name|$remit|$debit")
                    val n = (seen[h] ?: 0) + 1; seen[h] = n
                    (if (pending) "p:" else "h:") + h + if (n > 1) "#$n" else ""
                }
                val extId = "$ext:$key"
                val transfer = (counterIban != null && counterIban in ownIbans) || looksLikeTransfer("$name $remit", own.map { it.name })
                val category = if (transfer) TRANSFER else cat.category(merchantKey, "$name $rawText", signed, mcc) ?: if (signed > 0) "Income" else "Other"
                val existing = dao.txnByExt(extId)
                if (existing == null) {
                    dao.insertTxn(
                        Txn(day = day, ts = Days.tsOf(day, LocalTime.NOON), name = name, category = category, amountCents = signed,
                            accountId = acc.id, note = rawText.take(300), extId = extId, pending = pending, merchant = merchantKey, mcc = mcc),
                    )
                    changed++
                } else {
                    // bank-owned fields always; name/category only while the user hasn't chosen a category
                    var u = existing.copy(day = day, ts = Days.tsOf(day, LocalTime.NOON), amountCents = signed, pending = pending, accountId = acc.id, mcc = mcc)
                    if (!existing.userCategory) u = u.copy(
                        category = category, merchant = merchantKey, note = rawText.take(300),
                        name = if (existing.name == name || com.lifeapi.app.data.money.Recategorize.looksRaw(existing.name)) name else existing.name,
                    )
                    if (u != existing) { dao.updateTxn(u); changed++ }
                }
            }
        }
        return changed
    }

    /** Prefer available/booked interim, then closing balances. */
    private fun pickBalance(arr: JSONArray): Long? {
        val list = (0 until arr.length()).map { arr.getJSONObject(it) }
        val order = listOf("ITAV", "CLAV", "ITBD", "CLBD", "XPCD", "OPAV", "OPBD", "OTHR", "INFO")
        val b = order.firstNotNullOfOrNull { t -> list.firstOrNull { it.optString("balance_type") == t } } ?: list.firstOrNull() ?: return null
        return cents(b.optJSONObject("balance_amount")?.optString("amount"))
    }

    // ------------------------------------------------------------------ disconnect

    /** Ends the consent at the bank (best effort) and forgets the session. [removeTxns] deletes synced rows. */
    suspend fun disconnect(removeTxns: Boolean) = mutex.withLock {
        val id = store.sessionId
        if (id.isNotBlank()) runCatching { client().deleteSession(id) }
        val dao = repo.db.bank()
        repo.db.withTransaction {
            if (removeTxns) dao.deleteAllBankTxns()
            dao.unlinkAll()
        }
        runCatching { MoneyOps(repo).snapshot() }
        store.clearSession()
        store.lastSync = 0; store.lastCount = 0
        publish()
        snack(if (removeTxns) "Bank disconnected · synced transactions removed" else "Bank disconnected · history kept")
    }

    suspend fun bankTxnCount(): Int = repo.db.bank().bankTxnCount()

    // ------------------------------------------------------------------ helpers

    private fun fail(e: Throwable) {
        when {
            e is BankApiException && e.consentGone -> { store.errorKind = "expired"; store.errorText = "Bank consent ended or was revoked — reconnect" }
            e is BankApiException && e.rateLimited -> { store.errorKind = "rate"; store.errorText = "The bank's daily limit was reached — try again later" }
            e is BankApiException && e.badAppKey -> { store.errorKind = "config"; store.errorText = "Server rejected the app key — check it in Bank settings" }
            e is BankApiException -> { store.errorKind = "api"; store.errorText = e.message ?: "Bank error ${e.code}" }
            BankClient.isNetwork(e) -> { store.errorKind = "offline"; store.errorText = "No connection — will retry" }
            else -> { store.errorKind = "api"; store.errorText = e.message ?: e.toString() }
        }
        if (store.sessionId.isBlank()) {
            // failed during connect: keep the session empty, show the error
            publish(BankPhase.Error, store.errorText)
        } else publish()
    }

    private fun cents(s: String?): Long? =
        s?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { BigDecimal(it).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull() }

    private fun parseTs(s: String?): Long? =
        s?.takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
}
