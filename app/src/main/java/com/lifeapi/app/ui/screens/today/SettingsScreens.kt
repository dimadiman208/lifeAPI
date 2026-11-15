package com.lifeapi.app.ui.screens.today

import android.Manifest
import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Profile
import com.lifeapi.app.data.backup.Backup
import com.lifeapi.app.notify.Reminders
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.ProfileSheet
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.BaseVM
import com.lifeapi.app.vm.cardLayout
import com.lifeapi.app.vm.today.AiCat
import com.lifeapi.app.vm.today.aiCats
import com.lifeapi.app.vm.today.aiOn
import com.lifeapi.app.vm.today.aiString
import com.lifeapi.app.vm.today.enabledGoals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DbStats(val bytes: Long, val counts: Map<String, Int>)

class SettingsVM(app: Application) : BaseVM(app) {
    val prof = profile.state()
    val stats = MutableStateFlow<DbStats?>(null)
    val busy = MutableStateFlow(false)

    fun loadStats() = launch {
        stats.value = DbStats(Backup.dbSizeBytes(getApplication()), Backup.rowCounts(repo.db))
    }

    fun update(f: (Profile) -> Profile) = launch { dao.saveProfile(f(dao.profileNow() ?: Profile())) }

    fun export(uri: Uri) = launch {
        busy.value = true
        runCatching { Backup.export(getApplication(), repo.db, uri) }
            .onSuccess { n -> update { it.copy(lastBackupTs = Days.now()) }; c.snack("Backup saved · $n rows") }
            .onFailure { c.snack("Backup failed: ${it.message ?: "unknown error"}") }
        busy.value = false
    }

    fun import(uri: Uri) = launch {
        busy.value = true
        val prevBackup = dao.profileNow()?.lastBackupTs ?: 0L
        runCatching { Backup.import(getApplication(), repo, uri) }
            .onSuccess { n -> update { it.copy(lastBackupTs = maxOf(it.lastBackupTs, prevBackup)) }; c.snack("Restored $n rows"); loadStats() }
            .onFailure { c.snack("Restore failed: ${it.message ?: "not a LifeAPI backup"}") }
        busy.value = false
    }

    fun erase() = launch {
        Backup.eraseAll(repo)
        com.lifeapi.app.ui.screens.today.LockStore.clearPin(getApplication())
        c.snack("All data erased")
        loadStats()
    }

    fun refreshReminders() = launch { runCatching { Reminders.run(getApplication(), repo, fire = false) } }
}

private fun mb(bytes: Long) = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
private fun appVersion(ctx: android.content.Context) = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "—"

private fun lastBackupLabel(ts: Long): String {
    if (ts == 0L) return "Never backed up"
    val d = Days.dayOf(ts)
    val t = Days.timeOf(ts).format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (Days.today() - d) { 0L -> "Today $t"; 1L -> "Yesterday $t"; else -> "${com.lifeapi.app.vm.today.dayLabel(d)} $t" }
}

// ============================================================ home

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsVM = viewModel()) {
    val pp by vm.prof.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val p = pp ?: return
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var profileOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.loadStats() }
    val lock = p.lockEnabled && LockStore.hasPin(ctx)
    val notifOn = listOf(p.notifySupplements, p.notifyWindDown, p.notifyTasks).count { it }

    DetailList {
        item { DetailTop("Settings", onBack) }
        item {
            Row(Modifier.fillMaxWidth().press { profileOpen = true }.padding(horizontal = 2.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF26282E)), contentAlignment = Alignment.Center) {
                    Text(p.name.take(1).uppercase(), style = T.title.copy(fontSize = 20.sp), color = C.Ink)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(p.name, style = T.section.copy(fontSize = 16.sp), color = C.Ink)
                    Text("On this phone only · ${stats?.let { mb(it.bytes) } ?: "…"} · no account", style = T.caption, color = C.InkSubtle)
                }
            }
        }
        item {
            GroupCard {
                SRow("Daily goals", value = "${p.enabledGoals().size} goals", first = true, badge = "◎", badgeBg = Color(0xFF1F2610), badgeFg = C.Volt, chevron = true) { nav.navigate("settings/goals") }
                SRow("Today layout", value = "${p.cardLayout().count { it.second }} cards", badge = "▦", badgeBg = Color(0xFF1F2610), badgeFg = C.Volt, chevron = true) { nav.navigate("today/customize") }
                SRow("Notifications", value = "$notifOn on", badge = "♪", chevron = true) { nav.navigate("settings/notifications") }
            }
        }
        item {
            GroupCard {
                SRow("Profile & targets", sub = "Name, water, calories, macros, sleep, rest timer", first = true, badge = "Aa", chevron = true) { profileOpen = true }
                SRow("Units", value = "kg · ml · km", badge = "⚖", chevron = true) { sheet = "units" }
                SRow("Currency", value = p.currency, badge = "€", badgeBg = C.MoneyTint, badgeFg = C.Money, chevron = true) { sheet = "currency" }
                SRow("Appearance", value = "Dark · Graphite", badge = "◐", chevron = true) { sheet = "appearance" }
            }
        }
        item {
            GroupCard {
                val nConnected = connectedIntegrations()
                SRow("Integrations", sub = "FatSecret · Swedbank · Binance", value = "$nConnected connected", first = true, badge = "⇄", badgeBg = C.GrowTint, badgeFg = C.Grow, chevron = true) { nav.navigate("settings/integrations") }
                SRow("Security", sub = if (lock) (if (LockStore.bio(ctx)) "PIN + fingerprint" else "PIN") else "No lock", value = if (lock) "On" else "Off", badge = "⚿", badgeBg = C.HealthTint, badgeFg = C.Health, chevron = true) { nav.navigate("settings/security") }
                SRow("Data & backup", sub = "Last backup: ${lastBackupLabel(p.lastBackupTs)}", badge = "⤓", badgeBg = C.HealthTint, badgeFg = C.Health, chevron = true) { nav.navigate("settings/security") }
                SRow(
                    "Intelligence & privacy", sub = if (p.aiOn()) "${p.aiCats().size} of ${AiCat.entries.size} categories · on-device" else "Off",
                    badge = "✦", badgeBg = Color(0xFF1F2610), badgeFg = C.Volt, chevron = true,
                ) { nav.navigate("settings/privacy") }
            }
        }
        item {
            Text(
                "LifeAPI ${appVersion(ctx)} · local database v${runCatching { (ctx.applicationContext as com.lifeapi.app.LifeApp).container.db.openHelper.readableDatabase.version }.getOrDefault(0)}",
                style = T.caption, color = C.InkFaint, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (profileOpen) ProfileSheet(p, onDismiss = { profileOpen = false }) { np -> vm.update { np } }
    when (sheet) {
        "units" -> InfoSheet("Units", "LifeAPI uses metric units: kilograms for weights, millilitres and litres for water, kilometres for distance, 24-hour time.") { sheet = null }
        "appearance" -> InfoSheet("Appearance", "Graphite dark theme with Volt accents. Module colours: teal Health, amber Fuel, blue Money, coral Train, violet Grow.") { sheet = null }
        "currency" -> CurrencySheet(p.currency, onDismiss = { sheet = null }) { cur -> vm.update { it.copy(currency = cur) } }
    }
}

@Composable
private fun InfoSheet(title: String, body: String, onDismiss: () -> Unit) {
    FormSheet(title, onDismiss, confirm = "OK", onConfirm = onDismiss) {
        Text(body, style = T.bodyR.copy(lineHeight = 21.sp), color = C.InkMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencySheet(cur: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var sel by remember { mutableStateOf(cur) }
    FormSheet("Currency", onDismiss, onConfirm = { onPick(sel); onDismiss() }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("EUR", "USD", "GBP", "CHF", "PLN", "SEK", "NOK", "CZK").forEach { c -> ChipToggle(c, c == sel) { sel = c } }
        }
        Text("Stored as your preference. Amounts are entered and shown in one currency; there's no conversion offline.", style = T.caption, color = C.InkSubtle)
    }
}

// ============================================================ notifications

@Composable
fun NotificationsScreen(onBack: () -> Unit, vm: SettingsVM = viewModel()) {
    val pp by vm.prof.collectAsStateWithLifecycle()
    val p = pp ?: return
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Reminders.canNotify(ctx)) }
    var pending by remember { mutableStateOf<((Profile) -> Profile)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) pending?.let { vm.update(it) }
        pending = null
    }
    fun enable(f: (Profile) -> Profile, turningOn: Boolean) {
        if (turningOn && !granted && Build.VERSION.SDK_INT >= 33) { pending = f; launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else vm.update(f)
    }
    var windOpen by remember { mutableStateOf(false) }
    val supps by remember {
        val r = (ctx.applicationContext as com.lifeapi.app.LifeApp).container.repo
        kotlinx.coroutines.flow.combine(r.dao.supplements(), r.body.rules()) { l, rules ->
            val ids = l.filter { it.reminder }.map { it.id }.toSet()
            val fromRules = rules.filter { it.supplementId in ids }.flatMap { com.lifeapi.app.data.supps.DoseCodec.parse(it.doses) }.map { it.time }
            (fromRules + l.filter { it.reminder && rules.none { r2 -> r2.supplementId == it.id } }.map { it.time }).distinct().sorted()
        }
    }.collectAsStateWithLifecycle(emptyList())

    NotificationsBody(p, supps, onToggle = ::enable, onWind = { windOpen = true }, onBack = onBack, granted = granted, onAllow = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) })
    LaunchedEffect(p.notifySupplements, p.notifyWindDown, p.notifyTasks, p.windDown) { vm.refreshReminders() }
    if (windOpen) {
        var v by remember { mutableStateOf(p.windDown) }
        val ok = Regex("""^([01]?\d|2[0-3]):[0-5]\d$""").matches(v.trim())
        FormSheet("Wind-down time", { windOpen = false }, canConfirm = ok, onConfirm = { vm.update { it.copy(windDown = v.trim().padStart(5, '0')) }; windOpen = false }) {
            LifeField(v, { v = it }, "Time, HH:MM", placeholder = "23:00")
        }
    }
}

@Composable
private fun NotificationsBody(
    p: Profile, supps: List<String>, onToggle: ((Profile) -> Profile, Boolean) -> Unit, onWind: () -> Unit, onBack: () -> Unit,
    granted: Boolean, onAllow: () -> Unit,
) {
    DetailList {
        item { DetailTop("Notifications", onBack) }
        if (!granted && Build.VERSION.SDK_INT >= 33) item {
            GroupCard(border = Color(0xFF3A2420)) {
                SRow("Notifications are blocked", sub = "Allow them so reminders can reach you", value = "Allow", valueColor = C.Volt, first = true, onClick = onAllow)
            }
        }
        item { Cap("Reminders") }
        item {
            GroupCard {
                SRow("Supplements", sub = if (supps.isEmpty()) "Grouped per time slot · set times in Body" else "Grouped per time slot · ${supps.joinToString(", ")}", first = true, trailing = {
                    LifeSwitch(p.notifySupplements, onColor = C.Health) { v -> onToggle({ it.copy(notifySupplements = v) }, v) }
                })
                SRow("Wind-down", sub = "Nudge to start winding down at ${p.windDown}", trailing = {
                    LifeSwitch(p.notifyWindDown, onColor = C.Health) { v -> onToggle({ it.copy(notifyWindDown = v) }, v) }
                })
                SRow("Wind-down time", value = p.windDown, chevron = true, onClick = onWind)
                SRow("Tasks", sub = "At the task's time, minus its reminder offset", trailing = {
                    LifeSwitch(p.notifyTasks, onColor = C.Grow) { v -> onToggle({ it.copy(notifyTasks = v) }, v) }
                })
            }
        }
        item {
            Text("Reminders are scheduled on this phone. A supplement you've already ticked won't notify, and the \"Taken\" button logs the whole slot.", style = T.caption, color = C.InkSubtle, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

// ============================================================ security & data

@Composable
fun SecurityScreen(onBack: () -> Unit, vm: SettingsVM = viewModel()) {
    val pp by vm.prof.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val p = pp ?: return
    val ctx = LocalContext.current
    var pinSetup by remember { mutableStateOf(false) }
    var pinCheck by remember { mutableStateOf(false) }
    var bio by remember { mutableStateOf(LockStore.bio(ctx)) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var erase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.loadStats() }
    val lock = p.lockEnabled && LockStore.hasPin(ctx)
    val bioAvail = remember { LockStore.bioAvailable(ctx) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.export(uri) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }
    fun backupName() = "lifeapi-backup-${java.time.LocalDate.now()}.json"

    DetailList {
        item { DetailTop("Security & data", onBack) }
        item { Cap("App lock") }
        item {
            GroupCard {
                SRow("Lock with PIN", sub = if (lock) "4-digit PIN · stored as a salted hash" else null, first = true, trailing = {
                    LifeSwitch(lock, onColor = C.Health) { on -> if (on) pinSetup = true else pinCheck = true }
                })
                SRow("Unlock with fingerprint", sub = if (!bioAvail) "No fingerprint enrolled on this phone" else null, trailing = {
                    LifeSwitch(lock && bio, enabled = lock && bioAvail, onColor = C.Health) { on -> LockStore.setBio(ctx, on); bio = on }
                })
                SRow("Lock after", value = when (p.lockAfterSec) { 0 -> "Immediately"; 60 -> "1 minute"; 300 -> "5 minutes"; else -> "${p.lockAfterSec / 60} minutes" }, chevron = true) {
                    val opts = listOf(0, 60, 300, 900)
                    vm.update { it.copy(lockAfterSec = opts[(opts.indexOf(it.lockAfterSec) + 1) % opts.size]) }
                }
                SRow("Hide amounts", sub = "Masks balances in Money and blanks the app in the app switcher", trailing = {
                    LifeSwitch(p.hideBalances, onColor = C.Health) { v -> vm.update { it.copy(hideBalances = v) } }
                })
            }
        }
        item { Cap("Backup · all data stays on device") }
        item {
            GroupCard {
                SRow("Last backup", sub = lastBackupLabel(p.lastBackupTs), value = if (busy) "Working…" else "Back up now", valueColor = C.Health, first = true) {
                    if (!busy) exportLauncher.launch(backupName())
                }
                SRow("Export", sub = "Full JSON · every table", chevron = true) { if (!busy) exportLauncher.launch(backupName()) }
                SRow("Import or restore", sub = "Replaces everything with a LifeAPI backup", chevron = true) { if (!busy) importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            }
        }
        item {
            val st = stats
            GroupCard {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text("Storage", style = T.small, color = C.Ink, modifier = Modifier.weight(1f))
                        Text(st?.let { mb(it.bytes) } ?: "…", style = T.small, color = C.InkSubtle)
                    }
                    if (st != null) {
                        val per = Backup.MODULES.mapValues { (_, ts) -> ts.sumOf { st.counts[it] ?: 0 } }
                        val total = per.values.sum().coerceAtLeast(1)
                        val hues = mapOf("Money" to C.Money, "Train" to C.Train, "Health" to C.Health, "Food" to C.Fuel, "Grow" to C.Grow)
                        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (per.values.sum() == 0) Box(Modifier.fillMaxSize().background(C.Track))
                            per.forEach { (k, v) -> if (v > 0) Box(Modifier.weight(v.toFloat()).fillMaxSize().background(hues[k] ?: C.Ghost)) }
                        }
                        Text(
                            per.entries.joinToString(" · ") { (k, v) -> "$k ${String.format(Locale.US, "%.1f", st.bytes / 1_048_576.0 * v / total)}" } + " MB",
                            style = T.tiny.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = C.InkSubtle,
                        )
                    }
                }
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF3A2420), RoundedCornerShape(14.dp)).press { erase = 1 },
                contentAlignment = Alignment.Center,
            ) { Text("Erase all data…", style = T.body, color = C.Negative) }
        }
    }

    if (pinSetup) PinSetupDialog(onDismiss = { pinSetup = false }) { pin ->
        LockStore.setPin(ctx, pin); vm.update { it.copy(lockEnabled = true) }; pinSetup = false
    }
    if (pinCheck) Dialog(onDismissRequest = { pinCheck = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PinPad("Current PIN", "Enter it to turn the lock off") { pin ->
            LockStore.check(ctx, pin).also { ok ->
                if (ok) { LockStore.clearPin(ctx); LockStore.setBio(ctx, false); bio = false; vm.update { it.copy(lockEnabled = false) }; pinCheck = false }
            }
        }
    }
    restoreUri?.let { uri ->
        Confirm(
            "Restore this backup?", "Everything currently in LifeAPI will be replaced by the file's contents. Make a backup first if you're unsure.",
            "Restore", onDismiss = { restoreUri = null },
        ) { vm.import(uri); restoreUri = null }
    }
    if (erase == 1) Confirm("Erase all data?", "Every log, account, workout, project and setting on this phone will be deleted.", "Continue", onDismiss = { erase = 0 }) { erase = 2 }
    if (erase == 2) Confirm("This can't be undone", "Your data has no copy anywhere else unless you exported a backup. Erase everything now?", "Erase everything", onDismiss = { erase = 0 }) {
        vm.erase(); erase = 0
    }
}

@Composable
fun Confirm(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Graphite2,
        title = { Text(title, style = T.title.copy(fontSize = 19.sp), color = C.Ink) },
        text = { Text(body, style = T.bodyR, color = C.InkMuted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, style = T.smallB, color = C.Negative) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = T.smallB, color = C.InkSoft) } },
    )
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (first == null) PinPad("Choose a PIN", "4 digits · you'll enter it to open LifeAPI") { first = it; true }
        else PinPad("Repeat PIN", "Enter the same 4 digits") { pin -> (pin == first).also { ok -> if (ok) onDone(pin) else first = null } }
    }
}

// ============================================================ privacy

@Composable
fun PrivacyScreen(onBack: () -> Unit, vm: SettingsVM = viewModel()) {
    val pp by vm.prof.collectAsStateWithLifecycle()
    val p = pp ?: return
    val on = p.aiOn()
    val cats = p.aiCats()
    var preview by remember { mutableStateOf(false) }
    DetailList {
        item { DetailTop("Intelligence & privacy", onBack) }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF12140F))
                    .border(1.dp, Color(0xFF262D18), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("LifeAPI Intelligence", style = T.section, color = C.Ink)
                    Text("Runs on this phone · rule-based · nothing is sent", style = T.caption, color = C.InkMuted)
                }
                LifeSwitch(on) { v -> vm.update { it.copy(aiCategories = aiString(v, it.aiCats())) } }
            }
        }
        item { Cap("What it may read") }
        item {
            GroupCard(Modifier.alpha(if (on) 1f else 0.45f)) {
                AiCat.entries.forEachIndexed { i, c ->
                    SRow(c.label, sub = c.what, first = i == 0, trailing = {
                        LifeSwitch(on && c in cats, enabled = on) { v -> vm.update { it.copy(aiCategories = aiString(true, if (v) it.aiCats() + c else it.aiCats() - c)) } }
                    })
                }
            }
        }
        item {
            GroupCard {
                SRow("Preview what it reads", first = true, chevron = true) { preview = true }
            }
        }
        item {
            Text(
                "Insights are computed locally from the categories above and never leave this phone. Individual transactions only appear in an insight's supporting rows if you allow them.",
                style = T.caption.copy(lineHeight = 18.sp), color = C.InkSubtle, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
    if (preview) FormSheet("What Intelligence reads", { preview = false }, confirm = "OK", onConfirm = { preview = false }) {
        if (!on) Text("Nothing — Intelligence is off.", style = T.bodyR, color = C.InkMuted)
        else AiCat.entries.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.label, style = T.body, color = if (c in cats) C.Ink else C.InkFaint, modifier = Modifier.weight(1f))
                MonoLabel(if (c in cats) "Read" else "Skipped", if (c in cats) C.Volt else C.InkFaint)
            }
        }
        Text("Window: the last 70 days. Output: summaries, next actions, patterns and reports shown in the app only.", style = T.caption, color = C.InkSubtle)
    }
}
