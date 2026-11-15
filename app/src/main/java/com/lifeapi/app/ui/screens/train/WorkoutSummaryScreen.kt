package com.lifeapi.app.ui.screens.train

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.WorkoutSet
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.data.train.fmt1
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.FormSheet
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.train.WorkoutSummaryVM
import java.time.format.TextStyle
import java.util.Locale

/** "Workout complete" review: stats, PRs, per-exercise lines, edit sets, then Save or Discard. */
@Composable
fun WorkoutSummaryScreen(sessionId: Long, onDone: () -> Unit, onResume: () -> Unit, vm: WorkoutSummaryVM = viewModel(key = "summary-$sessionId")) {
    LaunchedEffect(sessionId) { vm.bind(sessionId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkoutSet?>(null) }
    val nav = LocalNav.current
    BackHandler { vm.resume(onResume) }

    ScreenList(glow = C.Train, bottomExtra = false) {
        val st = s ?: return@ScreenList
        item {
            Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MonoLabel("WORKOUT COMPLETE", C.Train)
                Text(st.session.name, style = T.headline.copy(fontSize = 26.sp), color = C.Ink)
                val d = Days.date(st.session.day)
                Text(
                    "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}",
                    style = T.small, color = C.InkMuted,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Volume", String.format(Locale.US, "%,.0f", st.volume),
                    st.volumeDelta?.let { (if (it >= 0) "+" else "−") + String.format(Locale.US, "%,.0f kg", kotlin.math.abs(it)) } ?: "kg",
                    when { st.volumeDelta == null -> C.InkSubtle; st.volumeDelta >= 0 -> C.Positive; else -> C.Negative }, center = true)
                MiniStat("Sets", st.sets.toString(), "${st.exercises.size} exercise${if (st.exercises.size == 1) "" else "s"}", center = true)
            }
        }
        st.prs.forEach { pr ->
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TC.WarmBg).border(1.dp, TC.WarmBorder, RoundedCornerShape(18.dp))
                        .press { nav.navigate("train/exercise/${android.net.Uri.encode(pr.name)}") }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.TrainTint), contentAlignment = Alignment.Center) {
                        Text("PR", style = T.caption.copy(fontWeight = FontWeight.Bold), color = TC.PrFg)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${pr.name} ${pr.set}", style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.Ink)
                        Text("e1RM ${fmt1(pr.e1rm)}" + (pr.beats?.let { " · beats ${fmt1(it)}" } ?: ""), style = T.caption, color = C.InkMuted)
                    }
                }
            }
        }
        item {
            Box(Modifier.animateContentSize()) {
                RowsCard {
                    if (st.exercises.isEmpty()) DlRow(first = true) { Text("No sets logged — saving will discard this workout.", style = T.small, color = C.InkMuted) }
                    val shown = if (expanded) st.exercises else st.exercises.take(3)
                    shown.forEachIndexed { i, e ->
                        DlRow(first = i == 0, modifier = Modifier.press { expanded = true }) {
                            Text(
                                buildAnnotatedString {
                                    append(e.name)
                                    if (e.isNew) withStyle(SpanStyle(fontSize = 11.sp, color = C.Train)) { append("  new") }
                                },
                                style = T.bodyR, color = C.Ink, modifier = Modifier.weight(1f),
                            )
                            Text(e.line, style = T.mono, color = C.InkMuted)
                        }
                        if (expanded) {
                            e.sets.forEachIndexed { j, set ->
                                Row(
                                    Modifier.fillMaxWidth().press { editing = set }.padding(start = 28.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("${j + 1}", style = T.caption, color = C.InkSubtle, modifier = Modifier.width(28.dp))
                                    Text("${fmtKg(set.kg)} kg × ${set.reps}", style = T.mono, color = C.InkSoft, modifier = Modifier.weight(1f))
                                    Text("Edit", style = T.caption, color = C.InkSubtle)
                                }
                            }
                        }
                    }
                    if (st.exercises.isNotEmpty()) {
                        DlRow(first = false, modifier = Modifier.press { expanded = !expanded }) {
                            Text(
                                when {
                                    expanded -> "Done editing"
                                    st.exercises.size > 3 -> "+ ${st.exercises.size - 3} more · Edit workout"
                                    else -> "Edit workout"
                                },
                                style = T.bodyR, color = C.InkMuted,
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, if (confirmDiscard) C.Negative else C.Bar, RoundedCornerShape(16.dp))
                        .press { if (confirmDiscard) vm.discard(onDone) else confirmDiscard = true },
                    contentAlignment = Alignment.Center,
                ) { Text(if (confirmDiscard) "Tap to confirm" else "Discard", style = T.body, color = if (confirmDiscard) C.Negative else C.InkMuted) }
                Box(
                    Modifier.weight(2f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(C.Train).press(haptic = true) { vm.save(onDone) },
                    contentAlignment = Alignment.Center,
                ) { Text("Save workout", style = T.section, color = C.Graphite0) }
            }
        }
        item {
            Text("Back to workout", style = T.small, color = C.InkSubtle, modifier = Modifier.fillMaxWidth().press { vm.resume(onResume) }.padding(8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    editing?.let { set ->
        var kg by remember(set.id) { mutableStateOf(fmtKg(set.kg)) }
        var reps by remember(set.id) { mutableStateOf(set.reps.toString()) }
        val k = kg.replace(',', '.').toDoubleOrNull()
        val r = reps.toIntOrNull()
        FormSheet("Edit set · ${set.exercise}", { editing = null }, confirmColor = C.Train, canConfirm = k != null && r != null && r > 0,
            onConfirm = { vm.editSet(set, k!!, r!!); editing = null },
            onDelete = { vm.deleteSet(set); editing = null }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeField(kg, { kg = it }, "Kg", Modifier.weight(1f), KeyboardType.Decimal)
                LifeField(reps, { reps = it }, "Reps", Modifier.weight(1f), KeyboardType.Number)
            }
        }
    }
}
