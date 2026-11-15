package com.lifeapi.app.vm.body

import android.app.Application
import com.lifeapi.app.data.Meal
import com.lifeapi.app.data.Profile
import com.lifeapi.app.vm.BaseVM
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.util.Locale
import kotlin.math.roundToInt

enum class NutMetric(val label: String, val unit: String) { KCAL("Calories", "kcal"), PROTEIN("Protein", "g"), CARBS("Carbs", "g"), FAT("Fat", "g") }

data class NutAnalytics(
    val metric: NutMetric,
    val period: BodyPeriod,
    val value: String,
    val unit: String,
    val sub: String,
    val delta: String?,
    val deltaGood: Boolean?,
    val bars: List<ChartBar>,
    val goal: Float,
    val goalLabel: String,
    val empty: Boolean,
)

/** Body → Nutrition top chart: calories / macros per day (per week / month for 3M / Y) vs the daily goal. */
@OptIn(ExperimentalCoroutinesApi::class)
class NutritionAnalyticsVM(app: Application) : BaseVM(app) {
    val metric = MutableStateFlow(NutMetric.KCAL)
    val period = MutableStateFlow(BodyPeriod.W)

    fun setMetric(m: NutMetric) { metric.value = m }
    fun setPeriod(p: BodyPeriod) { period.value = p }

    val state = combine(today, metric, period) { t, m, p -> Triple(t, m, p) }.flatMapLatest { (t, m, p) ->
        val (range, buckets) = periodLayout(t, p)
        val prev = periodPrev(range, p)
        combine(bodyDao.mealsRange(minOf(prev.first, range.first), t), profile) { meals, prof -> build(t, m, p, range, buckets, prev, meals, prof) }
    }.state()

    private fun value(m: NutMetric, meal: Meal): Int = when (m) {
        NutMetric.KCAL -> meal.kcal; NutMetric.PROTEIN -> meal.protein; NutMetric.CARBS -> meal.carbs; NutMetric.FAT -> meal.fat
    }

    private fun goalOf(m: NutMetric, p: Profile): Int = when (m) {
        NutMetric.KCAL -> p.kcalGoal; NutMetric.PROTEIN -> p.proteinG; NutMetric.CARBS -> p.carbsG; NutMetric.FAT -> p.fatG
    }

    private fun build(t: Long, m: NutMetric, p: BodyPeriod, range: LongRange, buckets: List<Bucket>, prev: LongRange, meals: List<Meal>, prof: Profile): NutAnalytics {
        val perDay: Map<Long, Int> = meals.groupBy { it.day }.mapValues { e -> e.value.sumOf { value(m, it) } }.filterValues { it > 0 }
        /** Average over days that have food logged (empty days aren't "zero eaten"). */
        fun avg(r: LongRange): Double? = r.mapNotNull { perDay[it]?.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val elapsed = range.first..minOf(range.last, t)
        val bars = buckets.map { b ->
            val fut = b.days.first > t
            ChartBar(b.label, if (fut) null else avg(b.days.first..minOf(b.days.last, t))?.toFloat(), null, t in b.days, fut)
        }
        val goal = goalOf(m, prof)
        val a = avg(elapsed); val pa = avg(prev)
        val logged = elapsed.count { perDay[it] != null }
        // "in range" = within ±10 % for calories; at least the goal for protein; at most the goal for carbs / fat
        val hit = elapsed.count { d ->
            val v = perDay[d] ?: return@count false
            when (m) {
                NutMetric.KCAL -> v >= goal * 0.9 && v <= goal * 1.1
                NutMetric.PROTEIN -> v >= goal
                else -> v <= goal
            }
        }
        val hitWord = when (m) { NutMetric.KCAL -> "in range"; NutMetric.PROTEIN -> "goal hit"; else -> "under goal" }
        fun n(v: Double) = String.format(Locale.US, "%,d", v.roundToInt())
        return NutAnalytics(
            m, p,
            value = a?.let { n(it) } ?: "—",
            unit = if (a != null) "${m.unit} avg" else "",
            sub = "goal ${String.format(Locale.US, "%,d", goal)} ${m.unit}" + if (logged > 0) " · $hitWord $hit of $logged days" else " · nothing logged",
            delta = if (a != null && pa != null) signed(a - pa, "%,.0f") + " ${m.unit} vs ${p.prevWord}" else null,
            deltaGood = if (a != null && pa != null) when (m) {
                NutMetric.PROTEIN -> a >= pa
                // closer to the goal is better
                else -> kotlin.math.abs(a - goal) <= kotlin.math.abs(pa - goal)
            } else null,
            bars = bars, goal = goal.toFloat(), goalLabel = String.format(Locale.US, "%,d", goal),
            empty = meals.isEmpty(),
        )
    }
}
