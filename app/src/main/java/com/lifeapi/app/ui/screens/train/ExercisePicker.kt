package com.lifeapi.app.ui.screens.train

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.train.EQUIPMENT
import com.lifeapi.app.data.train.MUSCLES
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.dashedBorder
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.train.ExercisePickerVM

/**
 * Add-exercise picker: recent first, search, muscle/equipment filters, inline create.
 * [selected] = names already in the workout/template (shown checked). [onToggle] adds (true) or removes (false).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    vm: ExercisePickerVM = viewModel(key = "exercise-picker"),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val muscle by vm.muscle.collectAsStateWithLifecycle()
    val equip by vm.equipment.collectAsStateWithLifecycle()
    val added = remember { mutableStateListOf<String>() }
    var newMuscle by remember { mutableStateOf<String?>(null) }
    var newEquip by remember { mutableStateOf("Barbell") }
    var newKind by remember { mutableIntStateOf(0) }
    val kinds = listOf("kg × reps", "reps", "time")
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { vm.query.value = ""; vm.muscle.value = null; vm.equipment.value = null }

    fun toggle(name: String) {
        val on = name in selected || name in added
        if (on) { added.remove(name); onToggle(name, false) } else { added.add(name); onToggle(name, true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.Graphite1,
        scrimColor = androidx.compose.ui.graphics.Color(0x9E050608),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Ghost)) },
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add exercise", style = T.title.copy(fontSize = 20.sp), color = C.Ink, modifier = Modifier.weight(1f))
                Text(if (added.isEmpty()) "Done" else "${added.size} added · Done", style = T.small, color = if (added.isEmpty()) C.InkSubtle else C.Train,
                    modifier = Modifier.press(onClick = onDismiss).padding(6.dp))
            }
            // search
            val focused = true
            Row(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color(0xFF0D0E10))
                    .border(1.5.dp, if (focused) C.Train else C.Hairline, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ic.Search, null, tint = C.InkSubtle, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Search or name a new one", style = T.bodyR.copy(fontSize = 16.sp), color = C.InkFaint)
                    BasicTextField(
                        q, { vm.query.value = it }, singleLine = true,
                        textStyle = T.bodyR.copy(fontSize = 16.sp, color = C.Ink),
                        cursorBrush = SolidColor(C.Train),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val s = st ?: return@KeyboardActions
                            if (s.canCreate && s.rows.isEmpty()) {
                                vm.create(s.createName, newMuscle ?: muscle ?: "", newEquip, kinds[newKind]); toggle(s.createName); vm.query.value = ""
                            }
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (q.isNotEmpty()) Icon(Ic.Close, "Clear", tint = C.InkSubtle, modifier = Modifier.size(18.dp).press { vm.query.value = "" })
            }
            // filters
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MUSCLES.forEach { m -> TrainChip(m, muscle == m, 30.dp) { vm.muscle.value = if (muscle == m) null else m } }
                Box(Modifier.width(1.dp).height(30.dp).background(C.Hairline))
                EQUIPMENT.forEach { e -> TrainChip(e, equip == e, 30.dp) { vm.equipment.value = if (equip == e) null else e } }
            }
            val s = st
            AnimatedVisibility(s?.canCreate == true) {
                val name = s?.createName ?: ""
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.WarmBg).dashedBorder(TC.CreateBorder, 16.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            buildAnnotatedString { append("Create "); withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("“$name”") } },
                            style = T.bodyR, color = C.Ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Box(
                            Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(C.Train)
                                .press(haptic = true) { vm.create(name, newMuscle ?: muscle ?: "", newEquip, kinds[newKind]); toggle(name); vm.query.value = "" }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Create & add", style = T.smallB.copy(fontWeight = FontWeight.Bold), color = C.Graphite0) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MUSCLES.forEach { m -> TrainChip(m, (newMuscle ?: muscle) == m, 30.dp) { newMuscle = m } }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EQUIPMENT.forEach { e -> TrainChip(e, newEquip == e, 30.dp) { newEquip = e } }
                        Box(Modifier.width(1.dp).height(30.dp).background(C.Hairline))
                        kinds.forEachIndexed { i, k -> TrainChip(k, newKind == i, 30.dp) { newKind = i } }
                    }
                }
            }
            if (s != null) {
                MonoLabel(s.title, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
                if (s.rows.isEmpty()) {
                    Text(if (s.canCreate) "Nothing in your library matches — create it above and it's saved for next time." else "No exercises for this filter.",
                        style = T.small, color = C.InkSubtle, modifier = Modifier.padding(horizontal = 2.dp))
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f, fill = false).clip(RoundedCornerShape(16.dp)).border(1.dp, C.Hairline, RoundedCornerShape(16.dp)),
                    ) {
                        itemsIndexed(s.rows, key = { _, r -> r.name }) { i, r ->
                            val on = r.name in selected || r.name in added
                            DlRow(first = i == 0, modifier = Modifier.press { toggle(r.name) }) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(r.name, style = T.body, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(r.meta, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                val bg by animateColorAsState(if (on) C.Train else C.TrainTint, label = "add")
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(bg).press(haptic = true) { toggle(r.name) }, contentAlignment = Alignment.Center) {
                                    Icon(if (on) Ic.Check else Ic.Plus, if (on) "Remove ${r.name}" else "Add ${r.name}", tint = if (on) C.Graphite0 else C.Train, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
