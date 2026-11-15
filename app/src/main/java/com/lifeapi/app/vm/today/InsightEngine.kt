package com.lifeapi.app.vm.today

import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Hue
import com.lifeapi.app.data.WorkoutSet
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.data.hhmm
import com.lifeapi.app.data.mealSlotFor
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * LifeAPI Intelligence — a small, deterministic, on-device rule engine.
 * Every claim is computed from the local database; nothing is sent anywhere.
 */

// ------------------------------------------------------------ data categories (privacy)

enum class AiCat(val key: String, val label: String, val what: String) {
    SLEEP("sleep", "Sleep & recovery", "times, durations, scores"),
    TRAIN("train", "Workouts", "exercises, sets, PRs"),
    FOOD("food", "Nutrition", "daily totals and usual meals"),
    HABITS("habits", "Habits & projects", "completion, tasks, next actions"),
    MONEY("money", "Spending by category", "category totals and budgets"),
    TX("tx", "Individual transactions", "merchants and exact amounts"),
    HEALTH("health", "Water & supplements", "intake and adherence");

    companion object { fun of(k: String) = entries.firstOrNull { it.key == k } }
}

val DEFAULT_AI = setOf(AiCat.SLEEP, AiCat.TRAIN, AiCat.FOOD, AiCat.HABITS, AiCat.MONEY, AiCat.HEALTH)

/** aiCategories: "" = defaults, "off" = master switch off, else comma list (may include "off"). */
fun com.lifeapi.app.data.Profile.aiOn(): Boolean = !aiCategories.split(',').contains("off")
fun com.lifeapi.app.data.Profile.aiCats(): Set<AiCat> =
    if (aiCategories.isBlank()) DEFAULT_AI else aiCategories.split(',').mapNotNull { AiCat.of(it.trim()) }.toSet()
fun aiString(on: Boolean, cats: Set<AiCat>): String =
    (listOfNotNull(if (!on) "off" else null) + AiCat.entries.filter { it in cats }.map { it.key }).joinToString(",").ifEmpty { "none" }

// ------------------------------------------------------------ outputs

sealed interface NextAction {
    data class Meal(val items: String) : NextAction
    data class Tab(val route: String, val seg: Int? = null) : NextAction
    data class Route(val route: String) : NextAction
}

data class DoNext(val key: String, val hue: Int, val title: String, val sub: String, val cta: String, val action: NextAction)

sealed interface PatternChart {
    /** x,y points; [hi] = highlighted indices; [xThreshold] dashed vertical line. */
    data class Scatter(
        val pts: List<Pair<Float, Float>>, val hi: Set<Int>, val xThreshold: Float?, val thresholdLabel: String?,
        val xMin: String, val xMax: String, val yLabel: String,
    ) : PatternChart
    data class Bars(val labels: List<String>, val values: List<Float>, val hi: Int?, val unit: String) : PatternChart
}

data class Pattern(
    val key: String,
    val module: String,
    val hue: Int,
    val claim: String,
    val chartTitle: String,
    val chart: PatternChart,
    val rows: List<Pair<String, String>>,
    val r: Double?,
    val n: Int,
    val uses: String,
)

data class Summary(val title: String, val text: String, val uses: String)

data class Report(
    val tag: String,
    val title: String,
    val range: String,
    val consistency: Int,
    val consistencyDelta: Int?,
    val activeDays: Int,
    val totalDays: Int,
    val avgScore: Int?,
    val scoreDelta: Int?,
    val workouts: Int,
    val volumeKg: Double,
    val spentCents: Long,
    val spentDeltaPct: Int?,
    val sleepAvgMin: Int?,
    val learnMin: Long,
    val tasksDone: Int,
    val highlights: List<String>,
    val text: String,
    val scoreSeries: List<Int?>,
    val seriesLabels: List<String>,
)

fun strength(r: Double): String = when {
    abs(r) >= 0.6 -> "strong"
    abs(r) >= 0.3 -> "moderate"
    abs(r) >= 0.15 -> "weak"
    else -> "no clear"
}

fun pearson(xs: List<Double>, ys: List<Double>): Double? {
    val n = xs.size
    if (n < 3) return null
    val mx = xs.average(); val my = ys.average()
    var sxy = 0.0; var sxx = 0.0; var syy = 0.0
    for (i in 0 until n) { val dx = xs[i] - mx; val dy = ys[i] - my; sxy += dx * dy; sxx += dx * dx; syy += dy * dy }
    if (sxx == 0.0 || syy == 0.0) return null
    return sxy / sqrt(sxx * syy)
}

private fun dow(day: Long) = Days.date(day).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
private fun money0(c: Long) = fmtCents(c).removeSuffix(".00")
private fun hm(min: Int) = "${min / 60} h ${String.format(Locale.US, "%02d", min % 60)} m"

/** Minutes after 18:00 for a bedtime, so 23:30 → 330 and 00:30 → 390. */
private fun bedMinutes(t: LocalTime): Int { val m = t.hour * 60 + t.minute; return if (m < 12 * 60) m + 6 * 60 else m - 18 * 60 }
private fun bedLabel(m: Int): String { val x = (m + 18 * 60) % (24 * 60); return String.format(Locale.US, "%02d:%02d", x / 60, x % 60) }

object InsightEngine {

    // ======================================================= daily summary
    fun summary(s: Snapshot, now: LocalTime = LocalTime.now()): Summary {
        val day = s.day
        val sc = s.score(day)
        val past = ((day - 30) until day).map { s.score(it) }.filter { it.hasData }
        val avg = if (past.size >= 3) past.map { it.score }.average().toInt() else null
        val title = "DAILY SUMMARY · ${dayLabel(day).uppercase()}"
        val cats = s.p.aiCats()
        if (!sc.hasData) {
            return Summary(title, if (avg != null) "Nothing logged yet today. Your 30-day average is $avg — a glass of water and your morning stack are the fastest start." else "Nothing logged yet. Log water, a meal or your stack and the summary fills in.", "Day Score · updated ${hhmm(now)}")
        }
        val mood = when {
            avg == null -> if (sc.score >= 70) "A strong day so far" else if (sc.score >= 45) "A steady day so far" else "A slow start"
            sc.score - avg >= 5 -> "A strong day so far"
            sc.score - avg <= -8 -> "Behind your usual pace"
            else -> "A steady day so far"
        }
        val vs = avg?.let { val d = sc.score - it; ", ${if (d >= 0) "+" else ""}$d on your average" } ?: ""
        // biggest gap among enabled goals
        val gaps = sc.parts.filter { it.value < 0.999f }.toList().sortedBy { it.second }
        val gap = gaps.firstOrNull { g -> when (g.first.ring) { 1 -> AiCat.FOOD in cats; 0 -> if (g.first == GoalId.SLEEP) AiCat.SLEEP in cats else AiCat.HEALTH in cats; 3 -> AiCat.MONEY in cats; else -> AiCat.HABITS in cats } }
        val p = s.p; val d = s.d
        val gapText = gap?.let { (g, _) ->
            when (g) {
                GoalId.KCAL, GoalId.PROTEIN -> {
                    val k = p.kcalGoal - (d.kcal[day] ?: 0).toInt(); val pr = p.proteinG - (d.protein[day] ?: 0).toInt()
                    val parts = listOfNotNull(if (k > 0) String.format(Locale.US, "%,d kcal", k) else null, if (pr > 0) "$pr g protein" else null)
                    if (parts.isEmpty()) "Fuel is over target today." else "Fuel is the gap — ${parts.joinToString(" and ")} short${if (now.hour < 20) " with ${mealSlotFor(now).lowercase()} still open" else ""}."
                }
                GoalId.WATER -> "Water is the gap — ${String.format(Locale.US, "%.1f", (p.waterGoalMl - (d.water[day] ?: 0)) / 1000f)} L to go."
                GoalId.SUPPS -> "Supplements are the gap — ${d.dosesOn(day).count { it.pending }} doses not taken yet."
                GoalId.SLEEP -> if (d.sleep[day] == null) "Last night's sleep isn't logged yet." else "Sleep was short — ${hm(d.sleep[day]!!.minutes)} vs ${hm(p.sleepGoalMin)} goal."
                GoalId.HABITS -> "Habits are the gap — ${d.habits.size - d.habitsDone(day)} left today."
                GoalId.LEARN -> "Learning is the gap — ${(p.learnGoalMin - (d.learning[day] ?: 0)).coerceAtLeast(0)} min to your goal."
                GoalId.SPEND -> "Spending is over today's ${money0(p.dailySpendCents)} — ${money0((d.spend[day] ?: 0) - p.dailySpendCents)} over."
            }
        } ?: "Every enabled goal is hit — nice."
        val uses = buildList {
            add("Day Score")
            if (gap?.first?.ring == 1) add("Nutrition")
            if (gap?.first?.ring == 0) add("Health")
            if (gap?.first?.ring == 3) add("Money")
            if (gap?.first?.ring == 2) add("Grow")
        }.joinToString(", ")
        return Summary(title, "$mood: score ${sc.score}$vs. $gapText", "Uses: $uses · computed on this phone · ${hhmm(now)}")
    }

    // ======================================================= do next
    fun doNext(s: Snapshot, now: LocalTime = LocalTime.now()): List<DoNext> {
        val cats = s.p.aiCats()
        val day = s.day
        val out = mutableListOf<DoNext>()
        val d = s.d

        // supplements due now
        if (AiCat.HEALTH in cats) {
            val due = d.nextDoses(day).filter { it.dose.time <= hhmm(now) }.map { it.supp }
            if (due.isNotEmpty()) out += DoNext("supps", Hue.HEALTH, "Take ${if (due.size == 1) due[0].name else "${due.size} supplements"}", due.joinToString(" · ") { it.name }.take(70), "Open", NextAction.Tab("body", 0))
        }
        // usual meal
        if (AiCat.FOOD in cats && now.hour in 6..21) {
            val slot = mealSlotFor(now)
            val loggedSlot = s.meals.any { it.day == day && it.slot == slot }
            if (!loggedSlot) {
                val usual = s.meals.filter { it.slot == slot && it.day < day }.groupBy { it.items.lowercase().trim() }.maxByOrNull { it.value.size }?.value
                if (usual != null && usual.size >= 2) {
                    val m = usual.last()
                    val prGap = s.p.proteinG - (d.protein[day] ?: 0).toInt()
                    val sub = if (prGap > 0 && m.protein > 0) "${m.items.take(40)} closes ${(m.protein * 100 / prGap).coerceAtMost(100)}% of the protein gap" else "${m.items.take(40)} · ${m.kcal} kcal"
                    out += DoNext("meal", Hue.FUEL, "Log your usual ${slot.lowercase()}", sub, "Log", NextAction.Meal(m.items))
                }
            }
        }
        // next workout
        if (AiCat.TRAIN in cats && s.templates.isNotEmpty() && s.sessions.none { it.day == day } && s.active == null) {
            val lastT = s.sessions.firstOrNull { it.templateId != null }
            val idx = s.templates.indexOfFirst { it.id == lastT?.templateId }
            val next = s.templates[(idx + 1) % s.templates.size]
            val ex = s.templateEx.filter { it.templateId == next.id }
            val first = ex.firstOrNull()
            val sub = first?.let { e ->
                val prev = s.sets.filter { it.exercise == e.name && !it.warmup }.groupBy { it.sessionId }.maxByOrNull { g -> g.value.maxOf { it.ts } }?.value
                val top = prev?.maxByOrNull { it.e1rm }
                if (top != null) {
                    val trend = trendUp(s.sets.filter { it.exercise == e.name })
                    "${e.name} last: ${fmtKg(top.kg)} × ${top.reps}${if (trend >= 2) " · e1RM up $trend sessions in a row" else ""}"
                } else "${ex.size} exercises · first: ${e.name}"
            } ?: "Empty template"
            val title = first?.let { e ->
                val prev = s.sets.filter { it.exercise == e.name && !it.warmup }.maxByOrNull { it.ts }
                if (prev != null && trendUp(s.sets.filter { it.exercise == e.name }) >= 2) "${next.name} — try ${fmtKg(prev.kg + 2.5)} kg × ${prev.reps}" else "Start ${next.name}"
            } ?: "Start ${next.name}"
            out += DoNext("workout", Hue.TRAIN, title, sub, "Start", NextAction.Tab("train"))
        }
        // stalled project
        if (AiCat.HABITS in cats) {
            val stalled = s.projects.filter { it.nextAction.isNotBlank() && it.nextSince > 0 }
                .map { it to (day - Days.dayOf(it.nextSince)).toInt() }.filter { it.second >= 2 }.maxByOrNull { it.second }
            if (stalled != null) {
                out += DoNext("project", Hue.GROW, "${stalled.first.name}: ${stalled.first.nextAction}".take(60), "Next action stalled ${stalled.second} days · a ${s.p.focusMin}-min focus block moves it", "Focus", NextAction.Tab("grow", 0))
            } else {
                val habitsLeft = d.habits.size - d.habitsDone(day)
                if (habitsLeft > 0 && now.hour >= 17) out += DoNext("habits", Hue.GROW, "$habitsLeft habit${if (habitsLeft == 1) "" else "s"} left today", d.habits.filter { it.id !in d.doneHabitIds(day) }.joinToString(" · ") { it.name }.take(60), "Open", NextAction.Tab("grow", 2))
            }
        }
        // water behind pace
        if (AiCat.HEALTH in cats && now.hour in 10..21) {
            val expected = s.p.waterGoalMl * ((now.hour - 7).coerceIn(0, 14) / 14f)
            val have = d.water[day] ?: 0
            if (have < expected - 400) out += DoNext("water", Hue.HEALTH, "Drink a glass of water", "${String.format(Locale.US, "%.1f", have / 1000f)} L so far · ${String.format(Locale.US, "%.1f", expected / 1000f)} L is on pace", "Open", NextAction.Tab("body", 0))
        }
        // budget
        if (AiCat.MONEY in cats) {
            val ms = Days.date(day).withDayOfMonth(1).toEpochDay()
            val spentBy = s.txns.filter { it.day >= ms && it.amountCents < 0 && it.toAccountId == null && it.category != "Transfer" }.groupBy { it.category }.mapValues { e -> -e.value.sumOf { it.amountCents } }
            val over = s.budgets.map { it to (spentBy[it.category] ?: 0) - it.limitCents }.filter { it.second > 0 }.maxByOrNull { it.second }
            if (over != null) out += DoNext("budget", Hue.MONEY, "${over.first.category} is ${money0(over.second)} over budget", "Skip it for the rest of the month to land on plan", "See", NextAction.Tab("money"))
        }
        return out.take(4)
    }

    private fun trendUp(sets: List<WorkoutSet>): Int {
        val bySession = sets.filter { !it.warmup }.groupBy { it.sessionId }.values.map { g -> g.maxOf { it.ts } to g.maxOf { it.e1rm } }.sortedBy { it.first }.map { it.second }
        var n = 0
        for (i in bySession.size - 1 downTo 1) { if (bySession[i] > bySession[i - 1]) n++ else break }
        return n
    }

    // ======================================================= patterns
    fun patterns(s: Snapshot): List<Pattern> {
        val cats = s.p.aiCats()
        val hidden = s.feedback.filter { !it.useful }.map { it.key }.toSet()
        val out = mutableListOf<Pattern>()
        if (AiCat.SLEEP in cats) { sleepScore(s)?.let(out::add); bedtime(s)?.let(out::add) }
        if (AiCat.MONEY in cats) { out += budgets(s, AiCat.TX in cats); weekdaySpend(s)?.let(out::add) }
        if (AiCat.TRAIN in cats) workoutDays(s)?.let(out::add)
        if (AiCat.HEALTH in cats) waterScore(s)?.let(out::add)
        return out.filter { it.key !in hidden }
    }

    fun pattern(s: Snapshot, key: String): Pattern? {
        val cats = s.p.aiCats()
        return listOfNotNull(sleepScore(s), bedtime(s), weekdaySpend(s), workoutDays(s), waterScore(s)).plus(budgets(s, AiCat.TX in cats)).firstOrNull { it.key == key }
    }

    private fun pastDays(s: Snapshot, n: Int = 30) = ((s.day - n) until s.day)

    private fun sleepScore(s: Snapshot): Pattern? {
        val pairs = pastDays(s, 45).mapNotNull { d -> val sl = s.d.sleep[d] ?: return@mapNotNull null; val sc = s.score(d); if (!sc.hasData) null else Triple(d, sl.minutes, sc.score) }
        if (pairs.size < 7) return null
        val r = pearson(pairs.map { it.second.toDouble() }, pairs.map { it.third.toDouble() }) ?: return null
        val cut = 420
        val long = pairs.filter { it.second >= cut }; val short = pairs.filter { it.second < cut }
        val claim = if (long.size >= 2 && short.size >= 2) {
            val a = long.map { it.third }.average().toInt(); val b = short.map { it.third }.average().toInt()
            "After 7 h+ of sleep your Day Score averages $a, vs $b after shorter nights."
        } else "Sleep length and your Day Score: ${strength(r)} link over ${pairs.size} nights."
        val top = pairs.sortedByDescending { it.third }.take(3)
        return Pattern(
            "sleep-score", "Sleep", Hue.HEALTH, claim, "DAY SCORE vs SLEEP · ${pairs.size} NIGHTS",
            PatternChart.Scatter(pairs.map { it.second / 60f to it.third.toFloat() }, top.map { pairs.indexOf(it) }.toSet(), 7f, "7 h",
                String.format(Locale.US, "%.0f h", pairs.minOf { it.second } / 60f), String.format(Locale.US, "%.0f h", pairs.maxOf { it.second } / 60f), "score"),
            top.map { "${dayLabel(it.first)} · score ${it.third}" to hm(it.second) }, r, pairs.size, "sleep durations and Day Scores",
        )
    }

    private fun bedtime(s: Snapshot): Pattern? {
        val pairs = pastDays(s, 30).mapNotNull { d -> val sl = s.d.sleep[d] ?: return@mapNotNull null; val sc = s.score(d); if (!sc.hasData) null else Triple(d, bedMinutes(Days.timeOf(sl.startTs)), sc.score) }
        if (pairs.size < 7) return null
        val r = pearson(pairs.map { it.second.toDouble() }, pairs.map { it.third.toDouble() }) ?: return null
        val top = pairs.sortedByDescending { it.third }.take(3)
        val median = pairs.map { it.second }.sorted()[pairs.size / 2]
        val latestTop = top.maxOf { it.second }
        if (latestTop > median) return null // no early-bedtime story
        val thr = ((latestTop + 14) / 15) * 15
        return Pattern(
            "bedtime-score", "Sleep", Hue.HEALTH,
            "Your 3 best days this month all followed lights-out before ${bedLabel(thr)}.",
            "DAY SCORE vs LIGHTS-OUT · ${pairs.size} NIGHTS",
            PatternChart.Scatter(pairs.map { it.second.toFloat() to it.third.toFloat() }, top.map { pairs.indexOf(it) }.toSet(), thr.toFloat(), bedLabel(thr),
                bedLabel(pairs.minOf { it.second }), bedLabel(pairs.maxOf { it.second }), "score"),
            top.map { "${dayLabel(it.first)} · score ${it.third}" to bedLabel(it.second) }, r, pairs.size, "bedtimes and Day Scores",
        )
    }

    private fun budgets(s: Snapshot, tx: Boolean): List<Pattern> {
        val ms = Days.date(s.day).withDayOfMonth(1).toEpochDay()
        val month = s.txns.filter { it.day >= ms && it.amountCents < 0 && it.toAccountId == null && it.category != "Transfer" }
        return s.budgets.mapNotNull { b ->
            val list = month.filter { it.category == b.category }
            val spent = -list.sumOf { it.amountCents }
            if (spent <= b.limitCents || list.isEmpty()) return@mapNotNull null
            val weekday = list.count { Days.date(it.day).dayOfWeek.value <= 5 }
            val avg = spent / list.size
            val weeks = list.groupBy { (it.day - ms) / 7 }.toSortedMap()
            val claim = "${b.category} is ${money0(spent - b.limitCents)} over budget. $weekday of ${list.size} purchases were on weekdays, avg ${fmtCents(avg)}."
            val rows = if (tx) list.sortedBy { it.amountCents }.take(3).map { "${it.name} · ${dayLabel(it.day)}" to fmtCents(-it.amountCents) }
            else listOf("Budget" to money0(b.limitCents), "Spent this month" to money0(spent), "Purchases" to list.size.toString())
            Pattern(
                "budget-${b.category}", "Money", Hue.MONEY, claim, "${b.category.uppercase()} BY WEEK · THIS MONTH",
                PatternChart.Bars(weeks.keys.map { "W${it + 1}" }, weeks.values.map { w -> -w.sumOf { it.amountCents } / 100f }, null, "€"),
                rows, null, list.size, if (tx) "transactions in ${b.category}" else "category totals",
            )
        }
    }

    private fun weekdaySpend(s: Snapshot): Pattern? {
        val from = s.day - 56
        val days = (from until s.day)
        val spendDays = days.count { (s.d.spend[it] ?: 0) > 0 }
        if (spendDays < 14) return null
        val byDow = (1..7).map { dw -> val ds = days.filter { Days.date(it).dayOfWeek.value == dw }; ds.sumOf { s.d.spend[it] ?: 0 }.toDouble() / ds.size.coerceAtLeast(1) }
        val maxI = byDow.indices.maxBy { byDow[it] }
        val others = byDow.filterIndexed { i, _ -> i != maxI }.average()
        if (others <= 0 || byDow[maxI] < others * 1.4) return null
        val name = DayOfWeek.of(maxI + 1).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return Pattern(
            "spend-weekday", "Money", Hue.MONEY,
            "You spend most on ${name}s: avg ${money0(byDow[maxI].toLong())} vs ${money0(others.toLong())} on other days.",
            "AVG SPEND BY WEEKDAY · 8 WEEKS",
            PatternChart.Bars((1..7).map { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }, byDow.map { (it / 100).toFloat() }, maxI, "€"),
            listOf("$name average" to money0(byDow[maxI].toLong()), "Other days average" to money0(others.toLong()), "Days with spending" to "$spendDays of 56"),
            null, spendDays, "daily spending totals",
        )
    }

    private fun workoutDays(s: Snapshot): Pattern? {
        val wDays = s.sessions.map { it.day }.toSet()
        val data = pastDays(s, 45).mapNotNull { d -> val sc = s.score(d); if (!sc.hasData) null else d to sc.score }
        val a = data.filter { it.first in wDays }; val b = data.filter { it.first !in wDays }
        if (a.size < 3 || b.size < 3) return null
        val r = pearson(data.map { if (it.first in wDays) 1.0 else 0.0 }, data.map { it.second.toDouble() }) ?: return null
        val ma = a.map { it.second }.average().toInt(); val mb = b.map { it.second }.average().toInt()
        return Pattern(
            "workout-score", "Train", Hue.TRAIN,
            if (ma >= mb) "Training days score higher: $ma on average vs $mb on rest days." else "Rest days score higher than training days: $mb vs $ma — check fuel on training days.",
            "AVG DAY SCORE · 45 DAYS", PatternChart.Bars(listOf("Training", "Rest"), listOf(ma.toFloat(), mb.toFloat()), if (ma >= mb) 0 else 1, ""),
            listOf("Training days" to "${a.size}", "Rest days" to "${b.size}", "Best training day" to (a.maxBy { it.second }.let { "${dayLabel(it.first)} · ${it.second}" })),
            r, data.size, "workout dates and Day Scores",
        )
    }

    private fun waterScore(s: Snapshot): Pattern? {
        val data = pastDays(s, 30).mapNotNull { d -> val sc = s.score(d); if (!sc.hasData) null else Triple(d, (s.d.water[d] ?: 0).toInt(), sc.score) }
        if (data.size < 10) return null
        val goalHit = data.filter { it.second >= s.p.waterGoalMl }; val miss = data.filter { it.second < s.p.waterGoalMl }
        if (goalHit.size < 3 || miss.size < 3) return null
        val r = pearson(data.map { it.second.toDouble() }, data.map { it.third.toDouble() }) ?: return null
        if (abs(r) < 0.15) return null
        val pct = goalHit.size * 100 / data.size
        return Pattern(
            "water-score", "Health", Hue.HEALTH,
            "You hit your water goal on $pct% of logged days. Those days average ${goalHit.map { it.third }.average().toInt()} vs ${miss.map { it.third }.average().toInt()}.",
            "DAY SCORE vs WATER · ${data.size} DAYS",
            PatternChart.Scatter(data.map { it.second / 1000f to it.third.toFloat() }, emptySet(), s.p.waterGoalMl / 1000f, String.format(Locale.US, "%.1f L", s.p.waterGoalMl / 1000f),
                String.format(Locale.US, "%.1f L", data.minOf { it.second } / 1000f), String.format(Locale.US, "%.1f L", data.maxOf { it.second } / 1000f), "score"),
            listOf("Goal hit" to "${goalHit.size} days", "Missed" to "${miss.size} days"), r, data.size, "daily water totals and Day Scores",
        )
    }

    // ======================================================= reports
    /** kind 0 = week (offset weeks back), 1 = month (offset months back). */
    fun report(s: Snapshot, kind: Int, offset: Int): Report {
        val today = Days.date(s.day)
        val (start, end, prevStart, prevEnd) = if (kind == 0) {
            val ws = today.with(DayOfWeek.MONDAY).minusWeeks(offset.toLong())
            listOf(ws, ws.plusDays(6), ws.minusWeeks(1), ws.minusDays(1))
        } else {
            val ms = today.withDayOfMonth(1).minusMonths(offset.toLong())
            listOf(ms, ms.plusMonths(1).minusDays(1), ms.minusMonths(1), ms.minusDays(1))
        }
        val a = start.toEpochDay(); val b = minOf(end.toEpochDay(), s.day)
        val pa = prevStart.toEpochDay(); val pb = prevEnd.toEpochDay()
        val days = (a..b)
        val pdays = (maxOf(pa, s.day - SNAP_DAYS)..pb)
        val cats = s.p.aiCats()

        val scores = days.map { s.score(it) }
        val pscores = pdays.map { s.score(it) }
        val active = scores.count { it.hasData }
        val pactive = pscores.count { it.hasData }
        val total = days.count()
        val cons = if (total == 0) 0 else active * 100 / total
        val pcons = if (pdays.count() == 0 || pa < s.day - SNAP_DAYS) null else pactive * 100 / pdays.count()
        val avg = scores.filter { it.hasData }.map { it.score }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val pavg = pscores.filter { it.hasData }.map { it.score }.takeIf { it.isNotEmpty() }?.average()?.toInt()

        val sess = s.sessions.filter { it.day in days }
        val sessIds = sess.map { it.id }.toSet()
        val vol = s.sets.filter { it.sessionId in sessIds && !it.warmup }.sumOf { it.kg * it.reps }
        val spent = days.sumOf { s.d.spend[it] ?: 0 }
        val pspent = pdays.sumOf { s.d.spend[it] ?: 0 }
        val sleeps = days.mapNotNull { s.d.sleep[it]?.minutes }
        val learn = days.sumOf { s.d.learning[it] ?: 0 }
        val tasksDone = s.taskDone.count { it.day in days } + s.tasks.count { t -> t.doneTs != null && Days.dayOf(t.doneTs) in days && !t.repeating }

        // PRs in period
        val prs = mutableListOf<String>()
        if (AiCat.TRAIN in cats) {
            s.sets.filter { !it.warmup && it.kg > 0 }.groupBy { it.exercise }.forEach { (ex, list) ->
                val before = list.filter { Days.dayOf(it.ts) < a }.maxOfOrNull { it.e1rm } ?: return@forEach
                val inP = list.filter { Days.dayOf(it.ts) in days }.maxByOrNull { it.e1rm } ?: return@forEach
                if (inP.e1rm > before) prs += ex
            }
        }
        val highlights = buildList {
            if (prs.isNotEmpty()) add(if (prs.size == 1) "${prs[0]} PR" else "PRs in ${prs.take(3).joinToString(", ")}")
            scores.withIndex().filter { it.value.hasData }.maxByOrNull { it.value.score }?.let { add("Best day: ${dayLabel(a + it.index)} · score ${it.value.score}") }
            if (avg != null && pavg != null && avg - pavg >= 5) add("Day Score avg $avg — up ${avg - pavg} on the previous ${if (kind == 0) "week" else "month"}")
            if (AiCat.MONEY in cats) {
                val spentBy = s.txns.filter { it.day in days && it.amountCents < 0 && it.toAccountId == null && it.category != "Transfer" }.groupBy { it.category }.mapValues { e -> -e.value.sumOf { it.amountCents } }
                if (kind == 1) s.budgets.forEach { bu -> val sp = spentBy[bu.category] ?: 0; if (sp > bu.limitCents) add("${bu.category} ${money0(sp - bu.limitCents)} over budget") }
                spentBy.maxByOrNull { it.value }?.let { if (size < 4) add("Top spend: ${it.key} ${money0(it.value)}") }
            }
            if (AiCat.SLEEP in cats && sleeps.size >= 3) add("Sleep avg ${hm(sleeps.average().toInt())} over ${sleeps.size} nights")
        }.take(4)

        val text = buildString {
            if (active == 0) { append("Nothing logged in this period."); return@buildString }
            append("Active $active of $total days")
            if (avg != null) append(", Day Score averaged $avg")
            if (avg != null && pavg != null) append(" (${if (avg >= pavg) "+" else ""}${avg - pavg} vs previous)")
            append(". ")
            if (AiCat.TRAIN in cats) append(if (sess.isEmpty()) "No workouts. " else "${sess.size} workout${if (sess.size == 1) "" else "s"}, ${String.format(Locale.US, "%,.0f", vol)} kg moved. ")
            if (AiCat.MONEY in cats && spent > 0) {
                append("Spent ${money0(spent)}")
                if (pspent > 0) { val pct = ((spent - pspent) * 100 / pspent).toInt(); append(" (${if (pct >= 0) "+" else ""}$pct%)") }
                append(". ")
            }
            if (AiCat.HABITS in cats && tasksDone > 0) append("$tasksDone tasks closed. ")
            // best weekday for output
            val best = scores.withIndex().filter { it.value.hasData }.groupBy { Days.date(a + it.index).dayOfWeek }.mapValues { e -> e.value.map { it.value.score }.average() }
            if (kind == 1 && best.size >= 4) best.maxByOrNull { it.value }?.let { append("${it.key.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}s were your strongest days.") }
        }.trim()

        val tag: String; val title: String
        if (kind == 0) {
            val wn = start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            tag = "W$wn"; title = "Weekly review · Week $wn"
        } else {
            tag = start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
            title = "Monthly report · ${start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
        }
        val range = "${start.dayOfMonth} ${start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} – ${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
        val series = (a..end.toEpochDay()).map { d -> if (d > s.day) null else s.score(d).let { if (it.hasData) it.score else 0 } }
        val labels = (a..end.toEpochDay()).map { d -> if (kind == 0) Days.date(d).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH) else Days.date(d).dayOfMonth.let { if (it == 1 || it % 7 == 1) "$it" else "" } }
        return Report(
            tag, title, range, cons, pcons?.let { cons - it }, active, total, avg, if (avg != null && pavg != null) avg - pavg else null,
            sess.size, vol, spent, if (pspent > 0) ((spent - pspent) * 100 / pspent).toInt() else null,
            sleeps.takeIf { it.isNotEmpty() }?.average()?.toInt(), learn, tasksDone, highlights, text, series, labels,
        )
    }

    /** One-line insight for the Today card. */
    fun top(s: Snapshot): Pair<String, String?>? {
        if (!s.p.aiOn()) return null
        val p = patterns(s).firstOrNull()
        if (p != null) return p.claim to p.key
        val dn = doNext(s).firstOrNull() ?: return null
        return "${dn.title}. ${dn.sub}." to null
    }
}
