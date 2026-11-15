package com.lifeapi.app.ui.screens.grow.growth

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.LearningEntry
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow3.growth.AddLearningVM
import com.lifeapi.app.vm.grow3.growth.JournalVM

/** grow/growth/journal: every learning entry grouped by day, filter by direction, tap = edit/delete (with Undo). */
@Composable
fun JournalScreen() {
    val vm: JournalVM = viewModel()
    val st by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var edit by remember { mutableStateOf<LearningEntry?>(null) }
    var add by remember { mutableStateOf(false) }
    val today = Days.today()

    ScreenList(glow = C.Grow, bottomExtra = false) {
        item(key = "top") {
            GrowthTop("Learning journal", st?.first?.sumOf { it.entries.size }?.let { "$it entries" }, { nav.popBackStack() }) {
                RoundIconButton(Ic.Plus, "New entry", size = 40.dp, bg = C.GrowTint, tint = C.Grow) { add = true }
            }
        }
        val s = st ?: return@ScreenList
        val (days, filter, refs) = s
        if (refs.skills.isNotEmpty()) item(key = "filter") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip("All", filter == null) { vm.filter.value = null }
                refs.skills.forEach { sk -> FilterChip(sk.name, filter == sk.id) { vm.filter.value = if (filter == sk.id) null else sk.id } }
            }
        }
        if (days.isEmpty()) item(key = "empty") {
            EmptyCard("Journal", C.Grow, "Nothing here yet", "Write one line after you read, watch or try something. Add details only when they help.", "New entry", { add = true })
        }
        days.forEach { d ->
            item(key = "d-${d.day}") {
                LifeCard(Modifier.fillMaxWidth(), gap = 2.dp) {
                    MonoLabel(dayLabel(d.day, today) + " · ${d.entries.size}", C.Grow)
                    d.entries.forEach { e -> JournalRow(e, refs, today, showDay = false) { edit = e } }
                }
            }
        }
    }
    val r = st?.third ?: return
    if (add || edit != null) LearningEntrySheet(edit, r, { add = false; edit = null }, vm::saveEntry, vm::deleteEntry, presetSkill = st?.second)
}

@Composable
private fun FilterChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) C.Grow else C.Graphite1).press(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = if (on) C.Graphite0 else C.InkSoft, maxLines = 1) }
}

/**
 * grow/growth/add?material=&skill=&text= — opens the journal entry sheet prefilled (used by Learn → "Add to Growth").
 * Closing or saving returns to where you came from.
 */
@Composable
fun AddLearningScreen(materialId: Long?, skillId: Long?, text: String) {
    val vm: AddLearningVM = viewModel()
    val refs by vm.refsState.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var open by remember { mutableStateOf(true) }
    LaunchedEffect(open) { if (!open) nav.popBackStack() }
    Box(Modifier.fillMaxSize().background(C.Graphite0))
    val r = refs ?: return
    if (open) LearningEntrySheet(null, r, { open = false }, vm::saveEntry, presetSkill = skillId, presetMaterial = materialId, presetText = text)
}
