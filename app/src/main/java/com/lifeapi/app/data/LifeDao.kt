package com.lifeapi.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class DaySum(val day: Long, val total: Long)
data class HabitDayCount(val day: Long, val count: Int)

@Dao
interface LifeDao {
    // ---------- profile
    @Query("SELECT * FROM profile WHERE id = 0")
    fun profile(): Flow<Profile?>

    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun profileNow(): Profile?

    @Upsert
    suspend fun saveProfile(p: Profile)

    // ---------- water
    @Query("SELECT * FROM water WHERE day = :day ORDER BY ts")
    fun water(day: Long): Flow<List<WaterLog>>

    @Query("SELECT day, SUM(ml) AS total FROM water WHERE day BETWEEN :from AND :to GROUP BY day")
    fun waterByDay(from: Long, to: Long): Flow<List<DaySum>>

    @Insert
    suspend fun insertWater(w: WaterLog): Long

    @Query("DELETE FROM water WHERE id = :id")
    suspend fun deleteWater(id: Long)

    // ---------- supplements
    @Query("SELECT * FROM supplement WHERE archived = 0 ORDER BY slot, time, sort, id")
    fun supplements(): Flow<List<Supplement>>

    @Query("SELECT * FROM supplement WHERE archived = 0")
    suspend fun supplementsNow(): List<Supplement>

    @Insert
    suspend fun insertSupplement(s: Supplement): Long

    @Update
    suspend fun updateSupplement(s: Supplement)

    @Query("SELECT * FROM intake WHERE day = :day")
    fun intakes(day: Long): Flow<List<Intake>>

    @Query("SELECT * FROM intake WHERE day BETWEEN :from AND :to")
    fun intakesRange(from: Long, to: Long): Flow<List<Intake>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIntake(i: Intake): Long

    @Query("DELETE FROM intake WHERE supplementId = :sid AND day = :day")
    suspend fun deleteIntake(sid: Long, day: Long)

    // ---------- sleep
    @Query("SELECT * FROM sleep WHERE day BETWEEN :from AND :to ORDER BY day")
    fun sleepRange(from: Long, to: Long): Flow<List<SleepLog>>

    @Query("SELECT * FROM sleep ORDER BY day")
    suspend fun allSleep(): List<SleepLog>

    @Upsert
    suspend fun upsertSleep(s: SleepLog)

    @Query("SELECT * FROM sleep WHERE day = :day")
    suspend fun sleepFor(day: Long): SleepLog?

    @Query("DELETE FROM sleep WHERE day = :day")
    suspend fun deleteSleep(day: Long)

    // ---------- meals
    @Query("SELECT * FROM meal WHERE day = :day ORDER BY ts")
    fun meals(day: Long): Flow<List<Meal>>

    @Query("SELECT day, SUM(kcal) AS total FROM meal WHERE day BETWEEN :from AND :to GROUP BY day")
    fun kcalByDay(from: Long, to: Long): Flow<List<DaySum>>

    @Query("SELECT day, SUM(protein) AS total FROM meal WHERE day BETWEEN :from AND :to GROUP BY day")
    fun proteinByDay(from: Long, to: Long): Flow<List<DaySum>>

    @Query("SELECT * FROM meal WHERE slot = :slot ORDER BY ts DESC LIMIT 30")
    fun mealsForSlot(slot: String): Flow<List<Meal>>

    @Query("SELECT * FROM meal ORDER BY ts DESC LIMIT 40")
    suspend fun recentMeals(): List<Meal>

    @Insert
    suspend fun insertMeal(m: Meal): Long

    @Delete
    suspend fun deleteMeal(m: Meal)

    // ---------- money
    @Query("SELECT * FROM account ORDER BY sort, id")
    fun accounts(): Flow<List<Account>>

    @Query("SELECT * FROM account ORDER BY sort, id")
    suspend fun accountsNow(): List<Account>

    @Insert
    suspend fun insertAccount(a: Account): Long

    @Update
    suspend fun updateAccount(a: Account)

    @Delete
    suspend fun deleteAccount(a: Account)

    @Query("UPDATE account SET balanceCents = balanceCents + :delta WHERE id = :id")
    suspend fun adjustBalance(id: Long, delta: Long)

    @Query("SELECT * FROM txn ORDER BY ts DESC LIMIT :limit")
    fun recentTxns(limit: Int): Flow<List<Txn>>

    @Query("SELECT * FROM txn WHERE day BETWEEN :from AND :to ORDER BY ts DESC")
    fun txnsRange(from: Long, to: Long): Flow<List<Txn>>

    @Insert
    suspend fun insertTxn(t: Txn): Long

    @Delete
    suspend fun deleteTxn(t: Txn)

    @Query("SELECT * FROM budget ORDER BY category")
    fun budgets(): Flow<List<Budget>>

    @Upsert
    suspend fun upsertBudget(b: Budget)

    @Query("DELETE FROM budget WHERE category = :category")
    suspend fun deleteBudget(category: String)

    @Query("SELECT * FROM networth WHERE day >= :from ORDER BY day")
    fun netWorth(from: Long): Flow<List<NetWorthPoint>>

    @Upsert
    suspend fun upsertNetWorth(p: NetWorthPoint)

    // ---------- training
    @Query("SELECT * FROM template ORDER BY sort, id")
    fun templates(): Flow<List<WorkoutTemplate>>

    @Query("SELECT * FROM template_ex ORDER BY templateId, sort, id")
    fun templateExercises(): Flow<List<TemplateExercise>>

    @Query("SELECT * FROM template_ex WHERE templateId = :tid ORDER BY sort, id")
    suspend fun exercisesFor(tid: Long): List<TemplateExercise>

    @Insert
    suspend fun insertTemplate(t: WorkoutTemplate): Long

    @Query("DELETE FROM template WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Insert
    suspend fun insertTemplateExercises(e: List<TemplateExercise>)

    @Query("DELETE FROM template_ex WHERE templateId = :tid")
    suspend fun deleteTemplateExercises(tid: Long)

    @Query("UPDATE template SET name = :name WHERE id = :id")
    suspend fun renameTemplate(id: Long, name: String)

    @Query("SELECT * FROM session WHERE endTs IS NOT NULL ORDER BY startTs DESC")
    fun finishedSessions(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM session WHERE endTs IS NULL ORDER BY startTs DESC LIMIT 1")
    fun activeSession(): Flow<WorkoutSession?>

    @Query("SELECT * FROM session WHERE id = :id")
    fun session(id: Long): Flow<WorkoutSession?>

    @Query("SELECT * FROM session WHERE endTs IS NULL ORDER BY startTs DESC LIMIT 1")
    suspend fun activeSessionNow(): WorkoutSession?

    @Insert
    suspend fun insertSession(s: WorkoutSession): Long

    @Update
    suspend fun updateSession(s: WorkoutSession)

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM wset ORDER BY ts")
    fun allSets(): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM wset WHERE sessionId = :sid ORDER BY exIndex, ts")
    fun setsFor(sid: Long): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM wset WHERE exercise = :exercise AND sessionId != :exceptSession ORDER BY ts DESC LIMIT 12")
    suspend fun previousSets(exercise: String, exceptSession: Long): List<WorkoutSet>

    @Insert
    suspend fun insertSet(s: WorkoutSet): Long

    @Query("DELETE FROM wset WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("DELETE FROM wset WHERE sessionId = :sid")
    suspend fun deleteSetsFor(sid: Long)

    // ---------- grow
    @Query("SELECT * FROM habit WHERE archived = 0 ORDER BY sort, id")
    fun habits(): Flow<List<Habit>>

    @Query("SELECT * FROM habit WHERE archived = 0 ORDER BY sort, id")
    suspend fun habitsNow(): List<Habit>

    @Insert
    suspend fun insertHabit(h: Habit): Long

    @Update
    suspend fun updateHabit(h: Habit)

    @Query("SELECT * FROM habit_check WHERE day BETWEEN :from AND :to")
    fun habitChecks(from: Long, to: Long): Flow<List<HabitCheck>>

    @Query("SELECT * FROM habit_check")
    suspend fun allHabitChecks(): List<HabitCheck>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheck(c: HabitCheck)

    @Query("DELETE FROM habit_check WHERE habitId = :hid AND day = :day")
    suspend fun deleteCheck(hid: Long, day: Long)

    @Query("SELECT * FROM learning WHERE day = :day ORDER BY ts")
    fun learning(day: Long): Flow<List<LearningLog>>

    // v8: learning time lives in learning_entry (old `learning` rows were copied there once)
    @Query("SELECT DISTINCT day FROM learning_entry ORDER BY day DESC")
    fun learningDays(): Flow<List<Long>>

    @Query("SELECT day, SUM(COALESCE(minutes, 0)) AS total FROM learning_entry WHERE day BETWEEN :from AND :to GROUP BY day")
    fun learningByDay(from: Long, to: Long): Flow<List<DaySum>>

    @Insert
    suspend fun insertLearning(l: LearningLog): Long

    @Delete
    suspend fun deleteLearning(l: LearningLog)

    @Query("SELECT * FROM goal ORDER BY sort, id")
    fun goals(): Flow<List<Goal>>

    @Insert
    suspend fun insertGoal(g: Goal): Long

    @Update
    suspend fun updateGoal(g: Goal)

    @Delete
    suspend fun deleteGoal(g: Goal)

    // ---------- quick log recents
    @Query("SELECT * FROM recent_log ORDER BY ts DESC LIMIT 4")
    fun recentLogs(): Flow<List<RecentLog>>

    @Upsert
    suspend fun upsertRecent(r: RecentLog)
}
