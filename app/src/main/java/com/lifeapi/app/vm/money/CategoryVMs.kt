package com.lifeapi.app.vm.money

import android.app.Application
import com.lifeapi.app.data.Category
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Txn
import com.lifeapi.app.data.money.Merchant
import com.lifeapi.app.data.money.Recategorize
import com.lifeapi.app.data.money.TRANSFER
import com.lifeapi.app.vm.BaseVM
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Editing one transaction: name, note, category (+ remember for the merchant). */
class TxnEditVM(app: Application) : BaseVM(app) {
    val categories: Flow<List<Category>> = moneyDao.categories()

    fun countFor(merchant: String): Flow<Int> = moneyDao.countByMerchant(merchant)

    fun merchantKey(t: Txn): String = t.merchant.ifBlank { Recategorize.keyFor(t) }

    fun save(t: Txn, name: String, category: String, note: String, remember: Boolean) = act(
        if (remember) "“${name.ifBlank { t.name }}” → $category · remembered" else "Saved · $category",
    ) { Recategorize.assign(repo, t, category, remember, name.trim(), note.trim()) }

    fun addCategory(name: String, income: Boolean) = launch {
        val n = name.trim()
        if (n.isNotEmpty()) moneyDao.upsertCategory(Category(n, income = income, sort = 50))
    }

    fun delete(t: Txn) = act(if (t.extId != null) "Bank transaction deleted" else "Transaction deleted") {
        com.lifeapi.app.data.money.MoneyOps(repo).let { ops ->
            if (t.extId != null) { dao.deleteTxn(t); suspend { dao.insertTxn(t.copy(id = 0)); Unit } } else { ops.delete(t); suspend { ops.add(t.copy(id = 0)); Unit } }
        }
    }
}

data class MerchantGroup(val key: String, val name: String, val cents: Long, val txns: List<Txn>)

data class CategoryState(
    val category: String,
    val monthTitle: String,
    val total: Long,
    val count: Int,
    val groups: List<MerchantGroup>,
    val hidden: Boolean,
)

/** One category of one month, grouped by merchant. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryVM(app: Application) : BaseVM(app) {
    private val args = MutableStateFlow<Pair<String, Int>?>(null)

    fun bind(category: String, offset: Int) { if (args.value != category to offset) args.value = category to offset }

    val state = combine(args, today, ::Pair).flatMapLatest { (a, day) ->
        val (cat, off) = a ?: ("" to 0)
        val ym = YearMonth.from(Days.date(day)).minusMonths(off.toLong())
        combine(dao.txnsRange(ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay()), profile) { tx, p ->
            val rows = tx.filter { it.category == cat && it.amountCents < 0 && !it.isTransfer && it.category != TRANSFER }
            val groups = rows.groupBy { it.merchant.ifBlank { Merchant.key(it.name) }.ifBlank { it.name.lowercase(Locale.ROOT) } }
                .map { (k, l) ->
                    val name = l.groupingBy { it.name }.eachCount().maxByOrNull { it.value }?.key ?: k
                    MerchantGroup(k, name, -l.sumOf { it.amountCents }, l.sortedByDescending { it.ts })
                }
                .sortedByDescending { it.cents }
            CategoryState(
                category = cat,
                monthTitle = "${ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${ym.year}",
                total = -rows.sumOf { it.amountCents },
                count = rows.size,
                groups = groups,
                hidden = p.hideBalances,
            )
        }
    }.state()

    fun assignMerchant(key: String, name: String, category: String) = act("“$name” → $category · remembered") {
        Recategorize.assignMerchant(repo, key, category)
    }

    val categories: Flow<List<Category>> = moneyDao.categories()

    fun addCategory(name: String) = launch {
        val n = name.trim()
        if (n.isNotEmpty()) moneyDao.upsertCategory(Category(n, income = false, sort = 50))
    }

    /** Every payment shown (this category, this month) → [target]; their merchants are remembered. */
    fun moveAll(target: String) {
        val st = state.value ?: return
        val rows = st.groups.flatMap { it.txns }
        if (rows.isEmpty() || target == st.category) return
        act("${rows.size} payment${if (rows.size == 1) "" else "s"} → $target · remembered") { Recategorize.moveRows(repo, rows, target) }
    }

    /** One merchant's payments in this view → [target] (+ remembered, its other months follow). */
    fun moveMerchant(g: MerchantGroup, target: String) {
        if (g.txns.isEmpty()) return
        act("“${g.name}” → $target · remembered") { Recategorize.moveRows(repo, g.txns, target) }
    }
}
