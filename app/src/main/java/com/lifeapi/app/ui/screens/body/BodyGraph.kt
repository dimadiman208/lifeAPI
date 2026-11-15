package com.lifeapi.app.ui.screens.body

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** Routes owned by Body (Health + Nutrition). */
object BodyRoutes {
    fun supplement(id: Long) = "body/supplement/$id"
    const val WATER = "body/water"
    const val WEIGHT = "body/weight"
    const val SLEEP = "body/sleep"
    const val MEAL_HISTORY = "body/nutrition/history"
}

/** Detail routes owned by Body (Health + Nutrition). Called from MainActivity's NavHost. */
fun NavGraphBuilder.bodyGraph(nav: NavHostController) {
    composable("body/supplement/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
        SupplementDetailScreen(e.arguments?.getLong("id") ?: 0L)
    }
    composable(BodyRoutes.WATER) { WaterScreen() }
    composable(BodyRoutes.WEIGHT) { WeightScreen() }
    composable(BodyRoutes.SLEEP) { SleepScreen() }
    composable(BodyRoutes.MEAL_HISTORY) { MealHistoryScreen() }
    composable(com.lifeapi.app.ui.screens.body.fatsecret.FATSECRET_ROUTE) { com.lifeapi.app.ui.screens.body.fatsecret.FatSecretScreen() }
}
