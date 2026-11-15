package com.lifeapi.app.ui.screens.body

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.ui.components.ProgressEasing
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.body.Analytics
import com.lifeapi.app.vm.body.BodyAnalyticsVM
import com.lifeapi.app.vm.body.BodyMetric
import com.lifeapi.app.vm.body.BodyPeriod
import com.lifeapi.app.vm.body.ChartBar
import com.lifeapi.app.vm.body.WeightSeries

private val CHART_H = 112.dp
private val TrackTeal = Color(0xFF1C2524)

/**
 * The one analytics card at the top of Body → Health: metric switch, period switch, headline vs goal / previous
 * period, and a chart that fits the metric. Fixed height, so switching never makes the list jump.
 */
@Composable
fun BodyAnalyticsCard(vm: BodyAnalyticsVM = viewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val a = st?.takeIf { it.metric == metric && it.period == period }
    AnalyticsFrame(
        chips = BodyMetric.entries.map { it.label },
        chipWeights = BodyMetric.entries.map { if (it == BodyMetric.SUPPS) 1.35f else 1f },
        selected = metric.ordinal, onChip = { vm.setMetric(BodyMetric.entries[it]) },
        period = period, onPeriod = vm::setPeriod,
        value = a?.value, unit = a?.unit, sub = a?.sub, delta = a?.delta, deltaGood = a?.deltaGood,
        hue = C.Health, top = Color(0xFF111A19),
        chartKey = a?.let { "${it.metric}-${it.period}" },
    ) {
        val x = a
        when {
            x == null -> Unit
            x.empty -> Box(Modifier.fillMaxWidth().height(CHART_H), contentAlignment = Alignment.Center) {
                Text(emptyText(x.metric), style = T.caption, color = C.InkFaint)
            }
            x.weight != null -> WeightChart(x.weight, x.goal, x.goalLabel)
            else -> Bars(x.bars, x.goal, x.goalLabel, "${x.metric}-${x.period}", C.Health)
        }
    }
}

/**
 * Shared analytics card scaffold (Body → Health and Body → Nutrition): metric chips, period switch, headline,
 * sub line, delta vs previous period and a fixed-height chart slot. Fixed height, so switching never makes the list jump.
 */
@Composable
fun AnalyticsFrame(
    chips: List<String>,
    chipWeights: List<Float>,
    selected: Int,
    onChip: (Int) -> Unit,
    period: BodyPeriod,
    onPeriod: (BodyPeriod) -> Unit,
    value: String?,
    unit: String?,
    sub: String?,
    delta: String?,
    deltaGood: Boolean?,
    hue: Color,
    top: Color,
    chartKey: Any?,
    chart: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().height(282.dp).clip(shape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, C.Graphite1)))
            .border(1.dp, C.Hairline, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier.weight(chipWeights.getOrElse(i) { 1f }).height(32.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (on) hue else C.Graphite2)
                        .press(haptic = true) { onChip(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = if (on) C.Graphite0 else C.InkMuted, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value ?: "—", style = T.headline.copy(fontSize = 26.sp, lineHeight = 30.sp), color = C.Ink, maxLines = 1)
                    if (!unit.isNullOrEmpty()) Text(" $unit", style = T.small, color = C.InkMuted, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            PeriodSwitch(period, onPeriod)
        }
        Column(Modifier.height(32.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(sub ?: "", style = T.caption, color = C.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                delta ?: "No earlier data to compare", style = T.caption.copy(fontWeight = FontWeight.SemiBold),
                color = when (deltaGood) { true -> C.Gain; false -> C.Fuel; null -> C.InkFaint }, maxLines = 1,
            )
        }
        AnimatedContent(
            targetState = chartKey, label = "chart",
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
        ) { _ ->
            Box(Modifier.fillMaxWidth().height(CHART_H)) { chart() }
        }
    }
}

private fun emptyText(m: BodyMetric) = when (m) {
    BodyMetric.SUPPS -> "Add a supplement below to see your plan here"
    BodyMetric.WATER -> "Log water below to see your days here"
    BodyMetric.SLEEP -> "Log a night below to see your sleep here"
    BodyMetric.WEIGHT -> "Log a weigh-in below to see your trend here"
}

@Composable
private fun PeriodSwitch(p: BodyPeriod, onPick: (BodyPeriod) -> Unit) {
    Row(
        Modifier.height(30.dp).clip(RoundedCornerShape(10.dp)).background(C.Graphite2).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BodyPeriod.entries.forEach { x ->
            val on = x == p
            Box(
                Modifier.width(34.dp).height(24.dp).clip(RoundedCornerShape(8.dp)).background(if (on) C.Graphite3 else Color.Transparent).press { onPick(x) },
                contentAlignment = Alignment.Center,
            ) { Text(x.label, style = T.tiny.copy(fontWeight = FontWeight.SemiBold), color = if (on) C.Ink else C.InkSubtle) }
        }
    }
}

@Composable
private fun grow(key: Any): Float {
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { anim.animateTo(1f, tween(600, easing = ProgressEasing)) }
    return anim.value
}

/** Bars (+ plan tracks for supplements) with an optional dashed goal line and sparse labels. */
@Composable
fun Bars(bars: List<ChartBar>, goal: Float?, goalLabel: String?, key: Any, hue: Color) {
    val k = grow(key)
    val tm = rememberTextMeasurer()
    val labelStyle = T.tiny.copy(fontSize = 10.sp, fontWeight = FontWeight.Normal, color = C.InkSubtle)
    val goalStyle = T.tiny.copy(fontSize = 10.sp, color = C.InkMuted)
    Canvas(Modifier.fillMaxWidth().height(CHART_H)) {
        if (bars.isEmpty()) return@Canvas
        val labelH = 16.dp.toPx()
        val h = size.height - labelH
        val max = (listOfNotNull(goal) + bars.mapNotNull { it.plan ?: it.value }).maxOrNull()?.takeIf { it > 0 }?.times(1.12f) ?: 1f
        val n = bars.size
        val gap = if (n > 20) 2.dp.toPx() else 6.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        val r = CornerRadius(minOf(bw / 2, 5.dp.toPx()))
        bars.forEachIndexed { i, b ->
            val x = i * (bw + gap)
            b.plan?.let { pl ->
                if (pl > 0) {
                    val ph = h * pl / max
                    drawRoundRect(if (b.future) C.Track else TrackTeal, Offset(x, h - ph), Size(bw, ph), r)
                }
            }
            val v = b.value
            if (v != null && v > 0) {
                val vh = h * v / max * k
                val reached = goal?.let { v >= it } ?: (b.plan != null && v >= b.plan)
                val col = when {
                    b.current -> hue
                    reached -> hue.copy(alpha = 0.8f)
                    else -> hue.copy(alpha = 0.45f)
                }
                drawRoundRect(col, Offset(x, h - vh), Size(bw, vh), r)
            } else if (b.plan == null && !b.future) {
                drawRoundRect(C.Track, Offset(x, h - 3.dp.toPx()), Size(bw, 3.dp.toPx()), CornerRadius(1.5f.dp.toPx()))
            }
            if (b.label.isNotEmpty()) {
                val lay = tm.measure(b.label, if (b.current) labelStyle.copy(color = C.Ink) else labelStyle)
                val lx = (x + bw / 2 - lay.size.width / 2).coerceIn(0f, size.width - lay.size.width)
                drawText(lay, topLeft = Offset(lx, h + 3.dp.toPx()))
            }
        }
        goal?.let { g ->
            val y = h - h * g / max
            drawLine(C.InkSubtle.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            goalLabel?.let { goalTag(tm, it, goalStyle, y) }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.goalTag(tm: TextMeasurer, text: String, style: androidx.compose.ui.text.TextStyle, y: Float) {
    val lay = tm.measure(text, style)
    val pad = 4.dp.toPx()
    val w = lay.size.width + pad * 2; val hh = lay.size.height.toFloat()
    val top = (y - hh - 2.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(C.Graphite1, Offset(size.width - w, top), Size(w, hh), CornerRadius(4.dp.toPx()))
    drawText(lay, topLeft = Offset(size.width - w + pad, top))
}

/** Daily weigh-ins as dots, 7-day average as a line, goal dashed. */
@Composable
private fun WeightChart(s: WeightSeries, goal: Float?, goalLabel: String?) {
    val k = grow(s)
    val tm = rememberTextMeasurer()
    val labelStyle = T.tiny.copy(fontSize = 10.sp, fontWeight = FontWeight.Normal, color = C.InkSubtle)
    val goalStyle = T.tiny.copy(fontSize = 10.sp, color = C.InkMuted)
    Canvas(Modifier.fillMaxWidth().height(CHART_H)) {
        val labelH = 16.dp.toPx()
        val top = 6.dp.toPx()
        val h = size.height - labelH - top
        val ys = s.dots.map { it.second } + s.avg.map { it.second }
        if (ys.isEmpty()) {
            val lay = tm.measure("No weigh-ins in this period", labelStyle)
            drawText(lay, topLeft = Offset(size.width / 2 - lay.size.width / 2, size.height / 2 - lay.size.height / 2))
            return@Canvas
        }
        // Include the goal only when it's near, otherwise the trend flattens into a line.
        var lo = ys.min(); var hi = ys.max()
        goal?.let { g -> if (g in (lo - 3f)..(hi + 3f)) { lo = minOf(lo, g); hi = maxOf(hi, g) } }
        val padY = maxOf(0.4f, (hi - lo) * 0.15f)
        lo -= padY; hi += padY
        fun px(x: Int) = size.width * x / s.span
        fun py(v: Float) = top + h * (1f - (v - lo) / (hi - lo))
        // grid
        for (i in 0..2) {
            val y = top + h * i / 2
            drawLine(C.Divider, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        goal?.let { g ->
            if (g in lo..hi) {
                val y = py(g)
                drawLine(C.Gain.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                goalLabel?.let { goalTag(tm, it, goalStyle, y) }
            }
        }
        val dotR = if (s.span > 40) 2.dp.toPx() else 3.dp.toPx()
        s.dots.forEach { (x, v) -> drawCircle(C.Health.copy(alpha = 0.35f * k), dotR, Offset(px(x), py(v))) }
        if (s.avg.size >= 2) {
            val path = Path()
            val cut = (s.avg.size * k).toInt().coerceAtLeast(2).coerceAtMost(s.avg.size)
            s.avg.take(cut).forEachIndexed { i, (x, v) -> if (i == 0) path.moveTo(px(x), py(v)) else path.lineTo(px(x), py(v)) }
            drawPath(path, C.Health, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        s.avg.lastOrNull()?.let { (x, v) -> drawCircle(C.Health, 4.dp.toPx(), Offset(px(x), py(v))) }
        // labels
        s.axis.forEach { (x, t) ->
            val lay = tm.measure(t, labelStyle)
            val lx = (px(x) - lay.size.width / 2).coerceIn(0f, size.width - lay.size.width)
            drawText(lay, topLeft = Offset(lx, size.height - labelH + 3.dp.toPx()))
        }
    }
}

@Suppress("unused")
private fun barsHaveData(b: List<ChartBar>) = b.any { (it.value ?: 0f) > 0f }

/** Body → Nutrition's top chart — same scaffold as Health, Fuel hue. */
@Composable
fun NutritionAnalyticsCard(vm: com.lifeapi.app.vm.body.NutritionAnalyticsVM = viewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val a = st?.takeIf { it.metric == metric && it.period == period }
    AnalyticsFrame(
        chips = com.lifeapi.app.vm.body.NutMetric.entries.map { it.label },
        chipWeights = com.lifeapi.app.vm.body.NutMetric.entries.map { if (it == com.lifeapi.app.vm.body.NutMetric.KCAL) 1.2f else 1f },
        selected = metric.ordinal, onChip = { vm.setMetric(com.lifeapi.app.vm.body.NutMetric.entries[it]) },
        period = period, onPeriod = vm::setPeriod,
        value = a?.value, unit = a?.unit, sub = a?.sub, delta = a?.delta, deltaGood = a?.deltaGood,
        hue = C.Fuel, top = Color(0xFF19160F),
        chartKey = a?.let { "${it.metric}-${it.period}" },
    ) {
        val x = a
        when {
            x == null -> Unit
            x.empty -> Box(Modifier.fillMaxWidth().height(CHART_H), contentAlignment = Alignment.Center) {
                Text("Log food in FatSecret to see your days here", style = T.caption, color = C.InkFaint)
            }
            else -> Bars(x.bars, x.goal, x.goalLabel, "${x.metric}-${x.period}", C.Fuel)
        }
    }
}
