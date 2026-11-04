package nl.lrvdlinden.liftr.core

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Everything Liftr stores, kept in memory for instant reads (like SwiftData's object graph on iOS)
 * and written to the Room database in the background, in order. Data stays on the device.
 */
class LiftrRepository private constructor(context: Context) {
    private val db = LiftrDatabase.get(context)
    private val dao = db.dao()
    private val _data = MutableStateFlow(LiftrData())
    val data: StateFlow<LiftrData> = _data.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val loadMutex = Mutex()
    private val lock = Any()
    private val queue = Channel<Pair<Change, CompletableDeferred<Unit>>>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called after every change (widgets, wearable sync …). */
    var onChanged: ((LiftrData) -> Unit)? = null

    init {
        scope.launch {
            for ((change, done) in queue) {
                try {
                    write(change)
                } catch (e: Exception) {
                    android.util.Log.e("Liftr", "Saving failed", e)
                }
                done.complete(Unit)
            }
        }
    }

    val current: LiftrData get() = _data.value

    /** Reads the database once and adds the built-in exercises that are missing. */
    suspend fun ensureLoaded() {
        if (_loaded.value) return
        loadMutex.withLock {
            if (_loaded.value) return
            val loaded = LiftrData(
                exercises = dao.exercises().associateBy { it.id },
                workouts = dao.workouts().associateBy { it.id },
                entries = dao.entries().associateBy { it.id },
                sets = dao.sets().associateBy { it.id },
                routines = dao.routines().associateBy { it.id },
                routineItems = dao.routineItems().associateBy { it.id },
                body = dao.body().associateBy { it.id },
                passes = dao.passes().associateBy { it.id },
            )
            synchronized(lock) { _data.value = loaded }
            _loaded.value = true
            prepareLibrary()
        }
    }

    /** Adds the built-in exercises that are missing (new app versions bring new exercises this way). */
    fun prepareLibrary() {
        commit { data ->
            val known = data.exercises.values.mapNotNull { it.seedId }.toSet()
            for (seed in ExerciseLibrary.seeds) {
                if (seed.id in known) continue
                exercises += Exercise(
                    name = L10n.exerciseName(seed.id, seed.en, seed.nl), seedId = seed.id,
                    muscleRaw = seed.muscle.raw, kindRaw = seed.kind.raw, createdAt = 0L,
                )
            }
        }
    }

    /** Applies a change to memory right away and queues it for the database. */
    fun commit(block: Change.(LiftrData) -> Unit): CompletableDeferred<Unit> {
        val done = CompletableDeferred<Unit>()
        val next: LiftrData
        synchronized(lock) {
            val current = _data.value
            val change = Change()
            change.block(current)
            if (change.isEmpty) {
                done.complete(Unit)
                return done
            }
            next = current.applying(change)
            _data.value = next
            queue.trySend(change to done)
        }
        onChanged?.invoke(next)
        return done
    }

    suspend fun commitAndWait(block: Change.(LiftrData) -> Unit) {
        commit(block).await()
    }

    private suspend fun write(c: Change) {
        db.withTransaction {
            if (c.exercises.isNotEmpty()) dao.upsertExercises(c.exercises)
            if (c.workouts.isNotEmpty()) dao.upsertWorkouts(c.workouts)
            if (c.entries.isNotEmpty()) dao.upsertEntries(c.entries)
            if (c.sets.isNotEmpty()) dao.upsertSets(c.sets)
            if (c.routines.isNotEmpty()) dao.upsertRoutines(c.routines)
            if (c.routineItems.isNotEmpty()) dao.upsertRoutineItems(c.routineItems)
            if (c.body.isNotEmpty()) dao.upsertBody(c.body)
            if (c.passes.isNotEmpty()) dao.upsertPasses(c.passes)
            c.deleteSets.chunked(500).forEach { dao.deleteSets(it) }
            c.deleteEntries.chunked(500).forEach { dao.deleteEntries(it) }
            c.deleteWorkouts.chunked(500).forEach { dao.deleteWorkouts(it) }
            c.deleteRoutineItems.chunked(500).forEach { dao.deleteRoutineItems(it) }
            c.deleteRoutines.chunked(500).forEach { dao.deleteRoutines(it) }
            c.deleteExercises.chunked(500).forEach { dao.deleteExercises(it) }
            c.deleteBody.chunked(500).forEach { dao.deleteBody(it) }
            c.deletePasses.chunked(500).forEach { dao.deletePasses(it) }
        }
    }

    companion object {
        @Volatile private var instance: LiftrRepository? = null

        fun get(context: Context): LiftrRepository = instance ?: synchronized(this) {
            instance ?: LiftrRepository(context.applicationContext).also { instance = it }
        }
    }
}
