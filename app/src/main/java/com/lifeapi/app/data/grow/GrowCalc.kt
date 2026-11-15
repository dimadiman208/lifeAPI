package com.lifeapi.app.data.grow

import com.lifeapi.app.data.Days
import com.lifeapi.app.data.FocusSession
import com.lifeapi.app.data.LearningLog
import com.lifeapi.app.data.Task
import com.lifeapi.app.data.TaskDone
import java.time.format.TextStyle
import java.util.Locale

/** One completed task occurrence. */
data class DoneEvent(val task: Task, val day: Long, val ts: Long)

object GrowCalc {
    /** All completion events: one-off tasks by doneTs, repeating by TaskDone rows. */
    fun doneEvents(tasks: List<Task>, done: List<TaskDone>): List<DoneEvent> {
        val byId = tasks.associateBy { it.id }
        val oneOff = tasks.filter { !it.repeating && it.doneTs != null }.map { DoneEvent(it, Days.dayOf(it.doneTs!!), it.doneTs) }
        val rep = done.mapNotNull { d -> byId[d.taskId]?.let { DoneEvent(it, d.day, d.ts) } }
        return oneOff + rep
    }

    fun isDone(t: Task, day: Long, done: Set<Pair<Long, Long>>): Boolean =
        if (t.repeating) (t.id to day) in done else t.doneTs != null

    fun focusMin(f: List<FocusSession>, from: Long, to: Long, projectId: Long? = null) =
        f.filter { it.day in from..to && (projectId == null || it.projectId == projectId) }.sumOf { it.minutes }

    fun learnMin(l: List<LearningLog>, from: Long, to: Long) = l.filter { it.day in from..to }.sumOf { it.minutes }

    fun hours(min: Int): String = if (min == 0) "0 h" else if (min < 60) "$min m" else String.format(Locale.US, "%.1f h", min / 60.0).replace(".0 h", " h")

    fun signedHours(minDelta: Int): String {
        val s = if (minDelta >= 0) "+" else "−"
        val a = kotlin.math.abs(minDelta)
        return s + if (a < 60) "$a m" else String.format(Locale.US, "%.1f h", a / 60.0).replace(".0 h", " h")
    }

    fun dayLabel(day: Long, today: Long): String = when (day) {
        today -> "Today"
        today + 1 -> "Tomorrow"
        today - 1 -> "Yesterday"
        else -> {
            val d = Days.date(day)
            "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
        }
    }

    fun shortDate(day: Long): String {
        val d = Days.date(day)
        return "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
    }

    val WEEK_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
    val WEEK_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun repeatLabel(mask: Int): String = when (mask) {
        0 -> ""
        127 -> "daily"
        31 -> "weekdays"
        96 -> "weekends"
        else -> (0..6).filter { mask and (1 shl it) != 0 }.joinToString(" ") { WEEK_SHORT[it] }
    }

    /** Consecutive active days ending today (or yesterday). */
    fun streak(active: Set<Long>, today: Long): Int {
        var d = if (today in active) today else today - 1
        var n = 0
        while (d in active) { n++; d-- }
        return n
    }
}

/** Result of parsing a quick-add line like "call mom tmrw 18:00 !". */
data class QuickTask(val title: String, val day: Long?, val time: String, val top: Boolean)

object TaskParser {
    private val dows = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    fun parse(raw: String, today: Long, defaultDay: Long?): QuickTask {
        var s = " ${raw.trim()} "
        var top = false
        if (s.contains(" !") || s.trim().startsWith("!") || s.trim().endsWith("!")) {
            top = true; s = s.replace("!", " ")
        }
        var time = ""
        Regex("""\s(\d{1,2})[:.](\d{2})\s""").find(s)?.let { m ->
            val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            if (h in 0..23 && mi in 0..59) { time = String.format(Locale.US, "%02d:%02d", h, mi); s = s.replaceRange(m.range, " ") }
        }
        var day: Long? = defaultDay
        val lower = s.lowercase()
        fun cut(word: String) { s = Regex("(?i)\\s$word\\s").replaceFirst(s, " ") }
        when {
            Regex("""\s(tmrw|tomorrow|tmr)\s""").containsMatchIn(lower) -> {
                day = today + 1; listOf("tmrw", "tomorrow", "tmr").forEach(::cut)
            }
            Regex("""\stoday\s""").containsMatchIn(lower) -> { day = today; cut("today") }
            Regex("""\s(someday|inbox)\s""").containsMatchIn(lower) -> { day = null; cut("someday"); cut("inbox") }
            else -> {
                val m = Regex("""\s(on\s)?(mon|tue|wed|thu|fri|sat|sun)[a-z]*\s""").find(lower)
                if (m != null) {
                    val target = dows.indexOf(m.groupValues[2])
                    val cur = Days.date(today).dayOfWeek.value - 1
                    var delta = (target - cur + 7) % 7
                    if (delta == 0) delta = 7
                    day = today + delta
                    s = s.replaceRange(m.range, " ")
                }
            }
        }
        if (time.isNotEmpty() && day == null) day = today
        val title = s.trim().replace(Regex("\\s+"), " ").replaceFirstChar { it.uppercase() }
        return QuickTask(title, day, time, top)
    }
}
