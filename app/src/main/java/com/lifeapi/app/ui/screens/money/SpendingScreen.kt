package com.lifeapi.app.ui.screens.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lifeapi.app.ui.components.press
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.animatedProgress
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.money.CatRow
import com.lifeapi.app.vm.money.SpendingVM

/** Shades of Money blue for the stacked bar; Eating out keeps the warm accent like the design. */
private val SHADES = listOf(Color(0xFF93A9FF), Color(0xFF7C8FD9), Color(0xFF6474B3), Color(0xFF4F5A8A), Color(0xFF434C70), Color(0xFF384063))
private val OTHER = Color(0xFF2C3247)
private const val SMALL = "\u0000small"

private data class Seg(val row: CatRow, val color: Color, val label: String, val count: String)

@Composable
fun SpendingScreen(vm: SpendingVM = viewModel()) {
    val nav = LocalNav.current
    val s by vm.state.collectAsStateWithLifecycle()
    val st = s ?: return
    var showSmall by remember { mutableStateOf(false) }
    val segs = run {
        val top = if (showSmall) st.rows else st.rows.take(6)
        val rest = if (showSmall) emptyList() else st.rows.drop(6)
        var shade = 0
        top.map { r ->
            val col = if (r.name == "Eating out") C.Train else SHADES[(shade++).coerceAtMost(SHADES.lastIndex)]
            Seg(r, col, r.name, countLabel(r))
        } + if (rest.isEmpty()) emptyList() else listOf(
            Seg(
                CatRow(SMALL, rest.sumOf { it.cents }, rest.sumOf { it.count }, rest.sumOf { it.pct },
                    rest.mapNotNull { it.deltaCents }.takeIf { it.isNotEmpty() }?.sum(), rest.first().prevLabel),
                OTHER, "Smaller categories · ${rest.size}", "${rest.sumOf { it.count }} payments · tap to show",
            ),
        )
    }

    ScreenList(glow = null, bottomExtra = false) {
        item {
            DetailTop("Spending", onBack = { nav.popBackStack() }) {
                DropChip(st.months[st.selected], st.months.mapIndexed { i, m -> m to i }, { vm.offset.value = it }, fg = C.InkMuted)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(cash(st.hidden, st.total), style = T.display.copy(fontSize = 34.sp, lineHeight = 40.sp), color = C.Ink)
                if (st.prevTotal > 0) Text(
                    buildAnnotatedString {
                        append("vs ${cash(st.hidden, st.prevTotal).substringBefore('.')} ${st.prevLabel}")
                        st.deltaPct?.let { d ->
                            append(" · ")
                            withStyle(SpanStyle(color = if (d <= 0) C.Positive else C.Negative)) { append((if (d > 0) "+" else if (d < 0) "−" else "") + "${kotlin.math.abs(d)}%") }
                        }
                    },
                    style = T.small, color = C.InkMuted,
                ) else Text("${st.title} · nothing to compare with yet", style = T.small, color = C.InkSubtle)
            }
        }
        if (st.rows.isEmpty()) {
            item {
                EmptyCard("Spending", C.Money, "No spending in ${st.title}", "Expenses you log or import show up here, ranked by category.", "Back to Money", { nav.popBackStack() })
            }
        } else {
            item { StackedBar(segs) }
            st.rows.firstOrNull { it.name == "Other" }?.let { o ->
                item {
                    com.lifeapi.app.ui.components.LifeCard(
                        Modifier.fillMaxWidth().press { nav.navigate(categoryRoute("Other", st.selected)) },
                        radius = 18.dp, padding = androidx.compose.foundation.layout.PaddingValues(14.dp), gap = 2.dp,
                    ) {
                        Text("${o.count} uncategorised · tap to sort", style = T.body, color = C.Ink)
                        Text("Pick a category per merchant once — it's remembered for future bank syncs.", style = T.caption, color = C.InkSubtle)
                    }
                }
            }
            item {
                ListCard {
                    segs.forEachIndexed { i, sg ->
                        DivRow(i == 0, onClick = {
                            if (sg.row.name == SMALL) showSmall = true else nav.navigate(categoryRoute(sg.row.name, st.selected))
                        }) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(sg.color))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(sg.label, style = T.body, color = C.Ink)
                                Text("${sg.row.pct}% · ${sg.count}", style = T.caption, color = C.InkSubtle)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(cash(st.hidden, sg.row.cents), style = T.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = C.Ink)
                                val d = sg.row.deltaCents
                                if (d != null && !st.hidden) Text(
                                    when {
                                        kotlin.math.abs(d) < 100 -> "= ${sg.row.prevLabel}"
                                        d > 0 -> "+${cashShort(d)}"
                                        else -> cashShort(d)
                                    },
                                    style = T.caption, color = when { kotlin.math.abs(d) < 100 -> C.InkSubtle; d > 0 -> C.Negative; else -> C.Positive },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun countLabel(r: CatRow): String = when (r.name) {
    "Groceries", "Eating out" -> "${r.count} visit${if (r.count == 1) "" else "s"}"
    "Transport" -> "${r.count} ride${if (r.count == 1) "" else "s"}"
    else -> "${r.count} payment${if (r.count == 1) "" else "s"}"
}

@Composable
private fun StackedBar(segs: List<Seg>) {
    val p = animatedProgress(1f)
    val total = segs.sumOf { it.row.cents }.toFloat().coerceAtLeast(1f)
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val gap = 2.dp.toPx()
        val usable = size.width - gap * (segs.size - 1)
        val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width * p, size.height, CornerRadius(size.height / 2))) }
        clipPath(clip) {
            var x = 0f
            segs.forEach { sg ->
                val w = usable * sg.row.cents / total
                drawRect(sg.color, Offset(x, 0f), Size(w, size.height))
                x += w + gap
            }
        }
    }
}
