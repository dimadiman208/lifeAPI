package com.lifeapi.app.ui.screens.grow.growth

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** Routes of Grow → Growth (all prefixed "grow/growth/"). */
object GrowthRoute {
    const val SKILL = "grow/growth/skill/{id}"
    const val JOURNAL = "grow/growth/journal"
    const val ADD = "grow/growth/add?material={material}&skill={skill}&text={text}"
    fun skill(id: Long) = "grow/growth/skill/$id"

    /** Add-to-Growth link: any of the prefills may be omitted. Learn uses "grow/growth/add?material={id}". */
    fun add(materialId: Long? = null, skillId: Long? = null, text: String? = null): String =
        "grow/growth/add?" + listOfNotNull(
            materialId?.let { "material=$it" }, skillId?.let { "skill=$it" }, text?.takeIf { it.isNotBlank() }?.let { "text=${Uri.encode(it)}" },
        ).joinToString("&")
}

/** Open the journal entry sheet prefilled from a material / skill / text (for other tabs). */
fun NavHostController.openAddLearning(materialId: Long? = null, skillId: Long? = null, text: String? = null) =
    navigate(GrowthRoute.add(materialId, skillId, text))

fun NavGraphBuilder.growthRoutes(nav: NavHostController) {
    composable(GrowthRoute.SKILL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
        SkillScreen(e.arguments?.getLong("id") ?: 0L)
    }
    composable(GrowthRoute.JOURNAL) { JournalScreen() }
    composable(
        GrowthRoute.ADD,
        arguments = listOf(
            navArgument("material") { type = NavType.LongType; defaultValue = -1L },
            navArgument("skill") { type = NavType.LongType; defaultValue = -1L },
            navArgument("text") { type = NavType.StringType; defaultValue = "" },
        ),
    ) { e ->
        val a = e.arguments
        AddLearningScreen(
            a?.getLong("material")?.takeIf { it > 0 },
            a?.getLong("skill")?.takeIf { it > 0 },
            a?.getString("text").orEmpty(),
        )
    }
}
