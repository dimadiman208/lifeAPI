package com.lifeapi.app.vm.train

import android.app.Application
import com.lifeapi.app.data.WorkoutSession
import com.lifeapi.app.data.WorkoutSet
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.vm.BaseVM
import com.lifeapi.app.vm.body.BodyPeriod
import com.lifeapi.app.vm.body.Bucket
import com.lifeapi.app.vm.body.ChartBar
import com.lifeapi.app.vm.body.periodLayout
import com.lifeapi.app.vm.body.periodPrev
import com.lifeapi.app.vm.body.signed
import com.lifeapi.app.vm.fmtTonnes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.util.Locale

enum class TrainMetric(val label: String) { VOLUME("Volume"), SETS("Sets"), SESSIONS("Sessions"), E1RM("e1RM") }

data class TrainAnalytics(
    val metric: TrainMetric,
    val period: BodyPeriod,
    val value: String,
    val unit: String,
    val sub: String,
    val delta: String?,
    val deltaGood: Boolean?,
    val bars: List<ChartBar>,
    val empty: Boolean,
)

/**
 * Train's top chart (same scaffold as Body): volume / sets / sessions per bucket, or best e1RM of your most-trained
 * lift. Counts saved workouts plus the one in progress. Totals per bucket (days for W/M, weeks for 3M, months for Y).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainAnalyticsVM(app: Application) : BaseVM(app) {
    val metric = MutableStateFlow(TrainMetric.VOLUME)
    val period = MutableStateFlow(BodyPeriod.W)

    fun setMetric(m: TrainMetric) { metric.value = m }
    fun setPeriod(p: BodyPeriod) { period.value = p }

    val state = combine(today, metric, period) { t, m, p -> Triple(t, m, p) }.flatMapLatest { (t, m, p) ->
        val (range, buckets) = periodLayout(t, p)
        val prev = periodPrev(range, p)
        combine(dao.finishedSessions(), dao.activeSession(), dao.allSets()) { fin, act, sets -> build(t, m, p, range, buckets, prev, fin + listOfNotNull(act), sets) }
    }.state()

    private fun build(
        t: Long, m: TrainMetric, p: BodyPeriod, range: LongRange, buckets: List<Bucket>, prev: LongRange,
        sessions: List<WorkoutSession>, all: List<WorkoutSet>,
    ): TrainAnalytics {
        val dayOf = sessions.associate { it.id to it.day }
        val work = all.filter { !it.warmup && it.sessionId in dayOf }
        val setsByDay = work.groupBy { dayOf.getValue(it.sessionId) }
        val sessByDay = sessions.filter { s -> work.any { it.sessionId == s.id } }.groupBy { it.day }
        val lift = work.groupBy { it.exercise }.maxByOrNull { it.value.size }?.key
        val elapsed = range.first..minOf(range.last, t)

        fun vol(r: LongRange) = r.sumOf { d -> setsByDay[d].orEmpty().sumOf { it.kg * it.reps } }
        fun cnt(r: LongRange) = r.sumOf { d -> setsByDay[d].orEmpty().size }
        fun ses(r: LongRange) = r.sumOf { d -> sessByDay[d].orEmpty().size }
        fun best(r: LongRange): WorkoutSet? = r.flatMap { d -> setsByDay[d].orEmpty().filter { it.exercise == lift && it.kg > 0 } }.maxByOrNull { it.e1rm }

        fun barsOf(f: (LongRange) -> Float?) = buckets.map { b ->
            val fut = b.days.first > t
            ChartBar(b.label, if (fut) null else f(b.days.first..minOf(b.days.last, t)), null, t in b.days, fut)
        }
        val word = p.prevWord
        fun pct(a: Double, b: Double): String? = if (b > 0) signed((a - b) / b * 100, "%.0f") + "% vs $word" else null

        return when (m) {
            TrainMetric.VOLUME -> {
                val a = vol(elapsed); val b = vol(prev)
                TrainAnalytics(m, p, fmtTonnes(a).substringBefore(' '), fmtTonnes(a).substringAfter(' '),
                    "${ses(elapsed)} session${if (ses(elapsed) == 1) "" else "s"} · ${cnt(elapsed)} sets",
                    pct(a, b), if (b > 0) a >= b else null,
                    barsOf { r -> vol(r).toFloat().takeIf { it > 0 } }, work.isEmpty())
            }
            TrainMetric.SETS -> {
                val a = cnt(elapsed); val b = cnt(prev)
                TrainAnalytics(m, p, a.toString(), "sets", "${ses(elapsed)} session${if (ses(elapsed) == 1) "" else "s"}",
                    if (b > 0) signed((a - b).toDouble(), "%.0f") + " vs $word" else null, if (b > 0) a >= b else null,
                    barsOf { r -> cnt(r).toFloat().takeIf { it > 0 } }, work.isEmpty())
            }
            TrainMetric.SESSIONS -> {
                val a = ses(elapsed); val b = ses(prev)
                val days = elapsed.count { (sessByDay[it]?.size ?: 0) > 0 }
                TrainAnalytics(m, p, a.toString(), if (a == 1) "session" else "sessions", "trained $days of ${elapsed.count()} days",
                    if (b > 0) signed((a - b).toDouble(), "%.0f") + " vs $word" else null, if (b > 0) a >= b else null,
                    barsOf { r -> ses(r).toFloat().takeIf { it > 0 } }, work.isEmpty())
            }
            TrainMetric.E1RM -> {
                val a = best(elapsed); val b = best(prev)
                TrainAnalytics(m, p, a?.let { String.format(Locale.US, "%.1f", it.e1rm) } ?: "—", if (a != null) "kg" else "",
                    lift?.let { l -> l + (a?.let { " · best ${fmtKg(it.kg)}×${it.reps}" } ?: " · not trained this period") } ?: "No lifts yet",
                    if (a != null && b != null) signed(a.e1rm - b.e1rm, "%.1f") + " kg vs $word" else null,
                    if (a != null && b != null) a.e1rm >= b.e1rm else null,
                    barsOf { r -> best(r)?.e1rm?.toFloat() }, lift == null)
            }
        }
    }
}
