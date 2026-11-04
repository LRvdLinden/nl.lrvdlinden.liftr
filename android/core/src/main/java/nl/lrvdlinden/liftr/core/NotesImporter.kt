package nl.lrvdlinden.liftr.core

import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Turns workout notes into Liftr workouts (port of NotesImporter.swift). Understands lines such as:
 *   3 november 2026            ← a date starts a new workout
 *   Leg press 3x12 40 kg
 *   Chest press: 3 x 10 x 35kg
 *   Lat pulldown 40kg 12, 45kg 10, 45kg 8
 *   Loopband 20 min 3,2 km
 */
object NotesImporter {
    data class ParsedSet(val reps: Int, val weightKg: Double, val minutes: Double = 0.0, val km: Double = 0.0)

    data class ParsedLine(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val sets: List<ParsedSet>,
        val original: String,
        val include: Boolean = true,
        /** Note for this exercise in the workout (e.g. "rechte" from "27 (rechte 3×10)"). */
        val note: String = "",
    )

    data class ParsedDay(
        val id: String = UUID.randomUUID().toString(),
        /** Epoch millis. */
        val date: Long,
        val lines: List<ParsedLine>,
        /** Note for the workout (e.g. "Periode 1"). */
        val note: String = "",
    )

    // MARK: Parsing

    fun parse(text: String, defaultDate: Long, unit: WeightUnit): List<ParsedDay> {
        val days = mutableListOf<ParsedDay>()
        var currentDate = defaultDate
        var lines = mutableListOf<ParsedLine>()
        for (raw in text.lines()) {
            val line = raw.trim().trim('-', '•', '*', '·', '–', ' ', '\t')
            if (line.isEmpty()) continue
            val date = parseDate(line)
            if (date != null) {
                if (lines.isNotEmpty()) days += ParsedDay(date = currentDate, lines = lines)
                currentDate = date
                lines = mutableListOf()
                continue
            }
            parseExercise(line, unit)?.let { lines += it }
        }
        if (lines.isNotEmpty()) days += ParsedDay(date = currentDate, lines = lines)
        return days
    }

    private fun toKg(value: Double, suffix: String?, unit: WeightUnit): Double {
        if (suffix != null && (suffix.startsWith("lb") || suffix.startsWith("pound"))) return value / 2.2046226
        if (suffix == null && unit == WeightUnit.LB) return value / 2.2046226
        return value
    }

    fun parseExercise(line: String, unit: WeightUnit): ParsedLine? {
        val lower = line.lowercase()
        val firstDigit = line.indexOfFirst { it.isDigit() }
        if (firstDigit < 0) return null
        var name = line.substring(0, firstDigit).trim(' ', ':', '-', '–', '—', '=', ',', '.', '\t')
        if (name.isEmpty() || name.none { it.isLetter() }) return null
        name = name.substring(0, 1).uppercase() + name.substring(1)
        val rest = lower.substring(firstDigit)

        // Cardio: "20 min", "3,2 km"
        val minutes = firstMatch("""(\d+(?:[.,]\d+)?)\s*(?:min|minuten|minutes|m\b)""", rest)?.let { LiftrFormat.parse(it[0]) }
        val km = firstMatch("""(\d+(?:[.,]\d+)?)\s*km""", rest)?.let { LiftrFormat.parse(it[0]) }
        if (((minutes ?: 0.0) > 0 || (km ?: 0.0) > 0) && !rest.contains("x") && !rest.contains("×")) {
            return ParsedLine(name = name, sets = listOf(ParsedSet(0, 0.0, minutes ?: 0.0, km ?: 0.0)), original = line)
        }

        // "3 x 10 x 35 kg"
        firstMatch("""(\d+)\s*[x×]\s*(\d+)\s*[x×@]\s*(\d+(?:[.,]\d+)?)\s*(kg|kilo|lbs?)?""", rest)?.let { m ->
            val sets = m[0].toIntOrNull()
            val reps = m[1].toIntOrNull()
            val w = LiftrFormat.parse(m[2])
            if (sets != null && reps != null && w != null && sets in 1..20) {
                val kg = toKg(w, m[3].ifEmpty { null }, unit)
                return ParsedLine(name = name, sets = List(sets) { ParsedSet(reps, kg) }, original = line)
            }
        }

        // "3x12 40 kg" or "40 kg 3x12"
        firstMatch("""(\d+)\s*[x×]\s*(\d+)""", rest)?.let { m ->
            val sets = m[0].toIntOrNull()
            val reps = m[1].toIntOrNull()
            if (sets != null && reps != null && sets in 1..20) {
                val weight = firstMatch("""(\d+(?:[.,]\d+)?)\s*(kg|kilo|lbs?)""", rest)
                val kg = weight?.let { w -> LiftrFormat.parse(w[0])?.let { toKg(it, w[1], unit) } } ?: 0.0
                return ParsedLine(name = name, sets = List(sets) { ParsedSet(reps, kg) }, original = line)
            }
        }

        // "12 herhalingen 40 kg" → one set
        firstMatch("""(\d+)\s*(?:herh|reps?|keer)""", rest)?.get(0)?.toIntOrNull()?.let { reps ->
            val weight = firstMatch("""(\d+(?:[.,]\d+)?)\s*(kg|kilo|lbs?)""", rest)
            val kg = weight?.let { w -> LiftrFormat.parse(w[0])?.let { toKg(it, w[1], unit) } } ?: 0.0
            return ParsedLine(name = name, sets = listOf(ParsedSet(reps, kg)), original = line)
        }

        // "40kg 12, 45kg 10" or "40 x 12, 45 x 10" (weight × reps per set)
        val pairs = allMatches("""(\d+(?:[.,]\d+)?)\s*(?:(kg|kilo|lbs?)\s*[x×*]?|[x×*])\s*(\d+)""", rest)
        val perSet = pairs.mapNotNull { m ->
            val w = LiftrFormat.parse(m[0]) ?: return@mapNotNull null
            val r = m[2].toIntOrNull() ?: return@mapNotNull null
            if (r <= 0 || r >= 200) return@mapNotNull null
            ParsedSet(r, toKg(w, m[1].ifEmpty { null }, unit))
        }
        if (perSet.isNotEmpty() && (rest.contains(",") || rest.contains(";") || rest.contains("kg"))) {
            return ParsedLine(name = name, sets = perSet, original = line)
        }
        return null
    }

    val months: Map<String, Int> = mapOf(
        "jan" to 1, "januari" to 1, "january" to 1, "feb" to 2, "februari" to 2, "february" to 2, "mrt" to 3, "maa" to 3, "maart" to 3,
        "mar" to 3, "march" to 3, "apr" to 4, "april" to 4, "mei" to 5, "may" to 5, "jun" to 6, "juni" to 6, "june" to 6,
        "jul" to 7, "juli" to 7, "july" to 7, "aug" to 8, "augustus" to 8, "august" to 8, "sep" to 9, "sept" to 9,
        "september" to 9, "okt" to 10, "oktober" to 10, "oct" to 10, "october" to 10, "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12,
    )

    /** Recognises "3-11-2026", "03/11/26", "3-11", "3 november 2026", "ma 3 nov". */
    fun parseDate(line: String): Long? {
        val lower = line.lowercase()
        val thisYear = LocalDate.now().year

        fun make(d: Int, m: Int, y: Int?): Long? {
            var year = y ?: thisYear
            if (year < 100) year += 2000
            if (d !in 1..31 || m !in 1..12) return null
            val date = runCatching { LocalDateTime.of(year, m, d, 18, 0) }.getOrNull() ?: return null
            var millis = date.atZone(LiftrFormat.zone).toInstant().toEpochMilli()
            // Without a year, a date in the future means last year.
            if (y == null && millis > System.currentTimeMillis() + 86_400_000L) {
                millis = date.minusYears(1).atZone(LiftrFormat.zone).toInstant().toEpochMilli()
            }
            return millis
        }

        firstMatch("""^(?:[a-z]+\.?,?\s+)?(\d{1,2})[-/.](\d{1,2})(?:[-/.](\d{2,4}))?\s*:?$""", lower)?.let { m ->
            val d = m[0].toIntOrNull()
            val mo = m[1].toIntOrNull()
            if (d != null && mo != null) return make(d, mo, m[2].toIntOrNull())
        }
        firstMatch("""^(?:[a-z]+\.?,?\s+)?(\d{1,2})\s+([a-z]+)\.?(?:\s+(\d{4}))?\s*:?$""", lower)?.let { m ->
            val d = m[0].toIntOrNull()
            val mo = months[m[1]]
            if (d != null && mo != null) return make(d, mo, m[2].toIntOrNull())
        }
        firstMatch("""^(\d{4})-(\d{2})-(\d{2})""", lower)?.let { m ->
            val y = m[0].toIntOrNull()
            val mo = m[1].toIntOrNull()
            val d = m[2].toIntOrNull()
            if (y != null && mo != null && d != null) return make(d, mo, y)
        }
        return null
    }

    // MARK: Regex helpers

    fun firstMatch(pattern: String, text: String): List<String>? = allMatches(pattern, text).firstOrNull()

    fun allMatches(pattern: String, text: String): List<List<String>> =
        Regex(pattern, RegexOption.IGNORE_CASE).findAll(text).map { m ->
            (1 until m.groupValues.size).map { i -> m.groups[i]?.value ?: "" }
        }.toList()

    // MARK: Import

    data class ImportResult(val added: Int, val skipped: Int)

    /**
     * Creates the workouts. Exercises are matched by name; unknown names become new exercises. A day that
     * was imported before (same date, same exercises) is skipped. Imported workouts are history only: they
     * are never written to Health Connect (source "import|…").
     */
    fun importDays(days: List<ParsedDay>, repository: LiftrRepository, label: String): ImportResult {
        var added = 0
        var skipped = 0
        val source = Workout.importSource(label)
        repository.commit { data ->
            val exercises = data.exercises.values.toMutableList()
            val existing = data.workouts.values.filter { it.isImported }
            for (day in days) {
                val lines = day.lines.filter { it.include && it.sets.isNotEmpty() }
                if (lines.isEmpty()) continue
                val names = lines.map { normalized(it.name) }.toSet()
                val dayDate = LiftrFormat.localDate(day.date)
                val duplicate = existing.any { w ->
                    LiftrFormat.localDate(w.startDate) == dayDate &&
                        data.sortedEntries(w).mapNotNull { e -> data.exercise(e.exerciseId)?.let { normalized(it.name) } }.toSet() == names
                }
                if (duplicate) {
                    skipped += 1
                    continue
                }
                val workout = Workout(
                    title = tr("Imported workout", "Geïmporteerde training"), startDate = day.date,
                    endDate = day.date + 60 * 60_000L, notes = day.note, source = source,
                )
                workouts += workout
                lines.forEachIndexed { index, line ->
                    val exercise = match(line.name, exercises) ?: run {
                        val isCardio = line.sets.all { it.reps == 0 && (it.minutes > 0 || it.km > 0) }
                        val new = Exercise(
                            name = line.name,
                            muscleRaw = (if (isCardio) Muscle.Cardio else guessMuscle(line.name)).raw,
                            kindRaw = (if (isCardio) ExerciseKind.Cardio else ExerciseKind.Machine).raw,
                        )
                        this.exercises += new
                        exercises += new
                        new
                    }
                    val entry = WorkoutEntry(order = index, notes = line.note, exerciseId = exercise.id, workoutId = workout.id)
                    entries += entry
                    line.sets.forEachIndexed { n, p ->
                        sets += SetRecord(
                            order = n, reps = p.reps, weight = p.weightKg, durationSeconds = (p.minutes * 60).toInt(),
                            distanceMeters = p.km * 1000, isDone = true, completedAt = workout.startDate, entryId = entry.id,
                        )
                    }
                }
                added += 1
            }
        }
        return ImportResult(added, skipped)
    }

    /** Muscle group for a new exercise from words in its name. */
    fun guessMuscle(name: String): Muscle {
        val n = name.lowercase()
        val table = listOf(
            Muscle.Glutes to listOf("abduct", "adduct", "glute", "bil", "hip thrust", "kickback"),
            Muscle.Legs to listOf("leg", "been", "squat", "lunge", "calf", "kuit", "hack"),
            Muscle.Chest to listOf("chest", "borst", "bench", "fly", "pec", "dip"),
            Muscle.Shoulders to listOf("shoulder", "schouder", "delt", "lateral", "raise"),
            Muscle.Back to listOf("lat", "row", "roei", "pulley", "pull", "rug", "back", "deadlift"),
            Muscle.Arms to listOf("curl", "bicep", "tricep", "arm"),
            Muscle.Core to listOf("abdom", "buik", "crunch", "torso", "core", "plank", "oblique"),
        )
        return table.firstOrNull { (_, words) -> words.any { n.contains(it) } }?.first ?: Muscle.FullBody
    }

    fun normalized(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().filter { it.isLetterOrDigit() }

    fun match(name: String, exercises: Collection<Exercise>): Exercise? {
        val key = normalized(name)
        if (key.isEmpty()) return null
        exercises.firstOrNull { normalized(it.name) == key }?.let { return it }
        val seed = ExerciseLibrary.seeds.firstOrNull {
            normalized(it.en) == key || normalized(it.nl) == key || normalized(L10n.exerciseName(it.id, it.en, it.nl)) == key
        }
        if (seed != null) exercises.firstOrNull { it.seedId == seed.id }?.let { return it }
        return exercises.firstOrNull { ex ->
            val n = normalized(ex.name)
            n.length >= 5 && key.length >= 5 && (n.startsWith(key) || key.startsWith(n))
        }
    }
}
