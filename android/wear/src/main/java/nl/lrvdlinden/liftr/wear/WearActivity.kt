package nl.lrvdlinden.liftr.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.lrvdlinden.liftr.core.BarcodeFormat
import nl.lrvdlinden.liftr.core.BarcodeRenderer
import nl.lrvdlinden.liftr.core.L10n
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.Muscle
import nl.lrvdlinden.liftr.core.WearSync
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.core.trf

private val Accent = Color(0xFFFF6B00)
private val AccentDeep = Color(0xFFE65400)
private val Card = Color(0xFF1B1B1E)
private val Raised = Color(0xFF27272A)
private val Muted = Color(0xFF9E9EA8)

/** Liftr on Wear OS (port of LiftrWatch): start a workout, log sets, rest timer, heart rate, gym pass. */
class WearActivity : ComponentActivity(), SensorEventListener {
    private var sensorManager: SensorManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L10n.init(this)
        WearStore.init(this)
        lifecycleScope.launch {
            WearStore.pull(this@WearActivity)
            WearStore.flushOutbox(this@WearActivity)
        }
        setContent { MaterialTheme { WearApp(::startHeartRate) } }
    }

    fun startHeartRate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) return
        val sm = getSystemService(SensorManager::class.java) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE) ?: return
        sensorManager = sm
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun stopHeartRate() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HEART_RATE && event.values.isNotEmpty()) WearStore.recordHeartRate(event.values[0].toDouble())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        stopHeartRate()
        super.onDestroy()
    }
}

@Composable
private fun WearApp(startHeartRate: () -> Unit) {
    val nav = rememberSwipeDismissableNavController()
    val context = LocalContext.current
    val activity = context as? WearActivity
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startHeartRate() }
    var summary by remember { mutableStateOf<WearSync.WatchWorkout?>(null) }
    LaunchedEffect(Unit) { if (WearStore.workout != null) { nav.navigate("workout"); startHeartRate() } }

    fun begin(routine: WearSync.WRoutine?) {
        WearStore.start(routine)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) startHeartRate()
        else permission.launch(Manifest.permission.BODY_SENSORS)
        nav.navigate("workout")
    }

    SwipeDismissableNavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(::begin, onPass = { nav.navigate("pass/$it") }, onResume = { nav.navigate("workout") }) }
        composable("workout") {
            WorkoutScreen(
                onPick = { nav.navigate("picker") },
                onFinished = { w ->
                    activity?.stopHeartRate()
                    summary = w
                    nav.popBackStack("home", false)
                    if (w != null) nav.navigate("summary")
                },
            )
        }
        composable("picker") { PickerScreen { id -> WearStore.add(id); nav.popBackStack() } }
        composable("summary") { SummaryScreen(summary) { nav.popBackStack() } }
        composable("pass/{id}") { entry -> PassScreen(entry.arguments?.getString("id") ?: "") }
    }
}

@Composable
private fun HomeScreen(onStart: (WearSync.WRoutine?) -> Unit, onPass: (String) -> Unit, onResume: () -> Unit) {
    val state = WearStore.state
    ScalingLazyColumn(Modifier.fillMaxSize().background(Color.Black)) {
        item { Text("LIFTR", color = Accent, fontWeight = FontWeight.Black, fontSize = 18.sp) }
        item {
            val active = WearStore.workout != null
            Chip(
                onClick = { if (active) onResume() else onStart(null) },
                label = { Text(if (active) tr("Continue", "Ga verder").uppercase() else tr("START", "START"), fontWeight = FontWeight.Black) },
                icon = { Icon(Icons.Filled.PlayArrow, null) },
                colors = ChipDefaults.gradientBackgroundChipColors(startBackgroundColor = Accent, endBackgroundColor = AccentDeep),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(state.passes) { pass ->
            Chip(
                onClick = { onPass(pass.id) },
                label = { Text(pass.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(passColor(pass.colorHex)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.QrCode, null, tint = if (inkBlack(pass.colorHex)) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                    }
                },
                colors = ChipDefaults.secondaryChipColors(backgroundColor = Card),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.routines.isNotEmpty()) {
            item { ListHeader { Text(tr("Routines", "Schema's"), color = Muted) } }
            items(state.routines) { routine ->
                Chip(
                    onClick = { if (WearStore.workout == null) onStart(routine) else onResume() },
                    label = { Text(routine.name, maxLines = 1) },
                    secondaryLabel = { Text(trf("{1} exercises", "{1} oefeningen", routine.items.size), color = Muted) },
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = Card),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Text("🔥 " + trf("{1} this week", "{1} deze week", state.weekCount) + " · " + trf("{1} wk", "{1} wk", state.streak),
                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        if (state.exercises.isEmpty()) {
            item {
                Text(tr("Open Liftr on your phone to bring over your exercises and routines.",
                    "Open Liftr op je telefoon om je oefeningen en schema's over te zetten."), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun passColor(hex: String) = Color(nl.lrvdlinden.liftr.core.GymTemplates.argb(hex))
private fun inkBlack(hex: String) = nl.lrvdlinden.liftr.core.GymTemplates.inkIsBlack(hex)

@Composable
private fun WorkoutScreen(onPick: () -> Unit, onFinished: (WearSync.WatchWorkout?) -> Unit) {
    val workout = WearStore.workout
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entryIndex by remember { mutableIntStateOf(-1) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    if (workout == null) { LaunchedEffect(Unit) { onFinished(null) }; return }

    val restEnd = WearStore.restEnd
    if (restEnd != null) {
        val remaining = restEnd - now
        LaunchedEffect(restEnd, remaining <= 0) {
            if (remaining <= 0) {
                vibrate(context)
                WearStore.restEnd = null
            }
        }
        Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(tr("REST", "RUST"), color = Muted, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text(LiftrFormat.duration(maxOf(0, (remaining + 999) / 1000)), color = Accent, fontSize = 44.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Button(onClick = { WearStore.restEnd = null }, colors = ButtonDefaults.buttonColors(backgroundColor = Accent), modifier = Modifier.padding(top = 6.dp)) {
                Text(tr("Skip", "Overslaan"), fontSize = 12.sp)
            }
        }
        return
    }

    val entries = workout.entries
    val current = entries.getOrNull(entryIndex) ?: entries.firstOrNull { e -> e.sets.any { !it.done } } ?: entries.lastOrNull()
    val currentIndex = if (current == null) -1 else entries.indexOf(current)
    val unit = WearStore.unit
    ScalingLazyColumn(Modifier.fillMaxSize().background(Color.Black)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LiftrFormat.duration((now - workout.start) / 1000), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                if (WearStore.heartRate > 0) {
                    Icon(Icons.Filled.Favorite, null, tint = Color(0xFFFF375F), modifier = Modifier.size(12.dp))
                    Text("${WearStore.heartRate.toInt()}", color = Color.White, fontSize = 14.sp)
                }
            }
        }
        if (current == null) {
            item { Text(tr("Choose your first exercise", "Kies je eerste oefening"), color = Color.White, textAlign = TextAlign.Center) }
            item {
                Chip(onClick = onPick, label = { Text(tr("Exercise", "Oefening")) }, icon = { Icon(Icons.Filled.Add, null) },
                    colors = ChipDefaults.primaryChipColors(backgroundColor = Accent), modifier = Modifier.fillMaxWidth())
            }
        } else {
            item {
                Text(current.exerciseName.uppercase(), color = Accent, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 2,
                    textAlign = TextAlign.Center, modifier = Modifier.clickable { onPick() })
            }
            val setIndex = current.sets.indexOfFirst { !it.done }
            if (setIndex >= 0) {
                item { SetEditor(currentIndex, setIndex, current, unit) }
            } else {
                item { Text(trf("{1} sets done", "{1} sets gedaan", current.sets.count { it.done }), color = Muted, fontSize = 12.sp) }
                item {
                    Chip(onClick = { WearStore.addSet(currentIndex) }, label = { Text(tr("Extra set", "Extra set")) }, icon = { Icon(Icons.Filled.Add, null) },
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = Card), modifier = Modifier.fillMaxWidth())
                }
                item {
                    Chip(onClick = {
                        val next = entries.indexOfFirst { e -> e.sets.any { !it.done } }
                        if (next >= 0) entryIndex = next else onPick()
                    }, label = { Text(tr("Next exercise", "Volgende oefening")) }, icon = { Icon(Icons.Filled.ArrowForward, null) },
                        colors = ChipDefaults.primaryChipColors(backgroundColor = Accent), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (entries.size > 1) {
            item { ListHeader { Text(tr("In this workout", "In deze training"), color = Muted) } }
            items(entries.size) { i ->
                val e = entries[i]
                Chip(onClick = { entryIndex = i }, label = { Text(e.exerciseName, maxLines = 1) },
                    secondaryLabel = { Text("${e.sets.count { it.done }}/${e.sets.size}", color = Muted) },
                    icon = { if (e.sets.all { it.done }) Icon(Icons.Filled.Check, null, tint = Accent) },
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = if (i == currentIndex) Raised else Card), modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Chip(onClick = onPick, label = { Text(tr("Add exercise", "Oefening toevoegen")) }, icon = { Icon(Icons.Filled.Add, null) },
                colors = ChipDefaults.secondaryChipColors(backgroundColor = Card), modifier = Modifier.fillMaxWidth())
        }
        item {
            Chip(onClick = { scope.launch { onFinished(WearStore.finish(context)) } }, label = { Text(tr("Finish", "Afronden"), fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Filled.Check, null) }, colors = ChipDefaults.gradientBackgroundChipColors(startBackgroundColor = Accent, endBackgroundColor = AccentDeep),
                modifier = Modifier.fillMaxWidth())
        }
        item {
            var confirm by remember { mutableStateOf(false) }
            Chip(onClick = { if (confirm) { WearStore.discard(); onFinished(null) } else confirm = true },
                label = { Text(if (confirm) tr("Discard this workout?", "Training weggooien?") else tr("Discard", "Weggooien"), color = Color(0xFFFF453A)) },
                colors = ChipDefaults.secondaryChipColors(backgroundColor = Card), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SetEditor(entryIndex: Int, setIndex: Int, entry: WearSync.WEntry, unit: nl.lrvdlinden.liftr.core.WeightUnit) {
    val set = entry.sets[setIndex]
    var weight by remember(entryIndex, setIndex) { mutableStateOf(Math.round(unit.fromKg(set.weight) * 10) / 10.0) }
    var reps by remember(entryIndex, setIndex) { mutableIntStateOf(set.reps) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(trf("Set {1} of {2}", "Set {1} van {2}", setIndex + 1, entry.sets.size), color = Muted, fontSize = 11.sp)
        if (!entry.cardio) {
            ValueRow(unit.symbol.uppercase(), LiftrFormat.decimal(weight), { weight = maxOf(0.0, weight - unit.step) }, { weight += unit.step })
            ValueRow(tr("REPS", "HERH"), "$reps", { reps = maxOf(0, reps - 1) }, { reps += 1 })
        } else {
            Text("${set.durationSeconds / 60} min", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = { WearStore.complete(entryIndex, setIndex, reps, unit.toKg(weight)) },
            colors = ButtonDefaults.buttonColors(backgroundColor = Accent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null)
                Text(" " + tr("Set done", "Set klaar"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ValueRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = onMinus, colors = ButtonDefaults.buttonColors(backgroundColor = Raised), modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Remove, null, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Button(onClick = onPlus, colors = ButtonDefaults.buttonColors(backgroundColor = Raised), modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PickerScreen(onPick: (String) -> Unit) {
    val state = WearStore.state
    val inWorkout = WearStore.workout?.entries.orEmpty()
    ScalingLazyColumn(Modifier.fillMaxSize().background(Color.Black)) {
        if (inWorkout.isNotEmpty()) {
            item { ListHeader { Text(tr("In this workout", "In deze training"), color = Muted) } }
            items(inWorkout) { e ->
                Chip(onClick = { onPick(e.exerciseId) }, label = { Text(e.exerciseName, maxLines = 2) },
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = Raised), modifier = Modifier.fillMaxWidth())
            }
        }
        for (m in Muscle.entries) {
            val list = state.exercises.filter { it.muscle == m.raw }.sortedByDescending { it.favorite }
            if (list.isEmpty()) continue
            item { ListHeader { Text(m.title, color = Muted) } }
            items(list) { ex ->
                Chip(onClick = { onPick(ex.id) }, label = { Text(ex.name, maxLines = 2) },
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = Card), modifier = Modifier.fillMaxWidth())
            }
        }
        if (state.exercises.isEmpty()) {
            item {
                Text(tr("Open Liftr on your phone to bring over your exercises and routines.",
                    "Open Liftr op je telefoon om je oefeningen en schema's over te zetten."), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SummaryScreen(workout: WearSync.WatchWorkout?, onClose: () -> Unit) {
    val unit = WearStore.unit
    ScalingLazyColumn(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Accent, AccentDeep))), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color.White)
            }
        }
        item { Text(tr("Well done!", "Goed gedaan!").uppercase(), fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White) }
        if (workout != null) {
            item { Text(LiftrFormat.longDuration((workout.end ?: workout.start) - workout.start), color = Color.White, fontSize = 15.sp) }
            val sets = workout.entries.sumOf { e -> e.sets.size }
            val volume = workout.entries.filter { !it.cardio }.sumOf { e -> e.sets.sumOf { it.reps * it.weight } }
            item { Text(trf("{1} sets", "{1} sets", sets) + " · " + unit.volume(volume), color = Muted, fontSize = 12.sp) }
            item { Text(tr("Sent to your phone", "Naar je telefoon gestuurd"), color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center) }
        }
        item {
            Button(onClick = onClose, colors = ButtonDefaults.buttonColors(backgroundColor = Accent)) { Icon(Icons.Filled.Check, tr("Close", "Sluiten")) }
        }
    }
}

/** The gym pass on the wrist: white background, code as large as the screen allows, full brightness. */
@Composable
private fun PassScreen(id: String) {
    val pass = WearStore.state.passes.firstOrNull { it.id == id }
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? ComponentActivity)?.window
        val old = window?.attributes?.screenBrightness
        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
        onDispose { window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = old ?: -1f } } }
    }
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        if (pass == null) return@Box
        val format = BarcodeFormat.from(pass.format)
        val bitmap = remember(pass.code, pass.format) {
            if (format.isLinear) BarcodeRenderer.render(pass.code, format, 600, 240) else BarcodeRenderer.render(pass.code, format, 400, 400)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(if (format.isLinear) 14.dp else 30.dp)) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), pass.name, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
            } else {
                Text(tr("This code can't be drawn", "Deze code kan niet worden getekend"), color = Color.Gray, textAlign = TextAlign.Center)
            }
            if (format.isLinear) Text(pass.code, color = Color.Black, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}

private fun vibrate(context: Context) {
    try {
        context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
    } catch (_: Exception) {}
}
