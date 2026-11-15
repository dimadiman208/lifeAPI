package com.lifeapi.app.ui.screens.body.nutrition

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Food
import com.lifeapi.app.data.Meal
import com.lifeapi.app.data.hhmm
import com.lifeapi.app.ui.LocalActions
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.body.BodySection
import com.lifeapi.app.ui.screens.body.EmptyLine
import com.lifeapi.app.ui.screens.body.EntryRow
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.NutritionState
import com.lifeapi.app.vm.NutritionVM
import kotlinx.coroutines.launch
import com.lifeapi.app.data.fatsecret.FsPhase
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Opens the real FatSecret app (or its Play Store page). Returning to LifeAPI forces a sync. */
fun openFatSecret(context: android.content.Context) {
    com.lifeapi.app.data.fatsecret.FatSecret.markOpenedFatSecret()
    val pkg = "com.fatsecret.android"
    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
    val intent = launch ?: android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg"))
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * FatSecret is the only way food gets in: big "Open FatSecret" button + a subtle sync line. While Nutrition is
 * visible it re-syncs every 2 minutes. Not connected → compact connect state.
 */
@Composable
fun NutritionActions(@Suppress("UNUSED_PARAMETER") st: NutritionState) {
    val nav = LocalNav.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val fs by com.lifeapi.app.data.fatsecret.FatSecret.status.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(fs.connected) {
        if (!fs.connected) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(120_000)
            com.lifeapi.app.data.fatsecret.FatSecret.autoSync(force = true)
        }
    }
    val openBtn: @Composable (Modifier) -> Unit = { m ->
        Row(
            m.height(52.dp).clip(RoundedCornerShape(16.dp)).background(C.Fuel).press(haptic = true) { openFatSecret(ctx) }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(C.Graphite0), contentAlignment = Alignment.Center,
            ) { Text("FS", style = T.tiny.copy(fontWeight = FontWeight.Bold), color = C.Fuel) }
            Text("Open FatSecret", style = T.section, color = C.Graphite0)
            androidx.compose.material3.Icon(NIc.Open, null, tint = C.Graphite0, modifier = Modifier.size(18.dp))
        }
    }
    if (!fs.connected && fs.phase != FsPhase.Expired) {
        BodySection {
            Text("Food comes from FatSecret", style = T.section, color = C.Ink)
            Text("Log meals in the FatSecret app — LifeAPI mirrors your diary automatically every time you open it.", style = T.caption, color = C.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Connect FatSecret", C.FuelTint, C.Fuel, Modifier.weight(1f), 48.dp, 14.dp) {
                    nav.navigate(com.lifeapi.app.ui.screens.body.fatsecret.FATSECRET_ROUTE)
                }
                openBtn(Modifier.weight(1f))
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        openBtn(Modifier.fillMaxWidth())
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).press { com.lifeapi.app.data.fatsecret.FatSecret.syncNow(2) }.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val syncing = fs.phase == FsPhase.Syncing
            if (syncing) androidx.compose.material3.CircularProgressIndicator(Modifier.size(12.dp), color = C.Fuel, strokeWidth = 1.5.dp)
            else androidx.compose.material3.Icon(NIc.Sync, null, tint = C.InkSubtle, modifier = Modifier.size(13.dp))
            val line = when (fs.phase) {
                FsPhase.Syncing -> "Syncing with FatSecret…"
                FsPhase.Expired -> "FatSecret sign-in expired — reconnect in the FatSecret card below"
                FsPhase.Offline -> "Offline — showing saved entries" + (if (fs.lastSync > 0) " · synced ${hhmm(Days.timeOf(fs.lastSync))}" else "")
                FsPhase.Error -> "Sync failed: ${fs.message.take(80)}"
                else -> if (fs.lastSync > 0) "Synced · ${hhmm(Days.timeOf(fs.lastSync))}" + (if (Days.dayOf(fs.lastSync) != Days.today()) " ${dayLabel(Days.dayOf(fs.lastSync))}" else "") else "Not synced yet"
            }
            Text(line, style = T.caption, color = if (fs.phase == FsPhase.Error || fs.phase == FsPhase.Expired) C.Negative else C.InkSubtle, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/** One card per meal slot: entries (tap = view/copy/edit old manual ones, swipe = delete). */
@Composable
fun SlotCard(slot: String, st: NutritionState, vm: NutritionVM) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var editing by remember { mutableStateOf<Pair<Meal, Food?>?>(null) }
    val meals = st.slot(slot)
    BodySection(Modifier.animateContentSize(tween(220))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(slot, style = T.section, color = C.Ink)
                val k = meals.sumOf { it.kcal }
                Text(
                    if (meals.isEmpty()) "Nothing logged" else "$k kcal · P ${meals.sumOf { it.protein }} · C ${meals.sumOf { it.carbs }} · F ${meals.sumOf { it.fat }}",
                    style = T.caption, color = if (meals.isEmpty()) C.InkFaint else C.Fuel,
                )
            }
        }
        meals.forEach { m ->
            key(m.id) {
                EntryRow(
                    hhmm(Days.timeOf(m.ts)), m.items,
                    trailing = "${m.kcal}",
                    sub = (if (m.source == "FatSecret") "FatSecret · " else "") +
                        (if (m.protein + m.carbs + m.fat > 0) "P ${m.protein} · C ${m.carbs} · F ${m.fat}" else ""),
                    onEdit = { scope.launch { editing = m to vm.foodOf(m) } },
                    // FatSecret rows are mirrored — removing them here would undo itself on the next sync
                    onDelete = { if (m.source == "FatSecret") openFatSecret(ctx) else vm.deleteMeal(m) },
                )
            }
        }
        if (meals.isEmpty()) EmptyLine("Nothing in FatSecret for this ${slot.lowercase()}")
    }
    editing?.let { (m, f) ->
        MealEditSheet(
            m, f, onDismiss = { editing = null },
            onDelete = { vm.deleteMeal(m) },
            onSave = { vm.updateMeal(m, it) },
            onCopy = { d, s -> vm.copyMeals(listOf(m), d, s) },
        )
    }
}

@Composable
fun ManageGoalsButton(st: NutritionState, vm: NutritionVM) {
    var open by remember { mutableStateOf(false) }
    GhostButton("Manage goals", Modifier.fillMaxWidth(), height = 40.dp) { open = true }
    if (open) NutritionGoalsSheet(st.profile, onDismiss = { open = false }) { vm.saveGoals(it) }
}

