package com.lifeapi.app.ui.screens.today

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.InsightFeedback
import com.lifeapi.app.ui.LocalActions
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.BarChart
import com.lifeapi.app.ui.components.BarDatum
import com.lifeapi.app.ui.components.Dot
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.components.StatBox
import com.lifeapi.app.ui.components.ValueUnit
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.BaseVM
import com.lifeapi.app.vm.today.DoNext
import com.lifeapi.app.vm.today.InsightEngine
import com.lifeapi.app.vm.today.NextAction
import com.lifeapi.app.vm.today.Pattern
import com.lifeapi.app.vm.today.PatternChart
import com.lifeapi.app.vm.today.Report
import com.lifeapi.app.vm.today.aiOn
import com.lifeapi.app.vm.today.snapshotFlow
import com.lifeapi.app.vm.today.strength
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsVM(app: Application) : BaseVM(app) {
    val snap = today.flatMapLatest { snapshotFlow(repo, it) }.state()
    val loading = MutableStateFlow(true)

    init { refresh(700) }

    fun refresh(ms: Long = 1100) {
        viewModelScope.launch { loading.value = true; delay(ms); loading.value = false }
    }

    fun feedback(key: String, useful: Boolean) = act(if (useful) "Thanks — noted" else "Hidden. You won't see this pattern again") {
        growDao.upsertFeedback(InsightFeedback(key, useful, Days.now()))
        if (useful) null else ({ growDao.upsertFeedback(InsightFeedback(key, true, Days.now())) })
    }
}

@Composable
fun InsightsScreen(onBack: () -> Unit, vm: InsightsVM = viewModel()) {
    val s by vm.snap.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val actions = LocalActions.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val snap = s

    DetailList {
        item {
            DetailTop("Intelligence", onBack) {
                Row(
                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF151A0C)).border(1.dp, Color(0xFF2B3318), RoundedCornerShape(18.dp))
                        .press(haptic = true) { vm.refresh() }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(TIc.Refresh, null, tint = C.Volt, modifier = Modifier.size(14.dp))
                    Text(if (loading) "Working…" else "Refresh", style = T.smallB, color = C.Volt)
                }
            }
        }
        item { Segmented(listOf("Daily", "Weekly", "Monthly"), tab) { tab = it; offset = 0 } }
        if (snap == null) return@DetailList
        if (!snap.p.aiOn()) {
            item {
                GroupCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MonoLabel("Intelligence is off")
                        Text("Choose what it may read", style = T.section.copy(fontSize = 16.sp), color = C.Ink)
                        Text("Insights are computed on this phone from the categories you allow. Nothing is sent anywhere, and everything else in LifeAPI works without it.", style = T.small, color = C.InkMuted)
                        PillButton("Set up privacy", C.Volt, C.Graphite0, Modifier.fillMaxWidth(), height = 44.dp, radius = 14.dp) { nav.navigate("settings/privacy") }
                    }
                }
            }
            return@DetailList
        }
        when (tab) {
            0 -> {
                item {
                    AnimatedContent(loading, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "sum") { l ->
                        if (l) LoadingCard("Analysing 30 days…")
                        else {
                            val sum = InsightEngine.summary(snap)
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF12140F)).border(1.dp, Color(0xFF262D18), RoundedCornerShape(20.dp)).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MonoLabel(sum.title, C.Volt)
                                Text(sum.text, style = T.bodyR.copy(fontSize = 15.sp, lineHeight = 22.sp), color = C.Ink)
                                Text(sum.uses, style = T.caption, color = C.InkSubtle)
                            }
                        }
                    }
                }
                val next = InsightEngine.doNext(snap)
                if (next.isNotEmpty()) {
                    item { Cap("Do next") }
                    item {
                        GroupCard {
                            next.forEachIndexed { i, d -> DoNextRow(d, i == 0) { runAction(d.action, nav, actions) } }
                        }
                    }
                }
                val pats = InsightEngine.patterns(snap)
                item { Cap("Patterns") }
                if (pats.isEmpty()) item {
                    GroupCard { Text("No clear patterns yet. They need about two weeks of sleep, food, training or spending logs — and only show up when the numbers back them.", style = T.small, color = C.InkMuted, modifier = Modifier.padding(16.dp)) }
                }
                pats.forEach { p -> item(key = p.key) { PatternCard(p) { nav.navigate("insights/pattern/${android.net.Uri.encode(p.key)}") } } }
            }
            else -> {
                val kind = tab - 1
                item {
                    AnimatedContent(loading to offset, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "rep") { (l, off) ->
                        if (l) LoadingCard(if (kind == 0) "Reading 7 days across 5 modules…" else "Reading the month across 5 modules…")
                        else ReportCard(InsightEngine.report(snap, kind, off), kind)
                    }
                }
                item { Cap("History") }
                item {
                    GroupCard {
                        (0..(if (kind == 0) 7 else 2)).forEachIndexed { i, off ->
                            val r = InsightEngine.report(snap, kind, off)
                            Row(
                                Modifier.fillMaxWidth().background(if (off == offset) C.Graphite2 else Color.Transparent).press { offset = off }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(C.Track), contentAlignment = Alignment.Center) {
                                    Text(r.tag, style = T.label.copy(fontSize = 10.sp, letterSpacing = 0.sp), color = C.InkMuted)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${r.title}${if (off == 0) " · so far" else ""}", style = T.body, color = C.Ink)
                                    Text(
                                        if (r.activeDays == 0) "Nothing logged" else listOfNotNull("Consistency ${r.consistency}%", if (r.workouts > 0) "${r.workouts} workouts" else null, if (r.spentCents > 0) "${com.lifeapi.app.data.fmtCents(r.spentCents).removeSuffix(".00")} spent" else null).joinToString(" · "),
                                        style = T.caption, color = C.InkSubtle,
                                    )
                                }
                            }
                            if (i < (if (kind == 0) 7 else 2)) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1A1B1F)))
                        }
                    }
                }
            }
        }
        item {
            Text("Computed on this phone from your own logs. Nothing is sent to any server or AI provider.", style = T.caption, color = C.InkFaint, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

fun runAction(a: NextAction, nav: androidx.navigation.NavHostController, actions: com.lifeapi.app.ui.AppActions) {
    when (a) {
        is NextAction.Meal -> actions.meal(a.items)
        is NextAction.Route -> nav.navigate(a.route)
        is NextAction.Tab -> {
            if (a.seg != null) { if (a.route == "body") actions.bodySegment(a.seg) else if (a.route == "grow") actions.growSegment(a.seg) }
            actions.tab(a.route)
        }
    }
}

@Composable
private fun LoadingCard(label: String) {
    GroupCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MonoLabel(label, C.Volt)
            Skeleton(0.92f); Skeleton(0.8f); Skeleton(0.64f)
        }
    }
}

@Composable
private fun DoNextRow(d: DoNext, first: Boolean, onClick: () -> Unit) {
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1A1B1F)))
        Row(
            Modifier.fillMaxWidth().press(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Dot(C.hue(d.hue))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(d.title, style = T.body, color = C.Ink)
                Text(d.sub, style = T.caption, color = C.InkSubtle)
            }
            Text(d.cta, style = T.smallB, color = C.hue(d.hue))
        }
    }
}

@Composable
private fun PatternCard(p: Pattern, onClick: () -> Unit) {
    GroupCard(Modifier.press(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.module, style = T.caption, color = C.hue(p.hue))
            Text(p.claim, style = T.bodyR.copy(lineHeight = 20.sp), color = C.Ink)
            Text("See the numbers →", style = T.caption, color = C.InkSubtle)
        }
    }
}

@Composable
private fun ReportCard(r: Report, kind: Int) {
    GroupCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MonoLabel("${if (kind == 0) "Weekly review" else "Monthly report"} · ${r.tag}", C.Volt)
                Text(r.range, style = T.caption, color = C.InkSubtle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Consistency", Modifier.weight(1f)) {
                    ValueUnit("${r.consistency}", "%")
                    Text(r.consistencyDelta?.let { "${if (it >= 0) "+" else ""}$it pts" } ?: "${r.activeDays} of ${r.totalDays} days", style = T.caption, color = deltaColor(r.consistencyDelta))
                }
                StatBox("Day Score", Modifier.weight(1f)) {
                    ValueUnit(r.avgScore?.toString() ?: "—", "avg")
                    Text(r.scoreDelta?.let { "${if (it >= 0) "+" else ""}$it vs prev" } ?: "—", style = T.caption, color = deltaColor(r.scoreDelta))
                }
                StatBox("Output", Modifier.weight(1f)) {
                    ValueUnit("${r.tasksDone + r.workouts}", null)
                    Text("${r.tasksDone} tasks · ${r.workouts} wkts", style = T.caption, color = C.InkSubtle, maxLines = 1)
                }
            }
            if (r.scoreSeries.isNotEmpty()) {
                BarChart(
                    r.scoreSeries.mapIndexed { i, v -> BarDatum(r.seriesLabels[i], (v ?: 0).toFloat(), highlight = v != null && v > 0, ghost = v == null) },
                    C.Volt, C.Bar, height = 72.dp, maxValue = 100f,
                )
            }
            Text(r.text, style = T.bodyR.copy(lineHeight = 20.sp), color = C.InkSoft)
            if (r.highlights.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MonoLabel("Highlights")
                    r.highlights.forEach { Text("• $it", style = T.small, color = C.Ink) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                r.sleepAvgMin?.let { Text("Sleep ${it / 60}h ${String.format(Locale.US, "%02d", it % 60)}m", style = T.caption, color = C.InkMuted) }
                if (r.learnMin > 0) Text("Learning ${r.learnMin} min", style = T.caption, color = C.InkMuted)
                if (r.volumeKg > 0) Text(com.lifeapi.app.vm.fmtTonnes(r.volumeKg), style = T.caption, color = C.InkMuted)
            }
        }
    }
}

private fun deltaColor(d: Int?) = when { d == null -> C.InkSubtle; d >= 0 -> C.Positive; else -> C.Negative }

// ------------------------------------------------------------ detail

@Composable
fun PatternScreen(key: String, onBack: () -> Unit, vm: InsightsVM = viewModel()) {
    val s by vm.snap.collectAsStateWithLifecycle()
    val snap = s
    val p = snap?.let { InsightEngine.pattern(it, key) }
    DetailList(gap = 16.dp) {
        item { DetailTop("Why this insight", onBack) }
        if (snap == null) return@DetailList
        if (p == null) {
            item { Text("This pattern no longer holds with your latest data.", style = T.bodyR, color = C.InkMuted) }
            return@DetailList
        }
        item { Text(p.claim, style = T.title.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = C.Ink) }
        item {
            GroupCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonoLabel(p.chartTitle)
                    when (val ch = p.chart) {
                        is PatternChart.Scatter -> ScatterChart(ch, C.hue(p.hue))
                        is PatternChart.Bars -> BarChart(ch.values.mapIndexed { i, v -> BarDatum(ch.labels[i], v, highlight = ch.hi == null || i == ch.hi) }, C.hue(p.hue), C.Bar, height = 120.dp)
                    }
                }
            }
        }
        item {
            GroupCard {
                Text("SUPPORTING DATA", style = T.label, color = C.InkSubtle, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp))
                p.rows.forEach { (a, b) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(a, style = T.small, color = C.InkMuted, modifier = Modifier.weight(1f))
                        Text(b, style = T.mono, color = C.Ink)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1A1B1F)))
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)) {
                    Text(if (p.r != null) "Strength of link" else "Based on", style = T.small, color = C.InkMuted, modifier = Modifier.weight(1f))
                    Text(
                        if (p.r != null) "${strength(p.r)} · r = ${String.format(Locale.US, "%.2f", p.r)}" else "${p.n} entries",
                        style = T.small, color = C.Fuel,
                    )
                }
            }
        }
        item {
            Text(
                "A pattern, not proof. Computed on this phone from ${p.uses} (${p.n} data points) — nothing was sent anywhere.",
                style = T.caption.copy(lineHeight = 18.sp), color = C.InkSubtle,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.lifeapi.app.ui.components.GhostButton("Useful", Modifier.weight(1f), color = C.Ink) { vm.feedback(p.key, true); onBack() }
                com.lifeapi.app.ui.components.GhostButton("Not for me", Modifier.weight(1f), color = C.InkMuted) { vm.feedback(p.key, false); onBack() }
            }
        }
    }
}

@Composable
private fun ScatterChart(ch: PatternChart.Scatter, hue: Color) {
    val p = com.lifeapi.app.ui.components.animatedProgress(1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val xs = ch.pts.map { it.first }; val ys = ch.pts.map { it.second }
                val x0 = minOf(xs.min(), ch.xThreshold ?: xs.min()); val x1 = maxOf(xs.max(), ch.xThreshold ?: xs.max())
                val y0 = (ys.min() - 5f).coerceAtLeast(0f); val y1 = ys.max() + 5f
                val pad = 8.dp.toPx()
                fun px(x: Float) = pad + (x - x0) / (x1 - x0).coerceAtLeast(0.001f) * (size.width - 2 * pad)
                fun py(y: Float) = size.height - pad - (y - y0) / (y1 - y0).coerceAtLeast(0.001f) * (size.height - 2 * pad)
                drawLine(C.Track, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                ch.xThreshold?.let { t ->
                    drawLine(C.Volt.copy(alpha = 0.6f), Offset(px(t), 0f), Offset(px(t), size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
                }
                ch.pts.forEachIndexed { i, (x, y) ->
                    val hi = i in ch.hi
                    drawCircle(if (hi) hue else hue.copy(alpha = 0.4f), (if (hi) 5.dp else 4.dp).toPx() * p, Offset(px(x), py(y)))
                }
            }
            if (ch.thresholdLabel != null) Text(ch.thresholdLabel, style = T.label.copy(fontSize = 10.sp), color = C.Volt, modifier = Modifier.align(Alignment.TopEnd))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(ch.xMin, style = T.label.copy(fontSize = 10.sp), color = C.InkSubtle, modifier = Modifier.weight(1f))
            Text("↑ ${ch.yLabel}", style = T.label.copy(fontSize = 10.sp), color = C.InkSubtle, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(ch.xMax, style = T.label.copy(fontSize = 10.sp), color = C.InkSubtle)
        }
    }
}

