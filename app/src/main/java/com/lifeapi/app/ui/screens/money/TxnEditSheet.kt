package com.lifeapi.app.ui.screens.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Txn
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.data.money.AUTO_EXPENSE
import com.lifeapi.app.data.money.DEFAULT_INCOME
import com.lifeapi.app.data.money.TRANSFER
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.body.EntrySheet
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.money.TxnEditVM

/**
 * Edit a transaction: name, category (one tap), note. "Apply to all … and remember" re-sorts every
 * transaction from the same merchant and saves a rule used by future bank syncs and imports.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxnEditSheet(t: Txn, hidden: Boolean, onDismiss: () -> Unit, vm: TxnEditVM = viewModel()) {
    val cats by vm.categories.collectAsStateWithLifecycle(emptyList())
    val key = remember(t.id) { vm.merchantKey(t) }
    val sameCount by remember(key) { vm.countFor(key) }.collectAsStateWithLifecycle(0)
    var name by remember(t.id) { mutableStateOf(t.name) }
    var note by remember(t.id) { mutableStateOf(t.note.takeUnless { it.startsWith("import:") && it.length < 40 } ?: t.note) }
    var cat by remember(t.id) { mutableStateOf(t.category) }
    var remember by remember(t.id) { mutableStateOf(key.isNotBlank()) }
    var adding by remember { mutableStateOf(false) }
    var newCat by remember { mutableStateOf("") }
    val income = t.amountCents > 0
    val options = run {
        val fromDb = cats.filter { it.income == income }.map { it.name }
        val base = if (income) DEFAULT_INCOME + "Income" else AUTO_EXPENSE
        (fromDb + base + TRANSFER + t.category).distinct()
    }
    val d = com.lifeapi.app.data.Days.date(t.day)
    EntrySheet(
        "Transaction", onDismiss,
        confirm = "Save", hue = C.Money,
        canConfirm = cat.isNotBlank(),
        onConfirm = { vm.save(t, name, cat, note, remember && key.isNotBlank()); onDismiss() },
        onDelete = { vm.delete(t); onDismiss() },
        subtitle = (if (hidden) "€ ••••" else fmtCents(t.amountCents, sign = true)) + " · ${d.dayOfMonth} ${d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)} ${d.year}" +
            (if (t.extId != null) " · from bank" else "") + (if (t.pending) " · pending" else ""),
    ) {
        LifeField(name, { name = it }, "Name")
        Text("Category", style = T.caption, color = C.InkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o -> OChip(o, sel = o == cat) { cat = o } }
            OChip(if (adding) "Cancel" else "+ New", fg = C.Money) { adding = !adding }
        }
        if (adding) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeField(newCat, { newCat = it }, "New category", Modifier.weight(1f))
            Text(
                "Add", style = T.smallB, color = if (newCat.isNotBlank()) C.Money else C.InkFaint,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).press(enabled = newCat.isNotBlank()) {
                    vm.addCategory(newCat, income); cat = newCat.trim(); newCat = ""; adding = false
                }.padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
        if (key.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Inset).border(1.dp, C.Hairline, RoundedCornerShape(14.dp))
                    .press { remember = !remember }.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Apply to all “${name.ifBlank { t.name }}”", style = T.body, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (sameCount > 1) "$sameCount transactions now, and future ones from this merchant" else "Future transactions from this merchant too",
                        style = T.caption, color = C.InkSubtle,
                    )
                }
                Switch(
                    remember, { remember = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = C.Graphite0, checkedTrackColor = C.Money, uncheckedTrackColor = C.Graphite3, uncheckedBorderColor = C.Graphite3),
                )
            }
        }
        LifeField(note, { note = it }, "Note", singleLine = false)
        if (t.extId != null) Box(Modifier.fillMaxWidth().height(1.dp))
        if (t.extId != null) Text("Amount and date come from the bank and can't be changed here.", style = T.caption, color = C.InkFaint)
    }
}
