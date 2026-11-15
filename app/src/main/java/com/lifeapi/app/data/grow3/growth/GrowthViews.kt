package com.lifeapi.app.data.grow3.growth

import com.lifeapi.app.data.Achievement
import com.lifeapi.app.data.LearningEntry
import com.lifeapi.app.data.Skill
import com.lifeapi.app.data.SkillTopic

/** One compact skill card on the Growth tab. Counts only — no levels or mastery %. */
data class SkillCard(
    val skill: Skill,
    val topicsDone: Int,
    val topicsTotal: Int,
    val lastEntryDay: Long?,
    val entriesThisMonth: Int,
    val minutesThisMonth: Int,
)

/** A skill that hasn't had a journal entry for a while (or never). */
data class StaleSkill(val skill: Skill, val daysSince: Int?)

/** Journal entries grouped by day, newest first. */
data class JournalDay(val day: Long, val entries: List<LearningEntry>)

object GrowthViews {
    const val STALE_DAYS = 14

    fun skillCards(skills: List<Skill>, topics: List<SkillTopic>, entries: List<LearningEntry>, monthFrom: Long, monthTo: Long): List<SkillCard> {
        val topicsBy = topics.groupBy { it.skillId }
        val entriesBy = entries.filter { it.skillId != null }.groupBy { it.skillId!! }
        return skills.map { s ->
            val ts = topicsBy[s.id].orEmpty()
            val es = entriesBy[s.id].orEmpty()
            val month = es.filter { it.day in monthFrom..monthTo }
            SkillCard(s, ts.count { it.done }, ts.size, es.maxOfOrNull { it.day }, month.size, month.sumOf { it.minutes ?: 0 })
        }
    }

    /** Active skills with no journal entry for [STALE_DAYS]+ days; skills created recently without entries don't count yet. */
    fun stale(today: Long, skills: List<Skill>, entries: List<LearningEntry>, dayOfTs: (Long) -> Long): List<StaleSkill> {
        val last = entries.filter { it.skillId != null }.groupBy { it.skillId!! }.mapValues { e -> e.value.maxOf { it.day } }
        return skills.filter { it.status == "Active" }.mapNotNull { s ->
            val l = last[s.id]
            when {
                l != null && today - l >= STALE_DAYS -> StaleSkill(s, (today - l).toInt())
                l == null && s.createdTs > 0 && today - dayOfTs(s.createdTs) >= STALE_DAYS -> StaleSkill(s, null)
                else -> null
            }
        }.sortedByDescending { it.daysSince ?: Int.MAX_VALUE }
    }

    /** Open topics, the ones whose skill was touched longest ago first (then topic order). */
    fun openTopics(skills: List<Skill>, topics: List<SkillTopic>, entries: List<LearningEntry>, limit: Int = 6): List<SkillTopic> {
        val active = skills.filter { it.status == "Active" }.map { it.id }.toSet()
        val last = entries.filter { it.skillId != null }.groupBy { it.skillId!! }.mapValues { e -> e.value.maxOf { it.day } }
        return topics.filter { !it.done && it.skillId in active }
            .sortedWith(compareBy<SkillTopic>({ last[it.skillId] ?: Long.MIN_VALUE }, { it.sort }, { it.id }))
            .take(limit)
    }

    fun journal(entries: List<LearningEntry>, skillFilter: Long?): List<JournalDay> =
        entries.filter { skillFilter == null || it.skillId == skillFilter }
            .groupBy { it.day }.toSortedMap(compareByDescending { it })
            .map { (d, es) -> JournalDay(d, es.sortedByDescending { it.ts }) }

    /** Projects / materials linked to a skill through its entries and achievements (ids, most recent first, no duplicates). */
    fun linkedProjects(entries: List<LearningEntry>, achievements: List<Achievement>): List<Long> =
        (entries.sortedByDescending { it.ts }.mapNotNull { it.projectId } + achievements.sortedByDescending { it.ts }.mapNotNull { it.projectId }).distinct()

    fun linkedMaterials(entries: List<LearningEntry>, achievements: List<Achievement>, materialIdsOfSkill: List<Long>): List<Long> =
        (materialIdsOfSkill + entries.sortedByDescending { it.ts }.mapNotNull { it.materialId } + achievements.mapNotNull { it.materialId }).distinct()

    /** First non-blank text of an entry, for one-line display. */
    fun headline(e: LearningEntry): String =
        listOf(e.learned, e.practiced, e.understood, e.unclear).firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    /** Is this entry just a quick thought (only the first field)? */
    fun isQuick(e: LearningEntry): Boolean = e.practiced.isBlank() && e.understood.isBlank() && e.unclear.isBlank()
}
