package com.lifeapi.app.data.money

import com.lifeapi.app.data.LifeRepository

object MoneyJobs {
    /** Auto-log recurring bills/income/transfers whose due date has passed. Called on app start and day change. */
    suspend fun runDue(repo: LifeRepository) {
        runCatching {
            MoneyOps(repo).ensureCategories()
            MoneyOps(repo).runDue()
        }
        // Re-sort automatically categorised rows with the current rules/keywords (never touches user choices).
        runCatching { Recategorize.all(repo) }
    }
}
