package com.lifeapi.app.data.grow3

import com.lifeapi.app.data.Achievement
import com.lifeapi.app.data.ActivityEntry
import com.lifeapi.app.data.DayPlan
import com.lifeapi.app.data.LearningEntry
import com.lifeapi.app.data.Milestone
import com.lifeapi.app.data.PlanTask
import com.lifeapi.app.data.Skill
import com.lifeapi.app.data.SkillTopic
import com.lifeapi.app.data.TimeBlock

/**
 * Counts, not scores: no productivity percentages, no skill levels.
 *
 * Time rule: spent time = Σ ActivityEntry.minutes (entries NOT linked to a block) + Σ TimeBlock.actualMinutes.
 * Planned block length and work hours are never counted. An activity linked to a block (blockId) only describes it.
 */
data class WeekStats(
    val tasksDone: Int,
    val tasksPlanned: Int,
    val goalsSet: Int,
    val goalsAchieved: Int,
    val loggedMinutes: Int,
    val activeDays: Int,
    val activities: Int,
    val achievements: Int,
    val minutesByProject: Map<Long?, Int>,
)

data class WeekDelta(val tasksDone: Int, val goalsAchieved: Int, val loggedMinutes: Int, val activeDays: Int)

object GrowStats {
    fun loggedMinutes(activity: List<ActivityEntry>, blocks: List<TimeBlock>): Int =
        activity.filter { it.blockId == null }.sumOf { it.minutes ?: 0 } + blocks.sumOf { it.actualMinutes ?: 0 }

    fun minutesByProject(activity: List<ActivityEntry>, blocks: List<TimeBlock>): Map<Long?, Int> {
        val m = HashMap<Long?, Int>()
        activity.filter { it.blockId == null && (it.minutes ?: 0) > 0 }.forEach { m[it.projectId] = (m[it.projectId] ?: 0) + it.minutes!! }
        blocks.filter { (it.actualMinutes ?: 0) > 0 }.forEach { m[it.projectId] = (m[it.projectId] ?: 0) + it.actualMinutes!! }
        return m
    }

    /** A day counts as active when something real happened: activity, a finished task, a logged block or a result. */
    fun activeDays(activity: List<ActivityEntry>, tasks: List<PlanTask>, blocks: List<TimeBlock>, plans: List<DayPlan>, doneDay: (PlanTask) -> Long?): Set<Long> =
        buildSet {
            activity.forEach { add(it.day) }
            tasks.filter { it.done }.mapNotNull(doneDay).forEach { add(it) }
            blocks.filter { (it.actualMinutes ?: 0) > 0 || it.result.isNotBlank() }.forEach { add(it.day) }
            plans.filter { it.result.isNotBlank() }.forEach { add(it.day) }
        }

    /**
     * Stats for [from]..[to]. Pass rows already limited to the range (and to one project if you want project stats).
     * A done task counts in the range of its scheduled day, or of its doneTs day when it had none (backlog).
     */
    fun week(
        from: Long, to: Long,
        plans: List<DayPlan>, tasks: List<PlanTask>, activity: List<ActivityEntry>, blocks: List<TimeBlock>,
        achievements: List<Achievement>, dayOfTs: (Long) -> Long,
    ): WeekStats {
        val doneDay: (PlanTask) -> Long? = { t -> if (!t.done) null else t.day ?: t.doneTs?.let(dayOfTs) }
        val inRange = { d: Long? -> d != null && d in from..to }
        val a = activity.filter { it.day in from..to }
        val b = blocks.filter { it.day in from..to }
        val p = plans.filter { it.day in from..to }
        val tDone = tasks.filter { inRange(doneDay(it)) }
        val tPlanned = tasks.filter { it.day != null && it.day in from..to }
        return WeekStats(
            tasksDone = tDone.size,
            tasksPlanned = tPlanned.size,
            goalsSet = p.count { it.goal.isNotBlank() },
            goalsAchieved = p.count { it.goal.isNotBlank() && it.result.isNotBlank() },
            loggedMinutes = loggedMinutes(a, b),
            activeDays = activeDays(a, tasks.filter { inRange(doneDay(it)) }, b, p, doneDay).count { it in from..to },
            activities = a.size,
            achievements = achievements.count { it.day in from..to },
            minutesByProject = minutesByProject(a, b),
        )
    }

    fun delta(cur: WeekStats, prev: WeekStats) = WeekDelta(
        cur.tasksDone - prev.tasksDone, cur.goalsAchieved - prev.goalsAchieved,
        cur.loggedMinutes - prev.loggedMinutes, cur.activeDays - prev.activeDays,
    )

    fun milestonesDone(ms: List<Milestone>): Int = ms.count { it.done }

    fun fmtMinutes(m: Int): String = when {
        m <= 0 -> "0m"
        m < 60 -> "${m}m"
        m % 60 == 0 -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}m"
    }
}

data class GrowthStats(
    val topicsDone: Int,
    val practiceDone: Int,
    val entries: Int,
    val learnedMinutes: Int,
    val achievements: Int,
    val activeSkills: Int,
    val activeDays: Int,
)

/** Suggestion for "what to review / learn next" — always with its reason, no invented scores. */
data class NextUp(val skillId: Long?, val title: String, val reason: String)

object GrowthCalc {
    /** Stats for a period [from]..[to] (e.g. the current month). */
    fun period(from: Long, to: Long, skills: List<Skill>, topics: List<SkillTopic>, entries: List<LearningEntry>, achievements: List<Achievement>, dayOfTs: (Long) -> Long): GrowthStats {
        val e = entries.filter { it.day in from..to }
        val doneInRange = topics.filter { it.done && it.doneTs != null && dayOfTs(it.doneTs) in from..to }
        val skillIdsActive = e.mapNotNull { it.skillId }.toSet() + doneInRange.map { it.skillId }
        return GrowthStats(
            topicsDone = doneInRange.count { it.kind != "Practice" },
            practiceDone = doneInRange.count { it.kind == "Practice" },
            entries = e.size,
            learnedMinutes = e.sumOf { it.minutes ?: 0 },
            achievements = achievements.count { it.day in from..to },
            activeSkills = skills.count { it.status == "Active" && it.id in skillIdsActive },
            activeDays = (e.map { it.day } + doneInRange.map { dayOfTs(it.doneTs!!) }).toSet().size,
        )
    }

    /**
     * What's worth doing next, with reasons:
     *  - questions you wrote under "unclear" in the last 60 days,
     *  - topics finished 21+ days ago with no journal entry for that skill since (review),
     *  - the first open topic of each active skill.
     */
    fun nextUp(today: Long, skills: List<Skill>, topics: List<SkillTopic>, entries: List<LearningEntry>, dayOfTs: (Long) -> Long, limit: Int = 5): List<NextUp> {
        val out = mutableListOf<NextUp>()
        entries.filter { it.unclear.isNotBlank() && today - it.day <= 60 }.sortedByDescending { it.day }.take(2).forEach {
            out += NextUp(it.skillId, it.unclear.trim(), "Still unclear since ${today - it.day} days ago")
        }
        val lastEntryBySkill = entries.filter { it.skillId != null }.groupBy { it.skillId!! }.mapValues { e -> e.value.maxOf { it.day } }
        topics.filter { it.done && it.doneTs != null }.forEach { t ->
            val doneDay = dayOfTs(t.doneTs!!)
            val since = today - doneDay
            if (since >= 21 && (lastEntryBySkill[t.skillId] ?: Long.MIN_VALUE) <= doneDay) out += NextUp(t.skillId, t.title, "Done $since days ago, not revisited — review")
        }
        val active = skills.filter { it.status == "Active" }.map { it.id }.toSet()
        topics.filter { !it.done && it.skillId in active }.groupBy { it.skillId }.forEach { (sid, list) ->
            list.minByOrNull { it.sort * 100000L + it.id }?.let { out += NextUp(sid, it.title, "Next open topic") }
        }
        return out.distinctBy { it.skillId to it.title.lowercase() }.take(limit)
    }
}
