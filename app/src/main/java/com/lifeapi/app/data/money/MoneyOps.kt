package com.lifeapi.app.data.money

import androidx.room.withTransaction
import com.lifeapi.app.data.Category
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.LifeRepository
import com.lifeapi.app.data.NetWorthPoint
import com.lifeapi.app.data.Recurring
import com.lifeapi.app.data.Txn
import com.lifeapi.app.data.Undo
import java.util.Locale

val DEFAULT_EXPENSE = AUTO_EXPENSE
val DEFAULT_INCOME = listOf("Salary", "Freelance", "Refund", "Gift")
const val TRANSFER = "Transfer"

/** Recurring kinds. */
object RK { const val SUB = 0; const val BILL = 1; const val INCOME = 2; const val TRANSFER = 3 }

val PERIOD_LABEL = mapOf("W" to "weekly", "M" to "monthly", "Y" to "yearly")

fun advance(day: Long, period: String): Long {
    val d = Days.date(day)
    return when (period) {
        "W" -> d.plusWeeks(1)
        "Y" -> d.plusYears(1)
        else -> d.plusMonths(1)
    }.toEpochDay()
}

/** Monthly equivalent of a recurring amount. */
fun Recurring.monthlyCents(): Long = when (period) {
    "W" -> amountCents * 52 / 12
    "Y" -> amountCents / 12
    else -> amountCents
}

fun Recurring.signedCents(): Long = when (kind) {
    RK.INCOME -> amountCents
    RK.TRANSFER -> -amountCents
    else -> -amountCents
}

private val TRANSFER_KW = Regex("own account|between accounts|transfer to|transfer from|internal transfer|to savings|savings vault|top-up|topup|top up|pārskaitījums starp|между своими|card top")

fun looksLikeTransfer(name: String, ownAccounts: List<String>): Boolean {
    val s = name.lowercase(Locale.ROOT)
    return TRANSFER_KW.containsMatchIn(s) || ownAccounts.any { it.length >= 3 && s.contains(it.lowercase(Locale.ROOT)) }
}

/** Money writes that LifeRepository doesn't cover: transfers on both accounts, recurring, categories. */
class MoneyOps(private val repo: LifeRepository) {
    private val dao = repo.dao
    private val money = repo.money

    suspend fun snapshot() {
        val total = dao.accountsNow().sumOf { it.balanceCents }
        dao.upsertNetWorth(NetWorthPoint(Days.today(), total))
    }

    /** Insert any txn (expense / income / transfer) and apply balances. */
    suspend fun add(t: Txn): Undo {
        val id = repo.db.withTransaction {
            val id = dao.insertTxn(t)
            t.accountId?.let { dao.adjustBalance(it, t.amountCents) }
            t.toAccountId?.let { dao.adjustBalance(it, -t.amountCents) }
            id
        }
        snapshot()
        val saved = t.copy(id = id)
        return { delete(saved) }
    }

    suspend fun delete(t: Txn) {
        repo.db.withTransaction {
            dao.deleteTxn(t)
            t.accountId?.let { dao.adjustBalance(it, -t.amountCents) }
            t.toAccountId?.let { dao.adjustBalance(it, t.amountCents) }
        }
        snapshot()
    }

    /** Imported rows are history: they're attributed to an account but don't move its balance. */
    suspend fun importRows(rows: List<Txn>) {
        repo.db.withTransaction { rows.forEach { dao.insertTxn(it) } }
    }

    /** Seeds defaults on first run and adds categories introduced later (Shopping, Travel, Cash, Fees) without touching custom ones. */
    suspend fun ensureCategories() {
        if (money.countCategories() == 0) {
            DEFAULT_EXPENSE.forEachIndexed { i, c -> money.upsertCategory(Category(c, income = false, sort = i)) }
            DEFAULT_INCOME.forEachIndexed { i, c -> money.upsertCategory(Category(c, income = true, sort = i)) }
        } else {
            val have = money.categoryNames().toSet()
            DEFAULT_EXPENSE.forEachIndexed { i, c -> if (c !in have) money.upsertCategory(Category(c, income = false, sort = i)) }
        }
    }

    suspend fun saveRecurring(r: Recurring): Long = money.upsertRecurring(r).let { if (r.id != 0L) r.id else it }

    /** Auto-log every due occurrence (catching up missed ones) and advance nextDay. */
    suspend fun runDue(today: Long = Days.today()) {
        val due = money.dueRecurring(today)
        for (r0 in due) {
            var r = r0
            var guard = 0
            while (r.nextDay <= today && guard++ < 60) {
                val ts = Days.tsOf(r.nextDay, java.time.LocalTime.of(9, 0))
                add(
                    Txn(
                        day = r.nextDay, ts = ts, name = r.name,
                        category = if (r.kind == RK.TRANSFER) TRANSFER else r.category,
                        amountCents = r.signedCents(), accountId = r.accountId,
                        toAccountId = if (r.kind == RK.TRANSFER) r.toAccountId else null,
                        recurringId = r.id,
                    )
                )
                r = r.copy(nextDay = advance(r.nextDay, r.period))
            }
            money.upsertRecurring(r)
        }
    }
}
