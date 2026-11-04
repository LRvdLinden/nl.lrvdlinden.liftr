package nl.lrvdlinden.liftr.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import nl.lrvdlinden.liftr.core.Prefs
import nl.lrvdlinden.liftr.core.WeightUnit

/** Settings as Compose state, saved in the same SharedPreferences keys as [Prefs] (like @AppStorage). */
object Settings {
    private val p get() = Prefs.prefs

    var unitRaw by mutableStateOf("kg"); private set
    var restDefault by mutableIntStateOf(90); private set
    var restAuto by mutableStateOf(true); private set
    var saveWorkouts by mutableStateOf(true); private set
    var saveBody by mutableStateOf(true); private set
    var userName by mutableStateOf(""); private set
    var onboardingDone by mutableStateOf(false); private set
    var remindDays by mutableStateOf(false); private set
    var remindDaysRaw by mutableStateOf("2,4,6"); private set
    var remindTime by mutableIntStateOf(18 * 60 + 30); private set
    var remindInactive by mutableStateOf(false); private set
    var inactiveDays by mutableIntStateOf(3); private set
    var remindWeekly by mutableStateOf(false); private set

    val unit: WeightUnit get() = WeightUnit.from(unitRaw)
    val firstName: String get() = Prefs.firstNameOf(userName)

    fun load() {
        unitRaw = p.getString(Prefs.WEIGHT_UNIT, "kg") ?: "kg"
        restDefault = p.getInt(Prefs.REST_DEFAULT, 90)
        restAuto = p.getBoolean(Prefs.REST_AUTO, true)
        saveWorkouts = p.getBoolean(Prefs.HEALTH_SAVE_WORKOUTS, true)
        saveBody = p.getBoolean(Prefs.HEALTH_SAVE_BODY, true)
        userName = p.getString(Prefs.USER_NAME, "") ?: ""
        onboardingDone = p.getBoolean(Prefs.ONBOARDING_DONE, false)
        remindDays = p.getBoolean(Prefs.REMIND_DAYS_ON, false)
        remindDaysRaw = p.getString(Prefs.REMIND_DAYS, "2,4,6") ?: "2,4,6"
        remindTime = p.getInt(Prefs.REMIND_TIME, 18 * 60 + 30)
        remindInactive = p.getBoolean(Prefs.REMIND_INACTIVE_ON, false)
        inactiveDays = p.getInt(Prefs.REMIND_INACTIVE_DAYS, 3)
        remindWeekly = p.getBoolean(Prefs.REMIND_WEEKLY_ON, false)
    }

    fun setUnit(v: String) { unitRaw = v; p.edit().putString(Prefs.WEIGHT_UNIT, v).apply() }
    fun setRestDefault(v: Int) { restDefault = v; p.edit().putInt(Prefs.REST_DEFAULT, v).apply() }
    fun setRestAuto(v: Boolean) { restAuto = v; p.edit().putBoolean(Prefs.REST_AUTO, v).apply() }
    fun setSaveWorkouts(v: Boolean) { saveWorkouts = v; p.edit().putBoolean(Prefs.HEALTH_SAVE_WORKOUTS, v).apply() }
    fun setSaveBody(v: Boolean) { saveBody = v; p.edit().putBoolean(Prefs.HEALTH_SAVE_BODY, v).apply() }
    fun setUserName(v: String) { userName = v; p.edit().putString(Prefs.USER_NAME, v.trim()).apply() }
    fun setOnboardingDone(v: Boolean) { onboardingDone = v; p.edit().putBoolean(Prefs.ONBOARDING_DONE, v).apply() }
    fun setRemindDays(v: Boolean) { remindDays = v; p.edit().putBoolean(Prefs.REMIND_DAYS_ON, v).apply() }
    fun setRemindDaysRaw(v: String) { remindDaysRaw = v; p.edit().putString(Prefs.REMIND_DAYS, v).apply() }
    fun setRemindTime(v: Int) { remindTime = v; p.edit().putInt(Prefs.REMIND_TIME, v).apply() }
    fun setRemindInactive(v: Boolean) { remindInactive = v; p.edit().putBoolean(Prefs.REMIND_INACTIVE_ON, v).apply() }
    fun setInactiveDays(v: Int) { inactiveDays = v; p.edit().putInt(Prefs.REMIND_INACTIVE_DAYS, v).apply() }
    fun setRemindWeekly(v: Boolean) { remindWeekly = v; p.edit().putBoolean(Prefs.REMIND_WEEKLY_ON, v).apply() }
}
