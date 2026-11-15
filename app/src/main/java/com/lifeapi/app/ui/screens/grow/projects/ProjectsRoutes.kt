package com.lifeapi.app.ui.screens.grow.projects

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** Routes of Grow → Projects (all prefixed "grow/projects/"). */
object ProjectsRoutes {
    const val PROJECT = "grow/projects/project/{id}"
    const val TIMELINE = "grow/projects/timeline?day={day}"
    const val REPORT = "grow/projects/report?scope={scope}&month={month}"

    fun project(id: Long) = "grow/projects/project/$id"
    /** day = -1 → today. */
    fun timeline(day: Long = -1) = "grow/projects/timeline?day=$day"
    /** scope: projectId, -1 = all work projects, -2 = all projects. */
    fun report(scope: Long, month: Boolean) = "grow/projects/report?scope=$scope&month=$month"
}

/** Detail routes of Grow → Projects. Owned by the Projects agent. */
fun NavGraphBuilder.projectsRoutes(nav: NavHostController) {
    composable(ProjectsRoutes.PROJECT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
        ProjectDetailScreen(e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
    }
    composable(ProjectsRoutes.TIMELINE, arguments = listOf(navArgument("day") { type = NavType.LongType; defaultValue = -1L })) { e ->
        TimelineScreen(e.arguments?.getLong("day")?.takeIf { it >= 0 }, onBack = { nav.popBackStack() })
    }
    composable(
        ProjectsRoutes.REPORT,
        arguments = listOf(navArgument("scope") { type = NavType.LongType; defaultValue = -1L }, navArgument("month") { type = NavType.BoolType; defaultValue = false }),
    ) { e ->
        ReportScreen(e.arguments?.getLong("scope") ?: -1L, e.arguments?.getBoolean("month") ?: false, onBack = { nav.popBackStack() })
    }
}
