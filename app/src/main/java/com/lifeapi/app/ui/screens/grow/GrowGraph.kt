package com.lifeapi.app.ui.screens.grow

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.lifeapi.app.ui.screens.grow.growth.growthRoutes
import com.lifeapi.app.ui.screens.grow.learn.learnRoutes
import com.lifeapi.app.ui.screens.grow.projects.projectsRoutes

/**
 * Grow detail routes (v3). Each sub-section registers its own routes under its prefix:
 * grow/projects/…, grow/growth/…, grow/learn/…  The pre-v3 screens (project detail, focus, task, wishlist) are no
 * longer reachable; their files stay as legacy code (see dev-docs/GROW_V3.md).
 */
fun NavGraphBuilder.growGraph(nav: NavHostController) {
    projectsRoutes(nav)
    growthRoutes(nav)
    learnRoutes(nav)
}
