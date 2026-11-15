package com.lifeapi.app.ui.screens.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.data.grow.GrowCalc
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LinearBar
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.SectionHeader
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow.ProjectRow

fun LazyListScope.projectItems(rows: List<ProjectRow>, onNew: () -> Unit, onOpen: (ProjectRow) -> Unit) {
    if (rows.isEmpty()) {
        item(key = "proj-empty") {
            EmptyCard(
                "Projects", C.Grow, "Start a project",
                "Anything bigger than a task — an app, a race, a move. Every project leads with one next action; consistency, effort and output are tracked separately.",
                "New project", onNew,
            )
        }
        return
    }
    item(key = "proj-head") { SectionHeader("Active projects", "${rows.size} · + New", onTrailing = onNew) }
    items(rows, key = { "proj-${it.p.id}" }) { r -> ProjectCard(r) { onOpen(r) } }
}

@Composable
fun ProjectCard(r: ProjectRow, onClick: () -> Unit) {
    LifeCard(Modifier.fillMaxWidth().press(onClick = onClick), radius = 20.dp, padding = PaddingValues(16.dp), gap = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.p.name, style = T.section.copy(fontSize = 17.sp), color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.pct}% · ${r.streakLabel}", style = T.caption, color = C.InkSubtle)
        }
        LinearBar(r.pct / 100f, C.Grow, GrowTrack, height = 4.dp)
        NextActionBox(r.p.nextAction, r.stalledDays, compact = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DimTile("CONSIST.", "${r.daysWorked14 * 100 / 14}%", "${r.daysWorked14} / 14 d", Modifier.weight(1f))
            DimTile("EFFORT", GrowCalc.hours(r.effortMin), "this week", Modifier.weight(1f))
            DimTile("OUTPUT", "${r.output}", if (r.output == 1) "task" else "tasks", Modifier.weight(1f))
        }
    }
}

@Composable
fun NextActionBox(text: String, stalled: Int, compact: Boolean, content: @Composable () -> Unit = {}) {
    val shape = RoundedCornerShape(if (compact) 14.dp else 20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(GrowCardTop, Color(0xFF131217))))
            .then(Modifier)
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp),
    ) {
        MonoLabel(if (stalled >= 2) "NEXT ACTION · STALLED $stalled DAYS" else "NEXT ACTION", C.Grow)
        if (text.isBlank()) Text("No next action — what's the very next step?", style = T.small, color = C.InkMuted)
        else Text(
            text, style = if (compact) T.body else T.section.copy(fontSize = 16.sp, lineHeight = 22.sp), color = C.Ink,
            maxLines = if (compact) 2 else 4, overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}
