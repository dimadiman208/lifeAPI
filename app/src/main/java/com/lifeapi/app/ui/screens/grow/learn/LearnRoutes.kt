package com.lifeapi.app.ui.screens.grow.learn

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** Detail routes of Grow → Learn ("grow/learn/…"). */
fun NavGraphBuilder.learnRoutes(@Suppress("UNUSED_PARAMETER") nav: NavHostController) {
    composable(LearnRoutes.MATERIAL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
        MaterialDetailScreen(e.arguments?.getLong("id") ?: 0L)
    }
    composable(
        LearnRoutes.PDF,
        arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("page") { type = NavType.IntType; defaultValue = -1 }),
    ) { e ->
        PdfReaderScreen(e.arguments?.getLong("id") ?: 0L, e.arguments?.getInt("page") ?: -1)
    }
}
