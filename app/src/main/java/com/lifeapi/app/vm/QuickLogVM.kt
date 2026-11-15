package com.lifeapi.app.vm

import android.app.Application
import com.lifeapi.app.data.ParseContext
import com.lifeapi.app.data.Parsed
import com.lifeapi.app.data.QuickAction
import com.lifeapi.app.data.RecentLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

data class QuickState(val text: String, val preview: Parsed?, val recent: List<RecentLog>, val stackHint: String?, val habitHint: String?)

@OptIn(ExperimentalCoroutinesApi::class)
class QuickLogVM(app: Application) : BaseVM(app) {
    val text = MutableStateFlow("")

    private val ctx = today.flatMapLatest { day ->
        combine(
            combine(dao.supplements(), dao.intakes(day), bodyDao.rules()) { s, i, r -> Pair(s, com.lifeapi.app.data.supps.SuppSchedule.resolve(s, r, i, day)) },
            combine(dao.habits(), dao.habitChecks(day, day), ::Pair),
            dao.budgets(),
            dao.accounts(),
            combine(dao.activeSession(), flow { emit(dao.recentMeals()) }, ::Pair),
        ) { (s, slots), (h, hc), b, a, (act, meals) ->
            val pending = slots.filter { it.pending }
            ParseContext(
                supplements = s,
                takenToday = slots.map { it.supp.id }.toSet() - pending.map { it.supp.id }.toSet(),
                nextDose = pending.distinctBy { it.supp.id }.associate { it.supp.id to "${it.part.label} · ${it.dose.time} · ${it.dose.label}" },
                habits = h,
                doneToday = hc.map { it.habitId }.toSet(),
                budgetCategories = b.map { it.category },
                accounts = a,
                recentMeals = meals,
                hasActiveWorkout = act != null,
            )
        }
    }

    val state = combine(text, ctx, dao.recentLogs()) { t, c, r ->
        QuickState(
            t, parser.parse(t, c), r,
            stackHint = c.supplements.firstOrNull { it.id in c.nextDose }?.name,
            habitHint = c.habits.firstOrNull { it.id !in c.doneToday }?.name,
        )
    }.state()

    private val parser get() = c.parser

    /** Executes the preview. Returns a meal draft text if the user must fill in a meal. */
    fun commit(p: Parsed, raw: String, onMealDraft: (String) -> Unit, onDone: () -> Unit) {
        when (val a = p.action) {
            is QuickAction.MealDraft -> onMealDraft(a.items)
            is QuickAction.Run -> launch {
                val undo = a.exec()
                repo.remember(raw.trim(), p.recentLabel, p.recentValue, p.hue)
                c.snack(a.toast, undo)
                text.value = ""
                onDone()
            }
        }
    }

    fun repeat(r: RecentLog, onMealDraft: (String) -> Unit, onDone: () -> Unit) = launch {
        val parsed = parser.parse(r.text, ctx.first()) ?: return@launch
        commit(parsed, r.text, onMealDraft, onDone)
    }

    fun addMeal(m: com.lifeapi.app.data.Meal) = act("Logged ${m.slot.lowercase()} · ${m.kcal} kcal") {
        repo.addMeal(m.copy(day = today.value, ts = com.lifeapi.app.data.Days.now()))
    }
}
