package com.lifeapi.app.ui.screens.body.supps

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeapi.app.data.Supplement
import com.lifeapi.app.data.SuppRule
import com.lifeapi.app.data.supps.DayProgress
import com.lifeapi.app.data.supps.SuppSchedule
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.ProgressEasing
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.SmallChip
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.body.BodyRoutes
import com.lifeapi.app.ui.screens.body.BodySection
import com.lifeapi.app.ui.screens.body.BodySectionHeader
import com.lifeapi.app.ui.screens.body.EntrySheet
import com.lifeapi.app.ui.screens.body.SupplementEditor
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.body.BodyHealthVM
import com.lifeapi.app.vm.body.CalDay
import com.lifeapi.app.vm.body.HealthTab

private val TrackTeal = Color(0xFF1C2524)

/**
 * Supplements: calendar week (Mon–Sun) or month with per-day plan progress, the selected day's doses grouped
 * Morning / Day / Evening with Take all. The selected day is shared with Water, Sleep and Weight.
 */
@Composable
fun SuppsSection(s: HealthTab, vm: BodyHealthVM) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var editing by remember { mutableStateOf<Supplement?>(null) }
    var creating by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }

    if (!s.anySupplements) {
        EmptyCard(
            "Supplements", C.Health, "Build your stack",
            "Add what you take and when — several times a day or different on weekends is fine. Tick doses off in one tap.",
            "Add supplement", { creating = true }, "Use a starter stack", vm::addStarterStack,
        )
    } else BodySection(Modifier.animateContentSize(tween(220))) {
        val p = s.progress
        BodySectionHeader(
            Ic.Pill, "Supplements",
            value = if (p.scheduled == 0) "nothing planned" else "${p.taken}/${p.scheduled} doses · ${p.pct}%",
            onAdd = { creating = true },
        ) { if (s.isToday && s.streak > 0) SmallChip("${s.streak}-day streak", Color(0xFF1E1B15), C.Fuel, Ic.Flame) }

        // ---- doses
        if (s.groups.isEmpty()) {
            Text(
                "Nothing scheduled ${if (s.isToday) "today" else "this day"}. Each supplement has its own days — change them in Manage.",
                style = T.caption, color = C.InkMuted,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                s.groups.forEachIndexed { i, g ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
                    GroupBlock(g, s.selected, s.today, s.now, vm, onEdit = { editing = it }, onDetails = { nav.navigate(BodyRoutes.supplement(it.id)) })
                }
            }
            when {
                s.selected < s.today -> Text("Fixing a past day — taps update that day's record.", style = T.caption, color = C.InkFaint)
                s.isFuture -> Text("Planned — you can tick these on the day.", style = T.caption, color = C.InkFaint)
            }
        }
        GhostButton("Manage supplements", Modifier.fillMaxWidth(), height = 40.dp) { manage = true }
    }

    if (creating || editing != null) {
        val e = editing
        SupplementEditor(
            e, rules.filter { e != null && it.supplementId == e.id },
            onDismiss = { creating = false; editing = null },
            onSave = vm::save,
            onArchive = if (e != null) vm::archive else null,
        )
    }
    if (manage) ManageSheet(vm, rules, onDismiss = { manage = false }, onEdit = { manage = false; editing = it }, onAdd = { manage = false; creating = true })
}

/** Shared day picker for the Health segment — thin adapter over the common [CalendarBar]. */
@Composable
fun BodyDateBar(s: HealthTab, vm: BodyHealthVM) {
    fun cell(d: CalDay) = com.lifeapi.app.ui.screens.body.DateCell(
        day = d.day, n = d.n, isToday = d.isToday, future = d.future, selected = d.selected,
        frac = if (d.p.scheduled == 0) null else d.p.taken.toFloat() / d.p.scheduled,
        skipFrac = if (d.p.scheduled == 0) 0f else d.p.skipped.toFloat() / d.p.scheduled,
        perfect = d.p.closed && d.p.taken > 0,
    )
    com.lifeapi.app.ui.screens.body.CalendarBar(
        mode = s.calMode, label = s.calLabel,
        cells = (if (s.calMode == 0) s.week else s.month).map(::cell),
        selectedLabel = s.selectedLabel, isToday = s.isToday, theme = com.lifeapi.app.ui.screens.body.HealthCal,
        onPage = vm::page, onMode = vm::setCalMode, onSelect = vm::select, onToday = vm::selectToday,
    )
}

@Composable
private fun ManageSheet(vm: BodyHealthVM, rules: List<SuppRule>, onDismiss: () -> Unit, onEdit: (Supplement) -> Unit, onAdd: () -> Unit) {
    val all by remember { vm.allSupplements }.collectAsStateWithLifecycle(emptyList())
    var selecting by remember { mutableStateOf(false) }
    val picked = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    val chosen = all.filter { it.id in picked }
    EntrySheet(
        "Your supplements", onDismiss,
        confirm = if (selecting) (if (chosen.isEmpty()) "Select to delete" else "Delete ${chosen.size}") else "+ Add supplement",
        hue = if (selecting) C.Negative else C.Health,
        canConfirm = !selecting || chosen.isNotEmpty(),
        onConfirm = {
            if (selecting) { vm.archiveMany(chosen); picked.clear(); selecting = false } else onAdd()
        },
        subtitle = if (selecting) "${chosen.size} selected · history is kept, Undo restores" else "Tap to edit · 🗑 deletes · history stays",
    ) {
        if (all.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${all.size} active", style = T.caption, color = C.InkSubtle, modifier = Modifier.weight(1f))
            if (selecting) Text(
                if (chosen.size == all.size) "Clear all" else "Select all", style = T.smallB, color = C.Health,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).press {
                    if (chosen.size == all.size) picked.clear() else { picked.clear(); picked.addAll(all.map { it.id }) }
                }.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Text(
                if (selecting) "Done" else "Select", style = T.smallB, color = if (selecting) C.InkMuted else C.Health,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).press { selecting = !selecting; picked.clear() }.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        all.forEach { sp ->
            val on = sp.id in picked
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (on) C.Negative.copy(alpha = 0.10f) else C.Graphite2)
                    .then(if (on) Modifier.border(1.dp, C.Negative.copy(alpha = 0.5f), RoundedCornerShape(14.dp)) else Modifier)
                    .press(onLongClick = { if (!selecting) { selecting = true; picked.add(sp.id) } }) {
                        if (selecting) { if (on) picked.remove(sp.id) else picked.add(sp.id) } else onEdit(sp)
                    }
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selecting) Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (on) C.Negative else Color.Transparent)
                        .then(if (!on) Modifier.border(1.5.dp, C.Ghost, RoundedCornerShape(6.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { if (on) Icon(Ic.Check, null, tint = C.Graphite0, modifier = Modifier.size(14.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(sp.name, style = T.body, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        SuppSchedule.summary(sp, rules) + " · ${SuppSchedule.perWeek(sp, rules)}/wk" + (sp.stock?.let { " · $it left" } ?: ""),
                        style = T.caption, color = C.InkSubtle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!selecting) RoundIconButton(Ic.Trash, "Delete ${sp.name}", size = 36.dp, bg = C.Track, tint = C.Negative) { vm.archiveMany(listOf(sp)) }
            }
        }
    }
}
