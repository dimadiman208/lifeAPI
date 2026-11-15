package com.lifeapi.app.data.grow3.growth

import com.lifeapi.app.data.Achievement
import com.lifeapi.app.data.LearningEntry
import com.lifeapi.app.data.Skill
import com.lifeapi.app.data.SkillTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthViewsTest {
    private val day = 20_000L
    private val dayOf: (Long) -> Long = { it / 1000 }
    private fun e(id: Long, skill: Long?, d: Long, min: Int? = null, project: Long? = null, material: Long? = null) =
        LearningEntry(id = id, skillId = skill, day = d, ts = d * 1000 + id, learned = "x$id", minutes = min, projectId = project, materialId = material)

    @Test fun skillCardsCountOnlyTheirOwnRows() {
        val skills = listOf(Skill(1, "PostgreSQL"), Skill(2, "Kotlin"))
        val topics = listOf(SkillTopic(1, 1, "MVCC", done = true), SkillTopic(2, 1, "Locks"), SkillTopic(3, 2, "Flow"))
        val entries = listOf(e(1, 1, day, 30), e(2, 1, day - 40, 20), e(3, null, day, 10))
        val cards = GrowthViews.skillCards(skills, topics, entries, day - 10, day)
        assertEquals(1, cards[0].topicsDone); assertEquals(2, cards[0].topicsTotal)
        assertEquals(day, cards[0].lastEntryDay); assertEquals(1, cards[0].entriesThisMonth); assertEquals(30, cards[0].minutesThisMonth)
        assertEquals(null, cards[1].lastEntryDay); assertEquals(0, cards[1].entriesThisMonth)
    }

    @Test fun staleNeedsFourteenDays() {
        val skills = listOf(Skill(1, "A", createdTs = (day - 100) * 1000), Skill(2, "B", createdTs = (day - 100) * 1000), Skill(3, "C", createdTs = day * 1000), Skill(4, "D", status = "Paused"))
        val entries = listOf(e(1, 1, day - 13), e(2, 2, day - 20))
        val st = GrowthViews.stale(day, skills, entries, dayOf)
        assertEquals(listOf(2L), st.map { it.skill.id })
        assertEquals(20, st[0].daysSince)
    }

    @Test fun openTopicsOldestTouchedSkillFirst() {
        val skills = listOf(Skill(1, "A"), Skill(2, "B"))
        val topics = listOf(SkillTopic(1, 1, "a1"), SkillTopic(2, 2, "b1"), SkillTopic(3, 2, "b0", done = true))
        val entries = listOf(e(1, 1, day), e(2, 2, day - 5))
        assertEquals(listOf("b1", "a1"), GrowthViews.openTopics(skills, topics, entries).map { it.title })
    }

    @Test fun journalGroupsByDayAndFilters() {
        val entries = listOf(e(1, 1, day), e(2, 2, day), e(3, 1, day - 1))
        val all = GrowthViews.journal(entries, null)
        assertEquals(listOf(day, day - 1), all.map { it.day })
        assertEquals(2, all[0].entries.size)
        assertEquals(listOf(1L, 3L), GrowthViews.journal(entries, 1).flatMap { it.entries }.map { it.id })
    }

    @Test fun linksAreDistinct() {
        val entries = listOf(e(1, 1, day, project = 5, material = 7), e(2, 1, day - 1, project = 5))
        val ach = listOf(Achievement(1, day, "x", projectId = 6, materialId = 7))
        assertEquals(listOf(5L, 6L), GrowthViews.linkedProjects(entries, ach))
        assertEquals(listOf(9L, 7L), GrowthViews.linkedMaterials(entries, ach, listOf(9L)))
    }

    @Test fun headlineAndQuick() {
        val q = LearningEntry(day = day, ts = 0, learned = "  idea ")
        assertEquals("idea", GrowthViews.headline(q)); assertTrue(GrowthViews.isQuick(q))
        assertEquals("p", GrowthViews.headline(q.copy(learned = "", practiced = "p")))
    }
}
