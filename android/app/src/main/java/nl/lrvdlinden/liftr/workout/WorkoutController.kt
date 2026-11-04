package nl.lrvdlinden.liftr.workout

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import nl.lrvdlinden.liftr.LiftrApp
import nl.lrvdlinden.liftr.core.Exercise
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.LiftrRepository
import nl.lrvdlinden.liftr.core.Prefs
import nl.lrvdlinden.liftr.core.Routine
import nl.lrvdlinden.liftr.core.SetKind
import nl.lrvdlinden.liftr.core.SetRecord
import nl.lrvdlinden.liftr.core.Workout
import nl.lrvdlinden.liftr.core.WorkoutEntry
import nl.lrvdlinden.liftr.core.WorkoutStats
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.data.Settings
import nl.lrvdlinden.liftr.health.HealthConnectManager
import java.time.LocalTime

/**
 * Runs the workout in progress (port of WorkoutController.swift): creating it, the rest timer and
 * finishing (saving to Health Connect). The workout is stored right away, so nothing is lost when the
 * app is closed mid-workout.
 */
object WorkoutController {
    var activeId by mutableStateOf<String?>(null); private set
    /** Epoch millis when the rest ends; null when no rest timer runs. */
    var restEnd by mutableStateOf<Long?>(null); private set
    var restTotal by mutableLongStateOf(90L); private set

    private val context: Context get() = LiftrApp.instance
    private val repo: LiftrRepository get() = LiftrRepository.get(context)

    /** Picks up a workout that was still running when the app was closed. */
    fun restore() {
        if (activeId != null) return
        val data = repo.current
        val running = data.workoutsNewestFirst.filter { it.endDate == null && it.source != "watch" }
        val workout = running.firstOrNull() ?: return
        if (System.currentTimeMillis() - workout.startDate > 12 * 3_600_000L) {
            // Older than 12 hours: close it at its last completed set.
            val last = data.sortedEntries(workout).flatMap { data.sortedSets(it) }.mapNotNull { it.completedAt }.maxOrNull()
            repo.commit { workouts += workout.copy(endDate = last ?: (workout.startDate + 3_600_000L)) }
        } else {
            activeId = workout.id
            val end = Prefs.prefs.getLong(Prefs.REST_END, 0L)
            if (end > System.currentTimeMillis()) {
                restEnd = end
                restTotal = Prefs.prefs.getLong(Prefs.REST_TOTAL, 90L)
            }
        }
    }

    fun active(data: LiftrData): Workout? = activeId?.let { data.workouts[it] }

    /** Starts a new workout (empty, or from a routine) and returns its id. */
    fun start(routine: Routine? = null): String {
        activeId?.let { return it }
        val title = routine?.name ?: defaultTitle()
        val workout = Workout(title = title, source = "android")
        repo.commit { data ->
            workouts += workout
            if (routine != null) {
                data.sortedItems(routine).forEachIndexed { index, item ->
                    val exercise = data.exercise(item.exerciseId) ?: return@forEachIndexed
                    val entry = WorkoutEntry(order = index, exerciseId = exercise.id, workoutId = workout.id)
                    entries += entry
                    val previous = WorkoutStats.previousSets(data, exercise, workout.id)?.sets?.filter { it.kind != SetKind.Warmup }
                    for (n in 0 until maxOf(1, item.targetSets)) {
                        val template = previous?.let { if (n < it.size) it[n] else it.lastOrNull() }
                        var set = SetRecord(order = n, reps = template?.reps ?: item.targetReps,
                            weight = template?.weight ?: item.targetWeight, entryId = entry.id)
                        if (exercise.kind.isCardio) {
                            set = set.copy(durationSeconds = template?.durationSeconds ?: (15 * 60), distanceMeters = template?.distanceMeters ?: 0.0)
                        }
                        sets += set
                    }
                }
            }
        }
        activeId = workout.id
        Prefs.prefs.edit().putString(Prefs.ACTIVE_WORKOUT, workout.id).apply()
        return workout.id
    }

    private fun defaultTitle(): String = when (LocalTime.now().hour) {
        in 5..11 -> tr("Morning workout", "Ochtendtraining")
        in 12..17 -> tr("Afternoon workout", "Middagtraining")
        else -> tr("Evening workout", "Avondtraining")
    }

    /** Adds an exercise, pre-filled with what was done last time. */
    fun add(exercise: Exercise, workoutId: String) {
        repo.commit { data ->
            val order = (data.sortedEntries(workoutId).maxOfOrNull { it.order } ?: -1) + 1
            val entry = WorkoutEntry(order = order, exerciseId = exercise.id, workoutId = workoutId)
            entries += entry
            val previous = WorkoutStats.previousSets(data, exercise, workoutId)?.sets.orEmpty()
            if (previous.isEmpty()) {
                sets += SetRecord(order = 0, reps = if (exercise.kind.isCardio) 0 else 10, weight = 0.0,
                    durationSeconds = if (exercise.kind.isCardio) 15 * 60 else 0, entryId = entry.id)
            } else {
                previous.forEachIndexed { n, old ->
                    sets += SetRecord(order = n, reps = old.reps, weight = old.weight, kindRaw = old.kindRaw,
                        durationSeconds = old.durationSeconds, distanceMeters = old.distanceMeters, entryId = entry.id)
                }
            }
        }
    }

    fun addSet(entry: WorkoutEntry) {
        repo.commit { data ->
            val last = data.sortedSets(entry).lastOrNull()
            sets += SetRecord(order = (last?.order ?: -1) + 1, reps = last?.reps ?: 10, weight = last?.weight ?: 0.0,
                durationSeconds = last?.durationSeconds ?: 0, distanceMeters = last?.distanceMeters ?: 0.0, entryId = entry.id)
        }
    }

    fun updateSet(set: SetRecord) { repo.commit { sets += set } }

    fun deleteSet(set: SetRecord) { repo.commit { deleteSets += set.id } }

    fun deleteEntry(entry: WorkoutEntry) { repo.commit { data -> removeEntry(data, entry.id) } }

    fun toggle(set: SetRecord, isCardio: Boolean, exerciseName: String) {
        val done = !set.isDone
        repo.commit { sets += set.copy(isDone = done, completedAt = if (done) System.currentTimeMillis() else null) }
        if (done && Settings.restAuto && !isCardio) startRest(Settings.restDefault, exerciseName)
    }

    // MARK: Rest timer

    private var restLabel = ""

    fun startRest(seconds: Int, exerciseName: String = restLabel) {
        restLabel = exerciseName
        restTotal = seconds.toLong()
        setRestEnd(System.currentTimeMillis() + seconds * 1000L)
    }

    fun addRest(seconds: Int) {
        val end = restEnd ?: return
        restTotal = maxOf(1L, restTotal + seconds)
        setRestEnd(maxOf(System.currentTimeMillis(), end) + seconds * 1000L)
    }

    private fun setRestEnd(end: Long) {
        restEnd = end
        Prefs.prefs.edit().putLong(Prefs.REST_END, end).putLong(Prefs.REST_TOTAL, restTotal).apply()
        RestNotifier.show(context, end, restLabel.ifEmpty { tr("Workout", "Training") })
    }

    fun stopRest() {
        restEnd = null
        Prefs.prefs.edit().remove(Prefs.REST_END).apply()
        RestNotifier.cancel(context)
    }

    /** The alarm fired: the bar shows 0:00 briefly, then closes. */
    fun onRestAlarm() {
        val end = restEnd ?: return
        if (end <= System.currentTimeMillis() + 500) {
            restEnd = null
            Prefs.prefs.edit().remove(Prefs.REST_END).apply()
        }
    }

    // MARK: Finish

    /** Finishes the workout: drops empty sets/exercises, saves to Health Connect. Returns the workout id. */
    suspend fun finish(): String? {
        val id = activeId ?: return null
        stopRest()
        val end = System.currentTimeMillis()
        repo.commitAndWait { data ->
            val workout = data.workouts[id] ?: return@commitAndWait
            workouts += workout.copy(endDate = end)
            for (entry in data.sortedEntries(workout)) {
                val all = data.sortedSets(entry)
                val dropped = all.filter { !it.isDone && it.reps == 0 && it.durationSeconds == 0 }
                dropped.forEach { deleteSets += it.id }
                if (dropped.size == all.size) deleteEntries += entry.id
            }
        }
        activeId = null
        Prefs.prefs.edit().remove(Prefs.ACTIVE_WORKOUT).apply()

        val data = repo.current
        val workout = data.workouts[id] ?: return id
        val health = HealthConnectManager
        val kcal = WorkoutStats.estimatedCalories(data, workout, health.latestWeightKg)
        var updated = workout.copy(activeCalories = if (workout.activeCalories == 0.0) kcal else workout.activeCalories)
        if (data.sortedEntries(workout).isNotEmpty()) {
            health.saveWorkout(context, data, updated)?.let { updated = updated.copy(healthRecordId = it) }
        }
        repo.commitAndWait { workouts += updated }
        LiftrApp.afterWorkoutChange()
        return id
    }

    fun discard() {
        val id = activeId ?: return
        stopRest()
        repo.commit { data -> removeWorkout(data, id) }
        activeId = null
        Prefs.prefs.edit().remove(Prefs.ACTIVE_WORKOUT).apply()
        LiftrApp.afterWorkoutChange()
    }
}
