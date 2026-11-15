package com.lifeapi.app.data.bank

import android.content.Context
import org.json.JSONObject

/**
 * Bank connection state in app-private SharedPreferences (not encrypted — protected by Android's app sandbox).
 * Holds no bank credentials: only the proxy URL, its app key, the Enable Banking session id and bookkeeping.
 */
class BankStore(context: Context) {
    private val p = context.getSharedPreferences("bank", Context.MODE_PRIVATE)

    var serverUrl: String get() = p.getString("server", "").orEmpty(); set(v) = p.edit().putString("server", v).apply()
    var appKey: String get() = p.getString("key", "").orEmpty(); set(v) = p.edit().putString("key", v).apply()
    var aspspName: String get() = p.getString("aspsp", "Swedbank").orEmpty(); set(v) = p.edit().putString("aspsp", v).apply()
    var country: String get() = p.getString("country", "LV").orEmpty(); set(v) = p.edit().putString("country", v).apply()
    var sessionId: String get() = p.getString("session", "").orEmpty(); set(v) = p.edit().putString("session", v).apply()
    /** Consent end, epoch millis (0 = unknown). */
    var validUntil: Long get() = p.getLong("valid", 0); set(v) = p.edit().putLong("valid", v).apply()
    var pendingState: String get() = p.getString("state", "").orEmpty(); set(v) = p.edit().putString("state", v).apply()
    var pendingValidUntil: Long get() = p.getLong("pvalid", 0); set(v) = p.edit().putLong("pvalid", v).apply()
    var lastSync: Long get() = p.getLong("last", 0); set(v) = p.edit().putLong("last", v).apply()
    var lastAttempt: Long get() = p.getLong("attempt", 0); set(v) = p.edit().putLong("attempt", v).apply()
    var lastCount: Int get() = p.getInt("count", 0); set(v) = p.edit().putInt("count", v).apply()
    /** "", "expired", "offline", "rate", "config", "api" */
    var errorKind: String get() = p.getString("ekind", "").orEmpty(); set(v) = p.edit().putString("ekind", v).apply()
    var errorText: String get() = p.getString("etext", "").orEmpty(); set(v) = p.edit().putString("etext", v).apply()

    /** Enable Banking account uid (per session) → local stable extId ("eb:<iban or hash>"). */
    var accountMap: Map<String, String>
        get() = runCatching { JSONObject(p.getString("accounts", "{}")!!).let { j -> j.keys().asSequence().associateWith { j.getString(it) } } }.getOrDefault(emptyMap())
        set(v) = p.edit().putString("accounts", JSONObject(v).toString()).apply()

    fun clearSession() {
        p.edit().remove("session").remove("valid").remove("accounts").remove("state").remove("pvalid")
            .remove("ekind").remove("etext").apply()
    }
}
