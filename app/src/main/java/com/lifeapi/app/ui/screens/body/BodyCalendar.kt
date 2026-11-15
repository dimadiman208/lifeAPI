package com.lifeapi.app.ui.screens.body

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.data.Days
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.ProgressEasing
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import java.time.format.TextStyle
import java.util.Locale

/**
 * One calendar cell for the shared Body date bar. n = 0 is a month-grid pad.
 * [frac] = progress 0..1 (null = nothing planned / no data → no indicator).
 * [skipFrac] / [perfect] are optional extras (supplements: skipped doses, all closed).
 */
data class DateCell(
    val day: Long,
    val n: Int,
    val isToday: Boolean,
    val future: Boolean,
    val selected: Boolean,
    val frac: Float?,
    val skipFrac: Float = 0f,
    val perfect: Boolean = false,
)

/** Colours of a segment's calendar: [hue] for marks, [tint] for today's fill, [track] for empty progress. */
data class CalTheme(val hue: Color, val tint: Color, val track: Color)

val HealthCal = CalTheme(C.Health, C.HealthTint, Color(0xFF1C2524))
val FuelCal = CalTheme(C.Fuel, C.FuelTint, Color(0xFF2A241B))

/**
 * Shared Body day picker (Health and Nutrition): Mon–Sun week strip or month grid, paged by calendar week/month,
 * Week/Month toggle, selected-day label and a "Today" link.
 */
@Composable
fun CalendarBar(
    mode: Int,
    label: String,
    cells: List<DateCell>,
    selectedLabel: String,
    isToday: Boolean,
    theme: CalTheme,
    onPage: (Int) -> Unit,
    onMode: (Int) -> Unit,
    onSelect: (Long) -> Unit,
    onToday: () -> Unit,
) {
    BodySection(Modifier.animateContentSize(tween(220))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundIconButton(Ic.ChevronLeft, if (mode == 0) "Previous week" else "Previous month", size = 32.dp, bg = C.Track) { onPage(-1) }
            Text(label, style = T.smallB, color = C.Ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            RoundIconButton(Ic.ChevronRight, if (mode == 0) "Next week" else "Next month", size = 32.dp, bg = C.Track) { onPage(1) }
            ModeToggle(mode, onMode)
        }
        if (mode == 0) WeekStrip(cells, theme, onSelect) else MonthGrid(cells, theme, onSelect)
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(selectedLabel, style = T.smallB, color = C.InkSoft, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!isToday) Text(
                "Today", style = T.smallB, color = theme.hue,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).press { onToday() }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ModeToggle(mode: Int, onPick: (Int) -> Unit) {
    Row(Modifier.height(30.dp).clip(RoundedCornerShape(10.dp)).background(C.Graphite2).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf("Week", "Month").forEachIndexed { i, l ->
            val on = i == mode
            Box(
                Modifier.height(24.dp).clip(RoundedCornerShape(8.dp)).background(if (on) C.Graphite3 else Color.Transparent)
                    .press { onPick(i) }.padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(l, style = T.tiny.copy(fontWeight = FontWeight.SemiBold), color = if (on) C.Ink else C.InkSubtle) }
        }
    }
}

private fun dow(d: Long) = Days.date(d).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)

/** Strict Mon–Sun row. Selected = filled; today = hue number + outline when not selected. */
@Composable
private fun WeekStrip(days: List<DateCell>, th: CalTheme, onPick: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().height(66.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { d ->
            val shape = RoundedCornerShape(14.dp)
            val bg = when { d.selected -> C.Ink; d.isToday -> th.tint; else -> Color.Transparent }
            Column(
                Modifier.weight(1f).height(66.dp).clip(shape).background(bg)
                    .then(if (d.isToday && !d.selected) Modifier.border(1.dp, th.hue.copy(alpha = 0.5f), shape) else Modifier)
                    .press(haptic = true) { onPick(d.day) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                Text(dow(d.day), style = T.tiny.copy(fontWeight = FontWeight.Normal), color = if (d.selected) C.Graphite0.copy(alpha = 0.7f) else C.InkSubtle)
                Text(
                    d.n.toString(), style = T.section.copy(fontSize = 15.sp),
                    color = when { d.selected -> C.Graphite0; d.isToday -> th.hue; d.future -> C.InkFaint; else -> C.Ink },
                )
                MiniProgress(d.frac, d.future, d.selected, th)
            }
        }
    }
}

/** 22 × 3 dp bar. Nothing when [frac] is null. */
@Composable
private fun MiniProgress(frac: Float?, future: Boolean, onDark: Boolean, th: CalTheme) {
    val f by animateFloatAsState((frac ?: 0f).coerceIn(0f, 1f), tween(600, easing = ProgressEasing), label = "mini")
    Box(Modifier.width(22.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(
        when {
            frac == null -> Color.Transparent
            onDark -> C.Graphite0.copy(alpha = 0.18f)
            future -> C.Track
            else -> th.track
        },
    )) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(3.dp).background(if (onDark) C.Graphite0 else th.hue))
    }
}

@Composable
private fun MonthGrid(cells: List<DateCell>, th: CalTheme, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, style = T.tiny, color = C.InkSubtle, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { c -> MonthCell(c, th, Modifier.weight(1f), onPick) }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MonthCell(c: DateCell, th: CalTheme, modifier: Modifier, onPick: (Long) -> Unit) {
    val frac by animateFloatAsState((c.frac ?: 0f).coerceIn(0f, 1f), tween(600, easing = ProgressEasing), label = "ring")
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.height(44.dp).clip(shape)
            .background(when { c.selected -> C.Graphite3; c.isToday -> th.tint; else -> Color.Transparent })
            .then(
                when {
                    c.selected -> Modifier.border(1.5.dp, C.Ink, shape)
                    c.isToday -> Modifier.border(1.dp, th.hue.copy(alpha = 0.5f), shape)
                    else -> Modifier
                },
            )
            .press(enabled = c.n > 0) { onPick(c.day) },
        contentAlignment = Alignment.Center,
    ) {
        if (c.n == 0) return@Box
        Canvas(Modifier.size(34.dp)) {
            val sw = 3.dp.toPx()
            val r = Size(size.width - sw, size.height - sw)
            val o = Offset(sw / 2, sw / 2)
            if (c.frac != null) {
                drawArc(if (c.future) C.Track else th.track, 0f, 360f, false, o, r, style = Stroke(sw))
                if (c.skipFrac > 0f) drawArc(C.Ghost, -90f + 360f * frac, 360f * c.skipFrac, false, o, r, style = Stroke(sw, cap = StrokeCap.Butt))
                if (frac > 0f) drawArc(th.hue, -90f, 360f * frac, false, o, r, style = Stroke(sw, cap = StrokeCap.Round))
                if (c.perfect) drawCircle(th.hue.copy(alpha = 0.16f), radius = size.minDimension / 2 - sw)
            }
        }
        Text(
            c.n.toString(), style = T.caption.copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center,
            color = when {
                c.isToday -> th.hue
                c.perfect -> th.hue
                c.future || c.frac == null -> C.InkFaint
                else -> C.Ink
            },
        )
    }
}
