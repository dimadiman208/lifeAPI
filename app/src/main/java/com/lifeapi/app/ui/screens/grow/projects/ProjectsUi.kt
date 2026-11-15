package com.lifeapi.app.ui.screens.grow.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.data.PlanTask
import com.lifeapi.app.data.Project
import com.lifeapi.app.ui.components.CheckCircle
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Visual language that keeps Goal / Task / Activity / Result apart at a glance. */
object Kind {
    val Goal = C.Grow
    val Activity = C.Money
    val Result = C.Gain
    val Achievement = C.Fuel
}

fun projectColor(p: Project?): Color = if (p == null) C.InkSubtle else C.hue(p.hue)
fun projectTint(p: Project?): Color = if (p == null) C.Graphite3 else C.tint(p.hue)

fun dayShort(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    return "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
}

fun dayLong(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    return "${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
}

fun relDay(day: Long, today: Long): String = when (day - today) {
    0L -> "Today"
    1L -> "Tomorrow"
    -1L -> "Yesterday"
    else -> dayShort(day)
}

@Composable
fun GrowTop(title: String, sub: String? = null, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundIconButton(Ic.ChevronLeft, "Back", size = 40.dp, bg = Color(0xFF1A1C20), onClick = onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = T.section.copy(fontSize = 18.sp), color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun ProjectBadge(p: Project?, size: Int = 34) {
    val txt = p?.icon?.takeIf { it.isNotBlank() } ?: p?.name?.trim()?.take(1)?.uppercase() ?: "·"
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(projectTint(p)), contentAlignment = Alignment.Center) {
        Text(txt, style = T.smallB.copy(fontSize = (size * 0.42f).sp), color = projectColor(p), maxLines = 1)
    }
}

/** Small mono label like "GOAL" in its kind colour. */
@Composable
fun KindLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = T.label.copy(fontSize = 10.sp), color = color, modifier = modifier)
}

@Composable
fun TextAction(text: String, color: Color = C.Grow, onClick: () -> Unit) {
    Text(
        text, style = T.smallB, color = color,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).press(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/**
 * One-line capture: Enter adds and keeps the keyboard + focus for the next item.
 */
@Composable
fun QuickInput(placeholder: String, modifier: Modifier = Modifier, hue: Color = C.Grow, autoFocus: Boolean = false, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    fun submit() { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } }
    Row(
        modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0D0E10))
            .border(1.dp, C.Hairline, RoundedCornerShape(14.dp)).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (text.isEmpty()) Text(placeholder, style = T.bodyR, color = C.InkFaint, maxLines = 1)
            BasicTextField(
                text, { text = it }, singleLine = true,
                textStyle = T.bodyR.copy(color = C.Ink),
                cursorBrush = SolidColor(hue),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(fr),
            )
        }
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(if (text.isBlank()) C.Graphite3 else hue).press(enabled = text.isNotBlank()) { submit() },
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.Plus, "Add", tint = if (text.isBlank()) C.InkFaint else C.Graphite0, modifier = Modifier.size(18.dp)) }
    }
    if (autoFocus) androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
}

/**
 * Task row: check + title (+ project / "moved from" meta) + an optional one-tap trailing action (e.g. "→ Today").
 * Long-press opens the move menu.
 */
@Composable
fun TaskLine(
    t: PlanTask,
    hue: Color = C.Grow,
    meta: String? = null,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
    onToggle: (() -> Unit)?,
    onMenu: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).press(onLongClick = onMenu, onClick = onMenu),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggle != null) CheckCircle(t.done, hue, size = 22.dp, square = true, onToggle = onToggle)
        else Box(Modifier.padding(horizontal = 14.dp).size(6.dp).clip(CircleShape).background(C.Ghost))
        Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                t.title, style = T.body.copy(textDecoration = if (t.done) TextDecoration.LineThrough else null),
                color = if (t.done) C.InkSubtle else C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) Text(meta, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailingLabel != null && onTrailing != null) {
            Text(
                trailingLabel, style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = hue,
                modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp)).background(hue.copy(alpha = 0.12f))
                    .press(haptic = true, onClick = onTrailing).padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

/** A labelled text block: kind label on top, text below; tap to edit. Shows a dim placeholder when empty. */
@Composable
fun KindBlock(label: String, color: Color, text: String, placeholder: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = if (text.isBlank()) 0.04f else 0.08f))
            .press(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        KindLabel(label, color)
        Text(text.ifBlank { placeholder }, style = if (text.isBlank()) T.small else T.body, color = if (text.isBlank()) C.InkFaint else C.Ink)
    }
}

/** Count tile used in the compact stats rows: big number + label + optional "vs last week" delta. */
@Composable
fun StatTile(value: String, label: String, delta: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF171A1C)).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = T.section.copy(fontSize = 18.sp), color = C.Ink, maxLines = 1)
        Text(label, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (delta != null) Text(delta, style = T.tiny, color = C.InkMuted, maxLines = 1)
    }
}

fun deltaText(d: Int, unit: (Int) -> String = { it.toString() }): String = when {
    d > 0 -> "+${unit(d)} vs last wk"
    d < 0 -> "−${unit(-d)} vs last wk"
    else -> "same as last wk"
}
