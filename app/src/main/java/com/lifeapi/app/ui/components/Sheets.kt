package com.lifeapi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lifeapi.app.data.Account
import com.lifeapi.app.data.Goal
import com.lifeapi.app.data.Habit
import com.lifeapi.app.data.Meal
import com.lifeapi.app.data.Profile
import com.lifeapi.app.data.Supplement
import com.lifeapi.app.data.TemplateExercise
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.data.mealSlotFor
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import java.time.LocalTime
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    confirm: String = "Save",
    confirmColor: androidx.compose.ui.graphics.Color = C.Volt,
    canConfirm: Boolean = true,
    onConfirm: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = C.Graphite1,
        scrimColor = androidx.compose.ui.graphics.Color(0x9E050608),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Ghost)) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = T.title, color = C.Ink, modifier = Modifier.weight(1f))
                if (onDelete != null) RoundIconButton(Ic.Trash, "Delete", size = 40.dp, bg = C.Track, tint = C.Negative, onClick = onDelete)
            }
            content()
            Spacer(Modifier.height(4.dp))
            PillButton(confirm, confirmColor, C.Graphite0, Modifier.fillMaxWidth(), height = 52.dp, radius = 16.dp, enabled = canConfirm, style = T.section, onClick = onConfirm)
        }
    }
}

private fun String.num(): Double? = replace(',', '.').trim().toDoubleOrNull()

// ---------------------------------------------------------------- meal

val MEAL_SLOTS = listOf("Breakfast", "Lunch", "Snack", "Dinner")

@Composable
fun MealSheet(initialItems: String = "", onDismiss: () -> Unit, onSave: (Meal) -> Unit) {
    var slot by remember { mutableStateOf(MEAL_SLOTS.indexOf(mealSlotFor(LocalTime.now()))) }
    var items by remember { mutableStateOf(initialItems) }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var cb by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    val k = kcal.num()
    FormSheet("Log meal", onDismiss, confirm = "Log meal", confirmColor = C.Fuel, canConfirm = items.isNotBlank() && k != null, onConfirm = {
        onSave(Meal(day = 0, ts = 0, slot = MEAL_SLOTS[slot], items = items.trim(), kcal = k!!.toInt(), protein = p.num()?.toInt() ?: 0, carbs = cb.num()?.toInt() ?: 0, fat = f.num()?.toInt() ?: 0))
        onDismiss()
    }) {
        Segmented(MEAL_SLOTS, slot, selBg = C.FuelTint, selFg = C.Fuel) { slot = it }
        LifeField(items, { items = it }, "What did you eat", placeholder = "Chicken rice bowl, avocado")
        LifeField(kcal, { kcal = it }, "Calories, kcal", keyboard = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeField(p, { p = it }, "Protein g", Modifier.weight(1f), KeyboardType.Number)
            LifeField(cb, { cb = it }, "Carbs g", Modifier.weight(1f), KeyboardType.Number)
            LifeField(f, { f = it }, "Fat g", Modifier.weight(1f), KeyboardType.Number)
        }
    }
}

// ---------------------------------------------------------------- supplement

@Composable
fun SupplementSheet(initial: Supplement?, onDismiss: () -> Unit, onSave: (Supplement) -> Unit, onDelete: ((Supplement) -> Unit)?) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var dose by remember { mutableStateOf(initial?.dose ?: "") }
    var slot by remember { mutableStateOf(initial?.slot ?: 0) }
    var time by remember { mutableStateOf(initial?.time ?: "08:00") }
    val timeOk = Regex("""^\d{1,2}:\d{2}$""").matches(time.trim())
    FormSheet(
        if (initial == null) "Add supplement" else "Edit supplement", onDismiss, confirmColor = C.Health,
        canConfirm = name.isNotBlank() && timeOk,
        onConfirm = {
            val t = time.trim().let { if (it.length == 4) "0$it" else it }
            onSave((initial ?: Supplement(name = "", dose = "", slot = 0, time = "")).copy(name = name.trim(), dose = dose.trim(), slot = slot, time = t))
            onDismiss()
        },
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial); onDismiss() }) else null,
    ) {
        LifeField(name, { name = it }, "Name", placeholder = "Magnesium glycinate")
        LifeField(dose, { dose = it }, "Dose & note", placeholder = "400 mg · with water")
        Segmented(listOf("Morning & midday", "Evening"), slot, selBg = C.HealthTint, selFg = C.Health) {
            slot = it; if (it == 1 && time < "15:00") time = "20:00" else if (it == 0 && time >= "15:00") time = "08:00"
        }
        LifeField(time, { time = it }, "Time (HH:MM)", keyboard = KeyboardType.Number)
    }
}

// ---------------------------------------------------------------- sleep

@Composable
fun SleepSheet(onDismiss: () -> Unit, onSave: (LocalTime, LocalTime) -> Unit) {
    var bed by remember { mutableStateOf("23:30") }
    var wake by remember { mutableStateOf("07:00") }
    fun parse(s: String) = Regex("""^(\d{1,2})[:.](\d{2})$""").find(s.trim())?.let {
        val h = it.groupValues[1].toInt(); val m = it.groupValues[2].toInt()
        if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }
    val b = parse(bed); val w = parse(wake)
    FormSheet("Log sleep", onDismiss, confirmColor = C.Health, canConfirm = b != null && w != null, onConfirm = { onSave(b!!, w!!); onDismiss() }) {
        Text("Last night", style = T.small, color = C.InkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeField(bed, { bed = it }, "Went to bed", Modifier.weight(1f), KeyboardType.Number)
            LifeField(wake, { wake = it }, "Woke up", Modifier.weight(1f), KeyboardType.Number)
        }
        if (b != null && w != null) {
            var mins = java.time.Duration.between(b, w).toMinutes()
            if (mins < 0) mins += 24 * 60
            Text("${mins / 60}h ${mins % 60}m", style = T.number, color = C.Health)
        }
    }
}

// ---------------------------------------------------------------- money

@Composable
fun AccountSheet(initial: Account?, onDismiss: () -> Unit, onSave: (Account) -> Unit, onDelete: ((Account) -> Unit)?) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var bal by remember { mutableStateOf(initial?.let { String.format(java.util.Locale.US, "%.2f", it.balanceCents / 100.0) } ?: "") }
    var hue by remember { mutableStateOf(initial?.hue ?: 2) }
    var kind by remember { mutableStateOf(initial?.kind ?: "Current") }
    val v = bal.num()
    FormSheet(
        if (initial == null) "Add account" else "Edit account", onDismiss, confirmColor = C.Money,
        canConfirm = name.isNotBlank() && v != null,
        onConfirm = { onSave((initial ?: Account(name = "", balanceCents = 0, hue = 2)).copy(name = name.trim(), balanceCents = (v!! * 100).roundToLong(), hue = hue, kind = kind)); onDismiss() },
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial); onDismiss() }) else null,
    ) {
        LifeField(name, { name = it }, "Name", placeholder = "Revolut")
        LifeField(bal, { bal = it }, "Balance, €", keyboard = KeyboardType.Decimal)
        Text("Type", style = T.caption, color = C.InkMuted)
        ChipPicker(com.lifeapi.app.data.ACCOUNT_KINDS, kind, C.Money) { kind = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (0..4).forEach { h ->
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(C.tint(h)).press { hue = h },
                    contentAlignment = Alignment.Center,
                ) { if (h == hue) Dot(C.hue(h), 12.dp) }
            }
        }
    }
}

@Composable
fun TxnSheet(categories: List<String>, accounts: List<Account>, income: Boolean, onDismiss: () -> Unit, onSave: (name: String, cents: Long, cat: String, income: Boolean, accId: Long?) -> Unit) {
    var isIncome by remember { mutableStateOf(income) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(categories.firstOrNull() ?: "Other") }
    var acc by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    val v = amount.num()
    FormSheet(if (isIncome) "Add income" else "Add expense", onDismiss, confirmColor = if (isIncome) C.Gain else C.Money,
        canConfirm = name.isNotBlank() && v != null && v > 0,
        onConfirm = { onSave(name.trim(), (v!! * 100).roundToLong(), cat, isIncome, acc); onDismiss() }) {
        Segmented(listOf("Expense", "Income"), if (isIncome) 1 else 0, selBg = C.MoneyTint, selFg = C.Money) { isIncome = it == 1 }
        LifeField(name, { name = it }, if (isIncome) "Source" else "Where / what", placeholder = if (isIncome) "Salary" else "Rimi")
        LifeField(amount, { amount = it }, "Amount, €", keyboard = KeyboardType.Decimal)
        if (!isIncome) ChipPicker(categories, cat, C.Money) { cat = it }
        if (accounts.isNotEmpty()) {
            Text("Account", style = T.caption, color = C.InkSubtle)
            ChipPicker(accounts.map { it.name } + "None", accounts.firstOrNull { it.id == acc }?.name ?: "None", C.Money) { n ->
                acc = accounts.firstOrNull { it.name == n }?.id
            }
        }
    }
}

@Composable
fun BudgetSheet(categories: List<String>, initialCat: String?, initialLimit: Long?, onDismiss: () -> Unit, onSave: (String, Long) -> Unit, onDelete: ((String) -> Unit)?) {
    var cat by remember { mutableStateOf(initialCat ?: categories.first()) }
    var custom by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf(initialLimit?.let { (it / 100).toString() } ?: "") }
    val v = limit.num()
    FormSheet("Monthly budget", onDismiss, confirmColor = C.Money, canConfirm = v != null && v > 0,
        onConfirm = { onSave(custom.trim().ifEmpty { cat }, (v!! * 100).roundToLong()); onDismiss() },
        onDelete = if (initialCat != null && onDelete != null) ({ onDelete(initialCat); onDismiss() }) else null) {
        if (initialCat == null) {
            ChipPicker(categories, cat, C.Money) { cat = it; custom = "" }
            LifeField(custom, { custom = it }, "…or a new category")
        } else Text(initialCat, style = T.section, color = C.Ink)
        LifeField(limit, { limit = it }, "Limit per month, €", keyboard = KeyboardType.Decimal)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipPicker(options: List<String>, selected: String, hue: androidx.compose.ui.graphics.Color, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val sel = o == selected
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) hue.copy(alpha = 0.16f) else C.Graphite2)
                    .press { onPick(o) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, style = T.small, color = if (sel) hue else C.InkMuted) }
        }
    }
}

// ---------------------------------------------------------------- train

private class ExDraft(name: String, sets: String, reps: String, kg: String) {
    var name by mutableStateOf(name); var sets by mutableStateOf(sets); var reps by mutableStateOf(reps); var kg by mutableStateOf(kg)
}

@Composable
fun TemplateSheet(initialName: String?, initial: List<TemplateExercise>, onDismiss: () -> Unit, onSave: (String, List<TemplateExercise>) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initialName ?: "") }
    val list = remember {
        mutableStateListOf<ExDraft>().apply {
            if (initial.isEmpty()) add(ExDraft("", "3", "8", ""))
            else initial.forEach { add(ExDraft(it.name, it.sets.toString(), it.reps.toString(), fmtKg(it.kg))) }
        }
    }
    FormSheet(if (initialName == null) "New workout" else "Edit workout", onDismiss, confirmColor = C.Train,
        canConfirm = name.isNotBlank() && list.any { it.name.isNotBlank() },
        onConfirm = {
            onSave(name.trim(), list.filter { it.name.isNotBlank() }.map {
                TemplateExercise(templateId = 0, name = it.name.trim(), sets = it.sets.num()?.toInt() ?: 3, reps = it.reps.num()?.toInt() ?: 8, kg = it.kg.num() ?: 0.0)
            }); onDismiss()
        }, onDelete = onDelete?.let { { it(); onDismiss() } }) {
        LifeField(name, { name = it }, "Workout name", placeholder = "Push A")
        list.forEachIndexed { i, e ->
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).background(C.Graphite2).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Exercise ${i + 1}", style = T.caption, color = C.InkSubtle, modifier = Modifier.weight(1f))
                    if (list.size > 1) Text("Remove", style = T.caption, color = C.Negative, modifier = Modifier.press { list.removeAt(i) }.padding(4.dp))
                }
                LifeField(e.name, { e.name = it }, "Name", placeholder = "Bench press")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeField(e.sets, { e.sets = it }, "Sets", Modifier.weight(1f), KeyboardType.Number)
                    LifeField(e.reps, { e.reps = it }, "Reps", Modifier.weight(1f), KeyboardType.Number)
                    LifeField(e.kg, { e.kg = it }, "Kg", Modifier.weight(1f), KeyboardType.Decimal)
                }
            }
        }
        GhostButton("+ Add exercise", Modifier.fillMaxWidth()) { list.add(ExDraft("", "3", "8", "")) }
    }
}

@Composable
fun NameSheet(title: String, label: String, placeholder: String, hue: androidx.compose.ui.graphics.Color, initial: String = "", onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    FormSheet(title, onDismiss, confirmColor = hue, canConfirm = v.isNotBlank(), onConfirm = { onSave(v.trim()); onDismiss() },
        onDelete = onDelete?.let { { it(); onDismiss() } }) {
        LifeField(v, { v = it }, label, placeholder = placeholder)
    }
}

// ---------------------------------------------------------------- grow

@Composable
fun HabitSheet(initial: Habit?, onDismiss: () -> Unit, onSave: (Habit) -> Unit, onDelete: ((Habit) -> Unit)?) {
    NameSheet(
        if (initial == null) "New habit" else "Edit habit", "Habit", "Read 20 pages", C.Grow, initial?.name ?: "", onDismiss,
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial) }) else null,
    ) { onSave((initial ?: Habit(name = "")).copy(name = it)) }
}

@Composable
fun LearningSheet(onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var topic by remember { mutableStateOf("") }
    var mins by remember { mutableStateOf("15") }
    val m = mins.num()?.toInt()
    FormSheet("Log learning", onDismiss, confirmColor = C.Grow, canConfirm = topic.isNotBlank() && m != null && m > 0,
        onConfirm = { onSave(topic.trim(), m!!); onDismiss() }) {
        LifeField(topic, { topic = it }, "Topic", placeholder = "Spanish · lesson 84")
        LifeField(mins, { mins = it }, "Minutes", keyboard = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 15, 20, 30, 45).forEach { q ->
                Box(Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(C.Graphite2).press { mins = q.toString() }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("${q}m", style = T.small, color = C.InkMuted)
                }
            }
        }
    }
}

@Composable
fun GoalSheet(initial: Goal?, onDismiss: () -> Unit, onSave: (Goal) -> Unit, onDelete: ((Goal) -> Unit)?) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var cur by remember { mutableStateOf(initial?.let { fmtKg(it.current) } ?: "0") }
    var target by remember { mutableStateOf(initial?.let { fmtKg(it.target) } ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    val c = cur.num(); val t = target.num()
    FormSheet(if (initial == null) "New goal" else "Update goal", onDismiss, confirmColor = C.Grow,
        canConfirm = name.isNotBlank() && c != null && t != null && t > 0,
        onConfirm = { onSave((initial ?: Goal(name = "", current = 0.0, target = 1.0, unit = "")).copy(name = name.trim(), current = c!!, target = t!!, unit = unit.trim())); onDismiss() },
        onDelete = if (initial != null && onDelete != null) ({ onDelete(initial); onDismiss() }) else null) {
        LifeField(name, { name = it }, "Goal", placeholder = "Read 24 books")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeField(cur, { cur = it }, "Now", Modifier.weight(1f), KeyboardType.Decimal)
            LifeField(target, { target = it }, "Target", Modifier.weight(1f), KeyboardType.Decimal)
            LifeField(unit, { unit = it }, "Unit", Modifier.weight(1f), placeholder = "books")
        }
        if (initial != null && c != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1.0, 1.0, 5.0).forEach { d ->
                    PillButton(if (d > 0) "+${fmtKg(d)}" else fmtKg(d), C.Graphite2, C.Ink, Modifier.weight(1f), height = 40.dp, radius = 12.dp) {
                        cur = fmtKg((c + d).coerceAtLeast(0.0))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- profile

@Composable
fun ProfileSheet(p: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var water by remember { mutableStateOf(p.waterGoalMl.toString()) }
    // calories + macros are edited only in the balanced goals sheet (macros always add up to calories)
    var macros by remember { mutableStateOf(com.lifeapi.app.ui.screens.body.nutrition.MacroBalance.fix(p.kcalGoal, p.proteinG, p.carbsG, p.fatG)) }
    var macroSheet by remember { mutableStateOf(false) }
    var spend by remember { mutableStateOf((p.dailySpendCents / 100).toString()) }
    var learn by remember { mutableStateOf(p.learnGoalMin.toString()) }
    var sleep by remember { mutableStateOf(String.format(java.util.Locale.US, "%.1f", p.sleepGoalMin / 60.0)) }
    FormSheet("Profile & goals", onDismiss, onConfirm = {
        onSave(
            p.copy(
                name = name.trim().ifEmpty { p.name },
                waterGoalMl = water.num()?.toInt() ?: p.waterGoalMl,
                kcalGoal = macros.kcal,
                proteinG = macros.p,
                carbsG = macros.c,
                fatG = macros.f,
                dailySpendCents = spend.num()?.let { (it * 100).roundToLong() } ?: p.dailySpendCents,
                learnGoalMin = learn.num()?.toInt() ?: p.learnGoalMin,
                sleepGoalMin = sleep.num()?.let { (it * 60).toInt() } ?: p.sleepGoalMin,
            )
        ); onDismiss()
    }) {
        LifeField(name, { name = it }, "Your name")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeField(water, { water = it }, "Water, ml", Modifier.weight(1f), KeyboardType.Number)
            LifeField(sleep, { sleep = it }, "Sleep, h", Modifier.weight(1f), KeyboardType.Decimal)
        }
        GhostButton("${macros.kcal} kcal · P ${macros.p} · C ${macros.c} · F ${macros.f} g  ›", Modifier.fillMaxWidth()) { macroSheet = true }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeField(spend, { spend = it }, "Daily spend, €", Modifier.weight(1f), KeyboardType.Decimal)
            LifeField(learn, { learn = it }, "Learning, min", Modifier.weight(1f), KeyboardType.Number)
        }
    }
    if (macroSheet) {
        val cur = p.copy(kcalGoal = macros.kcal, proteinG = macros.p, carbsG = macros.c, fatG = macros.f)
        com.lifeapi.app.ui.screens.body.nutrition.NutritionGoalsSheet(cur, onDismiss = { macroSheet = false }) { np ->
            macros = com.lifeapi.app.ui.screens.body.nutrition.MacroBalance.M(np.kcalGoal, np.proteinG, np.carbsG, np.fatG)
        }
    }
}
