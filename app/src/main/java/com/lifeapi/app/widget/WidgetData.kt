package com.lifeapi.app.widget

import android.content.Context
import com.lifeapi.app.LifeApp
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.LifeRepository
import com.lifeapi.app.data.Profile
import com.lifeapi.app.data.Supplement
import com.lifeapi.app.data.Task
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.data.hhmm
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.temporal.IsoFields
import java.util.Locale

internal fun repoOf(context: Context): LifeRepository = (context.applicationContext as LifeApp).container.repo

private suspend fun LifeRepository.profileOrDefault(): Profile = dao.profileNow() ?: Profile()

// ------------------------------------------------------------------ day score

data class DayScoreData(val score: Int, val hit: Int, val total: Int, val body: Float, val fuel: Float, val grow: Float)

suspend fun loadDayScore(repo: LifeRepository): DayScoreData {
    val day = Days.today()
    val p = repo.profileOrDefault()
    val water = repo.dao.water(day).first().sumOf { it.ml }
    val waterP = if (p.waterGoalMl > 0) (water.toFloat() / p.waterGoalMl).coerceIn(0f, 1f) else 0f

    val doses = com.lifeapi.app.data.supps.SuppOps.slotsOn(repo, day)
    val suppP: Float? = if (doses.isEmpty()) null else doses.count { it.taken }.toFloat() / doses.size

    val kcal = repo.dao.meals(day).first().sumOf { it.kcal }
    val fuelP = if (p.kcalGoal > 0) (kcal.toFloat() / p.kcalGoal).coerceIn(0f, 1f) else 0f

    val habits = repo.dao.habitsNow()
    val checks = repo.dao.habitChecks(day, day).first().map { it.habitId }.toSet()
    val habitP: Float? = if (habits.isEmpty()) null else habits.count { it.id in checks }.toFloat() / habits.size

    val parts = listOfNotNull(waterP, suppP, fuelP, habitP)
    val score = (parts.average() * 100).toInt()
    val body = listOfNotNull(waterP, suppP).average().toFloat()
    return DayScoreData(score, parts.count { it >= 0.999f }, parts.size, body, fuelP, habitP ?: 0f)
}

// ------------------------------------------------------------------ supplements

/** One planned dose today. */
data class SuppItem(val s: Supplement, val taken: Boolean, val key: String, val label: String)
data class SuppData(val label: String, val items: List<SuppItem>, val takenToday: Int, val totalToday: Int, val time: String?, val empty: Boolean)

suspend fun loadSupps(repo: LifeRepository): SuppData {
    val day = Days.today()
    val all = repo.dao.supplementsNow()
    if (all.isEmpty()) return SuppData("SUPPLEMENTS", emptyList(), 0, 0, null, true)
    val slots = com.lifeapi.app.data.supps.SuppOps.slotsOn(repo, day)
    val groups = slots.groupBy { it.part }
    val nowPart = com.lifeapi.app.data.supps.DayPart.of(com.lifeapi.app.data.supps.hhmmOf(LocalTime.now()))
    // Current part of day; if it's all done, the next part with pending doses; else the earliest part with pending doses.
    val part = when {
        groups[nowPart]?.any { it.pending } == true -> nowPart
        else -> groups.keys.filter { it.ordinal > nowPart.ordinal && groups[it]!!.any { s -> s.pending } }.minByOrNull { it.ordinal }
            ?: groups.keys.filter { groups[it]!!.any { s -> s.pending } }.minByOrNull { it.ordinal }
            ?: groups.keys.firstOrNull { it == nowPart } ?: groups.keys.maxByOrNull { it.ordinal }
    }
    val list = part?.let { groups[it] }.orEmpty()
    return SuppData(
        label = part?.let { "${it.label.uppercase()} STACK" } ?: "SUPPLEMENTS",
        items = list.map { SuppItem(it.supp, it.taken, it.dose.time, it.dose.label) },
        takenToday = slots.count { it.taken },
        totalToday = slots.size,
        time = list.firstOrNull { it.pending }?.dose?.time ?: list.firstOrNull()?.dose?.time,
        empty = false,
    )
}

// ------------------------------------------------------------------ water

data class WaterData(val ml: Int, val goal: Int, val quick: List<Int>)

suspend fun loadWater(repo: LifeRepository): WaterData {
    val p = repo.profileOrDefault()
    val ml = repo.dao.water(Days.today()).first().sumOf { it.ml }
    val quick = p.waterQuick.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
    return WaterData(ml, p.waterGoalMl, if (quick.size >= 2) quick.take(2) else listOf(250, 500))
}

// ------------------------------------------------------------------ project

data class ProjectData(val id: Long, val name: String, val pct: Int, val next: String, val hue: Int, val focusMin: Int)

suspend fun loadProject(repo: LifeRepository): ProjectData? {
    val projects = repo.grow.projects().first()
    val p = projects.firstOrNull { it.nextAction.isNotBlank() } ?: return null
    val tasks = repo.grow.tasks().first().filter { it.projectId == p.id }
    val pct = if (tasks.isEmpty()) 0 else tasks.count { it.doneTs != null } * 100 / tasks.size
    return ProjectData(p.id, p.name, pct, p.nextAction, p.hue, repo.profileOrDefault().focusMin)
}

/** True when there is no project at all (vs. projects without a next action). */
suspend fun hasProjects(repo: LifeRepository): Boolean = repo.grow.projects().first().isNotEmpty()

// ------------------------------------------------------------------ tasks

data class TaskItem(val t: Task, val done: Boolean)
data class TasksData(val items: List<TaskItem>, val left: Int)

suspend fun loadTasks(repo: LifeRepository): TasksData {
    val day = Days.today()
    val done = repo.grow.taskDone(day, day).first().map { it.taskId }.toSet()
    val items = repo.grow.tasks().first()
        .filter { it.occursOn(day) }
        .map { TaskItem(it, if (it.repeating) it.id in done else it.doneTs != null) }
        .sortedBy { !it.done } // done first, as in the design (struck-through on top)
    return TasksData(items, items.count { !it.done })
}

// ------------------------------------------------------------------ workout

data class WorkoutData(val label: String, val meta: String, val week: List<Boolean?>, val hasTemplate: Boolean)

suspend fun loadWorkout(repo: LifeRepository): WorkoutData {
    val day = Days.today()
    val templates = repo.dao.templates().first()
    val exs = repo.dao.templateExercises().first()
    val sessions = repo.dao.finishedSessions().first()
    val active = repo.dao.activeSession().first()
    val dow = Days.date(day).dayOfWeek.value - 1
    val ws = day - dow
    val sessionDays = sessions.map { it.day }.toSet()
    val week = (0 until 7).map { i -> val d = ws + i; if (d > day) null else d in sessionDays }

    if (active != null) {
        return WorkoutData("NOW · ${active.name.uppercase(Locale.ROOT)}", "In progress · tap to open", week, true)
    }
    if (templates.isEmpty()) return WorkoutData("TRAIN", "No templates yet", week, false)
    val scheduled = templates.firstOrNull { it.days and (1 shl dow) != 0 }
    val t = scheduled ?: run {
        val last = sessions.firstOrNull { it.templateId != null }
        val idx = templates.indexOfFirst { it.id == last?.templateId }
        templates[(idx + 1) % templates.size]
    }
    val list = exs.filter { it.templateId == t.id }
    val sets = list.sumOf { it.sets }
    val doneToday = day in sessionDays
    val head = if (doneToday) "DONE TODAY" else if (scheduled != null) "TODAY" else "NEXT"
    return WorkoutData(
        "$head · ${t.name.uppercase(Locale.ROOT)}",
        "${list.size} ex · $sets sets",
        week, true,
    )
}

fun weekNumber(): Int = Days.date(Days.today()).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

// ------------------------------------------------------------------ finance

data class FinanceData(val left: String, val of: String, val spentFrac: Float, val netWorth: String, val deltaPct: String?, val deltaUp: Boolean, val over: Boolean)

suspend fun loadFinance(repo: LifeRepository): FinanceData {
    val day = Days.today()
    val p = repo.profileOrDefault()
    val spent = -repo.dao.txnsRange(day, day).first()
        .filter { it.amountCents < 0 && it.toAccountId == null && it.category != "Transfer" }
        .sumOf { it.amountCents }
    val left = p.dailySpendCents - spent
    val nw = repo.dao.accountsNow().sumOf { it.balanceCents }
    val hist = repo.dao.netWorth(day - 30).first()
    val base = hist.firstOrNull { it.day < day }?.cents
    val delta = if (base != null && base != 0L) (nw - base).toDouble() / kotlin.math.abs(base) * 100 else null
    val mask = p.hideBalances
    fun m(s: String) = if (mask) "€••••" else s
    return FinanceData(
        left = m(fmtCents(left.coerceAtLeast(0))),
        of = if (left < 0) "${if (mask) "€••" else fmtCents(-left)} over €${p.dailySpendCents / 100} daily" else "left of ${fmtCents(p.dailySpendCents).removeSuffix(".00")} daily",
        spentFrac = if (p.dailySpendCents > 0) (spent.toFloat() / p.dailySpendCents).coerceIn(0f, 1f) else 0f,
        netWorth = m(fmtCents(nw).substringBefore('.')),
        deltaPct = delta?.let { String.format(Locale.US, "%+.1f%%", it) },
        deltaUp = (delta ?: 0.0) >= 0,
        over = left < 0,
    )
}

fun nowLabel(): String = hhmm(LocalTime.now())
