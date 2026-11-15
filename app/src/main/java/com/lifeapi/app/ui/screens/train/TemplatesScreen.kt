package com.lifeapi.app.ui.screens.train

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.TemplateExercise
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.data.train.daysLabel
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.NameSheet
import com.lifeapi.app.ui.components.dashedBorder
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.TemplateWithEx
import com.lifeapi.app.vm.TrainVM
import java.time.format.TextStyle
import java.util.Locale

fun templateMeta(t: TemplateWithEx): String {
    val sched = if (t.t.days != 0) daysLabel(t.t.days) else null
    val last = t.lastDay?.let { d ->
        val dt = Days.date(d)
        "last used ${dt.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${dt.dayOfMonth} ${dt.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
    } ?: "${t.ex.size} exercises · ${t.ex.sumOf { it.sets }} sets"
    return listOfNotNull(if (t.today) "Today" else null, sched, last).joinToString(" · ")
}

@Composable
fun TemplatesScreen(onBack: () -> Unit, onOpenWorkout: (Long) -> Unit, vm: TrainVM = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val st = s ?: return
    var editing by remember { mutableStateOf<TemplateWithEx?>(null) }
    var creating by remember { mutableStateOf(false) }
    var savingLast by remember { mutableStateOf(false) }
    val highlight = st.next?.t?.id

    ScreenList(glow = null, bottomExtra = false) {
        item {
            TrainTopBar("Templates", onBack) { TrainCircleButton(Ic.Plus, "New template") { creating = true } }
        }
        if (st.active != null) {
            item {
                Text("A workout is in progress — finish it before starting another.", style = T.small, color = C.InkMuted,
                    modifier = Modifier.press { onOpenWorkout(st.active.id) })
            }
        }
        items(st.templates, key = { it.t.id }) { t ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Graphite1).border(1.dp, C.Hairline, RoundedCornerShape(20.dp))
                    .press { editing = t }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(t.t.name, style = T.section.copy(fontSize = 16.sp), color = C.Ink)
                        Text(templateMeta(t), style = T.caption, color = if (t.today) C.Train else C.InkSubtle)
                    }
                    val hi = t.t.id == highlight && st.active == null
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(if (hi) C.Train else C.Graphite3)
                            .press(haptic = true) { if (st.active != null) onOpenWorkout(st.active.id) else vm.start(t.t, onOpenWorkout) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Start", style = T.smallB, color = if (hi) C.Graphite0 else C.Ink) }
                }
                if (t.ex.isNotEmpty()) Text(t.ex.joinToString(", ") { it.name }, style = T.small.copy(lineHeight = 19.sp), color = C.InkMuted)
            }
        }
        if (st.templates.isEmpty()) {
            item {
                Text("No templates yet. Create one, or save your last workout as a template.", style = T.small, color = C.InkMuted)
            }
        }
        if (st.lastFinished != null) {
            item {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).dashedBorder(C.Bar, 16.dp).press { savingLast = true },
                    contentAlignment = Alignment.Center,
                ) { Text("Save last workout as template", style = T.body, color = C.InkMuted) }
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, C.Bar, RoundedCornerShape(16.dp))
                    .press { if (st.active != null) onOpenWorkout(st.active.id) else vm.start(null, onOpenWorkout) },
                contentAlignment = Alignment.Center,
            ) { Text("Start empty workout", style = T.body, color = C.Ink) }
        }
    }

    if (creating || editing != null) {
        val e = editing
        TemplateEditSheet(e, { creating = false; editing = null },
            onSave = { name, ex, days -> vm.saveTemplate(e?.t?.id, name, ex, days) },
            onDelete = e?.let { { vm.deleteTemplate(it.t.id) } })
    }
    if (savingLast) {
        val ln = st.lastFinished?.name ?: "Workout"
        NameSheet("Save as template", "Template name", "Push A", C.Train,
            initial = if (st.templates.any { it.t.name == ln }) "$ln (new)" else ln,
            onDismiss = { savingLast = false }) { vm.saveLastAsTemplate(it) }
    }
}

private class Draft(name: String, sets: String, reps: String, kg: String) {
    var name by mutableStateOf(name); var sets by mutableStateOf(sets); var reps by mutableStateOf(reps); var kg by mutableStateOf(kg)
}

private fun String.num(): Double? = replace(',', '.').trim().toDoubleOrNull()

/** Template editor: name, schedule weekdays, exercises picked from the library. */
@Composable
fun TemplateEditSheet(
    initial: TemplateWithEx?,
    onDismiss: () -> Unit,
    onSave: (String, List<TemplateExercise>, Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.t?.name ?: "") }
    var days by remember { mutableIntStateOf(initial?.t?.days ?: 0) }
    var picking by remember { mutableStateOf(false) }
    val list = remember {
        mutableStateListOf<Draft>().apply { initial?.ex?.forEach { add(Draft(it.name, it.sets.toString(), it.reps.toString(), fmtKg(it.kg))) } }
    }
    FormSheet(
        if (initial == null) "New template" else "Edit template", onDismiss, confirmColor = C.Train,
        canConfirm = name.isNotBlank() && list.isNotEmpty(),
        onConfirm = {
            onSave(name.trim(), list.map {
                TemplateExercise(templateId = 0, name = it.name, sets = it.sets.num()?.toInt()?.coerceAtLeast(1) ?: 3, reps = it.reps.num()?.toInt()?.coerceAtLeast(1) ?: 8, kg = it.kg.num() ?: 0.0)
            }, days); onDismiss()
        },
        onDelete = onDelete?.let { { it(); onDismiss() } },
    ) {
        LifeField(name, { name = it }, "Template name", placeholder = "Push A")
        Text("Schedule", style = T.caption, color = C.InkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, l ->
                val on = days and (1 shl i) != 0
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.Train else C.Graphite2)
                        .press(haptic = true) { days = days xor (1 shl i) },
                    contentAlignment = Alignment.Center,
                ) { Text(l, style = T.smallB, color = if (on) C.Graphite0 else C.InkMuted) }
            }
        }
        list.forEachIndexed { i, e ->
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(C.Graphite2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(e.name, style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink, modifier = Modifier.weight(1f))
                    if (i > 0) Text("↑", style = T.body, color = C.InkSubtle, modifier = Modifier.press { list.add(i - 1, list.removeAt(i)) }.padding(6.dp))
                    Text("Remove", style = T.caption, color = C.Negative, modifier = Modifier.press { list.removeAt(i) }.padding(4.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeField(e.sets, { e.sets = it }, "Sets", Modifier.weight(1f), KeyboardType.Number)
                    LifeField(e.reps, { e.reps = it }, "Reps", Modifier.weight(1f), KeyboardType.Number)
                    LifeField(e.kg, { e.kg = it }, "Kg", Modifier.weight(1f), KeyboardType.Decimal)
                }
            }
        }
        GhostButton("+ Add exercise", Modifier.fillMaxWidth(), color = C.Train) { picking = true }
    }
    if (picking) {
        ExercisePickerSheet(
            selected = list.map { it.name }.toSet(),
            onDismiss = { picking = false },
            onToggle = { n, on -> if (on) { if (list.none { it.name == n }) list.add(Draft(n, "3", "8", "")) } else list.removeAll { it.name == n } },
        )
    }
}

