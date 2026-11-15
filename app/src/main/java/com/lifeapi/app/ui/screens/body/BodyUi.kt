package com.lifeapi.app.ui.screens.body

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.components.dashedBorder
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import java.time.LocalTime

/** Back button + title + optional trailing pill, as in the detail boards. */
@Composable
fun DetailHeader(title: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    val nav = LocalNav.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundIconButton(Ic.ChevronLeft, "Back", size = 40.dp, bg = C.Track, tint = C.InkSoft) { nav.popBackStack() }
        Text(title, style = T.title.copy(fontSize = 18.sp), color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1)
        if (trailing != null) {
            Box(
                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, C.Bar, RoundedCornerShape(16.dp))
                    .then(if (onTrailing != null) Modifier.press(onClick = onTrailing) else Modifier).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(trailing, style = T.small, color = C.InkMuted) }
        }
    }
}

/** The design's outlined chip; "on" uses the module hue + tint. */
@Composable
fun BChip(text: String, on: Boolean, hue: Color, tint: Color, modifier: Modifier = Modifier, mono: Boolean = false, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) tint else Color.Transparent, tween(180), label = "chip")
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier.height(34.dp).clip(shape).background(bg)
            .border(1.dp, if (on) hue.copy(alpha = 0.35f) else C.Bar, shape)
            .press(haptic = true, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = (if (mono) T.mono.copy(fontSize = 13.sp) else T.small.copy(fontWeight = FontWeight.Medium)), color = if (on) hue else C.InkMuted, maxLines = 1)
    }
}

@Composable
fun LifeSwitch(checked: Boolean, hue: Color, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = C.Graphite0, checkedTrackColor = hue, checkedBorderColor = hue,
            uncheckedThumbColor = C.InkSubtle, uncheckedTrackColor = C.Bar, uncheckedBorderColor = C.Bar,
        ),
    )
}

/** A row inside a grouped card: title (+ sub) and a trailing slot. */
@Composable
fun GroupRow(title: String, sub: String? = null, divider: Boolean = true, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1A1B1F)))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = T.bodyR, color = C.Ink)
                if (sub != null) Text(sub, style = T.caption, color = C.InkSubtle)
            }
            trailing()
        }
    }
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(C.Graphite1).border(1.dp, C.Hairline, shape), content = content)
}

/** Small stat tile: label + big value + optional colored sub. */
@Composable
fun MiniStat(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, subColor: Color = C.InkSubtle, valueSize: Dp = 20.dp) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(C.Graphite1).border(1.dp, C.Hairline, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = T.tiny.copy(fontWeight = FontWeight.Normal), color = C.InkSubtle)
        Text(value, style = T.title.copy(fontSize = valueSize.value.sp), color = C.Ink, maxLines = 1)
        if (sub != null) Text(sub, style = T.caption, color = subColor, maxLines = 1)
    }
}

/** Tall tile showing a time in mono; tap to edit. */
@Composable
fun TimeTile(label: String, time: LocalTime, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.height(72.dp).clip(shape).background(Color(0xFF0D0E10)).border(1.dp, C.Graphite3, shape)
            .press(haptic = true, onClick = onClick).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(label, style = T.tiny.copy(fontWeight = FontWeight.Normal), color = C.InkSubtle)
        Text(com.lifeapi.app.data.hhmm(time), style = T.mono.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium), color = C.Ink)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: LocalTime, hue: Color, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val st = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Graphite1,
        confirmButton = { TextButton({ onPick(LocalTime.of(st.hour, st.minute)); onDismiss() }) { Text("OK", color = hue, style = T.smallB) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.InkMuted, style = T.smallB) } },
        text = {
            TimePicker(
                st,
                colors = TimePickerDefaults.colors(
                    clockDialColor = C.Graphite2, selectorColor = hue, clockDialSelectedContentColor = C.Graphite0,
                    clockDialUnselectedContentColor = C.Ink, periodSelectorBorderColor = C.Bar,
                    timeSelectorSelectedContainerColor = hue.copy(alpha = 0.18f), timeSelectorSelectedContentColor = hue,
                    timeSelectorUnselectedContainerColor = C.Graphite2, timeSelectorUnselectedContentColor = C.Ink,
                ),
            )
        },
    )
}

/** Thin legend square. */
@Composable
fun LegendDot(color: Color, label: String, dashed: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color)
                .then(if (dashed != null) Modifier.dashedBorder(dashed, 3.dp, 1.5.dp) else Modifier),
        )
        Text(label, style = T.tiny.copy(fontWeight = FontWeight.Normal), color = C.InkMuted)
    }
}

/** Numeric text field parse helper. */
fun String.num(): Double? = replace(',', '.').trim().toDoubleOrNull()
