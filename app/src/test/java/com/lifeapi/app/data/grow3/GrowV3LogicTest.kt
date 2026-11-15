package com.lifeapi.app.data.grow3

import com.lifeapi.app.data.Achievement
import com.lifeapi.app.data.ActivityEntry
import com.lifeapi.app.data.DayPlan
import com.lifeapi.app.data.LearningEntry
import com.lifeapi.app.data.PlanTask
import com.lifeapi.app.data.Skill
import com.lifeapi.app.data.SkillTopic
import com.lifeapi.app.data.TimeBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeksTest {
    private val mon = LocalDate.of(2026, 11, 16).toEpochDay()   // Monday

    @Test fun weekStartsMonday() {
        assertEquals(mon, Weeks.start(mon))
        assertEquals(mon, Weeks.start(mon + 6))          // Sunday belongs to the same week
        assertEquals(mon + 7, Weeks.start(mon + 7))
        assertEquals(mon + 6, Weeks.end(mon + 3))
    }

    @Test fun workdaysMask() {
        assertEquals(5, Weeks.workdays(mon).size)
        assertTrue(Weeks.inMask(Weeks.WORKDAYS, mon + 4))  // Friday
        assertFalse(Weeks.inMask(Weeks.WORKDAYS, mon + 5)) // Saturday
        assertEquals(mon + 7, Weeks.nextWorkday(mon + 4))  // Fri → Mon
        assertEquals(mon + 1, Weeks.nextWorkday(mon, 0))   // empty mask → next day
    }
}

class TimeMathTest {
    private fun b(id: Long, s: String, e: String, day: Long = 1, actual: Int? = null) = TimeBlock(id = id, day = day, start = s, end = e, title = "x", actualMinutes = actual)

    @Test fun parseAndDuration() {
        assertEquals(9 * 60 + 30, TimeMath.parse("09:30"))
        assertEquals(24 * 60, TimeMath.parse("24:00"))
        assertNull(TimeMath.parse("24:30")); assertNull(TimeMath.parse("9:75")); assertNull(TimeMath.parse("abc"))
        assertEquals(90, TimeMath.duration("09:00", "10:30"))
        assertNull(TimeMath.duration("10:00", "10:00"))
        assertNull(TimeMath.duration("11:00", "10:00"))
    }

    @Test fun overlaps() {
        assertTrue(TimeMath.overlaps(b(1, "09:00", "10:30"), b(2, "10:00", "11:00")))
        assertFalse(TimeMath.overlaps(b(1, "09:00", "10:30"), b(2, "10:30", "11:00"))) // touching is fine
        assertEquals(1, TimeMath.conflicts(b(3, "09:30", "09:45"), listOf(b(1, "09:00", "10:30"), b(2, "10:30", "11:00"))).size)
    }

    @Test fun nextFreeSlot() {
        assertEquals("09:00" to "10:00", TimeMath.nextFreeSlot(emptyList(), "09:00", "18:00"))
        assertEquals("10:30" to "11:30", TimeMath.nextFreeSlot(listOf(b(1, "09:00", "10:30")), "09:00", "18:00"))
        // gap between blocks is used when big enough
        assertEquals("10:00" to "11:00", TimeMath.nextFreeSlot(listOf(b(1, "09:00", "10:00"), b(2, "12:00", "13:00")), "09:00", "18:00"))
        // full day → after the last block
        assertEquals("18:00" to "19:00", TimeMath.nextFreeSlot(listOf(b(1, "09:00", "18:00")), "09:00", "18:00"))
    }
}

class CarryOverTest {
    private fun t(id: Long, day: Long?, done: Boolean = false) = PlanTask(id = id, projectId = 1, day = day, title = "t$id", done = done)

    @Test fun candidatesAreOnlyUnfinishedPast() {
        val list = listOf(t(1, 10), t(2, 10, done = true), t(3, 12), t(4, null))
        assertEquals(listOf(1L), CarryOver.candidates(list, today = 12).map { it.id })
    }

    @Test fun moveKeepsIdAndRecordsOrigin() {
        val moved = CarryOver.move(listOf(t(1, 10), t(2, 10, done = true), t(3, 11)), to = 11)
        assertEquals(listOf(1L), moved.map { it.id })            // done stays, already-there stays
        assertEquals(11L, moved[0].day); assertEquals(10L, moved[0].movedFrom)
        val toBacklog = CarryOver.move(listOf(t(5, 9)), to = null)
        assertNull(toBacklog[0].day); assertEquals(9L, toBacklog[0].movedFrom)
    }
}

class GrowStatsTest {
    private val dayOfTs: (Long) -> Long = { it / 1000 }   // test clock: ts = day * 1000

    @Test fun timeIsOnlyLoggedTimeNoDoubleCounting() {
        val acts = listOf(
            ActivityEntry(id = 1, projectId = 1, day = 1, text = "a", minutes = 30, ts = 0),
            ActivityEntry(id = 2, projectId = 1, day = 1, text = "describes block", minutes = 90, ts = 0, blockId = 7),
            ActivityEntry(id = 3, projectId = 2, day = 1, text = "no time", ts = 0),
        )
        val blocks = listOf(
            TimeBlock(id = 7, day = 1, start = "09:00", end = "10:30", projectId = 1, title = "b", actualMinutes = 80),
            TimeBlock(id = 8, day = 1, start = "11:00", end = "18:00", projectId = 2, title = "planned only"), // not logged → 0
        )
        assertEquals(110, GrowStats.loggedMinutes(acts, blocks))
        assertEquals(mapOf<Long?, Int>(1L to 110), GrowStats.minutesByProject(acts, blocks))
    }

    @Test fun weekCounts() {
        val plans = listOf(
            DayPlan(projectId = 1, day = 1, goal = "G1", result = "done it"),
            DayPlan(projectId = 1, day = 2, goal = "G2"),
            DayPlan(projectId = 1, day = 3, note = "no goal"),
        )
        val tasks = listOf(
            PlanTask(id = 1, projectId = 1, day = 1, title = "a", done = true, doneTs = 1000),
            PlanTask(id = 2, projectId = 1, day = 2, title = "b"),
            PlanTask(id = 3, projectId = 1, day = null, title = "backlog done", done = true, doneTs = 4000),
            PlanTask(id = 4, projectId = 1, day = 20, title = "outside", done = true, doneTs = 20000),
        )
        val acts = listOf(ActivityEntry(projectId = 1, day = 5, text = "x", minutes = 15, ts = 0))
        val ach = listOf(Achievement(day = 2, title = "shipped"), Achievement(day = 30, title = "later"))
        val w = GrowStats.week(0, 6, plans, tasks, acts, emptyList(), ach, dayOfTs)
        assertEquals(2, w.tasksDone)            // day-1 task + backlog task done on day 4
        assertEquals(2, w.tasksPlanned)
        assertEquals(2, w.goalsSet)
        assertEquals(1, w.goalsAchieved)
        assertEquals(15, w.loggedMinutes)
        assertEquals(setOf(1L, 4L, 5L).size, w.activeDays) // result day 1, task done day 4, activity day 5
        assertEquals(1, w.achievements)
        val d = GrowStats.delta(w, w.copy(tasksDone = 1, loggedMinutes = 5))
        assertEquals(1, d.tasksDone); assertEquals(10, d.loggedMinutes)
    }

    @Test fun formatMinutes() {
        assertEquals("45m", GrowStats.fmtMinutes(45)); assertEquals("2h", GrowStats.fmtMinutes(120)); assertEquals("1h 30m", GrowStats.fmtMinutes(90))
    }
}

class GrowthCalcTest {
    private val dayOfTs: (Long) -> Long = { it / 1000 }

    @Test fun periodAndNextUp() {
        val skills = listOf(Skill(id = 1, name = "PostgreSQL"), Skill(id = 2, name = "English", status = "Paused"))
        val topics = listOf(
            SkillTopic(id = 1, skillId = 1, title = "MVCC", done = true, doneTs = 5000),
            SkillTopic(id = 2, skillId = 1, title = "Isolation levels"),
            SkillTopic(id = 3, skillId = 1, title = "FOR UPDATE test", kind = "Practice", done = true, doneTs = 6000),
            SkillTopic(id = 4, skillId = 2, title = "Phrasal verbs"),
        )
        val entries = listOf(LearningEntry(skillId = 1, day = 5, ts = 0, learned = "READ COMMITTED", unclear = "SKIP LOCKED with joins?", minutes = 40))
        val p = GrowthCalc.period(0, 30, skills, topics, entries, listOf(Achievement(day = 6, title = "test", skillId = 1)), dayOfTs)
        assertEquals(1, p.topicsDone); assertEquals(1, p.practiceDone); assertEquals(1, p.entries)
        assertEquals(40, p.learnedMinutes); assertEquals(1, p.achievements); assertEquals(1, p.activeSkills); assertEquals(2, p.activeDays)

        val next = GrowthCalc.nextUp(today = 40, skills, topics, entries, dayOfTs)
        val titles = next.map { it.title }
        assertTrue("SKIP LOCKED with joins?" in titles)       // unclear question
        assertTrue("MVCC" in titles)                           // done day 5, revisited only on day 5 → review
        assertTrue("Isolation levels" in titles)               // next open topic of an active skill
        assertFalse("Phrasal verbs" in titles)                 // paused skill
    }
}

class MaterialKindsTest {
    @Test fun detect() {
        assertEquals(MaterialKinds.YOUTUBE, MaterialKinds.detect("https://www.youtube.com/watch?v=abc"))
        assertEquals(MaterialKinds.YOUTUBE, MaterialKinds.detect("https://youtu.be/abc"))
        assertEquals(MaterialKinds.GITHUB, MaterialKinds.detect("https://github.com/square/okhttp"))
        assertEquals(MaterialKinds.PDF, MaterialKinds.detect("https://example.com/paper.pdf"))
        assertEquals(MaterialKinds.PDF, MaterialKinds.detect(null, "application/pdf"))
        assertEquals(MaterialKinds.DOCS, MaterialKinds.detect("https://www.postgresql.org/docs/current/mvcc.html"))
        assertEquals(MaterialKinds.DOCS, MaterialKinds.detect("https://developer.android.com/jetpack/compose"))
        assertEquals(MaterialKinds.ARTICLE, MaterialKinds.detect("https://medium.com/@x/some-story"))
        assertEquals(MaterialKinds.LINK, MaterialKinds.detect("https://example.com/"))
    }

    @Test fun urlsAndTags() {
        assertEquals("https://example.com/a", MaterialKinds.normalizeUrl(" example.com/a "))
        assertNull(MaterialKinds.normalizeUrl("not a url"))
        assertNull(MaterialKinds.normalizeUrl("localhost"))
        assertEquals("kotlin,coroutines", MaterialKinds.parseTags("Kotlin, #coroutines , kotlin"))
        assertEquals("Mvcc · postgresql.org", MaterialKinds.titleFromUrl("https://www.postgresql.org/docs/mvcc.html"))
    }
}
