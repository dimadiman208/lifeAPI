package com.lifeapi.app.ui.screens.grow.growth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifeapi.app.data.Achievement
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.LearningEntry
import com.lifeapi.app.data.Skill
import com.lifeapi.app.data.SkillTopic
import com.lifeapi.app.ui.components.ChipPicker
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.body.EntrySheet
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow3.growth.GrowthRefs
import java.time.format.TextStyle
import java.util.Locale

// ----------------------------------------------------------------- small shared bits

internal fun dayLabel(day: Long, today: Long = Days.today()): String {
    val d = Days.date(day)
    val base = "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
    return when (today - day) { 0L -> "Today · $base"; 1L -> "Yesterday · $base"; else -> base }
}

internal fun fmtMin(m: Int): String = when {
    m <= 0 -> "0m"
    m < 60 -> "${m}m"
    m % 60 == 0 -> "${m / 60}h"
    else -> "${m / 60}h ${m % 60}m"
}

/** Tappable link chip (project / material / skill). */
@Composable
internal fun LinkChip(text: String, hue: Color, onClick: (() -> Unit)?) {
    Box(
        Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(hue.copy(alpha = 0.12f))
            .then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.caption, color = hue, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** Optional single-choice picker: "None" + options. */
@Composable
private fun OptionalPicker(label: String, options: List<Pair<Long, String>>, selected: Long?, hue: Color, onPick: (Long?) -> Unit) {
    if (options.isEmpty()) return
    Text(label, style = T.caption, color = C.InkMuted)
    val none = "None"
    val names = options.map { it.second }
    ChipPicker(listOf(none) + names, options.firstOrNull { it.first == selected }?.second ?: none, hue) { pick ->
        onPick(options.firstOrNull { it.second == pick }?.first)
    }
}

@Composable
private fun DayStepper(day: Long, onChange: (Long) -> Unit) {
    val today = Days.today()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundIconButton(Ic.ChevronLeft, "Previous day", size = 36.dp, bg = C.Track) { onChange(day - 1) }
        Text(dayLabel(day, today), style = T.body, color = C.Ink, modifier = Modifier.weight(1f))
        if (day < today) RoundIconButton(Ic.ChevronRight, "Next day", size = 36.dp, bg = C.Track) { onChange(day + 1) }
    }
}

// ----------------------------------------------------------------- learning entry

/**
 * Journal entry. Only "What did you learn?" is needed — a quick thought; the rest is optional.
 * Prefills (skill, material, text) come from the quick bar, the skill screen or Learn → "Add to Growth".
 */
@Composable
fun LearningEntrySheet(
    initial: LearningEntry?,
    refs: GrowthRefs,
    onDismiss: () -> Unit,
    onSave: (LearningEntry) -> Unit,
    onDelete: ((LearningEntry) -> Unit)? = null,
    presetSkill: Long? = null,
    presetMaterial: Long? = null,
    presetText: String = "",
) {
    var skill by remember { mutableStateOf(initial?.skillId ?: presetSkill ?: refs.materials.firstOrNull { it.id == presetMaterial }?.skillId) }
    var material by remember { mutableStateOf(initial?.materialId ?: presetMaterial) }
    var project by remember { mutableStateOf(initial?.projectId) }
    var day by remember { mutableLongStateOf(initial?.day ?: Days.today()) }
    var learned by remember { mutableStateOf(initial?.learned ?: presetText) }
    var practiced by remember { mutableStateOf(initial?.practiced ?: "") }
    var understood by remember { mutableStateOf(initial?.understood ?: "") }
    var unclear by remember { mutableStateOf(initial?.unclear ?: "") }
    var minutes by remember { mutableStateOf(initial?.minutes?.toString() ?: "") }
    val min = minutes.trim().toIntOrNull()
    val any = listOf(learned, practiced, understood, unclear).any { it.isNotBlank() }
    EntrySheet(
        if (initial == null) "Learning journal" else "Edit entry", onDismiss,
        confirm = if (initial == null) "Add to journal" else "Save", hue = C.Grow,
        canConfirm = any && (minutes.isBlank() || (min != null && min in 1..1440)),
        subtitle = "Only the first line is needed",
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial); onDismiss() }) else null,
        onConfirm = {
            onSave(
                (initial ?: LearningEntry(day = day, ts = 0)).copy(
                    skillId = skill, materialId = material, projectId = project, day = day,
                    learned = learned.trim(), practiced = practiced.trim(), understood = understood.trim(), unclear = unclear.trim(),
                    minutes = min,
                ),
            )
            onDismiss()
        },
    ) {
        LifeField(learned, { learned = it }, "What did you learn or notice?", placeholder = "READ COMMITTED re-reads each statement", singleLine = false)
        OptionalPicker("Direction", refs.skills.filter { it.status != "Done" || it.id == skill }.map { it.id to it.name }, skill, C.Grow) { skill = it }
        LifeField(practiced, { practiced = it }, "What did you try in practice?", singleLine = false)
        LifeField(understood, { understood = it }, "What clicked?", singleLine = false)
        LifeField(unclear, { unclear = it }, "Still unclear", singleLine = false)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            LifeField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, "Minutes (optional)", Modifier.weight(1f), KeyboardType.Number)
        }
        DayStepper(day) { day = it }
        OptionalPicker("Applied in project", refs.projects.map { it.id to it.name }, project, C.Grow) { project = it }
        OptionalPicker("From material", refs.materials.sortedByDescending { it.lastOpenedTs.coerceAtLeast(it.addedTs) }.take(12).let { list ->
            val cur = refs.materials.firstOrNull { it.id == material }
            if (cur != null && list.none { it.id == cur.id }) listOf(cur) + list else list
        }.map { it.id to it.title.take(36) }, material, C.Grow) { material = it }
    }
}

// ----------------------------------------------------------------- skill (direction)

@Composable
fun SkillSheet(initial: Skill?, onDismiss: () -> Unit, onSave: (Skill) -> Unit, onDelete: ((Skill) -> Unit)? = null) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var goal by remember { mutableStateOf(initial?.goal ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: "Active") }
    EntrySheet(
        if (initial == null) "New direction" else "Edit direction", onDismiss, hue = C.Grow,
        canConfirm = name.isNotBlank(),
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial); onDismiss() }) else null,
        subtitle = if (initial == null) "Something you want to get better at" else "Deleting keeps journal entries",
        onConfirm = {
            onSave((initial ?: Skill(name = "")).copy(name = name.trim(), goal = goal.trim(), note = note.trim(), status = status))
            onDismiss()
        },
    ) {
        LifeField(name, { name = it }, "Direction", placeholder = "PostgreSQL")
        LifeField(goal, { goal = it }, "Learning goal (optional)", placeholder = "Work confidently with concurrent transactions", singleLine = false)
        LifeField(note, { note = it }, "Notes (optional)", singleLine = false)
        if (initial != null) {
            Text("Status", style = T.caption, color = C.InkMuted)
            ChipPicker(listOf("Active", "Paused", "Done"), status, C.Grow) { status = it }
        }
    }
}

// ----------------------------------------------------------------- topic

@Composable
fun TopicSheet(initial: SkillTopic?, skillId: Long, kind: String, onDismiss: () -> Unit, onSave: (SkillTopic) -> Unit, onDelete: ((SkillTopic) -> Unit)? = null) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var k by remember { mutableStateOf(initial?.kind ?: kind) }
    EntrySheet(
        if (initial == null) (if (k == "Practice") "Practice task" else "Topic") else "Edit", onDismiss, hue = C.Grow,
        canConfirm = title.isNotBlank(),
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial); onDismiss() }) else null,
        onConfirm = {
            onSave((initial ?: SkillTopic(skillId = skillId, title = "", sort = (Days.now() / 1000).toInt())).copy(title = title.trim(), note = note.trim(), kind = k))
            onDismiss()
        },
    ) {
        ChipPicker(listOf("Topic", "Practice"), k, C.Grow) { k = it }
        LifeField(title, { title = it }, if (k == "Practice") "What to do" else "What to study", placeholder = if (k == "Practice") "Write a test with two concurrent transactions" else "MVCC")
        LifeField(note, { note = it }, "Notes / knowledge (optional)", singleLine = false)
    }
}

// ----------------------------------------------------------------- achievement

@Composable
fun AchievementSheet(initial: Achievement?, skillId: Long?, refs: GrowthRefs, onDismiss: () -> Unit, onSave: (Achievement) -> Unit, onDelete: ((Achievement) -> Unit)? = null) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var detail by remember { mutableStateOf(initial?.detail ?: "") }
    var day by remember { mutableLongStateOf(initial?.day ?: Days.today()) }
    var project by remember { mutableStateOf(initial?.projectId) }
    var material by remember { mutableStateOf(initial?.materialId) }
    EntrySheet(
        if (initial == null) "Achievement" else "Edit achievement", onDismiss, hue = C.Grow,
        canConfirm = title.isNotBlank(),
        subtitle = "A concrete result — shows up in reports",
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial); onDismiss() }) else null,
        onConfirm = {
            onSave((initial ?: Achievement(day = day, title = "", skillId = skillId)).copy(title = title.trim(), detail = detail.trim(), day = day, projectId = project, materialId = material))
            onDismiss()
        },
    ) {
        LifeField(title, { title = it }, "What did you achieve?", placeholder = "Fixed a deadlock with SKIP LOCKED", singleLine = false)
        LifeField(detail, { detail = it }, "Details (optional)", singleLine = false)
        DayStepper(day) { day = it }
        OptionalPicker("Project", refs.projects.map { it.id to it.name }, project, C.Grow) { project = it }
        OptionalPicker("Material", refs.materials.take(12).map { it.id to it.title.take(36) }, material, C.Grow) { material = it }
    }
}

// ----------------------------------------------------------------- top bar for detail screens

@Composable
internal fun GrowthTop(title: String, sub: String?, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundIconButton(Ic.ChevronLeft, "Back", onClick = onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = T.title, color = C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
internal fun Gap(h: Int = 4) = Spacer(Modifier.height(h.dp).width(1.dp))

@Composable
internal fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = C.InkSubtle) =
    Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
