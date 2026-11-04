package nl.lrvdlinden.liftr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import nl.lrvdlinden.liftr.health.HealthConnectManager
import kotlin.math.abs

/** Progress: per exercise (weight, estimated max, volume, records), per week, and the body. */
@Composable
fun StatsScreen(data: LiftrData) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    LiftrPage(title = tr("Progress", "Voortgang")) {
        Segmented(listOf(tr("Exercises", "Oefeningen"), tr("Weeks", "Weken"), tr("Body", "Lichaam")), mode) { mode = it }
        when (mode) {
            0 -> ExerciseSection(data)
            1 -> WeeksSection(data)
            else -> BodyStatsSection(data)
        }
    }
}

@Composable
private fun ExerciseSection(data: LiftrData) {
    val unit = Settings.unit
    val used = remember(data) {
        data.exercisesByName.filter { WorkoutStats.history(data, it).isNotEmpty() }.sortedByDescending { data.entriesOf(it).size }
    }
    var exerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var metric by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val exercise = used.firstOrNull { it.id == exerciseId } ?: used.firstOrNull()
    if (exercise == null) {
        Column(Modifier.fillMaxWidth().liftrCard().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.ShowChart, null, tint = LiftrColors.accent, modifier = Modifier.size(44.dp))
            Text(tr("No data yet", "Nog geen gegevens").uppercase(), style = LiftrType.heading(22.sp))
            Text(tr("Finish a workout and your progress shows up here.", "Rond een training af en je vooruitgang verschijnt hier."),
                color = LiftrColors.muted, textAlign = TextAlign.Center)
        }
        return
    }
    Box {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { menu = true }.liftrCard(padding = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MuscleIcon(exercise.muscle, 40.dp)
            Text(exercise.name.uppercase(), style = LiftrType.heading(22.sp), modifier = Modifier.weight(1f))
            Icon(Icons.Filled.UnfoldMore, null, tint = LiftrColors.accent)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            for (ex in used) DropdownMenuItem(text = { Text(ex.name) }, onClick = { exerciseId = ex.id; menu = false })
        }
    }
    val points = WorkoutStats.history(data, exercise)
    if (exercise.kind.isCardio) {
        Text(tr("Charts are for strength exercises. Cardio sessions are in your history.",
            "Grafieken zijn er voor krachtoefeningen. Cardio staat in je geschiedenis."), color = LiftrColors.muted, modifier = Modifier.fillMaxWidth().liftrCard())
    } else {
        Segmented(listOf(tr("Heaviest", "Zwaarste"), tr("Est. max", "Geschat max"), tr("Volume", "Volume")), metric) { metric = it }
        val title = when (metric) {
            0 -> tr("Heaviest set per workout", "Zwaarste set per training")
            1 -> tr("Estimated one-rep max", "Geschat maximum (1 herhaling)")
            else -> tr("Total volume per workout (weight × reps)", "Totaal volume per training (gewicht × herhalingen)")
        }
        androidx.compose.runtime.key(exercise.id, metric) {
            InsightChart(title, unit.symbol, points.map {
                InsightPoint(it.date, unit.fromKg(when (metric) { 0 -> it.topWeight; 1 -> it.oneRepMax; else -> it.volume }))
            }, bars = metric == 2, decimals = if (metric == 2) 0 else 1)
        }
        WorkoutStats.records(data, exercise)?.let { r ->
            SectionHeader(tr("Records", "Records"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(unit.format(r.heaviest), tr("Heaviest", "Zwaarste"), Icons.Filled.EmojiEvents, Modifier.weight(1f))
                StatTile(unit.format(r.bestOneRepMax), tr("Est. 1-rep max", "Geschat 1RM"), Icons.Filled.Bolt, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("${r.mostReps}", tr("Most reps", "Meeste herh."), Icons.Filled.Repeat, Modifier.weight(1f))
                StatTile(unit.volume(r.bestVolume), tr("Best volume", "Beste volume"), Icons.Filled.MonitorWeight, Modifier.weight(1f))
            }
        }
    }
    SectionHeader(tr("Sessions", "Sessies"))
    for (p in points.reversed()) {
        Row(Modifier.fillMaxWidth().liftrCard(padding = 12.dp, radius = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(LiftrFormat.day(p.date), color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (!exercise.kind.isCardio) Text("${unit.format(p.topWeight)} · ${p.bestReps}×", color = LiftrColors.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WeeksSection(data: LiftrData) {
    val unit = Settings.unit
    val first = data.finished.minOfOrNull { it.startDate }
    val firstWeek = first?.let { WorkoutStats.weekStart(it).minusWeeks(1) }
    val weeks = WorkoutStats.weeklyCounts(data, 104).filter { firstWeek == null || !it.weekStart.isBefore(firstWeek) }
    fun millis(d: java.time.LocalDate) = d.atTime(12, 0).atZone(LiftrFormat.zone).toInstant().toEpochMilli()
    InsightChart(tr("Workouts per week", "Trainingen per week"), "", weeks.map { InsightPoint(millis(it.weekStart), it.count.toDouble()) },
        bars = true, decimals = 0)
    InsightChart(tr("Volume per week", "Volume per week"), unit.symbol, weeks.map { InsightPoint(millis(it.weekStart), unit.fromKg(it.volume)) },
        decimals = 0, zeroBased = true)
    val done = data.finished
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("${done.size}", tr("Workouts in total", "Trainingen totaal"), Icons.Filled.FitnessCenter, Modifier.weight(1f))
        StatTile("${WorkoutStats.weekStreak(data)}", tr("Weeks in a row", "Weken op rij"), Icons.Filled.LocalFireDepartment, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(unit.volume(done.sumOf { WorkoutStats.volume(data, it) }), tr("Lifted in total", "Totaal getild"), Icons.Filled.MonitorWeight, Modifier.weight(1f))
        StatTile(LiftrFormat.longDuration(done.sumOf { it.durationMillis() }), tr("Time trained", "Tijd getraind"), Icons.Filled.Schedule, Modifier.weight(1f))
    }
}

@Composable
private fun BodyStatsSection(data: LiftrData) {
    val unit = Settings.unit
    LiftrButton(tr("Log weight", "Gewicht invoeren"), icon = Icons.Filled.Add) { Nav.push(Route.BodyEntry) }
    // Health Connect when it has values, otherwise what was entered in Liftr.
    val weights = HealthConnectManager.weightHistory.ifEmpty { data.bodySorted.filter { it.weightKg > 0 }.map { it.date to it.weightKg } }
    val fats = HealthConnectManager.bodyFatHistory.ifEmpty { data.bodySorted.filter { it.bodyFatPercent > 0 }.map { it.date to it.bodyFatPercent } }
    if (weights.isEmpty()) {
        Text(tr("No weight yet. Log it here or connect a smart scale to Health Connect.",
            "Nog geen gewicht. Voer het hier in of koppel een slimme weegschaal aan Health Connect."),
            color = LiftrColors.muted, modifier = Modifier.fillMaxWidth().liftrCard())
    } else {
        val change = weights.last().second - weights.first().second
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(unit.format(weights.last().second), tr("Now", "Nu"), Icons.Filled.MonitorWeight, Modifier.weight(1f))
            StatTile((if (change >= 0) "+" else "−") + unit.format(abs(change)),
                trf("Since {1}", "Sinds {1}", LiftrFormat.shortDay(weights.first().first)),
                if (change <= 0) Icons.Filled.SouthEast else Icons.Filled.NorthEast, Modifier.weight(1f))
        }
        InsightChart(tr("Weight", "Gewicht"), unit.symbol, weights.map { InsightPoint(it.first, unit.fromKg(it.second)) })
    }
    if (fats.isNotEmpty()) InsightChart(tr("Body fat", "Vetpercentage"), "%", fats.map { InsightPoint(it.first, it.second) })
    HealthConnectManager.latestLeanMassKg?.let {
        StatTile(unit.format(it), tr("Lean body mass", "Vetvrije massa"), Icons.Filled.AccessibilityNew)
    }
}
