package com.lifeapi.app.vm

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.TemplateExercise
import com.lifeapi.app.data.WorkoutSession
import com.lifeapi.app.data.WorkoutSet
import com.lifeapi.app.data.WorkoutTemplate
import com.lifeapi.app.data.train.compactSets
import com.lifeapi.app.ui.screens.body.DateCell
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import java.time.format.TextStyle
import java.util.Locale

data class TemplateWithEx(val t: WorkoutTemplate, val ex: List<TemplateExercise>, val lastDay: Long? = null, val today: Boolean = false)

data class PrRow(val lift: String, val date: String, val best: String, val isNew: Boolean, val e1rm: Double)

/** A workout on the selected calendar day: one line per exercise. */
data class DaySession(val s: WorkoutSession, val inProgress: Boolean, val meta: String, val lines: List<Pair<String, String>>)

data class TrainState(
    val templates: List<TemplateWithEx>,
    val next: TemplateWithEx?,
    val nextLastVolume: Double?,
    val prs: List<PrRow>,
    val active: WorkoutSession?,
    val nextScheduled: Boolean = false,
    val lastFinished: WorkoutSession? = null,
    // calendar (same component as Body)
    val calMode: Int = 0,
    val calLabel: String = "",
    val cal: List<DateCell> = emptyList(),
    val selected: Long = 0,
    val selectedLabel: String = "",
    val isToday: Boolean = true,
    val daySessions: List<DaySession> = emptyList(),
    val hasAnyWorkout: Boolean = false,
)

fun fmtTonnes(kg: Double): String = if (kg >= 1000) String.format(Locale.US, "%.1f t", kg / 1000) else String.format(Locale.US, "%,.0f kg", kg)

fun List<WorkoutSet>.volume(): Double = filter { !it.warmup }.sumOf { it.kg * it.reps }

@OptIn(ExperimentalCoroutinesApi::class)
class TrainVM(app: Application) : BaseVM(app) {
    private val selectedDay = MutableStateFlow<Long?>(null)
    /** Any day inside the displayed week / month; null = the selected day's period. */
    private val anchor = MutableStateFlow<Long?>(null)
    val calMode = MutableStateFlow(0)

    fun select(day: Long) { selectedDay.value = if (day == today.value) null else day; anchor.value = null }
    fun selectToday() = select(today.value)
    fun setCalMode(m: Int) { calMode.value = m; anchor.value = null }

    /** ‹ › — whole calendar weeks or months; lands on today if it's in the new period, otherwise its first day. */
    fun page(delta: Int) {
        val t = today.value
        val base = anchor.value ?: (selectedDay.value ?: t)
        val next = if (calMode.value == 0) base + 7L * delta
        else Days.date(base).withDayOfMonth(1).plusMonths(delta.toLong()).toEpochDay()
        anchor.value = next
        val (from, to) = periodOf(next, calMode.value)
        val target = if (t in from..to) t else from
        selectedDay.value = if (target == t) null else target
    }

    private fun periodOf(d: Long, mode: Int): Pair<Long, Long> =
        if (mode == 0) weekStart(d).let { it to it + 6 }
        else Days.date(d).withDayOfMonth(1).let { m -> m.toEpochDay() to m.withDayOfMonth(m.lengthOfMonth()).toEpochDay() }

    private data class Key(val day: Long, val sel: Long, val anchor: Long, val mode: Int)

    val state = combine(today, selectedDay, anchor, calMode) { t, s, a, m -> Key(t, s ?: t, a ?: (s ?: t), m) }.flatMapLatest { key ->
        combine(
            dao.templates(),
            dao.templateExercises(),
            dao.finishedSessions(),
            dao.allSets(),
            dao.activeSession(),
        ) { templates, exs, sessions, sets, active -> build(key, templates, exs, sessions, sets, active) }
    }.state()

    private fun build(
        key: Key, templates: List<WorkoutTemplate>, exs: List<TemplateExercise>,
        sessions: List<WorkoutSession>, sets: List<WorkoutSet>, active: WorkoutSession?,
    ): TrainState {
        val day = key.day
        val date = Days.date(day)
        val todayBit = 1 shl (date.dayOfWeek.value - 1)
        val twe = templates.map { t ->
            TemplateWithEx(t, exs.filter { it.templateId == t.id }, sessions.firstOrNull { it.templateId == t.id }?.day, t.days and todayBit != 0)
        }
        val lastTemplated = sessions.firstOrNull { it.templateId != null }
        val doneToday = sessions.filter { it.day == day }.mapNotNull { it.templateId }.toSet()
        val scheduled = twe.firstOrNull { it.today && it.t.id !in doneToday }
        val next = scheduled ?: if (twe.isEmpty()) null else {
            val idx = twe.indexOfFirst { it.t.id == lastTemplated?.templateId }
            twe[(idx + 1) % twe.size]
        }
        val setsBySession = sets.groupBy { it.sessionId }
        val finishedIds = sessions.map { it.id }.toSet()
        val nextLast = next?.let { n -> sessions.firstOrNull { it.templateId == n.t.id }?.let { setsBySession[it.id]?.volume() } }

        // PRs (saved workouts + the open one)
        val work = sets.filter { !it.warmup && it.kg > 0 && (it.sessionId in finishedIds || it.sessionId == active?.id) }
        val prs = work.groupBy { it.exercise }.map { (lift, list) ->
            val best = list.maxBy { it.e1rm }
            val bestDay = Days.dayOf(best.ts)
            val before = list.filter { Days.dayOf(it.ts) < bestDay }.maxOfOrNull { it.e1rm }
            val isNew = day - bestDay <= 7 && before != null && best.e1rm > before
            val bd = Days.date(bestDay)
            val gain = if (isNew) String.format(Locale.US, " · +%.1f e1RM", best.e1rm - before!!) else ""
            PrRow(
                lift,
                (if (day - bestDay < 7) "${bd.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} " else "") +
                    "${bd.dayOfMonth} ${bd.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}$gain",
                "${com.lifeapi.app.data.fmtKg(best.kg)} × ${best.reps}",
                isNew,
                best.e1rm,
            )
        }.sortedByDescending { it.e1rm }.take(6)

        // calendar: per-day volume vs your typical session
        val all = sessions + listOfNotNull(active)
        val volByDay = all.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { s -> setsBySession[s.id].orEmpty().volume() } }
            .filterValues { it > 0 }
        val typical = volByDay.values.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
        val (calFrom, calTo) = periodOf(key.anchor, key.mode)
        fun cell(d: Long) = DateCell(
            day = d, n = Days.date(d).dayOfMonth, isToday = d == day, future = d > day, selected = d == key.sel,
            frac = volByDay[d]?.let { v -> if (typical > 0) (v / typical).toFloat().coerceIn(0.15f, 1f) else 1f },
            perfect = volByDay[d]?.let { typical > 0 && it >= typical } ?: false,
        )
        val cells = if (key.mode == 0) (calFrom..calFrom + 6).map(::cell) else buildList {
            repeat(Days.date(calFrom).dayOfWeek.value - 1) { add(DateCell(0, 0, false, false, false, null)) }
            for (d in calFrom..calTo) add(cell(d))
        }

        // the selected day's workouts
        val daySessions = all.filter { it.day == key.sel }.sortedBy { it.startTs }.mapNotNull { s ->
            val ss = setsBySession[s.id].orEmpty().filter { !it.warmup }.sortedBy { it.ts }
            if (ss.isEmpty() && s.id != active?.id) return@mapNotNull null
            val byEx = ss.groupBy { it.exercise }.entries.sortedBy { e -> e.value.minOf { it.ts } }
            val prCount = prs.count { pr -> pr.isNew && ss.any { it.exercise == pr.lift && it.e1rm == pr.e1rm } }
            DaySession(
                s, s.id == active?.id,
                listOfNotNull(
                    "${ss.size} set${if (ss.size == 1) "" else "s"}",
                    String.format(Locale.US, "%,.0f kg", ss.volume()),
                    if (prCount > 0) "$prCount PR" else null,
                ).joinToString(" · "),
                byEx.map { (n, l) -> n to compactSets(l) },
            )
        }

        return TrainState(
            templates = twe,
            next = next,
            nextLastVolume = nextLast,
            prs = prs,
            active = active,
            nextScheduled = scheduled != null,
            lastFinished = sessions.firstOrNull { setsBySession[it.id].orEmpty().isNotEmpty() },
            calMode = key.mode,
            calLabel = calLabel(calFrom, key.mode, day),
            cal = cells,
            selected = key.sel,
            selectedLabel = com.lifeapi.app.vm.supps.relDay(key.sel, day),
            isToday = key.sel == day,
            daySessions = daySessions,
            hasAnyWorkout = volByDay.isNotEmpty(),
        )
    }

    private fun mon(d: Long) = Days.date(d).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    private fun calLabel(from: Long, mode: Int, t: Long): String = if (mode == 0) {
        val a = Days.date(from); val b = Days.date(from + 6)
        if (a.month == b.month) "${a.dayOfMonth}–${b.dayOfMonth} ${mon(from)}" else "${a.dayOfMonth} ${mon(from)} – ${b.dayOfMonth} ${mon(from + 6)}"
    } else Days.date(from).let { it.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + if (it.year != Days.date(t).year) " ${it.year}" else "" }

    init { launch { com.lifeapi.app.data.train.ExerciseCatalog.ensureSeeded(trainDao) } }

    fun saveTemplate(id: Long?, name: String, ex: List<TemplateExercise>, days: Int? = null) = launch {
        repo.saveTemplate(id, name, ex)
        if (days != null) {
            val tid = id?.takeIf { it != 0L } ?: dao.templates().first().filter { it.name == name }.maxOfOrNull { it.id }
            if (tid != null) trainDao.setTemplateDays(tid, days)
        }
        ex.forEach { trainDao.insertExercise(com.lifeapi.app.data.Exercise(it.name, createdTs = Days.now())) }
    }

    fun setDays(id: Long, days: Int) = launch { trainDao.setTemplateDays(id, days) }

    /** Turn the last finished workout into a template: one row per exercise, sets = count, top kg, most common reps. */
    fun saveLastAsTemplate(name: String) = launch {
        val s = trainDao.lastFinishedSession() ?: return@launch
        val sets = trainDao.setsForNow(s.id).filter { !it.warmup }
        val ex = sets.groupBy { it.exercise }.entries.sortedBy { e -> e.value.minOf { it.ts } }.map { (n, l) ->
            TemplateExercise(
                templateId = 0, name = n, sets = l.size,
                reps = l.groupingBy { it.reps }.eachCount().maxBy { it.value }.key,
                kg = l.maxOf { it.kg },
            )
        }
        if (ex.isEmpty()) return@launch
        repo.saveTemplate(null, name, ex)
        c.snack("Saved “$name” · ${ex.size} exercises")
    }

    fun deleteTemplate(id: Long) = launch { repo.deleteTemplate(id) }

    fun start(t: WorkoutTemplate?, onStarted: (Long) -> Unit) = launch {
        val id = repo.startSession(t)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onStarted(id) }
    }

    /** Long-press delete of a saved workout from the day list, with Undo. */
    fun deleteSession(s: WorkoutSession) = act("${s.name} deleted") { com.lifeapi.app.data.train.SessionOps.delete(repo, s.id) }
}
