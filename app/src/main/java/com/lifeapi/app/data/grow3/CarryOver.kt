package com.lifeapi.app.data.grow3

import com.lifeapi.app.data.PlanTask

/**
 * Moving unfinished tasks is always an explicit user action — nothing here runs automatically.
 * The moved copy keeps its id (no duplicates) and records where it came from in movedFrom.
 */
object CarryOver {
    /** Unfinished tasks scheduled before [today] — show them with "Move to today / next workday / backlog". */
    fun candidates(tasks: List<PlanTask>, today: Long): List<PlanTask> = tasks.filter { !it.done && it.day != null && it.day < today }

    /** Returns the updated rows to write; done tasks and tasks already on [to] are left alone. to = null → backlog. */
    fun move(tasks: List<PlanTask>, to: Long?): List<PlanTask> =
        tasks.filter { !it.done && it.day != to }.map { it.copy(day = to, movedFrom = it.day ?: it.movedFrom) }
}
