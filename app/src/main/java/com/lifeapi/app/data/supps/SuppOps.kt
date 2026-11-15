package com.lifeapi.app.data.supps

import androidx.room.withTransaction
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Intake
import com.lifeapi.app.data.LifeRepository
import com.lifeapi.app.data.SuppRule
import com.lifeapi.app.data.Supplement
import com.lifeapi.app.data.Undo

/** Dose status for writes. */
enum class DoseState { PENDING, TAKEN, SKIPPED }

/**
 * All supplement writes go through here so stock, undo and per-dose keys stay consistent across
 * Health, Today, Quick Log, widgets and notifications.
 */
object SuppOps {
    suspend fun slotsOn(repo: LifeRepository, day: Long): List<DoseSlot> =
        SuppSchedule.resolve(repo.body.activeSupplementsNow(), repo.body.rulesNow(), repo.body.intakesOnDay(day), day)

    /** Set one planned dose to [state]. [amount] 0 = as planned. Returns an undo that restores record and stock. */
    suspend fun setDose(repo: LifeRepository, slot: DoseSlot, day: Long, state: DoseState, amount: Double = 0.0): Undo {
        val b = repo.body
        val sid = slot.supp.id
        val prev = slot.intake
        var inserted: Long? = null
        var prevStock: Int? = null
        repo.db.withTransaction {
            prevStock = b.stockOf(sid)
            if (prev != null) b.deleteIntakeById(prev.id)
            val ts = if (day == Days.today()) Days.now() else Days.tsOf(day, slot.dose.localTime)
            when (state) {
                DoseState.TAKEN -> inserted = b.putIntake(Intake(supplementId = sid, day = day, ts = ts, doseKey = slot.dose.time, amount = amount))
                DoseState.SKIPPED -> inserted = b.putIntake(Intake(supplementId = sid, day = day, ts = ts, doseKey = slot.dose.time, skipped = true))
                DoseState.PENDING -> Unit
            }
            val before = if (prev != null && !prev.skipped) stockUse(slot.dose.copy(amount = prev.amount.takeIf { it > 0 } ?: slot.dose.amount)) else 0
            val after = if (state == DoseState.TAKEN) stockUse(slot.dose.copy(amount = amount.takeIf { it > 0 } ?: slot.dose.amount)) else 0
            prevStock?.let { st -> if (after != before) b.setStock(sid, (st - (after - before)).coerceAtLeast(0)) }
        }
        val restoreStock = prevStock
        return {
            repo.db.withTransaction {
                inserted?.let { b.deleteIntakeById(it) }
                if (prev != null) b.putIntake(prev)
                if (restoreStock != null) b.setStock(sid, restoreStock)
            }
        }
    }

    /** Several doses at once (e.g. "Take all"). */
    suspend fun setDoses(repo: LifeRepository, slots: List<DoseSlot>, day: Long, state: DoseState): Undo {
        val undos = slots.map { setDose(repo, it, day, state) }
        return { undos.asReversed().forEach { it() } }
    }

    /**
     * Supplement-level toggle used by Quick Log, Today and widgets: taken = mark the earliest pending (or skipped) dose;
     * not taken = clear the latest taken dose.
     */
    suspend fun setTaken(repo: LifeRepository, s: Supplement, day: Long, taken: Boolean): Undo {
        val mine = slotsOn(repo, day).filter { it.supp.id == s.id }
        val target = if (taken) mine.firstOrNull { it.pending } ?: mine.firstOrNull { it.skipped }
        else mine.lastOrNull { it.taken }
        if (target == null) {
            if (taken && mine.isEmpty()) {
                // Not scheduled today: still record it against the first dose of its base schedule.
                val d = DoseCodec.parse(repo.body.rulesNow().firstOrNull { it.supplementId == s.id && it.base }?.doses ?: "").firstOrNull()
                    ?: SuppSchedule.legacyDoses(s).firstOrNull() ?: return {}
                return setDose(repo, DoseSlot(s, d, null), day, DoseState.TAKEN)
            }
            return {}
        }
        return setDose(repo, target, day, if (taken) DoseState.TAKEN else DoseState.PENDING)
    }

    /** Pending doses of [supps] on [day] (all of them, or only those due by now if [dueOnly]). */
    suspend fun takePending(repo: LifeRepository, supps: Collection<Long>?, day: Long, dueOnly: Boolean = false): Pair<Int, Undo> {
        val now = hhmmOf(java.time.LocalTime.now())
        val list = slotsOn(repo, day).filter { it.pending && (supps == null || it.supp.id in supps) && (!dueOnly || it.dose.time <= now) }
        return list.size to setDoses(repo, list, day, DoseState.TAKEN)
    }

    /** Replace a supplement's schedule. Keeps the legacy summary columns in sync for older readers. */
    suspend fun saveSupplement(repo: LifeRepository, s: Supplement, rules: List<SuppRule>, note: String): Long {
        val base = rules.firstOrNull { it.base } ?: rules.first()
        val baseDoses = DoseCodec.parse(base.doses)
        val first = baseDoses.firstOrNull() ?: rules.flatMap { DoseCodec.parse(it.doses) }.minByOrNull { it.time }
        val union = rules.fold(0) { a, r -> a or r.days } and 127
        val summary = s.copy(
            time = first?.time ?: s.time.ifBlank { "08:00" },
            slot = if ((first?.localTime?.hour ?: 8) < 15) 0 else 1,
            days = if (union == 0) 127 else union,
            dose = listOfNotNull(first?.label, note.trim().ifEmpty { null }).joinToString(" · "),
        )
        return repo.db.withTransaction {
            val id = if (summary.id == 0L) repo.dao.insertSupplement(summary) else { repo.dao.updateSupplement(summary); summary.id }
            repo.body.deleteRulesFor(id)
            repo.body.insertRules(rules.mapIndexed { i, r -> r.copy(id = 0, supplementId = id, base = r === base, sort = i) })
            id
        }
    }
}
