package com.lifeapi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Meal
import com.lifeapi.app.data.hhmm
import com.lifeapi.app.ui.components.BarChart
import com.lifeapi.app.ui.components.BarDatum
import com.lifeapi.app.ui.components.CheckCircle
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LinearBar
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RingSpec
import com.lifeapi.app.ui.components.Rings
import com.lifeapi.app.ui.components.RollingText
import com.lifeapi.app.ui.components.SectionHeader
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.components.SmallChip
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.screens.body.BodyRoutes
import com.lifeapi.app.ui.components.ValueUnit
import com.lifeapi.app.ui.components.animatedProgress
import com.lifeapi.app.ui.components.heroBrush
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.NutritionState
import com.lifeapi.app.vm.NutritionVM
import java.util.Locale

@Composable
fun BodyScreen(seg: Int, onSeg: (Int) -> Unit, openMeal: () -> Unit) {
    val hvm: com.lifeapi.app.vm.body.BodyHealthVM = viewModel()
    val nvm: NutritionVM = viewModel()
    val hs by hvm.state.collectAsStateWithLifecycle()
    val ns by nvm.state.collectAsStateWithLifecycle()
    val nav = LocalNav.current

    ScreenList(glow = if (seg == 0) C.Health else C.Fuel) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Body", style = T.headline, color = C.Ink, modifier = Modifier.weight(1f))
                    SmallChip(Days.date(Days.today()).let { "Today, ${it.dayOfMonth} ${it.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)}" }, C.Graphite1, C.InkSoft)
                }
                Segmented(listOf("Health", "Nutrition"), seg, selBg = if (seg == 0) C.HealthTint else C.FuelTint, selFg = if (seg == 0) C.Health else C.Fuel, onSelect = onSeg)
            }
        }
        if (seg == 0) {
            item(key = "analytics") { com.lifeapi.app.ui.screens.body.BodyAnalyticsCard() }
            val st = hs ?: return@ScreenList
            item(key = "date") { com.lifeapi.app.ui.screens.body.supps.BodyDateBar(st, hvm) }
            item(key = "supps") { com.lifeapi.app.ui.screens.body.supps.SuppsSection(st, hvm) }
            item(key = "water") { com.lifeapi.app.ui.screens.body.WaterSection(st, hvm) }
            item(key = "weight") { com.lifeapi.app.ui.screens.body.WeightSection(st, hvm) }
            item(key = "sleep") { com.lifeapi.app.ui.screens.body.SleepSection(st, hvm) }
        } else {
            item(key = "n_analytics") { com.lifeapi.app.ui.screens.body.NutritionAnalyticsCard() }
            val st = ns ?: return@ScreenList
            item(key = "n_date") {
                com.lifeapi.app.ui.screens.body.CalendarBar(
                    mode = st.calMode, label = st.calLabel, cells = st.cal, selectedLabel = st.selectedLabel, isToday = st.isToday,
                    theme = com.lifeapi.app.ui.screens.body.FuelCal,
                    onPage = nvm::page, onMode = nvm::setCalMode, onSelect = nvm::select, onToday = nvm::selectToday,
                )
            }
            item { CaloriesCard(st) }
            item { com.lifeapi.app.ui.screens.body.nutrition.NutritionActions(st) }
            com.lifeapi.app.ui.screens.body.nutrition.SLOTS.forEach { slot ->
                item(key = "slot_$slot") { com.lifeapi.app.ui.screens.body.nutrition.SlotCard(slot, st, nvm) }
            }
            item {
                com.lifeapi.app.ui.components.GhostButton("History & weekly goals", Modifier.fillMaxWidth(), height = 40.dp) { nav.navigate(BodyRoutes.MEAL_HISTORY) }
            }
            item { com.lifeapi.app.ui.screens.body.nutrition.ManageGoalsButton(st, nvm) }
        }
    }

}

// ======================================================================= Nutrition


@Composable
private fun CaloriesCard(st: NutritionState) {
    val p = st.profile
    LifeCard(Modifier.fillMaxWidth(), radius = 24.dp, brush = heroBrush(Color(0xFF19160F)), padding = PaddingValues(20.dp), gap = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Rings(listOf(RingSpec(st.kcal.toFloat() / p.kcalGoal, C.Fuel, C.FuelTint)), 120.dp, 10.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val left = p.kcalGoal - st.kcal
                    RollingText(String.format(Locale.US, "%,d", kotlin.math.abs(left)), T.title.copy(fontSize = 24.sp))
                    Text(if (left >= 0) "kcal left" else "kcal over", style = T.caption, color = if (left >= 0) C.InkMuted else C.Negative)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KV("Eaten", String.format(Locale.US, "%,d", st.kcal))
                KV("Goal", String.format(Locale.US, "%,d", p.kcalGoal))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Macro("Protein", st.protein, p.proteinG, C.Money, C.MoneyTint, Modifier.weight(1f))
            Macro("Carbs", st.carbs, p.carbsG, C.Fuel, C.FuelTint, Modifier.weight(1f))
            Macro("Fat", st.fat, p.fatG, C.Train, Color(0xFF33211C), Modifier.weight(1f))
        }
    }
}

@Composable
private fun KV(k: String, v: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(k, style = T.caption, color = C.InkSubtle)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RollingText(v, T.title.copy(fontSize = 20.sp)); Text("kcal", style = T.caption, color = C.InkSubtle, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun Macro(name: String, v: Int, goal: Int, c: Color, track: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, style = T.caption, color = C.InkMuted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$v", style = T.section, color = C.Ink)
            Text(" / $goal g", style = T.caption, color = C.InkSubtle)
        }
        LinearBar(v.toFloat() / goal, c, track)
    }
}


@Suppress("unused")
private fun Modifier.square() = this.aspectRatio(1f)
