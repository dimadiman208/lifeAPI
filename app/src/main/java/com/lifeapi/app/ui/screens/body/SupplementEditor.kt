package com.lifeapi.app.ui.screens.body

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.lifeapi.app.data.SuppRule
import com.lifeapi.app.data.Supplement
import com.lifeapi.app.data.supps.Dose
import com.lifeapi.app.data.supps.DoseCodec
import com.lifeapi.app.data.supps.SuppSchedule
import com.lifeapi.app.data.supps.daysText
import com.lifeapi.app.data.supps.fmtAmount
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.dashedBorder
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateListOf
import com.lifeapi.app.data.hhmm
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import java.time.LocalTime

val DOSE_UNITS = listOf("mg", "g", "IU", "caps", "tab", "drops")
private val DAY_L = listOf("M", "T", "W", "T", "F", "S", "S")

/** Splits "400 mg · with water" into amount / unit / note. Anything unparseable becomes the note. */
fun splitDose(dose: String): Triple<String, String, String> {
    val m = Regex("""^\s*([\d.,]+)\s*(mg|g|IU|iu|caps|cap|mcg)?\b\s*(?:·\s*)?(.*)$""").find(dose)
        ?: return Triple("", "mg", dose.trim())
    val unit = when (val u = m.groupValues[2]) { "" -> "mg"; "iu" -> "IU"; "cap" -> "caps"; else -> u }
    return Triple(m.groupValues[1], unit, m.groupValues[3].trim())
}

fun joinDose(amount: String, unit: String, note: String): String =
    listOfNotNull(amount.trim().takeIf { it.isNotEmpty() }?.let { "$it $unit" }, note.trim().takeIf { it.isNotEmpty() }).joinToString(" · ")

fun daysLabel(mask: Int): String = when {
    mask and 127 == 127 -> "daily"
    mask == 0b0011111 -> "weekdays"
    mask == 0b1100000 -> "weekends"
    else -> (0 until 7).filter { mask and (1 shl it) != 0 }.joinToString(" ") { listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")[it] }
}

/** Editable dose: time + amount (text while typing). */
private data class DoseDraft(val time: LocalTime, val amount: String)

/** base = the main schedule; extra rules override it on their days. */
private data class RuleDraft(val key: Int, val base: Boolean, val days: Int, val doses: List<DoseDraft>)

private var keySeq = 0

private fun draftsOf(initial: Supplement?, rules: List<SuppRule>): List<RuleDraft> {
    fun dd(r: List<Dose>) = r.map { DoseDraft(it.localTime, fmtAmount(it.amount)) }
    val mine = rules.sortedWith(compareBy({ !it.base }, { it.sort }, { it.id }))
    if (mine.isEmpty()) {
        val doses = initial?.let { SuppSchedule.legacyDoses(it) }.orEmpty().ifEmpty { listOf(Dose("08:00", 1.0, "caps")) }
        return listOf(RuleDraft(keySeq++, true, initial?.days ?: 127, dd(doses)))
    }
    val list = mine.map { RuleDraft(keySeq++, it.base, it.days and 127, dd(DoseCodec.parse(it.doses))) }
    return if (list.none { it.base }) listOf(RuleDraft(keySeq++, true, 0, list.first().doses)) + list else list
}

private fun initialUnit(initial: Supplement?, rules: List<SuppRule>): String {
    val d = rules.firstOrNull { it.base }?.let { DoseCodec.parse(it.doses).firstOrNull() }
        ?: initial?.let { SuppSchedule.legacyDoses(it).firstOrNull() }
    return d?.unit?.ifBlank { null } ?: "caps"
}

/**
 * Add / edit a supplement with a flexible schedule: a main schedule (days + any number of doses) and extra
 * rules for specific weekdays that take priority over it (e.g. weekdays 08:00, weekends 11:00).
 */
@Composable
fun SupplementEditor(
    initial: Supplement?,
    initialRules: List<SuppRule>,
    onDismiss: () -> Unit,
    onSave: (Supplement, List<SuppRule>, String) -> Unit,
    onArchive: ((Supplement) -> Unit)?,
) {
    val drafts = remember { mutableStateListOf<RuleDraft>().apply { addAll(draftsOf(initial, initialRules)) } }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var unit by remember { mutableStateOf(initialUnit(initial, initialRules)) }
    var note by remember { mutableStateOf(initial?.dose?.let { d -> d.substringAfter(" · ", "").trim() } ?: "") }
    var reminder by remember { mutableStateOf(initial?.reminder ?: true) }
    var withFood by remember { mutableStateOf(initial?.withFood ?: false) }
    var stock by remember { mutableStateOf(initial?.stock?.toString() ?: "") }
    var alert by remember { mutableStateOf((initial?.stockAlert ?: 10).toString()) }
    var picking by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun update(i: Int, f: (RuleDraft) -> RuleDraft) { drafts[i] = f(drafts[i]) }
    val valid = name.isNotBlank() && drafts.all { r -> r.doses.isNotEmpty() && r.doses.all { (it.amount.num() ?: 0.0) > 0 } } &&
        drafts.drop(1).all { it.days != 0 } && drafts.any { it.days != 0 }
    val unitLegacy = unit.any { it.isDigit() }

    fun toRules(): List<SuppRule> = drafts.mapIndexed { i, d ->
        SuppRule(
            supplementId = initial?.id ?: 0, days = d.days, base = d.base, sort = i,
            doses = DoseCodec.encode(d.doses.map { Dose(hhmm(it.time), it.amount.num() ?: 1.0, unit.trim()) }.distinctBy { it.time }),
        )
    }
    val preview = remember(drafts.toList(), unit) {
        val fake = Supplement(id = -1, name = "", dose = "", slot = 0, time = "")
        val rs = toRules().map { it.copy(supplementId = -1) }
        SuppSchedule.summary(fake, rs) + " · ${SuppSchedule.perWeek(fake, rs)} doses / week"
    }

    val expanded = remember { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
    EntrySheet(
        if (initial == null) "Add supplement" else "Edit ${initial.name}", onDismiss,
        canConfirm = valid,
        subtitle = preview,
        onConfirm = {
            onSave(
                (initial ?: Supplement(name = "", dose = "", slot = 0, time = "")).copy(
                    name = name.trim(), reminder = reminder, withFood = withFood,
                    stock = stock.trim().toIntOrNull()?.coerceAtLeast(0), stockAlert = alert.trim().toIntOrNull() ?: 10,
                ),
                toRules(), note,
            )
            onDismiss()
        },
        onDelete = if (initial != null && onArchive != null) ({ onArchive(initial); onDismiss() }) else null,
    ) {
        LifeField(name, { name = it }, "Name", placeholder = "Magnesium glycinate")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Unit", style = T.caption, color = C.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DOSE_UNITS.forEach { u -> BChip(u, unit == u, C.Health, C.HealthTint) { unit = u } }
            }
            if (unitLegacy || unit !in DOSE_UNITS) LifeField(unit, { unit = it }, "Custom unit", placeholder = "scoop")
        }

        // ------------------------------------------------------------ schedule
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Schedule", style = T.section, color = C.Ink)
            Text(preview, style = T.caption, color = C.Health)
        }
        drafts.forEachIndexed { ri, r ->
            val earlier = drafts.subList(1, maxOf(1, ri)).fold(0) { a, x -> a or x.days }
            val overlap = if (!r.base) r.days and earlier else 0
            val overridden = if (r.base) r.days and drafts.drop(1).fold(0) { a, x -> a or x.days } else 0
            val open = r.base || expanded[r.key] == true
            RuleCard(
                r, title = if (r.base) "Main schedule" else "Rule ${ri + 1} · overrides main",
                unit = unit,
                expanded = open,
                onToggle = if (r.base) null else ({ expanded[r.key] = !open }),
                warn = when {
                    overlap != 0 -> "${daysText(overlap)} also in an earlier rule — the earlier one wins."
                    !r.base && r.days == 0 -> "Pick the days this rule applies to."
                    r.base && overridden != 0 -> "${daysText(overridden)} follow the extra rule${if (drafts.size > 2) "s" else ""} below."
                    else -> null
                },
                onDays = { bit -> update(ri) { it.copy(days = it.days xor bit) } },
                onTime = { di -> picking = ri to di },
                onAmount = { di, v -> update(ri) { it.copy(doses = it.doses.toMutableList().also { l -> l[di] = l[di].copy(amount = v) }) } },
                onRemoveDose = { di -> update(ri) { it.copy(doses = it.doses.toMutableList().also { l -> l.removeAt(di) }) } },
                onAddDose = {
                    update(ri) { d ->
                        val last = d.doses.lastOrNull()
                        val t = last?.time?.plusHours(if (last.time.hour < 20) 4 else 0) ?: LocalTime.of(8, 0)
                        d.copy(doses = d.doses + DoseDraft(t, last?.amount ?: "1"))
                    }
                },
                onDelete = if (r.base) null else ({ drafts.removeAt(ri) }),
            )
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).dashedBorder(C.Health.copy(alpha = 0.5f), 14.dp)
                .press(haptic = true) {
                    val used = drafts.drop(1).fold(0) { a, x -> a or x.days }
                    val free = 0b1100000 and used.inv()
                    val base = drafts.first()
                    val nr = RuleDraft(keySeq++, false, if (free != 0) free else 0, base.doses.map { it.copy(time = it.time.plusHours(3)) })
                    // Collapse the others so the sheet stays short; open the new one.
                    drafts.forEach { if (!it.base) expanded[it.key] = false }
                    expanded[nr.key] = true
                    drafts.add(nr)
                },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Ic.Plus, null, tint = C.Health, modifier = Modifier.size(16.dp))
            Text("  Different on some days", style = T.smallB, color = C.Health)
        }

        // ------------------------------------------------------------ details
        LifeField(note, { note = it }, "Note (optional)", placeholder = "with breakfast")
        GroupCard {
            GroupRow("Reminder", "one notification per dose time", divider = false) { LifeSwitch(reminder, C.Health) { reminder = it } }
            GroupRow("Take with food") { LifeSwitch(withFood, C.Health) { withFood = it } }
            GroupRow("Stock", stock.toIntOrNull()?.let { "$it left · remind at ${alert.ifBlank { "10" }} · counts ${if (unit in listOf("caps", "tab", "drops")) unit else "servings"}" } ?: "Not tracked") {}
            Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeField(stock, { stock = it.filter(Char::isDigit) }, "In stock", Modifier.weight(1f), KeyboardType.Number, placeholder = "60")
                LifeField(alert, { alert = it.filter(Char::isDigit) }, "Remind at", Modifier.weight(1f), KeyboardType.Number)
            }
        }
        if (initial != null) Text("Schedule changes apply to every day's plan, past days included. The trash icon archives and keeps history.", style = T.caption.copy(fontWeight = FontWeight.Normal), color = C.InkFaint)
    }
    picking?.let { (ri, di) ->
        val cur = drafts.getOrNull(ri)?.doses?.getOrNull(di)?.time ?: LocalTime.of(8, 0)
        TimePickDialog(cur, C.Health, onDismiss = { picking = null }) { t ->
            if (ri < drafts.size) update(ri) { it.copy(doses = it.doses.toMutableList().also { l -> if (di < l.size) l[di] = l[di].copy(time = t) }.sortedBy { d -> d.time }) }
        }
    }
}

@Composable
private fun RuleCard(
    r: RuleDraft, title: String, unit: String, warn: String?,
    expanded: Boolean, onToggle: (() -> Unit)?,
    onDays: (Int) -> Unit, onTime: (Int) -> Unit, onAmount: (Int, String) -> Unit, onRemoveDose: (Int) -> Unit, onAddDose: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF0D0E10)).border(1.dp, if (r.base) C.Hairline else C.Health.copy(alpha = 0.3f), shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.then(if (onToggle != null) Modifier.press(onClick = onToggle) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = T.smallB, color = C.Ink)
                val summary = if (r.days == 0) "No days" else "${daysText(r.days)} · " +
                    r.doses.joinToString(", ") { "${hhmm(it.time)} ×${it.amount.ifBlank { "?" }}" } + " ${unit.take(10)}"
                Text(summary, style = T.caption, color = if (expanded) C.InkSubtle else C.Health, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (expanded && onDelete != null) RoundIconButton(Ic.Trash, "Remove rule", size = 34.dp, bg = C.Track, tint = C.Negative, onClick = onDelete)
            if (onToggle != null) Icon(if (expanded) Ic.ChevronDown else Ic.ChevronRight, if (expanded) "Collapse" else "Expand", tint = C.InkSubtle, modifier = Modifier.padding(start = 6.dp).size(18.dp))
        }
        if (!expanded) {
            warn?.let { Text(it, style = T.caption, color = C.Fuel) }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DAY_L.forEachIndexed { i, l ->
                val on = r.days and (1 shl i) != 0
                val s = RoundedCornerShape(10.dp)
                Box(
                    Modifier.weight(1f).height(38.dp).clip(s).background(if (on) C.HealthTint else Color.Transparent)
                        .border(1.dp, if (on) C.Health.copy(alpha = 0.35f) else C.Bar, s)
                        .press(haptic = true) { onDays(1 shl i) },
                    contentAlignment = Alignment.Center,
                ) { Text(l, style = T.smallB, color = if (on) C.Health else C.InkSubtle) }
            }
        }
        r.doses.forEachIndexed { di, d ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BChip(hhmm(d.time), true, C.Health, C.HealthTint, mono = true) { onTime(di) }
                LifeField(d.amount, { onAmount(di, it) }, "Amount", Modifier.weight(1f), KeyboardType.Decimal)
                Text(unit.take(12), style = T.small, color = C.InkMuted)
                if (r.doses.size > 1) RoundIconButton(Ic.Close, "Remove dose", size = 32.dp, bg = C.Track, tint = C.InkSubtle) { onRemoveDose(di) }
            }
        }
        Text("+ dose", style = T.smallB, color = C.Health, modifier = Modifier.press(onClick = onAddDose).padding(vertical = 4.dp))
        warn?.let { Text(it, style = T.caption, color = C.Fuel) }
    }
}
