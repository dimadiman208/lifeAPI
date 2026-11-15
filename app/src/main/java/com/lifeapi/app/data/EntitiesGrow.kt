package com.lifeapi.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Grow v3 (DB v8). See dev-docs/GROW_V3.md for the rules:
 *  Goal (DayPlan.goal) = what you planned to achieve · PlanTask = what needs doing ·
 *  ActivityEntry = what you actually did · Result (DayPlan.result / Achievement) = what came out of it.
 *  Work hours are a plan; only ActivityEntry.minutes and TimeBlock.actualMinutes count as spent time.
 */

/** Project type. Built-ins: 1 Work (weekly planning), 2 Personal. Deleting a category moves its projects to Personal. */
@Entity(tableName = "project_category")
data class ProjectCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Weekly Mon–Fri planning (Work-style). */
    @ColumnInfo(defaultValue = "0") val weekly: Boolean = false,
    @ColumnInfo(defaultValue = "0") val builtIn: Boolean = false,
    @ColumnInfo(defaultValue = "0") val sort: Int = 0,
) {
    companion object {
        const val WORK = 1L
        const val PERSONAL = 2L
    }
}

/** One project's plan for one day: the single main goal, the expected result, the real result and a note. */
@Entity(tableName = "day_plan", indices = [Index(value = ["projectId", "day"], unique = true), Index("day")])
data class DayPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val day: Long,
    @ColumnInfo(defaultValue = "") val goal: String = "",
    @ColumnInfo(defaultValue = "") val expectedResult: String = "",
    /** Non-blank = the day's goal was achieved with this result. */
    @ColumnInfo(defaultValue = "") val result: String = "",
    @ColumnInfo(defaultValue = "") val note: String = "",
)

/** A task of a project. day = null → backlog. Carry-over is explicit: a moved task keeps [movedFrom]. */
@Entity(tableName = "plan_task", indices = [Index("projectId"), Index("day")])
data class PlanTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val day: Long? = null,
    val title: String,
    @ColumnInfo(defaultValue = "0") val done: Boolean = false,
    val doneTs: Long? = null,
    @ColumnInfo(defaultValue = "0") val sort: Int = 0,
    /** Day this task was carried from (last move), null if never moved. */
    val movedFrom: Long? = null,
    @ColumnInfo(defaultValue = "0") val createdTs: Long = 0,
    /** Optional milestone (project stage) it belongs to. */
    val milestoneId: Long? = null,
)

/**
 * What you actually did. minutes is optional time spent. If [blockId] is set the entry describes a time block and
 * its own minutes are NOT counted (the block's actualMinutes is) — see GrowStats.loggedMinutes.
 */
@Entity(tableName = "activity", indices = [Index("day"), Index("projectId"), Index("skillId"), Index("materialId")])
data class ActivityEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long? = null,
    val day: Long,
    val text: String,
    val minutes: Int? = null,
    val ts: Long,
    val skillId: Long? = null,
    val materialId: Long? = null,
    val blockId: Long? = null,
)

/** Planned time slot on the Day Timeline. actualMinutes = null → not logged (never auto-counted). */
@Entity(tableName = "time_block", indices = [Index("day"), Index("projectId")])
data class TimeBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    /** "HH:MM" */
    val start: String,
    /** "HH:MM", after start (same day). */
    val end: String,
    val projectId: Long? = null,
    val title: String,
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(defaultValue = "") val result: String = "",
    val actualMinutes: Int? = null,
)

/** A growth direction (PostgreSQL, English, Communication …). No levels or percentages. */
@Entity(tableName = "skill")
data class Skill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "") val goal: String = "",
    @ColumnInfo(defaultValue = "") val note: String = "",
    /** Active | Paused | Done */
    @ColumnInfo(defaultValue = "Active") val status: String = "Active",
    @ColumnInfo(defaultValue = "0") val sort: Int = 0,
    @ColumnInfo(defaultValue = "0") val createdTs: Long = 0,
)

/** Concrete topic / practice item inside a skill. kind: Topic | Practice */
@Entity(tableName = "skill_topic", indices = [Index("skillId")])
data class SkillTopic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skillId: Long,
    val title: String,
    @ColumnInfo(defaultValue = "0") val done: Boolean = false,
    val doneTs: Long? = null,
    @ColumnInfo(defaultValue = "") val note: String = "",
    @ColumnInfo(defaultValue = "Topic") val kind: String = "Topic",
    @ColumnInfo(defaultValue = "0") val sort: Int = 0,
)

/**
 * Learning journal entry. Any field may be blank — a quick thought is just [learned].
 * legacyLogId: copied once from the old `learning` table (v7) so nothing shows twice.
 */
@Entity(tableName = "learning_entry", indices = [Index("day"), Index("skillId"), Index("projectId"), Index("materialId"), Index(value = ["legacyLogId"], unique = true)])
data class LearningEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skillId: Long? = null,
    val day: Long,
    val ts: Long,
    @ColumnInfo(defaultValue = "") val learned: String = "",
    @ColumnInfo(defaultValue = "") val practiced: String = "",
    @ColumnInfo(defaultValue = "") val understood: String = "",
    @ColumnInfo(defaultValue = "") val unclear: String = "",
    val minutes: Int? = null,
    val projectId: Long? = null,
    val materialId: Long? = null,
    val topicId: Long? = null,
    val legacyLogId: Long? = null,
)

/** A concrete result worth reporting (1-on-1s, weekly/monthly reports). */
@Entity(tableName = "achievement", indices = [Index("day"), Index("projectId"), Index("skillId"), Index("materialId")])
data class Achievement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val title: String,
    @ColumnInfo(defaultValue = "") val detail: String = "",
    val projectId: Long? = null,
    val skillId: Long? = null,
    val materialId: Long? = null,
    @ColumnInfo(defaultValue = "0") val ts: Long = 0,
)

/**
 * Learn library item. kind: PDF | Article | Docs | GitHub | YouTube | Link.
 * PDFs: fileUri is a content:// URI with a persisted SAF read permission (takePersistableUriPermission) — never a raw path.
 * status: To Learn | In Progress | Completed | Archived.
 */
@Entity(tableName = "material", indices = [Index("skillId"), Index("projectId"), Index("status")])
data class Material(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: String,
    val url: String? = null,
    val fileUri: String? = null,
    @ColumnInfo(defaultValue = "") val fileName: String = "",
    @ColumnInfo(defaultValue = "") val description: String = "",
    /** Comma-separated, lowercase. */
    @ColumnInfo(defaultValue = "") val tags: String = "",
    @ColumnInfo(defaultValue = "To Learn") val status: String = "To Learn",
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    val skillId: Long? = null,
    val projectId: Long? = null,
    @ColumnInfo(defaultValue = "0") val addedTs: Long = 0,
    @ColumnInfo(defaultValue = "0") val lastOpenedTs: Long = 0,
    /** PDF: last page index (0-based). */
    @ColumnInfo(defaultValue = "0") val progressPage: Int = 0,
    @ColumnInfo(defaultValue = "0") val pageCount: Int = 0,
    val completedTs: Long? = null,
)

/** kind: Idea | Example | Learned | Try | Conclusion */
@Entity(tableName = "material_note", indices = [Index("materialId")])
data class MaterialNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,
    val ts: Long,
    val text: String,
    @ColumnInfo(defaultValue = "Idea") val kind: String = "Idea",
    /** Optional page of a PDF the note refers to. */
    val page: Int? = null,
)
