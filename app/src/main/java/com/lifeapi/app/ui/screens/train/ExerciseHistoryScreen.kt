package com.lifeapi.app.ui.screens.train

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.fmtKg
import com.lifeapi.app.data.train.fmt1
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.components.LineChart
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.fmtTonnes
import com.lifeapi.app.vm.train.ExerciseHistoryVM

@Composable
fun ExerciseHistoryScreen(name: String, onBack: () -> Unit, vm: ExerciseHistoryVM = viewModel(key = "exhist-$name")) {
    LaunchedEffect(name) { vm.bind(name) }
    val nav = com.lifeapi.app.ui.LocalNav.current
    val s by vm.state.collectAsStateWithLifecycle()
    var metric by remember { mutableIntStateOf(0) }

    ScreenList(glow = null, bottomExtra = false) {
        item { TrainTopBar(name, onBack) }
        val st = s ?: return@ScreenList
        if (st.list.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Graphite1).border(1.dp, C.Hairline, RoundedCornerShape(20.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MonoLabel("No history yet", C.Train)
                    Text("Log a set of $name and its e1RM, best set and trend show up here.", style = T.small, color = C.InkMuted)
                }
            }
            return@ScreenList
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("e1RM", st.e1rm?.let(::fmt1) ?: "—",
                    st.e1rmDelta?.let { (if (it >= 0) "+" else "") + fmt1(it) + " · " + st.deltaLabel },
                    if ((st.e1rmDelta ?: 0.0) >= 0) C.Positive else C.Negative)
                MiniStat("Best set", st.best ?: "—", st.bestDate)
                MiniStat("Sessions", st.sessions.toString(), st.since)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Graphite1).border(1.dp, C.Hairline, RoundedCornerShape(20.dp))
                    .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("e1RM", "Top set", "Volume").forEachIndexed { i, l -> TrainChip(l, metric == i, 28.dp) { metric = i } }
                }
                val series = st.list.reversed().takeLast(24).map {
                    when (metric) { 0 -> it.e1rm; 1 -> it.top; else -> it.volume }.toFloat()
                }
                if (series.size >= 2) {
                    LineChart(series, C.Train, Modifier.fillMaxWidth().height(140.dp))
                    Row(Modifier.padding(horizontal = 4.dp)) {
                        Text(st.list.reversed().takeLast(24).first().date, style = T.tiny, color = C.InkSubtle, modifier = Modifier.weight(1f))
                        val last = series.last().toDouble()
                        Text(when (metric) { 0 -> fmt1(last) + " kg"; 1 -> fmtKg(last) + " kg"; else -> fmtTonnes(last) }, style = T.tiny, color = C.Ink)
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text("Trend appears after a second session", style = T.small, color = C.InkSubtle)
                    }
                }
            }
        }
        item {
            RowsCard {
                st.list.forEachIndexed { i, h ->
                    // tap a session → open that workout (edit a saved one)
                    DlRow(first = i == 0, modifier = Modifier.press { if (h.sessionId != 0L) nav.navigate("workout/${h.sessionId}") }) {
                        Text(h.date, style = T.small, color = C.InkMuted, modifier = Modifier.width(52.dp))
                        Text(h.sets, style = T.mono, color = C.Ink, modifier = Modifier.weight(1f))
                        if (h.pr) PrPill()
                    }
                }
            }
        }
    }
}
