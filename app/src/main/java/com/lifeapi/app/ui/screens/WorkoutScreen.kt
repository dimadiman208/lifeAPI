package com.lifeapi.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.WorkoutSet
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.train.openWorkoutSummary
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.ExBlock
import com.lifeapi.app.vm.WorkoutVM

/**
 * Open workout. No timers, no "complete set": each exercise shows its logged sets as editable rows
 * (kg / reps with ± steppers), "+ Add set" copies the previous row. Finish → summary → Save.
 */
@Composable
fun WorkoutScreen(sessionId: Long, onBack: () -> Unit, vm: WorkoutVM = viewModel(key = "workout-$sessionId")) {
    LaunchedEffect(sessionId) { vm.bind(sessionId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var addEx by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val nav = com.lifeapi.app.ui.LocalNav.current
    BackHandler(onBack = onBack)

    val st = s ?: return
    fun openHistory(name: String) = nav.navigate("train/exercise/${android.net.Uri.encode(name)}")

    var confirmEmptySave by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    ScreenList(glow = null, bottomExtra = false) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoundIconButton(Ic.ChevronLeft, "Back to Train", onClick = onBack)
                Column(Modifier.weight(1f).then(if (st.isEdit) Modifier.press { renaming = true } else Modifier)) {
                    if (st.isEdit) Text("Edit workout · ${editDate(st.session.day)}", style = T.caption, color = C.Train, maxLines = 1)
                    Text(st.session.name, style = T.section, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${st.blocks.size} exercise${if (st.blocks.size == 1) "" else "s"} · ${st.totalSets} set${if (st.totalSets == 1) "" else "s"}",
                        style = T.caption, color = C.InkSubtle,
                    )
                }
                if (st.isEdit) {
                    PillButton(if (confirmEmptySave) "Delete it?" else "Save", if (confirmEmptySave) C.Negative else C.Ink, C.Graphite0, height = 40.dp) {
                        when {
                            st.totalSets > 0 -> onBack() // edits are already stored
                            confirmEmptySave -> vm.deleteWorkout(onBack)
                            else -> confirmEmptySave = true
                        }
                    }
                } else {
                    PillButton("Finish", C.Ink, C.Graphite0, height = 40.dp) {
                        if (st.totalSets == 0) vm.finish(onBack) else nav.openWorkoutSummary(st.session.id)
                    }
                }
            }
        }
        if (st.isEdit) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Rename", C.Graphite2, C.Ink, Modifier.weight(1f), 40.dp, 12.dp, icon = Ic.Edit) { renaming = true }
                PillButton(editDate(st.session.day), C.Graphite2, C.Ink, Modifier.weight(1f), 40.dp, 12.dp, icon = Ic.Today) { pickDate = true }
            }
        }
        if (st.isEdit && confirmEmptySave) item {
            Text("No sets left — tap “Delete it?” to remove this workout, or add a set to keep it.", style = T.caption, color = C.InkMuted)
        }
        if (st.blocks.isEmpty()) {
            item {
                LifeCard(Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(20.dp)) {
                    MonoLabel("Empty workout", C.Train)
                    Text("Add your first exercise", style = T.title, color = C.Ink)
                    Text("Its first set appears right away — change the numbers to what you did, then add more sets.", style = T.small, color = C.InkMuted)
                    PillButton("+ Add exercise", C.Train, C.Graphite0, Modifier.fillMaxWidth(), 52.dp, 16.dp) { addEx = true }
                }
            }
        } else {
            st.blocks.forEach { b ->
                item(key = "ex_${b.name}") { ExerciseCard(b, vm, ::openHistory) }
            }
            item { PillButton("+ Add exercise", C.TrainTint, C.Train, Modifier.fillMaxWidth(), 48.dp, 14.dp) { addEx = true } }
        }
        item {
            if (st.isEdit) GhostButton(
                if (confirmDiscard) "Tap again to delete this workout" else "Delete workout", Modifier.fillMaxWidth(),
                color = if (confirmDiscard) C.Negative else C.InkSubtle,
            ) { if (confirmDiscard) vm.deleteWorkout(onBack) else confirmDiscard = true }
            else GhostButton(
                if (confirmDiscard) "Tap again to discard workout" else "Discard workout", Modifier.fillMaxWidth(),
                color = if (confirmDiscard) C.Negative else C.InkSubtle,
            ) { if (confirmDiscard) vm.discard(onBack) else confirmDiscard = true }
        }
    }

    if (renaming) com.lifeapi.app.ui.components.NameSheet(
        "Rename workout", "Name", "Push A", C.Train, initial = st.session.name,
        onDismiss = { renaming = false },
    ) { vm.rename(it); renaming = false }
    if (pickDate) WorkoutDatePicker(st.session.day, onDismiss = { pickDate = false }) { vm.moveToDay(it); pickDate = false }

    if (addEx) com.lifeapi.app.ui.screens.train.ExercisePickerSheet(
        selected = st.blocks.map { it.name }.toSet(),
        onDismiss = { addEx = false },
        onToggle = { n, on -> if (on) vm.addExercise(n) else vm.removeExercise(n) },
    )
}

private fun editDate(day: Long): String {
    val d = com.lifeapi.app.data.Days.date(day)
    return "${d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)} ${d.dayOfMonth} " +
        d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
}

/** Material date picker for moving a saved workout to another day (no future days). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDatePicker(day: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val today = com.lifeapi.app.data.Days.today()
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = day * 86_400_000L,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / 86_400_000L <= today
        },
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { state.selectedDateMillis?.let { onPick(it / 86_400_000L) } ?: onDismiss() }) {
                Text("Move", color = C.Train)
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = C.InkMuted) } },
    ) { androidx.compose.material3.DatePicker(state) }
}

@Composable
private fun ExerciseCard(b: ExBlock, vm: WorkoutVM, onHistory: (String) -> Unit) {
    LifeCard(Modifier.fillMaxWidth(), radius = 20.dp, padding = PaddingValues(start = 14.dp, end = 10.dp, top = 14.dp, bottom = 10.dp), gap = 8.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).press { onHistory(b.name) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(b.name, style = T.section.copy(fontSize = 17.sp), color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(b.target, b.last).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = T.caption, color = C.InkSubtle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            b.best?.let {
                Box(
                    Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(if (b.isPr) C.Train else C.TrainTint).padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(it, style = T.tiny.copy(fontWeight = FontWeight.SemiBold), color = if (b.isPr) C.Graphite0 else Color(0xFFFFB29C), maxLines = 1) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            MonoLabel("Set", modifier = Modifier.width(34.dp))
            MonoLabel("Kg", modifier = Modifier.weight(1.15f))
            MonoLabel("Reps", modifier = Modifier.weight(1f))
            Box(Modifier.width(36.dp))
        }
        b.sets.forEachIndexed { i, set ->
            androidx.compose.runtime.key(set.id) { SetRowEdit(i + 1, set, vm) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("+ Add set", C.Graphite2, C.Ink, Modifier.weight(1f), 40.dp, 12.dp) { vm.addSet(b) }
        }
    }
}

@Composable
private fun SetRowEdit(n: Int, set: WorkoutSet, vm: WorkoutVM) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(n.toString(), style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Train, modifier = Modifier.width(34.dp))
        NumberStepper(
            fmtKg(set.kg), Modifier.weight(1.15f), KeyboardType.Decimal,
            parse = { it.replace(',', '.').toDoubleOrNull()?.takeIf { v -> v >= 0 } },
            onValue = { vm.editSet(set, it, set.reps) },
            onMinus = { vm.editSet(set, (set.kg - 2.5).coerceAtLeast(0.0), set.reps) },
            onPlus = { vm.editSet(set, set.kg + 2.5, set.reps) },
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        NumberStepper(
            set.reps.toString(), Modifier.weight(1f), KeyboardType.Number,
            parse = { it.toIntOrNull()?.takeIf { v -> v >= 0 } },
            onValue = { vm.editSet(set, set.kg, it) },
            onMinus = { vm.editSet(set, set.kg, (set.reps - 1).coerceAtLeast(0)) },
            onPlus = { vm.editSet(set, set.kg, set.reps + 1) },
        )
        Box(Modifier.width(36.dp).height(40.dp).clip(RoundedCornerShape(10.dp)).press { vm.deleteSet(set) }, contentAlignment = Alignment.Center) {
            Icon(Ic.Close, "Delete set $n", tint = C.InkFaint, modifier = Modifier.size(16.dp))
        }
    }
}

/** − [editable number] + in one pill. Typing commits every valid value; the field re-syncs when the stored value changes. */
@Composable
private fun <V> NumberStepper(
    shown: String,
    modifier: Modifier,
    keyboard: KeyboardType,
    parse: (String) -> V?,
    onValue: (V) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    var text by remember(shown) { mutableStateOf(shown) }
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(C.Graphite2).border(1.dp, C.Hairline, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp).height(40.dp).press(haptic = true, onClick = onMinus), contentAlignment = Alignment.Center) {
            Text("−", style = T.section, color = C.InkMuted)
        }
        BasicTextField(
            value = text,
            onValueChange = { v -> text = v; parse(v)?.let(onValue) },
            singleLine = true,
            textStyle = T.body.copy(fontWeight = FontWeight.SemiBold, color = C.Ink, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            cursorBrush = SolidColor(C.Train),
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(30.dp).height(40.dp).press(haptic = true, onClick = onPlus), contentAlignment = Alignment.Center) {
            Text("+", style = T.section, color = C.InkMuted)
        }
    }
}
