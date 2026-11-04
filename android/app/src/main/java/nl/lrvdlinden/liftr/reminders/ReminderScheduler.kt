package nl.lrvdlinden.liftr.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.lrvdlinden.liftr.MainActivity
import nl.lrvdlinden.liftr.R
import nl.lrvdlinden.liftr.core.L10n
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.LiftrRepository
import nl.lrvdlinden.liftr.core.Prefs
import nl.lrvdlinden.liftr.core.WorkoutStats
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.core.trf
import nl.lrvdlinden.liftr.workout.RestNotifier
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Local training reminders, planned on the device (port of ReminderScheduler.swift): fixed training days,
 * a nudge after X days without training, and a weekly summary. Re-planned when Liftr opens, a workout is
 * finished, or settings change — so a reminder for a day already trained is dropped.
 */
object ReminderScheduler {
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"
    private val codes = (100 until 114).toList() + listOf(200, 300)

    private fun intent(context: Context, code: Int, title: String = "", body: String = ""): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BODY, body),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Calendar weekday numbers as in iOS: 1 = Sunday … 7 = Saturday. */
    private fun appleWeekday(d: DayOfWeek): Int = if (d == DayOfWeek.SUNDAY) 1 else d.value + 1

    suspend fun reschedule(context: Context) {
        val repo = LiftrRepository.get(context)
        repo.ensureLoaded()
        val data = repo.current
        val p = Prefs.prefs
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        for (code in codes) am.cancel(intent(context, code))

        val done = data.finished
        val lastWorkout = done.maxOfOrNull { it.startDate }
        val streak = WorkoutStats.weekStreak(data)
        val week = WorkoutStats.workoutsThisWeek(data)
        val weekVolume = week.sumOf { WorkoutStats.volume(data, it) }
        val trainedDays = done.map { LiftrFormat.localDate(it.startDate) }.toSet()
        val hasActive = data.workouts.values.any { it.endDate == null }
        val minutes = p.getInt(Prefs.REMIND_TIME, 18 * 60 + 30)
        val now = System.currentTimeMillis()
        val today = LocalDate.now(LiftrFormat.zone)
        fun at(day: LocalDate): Long = day.atTime(minutes / 60, minutes % 60).atZone(LiftrFormat.zone).toInstant().toEpochMilli()

        fun plan(code: Int, fire: Long, title: String, body: String) {
            val pi = intent(context, code, title, body)
            try {
                if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire, pi)
            } catch (_: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, fire, pi)
            }
        }

        // 1. Fixed training days: the next 14 days, skipping days already trained.
        if (p.getBoolean(Prefs.REMIND_DAYS_ON, false)) {
            val days = (p.getString(Prefs.REMIND_DAYS, "2,4,6") ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            for (offset in 0 until 14) {
                val day = today.plusDays(offset.toLong())
                if (appleWeekday(day.dayOfWeek) !in days || day in trainedDays) continue
                val fire = at(day)
                if (fire <= now) continue
                if (offset == 0 && hasActive) continue
                plan(100 + offset, fire, tr("Time to train 💪", "Tijd voor je training 💪"),
                    if (streak > 0) trf("Keep your {1}-week streak going.", "Houd je reeks van {1} weken vast.", streak)
                    else tr("Your workout is planned for today.", "Je training staat vandaag gepland."))
            }
        }

        // 2. Nudge after X days without training.
        if (p.getBoolean(Prefs.REMIND_INACTIVE_ON, false) && lastWorkout != null) {
            val n = p.getInt(Prefs.REMIND_INACTIVE_DAYS, 3).let { if (it > 0) it else 3 }
            val fire = at(LiftrFormat.localDate(lastWorkout).plusDays(n.toLong()))
            if (fire > now) {
                plan(200, fire, trf("{1} days without training", "Al {1} dagen niet getraind", n),
                    if (streak > 0) trf("Your {1}-week streak is at risk 🔥", "Je reeks van {1} weken loopt gevaar 🔥", streak)
                    else tr("A short workout today? Every rep counts.", "Vandaag een korte training? Elke herhaling telt."))
            }
        }

        // 3. Weekly summary on Sunday at 20:00.
        if (p.getBoolean(Prefs.REMIND_WEEKLY_ON, false)) {
            var sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            var fire = sunday.atTime(20, 0).atZone(LiftrFormat.zone).toInstant().toEpochMilli()
            if (fire <= now) {
                sunday = sunday.plusWeeks(1)
                fire = sunday.atTime(20, 0).atZone(LiftrFormat.zone).toInstant().toEpochMilli()
            }
            val isThisWeek = WorkoutStats.weekStart(sunday) == WorkoutStats.weekStart(today)
            val body = if (isThisWeek && week.isNotEmpty()) {
                val volume = Prefs.unit.volume(weekVolume)
                if (week.size == 1) trf("This week: 1 workout, {1} lifted. Great job!", "Deze week 1 training, {1} getild. Goed bezig!", volume)
                else trf("This week: {1} workouts, {2} lifted. Great job!", "Deze week {1} trainingen, {2} getild. Goed bezig!", week.size, volume)
            } else {
                tr("No workouts this week yet — next week is a fresh start.", "Deze week nog niet getraind — volgende week is een nieuwe kans.")
            }
            plan(300, fire, tr("Your week in Liftr", "Jouw week in Liftr"), body)
        }
    }

    fun show(context: Context, title: String, body: String, id: Int) {
        if (!RestNotifier.canNotify(context)) return
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, RestNotifier.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF6B00.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) {}
    }

    internal fun extras(intent: Intent): Pair<String, String> =
        (intent.getStringExtra(EXTRA_TITLE) ?: "") to (intent.getStringExtra(EXTRA_BODY) ?: "")
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val (title, body) = ReminderScheduler.extras(intent)
        if (title.isNotEmpty()) ReminderScheduler.show(context, title, body, 2000 + (title.hashCode() and 0xFF))
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { ReminderScheduler.reschedule(context) } finally { pending.finish() }
        }
    }
}

/** Plans the reminders again after a restart or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        L10n.init(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { ReminderScheduler.reschedule(context) } finally { pending.finish() }
        }
    }
}
