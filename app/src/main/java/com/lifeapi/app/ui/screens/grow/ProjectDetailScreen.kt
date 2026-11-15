package com.lifeapi.app.ui.screens.grow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Milestone
import com.lifeapi.app.data.grow.GrowCalc
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.HDivider
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.NameSheet
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.SectionHeader
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow.ProjectsVM

@Composable
fun ProjectDetailScreen(id: Long, onBack: () -> Unit, vm: ProjectsVM = viewModel()) {
    LaunchedEffect(id) { vm.bind(id) }
    val d by vm.detail.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var edit by remember { mutableStateOf(false) }
    var nextSheet by remember { mutableStateOf(false) }
    var askNext by remember { mutableStateOf(false) }
    var msNew by remember { mutableStateOf(false) }
    var msEdit by remember { mutableStateOf<Milestone?>(null) }
    var taskFor by remember { mutableStateOf<Long?>(null) } // milestone id, or -1 for loose
    val det = d ?: return
    val r = det.row
    val p = r.p

    DetailList(
        p.name,
        buildList {
            add("Started ${GrowCalc.shortDate(p.startDay)}")
            p.targetDay?.let { add("target ${GrowCalc.shortDate(it)}") }
        }.joinToString(" · "),
        onBack,
        trailing = {
            Text("${r.pct}%", style = T.title.copy(fontSize = 20.sp), color = C.Grow)
            RoundIconButton(Ic.Edit, "Edit project", size = 40.dp) { edit = true }
        },
    ) {
        item(key = "next") {
            LifeCard(Modifier.fillMaxWidth(), radius = 20.dp, border = GrowBorder, padding = PaddingValues(0.dp), gap = 0.dp) {
                NextActionBox(p.nextAction, r.stalledDays, compact = false) {
                    if (p.nextAction.isBlank()) {
                        PillButton("Set next action", C.Grow, C.Graphite0, Modifier.fillMaxWidth(), 44.dp, 14.dp) { nextSheet = true }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("Focus ${det.focusMin} min", C.Grow, C.Graphite0, Modifier.weight(1f), 44.dp, 14.dp, icon = GrowIc.Timer) {
                                nav.navigate("grow/focus?project=${p.id}")
                            }
                            PillButton("Done", androidx.compose.ui.graphics.Color.Transparent, C.Grow, height = 44.dp, radius = 14.dp,
                                border = androidx.compose.foundation.BorderStroke(1.dp, GrowBorder)) {
                                vm.completeNext(p); askNext = true
                            }
                        }
                        Text("Tap to rephrase", style = T.caption, color = C.InkSubtle, modifier = Modifier.press { nextSheet = true })
                    }
                }
            }
        }
        item(key = "dims") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DimTile("CONSISTENCY", "${r.daysWorked14} / 14", "days worked", Modifier.weight(1f))
                DimTile(
                    "EFFORT", GrowCalc.hours(r.effortMin),
                    if (r.effortDeltaMin == 0) "this week" else "${GrowCalc.signedHours(r.effortDeltaMin)} vs last wk", Modifier.weight(1f),
                    subColor = if (r.effortDeltaMin < 0) C.Negative else if (r.effortDeltaMin > 0) C.Positive else C.InkSubtle,
                )
                DimTile(
                    "OUTPUT", "${r.output} task${if (r.output == 1) "" else "s"}",
                    listOfNotNull(if (r.outputDelta != 0) (if (r.outputDelta > 0) "+" else "−") + kotlin.math.abs(r.outputDelta) else null,
                        if (r.milestonesDone > 0) "${r.milestonesDone} milestone${if (r.milestonesDone > 1) "s" else ""}" else null).joinToString(" · ").ifEmpty { "this week" },
                    Modifier.weight(1f),
                )
            }
        }
        item(key = "ms-head") { SectionHeader("Milestones", "+ Add", onTrailing = { msNew = true }) }
        val cur = det.current
        if (cur == null && det.milestones.isEmpty()) {
            item(key = "ms-empty") { EmptyLine("Break the project into milestones — each one a shippable chunk with its own tasks.") }
        }
        if (cur != null) item(key = "ms-cur") {
            LifeCard(Modifier.fillMaxWidth(), radius = 20.dp, gap = 4.dp, padding = PaddingValues(start = 4.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)) {
                Row(Modifier.padding(start = 12.dp).press(onLongClick = { msEdit = cur.m }) { msEdit = cur.m }, verticalAlignment = Alignment.CenterVertically) {
                    Text(cur.m.name, style = T.section, color = C.Ink, modifier = Modifier.weight(1f))
                    Text("${cur.done} / ${cur.tasks.size}", style = T.smallB, color = C.Grow)
                }
                cur.tasks.forEachIndexed { i, t ->
                    if (i > 0) HDivider(start = 48.dp)
                    TaskLine(t.title, "", t.doneTs != null, C.Grow, onToggle = { vm.toggleTask(t) }, onOpen = { nav.navigate("grow/task/${t.id}") },
                        onLong = { vm.promoteToNext(p, t) })
                }
                Row(Modifier.padding(start = 12.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("+ Task", Modifier.weight(1f), height = 40.dp) { taskFor = cur.m.id }
                    if (cur.tasks.isNotEmpty() && cur.done == cur.tasks.size)
                        PillButton("Complete milestone", C.Grow, C.Graphite0, Modifier.weight(1f), 40.dp, 12.dp) { vm.setMilestoneDone(cur.m, true) }
                }
                det.next?.let { n ->
                    Text("Next: ${n.m.name}${if (n.tasks.isNotEmpty()) " (${n.tasks.size} task${if (n.tasks.size > 1) "s" else ""})" else ""}",
                        style = T.caption, color = C.InkSubtle, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
                }
                if (cur.tasks.isNotEmpty()) Text("Long-press a task to make it the next action.", style = T.caption, color = C.InkFaint, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
            }
        }
        val doneMs = det.milestones.filter { it.m.done }
        if (doneMs.isNotEmpty()) item(key = "ms-done") {
            LifeCard(Modifier.fillMaxWidth(), radius = 18.dp, gap = 8.dp) {
                MonoLabel("COMPLETED")
                doneMs.forEach { m ->
                    Row(Modifier.fillMaxWidth().press(onLongClick = { vm.setMilestoneDone(m.m, false) }) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Ic.Check, null, tint = C.Grow, modifier = Modifier.size(16.dp))
                        Text(m.m.name, style = T.small, color = C.InkMuted, modifier = Modifier.weight(1f))
                        Text("${m.tasks.size} tasks", style = T.caption, color = C.InkSubtle)
                    }
                }
            }
        }
        item(key = "loose") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Other tasks", "+ Add", onTrailing = { taskFor = -1 })
                if (det.looseTasks.isNotEmpty()) LifeCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 4.dp, end = 14.dp, top = 2.dp, bottom = 2.dp), gap = 0.dp) {
                    det.looseTasks.forEachIndexed { i, t ->
                        if (i > 0) HDivider(start = 48.dp)
                        TaskLine(t.title, t.day?.let { GrowCalc.dayLabel(it, com.lifeapi.app.data.Days.today()) } ?: "", false, C.Grow,
                            onToggle = { vm.toggleTask(t) }, onOpen = { nav.navigate("grow/task/${t.id}") }, onLong = { vm.promoteToNext(p, t) })
                    }
                }
            }
        }
        item(key = "gh") {
            Text("GitHub activity isn't connected in this offline build.", style = T.caption, color = C.InkFaint, modifier = Modifier.padding(start = 4.dp))
        }
    }

    if (edit) ProjectSheet(p, { edit = false }, vm::saveProject) { vm.archive(it); onBack() }
    if (nextSheet || askNext) NameSheet(
        if (askNext) "What's next?" else "Next action", "Next action", "The very next physical step", C.Grow,
        if (askNext) "" else p.nextAction, { nextSheet = false; askNext = false },
    ) { vm.setNext(p.copy(nextAction = "", nextSince = 0), it) }
    if (msNew) NameSheet("New milestone", "Milestone", "v0.4 · Widgets & reminders", C.Grow, "", { msNew = false }) { vm.addMilestone(p.id, it, det.milestones.size) }
    msEdit?.let { m ->
        NameSheet("Milestone", "Milestone", "", C.Grow, m.name, { msEdit = null }, onDelete = { vm.deleteMilestone(m) }) { vm.renameMilestone(m, it) }
    }
    taskFor?.let { ms ->
        NameSheet("New task", "Task", "Write migration test", C.Grow, "", { taskFor = null }) { vm.addTask(p.id, ms.takeIf { it > 0 }, it) }
    }
}
