package nl.lrvdlinden.liftr.health

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.Prefs
import nl.lrvdlinden.liftr.core.Workout
import nl.lrvdlinden.liftr.core.WorkoutStats
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Everything Liftr reads from and writes to Health Connect (in place of Apple Health). Every call is
 * skipped quietly when Health Connect is not installed or access was not given.
 */
object HealthConnectManager {
    var isAvailable by mutableStateOf(false); private set
    var hasAccess by mutableStateOf(false); private set
    var latestWeightKg by mutableStateOf<Double?>(null); private set
    var latestBodyFat by mutableStateOf<Double?>(null); private set
    var latestLeanMassKg by mutableStateOf<Double?>(null); private set
    var restingHeartRate by mutableStateOf<Double?>(null); private set
    var weightHistory by mutableStateOf<List<Pair<Long, Double>>>(emptyList()); private set
    var bodyFatHistory by mutableStateOf<List<Pair<Long, Double>>>(emptyList()); private set

    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    )

    fun sdkAvailable(context: Context): Boolean = try {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    } catch (e: Exception) {
        false
    }

    private fun client(context: Context): HealthConnectClient? =
        if (sdkAvailable(context)) try { HealthConnectClient.getOrCreate(context) } catch (e: Exception) { null } else null

    private suspend fun granted(context: Context): Set<String> = try {
        client(context)?.permissionController?.getGrantedPermissions() ?: emptySet()
    } catch (e: Exception) {
        emptySet()
    }

    private val writeWorkouts get() = Prefs.prefs.getBoolean(Prefs.HEALTH_SAVE_WORKOUTS, true)
    private val writeBody get() = Prefs.prefs.getBoolean(Prefs.HEALTH_SAVE_BODY, true)

    /** Re-reads availability, access and the latest body values. */
    suspend fun refresh(context: Context) {
        isAvailable = sdkAvailable(context)
        if (!isAvailable) { hasAccess = false; return }
        val g = granted(context)
        hasAccess = g.contains(HealthPermission.getWritePermission(ExerciseSessionRecord::class))
        val c = client(context) ?: return
        val since = Instant.now().minus(730, ChronoUnit.DAYS)
        try {
            if (g.contains(HealthPermission.getReadPermission(WeightRecord::class))) {
                val weights = c.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.after(since))).records
                    .sortedBy { it.time }
                weightHistory = weights.map { it.time.toEpochMilli() to it.weight.inKilograms }
                latestWeightKg = weights.lastOrNull()?.weight?.inKilograms
            }
            if (g.contains(HealthPermission.getReadPermission(BodyFatRecord::class))) {
                val fats = c.readRecords(ReadRecordsRequest(BodyFatRecord::class, TimeRangeFilter.after(since))).records
                    .sortedBy { it.time }
                bodyFatHistory = fats.map { it.time.toEpochMilli() to it.percentage.value }
                latestBodyFat = fats.lastOrNull()?.percentage?.value
            }
            if (g.contains(HealthPermission.getReadPermission(LeanBodyMassRecord::class))) {
                latestLeanMassKg = c.readRecords(ReadRecordsRequest(LeanBodyMassRecord::class, TimeRangeFilter.after(since))).records
                    .maxByOrNull { it.time }?.mass?.inKilograms
            }
            if (g.contains(HealthPermission.getReadPermission(RestingHeartRateRecord::class))) {
                restingHeartRate = c.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class,
                    TimeRangeFilter.after(Instant.now().minus(60, ChronoUnit.DAYS)))).records
                    .maxByOrNull { it.time }?.beatsPerMinute?.toDouble()
            }
        } catch (e: Exception) {
            Log.w("Liftr", "Health Connect read failed", e)
        }
    }

    fun markRequested() { Prefs.prefs.edit().putBoolean(Prefs.HEALTH_REQUESTED, true).apply() }
    val hasRequested: Boolean get() = Prefs.prefs.getBoolean(Prefs.HEALTH_REQUESTED, false)

    // MARK: Body

    suspend fun saveBody(context: Context, weightKg: Double, bodyFatPercent: Double?, date: Long) {
        if (!writeBody) return
        val c = client(context) ?: return
        val time = Instant.ofEpochMilli(date)
        val offset = ZoneId.systemDefault().rules.getOffset(time)
        val records = mutableListOf<Record>()
        if (weightKg > 0) records += WeightRecord(time = time, zoneOffset = offset, weight = Mass.kilograms(weightKg))
        if (bodyFatPercent != null && bodyFatPercent > 0) {
            records += BodyFatRecord(time = time, zoneOffset = offset, percentage = Percentage(bodyFatPercent))
            if (weightKg > 0) records += LeanBodyMassRecord(time = time, zoneOffset = offset, mass = Mass.kilograms(weightKg * (1 - bodyFatPercent / 100)))
        }
        if (records.isEmpty()) return
        try {
            c.insertRecords(records)
        } catch (e: Exception) {
            Log.w("Liftr", "Saving body data failed", e)
        }
        refresh(context)
    }

    // MARK: Workouts

    /** Saves a finished Liftr workout as an exercise session. Returns the session's record id. */
    suspend fun saveWorkout(context: Context, data: LiftrData, workout: Workout): String? {
        // Imported history (notes, spreadsheets) stays in Liftr and is never written to Health Connect.
        if (!writeWorkouts || workout.isImported) return null
        val end = workout.endDate ?: return null
        val c = client(context) ?: return null
        val entries = data.sortedEntries(workout)
        val cardio = entries.filter { data.exercise(it.exerciseId)?.kind?.isCardio == true }
        val allCardio = entries.isNotEmpty() && cardio.size == entries.size
        val type = if (allCardio) cardioType(data.exercise(cardio.first().exerciseId)?.seedId) else ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        val start = Instant.ofEpochMilli(workout.startDate)
        val stop = Instant.ofEpochMilli(maxOf(end, workout.startDate + 1000))
        val zone = ZoneId.systemDefault().rules
        val names = entries.mapNotNull { data.exercise(it.exerciseId)?.name }
        val notes = buildString {
            append("${WorkoutStats.setCount(data, workout)} sets · ${WorkoutStats.repCount(data, workout)} reps · ")
            append("${Math.round(WorkoutStats.volume(data, workout))} kg")
            if (names.isNotEmpty()) append("\n").append(names.joinToString(", "))
        }
        val records = mutableListOf<Record>(
            ExerciseSessionRecord(
                startTime = start, startZoneOffset = zone.getOffset(start), endTime = stop, endZoneOffset = zone.getOffset(stop),
                exerciseType = type, title = "Liftr – ${workout.title}", notes = notes,
                metadata = Metadata(clientRecordId = "liftr-${workout.id}"),
            ),
        )
        if (workout.activeCalories > 0) {
            records += ActiveCaloriesBurnedRecord(startTime = start, startZoneOffset = zone.getOffset(start), endTime = stop,
                endZoneOffset = zone.getOffset(stop), energy = Energy.kilocalories(workout.activeCalories),
                metadata = Metadata(clientRecordId = "liftr-kcal-${workout.id}"))
        }
        val meters = cardio.flatMap { WorkoutStats.countedSets(data, it) }.sumOf { it.distanceMeters }
        if (meters > 0) {
            records += DistanceRecord(startTime = start, startZoneOffset = zone.getOffset(start), endTime = stop,
                endZoneOffset = zone.getOffset(stop), distance = Length.meters(meters),
                metadata = Metadata(clientRecordId = "liftr-distance-${workout.id}"))
        }
        return try {
            c.insertRecords(records).recordIdsList.firstOrNull()
        } catch (e: Exception) {
            Log.w("Liftr", "Saving workout to Health Connect failed", e)
            null
        }
    }

    /** Removes the session Liftr saved (and its calories / distance). */
    suspend fun deleteWorkout(context: Context, workout: Workout) {
        val c = client(context) ?: return
        try {
            val ids = listOfNotNull(workout.healthRecordId)
            c.deleteRecords(ExerciseSessionRecord::class, recordIdsList = ids, clientRecordIdsList = listOf("liftr-${workout.id}"))
            c.deleteRecords(ActiveCaloriesBurnedRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf("liftr-kcal-${workout.id}"))
            c.deleteRecords(DistanceRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf("liftr-distance-${workout.id}"))
        } catch (e: Exception) {
            Log.w("Liftr", "Deleting from Health Connect failed", e)
        }
    }

    private fun cardioType(seedId: String?): Int = when (seedId) {
        "treadmill" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
        "incline-walk" -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        "exercise-bike", "recumbent-bike", "spin-bike", "air-bike" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
        "stepper", "stair-climber" -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE
        "rowing-machine" -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
        "cross-trainer" -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
        else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }
}
