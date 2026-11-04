package nl.lrvdlinden.liftr.core

import android.content.Context
import android.content.SharedPreferences

/** Settings, with the same keys as the iOS app's UserDefaults. */
object Prefs {
    const val WEIGHT_UNIT = "weightUnit"
    const val REST_DEFAULT = "rest.default"
    const val REST_AUTO = "rest.auto"
    const val HEALTH_SAVE_WORKOUTS = "health.saveWorkouts"
    const val HEALTH_SAVE_BODY = "health.saveBody"
    const val HEALTH_REQUESTED = "health.requested"
    const val USER_NAME = "user.name"
    const val ONBOARDING_DONE = "onboarding.done"
    const val REMIND_DAYS_ON = "reminder.days.on"
    const val REMIND_DAYS = "reminder.days"
    const val REMIND_TIME = "reminder.time"
    const val REMIND_INACTIVE_ON = "reminder.inactive.on"
    const val REMIND_INACTIVE_DAYS = "reminder.inactive.days"
    const val REMIND_WEEKLY_ON = "reminder.weekly.on"
    const val ACTIVE_WORKOUT = "workout.active"
    const val REST_END = "rest.end"
    const val REST_TOTAL = "rest.total"
    const val GYM_LOGO_CHECKED = "gymLogoChecked"

    lateinit var prefs: SharedPreferences
        private set

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = context.applicationContext.getSharedPreferences("liftr", Context.MODE_PRIVATE)
    }

    val unit: WeightUnit get() = WeightUnit.from(prefs.getString(WEIGHT_UNIT, "kg"))

    val restDefault: Int get() = prefs.getInt(REST_DEFAULT, 90).let { if (it > 0) it else 90 }
    val restAuto: Boolean get() = prefs.getBoolean(REST_AUTO, true)

    val userName: String get() = (prefs.getString(USER_NAME, "") ?: "").trim()

    /** The first word of the name, for greetings ("Léonie" from "Léonie de Vries"). */
    val firstName: String get() = firstNameOf(userName)

    fun firstNameOf(name: String): String = name.trim().split(" ").firstOrNull { it.isNotEmpty() } ?: ""
}
