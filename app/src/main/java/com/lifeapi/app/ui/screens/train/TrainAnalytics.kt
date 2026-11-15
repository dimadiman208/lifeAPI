package com.lifeapi.app.ui.screens.train

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.ui.screens.body.AnalyticsFrame
import com.lifeapi.app.ui.screens.body.Bars
import com.lifeapi.app.ui.screens.body.CalTheme
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.train.TrainAnalyticsVM
import com.lifeapi.app.vm.train.TrainMetric

/** Calendar colours for Train (same shared CalendarBar as Body). */
val TrainCal = CalTheme(C.Train, C.TrainTint, Color(0xFF2A1F1C))

/** Train → the one analytics card at the top: same scaffold and look as Body's, Train hue. */
@Composable
fun TrainAnalyticsCard(vm: TrainAnalyticsVM = viewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val a = st?.takeIf { it.metric == metric && it.period == period }
    AnalyticsFrame(
        chips = TrainMetric.entries.map { it.label },
        chipWeights = TrainMetric.entries.map { if (it == TrainMetric.SESSIONS) 1.25f else 1f },
        selected = metric.ordinal, onChip = { vm.setMetric(TrainMetric.entries[it]) },
        period = period, onPeriod = vm::setPeriod,
        value = a?.value, unit = a?.unit, sub = a?.sub, delta = a?.delta, deltaGood = a?.deltaGood,
        hue = C.Train, top = Color(0xFF1C1513),
        chartKey = a?.let { "${it.metric}-${it.period}" },
    ) {
        val x = a
        when {
            x == null -> Unit
            x.empty -> Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                Text("Log a workout below to see your training here", style = T.caption, color = C.InkFaint)
            }
            else -> Bars(x.bars, null, null, "${x.metric}-${x.period}", C.Train)
        }
    }
}
