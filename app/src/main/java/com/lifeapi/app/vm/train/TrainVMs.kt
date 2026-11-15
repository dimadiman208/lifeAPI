package com.lifeapi.app.vm.train

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Exercise
import com.lifeapi.app.data.WorkoutSession
import com.lifeapi.app.data.WorkoutSet
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.data.train.ExerciseCatalog
import com.lifeapi.app.data.train.compactSets
import com.lifeapi.app.data.train.exerciseLine
import com.lifeapi.app.vm.BaseVM
import com.lifeapi.app.vm.volume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import java.time.format.TextStyle
import java.util.Locale

private fun shortDate(day: Long): String = Days.date(day).let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" }

// ===================================================================== picker

data class PickRow(val name: String, val meta: String, val recent: Boolean)

data class PickerState(val rows: List<PickRow>, val title: String, val canCreate: Boolean, val createName: String)

class ExercisePickerVM(app: Application) : BaseVM(app) {
    val query = MutableStateFlow("")
    val muscle = MutableStateFlow<String?>(null)
    val equipment = MutableStateFlow<String?>(null)

    init { launch { ExerciseCatalog.ensureSeeded(trainDao) } }

    private val filters = combine(query, muscle, equipment) { q, m, e -> Triple(q, m, e) }

    val state = combine(trainDao.exercises(), trainDao.recentExerciseNames(), dao.allSets(), filters) { lib, recent, sets, (q0, m, e) ->
        val q = q0.trim().lowercase()
        val bySession = sets.groupBy { it.exercise }
        fun meta(x: Exercise): String {
            val parts = mutableListOf<String>()
            if (x.muscle.isNotBlank()) parts += x.muscle
            val mine = bySession[x.name]
            if (mine != null) {
                val lastSid = mine.maxBy { it.ts }.sessionId
                val last = mine.filter { it.sessionId == lastSid }
                val top = last.maxBy { it.kg }
                parts += "last ${last.size}×${top.reps}" + if (top.kg > 0) " · ${fmtKg(top.kg)} kg" else ""
            } else if (x.createdTs > 0 && x.createdTs > Days.now() - 86_400_000L) parts += "new"
            else parts += x.equipment
            return parts.joinToString(" · ")
        }
        val recentRank = recent.withIndex().associate { it.value to it.index }
        val filtered = lib.filter { (m == null || it.muscle == m) && (e == null || it.equipment == e) }
        val words = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        val matches = if (q.isEmpty()) filtered else filtered.filter { x -> words.all { w -> x.name.lowercase().contains(w) } || x.muscle.lowercase() == q }
        val sorted = matches.sortedWith(compareBy<Exercise> { recentRank[it.name] ?: Int.MAX_VALUE }.thenBy { it.name })
        val shown = if (q.isEmpty() && m == null && e == null) {
            val r = sorted.filter { it.name in recentRank }
            (r + sorted.filter { it.name !in recentRank }).take(40)
        } else sorted.take(40)
        val exact = lib.any { it.name.equals(q0.trim(), ignoreCase = true) }
        PickerState(
            rows = shown.map { PickRow(it.name, meta(it), it.name in recentRank) },
            title = when {
                q.isEmpty() && m == null && e == null -> if (recent.isEmpty()) "LIBRARY" else "RECENT"
                matches.isEmpty() -> "NO MATCH"
                else -> "MATCHES"
            },
            canCreate = q.isNotEmpty() && !exact,
            createName = ExerciseCatalog.normalize(q0),
        )
    }.state()

    fun create(name: String, muscle: String, equipment: String, kind: String) = launch {
        trainDao.insertExercise(Exercise(ExerciseCatalog.normalize(name), muscle, equipment, kind, Days.now()))
    }
}

// ===================================================================== exercise history

data class ExSession(val day: Long, val date: String, val sets: String, val pr: Boolean, val e1rm: Double, val top: Double, val volume: Double, val sessionId: Long = 0)

data class ExHistoryState(
    val name: String,
    val e1rm: Double?,
    val e1rmDelta: Double?,
    val deltaLabel: String,
    val best: String?,
    val bestDate: String?,
    val sessions: Int,
    val since: String?,
    val list: List<ExSession>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseHistoryVM(app: Application) : BaseVM(app) {
    private val name = MutableStateFlow<String?>(null)
    fun bind(n: String) { name.value = n }

    val state = name.filterNotNull().flatMapLatest { n ->
        combine(trainDao.setsOf(n), dao.finishedSessions(), dao.activeSession()) { sets, finished, active ->
            val valid = finished.associateBy { it.id } + listOfNotNull(active).associateBy { it.id }
            val work = sets.filter { it.sessionId in valid && !it.warmup }
            val grouped = work.groupBy { it.sessionId }.entries
                .map { (sid, l) -> valid.getValue(sid) to l.sortedBy { it.ts } }
                .sortedBy { it.first.startTs }
            var bestSoFar = 0.0
            val rows = grouped.mapIndexed { i, (s, l) ->
                val e = l.maxOf { it.e1rm }
                val pr = i > 0 && e > bestSoFar + 1e-9
                bestSoFar = maxOf(bestSoFar, e)
                ExSession(s.day, shortDate(s.day), compactSets(l), pr, e, l.maxOf { it.kg }, l.volume(), s.id)
            }
            val bestSet = work.maxByOrNull { it.e1rm }
            val allBest = bestSet?.e1rm
            val cutoff = Days.now() - 84L * 86_400_000L
            val before = work.filter { it.ts < cutoff }.maxOfOrNull { it.e1rm }
            val first = rows.firstOrNull()
            val delta = when {
                allBest == null -> null
                before != null -> allBest - before
                rows.size > 1 -> allBest - first!!.e1rm
                else -> null
            }
            val firstDate = first?.let { Days.date(it.day) }
            ExHistoryState(
                name = n,
                e1rm = allBest,
                e1rmDelta = delta,
                deltaLabel = if (before != null) "12 wk" else "all time",
                best = bestSet?.let { "${fmtKg(it.kg)}×${it.reps}" },
                bestDate = bestSet?.let { shortDate(Days.dayOf(it.ts)) },
                sessions = rows.size,
                since = firstDate?.let {
                    val m = it.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    if (it.year == Days.date(Days.today()).year) "since $m" else "since $m ${it.year}"
                },
                list = rows.reversed(),
            )
        }
    }.state()
}

// ===================================================================== workout summary

data class SumExercise(val name: String, val line: String, val isNew: Boolean, val sets: List<WorkoutSet>)
data class SumPr(val name: String, val set: String, val e1rm: Double, val beats: Double?)

data class SummaryState(
    val session: WorkoutSession,
    val volume: Double,
    val volumeDelta: Double?,
    val sets: Int,
    val exercises: List<SumExercise>,
    val prs: List<SumPr>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSummaryVM(app: Application) : BaseVM(app) {
    private val id = MutableStateFlow<Long?>(null)
    fun bind(sid: Long) {
        if (id.value == sid) return
        id.value = sid
    }

    val state = id.filterNotNull().flatMapLatest { sid ->
        combine(dao.session(sid), dao.allSets(), dao.finishedSessions()) { s, all, finished ->
            if (s == null) return@combine null
            val mine = all.filter { it.sessionId == sid && !it.warmup }
            val others = all.filter { it.sessionId != sid && !it.warmup && it.ts < (mine.minOfOrNull { m -> m.ts } ?: Long.MAX_VALUE) }
            val byEx = mine.groupBy { it.exercise }.entries.sortedBy { e -> e.value.minOf { it.ts } }
            val exercises = byEx.map { (n, l) -> SumExercise(n, exerciseLine(l.sortedBy { it.ts }), others.none { it.exercise == n } && all.none { it.exercise == n && it.sessionId != sid }, l.sortedBy { it.ts }) }
            val prs = byEx.mapNotNull { (n, l) ->
                val prev = others.filter { it.exercise == n }.maxOfOrNull { it.e1rm } ?: return@mapNotNull null
                val best = l.maxBy { it.e1rm }
                if (best.e1rm > prev + 1e-9 && best.kg > 0) SumPr(n, "${fmtKg(best.kg)} × ${best.reps}", best.e1rm, prev) else null
            }
            val lastSame = s.templateId?.let { t -> finished.firstOrNull { it.templateId == t && it.id != sid } }
            val vol = mine.volume()
            val lastVol = lastSame?.let { ls -> all.filter { it.sessionId == ls.id }.volume() }
            SummaryState(s, vol, lastVol?.let { vol - it }, mine.size, exercises, prs)
        }
    }.state()

    fun save(onDone: () -> Unit) = launch {
        val s = id.value?.let { trainDao.sessionNow(it) } ?: return@launch
        val now = Days.now()
        val sets = state.value?.sets ?: 0
        if (sets == 0) {
            repo.discardSession(s.id)
            c.snack("Empty workout discarded")
        } else {
            dao.updateSession(s.copy(endTs = now, pausedAt = null))
            c.snack("Workout saved · $sets set${if (sets == 1) "" else "s"}")
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
    }

    /** Back to the open workout (nothing to restore — there's no clock). */
    fun resume(onDone: () -> Unit) = onDone()

    fun discard(onDone: () -> Unit) = launch {
        val sid = id.value ?: return@launch
        repo.discardSession(sid)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
    }

    fun deleteSet(set: WorkoutSet) = act("Set removed") {
        dao.deleteSet(set.id)
        suspend { dao.insertSet(set); Unit }
    }

    fun editSet(set: WorkoutSet, kg: Double, reps: Int) = launch { trainDao.updateSet(set.copy(kg = kg, reps = reps)) }
}

