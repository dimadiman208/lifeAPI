package com.lifeapi.app.vm.grow3.growth

import android.app.Application
import com.lifeapi.app.data.Achievement
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.LearningEntry
import com.lifeapi.app.data.Material
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.Skill
import com.lifeapi.app.data.SkillTopic
import com.lifeapi.app.data.grow3.GrowthCalc
import com.lifeapi.app.data.grow3.GrowthStats
import com.lifeapi.app.data.grow3.NextUp
import com.lifeapi.app.data.grow3.growth.GrowthViews
import com.lifeapi.app.data.grow3.growth.JournalDay
import com.lifeapi.app.data.grow3.growth.SkillCard
import com.lifeapi.app.data.grow3.growth.StaleSkill
import com.lifeapi.app.vm.BaseVM
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Pickers used by the entry sheet: skills, projects, materials. */
data class GrowthRefs(val skills: List<Skill>, val projects: List<Project>, val materials: List<Material>) {
    fun skillName(id: Long?) = skills.firstOrNull { it.id == id }?.name
    fun projectName(id: Long?) = projects.firstOrNull { it.id == id }?.name
    fun materialTitle(id: Long?) = materials.firstOrNull { it.id == id }?.title
}

data class GrowthTabState(
    val today: Long,
    val cards: List<SkillCard>,
    val month: GrowthStats,
    val prevMonth: GrowthStats,
    val monthLabel: String,
    val nextUp: List<NextUp>,
    val stale: List<StaleSkill>,
    val recent: List<LearningEntry>,
    val refs: GrowthRefs,
)

/** Shared write operations (journal, skills, topics, achievements) with Undo. */
abstract class GrowthOpsVM(app: Application) : BaseVM(app) {
    protected val growth = repo.db.growth()
    protected val projectsDao = repo.db.projects()
    protected val learnDao = repo.db.learn()

    val refs = combine(growth.skills(), projectsDao.allProjects(), learnDao.materials()) { s, p, m ->
        GrowthRefs(s, p.filter { it.status != "Archived" }, m.filter { it.status != "Archived" })
    }

    fun saveEntry(e: LearningEntry) = act(if (e.id == 0L) "Added to journal" else "Entry saved") {
        val prev = if (e.id != 0L) e else null
        val id = growth.upsertLearning(if (e.ts == 0L) e.copy(ts = Days.now()) else e)
        if (prev == null) suspend { growth.deleteLearningById(id) } else null
    }

    fun deleteEntry(e: LearningEntry) = act("Entry deleted") {
        growth.deleteLearning(e)
        suspend { growth.upsertLearning(e); Unit }
    }

    fun saveSkill(s: Skill, onCreated: (Long) -> Unit = {}) = launch {
        val id = growth.upsertSkill(if (s.id == 0L) s.copy(createdTs = Days.now()) else s)
        if (s.id == 0L) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onCreated(id) }
    }

    fun saveTopic(t: SkillTopic) = launch { growth.upsertTopic(t) }

    fun toggleTopic(t: SkillTopic) = launch {
        growth.upsertTopic(t.copy(done = !t.done, doneTs = if (!t.done) Days.now() else null))
    }

    fun deleteTopic(t: SkillTopic) = act("“${t.title}” removed") {
        growth.deleteTopic(t)
        suspend { growth.upsertTopic(t); Unit }
    }

    fun saveAchievement(a: Achievement) = act(if (a.id == 0L) "Achievement saved" else null) {
        val id = growth.upsertAchievement(if (a.ts == 0L) a.copy(ts = Days.now()) else a)
        if (a.id == 0L) suspend { growth.deleteAchievement(a.copy(id = id)) } else null
    }

    fun deleteAchievement(a: Achievement) = act("Achievement deleted") {
        growth.deleteAchievement(a)
        suspend { growth.upsertAchievement(a); Unit }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GrowthTabVM(app: Application) : GrowthOpsVM(app) {
    val state = today.flatMapLatest { day ->
        val d = Days.date(day)
        val mFrom = d.withDayOfMonth(1).toEpochDay()
        val pFrom = d.minusMonths(1).withDayOfMonth(1).toEpochDay()
        combine(
            growth.allTopics(),
            growth.learningRange(Long.MIN_VALUE / 4, Long.MAX_VALUE / 4),
            growth.achievementsRange(pFrom, day),
            refs,
        ) { topics, entries, ach, r ->
            val skills = r.skills
            GrowthTabState(
                today = day,
                cards = GrowthViews.skillCards(skills.filter { it.status != "Done" }, topics, entries, mFrom, day),
                month = GrowthCalc.period(mFrom, day, skills, topics, entries, ach, Days::dayOf),
                prevMonth = GrowthCalc.period(pFrom, mFrom - 1, skills, topics, entries, ach, Days::dayOf),
                monthLabel = d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH),
                nextUp = GrowthCalc.nextUp(day, skills, topics, entries, Days::dayOf),
                stale = GrowthViews.stale(day, skills, entries, Days::dayOf),
                recent = entries.take(5),
                refs = r,
            )
        }
    }.state()
}

data class SkillState(
    val skill: Skill,
    val topics: List<SkillTopic>,
    val entries: List<LearningEntry>,
    val achievements: List<Achievement>,
    val projects: List<Project>,
    val materials: List<Material>,
    val refs: GrowthRefs,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SkillVM(app: Application) : GrowthOpsVM(app) {
    private val id = MutableStateFlow<Long?>(null)
    fun bind(skillId: Long) { id.value = skillId }

    val state = id.filterNotNull().flatMapLatest { sid ->
        combine(
            growth.skill(sid), growth.topicsOf(sid), growth.learningOf(sid), growth.achievementsOf(sid),
            combine(learnDao.materialsOfSkill(sid), refs) { m, r -> m to r },
        ) { s, topics, entries, ach, (mats, r) ->
            if (s == null) return@combine null
            val pIds = GrowthViews.linkedProjects(entries, ach)
            val mIds = GrowthViews.linkedMaterials(entries, ach, mats.map { it.id })
            val allMats = (mats + r.materials).distinctBy { it.id }
            SkillState(s, topics, entries, ach, pIds.mapNotNull { p -> r.projects.firstOrNull { it.id == p } }, mIds.mapNotNull { m -> allMats.firstOrNull { it.id == m } }, r)
        }
    }.map { it }.state()

    fun deleteSkill(s: Skill, onDone: () -> Unit) = launch {
        growth.deleteSkillCascade(s.id)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
        c.snack("${s.name} deleted · journal entries kept")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class JournalVM(app: Application) : GrowthOpsVM(app) {
    val filter = MutableStateFlow<Long?>(null)
    val state = combine(growth.recentLearning(2000), filter, refs) { entries, f, r -> Triple(GrowthViews.journal(entries, f), f, r) }.state()
}

/** Prefill for the add route (from a material, a skill, or text). */
class AddLearningVM(app: Application) : GrowthOpsVM(app) {
    val refsState = refs.state()
}

typealias JournalDays = List<JournalDay>
