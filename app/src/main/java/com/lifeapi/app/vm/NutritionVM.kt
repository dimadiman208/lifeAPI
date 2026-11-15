package com.lifeapi.app.vm

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Meal
import com.lifeapi.app.data.Profile
import com.lifeapi.app.data.mealSlotFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

data class DayChip(val day: Long, val dow: String, val n: Int, val selected: Boolean, val future: Boolean, val isToday: Boolean, val hit: Boolean?)

data class NutritionState(
    val selected: Long,
    val isToday: Boolean,
    val days: List<DayChip>,
    val meals: List<Meal>,
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val profile: Profile,
    val week: List<Pair<String, Int>>,
    val todayIndex: Int,
    val avgKcal: Int,
    val avgProtein: Int,
    val usual: Meal?,
    val usualSlot: String,
    /** Slots that had entries the day before the selected day (for "Repeat yesterday"). */
    val yesterday: Map<String, List<Meal>> = emptyMap(),
    // ---- shared Body calendar (Week / Month)
    val calMode: Int = 0,
    val calLabel: String = "",
    val cal: List<com.lifeapi.app.ui.screens.body.DateCell> = emptyList(),
    val selectedLabel: String = "",
) {
    fun slot(name: String) = meals.filter { it.slot == name }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionVM(app: Application) : BaseVM(app) {
    init {
        com.lifeapi.app.data.fatsecret.FatSecret.init(app)
    }

    private val selected = MutableStateFlow<Long?>(null)
    /** Any day inside the displayed week / month; null = the selected day's period. */
    private val anchor = MutableStateFlow<Long?>(null)
    val calMode = MutableStateFlow(0)

    fun select(day: Long) { selected.value = if (day == today.value) null else day; anchor.value = null }
    fun selectToday() = select(today.value)
    fun setCalMode(m: Int) { calMode.value = m; anchor.value = null }

    /** ‹ › — whole calendar weeks or months; lands on today if it's in the new period, otherwise its first day. */
    fun page(delta: Int) {
        val t = today.value
        val base = anchor.value ?: (selected.value ?: t)
        val next = if (calMode.value == 0) base + 7L * delta
        else Days.date(base).withDayOfMonth(1).plusMonths(delta.toLong()).toEpochDay()
        anchor.value = next
        val (from, to) = periodOf(next, calMode.value)
        val target = if (t in from..to) t else from
        selected.value = if (target == t) null else target
    }

    private fun periodOf(d: Long, mode: Int): Pair<Long, Long> =
        if (mode == 0) weekStart(d).let { it to it + 6 }
        else Days.date(d).withDayOfMonth(1).let { m -> m.toEpochDay() to m.withDayOfMonth(m.lengthOfMonth()).toEpochDay() }

    private data class CalKey(val day: Long, val sel: Long, val anchor: Long, val mode: Int)

    val state = combine(today, selected, anchor, calMode) { t, s, a, m -> CalKey(t, s ?: t, a ?: (s ?: t), m) }.flatMapLatest { key ->
        val day = key.day; val sel = key.sel
        val (calFrom, calTo) = periodOf(key.anchor, key.mode)
        val ws = weekStart(day)
        val slotNow = mealSlotFor(LocalTime.now())
        combine(
            dao.meals(sel),
            dao.kcalByDay(minOf(ws, calFrom), maxOf(ws + 6, calTo)),
            dao.proteinByDay(ws, ws + 6),
            profile,
            combine(dao.mealsForSlot(slotNow), dao.meals(sel - 1)) { a, b -> a to b },
        ) { meals, kcalW, protW, p, slotPair ->
            val (slotMeals, yMeals) = slotPair
            val kmap = kcalW.toMap()
            val days = (0 until 7).map { i ->
                val d = ws + i
                val date = Days.date(d)
                val k = kmap[d]
                DayChip(
                    day = d,
                    dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    n = date.dayOfMonth,
                    selected = d == sel,
                    future = d > day,
                    isToday = d == day,
                    hit = k?.let { it >= p.kcalGoal * 0.9 && it <= p.kcalGoal * 1.1 },
                )
            }
            val logged = kcalW.filter { it.day in ws..day && it.total > 0 }
            val loggedP = protW.filter { it.day <= day && it.total > 0 }
            val usual = slotMeals.groupBy { it.items.lowercase() }.maxByOrNull { it.value.size }?.value?.first()
            val alreadyHasSlot = sel == day && meals.any { it.slot == slotNow }
            NutritionState(
                selected = sel,
                isToday = sel == day,
                days = days,
                meals = meals,
                kcal = meals.sumOf { it.kcal },
                protein = meals.sumOf { it.protein },
                carbs = meals.sumOf { it.carbs },
                fat = meals.sumOf { it.fat },
                profile = p,
                week = (0 until 7).map { i -> days[i].dow to (kmap[ws + i] ?: 0L).toInt() },
                todayIndex = (day - ws).toInt(),
                avgKcal = if (logged.isEmpty()) 0 else logged.map { it.total }.average().toInt(),
                avgProtein = if (loggedP.isEmpty()) 0 else loggedP.map { it.total }.average().toInt(),
                usual = if (sel == day && !alreadyHasSlot) usual else null,
                usualSlot = slotNow,
                yesterday = yMeals.groupBy { it.slot },
                calMode = key.mode,
                calLabel = calLabel(calFrom, key.mode, day),
                cal = calCells(calFrom, calTo, key.mode, day, sel, kmap, p.kcalGoal),
                selectedLabel = com.lifeapi.app.vm.supps.relDay(sel, day),
            )
        }
    }.state()

    fun addMeal(m: Meal) = act("Logged ${m.slot.lowercase()} · ${m.kcal} kcal") {
        val day = selected.value ?: today.value
        val ts = if (day == today.value) Days.now() else Days.tsOf(day, LocalTime.of(12, 0))
        repo.addMeal(m.copy(day = day, ts = ts))
    }

    fun logUsual(m: Meal) = addMeal(m.copy(id = 0, slot = mealSlotFor(LocalTime.now())))

    fun deleteMeal(m: Meal) = act("Meal deleted") {
        dao.deleteMeal(m)
        suspend { dao.insertMeal(m); Unit }
    }

    fun saveGoals(p: Profile) = act("Nutrition goals saved") {
        val old = dao.profileNow()
        dao.saveProfile(p)
        if (old != null) suspend { dao.saveProfile(old) } else null
    }

    private val foodRepo = com.lifeapi.app.data.nutrition.FoodRepo(repo)

    fun updateMeal(old: Meal, new: Meal) = act("Entry updated") { foodRepo.update(old, new) }

    /** Copy entries to another day/slot (slot null = keep each entry's slot). */
    fun copyMeals(meals: List<Meal>, day: Long, slot: String?) {
        val where = (if (day == today.value) "today" else Days.date(day).let { d -> "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" }) + (slot?.let { " · ${it.lowercase()}" } ?: "")
        act("Copied ${meals.size} ${if (meals.size == 1) "entry" else "entries"} to $where") { foodRepo.copy(meals, day, slot) }
    }

    suspend fun foodOf(m: Meal): com.lifeapi.app.data.Food? = m.foodId?.let { foodRepo.food(it) }

    val selectedDay: Long get() = selected.value ?: today.value

    private fun mon(d: Long) = Days.date(d).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    private fun calLabel(from: Long, mode: Int, t: Long): String = if (mode == 0) {
        val a = Days.date(from); val b = Days.date(from + 6)
        if (a.month == b.month) "${a.dayOfMonth}–${b.dayOfMonth} ${mon(from)}" else "${a.dayOfMonth} ${mon(from)} – ${b.dayOfMonth} ${mon(from + 6)}"
    } else Days.date(from).let { it.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + if (it.year != Days.date(t).year) " ${it.year}" else "" }

    /** Per-day indicator = kcal / goal (capped by the bar itself); null (no mark) when nothing was eaten / logged. */
    private fun calCells(from: Long, to: Long, mode: Int, t: Long, sel: Long, kcal: Map<Long, Long>, goal: Int): List<com.lifeapi.app.ui.screens.body.DateCell> {
        fun cell(d: Long) = com.lifeapi.app.ui.screens.body.DateCell(
            day = d, n = Days.date(d).dayOfMonth, isToday = d == t, future = d > t, selected = d == sel,
            frac = kcal[d]?.takeIf { it > 0 }?.let { (it.toFloat() / goal.coerceAtLeast(1)).coerceAtMost(1f) },
            perfect = kcal[d]?.let { goal > 0 && it >= goal * 0.9 && it <= goal * 1.1 } ?: false,
        )
        return if (mode == 0) (from..from + 6).map(::cell) else buildList {
            repeat(Days.date(from).dayOfWeek.value - 1) { add(com.lifeapi.app.ui.screens.body.DateCell(0, 0, false, false, false, null)) }
            for (d in from..to) add(cell(d))
        }
    }

}
