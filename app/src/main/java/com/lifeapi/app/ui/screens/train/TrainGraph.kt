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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lifeapi.app.LifeApp
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T

/**
 * Train routes:
 *  - train/templates
 *  - train/exercise/{name}   (name Uri-encoded)
 *  - train/summary/{id}      workout-complete review before saving
 */
fun NavGraphBuilder.trainGraph(nav: NavHostController) {
    composable("train/templates") {
        TemplatesScreen(onBack = { nav.popBackStack() }, onOpenWorkout = { nav.navigate("workout/$it") })
    }
    composable("train/exercise/{name}", arguments = listOf(navArgument("name") { type = NavType.StringType })) { e ->
        val name = android.net.Uri.decode(e.arguments?.getString("name") ?: "")
        ExerciseHistoryScreen(name, onBack = { nav.popBackStack() })
    }
    composable("train/summary/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
        val id = e.arguments?.getLong("id") ?: 0L
        WorkoutSummaryScreen(
            id,
            onDone = { nav.popBackStack() },
            onResume = { nav.navigate("workout/$id") { popUpTo("train/summary/{id}") { inclusive = true } } },
        )
    }
}

/** Open the summary for [id], replacing the workout screen in the back stack. */
fun NavHostController.openWorkoutSummary(id: Long) {
    navigate("train/summary/$id") { popUpTo("workout/{id}") { inclusive = true } }
}

/**
 * Slim bar above the bottom nav on every tab while a workout is open: "Push A · in progress · 7 sets  [Open]".
 * No clock — it's just the way back into the open workout.
 */
@Composable
fun PausedWorkoutBar(onOpen: (Long) -> Unit) {
    val container = (LocalContext.current.applicationContext as LifeApp).container
    val active by container.repo.dao.activeSession().collectAsStateWithLifecycle(null)
    val s = active ?: return
    val sets by remember(s.id) { container.repo.dao.setsFor(s.id) }.collectAsStateWithLifecycle(emptyList())
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TC.PausedBg)
            .border(1.dp, TC.WarmBorder, RoundedCornerShape(16.dp))
            .press { onOpen(s.id) }
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(C.Train))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("${s.name} · in progress", style = T.smallB, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${sets.size} set${if (sets.size == 1) "" else "s"} logged", style = T.caption, color = C.InkMuted)
        }
        Box(
            Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(C.Train)
                .press(haptic = true) { onOpen(s.id) }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Open", style = T.smallB.copy(fontWeight = FontWeight.Bold), color = C.Graphite0) }
    }
}
