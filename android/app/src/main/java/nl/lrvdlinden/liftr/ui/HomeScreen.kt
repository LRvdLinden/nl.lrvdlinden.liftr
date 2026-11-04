package nl.lrvdlinden.liftr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.lrvdlinden.liftr.MainActivity
import nl.lrvdlinden.liftr.core.L10n
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.Routine
import nl.lrvdlinden.liftr.core.WeightUnit
import nl.lrvdlinden.liftr.core.Workout
import nl.lrvdlinden.liftr.core.WorkoutStats
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.core.trf
import nl.lrvdlinden.liftr.data.Settings
import nl.lrvdlinden.liftr.health.HealthConnectManager
import nl.lrvdlinden.liftr.workout.WorkoutController
import java.time.format.DateTimeFormatter

fun startWorkout(routine: Routine? = null) {
    MainActivity.current.value?.askNotifications()
    WorkoutController.start(routine)
    if (Nav.stack.none { it is Route.Session }) Nav.push(Route.Session)
}

@Composable
fun HomeScreen(data: LiftrData) {
    val unit = Settings.unit
    LiftrPage(title = tr("Today", "Vandaag"), subtitle = LiftrFormat.day(System.currentTimeMillis()), trailing = { GymPassButton(data) }) {
        StartCard(data)
        WeekCard(data)
        StatsRow(data, unit)
        data.finished.firstOrNull()?.let { last ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(tr("Last workout", "Laatste training"))
                WorkoutRow(data, last, unit) { Nav.push(Route.WorkoutDetail(last.id)) }
            }
        }
        RoutinesSection(data)
        BodySection(data, unit)
        BadgesSection(data)
    }
}

/** Live "0:42" / "1:02:13" since [start]. */
@Composable
fun elapsedText(start: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return LiftrFormat.duration((now - start) / 1000)
}

@Composable
private fun StartCard(data: LiftrData) {
    val active = WorkoutController.active(data)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LiftrColors.accentGradient)) {
        Icon(Icons.Filled.FitnessCenter, null, tint = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(90.dp).rotate(-30f))
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (active != null) {
                Text(tr("Workout in progress", "Training bezig").uppercase(), color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(active.title.uppercase(), style = LiftrType.display(30.sp))
                Text(elapsedText(active.startDate), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace)
                WhiteButton(tr("Continue", "Ga verder")) { Nav.push(Route.Session) }
            } else {
                val first = Settings.firstName
                Text((if (first.isEmpty()) tr("Ready to train?", "Klaar om te trainen?")
                else trf("Ready to train, {1}?", "Klaar om te trainen, {1}?", first)).uppercase(), style = LiftrType.display(30.sp))
                Text(tr("Start an empty workout and add exercises as you go.",
                    "Start een lege training en voeg oefeningen toe terwijl je bezig bent."),
                    color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                WhiteButton(tr("Start workout", "Start training"), Icons.Filled.PlayArrow) { startWorkout() }
            }
        }
    }
}

@Composable
private fun WeekCard(data: LiftrData) {
    val streak = WorkoutStats.weekStreak(data)
    val start = WorkoutStats.weekStart(WorkoutStats.today())
    val f = DateTimeFormatter.ofPattern("d MMM", L10n.locale)
    val range = "${f.format(start).trimEnd('.')} – ${f.format(start.plusDays(6)).trimEnd('.')}"
    Column(Modifier.fillMaxWidth().liftrCard(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("This week", "Deze week").uppercase(), style = LiftrType.heading(18.sp))
                Text(range, color = LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Filled.LocalFireDepartment, null, tint = if (streak > 0) LiftrColors.accent else LiftrColors.muted, modifier = Modifier.size(18.dp))
            Text(" " + trf("{1} wk streak", "{1} wk op rij", streak), color = if (streak > 0) LiftrColors.accent else LiftrColors.muted,
                fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        val today = WorkoutStats.today()
        WeekDots(
            trained = WorkoutStats.trainedDaysThisWeek(data),
            labels = L10n.weekdaySymbols(veryShort = true),
            dayNumbers = (0L until 7L).map { start.plusDays(it).dayOfMonth.toString() },
            todayIndex = today.dayOfWeek.value - 1,
        )
    }
}

@Composable
private fun StatsRow(data: LiftrData, unit: WeightUnit) {
    val week = WorkoutStats.workoutsThisWeek(data)
    val month = WorkoutStats.workoutsThisMonth(data)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("${week.size}", tr("Workouts this week", "Trainingen deze week"), Icons.Filled.CalendarMonth, Modifier.weight(1f))
        StatTile("${month.size}", tr("This month", "Deze maand"), Icons.Filled.EventAvailable, Modifier.weight(1f))
        StatTile(unit.volume(week.sumOf { WorkoutStats.volume(data, it) }), tr("Lifted this week", "Getild deze week"),
            Icons.Filled.MonitorWeight, Modifier.weight(1f))
    }
}

@Composable
fun WorkoutRow(data: LiftrData, workout: Workout, unit: WeightUnit, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).liftrCard(),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(workout.title.uppercase(), style = LiftrType.heading(20.sp))
                Text("${LiftrFormat.day(workout.startDate)} · ${LiftrFormat.time(workout.startDate)}", color = LiftrColors.muted, fontSize = 12.sp)
            }
            if (workout.source == "watch") Icon(Icons.Filled.Watch, null, tint = LiftrColors.muted, modifier = Modifier.size(18.dp))
            if (workout.healthRecordId != null) Icon(Icons.Filled.Favorite, null, tint = LiftrColors.pink, modifier = Modifier.size(18.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = LiftrColors.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Meta(Icons.Filled.Schedule, LiftrFormat.longDuration(workout.durationMillis()))
            Meta(Icons.Filled.Layers, trf("{1} sets", "{1} sets", WorkoutStats.setCount(data, workout)))
            Meta(Icons.Filled.MonitorWeight, unit.volume(WorkoutStats.volume(data, workout)))
        }
        val names = data.sortedEntries(workout).mapNotNull { data.exercise(it.exerciseId)?.name }
        if (names.isNotEmpty()) {
            Text(names.joinToString(" · "), color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Meta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LiftrColors.muted, modifier = Modifier.size(14.dp))
        Text(" $text", color = LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoutinesSection(data: LiftrData) {
    val routines = data.routinesSorted
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(tr("My routines", "Mijn schema's"), tr("New", "Nieuw")) { Nav.push(Route.RoutineEdit(null)) }
        if (routines.isEmpty()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { Nav.push(Route.RoutineEdit(null)) }.liftrCard(),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Filled.ListAlt, null, tint = LiftrColors.accent, modifier = Modifier.size(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tr("Make a routine", "Maak een schema"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(tr("E.g. “Monday: legs” – start it with one tap.", "Bijv. “Maandag: benen” – start hem met één tik."),
                        color = LiftrColors.muted, fontSize = 15.sp)
                }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (routine in routines) RoutineCard(data, routine)
            }
        }
    }
}

@Composable
private fun RoutineCard(data: LiftrData, routine: Routine) {
    val items = data.sortedItems(routine)
    val busy = WorkoutController.activeId != null
    Column(Modifier.width(220.dp).liftrCard(padding = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(routine.name.uppercase(), style = LiftrType.heading(18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.MoreHoriz, tr("Edit", "Wijzig"), tint = LiftrColors.muted,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { Nav.push(Route.RoutineEdit(routine.id)) }.padding(4.dp))
        }
        Text(trf("{1} exercises", "{1} oefeningen", items.size), color = LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(items.mapNotNull { data.exercise(it.exerciseId)?.name }.joinToString(", "), color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(LiftrColors.accentGradient)
                .clickable(enabled = !busy) { startWorkout(routine) }.padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(" " + tr("Start", "Start").uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
    }
}

@Composable
private fun BodySection(data: LiftrData, unit: WeightUnit) {
    val lastBody = data.bodySorted.lastOrNull()
    val weight = HealthConnectManager.latestWeightKg ?: lastBody?.weightKg?.takeIf { it > 0 }
    val fat = HealthConnectManager.latestBodyFat ?: data.bodySorted.lastOrNull { it.bodyFatPercent > 0 }?.bodyFatPercent
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(tr("Body", "Lichaam"), tr("Add", "Toevoegen")) { Nav.push(Route.BodyEntry) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(weight?.let { unit.format(it) } ?: "–", tr("Weight", "Gewicht"), Icons.Filled.MonitorWeight, Modifier.weight(1f))
            StatTile(fat?.let { "${LiftrFormat.decimal(it)}%" } ?: "–", tr("Body fat", "Vetpercentage"), Icons.Filled.Percent, Modifier.weight(1f))
            StatTile(HealthConnectManager.restingHeartRate?.let { "${it.toInt()}" } ?: "–", tr("Resting heart rate", "Rusthartslag"),
                Icons.Filled.Favorite, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BadgesSection(data: LiftrData) {
    val badges = remember(data) { WorkoutStats.badges(data) }
    val earned = badges.filter { it.achieved }.sortedByDescending { it.date }
    val next = badges.filter { !it.achieved }.sortedByDescending { it.progress }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(trf("Badges {1}/{2}", "Badges {1}/{2}", earned.size, badges.size), tr("All", "Alle")) { Nav.push(Route.Badges) }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (badge in (earned + next).take(14)) BadgeView(badge)
        }
    }
}

@Suppress("unused")
private fun Modifier.nudge() = this.offset(0.dp, 0.dp)
