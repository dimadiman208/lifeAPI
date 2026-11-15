package com.lifeapi.app.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Hue {
    const val HEALTH = 0
    const val FUEL = 1
    const val MONEY = 2
    const val TRAIN = 3
    const val GROW = 4
    const val GAIN = 5
}

object Days {
    fun today(): Long = LocalDate.now().toEpochDay()
    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)
    fun now(): Long = System.currentTimeMillis()
    fun tsOf(day: Long, time: LocalTime): Long =
        LocalDateTime.of(date(day), time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun timeOf(ts: Long): LocalTime =
        java.time.Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalTime()
    fun dayOf(ts: Long): Long =
        java.time.Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
}

typealias Undo = suspend () -> Unit

class LifeRepository(val db: LifeDatabase) {
    val dao = db.dao()
    val body = db.body()
    val train = db.train()
    val money = db.money()
    val grow = db.grow()
    val food = db.food()

    suspend fun ensureProfile() {
        if (dao.profileNow() == null) dao.saveProfile(Profile())
    }

    // ---------------- water
    suspend fun addWater(ml: Int, day: Long = Days.today()): Undo {
        val id = dao.insertWater(WaterLog(day = day, ts = Days.now(), ml = ml))
        return { dao.deleteWater(id) }
    }

    // ---------------- supplements
    /** Supplement-level toggle: takes the next pending dose / clears the last taken one. See [com.lifeapi.app.data.supps.SuppOps]. */
    suspend fun setTaken(s: Supplement, day: Long, taken: Boolean): Undo =
        com.lifeapi.app.data.supps.SuppOps.setTaken(this, s, day, taken)

    /** Takes every pending dose of [list] on [day]. */
    suspend fun takeAll(list: List<Supplement>, day: Long): Undo =
        com.lifeapi.app.data.supps.SuppOps.takePending(this, list.map { it.id }.toSet(), day).second

    // ---------------- sleep
    suspend fun logSleep(day: Long, start: Long, end: Long): Undo {
        val prev = dao.sleepFor(day)
        dao.upsertSleep(SleepLog(id = prev?.id ?: 0, day = day, startTs = start, endTs = end))
        return { if (prev != null) dao.upsertSleep(prev) else dao.deleteSleep(day) }
    }

    // ---------------- meals
    suspend fun addMeal(m: Meal): Undo {
        val id = dao.insertMeal(m)
        return { dao.deleteMeal(m.copy(id = id)) }
    }

    // ---------------- money
    private suspend fun snapshotNetWorth() {
        val total = dao.accountsNow().sumOf { it.balanceCents }
        dao.upsertNetWorth(NetWorthPoint(Days.today(), total))
    }

    suspend fun addTxn(t: Txn): Undo {
        val id = db.withTransaction {
            val id = dao.insertTxn(t)
            t.accountId?.let { dao.adjustBalance(it, t.amountCents) }
            id
        }
        snapshotNetWorth()
        return { deleteTxn(t.copy(id = id)) }
    }

    suspend fun deleteTxn(t: Txn) {
        db.withTransaction {
            dao.deleteTxn(t)
            t.accountId?.let { dao.adjustBalance(it, -t.amountCents) }
        }
        snapshotNetWorth()
    }

    suspend fun saveAccount(a: Account) {
        if (a.id == 0L) dao.insertAccount(a) else dao.updateAccount(a)
        snapshotNetWorth()
    }

    suspend fun deleteAccount(a: Account) {
        dao.deleteAccount(a)
        snapshotNetWorth()
    }

    suspend fun defaultAccountId(): Long? = dao.accountsNow().firstOrNull()?.id

    // ---------------- training
    suspend fun saveTemplate(id: Long?, name: String, exercises: List<TemplateExercise>) {
        db.withTransaction {
            val tid = if (id == null || id == 0L) dao.insertTemplate(WorkoutTemplate(name = name)) else {
                dao.renameTemplate(id, name); id
            }
            dao.deleteTemplateExercises(tid)
            dao.insertTemplateExercises(exercises.mapIndexed { i, e -> e.copy(id = 0, templateId = tid, sort = i) })
        }
    }

    suspend fun deleteTemplate(id: Long) {
        db.withTransaction {
            dao.deleteTemplateExercises(id)
            dao.deleteTemplate(id)
        }
    }

    /** Returns the active session id, creating one if needed. */
    suspend fun startSession(template: WorkoutTemplate?): Long {
        dao.activeSessionNow()?.let { return it.id }
        return dao.insertSession(
            WorkoutSession(
                templateId = template?.id,
                name = template?.name ?: "Workout",
                day = Days.today(),
                startTs = Days.now(),
            )
        )
    }

    suspend fun finishSession(s: WorkoutSession, hasSets: Boolean) {
        if (!hasSets) {
            dao.deleteSession(s.id)
        } else {
            dao.updateSession(s.copy(endTs = Days.now()))
        }
    }

    suspend fun discardSession(id: Long) {
        db.withTransaction {
            dao.deleteSetsFor(id)
            dao.deleteSession(id)
        }
    }

    /** A set typed into Quick Log: goes into the active session, or a one-off "Logged sets" session. */
    suspend fun logLooseSet(exercise: String, kg: Double, reps: Int): Undo {
        var createdSession: Long? = null
        val sid = dao.activeSessionNow()?.id ?: run {
            val now = Days.now()
            dao.insertSession(
                WorkoutSession(templateId = null, name = "Logged sets", day = Days.today(), startTs = now, endTs = now)
            ).also { createdSession = it }
        }
        val id = dao.insertSet(WorkoutSet(sessionId = sid, exercise = exercise, exIndex = 0, kg = kg, reps = reps, ts = Days.now()))
        return {
            dao.deleteSet(id)
            createdSession?.let { dao.deleteSession(it) }
        }
    }

    // ---------------- grow
    suspend fun setHabit(habitId: Long, day: Long, done: Boolean): Undo {
        return if (done) {
            dao.insertCheck(HabitCheck(habitId, day))
            ({ dao.deleteCheck(habitId, day) })
        } else {
            dao.deleteCheck(habitId, day)
            ({ dao.insertCheck(HabitCheck(habitId, day)) })
        }
    }

    /** Quick learning log (Quick Log "topic 15m"). v8: goes into the Growth learning journal. */
    suspend fun addLearning(topic: String, minutes: Int, day: Long = Days.today()): Undo {
        val g = db.growth()
        val id = g.insertLearning(LearningEntry(day = day, ts = Days.now(), learned = topic, minutes = minutes))
        return { g.deleteLearningById(id) }
    }

    suspend fun remember(text: String, label: String, value: String, hue: Int) {
        dao.upsertRecent(RecentLog(text = text, ts = Days.now(), label = label, value = value, hue = hue))
    }
}
