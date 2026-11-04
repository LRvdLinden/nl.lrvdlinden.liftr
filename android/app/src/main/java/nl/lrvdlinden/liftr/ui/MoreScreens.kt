package nl.lrvdlinden.liftr.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lrvdlinden.liftr.LiftrApp
import nl.lrvdlinden.liftr.core.BodyMeasurement
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.core.trf
import nl.lrvdlinden.liftr.data.Settings
import nl.lrvdlinden.liftr.health.HealthConnectManager
import nl.lrvdlinden.liftr.workout.WorkoutController
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

/** Log weight (and optionally body fat); saved in Liftr and in Health Connect. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyEntryScreen() {
    val unit = Settings.unit
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var weight by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val value = LiftrFormat.parse(weight) ?: 0.0
    fun close() { Nav.stack.lastOrNull { it is Route.BodyEntry }?.let { Nav.remove(it) } }
    DetailScaffold(tr("Log weight", "Gewicht invoeren"), onBack = { close() }, actions = {
        TextButton(enabled = value > 0 && !saving, onClick = {
            saving = true
            val kg = unit.toKg(value)
            val f = LiftrFormat.parse(fat)
            LiftrApp.repository.commit { body += BodyMeasurement(date = date, weightKg = kg, bodyFatPercent = f ?: 0.0) }
            scope.launch {
                HealthConnectManager.saveBody(LiftrApp.instance, kg, f, date)
                close()
            }
        }) { Text(tr("Save", "Bewaar"), color = LiftrColors.accent, fontWeight = FontWeight.Bold) }
    }) {
        CardSection {
            RowItem(tr("Date", "Datum"), trailing = { Text(LiftrFormat.mediumDate(date), color = LiftrColors.accent) }) { pickDate = true }
            Divider()
            RowItem(tr("Weight", "Gewicht"), trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(110.dp)) { LiftrTextField(weight, { weight = it }, "0", keyboardType = KeyboardType.Decimal) }
                    Text(" ${unit.symbol}", color = LiftrColors.muted)
                }
            })
            Divider()
            RowItem(tr("Body fat (optional)", "Vetpercentage (optioneel)"), trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(90.dp)) { LiftrTextField(fat, { fat = it }, "0", keyboardType = KeyboardType.Decimal) }
                    Text(" %", color = LiftrColors.muted)
                }
            })
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LiftrFormat.localDate(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = {
            TextButton(onClick = {
                pickDate = false
                state.selectedDateMillis?.let {
                    val day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    val t = day.atTime(LocalTime.now()).atZone(LiftrFormat.zone).toInstant().toEpochMilli()
                    date = minOf(t, System.currentTimeMillis())
                }
            }) { Text("OK") }
        }) { DatePicker(state) }
    }
}

/** Privacy statement, reachable from Settings (PrivacyView.swift, adapted to Android: no iCloud, Health Connect). */
@Composable
fun PrivacyScreen() {
    val context = LocalContext.current
    val items: List<Triple<ImageVector, String, String>> = listOf(
        Triple(Icons.Filled.Lock, tr("Your data stays yours", "Jouw gegevens blijven van jou"),
            tr("Liftr has no account, no server and no advertising. Your workouts, exercises, routines, body measurements and gym pass are stored only on your phone (and your watch, if you use one).",
                "Liftr heeft geen account, geen server en geen advertenties. Je trainingen, oefeningen, schema's, lichaamsmetingen en sportschoolpas staan alleen op je telefoon (en je horloge, als je dat gebruikt).")),
        Triple(Icons.Filled.Favorite, "Health Connect",
            tr("Only with your permission, Liftr reads your weight, body composition and resting heart rate, and saves workouts, calories, distance and weight. Health data never leaves your devices through Liftr and is never used for advertising. You can change access at any time in Health Connect.",
                "Alleen met jouw toestemming leest Liftr je gewicht, lichaamssamenstelling en rusthartslag, en bewaart het trainingen, calorieën, afstand en gewicht. Gezondheidsgegevens verlaten je apparaten nooit via Liftr en worden nooit voor advertenties gebruikt. Je wijzigt de toegang altijd in Health Connect.")),
        Triple(Icons.Filled.PhotoCamera, tr("Camera and photos", "Camera en foto's"),
            tr("The camera and your photos are only used to read the code of your gym pass. Images are not saved or sent anywhere.",
                "De camera en je foto's worden alleen gebruikt om de code van je sportschoolpas te lezen. Afbeeldingen worden niet bewaard of verstuurd.")),
        Triple(Icons.Filled.Notifications, tr("Notifications", "Meldingen"),
            tr("Reminders and the rest timer are planned on your device. There is no push server.",
                "Herinneringen en de rusttimer worden op je apparaat ingepland. Er is geen pushserver.")),
        Triple(Icons.Filled.VisibilityOff, tr("No tracking", "Geen tracking"),
            tr("Liftr contains no analytics and no tracking of any kind.", "Liftr bevat geen analytics en geen enkele vorm van tracking.")),
        Triple(Icons.Filled.Delete, tr("Deleting your data", "Je gegevens verwijderen"),
            tr("Delete workouts in the app, or everything at once in Settings → Manage data. Uninstalling Liftr removes all its data from your phone.",
                "Verwijder trainingen in de app, of alles tegelijk via Instellingen → Gegevens beheren. Als je Liftr verwijdert, verdwijnen al zijn gegevens van je telefoon.")),
    )
    DetailScaffold(tr("Privacy", "Privacy"), onBack = { Nav.stack.lastOrNull { it is Route.Privacy }?.let { Nav.remove(it) } }) {
        Text(tr("Privacy", "Privacy").uppercase(), style = LiftrType.display(40.sp))
        Text(tr("Liftr is built to keep your training private.", "Liftr is gemaakt om je training privé te houden."), color = LiftrColors.muted, fontSize = 19.sp)
        for ((icon, title, text) in items) {
            Row(Modifier.fillMaxWidth().liftrCard(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(icon, null, tint = LiftrColors.accent, modifier = Modifier.size(26.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(text, color = LiftrColors.muted, fontSize = 15.sp, lineHeight = 20.sp)
                }
            }
        }
        Column(Modifier.fillMaxWidth().liftrCard(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Questions?", "Vragen?"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("liftr.nu/privacy", color = LiftrColors.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://liftr.nu/privacy/"))) })
            Text("lrvdlinden.app", color = LiftrColors.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lrvdlinden.app"))) })
        }
        Text(tr("Last updated: November 2026", "Laatst bijgewerkt: november 2026"), color = LiftrColors.muted, fontSize = 12.sp)
    }
}

private data class ImportBatch(val source: String, val label: String, val date: Long?, val count: Int, val first: Long, val last: Long)

/** Delete one import again, or wipe everything Liftr stores. */
@Composable
fun DataManagementScreen(data: LiftrData) {
    val scope = rememberCoroutineScope()
    val repo = LiftrApp.repository
    var deleting by remember { mutableStateOf<ImportBatch?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    val batches = data.workouts.values.filter { it.isImported }.groupBy { it.source }.map { (source, list) ->
        val info = list.first().importBatch
        ImportBatch(source, info?.second?.ifEmpty { null } ?: tr("Earlier import", "Eerdere import"), info?.first, list.size,
            list.minOf { it.startDate }, list.maxOf { it.startDate })
    }.sortedByDescending { it.date ?: 0L }

    fun wipe(alsoHealth: Boolean) {
        working = true
        scope.launch {
            if (WorkoutController.activeId != null) WorkoutController.discard()
            val all = repo.current.workouts.values.toList()
            if (alsoHealth) for (w in all) if (w.healthRecordId != null) HealthConnectManager.deleteWorkout(LiftrApp.instance, w)
            repo.commitAndWait { d ->
                for (w in d.workouts.keys) removeWorkout(d, w)
                for (r in d.routines.keys) removeRoutine(d, r)
                deleteBody += d.body.keys
                deletePasses += d.passes.keys
                for (e in d.exercises.values) {
                    if (e.seedId == null) deleteExercises += e.id
                    else exercises += e.copy(isFavorite = false, isArchived = false, notes = "")
                }
            }
            repo.prepareLibrary()
            LiftrApp.afterWorkoutChange()
            working = false
            done = tr("All data has been deleted.", "Alle gegevens zijn gewist.")
        }
    }

    DetailScaffold(tr("Manage data", "Gegevens beheren"), onBack = { Nav.stack.lastOrNull { it is Route.DataManagement }?.let { Nav.remove(it) } }) {
        CardSection(tr("Imports", "Imports"), tr("Deleting an import removes only the workouts from that file. Workouts you logged in Liftr stay.",
            "Een import verwijderen haalt alleen de trainingen uit dat bestand weg. Trainingen die je in Liftr hebt gelogd blijven staan.")) {
            if (batches.isEmpty()) RowItem(tr("No imports yet.", "Nog geen imports."), color = LiftrColors.muted)
            batches.forEachIndexed { i, batch ->
                if (i > 0) Divider()
                val isFile = batch.label.lowercase().let { it.endsWith(".csv") || it.endsWith(".xlsx") }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (isFile) Icons.Filled.TableChart else Icons.Filled.StickyNote2, null, tint = LiftrColors.accent, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(batch.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(trf("{1} workouts · {2} – {3}", "{1} trainingen · {2} – {3}", batch.count, LiftrFormat.shortDay(batch.first), LiftrFormat.shortDay(batch.last)),
                            color = LiftrColors.muted, fontSize = 12.sp)
                        batch.date?.let { Text(trf("Imported {1}", "Geïmporteerd op {1}", LiftrFormat.dateTime(it)), color = LiftrColors.muted, fontSize = 11.sp) }
                    }
                    IconButton(onClick = { deleting = batch }) { Icon(Icons.Filled.Delete, tr("Delete import", "Import verwijderen"), tint = LiftrColors.danger) }
                }
            }
        }
        CardSection(footer = tr("Deletes all workouts, routines, body measurements, gym passes and your own exercises from this phone. Your settings stay.",
            "Wist alle trainingen, schema's, lichaamsmetingen, sportschoolpassen en je eigen oefeningen van deze telefoon. Je instellingen blijven staan.")) {
            RowItem(tr("Delete all data", "Alle gegevens wissen"), icon = Icons.Filled.DeleteForever, iconTint = LiftrColors.danger, color = LiftrColors.danger) {
                if (!working) confirmWipe = true
            }
        }
        if (working) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LiftrColors.accent) }
    }

    deleting?.let { batch ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(tr("Delete this import?", "Deze import verwijderen?")) },
            text = { Text(batch.label) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    repo.commit { d -> for (w in d.workouts.values) if (w.source == batch.source) removeWorkout(d, w.id) }
                    LiftrApp.afterWorkoutChange()
                    done = trf("{1} imported workouts deleted.", "{1} geïmporteerde trainingen verwijderd.", batch.count)
                }) { Text(trf("Delete {1} workouts", "{1} trainingen verwijderen", batch.count), color = LiftrColors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Cancel", "Annuleren")) } },
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(tr("Delete all data?", "Alle gegevens wissen?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("This can't be undone.", "Dit kan niet ongedaan worden gemaakt."))
                    TextButton(onClick = { confirmWipe = false; wipe(true) }) {
                        Text(tr("Delete all, also from Health Connect", "Alles wissen, ook uit Health Connect"), color = LiftrColors.danger)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { confirmWipe = false; wipe(false) }) { Text(tr("Delete all data", "Alle gegevens wissen"), color = LiftrColors.danger) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(tr("Cancel", "Annuleren")) } },
        )
    }
    done?.let {
        AlertDialog(onDismissRequest = { done = null }, title = { Text(tr("Done", "Klaar")) }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { done = null }) { Text("OK") } })
    }
}

