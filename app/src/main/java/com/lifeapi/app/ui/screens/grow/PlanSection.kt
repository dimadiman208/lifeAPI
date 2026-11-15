package com.lifeapi.app.ui.screens.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.ui.components.HDivider
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow.PlanState
import com.lifeapi.app.vm.grow.TaskRow
import com.lifeapi.app.vm.grow.WishState

@Composable
private fun TaskCard(rows: List<TaskRow>, label: String?, onToggle: (TaskRow) -> Unit, onOpen: (TaskRow) -> Unit, onLong: (TaskRow) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) MonoLabel(label)
        LifeCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 4.dp, end = 14.dp, top = 2.dp, bottom = 2.dp), gap = 0.dp) {
            rows.forEachIndexed { i, r ->
                if (i > 0) HDivider(start = 48.dp)
                TaskLine(r.t.title, r.meta, r.done, C.Volt, r.t.top, r.overdue, onToggle = { onToggle(r) }, onOpen = { onOpen(r) }, onLong = { onLong(r) })
            }
        }
    }
}

fun LazyListScope.planItems(
    st: PlanState,
    wish: WishState?,
    tab: Int,
    onTab: (Int) -> Unit,
    selectedDay: Long?,
    onSelectDay: (Long) -> Unit,
    dayRows: List<TaskRow>,
    onToggle: (TaskRow) -> Unit,
    onOpen: (TaskRow) -> Unit,
    onLong: (TaskRow) -> Unit,
    onMonth: (Int) -> Unit,
    onWishlist: () -> Unit,
) {
    item(key = "plan-tabs") {
        Segmented(listOf("Today", "Upcoming", if (st.inbox.isEmpty()) "Inbox" else "Inbox ${st.inbox.size}", "Calendar"), tab, onSelect = onTab)
    }
    when (tab) {
        0 -> {
            if (st.top.isEmpty() && st.anytime.isEmpty()) {
                item(key = "plan-empty") {
                    LifeCard(Modifier.fillMaxWidth(), radius = 20.dp, gap = 8.dp) {
                        MonoLabel("Plan", C.Volt)
                        Text("A clear day", style = T.title.copy(fontSize = 20.sp), color = C.Ink)
                        Text("Type a task in the bar below. Add ! to put it in your Top 3, “tmrw 18:00” to schedule it.", style = T.small, color = C.InkMuted)
                    }
                }
            }
            if (st.top.isNotEmpty()) item(key = "plan-top") { TaskCard(st.top, "TOP ${st.top.size} TODAY", onToggle, onOpen, onLong) }
            if (st.anytime.isNotEmpty()) item(key = "plan-any") { TaskCard(st.anytime, "ANYTIME", onToggle, onOpen, onLong) }
            if (st.top.size + st.anytime.size > 0) item(key = "plan-hint") {
                Text("Long-press a task to move it in or out of Top 3.", style = T.caption, color = C.InkFaint, modifier = Modifier.padding(start = 4.dp))
            }
        }
        1 -> {
            if (st.upcoming.isEmpty()) item(key = "up-empty") { EmptyLine("Nothing scheduled for the next two weeks.") }
            st.upcoming.forEach { (label, rows) -> item(key = "up-$label") { TaskCard(rows, label.uppercase(), onToggle, onOpen, onLong) } }
        }
        2 -> {
            if (st.inbox.isEmpty()) item(key = "in-empty") { EmptyLine("Inbox zero. Tasks without a date land here (“someday …”).") }
            else item(key = "in-list") { TaskCard(st.inbox, "NO DATE", onToggle, onOpen, onLong) }
        }
        else -> {
            item(key = "cal") { CalendarCard(st, selectedDay, onSelectDay, onMonth) }
            if (selectedDay != null) item(key = "cal-day") {
                if (dayRows.isEmpty()) EmptyLine("Nothing on ${com.lifeapi.app.data.grow.GrowCalc.dayLabel(selectedDay, st.today)}.")
                else TaskCard(dayRows, com.lifeapi.app.data.grow.GrowCalc.dayLabel(selectedDay, st.today).uppercase(), onToggle, onOpen, onLong)
            }
        }
    }
    item(key = "wish-entry") {
        LifeCard(Modifier.fillMaxWidth().press(onClick = onWishlist), radius = 18.dp, padding = PaddingValues(14.dp), gap = 0.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.MoneyTint), contentAlignment = Alignment.Center) {
                    Icon(GrowIc.Gift, null, tint = C.Money, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Wishlist", style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink)
                    Text(
                        if (wish == null || wish.items.isEmpty()) "Things you want — price, priority, savings"
                        else "${wish.items.size} items · ${fmtCents(wish.totalWanted)} wanted · ${fmtCents(wish.savedToward)} saved",
                        style = T.caption, color = C.InkSubtle,
                    )
                }
                Icon(Ic.ChevronRight, null, tint = C.InkSubtle, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun EmptyLine(text: String) {
    LifeCard(Modifier.fillMaxWidth(), radius = 18.dp, padding = PaddingValues(16.dp)) {
        Text(text, style = T.small, color = C.InkMuted)
    }
}

@Composable
private fun CalendarCard(st: PlanState, selected: Long?, onSelect: (Long) -> Unit, onMonth: (Int) -> Unit) {
    LifeCard(Modifier.fillMaxWidth(), gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(st.monthLabel, style = T.section, color = C.Ink, modifier = Modifier.weight(1f))
            RoundIconButton(Ic.ChevronLeft, "Previous month", size = 36.dp, bg = C.Graphite2) { onMonth(-1) }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Ic.ChevronRight, "Next month", size = 36.dp, bg = C.Graphite2) { onMonth(1) }
        }
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = T.tiny, color = C.InkSubtle, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        st.calendar.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { c ->
                    val sel = c.day == selected
                    val isToday = c.day == st.today
                    Column(
                        Modifier.weight(1f).aspectRatio(0.9f).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) C.Graphite3 else Color.Transparent)
                            .then(if (isToday) Modifier.border(1.dp, C.Volt.copy(alpha = 0.6f), RoundedCornerShape(10.dp)) else Modifier)
                            .press { onSelect(c.day) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("${c.n}", style = T.small.copy(fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal), color = if (!c.inMonth) C.InkFaint.copy(alpha = 0.5f) else C.Ink)
                        Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(minOf(c.count, 3)) { i ->
                                Box(Modifier.size(4.dp).clip(CircleShape).background(if (i < c.doneCount) C.Volt else C.Ghost))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The quick-add bar that replaces the Log FAB on the Plan segment. */
@Composable
fun QuickAddBar(modifier: Modifier = Modifier, placeholder: String = "Add task · “call mom tmrw 18:00”", onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun submit() { if (text.isNotBlank()) { onAdd(text); text = "" } }
    Row(
        modifier.padding(horizontal = 12.dp).fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(18.dp))
            .height(52.dp).clip(RoundedCornerShape(18.dp)).background(C.Graphite2)
            .border(1.dp, C.Bar, RoundedCornerShape(18.dp))
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(placeholder, style = T.bodyR.copy(fontSize = 15.sp), color = C.InkFaint, maxLines = 1)
            BasicTextField(
                text, { text = it }, singleLine = true,
                textStyle = T.bodyR.copy(fontSize = 15.sp, color = C.Ink),
                cursorBrush = SolidColor(C.Volt),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(if (text.isBlank()) C.Graphite3 else C.Volt).press(haptic = true) { submit() },
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.Plus, "Add task", tint = if (text.isBlank()) C.InkSubtle else C.Graphite0, modifier = Modifier.size(18.dp)) }
    }
}
