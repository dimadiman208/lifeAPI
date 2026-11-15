package com.lifeapi.app.ui.screens.body.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Food
import com.lifeapi.app.data.Meal
import com.lifeapi.app.data.Profile
import com.lifeapi.app.data.nutrition.MealSource
import com.lifeapi.app.data.nutrition.fmtGrams
import com.lifeapi.app.data.nutrition.macrosFor
import com.lifeapi.app.data.nutrition.title
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RollingText
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.body.BChip
import com.lifeapi.app.ui.screens.body.EntrySheet
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

internal fun String.num(): Double? = replace(',', '.').trim().toDoubleOrNull()

internal fun dayLabel(day: Long): String {
    val today = Days.today()
    return when (day) {
        today -> "Today"
        today - 1 -> "Yesterday"
        today + 1 -> "Tomorrow"
        else -> Days.date(day).let { "${it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" }
    }
}

// ================================================================ macros line

@Composable
internal fun MacroLine(kcal: Int, p: Int, c: Int, f: Int, big: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (big) RollingText("$kcal", T.title.copy(fontSize = 30.sp), C.Ink) else Text("$kcal", style = T.section, color = C.Ink)
            Text(" kcal", style = T.caption, color = C.InkSubtle, modifier = Modifier.padding(bottom = if (big) 5.dp else 1.dp))
        }
        Box(Modifier.weight(1f))
        Text("P $p", style = T.mono, color = C.Money)
        Text("C $c", style = T.mono, color = C.Fuel)
        Text("F $f", style = T.mono, color = C.Train)
    }
}

@Composable
private fun GramsInput(grams: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepBox("−") { onChange(fmtGrams(((grams.num() ?: 0.0) - 10).coerceAtLeast(1.0))) }
        LifeField(grams, onChange, "Amount, g", Modifier.weight(1f), KeyboardType.Decimal)
        StepBox("+") { onChange(fmtGrams((grams.num() ?: 0.0) + 10)) }
    }
}

@Composable
private fun StepBox(t: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(C.Graphite3).press(haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, style = T.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium), color = C.Ink) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotChips(slot: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SLOTS.forEach { s -> BChip(s, s == slot, C.Fuel, C.FuelTint) { onPick(s) } }
    }
}

/**
 * Edit / move / copy / delete one diary entry. Product-based entries (foodId + grams) recalculate from grams;
 * others edit numbers directly.
 */
@Composable
fun MealEditSheet(
    meal: Meal,
    food: Food?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Meal) -> Unit,
    onCopy: (day: Long, slot: String) -> Unit,
) {
    var slot by remember { mutableStateOf(meal.slot) }
    var day by remember { mutableStateOf(meal.day) }
    val byGrams = food != null && meal.grams != null
    var grams by remember { mutableStateOf(meal.grams?.let { fmtGrams(it) } ?: "") }
    var items by remember { mutableStateOf(meal.items) }
    var kcal by remember { mutableStateOf(meal.kcal.toString()) }
    var p by remember { mutableStateOf(meal.protein.toString()) }
    var c by remember { mutableStateOf(meal.carbs.toString()) }
    var f by remember { mutableStateOf(meal.fat.toString()) }
    val g = grams.num()?.takeIf { it > 0 && it <= 5000 }
    val fromSync = meal.source == "FatSecret"
    val ctx = androidx.compose.ui.platform.LocalContext.current

    fun build(): Meal? {
        val ts = if (day == meal.day && slot == meal.slot) meal.ts else com.lifeapi.app.data.nutrition.FoodRepo.tsFor(day, slot)
        return if (byGrams) {
            val gg = g ?: return null
            val m = food!!.macrosFor(gg)
            meal.copy(day = day, slot = slot, ts = ts, grams = gg, kcal = m.kcal, protein = m.p, carbs = m.c, fat = m.f, items = "${food.title()} · ${fmtGrams(gg)} g")
        } else {
            val k = kcal.num() ?: return null
            meal.copy(
                day = day, slot = slot, ts = ts, items = items.trim().ifBlank { meal.items }, kcal = k.roundToInt(),
                protein = p.num()?.roundToInt() ?: 0, carbs = c.num()?.roundToInt() ?: 0, fat = f.num()?.roundToInt() ?: 0,
            )
        }
    }
    val preview = build()

    EntrySheet(
        if (byGrams) food!!.name else if (fromSync) "FatSecret entry" else "Edit entry", onDismiss, hue = C.Fuel,
        onDelete = if (fromSync) null else ({ onDelete(); onDismiss() }),
        canConfirm = fromSync || preview != null,
        subtitle = "${MealSource.label(meal.source)} · ${dayLabel(meal.day)} · ${meal.slot.lowercase()}",
        confirm = if (fromSync) "Edit in FatSecret" else "Save",
        onConfirm = { if (fromSync) openFatSecret(ctx) else preview?.let(onSave); onDismiss() },
    ) {
        if (fromSync) Text("Synced from your FatSecret diary — change or delete it in FatSecret; LifeAPI updates when you come back.", style = T.caption, color = C.InkMuted)
        if (byGrams) {
            GramsInput(grams) { grams = it }
        } else if (!fromSync) {
            LifeField(items, { items = it }, "What")
            LifeField(kcal, { kcal = it }, "Calories, kcal", keyboard = KeyboardType.Number)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeField(p, { p = it }, "Protein g", Modifier.weight(1f), KeyboardType.Number)
                LifeField(c, { c = it }, "Carbs g", Modifier.weight(1f), KeyboardType.Number)
                LifeField(f, { f = it }, "Fat g", Modifier.weight(1f), KeyboardType.Number)
            }
        }
        preview?.let { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Graphite2).padding(14.dp)) { MacroLine(it.kcal, it.protein, it.carbs, it.fat) } }
        if (!fromSync) {
            SlotChips(slot) { slot = it }
            DayStepper(day) { day = it }
        }
        // ---- copy
        Text("Copy to", style = T.caption, color = C.InkMuted)
        val today = Days.today()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Today", C.Graphite3, C.Ink, Modifier.weight(1f), 40.dp, 12.dp, icon = NIc.Copy) { onCopy(today, slot); onDismiss() }
            PillButton("Tomorrow", C.Graphite3, C.Ink, Modifier.weight(1f), 40.dp, 12.dp, icon = NIc.Copy) { onCopy(today + 1, slot); onDismiss() }
            if (day != today && day != today + 1) PillButton(dayLabel(day), C.Graphite3, C.Ink, Modifier.weight(1f), 40.dp, 12.dp, icon = NIc.Copy) { onCopy(day, slot); onDismiss() }
        }
        Text("Copies go to the ${slot.lowercase()} slot selected above.", style = T.caption, color = C.InkFaint)
    }
}

@Composable
private fun DayStepper(day: Long, onChange: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(C.Graphite2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("‹", style = T.title, color = C.InkSoft, modifier = Modifier.width(52.dp).press { onChange(day - 1) }.padding(horizontal = 20.dp))
        Text(dayLabel(day), style = T.smallB, color = C.Ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("›", style = T.title, color = C.InkSoft, modifier = Modifier.width(52.dp).press { onChange(day + 1) }.padding(horizontal = 20.dp))
    }
}

// ================================================================ goals

/**
 * Calories are the anchor: P·4 + C·4 + F·9 always equals the calorie goal.
 * Protein edits re-split the rest between carbs and fat (keeping their current ratio);
 * carbs edits move fat, fat edits move carbs; nothing can exceed what the calories allow.
 */
internal object MacroBalance {
    data class M(val kcal: Int, val p: Int, val c: Int, val f: Int)

    private fun split(kcal: Int, p: Int, cOld: Int, fOld: Int): M {
        val rest = (kcal - p * 4).coerceAtLeast(0)
        val cK = cOld * 4.0; val fK = fOld * 9.0
        val share = if (cK + fK > 0) cK / (cK + fK) else 0.5
        val c = (rest * share / 4).toInt()
        val f = ((rest - c * 4) / 9.0).toInt()
        return M(kcal, p, c, f)
    }

    fun protein(m: M, p: Int) = split(m.kcal, p.coerceIn(0, m.kcal / 4), m.c, m.f)
    fun carbs(m: M, c: Int): M {
        val cc = c.coerceIn(0, ((m.kcal - m.p * 4) / 4).coerceAtLeast(0))
        return m.copy(c = cc, f = ((m.kcal - m.p * 4 - cc * 4) / 9).coerceAtLeast(0))
    }
    fun fat(m: M, f: Int): M {
        val ff = f.coerceIn(0, ((m.kcal - m.p * 4) / 9).coerceAtLeast(0))
        return m.copy(f = ff, c = ((m.kcal - m.p * 4 - ff * 9) / 4).coerceAtLeast(0))
    }
    fun calories(m: M, kcal: Int) = split(kcal, m.p.coerceAtMost(kcal / 4), m.c, m.f)

    /** Repairs any stored combination (e.g. 2500 kcal with 3000 g protein) into one that adds up. */
    fun fix(kcal: Int, p: Int, c: Int, f: Int): M = calories(M(kcal, p, c, f), kcal.coerceIn(500, 10000))
}

@Composable
fun NutritionGoalsSheet(p: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    // start from a consistent split of the saved goals
    var m by remember { mutableStateOf(MacroBalance.calories(MacroBalance.M(p.kcalGoal, p.proteinG, p.carbsG, p.fatG), p.kcalGoal)) }
    var kcal by remember { mutableStateOf(m.kcal.toString()) }
    var pr by remember { mutableStateOf(m.p.toString()) }
    var cb by remember { mutableStateOf(m.c.toString()) }
    var ft by remember { mutableStateOf(m.f.toString()) }
    fun show(n: MacroBalance.M, except: Char) {
        m = n
        if (except != 'k') kcal = n.kcal.toString()
        if (except != 'p') pr = n.p.toString()
        if (except != 'c') cb = n.c.toString()
        if (except != 'f') ft = n.f.toString()
    }
    val k = kcal.num()?.roundToInt()
    EntrySheet(
        "Nutrition goals", onDismiss, hue = C.Fuel, subtitle = "Calories lead · macros always add up to them",
        canConfirm = k != null && k in 500..10000,
        onConfirm = {
            onSave(p.copy(kcalGoal = m.kcal, proteinG = m.p, carbsG = m.c, fatG = m.f))
            onDismiss()
        },
    ) {
        LifeField(kcal, { v -> kcal = v; v.num()?.roundToInt()?.takeIf { it in 500..10000 }?.let { show(MacroBalance.calories(m, it), 'k') } },
            "Calories, kcal", keyboard = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeField(pr, { v -> pr = v; v.num()?.roundToInt()?.let { n -> val r = MacroBalance.protein(m, n); show(r, if (r.p == n) 'p' else ' ') } },
                "Protein g", Modifier.weight(1f), KeyboardType.Number)
            LifeField(cb, { v -> cb = v; v.num()?.roundToInt()?.let { n -> val r = MacroBalance.carbs(m, n); show(r, if (r.c == n) 'c' else ' ') } },
                "Carbs g", Modifier.weight(1f), KeyboardType.Number)
            LifeField(ft, { v -> ft = v; v.num()?.roundToInt()?.let { n -> val r = MacroBalance.fat(m, n); show(r, if (r.f == n) 'f' else ' ') } },
                "Fat g", Modifier.weight(1f), KeyboardType.Number)
        }
        val used = m.p * 4 + m.c * 4 + m.f * 9
        Text(
            "P ${m.p * 4} · C ${m.c * 4} · F ${m.f * 9} kcal = $used of ${m.kcal}" +
                "  ·  max protein ${m.kcal / 4} g",
            style = T.caption, color = C.InkSubtle,
        )
        Text("Change protein → carbs and fat share the rest. Change carbs ↔ fat trade off against each other.", style = T.caption, color = C.InkFaint)
    }
}
