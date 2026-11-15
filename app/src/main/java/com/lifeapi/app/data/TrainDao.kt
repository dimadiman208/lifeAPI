package com.lifeapi.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainDao {
    @Query("SELECT * FROM exercise ORDER BY name")
    fun exercises(): Flow<List<Exercise>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercise(e: Exercise)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(e: List<Exercise>)

    /** Exercise names by most recent use. */
    @Query("SELECT exercise FROM wset GROUP BY exercise ORDER BY MAX(ts) DESC")
    fun recentExerciseNames(): Flow<List<String>>

    @Query("SELECT * FROM wset WHERE exercise = :name ORDER BY ts")
    fun setsOf(name: String): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM session ORDER BY startTs DESC LIMIT 1")
    suspend fun lastSession(): WorkoutSession?

    @Query("SELECT * FROM wset WHERE sessionId = :sid ORDER BY exIndex, ts")
    suspend fun setsForNow(sid: Long): List<WorkoutSet>

    @Query("UPDATE template SET days = :days WHERE id = :id")
    suspend fun setTemplateDays(id: Long, days: Int)

    @Update
    suspend fun updateSet(s: WorkoutSet)

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun exerciseCount(): Int

    @Query("SELECT DISTINCT exercise FROM wset UNION SELECT DISTINCT name FROM template_ex")
    suspend fun usedExerciseNames(): List<String>

    @Query("SELECT * FROM session WHERE endTs IS NOT NULL ORDER BY startTs DESC LIMIT 1")
    suspend fun lastFinishedSession(): WorkoutSession?

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun sessionNow(id: Long): WorkoutSession?
}
