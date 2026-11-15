package com.lifeapi.app.ui.screens.grow.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.PlanTask
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.grow3.GrowStats
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow3.projects.PROJECT_STATUSES
import com.lifeapi.app.vm.grow3.projects.ProjectRow
import com.lifeapi.app.vm.grow3.projects.ProjectsListState
import com.lifeapi.app.vm.grow3.projects.ProjectsListVM

/**
 * Grow → Projects: cross-project Today (tasks taken into work + pull from backlog), this week in numbers,
 * then projects by type with one-tap capture into each backlog.
 */
@Composable
fun ProjectsTab(header: LazyListScope.(subtitle: String) -> Unit) {
    val vm: ProjectsListVM = viewModel()
    val st by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var editing by remember { mutableStateOf<Project?>(null) }
    var creating by remember { mutableStateOf(false) }
    var cats by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<Long?>(null) }
    var captureOpen by remember { mutableStateOf(false) }
    var pull by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<PlanTask?>(null) }

    ScreenList(glow = C.Grow) {
        header("What I'm working on — plans, tasks, what got done")
        val s = st ?: return@ScreenList
        if (s.total == 0) {
            item(key = "empty") {
                EmptyCard(
                    "PROJECTS", C.Grow, "Start your first project",
                    "Work projects get a Mon–Fri plan with a goal per day; personal ones are planned whenever you like. Capture tasks fast, take them into work with one tap.",
                    "New project", { creating = true },
                )
            }
            return@ScreenList
        }
        item(key = "today") { TodayCard(s, vm, onOpen = { nav.navigate(ProjectsRoutes.project(it)) }, onMenu = { menu = it }, onPull = { pull = true }, onCapture = { capture = null; captureOpen = true }) }
        item(key = "week") { WeekCard(s, onTimeline = { nav.navigate(ProjectsRoutes.timeline()) }, onReport = { nav.navigate(ProjectsRoutes.report(-1, false)) }) }
        item(key = "filter") {
            Segmented(PROJECT_STATUSES.map { st -> "$st${s.counts[st]?.takeIf { it > 0 }?.let { " $it" } ?: ""}" }, PROJECT_STATUSES.indexOf(s.filter).coerceAtLeast(0)) {
                vm.filter.value = PROJECT_STATUSES[it]
            }
        }
        if (s.groups.isEmpty()) item(key = "none") { Text("No ${s.filter.lowercase()} projects.", style = T.small, color = C.InkMuted, modifier = Modifier.padding(horizontal = 4.dp)) }
        s.groups.forEach { g ->
            item(key = "g-${g.category?.id ?: -1}") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonoLabel(g.title.uppercase() + if (g.category?.weekly == true) " · WEEKLY" else "", C.InkSubtle, Modifier.padding(start = 4.dp))
                    g.rows.forEach { r ->
                        ProjectCard(r, s.today, onOpen = { nav.navigate(ProjectsRoutes.project(r.p.id)) }, onEdit = { editing = r.p }, onCapture = { capture = r.p.id; captureOpen = true })
                    }
                }
            }
        }
        item(key = "add") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("New project", C.Grow, C.Graphite0, Modifier.weight(1f), 44.dp, 14.dp, icon = Ic.Plus) { creating = true }
                PillButton("Types", C.Graphite2, C.InkSoft, Modifier, 44.dp, 14.dp) { cats = true }
            }
        }
    }

    val s = st
    if (creating || editing != null) ProjectSheet(
        editing, s?.categories.orEmpty(), onDismiss = { creating = false; editing = null }, onManageCategories = { cats = true },
        onDelete = { vm.deleteProject(it) },
    ) { vm.saveProject(it) }
    if (cats) CategoriesSheet(s?.categories.orEmpty(), onDismiss = { cats = false }, onSave = vm::saveCategory, onDelete = vm::deleteCategory)
    if (captureOpen) CaptureSheet(
        s?.projectsById?.values?.filter { it.status == "Active" }.orEmpty(), capture, onDismiss = { captureOpen = false },
    ) { pid, t -> vm.capture(pid, t) }
    if (pull && s != null) PullSheet(s.backlog, onDismiss = { pull = false }) { vm.take(it) }
    menu?.let { t ->
        val p = s?.projectsById?.get(t.projectId)
        TaskMenuSheet(
            t, s?.today ?: 0, p?.workDays ?: 31, onDismiss = { menu = null },
            onMove = { d, l -> vm.take(listOf(t), d, l) },
            onStartBlock = { vm.take(listOf(t)); vm.startBlockNow(t) },
            onRename = { vm.renameTask(t, it) },
            onDelete = { vm.deleteTask(t) },
        )
    }
}

@Composable
private fun TodayCard(s: ProjectsListState, vm: ProjectsListVM, onOpen: (Long) -> Unit, onMenu: (PlanTask) -> Unit, onPull: () -> Unit, onCapture: () -> Unit) {
    val open = s.todayTasks.filter { !it.done }
    val done = s.todayTasks.filter { it.done }
    LifeCard(Modifier.fillMaxWidth(), radius = 22.dp, padding = PaddingValues(16.dp), gap = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MonoLabel("TODAY · IN WORK", C.Grow)
                Text(
                    if (s.todayTasks.isEmpty()) "Nothing taken into work yet" else "${done.size} of ${s.todayTasks.size} done",
                    style = T.section, color = C.Ink,
                )
            }
            RoundIconButton(Ic.Plus, "Capture task", size = 36.dp, bg = C.GrowTint, tint = C.Grow, onClick = onCapture)
        }
        (open + done).forEach { t ->
            val p = s.projectsById[t.projectId]
            TaskLine(
                t, projectColor(p), meta = p?.name + (t.movedFrom?.let { " · from ${dayShort(it)}" } ?: ""),
                trailingLabel = if (!t.done) "Open" else null, onTrailing = { onOpen(t.projectId) },
                onToggle = { vm.toggleTask(t) }, onMenu = { onMenu(t) },
            )
        }
        val backlogCount = s.backlog.sumOf { it.second.size }
        PillButton(
            if (backlogCount == 0) "Backlogs are empty" else "Pull from backlog · $backlogCount",
            C.GrowTint, C.Grow, Modifier.fillMaxWidth(), 42.dp, 12.dp, enabled = backlogCount > 0, onClick = onPull,
        )
    }
}

@Composable
private fun WeekCard(s: ProjectsListState, onTimeline: () -> Unit, onReport: () -> Unit) {
    val w = s.week; val p = s.prev
    LifeCard(Modifier.fillMaxWidth(), radius = 22.dp, padding = PaddingValues(16.dp), gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel("THIS WEEK · ${com.lifeapi.app.data.grow3.projects.ProjectReport.rangeTitle(s.weekStart, s.weekStart + 6).uppercase()}", C.InkSubtle, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${w.tasksDone}", "tasks done", deltaText(w.tasksDone - p.tasksDone), Modifier.weight(1f))
            StatTile("${w.goalsAchieved}/${w.goalsSet}", "day goals met", deltaText(w.goalsAchieved - p.goalsAchieved), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(GrowStats.fmtMinutes(w.loggedMinutes), "time logged", deltaText(w.loggedMinutes - p.loggedMinutes) { GrowStats.fmtMinutes(it) }, Modifier.weight(1f))
            StatTile("${w.activeDays}", "active days", deltaText(w.activeDays - p.activeDays), Modifier.weight(1f))
        }
        if (s.results.isNotEmpty()) {
            KindLabel("Results this week", Kind.Result)
            s.results.forEach { Text("• $it", style = T.small, color = C.InkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Today's timeline", C.Graphite2, C.InkSoft, Modifier.weight(1f), 40.dp, 12.dp, onClick = onTimeline)
            PillButton("Week report", C.Graphite2, C.InkSoft, Modifier.weight(1f), 40.dp, 12.dp, onClick = onReport)
        }
    }
}

@Composable
private fun ProjectCard(r: ProjectRow, today: Long, onOpen: () -> Unit, onEdit: () -> Unit, onCapture: () -> Unit) {
    LifeCard(Modifier.fillMaxWidth().press(onLongClick = onEdit, onClick = onOpen), radius = 18.dp, padding = PaddingValues(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), gap = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProjectBadge(r.p)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.p.name, style = T.section, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val parts = buildList {
                    add(r.category?.name ?: "Personal")
                    if (r.tasksPlanned > 0 || r.tasksDone > 0) add("${r.tasksDone}/${maxOf(r.tasksPlanned, r.tasksDone)} tasks this wk")
                    if (r.minutes > 0) add(GrowStats.fmtMinutes(r.minutes))
                    r.lastDay?.let { add("last ${relDay(it, today).lowercase()}") }
                }
                Text(parts.joinToString(" · "), style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RoundIconButton(Ic.Plus, "Add task to ${r.p.name}", size = 34.dp, bg = C.Track, tint = projectColor(r.p), onClick = onCapture)
        }
        r.nextOpen?.let { t ->
            Text("Next: ${t.title} · ${relDay(t.day!!, today)}", style = T.small, color = C.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 46.dp))
        }
    }
}
