package com.lifeapi.app.vm.supps

import android.app.Application
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Intake
import com.lifeapi.app.data.SuppRule
import com.lifeapi.app.data.Supplement
import com.lifeapi.app.data.supps.DayPart
import com.lifeapi.app.data.supps.DayProgress
import com.lifeapi.app.data.supps.DoseCodec
import com.lifeapi.app.data.supps.Dose
import com.lifeapi.app.data.supps.DoseSlot
import com.lifeapi.app.data.supps.DoseState
import com.lifeapi.app.data.supps.SuppOps
import com.lifeapi.app.data.supps.SuppSchedule
import com.lifeapi.app.data.supps.hhmmOf
import com.lifeapi.app.vm.BaseVM
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

data class SuppGroup(val part: DayPart, val slots: List<DoseSlot>) {
    val pending get() = slots.filter { it.pending }
    val taken get() = slots.count { it.taken }
}

fun shortDay(d: Long): String = Days.date(d).let { "${it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" }

fun relDay(d: Long, today: Long): String = when (d) {
    today -> "Today, " + Days.date(d).let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" }
    today - 1 -> "Yesterday"
    today + 1 -> "Tomorrow"
    else -> shortDay(d)
}

fun groupSlots(slots: List<DoseSlot>): List<SuppGroup> =
    DayPart.entries.mapNotNull { p -> slots.filter { it.part == p }.takeIf { it.isNotEmpty() }?.let { SuppGroup(p, it) } }

fun dowLetter(d: Long) = Days.date(d).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)

/** Shared supplement actions (Health tab, history, detail). */
abstract class SuppActionsVM(app: Application) : BaseVM(app) {
    val rules = bodyDao.rules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setState(slot: DoseSlot, day: Long, state: DoseState, amount: Double = 0.0) {
        if (day > today.value) return
        val what = "${slot.supp.name} · ${slot.dose.time}"
        act(when (state) { DoseState.TAKEN -> "$what taken"; DoseState.SKIPPED -> "$what skipped"; DoseState.PENDING -> "$what cleared" }) {
            SuppOps.setDose(repo, slot, day, state, amount)
        }
    }

    fun toggle(slot: DoseSlot, day: Long) = setState(slot, day, if (slot.taken) DoseState.PENDING else DoseState.TAKEN)

    fun takeAll(slots: List<DoseSlot>, day: Long) {
        val list = slots.filter { it.pending }
        if (list.isEmpty() || day > today.value) return
        act("Took ${list.size} dose${if (list.size == 1) "" else "s"}") { SuppOps.setDoses(repo, list, day, DoseState.TAKEN) }
    }

    fun save(s: Supplement, rules: List<SuppRule>, note: String) = launch { SuppOps.saveSupplement(repo, s, rules, note) }

    /** Bulk delete (archive — intake history is kept; Undo restores all). */
    fun archiveMany(list: List<Supplement>) = act(if (list.size == 1) "${list[0].name} deleted" else "${list.size} supplements deleted") {
        list.forEach { dao.updateSupplement(it.copy(archived = true)) }
        suspend { list.forEach { dao.updateSupplement(it.copy(archived = false)) } }
    }

    fun archive(s: Supplement) = act("${s.name} archived") {
        dao.updateSupplement(s.copy(archived = true))
        suspend { dao.updateSupplement(s.copy(archived = false)) }
    }
}
