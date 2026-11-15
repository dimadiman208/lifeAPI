package com.lifeapi.app.ui.screens.body

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Profile
import com.lifeapi.app.data.SleepLog
import com.lifeapi.app.data.WaterLog
import com.lifeapi.app.data.WeightLog
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.data.hhmm
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.LinearBar
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.body.BodyHealthVM
import com.lifeapi.app.vm.body.HealthTab
import com.lifeapi.app.vm.body.fmtHm
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private fun dowShort(d: Long) = Days.date(d).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
private fun dateShort(d: Long) = Days.date(d).let { "${dowShort(d)} ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" }
private fun forDay(s: HealthTab) = if (s.isToday) "today" else s.selectedLabel.replaceFirstChar { it.lowercase() }

// ===================================================================== Water

@Composable
fun WaterSection(s: HealthTab, vm: BodyHealthVM) {
    var custom by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WaterLog?>(null) }
    var settings by remember { mutableStateOf(false) }
    val canLog = !s.isFuture
    BodySection {
        BodySectionHeader(
            Ic.Drop, "Water",
            value = String.format(Locale.US, "%.2f L", s.waterTotal / 1000f), goal = String.format(Locale.US, "%.1f L", s.waterGoal / 1000f),
            onAdd = if (canLog) ({ custom = true }) else null,
        )
        LinearBar(s.waterTotal.toFloat() / s.waterGoal.coerceAtLeast(1), C.Health, Color(0xFF1C2524), 5.dp)
        if (canLog) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.waterQuick.take(4).forEachIndexed { i, a ->
                PillButton("+$a", if (i == 0) C.HealthTint else C.Graphite2, if (i == 0) C.Health else C.Ink, Modifier.weight(1f), 40.dp, 12.dp) { vm.addWater(a, s.selected) }
            }
        }
        if (s.water.isEmpty()) EmptyLine(if (s.isFuture) "Future day — nothing to log yet" else "No water logged ${forDay(s)}")
        else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.water.forEach { w ->
                androidx.compose.runtime.key(w.id) {
                    EntryRow(hhmm(Days.timeOf(w.ts)), "${w.ml} ml", onEdit = { editing = w }, onDelete = { vm.deleteWater(w) })
                }
            }
        }
        com.lifeapi.app.ui.components.GhostButton("Manage water", Modifier.fillMaxWidth(), height = 40.dp) { settings = true }
    }
    if (custom) WaterAmountSheet("Add water", "for ${forDay(s)}", 250, onDismiss = { custom = false }) { vm.addWater(it, s.selected) }
    editing?.let { w ->
        WaterAmountSheet("Edit entry", "${hhmm(Days.timeOf(w.ts))} · ${dateShort(w.day)}", w.ml, onDismiss = { editing = null }, onDelete = { vm.deleteWater(w); editing = null }) { vm.editWater(w, it) }
    }
    if (settings) WaterSettingsSheet(s.profile, s.waterQuick, onDismiss = { settings = false }) { a, g -> vm.saveWaterSettings(s.profile, a, g) }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun WaterAmountSheet(title: String, subtitle: String, initial: Int, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (Int) -> Unit) {
    var v by remember { mutableStateOf(initial.toString()) }
    val ml = v.trim().toIntOrNull()
    EntrySheet(title, onDismiss, canConfirm = ml != null && ml in 1..5000, subtitle = subtitle, onDelete = onDelete, onConfirm = { onSave(ml!!); onDismiss() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            PillButton("−50", C.Graphite3, C.Ink, height = 48.dp, radius = 14.dp) { v = ((ml ?: 0) - 50).coerceAtLeast(50).toString() }
            LifeField(v, { x -> v = x.filter(Char::isDigit).take(4) }, "Millilitres", Modifier.weight(1f), KeyboardType.Number)
            PillButton("+50", C.Graphite3, C.Ink, height = 48.dp, radius = 14.dp) { v = ((ml ?: 0) + 50).coerceAtMost(5000).toString() }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(150, 200, 250, 330, 400, 500, 750, 1000).forEach { a -> BChip("$a ml", ml == a, C.Health, C.HealthTint) { v = a.toString() } }
        }
    }
}

@Composable
private fun WaterSettingsSheet(p: Profile, current: List<Int>, onDismiss: () -> Unit, onSave: (List<Int>, Int) -> Unit) {
    val fields = remember { List(4) { i -> mutableStateOf(current.getOrNull(i)?.toString() ?: "") } }
    var goal by remember { mutableStateOf(p.waterGoalMl.toString()) }
    val amounts = fields.mapNotNull { it.value.trim().toIntOrNull()?.takeIf { v -> v in 1..5000 } }.distinct()
    val g = goal.trim().toIntOrNull()
    EntrySheet("Water settings", onDismiss, canConfirm = amounts.isNotEmpty() && g != null && g in 250..10000, onConfirm = { onSave(amounts, g!!); onDismiss() }) {
        LifeField(goal, { goal = it.filter(Char::isDigit).take(5) }, "Daily goal, ml", keyboard = KeyboardType.Number)
        Text("Quick-add buttons (up to 4)", style = T.caption, color = C.InkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.forEachIndexed { i, f -> LifeField(f.value, { f.value = it.filter(Char::isDigit).take(4) }, "#${i + 1}", Modifier.weight(1f), KeyboardType.Number) }
        }
    }
}

// ===================================================================== Sleep

@Composable
fun SleepSection(s: HealthTab, vm: BodyHealthVM) {
    /** Night being edited: wake day + existing entry. */
    var editing by remember { mutableStateOf<Pair<Long, SleepLog?>?>(null) }
    val canLog = !s.isFuture
    BodySection {
        BodySectionHeader(
            Ic.Moon, "Sleep",
            value = s.sleep?.let { fmtHm(it.minutes) } ?: "—", goal = fmtHm(s.sleepGoal),
            onAdd = if (canLog) ({ editing = s.selected to s.sleep }) else null,
        )
        val night = s.sleep
        if (night != null) {
            androidx.compose.runtime.key(night.id) {
                EntryRow(
                    "${hhmm(Days.timeOf(night.startTs))}→${hhmm(Days.timeOf(night.endTs))}", fmtHm(night.minutes),
                    sub = sleepSub(night) ?: "Night to ${dateShort(night.day)}", highlight = true,
                    trailing = night.rating?.let { "$it/5" }, trailingColor = C.InkMuted,
                    onEdit = { editing = night.day to night }, onDelete = { vm.deleteSleep(night) },
                )
            }
        } else if (canLog) {
            EmptyLine(if (s.isToday) "No sleep logged for last night — tap +" else "No sleep logged for ${dateShort(s.selected)} — tap +")
        } else EmptyLine("Future day — nothing to log yet")
        val recent = s.sleepRecent.filter { it.day != s.selected }.take(4)
        if (recent.isNotEmpty()) {
            MonoLabel("RECENT NIGHTS")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recent.forEach { l ->
                    androidx.compose.runtime.key(l.id) {
                        EntryRow(
                            dowShort(l.day), fmtHm(l.minutes), sub = "${hhmm(Days.timeOf(l.startTs))} → ${hhmm(Days.timeOf(l.endTs))}" + (sleepSub(l)?.let { " · $it" } ?: ""),
                            trailing = l.rating?.let { "$it/5" }, trailingColor = if (l.minutes >= s.sleepGoal) C.Health else C.InkMuted,
                            onEdit = { editing = l.day to l }, onDelete = { vm.deleteSleep(l) },
                        )
                    }
                }
            }
        }
    }
    editing?.let { (day, log) ->
        SleepSheet(day, log, onDismiss = { editing = null }, onDelete = log?.let { l -> { vm.deleteSleep(l); editing = null } }) { bed, wake, r, tags ->
            vm.saveSleep(day, bed, wake, r, tags)
        }
    }
}

private fun sleepSub(l: SleepLog): String? = l.tags.split(',').filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(", ")

/** Bed / wake tiles with auto duration; bed later than wake = the evening before. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun SleepSheet(day: Long, log: SleepLog?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (LocalTime, LocalTime, Int?, Set<String>) -> Unit) {
    var bed by remember { mutableStateOf(log?.let { Days.timeOf(it.startTs) } ?: LocalTime.of(23, 30)) }
    var wake by remember { mutableStateOf(log?.let { Days.timeOf(it.endTs) } ?: LocalTime.of(7, 0)) }
    var rating by remember { mutableStateOf(log?.rating) }
    var tags by remember { mutableStateOf(log?.tags?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()) }
    var pick by remember { mutableStateOf<Int?>(null) }
    var mins = java.time.Duration.between(bed, wake).toMinutes().toInt()
    if (mins <= 0) mins += 24 * 60
    val bedDay = if (bed >= wake) day - 1 else day
    EntrySheet(
        if (log == null) "Log sleep" else "Edit sleep", onDismiss,
        subtitle = "Night to ${dateShort(day)}", onDelete = onDelete,
        canConfirm = mins in 30..(20 * 60),
        onConfirm = { onSave(bed, wake, rating, tags); onDismiss() },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeTile("Lights out · ${dowShort(bedDay)}", bed, Modifier.weight(1f)) { pick = 0 }
            TimeTile("Woke · ${dowShort(day)}", wake, Modifier.weight(1f)) { pick = 1 }
        }
        Text(
            "${fmtHm(mins)} asleep" + if (bedDay < day) " · crosses midnight" else "",
            style = T.section, color = if (mins in 30..(20 * 60)) C.Health else C.Negative,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How rested? (optional)", style = T.caption, color = C.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { r ->
                    val on = rating == r
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(shape).background(if (on) C.Health else Color.Transparent)
                            .then(if (on) Modifier else Modifier.border(1.dp, C.Bar, shape))
                            .press(haptic = true) { rating = if (on) null else r },
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) { Text("$r", style = T.body.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), color = if (on) C.Graphite0 else C.InkSubtle) }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SLEEP_TAGS.forEach { t -> BChip(t, t in tags, C.Health, C.HealthTint) { tags = if (t in tags) tags - t else tags + t } }
        }
    }
    pick?.let { which ->
        TimePickDialog(if (which == 0) bed else wake, C.Health, onDismiss = { pick = null }) { if (which == 0) bed = it else wake = it }
    }
}

// ===================================================================== Weight

@Composable
fun WeightSection(s: HealthTab, vm: BodyHealthVM) {
    var editing by remember { mutableStateOf<Pair<Long, WeightLog?>?>(null) }
    var plan by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val canLog = !s.isFuture
    val goal = s.profile.weightGoalKg
    BodySection {
        BodySectionHeader(
            Ic.Spark, "Weight",
            value = (s.weightOnDay ?: s.lastWeight)?.let { "${fmtKg(it.kg)} kg" + if (s.weightOnDay == null) " · last" else "" } ?: "—",
            goal = goal?.let { "goal ${fmtKg(it)}" },
            onAdd = if (canLog) ({ editing = s.selected to s.weightOnDay }) else null,
        )
        if (s.weightOnDay == null && canLog) {
            PillButton(
                if (s.isToday) "Log today's weight" else "Log weight for ${dateShort(s.selected)}", C.HealthTint, C.Health,
                Modifier.fillMaxWidth(), 44.dp, 12.dp, icon = Ic.Plus,
            ) { editing = s.selected to null }
        }
        if (s.weightRecent.isEmpty()) EmptyLine("No weigh-ins yet — trend shows in the chart above")
        else {
            val list = if (showAll) s.weightRecent else s.weightRecent.take(5)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { w ->
                    val i = s.weightRecent.indexOf(w)
                    val prev = s.weightRecent.getOrNull(i + 1)
                    val d = prev?.let { w.kg - it.kg }
                    androidx.compose.runtime.key(w.day) {
                        EntryRow(
                            dateShort(w.day), "${fmtKg(w.kg)} kg", highlight = w.day == s.selected,
                            trailing = d?.let { (if (it >= 0) "+" else "−") + String.format(Locale.US, "%.1f", abs(it)) },
                            trailingColor = C.InkMuted,
                            onEdit = { editing = w.day to w }, onDelete = { vm.deleteWeight(w) },
                        )
                    }
                }
            }
            if (s.weightRecent.size > 5) Text(
                if (showAll) "Show less" else "Show ${s.weightRecent.size - 5} more", style = T.smallB, color = C.Health, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).press { showAll = !showAll }.padding(vertical = 8.dp),
            )
        }
        com.lifeapi.app.ui.components.GhostButton("Manage weight", Modifier.fillMaxWidth(), height = 40.dp) { plan = true }
    }
    editing?.let { (day, w) ->
        WeightSheet(day, w, s.lastWeight, onDismiss = { editing = null }, onDelete = w?.let { x -> { vm.deleteWeight(x); editing = null } }) { vm.saveWeight(day, it) }
    }
    if (plan) WeightPlanSheet(s.profile, s.lastWeight, onDismiss = { plan = false }) { vm.saveProfile(it) }
}

@Composable
private fun WeightSheet(day: Long, existing: WeightLog?, last: WeightLog?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Double) -> Unit) {
    var kg by remember { mutableStateOf((existing ?: last)?.kg?.let { fmtKg(it) } ?: "") }
    val v = kg.num()
    EntrySheet(
        if (existing == null) "Log weight" else "Edit weight", onDismiss,
        subtitle = dateShort(day) + (if (existing == null && last != null) " · last ${fmtKg(last.kg)} kg" else ""),
        canConfirm = v != null && v in 20.0..400.0, onDelete = onDelete,
        onConfirm = { onSave(Math.round(v!! * 10) / 10.0); onDismiss() },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            PillButton("−0.1", C.Graphite3, C.Ink, height = 48.dp, radius = 14.dp) { kg = fmtKg(((v ?: last?.kg ?: 70.0) - 0.1).let { Math.round(it * 10) / 10.0 }) }
            LifeField(kg, { kg = it.take(6) }, "Weight, kg", Modifier.weight(1f), KeyboardType.Decimal)
            PillButton("+0.1", C.Graphite3, C.Ink, height = 48.dp, radius = 14.dp) { kg = fmtKg(((v ?: last?.kg ?: 70.0) + 0.1).let { Math.round(it * 10) / 10.0 }) }
        }
        Text("Same time each morning gives the steadiest trend.", style = T.caption, color = C.InkSubtle)
    }
}

@Composable
private fun WeightPlanSheet(p: Profile, last: WeightLog?, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    val plans = listOf("Lean bulk", "Maintain", "Cut")
    var name by remember { mutableStateOf(p.weightPlan.ifBlank { "Lean bulk" }) }
    var start by remember { mutableStateOf(p.weightStartKg?.let { fmtKg(it) } ?: last?.let { fmtKg(it.kg) } ?: "") }
    var goal by remember { mutableStateOf(p.weightGoalKg?.let { fmtKg(it) } ?: "") }
    var rate by remember { mutableStateOf(String.format(Locale.US, "%.2f", p.weightRateKg)) }
    EntrySheet("Weight goal", onDismiss, canConfirm = rate.num() != null, onConfirm = {
        onSave(p.copy(weightPlan = name.trim(), weightStartKg = start.num(), weightGoalKg = goal.num(), weightRateKg = rate.num()!!))
        onDismiss()
    }) {
        Segmented(plans, plans.indexOf(name).coerceAtLeast(0), selBg = C.HealthTint, selFg = C.Health) {
            name = plans[it]
            rate = when (it) { 0 -> "0.25"; 1 -> "0.00"; else -> "-0.50" }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeField(start, { start = it }, "Start, kg", Modifier.weight(1f), KeyboardType.Decimal)
            LifeField(goal, { goal = it }, "Goal, kg", Modifier.weight(1f), KeyboardType.Decimal)
        }
        LifeField(rate, { rate = it }, "Target rate, kg per week (− to lose)")
        Text("The goal line and weekly rate show on the Weight chart at the top.", style = T.caption, color = C.InkSubtle)
    }
}
