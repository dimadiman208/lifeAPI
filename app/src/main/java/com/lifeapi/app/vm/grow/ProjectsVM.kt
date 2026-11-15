package com.lifeapi.app.vm.grow

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.FocusSession
import com.lifeapi.app.data.JournalEntry
import com.lifeapi.app.data.Milestone
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.Task
import com.lifeapi.app.data.grow.DoneEvent
import com.lifeapi.app.data.grow.GrowCalc
import com.lifeapi.app.vm.BaseVM
import com.lifeapi.app.vm.weekStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

data class ProjectRow(
    val p: Project,
    val pct: Int,
    val streakLabel: String,
    val daysWorked14: Int,
    val effortMin: Int,
    val effortDeltaMin: Int,
    val output: Int,
    val outputDelta: Int,
    val stalledDays: Int,
    val milestonesDone: Int,
)

data class MilestoneView(val m: Milestone, val tasks: List<Task>, val done: Int)

data class ProjectDetail(
    val row: ProjectRow,
    val current: MilestoneView?,
    val next: MilestoneView?,
    val milestones: List<MilestoneView>,
    val looseTasks: List<Task>,
    val focusToday: Int,
    val focusMin: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsVM(app: Application) : BaseVM(app) {

    private data class Raw(val projects: List<Project>, val ms: List<Milestone>, val tasks: List<Task>, val events: List<DoneEvent>, val focus: List<FocusSession>)

    private val raw = combine(growDao.projects(), growDao.milestones(), growDao.tasks(), growDao.allTaskDone(), growDao.allFocus()) { p, m, t, d, f ->
        Raw(p, m, t, GrowCalc.doneEvents(t, d), f)
    }

    private fun rowFor(p: Project, r: Raw, day: Long): ProjectRow {
        val ws = weekStart(day)
        val tasks = r.tasks.filter { it.projectId == p.id && !it.repeating }
        val ms = r.ms.filter { it.projectId == p.id }
        val pct = when {
            tasks.isNotEmpty() -> tasks.count { it.doneTs != null } * 100 / tasks.size
            ms.isNotEmpty() -> ms.count { it.done } * 100 / ms.size
            else -> 0
        }
        val ev = r.events.filter { it.task.projectId == p.id }
        val fo = r.focus.filter { it.projectId == p.id }
        val active = (ev.map { it.day } + fo.map { it.day }).toSet()
        val streak = GrowCalc.streak(active, day)
        val last = active.filter { it <= day }.maxOrNull()
        val streakLabel = when {
            streak >= 2 -> "$streak-day streak"
            last == null -> "not started"
            last == day -> "worked today"
            else -> "last ${day - last} day${if (day - last == 1L) "" else "s"} ago"
        }
        val effort = GrowCalc.focusMin(fo, ws, day)
        val effortPrev = GrowCalc.focusMin(fo, ws - 7, ws - 1)
        val out = ev.count { it.day in ws..day }
        val outPrev = ev.count { it.day in (ws - 7) until ws }
        val stalled = if (p.nextAction.isBlank() || p.nextSince == 0L) 0 else (day - Days.dayOf(p.nextSince)).toInt()
        return ProjectRow(
            p, pct, streakLabel, active.count { it in (day - 13)..day },
            effort, effort - effortPrev, out, out - outPrev, stalled, ms.count { it.done },
        )
    }

    val list = today.flatMapLatest { day -> raw.map { r -> r.projects.map { rowFor(it, r, day) } } }.state()

    private val detailId = MutableStateFlow<Long?>(null)
    fun bind(id: Long) { detailId.value = id }

    val detail = detailId.filterNotNull().flatMapLatest { id ->
        today.flatMapLatest { day ->
            combine(raw, profile) { r, prof ->
                val p = r.projects.firstOrNull { it.id == id } ?: return@combine null
                val ms = r.ms.filter { it.projectId == id }.map { m ->
                    val t = r.tasks.filter { it.milestoneId == m.id }
                    MilestoneView(m, t, t.count { it.doneTs != null })
                }
                val curIdx = ms.indexOfFirst { !it.m.done }
                ProjectDetail(
                    rowFor(p, r, day),
                    ms.getOrNull(curIdx),
                    if (curIdx >= 0) ms.drop(curIdx + 1).firstOrNull { !it.m.done } else null,
                    ms,
                    r.tasks.filter { it.projectId == id && it.milestoneId == null && !it.repeating && it.doneTs == null },
                    r.focus.count { it.day == day && it.projectId == id },
                    prof.focusMin,
                )
            }
        }
    }.state()

    fun saveProject(p: Project) = launch {
        val base = if (p.id == 0L) p.copy(startDay = if (p.startDay == 0L) today.value else p.startDay) else p
        growDao.upsertProject(if (base.nextAction.isNotBlank() && base.nextSince == 0L) base.copy(nextSince = Days.now()) else base)
    }

    fun archive(p: Project) = act("${p.name} archived") {
        growDao.upsertProject(p.copy(archived = true))
        suspend { growDao.upsertProject(p); Unit }
    }

    fun setNext(p: Project, text: String) = launch {
        growDao.upsertProject(p.copy(nextAction = text.trim(), nextSince = if (text.isBlank()) 0 else Days.now()))
    }

    /** Completes the next action: logs a done task + journal entry, clears the action. */
    fun completeNext(p: Project) {
        if (p.nextAction.isBlank()) return
        act("Done · ${p.nextAction}") {
            val now = Days.now()
            val tid = growDao.upsertTask(Task(title = p.nextAction, day = today.value, projectId = p.id, doneTs = now, createdTs = now))
            val jid = growDao.insertJournal(JournalEntry(day = today.value, ts = now, text = p.nextAction, tag = p.name))
            growDao.upsertProject(p.copy(nextAction = "", nextSince = 0))
            suspend {
                growDao.deleteTask(tid)
                growDao.deleteJournal(JournalEntry(id = jid, day = today.value, ts = now, text = p.nextAction, tag = p.name))
                growDao.upsertProject(p)
            }
        }
    }

    fun addMilestone(projectId: Long, name: String, sort: Int) = launch { growDao.upsertMilestone(Milestone(projectId = projectId, name = name.trim(), sort = sort)) }
    fun renameMilestone(m: Milestone, name: String) = launch { growDao.upsertMilestone(m.copy(name = name.trim())) }
    fun deleteMilestone(m: Milestone) = act("Milestone removed") { growDao.deleteMilestone(m); suspend { growDao.upsertMilestone(m); Unit } }
    fun setMilestoneDone(m: Milestone, done: Boolean) = act(if (done) "Milestone complete · ${m.name}" else null) {
        growDao.upsertMilestone(m.copy(done = done))
        suspend { growDao.upsertMilestone(m); Unit }
    }

    fun addTask(projectId: Long, milestoneId: Long?, title: String) = launch {
        growDao.upsertTask(Task(title = title.trim(), projectId = projectId, milestoneId = milestoneId, createdTs = Days.now()))
    }

    fun toggleTask(t: Task) = act(if (t.doneTs == null) "Done · ${t.title}" else null) {
        growDao.upsertTask(t.copy(doneTs = if (t.doneTs == null) Days.now() else null, day = t.day ?: if (t.doneTs == null) today.value else null))
        suspend { growDao.upsertTask(t); Unit }
    }

    fun promoteToNext(p: Project, t: Task) = launch {
        growDao.upsertProject(p.copy(nextAction = t.title, nextSince = Days.now()))
    }
}
