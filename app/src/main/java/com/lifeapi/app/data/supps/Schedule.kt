package com.lifeapi.app.data.supps

import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Intake
import com.lifeapi.app.data.SuppRule
import com.lifeapi.app.data.Supplement
import java.time.LocalTime
import java.util.Locale

/** One planned dose: time "HH:MM", amount of unit (e.g. 2 × "caps", 400 × "mg"). */
data class Dose(val time: String, val amount: Double, val unit: String) {
    val label: String get() = doseLabel(amount, unit)
    val localTime: LocalTime get() = parseTime(time) ?: LocalTime.of(8, 0)
}

fun parseTime(s: String): LocalTime? = runCatching {
    val (h, m) = s.trim().split(':').map { it.toInt() }
    LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59))
}.getOrNull()

fun fmtAmount(a: Double): String =
    if (a == Math.floor(a) && a < 1e7) a.toLong().toString() else String.format(Locale.US, "%.2f", a).trimEnd('0').trimEnd('.')

/** "400 mg", "2 caps", or a legacy free-text unit like "1 g · EPA/DHA". */
fun doseLabel(amount: Double, unit: String): String {
    val u = unit.trim()
    return when {
        u.isEmpty() -> fmtAmount(amount)
        u.any { it.isDigit() } -> if (amount == 1.0) u else "${fmtAmount(amount)} × $u"
        else -> "${fmtAmount(amount)} $u"
    }
}

private val THOUSANDS = Regex("""^\d{1,3}(,\d{3})+$""")
private val LEGACY = Regex("""^\s*([\d.,]+)\s*([A-Za-zµ]+)?\s*(?:·\s*.*)?$""")

/** Units counted out of a container — stock decrements by the amount. Others decrement stock by 1 serving. */
private val COUNT_UNITS = setOf("caps", "cap", "tab", "tabs", "pcs", "pc", "drops", "softgels", "scoop", "scoops")

fun stockUse(d: Dose): Int = if (d.unit.trim().lowercase() in COUNT_UNITS) Math.round(d.amount).toInt().coerceAtLeast(1) else 1

object DoseCodec {
    /** "HH:MM*amount*unit;…". Defensive: legacy units like "400 mg" with amount 1 become 400 × mg. */
    fun parse(s: String): List<Dose> = s.split(';').mapNotNull { part ->
        if (part.isBlank()) return@mapNotNull null
        val f = part.split('*')
        val time = parseTime(f.getOrNull(0) ?: "")?.let { hhmmOf(it) } ?: return@mapNotNull null
        var amount = f.getOrNull(1)?.replace(',', '.')?.trim()?.toDoubleOrNull() ?: 1.0
        var unit = f.drop(2).joinToString("*").trim()
        if (amount == 1.0) LEGACY.find(unit)?.let { m ->
            val raw = m.groupValues[1]
            val a = (if (THOUSANDS.matches(raw)) raw.replace(",", "") else raw.replace(',', '.')).toDoubleOrNull()
            val u = m.groupValues[2]
            if (a != null && u.isNotEmpty()) { amount = a; unit = normUnit(u) }
        }
        Dose(time, amount, unit)
    }.sortedBy { it.time }

    fun encode(doses: List<Dose>): String = doses.sortedBy { it.time }.joinToString(";") {
        "${it.time}*${fmtAmount(it.amount)}*${it.unit.replace(';', ',').replace('*', 'x')}"
    }

    private fun normUnit(u: String) = when (u.lowercase()) { "iu" -> "IU"; "cap" -> "caps"; "mg" -> "mg"; "g" -> "g"; "mcg" -> "mcg"; else -> u }
}

fun hhmmOf(t: LocalTime): String = String.format(Locale.US, "%02d:%02d", t.hour, t.minute)

/** Part of day for grouping. */
enum class DayPart(val label: String) { MORNING("Morning"), DAY("Day"), EVENING("Evening");
    companion object {
        fun of(time: String): DayPart {
            val h = parseTime(time)?.hour ?: 8
            return when { h < 12 -> MORNING; h < 17 -> DAY; else -> EVENING }
        }
    }
}

fun weekdayBit(day: Long): Int = 1 shl (Days.date(day).dayOfWeek.value - 1)

/** A dose planned on a specific day, resolved against that day's intakes. */
data class DoseSlot(val supp: Supplement, val dose: Dose, val intake: Intake?) {
    val key: String get() = dose.time
    val taken: Boolean get() = intake != null && !intake.skipped
    val skipped: Boolean get() = intake?.skipped == true
    val pending: Boolean get() = intake == null
    val part: DayPart get() = DayPart.of(dose.time)
    /** Amount actually taken (or planned). */
    val takenAmount: Double get() = intake?.amount?.takeIf { it > 0 } ?: dose.amount
}

data class DayProgress(val scheduled: Int, val taken: Int, val skipped: Int) {
    val pending: Int get() = scheduled - taken - skipped
    /** Every scheduled dose is taken or deliberately skipped. */
    val closed: Boolean get() = scheduled > 0 && pending <= 0
    val pct: Int get() = if (scheduled == 0) 0 else taken * 100 / scheduled
}

/**
 * The single source of truth for "what is scheduled when". Pure; give it all rules (any supplements) and it
 * picks the right ones.
 */
object SuppSchedule {
    /** The rule that applies to [s] on [day]: first extra rule covering the weekday, else the base rule if it covers it. */
    fun ruleOn(s: Supplement, rules: List<SuppRule>, day: Long): SuppRule? {
        val bit = weekdayBit(day)
        val mine = rules.filter { it.supplementId == s.id }
        mine.filter { !it.base }.sortedWith(compareBy({ it.sort }, { it.id })).firstOrNull { it.days and bit != 0 }?.let { return it }
        return mine.firstOrNull { it.base }?.takeIf { it.days and bit != 0 }
    }

    /** Doses of one supplement on a day. Falls back to the legacy single schedule when it has no rules at all. */
    fun dosesOn(s: Supplement, rules: List<SuppRule>, day: Long): List<Dose> {
        val mine = rules.any { it.supplementId == s.id }
        if (!mine) return if (s.onDay(day)) legacyDoses(s) else emptyList()
        return ruleOn(s, rules, day)?.let { DoseCodec.parse(it.doses) }.orEmpty()
    }

    /**
     * Resolve the day's plan against its intakes. Intakes match their dose by doseKey first; leftovers (legacy
     * keys, schedule changed after logging) fill the remaining doses of the same supplement in time order.
     */
    fun resolve(supps: List<Supplement>, rules: List<SuppRule>, intakes: List<Intake>, day: Long): List<DoseSlot> {
        val dayIntakes = intakes.filter { it.day == day }.groupBy { it.supplementId }
        return supps.flatMap { s ->
            val doses = dosesOn(s, rules, day)
            if (doses.isEmpty()) return@flatMap emptyList()
            val pool = dayIntakes[s.id].orEmpty().toMutableList()
            val matched = doses.map { d -> pool.firstOrNull { it.doseKey == d.time }?.also { pool.remove(it) } }.toMutableList()
            for (i in doses.indices) if (matched[i] == null && pool.isNotEmpty()) matched[i] = pool.removeAt(0)
            doses.mapIndexed { i, d -> DoseSlot(s, d, matched[i]) }
        }.sortedWith(compareBy({ it.dose.time }, { it.supp.sort }, { it.supp.id }))
    }

    fun legacyDoses(s: Supplement): List<Dose> =
        DoseCodec.parse("${s.time.ifBlank { "08:00" }}*1*${s.dose.replace(';', ',').replace('*', 'x')}")

    fun progress(slots: List<DoseSlot>) = DayProgress(slots.size, slots.count { it.taken }, slots.count { it.skipped })

    /** Streak of closed days (≥1 taken) ending today, or yesterday while today is open. Unscheduled days are transparent. */
    fun streak(supps: List<Supplement>, rules: List<SuppRule>, intakes: List<Intake>, today: Long, firstDay: Long): Int {
        if (supps.isEmpty()) return 0
        val by = intakes.groupBy { it.day }
        fun p(d: Long) = progress(resolve(supps, rules, by[d].orEmpty(), d))
        var d = if (p(today).closed) today else today - 1
        var n = 0
        while (d >= firstDay && d > today - 1000) {
            val st = p(d)
            if (st.scheduled == 0) { d--; continue }
            if (!st.closed) break
            if (st.taken > 0) n++
            d--
        }
        return n
    }

    /** "Mo–Fr 08:00 · Sa Su 11:00, 20:00" style summary. */
    fun summary(s: Supplement, rules: List<SuppRule>): String {
        val mine = rules.filter { it.supplementId == s.id }.sortedWith(compareBy({ !it.base }, { it.sort }, { it.id }))
        if (mine.isEmpty()) return listOf(daysText(s.days), s.time).joinToString(" ")
        val base = mine.firstOrNull { it.base }
        val extraBits = mine.filter { !it.base }.fold(0) { a, r -> a or r.days }
        return mine.mapNotNull { r ->
            val days = if (r.base) r.days and extraBits.inv() and 127 else r.days
            if (days == 0) null else "${daysText(days)} " + DoseCodec.parse(r.doses).joinToString(", ") { it.time }
        }.joinToString(" · ").ifEmpty { if (base == null) "No schedule" else "No days" }
    }

    fun perWeek(s: Supplement, rules: List<SuppRule>): Int {
        val monday = Days.today().let { it - (Days.date(it).dayOfWeek.value - 1) }
        return (0 until 7).sumOf { dosesOn(s, rules, monday + it).size }
    }
}

private val DAY2 = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

fun daysText(mask: Int): String = when {
    mask and 127 == 127 -> "Daily"
    mask and 127 == 0b0011111 -> "Mo–Fr"
    mask and 127 == 0b1100000 -> "Sa Su"
    else -> (0 until 7).filter { mask and (1 shl it) != 0 }.joinToString(" ") { DAY2[it] }
}
