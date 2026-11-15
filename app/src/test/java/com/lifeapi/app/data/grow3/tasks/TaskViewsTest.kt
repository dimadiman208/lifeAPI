package com.lifeapi.app.data.grow3.tasks

import com.lifeapi.app.data.PlanTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskViewsTest {
    private val today = 20_000L
    private fun t(id: Long, day: Long?, done: Boolean = false, project: Long = 1, title: String = "Task $id", sort: Int = 0) =
        PlanTask(id = id, projectId = project, day = day, title = title, done = done, sort = sort)

    private val tasks = listOf(
        t(1, today), t(2, today, done = true), t(3, today - 2), t(4, today - 1, done = true),
        t(5, today + 1), t(6, today + 14), t(7, today + 15), t(8, null), t(9, null, done = true, project = 2),
    )

    @Test fun views() {
        fun ids(v: TaskView) = TaskViews.filter(tasks, v, today, emptySet(), "").map { it.id }.toSet()
        assertEquals(setOf(1L, 2L), ids(TaskView.TODAY))
        assertEquals(setOf(5L, 6L), ids(TaskView.UPCOMING))
        assertEquals(setOf(3L), ids(TaskView.OVERDUE))
        assertEquals(setOf(8L), ids(TaskView.BACKLOG))
        assertEquals(setOf(2L, 4L, 9L), ids(TaskView.DONE))
        assertEquals(9, ids(TaskView.ALL).size)
    }

    @Test fun counts() {
        val c = TaskViews.counts(tasks, today)
        assertEquals(1, c.today); assertEquals(1, c.overdue); assertEquals(2, c.upcoming); assertEquals(1, c.backlog)
        assertEquals(6, c.all)
    }

    @Test fun projectFilterAndSearch() {
        val names = mapOf(1L to "LifeAPI", 2L to "Work SMS")
        val r = TaskViews.filter(tasks, TaskView.ALL, today, setOf(2L), "", { names[it] ?: "" })
        assertEquals(listOf(9L), r.map { it.id })
        val s = TaskViews.filter(listOf(t(1, today, title = "Write tests"), t(2, today, title = "Fix bug", project = 2)), TaskView.ALL, today, emptySet(), "sms", { names[it] ?: "" })
        assertEquals(listOf(2L), s.map { it.id })
        val multi = TaskViews.filter(listOf(t(1, today, title = "Write unit tests"), t(2, today, title = "Write docs")), TaskView.ALL, today, emptySet(), "write tests")
        assertEquals(listOf(1L), multi.map { it.id })
    }

    @Test fun groupByDate() {
        val g = TaskViews.group(tasks.filter { !it.done }, GroupBy.DATE, TaskView.ALL, listOf(1, 2))
        assertEquals(listOf(today - 2, today, today + 1, today + 14, today + 15, null), g.map { it.day })
        assertEquals("backlog", g.last().key)
    }

    @Test fun groupByProjectKeepsOrderAndPutsOpenFirst() {
        val list = listOf(t(1, today, done = true, project = 2), t(2, today, project = 2), t(3, null, project = 1))
        val g = TaskViews.group(list, GroupBy.PROJECT, TaskView.ALL, listOf(2, 1))
        assertEquals(listOf(2L, 1L), g.map { it.projectId })
        assertEquals(listOf(2L, 1L), g[0].tasks.map { it.id })
    }

    @Test fun doneViewNewestFirst() {
        val g = TaskViews.group(listOf(t(1, today - 3, true), t(2, today, true)), GroupBy.DATE, TaskView.DONE, emptyList())
        assertTrue(g.first().day == today)
    }
}
