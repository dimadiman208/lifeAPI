package com.lifeapi.app.data.grow3.projects

import com.lifeapi.app.data.grow3.Weeks

/** Which day cards a project's week shows. Nothing here forces the user to fill every day. */
data class WeekDays(
    /** Day cards to render, ascending. */
    val shown: List<Long>,
    /** Weekend days of a work-style week that are hidden (collapsed) because they're empty. */
    val collapsed: List<Long>,
    /** Personal projects: days you may add a plan for; [suggested] are your usual days. */
    val addable: List<Long>,
    val suggested: Set<Long>,
)

object WeekLayout {
    /**
     * Work-style (weekly) projects: every working day of [workDays] is shown; other days only when they have content
     * or [expanded] is on. Personal/custom: only days with content (plus [extra] days the user just opened);
     * the rest are offered as "plan a day" chips, the usual days ([workDays]) marked as suggestions.
     */
    fun layout(weekStart: Long, weekly: Boolean, workDays: Int, contentDays: Set<Long>, extra: Set<Long> = emptySet(), expanded: Boolean = false): WeekDays {
        val all = Weeks.days(weekStart)
        return if (weekly) {
            val mask = if (workDays and Weeks.ALL_DAYS == 0) Weeks.WORKDAYS else workDays
            val off = all.filter { !Weeks.inMask(mask, it) }
            val shown = all.filter { Weeks.inMask(mask, it) || it in contentDays || it in extra || expanded }
            WeekDays(shown, off.filter { it !in shown }, emptyList(), emptySet())
        } else {
            val shown = all.filter { it in contentDays || it in extra }
            val addable = all.filter { it !in shown }
            WeekDays(shown, emptyList(), addable, addable.filter { Weeks.inMask(workDays, it) }.toSet())
        }
    }
}
