package com.lifeapi.app.ui.screens.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.lifeapi.app.data.WishItem
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LinearBar
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.components.StatBox
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow.PlanVM

@Composable
fun WishlistScreen(onBack: () -> Unit, vm: PlanVM = viewModel()) {
    val ws by vm.wishes.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<WishItem?>(null) }
    var new by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<WishItem?>(null) }
    var decide by remember { mutableStateOf<WishItem?>(null) }
    val w = ws ?: return

    DetailList(
        "Wishlist", if (w.items.isEmpty()) null else "${w.items.size} item${if (w.items.size == 1) "" else "s"}", onBack,
        trailing = { RoundIconButton(Ic.Plus, "Add item", size = 40.dp, bg = C.MoneyTint, tint = C.Money) { new = true } },
        glow = C.Money,
    ) {
        if (w.items.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    "Wishlist", C.Money, "Want it? Write it down.",
                    "Price, priority and how much you've put aside. A 30-day wait note kills most impulse buys — funded items ask you to decide.",
                    "Add first item", { new = true },
                )
            }
            return@DetailList
        }
        item(key = "sum") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Total wanted", Modifier.weight(1f)) { Text(fmtCents(w.totalWanted), style = T.number.copy(fontSize = 20.sp), color = C.Ink) }
                StatBox("Saved toward", Modifier.weight(1f)) { Text(fmtCents(w.savedToward), style = T.number.copy(fontSize = 20.sp), color = C.Money) }
            }
        }
        item(key = "sort") { Segmented(listOf("Priority", "Closest", "Price"), sort, selBg = C.MoneyTint, selFg = C.Money) { sort = it } }
        val list = when (sort) {
            1 -> w.items.sortedByDescending { if (it.priceCents > 0) it.savedCents.toDouble() / it.priceCents else 0.0 }
            2 -> w.items.sortedByDescending { it.priceCents }
            else -> w.items.sortedWith(compareBy<WishItem> { it.priority }.thenBy { it.id })
        }
        items(list, key = { "w-${it.id}" }) { item ->
            WishCard(item, onClick = { if (item.savedCents >= item.priceCents) decide = item else edit = item }, onAdd = { addTo = item })
        }
        item(key = "hint") { Text("Tap to edit · + to put money aside", style = T.caption, color = C.InkFaint, modifier = Modifier.padding(start = 4.dp)) }
    }

    if (new || edit != null) WishSheet(edit, { new = false; edit = null }, vm::saveWish, vm::deleteWish)
    addTo?.let { item -> AmountSheet("Save toward ${item.name}", C.Money, { addTo = null }) { vm.addSaved(item, it) } }
    decide?.let { item ->
        FormSheet("Funded — buy it?", { decide = null }, confirm = "Bought it", confirmColor = C.Gain, onConfirm = { vm.buy(item); decide = null }) {
            Text("${item.name} · ${fmtCents(item.priceCents)} is fully saved. Still want it?", style = T.bodyR, color = C.InkSoft)
            GhostButton("Keep saving · edit", Modifier.fillMaxWidth()) { decide = null; edit = item }
            GhostButton("Remove from list", Modifier.fillMaxWidth(), color = C.Negative) { vm.deleteWish(item); decide = null }
        }
    }
}

@Composable
private fun WishCard(w: WishItem, onClick: () -> Unit, onAdd: () -> Unit) {
    val funded = w.savedCents >= w.priceCents
    val pct = if (w.priceCents > 0) (w.savedCents.toFloat() / w.priceCents).coerceIn(0f, 1f) else 0f
    LifeCard(
        Modifier.fillMaxWidth().press(onClick = onClick), radius = 18.dp, padding = PaddingValues(14.dp), gap = 10.dp,
        border = if (funded) Color(0xFF2A4A35) else C.Hairline,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(6.dp)).background(if (w.priority == 1) Color(0xFF1F2610) else C.Track).padding(horizontal = 6.dp, vertical = 2.dp),
            ) { Text("P${w.priority}", style = T.label.copy(fontSize = 10.sp), color = if (w.priority == 1) C.Volt else C.InkMuted) }
            Text(w.name, style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmtCents(w.priceCents), style = T.body, color = C.Ink)
        }
        LinearBar(pct, if (funded) C.Gain else C.Money, C.Track, height = 5.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (funded) "${fmtCents(w.savedCents)} saved" else if (w.savedCents > 0) "${fmtCents(w.savedCents)} saved · ${fmtCents(w.priceCents - w.savedCents)} to go" else "€0 saved",
                    style = T.caption, color = C.InkSubtle,
                )
                val note = if (funded) "Funded — buy?" else w.note
                if (note.isNotBlank()) Text(note, style = T.caption, color = if (funded) C.Gain else C.InkSubtle)
            }
            if (!funded) Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.MoneyTint).press(haptic = true, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { androidx.compose.material3.Icon(Ic.Plus, "Save toward", tint = C.Money, modifier = Modifier.size(16.dp)) }
        }
    }
}
