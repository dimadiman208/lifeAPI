package com.lifeapi.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WaterLog::class, Supplement::class, Intake::class, SleepLog::class, Meal::class,
        Account::class, Txn::class, Budget::class, NetWorthPoint::class,
        WorkoutTemplate::class, TemplateExercise::class, WorkoutSession::class, WorkoutSet::class,
        Habit::class, HabitCheck::class, LearningLog::class, Goal::class, Profile::class, RecentLog::class,
        // v2
        WeightLog::class, Food::class, Exercise::class, Category::class, Recurring::class,
        SavingsGoal::class, SavingsMove::class, CryptoHolding::class, Project::class, Milestone::class,
        Task::class, TaskDone::class, CheckItem::class, FocusSession::class, JournalEntry::class,
        WishItem::class, InsightFeedback::class,
        // v3
        SuppRule::class,
        // v6
        CatRule::class,
        // v8 — Grow v3
        ProjectCategory::class, DayPlan::class, PlanTask::class, ActivityEntry::class, TimeBlock::class,
        Skill::class, SkillTopic::class, LearningEntry::class, Achievement::class, Material::class, MaterialNote::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3, spec = Migration2to3::class), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8, spec = Migration7to8::class)],
)
abstract class LifeDatabase : RoomDatabase() {
    abstract fun dao(): LifeDao
    abstract fun body(): BodyDao
    abstract fun train(): TrainDao
    abstract fun money(): MoneyDao
    abstract fun grow(): GrowDao
    abstract fun bank(): BankDao
    abstract fun food(): FoodDao
    abstract fun projects(): ProjectsDao
    abstract fun growth(): GrowthDao
    abstract fun learn(): LearnDao

    companion object {
        const val NAME = "lifeapi.db"
        fun build(context: Context): LifeDatabase =
            Room.databaseBuilder(context, LifeDatabase::class.java, NAME).build()
    }
}

/** Seeds one base rule per supplement from its old single schedule and keys existing intakes to that dose. */
class Migration2to3 : androidx.room.migration.AutoMigrationSpec {
    override fun onPostMigrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO supp_rule (supplementId, days, base, doses, sort) " +
                "SELECT id, days, 1, time || '*1*' || REPLACE(REPLACE(dose, '*', ''), ';', ','), 0 FROM supplement"
        )
        db.execSQL("UPDATE intake SET doseKey = COALESCE((SELECT time FROM supplement WHERE supplement.id = intake.supplementId), '')")
    }
}

/**
 * Grow v3. Seeds the built-in project categories and moves old Grow data into the new model ONCE, so nothing shows
 * twice: learning logs → learning_entry (legacyLogId), done-journal → activity, focus sessions → activity (with
 * minutes), one-off project tasks + next actions → plan_task. Old tables stay untouched (read-only legacy).
 */
class Migration7to8 : androidx.room.migration.AutoMigrationSpec {
    override fun onPostMigrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO project_category (id, name, weekly, builtIn, sort) VALUES (1, 'Work', 1, 1, 0), (2, 'Personal', 0, 1, 1)")
        db.execSQL("UPDATE project SET status = 'Archived' WHERE archived = 1")
        db.execSQL(
            "INSERT INTO learning_entry (day, ts, learned, minutes, legacyLogId) " +
                "SELECT day, ts, topic, minutes, id FROM learning"
        )
        db.execSQL(
            "INSERT INTO activity (projectId, day, text, ts) " +
                "SELECT (SELECT p.id FROM project p WHERE p.name = journal.tag LIMIT 1), day, text, ts FROM journal"
        )
        db.execSQL(
            "INSERT INTO activity (projectId, day, text, minutes, ts) " +
                "SELECT f.projectId, f.day, COALESCE('Focus: ' || (SELECT t.title FROM task t WHERE t.id = f.taskId), 'Focus session'), f.minutes, f.startTs FROM focus f"
        )
        db.execSQL(
            "INSERT INTO plan_task (projectId, day, title, done, doneTs, sort, createdTs, milestoneId) " +
                "SELECT projectId, day, title, CASE WHEN doneTs IS NULL THEN 0 ELSE 1 END, doneTs, 0, createdTs, milestoneId " +
                "FROM task WHERE projectId IS NOT NULL AND repeatDays = 0"
        )
        db.execSQL(
            "INSERT INTO plan_task (projectId, day, title, done, sort, createdTs) " +
                "SELECT p.id, NULL, p.nextAction, 0, -1, p.nextSince FROM project p " +
                "WHERE p.nextAction != '' AND NOT EXISTS (SELECT 1 FROM plan_task x WHERE x.projectId = p.id AND x.title = p.nextAction)"
        )
    }
}
