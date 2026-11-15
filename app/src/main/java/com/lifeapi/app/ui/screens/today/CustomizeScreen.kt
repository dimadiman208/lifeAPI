package com.lifeapi.app.ui.screens.today

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Profile
import com.lifeapi.app.ui.components.Dot
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.ProfileSheet
import com.lifeapi.app.ui.components.RollingText
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.BaseVM
import com.lifeapi.app.vm.QUICK_TILES
import com.lifeapi.app.vm.TODAY_CARDS
import com.lifeapi.app.vm.cardLayout
import com.lifeapi.app.vm.layoutString
import com.lifeapi.app.vm.tiles
import com.lifeapi.app.vm.today.GoalId
import com.lifeapi.app.vm.today.enabledGoals
import com.lifeapi.app.vm.today.goalTarget
import com.lifeapi.app.vm.today.goalsString
import com.lifeapi.app.vm.today.scoreFor
import com.lifeapi.app.vm.today.snapshotFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class CustomizeVM(app: Application) : BaseVM(app) {
    val snap = today.flatMapLatest { snapshotFlow(repo, it) }.state()

    fun save(p: Profile, layout: List<Pair<String, Boolean>>?, tiles: List<String>?, goals: Set<GoalId>?, onDone: () -> Unit) = launch {
        val cur = dao.profileNow() ?: p
        dao.saveProfile(
            cur.copy(
                todayCards = layout?.let(::layoutString) ?: cur.todayCards,
                quickTiles = tiles?.joinToString(",") ?: cur.quickTiles,
                dayGoals = goals?.let(::goalsString) ?: cur.dayGoals,
            )
        )
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
    }

    fun saveProfile(p: Profile) = launch { dao.saveProfile(p) }
}

fun hueColor(h: Int): Color = if (h < 0) C.Volt else C.hue(h)

/** Customize Today: cards (toggle + reorder), Quick Log tiles (pick 4) and Daily goals with live Day Score preview. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizeScreen(onBack: () -> Unit, goalsFirst: Boolean = false, vm: CustomizeVM = viewModel()) {
    val s by vm.snap.collectAsStateWithLifecycle()
    val snap = s ?: return
    val p = snap.p
    val layout = remember { mutableStateListOf<Pair<String, Boolean>>() }
    val tiles = remember { mutableStateListOf<String>() }
    var goals by remember { mutableStateOf<Set<GoalId>>(emptySet()) }
    var loaded by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!loaded) { layout.addAll(p.cardLayout()); tiles.addAll(p.tiles()); goals = p.enabledGoals(); loaded = true }
    }
    if (!loaded) return
    val preview = scoreFor(snap.day, snap.d, p, goals)
    val suppsToday = snap.d.suppsOn(snap.day).size

    DetailList(gap = 14.dp) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", style = T.body.copy(fontSize = 15.sp), color = C.InkMuted, modifier = Modifier.height(40.dp).press(onClick = onBack).padding(horizontal = 8.dp, vertical = 10.dp))
                Text("Customize Today", style = T.section.copy(fontSize = 16.sp), color = C.Ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                PillButton("Done", C.Volt, C.Graphite0, height = 40.dp, radius = 20.dp, style = T.smallB.copy(fontSize = 14.sp)) {
                    vm.save(p, layout.toList(), tiles.toList(), goals, onBack)
                }
            }
        }
        if (!goalsFirst) {
            item { Cap("Cards · ${layout.count { it.second }} shown") }
            item { CardsList(layout) }
            item { Cap("Quick log tiles · pick 4") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_TILES.forEach { t ->
                        val on = t in tiles
                        ChipToggle(t, on) {
                            if (on) { if (tiles.size > 1) tiles.remove(t) }
                            else { if (tiles.size >= 4) tiles.removeAt(0); tiles.add(t) }
                        }
                    }
                }
            }
            item { Text("The first four you pick show on Today. Long-press any card on Today to come back here.", style = T.caption, color = C.InkSubtle, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
        item {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Daily goals", style = T.title.copy(fontSize = 20.sp), color = C.Ink)
                Text("Day Score = how close you are to each enabled goal today, averaged. Each counts equally.", style = T.small, color = C.InkMuted)
            }
        }
        item {
            GroupCard {
                GoalId.entries.forEachIndexed { i, g ->
                    val on = g in goals
                    val prog = preview.parts[g]
                    Column {
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1A1B1F)))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Dot(C.hue(g.hue))
                            Column(Modifier.weight(1f)) {
                                Text(g.label, style = T.body, color = C.Ink)
                                if (on && prog != null) Text("Today ${(prog * 100).toInt()}%", style = T.caption, color = C.InkSubtle)
                            }
                            Box(
                                Modifier.height(32.dp).clip(RoundedCornerShape(10.dp)).background(C.Track).press { profileOpen = true }.padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(goalTarget(g, p, suppsToday, snap.d.habits.size), style = T.smallB, color = C.Ink) }
                            LifeSwitch(on) { goals = if (it) goals + g else goals - g }
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF17181C)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Today with these goals", style = T.small, color = C.InkMuted, modifier = Modifier.weight(1f))
                RollingText(if (goals.isEmpty() || !preview.hasData) "—" else preview.score.toString(), T.title.copy(fontSize = 18.sp))
            }
        }
        item { Text("Tap a target to change it. Goals that don't apply yet (no supplements or habits set up) are skipped.", style = T.caption, color = C.InkSubtle, modifier = Modifier.padding(horizontal = 4.dp)) }
        item {
            PillButton("Save goals", C.Volt, C.Graphite0, Modifier.fillMaxWidth(), height = 52.dp, radius = 16.dp, style = T.section) {
                vm.save(p, if (goalsFirst) null else layout.toList(), if (goalsFirst) null else tiles.toList(), goals, onBack)
            }
        }
    }
    if (profileOpen) ProfileSheet(p, onDismiss = { profileOpen = false }) { vm.saveProfile(it) }
}

@Composable
private fun CardsList(layout: MutableList<Pair<String, Boolean>>) {
    GroupCard {
        layout.forEachIndexed { i, (id, on) ->
            val def = TODAY_CARDS.first { it.id == id }
            Column {
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1A1B1F)))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(TIc.Grip, null, tint = Color(0xFF5A5F6A), modifier = Modifier.size(16.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(hueColor(def.hue)))
                    Column(Modifier.weight(1f).alpha(if (on) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(def.name, style = T.body, color = C.Ink)
                        Text(def.meta, style = T.tiny.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = C.InkSubtle)
                    }
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).press(enabled = i > 0) {
                        val x = layout.removeAt(i); layout.add(i - 1, x)
                    }, contentAlignment = Alignment.Center) { Icon(TIc.Up, "Move ${def.name} up", tint = if (i > 0) C.InkSubtle else C.Bar, modifier = Modifier.size(16.dp)) }
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).press(enabled = i < layout.size - 1) {
                        val x = layout.removeAt(i); layout.add(i + 1, x)
                    }, contentAlignment = Alignment.Center) { Icon(TIc.Down, "Move ${def.name} down", tint = if (i < layout.size - 1) C.InkSubtle else C.Bar, modifier = Modifier.size(16.dp)) }
                    LifeSwitch(on) { v -> layout[i] = id to v }
                }
            }
        }
    }
}
