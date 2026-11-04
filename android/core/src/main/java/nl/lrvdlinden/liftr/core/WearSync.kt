package nl.lrvdlinden.liftr.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * What phone and watch exchange over the Wearable Data Layer, as JSON.
 * Phone → watch: [WatchState] at [STATE_PATH] (exercises, routines, passes, last sets, settings).
 * Watch → phone: a finished [WatchWorkout] at [WORKOUT_PATH]/<id>; the phone deletes the item after importing it.
 */
object WearSync {
    const val STATE_PATH = "/liftr/state"
    const val WORKOUT_PATH = "/liftr/workout"
    const val KEY_JSON = "json"

    data class WExercise(val id: String, val name: String, val muscle: String, val kind: String, val favorite: Boolean)
    data class WItem(val exerciseId: String, val sets: Int, val reps: Int, val weight: Double)
    data class WRoutine(val id: String, val name: String, val items: List<WItem>)
    data class WPass(val id: String, val name: String, val colorHex: String, val code: String, val format: String, val member: String)
    data class WSet(val reps: Int, val weight: Double, val done: Boolean = false, val completedAt: Long? = null,
                    val durationSeconds: Int = 0, val distanceMeters: Double = 0.0)

    data class WatchState(
        val unit: String = "kg",
        val restSeconds: Int = 90,
        val userName: String = "",
        val exercises: List<WExercise> = emptyList(),
        val routines: List<WRoutine> = emptyList(),
        val passes: List<WPass> = emptyList(),
        /** Last sets per exercise id, to fill in new exercises. */
        val last: Map<String, List<WSet>> = emptyMap(),
        val weekCount: Int = 0,
        val streak: Int = 0,
    )

    data class WEntry(val exerciseId: String, val exerciseName: String, val cardio: Boolean, val sets: List<WSet>)
    data class WatchWorkout(
        val id: String, val title: String, val start: Long, val end: Long?,
        val averageHeartRate: Double, val calories: Double, val entries: List<WEntry>,
    )

    private fun setJson(s: WSet) = JSONObject().put("reps", s.reps).put("weight", s.weight).put("done", s.done)
        .put("completedAt", s.completedAt ?: 0L).put("duration", s.durationSeconds).put("distance", s.distanceMeters)

    private fun setFrom(o: JSONObject) = WSet(o.optInt("reps"), o.optDouble("weight", 0.0), o.optBoolean("done"),
        o.optLong("completedAt").takeIf { it > 0 }, o.optInt("duration"), o.optDouble("distance", 0.0))

    private fun <T> JSONArray?.list(map: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it)?.let(map) }
    }

    fun encode(s: WatchState): String {
        val o = JSONObject()
        o.put("unit", s.unit).put("rest", s.restSeconds).put("name", s.userName).put("week", s.weekCount).put("streak", s.streak)
        o.put("exercises", JSONArray(s.exercises.map {
            JSONObject().put("id", it.id).put("name", it.name).put("muscle", it.muscle).put("kind", it.kind).put("fav", it.favorite)
        }))
        o.put("routines", JSONArray(s.routines.map { r ->
            JSONObject().put("id", r.id).put("name", r.name).put("items", JSONArray(r.items.map {
                JSONObject().put("exerciseId", it.exerciseId).put("sets", it.sets).put("reps", it.reps).put("weight", it.weight)
            }))
        }))
        o.put("passes", JSONArray(s.passes.map {
            JSONObject().put("id", it.id).put("name", it.name).put("color", it.colorHex).put("code", it.code)
                .put("format", it.format).put("member", it.member)
        }))
        val last = JSONObject()
        for ((k, v) in s.last) last.put(k, JSONArray(v.map(::setJson)))
        o.put("last", last)
        return o.toString()
    }

    fun decodeState(text: String): WatchState? = try {
        val o = JSONObject(text)
        val lastObj = o.optJSONObject("last")
        val last = HashMap<String, List<WSet>>()
        if (lastObj != null) {
            val keys = lastObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                last[k] = lastObj.optJSONArray(k).list(::setFrom)
            }
        }
        WatchState(
            unit = o.optString("unit", "kg"), restSeconds = o.optInt("rest", 90), userName = o.optString("name"),
            exercises = o.optJSONArray("exercises").list {
                WExercise(it.optString("id"), it.optString("name"), it.optString("muscle"), it.optString("kind"), it.optBoolean("fav"))
            },
            routines = o.optJSONArray("routines").list { r ->
                WRoutine(r.optString("id"), r.optString("name"), r.optJSONArray("items").list {
                    WItem(it.optString("exerciseId"), it.optInt("sets", 3), it.optInt("reps", 10), it.optDouble("weight", 0.0))
                })
            },
            passes = o.optJSONArray("passes").list {
                WPass(it.optString("id"), it.optString("name"), it.optString("color", "#FF6B00"), it.optString("code"),
                    it.optString("format", "qr"), it.optString("member"))
            },
            last = last, weekCount = o.optInt("week"), streak = o.optInt("streak"),
        )
    } catch (e: Exception) {
        null
    }

    fun encode(w: WatchWorkout): String = JSONObject()
        .put("id", w.id).put("title", w.title).put("start", w.start).put("end", w.end ?: 0L)
        .put("hr", w.averageHeartRate).put("kcal", w.calories)
        .put("entries", JSONArray(w.entries.map { e ->
            JSONObject().put("exerciseId", e.exerciseId).put("name", e.exerciseName).put("cardio", e.cardio)
                .put("sets", JSONArray(e.sets.map(::setJson)))
        })).toString()

    fun decodeWorkout(text: String): WatchWorkout? = try {
        val o = JSONObject(text)
        WatchWorkout(
            id = o.getString("id"), title = o.optString("title"), start = o.optLong("start"),
            end = o.optLong("end").takeIf { it > 0 }, averageHeartRate = o.optDouble("hr", 0.0), calories = o.optDouble("kcal", 0.0),
            entries = o.optJSONArray("entries").list { e ->
                WEntry(e.optString("exerciseId"), e.optString("name"), e.optBoolean("cardio"), e.optJSONArray("sets").list(::setFrom))
            },
        )
    } catch (e: Exception) {
        null
    }

    /** The state the phone shares with the watch. */
    fun stateFrom(data: LiftrData, unit: String, rest: Int, userName: String): WatchState {
        val used = data.exercises.values.filter { !it.isArchived }
        val last = HashMap<String, List<WSet>>()
        for (ex in used) {
            val prev = WorkoutStats.previousSets(data, ex, null) ?: continue
            last[ex.id] = prev.sets.map { WSet(it.reps, it.weight, durationSeconds = it.durationSeconds, distanceMeters = it.distanceMeters) }
        }
        return WatchState(
            unit = unit, restSeconds = rest, userName = userName,
            exercises = used.sortedBy { it.name.lowercase() }.map { WExercise(it.id, it.name, it.muscleRaw, it.kindRaw, it.isFavorite) },
            routines = data.routinesSorted.map { r ->
                WRoutine(r.id, r.name, data.sortedItems(r).mapNotNull { i ->
                    i.exerciseId?.let { WItem(it, i.targetSets, i.targetReps, i.targetWeight) }
                })
            },
            passes = data.passesSorted.map { WPass(it.id, it.displayName, it.colorHex, it.code, it.formatRaw, it.memberName) },
            last = last,
            weekCount = WorkoutStats.workoutsThisWeek(data).size,
            streak = WorkoutStats.weekStreak(data),
        )
    }

    /** Adds a workout from the watch to the phone's data (skipped when it is already there). */
    fun importWorkout(repository: LiftrRepository, w: WatchWorkout) {
        repository.commit { data ->
            if (data.workouts.containsKey(w.id)) return@commit
            workouts += Workout(id = w.id, title = w.title, startDate = w.start, endDate = w.end ?: System.currentTimeMillis(),
                averageHeartRate = w.averageHeartRate, activeCalories = w.calories, source = "watch")
            val known = data.exercises.values.toMutableList()
            w.entries.forEachIndexed { index, e ->
                val exercise = data.exercises[e.exerciseId] ?: NotesImporter.match(e.exerciseName, known) ?: Exercise(
                    name = e.exerciseName.ifBlank { tr("Exercise", "Oefening") },
                    muscleRaw = (if (e.cardio) Muscle.Cardio else NotesImporter.guessMuscle(e.exerciseName)).raw,
                    kindRaw = (if (e.cardio) ExerciseKind.Cardio else ExerciseKind.Machine).raw,
                ).also { exercises += it; known += it }
                val entry = WorkoutEntry(order = index, exerciseId = exercise.id, workoutId = w.id)
                entries += entry
                e.sets.forEachIndexed { n, s ->
                    sets += SetRecord(order = n, reps = s.reps, weight = s.weight, isDone = s.done, completedAt = s.completedAt,
                        durationSeconds = s.durationSeconds, distanceMeters = s.distanceMeters, entryId = entry.id)
                }
            }
        }
    }
}
