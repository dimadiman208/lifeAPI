package com.lifeapi.app.vm.body

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Intake
import com.lifeapi.app.data.Profile
import com.lifeapi.app.data.SleepLog
import com.lifeapi.app.data.SuppRule
import com.lifeapi.app.data.Supplement
import com.lifeapi.app.data.WaterLog
import com.lifeapi.app.data.WeightLog
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.data.supps.DayProgress
import com.lifeapi.app.data.supps.SuppSchedule
import com.lifeapi.app.data.supps.hhmmOf
import com.lifeapi.app.vm.parseQuick
import com.lifeapi.app.vm.supps.SuppActionsVM
import com.lifeapi.app.vm.supps.SuppGroup
import com.lifeapi.app.vm.supps.groupSlots
import com.lifeapi.app.vm.supps.relDay
import com.lifeapi.app.vm.weekStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** One calendar day for the supplements week strip / month grid. [n] = 0 → padding cell. */
data class CalDay(val day: Long, val n: Int, val p: DayProgress, val isToday: Boolean, val future: Boolean, val selected: Boolean)

data class HealthTab(
    val today: Long,
    val selected: Long,
    val selectedLabel: String,
    val now: String,
    // ---- supplements
    val anySupplements: Boolean,
    val groups: List<SuppGroup>,
    val progress: DayProgress,
    val streak: Int,
    /** 0 week, 1 month */
    val calMode: Int,
    val calLabel: String,
    val week: List<CalDay>,
    val month: List<CalDay>,
    // ---- water
    val water: List<WaterLog>,
    val waterTotal: Int,
    val waterGoal: Int,
    val waterQuick: List<Int>,
    // ---- sleep
    /** The night that ended on the selected day. */
    val sleep: SleepLog?,
    val sleepRecent: List<SleepLog>,
    val sleepGoal: Int,
    // ---- weight
    val weightOnDay: WeightLog?,
    val weightRecent: List<WeightLog>,
    val lastWeight: WeightLog?,
    val profile: Profile,
) {
    val isToday get() = selected == today
    val isFuture get() = selected > today
}

private fun mon(d: Long) = Days.date(d).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

/**
 * The Body → Health tab: one selected date shared by Supplements, Water, Sleep and Weight, plus the supplements
 * week / month calendar. Analytics live in [BodyAnalyticsVM].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BodyHealthVM(app: Application) : SuppActionsVM(app) {
    /** null = follow today (so the tab re-selects today after midnight / on reopen). */
    private val picked = MutableStateFlow<Long?>(null)
    /** Any day inside the displayed week / month; null = the selected day's period. */
    private val anchor = MutableStateFlow<Long?>(null)
    val calMode = MutableStateFlow(0)

    fun select(day: Long) {
        picked.value = if (day == today.value) null else day
        anchor.value = null
    }
    fun selectToday() = select(today.value)
    fun setCalMode(m: Int) { calMode.value = m; anchor.value = null }

    /** ‹ › — whole calendar weeks or months. Keeps the selection unless it falls outside, then selects into the new period. */
    fun page(delta: Int) {
        val t = today.value
        val sel = picked.value ?: t
        val base = anchor.value ?: sel
        val next = if (calMode.value == 0) base + 7L * delta
        else Days.date(base).withDayOfMonth(1).plusMonths(delta.toLong()).toEpochDay()
        anchor.value = next
        // Land on today if it's in the new period, otherwise the period's first day.
        val (from, to) = periodOf(next, calMode.value)
        val target = if (t in from..to) t else from
        picked.value = if (target == t) null else target
    }

    private fun periodOf(d: Long, mode: Int): Pair<Long, Long> =
        if (mode == 0) weekStart(d).let { it to it + 6 }
        else Days.date(d).withDayOfMonth(1).let { m -> m.toEpochDay() to m.withDayOfMonth(m.lengthOfMonth()).toEpochDay() }

    private data class Key(val t: Long, val sel: Long, val anchor: Long, val mode: Int)

    private val supps = combine(dao.supplements(), bodyDao.rules()) { a, b -> a to b }

    val state = combine(today, picked, anchor, calMode) { t, p, a, m -> Key(t, p ?: t, a ?: (p ?: t), m) }.flatMapLatest { k ->
        val (from, to) = periodOf(k.anchor, k.mode)
        val suppFlow = combine(supps, dao.intakesRange(minOf(from, k.sel, k.t - 400), maxOf(to, k.sel, k.t))) { (s, r), i -> Triple(s, r, i) }
        val dayFlow = combine(dao.water(k.sel), bodyDao.sleepRange(minOf(k.t - 60, k.sel), maxOf(k.t, k.sel)), bodyDao.weights(minOf(k.t - 400, k.sel)), profile) { w, sl, wt, p ->
            DayData(w, sl, wt, p)
        }
        combine(suppFlow, dayFlow) { (s, r, i), d -> build(k, from, to, s, r, i, d) }
    }.state()

    private data class DayData(val water: List<WaterLog>, val sleep: List<SleepLog>, val weights: List<WeightLog>, val p: Profile)

    private fun build(k: Key, from: Long, to: Long, supps: List<Supplement>, rules: List<SuppRule>, intakes: List<Intake>, d: DayData): HealthTab {
        val by = intakes.groupBy { it.day }
        fun prog(day: Long) = SuppSchedule.progress(SuppSchedule.resolve(supps, rules, if (day > k.t) emptyList() else by[day].orEmpty(), day))
        fun cell(day: Long) = CalDay(day, Days.date(day).dayOfMonth, prog(day), day == k.t, day > k.t, day == k.sel)
        val ws = if (k.mode == 0) from else weekStart(k.sel)
        val week = (0 until 7).map { cell(ws + it) }
        val month = if (k.mode == 1) buildList {
            repeat(Days.date(from).dayOfWeek.value - 1) { add(CalDay(0, 0, DayProgress(0, 0, 0), false, false, false)) }
            for (x in from..to) add(cell(x))
        } else emptyList()
        val label = if (k.mode == 0) {
            val a = Days.date(from); val b = Days.date(from + 6)
            if (a.month == b.month) "${a.dayOfMonth}–${b.dayOfMonth} ${mon(from)}" else "${a.dayOfMonth} ${mon(from)} – ${b.dayOfMonth} ${mon(from + 6)}"
        } else Days.date(from).let { it.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + if (it.year != Days.date(k.t).year) " ${it.year}" else "" }

        val slots = SuppSchedule.resolve(supps, rules, by[k.sel].orEmpty(), k.sel)
        val first = intakes.minOfOrNull { it.day } ?: k.t
        val weightsDesc = d.weights.sortedByDescending { it.day }
        return HealthTab(
            today = k.t, selected = k.sel, selectedLabel = relDay(k.sel, k.t), now = hhmmOf(LocalTime.now()),
            anySupplements = supps.isNotEmpty(),
            groups = groupSlots(slots), progress = SuppSchedule.progress(slots),
            streak = SuppSchedule.streak(supps, rules, intakes, k.t, first),
            calMode = k.mode, calLabel = label, week = week, month = month,
            water = d.water.sortedByDescending { it.ts }, waterTotal = d.water.sumOf { it.ml },
            waterGoal = d.p.waterGoalMl, waterQuick = parseQuick(d.p.waterQuick),
            sleep = d.sleep.firstOrNull { it.day == k.sel },
            sleepRecent = d.sleep.filter { it.day <= k.t }.sortedByDescending { it.day }.take(5),
            sleepGoal = d.p.sleepGoalMin,
            weightOnDay = d.weights.firstOrNull { it.day == k.sel },
            weightRecent = weightsDesc.take(10), lastWeight = weightsDesc.firstOrNull(), profile = d.p,
        )
    }

    // ================================================================ water
    private fun tsFor(day: Long, fallback: LocalTime = LocalTime.NOON): Long =
        if (day == Days.today()) Days.now() else Days.tsOf(day, fallback)

    fun addWater(ml: Int, day: Long) {
        if (day > today.value) return
        act("Logged $ml ml water") {
            val id = dao.insertWater(WaterLog(day = day, ts = tsFor(day), ml = ml))
            suspend { dao.deleteWater(id) }
        }
    }

    fun deleteWater(w: WaterLog) = act("Deleted ${w.ml} ml") {
        dao.deleteWater(w.id)
        suspend { bodyDao.insertWaterRow(w); Unit }
    }

    fun editWater(w: WaterLog, ml: Int) = act("Changed to $ml ml") {
        bodyDao.updateWaterMl(w.id, ml)
        suspend { bodyDao.updateWaterMl(w.id, w.ml) }
    }

    fun saveWaterSettings(p: Profile, amounts: List<Int>, goal: Int) = launch {
        dao.saveProfile(p.copy(waterQuick = amounts.joinToString(","), waterGoalMl = goal))
    }

    // ================================================================ sleep
    /** [day] = the morning of waking. Bed after wake (e.g. 23:30 → 07:00) means bed was the evening before. */
    fun saveSleep(day: Long, bed: LocalTime, wake: LocalTime, rating: Int?, tags: Set<String>) = act("Sleep saved") {
        val end = Days.tsOf(day, wake)
        val start = Days.tsOf(if (bed >= wake) day - 1 else day, bed)
        val prev = dao.sleepFor(day)
        dao.upsertSleep(SleepLog(id = prev?.id ?: 0, day = day, startTs = start, endTs = end, rating = rating, tags = tags.joinToString(",")))
        suspend { if (prev != null) dao.upsertSleep(prev) else dao.deleteSleep(day) }
    }

    fun deleteSleep(s: SleepLog) = act("Sleep entry deleted") {
        dao.deleteSleep(s.day)
        suspend { dao.upsertSleep(s) }
    }

    // ================================================================ weight
    fun saveWeight(day: Long, kg: Double) = act("Weight · ${fmtKg(kg)} kg") {
        val prev = bodyDao.weightOn(day)
        bodyDao.upsertWeight(WeightLog(day, kg, tsFor(day, LocalTime.of(8, 0))))
        suspend { if (prev != null) bodyDao.upsertWeight(prev) else bodyDao.deleteWeight(day) }
    }

    fun deleteWeight(w: WeightLog) = act("Deleted ${fmtKg(w.kg)} kg") {
        bodyDao.deleteWeight(w.day)
        suspend { bodyDao.upsertWeight(w) }
    }

    fun saveProfile(p: Profile) = launch { dao.saveProfile(p) }

    // ================================================================ supplements
    fun addStarterStack() = launch {
        fun r(vararg d: com.lifeapi.app.data.supps.Dose) =
            SuppRule(supplementId = 0, days = 127, base = true, doses = com.lifeapi.app.data.supps.DoseCodec.encode(d.toList()))
        listOf(
            Triple(Supplement(name = "Vitamin D3 + K2", dose = "", slot = 0, time = "08:00", withFood = true), listOf(r(com.lifeapi.app.data.supps.Dose("08:00", 2000.0, "IU"))), "with breakfast"),
            Triple(Supplement(name = "Omega-3", dose = "", slot = 0, time = "08:00", withFood = true), listOf(r(com.lifeapi.app.data.supps.Dose("08:00", 2.0, "caps"))), "EPA/DHA"),
            Triple(Supplement(name = "Creatine monohydrate", dose = "", slot = 0, time = "08:10"), listOf(r(com.lifeapi.app.data.supps.Dose("08:10", 5.0, "g"))), ""),
            Triple(Supplement(name = "Magnesium glycinate", dose = "", slot = 1, time = "20:00"), listOf(r(com.lifeapi.app.data.supps.Dose("20:00", 400.0, "mg"))), ""),
        ).forEach { (s, rules, note) -> com.lifeapi.app.data.supps.SuppOps.saveSupplement(repo, s, rules, note) }
    }

    val allSupplements = dao.supplements()
}
