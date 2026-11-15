package com.lifeapi.app.ui.screens.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.CryptoHolding
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.ChipPicker
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.animatedProgress
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.Mono
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.money.CoinRow
import com.lifeapi.app.vm.money.CryptoVM
import java.util.Locale
import kotlin.math.roundToLong

private val COIN_SHADES = listOf(C.Grow, Color(0xFF8C72B8), Color(0xFF5E4D80), Color(0xFF453A5E), Color(0xFF332B45))

private fun qtyFmt(q: Double): String = String.format(Locale.US, if (q >= 100) "%,.2f" else if (q >= 1) "%.4f" else "%.6f", q).trimEnd('0').trimEnd('.')

@Composable
fun CryptoScreen(vm: CryptoVM = viewModel()) {
    val nav = LocalNav.current
    val s by vm.state.collectAsStateWithLifecycle()
    val st = s ?: return
    var price by remember { mutableStateOf<CoinRow?>(null) }
    var edit by remember { mutableStateOf<CryptoHolding?>(null) }

    ScreenList(glow = null, bottomExtra = false) {
        item { DetailTop("Crypto", "Binance (live) + manual holdings", onBack = { nav.popBackStack() }) }
        if (st.coins.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(cash(st.hidden, st.total), style = T.display.copy(fontSize = 34.sp, lineHeight = 40.sp), color = C.Ink)
                    val parts = listOfNotNull(
                        if (st.change != 0L) "${fmtCents(st.change, sign = true)} since last price update" else null,
                        st.plPct?.let { String.format(Locale.US, "%+.1f%% all time", it) },
                    )
                    if (parts.isNotEmpty() && !st.hidden) Text(parts.joinToString(" · "), style = T.small, color = if ((st.plPct ?: 0.0) >= 0) C.Positive else C.Negative)
                }
            }
            item {
                val total = st.total.toFloat().coerceAtLeast(1f)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val p = animatedProgress(1f)
                    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                        val gap = 2.dp.toPx(); val usable = size.width - gap * (st.coins.size - 1)
                        clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width * p, size.height, CornerRadius(size.height / 2))) }) {
                            var x = 0f
                            st.coins.forEachIndexed { i, c ->
                                val w = usable * c.sum.valueCents / total
                                drawRect(COIN_SHADES[i.coerceAtMost(COIN_SHADES.lastIndex)], Offset(x, 0f), Size(w, size.height)); x += w + gap
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        st.coins.take(5).forEach { c -> Text("${c.sum.sym} ${(c.sum.valueCents * 100 / total).toInt()}%", style = T.caption, color = C.InkMuted) }
                    }
                }
            }
            item {
                ListCard {
                    st.coins.forEachIndexed { i, c ->
                        DivRow(i == 0, onClick = { price = c }, onLong = { edit = c.holdings.first() }) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row {
                                    Text(c.sum.sym, style = T.mono, color = C.Ink, modifier = Modifier.width(48.dp))
                                    Text(qtyFmt(c.sum.qty), style = T.body, color = C.Ink, modifier = Modifier.weight(1f))
                                    Text(cash(st.hidden, c.sum.valueCents), style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink)
                                }
                                Row {
                                    Text("", modifier = Modifier.width(48.dp))
                                    Text(
                                        (if (c.avgCents > 0) "avg buy ${cashShort(c.avgCents)} · " else "") + "${c.holdings.joinToString(" + ") { h -> if (c.holdings.size > 1) "${h.source} ${qtyFmt(h.qty)}" else h.source }}",
                                        style = T.caption, color = C.InkSubtle, modifier = Modifier.weight(1f), maxLines = 1,
                                    )
                                    if (!st.hidden && c.avgCents > 0) Text(fmtCents(c.plCents, sign = true), style = T.caption, color = if (c.plCents >= 0) C.Positive else C.Negative)
                                }
                            }
                        }
                    }
                }
            }
            item { Text("Tap a coin to update its price · long-press to edit a holding", style = T.caption, color = C.InkSubtle, modifier = Modifier.padding(start = 4.dp)) }
        }
        item { AddManually(st.coins.map { it.sum.sym }, vm) }
    }

    price?.let { c ->
        var v by remember(c.sum.sym) { mutableStateOf(String.format(Locale.US, "%.2f", c.priceCents / 100.0)) }
        val d = v.replace(',', '.').toDoubleOrNull()
        FormSheet("${c.sum.sym} price", { price = null }, confirm = "Update price", confirmColor = C.Grow, canConfirm = d != null && d > 0, onConfirm = {
            vm.setPrice(c.sum.sym, (d!! * 100).roundToLong()); price = null
        }) {
            LifeField(v, { v = it }, "Price per coin, €", keyboard = KeyboardType.Decimal)
            Text("Holding ${qtyFmt(c.sum.qty)} ${c.sum.sym}", style = T.caption, color = C.InkSubtle)
        }
    }
    edit?.let { h ->
        var qty by remember(h.id) { mutableStateOf(qtyFmt(h.qty)) }
        var avg by remember(h.id) { mutableStateOf(String.format(Locale.US, "%.2f", h.avgBuyCents / 100.0)) }
        var src by remember(h.id) { mutableStateOf(h.source) }
        val q = qty.replace(',', '.').toDoubleOrNull(); val a = avg.replace(',', '.').toDoubleOrNull()
        FormSheet("${h.sym} holding", { edit = null }, confirmColor = C.Grow, canConfirm = q != null && q > 0 && a != null,
            onConfirm = { vm.updateHolding(h.copy(qty = q!!, avgBuyCents = (a!! * 100).roundToLong(), source = src.ifBlank { "Manual" })); edit = null },
            onDelete = { vm.delete(h); edit = null }) {
            LifeField(qty, { qty = it }, "Quantity", keyboard = KeyboardType.Decimal)
            LifeField(avg, { avg = it }, "Average buy price, €", keyboard = KeyboardType.Decimal)
            LifeField(src, { src = it }, "Where", placeholder = "Ledger wallet")
        }
    }
}

@Composable
private fun AddManually(existing: List<String>, vm: CryptoVM) {
    var sym by remember { mutableStateOf(existing.firstOrNull() ?: "BTC") }
    var custom by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var avg by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var where by remember { mutableStateOf("") }
    val q = qty.replace(',', '.').toDoubleOrNull()
    val a = avg.replace(',', '.').toDoubleOrNull()
    val p = price.replace(',', '.').toDoubleOrNull()
    val symbol = custom.trim().ifEmpty { sym }
    LifeCard(Modifier.fillMaxWidth(), gap = 10.dp) {
        Text("ADD MANUALLY", style = T.label, color = C.InkSubtle)
        ChipPicker((existing + listOf("BTC", "ETH", "SOL", "USDC")).distinct(), if (custom.isBlank()) sym else "", C.Grow) { sym = it; custom = "" }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeField(custom, { custom = it.uppercase(Locale.ROOT).take(8) }, "Other coin", Modifier.weight(1f), placeholder = "ADA")
            LifeField(qty, { qty = it }, "Quantity", Modifier.weight(1f), KeyboardType.Decimal)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeField(avg, { avg = it }, "Avg buy, €", Modifier.weight(1f), KeyboardType.Decimal)
            LifeField(price, { price = it }, "Price now, €", Modifier.weight(1f), KeyboardType.Decimal)
        }
        LifeField(where, { where = it }, "Where", placeholder = "Ledger wallet")
        PillButton("Add $symbol", C.Grow, C.Graphite0, Modifier.fillMaxWidth(), 44.dp, 12.dp, style = T.body.copy(fontWeight = FontWeight.SemiBold),
            enabled = q != null && q > 0 && p != null && p > 0) {
            vm.add(symbol, q!!, ((a ?: p!!) * 100).roundToLong(), (p!! * 100).roundToLong(), where)
            qty = ""; avg = ""; price = ""; custom = ""
        }
        Text("Binance holdings update by themselves; prices for manual coins — tap a coin.", style = T.caption, color = C.InkSubtle)
    }
}
