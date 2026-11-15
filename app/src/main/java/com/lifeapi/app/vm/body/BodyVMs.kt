package com.lifeapi.app.vm.body

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Intake
import com.lifeapi.app.data.Meal
import com.lifeapi.app.data.Profile
import com.lifeapi.app.data.SleepLog
import com.lifeapi.app.data.Supplement
import com.lifeapi.app.data.WaterLog
import com.lifeapi.app.data.WeightLog
import com.lifeapi.app.data.hhmm
import com.lifeapi.app.vm.BaseVM
import com.lifeapi.app.data.SuppRule
import com.lifeapi.app.data.supps.DoseSlot
import com.lifeapi.app.data.supps.SuppSchedule
import com.lifeapi.app.data.supps.hhmmOf
import com.lifeapi.app.vm.supps.SuppActionsVM
import com.lifeapi.app.vm.parseQuick
import com.lifeapi.app.vm.weekStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private fun dow(d: Long) = Days.date(d).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
private fun mon(d: Long) = Days.date(d).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
fun dayLabel(d: Long) = "${dow(d)} ${Days.date(d).dayOfMonth} ${mon(d)}"

// =================================================================== weight

object WeightMath {
    fun avg7(w: List<WeightLog>, day: Long): Double? =
        w.filter { it.day in (day - 6)..day }.map { it.kg }.takeIf { it.isNotEmpty() }?.average()

    /** kg per week from a least-squares fit over the last 28 days; null without enough data. */
    fun rate(w: List<WeightLog>, day: Long): Double? {
        val pts = w.filter { it.day in (day - 27)..day }
        if (pts.size < 3 || pts.last().day - pts.first().day < 6) return null
        val xs = pts.map { it.day.toDouble() }; val ys = pts.map { it.kg }
        val mx = xs.average(); val my = ys.average()
        val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return null
        return num / den * 7
    }
}

// =================================================================== supplement detail

/** kind: 0 pad, 1 all taken, 2 skipped, 3 missed, 4 today (open), 5 neutral (future / not scheduled / before tracking), 6 partial */
data class SuppCal(val n: Int, val kind: Int)

/** tone: 0 taken, 1 late, 2 skipped, 3 missed, 4 partial */
data class IntakeRow(val day: String, val status: String, val tone: Int, val time: String)

data class SuppDetailState(
    val s: Supplement,
    val rules: List<SuppRule>,
    val schedule: String,
    val perWeek: Int,
    val streak: Int,
    val best: Int,
    val pct30: Int?,
    val monthLabel: String,
    val canNext: Boolean,
    val cal: List<SuppCal>,
    val log: List<IntakeRow>,
    val todaySlots: List<DoseSlot>,
    val today: Long,
    val now: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SupplementVM(app: Application) : SuppActionsVM(app) {
    private val id = MutableStateFlow<Long?>(null)
    private val monthOffset = MutableStateFlow(0)

    fun bind(sid: Long) { if (id.value != sid) { id.value = sid; monthOffset.value = 0 } }
    fun month(delta: Int) { monthOffset.value = (monthOffset.value + delta).coerceAtMost(0) }

    val state = id.filterNotNull().flatMapLatest { sid ->
        combine(bodyDao.supplement(sid).filterNotNull(), bodyDao.intakesFor(sid), bodyDao.rulesFor(sid), today, monthOffset) { s, intakes, rules, day, off ->
            build(s, intakes, rules, day, off)
        }
    }.state()

    private fun build(s: Supplement, intakes: List<Intake>, rules: List<SuppRule>, day: Long, off: Int): SuppDetailState {
        val by = intakes.groupBy { it.day }
        val first = intakes.minOfOrNull { it.day } ?: day
        fun slots(d: Long) = SuppSchedule.resolve(listOf(s), rules, by[d].orEmpty(), d)
        /** 0 unscheduled, 1 all taken (or closed with ≥1 taken), 2 all skipped, 3 none / missed, 6 partial */
        fun status(d: Long): Int {
            val p = SuppSchedule.progress(slots(d))
            return when {
                p.scheduled == 0 -> if (by[d].orEmpty().any { !it.skipped }) 1 else 0
                p.closed && p.taken > 0 -> 1
                p.closed -> 2
                p.taken > 0 -> 6
                else -> 3
            }
        }
        var streak = 0
        var d = if (status(day) in listOf(1, 2)) day else day - 1
        while (d >= first) {
            when (status(d)) { 1 -> streak++; 3, 6 -> break }
            d--
        }
        var best = 0; var run = 0
        for (x in first..day) {
            when (status(x)) { 1 -> { run++; best = maxOf(best, run) }; 3, 6 -> if (x != day) run = 0 }
        }
        var req = 0; var got = 0
        for (x in maxOf(first, day - 29)..day) {
            val p = SuppSchedule.progress(slots(x))
            if (x == day) { got += p.taken; req += p.taken; continue }
            req += p.scheduled - p.skipped; got += p.taken.coerceAtMost(p.scheduled)
        }
        val pct = if (req <= 0) null else (got * 100 / req).coerceIn(0, 100)

        val month = Days.date(day).withDayOfMonth(1).plusMonths(off.toLong())
        val ms = month.toEpochDay(); val me = month.withDayOfMonth(month.lengthOfMonth()).toEpochDay()
        val cal = buildList {
            repeat(month.dayOfWeek.value - 1) { add(SuppCal(0, 0)) }
            for (x in ms..me) {
                val n = Days.date(x).dayOfMonth
                val st = status(x)
                add(SuppCal(n, when {
                    x == day -> if (st == 1) 1 else 4
                    x > day || x < first -> 5
                    st == 0 -> 5
                    else -> st
                }))
            }
        }
        val log = buildList {
            var x = day
            while (x >= first && size < 20 && x > day - 120) {
                val sl = slots(x)
                val p = SuppSchedule.progress(sl)
                val times = sl.filter { it.taken }.joinToString(", ") { hhmm(Days.timeOf(it.intake!!.ts)) }.ifEmpty { "—" }
                val late = sl.any { it.taken && java.time.Duration.between(it.dose.localTime, Days.timeOf(it.intake!!.ts)).toMinutes() > 120 }
                when {
                    p.scheduled == 0 -> {}
                    p.closed && p.taken == p.scheduled -> add(IntakeRow(dayLabel(x), if (late) "Taken late ${p.taken}/${p.scheduled}" else "Taken ${p.taken}/${p.scheduled}", if (late) 1 else 0, times))
                    p.closed && p.taken == 0 -> add(IntakeRow(dayLabel(x), "Skipped", 2, "—"))
                    p.taken > 0 || p.skipped > 0 -> if (x != day || p.closed) add(IntakeRow(dayLabel(x), "${p.taken}/${p.scheduled}" + if (p.skipped > 0) " · ${p.skipped} skipped" else "", 4, times))
                    x != day -> add(IntakeRow(dayLabel(x), "Missed", 3, "—"))
                }
                x--
            }
        }
        return SuppDetailState(
            s = s, rules = rules, schedule = SuppSchedule.summary(s, rules), perWeek = SuppSchedule.perWeek(s, rules),
            streak = streak, best = best, pct30 = pct,
            monthLabel = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + if (month.year != Days.date(day).year) " ${month.year}" else "",
            canNext = off < 0, cal = cal, log = log,
            todaySlots = slots(day), today = day, now = hhmmOf(LocalTime.now()),
        )
    }
}

// =================================================================== meal history

data class HistDay(val day: Long, val dow: String, val d: Int, val kcal: Int, val macro: String, val src: String, val hit: Boolean)

data class MealHistoryState(
    val weekNo: Int,
    val rangeLabel: String,
    val daysElapsed: Int,
    /** 7 entries; null = future */
    val kcalHits: List<Boolean?>,
    val proteinHits: List<Boolean?>,
    val kcalHitCount: Int,
    val proteinHitCount: Int,
    val loggedDays: Int,
    val avgLine: String?,
    val days: List<HistDay>,
    val profile: Profile,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MealHistoryVM(app: Application) : BaseVM(app) {
    val state = today.flatMapLatest { day ->
        combine(bodyDao.mealsRange(day - 27, day), profile) { meals, p -> build(meals, p, day) }
    }.state()

    private fun build(meals: List<Meal>, p: Profile, day: Long): MealHistoryState {
        val byDay = meals.groupBy { it.day }
        fun inRange(k: Int) = k >= p.kcalGoal * 0.9 && k <= p.kcalGoal * 1.1
        val ws = weekStart(day)
        val kH = (0 until 7).map { i ->
            val d = ws + i
            if (d > day) null else byDay[d]?.sumOf { it.kcal }?.let(::inRange) ?: false
        }
        val pH = (0 until 7).map { i ->
            val d = ws + i
            if (d > day) null else (byDay[d]?.sumOf { it.protein } ?: 0) >= p.proteinG
        }
        val week = byDay.filterKeys { it in ws..day }.values
        val n = week.size
        val avg = if (n == 0) null else String.format(
            Locale.US, "Avg %,d kcal · %d g P · %d g C · %d g F",
            week.sumOf { l -> l.sumOf { it.kcal } } / n, week.sumOf { l -> l.sumOf { it.protein } } / n,
            week.sumOf { l -> l.sumOf { it.carbs } } / n, week.sumOf { l -> l.sumOf { it.fat } } / n,
        )
        val days = byDay.keys.sortedDescending().map { d ->
            val l = byDay[d]!!
            val k = l.sumOf { it.kcal }
            val srcs = l.map { com.lifeapi.app.data.nutrition.MealSource.label(it.source).removeSuffix(" food").removeSuffix(" diary") }.distinct()
            HistDay(
                d, dow(d), Days.date(d).dayOfMonth, k,
                "P ${l.sumOf { it.protein }} · C ${l.sumOf { it.carbs }} · F ${l.sumOf { it.fat }}",
                if (srcs.size == 1) srcs.first() else "Mixed", inRange(k),
            )
        }
        val elapsed = (day - ws + 1).toInt()
        return MealHistoryState(
            weekNo = Days.date(day).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
            rangeLabel = if (elapsed == 1) "Mon" else "Mon–${dow(day)}",
            daysElapsed = elapsed, kcalHits = kH, proteinHits = pH,
            kcalHitCount = kH.count { it == true }, proteinHitCount = pH.count { it == true },
            loggedDays = n, avgLine = avg, days = days, profile = p,
        )
    }
}
