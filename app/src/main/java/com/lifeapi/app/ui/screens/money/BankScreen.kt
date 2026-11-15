package com.lifeapi.app.ui.screens.money

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeapi.app.LifeApp
import com.lifeapi.app.data.Account
import com.lifeapi.app.data.bank.Bank
import com.lifeapi.app.data.bank.BankPhase
import com.lifeapi.app.data.bank.BankStatus
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
import java.text.DateFormat
import java.util.Date

internal fun bankMoney(a: Account): String =
    if (a.currency == null || a.currency == "EUR") fmtCents(a.balanceCents) else fmtCents(a.balanceCents).replace("€", "") + " " + a.currency

private fun ago(ts: Long): String {
    if (ts <= 0) return "never"
    val m = (System.currentTimeMillis() - ts) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 48 * 60 -> "${m / 60} h ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))
    }
}

private fun validLabel(ts: Long): String =
    if (ts <= 0) "" else "consent until " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))

private fun phaseLabel(s: BankStatus): Pair<String, Color> = when (s.phase) {
    BankPhase.NotConfigured -> "Not set up" to C.InkSubtle
    BankPhase.Disconnected -> "Not connected" to C.InkSubtle
    BankPhase.Authorizing -> "Waiting for bank" to C.Fuel
    BankPhase.Connected -> "Connected" to C.Positive
    BankPhase.Syncing -> "Syncing…" to C.Money
    BankPhase.Expired -> "Reconnect" to C.Negative
    BankPhase.Offline -> "Offline" to C.Fuel
    BankPhase.RateLimited -> "Bank limit" to C.Fuel
    BankPhase.Error -> "Error" to C.Negative
}

@Composable
private fun Pill(text: String, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Graphite2).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = T.tiny, color = fg)
    }
}

/** Compact live status card — Connections screen and Money tab. Tap opens the bank screen. */
@Composable
fun BankCard(compact: Boolean = false) {
    val nav = LocalNav.current
    val s by Bank.status.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val accounts by remember { (ctx.applicationContext as LifeApp).container.repo.db.bank().bankAccounts() }.collectAsStateWithLifecycle(emptyList())
    val (label, fg) = phaseLabel(s)
    LifeCard(Modifier.fillMaxWidth(), gap = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar("S", C.FuelTint, C.Fuel)
            Column(Modifier.weight(1f)) {
                Text(s.aspsp.ifBlank { "Bank" }, style = T.section, color = C.Ink)
                Text(
                    when {
                        s.connected -> "Enable Banking · read-only · synced ${ago(s.lastSync)}"
                        else -> "Open banking via Enable Banking · read-only"
                    },
                    style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Pill(label, fg)
        }
        if (s.connected && accounts.isNotEmpty() && !compact) {
            accounts.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.name, style = T.small, color = C.InkMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(bankMoney(a), style = T.smallB, color = C.Ink)
                }
            }
        }
        if (s.message.isNotBlank() && s.phase != BankPhase.Connected && s.phase != BankPhase.Syncing) {
            Text(s.message, style = T.caption, color = if (s.phase == BankPhase.Expired || s.phase == BankPhase.Error) C.Negative else C.Fuel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.connected && s.phase != BankPhase.Expired) {
                PillButton(if (s.phase == BankPhase.Syncing) "Syncing…" else "Sync now", C.MoneyTint, C.Money, Modifier.weight(1f), 44.dp, 12.dp, style = T.body, enabled = s.phase != BankPhase.Syncing) { Bank.syncNow() }
            }
            PillButton(
                when (s.phase) {
                    BankPhase.NotConfigured -> "Set up"
                    BankPhase.Disconnected, BankPhase.Authorizing -> "Connect"
                    BankPhase.Expired -> "Reconnect"
                    else -> "Manage"
                },
                C.Graphite3, C.Ink, Modifier.weight(1f), 44.dp, 12.dp, style = T.body,
            ) { nav.navigate("money/bank") }
        }
    }
}

/** Full bank connection screen: proxy setup, bank choice, connect/reconnect, accounts, sync, disconnect. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BankScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Bank.status.collectAsStateWithLifecycle()
    val accounts by remember { (ctx.applicationContext as LifeApp).container.repo.db.bank().bankAccounts() }.collectAsStateWithLifecycle(emptyList())

    var url by remember(s.serverUrl) { mutableStateOf(s.serverUrl) }
    var key by remember(s.appKey) { mutableStateOf(s.appKey) }
    var serverNote by remember { mutableStateOf<String?>(null) }
    var editServer by remember { mutableStateOf(false) }
    var banks by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var bankNote by remember { mutableStateOf<String?>(null) }
    var aspsp by remember(s.aspsp) { mutableStateOf(s.aspsp.ifBlank { "Swedbank" }) }
    var busy by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var bankTxns by remember { mutableStateOf(0) }

    val configured = s.phase != BankPhase.NotConfigured
    LaunchedEffect(configured) {
        if (configured && banks.isEmpty()) runCatching { Bank.banks("LV") }
            .onSuccess { banks = it; bankNote = if (it.isEmpty()) "No banks returned for Latvia" else null }
            .onFailure { bankNote = "Couldn't load banks: ${it.message}" }
    }

    fun connect() {
        busy = true
        scope.launch {
            runCatching { Bank.startAuth(aspsp, "LV") }
                .onSuccess { link -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { bankNote = "Couldn't start: ${it.message}" }
            busy = false
        }
    }

    ScreenList(glow = C.Money, bottomExtra = false) {
        item { DetailTop("Bank connection", "Enable Banking · read-only, nothing can move money", onBack = { nav.popBackStack() }) }

        // ---- status
        item {
            val (label, fg) = phaseLabel(s)
            LifeCard(Modifier.fillMaxWidth(), gap = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar("S", C.FuelTint, C.Fuel)
                    Column(Modifier.weight(1f)) {
                        Text(s.aspsp.ifBlank { "Bank" } + " · " + s.country, style = T.section, color = C.Ink)
                        Text(
                            listOf(if (s.connected) "synced ${ago(s.lastSync)}" else null, validLabel(s.validUntil).ifBlank { null })
                                .filterNotNull().joinToString(" · ").ifBlank { "Not connected" },
                            style = T.caption, color = C.InkSubtle,
                        )
                    }
                    Pill(label, fg)
                }
                if (s.message.isNotBlank() && s.phase != BankPhase.Connected) {
                    Text(s.message, style = T.caption, color = if (s.phase == BankPhase.Expired || s.phase == BankPhase.Error) C.Negative else C.Fuel)
                }
                if (s.connected && s.phase != BankPhase.Expired) {
                    PillButton(if (s.phase == BankPhase.Syncing) "Syncing…" else "Sync now", C.Money, C.Graphite0, Modifier.fillMaxWidth(), 48.dp, 14.dp, enabled = s.phase != BankPhase.Syncing) { Bank.syncNow() }
                    Text("Auto-sync at most every 6 h when you open Money — banks limit background refreshes. Last run added or updated ${s.lastCount}.", style = T.caption, color = C.InkFaint)
                }
            }
        }

        // ---- accounts
        if (accounts.isNotEmpty()) item {
            ListCard {
                accounts.forEachIndexed { i, a ->
                    DivRow(i == 0) {
                        Avatar(a.name.take(1).uppercase(), C.MoneyTint, C.Money)
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = T.body, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(a.iban?.let { "••" + it.takeLast(4) }, a.currency, "updated ${ago(a.lastSync)}").joinToString(" · "), style = T.caption, color = C.InkSubtle)
                        }
                        Text(bankMoney(a), style = T.section, color = C.Ink)
                    }
                }
            }
        }

        // ---- connect / reconnect
        if (configured && (!s.connected || s.phase == BankPhase.Expired)) item {
            LifeCard(Modifier.fillMaxWidth(), gap = 12.dp) {
                Text(if (s.phase == BankPhase.Expired) "Reconnect" else "Connect your bank", style = T.section, color = C.Ink)
                Text("You'll log in at the bank and approve read-only access. LifeAPI never sees your bank password.", style = T.caption, color = C.InkMuted)
                val shown = banks.filter { it.second == "LV" }.map { it.first }.distinct()
                    .sortedWith(compareBy({ !it.contains("Swedbank", true) }, { it }))
                if (shown.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.take(12).forEach { n -> OChip(n, sel = n == aspsp) { aspsp = n } }
                }
                bankNote?.let { Text(it, style = T.caption, color = C.Fuel) }
                PillButton(if (busy) "Opening bank…" else "Connect $aspsp", C.Money, C.Graphite0, Modifier.fillMaxWidth(), 48.dp, 14.dp, enabled = !busy) { connect() }
                if (s.phase == BankPhase.Authorizing) {
                    Text("Back from the bank but the app didn't continue? Paste the code shown on the page:", style = T.caption, color = C.InkMuted)
                    LifeField(code, { code = it }, "Code")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Finish", C.MoneyTint, C.Money, Modifier.weight(1f), 44.dp, 12.dp, enabled = code.isNotBlank()) {
                            val c = code; code = ""; scope.launch { Bank.finish(c) }
                        }
                        GhostButton("Cancel", Modifier.weight(1f)) { Bank.cancelAuth() }
                    }
                }
            }
        }

        // ---- server
        item {
            LifeCard(Modifier.fillMaxWidth(), gap = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Server", style = T.section, color = C.Ink, modifier = Modifier.weight(1f))
                }
                if (!configured || editServer) {
                    Text("Your bank proxy (server/bank-proxy in the project) holds the Enable Banking key — paste its address and app key.", style = T.caption, color = C.InkMuted)
                    LifeField(url, { url = it }, "Server URL", placeholder = "https://lifeapi-bank.….workers.dev")
                    LifeField(key, { key = it }, "App key (APP_SHARED_KEY)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Save", C.Money, C.Graphite0, Modifier.weight(1f), 44.dp, 12.dp, enabled = url.startsWith("https://") && key.isNotBlank()) {
                            Bank.configure(url, key); editServer = false
                            scope.launch { serverNote = Bank.testServer() }
                        }
                        if (configured) GhostButton("Cancel", Modifier.weight(1f)) { editServer = false }
                    }
                } else {
                    Text(s.serverUrl, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Test", Modifier.weight(1f)) { scope.launch { serverNote = "Checking…"; serverNote = Bank.testServer() } }
                        GhostButton("Change", Modifier.weight(1f)) { editServer = true }
                    }
                }
                serverNote?.let { Text(it, style = T.caption, color = if (it == "Server OK") C.Positive else C.Fuel) }
            }
        }

        // ---- disconnect
        if (s.connected || s.phase == BankPhase.Expired) item {
            GhostButton("Disconnect bank", Modifier.fillMaxWidth(), color = C.Negative) {
                scope.launch { bankTxns = Bank.bankTxnCount(); confirmDisconnect = true }
            }
        }
        item {
            Text(
                "Read-only access through Enable Banking (licensed AIS provider). Balances and transactions are stored only on this phone; the proxy doesn't keep them. Synced transactions don't change balances — the bank's balance is used.",
                style = T.caption, color = C.InkFaint,
            )
        }
    }

    if (confirmDisconnect) AlertDialog(
        onDismissRequest = { confirmDisconnect = false },
        containerColor = C.Graphite1,
        title = { Text("Disconnect bank?", style = T.title, color = C.Ink) },
        text = {
            Text(
                "Ends the consent and stops syncing. Linked accounts become manual accounts. Keep the $bankTxns synced transactions or remove them?",
                style = T.bodyR, color = C.InkMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = { confirmDisconnect = false; scope.launch { Bank.disconnect(removeTxns = false) } }) { Text("Keep history", color = C.Money) }
        },
        dismissButton = {
            TextButton(onClick = { confirmDisconnect = false; scope.launch { Bank.disconnect(removeTxns = true) } }) { Text("Remove them", color = C.Negative) }
        },
    )
}
