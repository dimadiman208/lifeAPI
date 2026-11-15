package com.lifeapi.app.ui.screens.grow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Goal
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.WishItem
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.data.grow.GrowCalc
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow.GrowthExtra
import java.time.ZoneOffset
import kotlin.math.roundToLong

internal fun String.num(): Double? = replace(',', '.').replace("€", "").trim().toDoubleOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(initial: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = (initial ?: Days.today()).let { Days.date(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() })
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({ st.selectedDateMillis?.let { onPick(it / 86_400_000L) }; onDismiss() }) { Text("OK", color = C.Volt) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.InkMuted) } },
        colors = DatePickerDefaults.colors(containerColor = C.Graphite1),
    ) { DatePicker(st) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectSheet(initial: Project?, onDismiss: () -> Unit, onSave: (Project) -> Unit, onArchive: ((Project) -> Unit)?) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var next by remember { mutableStateOf(initial?.nextAction ?: "") }
    var target by remember { mutableStateOf(initial?.targetDay) }
    var pick by remember { mutableStateOf(false) }
    val today = Days.today()
    FormSheet(
        if (initial == null) "New project" else "Edit project", onDismiss, confirmColor = C.Grow, canConfirm = name.isNotBlank(),
        onConfirm = {
            val base = initial ?: Project(name = "", startDay = today)
            val changedNext = next.trim() != base.nextAction
            onSave(base.copy(name = name.trim(), nextAction = next.trim(), targetDay = target, nextSince = if (changedNext) (if (next.isBlank()) 0 else Days.now()) else base.nextSince))
            onDismiss()
        },
        onDelete = if (initial != null && onArchive != null) ({ onArchive(initial); onDismiss() }) else null,
    ) {
        LifeField(name, { name = it }, "Project", placeholder = "LifeAPI app")
        LifeField(next, { next = it }, "Next action", placeholder = "The very next physical step", singleLine = false)
        MonoLabel("Target date")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("None", target == null) { target = null }
            listOf(1L to "1 month", 3L to "3 months", 6L to "6 months").forEach { (m, l) ->
                val d = Days.date(today).plusMonths(m).toEpochDay()
                Chip(l, target == d) { target = d }
            }
            Chip(target?.let { GrowCalc.shortDate(it) } ?: "Pick…", target != null && target !in listOf(1L, 3L, 6L).map { Days.date(today).plusMonths(it).toEpochDay() }) { pick = true }
        }
    }
    if (pick) DayPickerDialog(target, { pick = false }) { target = it }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrowGoalSheet(initial: Goal?, ex: GrowthExtra?, defaultHorizon: Int, onDismiss: () -> Unit, onSave: (Goal) -> Unit, onDelete: ((Goal) -> Unit)?) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var horizon by remember { mutableStateOf(initial?.horizonDays ?: defaultHorizon) }
    var kind by remember { mutableStateOf(initial?.link?.substringBefore(":", "") ?: "") }
    var arg by remember { mutableStateOf(initial?.link?.substringAfter(":", "") ?: "") }
    var cur by remember { mutableStateOf(initial?.let { fmtKg(it.current) } ?: "0") }
    var target by remember { mutableStateOf(initial?.let { fmtKg(it.target) } ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    val t = target.num(); val c = cur.num()
    val kinds = listOf("" to "Manual", "habit" to "Habit", "lift" to "Lift", "project" to "Project", "learning" to "Learning")
    val argOk = kind == "" || arg.isNotBlank()
    FormSheet(
        if (initial == null) "New goal" else "Edit goal", onDismiss, confirmColor = C.Grow,
        canConfirm = name.isNotBlank() && t != null && t > 0 && argOk && (kind != "" || c != null),
        onConfirm = {
            val base = initial ?: Goal(name = "", current = 0.0, target = 1.0, unit = "")
            val restart = initial == null || initial.horizonDays != horizon
            onSave(
                base.copy(
                    name = name.trim(), target = t!!, current = if (kind == "") c ?: 0.0 else base.current,
                    unit = when (kind) { "" -> unit.trim(); "lift" -> "kg"; "learning" -> "h"; "habit" -> "days"; else -> "tasks" },
                    horizonDays = horizon, link = if (kind == "") "" else "$kind:$arg",
                    startDay = if (restart || base.startDay == 0L) Days.today() else base.startDay,
                ),
            )
            onDismiss()
        },
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial); onDismiss() }) else null,
    ) {
        LifeField(name, { name = it }, "Goal", placeholder = "Ship v0.3 · Finish 2 books")
        Segmented(listOf("30 days", "90 days"), if (horizon >= 60) 1 else 0, selBg = C.GrowTint, selFg = C.Grow) { horizon = if (it == 0) 30 else 90 }
        MonoLabel("Measured by")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            kinds.forEach { (k, l) -> Chip(l, kind == k) { if (kind != k) { kind = k; arg = "" } } }
        }
        val options: List<Pair<String, String>> = when (kind) {
            "habit" -> ex?.habits?.map { it.id.toString() to it.name }.orEmpty()
            "lift" -> ex?.lifts?.map { it to it }.orEmpty()
            "project" -> ex?.projects?.map { it.id.toString() to it.name }.orEmpty()
            "learning" -> ex?.learnTopics?.map { it to it }.orEmpty()
            else -> emptyList()
        }
        if (kind != "") {
            if (options.isEmpty() && kind != "learning") Text("Nothing to link yet — log some first.", style = T.small, color = C.InkMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (v, l) -> Chip(l, arg == v) { arg = v } }
            }
            if (kind == "learning") LifeField(arg, { arg = it }, "Topic contains", placeholder = "Spanish")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (kind == "") LifeField(cur, { cur = it }, "Now", Modifier.weight(1f), KeyboardType.Decimal)
            LifeField(
                target, { target = it },
                when (kind) { "lift" -> "Target e1RM, kg"; "habit" -> "Days done"; "project" -> "Tasks done"; "learning" -> "Hours"; else -> "Target" },
                Modifier.weight(1f), KeyboardType.Decimal,
            )
            if (kind == "") LifeField(unit, { unit = it }, "Unit", Modifier.weight(1f), placeholder = "books")
        }
        if (initial != null && kind == "" && c != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1.0, 1.0, 5.0).forEach { d ->
                    PillButton(if (d > 0) "+${fmtKg(d)}" else fmtKg(d), C.Graphite2, C.Ink, Modifier.weight(1f), height = 40.dp, radius = 12.dp) {
                        cur = fmtKg((c + d).coerceAtLeast(0.0))
                    }
                }
            }
        }
    }
}

@Composable
fun WishSheet(initial: WishItem?, onDismiss: () -> Unit, onSave: (WishItem) -> Unit, onDelete: ((WishItem) -> Unit)?) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.let { fmtKg(it.priceCents / 100.0) } ?: "") }
    var saved by remember { mutableStateOf(initial?.let { fmtKg(it.savedCents / 100.0) } ?: "0") }
    var prio by remember { mutableStateOf(initial?.priority ?: 2) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val p = price.num(); val s = saved.num()
    FormSheet(
        if (initial == null) "Add to wishlist" else "Edit wish", onDismiss, confirmColor = C.Money,
        canConfirm = name.isNotBlank() && p != null && p > 0 && s != null,
        onConfirm = {
            onSave((initial ?: WishItem(name = "", priceCents = 0)).copy(
                name = name.trim(), priceCents = (p!! * 100).roundToLong(), savedCents = (s!! * 100).roundToLong(), priority = prio, note = note.trim(),
            )); onDismiss()
        },
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial); onDismiss() }) else null,
    ) {
        LifeField(name, { name = it }, "Item", placeholder = "Kindle Paperwhite")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeField(price, { price = it }, "Price, €", Modifier.weight(1f), KeyboardType.Decimal)
            LifeField(saved, { saved = it }, "Saved, €", Modifier.weight(1f), KeyboardType.Decimal)
        }
        Segmented(listOf("P1 · need", "P2 · want", "P3 · maybe"), prio - 1, selBg = C.MoneyTint, selFg = C.Money) { prio = it + 1 }
        LifeField(note, { note = it }, "Note", placeholder = "wait 30 days · watch for sale")
    }
}

@Composable
fun AmountSheet(title: String, hue: androidx.compose.ui.graphics.Color, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var v by remember { mutableStateOf("") }
    val n = v.num()
    FormSheet(title, onDismiss, confirmColor = hue, canConfirm = n != null && n != 0.0, onConfirm = { onSave((n!! * 100).roundToLong()); onDismiss() }) {
        LifeField(v, { v = it }, "Amount, €", keyboard = KeyboardType.Decimal, placeholder = "50")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 25, 50, 100).forEach { q -> PillButton("€$q", C.Graphite2, C.Ink, Modifier.weight(1f), height = 40.dp, radius = 12.dp) { v = q.toString() } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickTaskSheet(projects: List<Project>, initialProject: Long? = null, onDismiss: () -> Unit, onAdd: (String, Long?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var pid by remember { mutableStateOf(initialProject) }
    FormSheet("New task", onDismiss, confirm = "Add task", canConfirm = text.isNotBlank(), onConfirm = { onAdd(text, pid); onDismiss() }) {
        LifeField(text, { text = it }, "Task", placeholder = "call mom tmrw 18:00 · ! for Top 3")
        Text("Understands today, tmrw, weekdays, 18:00 and ! for Top 3. No date → Today.", style = T.caption, color = C.InkSubtle)
        if (projects.isNotEmpty()) {
            MonoLabel("Project")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("None", pid == null, C.Volt) { pid = null }
                projects.forEach { p -> Chip(p.name, pid == p.id, C.Volt) { pid = p.id } }
            }
        }
    }
}
