package com.lifeapi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.lifeapi.app.ui.Segments
import com.lifeapi.app.ui.components.Segmented
import com.lifeapi.app.ui.screens.grow.growth.GrowthTab
import com.lifeapi.app.ui.screens.grow.learn.LearnTab
import com.lifeapi.app.ui.screens.grow.projects.ProjectsTab
import com.lifeapi.app.ui.screens.grow.tasks.TasksTab
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T

/**
 * Grow = Projects (what I work on) · Tasks (every task across projects) · Growth (what I'm getting better at) · Learn (library).
 * Each tab owns its whole screen (its own ScreenList, VMs, sheets) and puts [header] first so the title and the
 * segmented switch look identical across tabs. See dev-docs/GROW_V3.md.
 */
@Composable
fun GrowScreen() {
    var seg by Segments.grow
    val header: LazyListScope.(subtitle: String) -> Unit = { subtitle ->
        item(key = "grow-head") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Grow", style = T.headline, color = C.Ink)
                if (subtitle.isNotBlank()) Text(subtitle, style = T.small, color = C.InkMuted)
            }
        }
        item(key = "grow-seg") { Segmented(listOf("Projects", "Tasks", "Growth", "Learn"), seg.coerceIn(0, 3)) { seg = it } }
    }
    when (seg) {
        0 -> ProjectsTab(header)
        1 -> TasksTab(header)
        2 -> GrowthTab(header)
        else -> LearnTab(header)
    }
}
