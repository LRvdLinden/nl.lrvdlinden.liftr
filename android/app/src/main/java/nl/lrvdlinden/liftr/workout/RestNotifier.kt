package nl.lrvdlinden.liftr.workout

import android.Manifest
import android.app.AlarmManager
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
import nl.lrvdlinden.liftr.MainActivity
import nl.lrvdlinden.liftr.R
import nl.lrvdlinden.liftr.core.tr

/**
 * The rest timer outside the app: an ongoing notification with a live countdown (chronometer, no
 * foreground service) and an alarm that alerts when the rest is over.
 */
object RestNotifier {
    const val CHANNEL_REST = "rest"
    const val CHANNEL_REST_DONE = "rest_done"
    const val CHANNEL_REMINDERS = "reminders"
    private const val ID_REST = 1001
    private const val ID_REST_DONE = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REST, tr("Rest timer", "Rusttimer"), NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REST_DONE, tr("Rest is over", "Rust voorbij"), NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300)
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, tr("Reminders", "Herinneringen"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_WORKOUT)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, RestAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Shows the countdown and plans the alert for [endMillis]. */
    fun show(context: Context, endMillis: Long, exerciseName: String) {
        NotificationManagerCompat.from(context).cancel(ID_REST_DONE)
        if (canNotify(context)) {
            val n = NotificationCompat.Builder(context, CHANNEL_REST)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFFF6B00.toInt())
                .setContentTitle(tr("Rest", "Rust"))
                .setContentText(exerciseName)
                .setWhen(endMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setContentIntent(openApp(context))
                .setTimeoutAfter(maxOf(1000L, endMillis - System.currentTimeMillis() + 1500))
                .build()
            try { NotificationManagerCompat.from(context).notify(ID_REST, n) } catch (_: SecurityException) {}
        }
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, endMillis, pi)
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_REST)
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
    }

    /** Called by the alarm when the rest is over. */
    fun restOver(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_REST)
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_REST_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF6B00.toInt())
            .setContentTitle(tr("Rest is over", "Rust voorbij"))
            .setContentText(tr("Time for your next set 💪", "Tijd voor je volgende set 💪"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setContentIntent(openApp(context))
            .build()
        try { NotificationManagerCompat.from(context).notify(ID_REST_DONE, n) } catch (_: SecurityException) {}
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestNotifier.restOver(context)
        WorkoutController.onRestAlarm()
    }
}
