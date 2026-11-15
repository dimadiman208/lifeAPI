package com.lifeapi.app.ui.screens.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

internal fun cash(hidden: Boolean, cents: Long, sign: Boolean = false) = if (hidden) "€ ••••" else fmtCents(cents, sign)

/** "€86" style without cents for big round summaries. */
internal fun cashShort(cents: Long): String = String.format(Locale.US, "€%,d", kotlin.math.abs(cents) / 100).let { if (cents < 0) "−$it" else it }

internal fun dayLabel(day: Long, today: Long = Days.today()): String {
    val d = Days.date(day)
    return when (day) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
    }
}

internal fun shortDate(day: Long): String = Days.date(day).let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" }

internal fun ordinal(n: Int) = "$n" + if (n in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

internal val Inset = Color(0xFF0D0E10)
internal val RowDivider = Color(0xFF1A1B1F)
internal val ChipBorder = Color(0xFF2C2F36)
internal val MoneySel = Color(0xFF1E2233)
internal val MoneySelFg = Color(0xFFC9D3FF)
internal val MoneySelBd = Color(0xFF3A4670)

/** Back button + title row used by every Money detail screen. */
@Composable
internal fun DetailTop(title: String, sub: String? = null, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundIconButton(Ic.ChevronLeft, "Back", size = 40.dp, bg = Color(0xFF1A1C20), onClick = onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = T.section.copy(fontSize = 18.sp), color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Card with hairline-divided rows. */
@Composable
internal fun ListCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Graphite1).border(1.dp, C.Hairline, RoundedCornerShape(20.dp)),
        content = content,
    )
}

@Composable
internal fun DivRow(first: Boolean, onClick: (() -> Unit)? = null, onLong: (() -> Unit)? = null, minH: Dp = 56.dp, content: @Composable RowScope.() -> Unit) {
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(RowDivider))
        Row(
            Modifier.fillMaxWidth().heightIn(min = minH)
                .then(if (onClick != null || onLong != null) Modifier.press(onLongClick = onLong) { onClick?.invoke() } else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
    }
}

@Composable
internal fun Avatar(text: String, bg: Color = Color(0xFF1A1C20), fg: Color = C.InkSoft, size: Dp = 36.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(text, style = T.smallB.copy(fontWeight = FontWeight.Bold, fontSize = if (text.length > 2) 10.sp else 13.sp), color = fg, maxLines = 1)
    }
}

@Composable
internal fun Cap(text: String, color: Color = C.InkSubtle) {
    Text(text.uppercase(), style = T.label, color = color, modifier = Modifier.padding(horizontal = 4.dp))
}

/** Outline chip, optionally selected (Money blue). */
@Composable
internal fun OChip(text: String, sel: Boolean = false, fg: Color = C.InkMuted, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        Modifier.height(34.dp).clip(shape).background(if (sel) MoneySel else Color.Transparent)
            .border(1.dp, if (sel) MoneySelBd else ChipBorder, shape).press(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.small.copy(fontWeight = FontWeight.Medium), color = if (sel) MoneySelFg else fg, maxLines = 1) }
}

/** Chip that opens a dropdown of options. */
@Composable
internal fun <T> DropChip(label: String, options: List<Pair<String, T>>, onPick: (T) -> Unit, fg: Color = C.Ink) {
    var open by remember { mutableStateOf(false) }
    Box {
        OChip("$label ▾", fg = fg) { open = true }
        DropdownMenu(open, { open = false }, containerColor = C.Graphite2) {
            options.forEach { (l, v) ->
                DropdownMenuItem(text = { Text(l, style = T.body, color = C.Ink) }, onClick = { open = false; onPick(v) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayPickerDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = Days.date(initial).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                st.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                onDismiss()
            }) { Text("OK", color = C.Money) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.InkMuted) } },
        colors = DatePickerDefaults.colors(containerColor = C.Graphite1),
    ) {
        DatePicker(
            st,
            colors = DatePickerDefaults.colors(
                containerColor = C.Graphite1, selectedDayContainerColor = C.Money, selectedDayContentColor = C.Graphite0,
                todayDateBorderColor = C.Money, todayContentColor = C.Money,
            ),
        )
    }
}

@Composable
internal fun Toggle(label: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().press { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = T.body, color = C.Ink)
            if (sub != null) Text(sub, style = T.caption, color = C.InkSubtle)
        }
        androidx.compose.material3.Switch(
            on, onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = C.Money, checkedThumbColor = C.Graphite0, uncheckedTrackColor = C.Graphite3, uncheckedBorderColor = C.Ghost),
        )
    }
}
