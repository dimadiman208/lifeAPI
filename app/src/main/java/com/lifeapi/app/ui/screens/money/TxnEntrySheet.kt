package com.lifeapi.app.ui.screens.money

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.data.Account
import com.lifeapi.app.data.Days
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RollingText
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.EntryDraft

private val TYPES = listOf("Expense", "Income", "Transfer")
private val REPEATS = listOf("never" to "", "weekly" to "W", "monthly" to "M", "yearly" to "Y")

/**
 * Keypad transaction entry (MoneyFlow · New transaction).
 * type: 0 expense, 1 income, 2 transfer.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxnEntrySheet(
    initialType: Int,
    accounts: List<Account>,
    expenseCats: List<String>,
    incomeCats: List<String>,
    onDismiss: () -> Unit,
    onAddCategory: (String, Boolean) -> Unit,
    onSave: (EntryDraft) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableIntStateOf(initialType) }
    var amt by remember { mutableStateOf("0") }
    var cat by remember { mutableStateOf(if (initialType == 1) incomeCats.firstOrNull() ?: "Salary" else expenseCats.firstOrNull() ?: "Other") }
    var name by remember { mutableStateOf("") }
    var acc by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var toAcc by remember { mutableStateOf(accounts.getOrNull(1)?.id) }
    var day by remember { mutableLongStateOf(Days.today()) }
    var repeat by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var newCat by remember { mutableStateOf<String?>(null) }

    val cents = amt.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L
    val isT = type == 2
    val accName = accounts.firstOrNull { it.id == acc }?.name
    val canSave = cents > 0 && (!isT || (acc != null && toAcc != null && acc != toAcc))

    fun press(k: String) {
        var a = if (amt == "0") "" else amt
        when (k) {
            "del" -> a = a.dropLast(1)
            "." -> if ('.' !in a) a = (a.ifEmpty { "0" }) + "."
            else -> { val dot = a.indexOf('.'); if ((dot < 0 && a.length < 7) || (dot >= 0 && a.length - dot <= 2)) a += k }
        }
        amt = a.ifEmpty { "0" }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = C.Graphite1,
        scrimColor = Color(0x9E050608),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Ghost)) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // type tabs
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Inset).border(1.dp, C.Hairline, RoundedCornerShape(14.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TYPES.forEachIndexed { i, l ->
                    val sel = i == type
                    val bg by animateColorAsState(if (sel) C.Graphite3 else Color.Transparent, tween(220), label = "t")
                    Box(
                        Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(bg).press {
                            if (type != i) {
                                type = i
                                cat = if (i == 1) incomeCats.firstOrNull() ?: "Salary" else expenseCats.firstOrNull() ?: "Other"
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Text(l, style = T.smallB, color = if (sel) C.Ink else C.InkSubtle) }
                }
            }
            // amount
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RollingText(
                    (if (type == 1) "+" else "") + "€" + amt,
                    T.display.copy(fontSize = 44.sp, lineHeight = 50.sp),
                    if (type == 1) C.Positive else C.Ink,
                )
                Text(
                    if (isT) "Between your accounts · not counted as spending" else listOfNotNull(cat, accName).joinToString(" · "),
                    style = T.caption, color = C.InkSubtle,
                )
                BasicTextField(
                    name, { name = it.take(60) }, singleLine = true,
                    textStyle = T.small.copy(color = C.InkSoft, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(C.Money),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(),
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (name.isEmpty()) Text(if (isT) "Note (optional)" else if (type == 1) "From whom? (optional)" else "Where / what? (optional)", style = T.small, color = C.InkFaint)
                            inner()
                        }
                    },
                )
            }
            if (isT) {
                if (accounts.size < 2) {
                    Text("Add at least two accounts to move money between them.", style = T.small, color = C.Negative, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountTile("From", acc, accounts, Modifier.weight(1f)) { acc = it }
                    Text("→", style = T.section, color = C.InkSubtle, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                    AccountTile("To", toAcc, accounts, Modifier.weight(1f)) { toAcc = it }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (if (type == 1) incomeCats else expenseCats).forEach { c -> OChip(c, c == cat) { cat = c } }
                    if (newCat == null) OChip("+ New") { newCat = "" }
                }
                newCat?.let { v ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicTextField(
                            v, { newCat = it.take(30) }, singleLine = true,
                            textStyle = T.body.copy(color = C.Ink), cursorBrush = SolidColor(C.Money),
                            modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(Inset)
                                .border(1.dp, C.Hairline, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                            decorationBox = { inner -> Box { if (v.isEmpty()) Text("Category name", style = T.body, color = C.InkFaint); inner() } },
                        )
                        PillButton("Add", C.Money, C.Graphite0, height = 40.dp, radius = 12.dp, enabled = v.isNotBlank()) {
                            onAddCategory(v.trim(), type == 1); cat = v.trim(); newCat = null
                        }
                    }
                }
            }
            // account · date · repeat
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isT && accounts.isNotEmpty()) {
                    DropChip(accName ?: "No account", accounts.map { it.name to it.id as Long? } + ("No account" to null), { acc = it })
                }
                OChip(dayLabel(day) + " ▾", fg = C.Ink) { pickDate = true }
                DropChip("Repeat: " + REPEATS.first { it.second == repeat }.first, REPEATS, { repeat = it }, fg = if (repeat.isEmpty()) C.InkMuted else C.Ink)
            }
            // keypad
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "del")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(C.Graphite2)
                                .press(haptic = true, onLongClick = if (k == "del") ({ amt = "0" }) else null) { press(k) },
                            contentAlignment = Alignment.Center,
                        ) { Text(if (k == "del") "⌫" else k, style = T.title.copy(fontWeight = FontWeight.Medium, fontSize = 22.sp), color = C.Ink) }
                    }
                }
            }
            PillButton(
                "Save " + TYPES[type].lowercase(), if (type == 1) C.Gain else C.Money, C.Graphite0, Modifier.fillMaxWidth(),
                height = 52.dp, radius = 16.dp, style = T.section, enabled = canSave,
            ) {
                onSave(EntryDraft(type, cents, name.trim(), cat, acc, if (isT) toAcc else null, day, repeat))
                onDismiss()
            }
        }
    }
    if (pickDate) DayPickerDialog(day, { pickDate = false }) { day = minOf(it, Days.today() + 366) }
}

@Composable
private fun AccountTile(label: String, sel: Long?, accounts: List<Account>, modifier: Modifier, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, C.Graphite3, RoundedCornerShape(14.dp))
                .press { open = true }.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = T.tiny, color = C.InkSubtle)
            Text(accounts.firstOrNull { it.id == sel }?.name ?: "Pick", style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink, maxLines = 1)
        }
        androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = C.Graphite2) {
            accounts.forEach { a ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(a.name, style = T.body, color = C.Ink) }, onClick = { open = false; onPick(a.id) })
            }
        }
    }
}
