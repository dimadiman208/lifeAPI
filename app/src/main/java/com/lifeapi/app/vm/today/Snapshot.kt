package com.lifeapi.app.vm.today

import com.lifeapi.app.data.Account
import com.lifeapi.app.data.Budget
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Habit
import com.lifeapi.app.data.HabitCheck
import com.lifeapi.app.data.Hue
import com.lifeapi.app.data.InsightFeedback
import com.lifeapi.app.data.Intake
import com.lifeapi.app.data.LifeRepository
import com.lifeapi.app.data.Meal
import com.lifeapi.app.data.Profile
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.SleepLog
import com.lifeapi.app.data.Supplement
import com.lifeapi.app.data.Task
import com.lifeapi.app.data.TaskDone
import com.lifeapi.app.data.TemplateExercise
import com.lifeapi.app.data.Txn
import com.lifeapi.app.data.WorkoutSession
import com.lifeapi.app.data.WorkoutSet
import com.lifeapi.app.data.WorkoutTemplate
import com.lifeapi.app.data.fmtCents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale

// ============================================================ daily goals

/** ring: 0 body, 1 fuel, 2 grow, 3 money (not a ring). */
enum class GoalId(val key: String, val label: String, val hue: Int, val ring: Int) {
    WATER("water", "Water", Hue.HEALTH, 0),
    SUPPS("supps", "Supplements", Hue.HEALTH, 0),
    SLEEP("sleep", "Sleep", Hue.HEALTH, 0),
    KCAL("kcal", "Calories", Hue.FUEL, 1),
    PROTEIN("protein", "Protein", Hue.FUEL, 1),
    HABITS("habits", "Habits", Hue.GROW, 2),
    LEARN("learn", "Learning", Hue.GROW, 2),
    SPEND("spend", "Daily spend", Hue.MONEY, 3);

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

val DEFAULT_GOALS: Set<GoalId> = GoalId.entries.toSet()

fun Profile.enabledGoals(): Set<GoalId> =
    if (dayGoals.isBlank()) DEFAULT_GOALS else dayGoals.split(',').mapNotNull { GoalId.of(it.trim()) }.toSet()

fun goalsString(set: Set<GoalId>): String = GoalId.entries.filter { it in set }.joinToString(",") { it.key }.ifEmpty { "none" }

fun goalTarget(g: GoalId, p: Profile, suppsToday: Int, habits: Int): String = when (g) {
    GoalId.WATER -> String.format(Locale.US, "%.1f L", p.waterGoalMl / 1000f)
    GoalId.SUPPS -> if (suppsToday == 0) "none set" else "all $suppsToday"
    GoalId.SLEEP -> "${p.sleepGoalMin / 60} h ${String.format(Locale.US, "%02d", p.sleepGoalMin % 60)} m"
    GoalId.KCAL -> String.format(Locale.US, "%,d ±10%%", p.kcalGoal)
    GoalId.PROTEIN -> "${p.proteinG} g"
    GoalId.HABITS -> if (habits == 0) "none set" else "$habits of $habits"
    GoalId.LEARN -> "${p.learnGoalMin} min"
    GoalId.SPEND -> "≤ ${fmtCents(p.dailySpendCents).removeSuffix(".00")}"
}

// ============================================================ per-day data

data class DayData(
    val water: Map<Long, Long> = emptyMap(),
    val kcal: Map<Long, Long> = emptyMap(),
    val protein: Map<Long, Long> = emptyMap(),
    val learning: Map<Long, Long> = emptyMap(),
    val sleep: Map<Long, SleepLog> = emptyMap(),
    val intakes: List<Intake> = emptyList(),
    val checks: List<HabitCheck> = emptyList(),
    /** Expenses per day (positive cents), transfers excluded. */
    val spend: Map<Long, Long> = emptyMap(),
    val supps: List<Supplement> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val suppRules: List<com.lifeapi.app.data.SuppRule> = emptyList(),
) {
    private val intakeByDay by lazy { intakes.groupBy { it.day } }
    private val doseCache = HashMap<Long, List<com.lifeapi.app.data.supps.DoseSlot>>()

    /** Planned doses on [day] resolved against intakes (see SuppSchedule). */
    fun dosesOn(day: Long): List<com.lifeapi.app.data.supps.DoseSlot> = doseCache.getOrPut(day) {
        com.lifeapi.app.data.supps.SuppSchedule.resolve(supps, suppRules, intakeByDay[day].orEmpty(), day)
    }
    /** Earliest pending dose per supplement. */
    fun nextDoses(day: Long): List<com.lifeapi.app.data.supps.DoseSlot> = dosesOn(day).filter { it.pending }.distinctBy { it.supp.id }
    private val checksByDay by lazy { checks.groupBy { it.day } }

    /** Supplements with at least one dose planned on [day]. */
    fun suppsOn(day: Long) = dosesOn(day).map { it.supp }.distinctBy { it.id }
    /** Supplements whose every dose that day is taken or skipped. */
    fun handledSupps(day: Long): Int = suppsOn(day).size - dosesOn(day).filter { it.pending }.map { it.supp.id }.distinct().size
    fun takenIds(day: Long): Set<Long> = suppsOn(day).map { it.id }.toSet() - dosesOn(day).filter { it.pending }.map { it.supp.id }.toSet()
    fun habitsDone(day: Long): Int {
        val ids = habits.map { it.id }.toSet()
        return checksByDay[day].orEmpty().count { it.habitId in ids }
    }
    fun doneHabitIds(day: Long): Set<Long> = checksByDay[day].orEmpty().map { it.habitId }.toSet()

    fun hasData(day: Long): Boolean =
        (water[day] ?: 0) > 0 || (kcal[day] ?: 0) > 0 || (learning[day] ?: 0) > 0 || sleep[day] != null ||
            intakeByDay[day].orEmpty().isNotEmpty() || checksByDay[day].orEmpty().isNotEmpty() || (spend[day] ?: 0) > 0
}

/** Progress 0..1 toward a goal on a day, null if the goal doesn't apply (e.g. no supplements set up). */
fun goalProgress(g: GoalId, day: Long, d: DayData, p: Profile): Float? = when (g) {
    GoalId.WATER -> ((d.water[day] ?: 0L).toFloat() / p.waterGoalMl.coerceAtLeast(1)).coerceIn(0f, 1f)
    GoalId.SUPPS -> d.dosesOn(day).let { l -> if (l.isEmpty()) null else l.count { it.taken }.toFloat() / l.size }
    GoalId.SLEEP -> ((d.sleep[day]?.minutes ?: 0).toFloat() / p.sleepGoalMin.coerceAtLeast(1)).coerceIn(0f, 1f)
    GoalId.KCAL -> {
        val k = (d.kcal[day] ?: 0L).toFloat(); val goal = p.kcalGoal.coerceAtLeast(1).toFloat()
        if (k <= goal * 1.1f) (k / (goal * 0.9f)).coerceIn(0f, 1f) else (1f - (k - goal * 1.1f) / (goal * 0.5f)).coerceIn(0f, 1f)
    }
    GoalId.PROTEIN -> ((d.protein[day] ?: 0L).toFloat() / p.proteinG.coerceAtLeast(1)).coerceIn(0f, 1f)
    GoalId.HABITS -> if (d.habits.isEmpty()) null else d.habitsDone(day).toFloat() / d.habits.size
    GoalId.LEARN -> ((d.learning[day] ?: 0L).toFloat() / p.learnGoalMin.coerceAtLeast(1)).coerceIn(0f, 1f)
    GoalId.SPEND -> {
        val s = (d.spend[day] ?: 0L).toFloat(); val lim = p.dailySpendCents.coerceAtLeast(1).toFloat()
        if (s <= lim) 1f else (1f - (s - lim) / lim).coerceIn(0f, 1f)
    }
}

data class DayScore(
    val score: Int,
    val body: Float,
    val fuel: Float,
    val grow: Float,
    val hasData: Boolean,
    val parts: Map<GoalId, Float> = emptyMap(),
) {
    val hit: Int get() = parts.count { it.value >= 0.999f }
}

fun scoreFor(day: Long, d: DayData, p: Profile, enabled: Set<GoalId> = p.enabledGoals()): DayScore {
    val all = GoalId.entries.mapNotNull { g -> goalProgress(g, day, d, p)?.let { g to it } }.toMap()
    fun ring(r: Int) = all.filterKeys { it.ring == r }.values.let { if (it.isEmpty()) 0f else it.average().toFloat() }
    val parts = all.filterKeys { it in enabled }
    val has = d.hasData(day)
    val score = if (!has || parts.isEmpty()) 0 else (parts.values.average() * 100).toInt()
    return DayScore(score, ring(0), ring(1), ring(2), has, parts)
}

// ============================================================ the snapshot

data class Snapshot(
    val day: Long,
    val p: Profile,
    val d: DayData,
    val meals: List<Meal>,
    val txns: List<Txn>,
    val budgets: List<Budget>,
    val accounts: List<Account>,
    val templates: List<WorkoutTemplate>,
    val templateEx: List<TemplateExercise>,
    val sessions: List<WorkoutSession>,
    val sets: List<WorkoutSet>,
    val active: WorkoutSession?,
    val projects: List<Project>,
    val tasks: List<Task>,
    val taskDone: List<TaskDone>,
    val feedback: List<InsightFeedback>,
) {
    fun score(day: Long) = scoreFor(day, d, p)
}

const val SNAP_DAYS = 70L

private data class Body(val d: DayData, val meals: List<Meal>)
private data class Money(val txns: List<Txn>, val budgets: List<Budget>, val accounts: List<Account>)
private data class Train(val t: List<WorkoutTemplate>, val te: List<TemplateExercise>, val s: List<WorkoutSession>, val sets: List<WorkoutSet>, val active: WorkoutSession?)
private data class Grow(val projects: List<Project>, val tasks: List<Task>, val done: List<TaskDone>, val fb: List<InsightFeedback>)

fun snapshotFlow(repo: LifeRepository, day: Long): Flow<Snapshot> {
    val dao = repo.dao
    val from = day - SNAP_DAYS
    val days = combine(dao.waterByDay(from, day), dao.kcalByDay(from, day), dao.proteinByDay(from, day), dao.learningByDay(from, day), dao.sleepRange(from, day)) { w, k, pr, l, s ->
        DayData(w.associate { it.day to it.total }, k.associate { it.day to it.total }, pr.associate { it.day to it.total }, l.associate { it.day to it.total }, s.associateBy { it.day })
    }
    val days2 = combine(days, dao.intakesRange(from, day), dao.habitChecks(from, day), combine(dao.supplements(), repo.body.rules(), ::Pair), dao.habits()) { d, i, c, (s, r), h ->
        d.copy(intakes = i, checks = c, supps = s, habits = h, suppRules = r)
    }
    val money = combine(dao.txnsRange(from, day), dao.budgets(), dao.accounts()) { t, b, a -> Money(t, b, a) }
    val body = combine(days2, repo.body.mealsRange(day - 35, day), money) { d, m, mo ->
        val spend = mo.txns.filter { it.amountCents < 0 && it.toAccountId == null && it.category != "Transfer" }.groupBy { it.day }.mapValues { e -> -e.value.sumOf { it.amountCents } }
        Body(d.copy(spend = spend), m) to mo
    }
    val train = combine(dao.templates(), dao.templateExercises(), dao.finishedSessions(), dao.allSets(), dao.activeSession()) { a, b, c, d, e -> Train(a, b, c, d, e) }
    val grow = combine(repo.grow.projects(), repo.grow.tasks(), repo.grow.taskDone(from, day), repo.grow.feedback()) { a, b, c, d -> Grow(a, b, c, d) }
    return combine(body, train, grow, dao.profile().map { it ?: Profile() }) { (b, mo), t, g, p ->
        Snapshot(day, p, b.d, b.meals, mo.txns, mo.budgets, mo.accounts, t.t, t.te, t.s, t.sets, t.active, g.projects, g.tasks, g.done, g.fb)
    }
}

fun dayLabel(day: Long): String {
    val d = Days.date(day)
    return "${d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)}"
}
