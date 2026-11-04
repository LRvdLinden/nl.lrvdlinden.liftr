package nl.lrvdlinden.liftr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import nl.lrvdlinden.liftr.core.L10n
import nl.lrvdlinden.liftr.core.tr

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L10n.init(this)
        setContent { Text(tr("Today", "Vandaag")) }
    }
}
