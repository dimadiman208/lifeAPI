package com.lifeapi.app.vm.grow

import android.app.Application
import com.lifeapi.app.data.CheckItem
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.Task
import com.lifeapi.app.data.TaskDone
import com.lifeapi.app.data.WishItem
import com.lifeapi.app.data.grow.GrowCalc
import com.lifeapi.app.data.grow.TaskParser
import com.lifeapi.app.vm.BaseVM
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

data class TaskRow(val t: Task, val done: Boolean, val meta: String, val overdue: Boolean = false, val day: Long? = null)

data class CalDay(val day: Long, val n: Int, val count: Int, val doneCount: Int, val inMonth: Boolean)

data class PlanState(
    val today: Long,
    val top: List<TaskRow>,
    val anytime: List<TaskRow>,
    val doneToday: Int,
    val totalToday: Int,
    val upcoming: List<Pair<String, List<TaskRow>>>,
    val inbox: List<TaskRow>,
    val calendar: List<CalDay>,
    val monthLabel: String,
    val projects: List<Project>,
)

data class TaskDetail(val t: Task, val items: List<CheckItem>, val timesDone: Int, val doneToday: Boolean, val projects: List<Project>)

data class WishState(val items: List<WishItem>, val totalWanted: Long, val savedToward: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanVM(app: Application) : BaseVM(app) {

    val calMonth = MutableStateFlow(0) // offset from current month

    val state = today.flatMapLatest { day ->
        combine(
            growDao.tasks(),
            growDao.taskDone(day - 62, day + 62),
            growDao.projects(),
            growDao.allCheckItems(),
            calMonth,
        ) { tasks, doneRows, projects, checks, mOff ->
            val doneSet = doneRows.map { it.taskId to it.day }.toSet()
            val pName = projects.associate { it.id to it.name }
            val ckCount = checks.groupBy { it.taskId }.mapValues { it.value.size }

            fun meta(t: Task, forDay: Long?, overdue: Boolean): String = buildList {
                if (overdue && t.day != null) add("Overdue · ${GrowCalc.shortDate(t.day)}")
                t.projectId?.let { pName[it] }?.let { add(it) }
                if (t.module.isNotBlank()) add(t.module)
                if (t.time.isNotBlank()) add(t.time)
                if (t.repeating) add("↻ ${GrowCalc.repeatLabel(t.repeatDays)}")
                if (!t.repeating && t.day == null && forDay == null) add("Inbox · no date")
                ckCount[t.id]?.let { add("checklist $it") }
            }.joinToString(" · ").ifEmpty { if (forDay == day) "Today" else "" }

            fun row(t: Task, d: Long?, overdue: Boolean = false) =
                TaskRow(t, if (t.repeating) d != null && (t.id to d) in doneSet else t.doneTs != null, meta(t, d, overdue), overdue, d)

            // today: scheduled today, repeating today, overdue undone one-offs, one-offs done today
            val todayRows = tasks.mapNotNull { t ->
                when {
                    t.repeating -> if (t.occursOn(day)) row(t, day) else null
                    t.day == day -> if (t.doneTs == null || Days.dayOf(t.doneTs) == day) row(t, day) else null
                    t.day != null && t.day < day && t.doneTs == null -> row(t, day, overdue = true)
                    t.day != null && t.day < day && t.doneTs != null && Days.dayOf(t.doneTs) == day -> row(t, day)
                    t.day == null && t.doneTs != null && Days.dayOf(t.doneTs) == day -> row(t, day)
                    else -> null
                }
            }
            val sortRows: (List<TaskRow>) -> List<TaskRow> = { l -> l.sortedWith(compareBy<TaskRow> { it.done }.thenBy { it.t.time.ifEmpty { "99" } }.thenBy { it.t.id }) }
            val top = sortRows(todayRows.filter { it.t.top })
            val anytime = sortRows(todayRows.filter { !it.t.top })

            val upcoming = (1..14).mapNotNull { off ->
                val d = day + off
                val list = tasks.filter { t -> if (t.repeating) t.occursOn(d) else t.day == d && t.doneTs == null }.map { row(it, d) }
                if (list.isEmpty()) null else GrowCalc.dayLabel(d, day) to sortRows(list)
            }
            val inbox = tasks.filter { !it.repeating && it.day == null && it.doneTs == null }.map { row(it, null) }

            val month = Days.date(day).withDayOfMonth(1).plusMonths(mOff.toLong())
            val first = month.toEpochDay()
            val pad = month.dayOfWeek.value - 1
            val gridStart = first - pad
            val cells = (0 until 42).map { i ->
                val d = gridStart + i
                val dd = Days.date(d)
                val occ = tasks.filter { t -> if (t.repeating) t.occursOn(d) && d >= day - 62 else t.day == d }
                val dn = occ.count { t -> if (t.repeating) (t.id to d) in doneSet else t.doneTs != null }
                CalDay(d, dd.dayOfMonth, occ.size, dn, dd.month == month.month)
            }.let { c -> if (c.drop(35).none { it.inMonth }) c.take(35) else c }

            PlanState(
                today = day,
                top = top, anytime = anytime,
                doneToday = todayRows.count { it.done }, totalToday = todayRows.size,
                upcoming = upcoming, inbox = inbox,
                calendar = cells,
                monthLabel = "${month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)} ${month.year}",
                projects = projects,
            )
        }
    }.state()

    /** Tasks for a calendar day (computed on demand from current lists). */
    fun tasksOn(d: Long) = combine(growDao.tasks(), growDao.taskDone(d, d), growDao.projects()) { tasks, done, projects ->
        val ds = done.map { it.taskId }.toSet()
        val pName = projects.associate { it.id to it.name }
        tasks.filter { t -> if (t.repeating) t.occursOn(d) else t.day == d }.map { t ->
            TaskRow(t, if (t.repeating) t.id in ds else t.doneTs != null,
                listOfNotNull(t.projectId?.let { pName[it] }, t.time.ifBlank { null }, if (t.repeating) "↻ ${GrowCalc.repeatLabel(t.repeatDays)}" else null).joinToString(" · "), day = d)
        }
    }

    fun quickAdd(raw: String, defaultDay: Long?, projectId: Long? = null) {
        val q = TaskParser.parse(raw, today.value, defaultDay)
        if (q.title.isBlank()) return
        act("Added “${q.title}”${q.day?.let { " · " + GrowCalc.dayLabel(it, today.value) } ?: " · Inbox"}") {
            val id = growDao.upsertTask(Task(title = q.title, day = q.day, time = q.time, top = q.top, projectId = projectId, createdTs = Days.now()))
            suspend { growDao.deleteTask(id) }
        }
    }

    fun toggle(r: TaskRow, day: Long = today.value) {
        val t = r.t
        val nowDone = !r.done
        act(if (nowDone) "Done · ${t.title}" else "Reopened · ${t.title}") {
            if (t.repeating) {
                if (nowDone) {
                    val items = growDao.allCheckItemsNow(t.id)
                    growDao.insertTaskDone(TaskDone(t.id, day, Days.now()))
                    growDao.resetChecklist(t.id)
                    suspend {
                        growDao.deleteTaskDone(t.id, day)
                        items.forEach { growDao.upsertCheckItem(it) }
                    }
                } else {
                    growDao.deleteTaskDone(t.id, day)
                    suspend { growDao.insertTaskDone(TaskDone(t.id, day, Days.now())) }
                }
            } else {
                growDao.upsertTask(t.copy(doneTs = if (nowDone) Days.now() else null))
                suspend { growDao.upsertTask(t) }
            }
        }
    }

    fun setTop(t: Task, top: Boolean) = launch { growDao.upsertTask(t.copy(top = top)) }

    fun delete(t: Task) = act("Task deleted") {
        val items = growDao.allCheckItemsNow(t.id)
        growDao.deleteTask(t.id)
        items.forEach { growDao.deleteCheckItem(it) }
        suspend {
            growDao.upsertTask(t)
            items.forEach { growDao.upsertCheckItem(it) }
        }
    }

    fun shiftMonth(d: Int) { calMonth.value += d }

    // ---------------- task detail
    private val detailId = MutableStateFlow<Long?>(null)
    fun bind(id: Long) { detailId.value = id }

    val detail = detailId.filterNotNull().flatMapLatest { id ->
        combine(growDao.task(id), growDao.checkItems(id), growDao.taskDoneCount(id), growDao.projects(), growDao.taskDone(today.value, today.value)) { t, items, n, projects, td ->
            t?.let { TaskDetail(it, items, n + if (!it.repeating && it.doneTs != null) 1 else 0, if (it.repeating) td.any { d -> d.taskId == id } else it.doneTs != null, projects) }
        }
    }.state()

    fun save(t: Task) = launch { growDao.upsertTask(t) }
    fun addItem(taskId: Long, text: String, sort: Int) = launch { growDao.upsertCheckItem(CheckItem(taskId = taskId, text = text.trim(), sort = sort)) }
    fun toggleItem(c: CheckItem) = launch { growDao.upsertCheckItem(c.copy(done = !c.done)) }
    fun deleteItem(c: CheckItem) = act("Item removed") { growDao.deleteCheckItem(c); suspend { growDao.upsertCheckItem(c); Unit } }
    fun resetChecklist(taskId: Long) = launch { growDao.resetChecklist(taskId) }

    // ---------------- wishlist
    val wishes = growDao.wishes().let { f ->
        combine(f, flowOf(Unit)) { items, _ ->
            WishState(items, items.sumOf { it.priceCents }, items.sumOf { minOf(it.savedCents, it.priceCents) })
        }
    }.state()

    fun saveWish(w: WishItem) = launch { growDao.upsertWish(if (w.id == 0L) w.copy(createdTs = Days.now()) else w) }
    fun deleteWish(w: WishItem) = act("${w.name} removed") { growDao.deleteWish(w); suspend { growDao.upsertWish(w); Unit } }
    fun addSaved(w: WishItem, cents: Long) = act("Saved ${com.lifeapi.app.data.fmtCents(cents)} toward ${w.name}") {
        growDao.upsertWish(w.copy(savedCents = (w.savedCents + cents).coerceAtLeast(0)))
        suspend { growDao.upsertWish(w); Unit }
    }
    fun buy(w: WishItem) = act("${w.name} — bought. Enjoy it.") {
        growDao.upsertWish(w.copy(bought = true))
        suspend { growDao.upsertWish(w); Unit }
    }
}
