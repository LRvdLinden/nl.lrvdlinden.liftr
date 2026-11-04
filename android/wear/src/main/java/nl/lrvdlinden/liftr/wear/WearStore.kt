package nl.lrvdlinden.liftr.wear

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import nl.lrvdlinden.liftr.core.WeightUnit
import nl.lrvdlinden.liftr.core.WearSync
import nl.lrvdlinden.liftr.core.newId
import nl.lrvdlinden.liftr.core.tr
import java.io.File

/**
 * The watch's data: what the phone shares (exercises, routines, passes, last sets) and the workout in
 * progress, kept as JSON files on the watch. A finished workout goes to the phone as a data item, which the
 * Data Layer delivers as soon as phone and watch are connected.
 */
object WearStore {
    var state by mutableStateOf(WearSync.WatchState()); private set
    var workout by mutableStateOf<WearSync.WatchWorkout?>(null); private set
    var heartRate by mutableDoubleStateOf(0.0)
    var restEnd by mutableStateOf<Long?>(null)
    private var hrSum = 0.0
    private var hrCount = 0

    private lateinit var dir: File
    val unit: WeightUnit get() = WeightUnit.from(state.unit)

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = context.filesDir
        File(dir, "state.json").takeIf { it.exists() }?.readText()?.let { WearSync.decodeState(it) }?.let { state = it }
        File(dir, "current.json").takeIf { it.exists() }?.readText()?.let { WearSync.decodeWorkout(it) }?.let { workout = it }
    }

    fun setState(json: String) {
        val decoded = WearSync.decodeState(json) ?: return
        state = decoded
        if (::dir.isInitialized) File(dir, "state.json").writeText(json)
    }

    /** Reads the latest state the phone left (also when it arrived while the app was closed). */
    suspend fun pull(context: Context) {
        try {
            val items = Wearable.getDataClient(context).dataItems.await()
            try {
                for (item in items) {
                    if (item.uri.path == WearSync.STATE_PATH) {
                        DataMapItem.fromDataItem(item).dataMap.getString(WearSync.KEY_JSON)?.let { setState(it) }
                    }
                }
            } finally {
                items.release()
            }
        } catch (e: Exception) {
            Log.i("LiftrWear", "No phone data: ${e.message}")
        }
    }

    private fun save() {
        val w = workout
        val file = File(dir, "current.json")
        if (w == null) file.delete() else file.writeText(WearSync.encode(w))
    }

    private fun update(w: WearSync.WatchWorkout?) {
        workout = w
        save()
    }

    fun exerciseName(id: String): String = state.exercises.firstOrNull { it.id == id }?.name ?: tr("Exercise", "Oefening")
    fun isCardio(id: String): Boolean = state.exercises.firstOrNull { it.id == id }?.kind == "cardio"

    // MARK: Workout

    fun start(routine: WearSync.WRoutine?) {
        if (workout != null) return
        hrSum = 0.0; hrCount = 0
        val entries = routine?.items?.map { item ->
            val last = state.last[item.exerciseId].orEmpty()
            WearSync.WEntry(item.exerciseId, exerciseName(item.exerciseId), isCardio(item.exerciseId),
                (0 until maxOf(1, item.sets)).map { n ->
                    val t = if (n < last.size) last[n] else last.lastOrNull()
                    WearSync.WSet(t?.reps ?: item.reps, t?.weight ?: item.weight, durationSeconds = t?.durationSeconds ?: 0, distanceMeters = t?.distanceMeters ?: 0.0)
                })
        }.orEmpty()
        update(WearSync.WatchWorkout(newId(), routine?.name ?: tr("Workout", "Training"), System.currentTimeMillis(), null, 0.0, 0.0, entries))
    }

    /** Adds an exercise with what was done last time; returns its index. */
    fun add(exerciseId: String): Int {
        val w = workout ?: return 0
        val existing = w.entries.indexOfFirst { it.exerciseId == exerciseId }
        if (existing >= 0) return existing
        val last = state.last[exerciseId].orEmpty()
        val sets = last.ifEmpty { listOf(WearSync.WSet(if (isCardio(exerciseId)) 0 else 10, 0.0, durationSeconds = if (isCardio(exerciseId)) 900 else 0)) }
            .map { it.copy(done = false, completedAt = null) }
        update(w.copy(entries = w.entries + WearSync.WEntry(exerciseId, exerciseName(exerciseId), isCardio(exerciseId), sets)))
        return w.entries.size
    }

    fun complete(entryIndex: Int, setIndex: Int, reps: Int, weightKg: Double) {
        val w = workout ?: return
        val entries = w.entries.toMutableList()
        val e = entries.getOrNull(entryIndex) ?: return
        val sets = e.sets.toMutableList()
        if (setIndex !in sets.indices) return
        sets[setIndex] = sets[setIndex].copy(reps = reps, weight = weightKg, done = true, completedAt = System.currentTimeMillis())
        entries[entryIndex] = e.copy(sets = sets)
        update(w.copy(entries = entries))
        if (!e.cardio) restEnd = System.currentTimeMillis() + state.restSeconds * 1000L
    }

    fun addSet(entryIndex: Int) {
        val w = workout ?: return
        val entries = w.entries.toMutableList()
        val e = entries.getOrNull(entryIndex) ?: return
        val last = e.sets.lastOrNull() ?: WearSync.WSet(10, 0.0)
        entries[entryIndex] = e.copy(sets = e.sets + last.copy(done = false, completedAt = null))
        update(w.copy(entries = entries))
    }

    fun recordHeartRate(bpm: Double) {
        if (bpm <= 0) return
        heartRate = bpm
        if (workout != null) { hrSum += bpm; hrCount++ }
    }

    /** Finishes the workout and hands it to the phone. Returns the finished workout for the summary. */
    suspend fun finish(context: Context): WearSync.WatchWorkout? {
        val w = workout ?: return null
        restEnd = null
        val end = System.currentTimeMillis()
        val cleaned = w.entries.map { e -> e.copy(sets = e.sets.filter { it.done || it.reps > 0 || it.durationSeconds > 0 }) }.filter { it.sets.isNotEmpty() }
        val hours = (end - w.start) / 3_600_000.0
        val kcal = Math.round(5.0 * 70 * minOf(hours, 4.0)).toDouble()
        val finished = w.copy(end = end, entries = cleaned, averageHeartRate = if (hrCount > 0) hrSum / hrCount else 0.0, calories = kcal)
        update(null)
        if (finished.entries.isNotEmpty()) send(context, finished)
        return finished
    }

    fun discard() {
        restEnd = null
        update(null)
    }

    private suspend fun send(context: Context, w: WearSync.WatchWorkout) {
        try {
            val request = PutDataMapRequest.create("${WearSync.WORKOUT_PATH}/${w.id}").apply {
                dataMap.putString(WearSync.KEY_JSON, WearSync.encode(w))
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: Exception) {
            // Kept on the watch; tried again next launch.
            File(dir, "outbox-${w.id}.json").writeText(WearSync.encode(w))
        }
    }

    /** Sends workouts that could not be handed to the Data Layer earlier. */
    suspend fun flushOutbox(context: Context) {
        val files = dir.listFiles { f -> f.name.startsWith("outbox-") }.orEmpty()
        for (f in files) {
            val w = WearSync.decodeWorkout(f.readText()) ?: continue
            try {
                val request = PutDataMapRequest.create("${WearSync.WORKOUT_PATH}/${w.id}").apply {
                    dataMap.putString(WearSync.KEY_JSON, WearSync.encode(w))
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(context).putDataItem(request).await()
                f.delete()
            } catch (_: Exception) {}
        }
    }
}

class WearListenerService : com.google.android.gms.wearable.WearableListenerService() {
    override fun onDataChanged(events: com.google.android.gms.wearable.DataEventBuffer) {
        WearStore.init(applicationContext)
        for (event in events) {
            if (event.type != com.google.android.gms.wearable.DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path == WearSync.STATE_PATH) {
                DataMapItem.fromDataItem(item).dataMap.getString(WearSync.KEY_JSON)?.let { WearStore.setState(it) }
            }
        }
    }
}
