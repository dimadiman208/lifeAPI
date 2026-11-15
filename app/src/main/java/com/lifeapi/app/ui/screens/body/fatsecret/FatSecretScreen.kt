package com.lifeapi.app.ui.screens.body.fatsecret

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.fatsecret.FatSecret
import com.lifeapi.app.data.fatsecret.FsPhase
import com.lifeapi.app.data.fatsecret.FsStatus
import com.lifeapi.app.data.hhmm
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.screens.body.DetailHeader
import com.lifeapi.app.ui.screens.body.GroupCard
import com.lifeapi.app.ui.screens.body.GroupRow
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.fatsecret.FatSecretVM
import java.util.Locale

const val FATSECRET_ROUTE = "body/fatsecret"

private val SYNC_RANGES = listOf("Today" to 1, "7 days" to 7, "30 days" to 30)

@Composable
fun FatSecretScreen(vm: FatSecretVM = viewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    val status = s.status
    var editServer by remember { mutableStateOf(false) }
    var askDisconnect by remember { mutableStateOf(false) }
    var range by remember { mutableIntStateOf(0) }

    ScreenList(glow = C.Fuel, bottomExtra = false) {
        item { DetailHeader("Food sources") }
        item {
            LifeCard(Modifier.fillMaxWidth(), gap = 14.dp, padding = PaddingValues(16.dp)) {
                FsHeaderRow(status)
                when (status.phase) {
                    FsPhase.NotConfigured, FsPhase.Disconnected -> {
                        if (status.phase == FsPhase.Disconnected && !editServer) {
                            Text("Server: ${status.serverUrl}", style = T.caption, color = C.InkSubtle)
                            PillButton("Connect FatSecret", C.Fuel, C.Graphite0, Modifier.fillMaxWidth(), height = 48.dp, radius = 14.dp, style = T.section) { vm.connect() }
                            GhostButton("Change server", Modifier.fillMaxWidth()) { editServer = true }
                        } else ServerForm(status) { url, key -> vm.save(url, key); editServer = false }
                    }
                    FsPhase.Authorizing -> AuthorizingBlock(status, onFinish = vm::finish, onRestart = vm::connect)
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Last sync", lastSyncLabel(status), Modifier.weight(1f))
                            StatTile("Imported today", if (s.todayMeals == 0) "—" else String.format(Locale.US, "%d meal%s · %,d kcal", s.todayMeals, if (s.todayMeals == 1) "" else "s", s.todayKcal), Modifier.weight(1f))
                        }
                        Segmented(SYNC_RANGES.map { it.first }, range, selBg = C.FuelTint, selFg = C.Fuel) { range = it }
                        GhostButton(if (status.phase == FsPhase.Syncing) "Syncing…" else "Sync now", Modifier.fillMaxWidth()) {
                            if (status.phase != FsPhase.Syncing) vm.sync(SYNC_RANGES[range].second)
                        }
                    }
                }
            }
        }
        if (status.connected || status.phase == FsPhase.Expired) {
            item { MonoLabel("STATUS", modifier = Modifier.padding(horizontal = 4.dp)) }
            item { GroupCard { StatusRow(status, onReconnect = vm::connect, onRetry = { vm.sync(1) }) } }
            item {
                GroupCard {
                    GroupRow("Import meals", sub = "Breakfast, lunch, dinner and snacks with kcal & macros", divider = false) {
                        Text("On", style = T.small, color = C.Fuel)
                    }
                    GroupRow("Edits", sub = "FatSecret entries are read-only here — change them in FatSecret, the next sync updates or removes them") {}
                    GroupRow("Server", sub = status.serverUrl, onClick = { editServer = true }) { Text("Edit ›", style = T.small, color = C.InkMuted) }
                    GroupRow("Disconnect", onClick = { askDisconnect = true }) { Text("›", style = T.small, color = C.Negative) }
                }
            }
            if (editServer) item { LifeCard(Modifier.fillMaxWidth()) { ServerForm(status) { url, key -> vm.save(url, key); editServer = false } } }
        }
        item {
            Text(
                "Sync is a source, never a dependency: manual entry always works, and synced meals stay on this phone offline. " +
                    "Sign-in goes through your own small server so the FatSecret secret key never ships inside the app " +
                    "(setup: server/fatsecret-proxy/README.md).",
                style = T.caption, color = C.InkFaint, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (askDisconnect) {
        AlertDialog(
            onDismissRequest = { askDisconnect = false },
            containerColor = C.Graphite2,
            title = { Text("Disconnect FatSecret?", style = T.section, color = C.Ink) },
            text = { Text("Meals already imported can stay on this phone or be removed. Manual entries are never touched.", style = T.small, color = C.InkMuted) },
            confirmButton = { TextButton({ askDisconnect = false; vm.disconnect(false) }) { Text("Keep meals", color = C.Fuel) } },
            dismissButton = { TextButton({ askDisconnect = false; vm.disconnect(true) }) { Text("Remove imported", color = C.Negative) } },
        )
    }
}

@Composable
private fun FsHeaderRow(s: FsStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FsBadge()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("FatSecret", style = T.section, color = C.Ink)
            Text(subtitle(s), style = T.caption, color = C.InkSubtle)
        }
        StatusPill(s.phase)
    }
}

@Composable
fun FsBadge() {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(C.FuelTint), contentAlignment = Alignment.Center) {
        Text("FS", style = T.section.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), color = C.Fuel)
    }
}

private fun subtitle(s: FsStatus) = when (s.phase) {
    FsPhase.NotConfigured -> "Not set up · needs your sync server"
    FsPhase.Disconnected -> "Not connected"
    FsPhase.Authorizing -> "Waiting for approval on fatsecret.com"
    FsPhase.Expired -> "Sign-in expired"
    else -> "Connected · food diary"
}

@Composable
fun StatusPill(p: FsPhase) {
    val (label, fg, bg) = when (p) {
        FsPhase.Connected -> Triple("Synced", C.Positive, C.GainTint)
        FsPhase.Syncing -> Triple("Syncing", C.Fuel, C.FuelTint)
        FsPhase.Expired -> Triple("Expired", C.Negative, Color(0xFF33211C))
        FsPhase.Offline -> Triple("Offline", C.InkMuted, C.Graphite3)
        FsPhase.Error -> Triple("Error", C.Negative, Color(0xFF33211C))
        FsPhase.Authorizing -> Triple("Pending", C.Fuel, C.FuelTint)
        else -> Triple("Off", C.InkMuted, C.Graphite3)
    }
    Row(
        Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(bg).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(fg))
        Text(label, style = T.tiny, color = fg)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF17181C)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = T.tiny.copy(fontWeight = FontWeight.Normal), color = C.InkSubtle)
        Text(value, style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink, maxLines = 1)
    }
}

fun lastSyncLabel(s: FsStatus): String {
    if (s.lastSync == 0L) return "Never"
    val rel = DateUtils.getRelativeTimeSpanString(s.lastSync, Days.now(), DateUtils.MINUTE_IN_MILLIS).toString()
    return if (Days.dayOf(s.lastSync) == Days.today()) "${hhmm(Days.timeOf(s.lastSync))} · $rel" else rel
}

@Composable
private fun StatusRow(s: FsStatus, onReconnect: () -> Unit, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (s.phase) {
            FsPhase.Syncing -> CircularProgressIndicator(Modifier.size(16.dp), color = C.Fuel, strokeWidth = 2.dp)
            FsPhase.Expired, FsPhase.Error -> Box(Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF33211C)), contentAlignment = Alignment.Center) {
                Text("!", style = T.tiny.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp), color = C.Negative)
            }
            FsPhase.Offline -> Box(Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).background(C.Graphite3))
            else -> Box(Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).background(C.GainTint), contentAlignment = Alignment.Center) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(C.Positive))
            }
        }
        val last = if (s.lastSync == 0L) "no successful sync yet" else "last good sync ${lastSyncLabel(s)}"
        val (title, sub) = when (s.phase) {
            FsPhase.Syncing -> (if (s.progress > 0) "Syncing ${s.progress} entries…" else "Syncing…") to "just now"
            FsPhase.Expired -> "Sign-in expired" to "${last.replaceFirstChar { it.uppercase() }} · manual entries kept"
            FsPhase.Offline -> "Offline" to "Will sync when connected · $last"
            FsPhase.Error -> "Sync failed" to s.message.ifBlank { "Unknown error" }
            else -> "Up to date" to (if (s.lastSync == 0L) "Waiting for first sync" else "${s.lastSyncCount} entries in the last sync")
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = T.bodyR, color = C.Ink)
            Text(sub, style = T.caption, color = C.InkSubtle)
        }
        when (s.phase) {
            FsPhase.Expired -> Text("Reconnect", style = T.smallB, color = C.Fuel, modifier = Modifier.press(onClick = onReconnect).padding(6.dp))
            FsPhase.Offline, FsPhase.Error -> Text("Retry", style = T.smallB, color = C.Fuel, modifier = Modifier.press(onClick = onRetry).padding(6.dp))
            else -> {}
        }
    }
}

@Composable
private fun ServerForm(s: FsStatus, onSave: (String, String) -> Unit) {
    var url by remember(s.serverUrl) { mutableStateOf(s.serverUrl) }
    var key by remember(s.appKey) { mutableStateOf(s.appKey) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "FatSecret's diary needs 3-legged OAuth 1.0 signed with a secret key that must not live in the app. " +
                "Deploy the small proxy from server/fatsecret-proxy (free Cloudflare Worker) and paste its address here.",
            style = T.caption, color = C.InkMuted,
        )
        LifeField(url, { url = it }, "Server URL", keyboard = KeyboardType.Uri, placeholder = "https://lifeapi-fatsecret.<you>.workers.dev")
        LifeField(key, { key = it }, "App key (optional)", placeholder = "APP_SHARED_KEY from the server")
        PillButton("Save", C.Fuel, C.Graphite0, Modifier.fillMaxWidth(), height = 48.dp, radius = 14.dp, enabled = url.isNotBlank(), style = T.section) { onSave(url, key) }
    }
}

@Composable
private fun AuthorizingBlock(s: FsStatus, onFinish: (String) -> Unit, onRestart: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            s.message.ifBlank { "Approve LifeAPI on fatsecret.com. The app finishes on its own when you come back; if it doesn't, enter the code shown on the page." },
            style = T.caption, color = C.InkMuted,
        )
        LifeField(code, { code = it }, "Verification code")
        PillButton("Finish", C.Fuel, C.Graphite0, Modifier.fillMaxWidth(), height = 48.dp, radius = 14.dp, enabled = code.isNotBlank(), style = T.section) { onFinish(code) }
        GhostButton("Open fatsecret.com again", Modifier.fillMaxWidth(), onClick = onRestart)
    }
}

/** Compact live card for Nutrition / History: status + last sync + Sync now; tap opens the connection screen. */
@Composable
fun FatSecretSourceCard() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { FatSecret.init(ctx) }
    val s by FatSecret.status.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    LifeCard(Modifier.fillMaxWidth().press { nav.navigate(FATSECRET_ROUTE) }, gap = 12.dp, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FsBadge()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("FatSecret", style = T.section, color = C.Ink)
                Text(
                    when (s.phase) {
                        FsPhase.NotConfigured, FsPhase.Disconnected -> "Not connected · tap to set up"
                        FsPhase.Authorizing -> "Waiting for approval"
                        FsPhase.Expired -> "Sign-in expired · tap to reconnect"
                        FsPhase.Syncing -> if (s.progress > 0) "Syncing ${s.progress} entries…" else "Syncing…"
                        FsPhase.Offline -> "Offline · last sync ${lastSyncLabel(s)}"
                        FsPhase.Error -> s.message.ifBlank { "Sync failed" }
                        FsPhase.Connected -> "Last sync ${lastSyncLabel(s)}"
                    },
                    style = T.caption, color = C.InkSubtle, maxLines = 2,
                )
            }
            StatusPill(s.phase)
        }
        if (s.phase == FsPhase.Connected || s.phase == FsPhase.Offline || s.phase == FsPhase.Error) {
            GhostButton("Sync now", Modifier.fillMaxWidth()) { FatSecret.syncNow(1) }
        }
    }
}
