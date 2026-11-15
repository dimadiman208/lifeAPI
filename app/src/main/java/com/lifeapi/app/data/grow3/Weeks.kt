package com.lifeapi.app.data.grow3

import java.time.DayOfWeek
import java.time.LocalDate

/** Calendar weeks are Monday–Sunday. Days are epoch days (LocalDate.toEpochDay). */
object Weeks {
    fun start(day: Long): Long = LocalDate.ofEpochDay(day).with(DayOfWeek.MONDAY).toEpochDay()
    fun end(day: Long): Long = start(day) + 6
    fun days(weekStart: Long): List<Long> = (0L..6L).map { weekStart + it }

    /** Bitmask Mon=1 … Sun=64. */
    fun bit(day: Long): Int = 1 shl (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)
    fun inMask(mask: Int, day: Long): Boolean = mask and bit(day) != 0
    const val WORKDAYS = 31   // Mon–Fri
    const val ALL_DAYS = 127

    /** Work-style projects show these days prominently; the weekend is still available, just secondary. */
    fun workdays(weekStart: Long, mask: Int = WORKDAYS): List<Long> = days(weekStart).filter { inMask(mask, it) }

    /** Next working day after [day] according to [mask] (falls back to day + 1 when mask is empty). */
    fun nextWorkday(day: Long, mask: Int = WORKDAYS): Long {
        if (mask and ALL_DAYS == 0) return day + 1
        var d = day + 1
        while (!inMask(mask, d)) d++
        return d
    }
}
