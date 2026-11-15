package com.lifeapi.app.vm

import android.app.Application
import com.lifeapi.app.data.Account
import com.lifeapi.app.data.Budget
import com.lifeapi.app.data.Category
import com.lifeapi.app.data.CryptoHolding
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Recurring
import com.lifeapi.app.data.Txn
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.data.money.DEFAULT_EXPENSE
import com.lifeapi.app.data.money.MoneyOps
import com.lifeapi.app.data.money.RK
import com.lifeapi.app.data.money.TRANSFER
import com.lifeapi.app.data.money.advance
import com.lifeapi.app.data.money.monthlyCents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.format.TextStyle
import java.util.Locale

data class BudgetRow(val category: String, val spent: Long, val limit: Long)

data class CoinSummary(val sym: String, val qty: Double, val valueCents: Long, val changeCents: Long)

data class MoneyLinks(
    val subsMonthly: Long,
    val recurringCount: Int,
    val savingsSaved: Long,
    val savingsTarget: Long,
    val goalCount: Int,
    val cryptoValue: Long,
    val cryptoChange: Long,
    val coins: List<CoinSummary>,
)

data class MoneyState(
    val hidden: Boolean,
    val accounts: List<Account>,
    val netWorth: Long,
    val period: String,
    val series: List<Float>,
    val delta: Long?,
    val deltaPct: Double?,
    val periodLabel: String,
    val monthName: String,
    val daysLeft: Int,
    val income: Long,
    val spent: Long,
    val budgets: List<BudgetRow>,
    val unbudgeted: List<Pair<String, Long>>,
    val recent: List<Txn>,
    val categories: List<String>,
    val incomeCategories: List<String>,
    val links: MoneyLinks,
    /** 0 = this month, 1 = last month … */
    val monthOffset: Int = 0,
    val canPrevMonth: Boolean = false,
)

val PERIODS = listOf("1W" to 7, "1M" to 31, "3M" to 92, "1Y" to 365)

/** What the keypad entry sheet saves. type: 0 expense, 1 income, 2 transfer. repeat: "" | "W" | "M" | "Y". */
data class EntryDraft(
    val type: Int,
    val cents: Long,
    val name: String,
    val category: String,
    val accountId: Long?,
    val toAccountId: Long?,
    val day: Long,
    val repeat: String,
)

fun coinSummaries(list: List<CryptoHolding>): List<CoinSummary> =
    list.groupBy { it.sym }.map { (sym, hs) ->
        val price = hs.maxBy { it.updatedTs }.priceCents
        val prev = hs.maxBy { it.updatedTs }.prevPriceCents.takeIf { it > 0 } ?: price
        val qty = hs.sumOf { it.qty }
        CoinSummary(sym, qty, (qty * price).toLong(), (qty * (price - prev)).toLong())
    }.sortedByDescending { it.valueCents }

@OptIn(ExperimentalCoroutinesApi::class)
class MoneyVM(app: Application) : BaseVM(app) {
    val period = MutableStateFlow("1M")
    /** Month shown in the spending card: 0 = current, 1 = previous … */
    val monthOffset = MutableStateFlow(0)
    fun shiftMonth(delta: Int) { monthOffset.value = (monthOffset.value - delta).coerceAtLeast(0) }
    private val ops = MoneyOps(repo)

    init { launch { ops.ensureCategories() } }

    private val links = combine(moneyDao.recurring(), moneyDao.savingsGoals(), moneyDao.savingsMoves(), moneyDao.crypto()) { rec, goals, moves, crypto ->
        val coins = coinSummaries(crypto)
        MoneyLinks(
            subsMonthly = rec.filter { it.kind == RK.SUB }.sumOf { it.monthlyCents() },
            recurringCount = rec.size,
            savingsSaved = moves.filter { m -> goals.any { it.id == m.goalId } }.sumOf { it.amountCents },
            savingsTarget = goals.sumOf { it.targetCents },
            goalCount = goals.size,
            cryptoValue = coins.sumOf { it.valueCents },
            cryptoChange = coins.sumOf { it.changeCents },
            coins = coins,
        )
    }

    val state = combine(today, period, monthOffset, ::Triple).flatMapLatest { (day, per, off) ->
        val date = Days.date(day)
        val ym = java.time.YearMonth.from(date).minusMonths(off.toLong())
        val monthStart = ym.atDay(1).toEpochDay()
        val monthEnd = minOf(ym.atEndOfMonth().toEpochDay(), day)
        val span = PERIODS.first { it.first == per }.second
        combine(
            combine(dao.accounts(), dao.netWorth(day - span), ::Pair),
            combine(dao.txnsRange(monthStart, monthEnd), moneyDao.oldestTxnDay(), ::Pair),
            combine(dao.budgets(), dao.recentTxns(12), moneyDao.categories(), ::Triple),
            profile,
            links,
        ) { (accounts, nw), (monthTx, oldest), (budgets, recent, cats), p, lk ->
            val total = accounts.sumOf { it.balanceCents }
            val pts = (nw.map { it.day to it.cents }.filter { it.first < day } + (day to total))
            val series = pts.map { it.second / 100f }
            val first = pts.first().second
            val delta = if (pts.size >= 2) total - first else null
            val pct = if (delta != null && first != 0L) delta * 100.0 / kotlin.math.abs(first) else null

            val real = monthTx.filter { !it.isTransfer && it.category != TRANSFER }
            val spentBy = real.filter { it.amountCents < 0 }.groupBy { it.category }.mapValues { e -> -e.value.sumOf { it.amountCents } }
            val budgetRows = budgets.map { BudgetRow(it.category, spentBy[it.category] ?: 0, it.limitCents) }
            val budgetCats = budgets.map { it.category }.toSet()
            val unbudgeted = spentBy.filterKeys { it !in budgetCats }.toList().sortedByDescending { it.second }
            val expenseCats = cats.filter { !it.income }.map { it.name }.ifEmpty { DEFAULT_EXPENSE }

            MoneyState(
                hidden = p.hideBalances,
                accounts = accounts,
                netWorth = total,
                period = per,
                series = series,
                delta = delta,
                deltaPct = pct,
                periodLabel = when (per) { "1W" -> "past week"; "1M" -> "past month"; "3M" -> "past 3 months"; else -> "past year" },
                monthName = ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + if (ym.year != date.year) " ${ym.year}" else "",
                daysLeft = if (off == 0) date.lengthOfMonth() - date.dayOfMonth else 0,
                income = real.filter { it.amountCents > 0 }.sumOf { it.amountCents },
                spent = spentBy.values.sum(),
                budgets = budgetRows,
                unbudgeted = unbudgeted,
                recent = recent,
                categories = (expenseCats + budgets.map { it.category }).distinct(),
                incomeCategories = cats.filter { it.income }.map { it.name },
                links = lk,
                monthOffset = off,
                canPrevMonth = oldest != null && oldest < monthStart,
            )
        }
    }.state()

    fun toggleHidden() = launch {
        val p = dao.profileNow() ?: return@launch
        dao.saveProfile(p.copy(hideBalances = !p.hideBalances))
    }

    fun saveAccount(a: Account) = launch { repo.saveAccount(a) }
    /** Only the type — safe for bank/Binance accounts whose balance the sync owns. */
    init { launch { com.lifeapi.app.vm.money.linkGoalsToSavings(repo) } }

    fun setAccountKind(a: Account, kind: String) = launch {
        val cur = dao.accountsNow().firstOrNull { it.id == a.id } ?: return@launch
        dao.updateAccount(cur.copy(kind = kind))
        com.lifeapi.app.vm.money.linkGoalsToSavings(repo)
    }
    fun deleteAccount(a: Account) = launch { repo.deleteAccount(a) }

    fun addCategory(name: String, income: Boolean) = launch {
        moneyDao.upsertCategory(Category(name.trim(), income = income, sort = 100))
    }

    fun save(d: EntryDraft) {
        val toast = when (d.type) {
            2 -> "Transfer ${fmtCents(d.cents)} saved"
            1 -> "Logged income ${fmtCents(d.cents)}"
            else -> "Logged ${fmtCents(d.cents)} · ${d.name.ifBlank { d.category }}"
        } + if (d.repeat.isNotEmpty()) " · repeats" else ""
        act(toast) {
            var rid: Long? = null
            if (d.repeat.isNotEmpty()) {
                val kind = when (d.type) { 1 -> RK.INCOME; 2 -> RK.TRANSFER; else -> if (d.category == "Subscriptions") RK.SUB else RK.BILL }
                rid = ops.saveRecurring(
                    Recurring(
                        name = d.name.ifBlank { if (d.type == 2) "Transfer" else d.category }, amountCents = d.cents, kind = kind,
                        category = if (d.type == 2) TRANSFER else d.category, accountId = d.accountId, toAccountId = d.toAccountId,
                        period = d.repeat, nextDay = advance(d.day, d.repeat),
                    )
                )
            }
            val ts = if (d.day == Days.today()) Days.now() else Days.tsOf(d.day, java.time.LocalTime.NOON)
            val t = Txn(
                day = d.day, ts = ts,
                name = d.name.ifBlank { if (d.type == 2) "Transfer" else d.category },
                category = if (d.type == 2) TRANSFER else d.category,
                amountCents = if (d.type == 1) d.cents else -d.cents,
                accountId = d.accountId,
                toAccountId = if (d.type == 2) d.toAccountId else null,
                recurringId = rid,
            )
            val undo = ops.add(t)
            val r = rid
            if (r == null) undo else ({ undo(); moneyDao.recurringNow(r)?.let { moneyDao.deleteRecurring(it) } })
        }
    }

    fun deleteTxn(t: Txn) = act(if (t.extId != null) "Bank transaction deleted" else "Transaction deleted") {
        if (t.extId != null) {
            // bank-synced rows never moved the balance — just remove the row (balance stays the bank's)
            dao.deleteTxn(t)
            suspend { dao.insertTxn(t.copy(id = 0)); Unit }
        } else {
            ops.delete(t)
            suspend { ops.add(t.copy(id = 0)); Unit }
        }
    }

    fun saveBudget(category: String, limitCents: Long) = launch { dao.upsertBudget(Budget(category, limitCents)) }
    fun deleteBudget(category: String) = launch { dao.deleteBudget(category) }

    fun setDailyBudget(cents: Long) = launch {
        val p = dao.profileNow() ?: return@launch
        dao.saveProfile(p.copy(dailySpendCents = cents))
    }

    companion object {
        val DEFAULT_CATEGORIES = DEFAULT_EXPENSE
    }
}
