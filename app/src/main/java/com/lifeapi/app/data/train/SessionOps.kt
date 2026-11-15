package com.lifeapi.app.data.train

import androidx.room.withTransaction
import com.lifeapi.app.data.LifeRepository
import com.lifeapi.app.data.Undo
import com.lifeapi.app.data.WorkoutSession

/** Edits on an already saved (finished) workout. Never touches endTs → the session can't become "active" again. */
object SessionOps {
    /** Deletes the session and all its sets; Undo re-inserts both with their original ids and timestamps. */
    suspend fun delete(repo: LifeRepository, id: Long): Undo? {
        val dao = repo.dao
        val s = repo.train.sessionNow(id) ?: return null
        val sets = repo.train.setsForNow(id)
        repo.discardSession(id)
        return {
            repo.db.withTransaction {
                dao.insertSession(s)
                sets.forEach { dao.insertSet(it) }
            }
        }
    }

    suspend fun rename(repo: LifeRepository, s: WorkoutSession, name: String) {
        val n = name.trim()
        if (n.isNotEmpty() && n != s.name) repo.dao.updateSession(s.copy(name = n))
    }

    /** Moves a saved workout to [newDay], keeping times of day (session start/end and every set). */
    suspend fun moveToDay(repo: LifeRepository, s: WorkoutSession, newDay: Long) {
        if (newDay == s.day) return
        val shift = (newDay - s.day) * 86_400_000L
        repo.db.withTransaction {
            repo.dao.updateSession(s.copy(day = newDay, startTs = s.startTs + shift, endTs = s.endTs?.plus(shift)))
            repo.train.setsForNow(s.id).forEach { repo.train.updateSet(it.copy(ts = it.ts + shift)) }
        }
    }
}
