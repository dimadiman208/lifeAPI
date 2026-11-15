package com.lifeapi.app.data.grow3.projects

import com.lifeapi.app.data.Achievement
import com.lifeapi.app.data.ActivityEntry
import com.lifeapi.app.data.DayPlan
import com.lifeapi.app.data.PlanTask
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.TimeBlock
import com.lifeapi.app.data.grow3.GrowStats
import com.lifeapi.app.data.grow3.WeekStats
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** One project's part of one day in a report: plan → what happened → results. */
data class ReportItem(
    val projectId: Long?,
    val goal: String,
    val expected: String,
    val result: String,
    val note: String,
    val doneTasks: List<PlanTask>,
    val openTasks: List<PlanTask>,
    val activity: List<ActivityEntry>,
    val blocks: List<TimeBlock>,
    val achievements: List<Achievement>,
    /** Logged minutes by the GrowStats rule (activity without block + block.actualMinutes). */
    val minutes: Int,
) {
    val isEmpty: Boolean
        get() = goal.isBlank() && expected.isBlank() && result.isBlank() && note.isBlank() && doneTasks.isEmpty() &&
            openTasks.isEmpty() && activity.isEmpty() && achievements.isEmpty() && blocks.none { it.result.isNotBlank() || (it.actualMinutes ?: 0) > 0 }
}

data class ReportDay(val day: Long, val items: List<ReportItem>) {
    val minutes: Int get() = items.sumOf { it.minutes }
}

/**
 * Groups plans, tasks, activity, blocks and achievements by day and project — the backbone of the
 * project week view and the weekly / monthly report (what was planned, what was done, what came out of it).
 */
object ProjectReport {

    /**
     * Days in [from]..[to] (ascending) that have anything worth reporting. [dayOfTs] maps a task's doneTs to a day
     * (used for backlog tasks finished in the range). Rows may span several projects.
     */
    fun group(
        from: Long, to: Long,
        plans: List<DayPlan>, tasks: List<PlanTask>, activity: List<ActivityEntry>, blocks: List<TimeBlock>,
        achievements: List<Achievement>, dayOfTs: (Long) -> Long,
        includeOpenTasks: Boolean = true,
    ): List<ReportDay> {
        val doneDay: (PlanTask) -> Long? = { t -> if (!t.done) null else t.day ?: t.doneTs?.let(dayOfTs) }
        val out = mutableListOf<ReportDay>()
        for (d in from..to) {
            val pl = plans.filter { it.day == d }
            val done = tasks.filter { doneDay(it) == d }
            val open = if (includeOpenTasks) tasks.filter { !it.done && it.day == d } else emptyList()
            val act = activity.filter { it.day == d }
            val bl = blocks.filter { it.day == d }
            val ach = achievements.filter { it.day == d }
            val pids: List<Long?> = (pl.map { it.projectId } + done.map { it.projectId } + open.map { it.projectId } +
                act.map { it.projectId } + bl.map { it.projectId } + ach.map { it.projectId }).distinct()
            val items = pids.map { pid ->
                val p = pl.firstOrNull { it.projectId == pid }
                val a = act.filter { it.projectId == pid }
                val b = bl.filter { it.projectId == pid }
                ReportItem(
                    projectId = pid,
                    goal = p?.goal.orEmpty(), expected = p?.expectedResult.orEmpty(), result = p?.result.orEmpty(), note = p?.note.orEmpty(),
                    doneTasks = done.filter { it.projectId == pid },
                    openTasks = open.filter { it.projectId == pid },
                    activity = a, blocks = b,
                    achievements = ach.filter { it.projectId == pid },
                    minutes = GrowStats.loggedMinutes(a, b),
                )
            }.filterNot { it.isEmpty }
            if (items.isNotEmpty()) out += ReportDay(d, items)
        }
        return out
    }

    fun dayTitle(day: Long): String {
        val d = LocalDate.ofEpochDay(day)
        return "${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
    }

    fun rangeTitle(from: Long, to: Long): String {
        val a = LocalDate.ofEpochDay(from); val b = LocalDate.ofEpochDay(to)
        val ma = a.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH); val mb = b.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        return when {
            a.year != b.year -> "${a.dayOfMonth} $ma ${a.year} – ${b.dayOfMonth} $mb ${b.year}"
            a.month != b.month -> "${a.dayOfMonth} $ma – ${b.dayOfMonth} $mb ${b.year}"
            else -> "${a.dayOfMonth}–${b.dayOfMonth} $mb ${b.year}"
        }
    }

    /**
     * Plain-text report for sharing (1-on-1s): summary counts, then per day goal → tasks done → activity → result →
     * achievements. Unfinished tasks are listed as "Open" so nothing is silently dropped.
     */
    fun text(title: String, from: Long, to: Long, days: List<ReportDay>, projects: Map<Long, Project>, stats: WeekStats?): String = buildString {
        appendLine(title)
        appendLine(rangeTitle(from, to))
        if (stats != null) {
            val parts = mutableListOf("${stats.tasksDone} task${if (stats.tasksDone == 1) "" else "s"} done")
            if (stats.goalsSet > 0) parts += "${stats.goalsAchieved}/${stats.goalsSet} day goals achieved"
            if (stats.loggedMinutes > 0) parts += "${GrowStats.fmtMinutes(stats.loggedMinutes)} logged"
            parts += "${stats.activeDays} active day${if (stats.activeDays == 1) "" else "s"}"
            if (stats.achievements > 0) parts += "${stats.achievements} achievement${if (stats.achievements == 1) "" else "s"}"
            appendLine(parts.joinToString(" · "))
        }
        if (days.isEmpty()) { appendLine(); appendLine("Nothing recorded in this period."); return@buildString }
        val multi = days.flatMap { d -> d.items.map { it.projectId } }.distinct().size > 1
        for (d in days) {
            appendLine()
            appendLine(dayTitle(d.day) + if (d.minutes > 0) " · ${GrowStats.fmtMinutes(d.minutes)}" else "")
            for (it in d.items) {
                val pad = if (multi) "  " else ""
                if (multi) appendLine("[" + (it.projectId?.let { id -> projects[id]?.name } ?: "No project") + "]")
                if (it.goal.isNotBlank()) appendLine("${pad}Goal: ${it.goal.trim()}")
                if (it.expected.isNotBlank() && it.result.isBlank()) appendLine("${pad}Expected: ${it.expected.trim()}")
                if (it.doneTasks.isNotEmpty()) {
                    appendLine("${pad}Done:")
                    it.doneTasks.forEach { t -> appendLine("$pad- ${t.title.trim()}") }
                }
                if (it.openTasks.isNotEmpty()) {
                    appendLine("${pad}Open:")
                    it.openTasks.forEach { t -> appendLine("$pad- ${t.title.trim()}") }
                }
                val acts = it.activity.filter { a -> a.text.isNotBlank() }
                val blockLines = it.blocks.filter { b -> b.result.isNotBlank() || (b.actualMinutes ?: 0) > 0 }
                if (acts.isNotEmpty() || blockLines.isNotEmpty()) {
                    appendLine("${pad}Activity:")
                    acts.forEach { a ->
                        val m = if (a.blockId == null && (a.minutes ?: 0) > 0) " (${GrowStats.fmtMinutes(a.minutes!!)})" else ""
                        appendLine("$pad- ${a.text.trim()}$m")
                    }
                    blockLines.forEach { b ->
                        val m = b.actualMinutes?.takeIf { m -> m > 0 }?.let { m -> " (${GrowStats.fmtMinutes(m)})" } ?: ""
                        appendLine("$pad- ${b.start}–${b.end} ${b.title.trim()}$m" + if (b.result.isNotBlank()) " → ${b.result.trim()}" else "")
                    }
                }
                if (it.result.isNotBlank()) appendLine("${pad}Result: ${it.result.trim()}")
                it.achievements.forEach { a -> appendLine("${pad}★ ${a.title.trim()}" + if (a.detail.isNotBlank()) " — ${a.detail.trim()}" else "") }
                if (it.note.isNotBlank()) appendLine("${pad}Note: ${it.note.trim()}")
            }
        }
    }.trimEnd()
}
