package com.lifeapi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Account
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Txn
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.ui.components.AccountSheet
import com.lifeapi.app.ui.components.BudgetSheet
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.HDivider
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LineChart
import com.lifeapi.app.ui.components.LinearBar
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RollingText
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.SectionHeader
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.screens.money.TxnEntrySheet
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.MoneyState
import com.lifeapi.app.vm.MoneyVM
import com.lifeapi.app.vm.PERIODS
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MoneyScreen(vm: MoneyVM = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val st = s ?: return
    var accEdit by remember { mutableStateOf<Account?>(null) }
    var accNew by remember { mutableStateOf(false) }
    var accKind by remember { mutableStateOf<Account?>(null) }
    var txnOpen by remember { mutableStateOf<Int?>(null) } // 0 expense, 1 income, 2 transfer
    val nav = LocalNav.current
    var budgetEdit by remember { mutableStateOf<Pair<String?, Long?>?>(null) }
    var showAllTx by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) { com.lifeapi.app.data.bank.Bank.maybeAutoSync(); com.lifeapi.app.data.binance.Binance.maybeAutoSync() }
    val bank by com.lifeapi.app.data.bank.Bank.status.collectAsStateWithLifecycle()

    ScreenList(glow = C.Money) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Money", style = T.headline, color = C.Ink, modifier = Modifier.weight(1f))
                RoundIconButton(if (st.hidden) Ic.EyeOff else Ic.Eye, if (st.hidden) "Show balances" else "Hide balances", onClick = vm::toggleHidden)
            }
        }
        if (st.accounts.isEmpty()) {
            item {
                EmptyCard(
                    "Money", C.Money, "Start with your accounts",
                    "Add each account with its current balance — net worth and trends build from there. Then type \"coffee 4.50\" in Log and it lands in the right budget.",
                    "Add account", { accNew = true }, "Add first expense", { txnOpen = 0 },
                )
            }
        } else {
            item { NetWorthCard(st, vm) }
            item { Accounts(st, onEdit = { if (it.isBank || it.source == "Binance") accKind = it else accEdit = it }, onAdd = { accNew = true }) }
            item { SavingsSection(st.hidden) { nav.navigate("money/savings") } }
        }
        item {
            MonthCard(
                st, onBudget = { c, l -> budgetEdit = c to l }, onAddBudget = { budgetEdit = null to null },
                onSpending = { nav.navigate("money/spending") },
                onCategory = { nav.navigate(com.lifeapi.app.ui.screens.money.categoryRoute(it, st.monthOffset)) },
                onShift = vm::shiftMonth,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Expense", C.MoneyTint, C.Money, Modifier.weight(1f), 44.dp, 12.dp, icon = Ic.Receipt) { txnOpen = 0 }
                PillButton("Income", C.GainTint, C.Gain, Modifier.weight(1f), 44.dp, 12.dp, icon = Ic.Plus) { txnOpen = 1 }
                PillButton("Transfer", C.Graphite2, C.InkSoft, Modifier.weight(1f), 44.dp, 12.dp) { txnOpen = 2 }
            }
        }
        if (st.links.coins.isNotEmpty()) item { CryptoCard(st) { nav.navigate("money/crypto") } }
        item { PlanLinks(st) { nav.navigate(it) } }
        if (st.recent.isNotEmpty()) item { Recent(st, showAllTx, { showAllTx = !showAllTx }, vm::deleteTxn) }
    }

    if (accNew || accEdit != null) AccountSheet(accEdit, { accNew = false; accEdit = null }, vm::saveAccount, vm::deleteAccount)
    accKind?.let { a ->
        LinkedAccountSheet(a, { accKind = null }, onKind = { k -> vm.setAccountKind(a, k) }) {
            accKind = null; nav.navigate(if (a.isBank) "money/bank" else "money/binance")
        }
    }
    txnOpen?.let { type ->
        TxnEntrySheet(type, st.accounts, st.categories, st.incomeCategories, { txnOpen = null }, vm::addCategory, vm::save)
    }
    budgetEdit?.let { (cat, lim) ->
        BudgetSheet(st.categories, cat, lim, { budgetEdit = null }, vm::saveBudget, vm::deleteBudget)
    }
}

private fun money(st: MoneyState, cents: Long, sign: Boolean = false) = if (st.hidden) "€ ••••" else fmtCents(cents, sign)

@Composable
private fun NetWorthCard(st: MoneyState, vm: MoneyVM) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MonoLabel("Net worth")
            if (st.hidden) {
                Text("€ ••••••", style = T.display, color = C.Ink)
            } else {
                val full = fmtCents(st.netWorth)
                val dot = full.lastIndexOf('.')
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(full.substring(0, dot), T.display)
                    Text(full.substring(dot), style = T.display.copy(fontSize = 24.sp), color = C.InkFaint, modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            if (st.delta != null && !st.hidden) {
                val up = st.delta >= 0
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Ic.Spark, null, tint = if (up) C.Positive else C.Negative, modifier = Modifier.size(16.dp))
                    Text(
                        "${fmtCents(st.delta, sign = true)}${st.deltaPct?.let { String.format(Locale.US, " (%.1f%%)", it) } ?: ""}",
                        style = T.smallB, color = if (up) C.Positive else C.Negative,
                    )
                    Text("· ${st.periodLabel}", style = T.small, color = C.InkSubtle)
                }
            } else if (!st.hidden) {
                Text("Trend starts tomorrow — balances are snapshotted daily.", style = T.caption, color = C.InkSubtle)
            }
        }
        if (st.series.size >= 2) {
            LineChart(st.series, C.Money, Modifier.fillMaxWidth().height(132.dp))
        } else {
            Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, C.Hairline, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text("Chart appears after a second day of data", style = T.caption, color = C.InkSubtle)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PERIODS.forEach { (k, _) ->
                val sel = k == st.period
                Box(
                    Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) C.MoneyTint else Color.Transparent)
                        .press { vm.period.value = k },
                    contentAlignment = Alignment.Center,
                ) { Text(k, style = T.smallB, color = if (sel) Color(0xFFC9D3FF) else C.InkSubtle) }
            }
        }
    }
}

private val ACCOUNT_GLYPH = mapOf(0 to "%", 1 to "S", 2 to "R", 3 to "T", 4 to "₿")

@Composable
private fun Accounts(st: MoneyState, onEdit: (Account) -> Unit, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Accounts", "${st.accounts.size} · + Add", onTrailing = onAdd)
        st.accounts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    LifeCard(Modifier.weight(1f).press { onEdit(a) }, radius = 18.dp, padding = PaddingValues(14.dp), gap = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(C.tint(a.hue)), contentAlignment = Alignment.Center) {
                                Text(a.name.take(1).uppercase().ifEmpty { ACCOUNT_GLYPH[a.hue] ?: "€" }, style = T.tiny.copy(fontWeight = FontWeight.Bold), color = C.hue(a.hue))
                            }
                            Text(a.name, style = T.small, color = C.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (a.isSavings) Box(Modifier.clip(RoundedCornerShape(6.dp)).background(C.GainTint).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                Text("Savings", style = T.tiny, color = C.Gain)
                            }
                            if (a.isBank || a.source == "Binance") Box(Modifier.clip(RoundedCornerShape(6.dp)).background(C.MoneyTint).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                Text(if (a.isBank) "Bank" else "Binance", style = T.tiny, color = C.Money)
                            }
                        }
                        Text(money(st, a.balanceCents), style = T.section.copy(fontSize = 17.sp), color = C.Ink, maxLines = 1)
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MonthCard(
    st: MoneyState, onBudget: (String, Long) -> Unit, onAddBudget: () -> Unit, onSpending: () -> Unit,
    onCategory: (String) -> Unit, onShift: (Int) -> Unit,
) {
    LifeCard(Modifier.fillMaxWidth(), gap = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundIconButton(Ic.ChevronLeft, "Previous month", size = 32.dp, bg = C.Track, tint = if (st.canPrevMonth) C.InkSoft else C.InkFaint) {
                if (st.canPrevMonth) onShift(-1)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(st.monthName, style = T.section, color = C.Ink)
                Text(
                    if (st.monthOffset == 0) "${st.daysLeft} days left · Breakdown ›" else "Breakdown ›",
                    style = T.caption, color = C.InkSubtle, modifier = Modifier.press(onClick = onSpending),
                )
            }
            RoundIconButton(Ic.ChevronRight, "Next month", size = 32.dp, bg = C.Track, tint = if (st.monthOffset > 0) C.InkSoft else C.InkFaint) {
                if (st.monthOffset > 0) onShift(1)
            }
        }
        Row(Modifier.press(onClick = onSpending)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Income", style = T.caption, color = C.InkSubtle)
                Text(money(st, st.income, sign = true), style = T.section.copy(fontSize = 17.sp), color = C.Positive)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Spent", style = T.caption, color = C.InkSubtle)
                Text(money(st, st.spent), style = T.section.copy(fontSize = 17.sp), color = C.Ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Saved", style = T.caption, color = C.InkSubtle)
                val saved = if (st.income > 0) ((st.income - st.spent) * 100 / st.income).toInt() else null
                Text(saved?.let { "$it%" } ?: "—", style = T.section.copy(fontSize = 17.sp), color = C.Ink)
            }
        }
        HDivider()
        if (st.budgets.isEmpty()) {
            Text("No budgets yet. Set a monthly limit per category to see how you're tracking.", style = T.small, color = C.InkMuted)
        }
        st.budgets.forEach { b ->
            val over = b.spent > b.limit
            Column(Modifier.fillMaxWidth().press(onLongClick = { onBudget(b.category, b.limit) }) { onCategory(b.category) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(b.category, style = T.body, color = C.Ink, modifier = Modifier.weight(1f))
                    Text(
                        if (over) "${money(st, b.spent - b.limit)} over · ${money(st, b.spent)} of ${money(st, b.limit)}"
                        else "${money(st, b.spent)} of ${money(st, b.limit)}",
                        style = T.caption, color = if (over) C.Negative else C.InkMuted,
                    )
                }
                LinearBar(b.spent.toFloat() / b.limit, if (over) C.Train else C.Money, Color(0xFF1C1E24))
            }
        }
        st.unbudgeted.forEach { (cat, spent) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).press { onCategory(cat) }, verticalAlignment = Alignment.CenterVertically) {
                Text(cat, style = T.body, color = C.InkMuted, modifier = Modifier.weight(1f))
                Text("${money(st, spent)} · no budget", style = T.caption, color = C.InkSubtle)
                Icon(Ic.ChevronRight, null, tint = C.InkFaint, modifier = Modifier.padding(start = 4.dp).size(16.dp))
            }
        }
        if (st.budgets.isNotEmpty()) Text("Tap a category to see and move its payments · long-press a budget to edit it", style = T.caption, color = C.InkFaint)
        GhostButton("+ Budget", Modifier.fillMaxWidth(), height = 40.dp, onClick = onAddBudget)
    }
}

@Composable
private fun Recent(st: MoneyState, all: Boolean, toggle: () -> Unit, delete: (Txn) -> Unit) {
    var editing by remember { mutableStateOf<Txn?>(null) }
    editing?.let { com.lifeapi.app.ui.screens.money.TxnEditSheet(it, st.hidden, onDismiss = { editing = null }) }
    LifeCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp), gap = 4.dp) {
        SectionHeader("Recent", if (st.recent.size > 5) (if (all) "Show less" else "See all") else null, C.Money, onTrailing = toggle)
        (if (all) st.recent else st.recent.take(5)).forEach { t -> TxnRow(st, t, delete) { editing = t } }
    }
}

@Composable
private fun TxnRow(st: MoneyState, t: Txn, delete: (Txn) -> Unit, onEdit: () -> Unit) {
    var confirm by remember(t.id) { mutableStateOf(false) }
    val transfer = t.isTransfer
    val income = t.amountCents > 0 && !transfer
    val hue = when {
        transfer -> 4
        income -> 5
        t.category == "Groceries" -> 2
        t.category == "Eating out" -> 3
        else -> -1
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).press(onLongClick = { confirm = true }) { if (confirm) confirm = false else onEdit() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (hue >= 0) C.tint(hue) else C.Track),
            contentAlignment = Alignment.Center,
        ) { Text(if (transfer) "↻" else t.name.take(1).uppercase(), style = T.smallB, color = if (hue >= 0) C.hue(hue) else C.InkSoft) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.name, style = T.body, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val d = Days.date(t.day)
            val when_ = if (t.day == Days.today()) "Today ${com.lifeapi.app.data.hhmm(Days.timeOf(t.ts))}" else "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
            val cat = if (transfer) {
                val from = st.accounts.firstOrNull { it.id == t.accountId }?.name ?: "?"
                val to = st.accounts.firstOrNull { it.id == t.toAccountId }?.name ?: "?"
                "$from → $to"
            } else t.category
            Text("$cat · $when_${if (t.recurringId != null) " · repeats" else ""}", style = T.caption, color = C.InkSubtle, maxLines = 1)
        }
        if (confirm) PillButton("Delete", Color(0xFF2A1715), C.Negative, height = 32.dp) { delete(t) }
        else Text(money(st, if (transfer) -t.amountCents else t.amountCents, sign = !transfer), style = T.body.copy(fontWeight = FontWeight.SemiBold), color = if (income) C.Positive else C.Ink)
    }
}

@Composable
private fun CryptoCard(st: MoneyState, onOpen: () -> Unit) {
    val lk = st.links
    LifeCard(Modifier.fillMaxWidth().press(onClick = onOpen), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), gap = 4.dp) {
        SectionHeader(
            "Crypto",
            if (lk.cryptoChange != 0L && !st.hidden) fmtCents(lk.cryptoChange, sign = true) else money(st, lk.cryptoValue),
            if (lk.cryptoChange >= 0) C.Positive else C.Negative,
        )
        lk.coins.take(3).forEach { c ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(C.GrowTint), contentAlignment = Alignment.Center) {
                    Text(c.sym.take(3), style = T.tiny.copy(fontWeight = FontWeight.Bold), color = C.Grow)
                }
                Text(String.format(Locale.US, "%.4f", c.qty).trimEnd('0').trimEnd('.'), style = T.body, color = C.InkMuted, modifier = Modifier.weight(1f))
                Text(money(st, c.valueCents), style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink)
            }
        }
    }
}

@Composable
private fun PlanLinks(st: MoneyState, go: (String) -> Unit) {
    val lk = st.links
    LifeCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 4.dp), gap = 0.dp) {
        LinkRow(
            "↻", C.MoneyTint, C.Money, "Recurring & subscriptions",
            if (lk.recurringCount == 0) "Rent, salary, subscriptions" else "${fmtCents(lk.subsMonthly)} / mo in subscriptions · ${lk.recurringCount} items",
        ) { go("money/recurring") }
        HDivider(start = 64.dp)
        LinkRow(
            "◎", C.GainTint, C.Gain, "Savings goals",
            if (lk.goalCount == 0) "Envelopes with a real ETA" else "${money(st, lk.savingsSaved)} of ${money(st, lk.savingsTarget)} · ${lk.goalCount} goal${if (lk.goalCount == 1) "" else "s"}",
        ) { go("money/savings") }
        HDivider(start = 64.dp)
        LinkRow("▤", C.Graphite3, C.InkSoft, "Spending breakdown", "${st.monthName} by category") { go("money/spending") }
        HDivider(start = 64.dp)
        LinkRow("⇅", C.Graphite3, C.InkSoft, "Import & connections", "CSV statements · crypto holdings") { go("money/import") }
    }
}

@Composable
private fun LinkRow(glyph: String, bg: Color, fg: Color, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).press(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) { Text(glyph, style = T.section, color = fg) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = T.body, color = C.Ink)
            Text(sub, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Ic.ChevronRight, null, tint = C.InkFaint, modifier = Modifier.size(18.dp))
    }
}


/** Bank/Binance accounts: balance is owned by the sync, only the type can be changed here. */
@Composable
private fun LinkedAccountSheet(a: Account, onDismiss: () -> Unit, onKind: (String) -> Unit, onOpen: () -> Unit) {
    var kind by remember { mutableStateOf(a.kind) }
    com.lifeapi.app.ui.components.FormSheet(a.name, onDismiss, confirm = "Save", confirmColor = C.Money, onConfirm = { onKind(kind); onDismiss() }) {
        Text("Balance comes from ${if (a.isBank) "the bank" else "Binance"} — only the type is yours to set.", style = T.caption, color = C.InkSubtle)
        Text("Type", style = T.caption, color = C.InkMuted)
        com.lifeapi.app.ui.components.ChipPicker(com.lifeapi.app.data.ACCOUNT_KINDS, kind, C.Money) { kind = it }
        if (kind == "Savings") Text("Savings accounts can fund goals: earmark part of the balance for each goal — the money stays in the account.", style = T.caption, color = C.Gain)
        com.lifeapi.app.ui.components.GhostButton(if (a.isBank) "Bank connection ›" else "Binance ›", Modifier.fillMaxWidth(), onClick = onOpen)
    }
}

/** Under the accounts: each savings account, how much is earmarked for goals, and the goals' progress. */
@Composable
private fun SavingsSection(hidden: Boolean, onOpen: () -> Unit) {
    val svm: com.lifeapi.app.vm.money.SavingsVM = viewModel()
    val sv by svm.state.collectAsStateWithLifecycle()
    val s = sv ?: return
    if (s.accounts.isEmpty() && s.goals.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Savings & goals", "All ›", onTrailing = onOpen)
        s.accounts.forEach { av -> com.lifeapi.app.ui.screens.money.SavingsAccountCard(av, hidden, onOpen) }
        val goals = s.goals.take(4)
        if (goals.isNotEmpty()) LifeCard(Modifier.fillMaxWidth().press(onClick = onOpen), gap = 12.dp) {
            goals.forEach { g ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(g.goal.name, style = T.body, color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${if (hidden) "•••" else fmtCents(g.saved).substringBefore('.')} / ${fmtCents(g.goal.targetCents).substringBefore('.')} · ${g.pct}%",
                            style = T.small, color = C.InkMuted,
                        )
                    }
                    LinearBar(g.pct / 100f, C.Money, C.MoneyTint, 6.dp)
                    Text(
                        listOfNotNull(g.account?.let { "in ${it.name}" } ?: "manual", g.eta).joinToString(" · "),
                        style = T.caption, color = C.InkSubtle,
                    )
                }
            }
            if (s.goals.size > 4) Text("+ ${s.goals.size - 4} more", style = T.caption, color = C.InkSubtle)
        }
    }
}
