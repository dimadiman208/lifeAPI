package com.lifeapi.app.vm.body

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Intake
import com.lifeapi.app.data.Profile
import com.lifeapi.app.data.SleepLog
import com.lifeapi.app.data.SuppRule
import com.lifeapi.app.data.Supplement
import com.lifeapi.app.data.WeightLog
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.data.supps.SuppSchedule
import com.lifeapi.app.vm.BaseVM
import com.lifeapi.app.vm.weekStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class BodyMetric(val label: String) { SUPPS("Supplements"), WATER("Water"), SLEEP("Sleep"), WEIGHT("Weight") }
enum class BodyPeriod(val label: String, val prevWord: String) { W("W", "last week"), M("M", "last month"), Q("3M", "previous 3M"), Y("Y", "previous year") }

/** One bar. [plan] (supplements) draws as the track behind [value]. null value = no data. */
data class ChartBar(val label: String, val value: Float?, val plan: Float? = null, val current: Boolean = false, val future: Boolean = false)

/** Weight line chart: x = days from range start. */
data class WeightSeries(val span: Int, val dots: List<Pair<Int, Float>>, val avg: List<Pair<Int, Float>>, val axis: List<Pair<Int, String>>)

data class Analytics(
    val metric: BodyMetric,
    val period: BodyPeriod,
    /** Big number + unit, e.g. "86" "%", "2.1" "L avg". */
    val value: String,
    val unit: String,
    /** Secondary line: goal / counts / hint. */
    val sub: String,
    /** "+4 pts vs last week" — null when there's nothing to compare. */
    val delta: String?,
    /** true green, false amber, null neutral. */
    val deltaGood: Boolean?,
    val bars: List<ChartBar>,
    val weight: WeightSeries?,
    /** Goal line in the chart's units (ml, minutes, kg); null = none. */
    val goal: Float?,
    val goalLabel: String?,
    val empty: Boolean,
)

internal fun monShort(m: java.time.LocalDate) = m.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
internal fun signed(v: Double, fmt: String) = (if (v >= 0) "+" else "−") + String.format(Locale.US, fmt, abs(v))
fun fmtHm(min: Int): String = "${min / 60}h ${String.format(Locale.US, "%02d", min % 60)}m"

/** Buckets for a period: days or groups of days, oldest first. */
internal data class Bucket(val label: String, val days: LongRange)

/** Current range [from, to] (to may be in the future) and the bucket layout. */
internal fun periodLayout(t: Long, p: BodyPeriod): Pair<LongRange, List<Bucket>> = when (p) {
    BodyPeriod.W -> {
        val ws = weekStart(t)
        (ws..ws + 6) to (0 until 7).map { val d = ws + it; Bucket(Days.date(d).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), d..d) }
    }
    BodyPeriod.M -> {
        val m = Days.date(t).withDayOfMonth(1)
        val from = m.toEpochDay(); val to = m.withDayOfMonth(m.lengthOfMonth()).toEpochDay()
        (from..to) to (from..to).map { d -> val n = Days.date(d).dayOfMonth; Bucket(if (n == 1 || n % 7 == 1) "$n" else "", d..d) }
    }
    BodyPeriod.Q -> {
        val ws = weekStart(t) - 12 * 7
        (ws..ws + 13 * 7 - 1) to (0 until 13).map { i ->
            val a = ws + i * 7; val ad = Days.date(a)
            Bucket(if (i % 4 == 0) "${ad.dayOfMonth} ${monShort(ad)}" else "", a..a + 6)
        }
    }
    BodyPeriod.Y -> {
        val m0 = Days.date(t).withDayOfMonth(1).minusMonths(11)
        val from = m0.toEpochDay()
        val to = Days.date(t).withDayOfMonth(Days.date(t).lengthOfMonth()).toEpochDay()
        (from..to) to (0 until 12).map { i ->
            val m = m0.plusMonths(i.toLong())
            Bucket(monShort(m).take(1), m.toEpochDay()..m.withDayOfMonth(m.lengthOfMonth()).toEpochDay())
        }
    }
}

/** The same-length window right before [r] — for "vs last period". */
internal fun periodPrev(r: LongRange, p: BodyPeriod): LongRange = when (p) {
    BodyPeriod.M -> {
        val m = Days.date(r.first).minusMonths(1)
        m.toEpochDay()..m.withDayOfMonth(m.lengthOfMonth()).toEpochDay()
    }
    BodyPeriod.Y -> Days.date(r.first).minusYears(1).toEpochDay()..r.first - 1
    else -> (r.first - (r.last - r.first + 1))..(r.first - 1)
}

@OptIn(ExperimentalCoroutinesApi::class)
class BodyAnalyticsVM(app: Application) : BaseVM(app) {
    val metric = MutableStateFlow(BodyMetric.SUPPS)
    val period = MutableStateFlow(BodyPeriod.W)

    fun setMetric(m: BodyMetric) { metric.value = m }
    fun setPeriod(p: BodyPeriod) { period.value = p }

    private data class Src(
        val supps: List<Supplement>, val rules: List<SuppRule>, val intakes: List<Intake>,
        val water: Map<Long, Long>, val sleep: List<SleepLog>, val weights: List<WeightLog>, val p: Profile,
    )

    val state = combine(today, metric, period) { t, m, p -> Triple(t, m, p) }.flatMapLatest { (t, m, p) ->
        val (range, buckets) = periodLayout(t, p)
        val prev = periodPrev(range, p)
        val from = minOf(prev.first, t - 35)
        source(m, from, t).map { s -> build(t, m, p, range, buckets, prev, s) }
    }.state()

    /** Only query what the metric needs. */
    private fun source(m: BodyMetric, from: Long, t: Long): Flow<Src> = when (m) {
        BodyMetric.SUPPS -> combine(dao.supplements(), bodyDao.rules(), dao.intakesRange(from, t), profile) { s, r, i, p ->
            Src(s, r, i, emptyMap(), emptyList(), emptyList(), p)
        }
        BodyMetric.WATER -> combine(dao.waterByDay(from, t), profile) { w, p ->
            Src(emptyList(), emptyList(), emptyList(), w.associate { it.day to it.total }, emptyList(), emptyList(), p)
        }
        BodyMetric.SLEEP -> combine(bodyDao.sleepRange(from, t), profile) { s, p ->
            Src(emptyList(), emptyList(), emptyList(), emptyMap(), s, emptyList(), p)
        }
        BodyMetric.WEIGHT -> combine(bodyDao.weights(from - 7), profile) { w, p ->
            Src(emptyList(), emptyList(), emptyList(), emptyMap(), emptyList(), w, p)
        }
    }

    private fun build(t: Long, m: BodyMetric, p: BodyPeriod, range: LongRange, buckets: List<Bucket>, prev: LongRange, s: Src): Analytics {
        val elapsed = range.first..minOf(range.last, t)
        return when (m) {
            BodyMetric.SUPPS -> supps(t, p, buckets, elapsed, prev, s)
            BodyMetric.WATER -> water(t, p, buckets, elapsed, prev, s)
            BodyMetric.SLEEP -> sleep(t, p, buckets, elapsed, prev, s)
            BodyMetric.WEIGHT -> weight(t, p, range, prev, s)
        }
    }

    private fun supps(t: Long, p: BodyPeriod, buckets: List<Bucket>, elapsed: LongRange, prev: LongRange, s: Src): Analytics {
        val by = s.intakes.groupBy { it.day }
        val first = s.intakes.minOfOrNull { it.day } ?: t
        val cache = HashMap<Long, Pair<Int, Int>>()
        /** (taken, scheduled) — days before tracking started count as nothing planned. */
        fun day(d: Long): Pair<Int, Int> = cache.getOrPut(d) {
            if (d < first && d < t) 0 to 0
            else SuppSchedule.progress(SuppSchedule.resolve(s.supps, s.rules, if (d > t) emptyList() else by[d].orEmpty(), d)).let { it.taken to it.scheduled }
        }
        fun sum(r: LongRange) = r.fold(0 to 0) { a, d -> val x = day(d); (a.first + x.first) to (a.second + x.second) }
        val bars = buckets.map { b ->
            val past = b.days.first..minOf(b.days.last, t)
            val fut = b.days.first > t
            val (tk, sc) = if (fut) 0 to b.days.sumOf { day(it).second } else sum(past)
            ChartBar(b.label, if (fut) 0f else tk.toFloat(), sc.toFloat(), t in b.days, fut)
        }
        val (tk, sc) = sum(elapsed)
        val (ptk, psc) = sum(prev)
        val pct = if (sc == 0) null else tk * 100 / sc
        val ppct = if (psc == 0) null else ptk * 100 / psc
        return Analytics(
            BodyMetric.SUPPS, p,
            value = pct?.toString() ?: "—", unit = if (pct != null) "%" else "",
            sub = if (sc == 0) "Nothing planned in this period" else "$tk of $sc doses taken",
            delta = if (pct != null && ppct != null) "${signed((pct - ppct).toDouble(), "%.0f")} pts vs ${p.prevWord}" else null,
            deltaGood = if (pct != null && ppct != null) pct >= ppct else null,
            bars = bars, weight = null, goal = null, goalLabel = null,
            empty = s.supps.isEmpty(),
        )
    }

    private fun water(t: Long, p: BodyPeriod, buckets: List<Bucket>, elapsed: LongRange, prev: LongRange, s: Src): Analytics {
        fun avg(r: LongRange): Double? = r.mapNotNull { s.water[it]?.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val bars = buckets.map { b ->
            val fut = b.days.first > t
            ChartBar(b.label, if (fut) null else avg(b.days.first..minOf(b.days.last, t))?.toFloat(), null, t in b.days, fut)
        }
        val a = avg(elapsed); val pa = avg(prev)
        val goal = s.p.waterGoalMl
        val hit = elapsed.count { (s.water[it] ?: 0) >= goal }
        val logged = elapsed.count { s.water[it] != null }
        return Analytics(
            BodyMetric.WATER, p,
            value = a?.let { String.format(Locale.US, "%.1f", it / 1000) } ?: "—", unit = if (a != null) "L avg" else "",
            sub = String.format(Locale.US, "goal %.1f L", goal / 1000f) + if (logged > 0) " · hit $hit of ${elapsed.count()} days" else " · nothing logged",
            delta = if (a != null && pa != null) signed((a - pa) / 1000, "%.1f") + " L vs ${p.prevWord}" else null,
            deltaGood = if (a != null && pa != null) a >= pa else null,
            bars = bars, weight = null, goal = goal.toFloat(), goalLabel = String.format(Locale.US, "%.1f L", goal / 1000f),
            empty = s.water.isEmpty(),
        )
    }

    private fun sleep(t: Long, p: BodyPeriod, buckets: List<Bucket>, elapsed: LongRange, prev: LongRange, s: Src): Analytics {
        val byDay = s.sleep.associateBy { it.day }
        fun avg(r: LongRange): Double? = r.mapNotNull { byDay[it]?.minutes?.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val bars = buckets.map { b ->
            val fut = b.days.first > t
            ChartBar(b.label, if (fut) null else avg(b.days.first..minOf(b.days.last, t))?.toFloat(), null, t in b.days, fut)
        }
        val a = avg(elapsed); val pa = avg(prev)
        val nights = elapsed.mapNotNull { byDay[it] }
        // Average lights-out, measured past noon so 23:30 and 00:30 average sensibly.
        val bed = nights.takeIf { it.isNotEmpty() }?.map { val x = Days.timeOf(it.startTs); var mm = x.hour * 60 + x.minute; if (mm < 12 * 60) mm += 24 * 60; mm }?.average()?.roundToInt()
        val bedTxt = bed?.let { val mm = it % (24 * 60); String.format(Locale.US, "%02d:%02d", mm / 60, mm % 60) }
        return Analytics(
            BodyMetric.SLEEP, p,
            value = a?.let { fmtHm(it.roundToInt()) } ?: "—", unit = if (a != null) "avg" else "",
            sub = "goal ${fmtHm(s.p.sleepGoalMin)}" + (bedTxt?.let { " · lights out ~$it" } ?: " · no nights logged"),
            delta = if (a != null && pa != null) signed(a - pa, "%.0f") + " m vs ${p.prevWord}" else null,
            deltaGood = if (a != null && pa != null) a >= pa else null,
            bars = bars, weight = null, goal = s.p.sleepGoalMin.toFloat(), goalLabel = fmtHm(s.p.sleepGoalMin).replace(" 00m", "h").replace("h ", "h"),
            empty = s.sleep.isEmpty(),
        )
    }

    private fun weight(t: Long, p: BodyPeriod, range: LongRange, prev: LongRange, s: Src): Analytics {
        val w = s.weights
        val inRange = w.filter { it.day in range.first..t }
        val span = (range.last - range.first).toInt().coerceAtLeast(1)
        fun avg7(d: Long) = WeightMath.avg7(w, d)
        val dots = inRange.map { (it.day - range.first).toInt() to it.kg.toFloat() }
        val avg = inRange.mapNotNull { pt -> avg7(pt.day)?.let { (pt.day - range.first).toInt() to it.toFloat() } }
        val axis = when (p) {
            BodyPeriod.W -> (0..6).map { it to Days.date(range.first + it).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH) }
            BodyPeriod.M -> listOf(0, 7, 14, 21, 28).filter { it <= span }.map { it to "${it + 1}" }
            BodyPeriod.Q -> (0..12 step 4).map { it * 7 to Days.date(range.first + it * 7).let { d -> "${d.dayOfMonth} ${monShort(d)}" } }
            BodyPeriod.Y -> (0 until 12 step 2).map { i -> Days.date(range.first).plusMonths(i.toLong()).let { (it.toEpochDay() - range.first).toInt() to monShort(it) } }
        }
        val now = avg7(t) ?: w.lastOrNull()?.kg
        val rate = WeightMath.rate(w, t)
        // Change across the period: 7-day avg now vs at the period start (or the first weigh-in in it).
        val startAvg = avg7(range.first - 1) ?: inRange.firstOrNull()?.let { avg7(it.day) ?: it.kg }
        val change = if (now != null && startAvg != null && inRange.size >= 2) now - startAvg else null
        val target = s.p.weightRateKg
        val goal = s.p.weightGoalKg
        return Analytics(
            BodyMetric.WEIGHT, p,
            value = now?.let { String.format(Locale.US, "%.1f", it) } ?: "—", unit = if (now != null) "kg" else "",
            sub = listOfNotNull(
                rate?.let { signed(it, "%.2f") + " kg/wk" } ?: if (now != null) "rate after ~1 week" else "no weigh-ins yet",
                goal?.let { "goal ${fmtKg(it)}" },
            ).joinToString(" · "),
            delta = change?.let { signed(it, "%.1f") + " kg this " + when (p) { BodyPeriod.W -> "week"; BodyPeriod.M -> "month"; BodyPeriod.Q -> "3M"; BodyPeriod.Y -> "year" } },
            deltaGood = change?.let { if (target >= 0) it >= 0 else it <= 0 },
            bars = emptyList(), weight = WeightSeries(span, dots, avg, axis),
            goal = goal?.toFloat(), goalLabel = goal?.let { "${fmtKg(it)} kg" },
            empty = w.isEmpty(),
        )
    }
}
