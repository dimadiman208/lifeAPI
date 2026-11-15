package com.lifeapi.app.vm.grow3.projects

import android.app.Application
import com.lifeapi.app.data.Achievement
import com.lifeapi.app.data.ActivityEntry
import com.lifeapi.app.data.DayPlan
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Milestone
import com.lifeapi.app.data.PlanTask
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.ProjectCategory
import com.lifeapi.app.data.TimeBlock
import com.lifeapi.app.data.grow3.CarryOver
import com.lifeapi.app.data.grow3.GrowStats
import com.lifeapi.app.data.grow3.TimeMath
import com.lifeapi.app.data.grow3.WeekStats
import com.lifeapi.app.data.grow3.Weeks
import com.lifeapi.app.data.grow3.projects.ProjectReport
import com.lifeapi.app.data.grow3.projects.ReportDay
import com.lifeapi.app.data.grow3.projects.WeekDays
import com.lifeapi.app.data.grow3.projects.WeekLayout
import com.lifeapi.app.vm.BaseVM
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import java.time.LocalTime

// ============================================================================ shared helpers

/** A project is work-style (weekly Mon–Fri planning) when its category says so. */
fun isWeekly(p: Project, cats: List<ProjectCategory>): Boolean =
    cats.firstOrNull { it.id == (p.categoryId ?: ProjectCategory.PERSONAL) }?.weekly ?: (p.categoryId == ProjectCategory.WORK)

fun categoryOf(p: Project, cats: List<ProjectCategory>): ProjectCategory? = cats.firstOrNull { it.id == (p.categoryId ?: ProjectCategory.PERSONAL) }

private fun tsStart(day: Long) = Days.tsOf(day, LocalTime.MIN)
private fun tsEnd(day: Long) = Days.tsOf(day + 1, LocalTime.MIN) - 1

val PROJECT_STATUSES = listOf("Active", "Paused", "Completed", "Archived")

/** Newest-first sort key: a task taken into work lands at the top of that day's list (ORDER BY done, sort). */
fun topSort(): Int = -(Days.now() / 1000 % Int.MAX_VALUE).toInt()

/**
 * Backlog → work in one tap, and the other quick moves. Shared by the Projects tab (cross-project Today) and
 * the project detail. Nothing here runs on its own — every move is a user action.
 */
class TaskOps(private val pd: com.lifeapi.app.data.ProjectsDao) {
    suspend fun capture(projectId: Long, title: String, day: Long? = null): Long? {
        val t = title.trim(); if (t.isEmpty()) return null
        return pd.upsertTask(PlanTask(projectId = projectId, day = day, title = t, createdTs = Days.now(), sort = if (day != null) topSort() else (Days.now() / 1000 % Int.MAX_VALUE).toInt()))
    }

    /** Schedules [tasks] on [day] (null = back to backlog), on top of that day's list. Returns the undo. */
    suspend fun schedule(tasks: List<PlanTask>, day: Long?): com.lifeapi.app.data.Undo? {
        val moving = tasks.filter { it.day != day }
        if (moving.isEmpty()) return null
        val base = topSort()
        pd.updateTasks(moving.mapIndexed { i, t -> t.copy(day = day, sort = if (day != null) base - (moving.size - i) else t.sort, movedFrom = if (t.day != null) t.day else t.movedFrom) })
        return { pd.updateTasks(moving) }
    }

    /** Optional "start block now": a 1-hour block from now for the task's project. Actual time is still logged explicitly. */
    suspend fun startBlockNow(t: PlanTask, day: Long): Long {
        val now = java.time.LocalTime.now()
        val s = now.hour * 60 + now.minute
        val e = (s + 60).coerceAtMost(24 * 60)
        val start = if (e - s < 15) (24 * 60 - 15) else s
        return pd.upsertBlock(TimeBlock(day = day, start = TimeMath.format(start), end = TimeMath.format(maxOf(e, start + 15)), projectId = t.projectId, title = t.title))
    }
}

// ============================================================================ list

data class ProjectRow(
    val p: Project,
    val category: ProjectCategory?,
    val weekly: Boolean,
    val tasksDone: Int,
    val tasksPlanned: Int,
    val minutes: Int,
    val lastDay: Long?,
    val nextOpen: PlanTask?,
)

data class ProjectGroup(val category: ProjectCategory?, val title: String, val rows: List<ProjectRow>)

data class ProjectsListState(
    val today: Long,
    val weekStart: Long,
    val categories: List<ProjectCategory>,
    val filter: String,
    val counts: Map<String, Int>,
    val groups: List<ProjectGroup>,
    val week: WeekStats,
    val prev: WeekStats,
    val results: List<String>,
    val total: Int,
    /** Cross-project "Today": tasks taken into work today (open first). */
    val todayTasks: List<PlanTask>,
    /** Open backlog of active projects, grouped for the "Pull from backlog" picker. */
    val backlog: List<Pair<Project, List<PlanTask>>>,
    val projectsById: Map<Long, Project>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsListVM(app: Application) : BaseVM(app) {
    private val pd = repo.db.projects()
    val filter = MutableStateFlow("Active")

    private data class Range(val tasks: List<PlanTask>, val plans: List<DayPlan>, val act: List<ActivityEntry>, val blocks: List<TimeBlock>, val ach: List<Achievement>)

    private fun range(from: Long, to: Long): Flow<Range> = combine(
        pd.tasksTouching(from, to, tsStart(from), tsEnd(to)), pd.dayPlansRange(from, to), pd.activityRange(from, to),
        pd.blocksRange(from, to), pd.achievementsRange(from, to),
    ) { t, p, a, b, h -> Range(t, p, a, b, h) }

    val state = today.flatMapLatest { day ->
        val ws = Weeks.start(day)
        combine(
            combine(pd.categories(), pd.allProjects(), ::Pair),
            range(ws - 7, ws + 6),
            combine(pd.lastActivityDays(), pd.tasksOnDay(day), pd.openBacklog(), ::Triple),
            filter,
        ) { (cats, projects), r, (last, todayTasks, backlog), f ->
            val lastBy = last.associate { it.projectId to it.day }
            fun stats(from: Long, to: Long, pid: Long?): WeekStats {
                val keep = { id: Long? -> pid == null || id == pid }
                return GrowStats.week(
                    from, to, r.plans.filter { keep(it.projectId) }, r.tasks.filter { keep(it.projectId) },
                    r.act.filter { keep(it.projectId) }, r.blocks.filter { keep(it.projectId) }, r.ach.filter { keep(it.projectId) }, Days::dayOf,
                )
            }
            val rows = projects.map { p ->
                val s = stats(ws, ws + 6, p.id)
                ProjectRow(
                    p, categoryOf(p, cats), isWeekly(p, cats), s.tasksDone, s.tasksPlanned, s.loggedMinutes, lastBy[p.id],
                    r.tasks.filter { it.projectId == p.id && !it.done && it.day != null && it.day >= day }.minByOrNull { it.day!! * 1000 + it.sort },
                )
            }
            val shown = rows.filter { it.p.status == f }
            val groups = cats.map { c -> ProjectGroup(c, c.name, shown.filter { (it.p.categoryId ?: ProjectCategory.PERSONAL) == c.id }) }
                .filter { it.rows.isNotEmpty() } +
                listOfNotNull(shown.filter { r -> cats.none { it.id == (r.p.categoryId ?: ProjectCategory.PERSONAL) } }.takeIf { it.isNotEmpty() }?.let { ProjectGroup(null, "Other", it) })
            val results = (r.plans.filter { it.day in ws..ws + 6 && it.result.isNotBlank() }.map { it.result.trim() } +
                r.ach.filter { it.day in ws..ws + 6 }.map { "★ " + it.title.trim() }).take(6)
            ProjectsListState(
                day, ws, cats, f, PROJECT_STATUSES.associateWith { st -> projects.count { it.status == st } }, groups,
                stats(ws, ws + 6, null), stats(ws - 7, ws - 1, null), results, projects.size,
                todayTasks.filter { t -> projects.any { it.id == t.projectId } },
                projects.filter { it.status == "Active" }.mapNotNull { p -> backlog.filter { it.projectId == p.id }.takeIf { it.isNotEmpty() }?.let { p to it } },
                projects.associateBy { it.id },
            )
        }
    }.state()

    private val ops = TaskOps(pd)

    /** Quick capture into a project's backlog without opening it. */
    fun capture(projectId: Long, title: String) = launch { ops.capture(projectId, title) }
    fun take(tasks: List<PlanTask>, day: Long? = today.value, label: String = "today") =
        act(if (tasks.size == 1) "“${tasks[0].title}” → $label" else "${tasks.size} tasks → $label") { ops.schedule(tasks, day) }
    fun toggleTask(t: PlanTask) = launch { pd.upsertTask(t.copy(done = !t.done, doneTs = if (!t.done) Days.now() else null)) }
    fun renameTask(t: PlanTask, title: String) = launch { if (title.isNotBlank()) pd.upsertTask(t.copy(title = title.trim())) }
    fun deleteTask(t: PlanTask) = act("Task deleted") { pd.deleteTask(t); suspend { pd.upsertTask(t); Unit } }
    fun startBlockNow(t: PlanTask) = act("Block started for “${t.title}” · log the actual time when done") {
        val bid = ops.startBlockNow(t, today.value)
        suspend { pd.deleteBlock(TimeBlock(id = bid, day = today.value, start = "00:00", end = "00:01", title = "")) }
    }

    fun saveProject(p: Project) = launch { pd.saveProject(p) }

    fun deleteProject(p: Project) = act("${p.name} deleted · its log entries were kept") {
        pd.deleteProjectCascade(p.id); null
    }

    fun saveCategory(c: ProjectCategory) = launch { pd.upsertCategory(c) }
    fun deleteCategory(c: ProjectCategory) = act("${c.name} removed · its projects moved to Personal") { pd.deleteCategory(c.id); null }
}

// ============================================================================ detail

data class DayView(
    val day: Long,
    val plan: DayPlan?,
    val tasks: List<PlanTask>,
    val activity: List<ActivityEntry>,
    val blocks: List<TimeBlock>,
    val achievements: List<Achievement>,
    val minutes: Int,
) {
    val hasContent: Boolean
        get() = (plan != null && (plan.goal.isNotBlank() || plan.expectedResult.isNotBlank() || plan.result.isNotBlank() || plan.note.isNotBlank())) ||
            tasks.isNotEmpty() || activity.isNotEmpty() || blocks.isNotEmpty() || achievements.isNotEmpty()
}

data class ProjectDetailState(
    val p: Project,
    val category: ProjectCategory?,
    val categories: List<ProjectCategory>,
    val weekly: Boolean,
    val today: Long,
    val weekStart: Long,
    val layout: WeekDays,
    val days: Map<Long, DayView>,
    val backlog: List<PlanTask>,
    val overdue: List<PlanTask>,
    val week: WeekStats,
    val prev: WeekStats,
    val achievements: List<Achievement>,
    val milestones: List<Milestone>,
    val expanded: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailVM(app: Application) : BaseVM(app) {
    private val pd = repo.db.projects()
    private val id = MutableStateFlow<Long?>(null)
    private val weekStart = MutableStateFlow<Long?>(null)
    val expanded = MutableStateFlow(false)
    private val extra = MutableStateFlow<Set<Long>>(emptySet())

    fun bind(projectId: Long) { if (id.value != projectId) { id.value = projectId; weekStart.value = null; extra.value = emptySet(); expanded.value = false } }
    fun page(delta: Int) { val ws = weekStart.value ?: Weeks.start(today.value); weekStart.value = ws + 7 * delta; extra.value = emptySet() }
    fun thisWeek() { weekStart.value = null; extra.value = emptySet() }
    fun openDay(day: Long) { extra.value = extra.value + day }

    private data class Key(val id: Long, val ws: Long, val today: Long)

    val state = combine(id.filterNotNull(), weekStart, today) { i, w, t -> Key(i, w ?: Weeks.start(t), t) }.flatMapLatest { k ->
        val from = k.ws - 7; val to = k.ws + 6
        combine(
            combine(pd.project(k.id), pd.categories(), pd.milestonesOf(k.id), ::Triple),
            combine(pd.tasksForWeek(k.id, from, to), pd.dayPlans(k.id, from, to), pd.activityOf(k.id, from, to), ::Triple),
            combine(pd.blocksOf(k.id, from, to), pd.achievementsOf(k.id), pd.overdue(k.id, minOf(k.today, k.ws)), ::Triple),
            combine(expanded, extra, ::Pair),
        ) { (p, cats, ms), (tasks, plans, act), (blocks, ach, overdue), (exp, ext) ->
            if (p == null) return@combine null
            val weekly = isWeekly(p, cats)
            val dayOf: (PlanTask) -> Long? = { it.day }
            val views = Weeks.days(k.ws).associateWith { d ->
                val a = act.filter { it.day == d }; val b = blocks.filter { it.day == d }
                DayView(d, plans.firstOrNull { it.day == d }, tasks.filter { dayOf(it) == d }, a, b, ach.filter { it.day == d }, GrowStats.loggedMinutes(a, b))
            }
            val content = views.values.filter { it.hasContent }.map { it.day }.toSet()
            val stats = { f: Long, t: Long -> GrowStats.week(f, t, plans, tasks, act, blocks, ach, Days::dayOf) }
            ProjectDetailState(
                p, categoryOf(p, cats), cats, weekly, k.today, k.ws,
                WeekLayout.layout(k.ws, weekly, p.workDays, content, ext + if (k.today in k.ws..k.ws + 6 && weekly) setOf(k.today) else emptySet(), exp),
                views, tasks.filter { it.day == null }, CarryOver.candidates(overdue, k.ws).filter { it.day!! < k.ws },
                stats(k.ws, k.ws + 6), stats(k.ws - 7, k.ws - 1), ach, ms, exp,
            )
        }
    }.state()

    // ---------- day plan
    private suspend fun plan(day: Long): DayPlan = pd.dayPlanNow(id.value ?: 0, day) ?: DayPlan(projectId = id.value ?: 0, day = day)

    fun setPlan(day: Long, goal: String, expected: String, result: String, note: String) = launch {
        val p = plan(day)
        pd.upsertDayPlan(p.copy(goal = goal.trim(), expectedResult = expected.trim(), result = result.trim(), note = note.trim()))
    }

    // ---------- tasks
    private val ops = TaskOps(pd)
    /** One-line capture into this project's backlog. */
    fun capture(title: String) = launch { id.value?.let { ops.capture(it, title) } }
    fun take(tasks: List<PlanTask>, day: Long?, label: String) =
        act(if (tasks.size == 1) "“${tasks[0].title}” → $label" else "${tasks.size} tasks → $label") { ops.schedule(tasks, day) }
    fun startBlockNow(t: PlanTask) = act("Block started · log the actual time when done") {
        val bid = ops.startBlockNow(t, today.value)
        suspend { pd.deleteBlock(TimeBlock(id = bid, day = today.value, start = "00:00", end = "00:01", title = "")) }
    }

    fun addTask(day: Long?, title: String) = launch {
        val pid = id.value ?: return@launch
        if (title.isBlank()) return@launch
        pd.upsertTask(PlanTask(projectId = pid, day = day, title = title.trim(), createdTs = Days.now(), sort = (Days.now() / 1000).toInt()))
    }

    fun toggleTask(t: PlanTask) = launch { pd.upsertTask(t.copy(done = !t.done, doneTs = if (!t.done) Days.now() else null)) }
    fun renameTask(t: PlanTask, title: String) = launch { if (title.isNotBlank()) pd.upsertTask(t.copy(title = title.trim())) }
    fun deleteTask(t: PlanTask) = act("Task deleted") { pd.deleteTask(t); suspend { pd.upsertTask(t); Unit } }

    /** Explicit move of unfinished tasks (never automatic). to = null → backlog. */
    fun move(tasks: List<PlanTask>, to: Long?, label: String) = act(if (tasks.size == 1) "Moved to $label" else "${tasks.size} tasks moved to $label") {
        val moved = CarryOver.move(tasks, to)
        if (moved.isEmpty()) return@act null
        pd.updateTasks(moved)
        suspend { pd.updateTasks(tasks.filter { t -> moved.any { it.id == t.id } }) }
    }

    // ---------- activity
    fun saveActivity(e: ActivityEntry) = launch {
        if (e.text.isBlank()) return@launch
        pd.upsertActivity(e.copy(projectId = e.projectId ?: id.value, ts = if (e.ts == 0L) Days.now() else e.ts, text = e.text.trim()))
    }

    fun addActivity(day: Long, text: String, minutes: Int?) = saveActivity(ActivityEntry(projectId = id.value, day = day, text = text, minutes = minutes, ts = Days.now()))
    fun deleteActivity(e: ActivityEntry) = act("Entry deleted") { pd.deleteActivity(e); suspend { pd.upsertActivity(e); Unit } }

    // ---------- achievements
    fun saveAchievement(a: Achievement) = launch {
        if (a.title.isBlank()) return@launch
        pd.upsertAchievement(a.copy(projectId = a.projectId ?: id.value, ts = if (a.ts == 0L) Days.now() else a.ts, title = a.title.trim(), detail = a.detail.trim()))
    }
    fun deleteAchievement(a: Achievement) = act("Achievement deleted") { pd.deleteAchievement(a); suspend { pd.upsertAchievement(a); Unit } }

    // ---------- milestones (project stages)
    fun saveMilestone(m: Milestone) = launch { if (m.name.isNotBlank()) pd.upsertMilestone(m.copy(projectId = id.value ?: m.projectId, name = m.name.trim())) }
    fun deleteMilestone(m: Milestone) = act("Stage deleted") { pd.deleteMilestone(m); suspend { pd.upsertMilestone(m); Unit } }

    // ---------- project
    fun saveProject(p: Project) = launch { pd.saveProject(p) }
    fun deleteProject(p: Project, after: () -> Unit) = launch { pd.deleteProjectCascade(p.id); c.snack("${p.name} deleted · its log entries were kept"); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { after() } }
}

// ============================================================================ day timeline

data class TimelineState(
    val day: Long,
    val today: Long,
    val blocks: List<TimeBlock>,
    val projects: List<Project>,
    val conflicts: Set<Long>,
    /** Earliest start / latest end of active work-style projects (light background band — a plan, not logged time). */
    val workBand: Pair<Int, Int>?,
    val logged: Int,
    val planned: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineVM(app: Application) : BaseVM(app) {
    private val pd = repo.db.projects()
    private val day = MutableStateFlow<Long?>(null)

    fun bind(d: Long?) { if (day.value == null) day.value = d }
    fun page(delta: Int) { day.value = (day.value ?: today.value) + delta }
    fun select(d: Long) { day.value = d }

    val state = combine(day, today) { d, t -> (d ?: t) to t }.flatMapLatest { (d, t) ->
        combine(pd.blocksOn(d), pd.allProjects(), pd.categories()) { blocks, projects, cats ->
            val active = projects.filter { it.status == "Active" }
            val band = active.filter { isWeekly(it, cats) && Weeks.inMask(it.workDays, d) }
                .mapNotNull { p -> val s = TimeMath.parse(p.workStart); val e = TimeMath.parse(p.workEnd); if (s != null && e != null && e > s) s to e else null }
                .takeIf { it.isNotEmpty() }?.let { l -> l.minOf { it.first } to l.maxOf { it.second } }
            TimelineState(
                d, t, blocks.sortedBy { TimeMath.parse(it.start) ?: 0 }, projects.filter { it.status != "Archived" },
                blocks.filter { TimeMath.conflicts(it, blocks).isNotEmpty() }.map { it.id }.toSet(), band,
                blocks.sumOf { it.actualMinutes ?: 0 }, blocks.sumOf { TimeMath.duration(it.start, it.end) ?: 0 },
            )
        }
    }.state()

    /** Suggested slot for a new block on [d]: the first free hour inside the given (or default) work hours. */
    suspend fun suggestSlot(d: Long, project: Project?): Pair<String, String> =
        TimeMath.nextFreeSlot(pd.blocksOnNow(d), project?.workStart ?: "09:00", project?.workEnd ?: "18:00", 60)

    fun saveBlock(b: TimeBlock) = launch {
        if (TimeMath.duration(b.start, b.end) == null || b.title.isBlank()) return@launch
        pd.upsertBlock(b.copy(title = b.title.trim(), description = b.description.trim(), result = b.result.trim()))
    }

    fun deleteBlock(b: TimeBlock) = act("Block deleted") { pd.deleteBlock(b); suspend { pd.upsertBlock(b); Unit } }
}

// ============================================================================ report

data class ReportState(
    val title: String,
    val from: Long,
    val to: Long,
    val monthly: Boolean,
    val days: List<ReportDay>,
    val stats: WeekStats,
    val projects: Map<Long, Project>,
    val text: String,
    val scopeLabel: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportVM(app: Application) : BaseVM(app) {
    private val pd = repo.db.projects()
    /** projectId, or -1 = all work-style projects, -2 = all projects. */
    private val scope = MutableStateFlow<Long?>(null)
    val monthly = MutableStateFlow(false)
    private val anchor = MutableStateFlow<Long?>(null)

    fun bind(projectId: Long, month: Boolean) { if (scope.value == null) { scope.value = projectId; monthly.value = month } }
    fun setMonthly(m: Boolean) { monthly.value = m; anchor.value = null }
    fun page(delta: Int) {
        val a = anchor.value ?: today.value
        anchor.value = if (monthly.value) LocalDate.ofEpochDay(a).plusMonths(delta.toLong()).toEpochDay() else a + 7 * delta
    }
    fun setScope(s: Long) { scope.value = s }

    private data class Key(val scope: Long, val from: Long, val to: Long, val monthly: Boolean)

    val state = combine(scope.filterNotNull(), monthly, anchor, today) { s, m, a, t ->
        val d = a ?: t
        if (m) { val ld = LocalDate.ofEpochDay(d).withDayOfMonth(1); Key(s, ld.toEpochDay(), ld.plusMonths(1).toEpochDay() - 1, true) }
        else Key(s, Weeks.start(d), Weeks.end(d), false)
    }.flatMapLatest { k ->
        combine(
            combine(pd.allProjects(), pd.categories(), ::Pair),
            pd.tasksTouching(k.from, k.to, tsStart(k.from), tsEnd(k.to)), pd.dayPlansRange(k.from, k.to),
            combine(pd.activityRange(k.from, k.to), pd.blocksRange(k.from, k.to), ::Pair), pd.achievementsRange(k.from, k.to),
        ) { (projects, cats), tasks, plans, (act, blocks), ach ->
            val ids: Set<Long?> = when (k.scope) {
                -1L -> projects.filter { isWeekly(it, cats) }.map { it.id }.toSet()
                -2L -> (projects.map { it.id } + listOf(null)).toSet()
                else -> setOf(k.scope)
            }
            val keep = { pid: Long? -> pid in ids }
            val pl = plans.filter { keep(it.projectId) }; val tk = tasks.filter { keep(it.projectId) }
            val ac = act.filter { keep(it.projectId) }; val bl = blocks.filter { keep(it.projectId) }; val ah = ach.filter { keep(it.projectId) }
            val days = ProjectReport.group(k.from, k.to, pl, tk, ac, bl, ah, Days::dayOf, includeOpenTasks = true)
            val stats = GrowStats.week(k.from, k.to, pl, tk, ac, bl, ah, Days::dayOf)
            val scopeLabel = when (k.scope) { -1L -> "All work projects"; -2L -> "All projects"; else -> projects.firstOrNull { it.id == k.scope }?.name ?: "Project" }
            val title = (if (k.monthly) "Month report" else "Week report") + " · " + scopeLabel
            val byId = projects.associateBy { it.id }
            ReportState(title, k.from, k.to, k.monthly, days, stats, byId, ProjectReport.text(title, k.from, k.to, days, byId, stats), scopeLabel)
        }
    }.state()
}
