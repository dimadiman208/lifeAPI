package com.lifeapi.app.vm

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.WorkoutSession
import com.lifeapi.app.data.WorkoutSet
import com.lifeapi.app.data.fmtKg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

data class ExPlan(val name: String, val sets: Int, val reps: Int, val kg: Double)

/**
 * One exercise in the open workout. Every row in [sets] is a real logged set (editable in place) —
 * there's no "complete set" step: adding an exercise creates its first set, "+ Add set" the next.
 */
data class ExBlock(
    val index: Int,
    val name: String,
    /** "Plan 4 × 6 @ 60 kg" for template exercises. */
    val target: String?,
    val sets: List<WorkoutSet>,
    /** "Last: 80×5 · 80×5" from the previous session with this exercise. */
    val last: String?,
    /** Best ever before this workout ("Best 92.5×3"), or "New PR 95×3" when this workout beats it. */
    val best: String?,
    val isPr: Boolean,
)

data class WorkoutState(
    val session: WorkoutSession,
    val blocks: List<ExBlock>,
    val totalSets: Int,
) {
    /** A saved workout opened for editing (vs. the open, in-progress one). */
    val isEdit: Boolean get() = session.endTs != null
    /** Kept for callers that only need the names in order. */
    val plan: List<ExPlan> get() = blocks.map { ExPlan(it.name, it.sets.size, it.sets.lastOrNull()?.reps ?: 0, it.sets.lastOrNull()?.kg ?: 0.0) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutVM(app: Application) : BaseVM(app) {
    private val sessionId = MutableStateFlow<Long?>(null)
    /** Exercises added this session that may (temporarily) have no sets. */
    private val extra = MutableStateFlow<List<String>>(emptyList())

    fun bind(id: Long) {
        if (sessionId.value == id) return
        sessionId.value = id
        extra.value = emptyList()
        launch { seedTemplate(id) }
    }

    /** A template workout opens with each planned exercise's first set already there (once per session). */
    private suspend fun seedTemplate(id: Long) {
        if (id in seeded) return
        seeded += id
        val s = dao.session(id).first() ?: return
        if (s.endTs != null) return // editing a saved workout: never re-seed
        val tid = s.templateId ?: return
        if (trainDao.setsForNow(id).isNotEmpty()) return
        dao.exercisesFor(tid).forEachIndexed { i, e ->
            val (kg, reps) = prefill(e.name, id) ?: (e.kg to e.reps)
            dao.insertSet(WorkoutSet(sessionId = id, exercise = e.name, exIndex = i, kg = kg, reps = reps, ts = Days.now() + i))
        }
    }

    /** First set of the last session that had [name] (excluding [exceptSession]). */
    private suspend fun prefill(name: String, exceptSession: Long): Pair<Double, Int>? {
        val prev = dao.previousSets(name, exceptSession)
        val lastSid = prev.firstOrNull()?.sessionId ?: return null
        val first = prev.filter { it.sessionId == lastSid }.minByOrNull { it.ts } ?: return null
        return first.kg to first.reps
    }

    val state = sessionId.filterNotNull().flatMapLatest { sid ->
        dao.session(sid).filterNotNull().flatMapLatest { s ->
            val planFlow = flow { emit(s.templateId?.let { dao.exercisesFor(it) }.orEmpty().map { ExPlan(it.name, it.sets, it.reps, it.kg) }) }
            combine(planFlow, dao.setsFor(sid), extra, dao.allSets()) { tpl, sets, ext, all -> build(s, tpl, sets, ext, all) }
        }
    }.state()

    private fun build(s: WorkoutSession, tpl: List<ExPlan>, sets: List<WorkoutSet>, ext: List<String>, all: List<WorkoutSet>): WorkoutState {
        val byTime = sets.sortedBy { it.ts }
        val names = (tpl.map { it.name } + byTime.map { it.exercise } + ext).distinct()
        val others = all.filter { it.sessionId != s.id && !it.warmup }
        val blocks = names.mapIndexed { i, n ->
            val mine = byTime.filter { it.exercise == n }
            val prevAll = others.filter { it.exercise == n }
            val lastSid = prevAll.maxByOrNull { it.ts }?.sessionId
            val lastSets = prevAll.filter { it.sessionId == lastSid }.sortedBy { it.ts }
            val bestBefore = prevAll.maxByOrNull { it.e1rm }
            val bestNow = mine.filter { it.kg > 0 }.maxByOrNull { it.e1rm }
            val isPr = bestBefore != null && bestNow != null && bestNow.e1rm > bestBefore.e1rm + 1e-9
            val p = tpl.firstOrNull { it.name == n }
            ExBlock(
                index = i,
                name = n,
                target = p?.let { "Plan ${it.sets} × ${it.reps}" + if (it.kg > 0) " @ ${fmtKg(it.kg)} kg" else "" },
                sets = mine,
                last = lastSets.takeIf { it.isNotEmpty() }?.let { l -> "Last: " + l.joinToString(" · ") { "${fmtKg(it.kg)}×${it.reps}" } },
                best = when {
                    isPr -> "New PR ${fmtKg(bestNow!!.kg)}×${bestNow.reps}"
                    bestBefore != null -> "Best ${fmtKg(bestBefore.kg)}×${bestBefore.reps}"
                    else -> null
                },
                isPr = isPr,
            )
        }
        return WorkoutState(s, blocks, sets.size)
    }

    /**
     * Timestamp for a new set: now for the open workout; for a saved one, just after its last set
     * (so it stays on that workout's day and in order).
     */
    private suspend fun newTs(sid: Long): Long {
        val s = trainDao.sessionNow(sid) ?: return Days.now()
        if (s.endTs == null) return Days.now()
        val last = trainDao.setsForNow(sid).maxOfOrNull { it.ts } ?: s.startTs
        return last + 1_000
    }

    /** Adds the exercise with its first set (prefilled from last time, else a neutral default). */
    fun addExercise(name: String) = launch {
        val n = com.lifeapi.app.data.train.ExerciseCatalog.normalize(name)
        val sid = sessionId.value ?: return@launch
        val st = state.value
        if (st?.blocks?.any { it.name == n } == true) return@launch
        if (n !in extra.value) extra.value = extra.value + n
        val (kg, reps) = prefill(n, sid) ?: (20.0 to 8)
        dao.insertSet(WorkoutSet(sessionId = sid, exercise = n, exIndex = st?.blocks?.size ?: 0, kg = kg, reps = reps, ts = newTs(sid)))
    }

    /** Removes the exercise and all its sets in this workout (Undo restores them). */
    fun removeExercise(name: String) = act("$name removed") {
        val st = state.value ?: return@act null
        val b = st.blocks.firstOrNull { it.name == name } ?: return@act null
        b.sets.forEach { dao.deleteSet(it.id) }
        extra.value = extra.value - name
        suspend { b.sets.forEach { dao.insertSet(it) }; Unit }
    }

    /** Next set = a copy of the previous one (or last time's first set). */
    fun addSet(b: ExBlock) = launch {
        val sid = sessionId.value ?: return@launch
        val src = b.sets.lastOrNull()
        val (kg, reps) = src?.let { it.kg to it.reps } ?: prefill(b.name, sid) ?: (20.0 to 8)
        dao.insertSet(WorkoutSet(sessionId = sid, exercise = b.name, exIndex = b.index, kg = kg, reps = reps, ts = newTs(sid)))
    }

    fun editSet(set: WorkoutSet, kg: Double, reps: Int) = launch {
        val k = kg.coerceIn(0.0, 1000.0); val r = reps.coerceIn(0, 999)
        if (k != set.kg || r != set.reps) trainDao.updateSet(set.copy(kg = k, reps = r))
    }

    fun deleteSet(set: WorkoutSet) = act("Set removed") {
        dao.deleteSet(set.id)
        suspend { dao.insertSet(set); Unit }
    }

    init { launch { com.lifeapi.app.data.train.ExerciseCatalog.ensureSeeded(trainDao) } }

    /** Empty workout → discarded right away (nothing to review). */
    fun finish(onDone: () -> Unit) = launch {
        val s = state.value ?: run { onDone(); return@launch }
        val hasSets = s.totalSets > 0
        repo.finishSession(s.session, hasSets)
        c.snack(if (hasSets) "Workout saved · ${s.totalSets} set${if (s.totalSets == 1) "" else "s"}" else "Empty workout discarded")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
    }

    fun discard(onDone: () -> Unit) = launch {
        val id = sessionId.value ?: return@launch
        repo.discardSession(id)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
    }

    // ---------------------------------------------------------------- saved workout edits

    fun rename(name: String) = launch {
        val s = state.value?.session ?: return@launch
        com.lifeapi.app.data.train.SessionOps.rename(repo, s, name)
    }

    fun moveToDay(day: Long) = launch {
        val s = state.value?.session ?: return@launch
        if (s.endTs == null) return@launch
        com.lifeapi.app.data.train.SessionOps.moveToDay(repo, s, day)
    }

    /** Deletes the saved workout (Undo in the snackbar restores it). */
    fun deleteWorkout(onDone: () -> Unit) = launch {
        val s = state.value?.session ?: return@launch
        val undo = com.lifeapi.app.data.train.SessionOps.delete(repo, s.id)
        c.snack("${s.name} deleted", undo)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
    }

    private companion object {
        /** Sessions already seeded from their template in this process (so deleting the seeded sets sticks). */
        val seeded = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
    }
}
