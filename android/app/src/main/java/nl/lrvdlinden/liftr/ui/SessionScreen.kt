package nl.lrvdlinden.liftr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.lrvdlinden.liftr.LiftrApp
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.SetKind
import nl.lrvdlinden.liftr.core.SetRecord
import nl.lrvdlinden.liftr.core.WeightUnit
import nl.lrvdlinden.liftr.core.Workout
import nl.lrvdlinden.liftr.core.WorkoutEntry
import nl.lrvdlinden.liftr.core.WorkoutStats
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.core.trf
import nl.lrvdlinden.liftr.data.Settings
import nl.lrvdlinden.liftr.workout.WorkoutController

/** The workout in progress: exercises with their sets, what you did last time, and the rest timer. */
@Composable
fun SessionScreen(data: LiftrData) {
    val workout = WorkoutController.active(data)
    val scope = rememberCoroutineScope()
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var editingTitle by remember { mutableStateOf(false) }
    BackHandler { Nav.pop() }
    if (workout == null) {
        LaunchedEffect(Unit) { if (!finishing) Nav.remove(Nav.stack.lastOrNull { it is Route.Session } ?: return@LaunchedEffect) }
        return
    }
    val unit = Settings.unit
    val entries = data.sortedEntries(workout)
    val recent = remember(data) {
        data.finished.asSequence().filter { it.id != workout.id && data.sortedEntries(it).isNotEmpty() }.take(6).toList()
    }

    Box(Modifier.fillMaxSize().background(LiftrColors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { Nav.pop() }) {
                    Icon(Icons.Filled.KeyboardArrowDown, tr("Minimise", "Minimaliseren"), tint = LiftrColors.accent, modifier = Modifier.size(30.dp))
                }
                Text(elapsedText(workout.startDate), Modifier.weight(1f), color = Color.White, fontSize = 17.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                TextButton(onClick = { confirmFinish = true }, enabled = !finishing) {
                    Text(tr("Finish", "Afronden"), color = LiftrColors.accent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
            Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(16.dp).padding(bottom = if (WorkoutController.restEnd != null) 110.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Header
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.clickable { editingTitle = true }, verticalAlignment = Alignment.CenterVertically) {
                            Text(workout.title.uppercase(), style = LiftrType.display(34.sp), modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.Filled.Edit, null, tint = LiftrColors.muted, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                        }
                        val sets = entries.flatMap { data.sortedSets(it) }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = LiftrColors.muted, modifier = Modifier.size(16.dp))
                            Text(trf("{1}/{2} sets", "{1}/{2} sets", sets.count { it.isDone }, sets.size), color = LiftrColors.muted,
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Filled.MonitorWeight, null, tint = LiftrColors.muted, modifier = Modifier.size(16.dp))
                            Text(unit.volume(WorkoutStats.volume(data, workout)), color = LiftrColors.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (entries.isEmpty() && recent.isNotEmpty()) PreviousWorkoutsCard(data, workout, recent, unit)
                    for (entry in entries) {
                        key(entry.id) {
                            EntryCard(data, entry, workout, unit) { set ->
                                val ex = data.exercise(entry.exerciseId)
                                WorkoutController.toggle(set, ex?.kind?.isCardio == true, ex?.name ?: "")
                            }
                        }
                    }
                    LiftrButton(tr("Add exercise", "Oefening toevoegen"), filled = entries.isEmpty(), icon = Icons.Filled.Add) {
                        val id = workout.id
                        Nav.push(Route.ExercisePicker { WorkoutController.add(it, id) })
                    }
                    if (entries.isNotEmpty() && recent.isNotEmpty()) PreviousWorkoutsCard(data, workout, recent, unit)
                    Text(tr("Discard workout", "Training weggooien"), color = LiftrColors.danger.copy(alpha = 0.85f), fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { confirmDiscard = true }.padding(12.dp))
                }
            }
        }
        if (WorkoutController.restEnd != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 760.dp)) { RestTimerBar() }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(tr("Finish this workout?", "Training afronden?")) },
            text = { Text(tr("Sets you didn't fill in are left out.", "Sets die je niet hebt ingevuld worden weggelaten.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    finishing = true
                    scope.launch {
                        val id = WorkoutController.finish()
                        Nav.popTo { it !is Route.Session && it !is Route.ExercisePicker }
                        Nav.stack.removeAll { it is Route.Session }
                        if (id != null) Nav.push(Route.Summary(id))
                    }
                }) { Text(tr("Finish and save", "Afronden en opslaan"), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(tr("Keep going", "Doorgaan")) } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(tr("Discard this workout?", "Training weggooien?")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    finishing = true
                    WorkoutController.discard()
                    Nav.stack.removeAll { it is Route.Session }
                }) { Text(tr("Discard", "Weggooien"), color = LiftrColors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(tr("Cancel", "Annuleren")) } },
        )
    }
    if (editingTitle) {
        TextInputDialog(tr("Workout name", "Naam training"), workout.title, tr("Name", "Naam"), onDismiss = { editingTitle = false }) { name ->
            if (name.isNotBlank()) LiftrApp.repository.commit { workouts += workout.copy(title = name.trim()) }
            editingTitle = false
        }
    }
}

@Composable
fun TextInputDialog(title: String, initial: String, placeholder: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { LiftrTextField(text, { text = it }, placeholder) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("OK", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Annuleren")) } },
        containerColor = LiftrColors.card,
    )
}

// MARK: - Exercise card

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryCard(data: LiftrData, entry: WorkoutEntry, workout: Workout, unit: WeightUnit, onToggle: (SetRecord) -> Unit) {
    val exercise = data.exercise(entry.exerciseId)
    val isCardio = exercise?.kind?.isCardio == true
    val previous = exercise?.let { WorkoutStats.previousSets(data, it, workout.id) }
    var menu by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    val repo = LiftrApp.repository

    Column(Modifier.fillMaxWidth().liftrCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MuscleIcon(exercise?.muscle ?: nl.lrvdlinden.liftr.core.Muscle.FullBody, 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text((exercise?.name ?: tr("Exercise", "Oefening")).uppercase(), style = LiftrType.heading(20.sp))
                if (previous != null) {
                    Text("${tr("Last time", "Vorige keer")} (${LiftrFormat.shortDay(previous.date)}): ${WorkoutStats.setsList(previous.sets, unit, isCardio)}",
                        color = LiftrColors.accent, fontSize = 12.sp)
                } else {
                    Text(tr("First time — set your starting point", "Eerste keer — bepaal je startpunt"), color = LiftrColors.muted, fontSize = 12.sp)
                }
            }
            Box {
                Icon(Icons.Filled.MoreHoriz, null, tint = LiftrColors.muted,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable { menu = true }.padding(6.dp))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Note", "Notitie")) }, leadingIcon = { Icon(Icons.Filled.StickyNote2, null) },
                        onClick = { menu = false; showNotes = true })
                    DropdownMenuItem(text = { Text(tr("Remove exercise", "Oefening verwijderen"), color = LiftrColors.danger) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = LiftrColors.danger) },
                        onClick = { menu = false; WorkoutController.deleteEntry(entry) })
                }
            }
        }
        if (entry.notes.isNotEmpty()) Text(entry.notes, color = LiftrColors.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))

        // Column titles
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val style = Modifier
            ColumnTitle(tr("SET", "SET"), style.width(34.dp))
            if (previous != null) ColumnTitle(tr("LAST", "VORIGE"), style.width(64.dp))
            if (isCardio) {
                ColumnTitle(tr("MIN", "MIN"), style.weight(1f))
                ColumnTitle(tr("KM", "KM"), style.weight(1f))
            } else {
                ColumnTitle(unit.symbol.uppercase(), style.weight(1f))
                ColumnTitle(tr("REPS", "HERH"), style.weight(1f))
            }
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = LiftrColors.muted, modifier = Modifier.size(14.dp))
            }
        }

        data.sortedSets(entry).forEachIndexed { index, set ->
            key(set.id) { SetRow(set, index + 1, unit, isCardio, previous?.let { it.sets.getOrNull(index) }, previous != null, onToggle = { onToggle(set) }) }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LiftrColors.cardRaised)
                .clickable { WorkoutController.addSet(entry) }.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(" " + tr("Add set", "Set toevoegen"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
    if (showNotes) {
        TextInputDialog(tr("Note", "Notitie"), entry.notes, tr("E.g. seat position 4", "Bijv. stoelstand 4"), onDismiss = { showNotes = false }) { v ->
            repo.commit { entries += entry.copy(notes = v) }
            showNotes = false
        }
    }
}

@Composable
private fun ColumnTitle(text: String, modifier: Modifier) {
    Text(text, modifier, color = LiftrColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetRow(set: SetRecord, number: Int, unit: WeightUnit, cardio: Boolean, previous: SetRecord?, hasPrevious: Boolean, onToggle: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    fun update(s: SetRecord) = WorkoutController.updateSet(s)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Box(
                Modifier.size(34.dp, 44.dp).clip(RoundedCornerShape(10.dp)).background(LiftrColors.cardRaised)
                    .combinedClickable(onClick = { menu = true }, onLongClick = { menu = true }),
                contentAlignment = Alignment.Center,
            ) {
                Text(set.kind.badge.ifEmpty { "$number" }, fontWeight = FontWeight.Black, fontSize = 17.sp,
                    color = when (set.kind) { SetKind.Warmup -> Color(0xFFFFD60A); SetKind.Work -> Color.White; else -> LiftrColors.accent })
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                for (kind in SetKind.entries) {
                    DropdownMenuItem(text = { Text(kind.title) },
                        leadingIcon = { if (set.kind == kind) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                        onClick = { menu = false; update(set.copy(kindRaw = kind.raw)) })
                }
                DropdownMenuItem(text = { Text(tr("Delete set", "Set verwijderen"), color = LiftrColors.danger) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = LiftrColors.danger) },
                    onClick = { menu = false; WorkoutController.deleteSet(set) })
            }
        }
        if (hasPrevious) {
            val text = previous?.let { previousText(it, unit, cardio) } ?: "–"
            Box(
                Modifier.size(64.dp, 44.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = previous != null) {
                    previous?.let { old -> update(set.copy(weight = old.weight, reps = old.reps, durationSeconds = old.durationSeconds, distanceMeters = old.distanceMeters)) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(text, color = LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
        if (cardio) {
            NumberField(set.durationSeconds / 60.0, { update(set.copy(durationSeconds = Math.round(it * 60).toInt())) }, true,
                Modifier.weight(1f), done = set.isDone)
            NumberField(set.distanceMeters / 1000.0, { update(set.copy(distanceMeters = it * 1000)) }, true, Modifier.weight(1f), done = set.isDone)
        } else {
            NumberField(Math.round(unit.fromKg(set.weight) * 100) / 100.0, { update(set.copy(weight = unit.toKg(it))) }, true,
                Modifier.weight(1f), done = set.isDone)
            NumberField(set.reps.toDouble(), { update(set.copy(reps = maxOf(0, it.toInt()))) }, false, Modifier.weight(1f), done = set.isDone)
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                .background(if (set.isDone) LiftrColors.success else LiftrColors.cardRaised)
                .clickable {
                    if (!set.isDone) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, if (set.isDone) tr("Set done", "Set gedaan") else tr("Mark set done", "Set afvinken"),
                tint = if (set.isDone) Color.White else LiftrColors.muted, modifier = Modifier.size(22.dp))
        }
    }
}

/** "40×12", or "20′ · 2,5" for cardio. */
private fun previousText(old: SetRecord, unit: WeightUnit, cardio: Boolean): String {
    if (cardio) {
        val minutes = "${old.durationSeconds / 60}′"
        return if (old.distanceMeters > 0) minutes + " · " + LiftrFormat.decimal(old.distanceMeters / 1000, 1) else minutes
    }
    return if (old.weight > 0) "${unit.number(old.weight)}×${old.reps}" else "${old.reps}×"
}

// MARK: - Rest timer

@Composable
fun RestTimerBar() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val end = WorkoutController.restEnd ?: return
    val remaining = maxOf(0L, end - now)
    val progress = if (WorkoutController.restTotal > 0) remaining / (WorkoutController.restTotal * 1000f) else 0f
    LaunchedEffect(end, remaining <= 0) {
        if (remaining <= 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(2000)
            if ((WorkoutController.restEnd ?: Long.MAX_VALUE) <= System.currentTimeMillis()) WorkoutController.stopRest()
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(LiftrColors.cardRaised.copy(alpha = 0.97f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(LiftrColors.card, style = Stroke(5.dp.toPx()))
                drawArc(LiftrColors.accent, -90f, 360f * progress, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            }
            Icon(Icons.Filled.Timer, null, tint = LiftrColors.accent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(tr("REST", "RUST"), color = LiftrColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(LiftrFormat.duration((remaining + 999) / 1000), style = LiftrType.display(30.sp).copy(
                color = if (remaining > 0) Color.White else LiftrColors.success, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
        }
        RestButton("−15") { WorkoutController.addRest(-15) }
        RestButton("+15") { WorkoutController.addRest(15) }
        Box(
            Modifier.heightIn(min = 40.dp).width(48.dp).clip(RoundedCornerShape(12.dp)).background(LiftrColors.accent)
                .clickable { WorkoutController.stopRest() }.padding(8.dp),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.SkipNext, tr("Skip rest", "Rust overslaan"), tint = Color.White) }
    }
}

@Composable
private fun RestButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).width(48.dp).clip(RoundedCornerShape(12.dp)).background(LiftrColors.card).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp) }
}

// MARK: - Previous workouts

/** What you did in your last workouts, per exercise with every set. Tap an exercise to add it, or add them all. */
@Composable
fun PreviousWorkoutsCard(data: LiftrData, workout: Workout, recent: List<Workout>, unit: WeightUnit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = recent.firstOrNull { it.id == selectedId } ?: recent.firstOrNull() ?: return
    val added = data.sortedEntries(workout).mapNotNull { it.exerciseId }.toSet()
    val entries = data.sortedEntries(shown).filter { it.exerciseId != null && data.exercise(it.exerciseId) != null && WorkoutStats.countedSets(data, it).isNotEmpty() }
    val missing = entries.filter { it.exerciseId !in added }
    Column(Modifier.fillMaxWidth().liftrCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Previous workouts", "Vorige trainingen").uppercase(), style = LiftrType.heading(18.sp), modifier = Modifier.weight(1f))
            if (missing.size > 1) {
                Text(tr("Add all", "Alles toevoegen"), color = LiftrColors.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        for (e in missing) data.exercise(e.exerciseId)?.let { WorkoutController.add(it, workout.id) }
                    }.padding(6.dp))
            }
        }
        if (recent.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (w in recent) {
                    val on = w.id == shown.id
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) SolidColor(LiftrColors.accent) else SolidColor(LiftrColors.cardRaised))
                            .clickable { selectedId = w.id }.padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(LiftrFormat.shortDay(w.startDate), color = if (on) Color.White else LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Text(w.title, color = if (on) Color.White else LiftrColors.muted, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        for (entry in entries) {
            val exercise = data.exercise(entry.exerciseId) ?: continue
            val isAdded = exercise.id in added
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LiftrColors.cardRaised)
                    .clickable(enabled = !isAdded) { WorkoutController.add(exercise, workout.id) }.padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(exercise.name, color = Color.White.copy(alpha = if (isAdded) 0.55f else 1f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(WorkoutStats.setsList(WorkoutStats.countedSets(data, entry), unit, exercise.kind.isCardio),
                        color = LiftrColors.accent.copy(alpha = if (isAdded) 0.55f else 1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Icon(if (isAdded) Icons.Filled.CheckCircle else Icons.Filled.AddCircle, null,
                    tint = if (isAdded) LiftrColors.success else LiftrColors.accent, modifier = Modifier.size(24.dp))
            }
        }
    }
}
