package com.lifeapi.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Nutrition v4: product catalogue + diary edits. */
@Dao
interface FoodDao {
    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun food(id: Long): Food?

    @Query("SELECT * FROM food WHERE id = :id")
    fun foodFlow(id: Long): Flow<Food?>

    @Query("SELECT * FROM food WHERE extId = :extId LIMIT 1")
    suspend fun byExtId(extId: String): Food?

    @Query("SELECT * FROM food WHERE barcode = :code AND barcode != '' ORDER BY CASE source WHEN 'Custom' THEN 0 ELSE 1 END, id LIMIT 1")
    suspend fun byBarcode(code: String): Food?

    /** Local search: name or brand contains every word handled by the caller; here a single LIKE pattern. */
    @Query("SELECT * FROM food WHERE name LIKE :pattern OR brand LIKE :pattern OR barcode = :raw ORDER BY favorite DESC, useCount DESC, lastUsed DESC LIMIT 40")
    suspend fun search(pattern: String, raw: String): List<Food>

    @Query("SELECT * FROM food WHERE favorite = 1 ORDER BY name")
    fun favorites(): Flow<List<Food>>

    @Query("SELECT * FROM food WHERE lastUsed > 0 ORDER BY lastUsed DESC LIMIT 30")
    fun recents(): Flow<List<Food>>

    @Query("SELECT * FROM food WHERE source = 'Custom' ORDER BY name")
    fun custom(): Flow<List<Food>>

    @Insert
    suspend fun insert(f: Food): Long

    @Update
    suspend fun update(f: Food)

    @Delete
    suspend fun delete(f: Food)

    @Query("UPDATE food SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE food SET useCount = useCount + 1, lastUsed = :ts WHERE id = :id")
    suspend fun markUsed(id: Long, ts: Long)

    // ---- diary
    @Query("SELECT * FROM meal WHERE id = :id")
    suspend fun meal(id: Long): Meal?

    @Update
    suspend fun updateMeal(m: Meal)

    @Insert
    suspend fun insertMeals(m: List<Meal>): List<Long>

    @Query("SELECT * FROM meal WHERE day = :day AND slot = :slot ORDER BY ts")
    suspend fun mealsOf(day: Long, slot: String): List<Meal>

    @Query("SELECT COUNT(*) FROM meal WHERE foodId = :foodId")
    suspend fun usesOf(foodId: Long): Int
}
