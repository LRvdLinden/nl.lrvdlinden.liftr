package nl.lrvdlinden.liftr.core

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Dao
interface LiftrDao {
    @Query("SELECT * FROM exercise") suspend fun exercises(): List<Exercise>
    @Query("SELECT * FROM workout") suspend fun workouts(): List<Workout>
    @Query("SELECT * FROM workout_entry") suspend fun entries(): List<WorkoutEntry>
    @Query("SELECT * FROM set_record") suspend fun sets(): List<SetRecord>
    @Query("SELECT * FROM routine") suspend fun routines(): List<Routine>
    @Query("SELECT * FROM routine_item") suspend fun routineItems(): List<RoutineItem>
    @Query("SELECT * FROM body_measurement") suspend fun body(): List<BodyMeasurement>
    @Query("SELECT * FROM gym_pass") suspend fun passes(): List<GymPass>

    @Upsert suspend fun upsertExercises(items: List<Exercise>)
    @Upsert suspend fun upsertWorkouts(items: List<Workout>)
    @Upsert suspend fun upsertEntries(items: List<WorkoutEntry>)
    @Upsert suspend fun upsertSets(items: List<SetRecord>)
    @Upsert suspend fun upsertRoutines(items: List<Routine>)
    @Upsert suspend fun upsertRoutineItems(items: List<RoutineItem>)
    @Upsert suspend fun upsertBody(items: List<BodyMeasurement>)
    @Upsert suspend fun upsertPasses(items: List<GymPass>)

    @Query("DELETE FROM exercise WHERE id IN (:ids)") suspend fun deleteExercises(ids: List<String>)
    @Query("DELETE FROM workout WHERE id IN (:ids)") suspend fun deleteWorkouts(ids: List<String>)
    @Query("DELETE FROM workout_entry WHERE id IN (:ids)") suspend fun deleteEntries(ids: List<String>)
    @Query("DELETE FROM set_record WHERE id IN (:ids)") suspend fun deleteSets(ids: List<String>)
    @Query("DELETE FROM routine WHERE id IN (:ids)") suspend fun deleteRoutines(ids: List<String>)
    @Query("DELETE FROM routine_item WHERE id IN (:ids)") suspend fun deleteRoutineItems(ids: List<String>)
    @Query("DELETE FROM body_measurement WHERE id IN (:ids)") suspend fun deleteBody(ids: List<String>)
    @Query("DELETE FROM gym_pass WHERE id IN (:ids)") suspend fun deletePasses(ids: List<String>)
}

@Database(
    entities = [Exercise::class, Workout::class, WorkoutEntry::class, SetRecord::class, Routine::class,
        RoutineItem::class, BodyMeasurement::class, GymPass::class],
    version = 1,
    exportSchema = false,
)
abstract class LiftrDatabase : RoomDatabase() {
    abstract fun dao(): LiftrDao

    companion object {
        @Volatile private var instance: LiftrDatabase? = null

        fun get(context: Context): LiftrDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LiftrDatabase::class.java, "liftr.db")
                .build().also { instance = it }
        }
    }
}
