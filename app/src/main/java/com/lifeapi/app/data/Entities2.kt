package com.lifeapi.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ======================================================= v2 tables

// ---------------- body
@Entity(tableName = "weight")
data class WeightLog(
    @PrimaryKey val day: Long,
    val kg: Double,
    val ts: Long,
)

// ---------------- nutrition
/**
 * Local product catalogue: own products ("Custom"), cached Open Food Facts ("OFF") and FatSecret ("FatSecret")
 * products. Macros per 100 g; optional serving ("1 bar" = unitGrams g). extId dedupes remote products
 * ("off:<barcode>", "fs:<food_id>"); barcode dedupes across sources.
 */
@Entity(tableName = "food", indices = [Index(value = ["extId"], unique = true), Index("barcode")])
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kcal100: Double,
    val p100: Double,
    val c100: Double,
    val f100: Double,
    @ColumnInfo(defaultValue = "") val unitName: String = "",
    @ColumnInfo(defaultValue = "0") val unitGrams: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val lastUsed: Long = 0,
    // ---- v4
    @ColumnInfo(defaultValue = "Custom") val source: String = "Custom",
    val extId: String? = null,
    @ColumnInfo(defaultValue = "") val barcode: String = "",
    @ColumnInfo(defaultValue = "") val brand: String = "",
    val fiber100: Double? = null,
    val sugar100: Double? = null,
    val salt100: Double? = null,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val useCount: Int = 0,
    @ColumnInfo(defaultValue = "") val imageUrl: String = "",
    /** True when kcal/macros came incomplete from a remote source and the user hasn't reviewed them. */
    @ColumnInfo(defaultValue = "0") val incomplete: Boolean = false,
)

// ---------------- train
@Entity(tableName = "exercise")
data class Exercise(
    @PrimaryKey val name: String,
    @ColumnInfo(defaultValue = "") val muscle: String = "",
    @ColumnInfo(defaultValue = "Barbell") val equipment: String = "Barbell",
    /** "kg × reps" | "reps" | "time" */
    @ColumnInfo(defaultValue = "kg × reps") val kind: String = "kg × reps",
    @ColumnInfo(defaultValue = "0") val createdTs: Long = 0,
)

// ---------------- money
@Entity(tableName = "category")
data class Category(
    @PrimaryKey val name: String,
    @ColumnInfo(defaultValue = "0") val income: Boolean = false,
    @ColumnInfo(defaultValue = "2") val hue: Int = 2,
    @ColumnInfo(defaultValue = "0") val sort: Int = 0,
)

/** kind: 0 subscription, 1 bill, 2 income, 3 transfer. period: "M" monthly, "Y" yearly, "W" weekly. */
@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Always positive; sign comes from kind. */
    val amountCents: Long,
    val kind: Int,
    val category: String,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    @ColumnInfo(defaultValue = "M") val period: String = "M",
    /** Next due date (epoch day). */
    val nextDay: Long,
    @ColumnInfo(defaultValue = "1") val autoLog: Boolean = true,
    @ColumnInfo(defaultValue = "0") val prevAmountCents: Long = 0,
)

@Entity(tableName = "savings_goal")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetCents: Long,
    @ColumnInfo(defaultValue = "0") val monthlyCents: Long = 0,
    val targetDay: Long? = null,
    @ColumnInfo(defaultValue = "0") val sort: Int = 0,
    /** Savings account whose money is earmarked for this goal (envelope). Null = manual goal. */
    val accountId: Long? = null,
)

/** Allocation ledger: + earmarks money into the goal, − releases it. Never moves money between accounts. */
@Entity(tableName = "savings_move", indices = [Index("goalId")])
data class SavingsMove(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val day: Long,
    val amountCents: Long,
    @ColumnInfo(defaultValue = "") val note: String = "",
)

/** Manual crypto holding. Price is entered by hand (no network). */
@Entity(tableName = "crypto")
data class CryptoHolding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sym: String,
    val qty: Double,
    val avgBuyCents: Long,
    val priceCents: Long,
    @ColumnInfo(defaultValue = "0") val prevPriceCents: Long = 0,
    @ColumnInfo(defaultValue = "Manual") val source: String = "Manual",
    @ColumnInfo(defaultValue = "0") val updatedTs: Long = 0,
)

// ---------------- grow
@Entity(tableName = "project")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDay: Long,
    val targetDay: Long? = null,
    @ColumnInfo(defaultValue = "4") val hue: Int = 4,
    @ColumnInfo(defaultValue = "") val nextAction: String = "",
    /** When the current next action was set — "stalled N days". */
    @ColumnInfo(defaultValue = "0") val nextSince: Long = 0,
    /** Legacy flag; kept in sync with [status] == "Archived" (older queries filter on it). */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val sort: Int = 0,
    // ---- v8 (Grow v3)
    @ColumnInfo(defaultValue = "") val description: String = "",
    /** ProjectCategory id; null = Personal. */
    val categoryId: Long? = null,
    /** Active | Paused | Completed | Archived */
    @ColumnInfo(defaultValue = "Active") val status: String = "Active",
    /** Emoji or 1–2 letters shown in the project badge; "" = first letter of the name. */
    @ColumnInfo(defaultValue = "") val icon: String = "",
    /** Planned working hours (a plan, never logged time). */
    @ColumnInfo(defaultValue = "09:00") val workStart: String = "09:00",
    @ColumnInfo(defaultValue = "18:00") val workEnd: String = "18:00",
    /** Working days bitmask Mon=1 … Sun=64; default Mon–Fri. */
    @ColumnInfo(defaultValue = "31") val workDays: Int = 31,
)

@Entity(tableName = "milestone", indices = [Index("projectId")])
data class Milestone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0") val sort: Int = 0,
    @ColumnInfo(defaultValue = "0") val done: Boolean = false,
)

/**
 * A task. day = scheduled day (null = Inbox). repeatDays bitmask Mon=1…Sun=64 (0 = one-off);
 * a repeating task is "done" for a day via TaskDone rows, one-offs via doneTs.
 */
@Entity(tableName = "task", indices = [Index("day"), Index("projectId")])
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val day: Long? = null,
    /** "HH:MM" or "" */
    @ColumnInfo(defaultValue = "") val time: String = "",
    @ColumnInfo(defaultValue = "0") val top: Boolean = false,
    val projectId: Long? = null,
    val milestoneId: Long? = null,
    /** Module tag: "", "Health", "Fuel", "Money", "Train", "Grow". */
    @ColumnInfo(defaultValue = "") val module: String = "",
    @ColumnInfo(defaultValue = "0") val repeatDays: Int = 0,
    val remindMin: Int? = null,
    val doneTs: Long? = null,
    @ColumnInfo(defaultValue = "0") val createdTs: Long = 0,
    @ColumnInfo(defaultValue = "") val note: String = "",
) {
    val repeating: Boolean get() = repeatDays != 0
    fun occursOn(d: Long): Boolean = if (repeating) repeatDays and (1 shl (Days.date(d).dayOfWeek.value - 1)) != 0 && (day == null || d >= day) else day == d
}

@Entity(tableName = "task_done", primaryKeys = ["taskId", "day"], indices = [Index("day")])
data class TaskDone(val taskId: Long, val day: Long, val ts: Long)

@Entity(tableName = "check_item", indices = [Index("taskId")])
data class CheckItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val text: String,
    @ColumnInfo(defaultValue = "0") val done: Boolean = false,
    @ColumnInfo(defaultValue = "0") val sort: Int = 0,
)

@Entity(tableName = "focus", indices = [Index("day"), Index("projectId")])
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val startTs: Long,
    val minutes: Int,
    val taskId: Long? = null,
    val projectId: Long? = null,
)

@Entity(tableName = "journal", indices = [Index("day")])
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val ts: Long,
    val text: String,
    @ColumnInfo(defaultValue = "") val tag: String = "",
)

/** priority 1 high … 3 low. */
@Entity(tableName = "wish")
data class WishItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val priceCents: Long,
    @ColumnInfo(defaultValue = "2") val priority: Int = 2,
    @ColumnInfo(defaultValue = "0") val savedCents: Long = 0,
    @ColumnInfo(defaultValue = "") val note: String = "",
    val goalId: Long? = null,
    @ColumnInfo(defaultValue = "0") val bought: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createdTs: Long = 0,
)

// ---------------- intelligence
@Entity(tableName = "insight_feedback")
data class InsightFeedback(
    @PrimaryKey val key: String,
    val useful: Boolean,
    val ts: Long,
)
