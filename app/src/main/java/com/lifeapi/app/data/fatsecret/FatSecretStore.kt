package com.lifeapi.app.data.fatsecret

import android.content.Context

/**
 * Connection settings and tokens. Plain MODE_PRIVATE SharedPreferences (app-private storage; no extra
 * crypto dependency). The access token alone is useless without the consumer secret, which lives only on
 * the user's proxy server.
 */
class FatSecretStore(context: Context) {
    private val p = context.getSharedPreferences("fatsecret", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = p.getString("server", "") ?: ""
        set(v) = p.edit().putString("server", v.trim()).apply()
    var appKey: String
        get() = p.getString("appKey", "") ?: ""
        set(v) = p.edit().putString("appKey", v.trim()).apply()

    var token: String
        get() = p.getString("token", "") ?: ""
        set(v) = p.edit().putString("token", v).apply()
    var tokenSecret: String
        get() = p.getString("tokenSecret", "") ?: ""
        set(v) = p.edit().putString("tokenSecret", v).apply()

    /** Request token waiting for the user to approve in the browser. */
    var pendingToken: String
        get() = p.getString("pendingToken", "") ?: ""
        set(v) = p.edit().putString("pendingToken", v).apply()
    var pendingSecret: String
        get() = p.getString("pendingSecret", "") ?: ""
        set(v) = p.edit().putString("pendingSecret", v).apply()

    var connectedTs: Long
        get() = p.getLong("connectedTs", 0)
        set(v) = p.edit().putLong("connectedTs", v).apply()
    var lastSync: Long
        get() = p.getLong("lastSync", 0)
        set(v) = p.edit().putLong("lastSync", v).apply()
    var lastAttempt: Long
        get() = p.getLong("lastAttempt", 0)
        set(v) = p.edit().putLong("lastAttempt", v).apply()
    var lastError: String
        get() = p.getString("lastError", "") ?: ""
        set(v) = p.edit().putString("lastError", v).apply()
    /** "", "offline", "expired", "config", "api" */
    var lastErrorKind: String
        get() = p.getString("lastErrorKind", "") ?: ""
        set(v) = p.edit().putString("lastErrorKind", v).apply()
    var lastSyncCount: Int
        get() = p.getInt("lastSyncCount", 0)
        set(v) = p.edit().putInt("lastSyncCount", v).apply()

    fun clearTokens() {
        p.edit().remove("token").remove("tokenSecret").remove("pendingToken").remove("pendingSecret")
            .remove("connectedTs").remove("lastErrorKind").remove("lastError").apply()
    }
}
