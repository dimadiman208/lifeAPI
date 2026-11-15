package com.lifeapi.app.ui.screens.grow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.hhmm
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.ScreenGlow
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow.FocusPhase
import com.lifeapi.app.vm.grow.FocusVM
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun FocusScreen(taskId: Long?, projectId: Long?, onBack: () -> Unit, vm: FocusVM = viewModel()) {
    LaunchedEffect(taskId, projectId) { vm.bind(taskId, projectId) }
    val info by vm.info.collectAsStateWithLifecycle()
    val t by vm.timer.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(Days.now()) }
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) { while (true) { now = Days.now(); vm.tick(now); delay(250) } }
    LaunchedEffect(info != null) {
        if (info != null && !autoStarted && t.phase == FocusPhase.Ready) { autoStarted = true; vm.start() }
    }
    LaunchedEffect(t.phase) { if (t.phase != FocusPhase.Ready) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    val running = (t.phase == FocusPhase.Focus || t.phase == FocusPhase.Break) && t.pausedLeft == null
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { vm.stop(onBack) }

    val i = info
    val left = vm.left(now)
    val total = t.totalMs.coerceAtLeast(1)
    val isBreak = t.phase == FocusPhase.Break
    val hue = if (isBreak) C.Health else C.Grow
    val mm = left / 60_000; val ss = (left / 1000) % 60

    Box(Modifier.fillMaxSize()) {
        ScreenGlow(hue, 0.10f)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Ic.ChevronLeft, "Back", size = 40.dp) { vm.stop(onBack) }
                Spacer(Modifier.weight(1f))
                MonoLabel(
                    when (t.phase) {
                        FocusPhase.Break -> "BREAK"
                        else -> "FOCUS · ${((i?.todayCount ?: 0) + if (t.phase == FocusPhase.Focus) 1 else 0).coerceAtMost(99)} OF 4 TODAY"
                    }, hue,
                )
                Spacer(Modifier.weight(1f)); Spacer(Modifier.size(40.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(i?.subtitle ?: "", style = T.small, color = C.InkMuted)
            Text(i?.title ?: "", style = T.title, color = C.Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp), maxLines = 3)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                val frac = if (t.phase == FocusPhase.Ready || t.phase == FocusPhase.Finished) 1f else left.toFloat() / total
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 10.dp.toPx()
                    val d = size.width - sw
                    drawArc(C.Track, 0f, 360f, false, Offset(sw / 2, sw / 2), Size(d, d), style = Stroke(sw))
                    drawArc(hue, -90f, 360f * frac, false, Offset(sw / 2, sw / 2), Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (t.phase) {
                            FocusPhase.Ready -> "${i?.focusMin ?: 25}:00"
                            FocusPhase.Finished -> "Done"
                            else -> String.format(Locale.US, "%d:%02d", mm, ss)
                        },
                        style = T.display.copy(fontSize = 56.sp, lineHeight = 60.sp), color = C.Ink,
                    )
                    Text(
                        when {
                            t.phase == FocusPhase.Finished -> "break over"
                            t.pausedLeft != null -> "paused"
                            t.phase == FocusPhase.Focus -> "focusing · ends ${hhmm(Days.timeOf(t.endTs))}"
                            t.phase == FocusPhase.Break -> "break · back at ${hhmm(Days.timeOf(t.endTs))}"
                            else -> "ready"
                        },
                        style = T.small, color = C.InkMuted,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            when (t.phase) {
                FocusPhase.Focus, FocusPhase.Break -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PillButton(if (t.pausedLeft == null) "Pause" else "Resume", hue, C.Graphite0, Modifier.weight(1f), 52.dp, 16.dp,
                            icon = if (t.pausedLeft == null) GrowIc.Pause else Ic.Play, style = T.section) { vm.pauseResume() }
                        PillButton("+5", C.Graphite2, C.Ink, height = 52.dp, radius = 16.dp, style = T.section) { vm.plus5() }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (isBreak) GhostButton("Skip break", Modifier.fillMaxWidth()) { vm.skipBreak() }
                    else GhostButton("Stop & log time", Modifier.fillMaxWidth()) { vm.stop(onBack) }
                }
                else -> {
                    PillButton(if (t.phase == FocusPhase.Finished) "Another round" else "Start", C.Grow, C.Graphite0, Modifier.fillMaxWidth(), 52.dp, 16.dp, style = T.section) { vm.start() }
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Close", Modifier.fillMaxWidth()) { vm.stop(onBack) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("${i?.focusMin ?: 25} / ${i?.breakMin ?: 5} · long break after 4 · time counts as Effort", style = T.caption, color = C.InkSubtle)
        }
    }
}
