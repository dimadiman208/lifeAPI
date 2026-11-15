package com.lifeapi.app.ui.screens.money

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.ChipPicker
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.money.ImportRow
import com.lifeapi.app.vm.money.ImportState
import com.lifeapi.app.vm.money.ImportVM

private val Warn = Color(0xFF4A3A20)
private val WarnBg = Color(0xFF17140E)

/** Money · integrations & import: connections overview, then CSV review once a file is picked. */
@Composable
fun ImportScreen(vm: ImportVM = viewModel()) {
    val nav = LocalNav.current
    val review by vm.review.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val last by vm.lastImport.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::load) }
    fun choose() = pickFile.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))

    val r = review
    if (r != null) {
        BackHandler { vm.cancel() }
        Review(r, accounts.orEmpty(), cats.orEmpty(), vm) { nav.popBackStack() }
        return
    }

    ScreenList(glow = null, bottomExtra = false) {
        item { DetailTop("Import", "CSV statements · manual holdings", onBack = { nav.popBackStack() }) }
        item {
            ListCard {
                DivRow(true, onClick = { nav.navigate("settings/integrations") }) {
                    Avatar("⇄", C.GrowTint, C.Grow)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Swedbank & Binance", style = T.body, color = C.Ink)
                        Text("Live sync lives in Settings › Integrations", style = T.caption, color = C.InkSubtle)
                    }
                    StatusPill("Open", C.Grow)
                }
            }
        }
        item {
            LifeCard(Modifier.fillMaxWidth(), gap = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar("CSV", C.MoneyTint, C.Money)
                    Column(Modifier.weight(1f)) {
                        Text("Import statement", style = T.section, color = C.Ink)
                        Text("Swedbank · Revolut · generic CSV", style = T.caption, color = C.InkSubtle)
                    }
                }
                Text(last?.let { "Last import: $it" } ?: "Date, description and amount columns are found automatically. Duplicates are flagged before anything is saved.", style = T.caption, color = C.InkMuted)
                error?.let { Text(it, style = T.caption, color = C.Negative) }
                PillButton(if (loading) "Reading…" else "Choose file", C.Money, C.Graphite0, Modifier.fillMaxWidth(), 44.dp, 12.dp, style = T.body.copy(fontWeight = FontWeight.SemiBold), enabled = !loading) { choose() }
            }
        }
        item {
            ListCard {
                DivRow(true, onClick = { nav.navigate("money/crypto") }) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Hardware wallet & other holdings", style = T.body, color = C.Ink)
                        Text("Manual · you update quantities", style = T.caption, color = C.InkSubtle)
                    }
                    StatusPill("Manual", C.InkMuted)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Graphite2).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = T.tiny, color = fg)
    }
}

@Composable
private fun Review(st: ImportState, accounts: List<com.lifeapi.app.data.Account>, cats: List<String>, vm: ImportVM, onDone: () -> Unit) {
    val dups = st.rows.filter { it.dup != null }
    val pending = dups.filter { it.keepDup == null }
    val newCount = st.rows.count { it.dup == null && !it.transfer }
    val noCat = st.rows.count { it.include && it.category == null }
    val toImport = st.rows.count { it.include }
    var catFor by remember { mutableStateOf<ImportRow?>(null) }

    ScreenList(glow = null, bottomExtra = false) {
        item { DetailTop("Review import", "${st.fileName} · ${st.rows.size} rows", onBack = { vm.cancel() }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountTile("New", newCount, C.InkSubtle, C.Hairline, Modifier.weight(1f))
                CountTile("Duplicates?", dups.size, C.Fuel, if (dups.isNotEmpty()) Warn else C.Hairline, Modifier.weight(1f))
                CountTile("No category", noCat, C.InkSubtle, C.Hairline, Modifier.weight(1f))
            }
        }
        if (accounts.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Cap("Into account")
                AccChips(accounts, st.accountId, allowNone = true) { vm.setAccount(it) }
                Text("Imported rows are history — account balances aren't changed.", style = T.caption, color = C.InkSubtle, modifier = Modifier.padding(start = 4.dp))
            }
        }
        pending.firstOrNull()?.let { row ->
            val d = row.dup!!
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(WarnBg).border(1.dp, Warn, RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("POSSIBLE DUPLICATE · ${dups.size - pending.size + 1} OF ${dups.size}", style = T.label, color = C.Fuel)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DupSide("From file", row.csv.name, "${shortDate(row.csv.day)} · ${fmtCents(row.csv.cents)}", Modifier.weight(1f))
                        DupSide("Already logged", d.name, "${shortDate(d.day)} · ${fmtCents(d.amountCents)}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Same — skip", C.Fuel, C.Graphite0, Modifier.weight(1f), 40.dp, 12.dp) { vm.decideDup(row.idx, false) }
                        PillButton("Keep both", Color.Transparent, C.Ink, Modifier.weight(1f), 40.dp, 12.dp, border = androidx.compose.foundation.BorderStroke(1.dp, ChipBorder)) { vm.decideDup(row.idx, true) }
                    }
                }
            }
        }
        item {
            ListCard {
                st.rows.filter { it.dup == null || it.keepDup != null }.forEachIndexed { i, row ->
                    DivRow(i == 0, onClick = { vm.toggle(row.idx) }, onLong = { if (!row.transfer) catFor = row }) {
                        CheckBox(row.include)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(row.csv.name, style = T.body, color = if (row.include) C.Ink else C.InkMuted, maxLines = 1)
                            val meta = when {
                                row.transfer -> "detected as transfer" to C.InkSubtle
                                row.keepDup == true -> "kept · possible duplicate" to C.Fuel
                                row.category == null -> "pick a category" to C.Negative
                                row.auto -> "${row.category} (auto)" to C.InkSubtle
                                else -> row.category to C.InkSubtle
                            }
                            Text(
                                "${shortDate(row.csv.day)} · ${meta.first}", style = T.caption, color = meta.second, maxLines = 1,
                                modifier = if (!row.transfer) Modifier.press { catFor = row } else Modifier,
                            )
                        }
                        Text(fmtCents(row.csv.cents, sign = true), style = T.body.copy(fontWeight = FontWeight.SemiBold), color = if (row.csv.cents > 0) C.Positive else C.Ink)
                    }
                }
            }
        }
        if (st.skippedLines > 0) item { Text("${st.skippedLines} line${if (st.skippedLines == 1) "" else "s"} skipped (balances, headers or unreadable)", style = T.caption, color = C.InkSubtle, modifier = Modifier.padding(start = 4.dp)) }
        item {
            PillButton(
                if (toImport == 0) "Nothing selected" else "Import $toImport transaction${if (toImport == 1) "" else "s"}",
                C.Money, C.Graphite0, Modifier.fillMaxWidth(), 52.dp, 16.dp, style = T.section, enabled = toImport > 0,
            ) { vm.commit(onDone) }
        }
    }

    catFor?.let { row ->
        var pick by remember(row.idx) { mutableStateOf(row.category ?: cats.firstOrNull() ?: "Other") }
        val options = if (row.csv.cents > 0) listOf("Income", "Salary", "Refund", "Freelance", "Gift") else cats
        FormSheet(row.csv.name, { catFor = null }, confirm = "Use category", confirmColor = C.Money, onConfirm = { vm.setCategory(row.idx, pick); catFor = null }) {
            Text("${shortDate(row.csv.day)} · ${fmtCents(row.csv.cents)}", style = T.small, color = C.InkMuted)
            ChipPicker(options, pick, C.Money) { pick = it }
        }
    }
}

@Composable
private fun CountTile(label: String, n: Int, labelColor: Color, border: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(C.Graphite1).border(1.dp, border, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = T.tiny, color = labelColor)
        Text("$n", style = T.section.copy(fontSize = 18.sp), color = C.Ink)
    }
}

@Composable
private fun DupSide(label: String, name: String, meta: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Inset).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = T.tiny, color = C.InkSubtle)
        Text(name, style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink, maxLines = 1)
        Text(meta, style = T.caption, color = C.InkMuted)
    }
}

@Composable
private fun CheckBox(on: Boolean) {
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(if (on) C.Money else Color.Transparent)
            .then(if (!on) Modifier.border(1.5.dp, C.Ghost, RoundedCornerShape(7.dp)) else Modifier),
        contentAlignment = Alignment.Center,
    ) { if (on) Icon(Ic.Check, null, tint = C.Graphite0, modifier = Modifier.size(14.dp)) }
}
