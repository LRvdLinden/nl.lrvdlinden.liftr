package nl.lrvdlinden.liftr

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.lrvdlinden.liftr.core.GymTemplates
import nl.lrvdlinden.liftr.core.L10n
import nl.lrvdlinden.liftr.gym.GymLogoService
import nl.lrvdlinden.liftr.health.HealthConnectManager
import nl.lrvdlinden.liftr.reminders.ReminderScheduler
import nl.lrvdlinden.liftr.ui.LiftrRoot
import nl.lrvdlinden.liftr.ui.LiftrTheme
import nl.lrvdlinden.liftr.ui.Nav
import nl.lrvdlinden.liftr.ui.Route
import nl.lrvdlinden.liftr.wear.PhoneWearSync
import nl.lrvdlinden.liftr.workout.WorkoutController

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        L10n.init(this)
        setContent { LiftrTheme { LiftrRoot() } }
        handle(intent)
        lifecycleScope.launch {
            val repo = LiftrApp.repository
            repo.ensureLoaded()
            WorkoutController.restore()
            GymTemplates.updateCardColors(repo)
            PhoneWearSync.pullWorkouts(this@MainActivity)
            PhoneWearSync.push(this@MainActivity)
            ReminderScheduler.reschedule(this@MainActivity)
            HealthConnectManager.refresh(this@MainActivity)
            GymLogoService.refreshAll(this@MainActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            LiftrApp.repository.ensureLoaded()
            HealthConnectManager.refresh(this@MainActivity)
            ReminderScheduler.reschedule(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_WORKOUT && WorkoutController.activeId != null) {
            if (Nav.stack.none { it is Route.Session }) Nav.push(Route.Session)
        }
    }

    /** Asks for the notification permission (Android 13+) when the rest timer or reminders need it. */
    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val ACTION_OPEN_WORKOUT = "nl.lrvdlinden.liftr.OPEN_WORKOUT"
        /** Set while the activity is alive, for permission requests from screens. */
        val current = mutableStateOf<MainActivity?>(null)
    }

    override fun onStart() {
        super.onStart()
        current.value = this
    }

    override fun onDestroy() {
        if (current.value === this) current.value = null
        super.onDestroy()
    }
}
