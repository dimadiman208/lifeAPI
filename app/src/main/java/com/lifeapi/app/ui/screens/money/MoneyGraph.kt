package com.lifeapi.app.ui.screens.money

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

/** Detail routes owned by this module. Called from MainActivity's NavHost. */
fun NavGraphBuilder.moneyGraph(nav: NavHostController) {
    composable("money/spending") { SpendingScreen() }
    composable("money/recurring") { RecurringScreen() }
    composable("money/savings") { SavingsScreen() }
    composable("money/import") { ImportScreen() }
    composable("money/crypto") { CryptoScreen() }
    composable("money/bank") { BankScreen() }
    composable("money/binance") { BinanceScreen() }
    composable(
        "money/category/{name}?m={m}",
        arguments = listOf(
            androidx.navigation.navArgument("name") { type = androidx.navigation.NavType.StringType },
            androidx.navigation.navArgument("m") { type = androidx.navigation.NavType.IntType; defaultValue = 0 },
        ),
    ) { e -> CategoryScreen(e.arguments?.getString("name").orEmpty(), e.arguments?.getInt("m") ?: 0) }
}
