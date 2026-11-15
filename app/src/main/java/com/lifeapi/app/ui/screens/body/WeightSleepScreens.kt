package com.lifeapi.app.ui.screens.body

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.vm.body.BodyHealthVM
import com.lifeapi.app.vm.body.HealthTab

val SLEEP_TAGS = listOf("Late caffeine", "Alcohol", "Screens off early", "Trained", "Late meal", "Stress")

/**
 * Deep-link routes (body/water, body/sleep, body/weight) show the same section as the Health tab — no separate
 * charts; trends live in the analytics card at the top of Body.
 */
@Composable
private fun SectionScreen(title: String, section: @Composable (HealthTab, BodyHealthVM) -> Unit) {
    val vm: BodyHealthVM = viewModel()
    val st by vm.state.collectAsStateWithLifecycle()
    ScreenList(glow = C.Health, bottomExtra = false) {
        item { DetailHeader(title) }
        val s = st ?: return@ScreenList
        item { section(s, vm) }
    }
}

@Composable fun WaterScreen() = SectionScreen("Water") { s, vm -> WaterSection(s, vm) }
@Composable fun SleepScreen() = SectionScreen("Sleep") { s, vm -> SleepSection(s, vm) }
@Composable fun WeightScreen() = SectionScreen("Weight") { s, vm -> WeightSection(s, vm) }
