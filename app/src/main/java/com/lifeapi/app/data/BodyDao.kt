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
interface BodyDao {
    // weight
    @Query("SELECT * FROM weight WHERE day >= :from ORDER BY day")
    fun weights(from: Long): Flow<List<WeightLog>>

    @Query("SELECT * FROM weight ORDER BY day DESC LIMIT 1")
    fun lastWeight(): Flow<WeightLog?>

    @Upsert
    suspend fun upsertWeight(w: WeightLog)

    @Query("DELETE FROM weight WHERE day = :day")
    suspend fun deleteWeight(day: Long)

    // water edit
    @Query("UPDATE water SET ml = :ml WHERE id = :id")
    suspend fun updateWaterMl(id: Long, ml: Int)

    @Insert
    suspend fun insertWaterRow(w: WaterLog): Long

    // intakes for one supplement
    @Query("SELECT * FROM intake WHERE supplementId = :sid ORDER BY day DESC")
    fun intakesFor(sid: Long): Flow<List<Intake>>

    @Upsert
    suspend fun upsertIntake(i: Intake)

    @Query("SELECT * FROM supplement WHERE id = :id")
    fun supplement(id: Long): Flow<Supplement?>

    @Query("UPDATE supplement SET stock = stock - 1 WHERE id = :id AND stock IS NOT NULL AND stock > 0")
    suspend fun decStock(id: Long)

    @Query("UPDATE supplement SET stock = stock + 1 WHERE id = :id AND stock IS NOT NULL")
    suspend fun incStock(id: Long)

    // sleep
    @Query("SELECT * FROM sleep WHERE day = :day")
    fun sleepDay(day: Long): Flow<SleepLog?>

    // foods
    @Query("SELECT * FROM food ORDER BY lastUsed DESC, name")
    fun foods(): Flow<List<Food>>

    @Upsert
    suspend fun upsertFood(f: Food): Long

    @Delete
    suspend fun deleteFood(f: Food)

    @Query("SELECT * FROM meal WHERE day BETWEEN :from AND :to ORDER BY ts")
    fun mealsRange(from: Long, to: Long): Flow<List<Meal>>

    @Query("SELECT * FROM intake WHERE supplementId = :sid AND day = :day")
    suspend fun intakeOn(sid: Long, day: Long): Intake?

    @Query("SELECT * FROM water WHERE id = :id")
    suspend fun waterRow(id: Long): WaterLog?

    @Query("SELECT * FROM weight WHERE day = :day")
    suspend fun weightOn(day: Long): WeightLog?

    @Query("SELECT * FROM sleep WHERE day BETWEEN :from AND :to ORDER BY day")
    fun sleepRange(from: Long, to: Long): Flow<List<SleepLog>>

    // ---------------- supplements v3: rules + per-dose intakes
    @Query("SELECT * FROM supp_rule ORDER BY supplementId, base DESC, sort, id")
    fun rules(): Flow<List<SuppRule>>

    @Query("SELECT * FROM supp_rule ORDER BY supplementId, base DESC, sort, id")
    suspend fun rulesNow(): List<SuppRule>

    @Query("SELECT * FROM supp_rule WHERE supplementId = :sid ORDER BY base DESC, sort, id")
    fun rulesFor(sid: Long): Flow<List<SuppRule>>

    @Query("DELETE FROM supp_rule WHERE supplementId = :sid")
    suspend fun deleteRulesFor(sid: Long)

    @Insert
    suspend fun insertRules(r: List<SuppRule>)

    @Query("SELECT * FROM intake WHERE day = :day")
    suspend fun intakesOnDay(day: Long): List<Intake>

    @Query("SELECT * FROM intake WHERE supplementId = :sid AND day = :day AND doseKey = :key")
    suspend fun intakeDose(sid: Long, day: Long, key: String): Intake?

    @Query("DELETE FROM intake WHERE id = :id")
    suspend fun deleteIntakeById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putIntake(i: Intake): Long

    @Query("SELECT stock FROM supplement WHERE id = :id")
    suspend fun stockOf(id: Long): Int?

    @Query("UPDATE supplement SET stock = :stock WHERE id = :id")
    suspend fun setStock(id: Long, stock: Int?)

    @Query("SELECT * FROM supplement WHERE archived = 0 ORDER BY slot, time, sort, id")
    suspend fun activeSupplementsNow(): List<Supplement>
}
