package com.lifeapi.app.ui.screens.body.supps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Supplement
import com.lifeapi.app.data.hhmm
import com.lifeapi.app.data.supps.DayProgress
import com.lifeapi.app.data.supps.DoseSlot
import com.lifeapi.app.data.supps.DoseState
import com.lifeapi.app.data.supps.doseLabel
import com.lifeapi.app.data.supps.fmtAmount
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.ProgressEasing
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.body.num
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.supps.SuppActionsVM
import com.lifeapi.app.vm.supps.SuppGroup

/** Muted tone for partially-done days (between Track and Health). */
val HealthMid = Color(0xFF2B5B55)

/** Visual state of a dose. */
enum class DoseLook { PENDING, OVERDUE, TAKEN, SKIPPED, FUTURE, MISSED }

fun lookOf(slot: DoseSlot, day: Long, today: Long, now: String): DoseLook = when {
    slot.taken -> DoseLook.TAKEN
    slot.skipped -> DoseLook.SKIPPED
    day > today -> DoseLook.FUTURE
    day < today -> DoseLook.MISSED
    slot.dose.time < now -> DoseLook.OVERDUE
    else -> DoseLook.PENDING
}

@Composable
fun DoseCheck(look: DoseLook, size: Dp = 28.dp) {
    val shape = CircleShape
    val bg = when (look) { DoseLook.TAKEN -> C.Health; DoseLook.SKIPPED -> C.Graphite3; else -> Color.Transparent }
    val ring = when (look) { DoseLook.OVERDUE -> C.Fuel.copy(alpha = 0.7f); DoseLook.MISSED -> C.Negative.copy(alpha = 0.55f); DoseLook.FUTURE -> C.Bar; else -> C.Ghost }
    Box(
        Modifier.size(size).clip(shape).background(bg).then(if (look != DoseLook.TAKEN && look != DoseLook.SKIPPED) Modifier.border(1.5.dp, ring, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when (look) {
            DoseLook.TAKEN -> Icon(Ic.Check, null, tint = C.Graphite0, modifier = Modifier.size(size * 0.58f))
            DoseLook.SKIPPED -> Box(Modifier.size(width = size * 0.4f, height = 2.dp).background(C.InkSubtle))
            else -> Unit
        }
    }
}

/**
 * One planned dose. Tap = take / untake. Long-press = menu (skip, amount, delete record, edit, details).
 * [showName] false on a single-supplement screen.
 */
@Composable
fun DoseRow(
    slot: DoseSlot,
    day: Long,
    today: Long,
    now: String,
    vm: SuppActionsVM,
    showName: Boolean = true,
    onEdit: ((Supplement) -> Unit)? = null,
    onDetails: ((Supplement) -> Unit)? = null,
) {
    val look = lookOf(slot, day, today, now)
    var menu by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf(false) }
    val editable = day <= today
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                .press(enabled = editable || onEdit != null, haptic = true, onLongClick = { menu = true }) { if (editable) vm.toggle(slot, day) else menu = true }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val title = if (showName) slot.supp.name else slot.dose.label
                Text(
                    title, style = T.body.copy(textDecoration = if (look == DoseLook.SKIPPED) TextDecoration.LineThrough else null),
                    color = when (look) { DoseLook.SKIPPED -> C.InkSubtle; DoseLook.FUTURE -> C.InkMuted; else -> C.Ink },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val amt = if (slot.taken && slot.intake?.amount?.let { it > 0 && it != slot.dose.amount } == true)
                    "took ${doseLabel(slot.takenAmount, slot.dose.unit)} of ${slot.dose.label}" else slot.dose.label
                val stock = slot.supp.stock?.takeIf { it <= slot.supp.stockAlert }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val sub = listOfNotNull(
                        if (showName) amt else if (amt != slot.dose.label) amt else null,
                        if (slot.supp.withFood) "with food" else null,
                    ).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (stock != null && showName) Text("$stock left", style = T.caption, color = C.Fuel, maxLines = 1)
                }
            }
            val (status, color) = when (look) {
                DoseLook.TAKEN -> (slot.intake?.let { hhmm(Days.timeOf(it.ts)) } ?: slot.dose.time) to C.Health
                DoseLook.SKIPPED -> "Skipped" to C.InkSubtle
                DoseLook.OVERDUE -> "${slot.dose.time} · due" to C.Fuel
                DoseLook.MISSED -> "${slot.dose.time} · missed" to C.Negative.copy(alpha = 0.8f)
                else -> slot.dose.time to C.InkSubtle
            }
            Text(status, style = T.mono, color = color, modifier = Modifier.padding(horizontal = 10.dp))
            DoseCheck(look)
            Box(Modifier.size(8.dp))
        }
        DropdownMenu(menu, { menu = false }, containerColor = C.Graphite2) {
            if (editable) {
                if (!slot.taken) MenuItem("Mark taken") { menu = false; vm.setState(slot, day, DoseState.TAKEN) }
                if (!slot.skipped) MenuItem("Skip this dose") { menu = false; vm.setState(slot, day, DoseState.SKIPPED) }
                MenuItem(if (slot.taken) "Change amount taken…" else "Take a different amount…") { menu = false; amount = true }
                if (slot.intake != null) MenuItem("Delete record", C.Negative) { menu = false; vm.setState(slot, day, DoseState.PENDING) }
            } else {
                Text("Planned · ${slot.dose.time} · ${slot.dose.label}", style = T.caption, color = C.InkSubtle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (onEdit != null || onDetails != null) HorizontalDivider(color = C.Hairline)
            onEdit?.let { e -> MenuItem("Edit schedule") { menu = false; e(slot.supp) } }
            onDetails?.let { d -> MenuItem("Details & streak") { menu = false; d(slot.supp) } }
        }
    }
    if (amount) AmountSheet(slot, onDismiss = { amount = false }) { vm.setState(slot, day, DoseState.TAKEN, it) }
}

@Composable
private fun MenuItem(text: String, color: Color = C.Ink, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text, style = T.body, color = color) }, onClick = onClick)
}

@Composable
fun AmountSheet(slot: DoseSlot, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var v by remember { mutableStateOf(fmtAmount(slot.takenAmount)) }
    val unit = slot.dose.unit.takeIf { u -> u.none { it.isDigit() } } ?: "× ${slot.dose.unit}"
    FormSheet("${slot.supp.name} · ${slot.dose.time}", onDismiss, confirm = "Save amount", confirmColor = C.Health, canConfirm = (v.num() ?: 0.0) > 0, onConfirm = {
        onSave(v.num()!!); onDismiss()
    }) {
        Text("Planned ${slot.dose.label}. Stock counts what you actually took.", style = T.caption, color = C.InkMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LifeField(v, { v = it }, "Amount taken", Modifier.weight(1f), KeyboardType.Decimal)
            Text(unit, style = T.body, color = C.InkMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.5, 1.0, 2.0).map { it * slot.dose.amount }.distinct().forEach { a ->
                PillButton(fmtAmount(a), if (v.num() == a) C.HealthTint else C.Graphite3, if (v.num() == a) C.Health else C.InkSoft, height = 36.dp) { v = fmtAmount(a) }
            }
        }
    }
}

/** Group card body: "MORNING · 2/3" + Take all + rows. */
@Composable
fun GroupBlock(g: SuppGroup, day: Long, today: Long, now: String, vm: SuppActionsVM, onEdit: ((Supplement) -> Unit)?, onDetails: ((Supplement) -> Unit)?) {
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            MonoLabel("${g.part.label.uppercase()} · ${g.taken}/${g.slots.size}", if (g.taken == g.slots.size) C.Health else C.InkSubtle, Modifier.weight(1f))
            val pend = g.pending
            if (pend.isNotEmpty() && day <= today) {
                PillButton("Take all · ${pend.size}", C.HealthTint, C.Health, height = 32.dp, icon = Ic.Check) { vm.takeAll(pend, day) }
            }
        }
        // Long groups scroll inside their own window so the card stays compact.
        val inner = androidx.compose.foundation.rememberScrollState()
        Box {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 236.dp).verticalScroll(inner),
            ) { g.slots.forEach { DoseRow(it, day, today, now, vm, onEdit = onEdit, onDetails = onDetails) } }
            if (inner.maxValue > 0) {
                // fade hints that there is more to scroll
                if (inner.value < inner.maxValue) Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, C.Graphite1))),
                )
                if (inner.value > 0) Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(20.dp)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.Graphite1, Color.Transparent))),
                )
            }
        }
    }
}
