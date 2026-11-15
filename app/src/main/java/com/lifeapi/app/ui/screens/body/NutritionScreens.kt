package com.lifeapi.app.ui.screens.body

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Food
import com.lifeapi.app.data.Meal
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.data.mealSlotFor
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.RollingText
import com.lifeapi.app.ui.components.Stepper
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.body.MealHistoryVM
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

// ===================================================================== history & goals

@Composable
fun MealHistoryScreen(vm: MealHistoryVM = viewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st
    ScreenList(glow = C.Fuel, bottomExtra = false) {
        item { DetailHeader("History & goals") }
        if (s == null) return@ScreenList
        item { FoodSourcesCard() }
        item {
            LifeCard(Modifier.fillMaxWidth(), gap = 12.dp) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Week ${s.weekNo} goals", style = T.section, color = C.Ink, modifier = Modifier.weight(1f))
                    Text(s.rangeLabel, style = T.caption, color = C.InkSubtle)
                }
                GoalStrip("Calories in range", "${s.kcalHitCount} of ${s.daysElapsed} days", s.kcalHits, C.Fuel, Color(0xFF3A3226))
                GoalStrip("Protein ≥ ${s.profile.proteinG} g", "${s.proteinHitCount} of ${s.daysElapsed} days", s.proteinHits, C.Money, Color(0xFF252A3D))
                Text(s.avgLine ?: "Nothing logged this week yet", style = T.caption, color = C.InkSubtle)
            }
        }
        if (s.days.isEmpty()) item {
            LifeCard(Modifier.fillMaxWidth()) {
                Text("No meals in the last 4 weeks", style = T.body, color = C.Ink)
                Text("Log meals from Nutrition or the Log button; each day shows here with its source.", style = T.caption, color = C.InkMuted)
            }
        } else item {
            GroupCard {
                s.days.forEachIndexed { i, d ->
                    Column {
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1A1B1F)))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.width(40.dp)) {
                                Text(d.dow, style = T.tiny.copy(fontWeight = FontWeight.Normal), color = C.InkSubtle)
                                Text("${d.d}", style = T.section, color = C.Ink)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(String.format(Locale.US, "%,d kcal", d.kcal), style = T.body, color = C.Ink)
                                Text(d.macro, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF17181C)).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                                Text(d.src, style = T.tiny, color = C.InkMuted)
                            }
                            Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(if (d.hit) C.Fuel else Color(0xFF3A3226)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalStrip(title: String, value: String, hits: List<Boolean?>, on: Color, off: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(title, style = T.small, color = C.Ink, modifier = Modifier.weight(1f))
            Text(value, style = T.small, color = C.InkMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            hits.forEach { h ->
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(when (h) { true -> on; false -> off; null -> C.Track }))
            }
        }
    }
}

/** Live FatSecret status card (tap → connection screen). */
@Composable
fun FoodSourcesCard() = com.lifeapi.app.ui.screens.body.fatsecret.FatSecretSourceCard()
