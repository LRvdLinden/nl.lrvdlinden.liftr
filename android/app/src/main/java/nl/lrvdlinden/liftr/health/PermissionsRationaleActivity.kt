package nl.lrvdlinden.liftr.health

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lrvdlinden.liftr.core.L10n
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.ui.LiftrButton
import nl.lrvdlinden.liftr.ui.LiftrColors
import nl.lrvdlinden.liftr.ui.LiftrTheme
import nl.lrvdlinden.liftr.ui.LiftrType

/** Shown by Health Connect when the user asks why Liftr wants access (required privacy-policy rationale). */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L10n.init(this)
        setContent {
            LiftrTheme {
                Column(
                    Modifier.fillMaxSize().background(LiftrColors.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("LIFTR × HEALTH CONNECT", style = LiftrType.display(30.sp))
                    Text(tr("Only with your permission, Liftr reads your weight, body composition and resting heart rate, and saves workouts, calories, distance and weight. Health data never leaves your devices through Liftr and is never used for advertising. You can change access at any time in Health Connect.",
                        "Alleen met jouw toestemming leest Liftr je gewicht, lichaamssamenstelling en rusthartslag, en bewaart het trainingen, calorieën, afstand en gewicht. Gezondheidsgegevens verlaten je apparaten nooit via Liftr en worden nooit voor advertenties gebruikt. Je wijzigt de toegang altijd in Health Connect."),
                        color = Color.White, fontSize = 17.sp)
                    LiftrButton(tr("Privacy policy", "Privacybeleid")) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://liftr.nu/privacy/")))
                    }
                    LiftrButton(tr("Close", "Sluiten"), filled = false) { finish() }
                }
            }
        }
    }
}
