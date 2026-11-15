package com.lifeapi.app.data.grow3

import com.lifeapi.app.data.TimeBlock

/** "HH:MM" helpers for time blocks. Blocks live inside one day (end > start). */
object TimeMath {
    private val HM = Regex("""^(\d{1,2}):(\d{2})$""")

    /** Minutes since midnight, or null when not a valid "HH:MM" (00:00–24:00). */
    fun parse(hm: String): Int? {
        val m = HM.find(hm.trim()) ?: return null
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        if (min > 59 || h > 24 || (h == 24 && min != 0)) return null
        return h * 60 + min
    }

    fun format(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** Planned length in minutes, null when invalid or end ≤ start. */
    fun duration(start: String, end: String): Int? {
        val s = parse(start) ?: return null
        val e = parse(end) ?: return null
        return (e - s).takeIf { it > 0 }
    }

    fun overlaps(a: TimeBlock, b: TimeBlock): Boolean {
        val a0 = parse(a.start) ?: return false; val a1 = parse(a.end) ?: return false
        val b0 = parse(b.start) ?: return false; val b1 = parse(b.end) ?: return false
        return a0 < b1 && b0 < a1
    }

    /** Blocks of the same day that overlap [b] (excluding itself). Overlaps are allowed but should be shown. */
    fun conflicts(b: TimeBlock, sameDay: List<TimeBlock>): List<TimeBlock> = sameDay.filter { it.id != b.id && it.day == b.day && overlaps(b, it) }

    /**
     * First free slot of [length] minutes inside [workStart, workEnd] that doesn't overlap existing blocks;
     * falls back to right after the last block (may be outside work hours). Used to prefill "new block".
     */
    fun nextFreeSlot(blocks: List<TimeBlock>, workStart: String, workEnd: String, length: Int = 60): Pair<String, String> {
        val ws = parse(workStart) ?: 9 * 60
        val we = parse(workEnd) ?: 18 * 60
        val busy = blocks.mapNotNull { b -> val s = parse(b.start); val e = parse(b.end); if (s != null && e != null && e > s) s to e else null }.sortedBy { it.first }
        var t = ws
        for ((s, e) in busy) {
            if (s - t >= length && t + length <= we) return format(t) to format(t + length)
            if (e > t) t = e
        }
        if (t + length <= we) return format(t) to format(t + length)
        val after = (busy.maxOfOrNull { it.second } ?: ws).coerceAtMost(24 * 60 - length)
        return format(after) to format((after + length).coerceAtMost(24 * 60))
    }
}
