package com.lifeapi.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T

val ProgressEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

// ------------------------------------------------------------------ press

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.press(
    enabled: Boolean = true,
    haptic: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, tween(90, easing = FastOutLinearInEasing), label = "press")
    val h = LocalHapticFeedback.current
    this
        .scale(s)
        .combinedClickable(
            interactionSource = src,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick?.let { l -> { h.performHapticFeedback(HapticFeedbackType.LongPress); l() } },
            onClick = {
                if (haptic) h.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        )
}

// ------------------------------------------------------------------ surfaces

@Composable
fun LifeCard(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    bg: Color = C.Graphite1,
    border: Color = C.Hairline,
    brush: Brush? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    gap: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(bg))
            .border(1.dp, border, shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

fun heroBrush(top: Color) = Brush.verticalGradient(listOf(top, Color(0xFF121316)))

@Composable
fun ScreenGlow(hue: Color, alpha: Float = 0.08f) {
    Canvas(Modifier.fillMaxWidth().height(360.dp)) {
        drawCircle(
            Brush.radialGradient(
                listOf(hue.copy(alpha = alpha), hue.copy(alpha = 0f)),
                center = Offset(size.width * 0.45f, 40.dp.toPx()),
                radius = size.width * 0.55f,
            ),
            radius = size.width * 0.55f,
            center = Offset(size.width * 0.45f, 40.dp.toPx()),
        )
    }
}

@Composable
fun MonoLabel(text: String, color: Color = C.InkSubtle, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = T.label, color = color, modifier = modifier)
}

@Composable
fun SectionHeader(title: String, trailing: String? = null, trailingColor: Color = C.InkSubtle, onTrailing: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.section, color = C.Ink, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = T.caption,
                color = trailingColor,
                modifier = if (onTrailing != null) Modifier.clip(RoundedCornerShape(8.dp)).press(onClick = onTrailing).padding(4.dp) else Modifier,
            )
        }
    }
}

@Composable
fun HDivider(start: Dp = 0.dp) {
    Box(Modifier.padding(start = start).fillMaxWidth().height(1.dp).background(C.Divider))
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

// ------------------------------------------------------------------ numbers

@Composable
fun RollingText(text: String, style: TextStyle, color: Color = C.Ink, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith
                (slideOutVertically(tween(260)) { -it / 3 } + fadeOut(tween(200)))
        },
        label = "roll",
        modifier = modifier,
    ) { Text(it, style = style, color = color, maxLines = 1) }
}

@Composable
fun ValueUnit(value: String, unit: String?, style: TextStyle = T.number, unitStyle: TextStyle = T.small, roll: Boolean = true) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (roll) RollingText(value, style) else Text(value, style = style, color = C.Ink)
        if (unit != null) Text(unit, style = unitStyle, color = C.InkSubtle, modifier = Modifier.padding(bottom = 3.dp))
    }
}

// ------------------------------------------------------------------ progress

@Composable
fun animatedProgress(target: Float): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target.coerceIn(0f, 1f), tween(600, easing = ProgressEasing)) }
    return anim.value
}

@Composable
fun LinearBar(progress: Float, color: Color, track: Color, height: Dp = 6.dp, modifier: Modifier = Modifier) {
    val p = animatedProgress(progress)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (p > 0f) drawRoundRect(color, size = Size(size.width * p, size.height), cornerRadius = r)
    }
}

data class RingSpec(val progress: Float, val color: Color, val track: Color)

@Composable
fun Rings(rings: List<RingSpec>, size: Dp, stroke: Dp, gap: Dp = 4.dp, modifier: Modifier = Modifier, center: @Composable BoxScope.() -> Unit = {}) {
    val anim = rings.map { animatedProgress(it.progress) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            rings.forEachIndexed { i, r ->
                val inset = sw / 2 + i * (sw + gap.toPx())
                val d = this.size.width - inset * 2
                val tl = Offset(inset, inset)
                drawArc(r.track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
                val sweep = 360f * anim[i]
                if (sweep > 0.5f) drawArc(r.color, -90f, sweep, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        center()
    }
}

// ------------------------------------------------------------------ charts

data class BarDatum(val label: String, val value: Float, val highlight: Boolean = false, val ghost: Boolean = false)

@Composable
fun BarChart(
    data: List<BarDatum>,
    color: Color,
    base: Color,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    target: Float? = null,
    targetLabel: String? = null,
    targetColor: Color = C.Ghost,
    maxValue: Float? = null,
    labelStyle: TextStyle = T.tiny.copy(fontSize = 10.sp, fontWeight = FontWeight.Normal),
    corner: Dp = 6.dp,
) {
    val p = animatedProgress(1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(height)) {
            Canvas(Modifier.fillMaxSize()) {
                val n = data.size.coerceAtLeast(1)
                val gapPx = 10.dp.toPx()
                val bw = (size.width - gapPx * (n - 1)) / n
                val top = 10.dp.toPx()
                val max = maxOf(maxValue ?: 0f, data.maxOfOrNull { it.value } ?: 0f, target ?: 0f).coerceAtLeast(1f)
                val h = size.height - top
                data.forEachIndexed { i, d ->
                    val x = i * (bw + gapPx)
                    if (d.ghost) {
                        val gh = h * 0.8f
                        drawRoundRect(
                            C.Bar, Offset(x + 0.5f, size.height - gh), Size(bw - 1f, gh - 0.5f), CornerRadius(corner.toPx()),
                            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                        )
                    } else if (d.value > 0f) {
                        val bh = (h * (d.value / max) * p).coerceAtLeast(4.dp.toPx())
                        drawRoundRect(if (d.highlight) color else base, Offset(x, size.height - bh), Size(bw, bh), CornerRadius(corner.toPx()))
                    } else {
                        drawRoundRect(C.Track, Offset(x, size.height - 4.dp.toPx()), Size(bw, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                    }
                }
                if (target != null) {
                    val y = size.height - h * (target / max)
                    drawLine(targetColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)))
                }
            }
            if (targetLabel != null && target != null) {
                Text(targetLabel, style = T.label.copy(fontSize = 10.sp, letterSpacing = 0.sp), color = C.InkSubtle, modifier = Modifier.align(Alignment.TopEnd).offset(y = (-4).dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            data.forEach {
                Text(
                    it.label, style = labelStyle, color = if (it.highlight) C.Ink else C.InkSubtle,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun LineChart(values: List<Float>, color: Color, modifier: Modifier = Modifier, fill: Boolean = true, grid: Boolean = true) {
    val p = animatedProgress(1f)
    Canvas(modifier) {
        if (grid) {
            drawLine(C.Track, Offset(0f, size.height / 3), Offset(size.width, size.height / 3), 1.dp.toPx())
            drawLine(C.Track, Offset(0f, size.height * 2 / 3), Offset(size.width, size.height * 2 / 3), 1.dp.toPx())
        }
        if (values.size < 2) return@Canvas
        val pad = 12.dp.toPx()
        val w = size.width - 6.dp.toPx()
        val min = values.min(); val max = values.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val pts = values.mapIndexed { i, v ->
            Offset(i * w / (values.size - 1), pad + (size.height - 2 * pad) * (1 - (v - min) / span))
        }
        val shown = (pts.size * p).toInt().coerceIn(2, pts.size)
        val line = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until shown) lineTo(pts[i].x, pts[i].y) }
        if (fill) {
            val area = Path().apply { addPath(line); lineTo(pts[shown - 1].x, size.height); lineTo(0f, size.height); close() }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
        }
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val end = pts[shown - 1]
        drawCircle(C.Graphite0, 4.dp.toPx(), end)
        drawCircle(color, 4.dp.toPx(), end, style = Stroke(2.dp.toPx()))
    }
}

// ------------------------------------------------------------------ controls

@Composable
fun CheckCircle(checked: Boolean, color: Color, size: Dp = 28.dp, square: Boolean = false, onToggle: () -> Unit) {
    val bg by animateColorAsState(if (checked) color else Color.Transparent, tween(220, easing = FastOutSlowInEasing), label = "chk")
    val shape = if (square) RoundedCornerShape(10.dp) else CircleShape
    Box(
        Modifier
            .size(44.dp)
            .press(haptic = true, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size).clip(shape).background(bg)
                .then(if (!checked) Modifier.border(1.5.dp, C.Ghost, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val a by animateFloatAsState(if (checked) 1f else 0f, tween(220), label = "chkA")
            Icon(Ic.Check, null, tint = C.Graphite0.copy(alpha = a), modifier = Modifier.size(size * 0.58f))
        }
    }
}

@Composable
fun PillButton(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    radius: Dp = height / 2,
    icon: ImageVector? = null,
    border: BorderStroke? = null,
    style: TextStyle = T.smallB,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(if (enabled) bg else C.Graphite3)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .press(enabled = enabled, haptic = true, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = if (enabled) fg else C.InkFaint, modifier = Modifier.size(16.dp))
        Text(text, style = style, color = if (enabled) fg else C.InkFaint, maxLines = 1)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, height: Dp = 44.dp, color: Color = C.InkSoft, onClick: () -> Unit) {
    PillButton(text, Color.Transparent, color, modifier, height, 12.dp, border = BorderStroke(1.dp, C.Hairline), onClick = onClick)
}

@Composable
fun RoundIconButton(icon: ImageVector, contentDescription: String?, size: Dp = 44.dp, bg: Color = C.Graphite1, tint: Color = C.InkSoft, badge: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).border(1.dp, Color(0xFF222328), CircleShape).press(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 11.dp).size(7.dp).clip(CircleShape).background(C.Volt))
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, selBg: Color = C.Graphite3, selFg: Color = C.Ink, onSelect: (Int) -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(C.Graphite1).border(1.dp, C.Hairline, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) selBg else Color.Transparent, tween(220), label = "seg")
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(bg).press { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, style = T.smallB, color = if (sel) selFg else C.InkSubtle) }
        }
    }
}

@Composable
fun StatBox(label: String, modifier: Modifier = Modifier, bg: Color = Color(0xFF171A1C), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        content()
    }
}

@Composable
fun TagBadge(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(text, style = T.label.copy(fontSize = 10.sp, letterSpacing = 0.02.sp), color = fg)
    }
}

@Composable
fun SmallChip(text: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Row(
        Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Text(text, style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = fg)
    }
}

// ------------------------------------------------------------------ empty state

@Composable
fun EmptyCard(
    module: String,
    hue: Color,
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    ghost: @Composable (() -> Unit)? = null,
) {
    LifeCard(Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(20.dp), gap = 12.dp) {
        MonoLabel(module, hue)
        if (ghost != null) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) { ghost() }
        Text(title, style = T.title.copy(fontSize = 20.sp), color = C.Ink)
        Text(body, style = T.small, color = C.InkMuted)
        Spacer(Modifier.height(2.dp))
        PillButton(primary, hue, C.Graphite0, Modifier.fillMaxWidth(), height = 48.dp, radius = 14.dp, style = T.smallB.copy(fontSize = 14.sp), onClick = onPrimary)
        if (secondary != null && onSecondary != null) GhostButton(secondary, Modifier.fillMaxWidth(), onClick = onSecondary)
    }
}

@Composable
fun GhostBars(hue: Color, filled: Int = 0, total: Int = 7) {
    Canvas(Modifier.width(200.dp).height(80.dp)) {
        val bw = 18.dp.toPx(); val gap = 6.dp.toPx()
        val heights = listOf(0.5f, 0.7f, 0.4f, 0.75f, 0.55f, 0.85f, 0.6f)
        val startX = (size.width - (bw * total + gap * (total - 1))) / 2
        for (i in 0 until total) {
            val h = size.height * heights[i % heights.size]
            val o = Offset(startX + i * (bw + gap), size.height - h)
            if (i < filled) drawRoundRect(hue, o, Size(bw, h), CornerRadius(5.dp.toPx()))
            else drawRoundRect(
                hue.copy(alpha = 0.7f), o, Size(bw, h), CornerRadius(5.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))),
            )
        }
    }
}

// ------------------------------------------------------------------ inputs

@Composable
fun LifeField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = T.caption) },
        placeholder = placeholder?.let { { Text(it, style = T.bodyR, color = C.InkFaint) } },
        singleLine = singleLine,
        textStyle = T.bodyR.copy(fontSize = 15.sp, color = C.Ink),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Volt.copy(alpha = 0.6f),
            unfocusedBorderColor = C.Hairline,
            focusedContainerColor = Color(0xFF0D0E10),
            unfocusedContainerColor = Color(0xFF0D0E10),
            focusedLabelColor = C.InkMuted,
            unfocusedLabelColor = C.InkSubtle,
            cursorColor = C.Volt,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = T.caption, color = C.InkMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepBtn("−", onMinus)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { RollingText(value, T.title.copy(fontSize = 26.sp)) }
            StepBtn("+", onPlus)
        }
    }
}

@Composable
private fun StepBtn(t: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(C.Graphite3).press(haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, style = T.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium), color = C.Ink) }
}

@Composable
fun RowScope.Spring() = Spacer(Modifier.weight(1f))

fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.dp): Modifier = drawBehind {
    drawRoundRect(
        color, cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
    )
}
