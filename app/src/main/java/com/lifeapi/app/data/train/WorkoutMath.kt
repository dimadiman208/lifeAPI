package com.lifeapi.app.data.train

import com.lifeapi.app.data.WorkoutSet
import com.lifeapi.app.data.fmtKg
import java.util.Locale

/** "92.5×3 · 87.5×6 ×2 · 87.5×5" — consecutive identical sets collapsed. */
fun compactSets(sets: List<WorkoutSet>): String {
    val out = mutableListOf<String>()
    var i = 0
    while (i < sets.size) {
        var j = i
        while (j + 1 < sets.size && sets[j + 1].kg == sets[i].kg && sets[j + 1].reps == sets[i].reps) j++
        val n = j - i + 1
        out += "${fmtKg(sets[i].kg)}×${sets[i].reps}" + if (n > 1) " ×$n" else ""
        i = j + 1
    }
    return out.joinToString(" · ")
}

/** "3 × 10 · 30" when uniform, else "5 sets · top 90×4". */
fun exerciseLine(sets: List<WorkoutSet>): String {
    if (sets.isEmpty()) return "—"
    val uniform = sets.all { it.kg == sets[0].kg && it.reps == sets[0].reps }
    return if (uniform) "${sets.size} × ${sets[0].reps}" + if (sets[0].kg > 0) " · ${fmtKg(sets[0].kg)}" else ""
    else {
        val top = sets.maxWith(compareBy<WorkoutSet> { it.kg }.thenBy { it.reps })
        "${sets.size} sets · top ${fmtKg(top.kg)}×${top.reps}"
    }
}

fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)
fun mmss(sec: Long): String = String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)
fun hmmss(sec: Long): String = if (sec >= 3600) String.format(Locale.US, "%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60) else mmss(sec)

/** Weekday bitmask → "Mon · Sat". */
fun daysLabel(mask: Int): String {
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return (0..6).filter { mask and (1 shl it) != 0 }.joinToString(" · ") { names[it] }
}
