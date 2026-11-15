package com.lifeapi.app.ui.screens.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Account
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Recurring
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.data.money.PERIOD_LABEL
import com.lifeapi.app.data.money.RK
import com.lifeapi.app.data.money.TRANSFER
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.ChipPicker
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.money.RecurringState
import com.lifeapi.app.vm.money.RecurringVM
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun RecurringScreen(vm: RecurringVM = viewModel()) {
    val nav = LocalNav.current
    val s by vm.state.collectAsStateWithLifecycle()
    val st = s ?: return
    var edit by remember { mutableStateOf<Recurring?>(null) }
    var add by remember { mutableStateOf(false) }

    ScreenList(glow = null, bottomExtra = false) {
        item {
            DetailTop("Recurring", "Bills auto-log on their due date", onBack = { nav.popBackStack() }) {
                RoundIconButton(Ic.Plus, "Add recurring", size = 40.dp, onClick = { add = true })
            }
        }
        if (st.subs.isEmpty() && st.others.isEmpty()) {
            item {
                EmptyCard(
                    "Recurring", C.Money, "Nothing recurring yet",
                    "Add rent, salary, subscriptions or a monthly transfer to savings. Due items log themselves, so balances stay right without typing.",
                    "Add recurring", { add = true },
                )
            }
        } else {
            item {
                LifeCard(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Subscriptions / mo", style = T.tiny, color = C.InkSubtle)
                            Text(fmtCents(st.subsMonthly), style = T.title, color = C.Ink)
                            Text("${fmtCents(st.subsYearly)} / yr", style = T.caption, color = C.InkMuted)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Next 7 days", style = T.tiny, color = C.InkSubtle)
                            Text(fmtCents(st.next7), style = T.title, color = C.Ink)
                            Text(st.next7Label ?: "Nothing due", style = T.caption, color = C.InkMuted, maxLines = 1)
                        }
                    }
                }
            }
            if (st.subs.isNotEmpty()) {
                item { Cap("Subscriptions") }
                item { ListCard { st.subs.forEachIndexed { i, r -> RecRow(r, i == 0, st) { edit = r } } } }
            }
            if (st.others.isNotEmpty()) {
                item { Cap("Bills & income") }
                item { ListCard { st.others.forEachIndexed { i, r -> RecRow(r, i == 0, st) { edit = r } } } }
            }
        }
    }

    if (add || edit != null) RecurringSheet(edit, st, { add = false; edit = null }, vm::save, vm::delete)
}

@Composable
private fun RecRow(r: Recurring, first: Boolean, st: RecurringState, onClick: () -> Unit) {
    val d = Days.date(r.nextDay)
    val acc = st.accounts.firstOrNull { it.id == r.accountId }?.name
    val (glyph, bg, fg) = when (r.kind) {
        RK.SUB -> Triple(r.name.take(1), Color(0xFF1A1C20), C.InkSoft)
        RK.BILL -> Triple("⌂", C.MoneyTint, C.Money)
        RK.INCOME -> Triple("€", C.GainTint, C.Gain)
        else -> Triple("↻", C.GrowTint, C.Grow)
    }
    val priceDelta = if (r.prevAmountCents > 0 && r.prevAmountCents != r.amountCents) r.amountCents - r.prevAmountCents else 0
    val meta: String
    val metaColor: Color
    if (r.kind == RK.SUB) {
        val date = if (r.nextDay - st.today < 7) "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
        else "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
        meta = date + when {
            priceDelta > 0 -> " · price up ${fmtCents(priceDelta)}"
            priceDelta < 0 -> " · price down ${fmtCents(-priceDelta)}"
            else -> if (r.period != "M") " · ${PERIOD_LABEL[r.period]}" else ""
        }
        metaColor = if (priceDelta > 0) C.Fuel else C.InkSubtle
    } else {
        val whenS = when (r.period) {
            "M" -> "${ordinal(d.dayOfMonth)} monthly"
            "W" -> "${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}s"
            else -> "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} yearly"
        }
        meta = listOfNotNull(whenS, if (r.kind == RK.TRANSFER) "transfer" else acc, if (r.autoLog && r.kind != RK.TRANSFER) "auto-logged" else null).joinToString(" · ")
        metaColor = C.InkSubtle
    }
    DivRow(first, onClick = onClick) {
        Avatar(glyph, bg, fg)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(r.name, style = T.body, color = C.Ink, maxLines = 1)
            Text(meta, style = T.caption, color = metaColor, maxLines = 1)
        }
        Text(
            when (r.kind) { RK.INCOME -> fmtCents(r.amountCents, sign = true); RK.BILL -> fmtCents(-r.amountCents); else -> fmtCents(r.amountCents) },
            style = T.body.copy(fontWeight = FontWeight.SemiBold), color = if (r.kind == RK.INCOME) C.Positive else C.Ink,
        )
    }
}

private val KINDS = listOf("Subscription", "Bill", "Income", "Transfer")
private val PERIODS = listOf("Weekly" to "W", "Monthly" to "M", "Yearly" to "Y")

@Composable
private fun RecurringSheet(initial: Recurring?, st: RecurringState, onDismiss: () -> Unit, onSave: (Recurring) -> Unit, onDelete: (Recurring) -> Unit) {
    var kind by remember { mutableIntStateOf(initial?.kind ?: RK.SUB) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(initial?.let { String.format(Locale.US, "%.2f", it.amountCents / 100.0) } ?: "") }
    var period by remember { mutableStateOf(initial?.period ?: "M") }
    var next by remember { mutableLongStateOf(initial?.nextDay ?: Days.today()) }
    var acc by remember { mutableStateOf(initial?.accountId ?: st.accounts.firstOrNull()?.id) }
    var toAcc by remember { mutableStateOf(initial?.toAccountId ?: st.accounts.getOrNull(1)?.id) }
    var cat by remember { mutableStateOf(initial?.category?.takeIf { it != TRANSFER } ?: "Subscriptions") }
    var auto by remember { mutableStateOf(initial?.autoLog ?: true) }
    var pick by remember { mutableStateOf(false) }
    val v = amount.replace(',', '.').toDoubleOrNull()
    val ok = name.isNotBlank() && v != null && v > 0 && (kind != RK.TRANSFER || (acc != null && toAcc != null && acc != toAcc))

    FormSheet(
        if (initial == null) "New recurring" else "Edit recurring", onDismiss, confirmColor = C.Money, canConfirm = ok,
        onConfirm = {
            onSave(
                (initial ?: Recurring(name = "", amountCents = 0, kind = kind, category = "", nextDay = next)).copy(
                    name = name.trim(), amountCents = (v!! * 100).roundToLong(), kind = kind,
                    category = when (kind) { RK.TRANSFER -> TRANSFER; RK.INCOME -> initial?.category?.takeIf { it != TRANSFER && it != "Subscriptions" } ?: "Salary"; RK.SUB -> "Subscriptions"; else -> cat },
                    accountId = acc, toAccountId = if (kind == RK.TRANSFER) toAcc else null, period = period, nextDay = next, autoLog = auto,
                ),
            )
            onDismiss()
        },
        onDelete = initial?.let { { onDelete(it); onDismiss() } },
    ) {
        Segmented(KINDS, kind, selBg = C.MoneyTint, selFg = C.Money) { kind = it }
        LifeField(name, { name = it }, "Name", placeholder = when (kind) { RK.SUB -> "Netflix"; RK.BILL -> "Rent"; RK.INCOME -> "Salary"; else -> "To savings" })
        LifeField(amount, { amount = it }, "Amount, €", keyboard = KeyboardType.Decimal)
        Text("Repeats", style = T.caption, color = C.InkSubtle)
        ChipPicker(PERIODS.map { it.first }, PERIODS.first { it.second == period }.first, C.Money) { l -> period = PERIODS.first { it.first == l }.second }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Next due", style = T.body, color = C.InkMuted, modifier = Modifier.weight(1f).padding(top = 8.dp))
            OChip(dayLabel(next) + " ▾", fg = C.Ink) { pick = true }
        }
        if (kind == RK.BILL) {
            Text("Category", style = T.caption, color = C.InkSubtle)
            ChipPicker(st.categories, cat, C.Money) { cat = it }
        }
        if (st.accounts.isNotEmpty()) {
            Text(if (kind == RK.TRANSFER) "From" else "Account", style = T.caption, color = C.InkSubtle)
            AccChips(st.accounts, acc, allowNone = kind != RK.TRANSFER) { acc = it }
            if (kind == RK.TRANSFER) {
                Text("To", style = T.caption, color = C.InkSubtle)
                AccChips(st.accounts, toAcc, allowNone = false) { toAcc = it }
            }
        }
        Toggle("Log automatically", "Adds the transaction on the due date", auto) { auto = it }
        if (initial != null && initial.prevAmountCents > 0 && initial.prevAmountCents != initial.amountCents)
            Text("Was ${fmtCents(initial.prevAmountCents)} before the last change", style = T.caption, color = C.Fuel)
    }
    if (pick) DayPickerDialog(next, { pick = false }) { next = it }
}

@Composable
internal fun AccChips(accounts: List<Account>, sel: Long?, allowNone: Boolean, onPick: (Long?) -> Unit) {
    ChipPicker(accounts.map { it.name } + if (allowNone) listOf("None") else emptyList(), accounts.firstOrNull { it.id == sel }?.name ?: "None", C.Money) { n ->
        onPick(accounts.firstOrNull { it.name == n }?.id)
    }
}
