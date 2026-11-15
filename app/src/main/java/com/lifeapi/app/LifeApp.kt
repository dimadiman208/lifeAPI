package com.lifeapi.app

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.LifeDatabase
import com.lifeapi.app.data.LifeRepository
import com.lifeapi.app.data.QuickParser
import com.lifeapi.app.data.Undo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Snack(val text: String, val undo: Undo?)

class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = LifeDatabase.build(app)
    val repo = LifeRepository(db)
    val parser = QuickParser(repo)

    private val _today = MutableStateFlow(Days.today())
    val today: StateFlow<Long> = _today.asStateFlow()

    private val _snacks = MutableSharedFlow<Snack>(extraBufferCapacity = 4)
    val snacks = _snacks.asSharedFlow()

    fun snack(text: String, undo: Undo? = null) { _snacks.tryEmit(Snack(text, undo)) }

    init {
        scope.launch {
            repo.ensureProfile()
            repo.dao.profileNow()?.let { pr ->
                val m = com.lifeapi.app.ui.screens.body.nutrition.MacroBalance.fix(pr.kcalGoal, pr.proteinG, pr.carbsG, pr.fatG)
                if (m.kcal != pr.kcalGoal || m.p != pr.proteinG || m.c != pr.carbsG || m.f != pr.fatG)
                    repo.dao.saveProfile(pr.copy(kcalGoal = m.kcal, proteinG = m.p, carbsG = m.c, fatG = m.f))
            }
            com.lifeapi.app.data.money.MoneyJobs.runDue(repo)
        }
        com.lifeapi.app.widget.startWidgetSync(app, repo, scope)
        com.lifeapi.app.notify.startReminders(app, repo, scope)
        scope.launch {
            while (true) {
                delay(30_000)
                val d = Days.today()
                if (d != _today.value) {
                    _today.value = d
                    com.lifeapi.app.data.money.MoneyJobs.runDue(repo)
                    com.lifeapi.app.data.fatsecret.FatSecret.autoSync(force = true)
                }
            }
        }
    }

    fun refreshDay() { _today.value = Days.today() }
}

class LifeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
