package com.lifeapi.app.ui.screens.grow.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifeapi.app.data.Achievement
import com.lifeapi.app.data.ActivityEntry
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.PlanTask
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.ProjectCategory
import com.lifeapi.app.data.TimeBlock
import com.lifeapi.app.data.grow3.TimeMath
import com.lifeapi.app.data.grow3.Weeks
import com.lifeapi.app.ui.components.CheckCircle
import com.lifeapi.app.ui.components.ChipPicker
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.body.EntrySheet
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow3.projects.PROJECT_STATUSES

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DaysMask(mask: Int, hue: Color = C.Grow, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DAY_LETTERS.forEachIndexed { i, l ->
            val on = mask and (1 shl i) != 0
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(if (on) hue.copy(alpha = 0.16f) else C.Graphite2)
                    .then(if (on) Modifier.border(1.dp, hue.copy(alpha = 0.5f), RoundedCornerShape(10.dp)) else Modifier)
                    .press { onChange(mask xor (1 shl i)) },
                contentAlignment = Alignment.Center,
            ) { Text(l, style = T.smallB, color = if (on) hue else C.InkSubtle) }
        }
    }
}

// ----------------------------------------------------------------------------- project

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectSheet(
    initial: Project?,
    categories: List<ProjectCategory>,
    onDismiss: () -> Unit,
    onManageCategories: () -> Unit,
    onDelete: ((Project) -> Unit)?,
    onSave: (Project) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var catId by remember { mutableStateOf(initial?.categoryId ?: ProjectCategory.WORK) }
    var hue by remember { mutableStateOf(initial?.hue ?: 4) }
    var icon by remember { mutableStateOf(initial?.icon ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: "Active") }
    var start by remember { mutableStateOf(initial?.workStart ?: "09:00") }
    var end by remember { mutableStateOf(initial?.workEnd ?: "18:00") }
    var days by remember { mutableStateOf(initial?.workDays ?: Weeks.WORKDAYS) }
    var confirmDelete by remember { mutableStateOf(false) }
    val cat = categories.firstOrNull { it.id == catId }
    val weekly = cat?.weekly ?: (catId == ProjectCategory.WORK)
    val hoursOk = !weekly || TimeMath.duration(start, end) != null
    EntrySheet(
        if (initial == null) "New project" else "Edit project", onDismiss, hue = C.Grow,
        canConfirm = name.isNotBlank() && hoursOk,
        onConfirm = {
            onSave(
                (initial ?: Project(name = "", startDay = Days.today())).copy(
                    name = name.trim(), description = desc.trim(), categoryId = catId, hue = hue, icon = icon.trim().take(2),
                    status = status, workStart = start.trim(), workEnd = end.trim(), workDays = days,
                ),
            )
            onDismiss()
        },
        onDelete = if (initial != null && onDelete != null) ({ if (confirmDelete) { onDelete(initial); onDismiss() } else confirmDelete = true }) else null,
        subtitle = if (confirmDelete) "Tap 🗑 again to delete — tasks and plans go, activity/achievements stay" else null,
    ) {
        LifeField(name, { name = it }, "Name", placeholder = "Payments API, LifeAPI, Thesis…")
        LifeField(desc, { desc = it }, "Description (optional)", singleLine = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Type", style = T.caption, color = C.InkMuted, modifier = Modifier.weight(1f))
            TextAction("Manage types", onClick = onManageCategories)
        }
        ChipPicker(categories.map { it.name }, cat?.name ?: "Personal", C.Grow) { pick -> categories.firstOrNull { it.name == pick }?.let { catId = it.id } }
        Text("Colour & badge", style = T.caption, color = C.InkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            (0..5).forEach { h ->
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(C.hue(h))
                        .then(if (h == hue) Modifier.border(2.dp, C.Ink, CircleShape) else Modifier).press { hue = h },
                )
            }
            LifeField(icon, { icon = it.take(2) }, "Badge", Modifier.weight(1f), placeholder = "🚀 / LA")
        }
        Text("Status", style = T.caption, color = C.InkMuted)
        ChipPicker(PROJECT_STATUSES, status, C.Grow) { status = it }
        if (weekly) {
            Text("Working hours — a plan for the timeline, never counted as time spent", style = T.caption, color = C.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeField(start, { start = it }, "Start", Modifier.weight(1f), placeholder = "09:00")
                LifeField(end, { end = it }, "End", Modifier.weight(1f), placeholder = "18:00")
            }
            if (!hoursOk) Text("Use HH:MM, end after start", style = T.caption, color = C.Negative)
            Text("Working days", style = T.caption, color = C.InkMuted)
        } else {
            Text("Usual days (optional) — only suggestions, nothing is required", style = T.caption, color = C.InkMuted)
        }
        DaysMask(days) { days = it }
    }
}

@Composable
fun CategoriesSheet(categories: List<ProjectCategory>, onDismiss: () -> Unit, onSave: (ProjectCategory) -> Unit, onDelete: (ProjectCategory) -> Unit) {
    var newName by remember { mutableStateOf("") }
    var newWeekly by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProjectCategory?>(null) }
    var editName by remember { mutableStateOf("") }
    EntrySheet(
        "Project types", onDismiss, hue = C.Grow, confirm = if (newName.isBlank()) "Done" else "Add “${newName.trim()}”",
        onConfirm = {
            if (newName.isNotBlank()) {
                onSave(ProjectCategory(name = newName.trim(), weekly = newWeekly, sort = (categories.maxOfOrNull { it.sort } ?: 0) + 1))
                newName = ""; newWeekly = false
            } else onDismiss()
        },
        subtitle = "Deleting a type moves its projects to Personal",
    ) {
        categories.forEach { c ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Graphite2).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (editing?.id == c.id) {
                    LifeField(editName, { editName = it }, "Name", Modifier.weight(1f))
                    TextAction("Save") { if (editName.isNotBlank()) onSave(c.copy(name = editName.trim())); editing = null }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = T.body, color = C.Ink)
                        Text(if (c.weekly) "Weekly plan · Mon–Fri" else "Free planning", style = T.caption, color = C.InkSubtle)
                    }
                    TextAction(if (c.weekly) "Weekly ✓" else "Weekly", if (c.weekly) C.Grow else C.InkSubtle) { onSave(c.copy(weekly = !c.weekly)) }
                    RoundIconButton(Ic.Edit, "Rename", size = 34.dp, bg = C.Track) { editing = c; editName = c.name }
                    if (!c.builtIn && c.id != ProjectCategory.WORK && c.id != ProjectCategory.PERSONAL)
                        RoundIconButton(Ic.Trash, "Delete", size = 34.dp, bg = C.Track, tint = C.Negative) { onDelete(c) }
                }
            }
        }
        LifeField(newName, { newName = it }, "New type", placeholder = "University, Open Source, Freelance…")
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckCircle(newWeekly, C.Grow, size = 22.dp, square = true) { newWeekly = !newWeekly }
            Text("Plan it by work week (like Work)", style = T.small, color = C.InkMuted)
        }
    }
}

// ----------------------------------------------------------------------------- tasks

/** Capture tasks into a project's backlog without opening it. Stays open for several entries. */
@Composable
fun CaptureSheet(projects: List<Project>, preselected: Long?, onDismiss: () -> Unit, onAdd: (Long, String) -> Unit) {
    var pid by remember { mutableStateOf(preselected ?: projects.firstOrNull()?.id) }
    val added = remember { mutableStateListOf<String>() }
    EntrySheet("Add to backlog", onDismiss, hue = C.Grow, confirm = "Done", onConfirm = onDismiss, subtitle = "Enter adds · take it into work later with one tap") {
        if (projects.isEmpty()) {
            Text("Create a project first.", style = T.small, color = C.InkMuted)
        } else {
            ChipPicker(projects.map { it.name }, projects.firstOrNull { it.id == pid }?.name ?: "", C.Grow) { n -> pid = projects.firstOrNull { it.name == n }?.id }
            QuickInput("Task…", autoFocus = true) { t -> pid?.let { onAdd(it, t); added.add(0, t) } }
            added.take(6).forEach { Text("✓ $it", style = T.small, color = C.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Multi-select backlog tasks of active projects → today. */
@Composable
fun PullSheet(groups: List<Pair<Project, List<PlanTask>>>, onDismiss: () -> Unit, onTake: (List<PlanTask>) -> Unit) {
    val picked = remember { mutableStateListOf<Long>() }
    val all = groups.flatMap { it.second }
    EntrySheet(
        "Pull from backlog", onDismiss, hue = C.Grow, confirm = if (picked.isEmpty()) "Pick tasks" else "Take ${picked.size} into today",
        canConfirm = picked.isNotEmpty(), onConfirm = { onTake(all.filter { it.id in picked }); onDismiss() },
    ) {
        if (groups.isEmpty()) Text("Backlogs are empty. Capture tasks with “+ task” on a project.", style = T.small, color = C.InkMuted)
        groups.forEach { (p, tasks) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectBadge(p, 24)
                Text(p.name, style = T.smallB, color = C.InkSoft, modifier = Modifier.weight(1f))
                TextAction(if (tasks.all { it.id in picked }) "None" else "All") {
                    if (tasks.all { it.id in picked }) picked.removeAll(tasks.map { it.id }.toSet()) else tasks.forEach { if (it.id !in picked) picked.add(it.id) }
                }
            }
            tasks.forEach { t ->
                val on = t.id in picked
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) C.GrowTint else Color.Transparent)
                        .press { if (on) picked.remove(t.id) else picked.add(t.id) }.padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckCircle(on, C.Grow, size = 22.dp, square = true) { if (on) picked.remove(t.id) else picked.add(t.id) }
                    Text(t.title, style = T.body, color = C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Explicit moves for one task: Today / Tomorrow / next working day / a day in the next two weeks / back to backlog,
 * optional "Start block now", rename, delete.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskMenuSheet(
    t: PlanTask,
    today: Long,
    workDays: Int,
    onDismiss: () -> Unit,
    onMove: (Long?, String) -> Unit,
    onStartBlock: (() -> Unit)?,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    /** When given, the sheet also lets you move the task to another project (Grow → Tasks). */
    projects: List<Project> = emptyList(),
    onProject: ((Project) -> Unit)? = null,
) {
    var title by remember { mutableStateOf(t.title) }
    EntrySheet(
        "Task", onDismiss, hue = C.Grow, confirm = "Save", canConfirm = title.isNotBlank(),
        onConfirm = { if (title.trim() != t.title) onRename(title); onDismiss() },
        onDelete = { onDelete(); onDismiss() },
        subtitle = when {
            t.day == null -> "In backlog" + (t.movedFrom?.let { " · was on ${dayShort(it)}" } ?: "")
            else -> "Planned ${relDay(t.day, today)}" + (t.movedFrom?.let { " · moved from ${dayShort(it)}" } ?: "")
        },
    ) {
        LifeField(title, { title = it }, "Task", singleLine = false)
        if (onProject != null && projects.isNotEmpty()) {
            Text("Project", style = T.caption, color = C.InkMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                projects.forEach { p ->
                    MoveChip(p.name, t.projectId == p.id) {
                        if (p.id != t.projectId) {
                            if (title.isNotBlank() && title.trim() != t.title) onRename(title)
                            onProject(p); onDismiss()
                        }
                    }
                }
            }
        }
        Text("Move to", style = T.caption, color = C.InkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val next = Weeks.nextWorkday(today, workDays)
            val opts = buildList {
                add(today to "Today"); add(today + 1 to "Tomorrow")
                if (next != today + 1) add(next to "Next workday (${dayShort(next)})")
                add(Weeks.start(today) + 7 to "Next week (Mon)")
            }
            opts.forEach { (d, l) -> MoveChip(l, t.day == d) { onMove(d, l.substringBefore(" (").lowercase()); onDismiss() } }
            MoveChip("Backlog", t.day == null) { onMove(null, "backlog"); onDismiss() }
        }
        Text("Or pick a day", style = T.caption, color = C.InkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (2L..13L).map { today + it }.forEach { d -> MoveChip(dayShort(d), t.day == d) { onMove(d, dayShort(d)); onDismiss() } }
        }
        if (onStartBlock != null && !t.done) GhostButton("Take into work + start a 1h block now", Modifier.fillMaxWidth(), color = C.Grow) { onStartBlock(); onDismiss() }
    }
}

@Composable
internal fun MoveChip(label: String, current: Boolean, onClick: () -> Unit) {
    Text(
        label, style = T.caption, color = if (current) C.Graphite0 else C.InkSoft,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (current) C.Grow else C.Graphite2).press(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

// ----------------------------------------------------------------------------- day plan / activity / achievement

/** Edit one of the day-plan texts (goal, expected result, result, note). */
@Composable
fun TextSheet(title: String, label: String, initial: String, hue: Color, hint: String? = null, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    EntrySheet(title, onDismiss, hue = hue, onConfirm = { onSave(v); onDismiss() }, subtitle = hint,
        onDelete = if (initial.isNotBlank()) ({ onSave(""); onDismiss() }) else null) {
        LifeField(v, { v = it }, label, singleLine = false)
    }
}

@Composable
fun ActivitySheet(initial: ActivityEntry?, day: Long, onDismiss: () -> Unit, onDelete: ((ActivityEntry) -> Unit)?, onSave: (String, Int?) -> Unit) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var mins by remember { mutableStateOf(initial?.minutes?.toString() ?: "") }
    val m = mins.trim().toIntOrNull()
    EntrySheet(
        if (initial == null) "What did you do?" else "Edit activity", onDismiss, hue = Kind.Activity,
        canConfirm = text.isNotBlank() && (mins.isBlank() || (m != null && m in 1..1440)),
        onConfirm = { onSave(text, m); onDismiss() },
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial); onDismiss() }) else null,
        subtitle = "Activity log · ${dayLong(day)}" + if (initial?.blockId != null) " · describes a time block (its minutes aren't counted twice)" else "",
    ) {
        LifeField(text, { text = it }, "Done", singleLine = false, placeholder = "Added transactional lock for retries")
        LifeField(mins, { mins = it.filter(Char::isDigit) }, "Minutes spent (optional)", keyboard = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 60, 90, 120).forEach { q -> MoveChip(if (q < 60) "${q}m" else "${q / 60}h${if (q % 60 > 0) " ${q % 60}m" else ""}", m == q) { mins = q.toString() } }
        }
    }
}

@Composable
fun AchievementSheet(initial: Achievement?, day: Long, prefill: String = "", onDismiss: () -> Unit, onDelete: ((Achievement) -> Unit)?, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: prefill) }
    var detail by remember { mutableStateOf(initial?.detail ?: "") }
    EntrySheet(
        if (initial == null) "New achievement" else "Edit achievement", onDismiss, hue = Kind.Achievement,
        canConfirm = title.isNotBlank(), onConfirm = { onSave(title, detail); onDismiss() },
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial); onDismiss() }) else null,
        subtitle = "Concrete result for reports · ${dayLong(day)}",
    ) {
        LifeField(title, { title = it }, "Achievement", singleLine = false, placeholder = "SMS API handles repeated requests correctly")
        LifeField(detail, { detail = it }, "Details (optional)", singleLine = false)
    }
}

// ----------------------------------------------------------------------------- time block

@Composable
fun BlockSheet(
    initial: TimeBlock?,
    day: Long,
    projects: List<Project>,
    defaultProject: Long?,
    slot: Pair<String, String>,
    conflicts: List<TimeBlock>,
    onDismiss: () -> Unit,
    onDelete: ((TimeBlock) -> Unit)?,
    onSave: (TimeBlock) -> Unit,
) {
    var start by remember { mutableStateOf(initial?.start ?: slot.first) }
    var end by remember { mutableStateOf(initial?.end ?: slot.second) }
    var pid by remember { mutableStateOf(initial?.projectId ?: defaultProject) }
    var title by remember { mutableStateOf(initial?.title ?: projects.firstOrNull { it.id == defaultProject }?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var result by remember { mutableStateOf(initial?.result ?: "") }
    var actual by remember { mutableStateOf(initial?.actualMinutes?.toString() ?: "") }
    val planned = TimeMath.duration(start, end)
    val a = actual.trim().toIntOrNull()
    EntrySheet(
        if (initial == null) "New time block" else "Time block", onDismiss, hue = C.Grow,
        canConfirm = planned != null && title.isNotBlank() && (actual.isBlank() || (a != null && a in 0..1440)),
        onConfirm = {
            onSave((initial ?: TimeBlock(day = day, start = "", end = "", title = "")).copy(
                day = day, start = start.trim(), end = end.trim(), projectId = pid, title = title, description = desc, result = result,
                actualMinutes = if (actual.isBlank()) null else a,
            ))
            onDismiss()
        },
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial); onDismiss() }) else null,
        subtitle = dayLong(day) + (planned?.let { " · planned ${com.lifeapi.app.data.grow3.GrowStats.fmtMinutes(it)}" } ?: ""),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeField(start, { start = it }, "Start", Modifier.weight(1f), placeholder = "09:00")
            LifeField(end, { end = it }, "End", Modifier.weight(1f), placeholder = "10:30")
        }
        if (planned == null) Text("Use HH:MM, end after start (same day)", style = T.caption, color = C.Negative)
        if (conflicts.isNotEmpty()) Text("Overlaps ${conflicts.joinToString { "${it.start}–${it.end} ${it.title}" }}", style = T.caption, color = C.Fuel)
        Text("Project", style = T.caption, color = C.InkMuted)
        ChipPicker(listOf("No project") + projects.map { it.name }, projects.firstOrNull { it.id == pid }?.name ?: "No project", C.Grow) { n ->
            val np = projects.firstOrNull { it.name == n }
            if (title.isBlank() || projects.any { it.name == title }) title = np?.name ?: title
            pid = np?.id
        }
        LifeField(title, { title = it }, "What", placeholder = "SMS API, Code review…")
        LifeField(desc, { desc = it }, "Description (optional)", singleLine = false)
        LifeField(result, { result = it }, "Result (optional)", singleLine = false)
        Text("Actual time — only this counts as time spent. Leave empty if you didn't do it.", style = T.caption, color = C.InkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LifeField(actual, { actual = it.filter(Char::isDigit) }, "Actual minutes", Modifier.weight(1f), keyboard = KeyboardType.Number)
            if (planned != null) TextAction("= planned") { actual = planned.toString() }
        }
    }
}

@Composable
fun MilestoneDot(done: Boolean) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(if (done) Kind.Result else C.Ghost))
}

@Composable
fun SmallIconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = C.InkSubtle, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).press(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun ColumnGap(content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
