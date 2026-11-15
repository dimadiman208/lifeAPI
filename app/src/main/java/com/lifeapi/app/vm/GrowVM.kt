package com.lifeapi.app.vm

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Goal
import com.lifeapi.app.data.Habit
import com.lifeapi.app.data.LearningLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import com.lifeapi.app.data.JournalEntry
import com.lifeapi.app.data.grow.GrowCalc
import com.lifeapi.app.vm.grow.GrowthCalc
import com.lifeapi.app.vm.grow.GrowthExtra
import com.lifeapi.app.vm.grow.JournalDay
import com.lifeapi.app.vm.grow.Suggestion

data class HabitRow(val h: Habit, val done: Boolean, val week: List<Boolean>, val meta: String)

data class Insight(val text: String, val highlight: String, val a: Float, val b: Float, val aLabel: String, val bLabel: String, val unit: String)

data class GrowState(
    val habits: List<HabitRow>,
    val doneCount: Int,
    val learning: List<LearningLog>,
    val learnToday: Int,
    val learnGoal: Int,
    val learnStreak: Int,
    val goals: List<Goal>,
    val heat: List<Float?>, // 26*7 cells, column-major (week, then Mon..Sun); null = future
    val heatMonths: List<String>,
    val bestStreak: Int,
    val loggedDays: Int,
    val insight: Insight?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GrowVM(app: Application) : BaseVM(app) {

    val state = today.flatMapLatest { day ->
        val ws = weekStart(day)
        val heatStart = ws - 25 * 7
        combine(
            dao.habits(),
            dao.habitChecks(heatStart, day),
            combine(dao.learning(day), dao.learningDays(), ::Pair),
            dao.goals(),
            combine(profile, dao.sleepRange(heatStart, day), ::Pair),
        ) { habits, checks, (learning, learnDays), goals, (p, sleep) ->
            val byDay = checks.groupBy { it.day }.mapValues { e -> e.value.map { it.habitId }.toSet() }
            val ids = habits.map { it.id }.toSet()

            val rows = habits.map { h ->
                val week = ((day - 6)..day).map { d -> byDay[d]?.contains(h.id) == true }
                var streak = 0
                var d = if (byDay[day]?.contains(h.id) == true) day else day - 1
                while (byDay[d]?.contains(h.id) == true) { streak++; d-- }
                val last7 = week.count { it }
                val meta = when {
                    streak >= 3 -> "$streak-day streak"
                    last7 > 0 -> "$last7 of last 7 days"
                    else -> "Start today"
                }
                HabitRow(h, byDay[day]?.contains(h.id) == true, week, meta)
            }

            val heat = (0 until 26 * 7).map { i ->
                val d = heatStart + i
                if (d > day) null else {
                    val n = byDay[d]?.count { it in ids } ?: 0
                    if (habits.isEmpty()) 0f else n.toFloat() / habits.size
                }
            }
            val months = listOf(0, 9, 18).map { w ->
                Days.date(heatStart + w * 7).month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
            }

            // best "all habits done" streak, counted on days with >= 1 check
            val activeDays = byDay.filterValues { s -> s.any { it in ids } }.keys.sorted()
            var best = 0; var run = 0; var prev = Long.MIN_VALUE
            activeDays.forEach { d -> run = if (d == prev + 1) run + 1 else 1; best = maxOf(best, run); prev = d }

            var lStreak = 0
            var cur = if (learnDays.firstOrNull() == day) day else day - 1
            val ls = learnDays.toSet()
            while (cur in ls) { lStreak++; cur-- }

            // Insight: habits done on days after 7h+ sleep vs. under
            val sleepMap = sleep.associateBy { it.day }
            val good = mutableListOf<Int>(); val bad = mutableListOf<Int>()
            for (d in heatStart..day) {
                val s = sleepMap[d] ?: continue
                val n = byDay[d]?.count { it in ids } ?: 0
                if (s.minutes >= 420) good += n else bad += n
            }
            val insight = if (good.size >= 3 && bad.size >= 3 && habits.isNotEmpty()) {
                val a = good.average().toFloat(); val b = bad.average().toFloat()
                val diff = if (b > 0) ((a - b) / b * 100).toInt() else 0
                Insight(
                    text = if (diff >= 0) "You complete %s habits on days you slept 7 h+." else "Interesting: you complete %s habits after shorter nights.",
                    highlight = "${kotlin.math.abs(diff)}% ${if (diff >= 0) "more" else "more"}",
                    a = a, b = b, aLabel = "Slept 7 h+", bLabel = "Under 7 h", unit = "habits",
                )
            } else null

            GrowState(
                habits = rows,
                doneCount = rows.count { it.done },
                learning = learning,
                learnToday = learning.sumOf { it.minutes },
                learnGoal = p.learnGoalMin,
                learnStreak = lStreak,
                goals = goals,
                heat = heat,
                heatMonths = months,
                bestStreak = best,
                loggedDays = byDay.keys.count { it > day - 7 },
                insight = insight,
            )
        }
    }.state()

    private data class GrowRaw(
        val tasks: List<com.lifeapi.app.data.Task>, val done: List<com.lifeapi.app.data.TaskDone>,
        val focus: List<com.lifeapi.app.data.FocusSession>, val journal: List<JournalEntry>,
        val learning: List<LearningLog>,
    )

    private val raw = combine(growDao.tasks(), growDao.allTaskDone(), growDao.allFocus(), growDao.allJournal(), growDao.allLearning(), ::GrowRaw)

    /** Journal, linked goals, skills and reviews. */
    val extra = today.flatMapLatest { day ->
        combine(
            raw,
            combine(growDao.allChecks(), growDao.allSets(), ::Pair),
            dao.goals(),
            dao.habits(),
            growDao.projects(),
        ) { r, (checks, sets), goals, habits, projects ->
            val events = GrowCalc.doneEvents(r.tasks, r.done)
            val jToday = r.journal.filter { it.day == day }
            val past = r.journal.filter { it.day < day && it.day >= day - 6 }.groupBy { it.day }.toSortedMap(compareByDescending { it })
                .map { (d, l) -> JournalDay(d, GrowCalc.dayLabel(d, day), l) }
            val pName = projects.associate { it.id to it.name }
            val logged = jToday.map { it.text.lowercase() }.toSet()
            val sugg = events.filter { it.day == day && it.task.title.lowercase() !in logged }
                .map { Suggestion(it.task.title, it.task.projectId?.let { id -> pName[id] } ?: it.task.module) }.toMutableList()
            val fToday = r.focus.filter { it.day == day }
            if (fToday.isNotEmpty()) {
                val t = "${fToday.size} focus session${if (fToday.size > 1) "s" else ""} (${fToday.sumOf { it.minutes }} min)"
                if (t.lowercase() !in logged) sugg += Suggestion(t, "")
            }
            val views = goals.map { GrowthCalc.goalView(it, day, checks, sets, events, r.learning, habits, projects) }
            GrowthExtra(
                journalToday = jToday,
                journalPast = past,
                suggestions = sugg,
                tags = (projects.map { it.name } + goals.map { it.name } + GrowthCalc.skills(r.learning, day).map { it.name }).distinct(),
                goals30 = views.filter { it.g.horizonDays < 60 },
                goals90 = views.filter { it.g.horizonDays >= 60 },
                skills = GrowthCalc.skills(r.learning, day),
                week = GrowthCalc.review(false, day, events, r.focus, r.learning, checks, r.journal, habits, sets),
                month = GrowthCalc.review(true, day, events, r.focus, r.learning, checks, r.journal, habits, sets),
                habits = habits,
                lifts = sets.map { it.exercise }.distinct().sorted(),
                projects = projects,
                learnTopics = r.learning.map { GrowthCalc.skillName(it.topic) }.distinct(),
            )
        }
    }.state()

    fun addJournal(text: String, tag: String) {
        if (text.isBlank()) return
        act("Added to journal") {
            val j = JournalEntry(day = today.value, ts = com.lifeapi.app.data.Days.now(), text = text.trim(), tag = tag)
            val id = growDao.insertJournal(j)
            suspend { growDao.deleteJournal(j.copy(id = id)) }
        }
    }

    fun addAll(list: List<Suggestion>) = act("Added ${list.size} to journal") {
        val ids = list.map { s ->
            val j = JournalEntry(day = today.value, ts = com.lifeapi.app.data.Days.now(), text = s.text, tag = s.tag)
            j.copy(id = growDao.insertJournal(j))
        }
        suspend { ids.forEach { growDao.deleteJournal(it) } }
    }

    fun deleteJournal(j: JournalEntry) = act("Entry removed") {
        growDao.deleteJournal(j)
        suspend { growDao.insertJournal(j); Unit }
    }

    fun toggleHabit(h: Habit, done: Boolean) = act(if (done) "${h.name} done" else "${h.name} unmarked") { repo.setHabit(h.id, today.value, done) }
    fun saveHabit(h: Habit) = launch { if (h.id == 0L) dao.insertHabit(h) else dao.updateHabit(h) }
    fun archiveHabit(h: Habit) = act("${h.name} removed") {
        dao.updateHabit(h.copy(archived = true))
        suspend { dao.updateHabit(h.copy(archived = false)) }
    }

    fun addLearning(topic: String, minutes: Int) = act("Logged ${minutes}m of $topic") { repo.addLearning(topic, minutes) }
    fun deleteLearning(l: LearningLog) = act("Removed") {
        dao.deleteLearning(l)
        suspend { dao.insertLearning(l); Unit }
    }

    fun saveGoal(g: Goal) = launch {
        if (g.id == 0L) dao.insertGoal(if (g.startDay == 0L) g.copy(startDay = today.value) else g) else dao.updateGoal(g)
    }
    fun deleteGoal(g: Goal) = act("Goal deleted") {
        dao.deleteGoal(g)
        suspend { dao.insertGoal(g); Unit }
    }

    fun setLearnGoal(min: Int) = launch {
        val p = dao.profileNow() ?: return@launch
        dao.saveProfile(p.copy(learnGoalMin = min))
    }

    fun addStarterHabits() = launch {
        listOf("Meditate 10 min", "Read 20 pages", "No phone before 9:00", "Evening journal", "10,000 steps")
            .forEachIndexed { i, n -> dao.insertHabit(Habit(name = n, sort = i)) }
    }
}
