package com.lifeapi.app.vm.grow

import com.lifeapi.app.data.Days
import com.lifeapi.app.data.FocusSession
import com.lifeapi.app.data.Goal
import com.lifeapi.app.data.Habit
import com.lifeapi.app.data.HabitCheck
import com.lifeapi.app.data.JournalEntry
import com.lifeapi.app.data.LearningLog
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.WorkoutSet
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.data.grow.DoneEvent
import com.lifeapi.app.data.grow.GrowCalc
import com.lifeapi.app.vm.weekStart
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

data class GoalView(val g: Goal, val value: String, val progress: Float, val note: String, val onTrack: Boolean?, val daysLeft: Int?)

data class SkillRow(val name: String, val minutes: Int, val sessions: Int, val meta: String, val isNew: Boolean)

data class Suggestion(val text: String, val tag: String)

data class ReviewDim(val value: String, val delta: String, val sub: String, val positive: Boolean?)

data class Review(
    val title: String,
    val range: String,
    val consistency: ReviewDim,
    val effort: ReviewDim,
    val output: ReviewDim,
    val weeks: List<Triple<String, Float, Float>>, // label, effort h, output
    val highlights: List<String>,
)

data class JournalDay(val day: Long, val label: String, val entries: List<JournalEntry>)

data class GrowthExtra(
    val journalToday: List<JournalEntry>,
    val journalPast: List<JournalDay>,
    val suggestions: List<Suggestion>,
    val tags: List<String>,
    val goals30: List<GoalView>,
    val goals90: List<GoalView>,
    val skills: List<SkillRow>,
    val week: Review,
    val month: Review,
    val habits: List<Habit>,
    val lifts: List<String>,
    val projects: List<Project>,
    val learnTopics: List<String>,
)

object GrowthCalc {
    fun skillName(topic: String) = topic.substringBefore(" · ").substringBefore(" - ").substringBefore(" — ").trim()

    fun goalView(g: Goal, day: Long, checks: List<HabitCheck>, sets: List<WorkoutSet>, events: List<DoneEvent>, learning: List<LearningLog>, habits: List<Habit>, projects: List<Project>): GoalView {
        val from = if (g.startDay > 0) g.startDay else Long.MIN_VALUE
        val kind = g.link.substringBefore(":", "")
        val arg = g.link.substringAfter(":", "")
        var value: String
        val current: Double
        var note: String
        when (kind) {
            "habit" -> {
                val hid = arg.toLongOrNull()
                val days = checks.filter { it.habitId == hid && it.day >= from && it.day <= day }.map { it.day }.toSet()
                current = days.size.toDouble()
                value = "${days.size} / ${fmtKg(g.target)}"
                val st = GrowCalc.streak(checks.filter { it.habitId == hid }.map { it.day }.toSet(), day)
                note = "Linked to habit${habits.firstOrNull { it.id == hid }?.let { " “${it.name}”" } ?: ""}" + if (st > 1) " · $st-day streak" else ""
            }
            "lift" -> {
                val s = sets.filter { it.exercise.equals(arg, true) && !it.warmup && it.kg > 0 }
                val best = s.maxByOrNull { it.e1rm }
                current = best?.e1rm ?: 0.0
                value = best?.let { "${fmtKg(it.kg)} × ${it.reps}" } ?: "no sets yet"
                note = "Linked to Train · e1RM ${String.format(Locale.US, "%.1f", current)} → needs ${fmtKg(g.target)}"
            }
            "project" -> {
                val pid = arg.toLongOrNull()
                current = events.count { it.task.projectId == pid && it.day >= from }.toDouble()
                value = "${current.toInt()} / ${fmtKg(g.target)}"
                note = "Linked to ${projects.firstOrNull { it.id == pid }?.name ?: "project"} · tasks done"
            }
            "learning" -> {
                val min = learning.filter { it.topic.contains(arg, true) && it.day >= from }.sumOf { it.minutes }
                current = min / 60.0
                value = "${String.format(Locale.US, "%.1f", current).removeSuffix(".0")} / ${fmtKg(g.target)} h"
                note = "Linked to learning · “$arg”"
            }
            else -> {
                current = g.current
                value = "${fmtKg(g.current)} / ${fmtKg(g.target)}${if (g.unit.isNotBlank()) " ${g.unit}" else ""}"
                note = ""
            }
        }
        val progress = if (g.target > 0) (current / g.target).toFloat().coerceIn(0f, 1f) else 0f
        var onTrack: Boolean? = null
        var daysLeft: Int? = null
        if (current >= g.target && g.target > 0) {
            onTrack = true
            note = "Done — nice." + if (note.isNotBlank()) " · $note" else ""
        } else if (g.startDay > 0) {
            val elapsed = (day - g.startDay + 1).coerceAtLeast(1)
            val left = (g.horizonDays - elapsed).toInt().coerceAtLeast(0)
            daysLeft = left
            val expected = g.target * elapsed / g.horizonDays
            onTrack = if (kind == "lift") current >= g.target * 0.97 else current >= expected * 0.9
            val remaining = g.target - current
            val pace = when {
                left == 0 -> "deadline passed"
                kind == "lift" -> "needs +${String.format(Locale.US, "%.1f", remaining)} e1RM"
                else -> {
                    val perDay = remaining / left
                    val unit = if (kind == "learning") "h" else ""
                    if (perDay <= 0) "" else if (perDay < 1 && unit.isEmpty()) "needs 1 every ${ceil(1 / perDay).toInt()} days"
                    else "needs ${String.format(Locale.US, "%.1f", perDay).removeSuffix(".0")}$unit a day"
                }
            }
            val head = if (onTrack) "On track" else "Behind"
            note = listOf(head + if (!onTrack && pace.isNotBlank()) ": $pace" else "", note).filter { it.isNotBlank() }.joinToString(" · ")
        } else if (note.isBlank()) {
            note = "${(progress * 100).toInt()}% · tap to update"
        }
        return GoalView(g, value, progress, note, onTrack, daysLeft)
    }

    fun skills(learning: List<LearningLog>, day: Long): List<SkillRow> =
        learning.groupBy { skillName(it.topic).lowercase() }.map { (_, l) ->
            val name = skillName(l.first().topic)
            val first = l.minOf { it.day }
            val last = l.maxOf { it.day }
            val isNew = day - first <= 14
            SkillRow(
                name, l.sumOf { it.minutes }, l.size,
                if (isNew) "new" else if (day - last <= 1) "${l.size} sessions · active" else "${l.size} sessions · last ${GrowCalc.shortDate(last)}",
                isNew,
            )
        }.sortedByDescending { it.minutes }.take(6)

    fun activeDays(events: List<DoneEvent>, focus: List<FocusSession>, learning: List<LearningLog>, checks: List<HabitCheck>, journal: List<JournalEntry>): Set<Long> =
        (events.map { it.day } + focus.map { it.day } + learning.map { it.day } + checks.map { it.day } + journal.map { it.day }).toSet()

    fun review(
        monthly: Boolean, day: Long,
        events: List<DoneEvent>, focus: List<FocusSession>, learning: List<LearningLog>, checks: List<HabitCheck>,
        journal: List<JournalEntry>, habits: List<Habit>, sets: List<WorkoutSet>,
    ): Review {
        val active = activeDays(events, focus, learning, checks, journal)
        val date = Days.date(day)
        val from: Long; val prevFrom: Long; val prevTo: Long; val title: String; val range: String
        if (monthly) {
            from = date.withDayOfMonth(1).toEpochDay()
            val pm = date.withDayOfMonth(1).minusMonths(1)
            prevFrom = pm.toEpochDay(); prevTo = from - 1
            title = "${date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} review"
            range = "1–${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} · vs ${pm.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
        } else {
            from = weekStart(day); prevFrom = from - 7; prevTo = from - 1
            val wn = date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            title = "Week $wn review"
            val end = Days.date(from + 6)
            range = "${Days.date(from).dayOfMonth}–${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} · vs week ${wn - 1}"
        }
        val elapsed = (day - from + 1).toInt()
        val prevLen = (prevTo - prevFrom + 1).toInt()
        val actNow = active.count { it in from..day }
        val actPrev = active.count { it in prevFrom..prevTo }
        val cNow = actNow * 100 / elapsed
        val cPrev = actPrev * 100 / prevLen
        val eNow = GrowCalc.focusMin(focus, from, day) + GrowCalc.learnMin(learning, from, day)
        val ePrev = GrowCalc.focusMin(focus, prevFrom, prevTo) + GrowCalc.learnMin(learning, prevFrom, prevTo)
        val oNow = events.count { it.day in from..day }
        val oPrev = events.count { it.day in prevFrom..prevTo }
        val hasPrev = actPrev > 0

        val ws = weekStart(day)
        val weeks = (4 downTo 0).map { k ->
            val a = ws - k * 7; val b = a + 6
            val wn = Days.date(a).get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            Triple("W$wn", (GrowCalc.focusMin(focus, a, b) + GrowCalc.learnMin(learning, a, b)) / 60f, events.count { it.day in a..b }.toFloat())
        }

        val hl = mutableListOf<String>()
        // PRs this period
        val work = sets.filter { !it.warmup && it.kg > 0 }
        val prLifts = work.groupBy { it.exercise }.filter { (_, l) ->
            val inP = l.filter { Days.dayOf(it.ts) in from..day }.maxOfOrNull { it.e1rm }
            val before = l.filter { Days.dayOf(it.ts) < from }.maxOfOrNull { it.e1rm }
            inP != null && before != null && inP > before
        }.keys
        if (prLifts.isNotEmpty()) hl += "New PR${if (prLifts.size > 1) "s" else ""}: ${prLifts.take(3).joinToString(", ")}"
        if (habits.isNotEmpty()) {
            val ids = habits.map { it.id }.toSet()
            val rate = checks.count { it.habitId in ids && it.day in from..day } * 100 / (habits.size * elapsed)
            hl += "Habits $rate% complete"
        }
        if (eNow > 0 && hasPrev && ePrev > 0) {
            val d = (eNow - ePrev) * 100 / ePrev
            if (kotlin.math.abs(d) >= 10) hl += "Effort ${if (d > 0) "up" else "down"} ${kotlin.math.abs(d)}% vs last ${if (monthly) "month" else "week"}"
        }
        val recent = events.filter { it.day > day - 35 }
        if (recent.size >= 5) {
            val byDow = recent.groupBy { Days.date(it.day).dayOfWeek }
            val best = byDow.maxByOrNull { it.value.size }!!
            val pct = best.value.size * 100 / recent.size
            if (pct >= 25) hl += "${pct}% of tasks closed on ${best.key.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}s — protect that day"
        }
        val jn = journal.count { it.day in from..day }
        if (jn > 0) hl += "$jn journal entr${if (jn == 1) "y" else "ies"}"
        val lMin = GrowCalc.learnMin(learning, from, day)
        if (lMin > 0) hl += "${GrowCalc.hours(lMin)} learning"

        fun pts(d: Int) = (if (d >= 0) "+" else "−") + "${kotlin.math.abs(d)} pts"
        return Review(
            title, range,
            ReviewDim("$cNow%", if (hasPrev) pts(cNow - cPrev) else "—", "active $actNow of $elapsed days", if (hasPrev) cNow >= cPrev else null),
            ReviewDim(GrowCalc.hours(eNow), if (hasPrev) GrowCalc.signedHours(eNow - ePrev) else "—", "focus + learning", if (hasPrev) eNow >= ePrev else null),
            ReviewDim("$oNow", if (hasPrev) (if (oNow >= oPrev) "+" else "−") + "${kotlin.math.abs(oNow - oPrev)}" else "—", "tasks done", if (hasPrev) oNow >= oPrev else null),
            weeks, hl.take(4),
        )
    }
}
