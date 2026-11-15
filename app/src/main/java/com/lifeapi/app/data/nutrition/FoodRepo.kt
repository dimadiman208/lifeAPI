package com.lifeapi.app.data.nutrition

import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Food
import com.lifeapi.app.data.LifeRepository
import com.lifeapi.app.data.Meal
import com.lifeapi.app.data.Undo
import com.lifeapi.app.data.fmtKg
import java.time.LocalTime
import kotlin.math.roundToInt

/** Meal.source values for entries logged from products (FatSecret *diary* rows keep "FatSecret"). */
object MealSource {
    const val MANUAL = "Manual"
    const val OFF = "OFF"
    const val FS_FOOD = "FS food"

    fun of(food: Food) = when (food.source) {
        "OFF" -> OFF
        "FatSecret" -> FS_FOOD
        else -> MANUAL
    }

    fun label(source: String) = when (source) {
        OFF -> "Open Food Facts"
        FS_FOOD -> "FatSecret food"
        "FatSecret" -> "FatSecret diary"
        "" -> MANUAL
        else -> source
    }
}

data class Macros(val kcal: Int, val p: Int, val c: Int, val f: Int)

fun Food.macrosFor(grams: Double): Macros {
    val k = grams / 100.0
    return Macros((kcal100 * k).roundToInt(), (p100 * k).roundToInt(), (c100 * k).roundToInt(), (f100 * k).roundToInt())
}

fun Food.title(): String = if (brand.isBlank() || name.contains(brand, ignoreCase = true)) name else "$name · $brand"

fun fmtGrams(g: Double): String = fmtKg(g)

/**
 * Nutrition diary helpers. Room is the source of truth (Meal rows); entries arrive from the FatSecret diary sync,
 * older manual/product entries can still be edited, copied or deleted.
 */
class FoodRepo(private val repo: LifeRepository) {
    private val dao = repo.food

    // ------------------------------------------------------------- diary

    /** Edit an entry: product-based entries recalculate from grams; others take the given numbers. */
    suspend fun update(old: Meal, new: Meal): Undo {
        dao.updateMeal(new)
        return { dao.updateMeal(old) }
    }

    suspend fun copy(meals: List<Meal>, day: Long, slot: String?): Undo {
        val copies = meals.map { m ->
            val s = slot ?: m.slot
            m.copy(id = 0, day = day, slot = s, ts = tsFor(day, s), extId = null, source = if (m.source == "FatSecret") MealSource.MANUAL else m.source)
        }
        val ids = dao.insertMeals(copies)
        return { copies.zip(ids).forEach { (m, id) -> repo.dao.deleteMeal(m.copy(id = id)) } }
    }

    suspend fun mealsOf(day: Long, slot: String) = dao.mealsOf(day, slot)

    suspend fun food(id: Long) = dao.food(id)

    companion object {
        fun tsFor(day: Long, slot: String): Long =
            if (day == Days.today()) Days.now() else Days.tsOf(day, when (slot) {
                "Breakfast" -> LocalTime.of(8, 30); "Lunch" -> LocalTime.of(13, 0); "Snack" -> LocalTime.of(16, 30); else -> LocalTime.of(19, 30)
            })
    }
}
