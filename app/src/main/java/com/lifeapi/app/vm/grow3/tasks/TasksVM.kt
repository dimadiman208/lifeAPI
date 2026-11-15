package com.lifeapi.app.vm.grow3.tasks

import android.app.Application
import android.content.Context
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.PlanTask
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.grow3.tasks.GroupBy
import com.lifeapi.app.data.grow3.tasks.TaskCounts
import com.lifeapi.app.data.grow3.tasks.TaskGroup
import com.lifeapi.app.data.grow3.tasks.TaskView
import com.lifeapi.app.data.grow3.tasks.TaskViews
import com.lifeapi.app.vm.BaseVM
import com.lifeapi.app.vm.grow3.projects.TaskOps
import com.lifeapi.app.vm.grow3.projects.topSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

data class TasksState(
    val today: Long,
    val view: TaskView,
    val groupBy: GroupBy,
    val query: String,
    val projectFilter: Set<Long>,
    val counts: TaskCounts,
    val groups: List<TaskGroup>,
    val shown: Int,
    /** Projects you can put tasks into (not Archived), in list order. */
    val projects: List<Project>,
    val projectsById: Map<Long, Project>,
    val lastProject: Long?,
)

/** Grow → Tasks: every PlanTask across projects — add, check, re-date, move between projects, bulk edit. */
class TasksVM(app: Application) : BaseVM(app) {
    private val pd = repo.db.projects()
    private val ops = TaskOps(pd)
    private val prefs = app.getSharedPreferences("grow_tasks", Context.MODE_PRIVATE)

    val view = MutableStateFlow(TaskView.TODAY)
    val groupBy = MutableStateFlow(GroupBy.DATE)
    val query = MutableStateFlow("")
    val projectFilter = MutableStateFlow<Set<Long>>(emptySet())
    private val last = MutableStateFlow(prefs.getLong("last_project", -1L).takeIf { it > 0 })

    private val ui = combine(view, groupBy, query, projectFilter, last) { v, g, q, f, l -> Ui(v, g, q, f, l) }
    private data class Ui(val v: TaskView, val g: GroupBy, val q: String, val f: Set<Long>, val last: Long?)

    val state = combine(pd.allTasks(), pd.allProjects(), today, ui) { tasks, projects, day, u ->
        val byId = projects.associateBy { it.id }
        val usable = projects.filter { it.status != "Archived" }
        val doneDay: (PlanTask) -> Long? = { t -> t.doneTs?.let { Days.dayOf(it) } }
        val filtered = TaskViews.filter(tasks, u.v, day, u.f, u.q, { byId[it]?.name ?: "" }, doneDay)
        val groups = TaskViews.group(filtered, if (u.v == TaskView.BACKLOG) GroupBy.PROJECT else u.g, u.v, projects.map { it.id })
        TasksState(
            day, u.v, u.g, u.q, u.f.filter { it in byId }.toSet(), TaskViews.counts(tasks, day, doneDay), groups, filtered.size,
            usable, byId, u.last?.takeIf { id -> usable.any { it.id == id } } ?: usable.firstOrNull()?.id,
        )
    }.state()

    fun toggleFilter(pid: Long) { projectFilter.value = projectFilter.value.let { if (pid in it) it - pid else it + pid } }

    private fun remember(pid: Long) { last.value = pid; prefs.edit().putLong("last_project", pid).apply() }

    fun add(title: String, projectId: Long, day: Long?) = launch {
        remember(projectId)
        ops.capture(projectId, title, day)
    }

    fun toggle(t: PlanTask) = launch { pd.upsertTask(t.copy(done = !t.done, doneTs = if (!t.done) Days.now() else null)) }

    fun rename(t: PlanTask, title: String) = launch { if (title.isNotBlank() && title.trim() != t.title) pd.upsertTask(t.copy(title = title.trim())) }

    /** Re-date (null = backlog). Explicit, with Undo. */
    fun move(tasks: List<PlanTask>, day: Long?, label: String) =
        act(if (tasks.size == 1) "Moved to $label" else "${tasks.size} tasks moved to $label") { ops.schedule(tasks, day) }

    fun moveToProject(tasks: List<PlanTask>, p: Project) = act(if (tasks.size == 1) "Moved to ${p.name}" else "${tasks.size} tasks moved to ${p.name}") {
        val moving = tasks.filter { it.projectId != p.id }
        if (moving.isEmpty()) return@act null
        remember(p.id)
        val base = topSort()
        pd.updateTasks(moving.mapIndexed { i, t -> t.copy(projectId = p.id, milestoneId = null, sort = base - (moving.size - i)) })
        suspend { pd.updateTasks(moving) }
    }

    fun markDone(tasks: List<PlanTask>, done: Boolean = true) = act(if (done) "${tasks.size} marked done" else "${tasks.size} reopened") {
        val changing = tasks.filter { it.done != done }
        if (changing.isEmpty()) return@act null
        pd.updateTasks(changing.map { it.copy(done = done, doneTs = if (done) Days.now() else null) })
        suspend { pd.updateTasks(changing) }
    }

    fun delete(tasks: List<PlanTask>) = act(if (tasks.size == 1) "Task deleted" else "${tasks.size} tasks deleted") {
        tasks.forEach { pd.deleteTask(it) }
        suspend { pd.insertTasks(tasks) }
    }

    fun startBlockNow(t: PlanTask) = act("Block started for “${t.title}” · log the actual time when done") {
        val day = today.value
        val undoMove = ops.schedule(listOf(t), day)
        val id = ops.startBlockNow(t, day)
        suspend { pd.blocksOnNow(day).firstOrNull { it.id == id }?.let { pd.deleteBlock(it) }; undoMove?.invoke() }
    }
}
