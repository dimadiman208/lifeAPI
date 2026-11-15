package com.lifeapi.app.ui.screens.grow.projects

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.LifeApp
import com.lifeapi.app.data.grow3.GrowStats
import com.lifeapi.app.data.grow3.projects.ProjectReport
import com.lifeapi.app.ui.components.ChipPicker
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow3.projects.ReportVM

/** Weekly / monthly report: per day goal → tasks → activity → result → achievements. Share or copy as text (1-on-1s). */
@Composable
fun ReportScreen(scope: Long, month: Boolean, onBack: () -> Unit, vm: ReportVM = viewModel()) {
    LaunchedEffect(scope, month) { vm.bind(scope, month) }
    val st by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current

    ScreenList(glow = C.Grow, bottomExtra = false) {
        val s = st
        item(key = "top") { GrowTop(if (s?.monthly == true) "Month report" else "Week report", s?.scopeLabel, onBack) }
        if (s == null) return@ScreenList
        item(key = "mode") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(listOf("Week", "Month"), if (s.monthly) 1 else 0) { vm.setMonthly(it == 1) }
                if (scope < 0) ChipPicker(listOf("All work projects", "All projects"), if (s.scopeLabel == "All projects") "All projects" else "All work projects", C.Grow) {
                    vm.setScope(if (it == "All projects") -2 else -1)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoundIconButton(Ic.ChevronLeft, "Previous", size = 34.dp, bg = C.Track) { vm.page(-1) }
                    Text(ProjectReport.rangeTitle(s.from, s.to), style = T.smallB, color = C.Ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    RoundIconButton(Ic.ChevronRight, "Next", size = 34.dp, bg = C.Track) { vm.page(1) }
                }
            }
        }
        item(key = "sum") {
            val w = s.stats
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("${w.tasksDone}", "tasks done", null, Modifier.weight(1f))
                StatTile("${w.goalsAchieved}/${w.goalsSet}", "goals met", null, Modifier.weight(1f))
                StatTile(GrowStats.fmtMinutes(w.loggedMinutes), "logged", null, Modifier.weight(1f))
                StatTile("${w.activeDays}", "active days", null, Modifier.weight(1f))
            }
        }
        item(key = "share") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Share", C.Grow, C.Graphite0, Modifier.weight(1f), 44.dp, 14.dp) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, s.title).putExtra(Intent.EXTRA_TEXT, s.text)
                    ctx.startActivity(Intent.createChooser(send, s.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PillButton("Copy text", C.Graphite2, C.InkSoft, Modifier.weight(1f), 44.dp, 14.dp) {
                    clip.setText(AnnotatedString(s.text))
                    (ctx.applicationContext as LifeApp).container.snack("Report copied")
                }
            }
        }
        if (s.days.isEmpty()) item(key = "empty") {
            Text("Nothing recorded in this period yet. Goals, finished tasks, activity and results show up here.", style = T.small, color = C.InkMuted, modifier = Modifier.padding(horizontal = 4.dp))
        }
        s.days.forEach { d ->
            item(key = "d-${d.day}") {
                LifeCard(Modifier.fillMaxWidth(), radius = 18.dp, gap = 6.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ProjectReport.dayTitle(d.day), style = T.section, color = C.Ink, modifier = Modifier.weight(1f))
                        if (d.minutes > 0) Text(GrowStats.fmtMinutes(d.minutes), style = T.caption, color = C.InkSubtle)
                    }
                    val multi = d.items.size > 1 || s.scopeLabel.startsWith("All")
                    d.items.forEach { it ->
                        if (multi) MonoLabel((it.projectId?.let { id -> s.projects[id]?.name } ?: "No project").uppercase(), projectColor(it.projectId?.let { id -> s.projects[id] }))
                        if (it.goal.isNotBlank()) Line("Goal", Kind.Goal, it.goal)
                        if (it.doneTasks.isNotEmpty()) Line("Done", C.InkMuted, it.doneTasks.joinToString("\n") { t -> "✓ ${t.title}" })
                        if (it.openTasks.isNotEmpty()) Line("Open", C.InkSubtle, it.openTasks.joinToString("\n") { t -> "· ${t.title}" })
                        val acts = it.activity.map { a -> a.text + if (a.blockId == null && (a.minutes ?: 0) > 0) " (${GrowStats.fmtMinutes(a.minutes!!)})" else "" } +
                            it.blocks.filter { b -> b.result.isNotBlank() || (b.actualMinutes ?: 0) > 0 }.map { b -> "${b.start}–${b.end} ${b.title}" + (b.actualMinutes?.let { m -> " (${GrowStats.fmtMinutes(m)})" } ?: "") + if (b.result.isNotBlank()) " → ${b.result}" else "" }
                        if (acts.isNotEmpty()) Line("Activity", Kind.Activity, acts.joinToString("\n") { a -> "• $a" })
                        if (it.result.isNotBlank()) Line("Result", Kind.Result, it.result)
                        it.achievements.forEach { a -> Text("★ ${a.title}" + if (a.detail.isNotBlank()) " — ${a.detail}" else "", style = T.smallB, color = Kind.Achievement) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, color: androidx.compose.ui.graphics.Color, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        KindLabel(label, color)
        Text(text, style = T.body, color = C.Ink)
    }
}
