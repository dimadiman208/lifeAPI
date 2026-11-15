package com.lifeapi.app.data.fatsecret

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import com.lifeapi.app.LifeApp
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.LifeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

enum class FsPhase { NotConfigured, Disconnected, Authorizing, Connected, Syncing, Expired, Offline, Error }

data class FsStatus(
    val phase: FsPhase,
    val serverUrl: String = "",
    val appKey: String = "",
    val lastSync: Long = 0,
    val lastSyncCount: Int = 0,
    val message: String = "",
    /** While syncing: entries processed so far. */
    val progress: Int = 0,
    val connectedTs: Long = 0,
) {
    val connected: Boolean get() = phase in setOf(FsPhase.Connected, FsPhase.Syncing, FsPhase.Offline, FsPhase.Error)
}

/**
 * FatSecret diary sync (3-legged OAuth 1.0 via the user's proxy). Mirrors the remote diary into local Meal rows
 * (source "FatSecret", extId "fs:<food_entry_id>") so it's viewable offline. Re-syncing a day replaces exactly
 * that day's FatSecret rows: new entries inserted, changed ones updated, removed ones deleted — never duplicated.
 */
object FatSecret {
    const val SOURCE = "FatSecret"

    private lateinit var app: Context
    private lateinit var store: FatSecretStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _status = MutableStateFlow(FsStatus(FsPhase.NotConfigured))
    val status: StateFlow<FsStatus> = _status.asStateFlow()

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        store = FatSecretStore(app)
        publish()
    }

    private val repo: LifeRepository get() = (app as LifeApp).container.repo

    private fun client() = FatSecretClient(store.serverUrl, store.appKey)

    /** Client for the public food database, or null when no proxy server is configured. */
    fun publicClient(): FatSecretClient? =
        if (!::store.isInitialized || store.serverUrl.isBlank()) null else FatSecretClient(store.serverUrl, store.appKey)

    private fun publish(phase: FsPhase? = null, message: String? = null, progress: Int = 0) {
        val base = when {
            store.serverUrl.isBlank() -> FsPhase.NotConfigured
            store.token.isNotBlank() -> when (store.lastErrorKind) {
                "expired" -> FsPhase.Expired
                "offline" -> FsPhase.Offline
                "api", "config" -> FsPhase.Error
                else -> FsPhase.Connected
            }
            store.pendingToken.isNotBlank() -> FsPhase.Authorizing
            else -> FsPhase.Disconnected
        }
        _status.value = FsStatus(
            phase = phase ?: base,
            serverUrl = store.serverUrl,
            appKey = store.appKey,
            lastSync = store.lastSync,
            lastSyncCount = store.lastSyncCount,
            message = message ?: store.lastError,
            progress = progress,
            connectedTs = store.connectedTs,
        )
    }

    fun configure(serverUrl: String, appKey: String) {
        val url = serverUrl.trim().let { if (it.isNotEmpty() && !it.startsWith("http")) "https://$it" else it }.trimEnd('/')
        if (url != store.serverUrl) store.clearTokens()
        store.serverUrl = url
        store.appKey = appKey
        publish()
    }

    // ------------------------------------------------------------------ OAuth

    /** Step 1+2: get a request token from the proxy and open FatSecret's approval page in the browser. */
    fun connect(onError: (String) -> Unit = {}) = scope.launch {
        if (store.serverUrl.isBlank()) { publish(FsPhase.NotConfigured, "Add your server URL first"); return@launch }
        try {
            val s = client().start()
            store.pendingToken = s.token
            store.pendingSecret = s.secret
            publish(FsPhase.Authorizing, "Approve LifeAPI on fatsecret.com, then come back")
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(s.authorizeUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(i)
        } catch (e: FsError) {
            fail(e, keepTokens = true)
            onError(e.message ?: "Couldn't connect")
        } catch (e: Exception) {
            publish(FsPhase.Error, e.message ?: "Couldn't start sign-in")
            onError(e.message ?: "Couldn't start sign-in")
        }
    }

    /** Deep link lifeapi://fatsecret/callback?oauth_token=…&oauth_verifier=… — returns true if handled. */
    fun handleCallback(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "lifeapi" || uri.host != "fatsecret") return false
        val token = uri.getQueryParameter("oauth_token").orEmpty()
        val verifier = uri.getQueryParameter("oauth_verifier").orEmpty()
        if (verifier.isBlank()) { publish(FsPhase.Error, "FatSecret didn't return a verification code"); return true }
        if (token.isNotBlank() && store.pendingToken.isNotBlank() && token != store.pendingToken) {
            publish(FsPhase.Error, "This approval belongs to an older sign-in — tap Connect again"); return true
        }
        finishAuth(verifier)
        return true
    }

    /** Step 3: exchange verifier → access token. Also used by the manual "enter code" field. */
    fun finishAuth(verifier: String) = scope.launch {
        if (store.pendingToken.isBlank()) { publish(FsPhase.Error, "Tap Connect first"); return@launch }
        publish(FsPhase.Authorizing, "Finishing sign-in…")
        try {
            val t = client().access(store.pendingToken, store.pendingSecret, verifier.trim())
            store.token = t.token
            store.tokenSecret = t.secret
            store.pendingToken = ""; store.pendingSecret = ""
            store.connectedTs = Days.now()
            store.lastErrorKind = ""; store.lastError = ""
            publish()
            syncNow(7)
        } catch (e: FsError) {
            fail(e, keepTokens = true)
        }
    }

    /** Clears tokens. deleteImported = also remove all FatSecret meals stored on the phone. */
    fun disconnect(deleteImported: Boolean) = scope.launch {
        mutex.withLock {
            store.clearTokens()
            store.lastSync = 0; store.lastSyncCount = 0
            if (deleteImported) {
                repo.db.withTransaction {
                    repo.db.openHelper.writableDatabase.execSQL("DELETE FROM meal WHERE source = ?", arrayOf(SOURCE))
                }
            }
            publish()
        }
    }

    // ------------------------------------------------------------------ sync

    @Volatile private var coldStartDone = false
    @Volatile private var returnFromApp = false
    private const val MIN_GAP_MS = 60_000L

    /** Call right before opening the FatSecret app: the next resume of LifeAPI syncs immediately. */
    fun markOpenedFatSecret() { returnFromApp = true }

    /**
     * Keeps the diary mirrored without user action (connected + not expired only). First call after a cold start
     * syncs the last 7 days; later calls today + yesterday. Skips when the last successful sync is < 60 s old,
     * unless [force] or the user just came back from the FatSecret app. Overlapping calls collapse (mutex).
     */
    fun autoSync(force: Boolean = false) {
        if (!::store.isInitialized) return
        if (store.token.isBlank() || store.lastErrorKind == "expired") return
        val back = returnFromApp
        if (!coldStartDone) { coldStartDone = true; returnFromApp = false; syncNow(7); return }
        if (!force && !back && Days.now() - store.lastSync < MIN_GAP_MS) return
        returnFromApp = false
        syncNow(2)
        if (back) {
            // The FatSecret app uploads new entries to its server a few seconds after you log them,
            // so a single sync right on return often sees nothing new. Follow up a few times.
            followUps?.cancel()
            followUps = scope.launch {
                for (d in longArrayOf(8_000, 25_000, 60_000)) { kotlinx.coroutines.delay(d); syncNow(2) }
            }
        }
    }

    @Volatile private var followUps: kotlinx.coroutines.Job? = null
    /** A sync requested while another runs isn't dropped: the running one repeats once at the end (max range kept). */
    @Volatile private var rerunDays = 0

    /** Sync the last [days] days, ending today (1 = today only, 7 = week, 30 = month). */
    fun syncNow(days: Int): kotlinx.coroutines.Job = scope.launch {
        if (store.token.isBlank()) { publish(); return@launch }
        if (!mutex.tryLock()) { rerunDays = maxOf(rerunDays, days); return@launch }
        try {
            store.lastAttempt = Days.now()
            publish(FsPhase.Syncing, "Syncing…")
            val today = Days.today()
            val n = sync(today - (days - 1).coerceAtLeast(0), today)
            store.lastSync = Days.now()
            store.lastSyncCount = n
            store.lastErrorKind = ""; store.lastError = ""
            publish()
        } catch (e: FsError) {
            fail(e, keepTokens = true)
        } catch (e: Exception) {
            store.lastErrorKind = "api"; store.lastError = e.message ?: "Sync failed"
            publish()
        } finally {
            mutex.unlock()
            val again = rerunDays
            if (again > 0) { rerunDays = 0; syncNow(again) }
        }
    }

    private fun fail(e: FsError, keepTokens: Boolean) {
        store.lastErrorKind = when (e) {
            is FsError.Expired -> "expired"
            is FsError.Offline -> "offline"
            is FsError.Config -> "config"
            is FsError.Api -> "api"
        }
        store.lastError = e.message ?: "Error"
        if (!keepTokens) store.clearTokens()
        publish()
    }

    private data class Entry(val extId: String, val day: Long, val slot: String, val items: String, val kcal: Int, val p: Int, val c: Int, val f: Int)

    /** Returns number of remote entries mirrored. */
    private suspend fun sync(from: Long, to: Long): Int {
        val c = client()
        val access = FatSecretClient.Tokens(store.token, store.tokenSecret)

        // Which days have entries? One food_entries.get_month call per month touched (daily totals only),
        // then food_entries.get per day with data. A single day skips the month lookup.
        val daysWithData: Set<Long> = if (from == to) setOf(from) else buildSet {
            var m = LocalDate.ofEpochDay(from).withDayOfMonth(1)
            val last = LocalDate.ofEpochDay(to)
            while (!m.isAfter(last)) {
                val o = c.api(access, mapOf("method" to "food_entries.get_month.v2", "date" to m.toEpochDay().toString()))
                o.optJSONObject("month")?.arrayOrSingle("day")?.forEach { d ->
                    val di = d.optString("date_int").toLongOrNull() ?: return@forEach
                    if (di in from..to) add(di)
                }
                m = m.plusMonths(1)
            }
        }

        val remote = HashMap<Long, List<Entry>>()
        var done = 0
        for (d in (from..to).filter { it in daysWithData }) {
            val o = c.api(access, mapOf("method" to "food_entries.get.v2", "date" to d.toString()))
            val list = (o.optJSONObject("food_entries")?.arrayOrSingle("food_entry") ?: emptyList()).map { e -> e.toEntry(d) }
            remote[d] = list
            done += list.size
            publish(FsPhase.Syncing, "Syncing $done entries…", done)
        }
        apply(from, to, remote)
        return done
    }

    private fun JSONObject.toEntry(fallbackDay: Long): Entry {
        val id = optString("food_entry_id")
        val day = optString("date_int").toLongOrNull() ?: fallbackDay
        val name = optString("food_entry_name").ifBlank { "Food" }
        val desc = optString("food_entry_description")
        return Entry(
            extId = "fs:$id",
            day = day,
            slot = when (optString("meal").lowercase()) {
                "breakfast" -> "Breakfast"; "lunch" -> "Lunch"; "dinner" -> "Dinner"; else -> "Snack"
            },
            items = if (desc.isBlank() || desc.equals(name, true)) name else "$name · $desc",
            kcal = num("calories").roundToInt(),
            p = num("protein").roundToInt(),
            c = num("carbohydrate").roundToInt(),
            f = num("fat").roundToInt(),
        )
    }

    private fun slotTime(slot: String): LocalTime = when (slot) {
        "Breakfast" -> LocalTime.of(8, 0); "Lunch" -> LocalTime.of(13, 0); "Dinner" -> LocalTime.of(19, 0); else -> LocalTime.of(16, 0)
    }

    // Make local FatSecret rows for days from..to equal to the remote map (days absent from it have no entries).
    private suspend fun apply(from: Long, to: Long, remote: Map<Long, List<Entry>>) {
        val db = repo.db
        db.withTransaction {
            val w = db.openHelper.writableDatabase
            val local = HashMap<String, Long>() // extId → row id
            w.query("SELECT id, extId FROM meal WHERE source = ? AND day BETWEEN ? AND ?", arrayOf<Any>(SOURCE, from, to)).use { cur ->
                while (cur.moveToNext()) cur.getString(1)?.let { local[it] = cur.getLong(0) }
            }
            val seen = HashSet<String>()
            for (e in remote.values.flatten()) {
                if (!seen.add(e.extId)) continue
                val ts = Days.tsOf(e.day, slotTime(e.slot))
                val id = local[e.extId] ?: run {
                    // The same entry may already exist on another day (moved in FatSecret) — the unique index covers all days.
                    w.query("SELECT id FROM meal WHERE extId = ?", arrayOf<Any>(e.extId)).use { cur -> if (cur.moveToFirst()) cur.getLong(0) else null }
                }
                if (id != null) {
                    w.execSQL(
                        "UPDATE meal SET day = ?, ts = ?, slot = ?, items = ?, kcal = ?, protein = ?, carbs = ?, fat = ?, source = ? WHERE id = ?",
                        arrayOf<Any>(e.day, ts, e.slot, e.items, e.kcal, e.p, e.c, e.f, SOURCE, id),
                    )
                } else {
                    w.execSQL(
                        "INSERT INTO meal (day, ts, slot, items, kcal, protein, carbs, fat, source, extId) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf<Any>(e.day, ts, e.slot, e.items, e.kcal, e.p, e.c, e.f, SOURCE, e.extId),
                    )
                }
            }
            // Entries deleted in FatSecret disappear here too.
            local.keys.filter { it !in seen }.forEach { ext ->
                w.execSQL("DELETE FROM meal WHERE extId = ?", arrayOf<Any>(ext))
            }
        }
    }
}
