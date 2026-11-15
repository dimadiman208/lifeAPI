package com.lifeapi.app.ui.screens.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T

/** Extra stroke icons for Today / Settings / Intelligence. */
object TIc {
    private fun icon(name: String, w: Float, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { b.addPath(addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = w, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) }
        return b.build()
    }
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} $cy A$r $r 0 1 0 ${cx + r} $cy A$r $r 0 1 0 ${cx - r} $cy Z"

    val Sliders = icon("sliders", 1.75f, "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val Up = icon("up", 2f, "M6 15l6-6 6 6")
    val Down = icon("down", 2f, "M6 9l6 6 6-6")
    val Grip = icon("grip", 2.6f, "M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01")
    val Refresh = icon("refresh", 1.75f, "M20 11a8 8 0 0 0-14.3-4.9L4 8", "M4 4v4h4", "M4 13a8 8 0 0 0 14.3 4.9L20 16", "M20 20v-4h-4")
    val Lock = icon("lock", 1.75f, "M7 11V8a5 5 0 0 1 10 0v3", "M6 11h12v9H6z")
    val Finger = icon("finger", 1.6f, "M12 11v4", "M8.5 13c0-2 1.5-3.5 3.5-3.5s3.5 1.5 3.5 3.5v2c0 2-.5 3.5-1.5 5", "M5.5 15v-2a6.5 6.5 0 0 1 13 0v1", "M7 5.5A9 9 0 0 1 17 5.5", "M10 20.5c.6-1.6 1-3.3 1-5.5")
    val Scale = icon("scale", 1.75f, "M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M8.5 10a3.5 3.5 0 0 1 7 0", "M12 10l1.5-2")
    val Task = icon("task", 1.75f, "M4 6.5h2M4 12h2M4 17.5h2", "M9.5 6.5H20M9.5 12H20M9.5 17.5H20")
    val Backspace = icon("bksp", 1.75f, "M9 5h11v14H9l-6-7z", "M12.5 9.5l5 5M17.5 9.5l-5 5")
}

@Composable
fun LifeSwitch(on: Boolean, enabled: Boolean = true, onColor: Color = C.Volt, onToggle: (Boolean) -> Unit) {
    val trk by animateColorAsState(if (on) onColor else C.Bar, tween(200), label = "sw")
    val x by animateDpAsState(if (on) 20.dp else 4.dp, tween(200), label = "swx")
    Box(
        Modifier.size(44.dp, 28.dp).clip(RoundedCornerShape(14.dp)).background(if (enabled) trk else trk.copy(alpha = 0.4f))
            .press(enabled = enabled, haptic = true) { onToggle(!on) },
    ) {
        Box(Modifier.offset(x = x, y = 4.dp).size(20.dp).clip(CircleShape).background(if (on) C.Graphite0 else C.InkSubtle))
    }
}

@Composable
fun Cap(text: String, color: Color = C.InkSubtle, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = T.label, color = color, modifier = modifier.padding(horizontal = 4.dp))
}

/** Back-button header used by detail screens. */
@Composable
fun DetailTop(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(C.Track).press(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Ic.ChevronLeft, "Back", tint = C.InkSoft, modifier = Modifier.size(20.dp))
        }
        Text(title, style = T.section.copy(fontSize = 18.sp), color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** Full-screen scrolling list for detail routes (no bottom bar). */
@Composable
fun DetailList(gap: Dp = 14.dp, content: LazyListScope.() -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        Modifier.fillMaxSize().background(C.Graphite0),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 16.dp, bottom = bottom + 32.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, border: Color = C.Hairline, bg: Color = C.Graphite1, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).border(1.dp, border, RoundedCornerShape(20.dp)), content = content)
}

/** Settings-style row: optional square badge, title + small subtitle, trailing value or content. */
@Composable
fun SRow(
    title: String,
    sub: String? = null,
    value: String? = null,
    first: Boolean = false,
    badge: String? = null,
    badgeBg: Color = C.Track,
    badgeFg: Color = C.InkSoft,
    titleColor: Color = C.Ink,
    valueColor: Color = C.InkSubtle,
    chevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1A1B1F)))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (badge != null) Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(badgeBg), contentAlignment = Alignment.Center) {
                Text(badge, style = T.smallB, color = badgeFg)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = T.bodyR, color = titleColor)
                if (sub != null) Text(sub, style = T.caption, color = C.InkSubtle)
            }
            if (value != null) Text(value + if (chevron) " ›" else "", style = T.small, color = valueColor)
            else if (chevron) Text("›", style = T.small, color = C.InkSubtle)
            trailing?.invoke()
        }
    }
}

@Composable
fun ChipToggle(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) Color(0xFF1F2610) else Color.Transparent)
            .border(1.dp, if (on) Color(0xFF4A5A1E) else C.Bar, RoundedCornerShape(18.dp)).press(haptic = true, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = T.small.copy(fontSize = 13.sp), color = if (on) C.Volt else C.InkMuted) }
}

@Composable
fun Skeleton(widthFraction: Float, height: Dp = 14.dp) {
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "sk")
    val a by inf.animateFloat(0.45f, 0.9f, androidx.compose.animation.core.infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "ska")
    Box(Modifier.fillMaxWidth(widthFraction).height(height).clip(RoundedCornerShape(6.dp)).background(Color(0xFF202227).copy(alpha = a)))
}

