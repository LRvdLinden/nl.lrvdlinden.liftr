package nl.lrvdlinden.liftr.core

/**
 * An immutable snapshot of everything Liftr stores, with the relations (workout → entries → sets,
 * routine → items) resolved through indices. The UI reads this; changes go through [LiftrRepository.commit].
 */
data class LiftrData(
    val exercises: Map<String, Exercise> = emptyMap(),
    val workouts: Map<String, Workout> = emptyMap(),
    val entries: Map<String, WorkoutEntry> = emptyMap(),
    val sets: Map<String, SetRecord> = emptyMap(),
    val routines: Map<String, Routine> = emptyMap(),
    val routineItems: Map<String, RoutineItem> = emptyMap(),
    val body: Map<String, BodyMeasurement> = emptyMap(),
    val passes: Map<String, GymPass> = emptyMap(),
) {
    val entriesByWorkout: Map<String, List<WorkoutEntry>> by lazy {
        entries.values.filter { it.workoutId != null }.groupBy { it.workoutId!! }.mapValues { (_, v) -> v.sortedBy { it.order } }
    }
    val setsByEntry: Map<String, List<SetRecord>> by lazy {
        sets.values.filter { it.entryId != null }.groupBy { it.entryId!! }.mapValues { (_, v) -> v.sortedBy { it.order } }
    }
    val entriesByExercise: Map<String, List<WorkoutEntry>> by lazy {
        entries.values.filter { it.exerciseId != null }.groupBy { it.exerciseId!! }
    }
    val itemsByRoutine: Map<String, List<RoutineItem>> by lazy {
        routineItems.values.filter { it.routineId != null }.groupBy { it.routineId!! }.mapValues { (_, v) -> v.sortedBy { it.order } }
    }

    /** All workouts, newest first. */
    val workoutsNewestFirst: List<Workout> by lazy { workouts.values.sortedByDescending { it.startDate } }

    /** Finished workouts, newest first. */
    val finished: List<Workout> by lazy { workoutsNewestFirst.filter { it.endDate != null } }

    val exercisesByName: List<Exercise> by lazy { exercises.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
    val routinesSorted: List<Routine> by lazy { routines.values.sortedBy { it.createdAt } }
    val passesSorted: List<GymPass> by lazy { passes.values.sortedBy { it.createdAt } }
    val bodySorted: List<BodyMeasurement> by lazy { body.values.sortedBy { it.date } }

    fun exercise(id: String?): Exercise? = id?.let { exercises[it] }
    fun sortedEntries(workout: Workout): List<WorkoutEntry> = entriesByWorkout[workout.id].orEmpty()
    fun sortedEntries(workoutId: String): List<WorkoutEntry> = entriesByWorkout[workoutId].orEmpty()
    fun sortedSets(entry: WorkoutEntry): List<SetRecord> = setsByEntry[entry.id].orEmpty()
    fun sortedItems(routine: Routine): List<RoutineItem> = itemsByRoutine[routine.id].orEmpty()
    fun workoutOf(entry: WorkoutEntry): Workout? = entry.workoutId?.let { workouts[it] }
    fun entriesOf(exercise: Exercise): List<WorkoutEntry> = entriesByExercise[exercise.id].orEmpty()

    /** Applies a change set (upserts first, then deletes). */
    fun applying(c: Change): LiftrData = LiftrData(
        exercises = exercises.merge(c.exercises, c.deleteExercises) { it.id },
        workouts = workouts.merge(c.workouts, c.deleteWorkouts) { it.id },
        entries = entries.merge(c.entries, c.deleteEntries) { it.id },
        sets = sets.merge(c.sets, c.deleteSets) { it.id },
        routines = routines.merge(c.routines, c.deleteRoutines) { it.id },
        routineItems = routineItems.merge(c.routineItems, c.deleteRoutineItems) { it.id },
        body = body.merge(c.body, c.deleteBody) { it.id },
        passes = passes.merge(c.passes, c.deletePasses) { it.id },
    )
}

private fun <T> Map<String, T>.merge(upserts: List<T>, deletes: Collection<String>, key: (T) -> String): Map<String, T> {
    if (upserts.isEmpty() && deletes.isEmpty()) return this
    val m = HashMap(this)
    for (u in upserts) m[key(u)] = u
    for (d in deletes) m.remove(d)
    return m
}

/** A set of changes, written to memory at once and to the database in one transaction. */
class Change {
    val exercises = mutableListOf<Exercise>()
    val workouts = mutableListOf<Workout>()
    val entries = mutableListOf<WorkoutEntry>()
    val sets = mutableListOf<SetRecord>()
    val routines = mutableListOf<Routine>()
    val routineItems = mutableListOf<RoutineItem>()
    val body = mutableListOf<BodyMeasurement>()
    val passes = mutableListOf<GymPass>()
    val deleteExercises = linkedSetOf<String>()
    val deleteWorkouts = linkedSetOf<String>()
    val deleteEntries = linkedSetOf<String>()
    val deleteSets = linkedSetOf<String>()
    val deleteRoutines = linkedSetOf<String>()
    val deleteRoutineItems = linkedSetOf<String>()
    val deleteBody = linkedSetOf<String>()
    val deletePasses = linkedSetOf<String>()

    val isEmpty: Boolean
        get() = exercises.isEmpty() && workouts.isEmpty() && entries.isEmpty() && sets.isEmpty() && routines.isEmpty() &&
            routineItems.isEmpty() && body.isEmpty() && passes.isEmpty() && deleteExercises.isEmpty() &&
            deleteWorkouts.isEmpty() && deleteEntries.isEmpty() && deleteSets.isEmpty() && deleteRoutines.isEmpty() &&
            deleteRoutineItems.isEmpty() && deleteBody.isEmpty() && deletePasses.isEmpty()

    /** Deletes a workout with its entries and sets. */
    fun removeWorkout(data: LiftrData, id: String) {
        deleteWorkouts += id
        for (e in data.entriesByWorkout[id].orEmpty()) removeEntry(data, e.id)
    }

    fun removeEntry(data: LiftrData, id: String) {
        deleteEntries += id
        for (s in data.setsByEntry[id].orEmpty()) deleteSets += s.id
    }

    fun removeRoutine(data: LiftrData, id: String) {
        deleteRoutines += id
        for (i in data.itemsByRoutine[id].orEmpty()) deleteRoutineItems += i.id
    }
}
