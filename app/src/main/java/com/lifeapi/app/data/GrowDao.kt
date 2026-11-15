package com.lifeapi.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GrowDao {
    // projects
    @Query("SELECT * FROM project WHERE archived = 0 ORDER BY sort, id")
    fun projects(): Flow<List<Project>>

    @Query("SELECT * FROM project WHERE id = :id")
    fun project(id: Long): Flow<Project?>

    @Upsert
    suspend fun upsertProject(p: Project): Long

    @Query("DELETE FROM project WHERE id = :id")
    suspend fun deleteProject(id: Long)

    @Query("SELECT * FROM milestone ORDER BY projectId, sort, id")
    fun milestones(): Flow<List<Milestone>>

    @Upsert
    suspend fun upsertMilestone(m: Milestone): Long

    @Delete
    suspend fun deleteMilestone(m: Milestone)

    // tasks
    @Query("SELECT * FROM task ORDER BY top DESC, CASE WHEN time = '' THEN 1 ELSE 0 END, time, id")
    fun tasks(): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE id = :id")
    fun task(id: Long): Flow<Task?>

    @Upsert
    suspend fun upsertTask(t: Task): Long

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("SELECT * FROM task_done WHERE day BETWEEN :from AND :to")
    fun taskDone(from: Long, to: Long): Flow<List<TaskDone>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskDone(d: TaskDone)

    @Query("DELETE FROM task_done WHERE taskId = :taskId AND day = :day")
    suspend fun deleteTaskDone(taskId: Long, day: Long)

    @Query("SELECT COUNT(*) FROM task_done WHERE taskId = :taskId")
    fun taskDoneCount(taskId: Long): Flow<Int>

    @Query("SELECT * FROM check_item WHERE taskId = :taskId ORDER BY sort, id")
    fun checkItems(taskId: Long): Flow<List<CheckItem>>

    @Upsert
    suspend fun upsertCheckItem(c: CheckItem): Long

    @Delete
    suspend fun deleteCheckItem(c: CheckItem)

    @Query("UPDATE check_item SET done = 0 WHERE taskId = :taskId")
    suspend fun resetChecklist(taskId: Long)

    // focus
    @Query("SELECT * FROM focus WHERE day BETWEEN :from AND :to ORDER BY startTs")
    fun focusRange(from: Long, to: Long): Flow<List<FocusSession>>

    @Insert
    suspend fun insertFocus(f: FocusSession): Long

    // journal
    @Query("SELECT * FROM journal WHERE day BETWEEN :from AND :to ORDER BY ts DESC")
    fun journalRange(from: Long, to: Long): Flow<List<JournalEntry>>

    @Insert
    suspend fun insertJournal(j: JournalEntry): Long

    @Delete
    suspend fun deleteJournal(j: JournalEntry)

    // wishlist
    @Query("SELECT * FROM wish WHERE bought = 0 ORDER BY priority, id")
    fun wishes(): Flow<List<WishItem>>

    @Upsert
    suspend fun upsertWish(w: WishItem): Long

    @Delete
    suspend fun deleteWish(w: WishItem)

    // learning (skills)
    @Query("SELECT * FROM learning ORDER BY ts DESC")
    fun allLearning(): Flow<List<LearningLog>>

    // insight feedback
    @Upsert
    suspend fun upsertFeedback(f: InsightFeedback)

    @Query("SELECT * FROM insight_feedback")
    fun feedback(): Flow<List<InsightFeedback>>

    // ---- fork A additions
    @Query("SELECT * FROM task_done")
    fun allTaskDone(): Flow<List<TaskDone>>

    @Query("SELECT * FROM focus ORDER BY startTs")
    fun allFocus(): Flow<List<FocusSession>>

    @Query("SELECT * FROM journal ORDER BY ts DESC")
    fun allJournal(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM habit_check")
    fun allChecks(): Flow<List<HabitCheck>>

    @Query("SELECT * FROM wset ORDER BY ts")
    fun allSets(): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM wish WHERE id = :id")
    suspend fun wishNow(id: Long): WishItem?

    @Query("SELECT * FROM milestone WHERE projectId = :pid ORDER BY sort, id")
    suspend fun milestonesFor(pid: Long): List<Milestone>

    @Query("SELECT * FROM check_item")
    fun allCheckItems(): Flow<List<CheckItem>>

    @Query("SELECT * FROM check_item WHERE taskId = :taskId")
    suspend fun allCheckItemsNow(taskId: Long): List<CheckItem>
}
