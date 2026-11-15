package com.lifeapi.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController

/** App-wide actions any screen can trigger without threading callbacks through MainActivity. */
interface AppActions {
    /** Open the Quick Log sheet, optionally pre-filled. */
    fun quickLog(seed: String = "")
    /** Open the meal sheet with a draft. */
    fun meal(draft: String = "")
    /** Switch bottom tab: "today" | "body" | "money" | "train" | "grow". */
    fun tab(route: String)
    /** Body tab segment: 0 Health, 1 Nutrition. */
    fun bodySegment(seg: Int)
    /** Grow tab segment: 0 Projects, 1 Tasks, 2 Growth, 3 Learn. */
    fun growSegment(seg: Int)
}

val LocalNav = compositionLocalOf<NavHostController> { error("no nav") }
val LocalActions = compositionLocalOf<AppActions> { error("no actions") }

/**
 * The "Log" FAB is contextual: a screen can replace its label/action (Train → "Start", Grow·Plan hides it
 * because it has its own quick-add bar). Set while the calling composable is in composition.
 */
data class FabSpec(val label: String, val icon: ImageVector?, val hidden: Boolean = false, val onClick: () -> Unit)

/**
 * The global "Log" button (Quick Log sheet) is hidden for now. Flip to true to bring it back — the sheet, its VM
 * and all data paths are untouched. Screen overrides (e.g. Train → "Start") still show their own FAB.
 */
const val SHOW_LOG_FAB = false

object Fab {
    val override = mutableStateOf<FabSpec?>(null)

    /** Whether a FAB is drawn above the bottom bar right now (drives list padding and snackbar offset). */
    val visible: Boolean
        get() {
            val o = override.value
            return if (o != null) !o.hidden else SHOW_LOG_FAB
        }
}

@Composable
fun FabOverride(spec: FabSpec) {
    DisposableEffect(spec.label, spec.hidden) {
        Fab.override.value = spec
        onDispose { if (Fab.override.value?.label == spec.label) Fab.override.value = null }
    }
}

/** Tab sub-sections, kept across tab switches. */
object Segments {
    /** Body: 0 Health, 1 Nutrition */
    val body = androidx.compose.runtime.mutableIntStateOf(0)
    /** Grow: 0 Projects, 1 Tasks, 2 Growth, 3 Learn */
    val grow = androidx.compose.runtime.mutableIntStateOf(0)
}
