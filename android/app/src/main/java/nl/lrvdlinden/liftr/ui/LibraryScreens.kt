package nl.lrvdlinden.liftr.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lrvdlinden.liftr.LiftrApp
import nl.lrvdlinden.liftr.core.Exercise
import nl.lrvdlinden.liftr.core.ExerciseKind
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.Muscle
import nl.lrvdlinden.liftr.core.Routine
import nl.lrvdlinden.liftr.core.RoutineItem
import nl.lrvdlinden.liftr.core.WorkoutStats
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.core.trf
import nl.lrvdlinden.liftr.data.Settings
import nl.lrvdlinden.liftr.workout.WorkoutController

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    Row(
        Modifier.fillMaxWidth().background(LiftrColors.card, RoundedCornerShape(14.dp)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = LiftrColors.muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) { LiftrTextField(value, onChange, placeholder) }
        if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, null, tint = LiftrColors.muted) }
    }
}

@Composable
fun ExerciseListRow(data: LiftrData, exercise: Exercise, showsLastTime: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    @OptIn(ExperimentalFoundationApi::class)
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 14.dp, vertical = 10.dp)
            .then(if (exercise.isArchived) Modifier.background(Color.Transparent) else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MuscleIcon(exercise.muscle, 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val alpha = if (exercise.isArchived) 0.5f else 1f
            Text(exercise.name, color = Color.White.copy(alpha = alpha), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${exercise.muscle.title} · ${exercise.kind.title}", color = LiftrColors.muted, fontSize = 12.sp)
            if (showsLastTime) {
                WorkoutStats.previousSets(data, exercise, null)?.let { last ->
                    Text("${LiftrFormat.shortDay(last.date)}: ${WorkoutStats.setsList(last.sets, Settings.unit, exercise.kind.isCardio)}",
                        color = LiftrColors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (exercise.isFavorite) Icon(Icons.Filled.Star, null, tint = LiftrColors.accent, modifier = Modifier.size(16.dp))
    }
}

/** Choose an exercise: search, filter by muscle group, favourites first, or create a new one. */
@Composable
fun ExercisePickerScreen(data: LiftrData, onPick: (Exercise) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = data.exercises.values
        .filter { !it.isArchived && (muscle == null || it.muscle.raw == muscle) && (search.isEmpty() || it.name.contains(search, ignoreCase = true)) }
        .sortedWith(compareByDescending<Exercise> { it.isFavorite }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    fun pick(e: Exercise) {
        Nav.stack.lastOrNull { it is Route.ExercisePicker }?.let { Nav.remove(it) }
        onPick(e)
    }
    DetailScaffold(tr("Add exercise", "Oefening toevoegen"), onBack = { Nav.pop() }, scroll = false, actions = {
        IconButton(onClick = { Nav.push(Route.ExerciseEdit(null, search, muscle?.let { Muscle.from(it) }) { pick(it) }) }) {
            Icon(Icons.Filled.Add, tr("New exercise", "Nieuwe oefening"), tint = LiftrColors.accent)
        }
    }) {
        SearchField(search, { search = it }, tr("Search exercise", "Zoek oefening"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("All", "Alles"), muscle == null) { muscle = null }
            for (m in Muscle.entries) Chip(m.title, muscle == m.raw) { muscle = m.raw }
        }
        LazyColumn(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LiftrColors.card)) {
            items(filtered, key = { it.id }) { exercise ->
                ExerciseListRow(data, exercise, showsLastTime = true, onClick = { pick(exercise) })
                Divider()
            }
            if (filtered.isEmpty()) {
                item {
                    RowItem(if (search.isEmpty()) tr("New exercise", "Nieuwe oefening") else trf("Create “{1}”", "Maak “{1}” aan", search),
                        icon = Icons.Filled.AddCircle, color = LiftrColors.accent) {
                        Nav.push(Route.ExerciseEdit(null, search, muscle?.let { Muscle.from(it) }) { pick(it) })
                    }
                }
            }
        }
    }
}

/** Exercises (grouped by muscle) and routines. */
@Composable
fun LibraryScreen(data: LiftrData) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val repo = LiftrApp.repository
    Box(Modifier.fillMaxSize().background(LiftrColors.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 900.dp).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Library", "Bibliotheek").uppercase(), style = LiftrType.display(40.sp), modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (mode == 0) Nav.push(Route.ExerciseEdit(null)) else Nav.push(Route.RoutineEdit(null)) }) {
                        Icon(Icons.Filled.Add, if (mode == 0) tr("New exercise", "Nieuwe oefening") else tr("New routine", "Nieuw schema"),
                            tint = LiftrColors.accent, modifier = Modifier.size(28.dp))
                    }
                }
                Segmented(listOf(tr("Exercises", "Oefeningen"), tr("Routines", "Schema's")), mode) { mode = it }
                Box(Modifier.padding(vertical = 12.dp)) { SearchField(search, { search = it }, tr("Search", "Zoeken")) }
            }
            if (mode == 0) {
                val visible = data.exercisesByName.filter { (showArchived || !it.isArchived) && (search.isEmpty() || it.name.contains(search, true)) }
                val groups = mutableListOf<Pair<String, List<Exercise>>>()
                visible.filter { it.isFavorite }.takeIf { it.isNotEmpty() }?.let { groups += tr("Favourites", "Favorieten") to it }
                for (m in Muscle.entries) visible.filter { it.muscle == m }.takeIf { it.isNotEmpty() }?.let { groups += m.title to it }
                for ((title, list) in groups) {
                    item(key = "h-$title") {
                        Text(title.uppercase(), color = LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp))
                    }
                    items(list, key = { "$title-${it.id}" }) { exercise ->
                        Column(Modifier.fillMaxWidth().background(LiftrColors.card)) {
                            ExerciseListRow(data, exercise, showsLastTime = true,
                                onClick = { Nav.push(Route.ExerciseDetail(exercise.id)) },
                                onLongClick = { repo.commit { exercises += exercise.copy(isFavorite = !exercise.isFavorite) } })
                            Divider()
                        }
                    }
                }
                item {
                    Column(Modifier.padding(top = 16.dp)) {
                        CardSection(footer = tr("Long-press an exercise to make it a favourite.", "Houd een oefening ingedrukt om hem favoriet te maken.")) {
                            RowItem(tr("Show archived", "Toon gearchiveerd"), trailing = {
                                Switch(showArchived, { showArchived = it }, colors = switchColors())
                            })
                        }
                    }
                }
            } else {
                val visible = data.routinesSorted.filter { search.isEmpty() || it.name.contains(search, true) }
                if (visible.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().liftrCard(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tr("No routines yet", "Nog geen schema's"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(tr("A routine is a fixed set of exercises, like “Monday: legs”. Start it with one tap and Liftr fills in what you did last time.",
                                "Een schema is een vaste set oefeningen, zoals “Maandag: benen”. Start hem met één tik en Liftr vult in wat je vorige keer deed."),
                                color = LiftrColors.muted, fontSize = 15.sp)
                            LiftrButton(tr("New routine", "Nieuw schema"), Modifier.padding(top = 6.dp)) { Nav.push(Route.RoutineEdit(null)) }
                        }
                    }
                } else {
                    items(visible, key = { it.id }) { routine -> RoutineRow(data, routine) }
                }
            }
            item { Box(Modifier.padding(bottom = 32.dp)) }
        }
    }
}

@Composable
fun switchColors() = SwitchDefaults.colors(checkedTrackColor = LiftrColors.accent, checkedThumbColor = Color.White,
    uncheckedTrackColor = LiftrColors.cardRaised, uncheckedBorderColor = LiftrColors.cardRaised, uncheckedThumbColor = LiftrColors.muted)

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(LiftrColors.card, RoundedCornerShape(12.dp)).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            Text(label, Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .background(if (i == selected) LiftrColors.cardRaised else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 8.dp),
                color = if (i == selected) Color.White else LiftrColors.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun RoutineRow(data: LiftrData, routine: Routine) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(LiftrColors.card)
            .clickable { Nav.push(Route.RoutineEdit(routine.id)) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(routine.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(data.sortedItems(routine).mapNotNull { data.exercise(it.exerciseId)?.name }.joinToString(", "),
                color = LiftrColors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val enabled = WorkoutController.activeId == null
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(LiftrColors.accentGradient).clickable(enabled = enabled) { startWorkout(routine) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.PlayArrow, tr("Start", "Start"), tint = Color.White.copy(alpha = if (enabled) 1f else 0.5f)) }
    }
}

/** Create or edit an exercise. */
@Composable
fun ExerciseEditScreen(data: LiftrData, route: Route.ExerciseEdit) {
    val existing = route.id?.let { data.exercises[it] }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: route.initialName) }
    var muscle by rememberSaveable { mutableStateOf((existing?.muscle ?: route.initialMuscle ?: Muscle.Chest).raw) }
    var kind by rememberSaveable {
        mutableStateOf((existing?.kind ?: if (route.initialMuscle == Muscle.Cardio) ExerciseKind.Cardio else ExerciseKind.Machine).raw)
    }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    val repo = LiftrApp.repository
    fun save() {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (existing != null) {
            repo.commit { exercises += existing.copy(name = trimmed, muscleRaw = muscle, kindRaw = kind, notes = notes) }
            Nav.remove(route)
        } else {
            val new = Exercise(name = trimmed, muscleRaw = muscle, kindRaw = kind, notes = notes)
            repo.commit { exercises += new }
            Nav.remove(route)
            route.onCreate?.invoke(new)
        }
    }
    DetailScaffold(if (existing == null) tr("New exercise", "Nieuwe oefening") else tr("Edit exercise", "Oefening wijzigen"),
        onBack = { Nav.remove(route) }, actions = {
            TextButton(onClick = ::save, enabled = name.isNotBlank()) { Text(tr("Save", "Bewaar"), fontWeight = FontWeight.Bold, color = LiftrColors.accent) }
        }) {
        LiftrTextField(name, { name = it }, tr("Name", "Naam"))
        Text(tr("Muscle group", "Spiergroep").uppercase(), color = LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowChips(Muscle.entries.map { it.raw to it.title }, muscle) {
            muscle = it
            if (it == Muscle.Cardio.raw) kind = ExerciseKind.Cardio.raw
        }
        Text(tr("Type", "Soort").uppercase(), color = LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowChips(ExerciseKind.entries.map { it.raw to it.title }, kind) { kind = it }
        Text(tr("Notes", "Notities").uppercase(), color = LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        LiftrTextField(notes, { notes = it }, tr("E.g. seat position, grip", "Bijv. stoelstand, greep"), singleLine = false, minLines = 3)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) Chip(label, value == selected) { onSelect(value) }
    }
}

/** One exercise: records, recent sessions, edit and archive. */
@Composable
fun ExerciseDetailScreen(data: LiftrData, id: String) {
    val exercise = data.exercises[id]
    if (exercise == null) { DetailScaffold("", onBack = { Nav.pop() }) {}; return }
    val unit = Settings.unit
    val repo = LiftrApp.repository
    var menu by remember { mutableStateOf(false) }
    DetailScaffold("", onBack = { Nav.pop() }, actions = {
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = LiftrColors.accent) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(tr("Edit", "Wijzig")) }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { menu = false; Nav.push(Route.ExerciseEdit(exercise.id)) })
                DropdownMenuItem(text = { Text(if (exercise.isFavorite) tr("Remove favourite", "Geen favoriet meer") else tr("Favourite", "Favoriet")) },
                    leadingIcon = { Icon(if (exercise.isFavorite) Icons.Filled.StarBorder else Icons.Filled.Star, null) },
                    onClick = { menu = false; repo.commit { exercises += exercise.copy(isFavorite = !exercise.isFavorite) } })
                DropdownMenuItem(text = { Text(if (exercise.isArchived) tr("Restore", "Terugzetten") else tr("Archive", "Archiveren")) },
                    leadingIcon = { Icon(Icons.Filled.Archive, null) },
                    onClick = { menu = false; repo.commit { exercises += exercise.copy(isArchived = !exercise.isArchived) } })
            }
        }
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MuscleIcon(exercise.muscle, 54.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(exercise.name.uppercase(), style = LiftrType.display(28.sp))
                Text("${exercise.muscle.title} · ${exercise.kind.title}", color = LiftrColors.muted, fontSize = 15.sp)
            }
        }
        if (exercise.notes.isNotEmpty()) Text(exercise.notes, color = LiftrColors.muted, modifier = Modifier.fillMaxWidth().liftrCard())
        val record = WorkoutStats.records(data, exercise)
        if (record != null && !exercise.kind.isCardio) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(unit.format(record.heaviest), tr("Heaviest", "Zwaarste"), LiftrIcons.symbol("trophy.fill"), Modifier.weight(1f))
                StatTile(unit.format(record.bestOneRepMax), tr("Est. 1-rep max", "Geschat 1RM"), LiftrIcons.symbol("bolt.fill"), Modifier.weight(1f))
            }
        }
        SectionHeader(tr("Sessions", "Sessies"))
        val entries = data.entriesOf(exercise).filter { data.workoutOf(it)?.endDate != null }
            .sortedByDescending { data.workoutOf(it)?.startDate ?: 0L }
        if (entries.isEmpty()) Text(tr("Not done yet.", "Nog niet gedaan."), color = LiftrColors.muted, modifier = Modifier.fillMaxWidth().liftrCard())
        for (entry in entries) {
            val w = data.workoutOf(entry) ?: continue
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { Nav.push(Route.WorkoutDetail(w.id)) }
                .liftrCard(padding = 12.dp, radius = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(LiftrFormat.day(w.startDate).uppercase(), color = LiftrColors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(WorkoutStats.summary(WorkoutStats.countedSets(data, entry), unit, exercise.kind.isCardio), color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

private data class Draft(val exerciseId: String, val sets: Int, val reps: Int)

/** Create or edit a routine: a name and a list of exercises with target sets × reps. */
@Composable
fun RoutineEditScreen(data: LiftrData, id: String?) {
    val routine = id?.let { data.routines[it] }
    var name by rememberSaveable { mutableStateOf(routine?.name ?: "") }
    val items = remember {
        mutableStateListOf<Draft>().apply {
            if (routine != null) addAll(data.sortedItems(routine).mapNotNull { i -> i.exerciseId?.let { Draft(it, i.targetSets, i.targetReps) } })
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val repo = LiftrApp.repository
    fun close() { Nav.stack.lastOrNull { it is Route.RoutineEdit }?.let { Nav.remove(it) } }
    fun save() {
        val trimmed = name.trim()
        repo.commit { d ->
            val target = routine?.copy(name = trimmed) ?: Routine(name = trimmed)
            routines += target
            for (old in d.itemsByRoutine[target.id].orEmpty()) deleteRoutineItems += old.id
            items.forEachIndexed { index, draft ->
                routineItems += RoutineItem(order = index, exerciseId = draft.exerciseId, targetSets = draft.sets, targetReps = draft.reps, routineId = target.id)
            }
        }
        close()
        LiftrApp.afterWorkoutChange()
    }
    DetailScaffold(if (routine == null) tr("New routine", "Nieuw schema") else tr("Edit routine", "Schema wijzigen"), onBack = { close() }, actions = {
        TextButton(onClick = ::save, enabled = name.isNotBlank() && items.isNotEmpty()) {
            Text(tr("Save", "Bewaar"), fontWeight = FontWeight.Bold, color = LiftrColors.accent)
        }
    }) {
        LiftrTextField(name, { name = it }, tr("Name, e.g. Monday: legs", "Naam, bijv. Maandag: benen"))
        CardSection(tr("Exercises", "Oefeningen")) {
            items.forEachIndexed { index, item ->
                val exercise = data.exercises[item.exerciseId]
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(exercise?.name ?: "–", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (index > 0) { items.removeAt(index); items.add(index - 1, item) } }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, null, tint = if (index > 0) LiftrColors.muted else LiftrColors.cardRaised)
                        }
                        IconButton(onClick = { if (index < items.lastIndex) { items.removeAt(index); items.add(index + 1, item) } }, enabled = index < items.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = if (index < items.lastIndex) LiftrColors.muted else LiftrColors.cardRaised)
                        }
                        IconButton(onClick = { items.removeAt(index) }) { Icon(Icons.Filled.Delete, null, tint = LiftrColors.danger) }
                    }
                    if (exercise?.kind?.isCardio == true) {
                        Text(tr("Cardio", "Cardio"), color = LiftrColors.muted, fontSize = 12.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(trf("{1} sets", "{1} sets", item.sets), color = Color.White, modifier = Modifier.weight(1f))
                            StepButton("−") { items[index] = item.copy(sets = maxOf(1, item.sets - 1)) }
                            StepButton("+") { items[index] = item.copy(sets = minOf(10, item.sets + 1)) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(trf("{1} reps", "{1} herhalingen", item.reps), color = Color.White, modifier = Modifier.weight(1f))
                            StepButton("−") { items[index] = item.copy(reps = maxOf(1, item.reps - 1)) }
                            StepButton("+") { items[index] = item.copy(reps = minOf(50, item.reps + 1)) }
                        }
                    }
                }
                Divider()
            }
            RowItem(tr("Add exercise", "Oefening toevoegen"), icon = Icons.Filled.AddCircle, color = LiftrColors.accent) {
                Nav.push(Route.ExercisePicker { items.add(Draft(it.id, 3, 10)) })
            }
        }
        if (routine != null) {
            CardSection {
                RowItem(tr("Delete routine", "Schema verwijderen"), color = LiftrColors.danger, icon = Icons.Filled.Delete, iconTint = LiftrColors.danger) {
                    confirmDelete = true
                }
            }
        }
    }
    if (confirmDelete && routine != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete this routine?", "Schema verwijderen?")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    repo.commit { d -> removeRoutine(d, routine.id) }
                    close()
                }) { Text(tr("Delete", "Verwijderen"), color = LiftrColors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel", "Annuleren")) } },
        )
    }
}

