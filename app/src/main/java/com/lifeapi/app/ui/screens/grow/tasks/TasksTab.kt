package com.lifeapi.app.ui.screens.grow.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.PlanTask
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.grow3.Weeks
import com.lifeapi.app.data.grow3.tasks.GroupBy
import com.lifeapi.app.data.grow3.tasks.TaskGroup
import com.lifeapi.app.data.grow3.tasks.TaskView
import com.lifeapi.app.ui.Segments
import com.lifeapi.app.ui.components.CheckCircle
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.screens.body.EntrySheet
import com.lifeapi.app.ui.screens.grow.projects.MoveChip
import com.lifeapi.app.ui.screens.grow.projects.QuickInput
import com.lifeapi.app.ui.screens.grow.projects.TaskMenuSheet
import com.lifeapi.app.ui.screens.grow.projects.dayLong
import com.lifeapi.app.ui.screens.grow.projects.dayShort
import com.lifeapi.app.ui.screens.grow.projects.projectColor
import com.lifeapi.app.ui.screens.grow.projects.relDay
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow3.tasks.TasksState
import com.lifeapi.app.vm.grow3.tasks.TasksVM

/**
 * Grow → Tasks: every task across projects. Quick add (project + date), views Today / Upcoming / Overdue / Backlog /
 * Done / All, filter by project, search, group by date or project. Tap = edit (title, project, date, delete);
 * long-press = multi-select with bulk move / re-date / done / delete. Nothing moves on its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksTab(header: LazyListScope.(subtitle: String) -> Unit) {
    val vm: TasksVM = viewModel()
    val st by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlanTask?>(null) }
    val selected = remember { mutableStateListOf<Long>() }
    var bulkProject by remember { mutableStateOf(false) }
    var bulkDate by remember { mutableStateOf(false) }

    ScreenList(glow = C.Grow) {
        header("Every task across projects — add, plan, move")
        val s = st ?: return@ScreenList
        if (s.projects.isEmpty()) {
            item(key = "noproj") {
                EmptyCard(
                    "TASKS", C.Grow, "Tasks live in projects",
                    "Create a project first (Work, Personal or your own type) — then add, date and move tasks here.",
                    "Go to Projects", { Segments.grow.intValue = 0 },
                )
            }
            return@ScreenList
        }
        item(key = "add") { QuickAdd(s, vm) }
        item(key = "views") { Views(s) { vm.view.value = it; selected.clear() } }
        item(key = "tools") { Tools(s, vm) }
        if (selected.isNotEmpty()) item(key = "bulk") {
            val sel = s.groups.flatMap { it.tasks }.filter { it.id in selected }
            BulkBar(
                sel.size,
                onProject = { bulkProject = true },
                onDate = { bulkDate = true },
                onDone = { vm.markDone(sel, !sel.all { it.done }); selected.clear() },
                onDelete = { vm.delete(sel); selected.clear() },
                onClear = { selected.clear() },
                allDone = sel.isNotEmpty() && sel.all { it.done },
            )
        }
        if (s.groups.isEmpty()) item(key = "none") {
            Text(emptyText(s), style = T.small, color = C.InkMuted, modifier = Modifier.padding(horizontal = 4.dp))
        }
        s.groups.forEach { g ->
            item(key = "g-${g.key}") {
                GroupCard(
                    g, s,
                    selecting = selected.isNotEmpty(),
                    isSelected = { it.id in selected },
                    onToggle = vm::toggle,
                    onOpen = { t -> if (selected.isNotEmpty()) { if (t.id in selected) selected.remove(t.id) else selected.add(t.id) } else editing = t },
                    onSelect = { t -> if (t.id in selected) selected.remove(t.id) else selected.add(t.id) },
                    onTake = { t -> vm.move(listOf(t), s.today, "today") },
                )
            }
        }
    }

    val s = st
    editing?.let { t ->
        if (s == null) return@let
        var pendingTitle: String? = null
        TaskMenuSheet(
            t, s.today, s.projectsById[t.projectId]?.workDays ?: Weeks.WORKDAYS,
            onDismiss = { editing = null },
            onMove = { d, label -> vm.move(listOf(t.copy(title = pendingTitle ?: t.title)), d, label) },
            onStartBlock = { vm.startBlockNow(t) },
            onRename = { pendingTitle = it.trim(); vm.rename(t, it) },
            onDelete = { vm.delete(listOf(t)) },
            projects = s.projects,
            onProject = { p -> vm.moveToProject(listOf(t.copy(title = pendingTitle ?: t.title)), p) },
        )
    }
    if (bulkProject && s != null) {
        ProjectPickSheet(s.projects, null, "Move ${selected.size} to project", onDismiss = { bulkProject = false }) { p ->
            vm.moveToProject(s.groups.flatMap { it.tasks }.filter { it.id in selected }, p); selected.clear()
        }
    }
    if (bulkDate && s != null) {
        DateSheet("Set date for ${selected.size}", s.today, null, onDismiss = { bulkDate = false }) { d, label ->
            vm.move(s.groups.flatMap { it.tasks }.filter { it.id in selected }, d, label); selected.clear()
        }
    }
}

private fun emptyText(s: TasksState): String = when {
    s.query.isNotBlank() || s.projectFilter.isNotEmpty() -> "Nothing matches the filter."
    s.view == TaskView.TODAY -> "Nothing planned for today. Add a task above or pull one from Backlog."
    s.view == TaskView.OVERDUE -> "No overdue tasks."
    s.view == TaskView.UPCOMING -> "Nothing planned for the next two weeks."
    s.view == TaskView.BACKLOG -> "Backlog is empty."
    s.view == TaskView.DONE -> "Nothing finished in the last 30 days yet."
    else -> "No tasks yet."
}

// ---------------------------------------------------------------------------------------------- quick add

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAdd(s: TasksState, vm: TasksVM) {
    var projectId by remember(s.lastProject) { mutableStateOf(s.lastProject) }
    // null day = backlog; default today
    var day by remember { mutableStateOf<Long?>(s.today) }
    var pickProject by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val project = s.projectsById[projectId ?: -1]
    LifeCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(12.dp), gap = 10.dp) {
        QuickInput("Add a task to ${project?.name ?: "…"} · ${day?.let { relDay(it, s.today).lowercase() } ?: "backlog"}") { title ->
            projectId?.let { vm.add(title, it, day) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(C.Graphite2).press { pickProject = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(projectColor(project)))
                Text(project?.name ?: "Project", style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = C.Ink, maxLines = 1)
                Icon(Ic.ChevronDown, null, tint = C.InkSubtle, modifier = Modifier.size(14.dp))
            }
            MoveChip("Today", day == s.today) { day = s.today }
            MoveChip("Tomorrow", day == s.today + 1) { day = s.today + 1 }
            val custom = day != null && day != s.today && day != s.today + 1
            MoveChip(if (custom) dayShort(day!!) else "Pick day", custom) { pickDate = true }
            MoveChip("Backlog", day == null) { day = null }
        }
    }
    if (pickProject) ProjectPickSheet(s.projects, projectId, "Add tasks to", onDismiss = { pickProject = false }) { projectId = it.id }
    if (pickDate) DateSheet("Add tasks on", s.today, day, onDismiss = { pickDate = false }) { d, _ -> day = d }
}

// ---------------------------------------------------------------------------------------------- views / tools

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Views(s: TasksState, onPick: (TaskView) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TaskView.entries.forEach { v ->
            val n = s.counts.of(v)
            val on = s.view == v
            val warn = v == TaskView.OVERDUE && n > 0
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) C.GrowTint else C.Graphite1)
                    .border(1.dp, if (on) C.Grow.copy(alpha = 0.5f) else C.Hairline, RoundedCornerShape(12.dp))
                    .press { onPick(v) }.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(v.label, style = T.smallB, color = if (on) C.Grow else C.InkSoft)
                if (n > 0 && v != TaskView.DONE) Text(n.toString(), style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = if (warn) C.Fuel else C.InkSubtle)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tools(s: TasksState, vm: TasksVM) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0D0E10))
                    .border(1.dp, C.Hairline, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Ic.Search, null, tint = C.InkSubtle, modifier = Modifier.size(16.dp))
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (s.query.isEmpty()) Text("Search tasks", style = T.small, color = C.InkFaint)
                    BasicTextField(s.query, { vm.query.value = it }, singleLine = true, textStyle = T.small.copy(color = C.Ink), cursorBrush = SolidColor(C.Grow), modifier = Modifier.fillMaxWidth())
                }
                if (s.query.isNotEmpty()) Icon(Ic.Close, "Clear", tint = C.InkSubtle, modifier = Modifier.size(16.dp).press { vm.query.value = "" })
            }
            if (s.view != TaskView.BACKLOG) {
                val byProject = s.groupBy == GroupBy.PROJECT
                Text(
                    if (byProject) "By project" else "By date", style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = C.Grow,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.GrowTint)
                        .press { vm.groupBy.value = if (byProject) GroupBy.DATE else GroupBy.PROJECT }.padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
        }
        if (s.projects.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.projects.forEach { p ->
                val on = p.id in s.projectFilter
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) C.hue(p.hue).copy(alpha = 0.18f) else C.Graphite1)
                        .border(1.dp, if (on) C.hue(p.hue).copy(alpha = 0.6f) else C.Hairline, RoundedCornerShape(10.dp))
                        .press { vm.toggleFilter(p.id) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(C.hue(p.hue)))
                    Text(p.name, style = T.caption, color = if (on) C.Ink else C.InkMuted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BulkBar(n: Int, allDone: Boolean, onProject: () -> Unit, onDate: () -> Unit, onDone: () -> Unit, onDelete: () -> Unit, onClear: () -> Unit) {
    LifeCard(Modifier.fillMaxWidth(), bg = C.GrowTint, border = C.Grow.copy(alpha = 0.4f), padding = androidx.compose.foundation.layout.PaddingValues(12.dp), gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$n selected", style = T.section, color = C.Ink, modifier = Modifier.weight(1f))
            Icon(Ic.Close, "Cancel selection", tint = C.InkMuted, modifier = Modifier.size(20.dp).press(onClick = onClear))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BulkAction("Project…", Modifier.weight(1f), onClick = onProject)
            BulkAction("Date…", Modifier.weight(1f), onClick = onDate)
            BulkAction(if (allDone) "Reopen" else "Done", Modifier.weight(1f), onClick = onDone)
            BulkAction("Delete", Modifier.weight(1f), color = C.Negative, onClick = onDelete)
        }
    }
}

@Composable
private fun BulkAction(label: String, modifier: Modifier, color: Color = C.Ink, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(10.dp)).background(C.Graphite1).press(enabled = true, haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1) }
}

// ---------------------------------------------------------------------------------------------- groups & rows

@Composable
private fun GroupCard(
    g: TaskGroup, s: TasksState, selecting: Boolean, isSelected: (PlanTask) -> Boolean,
    onToggle: (PlanTask) -> Unit, onOpen: (PlanTask) -> Unit, onSelect: (PlanTask) -> Unit, onTake: (PlanTask) -> Unit,
) {
    val project = g.projectId?.let { s.projectsById[it] }
    val overdueGroup = g.day != null && g.day < s.today && g.tasks.any { !it.done }
    val title = when {
        g.projectId != null -> project?.name ?: "Project"
        g.day == null -> "Backlog"
        g.day == s.today -> "Today · ${dayLong(g.day).substringAfter(", ")}"
        g.day == s.today + 1 -> "Tomorrow · ${dayLong(g.day).substringAfter(", ")}"
        else -> dayLong(g.day)
    }
    LifeCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(start = 6.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), gap = 2.dp) {
        Row(Modifier.padding(start = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (project != null) Box(Modifier.size(8.dp).clip(CircleShape).background(projectColor(project)))
            MonoLabel(title.uppercase(), if (overdueGroup) C.Fuel else C.InkSubtle, Modifier.weight(1f))
            val open = g.tasks.count { !it.done }
            Text(if (open == g.tasks.size) "$open" else "$open open · ${g.tasks.size}", style = T.caption, color = C.InkFaint)
        }
        g.tasks.forEach { t ->
            androidx.compose.runtime.key(t.id) {
                TaskRow(t, s, showProject = g.projectId == null, showDate = g.projectId != null, selecting, isSelected(t), onToggle, onOpen, onSelect, onTake)
            }
        }
    }
}

@Composable
private fun TaskRow(
    t: PlanTask, s: TasksState, showProject: Boolean, showDate: Boolean, selecting: Boolean, selected: Boolean,
    onToggle: (PlanTask) -> Unit, onOpen: (PlanTask) -> Unit, onSelect: (PlanTask) -> Unit, onTake: (PlanTask) -> Unit,
) {
    val p = s.projectsById[t.projectId]
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) C.Grow.copy(alpha = 0.12f) else Color.Transparent)
            .press(onLongClick = { onSelect(t) }) { onOpen(t) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (selected) C.Grow else Color.Transparent)
                        .then(if (!selected) Modifier.border(1.5.dp, C.Ghost, RoundedCornerShape(6.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Ic.Check, null, tint = C.Graphite0, modifier = Modifier.size(14.dp)) }
            }
        } else CheckCircle(t.done, if (p != null) C.hue(p.hue) else C.Grow, size = 22.dp, square = true) { onToggle(t) }
        Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                t.title, style = T.body.copy(textDecoration = if (t.done) TextDecoration.LineThrough else null),
                color = if (t.done) C.InkSubtle else C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                if (showProject && p != null) add(p.name)
                if (showDate) add(t.day?.let { relDay(it, s.today) } ?: "Backlog")
                if (t.day != null && t.day < s.today && !t.done) add("overdue")
                t.movedFrom?.let { if (!t.done) add("moved from ${dayShort(it)}") }
            }
            if (meta.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showProject && p != null) Box(Modifier.size(6.dp).clip(CircleShape).background(C.hue(p.hue)))
                Text(meta.joinToString(" · "), style = T.caption, color = if (meta.contains("overdue")) C.Fuel else C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!selecting && !t.done && t.day != s.today) {
            Text(
                "→ Today", style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = C.Grow,
                modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp)).background(C.Grow.copy(alpha = 0.12f))
                    .press(haptic = true) { onTake(t) }.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------- pickers

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectPickSheet(projects: List<Project>, current: Long?, title: String, onDismiss: () -> Unit, onPick: (Project) -> Unit) {
    EntrySheet(title, onDismiss, hue = C.Grow, confirm = "Close", onConfirm = onDismiss) {
        projects.forEach { p ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (p.id == current) C.GrowTint else C.Graphite2).press { onPick(p); onDismiss() }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(C.hue(p.hue)))
                Text(p.name, style = T.body, color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.status != "Active") Text(p.status, style = T.caption, color = C.InkSubtle)
                if (p.id == current) Icon(Ic.Check, null, tint = C.Grow, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Date choice: Today / Tomorrow / next workday / next week / Backlog + the next two weeks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateSheet(title: String, today: Long, current: Long?, onDismiss: () -> Unit, onPick: (Long?, String) -> Unit) {
    EntrySheet(title, onDismiss, hue = C.Grow, confirm = "Close", onConfirm = onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val next = Weeks.nextWorkday(today)
            val opts = buildList {
                add(today to "Today"); add(today + 1 to "Tomorrow")
                if (next != today + 1) add(next to "Next workday (${dayShort(next)})")
                add(Weeks.start(today) + 7 to "Next week (Mon)")
            }
            opts.forEach { (d, l) -> MoveChip(l, current == d) { onPick(d, l.substringBefore(" (").lowercase()); onDismiss() } }
            MoveChip("Backlog", current == null) { onPick(null, "backlog"); onDismiss() }
        }
        Text("Or pick a day", style = T.caption, color = C.InkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (2L..20L).map { today + it }.forEach { d -> MoveChip(dayShort(d), current == d) { onPick(d, dayShort(d)); onDismiss() } }
        }
    }
}
