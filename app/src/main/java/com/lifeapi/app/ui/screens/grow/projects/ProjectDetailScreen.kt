package com.lifeapi.app.ui.screens.grow.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Achievement
import com.lifeapi.app.data.ActivityEntry
import com.lifeapi.app.data.Milestone
import com.lifeapi.app.data.PlanTask
import com.lifeapi.app.data.grow3.GrowStats
import com.lifeapi.app.data.grow3.projects.ProjectReport
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.CheckCircle
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow3.projects.DayView
import com.lifeapi.app.vm.grow3.projects.ProjectDetailState
import com.lifeapi.app.vm.grow3.projects.ProjectDetailVM

private enum class PlanField(val label: String, val title: String, val hint: String) {
    Goal("Goal", "Goal of the day", "One main thing you plan to achieve"),
    Expected("Expected result", "Expected result", "What “done” looks like"),
    Result("Result", "Result", "What actually came out of the day — a non-empty result counts the goal as achieved"),
    Note("Note", "Note", "Anything worth remembering"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectDetailScreen(id: Long, onBack: () -> Unit, vm: ProjectDetailVM = viewModel()) {
    LaunchedEffect(id) { vm.bind(id) }
    val st by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var plan by remember { mutableStateOf<Pair<Long, PlanField>?>(null) }
    var act by remember { mutableStateOf<Pair<Long, ActivityEntry?>?>(null) }
    var ach by remember { mutableStateOf<Triple<Long, Achievement?, String>?>(null) }
    var menu by remember { mutableStateOf<PlanTask?>(null) }
    var editProject by remember { mutableStateOf(false) }
    var cats by remember { mutableStateOf(false) }
    var showAllBacklog by remember { mutableStateOf(false) }

    ScreenList(glow = C.Grow, bottomExtra = false) {
        val s = st
        item(key = "top") {
            GrowTop(s?.p?.name ?: "Project", s?.let { listOfNotNull(it.category?.name ?: "Personal", it.p.status.takeIf { st -> st != "Active" }, if (it.weekly) "weekly plan" else "free planning").joinToString(" · ") }, onBack) {
                if (s != null) RoundIconButton(Ic.Edit, "Edit project", size = 40.dp, bg = Color(0xFF1A1C20)) { editProject = true }
            }
        }
        if (s == null) return@ScreenList
        if (s.p.description.isNotBlank()) item(key = "desc") { Text(s.p.description, style = T.small, color = C.InkMuted, modifier = Modifier.padding(horizontal = 4.dp)) }

        // ---- capture + backlog: write tasks down, take them into work with one tap
        item(key = "capture") { QuickInput("Add a task to the backlog…", hue = projectColor(s.p)) { vm.capture(it) } }
        item(key = "backlog") {
            val open = s.backlog.filter { !it.done }
            if (open.isNotEmpty()) LifeCard(Modifier.fillMaxWidth(), radius = 18.dp, padding = PaddingValues(start = 6.dp, end = 10.dp, top = 10.dp, bottom = 8.dp), gap = 0.dp) {
                Row(Modifier.padding(start = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel("BACKLOG · ${open.size}", C.InkSubtle, Modifier.weight(1f))
                    if (open.size > 1) TextAction("All → today") { vm.take(open, s.today, "today") }
                }
                (if (showAllBacklog) open else open.take(8)).forEach { t ->
                    TaskLine(t, projectColor(s.p), trailingLabel = "→ Today", onTrailing = { vm.take(listOf(t), s.today, "today") }, onToggle = null, onMenu = { menu = t })
                }
                if (open.size > 8) TextAction(if (showAllBacklog) "Show less" else "Show all ${open.size}") { showAllBacklog = !showAllBacklog }
            }
        }

        // ---- explicit carry-over of unfinished tasks from before this week
        if (s.overdue.isNotEmpty()) item(key = "overdue") {
            LifeCard(Modifier.fillMaxWidth(), radius = 18.dp, border = C.Fuel.copy(alpha = 0.35f), padding = PaddingValues(start = 6.dp, end = 10.dp, top = 10.dp, bottom = 8.dp), gap = 0.dp) {
                Text("${s.overdue.size} unfinished from earlier — nothing moves on its own", style = T.smallB, color = C.Fuel, modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
                s.overdue.take(6).forEach { t -> TaskLine(t, C.Fuel, meta = "planned ${dayShort(t.day!!)}", trailingLabel = "→ Today", onTrailing = { vm.take(listOf(t), s.today, "today") }, onToggle = { vm.toggleTask(t) }, onMenu = { menu = t }) }
                Row(Modifier.padding(start = 6.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextAction("All → today", C.Fuel) { vm.take(s.overdue, s.today, "today") }
                    TextAction("All → backlog", C.InkMuted) { vm.take(s.overdue, null, "backlog") }
                }
            }
        }

        // ---- week switcher
        item(key = "week") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RoundIconButton(Ic.ChevronLeft, "Previous week", size = 34.dp, bg = C.Track) { vm.page(-1) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ProjectReport.rangeTitle(s.weekStart, s.weekStart + 6), style = T.smallB, color = C.Ink)
                    val inWeek = s.today in s.weekStart..s.weekStart + 6
                    Text(if (inWeek) "This week" else "Tap to go to this week", style = T.caption, color = if (inWeek) C.Grow else C.InkSubtle,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).press(enabled = !inWeek) { vm.thisWeek() })
                }
                RoundIconButton(Ic.ChevronRight, "Next week", size = 34.dp, bg = C.Track) { vm.page(1) }
            }
        }

        // ---- days
        s.layout.shown.forEach { d ->
            item(key = "day-$d") {
                DayCard(
                    s, s.days.getValue(d), vm,
                    onPlan = { f -> plan = d to f }, onActivity = { e -> act = d to e }, onAchievement = { a, pre -> ach = Triple(d, a, pre) },
                    onTaskMenu = { menu = it }, onTimeline = { nav.navigate(ProjectsRoutes.timeline(d)) },
                )
            }
        }
        if (s.layout.collapsed.isNotEmpty()) item(key = "weekend") {
            TextAction("Show weekend (${s.layout.collapsed.joinToString { dayShort(it).substringBefore(' ') }})", C.InkMuted) { vm.expanded.value = true }
        }
        if (s.weekly && s.expanded) item(key = "weekend-hide") { TextAction("Hide empty weekend", C.InkMuted) { vm.expanded.value = false } }
        if (!s.weekly) item(key = "plan-day") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.layout.shown.isEmpty()) Text("Nothing planned this week. Plan a day only when you'll work on it.", style = T.small, color = C.InkMuted, modifier = Modifier.padding(horizontal = 4.dp))
                if (s.layout.addable.isNotEmpty()) {
                    MonoLabel("PLAN A DAY", C.InkSubtle, Modifier.padding(start = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.layout.addable.forEach { d ->
                            val sug = d in s.layout.suggested
                            Text(
                                (if (d == s.today) "Today" else dayShort(d)) + if (sug) " ·" else "", style = T.caption,
                                color = if (sug) C.Grow else C.InkSoft,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (sug) C.GrowTint else C.Graphite2).press { vm.openDay(d) }.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        item(key = "stats") { ProjectStats(s) }
        item(key = "stages") { Stages(s.milestones, vm) }
        if (s.achievements.isNotEmpty()) item(key = "ach") {
            LifeCard(Modifier.fillMaxWidth(), radius = 18.dp, gap = 6.dp) {
                MonoLabel("ACHIEVEMENTS", Kind.Achievement)
                s.achievements.take(8).forEach { a ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).press { ach = Triple(a.day, a, "") }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("★", style = T.body, color = Kind.Achievement)
                        Column(Modifier.weight(1f)) {
                            Text(a.title, style = T.body, color = C.Ink)
                            Text(dayShort(a.day) + if (a.detail.isNotBlank()) " · ${a.detail}" else "", style = T.caption, color = C.InkSubtle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item(key = "reports") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Week report", C.Graphite2, C.InkSoft, Modifier.weight(1f), 42.dp, 12.dp) { nav.navigate(ProjectsRoutes.report(s.p.id, false)) }
                PillButton("Month report", C.Graphite2, C.InkSoft, Modifier.weight(1f), 42.dp, 12.dp) { nav.navigate(ProjectsRoutes.report(s.p.id, true)) }
            }
        }
    }

    val s = st ?: return
    plan?.let { (d, f) ->
        val p = s.days[d]?.plan
        val cur = when (f) { PlanField.Goal -> p?.goal; PlanField.Expected -> p?.expectedResult; PlanField.Result -> p?.result; PlanField.Note -> p?.note }.orEmpty()
        val hue = when (f) { PlanField.Goal -> Kind.Goal; PlanField.Result -> Kind.Result; else -> C.InkSoft }
        TextSheet("${f.title} · ${dayShort(d)}", f.label, cur, hue, f.hint, onDismiss = { plan = null }) { v ->
            vm.setPlan(
                d,
                if (f == PlanField.Goal) v else p?.goal.orEmpty(), if (f == PlanField.Expected) v else p?.expectedResult.orEmpty(),
                if (f == PlanField.Result) v else p?.result.orEmpty(), if (f == PlanField.Note) v else p?.note.orEmpty(),
            )
        }
    }
    act?.let { (d, e) ->
        ActivitySheet(e, d, onDismiss = { act = null }, onDelete = { vm.deleteActivity(it) }) { text, m ->
            vm.saveActivity((e ?: ActivityEntry(projectId = s.p.id, day = d, text = "", ts = 0)).copy(text = text, minutes = m))
        }
    }
    ach?.let { (d, a, pre) ->
        AchievementSheet(a, d, pre, onDismiss = { ach = null }, onDelete = { vm.deleteAchievement(it) }) { title, detail ->
            vm.saveAchievement((a ?: Achievement(day = d, title = "", projectId = s.p.id)).copy(title = title, detail = detail))
        }
    }
    menu?.let { t ->
        TaskMenuSheet(
            t, s.today, s.p.workDays, onDismiss = { menu = null },
            onMove = { d, l -> vm.take(listOf(t), d, l) },
            onStartBlock = { vm.take(listOf(t), s.today, "today"); vm.startBlockNow(t) },
            onRename = { vm.renameTask(t, it) }, onDelete = { vm.deleteTask(t) },
        )
    }
    if (editProject) ProjectSheet(
        s.p, s.categories, onDismiss = { editProject = false }, onManageCategories = { cats = true },
        onDelete = { p -> vm.deleteProject(p) { onBack() } },
    ) { vm.saveProject(it) }
    if (cats) {
        val lvm: com.lifeapi.app.vm.grow3.projects.ProjectsListVM = viewModel()
        CategoriesSheet(s.categories, onDismiss = { cats = false }, onSave = lvm::saveCategory, onDelete = lvm::deleteCategory)
    }
}

@Composable
private fun DayCard(
    s: ProjectDetailState,
    v: DayView,
    vm: ProjectDetailVM,
    onPlan: (PlanField) -> Unit,
    onActivity: (ActivityEntry?) -> Unit,
    onAchievement: (Achievement?, String) -> Unit,
    onTaskMenu: (PlanTask) -> Unit,
    onTimeline: () -> Unit,
) {
    val isToday = v.day == s.today
    val past = v.day < s.today
    var addingTask by remember { mutableStateOf(false) }
    val hue = projectColor(s.p)
    LifeCard(
        Modifier.fillMaxWidth(), radius = 20.dp, border = if (isToday) C.Grow.copy(alpha = 0.45f) else C.Hairline,
        padding = PaddingValues(14.dp), gap = 8.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dayLong(v.day), style = T.section, color = if (isToday) C.Grow else C.Ink)
                val meta = buildList {
                    if (isToday) add("Today")
                    val done = v.tasks.count { it.done }
                    if (v.tasks.isNotEmpty()) add("$done/${v.tasks.size} tasks")
                    if (v.minutes > 0) add(GrowStats.fmtMinutes(v.minutes) + " logged")
                    if (v.blocks.isNotEmpty()) add("${v.blocks.size} block${if (v.blocks.size == 1) "" else "s"}")
                }
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = T.caption, color = C.InkSubtle)
            }
            SmallIconAction(Ic.Today, "Day timeline", onClick = onTimeline)
        }
        KindBlock("Goal", Kind.Goal, v.plan?.goal.orEmpty(), "Set the one main goal…") { onPlan(PlanField.Goal) }
        if (s.weekly || v.plan?.expectedResult?.isNotBlank() == true) KindBlock("Expected result", C.InkSoft, v.plan?.expectedResult.orEmpty(), "What “done” looks like…") { onPlan(PlanField.Expected) }

        // tasks
        if (v.tasks.isNotEmpty() || addingTask) {
            KindLabel("Tasks", C.InkMuted, Modifier.padding(top = 2.dp))
            v.tasks.forEach { t ->
                TaskLine(
                    t, hue, meta = t.movedFrom?.let { "moved from ${dayShort(it)}" },
                    trailingLabel = if (!t.done && past) "→ Today" else null, onTrailing = { vm.take(listOf(t), s.today, "today") },
                    onToggle = { vm.toggleTask(t) }, onMenu = { onTaskMenu(t) },
                )
            }
            val open = v.tasks.filter { !it.done }
            if (past && open.size > 1) TextAction("Carry ${open.size} unfinished → today", C.Fuel) { vm.take(open, s.today, "today") }
        }
        if (addingTask) QuickInput("Task for ${dayShort(v.day)}…", hue = hue, autoFocus = true) { vm.addTask(v.day, it) }

        // activity log — what was actually done
        if (v.activity.isNotEmpty() || v.blocks.any { (it.actualMinutes ?: 0) > 0 || it.result.isNotBlank() }) {
            KindLabel("Activity log · what I did", Kind.Activity, Modifier.padding(top = 2.dp))
            v.activity.forEach { e ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).press { onActivity(e) }.padding(vertical = 4.dp, horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(Kind.Activity))
                    Text(e.text, style = T.body, color = C.Ink, modifier = Modifier.weight(1f))
                    if (e.blockId == null && (e.minutes ?: 0) > 0) Text(GrowStats.fmtMinutes(e.minutes!!), style = T.caption, color = C.InkSubtle)
                }
            }
            v.blocks.filter { (it.actualMinutes ?: 0) > 0 || it.result.isNotBlank() }.forEach { b ->
                Text("${b.start}–${b.end} ${b.title}" + (b.actualMinutes?.let { " · ${GrowStats.fmtMinutes(it)}" } ?: "") + if (b.result.isNotBlank()) " → ${b.result}" else "",
                    style = T.small, color = C.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (v.plan?.result?.isNotBlank() == true || past || isToday) KindBlock("Result", Kind.Result, v.plan?.result.orEmpty(), "What came out of it…") { onPlan(PlanField.Result) }
        if (v.plan?.note?.isNotBlank() == true) KindBlock("Note", C.InkSubtle, v.plan.note, "") { onPlan(PlanField.Note) }
        v.achievements.forEach { a ->
            Text("★ ${a.title}", style = T.smallB, color = Kind.Achievement, modifier = Modifier.clip(RoundedCornerShape(8.dp)).press { onAchievement(a, "") }.padding(vertical = 2.dp))
        }

        // quick actions
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextAction(if (addingTask) "Done adding" else "+ Task", hue) { addingTask = !addingTask }
            TextAction("+ What I did", Kind.Activity) { onActivity(null) }
            TextAction("+ ★", Kind.Achievement) { onAchievement(null, v.plan?.result.orEmpty()) }
            if (v.plan?.note.isNullOrBlank()) TextAction("+ Note", C.InkSubtle) { onPlan(PlanField.Note) }
        }
    }
}

@Composable
private fun ProjectStats(s: ProjectDetailState) {
    val w = s.week; val p = s.prev
    LifeCard(Modifier.fillMaxWidth(), radius = 18.dp, gap = 8.dp) {
        MonoLabel("THIS WEEK", C.InkSubtle)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${w.tasksDone}", "tasks done", deltaText(w.tasksDone - p.tasksDone), Modifier.weight(1f))
            StatTile("${w.goalsAchieved}/${w.goalsSet}", "goals met", deltaText(w.goalsAchieved - p.goalsAchieved), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(GrowStats.fmtMinutes(w.loggedMinutes), "time logged", deltaText(w.loggedMinutes - p.loggedMinutes) { GrowStats.fmtMinutes(it) }, Modifier.weight(1f))
            StatTile("${w.activeDays}", "active days", "${s.milestones.count { it.done }}/${s.milestones.size} stages done".takeIf { s.milestones.isNotEmpty() }, Modifier.weight(1f))
        }
        Text("Time = what you logged (activity minutes + actual block time). Working hours never count.", style = T.caption, color = C.InkFaint)
    }
}

@Composable
private fun Stages(ms: List<Milestone>, vm: ProjectDetailVM) {
    var adding by remember { mutableStateOf(false) }
    LifeCard(Modifier.fillMaxWidth(), radius = 18.dp, gap = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel("STAGES" + if (ms.isNotEmpty()) " · ${ms.count { it.done }}/${ms.size}" else "", C.InkSubtle, Modifier.weight(1f))
            TextAction(if (adding) "Done" else "+ Stage") { adding = !adding }
        }
        if (ms.isEmpty() && !adding) Text("Optional milestones, e.g. “v0.4 · Widgets”.", style = T.caption, color = C.InkFaint)
        ms.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(m.done, Kind.Result, size = 22.dp) { vm.saveMilestone(m.copy(done = !m.done)) }
                Text(m.name, style = T.body, color = if (m.done) C.InkSubtle else C.Ink, modifier = Modifier.weight(1f))
                SmallIconAction(Ic.Trash, "Delete stage", C.InkFaint) { vm.deleteMilestone(m) }
            }
        }
        if (adding) QuickInput("Stage name…", autoFocus = true) { vm.saveMilestone(Milestone(projectId = 0, name = it, sort = ms.size)) }
    }
}
