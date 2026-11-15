package com.lifeapi.app.vm.money

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lifeapi.app.data.Account
import com.lifeapi.app.data.CryptoHolding
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Recurring
import com.lifeapi.app.data.SavingsGoal
import com.lifeapi.app.data.SavingsMove
import com.lifeapi.app.data.Txn
import com.lifeapi.app.data.WishItem
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.data.money.CsvImport
import com.lifeapi.app.data.money.CsvRow
import com.lifeapi.app.data.money.MoneyOps
import com.lifeapi.app.data.money.RK
import com.lifeapi.app.data.money.TRANSFER
import com.lifeapi.app.data.money.looksLikeTransfer
import com.lifeapi.app.data.money.monthlyCents
import com.lifeapi.app.vm.BaseVM
import com.lifeapi.app.vm.CoinSummary
import com.lifeapi.app.vm.coinSummaries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

private fun mShort(ym: YearMonth) = ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
private fun mFull(ym: YearMonth) = ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

// =============================================================== spending

data class CatRow(val name: String, val cents: Long, val count: Int, val pct: Int, val deltaCents: Long?, val prevLabel: String)

data class SpendingState(
    val months: List<String>,
    val selected: Int,
    val title: String,
    val total: Long,
    val prevTotal: Long,
    val prevLabel: String,
    val deltaPct: Int?,
    val rows: List<CatRow>,
    val hidden: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SpendingVM(app: Application) : BaseVM(app) {
    /** 0 = current month, 1 = previous … */
    val offset = MutableStateFlow(0)

    val state = combine(today, offset, ::Pair).flatMapLatest { (day, off) ->
        val now = YearMonth.from(Days.date(day))
        val ym = now.minusMonths(off.toLong())
        val prev = ym.minusMonths(1)
        val cutDom = if (off == 0) Days.date(day).dayOfMonth else ym.lengthOfMonth()
        val from = prev.atDay(1).toEpochDay()
        val to = ym.atEndOfMonth().toEpochDay()
        combine(dao.txnsRange(from, to), profile) { tx, p ->
            val spend = tx.filter { it.amountCents < 0 && !it.isTransfer && it.category != TRANSFER }
            val cur = spend.filter { it.day >= ym.atDay(1).toEpochDay() && it.day <= ym.atDay(cutDom).toEpochDay() }
            val prevCut = prev.atDay(minOf(cutDom, prev.lengthOfMonth())).toEpochDay()
            val prv = spend.filter { it.day >= prev.atDay(1).toEpochDay() && it.day <= prevCut }
            val total = -cur.sumOf { it.amountCents }
            val prevTotal = -prv.sumOf { it.amountCents }
            val prevBy = prv.groupBy { it.category }.mapValues { e -> -e.value.sumOf { it.amountCents } }
            val rows = cur.groupBy { it.category }.map { (c, l) ->
                val cents = -l.sumOf { it.amountCents }
                CatRow(c, cents, l.size, if (total > 0) (cents * 100 / total).toInt() else 0,
                    if (prv.isEmpty()) null else cents - (prevBy[c] ?: 0), mShort(prev))
            }.sortedByDescending { it.cents }
            SpendingState(
                months = (0 until 12).map { val m = now.minusMonths(it.toLong()); if (m.year == now.year) mFull(m) else "${mShort(m)} ${m.year}" },
                selected = off,
                title = mFull(ym),
                total = total,
                prevTotal = prevTotal,
                prevLabel = if (off == 0) "by ${minOf(cutDom, prev.lengthOfMonth())} ${mShort(prev)}" else mFull(prev),
                deltaPct = if (prevTotal > 0) ((total - prevTotal) * 100 / prevTotal).toInt() else null,
                rows = rows,
                hidden = p.hideBalances,
            )
        }
    }.state()
}

// =============================================================== recurring

data class RecurringState(
    val subs: List<Recurring>,
    val others: List<Recurring>,
    val subsMonthly: Long,
    val subsYearly: Long,
    val next7: Long,
    val next7Label: String?,
    val accounts: List<Account>,
    val categories: List<String>,
    val today: Long,
)

class RecurringVM(app: Application) : BaseVM(app) {
    private val ops = MoneyOps(repo)

    val state = combine(moneyDao.recurring(), dao.accounts(), moneyDao.categories(), today) { rec, acc, cats, day ->
        val subs = rec.filter { it.kind == RK.SUB }.sortedBy { it.nextDay }
        val upcoming = rec.filter { it.kind != RK.INCOME && it.kind != RK.TRANSFER && it.nextDay in day..day + 7 }.sortedBy { it.nextDay }
        val sm = subs.sumOf { it.monthlyCents() }
        RecurringState(
            subs = subs,
            others = rec.filter { it.kind != RK.SUB }.sortedWith(compareBy({ it.kind }, { Days.date(it.nextDay).dayOfMonth })),
            subsMonthly = sm,
            subsYearly = sm * 12,
            next7 = upcoming.sumOf { it.amountCents },
            next7Label = upcoming.firstOrNull()?.let { r ->
                val d = Days.date(r.nextDay)
                "${r.name} · ${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth}" + if (upcoming.size > 1) " +${upcoming.size - 1}" else ""
            },
            accounts = acc,
            categories = cats.filter { !it.income }.map { it.name },
            today = day,
        )
    }.state()

    fun save(r: Recurring) = launch {
        val old = if (r.id != 0L) moneyDao.recurringNow(r.id) else null
        val withPrev = if (old != null && old.amountCents != r.amountCents) r.copy(prevAmountCents = old.amountCents) else r
        ops.saveRecurring(withPrev)
        ops.runDue()
    }

    fun delete(r: Recurring) = act("${r.name} removed") {
        moneyDao.deleteRecurring(r)
        suspend { moneyDao.upsertRecurring(r); Unit }
    }
}

// =============================================================== savings

data class GoalView(
    val goal: SavingsGoal,
    val saved: Long,
    val pct: Int,
    val left: Long,
    val paceCents: Long,
    val paceFromPlan: Boolean,
    val eta: String?,
    val moves: List<SavingsMove>,
    val wish: WishItem?,
    /** Savings account funding this goal (envelope), or null for a manual goal. */
    val account: Account? = null,
    /** Free money on [account] (balance − all its allocations); 0 when no account. */
    val accountFree: Long = 0,
)

/** A savings account with the goals earmarked on it. Allocations never move money: free = balance − allocated. */
data class SavingsAccountView(val account: Account, val allocated: Long, val free: Long, val goals: List<GoalView>) {
    val overAllocated: Long get() = (-free).coerceAtLeast(0)
}

data class SavingsState(
    val goals: List<GoalView>,
    val allocated: Long,
    val hidden: Boolean,
    val accounts: List<SavingsAccountView> = emptyList(),
    val savingsAccounts: List<Account> = emptyList(),
)

/** Pure builder shared by the Savings screen and the Money tab. */
fun buildSavings(goals: List<SavingsGoal>, moves: List<SavingsMove>, wishes: List<WishItem>, accounts: List<Account>, day: Long, hidden: Boolean): SavingsState {
    val byGoal = moves.groupBy { it.goalId }
    val savingsAccs = accounts.filter { it.isSavings }
    val accById = accounts.associateBy { it.id }
    val base = goals.map { g ->
        val ms = byGoal[g.id].orEmpty()
        val saved = ms.sumOf { it.amountCents }
        val left = (g.targetCents - saved).coerceAtLeast(0)
        val recent = ms.filter { it.day > day - 90 }
        val first = ms.minOfOrNull { it.day }
        val spanMonths = first?.let { ((day - maxOf(it, day - 90)) / 30.4).coerceIn(1.0, 3.0) } ?: 1.0
        val net = recent.sumOf { it.amountCents }
        val observed = if (net > 0) (net / spanMonths).toLong() else 0L
        val pace = if (observed > 0) observed else g.monthlyCents
        val eta = when {
            left == 0L && g.targetCents > 0 -> "Funded"
            pace > 0 -> {
                val m = Days.date(day).plusMonths(ceil(left.toDouble() / pace).toLong())
                "≈ ${m.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${m.year}"
            }
            else -> null
        }
        GoalView(g, saved, if (g.targetCents > 0) (saved * 100 / g.targetCents).toInt().coerceIn(0, 100) else 0, left, pace,
            observed == 0L && g.monthlyCents > 0, eta, ms, wishes.firstOrNull { it.goalId == g.id }, g.accountId?.let { accById[it] })
    }
    val allocByAcc = base.filter { it.account != null }.groupBy { it.account!!.id }.mapValues { e -> e.value.sumOf { it.saved } }
    val views = base.map { gv -> gv.account?.let { a -> gv.copy(accountFree = a.balanceCents - (allocByAcc[a.id] ?: 0)) } ?: gv }
    val accViews = savingsAccs.map { a ->
        val alloc = allocByAcc[a.id] ?: 0
        SavingsAccountView(a, alloc, a.balanceCents - alloc, views.filter { it.account?.id == a.id })
    }
    return SavingsState(views, views.sumOf { it.saved }, hidden, accViews, savingsAccs)
}

class SavingsVM(app: Application) : BaseVM(app) {
    val state = combine(
        combine(moneyDao.savingsGoals(), moneyDao.savingsMoves(), ::Pair),
        growDao.wishes(), dao.accounts(), today, profile,
    ) { (goals, moves), wishes, accounts, day, p -> buildSavings(goals, moves, wishes, accounts, day, p.hideBalances) }.state()

    init {
        // Goals are envelopes inside a savings account: unlinked goals get the (first) savings account automatically,
        // so every euro put into a goal is reserved from real money and can't exceed what's free there.
        launch { linkGoalsToSavings(repo) }
    }

    fun saveGoal(g: SavingsGoal, startCents: Long = 0) = launch {
        val id = moneyDao.upsertSavingsGoal(g)
        if (g.id == 0L && startCents > 0) {
            // never reserve more than is free in the savings account
            val acc = g.accountId?.let { aid -> dao.accountsNow().firstOrNull { it.id == aid } } ?: return@launch
            val goalIds = moneyDao.savingsGoalsNow().filter { it.accountId == acc.id }.map { it.id }.toSet()
            val reserved = moneyDao.savingsMoves().first().filter { it.goalId in goalIds && it.goalId != id }.sumOf { it.amountCents }
            val amount = minOf(startCents, (acc.balanceCents - reserved).coerceAtLeast(0))
            if (amount > 0) moneyDao.insertSavingsMove(SavingsMove(goalId = id, day = Days.today(), amountCents = amount, note = "reserved"))
            if (amount < startCents) c.snack("Only ${com.lifeapi.app.data.fmtCents(amount)} was free in ${acc.name} — reserved that")
        }
    }

    fun deleteGoal(g: SavingsGoal) = act("${g.name} removed") {
        val moves = moneyDao.savingsMoves().first().filter { it.goalId == g.id }
        moneyDao.deleteMovesFor(g.id)
        moneyDao.deleteSavingsGoal(g)
        suspend {
            moneyDao.upsertSavingsGoal(g)
            moves.forEach { moneyDao.insertSavingsMove(it) }
        }
    }

    /** Allocation only: earmarks (+) or releases (−) money for the goal. No transaction, no balance change. */
    fun move(g: SavingsGoal, cents: Long, note: String) = act(
        when {
            g.accountId != null && cents > 0 -> "Earmarked ${fmtCents(cents)} for ${g.name}"
            g.accountId != null -> "Released ${fmtCents(-cents)} from ${g.name}"
            cents > 0 -> "Added ${fmtCents(cents)} to ${g.name}"
            else -> "Withdrew ${fmtCents(-cents)} from ${g.name}"
        },
    ) {
        val id = moneyDao.insertSavingsMove(SavingsMove(goalId = g.id, day = Days.today(), amountCents = cents, note = note))
        suspend { moneyDao.deleteSavingsMove(id) }
    }

    fun deleteMove(m: SavingsMove) = act("Entry removed") {
        moneyDao.deleteSavingsMove(m.id)
        suspend { moneyDao.insertSavingsMove(m); Unit }
    }
}


// =============================================================== import

data class ImportRow(
    val idx: Int,
    val csv: CsvRow,
    val category: String?,
    val auto: Boolean,
    val transfer: Boolean,
    val include: Boolean,
    val dup: Txn?,
    /** null undecided, true keep both, false skip */
    val keepDup: Boolean?,
)

data class ImportState(
    val fileName: String,
    val rows: List<ImportRow>,
    val skippedLines: Int,
    val accountId: Long?,
)

class ImportVM(app: Application) : BaseVM(app) {
    private val ops = MoneyOps(repo)
    private val prefs = app.getSharedPreferences("money_import", Context.MODE_PRIVATE)

    val review = MutableStateFlow<ImportState?>(null)
    val error = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow(false)
    val lastImport = MutableStateFlow(prefs.getString("last", null))

    val accounts = dao.accounts().state()
    val categories = combine(moneyDao.categories(), dao.budgets()) { c, b -> (c.filter { !it.income }.map { it.name } + b.map { it.category }).distinct() }.state()

    fun load(uri: Uri) = launch {
        loading.value = true; error.value = null
        val ctx = getApplication<Application>()
        val (name, text) = withContext(Dispatchers.IO) {
            val n = runCatching {
                ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: "statement.csv"
            val t = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            n to t
        }
        if (text == null) { error.value = "Couldn't read that file"; loading.value = false; return@launch }
        val res = withContext(Dispatchers.Default) { CsvImport.parse(text) }
        if (res.rows.isEmpty()) { error.value = res.error ?: "Nothing to import"; loading.value = false; return@launch }
        val accs = dao.accountsNow()
        val minDay = res.rows.minOf { it.day } - 1
        val maxDay = res.rows.maxOf { it.day } + 1
        val existing = moneyDao.txnsBetween(minDay, maxDay).toMutableList()
        val catz = com.lifeapi.app.data.money.Categorizer.load(repo)
        val rows = res.rows.mapIndexed { i, r ->
            val dup = existing.firstOrNull { it.amountCents == r.cents && kotlin.math.abs(it.day - r.day) <= 1 }
            if (dup != null) existing.remove(dup)
            val transfer = looksLikeTransfer(r.name, accs.map { it.name })
            val key = com.lifeapi.app.data.money.Merchant.key(com.lifeapi.app.data.money.Merchant.clean(r.name).ifBlank { r.name })
            val cat = if (r.cents > 0) (catz.category(key, r.name, r.cents, "") ?: "Income") else catz.category(key, r.name, r.cents, "")
            ImportRow(i, r, cat, cat != null, transfer, include = !transfer && dup == null, dup = dup, keepDup = null)
        }.sortedByDescending { it.csv.day }
        review.value = ImportState(name, rows, res.skipped, accs.firstOrNull()?.id)
        loading.value = false
    }

    private fun update(idx: Int, f: (ImportRow) -> ImportRow) {
        val s = review.value ?: return
        review.value = s.copy(rows = s.rows.map { if (it.idx == idx) f(it) else it })
    }

    fun toggle(idx: Int) = update(idx) { it.copy(include = !it.include) }
    fun setCategory(idx: Int, c: String) = update(idx) { it.copy(category = c, auto = false) }
    fun decideDup(idx: Int, keep: Boolean) = update(idx) { it.copy(keepDup = keep, include = keep) }
    fun setAccount(id: Long?) { review.value = review.value?.copy(accountId = id) }
    fun cancel() { review.value = null }

    fun commit(onDone: () -> Unit) = launch {
        val s = review.value ?: return@launch
        val chosen = s.rows.filter { it.include }
        val txns = chosen.map { r ->
            val clean = com.lifeapi.app.data.money.Merchant.clean(r.csv.name)
            val display = if (com.lifeapi.app.data.money.Recategorize.looksRaw(r.csv.name)) clean.ifBlank { r.csv.name } else r.csv.name
            Txn(
                day = r.csv.day, ts = Days.tsOf(r.csv.day, java.time.LocalTime.NOON), name = display,
                category = if (r.transfer) TRANSFER else r.category ?: (if (r.csv.cents > 0) "Income" else "Other"),
                amountCents = r.csv.cents, accountId = s.accountId, note = "import:${s.fileName} · ${r.csv.name}".take(300),
                merchant = com.lifeapi.app.data.money.Merchant.key(display), userCategory = !r.auto && r.category != null,
            )
        }
        ops.importRows(txns)
        val d = Days.date(Days.today())
        val label = "${s.fileName} · ${s.rows.size} rows · ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
        prefs.edit().putString("last", label).apply()
        lastImport.value = label
        review.value = null
        c.snack("Imported ${txns.size} transaction${if (txns.size == 1) "" else "s"}")
        withContext(Dispatchers.Main) { onDone() }
    }
}

// =============================================================== crypto

data class CoinRow(val sum: CoinSummary, val holdings: List<CryptoHolding>, val avgCents: Long, val plCents: Long, val priceCents: Long)

data class CryptoState(val total: Long, val change: Long, val plPct: Double?, val coins: List<CoinRow>, val hidden: Boolean)

class CryptoVM(app: Application) : BaseVM(app) {
    val state = combine(moneyDao.crypto(), profile) { list, p ->
        val sums = coinSummaries(list)
        val rows = sums.map { s ->
            val hs = list.filter { it.sym == s.sym }
            val kh = hs.filter { it.avgBuyCents > 0 }
            val kq = kh.sumOf { it.qty }
            val cost = kh.sumOf { it.qty * it.avgBuyCents }
            val avg = if (kq > 0) (cost / kq).toLong() else 0
            val px = hs.maxBy { it.updatedTs }.priceCents
            CoinRow(s, hs, avg, (kq * px - cost).toLong(), px)
        }
        // P/L only over holdings with a known buy price (Binance doesn't give one)
        val known = list.filter { it.avgBuyCents > 0 }
        val cost = known.sumOf { it.qty * it.avgBuyCents }
        val knownValue = known.sumOf { it.qty * it.priceCents }
        val total = sums.sumOf { it.valueCents }
        CryptoState(total, sums.sumOf { it.changeCents }, if (cost > 0) (knownValue - cost) * 100.0 / cost else null, rows, p.hideBalances)
    }.state()

    fun add(sym: String, qty: Double, avgCents: Long, priceCents: Long, source: String) = launch {
        val s = sym.trim().uppercase(Locale.ROOT)
        moneyDao.upsertCrypto(CryptoHolding(sym = s, qty = qty, avgBuyCents = avgCents, priceCents = priceCents, prevPriceCents = priceCents, source = source.ifBlank { "Manual" }, updatedTs = Days.now()))
        // keep one price per coin
        moneyDao.crypto().first().filter { it.sym == s && it.priceCents != priceCents }.forEach {
            moneyDao.upsertCrypto(it.copy(prevPriceCents = it.priceCents, priceCents = priceCents, updatedTs = Days.now()))
        }
    }

    fun updateHolding(h: CryptoHolding) = launch { moneyDao.upsertCrypto(h.copy(updatedTs = Days.now())) }

    fun setPrice(sym: String, priceCents: Long) = launch {
        moneyDao.crypto().first().filter { it.sym == sym }.forEach {
            moneyDao.upsertCrypto(it.copy(prevPriceCents = it.priceCents, priceCents = priceCents, updatedTs = Days.now()))
        }
        c.snack("$sym price updated")
    }

    fun delete(h: CryptoHolding) = act("${h.sym} holding removed") {
        moneyDao.deleteCrypto(h)
        suspend { moneyDao.upsertCrypto(h); Unit }
    }
}


/** Goals are envelopes inside a savings account: unlinked goals get the first savings account. Idempotent. */
suspend fun linkGoalsToSavings(repo: com.lifeapi.app.data.LifeRepository) {
    val accs = repo.dao.accountsNow().filter { it.isSavings }
    val first = accs.firstOrNull() ?: return
    repo.money.savingsGoalsNow().filter { it.accountId == null || accs.none { a -> a.id == it.accountId } }
        .forEach { repo.money.upsertSavingsGoal(it.copy(accountId = first.id)) }
}
