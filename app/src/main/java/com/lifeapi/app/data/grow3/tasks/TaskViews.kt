package com.lifeapi.app.data.grow3.tasks

import com.lifeapi.app.data.PlanTask
import java.util.Locale

/** The Tasks tab's views. Overdue is explicit: unfinished tasks on past days — they are never moved automatically. */
enum class TaskView(val label: String) { TODAY("Today"), UPCOMING("Upcoming"), OVERDUE("Overdue"), BACKLOG("Backlog"), DONE("Done"), ALL("All") }

enum class GroupBy { DATE, PROJECT }

/** A header + rows. [day] is set for date groups (null = backlog), [projectId] for project groups. */
data class TaskGroup(val key: String, val day: Long?, val projectId: Long?, val tasks: List<PlanTask>)

data class TaskCounts(val today: Int, val upcoming: Int, val overdue: Int, val backlog: Int, val done: Int, val all: Int) {
    fun of(v: TaskView) = when (v) {
        TaskView.TODAY -> today; TaskView.UPCOMING -> upcoming; TaskView.OVERDUE -> overdue
        TaskView.BACKLOG -> backlog; TaskView.DONE -> done; TaskView.ALL -> all
    }
}

/** Pure filtering / grouping for Grow → Tasks (unit-tested). */
object TaskViews {
    const val UPCOMING_DAYS = 14
    /** How far back the Done view looks (by doneTs day or scheduled day). */
    const val DONE_DAYS = 30

    fun inView(t: PlanTask, v: TaskView, today: Long, doneDay: Long?): Boolean = when (v) {
        TaskView.TODAY -> t.day == today
        TaskView.UPCOMING -> !t.done && t.day != null && t.day > today && t.day <= today + UPCOMING_DAYS
        TaskView.OVERDUE -> !t.done && t.day != null && t.day < today
        TaskView.BACKLOG -> !t.done && t.day == null
        TaskView.DONE -> t.done && (doneDay ?: t.day ?: today) >= today - DONE_DAYS
        TaskView.ALL -> true
    }

    fun counts(tasks: List<PlanTask>, today: Long, doneDayOf: (PlanTask) -> Long? = { null }): TaskCounts {
        fun n(v: TaskView) = tasks.count { inView(it, v, today, doneDayOf(it)) }
        // Today's badge counts what's still open today
        return TaskCounts(tasks.count { it.day == today && !it.done }, n(TaskView.UPCOMING), n(TaskView.OVERDUE), n(TaskView.BACKLOG), n(TaskView.DONE), tasks.count { !it.done })
    }

    private fun norm(s: String) = s.lowercase(Locale.ROOT).trim()

    /** View + project filter (empty = all projects) + search over title (and project name via [projectName]). */
    fun filter(
        tasks: List<PlanTask>, v: TaskView, today: Long, projects: Set<Long>, query: String,
        projectName: (Long) -> String = { "" }, doneDayOf: (PlanTask) -> Long? = { null },
    ): List<PlanTask> {
        val q = norm(query)
        return tasks.filter { t ->
            inView(t, v, today, doneDayOf(t)) &&
                (projects.isEmpty() || t.projectId in projects) &&
                (q.isEmpty() || q.split(' ').filter { it.isNotBlank() }.all { w -> norm(t.title).contains(w) || norm(projectName(t.projectId)).contains(w) })
        }
    }

    /**
     * Groups for display. By date: overdue days ascending, today, future ascending, then backlog (Done view: newest first).
     * By project: in [projectOrder] order. Within a group: open first, then the stored sort, then id.
     */
    fun group(tasks: List<PlanTask>, by: GroupBy, v: TaskView, projectOrder: List<Long>): List<TaskGroup> {
        val within = compareBy<PlanTask>({ it.done }, { it.day ?: Long.MAX_VALUE }, { it.sort }, { it.id })
        return when (by) {
            GroupBy.PROJECT -> {
                val order = projectOrder.withIndex().associate { it.value to it.index }
                tasks.groupBy { it.projectId }.entries
                    .sortedBy { order[it.key] ?: Int.MAX_VALUE }
                    .map { (pid, list) -> TaskGroup("p$pid", null, pid, list.sortedWith(within)) }
            }
            GroupBy.DATE -> {
                val byDay = tasks.groupBy { it.day }
                val days = byDay.keys.filterNotNull().let { if (v == TaskView.DONE) it.sortedDescending() else it.sorted() }
                days.map { d -> TaskGroup("d$d", d, null, byDay.getValue(d).sortedWith(within)) } +
                    (byDay[null]?.let { listOf(TaskGroup("backlog", null, null, it.sortedWith(within))) } ?: emptyList())
            }
        }
    }
}
