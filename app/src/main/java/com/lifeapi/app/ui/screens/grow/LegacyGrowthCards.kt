package com.lifeapi.app.ui.screens.grow

import com.lifeapi.app.ui.screens.ScreenList

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Goal
import com.lifeapi.app.data.Habit
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.ui.components.CheckCircle
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.GhostBars
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.HabitSheet
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LearningSheet
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LinearBar
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RingSpec
import com.lifeapi.app.ui.components.Rings
import com.lifeapi.app.ui.components.RollingText
import com.lifeapi.app.ui.components.SectionHeader
import com.lifeapi.app.ui.components.animatedProgress
import com.lifeapi.app.ui.components.heroBrush
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.GrowState
import com.lifeapi.app.vm.GrowVM
import com.lifeapi.app.vm.grow.PlanVM
import com.lifeapi.app.vm.grow.ProjectsVM
import com.lifeapi.app.ui.FabOverride
import com.lifeapi.app.ui.FabSpec
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.Segments
import com.lifeapi.app.ui.screens.grow.GoalsCard
import com.lifeapi.app.ui.screens.grow.GrowGoalSheet
import com.lifeapi.app.ui.screens.grow.JournalCard
import com.lifeapi.app.ui.screens.grow.ProjectSheet
import com.lifeapi.app.ui.screens.grow.QuickAddBar
import com.lifeapi.app.ui.screens.grow.QuickTaskSheet
import com.lifeapi.app.ui.screens.grow.ReviewCard
import com.lifeapi.app.ui.screens.grow.SkillsCard
import com.lifeapi.app.ui.screens.grow.planItems
import com.lifeapi.app.ui.screens.grow.projectItems
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.lifeapi.app.ui.components.Segmented
import java.util.Locale

/*
 * Legacy Growth cards (pre-v3), kept for reuse by the new Growth tab: habits (Today's Day Score and widgets still use
 * habits), learning-today ring, weekly habit insight, consistency heatmap. They read the old GrowVM.
 */

@Composable
fun LegacyLearningCard(st: GrowState, onLog: () -> Unit) {
    LifeCard(Modifier.fillMaxWidth(), radius = 24.dp, brush = heroBrush(Color(0xFF18141F)), padding = PaddingValues(20.dp), gap = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Rings(listOf(RingSpec(st.learnToday.toFloat() / st.learnGoal, C.Grow, C.GrowTint)), 96.dp, 8.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RollingText(st.learnToday.toString(), T.title.copy(fontSize = 24.sp))
                    Text("/ ${st.learnGoal} min", style = T.caption, color = C.InkMuted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoLabel("Learning today")
                if (st.learning.isEmpty()) Text("Nothing logged yet", style = T.small, color = C.InkMuted)
                st.learning.takeLast(3).forEach {
                    Row {
                        Text(it.topic, style = T.small, color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${it.minutes}m", style = T.small, color = C.InkMuted)
                    }
                }
                val left = st.learnGoal - st.learnToday
                if (left > 0 && st.learning.isNotEmpty()) Text("${left}m left", style = T.small, color = C.Grow)
            }
        }
        PillButton("Log learning", C.GrowTint, C.Grow, Modifier.fillMaxWidth(), 44.dp, 12.dp, icon = Ic.Book, onClick = onLog)
    }
}

@Composable
fun LegacyHabitsCard(st: GrowState, vm: GrowVM, onAdd: () -> Unit, onEdit: (Habit) -> Unit) {
    if (st.habits.isEmpty()) {
        EmptyCard(
            "Grow", C.Grow, "Pick a few habits",
            "Small daily things you want to keep. Tick them here or from Today — streaks and insights build themselves.",
            "Add habit", onAdd, "Use a starter set", vm::addStarterHabits,
        )
        return
    }
    LifeCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), gap = 0.dp) {
        Row(Modifier.padding(start = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Habits", style = T.section, color = C.Ink, modifier = Modifier.weight(1f))
            Text("last 7 days", style = T.label.copy(fontSize = 10.sp), color = C.InkSubtle)
        }
        st.habits.forEach { r ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).press(onLongClick = { onEdit(r.h) }) { onEdit(r.h) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckCircle(r.done, C.Grow, 28.dp, square = true) { vm.toggleHabit(r.h, !r.done) }
                Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.h.name, style = T.body, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.meta, style = T.caption, color = C.InkSubtle)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    r.week.forEachIndexed { i, d ->
                        val c = when { i == r.week.lastIndex && d -> C.Grow; d -> Color(0xFF7D66AB); else -> C.Graphite3 }
                        Box(Modifier.size(width = 6.dp, height = 18.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    }
                }
            }
        }
        GhostButton("+ Habit", Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, bottom = 8.dp), height = 40.dp, onClick = onAdd)
    }
}

@Composable
fun LegacyInsightCard(st: GrowState) {
    val ins = st.insight
    if (ins == null) {
        if (st.habits.isEmpty()) return
        LifeCard(Modifier.fillMaxWidth(), bg = Color(0xFF15131A), gap = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Ic.Sparkle, null, tint = C.Grow, modifier = Modifier.size(14.dp)); MonoLabel("Insights", C.Grow)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GhostBars(C.Grow, st.loggedDays.coerceAtMost(7)) }
            Text("Insights unlock with data", style = T.section, color = C.Ink)
            Text(
                "Log sleep and habits for a couple of weeks and we'll show what actually moves your days.",
                style = T.small, color = C.InkMuted,
            )
        }
        return
    }
    LifeCard(Modifier.fillMaxWidth(), bg = Color(0xFF15131A), gap = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Ic.Sparkle, null, tint = C.Grow, modifier = Modifier.size(14.dp)); MonoLabel("Weekly insight", C.Grow)
        }
        val parts = ins.text.split("%s")
        Text(
            buildAnnotatedString {
                append(parts[0])
                withStyle(SpanStyle(color = C.Grow)) { append(ins.highlight) }
                if (parts.size > 1) append(parts[1])
            },
            style = T.section.copy(fontSize = 17.sp, lineHeight = 24.sp), color = C.Ink,
        )
        val max = maxOf(ins.a, ins.b, 0.1f)
        listOf(Triple(ins.aLabel, ins.a, C.Grow), Triple(ins.bLabel, ins.b, Color(0xFF6B5A8A))).forEach { (l, v, c) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(l, style = T.caption, color = C.InkMuted, modifier = Modifier.width(76.dp))
                Box(Modifier.weight(1f)) { LinearBar(v / max, c, Color(0xFF1F1B27), height = 10.dp) }
                Text(String.format(Locale.US, "%.1f", v), style = T.smallB, color = C.Ink, modifier = Modifier.width(32.dp))
            }
        }
        Text("Average ${ins.unit} completed per day", style = T.caption, color = C.InkSubtle)
    }
}

@Composable
fun LegacyHeatmap(st: GrowState) {
    LifeCard(Modifier.fillMaxWidth(), gap = 12.dp) {
        SectionHeader("Consistency", if (st.bestStreak > 0) "best streak ${st.bestStreak} days" else null, C.InkMuted)
        val p = animatedProgress(1f)
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val cols = 26; val rows = 7
            val gap = 2.dp.toPx()
            val cw = (size.width - gap * (cols - 1)) / cols
            val ch = (size.height - gap * (rows - 1)) / rows
            st.heat.forEachIndexed { i, v ->
                val col = i / 7; val row = i % 7
                val c = when {
                    v == null -> C.Graphite0
                    v <= 0f -> C.Track
                    v < 0.4f -> Color(0xFF3F3557)
                    v < 0.8f -> Color(0xFF7D66AB)
                    else -> C.Grow
                }
                drawRoundRect(c.copy(alpha = c.alpha * p), Offset(col * (cw + gap), row * (ch + gap)), Size(cw, ch), CornerRadius(2.dp.toPx()))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            st.heatMonths.forEach { Text(it, style = T.tiny.copy(fontWeight = FontWeight.Normal), color = C.InkSubtle, modifier = Modifier.weight(1f)) }
            Text("Less ", style = T.tiny.copy(fontWeight = FontWeight.Normal), color = C.InkSubtle)
            listOf(C.Track, Color(0xFF3F3557), Color(0xFF7D66AB), C.Grow).forEach {
                Box(Modifier.padding(horizontal = 1.dp).size(9.dp).clip(RoundedCornerShape(2.dp)).background(it))
            }
            Text(" More", style = T.tiny.copy(fontWeight = FontWeight.Normal), color = C.InkSubtle)
        }
    }
}
