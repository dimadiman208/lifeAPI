package com.lifeapi.app.ui.screens.money

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Txn
import com.lifeapi.app.data.money.AUTO_EXPENSE
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.money.CategoryVM
import com.lifeapi.app.vm.money.MerchantGroup

/** Route helper: category names can contain "&" and spaces. */
fun categoryRoute(category: String, offset: Int) = "money/category/${android.net.Uri.encode(category)}?m=$offset"

/** One category for one month, grouped by merchant. "Other" gets one-tap sorting per merchant. */
@Composable
fun CategoryScreen(category: String, offset: Int, vm: CategoryVM = viewModel()) {
    LaunchedEffect(category, offset) { vm.bind(category, offset) }
    val nav = LocalNav.current
    val s by vm.state.collectAsStateWithLifecycle()
    val st = s?.takeIf { it.category == category } ?: return
    var editing by remember { mutableStateOf<Txn?>(null) }
    // null = closed; "" = move everything in this view; otherwise a merchant group key
    var moving by remember { mutableStateOf<String?>(null) }
    val open = remember { mutableStateListOf<String>() }
    val other = category == "Other"

    ScreenList(glow = null, bottomExtra = false) {
        item {
            DetailTop(category, "${st.monthTitle} · ${st.count} payment${if (st.count == 1) "" else "s"}", onBack = { nav.popBackStack() }) {
                Text(cash(st.hidden, st.total), style = T.section, color = C.Ink)
            }
        }
        if (st.groups.isEmpty()) {
            item { EmptyCard("Spending", C.Money, "Nothing here", "No $category spending in ${st.monthTitle}.", "Back", { nav.popBackStack() }) }
        }
        if (st.groups.isNotEmpty()) item {
            com.lifeapi.app.ui.components.PillButton(
                "Move all ${st.count} to another category", C.MoneyTint, C.Money, Modifier.fillMaxWidth(), 44.dp, 12.dp,
            ) { moving = "" }
        }
        if (other && st.groups.isNotEmpty()) item {
            LifeCard(Modifier.fillMaxWidth(), radius = 18.dp, padding = androidx.compose.foundation.layout.PaddingValues(14.dp), gap = 4.dp) {
                Text("${st.count} uncategorised · tap a category under a merchant", style = T.body, color = C.Ink)
                Text("Your choice is remembered — this merchant goes there automatically from now on.", style = T.caption, color = C.InkSubtle)
            }
        }
        st.groups.forEach { g ->
            item(key = g.key) {
                MerchantCard(g, st.hidden, expanded = g.key in open, other = other,
                    onToggle = { if (g.key in open) open.remove(g.key) else open.add(g.key) },
                    onAssign = { c -> vm.moveMerchant(g, c) },
                    onMove = { moving = g.key },
                    onEdit = { editing = it })
            }
        }
    }
    editing?.let { TxnEditSheet(it, st.hidden, onDismiss = { editing = null }) }
    moving?.let { key ->
        val g = st.groups.firstOrNull { it.key == key }
        val cats by vm.categories.collectAsStateWithLifecycle(emptyList())
        MoveSheet(
            title = if (g == null) "Move ${st.count} payments" else "Move “${g.name}”",
            subtitle = if (g == null) "All of $category in ${st.monthTitle} · merchants are remembered"
            else "${g.txns.size} payment${if (g.txns.size == 1) "" else "s"} · future ones from it follow",
            options = (cats.filter { !it.income }.map { it.name } + AUTO_EXPENSE).distinct().filter { it != category },
            onAdd = vm::addCategory,
            onDismiss = { moving = null },
        ) { target -> if (g == null) vm.moveAll(target) else vm.moveMerchant(g, target) }
    }
}

/** Pick a target category (chips + "+ New"), then Move. Fixed max height, scrolls inside. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MoveSheet(title: String, subtitle: String, options: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    var sel by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newCat by remember { mutableStateOf("") }
    com.lifeapi.app.ui.screens.body.EntrySheet(
        title, onDismiss, confirm = sel?.let { "Move to $it" } ?: "Pick a category", hue = C.Money,
        canConfirm = sel != null, onConfirm = { sel?.let(onMove); onDismiss() }, subtitle = subtitle,
    ) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o -> OChip(o, sel = o == sel) { sel = o } }
            OChip(if (adding) "Cancel" else "+ New", fg = C.Money) { adding = !adding }
        }
        if (adding) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.lifeapi.app.ui.components.LifeField(newCat, { newCat = it }, "New category", Modifier.weight(1f))
            com.lifeapi.app.ui.components.PillButton("Add", C.MoneyTint, C.Money, height = 44.dp, enabled = newCat.isNotBlank()) {
                onAdd(newCat); sel = newCat.trim(); newCat = ""; adding = false
            }
        }
    }
}

@Composable
private fun MerchantCard(g: MerchantGroup, hidden: Boolean, expanded: Boolean, other: Boolean, onToggle: () -> Unit, onAssign: (String) -> Unit, onMove: () -> Unit, onEdit: (Txn) -> Unit) {
    ListCard {
        Column(Modifier.animateContentSize()) {
            DivRow(true, onClick = { if (g.txns.size == 1) onEdit(g.txns.first()) else onToggle() }) {
                Avatar(g.name.take(1).uppercase())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(g.name, style = T.body, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${g.txns.size} payment${if (g.txns.size == 1) "" else "s"}", style = T.caption, color = C.InkSubtle)
                }
                Text(cash(hidden, g.cents), style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink)
                if (g.txns.size > 1) Icon(if (expanded) Ic.ChevronDown else Ic.ChevronRight, null, tint = C.InkSubtle, modifier = Modifier.padding(start = 2.dp))
            }
            if (!other) Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OChip("Move ${if (g.txns.size == 1) "it" else "all ${g.txns.size}"} to…", fg = C.Money, onClick = onMove)
                if (g.txns.size > 1 && !expanded) OChip("Pick one by one", onClick = onToggle)
            }
            if (other) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) { AUTO_EXPENSE.filter { it != "Other" }.forEach { c -> OChip(c) { onAssign(c) } } }
            if (expanded) Text(
                "Tap a payment to move just that one", style = T.caption, color = C.InkFaint,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 4.dp),
            )
            if (expanded) g.txns.forEach { t ->
                DivRow(false, onClick = { onEdit(t) }, minH = 48.dp) {
                    Text(shortDate(t.day), style = T.mono, color = C.InkSubtle)
                    Text(t.note.ifBlank { t.name }, style = T.caption.copy(fontSize = 12.sp), color = C.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(cash(hidden, -t.amountCents), style = T.small, color = C.Ink)
                }
            }
        }
    }
}
