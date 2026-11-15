package com.lifeapi.app.ui.screens.money

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.SavingsGoal
import com.lifeapi.app.data.SavingsMove
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.LinearBar
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.money.GoalView
import com.lifeapi.app.vm.money.SavingsVM
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun SavingsScreen(vm: SavingsVM = viewModel()) {
    val nav = LocalNav.current
    val s by vm.state.collectAsStateWithLifecycle()
    val st = s ?: return
    var expanded by remember { mutableStateOf<Long?>(null) }
    var edit by remember { mutableStateOf<SavingsGoal?>(null) }
    var add by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf<Pair<GoalView, Boolean>?>(null) } // goal, deposit?
    val open = expanded ?: st.goals.firstOrNull()?.goal?.id

    ScreenList(glow = null, bottomExtra = false) {
        item {
            DetailTop("Savings goals", if (st.goals.isEmpty()) null else "Envelopes · ${cash(st.hidden, st.allocated).substringBefore('.')} allocated", onBack = { nav.popBackStack() }) {
                RoundIconButton(Ic.Plus, "New goal", size = 40.dp, onClick = { add = true })
            }
        }
        st.accounts.forEach { av -> item(key = "acc${av.account.id}") { SavingsAccountCard(av, st.hidden) } }
        if (st.savingsAccounts.isEmpty() && st.goals.isNotEmpty()) item {
            Text("Tip: mark your savings account as \"Savings\" (Money → tap the account) to earmark its money for goals.", style = T.caption, color = C.InkSubtle)
        }
        if (st.goals.isEmpty()) {
            item {
                EmptyCard(
                    "Savings", C.Money, "Give your savings a purpose",
                    "Split savings into envelopes — a trip, an emergency fund, a laptop. Each shows its real saving pace and when you'll get there.",
                    "Create a goal", { add = true },
                )
            }
        }
        st.goals.forEach { gv ->
            item(key = gv.goal.id) {
                if (gv.goal.id == open) Hero(gv, st.hidden, { move = gv to true }, { move = gv to false }, { edit = gv.goal }, vm::deleteMove)
                else Compact(gv, st.hidden) { expanded = gv.goal.id }
            }
        }
    }

    if (add || edit != null) GoalSheet(edit, st.savingsAccounts, { add = false; edit = null }, { g, start -> vm.saveGoal(g, start) }, { vm.deleteGoal(it); expanded = null })
    move?.let { (gv, dep) -> MoveSheet(gv, dep, { move = null }) { cents, note -> vm.move(gv.goal, if (dep) cents else -cents, note) } }
}

private fun paceLine(gv: GoalView, hidden: Boolean): String = buildString {
    if (gv.left > 0) append("${cash(hidden, gv.left).substringBefore('.')} left") else append("Target reached")
    if (gv.paceCents > 0 && gv.left > 0) append(" · ${cashShort(gv.paceCents)}/mo ${if (gv.paceFromPlan) "plan" else "pace"}")
}

@Composable
private fun Hero(gv: GoalView, hidden: Boolean, onAdd: () -> Unit, onWithdraw: () -> Unit, onEdit: () -> Unit, onDeleteMove: (SavingsMove) -> Unit) {
    LifeCard(
        Modifier.fillMaxWidth(), radius = 22.dp, border = Color(0xFF262B3D),
        brush = Brush.verticalGradient(listOf(Color(0xFF151826), C.Graphite1)), gap = 12.dp,
    ) {
        Row(Modifier.fillMaxWidth().press(onClick = onEdit)) {
            Text(gv.goal.name, style = T.section.copy(fontSize = 17.sp), color = C.Ink, modifier = Modifier.weight(1f))
            Text("${gv.pct}%", style = T.small, color = C.InkMuted)
        }
        Text(
            buildAnnotatedString {
                append(cash(hidden, gv.saved).substringBefore('.'))
                withStyle(SpanStyle(fontSize = 14.sp, color = C.InkSubtle, fontWeight = FontWeight.Medium)) { append("  of ${cashShort(gv.goal.targetCents)}") }
            },
            style = T.title.copy(fontSize = 26.sp), color = C.Ink,
        )
        LinearBar(gv.pct / 100f, C.Money, C.MoneyTint, 8.dp)
        Row(Modifier.fillMaxWidth()) {
            Text(paceLine(gv, hidden), style = T.caption, color = C.InkMuted, modifier = Modifier.weight(1f))
            Text(gv.eta ?: "no monthly plan yet", style = T.caption, color = if (gv.eta != null) C.Positive else C.InkSubtle)
        }
        if (gv.wish != null) Text("Linked to Wishlist · ${gv.wish.name}", style = T.caption, color = C.Grow)
        gv.account?.let { a ->
            Text(
                "Earmarked in ${a.name} · money stays in the account · ${cash(hidden, gv.accountFree.coerceAtLeast(0)).substringBefore('.')} free there",
                style = T.caption, color = C.InkSubtle,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val acc = gv.account
            PillButton(if (acc != null) "Add from ${acc.name}" else "Add money", C.Money, C.Graphite0, Modifier.weight(1f), 44.dp, 14.dp,
                style = T.body.copy(fontWeight = FontWeight.SemiBold), enabled = acc == null || gv.accountFree > 0, onClick = onAdd)
            PillButton(
                if (acc != null) "Release" else "Withdraw", Color.Transparent, C.Ink, Modifier.weight(1f), 44.dp, 14.dp, border = BorderStroke(1.dp, ChipBorder),
                style = T.body, enabled = gv.saved > 0, onClick = onWithdraw,
            )
        }
        if (gv.moves.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            gv.moves.take(5).forEach { m ->
                var confirm by remember(m.id) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().press(onLongClick = { confirm = true }) { confirm = false }) {
                    Text("${shortDate(m.day)}${if (m.note.isNotBlank()) " · ${m.note}" else ""}", style = T.caption, color = C.InkMuted, modifier = Modifier.weight(1f), maxLines = 1)
                    if (confirm) Text("Delete", style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = C.Negative, modifier = Modifier.press { onDeleteMove(m) })
                    else Text(fmtCents(m.amountCents, sign = true), style = T.caption, color = if (m.amountCents < 0) C.Negative else C.Ink)
                }
            }
        }
    }
}

@Composable
private fun Compact(gv: GoalView, hidden: Boolean, onClick: () -> Unit) {
    LifeCard(Modifier.fillMaxWidth().press(onClick = onClick), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp), gap = 8.dp) {
        Row(Modifier.fillMaxWidth()) {
            Text(gv.goal.name, style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink, modifier = Modifier.weight(1f))
            Text("${cash(hidden, gv.saved).substringBefore('.')} / ${cashShort(gv.goal.targetCents)}", style = T.small, color = C.InkMuted)
        }
        LinearBar(gv.pct / 100f, C.Money, C.MoneyTint, 6.dp)
        Text(
            when {
                gv.wish != null && gv.paceCents == 0L -> "Linked to Wishlist · no monthly plan yet"
                gv.eta != null && gv.left > 0 -> "${gv.eta} at ${cashShort(gv.paceCents)}/mo"
                gv.eta != null -> gv.eta
                else -> "No monthly plan yet"
            },
            style = T.caption, color = C.InkSubtle,
        )
    }
}

private fun parse(s: String) = s.replace(',', '.').trim().toDoubleOrNull()

@Composable
private fun GoalSheet(initial: SavingsGoal?, savingsAccounts: List<com.lifeapi.app.data.Account>, onDismiss: () -> Unit, onSave: (SavingsGoal, Long) -> Unit, onDelete: (SavingsGoal) -> Unit) {
    var accId by remember { mutableStateOf(initial?.accountId?.takeIf { id -> savingsAccounts.any { it.id == id } } ?: savingsAccounts.firstOrNull()?.id) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var target by remember { mutableStateOf(initial?.let { (it.targetCents / 100).toString() } ?: "") }
    var monthly by remember { mutableStateOf(initial?.monthlyCents?.takeIf { it > 0 }?.let { (it / 100).toString() } ?: "") }
    var start by remember { mutableStateOf("") }
    val t = parse(target)
    FormSheet(
        if (initial == null) "New savings goal" else "Edit goal", onDismiss, confirmColor = C.Money,
        canConfirm = name.isNotBlank() && t != null && t > 0 && savingsAccounts.isNotEmpty(),
        onConfirm = {
            onSave(
                (initial ?: SavingsGoal(name = "", targetCents = 0)).copy(
                    name = name.trim(), targetCents = (t!! * 100).roundToLong(), monthlyCents = ((parse(monthly) ?: 0.0) * 100).roundToLong(),
                    accountId = accId,
                ),
                ((parse(start) ?: 0.0) * 100).roundToLong(),
            )
            onDismiss()
        },
        onDelete = initial?.let { { onDelete(it); onDismiss() } },
    ) {
        LifeField(name, { name = it }, "Goal", placeholder = "Japan, autumn 2027")
        LifeField(target, { target = it }, "Target, €", keyboard = KeyboardType.Decimal)
        LifeField(monthly, { monthly = it }, "Monthly plan, € (optional)", keyboard = KeyboardType.Decimal)
        if (savingsAccounts.isEmpty()) {
            Text("Mark one of your accounts as Savings first (Money → tap the account → Type: Savings). Goals reserve money from it.",
                style = T.caption, color = C.Negative)
        } else {
            Text("Reserved from", style = T.caption, color = C.InkMuted)
            com.lifeapi.app.ui.components.ChipPicker(
                savingsAccounts.map { it.name },
                savingsAccounts.firstOrNull { it.id == accId }?.name ?: savingsAccounts.first().name, C.Money,
            ) { pick -> accId = savingsAccounts.firstOrNull { it.name == pick }?.id }
        }
        if (initial == null && savingsAccounts.isNotEmpty()) LifeField(start, { start = it }, "Reserve now, € (optional)", keyboard = KeyboardType.Decimal)
        Text("The ETA uses what you actually put in over the last 3 months; the plan is only a fallback.", style = T.caption, color = C.InkSubtle)
    }
}

@Composable
private fun MoveSheet(gv: GoalView, deposit: Boolean, onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    var dep by remember { mutableStateOf(deposit) }
    var amount by remember { mutableStateOf(if (deposit && gv.paceCents > 0) (gv.paceCents / 100).toString() else "") }
    var note by remember { mutableStateOf("") }
    val v = parse(amount)
    val cents = v?.let { (it * 100).roundToLong() } ?: 0
    val linked = gv.account != null
    val freeCap = gv.accountFree.coerceAtLeast(0)
    // money can only be reserved from free savings; an unlinked goal can't take new money (it's linked automatically once a savings account exists)
    val ok = cents > 0 && (if (dep) linked && cents <= freeCap else cents <= gv.saved)
    val addWord = if (linked) "Add from ${gv.account!!.name}" else "Add money"
    val outWord = if (linked) "Release" else "Withdraw"
    FormSheet(gv.goal.name, onDismiss, confirm = if (dep) addWord else outWord, confirmColor = C.Money, canConfirm = ok, onConfirm = {
        onSave(cents, note.trim().ifEmpty { if (linked) (if (dep) "earmarked" else "released") else if (dep) "deposit" else "withdrawal" }); onDismiss()
    }) {
        Segmented(listOf(if (linked) "Add" else "Add money", outWord), if (dep) 0 else 1, selBg = C.MoneyTint, selFg = C.Money) { dep = it == 0 }
        LifeField(amount, { amount = it }, "Amount, €", keyboard = KeyboardType.Decimal)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val all = if (dep) (if (linked) freeCap else minOf(gv.left, Long.MAX_VALUE)) else gv.saved
            listOf(5000L to "€50", 10000L to "€100", all to if (dep) (if (linked) "All free" else "Rest") else "All").forEach { (c, l) ->
                if (c > 0) PillButton(l, C.Graphite2, C.Ink, height = 34.dp) { amount = String.format(Locale.US, "%.2f", c / 100.0) }
            }
        }
        LifeField(note, { note = it }, "Note (optional)", placeholder = if (dep) "monthly" else "flights deposit")
        Text(
            when {
                dep && linked -> "${fmtCents(freeCap)} free in ${gv.account!!.name} · nothing leaves the account, it's just earmarked"
                dep -> "${fmtCents(gv.saved)} saved · ${fmtCents(gv.left)} to go"
                linked -> "Back to free money in ${gv.account!!.name} · up to ${fmtCents(gv.saved)}"
                else -> "Up to ${fmtCents(gv.saved)} available"
            },
            style = T.caption, color = if ((!dep && cents > gv.saved) || (dep && linked && cents > freeCap)) C.Negative else C.InkSubtle, modifier = Modifier.padding(start = 4.dp),
        )
    }
}


/** Savings account summary: balance, how much is earmarked for goals and what's still free. */
@Composable
fun SavingsAccountCard(av: com.lifeapi.app.vm.money.SavingsAccountView, hidden: Boolean, onClick: (() -> Unit)? = null) {
    LifeCard(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier),
        radius = 20.dp, border = Color(0xFF233128), gap = 10.dp,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(av.account.name, style = T.section, color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1)
            androidx.compose.foundation.layout.Box(
                Modifier.clip(RoundedCornerShape(6.dp)).background(C.GainTint).padding(horizontal = 6.dp, vertical = 2.dp),
            ) { Text("Savings", style = T.tiny, color = C.Gain) }
        }
        Text(cash(hidden, av.account.balanceCents).substringBefore('.'), style = T.title.copy(fontSize = 24.sp), color = C.Ink)
        val bal = av.account.balanceCents.coerceAtLeast(1)
        LinearBar((av.allocated.toFloat() / bal).coerceIn(0f, 1f), C.Gain, C.GainTint, 6.dp)
        Text(
            if (av.goals.isEmpty()) "Nothing earmarked yet · create a goal funded by this account"
            else "Earmarked ${cash(hidden, av.allocated).substringBefore('.')} for ${av.goals.size} goal${if (av.goals.size == 1) "" else "s"} · free ${cash(hidden, av.free.coerceAtLeast(0)).substringBefore('.')}",
            style = T.caption, color = C.InkMuted,
        )
        if (av.overAllocated > 0) Text(
            "Over-earmarked by ${cash(hidden, av.overAllocated).substringBefore('.')} — the balance dropped below your goals. Release some.",
            style = T.caption, color = C.Negative,
        )
    }
}
