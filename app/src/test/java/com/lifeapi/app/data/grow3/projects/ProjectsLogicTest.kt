package com.lifeapi.app.data.grow3.projects

import com.lifeapi.app.data.Achievement
import com.lifeapi.app.data.ActivityEntry
import com.lifeapi.app.data.DayPlan
import com.lifeapi.app.data.PlanTask
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.TimeBlock
import com.lifeapi.app.data.grow3.GrowStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectsLogicTest {
    private val mon = LocalDate.of(2026, 11, 16).toEpochDay() // Monday
    private val dayOfTs: (Long) -> Long = { it } // tests pass epoch days as "timestamps"

    @Test fun groupsByDayAndProject_andSkipsEmptyDays() {
        val plans = listOf(
            DayPlan(projectId = 1, day = mon, goal = "Reliable SMS handling", result = "SMS API handles retries"),
            DayPlan(projectId = 1, day = mon + 1, goal = ""),
        )
        val tasks = listOf(
            PlanTask(id = 1, projectId = 1, day = mon, title = "Add idempotency", done = true, doneTs = mon),
            PlanTask(id = 2, projectId = 1, day = mon, title = "Write tests"),
            PlanTask(id = 3, projectId = 2, day = null, title = "Backlog finished on Tue", done = true, doneTs = mon + 1),
        )
        val act = listOf(ActivityEntry(projectId = 1, day = mon, text = "Added transactional lock", minutes = 90, ts = 0))
        val days = ProjectReport.group(mon, mon + 6, plans, tasks, act, emptyList(), emptyList(), dayOfTs)
        assertEquals(listOf(mon, mon + 1), days.map { it.day })
        val m = days[0].items.single()
        assertEquals("Reliable SMS handling", m.goal)
        assertEquals(listOf("Add idempotency"), m.doneTasks.map { it.title })
        assertEquals(listOf("Write tests"), m.openTasks.map { it.title })
        assertEquals(90, m.minutes)
        // backlog task done on Tuesday lands on Tuesday under project 2
        assertEquals(2L, days[1].items.single().projectId)
    }

    @Test fun blockMinutesFollowTheTimeRule() {
        val blocks = listOf(
            TimeBlock(id = 7, day = mon, start = "09:00", end = "10:30", projectId = 1, title = "SMS API", actualMinutes = 80),
            TimeBlock(id = 8, day = mon, start = "11:00", end = "13:00", projectId = 1, title = "Planned only"),
        )
        val act = listOf(ActivityEntry(projectId = 1, day = mon, text = "Describes block 7", minutes = 999, ts = 0, blockId = 7))
        val d = ProjectReport.group(mon, mon, emptyList(), emptyList(), act, blocks, emptyList(), dayOfTs).single()
        assertEquals(80, d.minutes) // linked activity minutes and planned length are not counted
    }

    @Test fun textReportListsGoalTasksActivityResultAndAchievements() {
        val plans = listOf(DayPlan(projectId = 1, day = mon, goal = "Reliable SMS handling", result = "Retries handled"))
        val tasks = listOf(PlanTask(projectId = 1, day = mon, title = "Add idempotency", done = true, doneTs = mon))
        val act = listOf(ActivityEntry(projectId = 1, day = mon, text = "Checked concurrent requests", minutes = 30, ts = 0))
        val ach = listOf(Achievement(day = mon, title = "SMS API shipped", projectId = 1))
        val days = ProjectReport.group(mon, mon + 6, plans, tasks, act, emptyList(), ach, dayOfTs)
        val stats = GrowStats.week(mon, mon + 6, plans, tasks, act, emptyList(), ach, dayOfTs)
        val txt = ProjectReport.text("Week report · Payments", mon, mon + 6, days, mapOf(1L to Project(id = 1, name = "Payments", startDay = 0)), stats)
        assertTrue(txt.startsWith("Week report · Payments\n16–22 Nov 2026"))
        assertTrue(txt.contains("1 task done"))
        assertTrue(txt.contains("Monday, 16 November · 30m"))
        assertTrue(txt.contains("Goal: Reliable SMS handling"))
        assertTrue(txt.contains("- Add idempotency"))
        assertTrue(txt.contains("- Checked concurrent requests (30m)"))
        assertTrue(txt.contains("Result: Retries handled"))
        assertTrue(txt.contains("★ SMS API shipped"))
        assertFalse(txt.contains("[Payments]")) // single project → no project headers
    }

    @Test fun emptyReportSaysSo() {
        val txt = ProjectReport.text("Month report", mon, mon + 29, emptyList(), emptyMap(), null)
        assertTrue(txt.endsWith("Nothing recorded in this period."))
    }

    @Test fun weeklyLayoutShowsWorkdaysAndCollapsesEmptyWeekend() {
        val l = WeekLayout.layout(mon, weekly = true, workDays = 31, contentDays = setOf(mon + 5))
        assertEquals((0..5).map { mon + it }, l.shown)       // Mon–Fri + Saturday (has content)
        assertEquals(listOf(mon + 6), l.collapsed)            // empty Sunday collapsed
        val all = WeekLayout.layout(mon, weekly = true, workDays = 31, contentDays = emptySet(), expanded = true)
        assertEquals(7, all.shown.size)
    }

    @Test fun personalLayoutShowsOnlyDaysWithContent_andSuggestsUsualDays() {
        val l = WeekLayout.layout(mon, weekly = false, workDays = 32 or 64, contentDays = setOf(mon + 5), extra = setOf(mon + 2))
        assertEquals(listOf(mon + 2, mon + 5), l.shown)
        assertEquals(setOf(mon + 6), l.suggested) // Sunday is a usual day not yet planned
        assertFalse(mon + 5 in l.addable)
    }
}
