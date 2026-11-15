package com.lifeapi.app.vm

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.DaySum
import com.lifeapi.app.data.Profile
import com.lifeapi.app.data.SleepLog
import com.lifeapi.app.data.TaskDone
import com.lifeapi.app.data.WeightLog
import com.lifeapi.app.data.hhmm
import com.lifeapi.app.vm.today.DayScore
import com.lifeapi.app.vm.today.InsightEngine
import com.lifeapi.app.vm.today.snapshotFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import com.lifeapi.app.vm.today.aiOn
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

fun List<DaySum>.toMap(): Map<Long, Long> = associate { it.day to it.total }

fun weekStart(day: Long): Long = Days.date(day).with(DayOfWeek.MONDAY).toEpochDay()

// ------------------------------------------------------------ Today layout

data class CardDef(val id: String, val name: String, val meta: String, val hue: Int)

/** hue: Hue constants, -1 = Volt */
val TODAY_CARDS = listOf(
    CardDef("score", "Day Score", "Rings · Body, Fuel, Grow", -1),
    CardDef("intel", "Intelligence", "One insight a day", -1),
    CardDef("quick", "Quick log", "4 one-tap tiles", 0),
    CardDef("next", "Up next", "Reminders & tasks", 4),
    CardDef("stats", "Stats grid", "Water · Sleep · Spend · Streak", 0),
    CardDef("week", "This week", "7-day score bars", -1),
    CardDef("project", "Project next action", "Your most active project", 4),
    CardDef("money", "Finance snapshot", "Budget left · net worth", 2),
    CardDef("workout", "Workout", "Next session · weekly volume", 3),
)
private val DEFAULT_SHOWN = setOf("score", "intel", "quick", "next", "stats", "week")

/** Ordered card ids with visibility. */
fun Profile.cardLayout(): List<Pair<String, Boolean>> {
    if (todayCards.isBlank()) return TODAY_CARDS.map { it.id to (it.id in DEFAULT_SHOWN) }
    val parsed = todayCards.split(',').filter { it.isNotBlank() }.map { if (it.startsWith("-")) it.drop(1) to false else it to true }
        .filter { (id, _) -> TODAY_CARDS.any { it.id == id } }.distinctBy { it.first }
    return parsed + TODAY_CARDS.filter { c -> parsed.none { it.first == c.id } }.map { it.id to false }
}

fun layoutString(l: List<Pair<String, Boolean>>) = l.joinToString(",") { (id, on) -> if (on) id else "-$id" }

val QUICK_TILES = listOf("Water", "Stack", "Meal", "Expense", "Set", "Task", "Habit", "Weight")
fun Profile.tiles(): List<String> {
    val t = quickTiles.split(',').map { it.trim() }.filter { it in QUICK_TILES }.distinct()
    return if (t.isEmpty()) QUICK_TILES.take(4) else t.take(4)
}

// ------------------------------------------------------------ state

data class UpNextItem(val key: String, val time: String, val title: String, val sub: String, val kind: Int, val refId: Long, val done: Boolean) {
    companion object { const val SUPP = 0; const val HABIT = 1; const val SLEEP = 2; const val TASK = 3 }
}

data class ProjectCard(val id: Long, val name: String, val next: String, val stalledDays: Int, val pct: Int?)
data class MoneyCard(val leftTodayCents: Long, val dailyCents: Long, val netWorthCents: Long?, val monthBudgetLeft: Long?, val hide: Boolean)
data class WorkoutCard(val nextName: String?, val exCount: Int, val activeId: Long?, val weekSessions: Int, val weekVolume: Double, val doneToday: Boolean)

data class TodayState(
    val dateLabel: String,
    val greeting: String,
    val name: String,
    val score: DayScore,
    val scoreDelta: Int?,
    val goalsHit: Int,
    val goalsTotal: Int,
    val kcal: Int,
    val habitsDone: Int,
    val habitsTotal: Int,
    val waterMl: Int,
    val waterGoal: Int,
    val supplementsDue: Int,
    val stackLeft: Int,
    val nextMealSlot: String,
    val upNext: List<UpNextItem>,
    val sleepLast: SleepLog?,
    val sleepWeek: List<Int>,
    val spentTodayCents: Long,
    val dailyBudgetCents: Long,
    val learnStreak: Int,
    val learnToday: Int,
    val learnGoal: Int,
    val week: List<Pair<String, Int?>>,
    val todayIndex: Int,
    val weekAvg: Int,
    val sleepGoalMin: Int,
    val cards: List<String>,
    val tiles: List<String>,
    val insight: Pair<String, String?>?,
    val aiOn: Boolean,
    val project: ProjectCard?,
    val money: MoneyCard,
    val workout: WorkoutCard,
    val firstHabit: Pair<Long, String>?,
    val lastWeight: Double?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayVM(app: Application) : BaseVM(app) {

    private val lastWeight = bodyDao.lastWeight()

    val state = today.flatMapLatest { day ->
        kotlinx.coroutines.flow.combine(snapshotFlow(repo, day), lastWeight) { s, lw -> build(s, day, lw) }
    }.state()

    private fun build(s: com.lifeapi.app.vm.today.Snapshot, day: Long, lw: WeightLog?): TodayState {
        val p = s.p; val d = s.d
        val sc = s.score(day)
        val past = ((day - 30) until day).map { s.score(it) }.filter { it.hasData }
        val delta = if (past.size >= 3) sc.score - past.map { it.score }.average().toInt() else null
        val now = LocalTime.now()
        val nowS = hhmm(now)
        val taken = d.takenIds(day)
        val doneHabits = d.doneHabitIds(day)
        val suppsToday = d.suppsOn(day)
        val doneTasks = s.taskDone.filter { it.day == day }.map(TaskDone::taskId).toSet()

        val up = buildList {
            d.nextDoses(day).forEach { add(UpNextItem("s${it.supp.id}@${it.dose.time}", it.dose.time, it.supp.name, it.dose.label, UpNextItem.SUPP, it.supp.id, false)) }
            s.tasks.filter { t -> t.occursOn(day) && (if (t.repeating) t.id !in doneTasks else t.doneTs == null) }.take(4).forEach { t ->
                add(UpNextItem("t${t.id}", t.time, t.title, listOfNotNull(if (t.top) "Top 3" else null, t.module.ifBlank { null }, if (t.repeating) "Repeats" else null).joinToString(" · ").ifEmpty { "Task" }, UpNextItem.TASK, t.id, false))
            }
            d.habits.filter { it.id !in doneHabits }.take(3).forEach { add(UpNextItem("h${it.id}", "", it.name, "Habit · not done yet", UpNextItem.HABIT, it.id, false)) }
        }.sortedBy { it.time.ifEmpty { "99" } }

        val ws = weekStart(day)
        val week = (0 until 7).map { i ->
            val dd = ws + i
            Days.date(dd).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH) to if (dd > day) null else s.score(dd).score
        }
        val weekVals = week.mapNotNull { it.second }.filter { it > 0 }

        var streak = 0
        var cur = if ((d.learning[day] ?: 0) > 0) day else day - 1
        while ((d.learning[cur] ?: 0) > 0) { streak++; cur-- }

        val date = Days.date(day)
        val greeting = when (now.hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }

        // project card: most recently active project with a next action
        val proj = s.projects.filter { it.nextAction.isNotBlank() }.maxByOrNull { it.nextSince } ?: s.projects.firstOrNull()
        val projectCard = proj?.let { pr ->
            val tasks = s.tasks.filter { it.projectId == pr.id && !it.repeating }
            ProjectCard(pr.id, pr.name, pr.nextAction, if (pr.nextSince > 0) (day - Days.dayOf(pr.nextSince)).toInt() else 0,
                if (tasks.isEmpty()) null else tasks.count { it.doneTs != null } * 100 / tasks.size)
        }

        val spent = d.spend[day] ?: 0
        val ms = date.withDayOfMonth(1).toEpochDay()
        val monthSpentBy = s.txns.filter { it.day >= ms && it.amountCents < 0 && it.toAccountId == null && it.category != "Transfer" }.groupBy { it.category }.mapValues { e -> -e.value.sumOf { it.amountCents } }
        val budgetLeft = if (s.budgets.isEmpty()) null else s.budgets.sumOf { it.limitCents - (monthSpentBy[it.category] ?: 0) }
        val money = MoneyCard(p.dailySpendCents - spent, p.dailySpendCents, if (s.accounts.isEmpty()) null else s.accounts.sumOf { it.balanceCents }, budgetLeft, p.hideBalances)

        val nextT = if (s.templates.isEmpty()) null else {
            val lastT = s.sessions.firstOrNull { it.templateId != null }
            val idx = s.templates.indexOfFirst { it.id == lastT?.templateId }
            s.templates[(idx + 1) % s.templates.size]
        }
        val weekSess = s.sessions.filter { it.day in ws..day }
        val weekIds = weekSess.map { it.id }.toSet()
        val workout = WorkoutCard(
            nextT?.name, nextT?.let { t -> s.templateEx.count { it.templateId == t.id } } ?: 0, s.active?.id,
            weekSess.size, s.sets.filter { it.sessionId in weekIds && !it.warmup }.sumOf { it.kg * it.reps }, s.sessions.any { it.day == day },
        )

        return TodayState(
            dateLabel = "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} · WEEK ${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}".uppercase(),
            greeting = greeting,
            name = p.name,
            score = sc,
            scoreDelta = delta,
            goalsHit = sc.hit,
            goalsTotal = sc.parts.size,
            kcal = (d.kcal[day] ?: 0L).toInt(),
            habitsDone = d.habitsDone(day),
            habitsTotal = d.habits.size,
            waterMl = (d.water[day] ?: 0L).toInt(),
            waterGoal = p.waterGoalMl,
            supplementsDue = d.dosesOn(day).count { it.pending && it.dose.time <= nowS },
            stackLeft = d.dosesOn(day).count { it.pending },
            nextMealSlot = com.lifeapi.app.data.mealSlotFor(now),
            upNext = up,
            sleepLast = d.sleep[day],
            sleepWeek = ((day - 6)..day).map { d.sleep[it]?.minutes ?: 0 },
            spentTodayCents = spent,
            dailyBudgetCents = p.dailySpendCents,
            learnStreak = streak,
            learnToday = (d.learning[day] ?: 0L).toInt(),
            learnGoal = p.learnGoalMin,
            week = week,
            todayIndex = (day - ws).toInt(),
            weekAvg = if (weekVals.isEmpty()) 0 else weekVals.average().toInt(),
            sleepGoalMin = p.sleepGoalMin,
            cards = p.cardLayout().filter { it.second }.map { it.first },
            tiles = p.tiles(),
            insight = InsightEngine.top(s),
            aiOn = s.p.aiOn(),
            project = projectCard,
            money = money,
            workout = workout,
            firstHabit = d.habits.firstOrNull { it.id !in doneHabits }?.let { it.id to it.name },
            lastWeight = lw?.kg,
        )
    }

    fun addWater(ml: Int) = act("Logged $ml ml water") { repo.addWater(ml) }

    fun takeSupplement(id: Long) = act(null) {
        val s = dao.supplementsNow().firstOrNull { it.id == id } ?: return@act null
        val u = repo.setTaken(s, today.value, true)
        c.snack("${s.name} marked as taken", u)
        null
    }

    fun takeAllDue() = act(null) {
        val day = today.value
        val anyDue = com.lifeapi.app.data.supps.SuppOps.slotsOn(repo, day).any { it.pending && it.dose.time <= hhmm(LocalTime.now()) }
        val (n, u) = com.lifeapi.app.data.supps.SuppOps.takePending(repo, null, day, dueOnly = anyDue)
        if (n == 0) { c.snack("Stack done for today"); return@act null }
        c.snack("Took $n dose${if (n == 1) "" else "s"}", u)
        null
    }

    fun doHabit(id: Long, name: String) = act("$name done") { repo.setHabit(id, today.value, true) }

    fun doTask(id: Long) = act(null) {
        val t = growDao.task(id).first() ?: return@act null
        val day = today.value
        if (t.repeating) {
            growDao.insertTaskDone(TaskDone(t.id, day, Days.now()))
            growDao.resetChecklist(t.id)
            c.snack("${t.title} done") { growDao.deleteTaskDone(t.id, day) }
        } else {
            growDao.upsertTask(t.copy(doneTs = Days.now()))
            c.snack("${t.title} done") { growDao.upsertTask(t); Unit }
        }
        null
    }

    fun logWeight(kg: Double) = act(null) {
        val day = today.value
        val prev = bodyDao.weights(day).first().firstOrNull { it.day == day }
        bodyDao.upsertWeight(WeightLog(day, kg, Days.now()))
        c.snack("Weight ${com.lifeapi.app.data.fmtKg(kg)} kg logged") { if (prev != null) bodyDao.upsertWeight(prev) else bodyDao.deleteWeight(day) }
        null
    }

    fun saveCards(layout: List<Pair<String, Boolean>>) = launch {
        val p = dao.profileNow() ?: Profile()
        dao.saveProfile(p.copy(todayCards = layoutString(layout)))
    }
}
