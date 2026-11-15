package com.lifeapi.app.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeapi.app.data.bank.Bank
import com.lifeapi.app.data.bank.BankPhase
import com.lifeapi.app.data.binance.Binance
import com.lifeapi.app.data.binance.BinancePhase
import com.lifeapi.app.data.fatsecret.FatSecret
import com.lifeapi.app.data.fatsecret.FsPhase
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun ago(ts: Long): String {
    if (ts <= 0) return "never synced"
    val m = (System.currentTimeMillis() - ts) / 60_000
    return "synced " + when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
        else -> SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date(ts))
    }
}

/** How many integrations are connected (Settings row value). */
@Composable
fun connectedIntegrations(): Int {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { FatSecret.init(ctx); Bank.init(ctx); Binance.init(ctx) }
    val fs by FatSecret.status.collectAsStateWithLifecycle()
    val bank by Bank.status.collectAsStateWithLifecycle()
    val bn by Binance.status.collectAsStateWithLifecycle()
    return listOf(fs.connected, bank.connected, bn.lastSync > 0 && bn.configured).count { it }
}

/** Settings → Integrations: FatSecret, Swedbank (Enable Banking), Binance — status, Sync now, Manage. */
@Composable
fun IntegrationsScreen(onBack: () -> Unit) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { FatSecret.init(ctx); Bank.init(ctx); Binance.init(ctx) }
    val fs by FatSecret.status.collectAsStateWithLifecycle()
    val bank by Bank.status.collectAsStateWithLifecycle()
    val bn by Binance.status.collectAsStateWithLifecycle()

    ScreenList(glow = null, bottomExtra = false) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoundIconButton(Ic.ChevronLeft, "Back", onClick = onBack)
                Column {
                    Text("Integrations", style = T.headline, color = C.Ink)
                    Text("Read-only · each runs through your own small server", style = T.caption, color = C.InkSubtle)
                }
            }
        }
        item {
            IntegrationCard(
                badge = "FS", bg = C.FuelTint, fg = C.Fuel, title = "FatSecret", sub = "Food diary → Body › Nutrition",
                status = when (fs.phase) {
                    FsPhase.NotConfigured -> "Not set up"
                    FsPhase.Disconnected -> "Not connected"
                    FsPhase.Authorizing -> "Waiting for sign-in"
                    FsPhase.Syncing -> "Syncing…"
                    FsPhase.Expired -> "Sign-in expired — reconnect"
                    FsPhase.Offline -> "Offline · ${ago(fs.lastSync)}"
                    FsPhase.Error -> fs.message.ifBlank { "Error" }
                    FsPhase.Connected -> "Connected · ${ago(fs.lastSync)}"
                },
                ok = fs.phase == FsPhase.Connected || fs.phase == FsPhase.Syncing,
                problem = fs.phase == FsPhase.Expired || fs.phase == FsPhase.Error,
                canSync = fs.connected && fs.phase != FsPhase.Syncing,
                onSync = { FatSecret.syncNow(7) },
                onManage = { nav.navigate("body/fatsecret") },
            )
        }
        item {
            IntegrationCard(
                badge = "S", bg = C.MoneyTint, fg = C.Money, title = bank.aspsp.ifBlank { "Bank" }, sub = "Accounts & transactions via Enable Banking → Money",
                status = when (bank.phase) {
                    BankPhase.NotConfigured -> "Not set up"
                    BankPhase.Disconnected -> "Not connected"
                    BankPhase.Authorizing -> "Waiting for bank approval"
                    BankPhase.Syncing -> "Syncing…"
                    BankPhase.Expired -> "Consent ended — reconnect"
                    BankPhase.Offline -> "Offline · ${ago(bank.lastSync)}"
                    BankPhase.RateLimited -> "Bank's daily limit reached"
                    BankPhase.Error -> bank.message.ifBlank { "Error" }
                    BankPhase.Connected -> "Connected · ${ago(bank.lastSync)}" +
                        if (bank.validUntil > 0) " · until " + SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date(bank.validUntil)) else ""
                },
                ok = bank.phase == BankPhase.Connected || bank.phase == BankPhase.Syncing,
                problem = bank.phase == BankPhase.Expired || bank.phase == BankPhase.Error,
                canSync = bank.connected && bank.phase != BankPhase.Syncing,
                onSync = { Bank.syncNow() },
                onManage = { nav.navigate("money/bank") },
            )
        }
        item {
            IntegrationCard(
                badge = "B", bg = Color(0xFF2A2410), fg = Color(0xFFF3D06A), title = "Binance", sub = "Read-only crypto balances → Money › Crypto",
                status = when (bn.phase) {
                    BinancePhase.NotConfigured -> "Not set up"
                    BinancePhase.Ready -> "Ready · not synced yet"
                    BinancePhase.Syncing -> "Syncing…"
                    BinancePhase.Synced -> "Connected · ${ago(bn.lastSync)}"
                    BinancePhase.Offline -> "Offline · ${ago(bn.lastSync)}"
                    BinancePhase.RateLimited -> "Rate limited — try later"
                    BinancePhase.Error -> bn.message.ifBlank { "Error" }
                },
                ok = bn.phase == BinancePhase.Synced || bn.phase == BinancePhase.Syncing,
                problem = bn.phase == BinancePhase.Error,
                canSync = bn.configured && bn.phase != BinancePhase.Syncing,
                onSync = { Binance.sync() },
                onManage = { nav.navigate("money/binance") },
            )
        }
        item { Text("Keys live on your Cloudflare Workers, never in the app. Data is stored on this phone only.", style = T.caption, color = C.InkFaint, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun IntegrationCard(
    badge: String, bg: Color, fg: Color, title: String, sub: String, status: String,
    ok: Boolean, problem: Boolean, canSync: Boolean, onSync: () -> Unit, onManage: () -> Unit,
) {
    LifeCard(Modifier.fillMaxWidth(), gap = 12.dp, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text(badge, style = T.smallB, color = fg)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = T.section, color = C.Ink)
                Text(sub, style = T.caption, color = C.InkSubtle)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(when { problem -> C.Negative; ok -> C.Positive; else -> C.Ghost }))
            Text(status, style = T.small, color = if (problem) C.Negative else C.InkMuted, modifier = Modifier.weight(1f), maxLines = 2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Sync now", fg, C.Graphite0, Modifier.weight(1f), 42.dp, 12.dp, enabled = canSync, style = T.body.copy(fontWeight = FontWeight.SemiBold), onClick = onSync)
            GhostButton("Manage", Modifier.weight(1f), height = 42.dp, onClick = onManage)
        }
    }
}
