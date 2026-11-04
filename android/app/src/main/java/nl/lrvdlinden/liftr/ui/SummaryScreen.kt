package nl.lrvdlinden.liftr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.WorkoutStats
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.core.trf
import nl.lrvdlinden.liftr.data.Settings

/** Shown after finishing a workout. */
@Composable
fun SummaryScreen(data: LiftrData, workoutId: String) {
    val workout = data.workouts[workoutId]
    fun close() { Nav.stack.removeAll { it is Route.Summary } }
    BackHandler { close() }
    if (workout == null) { androidx.compose.runtime.LaunchedEffect(Unit) { close() }; return }
    val unit = Settings.unit
    val records = remember(workoutId) { WorkoutStats.personalRecords(data, workout) }
    val newBadges = remember(workoutId) { WorkoutStats.badges(data).filter { it.date == workout.startDate } }
    Box(Modifier.fillMaxSize().background(LiftrColors.background).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.padding(top = 30.dp).size(150.dp).clip(CircleShape).background(LiftrColors.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(104.dp).clip(CircleShape).background(LiftrColors.accentGradient), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(52.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val first = Settings.firstName
                Text((if (first.isEmpty()) tr("Well done!", "Goed gedaan!") else trf("Well done, {1}!", "Goed gedaan, {1}!", first)).uppercase(),
                    style = LiftrType.display(42.sp), textAlign = TextAlign.Center)
                Text(workout.title, color = LiftrColors.muted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(LiftrFormat.longDuration(workout.durationMillis()), tr("Duration", "Duur"), Icons.Filled.Schedule, Modifier.weight(1f))
                StatTile(unit.volume(WorkoutStats.volume(data, workout)), tr("Volume", "Volume"), Icons.Filled.MonitorWeight, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("${WorkoutStats.setCount(data, workout)}", tr("Sets", "Sets"), Icons.Filled.Layers, Modifier.weight(1f))
                StatTile("${workout.activeCalories.toInt()}", "kcal", Icons.Filled.LocalFireDepartment, Modifier.weight(1f))
            }
            if (records.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().liftrCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = LiftrColors.accent)
                        Text(" " + tr("New records", "Nieuwe records").uppercase(), style = LiftrType.heading(20.sp).copy(color = LiftrColors.accent))
                    }
                    for (name in records) Text("🏆 $name", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (newBadges.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().liftrCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MilitaryTech, null, tint = LiftrColors.accent)
                        Text(" " + tr("New badges", "Nieuwe badges").uppercase(), style = LiftrType.heading(20.sp).copy(color = LiftrColors.accent))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (b in newBadges) BadgeView(b)
                    }
                }
            }
            if (workout.healthRecordId != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, null, tint = LiftrColors.pink, modifier = Modifier.size(16.dp))
                    Text(" " + tr("Saved in Health Connect", "Opgeslagen in Health Connect"), color = LiftrColors.pink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            LiftrButton(tr("Close", "Sluiten"), Modifier.padding(top = 8.dp)) { close() }
        }
    }
}
