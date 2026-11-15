package com.lifeapi.app.ui.screens.grow.growth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Achievement
import com.lifeapi.app.data.LearningEntry
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.SkillTopic
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.CheckCircle
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.SectionHeader
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow3.growth.SkillState
import com.lifeapi.app.vm.grow3.growth.SkillVM

/** grow/growth/skill/{id}: goal, topics & practice, notes, progress (journal), achievements, linked projects/materials. */
@Composable
fun SkillScreen(id: Long) {
    val vm: SkillVM = viewModel(key = "skill-$id")
    LaunchedEffect(id) { vm.bind(id) }
    val st by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var editSkill by remember { mutableStateOf(false) }
    var topicSheet by remember { mutableStateOf<Pair<SkillTopic?, String>?>(null) }
    var entrySheet by remember { mutableStateOf<LearningEntry?>(null) }
    var newEntry by remember { mutableStateOf(false) }
    var achSheet by remember { mutableStateOf<Achievement?>(null) }
    var newAch by remember { mutableStateOf(false) }

    ScreenList(glow = C.Grow, bottomExtra = false) {
        val s = st
        item(key = "top") {
            GrowthTop(s?.skill?.name ?: "Direction", s?.skill?.let { listOf(it.status, "${s.entries.size} journal entries").joinToString(" · ") }, { nav.popBackStack() }) {
                if (s != null) RoundIconButton(Ic.Edit, "Edit direction", size = 40.dp) { editSkill = true }
            }
        }
        if (s == null) return@ScreenList
        item(key = "goal") { GoalCard(s) { editSkill = true } }
        item(key = "add") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Journal entry", C.Grow, C.Graphite0, Modifier.weight(1f), 44.dp, 14.dp, icon = Ic.Plus) { newEntry = true }
                GhostButton("Achievement", Modifier.weight(1f), height = 44.dp) { newAch = true }
            }
        }
        item(key = "topics") { TopicsCard(s, "Topic", "Topics to study", vm::toggleTopic, { topicSheet = it to "Topic" }) { topicSheet = null to "Topic" } }
        item(key = "practice") { TopicsCard(s, "Practice", "Practice tasks", vm::toggleTopic, { topicSheet = it to "Practice" }) { topicSheet = null to "Practice" } }
        if (s.projects.isNotEmpty() || s.materials.isNotEmpty()) item(key = "links") {
            LifeCard(Modifier.fillMaxWidth(), gap = 10.dp) {
                if (s.projects.isNotEmpty()) {
                    Text("Applied in", style = T.caption, color = C.InkMuted)
                    ChipFlow { s.projects.forEach { p -> LinkChip(p.name, C.hue(p.hue)) { nav.navigate("grow/projects/project/${p.id}") } } }
                }
                if (s.materials.isNotEmpty()) {
                    Text("Materials", style = T.caption, color = C.InkMuted)
                    ChipFlow { s.materials.forEach { m -> LinkChip("${m.kind} · ${m.title.take(30)}", C.Grow) { nav.navigate("grow/learn/material/${m.id}") } } }
                }
            }
        }
        item(key = "ach") {
            LifeCard(Modifier.fillMaxWidth(), gap = 6.dp) {
                SectionHeader("Achievements", "+ Add", C.Grow) { newAch = true }
                if (s.achievements.isEmpty()) Text("Concrete results — “Fixed a deadlock with SKIP LOCKED”. They show up in reports.", style = T.small, color = C.InkMuted)
                s.achievements.forEach { a ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).press { achSheet = a }.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(a.title, style = T.body, color = C.Ink)
                        Text(listOfNotNull(dayLabel(a.day), s.refs.projectName(a.projectId), a.detail.takeIf { it.isNotBlank() }?.take(60)).joinToString(" · "), style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item(key = "journal") {
            LifeCard(Modifier.fillMaxWidth(), gap = 4.dp) {
                SectionHeader("Progress notes", if (s.entries.isEmpty()) null else "${s.entries.size}")
                if (s.entries.isEmpty()) Text("Write what you read, tried and understood — one line is enough.", style = T.small, color = C.InkMuted)
                s.entries.take(30).forEach { e -> JournalRow(e.copy(skillId = null), s.refs, Days.today()) { entrySheet = e } }
                if (s.entries.size > 30) Text("Older entries are in the journal", style = T.caption, color = C.InkSubtle)
            }
        }
    }

    val s = st ?: return
    if (editSkill) SkillSheet(s.skill, { editSkill = false }, onSave = { vm.saveSkill(it) }, onDelete = { sk -> vm.deleteSkill(sk) { nav.popBackStack() } })
    topicSheet?.let { (t, kind) -> TopicSheet(t, s.skill.id, kind, { topicSheet = null }, vm::saveTopic, vm::deleteTopic) }
    if (newEntry || entrySheet != null) LearningEntrySheet(entrySheet, s.refs, { newEntry = false; entrySheet = null }, vm::saveEntry, vm::deleteEntry, presetSkill = s.skill.id)
    if (newAch || achSheet != null) AchievementSheet(achSheet, s.skill.id, s.refs, { newAch = false; achSheet = null }, vm::saveAchievement, vm::deleteAchievement)
}

@Composable
private fun GoalCard(s: SkillState, onEdit: () -> Unit) {
    LifeCard(Modifier.fillMaxWidth().press(onClick = onEdit), gap = 6.dp, bg = C.GrowTint.copy(alpha = 0.5f), border = C.Grow.copy(alpha = 0.25f)) {
        Text("Goal", style = T.caption, color = C.Grow)
        Text(s.skill.goal.ifBlank { "Tap to set what you want to be able to do" }, style = T.body, color = if (s.skill.goal.isBlank()) C.InkSubtle else C.Ink)
        if (s.skill.note.isNotBlank()) {
            Text("Notes", style = T.caption, color = C.InkMuted)
            Text(s.skill.note, style = T.small, color = C.InkSoft)
        }
    }
}

@Composable
private fun TopicsCard(s: SkillState, kind: String, title: String, onToggle: (SkillTopic) -> Unit, onEdit: (SkillTopic) -> Unit, onAdd: () -> Unit) {
    val list = s.topics.filter { (it.kind == "Practice") == (kind == "Practice") }
    LifeCard(Modifier.fillMaxWidth(), gap = 0.dp) {
        SectionHeader(title, (if (list.isNotEmpty()) "${list.count { it.done }}/${list.size} · " else "") + "+ Add", C.Grow, onAdd)
        if (list.isEmpty()) Text(
            if (kind == "Practice") "Hands-on tasks — “write a test with two concurrent transactions”." else "What to study — MVCC, isolation levels, row locks…",
            style = T.small, color = C.InkMuted, modifier = Modifier.padding(top = 6.dp),
        )
        list.forEach { t ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).press { onEdit(t) }, verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(t.done, C.Grow, 24.dp, square = kind == "Practice") { onToggle(t) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(t.title, style = T.body.copy(textDecoration = if (t.done) TextDecoration.LineThrough else null), color = if (t.done) C.InkSubtle else C.Ink)
                    if (t.note.isNotBlank()) Text(t.note, style = T.caption, color = C.InkSubtle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
