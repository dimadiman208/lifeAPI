package com.lifeapi.app.widget

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.InvalidationTracker
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.LifeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** Bumped on every relevant DB change; live Glance sessions reload their data on it. */
object WidgetBus {
    val version = MutableStateFlow(0)
}

private val ALL = listOf(
    DayScoreWidget(), SupplementsWidget(), WaterWidget(), ProjectWidget(),
    TasksWidget(), WorkoutWidget(), FinanceWidget(),
)

/** Re-render every placed widget (no-op for widgets that aren't on the home screen). */
suspend fun refreshWidgets(context: Context) {
    WidgetBus.version.value++
    ALL.forEach { runCatching { it.updateAll(context) } }
}

private val TABLES = arrayOf(
    "water", "supplement", "supp_rule", "intake", "meal", "profile", "habit", "habit_check",
    "account", "txn", "networth", "template", "template_ex", "session", "wset",
    "project", "task", "task_done",
)

/** Called once from AppContainer: keep home-screen widgets in sync with the database. */
@OptIn(FlowPreview::class)
fun startWidgetSync(app: Application, repo: LifeRepository, scope: CoroutineScope) {
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    scope.launch {
        repo.db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(TABLES) {
            override fun onInvalidated(tables: Set<String>) { changes.tryEmit(Unit) }
        })
        changes.debounce(400).collect { refreshWidgets(app) }
    }
    // Day rollover: "today" numbers reset at midnight.
    scope.launch {
        var day = Days.today()
        while (true) {
            delay(60_000)
            val d = Days.today()
            if (d != day) { day = d; refreshWidgets(app) }
        }
    }
}
