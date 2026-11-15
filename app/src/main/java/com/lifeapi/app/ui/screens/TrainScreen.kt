package com.lifeapi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.HDivider
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.SectionHeader
import com.lifeapi.app.ui.components.heroBrush
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.body.CalendarBar
import com.lifeapi.app.ui.screens.train.TrainAnalyticsCard
import com.lifeapi.app.ui.screens.train.TrainCal
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.DaySession
import com.lifeapi.app.vm.TemplateWithEx
import com.lifeapi.app.vm.TrainState
import com.lifeapi.app.vm.TrainVM
import java.util.Locale

/**
 * Train, laid out like Body: analytics card on top, the shared week/month calendar, then what to do
 * (start / continue), the selected day's workouts and PRs.
 */
@Composable
fun TrainScreen(onOpenWorkout: (Long) -> Unit, vm: TrainVM = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TemplateWithEx?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pickOther by remember { mutableStateOf(false) }
    val nav = com.lifeapi.app.ui.LocalNav.current
    fun openHistory(name: String) = nav.navigate("train/exercise/${android.net.Uri.encode(name)}")

    // "Log" FAB becomes Start / Open on this tab.
    val fabAction by androidx.compose.runtime.rememberUpdatedState {
        val st = s ?: return@rememberUpdatedState
        val a = st.active
        if (a != null) onOpenWorkout(a.id) else vm.start(st.next?.t, onOpenWorkout)
    }
    com.lifeapi.app.ui.FabOverride(
        com.lifeapi.app.ui.FabSpec(if (s?.active != null) "Open" else "Start", Ic.Play) { fabAction() }
    )

    ScreenList(glow = C.Train) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Train", style = T.headline, color = C.Ink, modifier = Modifier.weight(1f))
                com.lifeapi.app.ui.screens.train.TrainCircleButton(com.lifeapi.app.ui.screens.train.TrainIc.List, "Templates", size = 36.dp) {
                    nav.navigate("train/templates")
                }
            }
        }
        item(key = "analytics") { TrainAnalyticsCard() }
        val st = s ?: return@ScreenList
        item(key = "calendar") {
            CalendarBar(
                mode = st.calMode, label = st.calLabel, cells = st.cal, selectedLabel = st.selectedLabel, isToday = st.isToday,
                theme = TrainCal,
                onPage = vm::page, onMode = vm::setCalMode, onSelect = vm::select, onToday = vm::selectToday,
            )
        }
        when {
            st.active != null -> item(key = "continue") { ContinueCard(st) { onOpenWorkout(st.active.id) } }
            st.templates.isEmpty() -> item(key = "empty") {
                EmptyCard(
                    "Train", C.Train, if (st.hasAnyWorkout) "No templates yet" else "No workouts yet",
                    "Create a template for the workouts you repeat, or start empty and add exercises as you go.",
                    "Create a template", { creating = true },
                    "Start empty workout", { vm.start(null, onOpenWorkout) },
                )
            }
            st.isToday -> st.next?.let { n ->
                item(key = "next") { NextCard(st, n, onStart = { vm.start(n.t, onOpenWorkout) }, onEdit = { editing = n }, onOther = { pickOther = !pickOther }) }
            }
        }
        if (pickOther && st.active == null && st.templates.isNotEmpty() && st.isToday) {
            item(key = "other") { OtherPicker(st, { pickOther = false; vm.start(it, onOpenWorkout) }, { editing = it }, { creating = true }, { pickOther = false; nav.navigate("train/templates") }) }
        }
        item(key = "day") { DayWorkouts(st, onOpenWorkout, ::openHistory, vm::deleteSession) }
        if (st.prs.isNotEmpty()) item(key = "prs") { PrCard(st, ::openHistory) }
    }

    if (creating || editing != null) {
        val e = editing
        com.lifeapi.app.ui.screens.train.TemplateEditSheet(
            e, { creating = false; editing = null },
            onSave = { name, ex, days -> vm.saveTemplate(e?.t?.id, name, ex, days) },
            onDelete = e?.let { { vm.deleteTemplate(it.t.id) } },
        )
    }
}

@Composable
private fun ContinueCard(st: TrainState, onOpen: () -> Unit) {
    val a = st.active ?: return
    LifeCard(Modifier.fillMaxWidth(), radius = 24.dp, brush = heroBrush(Color(0xFF1C1513)), padding = PaddingValues(20.dp), gap = 12.dp) {
        MonoLabel("In progress", C.Train)
        Text(a.name, style = T.title, color = C.Ink)
        PillButton("Open workout", C.Train, C.Graphite0, Modifier.fillMaxWidth(), 52.dp, 16.dp, icon = Ic.Play, style = T.section, onClick = onOpen)
    }
}

@Composable
private fun NextCard(st: TrainState, n: TemplateWithEx, onStart: () -> Unit, onEdit: () -> Unit, onOther: () -> Unit) {
    LifeCard(Modifier.fillMaxWidth(), radius = 24.dp, brush = heroBrush(Color(0xFF1C1513)), padding = PaddingValues(20.dp), gap = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MonoLabel(if (st.nextScheduled) "Today · scheduled" else "Today · up next", C.Train)
                Text(n.t.name, style = T.title, color = C.Ink)
                Text(
                    "${n.ex.size} exercises" + (st.nextLastVolume?.let { String.format(Locale.US, " · last %,.0f kg", it) } ?: ""),
                    style = T.small, color = C.InkMuted,
                )
            }
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).press(onClick = onEdit), contentAlignment = Alignment.Center) {
                Icon(Ic.Edit, "Edit workout", tint = C.InkMuted, modifier = Modifier.size(18.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            n.ex.take(3).forEach { e ->
                Row {
                    Text(e.name, style = T.body, color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${e.sets} × ${e.reps}${if (e.kg > 0) " · ${fmtKg(e.kg)} kg" else ""}", style = T.small, color = C.InkMuted)
                }
            }
            if (n.ex.size > 3) Text("+ " + n.ex.drop(3).joinToString(", ") { it.name }, style = T.small, color = C.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Start workout", C.Train, C.Graphite0, Modifier.weight(1f), 52.dp, 16.dp, icon = Ic.Play, style = T.section, onClick = onStart)
            PillButton("Other", C.TrainTint, C.Train, Modifier.width(88.dp), 52.dp, 16.dp, onClick = onOther)
        }
    }
}

@Composable
private fun OtherPicker(
    st: TrainState,
    onStart: (com.lifeapi.app.data.WorkoutTemplate?) -> Unit,
    onEdit: (TemplateWithEx) -> Unit,
    onNew: () -> Unit,
    onAll: () -> Unit,
) {
    LifeCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), gap = 0.dp) {
        st.templates.forEachIndexed { i, t ->
            if (i > 0) HDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).press(onLongClick = { onEdit(t) }) { onStart(t.t) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.t.name, style = T.body, color = C.Ink)
                    Text("${t.ex.size} exercises", style = T.caption, color = C.InkSubtle)
                }
                Text("Edit", style = T.caption, color = C.InkSubtle, modifier = Modifier.press { onEdit(t) }.padding(8.dp))
                Icon(Ic.Play, null, tint = C.Train, modifier = Modifier.size(18.dp))
            }
        }
        HDivider()
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Empty workout", style = T.body, color = C.InkMuted, modifier = Modifier.weight(1f).press { onStart(null) })
            Text("All templates", style = T.smallB, color = C.InkMuted, modifier = Modifier.press(onClick = onAll).padding(8.dp))
            Text("+ New", style = T.smallB, color = C.Train, modifier = Modifier.press(onClick = onNew).padding(8.dp))
        }
    }
}

/** The selected calendar day's workouts — one card each, exercise lines inside. */
@Composable
private fun DayWorkouts(st: TrainState, onOpenWorkout: (Long) -> Unit, onHistory: (String) -> Unit, onDelete: (com.lifeapi.app.data.WorkoutSession) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(if (st.isToday) "Today's training" else st.selectedLabel)
        if (st.daySessions.isEmpty()) {
            LifeCard(Modifier.fillMaxWidth(), radius = 18.dp, padding = PaddingValues(16.dp)) {
                Text(if (st.isToday) "No workout logged today yet." else "No workout on this day.", style = T.small, color = C.InkMuted)
            }
        }
        st.daySessions.forEach { d -> DayCard(d, onOpenWorkout, onHistory, onDelete = { onDelete(d.s) }) }
    }
}

@Composable
private fun DayCard(d: DaySession, onOpenWorkout: (Long) -> Unit, onHistory: (String) -> Unit, onDelete: () -> Unit) {
    // tap → open (in progress) or edit (saved); long-press a saved one → delete with Undo
    LifeCard(
        Modifier.fillMaxWidth().press(onLongClick = if (d.inProgress) null else onDelete) { onOpenWorkout(d.s.id) },
        radius = 18.dp, padding = PaddingValues(14.dp), gap = 8.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(d.s.name, style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink)
                Text(d.meta, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (d.inProgress) Box(Modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(C.TrainTint).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text("OPEN", style = T.label.copy(fontSize = 10.sp), color = Color(0xFFFFB29C))
            } else Icon(com.lifeapi.app.ui.components.Ic.Edit, "Edit workout", tint = C.InkFaint, modifier = Modifier.size(16.dp))
        }
        if (d.lines.isNotEmpty()) HDivider()
        d.lines.forEach { (name, sets) ->
            Row(Modifier.fillMaxWidth().press { onHistory(name) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = T.small, color = C.InkSoft, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sets, style = T.mono, color = C.InkMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PrCard(st: TrainState, onHistory: (String) -> Unit) {
    LifeCard(Modifier.fillMaxWidth(), gap = 4.dp) {
        SectionHeader("Personal records", "e1RM")
        st.prs.forEachIndexed { i, r ->
            if (i > 0) HDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).press { onHistory(r.lift) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.lift, style = T.body, color = C.Ink)
                    Text(r.date, style = T.caption, color = C.InkSubtle)
                }
                if (r.isNew) Box(Modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(C.TrainTint).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text("NEW", style = T.label.copy(fontSize = 10.sp), color = Color(0xFFFFB29C))
                }
                Text(r.best, style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink)
            }
        }
    }
}
