package com.lifeapi.app.ui.screens.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeapi.app.LifeApp
import com.lifeapi.app.data.binance.Binance
import com.lifeapi.app.data.binance.BinancePhase
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val BinanceYellow = Color(0xFFF3D06A)
internal val BinanceTint = Color(0xFF2A2410)

internal fun agoLabel(ts: Long): String {
    if (ts <= 0) return "never"
    val m = (System.currentTimeMillis() - ts) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
        else -> SimpleDateFormat("d MMM HH:mm", Locale.ENGLISH).format(Date(ts))
    }
}

/** Binance (read-only) — proxy setup, status, holdings mirrored into Crypto, disconnect. */
@Composable
fun BinanceScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Binance.init(ctx) }
    val st by Binance.status.collectAsStateWithLifecycle()
    val repo = (ctx.applicationContext as LifeApp).container.repo
    val holdings by remember { repo.money.crypto() }.collectAsStateWithLifecycle(emptyList())
    val mine = holdings.filter { it.source == Binance.SOURCE }.sortedByDescending { it.qty * it.priceCents }
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(false) }
    var url by remember(st.serverUrl) { mutableStateOf(st.serverUrl) }
    var key by remember(st.appKey) { mutableStateOf(st.appKey) }
    var note by remember { mutableStateOf<String?>(null) }
    var confirmOff by remember { mutableStateOf(false) }

    ScreenList(glow = null, bottomExtra = false) {
        item { DetailTop("Binance", "Read-only balances · Spot, Funding, Earn", onBack = { nav.popBackStack() }) }
        item {
            LifeCard(Modifier.fillMaxWidth(), gap = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar("B", BinanceTint, BinanceYellow)
                    Column(Modifier.weight(1f)) {
                        Text(if (st.configured) fmtCents(st.totalCents) else "Not set up", style = T.section, color = C.Ink)
                        Text(
                            when (st.phase) {
                                BinancePhase.NotConfigured -> "Needs your read-only proxy (server/binance-proxy)"
                                BinancePhase.Syncing -> "Syncing…"
                                BinancePhase.Ready -> "Ready · tap Sync now"
                                else -> "Last sync ${agoLabel(st.lastSync)}" + if (st.dust > 0) " · ${st.dust} dust hidden" else ""
                            },
                            style = T.caption, color = C.InkSubtle,
                        )
                    }
                }
                if (st.phase in setOf(BinancePhase.Error, BinancePhase.Offline, BinancePhase.RateLimited) && st.message.isNotBlank())
                    Text(st.message, style = T.caption, color = if (st.phase == BinancePhase.Offline) C.Fuel else C.Negative)
                if (st.unpriced.isNotBlank()) Text("No EUR price for: ${st.unpriced}", style = T.caption, color = C.InkFaint)
                if (st.configured && !edit) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(if (st.phase == BinancePhase.Syncing) "Syncing…" else "Sync now", BinanceYellow, C.Graphite0, Modifier.weight(1f), 44.dp, 12.dp,
                        enabled = st.phase != BinancePhase.Syncing, style = T.body.copy(fontWeight = FontWeight.SemiBold)) { Binance.sync() }
                    GhostButton("Server", Modifier.weight(1f)) { edit = true }
                }
            }
        }
        if (!st.configured || edit) item {
            LifeCard(Modifier.fillMaxWidth(), gap = 10.dp) {
                Text("PROXY", style = T.label, color = C.InkSubtle)
                LifeField(url, { url = it }, "Server URL", placeholder = "https://lifeapi-binance.….workers.dev")
                LifeField(key, { key = it }, "App key (APP_SHARED_KEY)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Save", BinanceYellow, C.Graphite0, Modifier.weight(1f), 44.dp, 12.dp, enabled = url.isNotBlank() && key.isNotBlank()) {
                        Binance.configure(url, key); edit = false; Binance.sync()
                    }
                    GhostButton("Test", Modifier.weight(1f)) {
                        Binance.configure(url, key)
                        scope.launch { note = Binance.testServer() }
                    }
                }
                note?.let { Text(it, style = T.caption, color = if (it == "Server OK") C.Positive else C.Fuel) }
                Text("The Binance key stays on your Cloudflare Worker. Use a key with Enable Reading only — this app can't trade or withdraw.", style = T.caption, color = C.InkSubtle)
            }
        }
        if (mine.isNotEmpty()) item {
            val total = mine.sumOf { it.qty * it.priceCents }.coerceAtLeast(1.0)
            ListCard {
                mine.forEachIndexed { i, h ->
                    DivRow(i == 0, onClick = { nav.navigate("money/crypto") }) {
                        Text(h.sym, style = T.mono, color = C.Ink, modifier = Modifier.width(56.dp))
                        Column(Modifier.weight(1f)) {
                            Text(String.format(Locale.US, "%.6f", h.qty).trimEnd('0').trimEnd('.'), style = T.body, color = C.Ink)
                            Text("${(h.qty * h.priceCents * 100 / total).toInt()}% · ${fmtCents(h.priceCents)} each", style = T.caption, color = C.InkSubtle)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmtCents(Math.round(h.qty * h.priceCents)), style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink)
                            val ch = Math.round(h.qty * (h.priceCents - h.prevPriceCents))
                            if (h.prevPriceCents > 0) Text(fmtCents(ch, sign = true) + " 24h", style = T.caption, color = if (ch >= 0) C.Positive else C.Negative)
                        }
                    }
                }
            }
        }
        if (st.configured) item {
            if (!confirmOff) GhostButton("Disconnect Binance", Modifier.fillMaxWidth(), color = C.Negative) { confirmOff = true }
            else LifeCard(Modifier.fillMaxWidth(), gap = 10.dp) {
                Text("Remove Binance holdings and the Binance account from LifeAPI? Your Binance account isn't touched.", style = T.small, color = C.InkMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Disconnect", C.Negative, C.Graphite0, Modifier.weight(1f), 44.dp, 12.dp) { Binance.disconnect(); confirmOff = false }
                    GhostButton("Cancel", Modifier.weight(1f)) { confirmOff = false }
                }
            }
        }
    }
}
