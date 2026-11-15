package com.lifeapi.app.ui.screens.grow.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.TimeBlock
import com.lifeapi.app.data.grow3.GrowStats
import com.lifeapi.app.data.grow3.TimeMath
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow3.projects.TimelineVM
import kotlinx.coroutines.launch

private const val HOUR_DP = 56

/**
 * Day Timeline across projects. Work hours of active weekly projects show as a light band — a plan, not time spent.
 * Only a block's "actual minutes" count as logged time.
 */
@Composable
fun TimelineScreen(day: Long?, onBack: () -> Unit, vm: TimelineVM = viewModel()) {
    LaunchedEffect(day) { vm.bind(day) }
    val st by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<TimeBlock?>(null) }
    var creating by remember { mutableStateOf<Pair<String, String>?>(null) }

    ScreenList(glow = C.Grow, bottomExtra = false) {
        val s = st
        item(key = "top") { GrowTop("Day timeline", s?.let { dayLong(it.day) }, onBack) }
        if (s == null) return@ScreenList
        item(key = "nav") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RoundIconButton(Ic.ChevronLeft, "Previous day", size = 34.dp, bg = C.Track) { vm.page(-1) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(relDay(s.day, s.today), style = T.smallB, color = C.Ink)
                    Text(
                        "Planned ${GrowStats.fmtMinutes(s.planned)} · logged ${GrowStats.fmtMinutes(s.logged)}", style = T.caption, color = C.InkSubtle,
                    )
                }
                RoundIconButton(Ic.ChevronRight, "Next day", size = 34.dp, bg = C.Track) { vm.page(1) }
            }
        }
        item(key = "add") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("New block", C.Grow, C.Graphite0, Modifier.weight(1f), 44.dp, 14.dp, icon = Ic.Plus) {
                    scope.launch { creating = vm.suggestSlot(s.day, null) }
                }
                if (s.day != s.today) PillButton("Today", C.Graphite2, C.InkSoft, Modifier, 44.dp, 14.dp) { vm.select(s.today) }
            }
        }
        item(key = "timeline") {
            val starts = s.blocks.mapNotNull { TimeMath.parse(it.start) }
            val ends = s.blocks.mapNotNull { TimeMath.parse(it.end) }
            val from = (listOfNotNull(7 * 60, s.workBand?.first, starts.minOrNull()).min() / 60) * 60
            val to = ((listOfNotNull(20 * 60, s.workBand?.second, ends.maxOrNull()).max() + 59) / 60) * 60
            val hours = (to - from) / 60
            val pxPerMin = HOUR_DP / 60f
            LifeCard(Modifier.fillMaxWidth(), radius = 20.dp, gap = 0.dp) {
                Box(Modifier.fillMaxWidth().height((hours * HOUR_DP).dp)) {
                    // work band (plan only)
                    s.workBand?.let { (a, b) ->
                        Box(
                            Modifier.padding(start = 44.dp).fillMaxWidth().offset(y = ((a - from) * pxPerMin).dp).height(((b - a) * pxPerMin).dp)
                                .clip(RoundedCornerShape(8.dp)).background(C.Grow.copy(alpha = 0.05f)),
                        )
                    }
                    // hour grid
                    for (h in 0..hours) {
                        Row(Modifier.offset(y = (h * HOUR_DP).dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(TimeMath.format(from + h * 60), style = T.mono, color = C.InkFaint, modifier = Modifier.width(44.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
                        }
                    }
                    // blocks
                    s.blocks.forEach { b ->
                        val a = TimeMath.parse(b.start) ?: return@forEach
                        val e = TimeMath.parse(b.end) ?: return@forEach
                        val p = s.projects.firstOrNull { it.id == b.projectId }
                        val hue = projectColor(p)
                        val conflict = b.id in s.conflicts
                        Column(
                            Modifier.padding(start = 48.dp, end = 2.dp).fillMaxWidth()
                                .offset(y = ((a - from) * pxPerMin).dp + 1.dp).height(((e - a) * pxPerMin).dp.coerceAtLeast(22.dp) - 2.dp)
                                .clip(RoundedCornerShape(10.dp)).background(hue.copy(alpha = 0.16f))
                                .then(if (conflict) Modifier.border(1.dp, C.Fuel, RoundedCornerShape(10.dp)) else Modifier)
                                .press { edit = b }.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(b.title, style = T.smallB, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(
                                "${b.start}–${b.end}", p?.name,
                                b.actualMinutes?.let { "✓ ${GrowStats.fmtMinutes(it)}" } ?: "not logged",
                                if (conflict) "overlaps" else null,
                            )
                            if (e - a >= 40) Text(meta.joinToString(" · "), style = T.caption, color = if (conflict) C.Fuel else C.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (e - a >= 70 && b.result.isNotBlank()) Text("→ ${b.result}", style = T.caption, color = Kind.Result, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (s.blocks.isEmpty()) item(key = "empty") {
            Text("No blocks. Planned blocks are a guide — only “actual time” you log counts as time spent.", style = T.small, color = C.InkMuted, modifier = Modifier.padding(horizontal = 4.dp))
        }
        if (s.blocks.isNotEmpty()) item(key = "byproject") {
            val by = s.blocks.groupBy { it.projectId }
            LifeCard(Modifier.fillMaxWidth(), radius = 18.dp, gap = 6.dp) {
                MonoLabel("BY PROJECT", C.InkSubtle)
                by.forEach { (pid, list) ->
                    val p = s.projects.firstOrNull { it.id == pid }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(projectColor(p)))
                        Text(p?.name ?: "No project", style = T.body, color = C.Ink, modifier = Modifier.weight(1f))
                        Text(
                            "planned ${GrowStats.fmtMinutes(list.sumOf { TimeMath.duration(it.start, it.end) ?: 0 })} · logged ${GrowStats.fmtMinutes(list.sumOf { it.actualMinutes ?: 0 })}",
                            style = T.caption, color = C.InkSubtle,
                        )
                    }
                }
            }
        }
    }

    val s = st ?: return
    val sheetBlock = edit
    if (sheetBlock != null || creating != null) {
        val b = sheetBlock
        BlockSheet(
            b, b?.day ?: s.day, s.projects, null, creating ?: (b!!.start to b.end),
            conflicts = b?.let { TimeMath.conflicts(it, s.blocks) }.orEmpty(),
            onDismiss = { edit = null; creating = null },
            onDelete = { vm.deleteBlock(it) },
        ) { vm.saveBlock(it) }
    }
}

