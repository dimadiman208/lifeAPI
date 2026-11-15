package com.lifeapi.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import com.lifeapi.app.MainActivity
import com.lifeapi.app.R
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Intake
import com.lifeapi.app.data.LifeRepository
import com.lifeapi.app.data.Task
import com.lifeapi.app.data.TaskDone
import com.lifeapi.app.ui.theme.C
import kotlinx.coroutines.flow.first
import java.util.Locale

// ===================================================================== base

/**
 * Loads its data once for the first frame, then reloads whenever [WidgetBus] bumps
 * (DB writes from the app or from widget buttons) while the Glance session is alive.
 */
abstract class LifeWidget<D> : GlanceAppWidget() {
    abstract suspend fun load(repo: LifeRepository): D

    @Composable
    abstract fun Content(d: D)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = repoOf(context)
        val initial = load(repo)
        provideContent {
            val ver by WidgetBus.version.collectAsState()
            val d by produceState(initial, ver) { value = load(repo) }
            Content(d)
        }
    }
}

private val openApp get() = actionStartActivity<MainActivity>()

// ===================================================================== day score 2×2

class DayScoreWidget : LifeWidget<DayScoreData>() {
    override suspend fun load(repo: LifeRepository) = loadDayScore(repo)

    @Composable
    override fun Content(d: DayScoreData) {
        WidgetSurface(onClick = openApp, vertical = Alignment.CenterVertically, horizontal = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Image(ImageProvider(ringsBitmap(d.body, d.fuel, d.grow)), "Day score ${d.score}", GlanceModifier.size(92.dp))
                T("${d.score}", 26.sp, W.ink, FontWeight.Bold)
            }
            Spacer(GlanceModifier.height(8.dp))
            T("Day score · ${d.hit} of ${d.total} goals", 12.sp, W.muted)
        }
    }
}

class DayScoreReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayScoreWidget()
}

// ===================================================================== supplements 2×2

private val SuppKey = ActionParameters.Key<Long>("supp")
private val DoseKey = ActionParameters.Key<String>("dose")

class SupplementsWidget : LifeWidget<SuppData>() {
    override suspend fun load(repo: LifeRepository) = loadSupps(repo)

    @Composable
    override fun Content(d: SuppData) {
        WidgetSurface {
            if (d.empty) {
                Cap("SUPPLEMENTS", C.Health)
                Spacer(GlanceModifier.defaultWeight())
                T("Add your first supplement", 14.sp, W.ink, FontWeight.Medium, maxLines = 2)
                Spacer(GlanceModifier.height(10.dp))
                PillBtn("Add supplement", R.drawable.widget_btn_health, C.Graphite0, openApp)
                return@WidgetSurface
            }
            Cap(d.label, C.Health)
            Spacer(GlanceModifier.height(6.dp))
            val show = d.items.take(3)
            show.forEach { item ->
                Row(
                    GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable(actionRunCallback<ToggleSupplement>(actionParametersOf(SuppKey to item.s.id, DoseKey to item.key))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Check(item.taken, R.drawable.widget_ck_on_health, GlanceModifier.size(20.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    T(item.s.name, 13.sp, if (item.taken) W.subtle else W.ink, strike = item.taken, modifier = GlanceModifier.defaultWeight())
                    T(item.key, 11.sp, W.subtle)
                }
            }
            if (d.items.size > show.size) T("+${d.items.size - show.size} more", 11.sp, W.subtle, modifier = GlanceModifier.clickable(openApp))
            Spacer(GlanceModifier.defaultWeight())
            T("${d.takenToday} of ${d.totalToday} today" + (d.time?.let { " · $it" } ?: ""), 11.sp, W.subtle)
        }
    }
}

class ToggleSupplement : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val sid = parameters[SuppKey] ?: return
        val key = parameters[DoseKey]
        val repo = repoOf(context)
        val day = Days.today()
        val slot = com.lifeapi.app.data.supps.SuppOps.slotsOn(repo, day).filter { it.supp.id == sid }
            .let { l -> l.firstOrNull { it.dose.time == key } ?: l.firstOrNull { !it.taken } ?: l.firstOrNull() } ?: return
        com.lifeapi.app.data.supps.SuppOps.setDose(
            repo, slot, day,
            if (slot.taken) com.lifeapi.app.data.supps.DoseState.PENDING else com.lifeapi.app.data.supps.DoseState.TAKEN,
        )
        refreshWidgets(context)
    }
}

class SupplementsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SupplementsWidget()
}

// ===================================================================== water 4×1

private val MlKey = ActionParameters.Key<Int>("ml")

class WaterWidget : LifeWidget<WaterData>() {
    override suspend fun load(repo: LifeRepository) = loadWater(repo)

    @Composable
    override fun Content(d: WaterData) {
        WidgetSurface(vertical = Alignment.CenterVertically) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight().clickable(openApp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        T(liters(d.ml), 15.sp, W.ink, FontWeight.Bold)
                        T(" / ${liters(d.goal)} L water", 12.sp, W.subtle, FontWeight.Medium)
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    Bar(if (d.goal > 0) d.ml.toFloat() / d.goal else 0f, C.Health, C.HealthTint)
                }
                Spacer(GlanceModifier.width(12.dp))
                PillBtn("+${d.quick[0]}", R.drawable.widget_btn_health, C.Graphite0, actionRunCallback<AddWater>(actionParametersOf(MlKey to d.quick[0])), height = 44.dp)
                Spacer(GlanceModifier.width(8.dp))
                PillBtn("+${d.quick[1]}", R.drawable.widget_btn_health_tint, C.Health, actionRunCallback<AddWater>(actionParametersOf(MlKey to d.quick[1])), height = 44.dp)
            }
        }
    }

    private fun liters(ml: Int): String {
        val s = String.format(Locale.US, "%.2f", ml / 1000.0).trimEnd('0').trimEnd('.')
        return if (s.isEmpty()) "0" else s
    }
}

class AddWater : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val ml = parameters[MlKey] ?: return
        repoOf(context).addWater(ml)
        refreshWidgets(context)
    }
}

class WaterReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaterWidget()
}

// ===================================================================== project next action 4×2

private val ProjectKey = ActionParameters.Key<Long>("project")

class ProjectWidget : LifeWidget<Pair<ProjectData?, Boolean>>() {
    override suspend fun load(repo: LifeRepository) = loadProject(repo) to hasProjects(repo)

    @Composable
    override fun Content(d: Pair<ProjectData?, Boolean>) {
        val p = d.first
        WidgetSurface {
            if (p == null) {
                Cap("PROJECTS", C.Grow)
                Spacer(GlanceModifier.defaultWeight())
                T(if (d.second) "No next action set" else "Start your first project", 15.sp, W.ink, FontWeight.Medium, maxLines = 2)
                T("Every project leads with one next action.", 12.sp, W.subtle, maxLines = 2)
                Spacer(GlanceModifier.height(10.dp))
                PillBtn(if (d.second) "Open projects" else "Create project", R.drawable.widget_btn_grow, C.Graphite0, openApp)
                return@WidgetSurface
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.defaultWeight()) { Cap("PROJECT · ${p.name.uppercase(Locale.ROOT)}", C.Grow) }
                T("${p.pct}%", 12.sp, W.muted)
            }
            Spacer(GlanceModifier.height(10.dp))
            Bar(p.pct / 100f, C.Grow, C.GrowTint)
            Spacer(GlanceModifier.height(10.dp))
            T("NEXT ACTION", 11.sp, W.subtle)
            Spacer(GlanceModifier.height(2.dp))
            T(p.next, 15.sp, W.ink, FontWeight.Medium, maxLines = 2, modifier = GlanceModifier.clickable(openApp))
            Spacer(GlanceModifier.defaultWeight())
            Row {
                PillBtn("Focus ${p.focusMin} min", R.drawable.widget_btn_grow, C.Graphite0, openApp)
                Spacer(GlanceModifier.width(8.dp))
                PillBtn("Done", R.drawable.widget_btn_grow_tint, C.Grow, actionRunCallback<ProjectDone>(actionParametersOf(ProjectKey to p.id)), bold = false)
            }
        }
    }
}

class ProjectDone : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ProjectKey] ?: return
        val repo = repoOf(context)
        val p = repo.grow.project(id).first() ?: return
        if (p.nextAction.isBlank()) return
        val now = Days.now()
        repo.grow.upsertTask(Task(title = p.nextAction, day = Days.today(), projectId = p.id, doneTs = now, createdTs = now))
        repo.grow.upsertProject(p.copy(nextAction = "", nextSince = now))
        refreshWidgets(context)
    }
}

class ProjectReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProjectWidget()
}

// ===================================================================== today's tasks 2×2

private val TaskKey = ActionParameters.Key<Long>("task")

class TasksWidget : LifeWidget<TasksData>() {
    override suspend fun load(repo: LifeRepository) = loadTasks(repo)

    @Composable
    override fun Content(d: TasksData) {
        WidgetSurface {
            Cap(if (d.items.isEmpty()) "TODAY" else if (d.left == 0) "TODAY · ALL DONE" else "TODAY · ${d.left} LEFT", C.Volt)
            Spacer(GlanceModifier.height(6.dp))
            if (d.items.isEmpty()) {
                T("Nothing planned", 13.sp, W.muted)
            }
            // keep pending ones visible when the list is long
            val show = if (d.items.size <= 4) d.items else (d.items.filter { !it.done } + d.items.filter { it.done }).take(4)
            show.forEach { item ->
                Row(
                    GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
                        .clickable(actionRunCallback<ToggleTask>(actionParametersOf(TaskKey to item.t.id))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Check(item.done, R.drawable.widget_ck_on_volt, GlanceModifier.size(20.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    T(item.t.title, 13.sp, if (item.done) W.subtle else W.ink, strike = item.done)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            T("+ task", 18.sp, W.muted, modifier = GlanceModifier.clickable(openApp))
        }
    }
}

class ToggleTask : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskKey] ?: return
        val repo = repoOf(context)
        val t = repo.grow.task(id).first() ?: return
        val day = Days.today()
        if (t.repeating) {
            val done = repo.grow.taskDone(day, day).first().any { it.taskId == id }
            if (done) repo.grow.deleteTaskDone(id, day) else repo.grow.insertTaskDone(TaskDone(id, day, Days.now()))
        } else {
            repo.grow.upsertTask(t.copy(doneTs = if (t.doneTs != null) null else Days.now()))
        }
        refreshWidgets(context)
    }
}

class TasksReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}

// ===================================================================== workout 2×1 (taller → week strip)

class WorkoutWidget : LifeWidget<WorkoutData>() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun load(repo: LifeRepository) = loadWorkout(repo)

    @Composable
    override fun Content(d: WorkoutData) {
        val tall = LocalSize.current.height >= 120.dp
        WidgetSurface(onClick = openApp) {
            Cap(d.label, C.Train)
            Spacer(GlanceModifier.defaultWeight())
            if (tall) {
                WeekStrip(d.week)
                Spacer(GlanceModifier.height(10.dp))
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.defaultWeight()) { T(d.meta, 13.sp, W.muted) }
                if (d.hasTemplate) Image(ImageProvider(R.drawable.widget_play), "Start", GlanceModifier.size(34.dp))
            }
        }
    }

    @Composable
    private fun WeekStrip(week: List<Boolean?>) {
        val todayIdx = week.indexOfLast { it != null }
        Row(GlanceModifier.fillMaxWidth()) {
            week.forEachIndexed { i, done ->
                val res = when {
                    done == true -> R.drawable.widget_day_on
                    i == todayIdx -> R.drawable.widget_day_today
                    else -> R.drawable.widget_day_off
                }
                Box(GlanceModifier.defaultWeight().height(20.dp).padding(horizontal = 2.dp)) {
                    Box(GlanceModifier.fillMaxWidth().height(20.dp).background(ImageProvider(res))) {}
                }
            }
        }
    }
}

class WorkoutReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkoutWidget()
}

// ===================================================================== finance 2×2

class FinanceWidget : LifeWidget<FinanceData>() {
    override suspend fun load(repo: LifeRepository) = loadFinance(repo)

    @Composable
    override fun Content(d: FinanceData) {
        WidgetSurface(onClick = openApp) {
            Cap("MONEY · TODAY", C.Money)
            Spacer(GlanceModifier.height(6.dp))
            T(d.left, 28.sp, if (d.over) W.c(C.Negative) else W.ink, FontWeight.Bold)
            T(d.of, 12.sp, W.muted)
            Spacer(GlanceModifier.height(8.dp))
            Bar(d.spentFrac, if (d.over) C.Negative else C.Money, C.MoneyTint)
            Spacer(GlanceModifier.defaultWeight())
            Row {
                T("Net worth ${d.netWorth}", 12.sp, W.subtle)
                if (d.deltaPct != null) T(" · ${d.deltaPct}", 12.sp, W.c(if (d.deltaUp) C.Positive else C.Negative))
            }
        }
    }
}

class FinanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FinanceWidget()
}
