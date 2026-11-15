package com.lifeapi.app.ui.screens.today

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** Detail routes owned by Today / Settings / Intelligence. Called from MainActivity's NavHost. */
fun NavGraphBuilder.todayGraph(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    composable("today/customize") { CustomizeScreen(back) }
    composable("insights") { InsightsScreen(back) }
    composable("insights/pattern/{key}", arguments = listOf(navArgument("key") { type = NavType.StringType })) { e ->
        PatternScreen(e.arguments?.getString("key").orEmpty(), back)
    }
    composable("settings") { SettingsScreen(back) }
    composable("settings/goals") { CustomizeScreen(back, goalsFirst = true) }
    composable("settings/notifications") { NotificationsScreen(back) }
    composable("settings/security") { SecurityScreen(back) }
    composable("settings/privacy") { PrivacyScreen(back) }
    composable("settings/integrations") { IntegrationsScreen(back) }
}

/** Wraps the whole app UI; shows the PIN / fingerprint lock when enabled. */
@Composable
fun AppLockGate(content: @Composable () -> Unit) = AppLockGateImpl(content)
