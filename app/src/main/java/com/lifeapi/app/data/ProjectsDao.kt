package com.lifeapi.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Last day with recorded activity per project (Projects list "last activity"). */
data class ProjectLastDay(val projectId: Long, val day: Long)

/** Grow v3 · Projects: categories, projects, day plans, tasks, activity log, time blocks, milestones. */
@Dao
interface ProjectsDao {
    // ---------------- categories
    @Query("SELECT * FROM project_category ORDER BY sort, id")
    fun categories(): Flow<List<ProjectCategory>>

    @Query("SELECT * FROM project_category ORDER BY sort, id")
    suspend fun categoriesNow(): List<ProjectCategory>

    @Upsert
    suspend fun upsertCategory(c: ProjectCategory): Long

    /** Projects of a deleted category fall back to Personal (null). Built-ins can't be deleted. */
    @Transaction
    suspend fun deleteCategory(id: Long) {
        if (id == ProjectCategory.WORK || id == ProjectCategory.PERSONAL) return
        clearCategory(id)
        deleteCategoryRow(id)
    }

    @Query("UPDATE project SET categoryId = NULL WHERE categoryId = :id")
    suspend fun clearCategory(id: Long)

    @Query("DELETE FROM project_category WHERE id = :id")
    suspend fun deleteCategoryRow(id: Long)

    // ---------------- projects
    /** All projects incl. archived; filter by status in the UI. */
    @Query("SELECT * FROM project ORDER BY CASE status WHEN 'Active' THEN 0 WHEN 'Paused' THEN 1 WHEN 'Completed' THEN 2 ELSE 3 END, sort, id")
    fun allProjects(): Flow<List<Project>>

    @Query("SELECT * FROM project WHERE id = :id")
    fun project(id: Long): Flow<Project?>

    @Query("SELECT * FROM project WHERE id = :id")
    suspend fun projectNow(id: Long): Project?

    /** Keeps the legacy `archived` flag in sync with status. */
    suspend fun saveProject(p: Project): Long = upsertProjectRow(p.copy(archived = p.status == "Archived"))

    @Upsert
    suspend fun upsertProjectRow(p: Project): Long

    /** Deletes a project and everything that only belongs to it (plans, tasks, blocks). Activity/achievements keep their text but lose the link. */
    @Transaction
    suspend fun deleteProjectCascade(id: Long) {
        deleteDayPlansOf(id); deletePlanTasksOf(id)
        unlinkBlocks(id); unlinkActivity(id); unlinkAchievements(id); unlinkLearning(id); unlinkMaterials(id)
        deleteProjectRow(id)
    }

    @Query("DELETE FROM project WHERE id = :id") suspend fun deleteProjectRow(id: Long)
    @Query("DELETE FROM day_plan WHERE projectId = :id") suspend fun deleteDayPlansOf(id: Long)
    @Query("DELETE FROM plan_task WHERE projectId = :id") suspend fun deletePlanTasksOf(id: Long)
    @Query("UPDATE time_block SET projectId = NULL WHERE projectId = :id") suspend fun unlinkBlocks(id: Long)
    @Query("UPDATE activity SET projectId = NULL WHERE projectId = :id") suspend fun unlinkActivity(id: Long)
    @Query("UPDATE achievement SET projectId = NULL WHERE projectId = :id") suspend fun unlinkAchievements(id: Long)
    @Query("UPDATE learning_entry SET projectId = NULL WHERE projectId = :id") suspend fun unlinkLearning(id: Long)
    @Query("UPDATE material SET projectId = NULL WHERE projectId = :id") suspend fun unlinkMaterials(id: Long)

    // ---------------- milestones (project stages; table from v2)
    @Query("SELECT * FROM milestone WHERE projectId = :projectId ORDER BY sort, id")
    fun milestonesOf(projectId: Long): Flow<List<Milestone>>

    @Upsert suspend fun upsertMilestone(m: Milestone): Long
    @Delete suspend fun deleteMilestone(m: Milestone)

    // ---------------- day plans
    @Query("SELECT * FROM day_plan WHERE projectId = :projectId AND day BETWEEN :from AND :to ORDER BY day")
    fun dayPlans(projectId: Long, from: Long, to: Long): Flow<List<DayPlan>>

    /** All projects' plans in a range (Day Timeline, weekly reports). */
    @Query("SELECT * FROM day_plan WHERE day BETWEEN :from AND :to ORDER BY day")
    fun dayPlansRange(from: Long, to: Long): Flow<List<DayPlan>>

    @Query("SELECT * FROM day_plan WHERE projectId = :projectId AND day = :day")
    suspend fun dayPlanNow(projectId: Long, day: Long): DayPlan?

    @Upsert suspend fun upsertDayPlan(p: DayPlan): Long
    @Delete suspend fun deleteDayPlan(p: DayPlan)

    // ---------------- tasks
    @Query("SELECT * FROM plan_task WHERE projectId = :projectId AND (day BETWEEN :from AND :to OR day IS NULL) ORDER BY day IS NULL, day, done, sort, id")
    fun tasksForWeek(projectId: Long, from: Long, to: Long): Flow<List<PlanTask>>

    @Query("SELECT * FROM plan_task WHERE projectId = :projectId ORDER BY day IS NULL, day, done, sort, id")
    fun tasksOf(projectId: Long): Flow<List<PlanTask>>

    @Query("SELECT * FROM plan_task WHERE day BETWEEN :from AND :to ORDER BY day, done, sort, id")
    fun tasksRange(from: Long, to: Long): Flow<List<PlanTask>>

    /** Unfinished tasks scheduled before [day] — candidates for an explicit carry-over prompt. */
    @Query("SELECT * FROM plan_task WHERE projectId = :projectId AND done = 0 AND day IS NOT NULL AND day < :day ORDER BY day, sort")
    fun overdue(projectId: Long, day: Long): Flow<List<PlanTask>>

    @Upsert suspend fun upsertTask(t: PlanTask): Long
    @Update suspend fun updateTasks(t: List<PlanTask>)
    @Delete suspend fun deleteTask(t: PlanTask)
    @Insert suspend fun insertTasks(t: List<PlanTask>)

    // ---------------- activity log
    @Query("SELECT * FROM activity WHERE projectId = :projectId AND day BETWEEN :from AND :to ORDER BY day, ts")
    fun activityOf(projectId: Long, from: Long, to: Long): Flow<List<ActivityEntry>>

    @Query("SELECT * FROM activity WHERE day BETWEEN :from AND :to ORDER BY day, ts")
    fun activityRange(from: Long, to: Long): Flow<List<ActivityEntry>>

    @Query("SELECT * FROM activity WHERE projectId = :projectId ORDER BY day DESC, ts DESC")
    fun activityAllOf(projectId: Long): Flow<List<ActivityEntry>>

    @Upsert suspend fun upsertActivity(a: ActivityEntry): Long
    @Delete suspend fun deleteActivity(a: ActivityEntry)

    // ---------------- time blocks (Day Timeline across projects)
    @Query("SELECT * FROM time_block WHERE day = :day ORDER BY start, id")
    fun blocksOn(day: Long): Flow<List<TimeBlock>>

    @Query("SELECT * FROM time_block WHERE day BETWEEN :from AND :to ORDER BY day, start, id")
    fun blocksRange(from: Long, to: Long): Flow<List<TimeBlock>>

    @Query("SELECT * FROM time_block WHERE projectId = :projectId AND day BETWEEN :from AND :to ORDER BY day, start")
    fun blocksOf(projectId: Long, from: Long, to: Long): Flow<List<TimeBlock>>

    @Upsert suspend fun upsertBlock(b: TimeBlock): Long
    @Delete suspend fun deleteBlock(b: TimeBlock)

    // ---------------- achievements of a project
    @Query("SELECT * FROM achievement WHERE projectId = :projectId ORDER BY day DESC, id DESC")
    fun achievementsOf(projectId: Long): Flow<List<Achievement>>

    // ---------------- added for the Projects UI
    @Query("SELECT * FROM achievement WHERE day BETWEEN :from AND :to ORDER BY day, id")
    fun achievementsRange(from: Long, to: Long): Flow<List<Achievement>>

    @Upsert suspend fun upsertAchievement(a: Achievement): Long
    @Delete suspend fun deleteAchievement(a: Achievement)

    @Query("SELECT projectId, MAX(day) AS day FROM activity WHERE projectId IS NOT NULL GROUP BY projectId")
    fun lastActivityDays(): Flow<List<ProjectLastDay>>

    /** Done tasks (any day, incl. backlog) — finished-in-range filtering happens in GrowStats. */
    @Query("SELECT * FROM plan_task WHERE (day BETWEEN :from AND :to) OR (done = 1 AND doneTs BETWEEN :fromTs AND :toTs) ORDER BY day, sort, id")
    fun tasksTouching(from: Long, to: Long, fromTs: Long, toTs: Long): Flow<List<PlanTask>>

    @Query("SELECT * FROM plan_task WHERE projectId = :projectId AND day IS NULL ORDER BY done, sort, id")
    fun backlogOf(projectId: Long): Flow<List<PlanTask>>

    @Query("SELECT * FROM time_block WHERE day = :day")
    suspend fun blocksOnNow(day: Long): List<TimeBlock>

    /** Cross-project "Today": tasks scheduled on [day] (taken into work), unfinished first. */
    @Query("SELECT * FROM plan_task WHERE day = :day ORDER BY done, sort, id")
    fun tasksOnDay(day: Long): Flow<List<PlanTask>>

    /** All open backlog tasks (day = null) — the "Pull from backlog" picker groups them by project. */
    @Query("SELECT * FROM plan_task WHERE day IS NULL AND done = 0 ORDER BY projectId, sort, id")
    fun openBacklog(): Flow<List<PlanTask>>

    /** Every task (Grow → Tasks filters/groups in memory — the list is small). */
    @Query("SELECT * FROM plan_task ORDER BY day IS NULL, day, done, sort, id")
    fun allTasks(): Flow<List<PlanTask>>
}
