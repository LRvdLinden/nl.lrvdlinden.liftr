package nl.lrvdlinden.liftr.core

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Ported from Shared/Models.swift. Dates are epoch milliseconds, ids are UUID strings. */
enum class Muscle(val raw: String) {
    Chest("chest"), Back("back"), Shoulders("shoulders"), Arms("arms"), Legs("legs"),
    Glutes("glutes"), Core("core"), Cardio("cardio"), FullBody("fullBody");

    val title: String
        get() = when (this) {
            Chest -> tr("Chest", "Borst")
            Back -> tr("Back", "Rug")
            Shoulders -> tr("Shoulders", "Schouders")
            Arms -> tr("Arms", "Armen")
            Legs -> tr("Legs", "Benen")
            Glutes -> tr("Glutes", "Billen")
            Core -> tr("Core", "Core")
            Cardio -> tr("Cardio", "Cardio")
            FullBody -> tr("Full body", "Hele lichaam")
        }

    companion object {
        fun from(raw: String?): Muscle = entries.firstOrNull { it.raw == raw } ?: FullBody
    }
}

enum class ExerciseKind(val raw: String) {
    Machine("machine"), FreeWeight("freeWeight"), Cable("cable"), Bodyweight("bodyweight"), Cardio("cardio");

    val title: String
        get() = when (this) {
            Machine -> tr("Machine", "Apparaat")
            FreeWeight -> tr("Free weight", "Vrij gewicht")
            Cable -> tr("Cable", "Kabel")
            Bodyweight -> tr("Bodyweight", "Lichaamsgewicht")
            Cardio -> tr("Cardio", "Cardio")
        }

    val isCardio: Boolean get() = this == Cardio

    companion object {
        fun from(raw: String?): ExerciseKind = entries.firstOrNull { it.raw == raw } ?: Machine
    }
}

enum class SetKind(val raw: String) {
    Warmup("warmup"), Work("work"), Drop("drop"), Failure("failure");

    val title: String
        get() = when (this) {
            Warmup -> tr("Warm-up", "Warming-up")
            Work -> tr("Working set", "Werkset")
            Drop -> tr("Drop set", "Dropset")
            Failure -> tr("To failure", "Tot falen")
        }

    val badge: String
        get() = when (this) {
            Warmup -> "W"
            Work -> ""
            Drop -> "D"
            Failure -> "F"
        }

    companion object {
        fun from(raw: String?): SetKind = entries.firstOrNull { it.raw == raw } ?: Work
    }
}

fun newId(): String = UUID.randomUUID().toString().uppercase()

@Entity(tableName = "exercise", indices = [Index("seedId")])
data class Exercise(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    /** Set for exercises from the built-in list. */
    val seedId: String? = null,
    val muscleRaw: String = Muscle.Chest.raw,
    val kindRaw: String = ExerciseKind.Machine.raw,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val muscle: Muscle get() = Muscle.from(muscleRaw)
    val kind: ExerciseKind get() = ExerciseKind.from(kindRaw)
}

@Entity(tableName = "workout")
data class Workout(
    @PrimaryKey val id: String = newId(),
    val title: String = "",
    val startDate: Long = System.currentTimeMillis(),
    /** null while the workout is still running. */
    val endDate: Long? = null,
    val notes: String = "",
    /** Id of the matching exercise session in Health Connect. */
    val healthRecordId: String? = null,
    val activeCalories: Double = 0.0,
    val averageHeartRate: Double = 0.0,
    /** "android", "watch", or "import|<ISO date>|<file name>" for imported history. */
    val source: String = "android",
) {
    val isActive: Boolean get() = endDate == null
    val isImported: Boolean get() = source.startsWith("import")

    fun durationMillis(now: Long = System.currentTimeMillis()): Long = (endDate ?: now) - startDate

    /** The import this workout came from: (date, label); null for workouts done in Liftr. */
    val importBatch: Pair<Long?, String>?
        get() {
            if (!isImported) return null
            val parts = source.split("|", limit = 3)
            val date = parts.getOrNull(1)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            return date to (parts.getOrNull(2) ?: "")
        }

    companion object {
        /** Marks the workouts of one import, so that import can be deleted again later. */
        fun importSource(label: String, at: Long = System.currentTimeMillis()): String {
            val clean = label.replace("|", " ")
            val iso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(at - at % 1000))
            return "import|$iso|$clean"
        }
    }
}

@Entity(tableName = "workout_entry", indices = [Index("workoutId"), Index("exerciseId")])
data class WorkoutEntry(
    @PrimaryKey val id: String = newId(),
    val order: Int = 0,
    val notes: String = "",
    val exerciseId: String? = null,
    val workoutId: String? = null,
)

@Entity(tableName = "set_record", indices = [Index("entryId")])
data class SetRecord(
    @PrimaryKey val id: String = newId(),
    val order: Int = 0,
    val reps: Int = 0,
    /** Weight per repetition, always in kilograms. */
    val weight: Double = 0.0,
    val kindRaw: String = SetKind.Work.raw,
    val isDone: Boolean = false,
    val rpe: Int = 0,
    /** Cardio: duration in seconds and distance in metres. */
    val durationSeconds: Int = 0,
    val distanceMeters: Double = 0.0,
    val completedAt: Long? = null,
    val entryId: String? = null,
) {
    val kind: SetKind get() = SetKind.from(kindRaw)
    val volume: Double get() = reps * weight
}

@Entity(tableName = "routine")
data class Routine(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "routine_item", indices = [Index("routineId")])
data class RoutineItem(
    @PrimaryKey val id: String = newId(),
    val order: Int = 0,
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val targetWeight: Double = 0.0,
    val exerciseId: String? = null,
    val routineId: String? = null,
)

/** Weight and body fat entered in Liftr (also written to Health Connect when allowed). */
@Entity(tableName = "body_measurement")
data class BodyMeasurement(
    @PrimaryKey val id: String = newId(),
    val date: Long = System.currentTimeMillis(),
    val weightKg: Double = 0.0,
    val bodyFatPercent: Double = 0.0,
)

/** The gym membership pass: its access code shown as QR or barcode at the entrance. */
@Entity(tableName = "gym_pass")
data class GymPass(
    @PrimaryKey val id: String = newId(),
    val gymName: String = "",
    /** Known chain from GymTemplates (logo, colour); null for another gym. */
    val templateId: String? = null,
    /** Card colour, e.g. "#FB7819". */
    val colorHex: String = "#FF6B00",
    val memberName: String = "",
    val code: String = "",
    val formatRaw: String = BarcodeFormat.QR.raw,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val format: BarcodeFormat get() = BarcodeFormat.from(formatRaw)
    val template: GymTemplate? get() = GymTemplates.template(templateId)

    /** The name on the card: the chain's name, or what the user typed. */
    val displayName: String
        get() {
            val t = template
            if (t != null && gymName.isEmpty()) return t.name
            return gymName.ifEmpty { tr("Gym", "Sportschool") }
        }

    val groupedCode: String get() = groupCode(code)
}

/** "8 7192 4402 1187": numbers in groups of four, as printed on most passes. */
fun groupCode(code: String): String {
    val compact = code.replace(" ", "")
    if (compact.length <= 8 || compact.length > 24 || !compact.all { it.isDigit() }) return code
    return compact.chunked(4).joinToString(" ")
}
