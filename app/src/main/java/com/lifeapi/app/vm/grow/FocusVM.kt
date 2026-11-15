package com.lifeapi.app.vm.grow

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.FocusSession
import com.lifeapi.app.vm.BaseVM
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

enum class FocusPhase { Ready, Focus, Break, Finished }

data class FocusTimer(
    val phase: FocusPhase = FocusPhase.Ready,
    /** Wall-clock end while running. */
    val endTs: Long = 0,
    /** Remaining ms while paused (null = running). */
    val pausedLeft: Long? = null,
    val totalMs: Long = 25 * 60_000L,
    val startTs: Long = 0,
)

data class FocusInfo(val title: String, val subtitle: String, val todayCount: Int, val focusMin: Int, val breakMin: Int, val projectId: Long?, val taskId: Long?)

@OptIn(ExperimentalCoroutinesApi::class)
class FocusVM(app: Application) : BaseVM(app) {
    val timer = MutableStateFlow(FocusTimer())
    private val target = MutableStateFlow<Pair<Long?, Long?>>(null to null)

    fun bind(taskId: Long?, projectId: Long?) { if (target.value != (taskId to projectId)) target.value = taskId to projectId }

    val info = target.flatMapLatest { (tid, pid) ->
        combine(
            if (tid != null) growDao.task(tid) else flowOf(null),
            growDao.projects(),
            today.flatMapLatest { d -> growDao.focusRange(d, d) },
            profile,
        ) { task, projects, f, p ->
            val projectId = pid ?: task?.projectId
            val project = projects.firstOrNull { it.id == projectId }
            FocusInfo(
                title = task?.title ?: project?.nextAction?.ifBlank { null } ?: project?.name ?: "Deep work",
                subtitle = project?.name ?: if (task != null) "Task" else "Focus",
                todayCount = f.size,
                focusMin = p.focusMin, breakMin = p.breakMin,
                projectId = projectId, taskId = tid,
            )
        }
    }.state()

    fun left(now: Long): Long = timer.value.let { it.pausedLeft ?: (it.endTs - now) }.coerceAtLeast(0)

    fun start() {
        val i = info.value ?: return
        val ms = i.focusMin * 60_000L
        val now = Days.now()
        timer.value = FocusTimer(FocusPhase.Focus, now + ms, null, ms, now)
    }

    fun pauseResume() {
        val t = timer.value
        val now = Days.now()
        timer.value = if (t.pausedLeft == null) t.copy(pausedLeft = (t.endTs - now).coerceAtLeast(0))
        else t.copy(endTs = now + t.pausedLeft, pausedLeft = null)
    }

    fun plus5() {
        val t = timer.value
        val add = 5 * 60_000L
        timer.value = t.copy(endTs = t.endTs + add, pausedLeft = t.pausedLeft?.plus(add), totalMs = t.totalMs + add)
    }

    /** Called by the UI tick when time runs out. */
    fun tick(now: Long) {
        val t = timer.value
        if (t.pausedLeft != null || t.phase == FocusPhase.Ready || t.phase == FocusPhase.Finished) return
        if (now < t.endTs) return
        if (t.phase == FocusPhase.Focus) {
            record((t.totalMs / 60_000L).toInt())
            val i = info.value
            val brk = (i?.breakMin ?: 5) * (if (((i?.todayCount ?: 0) + 1) % 4 == 0) 3 else 1) * 60_000L
            timer.value = FocusTimer(FocusPhase.Break, now + brk, null, brk, now)
        } else {
            timer.value = FocusTimer(FocusPhase.Finished)
        }
    }

    /** Stop early; credits the focused minutes if at least one. */
    fun stop(onDone: () -> Unit) {
        val t = timer.value
        if (t.phase == FocusPhase.Focus) {
            val mins = ((t.totalMs - left(Days.now())) / 60_000L).toInt()
            if (mins >= 1) record(mins)
        }
        timer.value = FocusTimer()
        onDone()
    }

    fun skipBreak() { timer.value = FocusTimer() }

    private fun record(minutes: Int) {
        val i = info.value
        val st = timer.value.startTs.takeIf { it > 0 } ?: Days.now()
        launch {
            growDao.insertFocus(FocusSession(day = today.value, startTs = st, minutes = minutes, taskId = i?.taskId, projectId = i?.projectId))
        }
        c.snack("Focus logged · $minutes min")
    }
}
