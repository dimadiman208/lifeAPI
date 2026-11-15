package com.lifeapi.app.ui.screens.body

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.WaterLog
import com.lifeapi.app.data.hhmm
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.LinearBar
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RollingText
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.dashedBorder
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.body.SupplementVM
import com.lifeapi.app.ui.screens.body.supps.DoseRow
import com.lifeapi.app.ui.screens.body.supps.HealthMid
import androidx.compose.foundation.layout.widthIn
import java.util.Locale

// ===================================================================== supplement detail

@Composable
fun SupplementDetailScreen(id: Long, vm: SupplementVM = viewModel()) {
    LaunchedEffect(id) { vm.bind(id) }
    val st by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val s = st?.takeIf { it.s.id == id }

    ScreenList(glow = C.Health, bottomExtra = false) {
        item { DetailHeader(s?.s?.name ?: "Supplement", trailing = if (s != null) "Edit" else null, onTrailing = { editing = true }) }
        if (s == null) return@ScreenList
        item {
            Text(
                "${s.schedule} · ${s.perWeek} doses / week" + listOfNotNull(if (s.s.withFood) "with food" else null, s.s.stock?.let { "$it left" }).joinToString("") { " · $it" },
                style = T.small, color = if ((s.s.stock ?: Int.MAX_VALUE) <= s.s.stockAlert) C.Fuel else C.InkMuted,
                modifier = Modifier.padding(start = 50.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Streak", "${s.streak} d", Modifier.weight(1f))
                MiniStat("Best", "${s.best} d", Modifier.weight(1f))
                MiniStat("30-day", s.pct30?.let { "$it%" } ?: "—", Modifier.weight(1f))
            }
        }
        item {
            LifeCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), gap = 2.dp) {
                MonoLabel("Today · ${s.todaySlots.count { it.taken }}/${s.todaySlots.size}")
                if (s.todaySlots.isEmpty()) Text("Not scheduled today.", style = T.small, color = C.InkMuted, modifier = Modifier.padding(vertical = 10.dp))
                s.todaySlots.forEach { slot -> DoseRow(slot, s.today, s.today, s.now, vm, showName = false) }
            }
        }
        item {
            LifeCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp), gap = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.monthLabel, style = T.section, color = C.Ink, modifier = Modifier.weight(1f))
                    RoundIconButton(Ic.ChevronLeft, "Previous month", size = 32.dp, bg = C.Track) { vm.month(-1) }
                    if (s.canNext) RoundIconButton(Ic.ChevronRight, "Next month", size = 32.dp, bg = C.Track) { vm.month(1) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        Text(it, style = T.tiny, color = C.InkSubtle, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
                s.cal.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        week.forEach { c ->
                            val shape = RoundedCornerShape(8.dp)
                            val (bg, fg) = when (c.kind) {
                                1 -> C.Health to C.Graphite0
                                2 -> C.Ghost to C.Ink
                                3 -> Color.Transparent to C.InkMuted
                                4 -> C.HealthTint to C.Ink
                                5 -> C.Track to C.InkFaint
                                6 -> HealthMid to Color(0xFFD7F3EE)
                                else -> Color.Transparent to Color.Transparent
                            }
                            Box(
                                Modifier.weight(1f).height(32.dp).clip(shape).background(bg)
                                    .then(
                                        when (c.kind) {
                                            3 -> Modifier.dashedBorder(C.Train, 8.dp, 1.5.dp)
                                            4 -> Modifier.border(1.5.dp, C.Health, shape)
                                            else -> Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) { if (c.n > 0) Text(c.n.toString(), style = T.tiny.copy(fontWeight = FontWeight.Medium), color = fg) }
                        }
                        repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(C.Health, "All taken"); LegendDot(HealthMid, "Partial"); LegendDot(C.Ghost, "Skipped"); LegendDot(Color.Transparent, "Missed", dashed = C.Train)
                }
            }
        }
        item {
            if (s.log.isEmpty()) {
                LifeCard(Modifier.fillMaxWidth()) {
                    Text("No history yet", style = T.body, color = C.Ink)
                    Text("Tick doses on the Health tab or above. Skipping on purpose never breaks a streak.", style = T.caption, color = C.InkMuted)
                }
            } else GroupCard {
                s.log.forEachIndexed { i, l ->
                    val color = when (l.tone) { 0 -> C.Health; 1 -> C.Fuel; 2 -> C.InkMuted; 4 -> C.InkSoft; else -> C.Negative }
                    GroupRow(l.day, divider = i > 0) {
                        Text(l.status, style = T.caption, color = color)
                        Text(l.time, style = T.mono, color = C.InkSubtle, modifier = Modifier.widthIn(min = 44.dp), textAlign = TextAlign.End, maxLines = 1)
                    }
                }
            }
        }
    }
    if (editing && s != null) {
        val nav = com.lifeapi.app.ui.LocalNav.current
        SupplementEditor(s.s, s.rules, onDismiss = { editing = false }, onSave = vm::save, onArchive = { vm.archive(it); nav.popBackStack() })
    }
}
