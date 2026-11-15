package com.lifeapi.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lifeapi.app.LifeApp
import com.lifeapi.app.MainActivity
import com.lifeapi.app.R
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.LifeRepository
import com.lifeapi.app.data.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Local reminders: one inexact alarm for the next due event; when it fires, every event that came due
 * since the last run is posted and the next alarm is scheduled. Sources are re-read each time, so a
 * supplement taken in the app never gets a stale notification.
 */
private const val CH_REMIND = "reminders"
private const val ACTION_FIRE = "com.lifeapi.app.REMIND"
private const val ACTION_TAKEN = "com.lifeapi.app.TAKEN"

/** kind: 0 supplements, 1 wind-down, 2 task */
data class RemindEvent(val ts: Long, val kind: Int, val key: String, val title: String, val text: String, val suppIds: LongArray = LongArray(0))

private fun parseTime(s: String): LocalTime? = Regex("""^(\d{1,2}):(\d{2})$""").find(s.trim())?.let {
    val h = it.groupValues[1].toInt(); val m = it.groupValues[2].toInt()
    if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
}

object Reminders {
    private fun prefs(c: Context) = c.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun canNotify(c: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_REMIND) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Supplements, wind-down and task reminders"
            })
        }
    }

    /** Events for [day] (today or tomorrow). */
    suspend fun eventsFor(repo: LifeRepository, day: Long, p: Profile): List<RemindEvent> {
        val out = mutableListOf<RemindEvent>()
        if (p.notifySupplements) {
            // One notification per dose time, only for doses still pending.
            com.lifeapi.app.data.supps.SuppOps.slotsOn(repo, day).filter { it.pending && it.supp.reminder }
                .groupBy { it.dose.time }.forEach { (time, list) ->
                    val t = parseTime(time) ?: return@forEach
                    val slot = "${com.lifeapi.app.data.supps.DayPart.of(time).label} stack"
                    out += RemindEvent(
                        Days.tsOf(day, t), 0, "s$day-$time", "$slot · $time",
                        list.joinToString(", ") { "${it.supp.name} ${it.dose.label}" + if (it.supp.withFood) " (with food)" else "" }, list.map { it.supp.id }.toLongArray(),
                    )
                }
        }
        if (p.notifyWindDown) parseTime(p.windDown)?.let { t ->
            out += RemindEvent(Days.tsOf(day, t), 1, "w$day", "Wind-down · ${p.windDown}", "Screens off, lights low — sleep goal ${p.sleepGoalMin / 60} h ${p.sleepGoalMin % 60} m.")
        }
        if (p.notifyTasks) {
            val done = repo.grow.taskDone(day, day).first().map { it.taskId }.toSet()
            repo.grow.tasks().first().filter { it.occursOn(day) && it.time.isNotBlank() && (if (it.repeating) it.id !in done else it.doneTs == null) }
                .forEach { task ->
                    val t = parseTime(task.time) ?: return@forEach
                    val ts = Days.tsOf(day, t) - (task.remindMin ?: 0) * 60_000L
                    out += RemindEvent(ts, 2, "t${task.id}-$day", task.title, if ((task.remindMin ?: 0) > 0) "In ${task.remindMin} min · ${task.time}" else "Now · ${task.time}")
                }
        }
        return out.sortedBy { it.ts }
    }

    /** Post whatever came due since the last run, then schedule the next alarm. */
    suspend fun run(context: Context, repo: LifeRepository, fire: Boolean) {
        val p = repo.dao.profileNow() ?: return
        val now = Days.now()
        val pr = prefs(context)
        var last = pr.getLong("last", 0L)
        if (last == 0L || now - last > 6 * 3600_000L) last = now - 60_000L // first run or long gap: don't replay old ones
        val today = Days.today()
        val events = eventsFor(repo, today, p) + eventsFor(repo, today + 1, p)
        if (fire) {
            events.filter { it.ts in (last + 1)..(now + 30_000) }.forEach { post(context, it) }
        }
        pr.edit().putLong("last", now + 30_000).apply()
        val next = events.firstOrNull { it.ts > now + 30_000 }
        schedule(context, next?.ts)
    }

    private fun schedule(context: Context, at: Long?) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 1, Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        if (at != null) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun post(context: Context, e: RemindEvent) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val id = e.key.hashCode()
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, CH_REMIND)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setColor(0xFFD3F36B.toInt())
            .setContentTitle(e.title)
            .setContentText(e.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(e.text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (e.kind == 0 && e.suppIds.isNotEmpty()) {
            val taken = PendingIntent.getBroadcast(
                context, id, Intent(context, ReminderReceiver::class.java).setAction(ACTION_TAKEN)
                    .putExtra("ids", e.suppIds).putExtra("nid", id).putExtra("day", Days.dayOf(e.ts))
                    .putExtra("time", com.lifeapi.app.data.supps.hhmmOf(Days.timeOf(e.ts))),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(0, "Taken", taken)
        }
        try { NotificationManagerCompat.from(context).notify(id, b.build()) } catch (_: SecurityException) {}
    }

    /** "Taken" action: marks exactly the doses this notification was about (same time), pending ones only. */
    suspend fun markTaken(context: Context, repo: LifeRepository, ids: LongArray, day: Long, nid: Int, time: String?) {
        val set = ids.toSet()
        val slots = com.lifeapi.app.data.supps.SuppOps.slotsOn(repo, day)
            .filter { it.supp.id in set && it.pending && (time == null || it.dose.time == time) }
        com.lifeapi.app.data.supps.SuppOps.setDoses(repo, slots, day, com.lifeapi.app.data.supps.DoseState.TAKEN)
        NotificationManagerCompat.from(context).cancel(nid)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LifeApp
        val repo = app.container.repo
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_TAKEN -> Reminders.markTaken(context, repo, intent.getLongArrayExtra("ids") ?: LongArray(0), intent.getLongExtra("day", Days.today()), intent.getIntExtra("nid", 0), intent.getStringExtra("time"))
                    ACTION_FIRE -> Reminders.run(context, repo, fire = true)
                    else -> Reminders.run(context, repo, fire = false) // boot, time change
                }
            } finally { pending.finish() }
        }
    }
}

@OptIn(FlowPreview::class)
fun startReminders(app: Application, repo: LifeRepository, scope: CoroutineScope) {
    scope.launch {
        val day = Days.today()
        combine(repo.dao.profile(), combine(repo.dao.supplements(), repo.body.rules(), ::Pair), repo.dao.intakesRange(day - 1, day + 1), repo.grow.tasks(), repo.grow.taskDone(day - 1, day + 1)) { a, b, c, d, e -> listOf(a, b, c, d, e) }
            .debounce(800)
            .collect { runCatching { Reminders.run(app, repo, fire = true) } }
    }
}

/** Exported only for system broadcasts (boot, clock changes); it just reschedules. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as LifeApp).container.repo
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { Reminders.run(context, repo, fire = false) } finally { pending.finish() }
        }
    }
}
