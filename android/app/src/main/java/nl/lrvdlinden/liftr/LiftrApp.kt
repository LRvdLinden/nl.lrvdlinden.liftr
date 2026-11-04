package nl.lrvdlinden.liftr

import android.app.Application
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.lrvdlinden.liftr.core.L10n
import nl.lrvdlinden.liftr.core.LiftrRepository
import nl.lrvdlinden.liftr.core.Prefs
import nl.lrvdlinden.liftr.data.Settings
import nl.lrvdlinden.liftr.reminders.ReminderScheduler
import nl.lrvdlinden.liftr.wear.PhoneWearSync
import nl.lrvdlinden.liftr.widget.WeekWidget
import nl.lrvdlinden.liftr.workout.RestNotifier

class LiftrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        L10n.init(this)
        Settings.load()
        RestNotifier.createChannels(this)
    }

    companion object {
        lateinit var instance: LiftrApp
            private set

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val repository: LiftrRepository get() = LiftrRepository.get(instance)

        /** After a workout was finished, deleted or imported: widget, reminders and watch follow. */
        fun afterWorkoutChange() {
            scope.launch {
                ReminderScheduler.reschedule(instance)
                try { WeekWidget().updateAll(instance) } catch (_: Exception) {}
                PhoneWearSync.push(instance)
            }
        }
    }
}
