package com.lifeapi.app.ui.screens.grow

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.ui.components.CheckCircle
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T

/** Extra stroke icons for Grow. */
object GrowIc {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach {
            b.addPath(addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        return b.build()
    }
    val Timer = icon("timer", "M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16z", "M12 9v4l2.5 2", "M9.5 2.5h5")
    val Folder = icon("folder", "M3.5 7.5a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2v7.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z")
    val Gift = icon("gift", "M4 10h16v10H4z", "M3 7h18v3H3z", "M12 7v13", "M12 7c-1.5-3-5-3.5-5-1.2C7 7 9.5 7 12 7zM12 7c1.5-3 5-3.5 5-1.2C17 7 14.5 7 12 7z")
    val Calendar = icon("cal", "M4.5 6.5a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2z", "M4.5 10h15M9 3v3M15 3v3")
    val Repeat = icon("repeat", "M4 11V9a3 3 0 0 1 3-3h12l-3-3M20 13v2a3 3 0 0 1-3 3H5l3 3")
    val Flag = icon("flag", "M5 21V4M5 4h11l-2 4 2 4H5")
    val Pause = icon("pause", "M8.5 5.5v13M15.5 5.5v13")
    val Star = icon("star", "M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8-5.2-2.8-5.2 2.8 1-5.8-4.3-4.1 5.9-.8z")
    val Bell = Ic.Bell
}

val GrowDim = Color(0xFF7D66AB)
val GrowBorder = Color(0xFF3A2F52)
val GrowCardTop = Color(0xFF1C1726)
val GrowTrack = Color(0xFF1F1B27)

/** Detail screen frame: back button + title, then the list. */
@Composable
fun DetailList(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    glow: Color? = C.Grow,
    content: LazyListScope.() -> Unit,
) {
    ScreenList(glow = glow, bottomExtra = false) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundIconButton(Ic.ChevronLeft, "Back", size = 40.dp, onClick = onBack)
                Column(Modifier.weight(1f)) {
                    Text(title, style = T.title.copy(fontSize = 18.sp), color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                trailing()
            }
        }
        content()
    }
}

/** Task row with Volt (Plan) or module-hue check. */
@Composable
fun TaskLine(
    title: String,
    meta: String,
    done: Boolean,
    hue: Color = C.Volt,
    top: Boolean = false,
    overdue: Boolean = false,
    onToggle: () -> Unit,
    onOpen: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onOpen != null) Modifier.press(onLongClick = onLong, onClick = onOpen) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(done, hue, 24.dp, square = true, onToggle = onToggle)
        Column(Modifier.weight(1f).padding(start = 2.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val fg by animateColorAsState(if (done) C.InkSubtle else C.Ink, tween(220), label = "tl")
            Text(
                title, style = T.body.copy(textDecoration = if (done) TextDecoration.LineThrough else null), color = fg,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) Text(meta, style = T.caption, color = if (overdue && !done) C.Negative else C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (top && !done) Icon(GrowIc.Star, "Top 3", tint = C.Volt.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
    }
}

/** Three-dimension tile used in projects and reviews. */
@Composable
fun DimTile(label: String, value: String, sub: String, modifier: Modifier = Modifier, subColor: Color = C.InkSubtle, big: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF16151A)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MonoLabel(label, C.InkSubtle)
        Text(value, style = if (big) T.number else T.section.copy(fontSize = 17.sp), color = C.Ink, maxLines = 1)
        Text(sub, style = T.caption, color = subColor, maxLines = 2)
    }
}

/** Small selectable chip. */
@Composable
fun Chip(text: String, selected: Boolean, hue: Color = C.Grow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) hue.copy(alpha = 0.16f) else C.Graphite2, tween(200), label = "chip")
    Box(
        modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(bg)
            .then(if (selected) Modifier.border(1.dp, hue.copy(alpha = 0.5f), RoundedCornerShape(10.dp)) else Modifier)
            .press(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.small.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), color = if (selected) hue else C.InkMuted, maxLines = 1) }
}

/** Weekday toggles M T W T F S S over a bitmask. */
@Composable
fun WeekdayPicker(mask: Int, hue: Color = C.Volt, size: Dp = 38.dp, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, l ->
            val on = mask and (1 shl i) != 0
            val bg by animateColorAsState(if (on) hue else C.Graphite2, tween(180), label = "wd")
            Box(
                Modifier.size(size).clip(CircleShape).background(bg).press(haptic = true) { onChange(mask xor (1 shl i)) },
                contentAlignment = Alignment.Center,
            ) { Text(l, style = T.smallB, color = if (on) C.Graphite0 else C.InkMuted) }
        }
    }
}
