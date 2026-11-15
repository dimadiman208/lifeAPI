package com.lifeapi.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Days are stored as LocalDate.toEpochDay(); timestamps as epoch millis. Money is in cents. */

@Entity(tableName = "water", indices = [Index("day")])
data class WaterLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val ts: Long,
    val ml: Int,
)

/** slot: 0 = morning & midday, 1 = evening */
@Entity(tableName = "supplement")
data class Supplement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String,
    val slot: Int,
    val time: String,
    val sort: Int = 0,
    val archived: Boolean = false,
    /** Bitmask Mon=1 … Sun=64. */
    @ColumnInfo(defaultValue = "127") val days: Int = 127,
    @ColumnInfo(defaultValue = "0") val withFood: Boolean = false,
    @ColumnInfo(defaultValue = "1") val reminder: Boolean = true,
    val stock: Int? = null,
    @ColumnInfo(defaultValue = "10") val stockAlert: Int = 10,
) {
    fun onDay(day: Long): Boolean = days and (1 shl (Days.date(day).dayOfWeek.value - 1)) != 0
}

/** One row per dose: (supplement, day, doseKey) where doseKey = the dose's planned "HH:MM". */
@Entity(tableName = "intake", indices = [Index(value = ["supplementId", "day", "doseKey"], unique = true), Index("day")])
data class Intake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplementId: Long,
    val day: Long,
    val ts: Long,
    /** Planned time of the dose this intake covers ("HH:MM"). */
    @ColumnInfo(defaultValue = "") val doseKey: String = "",
    /** Amount actually taken (units of the dose), 0 = as planned. */
    @ColumnInfo(defaultValue = "0") val amount: Double = 0.0,
    /** Skipped on purpose: never breaks a streak, doesn't count as taken. */
    @ColumnInfo(defaultValue = "0") val skipped: Boolean = false,
)

/** day = the morning you woke up. */
@Entity(tableName = "sleep", indices = [Index(value = ["day"], unique = true)])
data class SleepLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val startTs: Long,
    val endTs: Long,
    /** 1–5, null = not rated. */
    val rating: Int? = null,
    /** Comma-separated tags. */
    @ColumnInfo(defaultValue = "") val tags: String = "",
) {
    val minutes: Int get() = ((endTs - startTs) / 60000L).toInt()
}

@Entity(tableName = "meal", indices = [Index("day"), Index(value = ["extId"], unique = true)])
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val ts: Long,
    val slot: String,
    val items: String,
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    @ColumnInfo(defaultValue = "Manual") val source: String = "Manual",
    /** External id for synced rows (e.g. "fs:<food_entry_id>") — dedupes repeated syncs. */
    val extId: String? = null,
    /** v4: product this entry was logged from and its amount — lets an entry be edited/copied with recalculation. */
    val foodId: Long? = null,
    val grams: Double? = null,
)

@Entity(tableName = "account", indices = [Index(value = ["extId"], unique = true)])
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val balanceCents: Long,
    val hue: Int,
    val sort: Int = 0,
    /** Linked bank account: "eb:<account uid>" (Enable Banking). Null = manual account. */
    val extId: String? = null,
    /** "Manual" | "Bank" */
    @ColumnInfo(defaultValue = "Manual") val source: String = "Manual",
    val iban: String? = null,
    val currency: String? = null,
    @ColumnInfo(defaultValue = "0") val lastSync: Long = 0,
    /** "Current" | "Savings" | "Cash" | "Crypto" | "Other" — set by the user, also on bank-synced accounts. */
    @ColumnInfo(defaultValue = "Current") val kind: String = "Current",
) {
    val isBank: Boolean get() = source == "Bank"
    val isSavings: Boolean get() = kind == "Savings"
}

val ACCOUNT_KINDS = listOf("Current", "Savings", "Cash", "Other")

/** amountCents < 0 = expense, > 0 = income. */
@Entity(tableName = "txn", indices = [Index("day"), Index(value = ["extId"], unique = true)])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val ts: Long,
    val name: String,
    val category: String,
    val amountCents: Long,
    val accountId: Long? = null,
    /** Transfers: money leaves accountId (amountCents < 0) and arrives here. */
    val toAccountId: Long? = null,
    val recurringId: Long? = null,
    @ColumnInfo(defaultValue = "") val note: String = "",
    /** Bank-synced rows: "eb:<account uid>:<entry reference or content hash>". Dedupes repeated syncs. */
    val extId: String? = null,
    /** Bank says not yet booked (PDNG); replaced/updated once booked. */
    @ColumnInfo(defaultValue = "0") val pending: Boolean = false,
    /** v6: the user picked this category by hand — automatic categorisation never overwrites it. */
    @ColumnInfo(defaultValue = "0") val userCategory: Boolean = false,
    /** v6: normalised merchant key (lowercase) used for rules and grouping. */
    @ColumnInfo(defaultValue = "") val merchant: String = "",
    /** v6: ISO 18245 merchant category code from the bank, if any. */
    @ColumnInfo(defaultValue = "") val mcc: String = "",
) {
    val isTransfer: Boolean get() = toAccountId != null
}

/** v6: remembered "merchant → category" choice; applied before MCC and keywords. */
@Entity(tableName = "cat_rule")
data class CatRule(
    @PrimaryKey val merchant: String,
    val category: String,
    val ts: Long,
)

@Entity(tableName = "budget")
data class Budget(
    @PrimaryKey val category: String,
    val limitCents: Long,
)

@Entity(tableName = "networth")
data class NetWorthPoint(
    @PrimaryKey val day: Long,
    val cents: Long,
)

@Entity(tableName = "template")
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sort: Int = 0,
    /** Scheduled weekdays bitmask (Mon=1 … Sun=64), 0 = unscheduled. */
    @ColumnInfo(defaultValue = "0") val days: Int = 0,
)

@Entity(tableName = "template_ex", indices = [Index("templateId")])
data class TemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val name: String,
    val sets: Int,
    val reps: Int,
    val kg: Double,
    val sort: Int = 0,
)

@Entity(tableName = "session")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    val name: String,
    val day: Long,
    val startTs: Long,
    val endTs: Long? = null,
    /** When paused: the moment pause started. */
    val pausedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val pausedMs: Long = 0,
) {
    /** Active (non-paused) duration in ms up to [now]. */
    fun activeMs(now: Long): Long = ((endTs ?: pausedAt ?: now) - startTs - pausedMs).coerceAtLeast(0)
}

@Entity(tableName = "wset", indices = [Index("sessionId"), Index("exercise")])
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exercise: String,
    val exIndex: Int,
    val kg: Double,
    val reps: Int,
    val ts: Long,
    val warmup: Boolean = false,
) {
    /** Epley estimated 1RM. */
    val e1rm: Double get() = if (reps <= 1) kg else kg * (1 + reps / 30.0)
}

@Entity(tableName = "habit")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sort: Int = 0,
    val archived: Boolean = false,
)

@Entity(tableName = "habit_check", primaryKeys = ["habitId", "day"], indices = [Index("day")])
data class HabitCheck(
    val habitId: Long,
    val day: Long,
)

@Entity(tableName = "learning", indices = [Index("day")])
data class LearningLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val ts: Long,
    val topic: String,
    val minutes: Int,
)

@Entity(tableName = "goal")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val current: Double,
    val target: Double,
    val unit: String,
    val sort: Int = 0,
    /** 30 or 90 day horizon. */
    @ColumnInfo(defaultValue = "30") val horizonDays: Int = 30,
    @ColumnInfo(defaultValue = "0") val startDay: Long = 0,
    /** Live link: "" manual, "habit:<id>", "lift:<exercise>" (target kg e1RM), "project:<id>", "learning:<topic>" (hours). */
    @ColumnInfo(defaultValue = "") val link: String = "",
    @ColumnInfo(defaultValue = "") val note: String = "",
)

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 0,
    val name: String = "Denis",
    val waterGoalMl: Int = 2500,
    val kcalGoal: Int = 2400,
    val proteinG: Int = 160,
    val carbsG: Int = 250,
    val fatG: Int = 80,
    val dailySpendCents: Long = 6500,
    val learnGoalMin: Int = 45,
    val sleepGoalMin: Int = 450,
    val restSec: Int = 150,
    val hideBalances: Boolean = false,
    // ---- v2
    @ColumnInfo(defaultValue = "250,500,750") val waterQuick: String = "250,500,750",
    val weightGoalKg: Double? = null,
    val weightStartKg: Double? = null,
    /** Target weekly change, kg (+ bulk, − cut). */
    @ColumnInfo(defaultValue = "0.25") val weightRateKg: Double = 0.25,
    @ColumnInfo(defaultValue = "") val weightPlan: String = "",
    /** Today cards, ordered; "-" prefix = hidden. */
    @ColumnInfo(defaultValue = "") val todayCards: String = "",
    /** Enabled Day-Score goals, comma ids. Empty = defaults. */
    @ColumnInfo(defaultValue = "") val dayGoals: String = "",
    /** 4 Quick Log tile ids. */
    @ColumnInfo(defaultValue = "") val quickTiles: String = "",
    @ColumnInfo(defaultValue = "EUR") val currency: String = "EUR",
    @ColumnInfo(defaultValue = "0") val lockEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "60") val lockAfterSec: Int = 60,
    @ColumnInfo(defaultValue = "1") val notifySupplements: Boolean = true,
    @ColumnInfo(defaultValue = "0") val notifyWindDown: Boolean = false,
    @ColumnInfo(defaultValue = "23:00") val windDown: String = "23:00",
    @ColumnInfo(defaultValue = "1") val notifyTasks: Boolean = true,
    /** Intelligence data categories allowed, comma ids. */
    @ColumnInfo(defaultValue = "") val aiCategories: String = "",
    @ColumnInfo(defaultValue = "25") val focusMin: Int = 25,
    @ColumnInfo(defaultValue = "5") val breakMin: Int = 5,
    @ColumnInfo(defaultValue = "0") val lastBackupTs: Long = 0,
)

/** One-tap repeats in Quick Log. */
@Entity(tableName = "recent_log")
data class RecentLog(
    @PrimaryKey val text: String,
    val ts: Long,
    val label: String,
    val value: String,
    val hue: Int,
)
