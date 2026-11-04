package nl.lrvdlinden.liftr.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lrvdlinden.liftr.LiftrApp
import nl.lrvdlinden.liftr.core.L10n
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.NotesImporter
import nl.lrvdlinden.liftr.core.SpreadsheetImporter
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.core.trf
import nl.lrvdlinden.liftr.data.Settings
import java.time.Instant
import java.time.ZoneOffset

private const val EXAMPLE = "3-11-2026\nLeg press 3x12 40 kg\nChest press 3 x 10 x 25 kg\nLat pulldown 30kg 12, 35kg 10, 35kg 8\nLoopband 20 min 2,5 km"

/**
 * Import old workouts: paste notes, or pick a spreadsheet (.xlsx / .csv). Check what Liftr understood, then import.
 * Imported workouts go into history and charts, but never into Health Connect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(data: LiftrData) {
    val context = LocalContext.current
    val unit = Settings.unit
    var fileMode by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var defaultDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val days = remember { mutableStateListOf<NotesImporter.ParsedDay>() }
    var analysed by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<NotesImporter.ImportResult?>(null) }
    var fileName by remember { mutableStateOf("") }
    var tables by remember { mutableStateOf<List<SpreadsheetImporter.Table>>(emptyList()) }
    var tableIndex by remember { mutableIntStateOf(0) }
    var options by remember { mutableStateOf(SpreadsheetImporter.Options()) }
    var exerciseColumns by remember { mutableStateOf<List<String>>(emptyList()) }
    val renames = remember { mutableStateMapOf<String, String>() }
    var skipped by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSkipped by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var dateFor by remember { mutableStateOf<Int?>(null) }
    var pickDefaultDate by remember { mutableStateOf(false) }
    fun close() { Nav.stack.lastOrNull { it is Route.Import }?.let { Nav.remove(it) } }

    fun reparse() {
        val t = tables.getOrNull(tableIndex) ?: return
        val parsed = SpreadsheetImporter.parse(t, options, unit)
        days.clear(); days.addAll(parsed.days)
        exerciseColumns = parsed.exerciseColumns
        skipped = parsed.skipped
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw SpreadsheetImporter.LoadException()
            val name = displayName(context, uri) ?: "import.xlsx"
            tables = SpreadsheetImporter.load(bytes, name)
            fileName = name
            renames.clear()
            val scores = tables.map { SpreadsheetImporter.parse(it, options, unit).days.size }
            tableIndex = scores.indexOf(scores.maxOrNull() ?: 0).coerceAtLeast(0)
            reparse()
            analysed = true
        } catch (e: Exception) {
            loadError = SpreadsheetImporter.unreadableMessage
        }
    }
    fun pickFile() = filePicker.launch(arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values",
        "text/tab-separated-values", "text/plain", "application/octet-stream",
    ))

    val healthFooter = tr("Imported workouts go into your history and charts, but are not written to Health Connect.",
        "Geïmporteerde trainingen komen in je geschiedenis en grafieken, maar worden niet naar Health Connect geschreven.")

    DetailScaffold(tr("Import", "Importeren"), onBack = { if (analysed) analysed = false else close() }, actions = {
        if (analysed) {
            TextButton(enabled = days.isNotEmpty(), onClick = {
                val list = if (fileMode && renames.isNotEmpty()) days.map { d ->
                    d.copy(lines = d.lines.map { l -> renames[l.name]?.trim()?.takeIf { it.isNotEmpty() }?.let { l.copy(name = it) } ?: l })
                } else days.toList()
                result = NotesImporter.importDays(list, LiftrApp.repository, if (fileMode) fileName else tr("Notes", "Notities"))
                LiftrApp.afterWorkoutChange()
            }) { Text(tr("Import", "Importeer"), color = LiftrColors.accent, fontWeight = FontWeight.Bold) }
        } else if (!fileMode) {
            TextButton(enabled = text.isNotBlank(), onClick = {
                days.clear(); days.addAll(NotesImporter.parse(text, defaultDate, unit))
                analysed = true
            }) { Text(tr("Analyse", "Analyseer"), color = LiftrColors.accent, fontWeight = FontWeight.Bold) }
        }
    }) {
        if (!analysed) {
            Segmented(listOf(tr("Notes", "Notities"), tr("Spreadsheet", "Spreadsheet")), if (fileMode) 1 else 0) { fileMode = it == 1 }
            if (!fileMode) {
                CardSection(tr("Your notes", "Je notities"), tr("Copy the note from your notes app and paste it here. A line with a date starts a new workout; lines without a date before them get the date below.",
                    "Kopieer de notitie uit je notitie-app en plak hem hier. Een regel met een datum begint een nieuwe training; regels zonder datum ervoor krijgen de datum hieronder.")) {
                    Column(Modifier.padding(12.dp)) {
                        LiftrTextField(text, { text = it }, EXAMPLE, singleLine = false, minLines = 8, monospace = true)
                    }
                    RowItem(tr("Paste", "Plakken"), icon = Icons.Filled.ContentPaste, color = LiftrColors.accent) {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { text = it }
                    }
                }
                CardSection(footer = healthFooter) {
                    RowItem(tr("Date without heading", "Datum zonder kopje"), icon = Icons.Filled.EditCalendar, trailing = {
                        Text(LiftrFormat.mediumDate(defaultDate), color = LiftrColors.accent)
                    }) { pickDefaultDate = true }
                }
            } else {
                CardSection(tr("Excel or CSV", "Excel of CSV"), tr("One row per training day with a date column and a column per exercise, e.g. “Date | Leg press | Chest press” with “45”, “27 (3×10)” or “25 / 18” in the cells. A file with one row per set (date, exercise, reps, weight) works too, as does a Liftr export.",
                    "Eén rij per trainingsdag met een datumkolom en een kolom per oefening, bijvoorbeeld “Datum | Leg press | Chest press” met “45”, “27 (3×10)” of “25 / 18” in de cellen. Een bestand met één rij per set (datum, oefening, herhalingen, gewicht) kan ook, net als een Liftr-export.") + "\n\n" + healthFooter) {
                    RowItem(tr("Choose file", "Kies bestand"), icon = Icons.Filled.TableChart, color = LiftrColors.accent) { pickFile() }
                    Divider()
                    RowItem(tr("Download the import sheet", "Download de importsheet"), icon = Icons.Filled.FileDownload, color = LiftrColors.accent) {
                        val url = if (L10n.isDutch) "https://liftr.nu/#importsheet" else "https://liftr.nu/en/#import-sheet"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            }
        } else {
            if (fileMode) {
                CardSection(fileName, tr("Used when a cell only holds a weight. A cell like “27 (3×10)” keeps its own sets and reps.",
                    "Gebruikt als een cel alleen een gewicht bevat. Een cel als “27 (3×10)” houdt zijn eigen sets en herhalingen.")) {
                    if (tables.size > 1) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tr("Sheet", "Werkblad"), color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
                            FlowChips(tables.mapIndexed { i, t -> i.toString() to t.name }, tableIndex.toString()) { tableIndex = it.toInt(); reparse() }
                        }
                        Divider()
                    }
                    StepperRow(tr("Sets per weight", "Sets per gewicht"), "${options.defaultSets}",
                        onMinus = { options = options.copy(defaultSets = maxOf(1, options.defaultSets - 1)); reparse() },
                        onPlus = { options = options.copy(defaultSets = minOf(10, options.defaultSets + 1)); reparse() })
                    Divider()
                    StepperRow(tr("Reps per set", "Herhalingen per set"), "${options.defaultReps}",
                        onMinus = { options = options.copy(defaultReps = maxOf(1, options.defaultReps - 1)); reparse() },
                        onPlus = { options = options.copy(defaultReps = minOf(50, options.defaultReps + 1)); reparse() })
                }
                if (exerciseColumns.isNotEmpty()) {
                    CardSection(tr("Exercises", "Oefeningen"), tr("Rename a column to link it to an exercise you already have.",
                        "Geef een kolom een andere naam om hem aan een oefening te koppelen die je al hebt.")) {
                        for (column in exerciseColumns) {
                            val current = renames[column] ?: column
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                LiftrTextField(current, { renames[column] = it }, column)
                                val found = NotesImporter.match(current, data.exercises.values)
                                Text(found?.let { trf("Linked to {1}", "Gekoppeld aan {1}", it.name) } ?: tr("New exercise", "Nieuwe oefening"),
                                    color = LiftrColors.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (skipped.isNotEmpty()) {
                    CardSection {
                        RowItem(trf("{1} cells not read", "{1} cellen niet gelezen", skipped.size), color = LiftrColors.accent) { showSkipped = !showSkipped }
                        if (showSkipped) for (s in skipped) Text(s, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = LiftrColors.muted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                    }
                }
            }
            if (days.isEmpty()) {
                Text(if (fileMode) tr("Liftr couldn't find a date column with exercises in this file.", "Liftr vond in dit bestand geen datumkolom met oefeningen.")
                    else tr("Liftr couldn't find any exercises. Use lines like “Leg press 3x12 40 kg”.", "Liftr vond geen oefeningen. Gebruik regels als “Leg press 3x12 40 kg”."),
                    color = LiftrColors.muted, modifier = Modifier.fillMaxWidth().liftrCard())
            } else {
                CardSection(footer = healthFooter) {
                    RowItem(trf("{1} workouts from {2} to {3}", "{1} trainingen van {2} tot {3}", days.size,
                        LiftrFormat.day(days.minOf { it.date }), LiftrFormat.day(days.maxOf { it.date })), icon = Icons.Filled.EditCalendar)
                }
                days.forEachIndexed { di, day ->
                    CardSection(if (day.note.isEmpty()) LiftrFormat.day(day.date) else LiftrFormat.day(day.date) + " · " + day.note) {
                        RowItem(tr("Date", "Datum"), trailing = { Text(LiftrFormat.mediumDate(day.date), color = LiftrColors.accent) }) { dateFor = di }
                        day.lines.forEachIndexed { li, line ->
                            Divider()
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        if (fileMode) Text(line.name, color = Color.White.copy(alpha = if (line.include) 1f else 0.45f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                        else LiftrTextField(line.name, { n -> days[di] = day.copy(lines = day.lines.toMutableList().also { it[li] = line.copy(name = n) }) }, tr("Exercise", "Oefening"))
                                    }
                                    Switch(line.include, { on -> days[di] = day.copy(lines = day.lines.toMutableList().also { it[li] = line.copy(include = on) }) },
                                        colors = switchColors(), modifier = Modifier.padding(start = 8.dp))
                                }
                                Text(describe(line, unit), color = LiftrColors.accent.copy(alpha = if (line.include) 1f else 0.45f), fontSize = 15.sp)
                                Text(if (line.note.isEmpty()) line.original else line.original + " · " + line.note, color = LiftrColors.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    result?.let { r ->
        val added = trf("{1} workouts added to your history.", "{1} trainingen toegevoegd aan je geschiedenis.", r.added)
        val message = if (r.skipped > 0) added + " " + trf("{1} were already imported and were skipped.", "{1} waren al eerder geïmporteerd en zijn overgeslagen.", r.skipped) else added
        AlertDialog(onDismissRequest = { result = null; close() }, title = { Text(tr("Imported", "Geïmporteerd")) }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { result = null; close() }) { Text("OK") } })
    }
    loadError?.let { msg ->
        AlertDialog(onDismissRequest = { loadError = null }, title = { Text(tr("Can't read file", "Bestand niet gelezen")) }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = { loadError = null }) { Text("OK") } })
    }
    if (pickDefaultDate || dateFor != null) {
        val initial = if (dateFor != null) days.getOrNull(dateFor!!)?.date ?: defaultDate else defaultDate
        val state = rememberDatePickerState(initialSelectedDateMillis = LiftrFormat.localDate(initial).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDefaultDate = false; dateFor = null }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { utc ->
                    val day = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                    val idx = dateFor
                    if (idx != null) {
                        days.getOrNull(idx)?.let { d ->
                            val time = LiftrFormat.zoned(d.date).toLocalTime()
                            days[idx] = d.copy(date = day.atTime(time).atZone(LiftrFormat.zone).toInstant().toEpochMilli())
                        }
                    } else {
                        defaultDate = day.atTime(18, 0).atZone(LiftrFormat.zone).toInstant().toEpochMilli()
                    }
                }
                pickDefaultDate = false; dateFor = null
            }) { Text("OK") }
        }) { DatePicker(state) }
    }
}

private fun displayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (e: Exception) {
    null
}

private fun describe(line: NotesImporter.ParsedLine, unit: nl.lrvdlinden.liftr.core.WeightUnit): String {
    if (line.sets.all { it.reps == 0 }) {
        val minutes = line.sets.sumOf { it.minutes }
        val km = line.sets.sumOf { it.km }
        return "${LiftrFormat.decimal(minutes)} min" + if (km > 0) " · ${LiftrFormat.decimal(km, 2)} km" else ""
    }
    return line.sets.joinToString(", ") { if (it.weightKg > 0) "${it.reps} × ${unit.format(it.weightKg)}" else "${it.reps} ×" }
}

