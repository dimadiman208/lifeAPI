package com.lifeapi.app.ui.screens.grow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Goal
import com.lifeapi.app.data.JournalEntry
import com.lifeapi.app.data.hhmm
import com.lifeapi.app.data.grow.GrowCalc
import com.lifeapi.app.ui.components.HDivider
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LinearBar
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.SectionHeader
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.components.animatedProgress
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow.GoalView
import com.lifeapi.app.vm.grow.GrowthExtra
import com.lifeapi.app.vm.grow.Review
import com.lifeapi.app.vm.grow.ReviewDim
import com.lifeapi.app.vm.grow.Suggestion

@Composable
fun JournalCard(ex: GrowthExtra, onAdd: (String, String) -> Unit, onAddAll: (List<Suggestion>) -> Unit, onDelete: (JournalEntry) -> Unit) {
    var text by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    val today = Days.today()
    LifeCard(Modifier.fillMaxWidth(), radius = 20.dp, gap = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Done journal", style = T.section, color = C.Ink, modifier = Modifier.weight(1f))
            Text(GrowCalc.dayLabel(today, today - 99), style = T.caption, color = C.InkSubtle)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0D0E10)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box {
                if (text.isEmpty()) Text("What did you get done?", style = T.bodyR.copy(fontSize = 15.sp), color = C.InkFaint)
                androidx.compose.foundation.text.BasicTextField(
                    text, { text = it }, textStyle = T.bodyR.copy(fontSize = 15.sp, color = C.Ink), cursorBrush = SolidColor(C.Grow),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { onAdd(text, tag); text = "" } }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ex.tags.isEmpty()) Text("Tags come from projects, goals and skills", style = T.caption, color = C.InkFaint)
                    ex.tags.forEach { t -> Chip(t, tag == t) { tag = if (tag == t) "" else t } }
                }
                PillButton("Add", if (text.isBlank()) C.Graphite3 else C.Grow, C.Graphite0, height = 34.dp, radius = 10.dp, enabled = text.isNotBlank()) {
                    onAdd(text, tag); text = ""
                }
            }
        }
        if (ex.suggestions.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.GrowTint.copy(alpha = 0.6f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MonoLabel("FROM TODAY'S COMPLETED TASKS", C.Grow)
                    Text(ex.suggestions.joinToString(" · ") { it.text }, style = T.small, color = C.InkSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                PillButton("Add all ${ex.suggestions.size}", C.Grow, C.Graphite0, height = 34.dp, radius = 10.dp) { onAddAll(ex.suggestions) }
            }
        }
        ex.journalToday.forEachIndexed { i, j ->
            if (i > 0) HDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).press(onLongClick = { onDelete(j) }) {},
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(hhmm(Days.timeOf(j.ts)), style = T.mono, color = C.InkSubtle, modifier = Modifier.padding(top = 2.dp))
                Text(j.text, style = T.small, color = C.Ink, modifier = Modifier.weight(1f))
                if (j.tag.isNotBlank()) Text(j.tag, style = T.tiny, color = C.Grow, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(C.GrowTint).padding(horizontal = 6.dp, vertical = 2.dp).widthIn(max = IntrinsicWidthCap))
            }
        }
        if (ex.journalToday.isEmpty() && ex.suggestions.isEmpty()) Text("Nothing yet today. Small wins count.", style = T.caption, color = C.InkSubtle)
        if (ex.journalPast.isNotEmpty()) {
            Text(
                ex.journalPast.joinToString("  ·  ") { "${it.label} · ${it.entries.size}" },
                style = T.caption, color = C.InkSubtle, maxLines = 2,
            )
        }
    }
}

private val IntrinsicWidthCap = 110.dp

@Composable
fun GoalsCard(ex: GrowthExtra, tab: Int, onTab: (Int) -> Unit, onAdd: () -> Unit, onEdit: (Goal) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val total = ex.goals30.size + ex.goals90.size
        SectionHeader("Goals", if (total == 0) "+ Add" else "$total active · + Add", onTrailing = onAdd)
        fun label(l: List<GoalView>, d: Int): String {
            val left = l.mapNotNull { it.daysLeft }.minOrNull()
            return "$d days" + if (left != null) " · $left left" else ""
        }
        Segmented(listOf(label(ex.goals30, 30), label(ex.goals90, 90)), tab, selBg = C.GrowTint, selFg = C.Grow, onSelect = onTab)
        val list = if (tab == 0) ex.goals30 else ex.goals90
        if (list.isEmpty()) {
            LifeCard(Modifier.fillMaxWidth().press(onClick = onAdd), radius = 18.dp) {
                Text("No ${if (tab == 0) "30" else "90"}-day goals", style = T.body, color = C.Ink)
                Text("Link a goal to a habit, a lift, a project or learning hours and it tracks itself.", style = T.caption, color = C.InkMuted)
            }
        }
        if (list.isNotEmpty()) LifeCard(Modifier.fillMaxWidth(), radius = 20.dp, gap = 0.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            list.forEachIndexed { i, g ->
                if (i > 0) HDivider()
                Column(Modifier.fillMaxWidth().press { onEdit(g.g) }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text(g.g.name, style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink, modifier = Modifier.weight(1f))
                        Text(g.value, style = T.small, color = C.InkMuted)
                    }
                    LinearBar(g.progress, if (g.onTrack == false) Color(0xFF9C84C9) else C.Grow, GrowTrack)
                    Text(g.note, style = T.caption, color = when (g.onTrack) { true -> C.Positive; false -> C.Fuel; null -> C.InkSubtle })
                }
            }
        }
    }
}

@Composable
fun SkillsCard(ex: GrowthExtra) {
    if (ex.skills.isEmpty()) return
    LifeCard(Modifier.fillMaxWidth(), radius = 20.dp, gap = 10.dp) {
        MonoLabel("SKILLS · TIME INVESTED")
        val max = ex.skills.maxOf { it.minutes }.coerceAtLeast(1)
        ex.skills.forEach { s ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = T.body, color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.meta, style = T.caption, color = if (s.isNew) C.Grow else C.InkSubtle, modifier = Modifier.padding(end = 10.dp))
                    Text(GrowCalc.hours(s.minutes), style = T.smallB, color = C.Ink)
                }
                LinearBar(s.minutes.toFloat() / max, GrowDim, GrowTrack, height = 4.dp)
            }
        }
    }
}

@Composable
fun ReviewCard(week: Review, month: Review) {
    var tab by remember { mutableIntStateOf(0) }
    val r = if (tab == 0) week else month
    LifeCard(Modifier.fillMaxWidth(), radius = 20.dp, gap = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.title, style = T.section, color = C.Ink)
                Text(r.range, style = T.caption, color = C.InkSubtle)
            }
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Graphite2).padding(3.dp)) {
                listOf("Week", "Month").forEachIndexed { i, l ->
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(if (tab == i) C.Graphite3 else Color.Transparent).press { tab = i }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text(l, style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = if (tab == i) C.Ink else C.InkSubtle) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dim("CONSISTENCY", r.consistency, Modifier.weight(1f))
            Dim("EFFORT", r.effort, Modifier.weight(1f))
            Dim("OUTPUT", r.output, Modifier.weight(1f))
        }
        MonoLabel("LAST 5 WEEKS · OUTPUT vs EFFORT")
        EffortOutputChart(r.weeks)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF3F3557))); Text("Effort (h)", style = T.caption, color = C.InkSubtle)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(14.dp).height(2.dp).background(C.Grow)); Text("Output (tasks)", style = T.caption, color = C.InkSubtle)
            }
        }
        if (r.highlights.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MonoLabel("HIGHLIGHTS")
                r.highlights.forEach { Text("• $it", style = T.small, color = C.InkSoft) }
            }
        }
    }
}

@Composable
private fun Dim(label: String, d: ReviewDim, modifier: Modifier) {
    DimTile(
        label, d.value, if (d.delta == "—") d.sub else "${d.delta} · ${d.sub}", modifier,
        subColor = when (d.positive) { true -> C.Positive; false -> C.Negative; null -> C.InkSubtle },
    )
}

@Composable
fun EffortOutputChart(weeks: List<Triple<String, Float, Float>>) {
    val p = animatedProgress(1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val n = weeks.size
            if (n == 0) return@Canvas
            val maxE = weeks.maxOf { it.second }.coerceAtLeast(1f)
            val maxO = weeks.maxOf { it.third }.coerceAtLeast(1f)
            val slot = size.width / n
            val bw = slot * 0.46f
            weeks.forEachIndexed { i, w ->
                val h = size.height * 0.9f * (w.second / maxE) * p
                drawRoundRect(
                    if (i == n - 1) Color(0xFF5A4A7D) else Color(0xFF3F3557),
                    Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h.coerceAtLeast(2f)), CornerRadius(5.dp.toPx()),
                )
            }
            val path = Path()
            weeks.forEachIndexed { i, w ->
                val x = i * slot + slot / 2
                val y = size.height - size.height * 0.9f * (w.third / maxO) * p
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, C.Grow, style = Stroke(2.dp.toPx()))
            weeks.forEachIndexed { i, w ->
                val x = i * slot + slot / 2
                val y = size.height - size.height * 0.9f * (w.third / maxO) * p
                drawCircle(C.Graphite1, 4.dp.toPx(), Offset(x, y)); drawCircle(C.Grow, 3.dp.toPx(), Offset(x, y))
            }
        }
        Row { weeks.forEach { Text(it.first, style = T.tiny.copy(fontSize = 10.sp, fontWeight = FontWeight.Normal), color = C.InkSubtle, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
    }
}
