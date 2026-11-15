package com.lifeapi.app.data.binance

import android.content.Context

/** Binance proxy settings + sync bookkeeping in app-private SharedPreferences (not encrypted; no Binance keys here). */
class BinanceStore(context: Context) {
    private val p = context.getSharedPreferences("binance", Context.MODE_PRIVATE)

    var serverUrl: String get() = p.getString("server", "").orEmpty(); set(v) = p.edit().putString("server", v).apply()
    var appKey: String get() = p.getString("key", "").orEmpty(); set(v) = p.edit().putString("key", v).apply()
    var lastSync: Long get() = p.getLong("last", 0); set(v) = p.edit().putLong("last", v).apply()
    var lastAttempt: Long get() = p.getLong("attempt", 0); set(v) = p.edit().putLong("attempt", v).apply()
    var totalCents: Long get() = p.getLong("total", 0); set(v) = p.edit().putLong("total", v).apply()
    var dustCount: Int get() = p.getInt("dust", 0); set(v) = p.edit().putInt("dust", v).apply()
    var unpriced: String get() = p.getString("unpriced", "").orEmpty(); set(v) = p.edit().putString("unpriced", v).apply()
    /** "", "key", "offline", "rate", "region", "config", "api" */
    var errorKind: String get() = p.getString("ekind", "").orEmpty(); set(v) = p.edit().putString("ekind", v).apply()
    var errorText: String get() = p.getString("etext", "").orEmpty(); set(v) = p.edit().putString("etext", v).apply()

    fun clear() { p.edit().clear().apply() }
}
