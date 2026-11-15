package com.lifeapi.app.ui.screens.grow.growth

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Habit
import com.lifeapi.app.data.LearningEntry
import com.lifeapi.app.data.grow3.GrowthStats
import com.lifeapi.app.data.grow3.growth.GrowthViews
import com.lifeapi.app.data.grow3.growth.SkillCard
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.HabitSheet
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.SectionHeader
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.screens.grow.LegacyHabitsCard
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.GrowVM
import com.lifeapi.app.vm.grow3.growth.GrowthRefs
import com.lifeapi.app.vm.grow3.growth.GrowthTabState
import com.lifeapi.app.vm.grow3.growth.GrowthTabVM

/**
 * Grow → Growth: "What am I getting better at?" — quick journal, directions (skills), month summary,
 * what to review next, recent journal, and the habits card (Today and widgets still use habits).
 */
@Composable
fun GrowthTab(header: LazyListScope.(subtitle: String) -> Unit) {
    val vm: GrowthTabVM = viewModel()
    val st by vm.state.collectAsStateWithLifecycle()
    val growVm: GrowVM = viewModel()
    val legacy by growVm.state.collectAsStateWithLifecycle()
    val nav = LocalNav.current

    var newSkill by remember { mutableStateOf(false) }
    var entrySheet by remember { mutableStateOf<Pair<LearningEntry?, Pair<String, Long?>>?>(null) } // (editing, (prefill text, skill))
    var habitEdit by remember { mutableStateOf<Habit?>(null) }
    var habitNew by remember { mutableStateOf(false) }

    ScreenList(glow = C.Grow) {
        header("What am I getting better at?")
        val s = st
        if (s == null) return@ScreenList

        item(key = "quick") {
            QuickJournal(s.refs, onAdd = { text, skill -> vm.saveEntry(LearningEntry(skillId = skill, day = s.today, ts = 0, learned = text)) },
                onMore = { text, skill -> entrySheet = null to (text to skill) })
        }

        item(key = "dirs-h") {
            SectionHeader("Directions", "+ Direction", C.Grow) { newSkill = true }
        }
        if (s.cards.isEmpty()) {
            item(key = "dirs-empty") {
                EmptyCard(
                    "Growth", C.Grow, "What do you want to get better at?",
                    "Add a direction — PostgreSQL, System Design, English. Then collect topics, practice and what you learned along the way.",
                    "Add direction", { newSkill = true },
                )
            }
        } else {
            s.cards.forEach { c -> item(key = "skill-${c.skill.id}") { SkillCardRow(c, s.today) { nav.navigate(GrowthRoute.skill(c.skill.id)) } } }
        }

        item(key = "month") { MonthCard(s) }

        if (s.nextUp.isNotEmpty() || s.stale.isNotEmpty()) item(key = "next") { ReviewCard(s) { id -> id?.let { nav.navigate(GrowthRoute.skill(it)) } } }

        item(key = "recent") {
            LifeCard(Modifier.fillMaxWidth(), gap = 8.dp) {
                SectionHeader("Learning journal", if (s.recent.isEmpty()) null else "All ›", C.Grow) { nav.navigate(GrowthRoute.JOURNAL) }
                if (s.recent.isEmpty()) Text("Nothing yet — write one line above after you read, watch or try something.", style = T.small, color = C.InkMuted)
                s.recent.forEach { e -> JournalRow(e, s.refs, s.today) { entrySheet = e to ("" to null) } }
            }
        }

        item(key = "habits") {
            val l = legacy
            if (l != null) LegacyHabitsCard(l, growVm, onAdd = { habitNew = true }, onEdit = { habitEdit = it })
        }
    }

    val r = st?.refs
    if (newSkill) SkillSheet(null, { newSkill = false }, onSave = { sk -> vm.saveSkill(sk) { id -> nav.navigate(GrowthRoute.skill(id)) } })
    entrySheet?.let { (e, pre) ->
        if (r != null) LearningEntrySheet(e, r, { entrySheet = null }, vm::saveEntry, vm::deleteEntry, presetSkill = pre.second, presetText = pre.first)
    }
    if (habitNew || habitEdit != null) {
        HabitSheet(habitEdit, onDismiss = { habitNew = false; habitEdit = null }, onSave = growVm::saveHabit, onDelete = habitEdit?.let { { h: Habit -> growVm.archiveHabit(h) } })
    }
}

// ----------------------------------------------------------------- quick journal

@Composable
private fun QuickJournal(refs: GrowthRefs, onAdd: (String, Long?) -> Unit, onMore: (String, Long?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var skill by remember { mutableStateOf<Long?>(null) }
    val active = refs.skills.filter { it.status == "Active" }
    LifeCard(Modifier.fillMaxWidth(), gap = 10.dp, bg = C.GrowTint.copy(alpha = 0.55f), border = C.Grow.copy(alpha = 0.25f)) {
        MonoLabel("Quick journal", C.Grow)
        LifeField(text, { text = it }, "What did you learn or notice?", placeholder = "FOR UPDATE blocks only conflicting rows", singleLine = false)
        if (active.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            active.forEach { sk ->
                val on = skill == sk.id
                Box(
                    Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (on) C.Grow else C.Graphite2)
                        .press { skill = if (on) null else sk.id }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(sk.name, style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = if (on) C.Graphite0 else C.InkSoft, maxLines = 1) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("More fields", Modifier.weight(1f), height = 42.dp) { onMore(text.trim(), skill); text = "" }
            PillButton("Add", C.Grow, C.Graphite0, Modifier.weight(1f), 42.dp, 12.dp, icon = Ic.Plus, enabled = text.isNotBlank()) {
                onAdd(text.trim(), skill); text = ""
            }
        }
    }
}

// ----------------------------------------------------------------- skill card

@Composable
private fun SkillCardRow(c: SkillCard, today: Long, onOpen: () -> Unit) {
    LifeCard(Modifier.fillMaxWidth().press(onClick = onOpen), gap = 6.dp, padding = PaddingValues(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.skill.name, style = T.section, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (c.skill.status == "Paused") LinkChip("Paused", C.InkSubtle, null)
                }
                if (c.skill.goal.isNotBlank()) Text(c.skill.goal, style = T.small, color = C.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            SmallIcon(Ic.ChevronRight)
        }
        val parts = buildList {
            if (c.topicsTotal > 0) add("${c.topicsDone}/${c.topicsTotal} topics")
            add(if (c.entriesThisMonth == 0) "no entries this month" else "${c.entriesThisMonth} entr${if (c.entriesThisMonth == 1) "y" else "ies"} this month")
            if (c.minutesThisMonth > 0) add(fmtMin(c.minutesThisMonth))
            c.lastEntryDay?.let { add(if (it == today) "last today" else "last ${today - it}d ago") }
        }
        Text(parts.joinToString(" · "), style = T.caption, color = C.InkSubtle)
    }
}

// ----------------------------------------------------------------- month summary

@Composable
private fun MonthCard(s: GrowthTabState) {
    val m = s.month; val p = s.prevMonth
    LifeCard(Modifier.fillMaxWidth(), gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.monthLabel, style = T.section, color = C.Ink, modifier = Modifier.weight(1f))
            Text("vs last month", style = T.caption, color = C.InkSubtle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Journal", m.entries.toString(), delta(m.entries, p.entries), Modifier.weight(1f))
            Stat("Time", if (m.learnedMinutes > 0) fmtMin(m.learnedMinutes) else "—", deltaMin(m, p), Modifier.weight(1f))
            Stat("Active days", m.activeDays.toString(), delta(m.activeDays, p.activeDays), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Topics studied", m.topicsDone.toString(), delta(m.topicsDone, p.topicsDone), Modifier.weight(1f))
            Stat("Practice done", m.practiceDone.toString(), delta(m.practiceDone, p.practiceDone), Modifier.weight(1f))
            Stat("Achievements", m.achievements.toString(), delta(m.achievements, p.achievements), Modifier.weight(1f))
        }
        Text("${m.activeSkills} active direction${if (m.activeSkills == 1) "" else "s"} with progress this month", style = T.caption, color = C.InkSubtle)
    }
}

private fun delta(a: Int, b: Int): String? = when { a == b -> null; a > b -> "+${a - b}"; else -> "−${b - a}" }
private fun deltaMin(a: GrowthStats, b: GrowthStats): String? {
    val d = a.learnedMinutes - b.learnedMinutes
    return when { d == 0 -> null; d > 0 -> "+${fmtMin(d)}"; else -> "−${fmtMin(-d)}" }
}

@Composable
private fun Stat(label: String, value: String, delta: String?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(C.Graphite2).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = T.section, color = C.Ink)
            if (delta != null) Text(delta, style = T.caption, color = if (delta.startsWith("+")) C.Grow else C.InkSubtle)
        }
    }
}

// ----------------------------------------------------------------- review next

@Composable
private fun ReviewCard(s: GrowthTabState, onSkill: (Long?) -> Unit) {
    LifeCard(Modifier.fillMaxWidth(), gap = 8.dp) {
        SectionHeader("Review / learn next")
        s.nextUp.forEach { n ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).press { onSkill(n.skillId) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(n.title, style = T.body, color = C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(s.refs.skillName(n.skillId), n.reason).joinToString(" · "), style = T.caption, color = C.InkSubtle)
                }
            }
        }
        s.stale.take(3).forEach { st ->
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).press { onSkill(st.skill.id) }, verticalAlignment = Alignment.CenterVertically) {
                Text(st.skill.name, style = T.body, color = C.Ink, modifier = Modifier.weight(1f))
                Text(st.daysSince?.let { "no entries for $it days" } ?: "no entries yet", style = T.caption, color = C.Fuel)
            }
        }
    }
}

// ----------------------------------------------------------------- journal row

@Composable
internal fun JournalRow(e: LearningEntry, refs: GrowthRefs, today: Long, showDay: Boolean = true, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).press(onClick = onEdit).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 7.dp).size(7.dp).clip(RoundedCornerShape(4.dp)).background(if (GrowthViews.isQuick(e)) C.Grow.copy(alpha = 0.6f) else C.Grow))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(GrowthViews.headline(e), style = T.body, color = C.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (e.practiced.isNotBlank() && e.learned.isNotBlank()) Text("Tried: ${e.practiced}", style = T.small, color = C.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.unclear.isNotBlank()) Text("Unclear: ${e.unclear}", style = T.small, color = C.Fuel, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                if (showDay) dayLabel(e.day, today) else null,
                refs.skillName(e.skillId), refs.projectName(e.projectId), refs.materialTitle(e.materialId)?.take(28),
                e.minutes?.let { fmtMin(it) },
            )
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
