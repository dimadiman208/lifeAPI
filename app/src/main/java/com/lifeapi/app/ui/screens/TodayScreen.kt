package com.lifeapi.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.fmtCents
import com.lifeapi.app.ui.components.Dot
import com.lifeapi.app.ui.components.HDivider
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LinearBar
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RingSpec
import com.lifeapi.app.ui.components.Rings
import com.lifeapi.app.ui.components.RollingText
import com.lifeapi.app.ui.components.SectionHeader
import com.lifeapi.app.ui.components.ValueUnit
import com.lifeapi.app.ui.components.animatedProgress
import com.lifeapi.app.ui.components.dashedBorder
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.TodayState
import com.lifeapi.app.vm.TodayVM
import com.lifeapi.app.vm.UpNextItem
import java.util.Locale
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lifeapi.app.ui.LocalActions
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.screens.today.TIc

fun fmtMins(m: Int): String = "${m / 60}h ${String.format(Locale.US, "%02d", m % 60)}m"

@Composable
fun TodayScreen(
    openStack: () -> Unit,
    openMeal: () -> Unit,
    openSpend: () -> Unit,
    openTab: (String) -> Unit,
    openProfile: () -> Unit,
    vm: TodayVM = viewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val st = s ?: return
    val nav = LocalNav.current
    val actions = LocalActions.current
    var weightOpen by remember { mutableStateOf(false) }
    val customize = { nav.navigate("today/customize") }
    ScreenList(glow = C.Volt) {
        item { Header(st, openProfile, openInsights = { nav.navigate("insights") }) }
        st.cards.forEach { id ->
            item(key = id) {
                Box(Modifier.animateItem()) {
                    when (id) {
                        "score" -> ScoreCard(st, customize)
                        "intel" -> IntelCard(st, customize) { key -> nav.navigate(if (key != null) "insights/pattern/${android.net.Uri.encode(key)}" else "insights") }
                        "quick" -> QuickTiles(st, customize, onTile = { t ->
                            when (t) {
                                "Water" -> vm.addWater(250)
                                "Stack" -> if (st.supplementsDue > 0) vm.takeAllDue() else openStack()
                                "Meal" -> openMeal()
                                "Expense" -> openSpend()
                                "Set" -> st.workout.activeId?.let { nav.navigate("workout/$it") } ?: openTab("train")
                                "Task" -> { actions.growSegment(1); openTab("grow") }
                                "Habit" -> st.firstHabit?.let { vm.doHabit(it.first, it.second) } ?: run { actions.growSegment(2); openTab("grow") }
                                "Weight" -> weightOpen = true
                            }
                        })
                        "next" -> UpNext(st, onTake = vm::takeSupplement, onHabit = vm::doHabit, onTask = vm::doTask, onLong = customize)
                        "stats" -> StatsGrid(st, openTab, customize)
                        "week" -> WeekCard(st, customize)
                        "project" -> ProjectCardView(st, customize) { actions.growSegment(0); openTab("grow") }
                        "money" -> MoneyCardView(st, customize) { openTab("money") }
                        "workout" -> WorkoutCardView(st, customize) { st.workout.activeId?.let { nav.navigate("workout/$it") } ?: openTab("train") }
                    }
                }
            }
        }
        item(key = "customize") {
            Row(
                Modifier.fillMaxWidth().height(48.dp).dashedBorder(C.Bar, 16.dp).clip(RoundedCornerShape(16.dp)).press(onClick = customize),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(TIc.Sliders, null, tint = C.InkMuted, modifier = Modifier.size(16.dp))
                Text("Customize Today", style = T.body, color = C.InkMuted)
            }
        }
    }
    if (weightOpen) WeightSheet(st.lastWeight, onDismiss = { weightOpen = false }) { vm.logWeight(it) }
}

@Composable
private fun Header(st: TodayState, openProfile: () -> Unit, openInsights: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(st.dateLabel, style = T.mono.copy(letterSpacing = 0.5.sp), color = C.InkSubtle)
            Text("${st.greeting}, ${st.name}", style = T.headline.copy(fontSize = 26.sp), color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF151A0C)).border(1.dp, Color(0xFF2B3318), CircleShape).press(onClick = openInsights),
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.Sparkle, "LifeAPI Intelligence", tint = C.Volt, modifier = Modifier.size(20.dp)) }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF26282E)).press(onClick = openProfile),
                contentAlignment = Alignment.Center,
            ) { Text(st.name.take(1).uppercase(), style = T.section, color = C.Ink) }
        }
    }
}

@Composable
private fun IntelCard(st: TodayState, onLong: () -> Unit, open: (String?) -> Unit) {
    val ins = st.insight
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF12140F)).border(1.dp, Color(0xFF262D18), RoundedCornerShape(20.dp))
            .press(onLongClick = onLong) { open(ins?.second) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Sparkle, null, tint = C.Volt, modifier = Modifier.size(13.dp))
            Text("INTELLIGENCE · TODAY", style = T.label, color = C.Volt, modifier = Modifier.padding(start = 6.dp).weight(1f))
            Text(if (st.aiOn) "On this phone" else "Off", style = T.caption, color = C.InkSubtle)
        }
        Text(
            when {
                !st.aiOn -> "Intelligence is off. Pick what it may read in Settings — it never leaves this phone."
                ins != null -> ins.first
                else -> "Keep logging for a week or two — patterns between sleep, food, training and spending show up here."
            },
            style = T.bodyR.copy(lineHeight = 20.sp), color = C.Ink,
        )
    }
}

@Composable
private fun ScoreCard(st: TodayState, onLong: () -> Unit) {
    LifeCard(
        Modifier.fillMaxWidth().press(onLongClick = onLong) {}, radius = 24.dp, brush = com.lifeapi.app.ui.components.heroBrush(Color(0xFF16171B)),
        padding = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Rings(
                listOf(
                    RingSpec(st.score.body, C.Health, Color(0xFF1E2A2A)),
                    RingSpec(st.score.fuel, C.Fuel, C.FuelTint),
                    RingSpec(st.score.grow, C.Grow, C.GrowTint),
                ),
                size = 132.dp, stroke = 9.dp,
            ) { RollingText(st.score.score.toString(), T.headline.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MonoLabel(if (st.goalsTotal > 0) "Day score · ${st.goalsHit} of ${st.goalsTotal} goals" else "Day score")
                    Text(
                        when {
                            st.scoreDelta == null -> if (st.score.hasData) "Keep logging to see trends" else "Log anything to start"
                            st.scoreDelta >= 0 -> "+${st.scoreDelta} vs. your 30-day avg"
                            else -> "${st.scoreDelta} vs. your 30-day avg"
                        },
                        style = T.small, color = C.InkMuted,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Legend(C.Health, "Body", "${(st.score.body * 100).toInt()}%")
                    Legend(C.Fuel, "Fuel", String.format(Locale.US, "%,d kcal", st.kcal))
                    Legend(C.Grow, "Grow", if (st.habitsTotal > 0) "${st.habitsDone} / ${st.habitsTotal} habits" else "${st.learnToday} min")
                }
            }
        }
    }
}

@Composable
private fun Legend(c: Color, name: String, v: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(c); Text(name, style = T.small, color = C.Ink, modifier = Modifier.padding(start = 8.dp).weight(1f))
        Text(v, style = T.small, color = C.InkMuted)
    }
}

@Composable
private fun QuickTiles(st: TodayState, onLong: () -> Unit, onTile: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Quick log", "1 tap")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            st.tiles.forEach { t ->
                val m = Modifier.weight(1f)
                when (t) {
                    "Water" -> Tile(Ic.Drop, C.Health, "+250 ml", "Water", m, Color(0xFF111917), Color(0xFF1F2A29), onLong) { onTile(t) }
                    "Stack" -> Tile(Ic.Pill, C.Health, if (st.supplementsDue > 0) "${st.supplementsDue} due" else if (st.stackLeft > 0) "${st.stackLeft} left" else "Stack", "Supplements", m, onLong = onLong) { onTile(t) }
                    "Meal" -> Tile(Ic.Fork, C.Fuel, st.nextMealSlot, "Meal", m, onLong = onLong) { onTile(t) }
                    "Expense" -> Tile(Ic.Receipt, C.Money, "Spend", "Expense", m, onLong = onLong) { onTile(t) }
                    "Set" -> Tile(Ic.Train, C.Train, if (st.workout.activeId != null) "Resume" else "Set", "Workout", m, onLong = onLong) { onTile(t) }
                    "Task" -> Tile(TIc.Task, C.Grow, "Task", "Plan", m, onLong = onLong) { onTile(t) }
                    "Habit" -> Tile(Ic.Check, C.Grow, st.firstHabit?.second ?: "Habits", if (st.firstHabit != null) "Tap = done" else "Habit", m, onLong = onLong) { onTile(t) }
                    "Weight" -> Tile(TIc.Scale, C.Health, st.lastWeight?.let { "${com.lifeapi.app.data.fmtKg(it)} kg" } ?: "Weigh-in", "Weight", m, onLong = onLong) { onTile(t) }
                }
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, hue: Color, title: String, sub: String, modifier: Modifier, bg: Color = C.Graphite1, border: Color = C.Hairline, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(
        modifier.height(88.dp).clip(RoundedCornerShape(18.dp)).background(bg).border(1.dp, border, RoundedCornerShape(18.dp))
            .press(haptic = true, onLongClick = onLong, onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = hue, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = T.smallB, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = T.tiny.copy(fontWeight = FontWeight.Normal), color = C.InkSubtle, maxLines = 1)
        }
    }
}

@Composable
private fun UpNext(st: TodayState, onTake: (Long) -> Unit, onHabit: (Long, String) -> Unit, onTask: (Long) -> Unit, onLong: () -> Unit) {
    LifeCard(Modifier.fillMaxWidth().press(onLongClick = onLong) {}, padding = PaddingValues(0.dp), gap = 0.dp) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Up next", style = T.section, color = C.Ink, modifier = Modifier.weight(1f))
            MonoLabel(if (st.upNext.isEmpty()) "All clear" else "${st.upNext.size} left today")
        }
        if (st.upNext.isEmpty()) {
            Text(
                "Nothing pending. Supplements, tasks and habits queue up here as you add them.",
                style = T.small, color = C.InkMuted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
        st.upNext.take(5).forEachIndexed { i, it ->
            if (i > 0) HDivider(start = 72.dp)
            UpRow(it, onTake, onHabit, onTask)
        }
        if (st.upNext.isNotEmpty()) Box(Modifier.height(4.dp))
    }
}

@Composable
private fun UpRow(it: UpNextItem, onTake: (Long) -> Unit, onHabit: (Long, String) -> Unit, onTask: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(it.time.ifEmpty { "—" }, style = T.mono, color = C.InkMuted, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(it.title, style = T.body, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(it.sub, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (it.kind) {
            UpNextItem.SUPP -> PillButton("Take", C.Volt, C.Graphite0, height = 36.dp) { onTake(it.refId) }
            else -> Box(
                Modifier.size(44.dp).press(haptic = true) { if (it.kind == UpNextItem.TASK) onTask(it.refId) else onHabit(it.refId, it.title) },
                contentAlignment = Alignment.Center,
            ) {
                val shape = if (it.kind == UpNextItem.TASK) RoundedCornerShape(8.dp) else CircleShape
                Box(Modifier.size(if (it.kind == UpNextItem.TASK) 24.dp else 28.dp).clip(shape).border(BorderStroke(1.5.dp, C.Grow.copy(alpha = 0.8f)), shape))
            }
        }
    }
}

@Composable
private fun StatsGrid(st: TodayState, openTab: (String) -> Unit, onLong: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Water", Modifier.weight(1f), { openTab("body") }, onLong) {
                ValueUnit(String.format(Locale.US, "%.2f", st.waterMl / 1000f), String.format(Locale.US, "/ %.1f L", st.waterGoal / 1000f))
                LinearBar(st.waterMl.toFloat() / st.waterGoal, C.Health, Color(0xFF1C2524))
            }
            StatCard("Sleep", Modifier.weight(1f), { openTab("body") }, onLong) {
                ValueUnit(st.sleepLast?.let { fmtMins(it.minutes) } ?: "—", null)
                SleepSpark(st.sleepWeek)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Spent today", Modifier.weight(1f), { openTab("money") }, onLong) {
                ValueUnit(fmtCents(st.spentTodayCents), null)
                val left = st.dailyBudgetCents - st.spentTodayCents
                Text(
                    if (left >= 0) "${fmtCents(left)} left of daily ${fmtCents(st.dailyBudgetCents).removeSuffix(".00")}"
                    else "${fmtCents(-left)} over daily ${fmtCents(st.dailyBudgetCents).removeSuffix(".00")}",
                    style = T.caption, color = if (left >= 0) C.InkMuted else C.Negative, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            StatCard("Learning", Modifier.weight(1f), { com.lifeapi.app.ui.Segments.grow.intValue = 2; openTab("grow") }, onLong) {
                ValueUnit(st.learnStreak.toString(), "day streak")
                Text("${st.learnToday} of ${st.learnGoal} min today", style = T.caption, color = C.InkMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, modifier: Modifier, onClick: () -> Unit, onLong: () -> Unit, content: @Composable () -> Unit) {
    LifeCard(modifier.press(onLongClick = onLong, onClick = onClick), padding = PaddingValues(14.dp), gap = 12.dp) {
        MonoLabel(label)
        content()
    }
}

@Composable
private fun SleepSpark(mins: List<Int>) {
    val p = animatedProgress(1f)
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val n = mins.size
        val gap = 7.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        val max = (mins.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        mins.forEachIndexed { i, m ->
            val h = if (m == 0) 3.dp.toPx() else (size.height * (m / max) * p).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                if (i == n - 1) C.Health else C.Ghost, Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun WeekCard(st: TodayState, onLong: () -> Unit) {
    LifeCard(Modifier.fillMaxWidth().press(onLongClick = onLong) {}, gap = 14.dp) {
        SectionHeader("This week", if (st.weekAvg > 0) "avg score ${st.weekAvg}" else null, C.InkMuted)
        Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            st.week.forEachIndexed { i, (label, score) ->
                val today = i == st.todayIndex
                val p = animatedProgress((score ?: 0) / 100f)
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        when {
                            score == null -> Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f).dashedBorder(C.Bar, 6.dp))
                            else -> Box(
                                Modifier.fillMaxWidth().fillMaxHeight((p).coerceAtLeast(0.05f)).clip(RoundedCornerShape(6.dp))
                                    .background(if (today) C.Volt else C.Bar),
                            )
                        }
                    }
                    Text(label, style = T.tiny.copy(fontWeight = if (today) FontWeight.SemiBold else FontWeight.Normal), color = if (today) C.Ink else C.InkSubtle)
                }
            }
        }
    }
}

@Composable
private fun ProjectCardView(st: TodayState, onLong: () -> Unit, onOpen: () -> Unit) {
    val p = st.project
    LifeCard(Modifier.fillMaxWidth().press(onLongClick = onLong, onClick = onOpen), gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(if (p != null) "Project · ${p.name}" else "Project", C.Grow, Modifier.weight(1f))
            if (p?.pct != null) Text("${p.pct}%", style = T.smallB, color = C.Ink)
        }
        if (p == null) Text("No projects yet. Add one in Grow → Projects and its next action shows here.", style = T.small, color = C.InkMuted)
        else {
            MonoLabel(if (p.stalledDays >= 2) "Next action · stalled ${p.stalledDays} days" else "Next action", if (p.stalledDays >= 2) C.Fuel else C.InkSubtle)
            Text(p.next.ifBlank { "Set a next action" }, style = T.body.copy(fontSize = 15.sp), color = C.Ink)
            if (p.pct != null) LinearBar(p.pct / 100f, C.Grow, C.GrowTint)
        }
    }
}

@Composable
private fun MoneyCardView(st: TodayState, onLong: () -> Unit, onOpen: () -> Unit) {
    val m = st.money
    fun mask(v: String) = if (m.hide) "€••••" else v
    LifeCard(Modifier.fillMaxWidth().press(onLongClick = onLong, onClick = onOpen), gap = 10.dp) {
        MonoLabel("Money · today", C.Money)
        Row(verticalAlignment = Alignment.Bottom) {
            ValueUnit(fmtCents(m.leftTodayCents.coerceAtLeast(0)), if (m.leftTodayCents >= 0) "left of ${fmtCents(m.dailyCents).removeSuffix(".00")} daily" else "over by ${fmtCents(-m.leftTodayCents)}")
        }
        LinearBar(((m.dailyCents - m.leftTodayCents).toFloat() / m.dailyCents.coerceAtLeast(1)).coerceIn(0f, 1f), if (m.leftTodayCents >= 0) C.Money else C.Negative, C.MoneyTint)
        Row {
            Text(m.netWorthCents?.let { "Net worth ${mask(fmtCents(it).removeSuffix(".00"))}" } ?: "Add accounts in Money for net worth", style = T.caption, color = C.InkMuted, modifier = Modifier.weight(1f))
            m.monthBudgetLeft?.let { Text("Budgets ${if (it >= 0) "${fmtCents(it).removeSuffix(".00")} left" else "${fmtCents(-it).removeSuffix(".00")} over"}", style = T.caption, color = if (it >= 0) C.InkMuted else C.Negative) }
        }
    }
}

@Composable
private fun WorkoutCardView(st: TodayState, onLong: () -> Unit, onOpen: () -> Unit) {
    val w = st.workout
    LifeCard(Modifier.fillMaxWidth().press(onLongClick = onLong, onClick = onOpen), gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(when { w.activeId != null -> "Workout · in progress"; w.doneToday -> "Workout · done today"; else -> "Today · ${w.nextName ?: "Train"}" }, C.Train, Modifier.weight(1f))
            Text("${w.weekSessions} this week", style = T.caption, color = C.InkMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(w.nextName ?: "No templates yet", style = T.section, color = C.Ink)
                Text(if (w.nextName != null) "${w.exCount} exercises · ${com.lifeapi.app.vm.fmtTonnes(w.weekVolume)} this week" else "Create one in Train", style = T.caption, color = C.InkSubtle)
            }
            PillButton(if (w.activeId != null) "Resume" else "Open", C.Train, C.Graphite0, height = 36.dp, icon = Ic.Play, onClick = onOpen)
        }
    }
}

@Composable
private fun WeightSheet(last: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var v by remember { mutableStateOf(last?.let { com.lifeapi.app.data.fmtKg(it) } ?: "") }
    val kg = v.replace(',', '.').toDoubleOrNull()
    com.lifeapi.app.ui.components.FormSheet("Log weight", onDismiss, confirm = "Save", confirmColor = C.Health, canConfirm = kg != null && kg in 20.0..400.0, onConfirm = { onSave(kg!!); onDismiss() }) {
        com.lifeapi.app.ui.components.LifeField(v, { v = it }, "Weight, kg", keyboard = androidx.compose.ui.text.input.KeyboardType.Decimal, placeholder = last?.let { "Last: ${com.lifeapi.app.data.fmtKg(it)} kg" })
    }
}
