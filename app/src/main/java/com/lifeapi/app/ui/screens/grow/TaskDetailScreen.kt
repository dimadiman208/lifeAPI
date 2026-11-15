package com.lifeapi.app.ui.screens.grow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Task
import com.lifeapi.app.data.grow.GrowCalc
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.CheckCircle
import com.lifeapi.app.ui.components.HDivider
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow.PlanVM
import com.lifeapi.app.vm.grow.TaskRow

private val TIME_RX = Regex("""^(\d{1,2})[:.](\d{2})$""")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(id: Long, onBack: () -> Unit, vm: PlanVM = viewModel()) {
    LaunchedEffect(id) { vm.bind(id) }
    val d by vm.detail.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val det = d ?: return
    val t = det.t
    var title by remember(t.id) { mutableStateOf(t.title) }
    var time by remember(t.id) { mutableStateOf(t.time) }
    var newItem by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(t)
    val today = Days.today()

    fun normTime(s: String): String? = if (s.isBlank()) "" else TIME_RX.find(s.trim())?.let {
        val h = it.groupValues[1].toInt(); val m = it.groupValues[2].toInt()
        if (h in 0..23 && m in 0..59) String.format(java.util.Locale.US, "%02d:%02d", h, m) else null
    }
    fun save(x: Task) = vm.save(x)

    DisposableEffect(t.id) {
        onDispose {
            val cur = latest
            val nt = normTime(time) ?: cur.time
            if (title.isNotBlank() && (title.trim() != cur.title || nt != cur.time)) vm.save(cur.copy(title = title.trim(), time = nt))
        }
    }

    DetailList(
        if (t.repeating) "Recurring task" else "Task",
        listOfNotNull(
            if (t.repeating) "↻ ${GrowCalc.repeatLabel(t.repeatDays)}" else t.day?.let { GrowCalc.dayLabel(it, today) } ?: "Inbox · no date",
            if (det.timesDone > 0) "${det.timesDone} time${if (det.timesDone == 1) "" else "s"} done" else null,
        ).joinToString(" · "),
        onBack,
        trailing = { RoundIconButton(Ic.Trash, "Delete task", size = 40.dp, bg = C.Track, tint = C.Negative) { vm.delete(t); onBack() } },
        glow = C.Volt,
    ) {
        item(key = "title") {
            LifeField(title, { title = it }, "Task", singleLine = false)
        }
        item(key = "when") {
            LifeCard(Modifier.fillMaxWidth(), gap = 12.dp) {
                MonoLabel(if (t.repeating) "STARTS" else "WHEN")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Today", t.day == today, C.Volt) { save(t.copy(day = today)) }
                    Chip("Tomorrow", t.day == today + 1, C.Volt) { save(t.copy(day = today + 1)) }
                    if (!t.repeating) Chip("No date", t.day == null, C.Volt) { save(t.copy(day = null, time = "", remindMin = null)) }
                    Chip(t.day?.takeIf { it != today && it != today + 1 }?.let { GrowCalc.shortDate(it) } ?: "Pick…",
                        t.day != null && t.day != today && t.day != today + 1, C.Volt) { pick = true }
                }
                LifeField(time, { v -> time = v; normTime(v)?.let { nt -> if (nt != t.time) save(t.copy(time = nt, day = t.day ?: if (nt.isNotEmpty()) today else null, remindMin = if (nt.isEmpty()) null else t.remindMin)) } },
                    "Time (optional)", keyboard = KeyboardType.Number, placeholder = "18:00")
                ToggleRow("Top 3 today", "Pinned above everything else", t.top) { save(t.copy(top = it)) }
                ToggleRow(
                    "Remind 10 min before", if (t.time.isBlank()) "Set a time first" else "at ${minus10(t.time)}",
                    t.remindMin != null, enabled = t.time.isNotBlank(),
                ) { save(t.copy(remindMin = if (it) 10 else null)) }
            }
        }
        item(key = "repeat") {
            LifeCard(Modifier.fillMaxWidth(), gap = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel("REPEAT", modifier = Modifier.weight(1f))
                    Text(if (t.repeating) GrowCalc.repeatLabel(t.repeatDays) else "never", style = T.caption, color = C.InkSubtle)
                }
                WeekdayPicker(t.repeatDays) { m -> save(t.copy(repeatDays = m, day = if (m != 0) (t.day ?: today) else t.day)) }
                if (t.repeating) Text("Ends never · ${det.timesDone} time${if (det.timesDone == 1) "" else "s"} done", style = T.caption, color = C.InkSubtle)
            }
        }
        item(key = "tags") {
            LifeCard(Modifier.fillMaxWidth(), gap = 10.dp) {
                MonoLabel("PROJECT")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("None", t.projectId == null) { save(t.copy(projectId = null, milestoneId = null)) }
                    det.projects.forEach { p -> Chip(p.name, t.projectId == p.id) { save(t.copy(projectId = p.id, milestoneId = if (t.projectId == p.id) t.milestoneId else null)) } }
                }
                MonoLabel("MODULE")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Health" to C.Health, "Fuel" to C.Fuel, "Money" to C.Money, "Train" to C.Train, "Grow" to C.Grow).forEach { (m, h) ->
                        Chip(m, t.module == m, h) { save(t.copy(module = if (t.module == m) "" else m)) }
                    }
                }
            }
        }
        item(key = "check") {
            LifeCard(Modifier.fillMaxWidth(), gap = 0.dp, padding = PaddingValues(start = 4.dp, end = 14.dp, top = 14.dp, bottom = 10.dp)) {
                Row(Modifier.padding(start = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel("CHECKLIST · ${det.items.count { it.done }} / ${det.items.size}", modifier = Modifier.weight(1f))
                    if (t.repeating) Text("resets each time", style = T.caption, color = C.InkSubtle)
                    else if (det.items.any { it.done }) Text("Reset", style = T.caption, color = C.Volt, modifier = Modifier.press { vm.resetChecklist(t.id) })
                }
                det.items.forEachIndexed { i, c ->
                    if (i > 0) HDivider(start = 48.dp)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).press(onLongClick = { vm.deleteItem(c) }) { vm.toggleItem(c) }, verticalAlignment = Alignment.CenterVertically) {
                        CheckCircle(c.done, C.Volt, 22.dp, square = true) { vm.toggleItem(c) }
                        Text(c.text, style = T.body, color = if (c.done) C.InkSubtle else C.Ink, modifier = Modifier.weight(1f))
                    }
                }
                Row(Modifier.padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        LifeField(newItem, { newItem = it }, "+ Add item", placeholder = "Lifting belt")
                    }
                    PillButton("Add", C.Graphite3, C.Ink, height = 44.dp, radius = 12.dp, enabled = newItem.isNotBlank()) {
                        vm.addItem(t.id, newItem, det.items.size); newItem = ""
                    }
                }
            }
        }
        item(key = "done") {
            val row = TaskRow(t, det.doneToday, "", day = if (t.repeating) today else t.day)
            PillButton(
                if (det.doneToday) (if (t.repeating) "Done today · undo" else "Reopen task") else (if (t.repeating) "Done for today" else "Mark done"),
                if (det.doneToday) C.Graphite2 else C.Volt, if (det.doneToday) C.Ink else C.Graphite0, Modifier.fillMaxWidth(), 52.dp, 16.dp,
                icon = Ic.Check, style = T.section,
            ) { vm.toggle(row, today) }
        }
        t.projectId?.let { pid ->
            item(key = "focus") {
                PillButton("Focus on this", C.GrowTint, C.Grow, Modifier.fillMaxWidth(), 44.dp, 14.dp, icon = GrowIc.Timer) {
                    nav.navigate("grow/focus?task=${t.id}&project=$pid")
                }
            }
        }
    }
    if (pick) DayPickerDialog(t.day, { pick = false }) { save(t.copy(day = it)) }
}

private fun minus10(time: String): String {
    val m = TIME_RX.find(time) ?: return time
    val total = (m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt() - 10 + 1440) % 1440
    return String.format(java.util.Locale.US, "%02d:%02d", total / 60, total % 60)
}

@Composable
fun ToggleRow(title: String, sub: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = T.body, color = if (enabled) C.Ink else C.InkFaint)
            if (sub != null) Text(sub, style = T.caption, color = C.InkSubtle)
        }
        Switch(
            checked, onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.Graphite0, checkedTrackColor = C.Volt, checkedBorderColor = C.Volt,
                uncheckedThumbColor = C.InkSubtle, uncheckedTrackColor = C.Graphite2, uncheckedBorderColor = C.Ghost,
            ),
        )
    }
}

