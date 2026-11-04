package nl.lrvdlinden.liftr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import nl.lrvdlinden.liftr.LiftrApp
import nl.lrvdlinden.liftr.core.L10n
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.Muscle
import nl.lrvdlinden.liftr.core.WeightUnit
import nl.lrvdlinden.liftr.core.Workout
import nl.lrvdlinden.liftr.core.WorkoutEntry
import nl.lrvdlinden.liftr.core.WorkoutStats
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.core.trf
import nl.lrvdlinden.liftr.data.Settings
import nl.lrvdlinden.liftr.health.HealthConnectManager
import nl.lrvdlinden.liftr.workout.WorkoutController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Month calendar with a dot on every training day; tap a day to see what was done. */
@Composable
fun HistoryScreen(data: LiftrData) {
    val unit = Settings.unit
    var monthText by rememberSaveable { mutableStateOf(WorkoutStats.today().withDayOfMonth(1).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(WorkoutStats.today().toString()) }
    val month = LocalDate.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    val done = data.finished
    LiftrPage(title = tr("History", "Geschiedenis"), subtitle = trf("{1} workouts in total", "{1} trainingen in totaal", done.size)) {
        CalendarCard(done, month, selected, onMonth = { monthText = it.toString() }, onSelect = { selectedText = it.toString() })
        val dayWorkouts = done.filter { LiftrFormat.localDate(it.startDate) == selected }
        SectionHeader(LiftrFormat.day(selected.atTime(12, 0).atZone(LiftrFormat.zone).toInstant().toEpochMilli()))
        if (dayWorkouts.isEmpty()) {
            Text(tr("No workout on this day.", "Geen training op deze dag."), color = LiftrColors.muted, fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().liftrCard())
        } else {
            for (w in dayWorkouts) WorkoutRow(data, w, unit) { Nav.push(Route.WorkoutDetail(w.id)) }
        }
        val monthWorkouts = done.filter { val d = LiftrFormat.localDate(it.startDate); d.year == month.year && d.month == month.month }
        if (monthWorkouts.isNotEmpty()) {
            SectionHeader(LiftrFormat.monthYear(month))
            for (w in monthWorkouts) CompactWorkoutRow(data, w, unit)
        }
    }
}

@Composable
private fun CalendarCard(done: List<Workout>, month: LocalDate, selected: LocalDate, onMonth: (LocalDate) -> Unit, onSelect: (LocalDate) -> Unit) {
    val trainedDays = done.map { LiftrFormat.localDate(it.startDate) }.toSet()
    val symbols = L10n.weekdaySymbols(veryShort = false).map { it.uppercase(L10n.locale) }
    val today = WorkoutStats.today()
    val leading = month.withDayOfMonth(1).dayOfWeek.value - 1
    val count = month.lengthOfMonth()
    val cells: List<LocalDate?> = List(leading) { null } + (1..count).map { month.withDayOfMonth(it) }
    Column(Modifier.fillMaxWidth().liftrCard(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) { Icon(Icons.Filled.ChevronLeft, null, tint = LiftrColors.accent) }
            Text(LiftrFormat.monthYear(month).uppercase(), style = LiftrType.heading(20.sp), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) { Icon(Icons.Filled.ChevronRight, null, tint = LiftrColors.accent) }
        }
        Row(Modifier.fillMaxWidth()) {
            for (s in symbols) Text(s, Modifier.weight(1f), color = LiftrColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
        for (week in cells.chunked(7)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    if (day == null) {
                        Box(Modifier.weight(1f).heightIn(min = 44.dp))
                    } else {
                        val trained = day in trainedDays
                        val isSelected = day == selected
                        val isToday = day == today
                        Column(
                            Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) LiftrColors.accentGradient else androidx.compose.ui.graphics.SolidColor(Color.Transparent))
                                .clickable { onSelect(day) }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text("${day.dayOfMonth}", fontSize = 15.sp,
                                fontWeight = if (trained || isToday) FontWeight.Black else FontWeight.Medium,
                                color = if (isSelected) Color.White else if (isToday) LiftrColors.accent else Color.White.copy(alpha = 0.9f))
                            Box(Modifier.size(6.dp).clip(CircleShape).background(
                                if (trained) (if (isSelected) Color.White else LiftrColors.accent) else Color.Transparent))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactWorkoutRow(data: LiftrData, workout: Workout, unit: WeightUnit) {
    val date = LiftrFormat.zoned(workout.startDate)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { Nav.push(Route.WorkoutDetail(workout.id)) }
            .liftrCard(padding = 12.dp, radius = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${date.dayOfMonth}", style = LiftrType.display(22.sp))
            Text(DateTimeFormatter.ofPattern("EEE", L10n.locale).format(date).trimEnd('.').uppercase(L10n.locale),
                color = LiftrColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(workout.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${data.sortedEntries(workout).size} ${tr("exercises", "oefeningen")} · ${unit.volume(WorkoutStats.volume(data, workout))}",
                color = LiftrColors.muted, fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = LiftrColors.muted)
    }
}

/** A finished workout: everything that was done, editable afterwards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(data: LiftrData, id: String) {
    val workout = data.workouts[id]
    val repo = LiftrApp.repository
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    if (workout == null) { DetailScaffold("", onBack = { Nav.pop() }) {}; return }
    val unit = Settings.unit
    DetailScaffold("", onBack = { Nav.pop() }, actions = {
        TextButton(onClick = { editing = !editing }) {
            Text(if (editing) tr("Done", "Klaar") else tr("Edit", "Wijzig"), color = LiftrColors.accent, fontWeight = FontWeight.Bold)
        }
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(LiftrFormat.day(workout.startDate).uppercase(), color = LiftrColors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (editing) {
                var title by remember(workout.id) { mutableStateOf(workout.title) }
                LiftrTextField(title, { v -> title = v; repo.commit { workouts += workout.copy(title = v) } }, tr("Name", "Naam"))
            } else {
                Text(workout.title.uppercase(), style = LiftrType.display(34.sp))
            }
        }
        val tiles = buildList {
            add(Triple(LiftrFormat.longDuration(workout.durationMillis()), tr("Duration", "Duur"), Icons.Filled.Schedule))
            add(Triple(unit.volume(WorkoutStats.volume(data, workout)), tr("Volume", "Volume"), Icons.Filled.MonitorWeight))
            add(Triple("${WorkoutStats.setCount(data, workout)}", tr("Sets", "Sets"), Icons.Filled.Layers))
            if (workout.activeCalories > 0) add(Triple("${workout.activeCalories.toInt()}", "kcal", Icons.Filled.LocalFireDepartment))
            if (workout.averageHeartRate > 0) add(Triple("${workout.averageHeartRate.toInt()}", tr("Avg. heart rate", "Gem. hartslag"), Icons.Filled.Favorite))
        }
        for (row in tiles.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((v, l, i) in row) StatTile(v, l, i, Modifier.weight(1f))
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        if (editing) {
            RowItem(tr("Start", "Start"), detail = LiftrFormat.dateTime(workout.startDate), modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(LiftrColors.card)) {
                pickDate = true
            }
            var notes by remember(workout.id) { mutableStateOf(workout.notes) }
            LiftrTextField(notes, { v -> notes = v; repo.commit { workouts += workout.copy(notes = v) } }, tr("Notes", "Notities"), singleLine = false, minLines = 2)
            for (entry in data.sortedEntries(workout)) {
                key(entry.id) {
                    EntryCard(data, entry, workout, unit) { set ->
                        val nowDone = !set.isDone
                        repo.commit { sets += set.copy(isDone = nowDone, completedAt = if (nowDone) (set.completedAt ?: workout.startDate) else null) }
                    }
                }
            }
            LiftrButton(tr("Add exercise", "Oefening toevoegen"), filled = false, icon = Icons.Filled.Add) {
                Nav.push(Route.ExercisePicker { WorkoutController.add(it, workout.id) })
            }
        } else {
            if (workout.notes.isNotEmpty()) Text(workout.notes, color = LiftrColors.muted, modifier = Modifier.fillMaxWidth().liftrCard())
            for (entry in data.sortedEntries(workout)) ReadOnlyEntry(data, entry, unit)
        }
        if (workout.healthRecordId != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Favorite, null, tint = LiftrColors.pink, modifier = Modifier.size(14.dp))
                Text(" " + tr("Saved in Health Connect", "Opgeslagen in Health Connect"), color = LiftrColors.pink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(tr("Delete workout", "Training verwijderen"), color = LiftrColors.danger.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
                .clickable { confirmDelete = true }.padding(12.dp))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete this workout?", "Training verwijderen?")) },
            text = { Text(tr("Also removed from Health Connect if Liftr saved it there.", "Wordt ook uit Health Connect gehaald als Liftr hem daar heeft opgeslagen.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val target = workout
                    Nav.stack.lastOrNull { it is Route.WorkoutDetail && it.id == id }?.let { Nav.remove(it) }
                    repo.commit { d -> removeWorkout(d, target.id) }
                    LiftrApp.afterWorkoutChange()
                    scope.launch { HealthConnectManager.deleteWorkout(LiftrApp.instance, target) }
                }) { Text(tr("Delete", "Verwijderen"), color = LiftrColors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel", "Annuleren")) } },
        )
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LiftrFormat.localDate(workout.startDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickDate = false
                    state.selectedDateMillis?.let { utc ->
                        val day = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val old = LiftrFormat.zoned(workout.startDate)
                        val start = day.atTime(old.toLocalTime()).atZone(LiftrFormat.zone).toInstant().toEpochMilli()
                        val shift = start - workout.startDate
                        repo.commit { workouts += workout.copy(startDate = start, endDate = workout.endDate?.plus(shift)) }
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(tr("Cancel", "Annuleren")) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun ReadOnlyEntry(data: LiftrData, entry: WorkoutEntry, unit: WeightUnit) {
    val exercise = data.exercise(entry.exerciseId)
    val cardio = exercise?.kind?.isCardio == true
    Column(Modifier.fillMaxWidth().liftrCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MuscleIcon(exercise?.muscle ?: Muscle.FullBody, 38.dp)
            Text((exercise?.name ?: tr("Exercise", "Oefening")).uppercase(), style = LiftrType.heading(19.sp), modifier = Modifier.weight(1f))
        }
        WorkoutStats.countedSets(data, entry).forEachIndexed { index, set ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(set.kind.badge.ifEmpty { "${index + 1}" }, Modifier.width(28.dp), color = LiftrColors.muted, fontWeight = FontWeight.Black, fontSize = 15.sp)
                val text = if (cardio) {
                    "${set.durationSeconds / 60} min" + if (set.distanceMeters > 0) " · ${LiftrFormat.decimal(set.distanceMeters / 1000, 2)} km" else ""
                } else {
                    "${set.reps} × ${if (set.weight > 0) unit.format(set.weight) else tr("bodyweight", "lichaamsgewicht")}"
                }
                Text(text, Modifier.weight(1f), color = Color.White, fontSize = 16.sp)
                if (set.isDone) Icon(Icons.Filled.CheckCircle, null, tint = LiftrColors.success, modifier = Modifier.size(20.dp))
            }
        }
        if (entry.notes.isNotEmpty()) Text(entry.notes, color = LiftrColors.muted, fontSize = 13.sp)
    }
}
