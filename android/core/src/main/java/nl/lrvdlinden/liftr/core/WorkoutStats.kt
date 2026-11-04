package nl.lrvdlinden.liftr.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min

/** Calculations on workouts: volume, estimated max, records, streaks and badges (from Shared/WorkoutStats.swift). */
object WorkoutStats {
    /** Estimated one-rep max (Epley). */
    fun oneRepMax(weight: Double, reps: Int): Double {
        if (weight <= 0 || reps <= 0) return 0.0
        if (reps == 1) return weight
        return weight * (1 + reps / 30.0)
    }

    fun countedSets(data: LiftrData, entry: WorkoutEntry): List<SetRecord> =
        data.sortedSets(entry).filter { it.isDone || it.reps > 0 || it.durationSeconds > 0 }

    fun volume(data: LiftrData, workout: Workout): Double =
        data.sortedEntries(workout).sumOf { e -> countedSets(data, e).filter { it.kind != SetKind.Warmup }.sumOf { it.volume } }

    fun setCount(data: LiftrData, workout: Workout): Int = data.sortedEntries(workout).sumOf { countedSets(data, it).size }

    fun repCount(data: LiftrData, workout: Workout): Int =
        data.sortedEntries(workout).sumOf { e -> countedSets(data, e).sumOf { it.reps } }

    data class Previous(val date: Long, val sets: List<SetRecord>)

    /** The sets done for this exercise in the most recent finished workout (excluding [excluding]). */
    fun previousSets(data: LiftrData, exercise: Exercise, excluding: String?): Previous? {
        var best: Pair<Workout, WorkoutEntry>? = null
        for (entry in data.entriesOf(exercise)) {
            val w = data.workoutOf(entry) ?: continue
            if (w.endDate == null || w.id == excluding) continue
            if (countedSets(data, entry).isEmpty()) continue
            if (best == null || w.startDate > best.first.startDate) best = w to entry
        }
        return best?.let { Previous(it.first.startDate, countedSets(data, it.second)) }
    }

    /** "3 × 12 · 40 kg" style summary of a list of sets. */
    fun summary(sets: List<SetRecord>, unit: WeightUnit, cardio: Boolean = false): String {
        if (sets.isEmpty()) return "–"
        if (cardio) {
            val minutes = sets.sumOf { it.durationSeconds } / 60
            val km = sets.sumOf { it.distanceMeters } / 1000
            val parts = mutableListOf("$minutes min")
            if (km > 0) parts += "${LiftrFormat.decimal(km, 2)} km"
            return parts.joinToString(" · ")
        }
        val reps = sets.map { it.reps }.toSet()
        val weights = sets.map { it.weight }.toSet()
        if (reps.size == 1 && weights.size == 1) {
            val w = weights.first()
            val weight = if (w > 0) " · ${unit.format(w)}" else ""
            return "${sets.size} × ${reps.first()}$weight"
        }
        return sets.joinToString(", ") { if (it.weight > 0) "${unit.number(it.weight)}×${it.reps}" else "${it.reps}" } +
            if (weights.any { it > 0 }) " ${unit.symbol}" else ""
    }

    /** Every set: "40×12 · 42,5×12 · 42,5×10 kg" (cardio: minutes and km). */
    fun setsList(sets: List<SetRecord>, unit: WeightUnit, cardio: Boolean = false): String {
        if (sets.isEmpty()) return "–"
        if (cardio) return summary(sets, unit, true)
        return sets.joinToString(" · ") { if (it.weight > 0) "${unit.number(it.weight)}×${it.reps}" else "${it.reps}×" } +
            if (sets.any { it.weight > 0 }) " ${unit.symbol}" else ""
    }

    data class ExercisePoint(val date: Long, val topWeight: Double, val oneRepMax: Double, val volume: Double, val bestReps: Int)

    /** One point per finished workout in which the exercise was done. */
    fun history(data: LiftrData, exercise: Exercise): List<ExercisePoint> =
        data.entriesOf(exercise).mapNotNull { entry ->
            val workout = data.workoutOf(entry) ?: return@mapNotNull null
            if (workout.endDate == null) return@mapNotNull null
            val sets = countedSets(data, entry).filter { it.kind != SetKind.Warmup }
            if (sets.isEmpty()) return@mapNotNull null
            ExercisePoint(
                date = workout.startDate,
                topWeight = sets.maxOf { it.weight },
                oneRepMax = sets.maxOf { oneRepMax(it.weight, it.reps) },
                volume = sets.sumOf { it.volume },
                bestReps = sets.maxOf { it.reps },
            )
        }.sortedBy { it.date }

    data class Record(val heaviest: Double, val bestOneRepMax: Double, val mostReps: Int, val bestVolume: Double)

    fun records(data: LiftrData, exercise: Exercise): Record? {
        val points = history(data, exercise)
        if (points.isEmpty()) return null
        return Record(points.maxOf { it.topWeight }, points.maxOf { it.oneRepMax }, points.maxOf { it.bestReps }, points.maxOf { it.volume })
    }

    /** Exercises in this workout that beat their previous best estimated max. */
    fun personalRecords(data: LiftrData, workout: Workout): List<String> =
        data.sortedEntries(workout).mapNotNull { entry ->
            val exercise = data.exercise(entry.exerciseId) ?: return@mapNotNull null
            if (exercise.kind.isCardio) return@mapNotNull null
            val sets = countedSets(data, entry).filter { it.kind != SetKind.Warmup }
            val best = sets.maxOfOrNull { oneRepMax(it.weight, it.reps) } ?: 0.0
            if (best <= 0) return@mapNotNull null
            val earlier = data.entriesOf(exercise).filter {
                val w = data.workoutOf(it) ?: return@filter false
                w.endDate != null && w.id != workout.id && w.startDate < workout.startDate
            }
            if (earlier.isEmpty()) return@mapNotNull null
            val previousBest = earlier.flatMap { countedSets(data, it) }.maxOfOrNull { oneRepMax(it.weight, it.reps) } ?: 0.0
            if (best > previousBest) exercise.name else null
        }

    // MARK: Calendar (weeks run Monday–Sunday)

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun weekStart(millis: Long): LocalDate = weekStart(LiftrFormat.localDate(millis))
    fun today(): LocalDate = LocalDate.now(LiftrFormat.zone)

    /** Consecutive weeks with at least one workout. This week counts once trained; an empty current week does not break the streak yet. */
    fun weekStreak(data: LiftrData, now: LocalDate = today()): Int {
        val weeks = data.finished.map { weekStart(it.startDate) }.toSet()
        var week = weekStart(now)
        if (week !in weeks) week = week.minusWeeks(1)
        var streak = 0
        while (week in weeks) {
            streak += 1
            week = week.minusWeeks(1)
        }
        return streak
    }

    /** Which days of the current week (Mon…Sun) had a workout. */
    fun trainedDaysThisWeek(data: LiftrData, now: LocalDate = today()): List<Boolean> {
        val start = weekStart(now)
        val days = data.finished.map { LiftrFormat.localDate(it.startDate) }.toSet()
        return (0L until 7L).map { start.plusDays(it) in days }
    }

    fun workoutsThisWeek(data: LiftrData, now: LocalDate = today()): List<Workout> {
        val start = weekStart(now)
        return data.finished.filter { weekStart(it.startDate) == start }
    }

    fun workoutsThisMonth(data: LiftrData, now: LocalDate = today()): List<Workout> =
        data.finished.filter { val d = LiftrFormat.localDate(it.startDate); d.year == now.year && d.month == now.month }

    data class WeekCount(val weekStart: LocalDate, val count: Int, val volume: Double)

    fun weeklyCounts(data: LiftrData, weeks: Int = 12, now: LocalDate = today()): List<WeekCount> {
        val thisWeek = weekStart(now)
        val byWeek = data.finished.groupBy { weekStart(it.startDate) }
        return (weeks - 1 downTo 0).map { back ->
            val start = thisWeek.minusWeeks(back.toLong())
            val list = byWeek[start].orEmpty()
            WeekCount(start, list.size, list.sumOf { volume(data, it) })
        }
    }

    // MARK: Badges

    enum class BadgeCategory {
        Workouts, Streak, Volume, Sets, Records, Strength, Variety, Moments, Cardio;

        val title: String
            get() = when (this) {
                Workouts -> tr("Workouts", "Trainingen")
                Streak -> tr("Streaks", "Reeksen")
                Volume -> tr("Lifted", "Getild")
                Sets -> tr("Sets & reps", "Sets & herhalingen")
                Records -> tr("Records", "Records")
                Strength -> tr("Strength", "Kracht")
                Variety -> tr("Variety", "Afwisseling")
                Moments -> tr("Moments", "Momenten")
                Cardio -> tr("Cardio", "Cardio")
            }
    }

    data class Badge(
        val id: String,
        val category: BadgeCategory,
        val title: String,
        val detail: String,
        /** SF Symbol name from the iOS app; the Android UI maps it to a Material icon. */
        val symbol: String,
        /** When it was earned; null while not earned yet. */
        val date: Long?,
        /** 0…1 towards the goal. */
        val progress: Double,
        /** "37 / 50" style progress, empty for one-off badges. */
        val progressText: String,
    ) {
        val achieved: Boolean get() = date != null
    }

    /** All badges with the date each was earned, worked out from the workout history (imported history counts too). */
    fun badges(data: LiftrData): List<Badge> {
        val done = data.finished.sortedBy { it.startDate }
        val reached = HashMap<String, Long>()
        fun mark(id: String, date: Long) { if (id !in reached) reached[id] = date }

        val workoutSteps = listOf(1, 5, 10, 25, 50, 100, 250, 500)
        val tonneSteps = listOf(1, 5, 10, 25, 50, 100, 250, 500)
        val setSteps = listOf(100, 500, 1000, 2500, 5000)
        val repSteps = listOf(1000, 5000, 10000, 25000, 50000)
        val recordSteps = listOf(1, 10, 25, 50, 100)
        val heavySteps = listOf(50, 100, 150, 200)
        val varietySteps = listOf(5, 15, 30, 50)
        val kmSteps = listOf(10, 50, 100, 250)
        val streakSteps = listOf(2, 4, 8, 12, 26, 52)

        var volumeKg = 0.0
        var sets = 0
        var reps = 0
        var records = 0
        var heaviest = 0.0
        var km = 0.0
        val exercisesDone = HashSet<String>()
        val bestOneRM = HashMap<String, Double>()
        val weekdaysByWeek = HashMap<LocalDate, MutableSet<DayOfWeek>>()

        done.forEachIndexed { index, workout ->
            val date = workout.startDate
            val n = index + 1
            for (step in workoutSteps) if (n >= step) mark("workouts-$step", date)

            var workoutVolume = 0.0
            for (entry in data.sortedEntries(workout)) {
                val counted = countedSets(data, entry)
                val exercise = data.exercise(entry.exerciseId)
                if (exercise == null || counted.isEmpty()) continue
                exercisesDone += exercise.id
                val working = counted.filter { it.kind != SetKind.Warmup }
                sets += counted.size
                reps += counted.sumOf { it.reps }
                workoutVolume += working.sumOf { it.volume }
                km += counted.sumOf { it.distanceMeters } / 1000
                heaviest = max(heaviest, working.maxOfOrNull { it.weight } ?: 0.0)
                if (!exercise.kind.isCardio) {
                    val best = working.maxOfOrNull { oneRepMax(it.weight, it.reps) } ?: 0.0
                    val previous = bestOneRM[exercise.id]
                    if (previous != null) {
                        if (best > previous) { records += 1; bestOneRM[exercise.id] = best }
                    } else if (best > 0) {
                        bestOneRM[exercise.id] = best
                    }
                }
            }
            volumeKg += workoutVolume

            for (step in tonneSteps) if (volumeKg >= step * 1000.0) mark("volume-$step", date)
            for (step in setSteps) if (sets >= step) mark("sets-$step", date)
            for (step in repSteps) if (reps >= step) mark("reps-$step", date)
            for (step in recordSteps) if (records >= step) mark("records-$step", date)
            for (step in heavySteps) if (heaviest >= step) mark("heavy-$step", date)
            for (step in varietySteps) if (exercisesDone.size >= step) mark("variety-$step", date)
            for (step in kmSteps) if (km >= step) mark("km-$step", date)

            // Moments
            val zoned = LiftrFormat.zoned(date)
            val hour = zoned.hour
            if (hour < 7) mark("early", date)
            if (hour >= 21) mark("late", date)
            val end = workout.endDate
            if (end != null && end - date >= 90 * 60_000L && !workout.isImported) mark("marathon", date)
            if (workoutVolume >= 10_000) mark("big-day", date)
            val week = weekStart(zoned.toLocalDate())
            val days = weekdaysByWeek.getOrPut(week) { mutableSetOf() }
            days += zoned.dayOfWeek
            if (DayOfWeek.SATURDAY in days && DayOfWeek.SUNDAY in days) mark("weekend", date)
            if (days.size >= 5) mark("five-a-week", date)
            if (zoned.monthValue == 1 && zoned.dayOfMonth <= 7) mark("new-year", date)
        }

        // Week streaks: walk the weeks in order and note when a run first reaches each length.
        val weekStarts = done.map { weekStart(it.startDate) }.toSortedSet()
        var run = 0
        var previousWeek: LocalDate? = null
        var bestRun = 0
        for (week in weekStarts) {
            run = if (previousWeek != null && previousWeek.plusWeeks(1) == week) run + 1 else 1
            previousWeek = week
            bestRun = max(bestRun, run)
            val firstInWeek = done.firstOrNull { weekStart(it.startDate) == week }?.startDate ?: continue
            for (step in streakSteps) if (run >= step) mark("streak-$step", firstInWeek)
        }

        fun progress(value: Double, goal: Double): Double = if (goal > 0) min(1.0, value / goal) else 0.0
        fun count(value: Int, goal: Int): String = "${LiftrFormat.integer(min(value, goal))} / ${LiftrFormat.integer(goal)}"

        val list = mutableListOf<Badge>()
        fun add(id: String, category: BadgeCategory, title: String, detail: String, symbol: String,
                value: Double = 0.0, goal: Double = 1.0, text: String = "") {
            val date = reached[id]
            list += Badge(id, category, title, detail, symbol, date, if (date != null) 1.0 else progress(value, goal), text)
        }

        for (step in workoutSteps) {
            add("workouts-$step", BadgeCategory.Workouts,
                if (step == 1) tr("First workout", "Eerste training") else trf("{1} workouts", "{1} trainingen", step),
                if (step == 1) tr("Finish your first workout", "Rond je eerste training af") else trf("Finish {1} workouts", "Rond {1} trainingen af", step),
                if (step >= 100) "trophy.fill" else "medal.fill", done.size.toDouble(), step.toDouble(), count(done.size, step))
        }
        for (step in streakSteps) {
            add("streak-$step", BadgeCategory.Streak, trf("{1} weeks in a row", "{1} weken op rij", step),
                trf("Train every week for {1} weeks", "Train {1} weken lang elke week", step),
                "flame.fill", bestRun.toDouble(), step.toDouble(), count(bestRun, step))
        }
        for (step in tonneSteps) {
            add("volume-$step", BadgeCategory.Volume, trf("{1} tonnes lifted", "{1} ton getild", step),
                trf("Lift {1} tonnes in total", "Til in totaal {1} ton", step),
                "scalemass.fill", volumeKg, step * 1000.0,
                "${LiftrFormat.decimal(min(volumeKg / 1000, step.toDouble()))} / $step t")
        }
        for (step in setSteps) {
            add("sets-$step", BadgeCategory.Sets, trf("{1} sets", "{1} sets", LiftrFormat.integer(step)),
                trf("Complete {1} sets", "Voltooi {1} sets", LiftrFormat.integer(step)),
                "square.stack.3d.up.fill", sets.toDouble(), step.toDouble(), count(sets, step))
        }
        for (step in repSteps) {
            add("reps-$step", BadgeCategory.Sets, trf("{1} reps", "{1} herhalingen", LiftrFormat.integer(step)),
                trf("Do {1} reps in total", "Doe in totaal {1} herhalingen", LiftrFormat.integer(step)),
                "repeat", reps.toDouble(), step.toDouble(), count(reps, step))
        }
        for (step in recordSteps) {
            add("records-$step", BadgeCategory.Records,
                if (step == 1) tr("First record", "Eerste record") else trf("{1} records", "{1} records", step),
                if (step == 1) tr("Beat your best on an exercise", "Verbeter je beste prestatie op een oefening")
                else trf("Set {1} personal records", "Zet {1} persoonlijke records neer", step),
                "star.fill", records.toDouble(), step.toDouble(), count(records, step))
        }
        for (step in heavySteps) {
            add("heavy-$step", BadgeCategory.Strength, trf("{1} kg club", "{1} kg-club", step),
                trf("Do a set with {1} kg or more", "Doe een set met {1} kg of meer", step),
                "dumbbell.fill", heaviest, step.toDouble(),
                "${LiftrFormat.decimal(min(heaviest, step.toDouble()))} / $step kg")
        }
        add("big-day", BadgeCategory.Strength, tr("10 tonne day", "Dag van 10 ton"),
            tr("Lift 10 tonnes in one workout", "Til 10 ton in één training"), "bolt.fill")
        for (step in varietySteps) {
            add("variety-$step", BadgeCategory.Variety, trf("{1} exercises", "{1} oefeningen", step),
                trf("Try {1} different exercises", "Probeer {1} verschillende oefeningen", step),
                "square.grid.3x3.fill", exercisesDone.size.toDouble(), step.toDouble(), count(exercisesDone.size, step))
        }
        add("early", BadgeCategory.Moments, tr("Early bird", "Vroege vogel"), tr("Start a workout before 7:00", "Begin een training voor 7:00"), "sunrise.fill")
        add("late", BadgeCategory.Moments, tr("Night owl", "Nachtuil"), tr("Start a workout after 21:00", "Begin een training na 21:00"), "moon.stars.fill")
        add("weekend", BadgeCategory.Moments, tr("Weekend warrior", "Weekendstrijder"), tr("Train on Saturday and Sunday of the same week", "Train op zaterdag én zondag van dezelfde week"), "calendar.badge.checkmark")
        add("five-a-week", BadgeCategory.Moments, tr("Five a week", "Vijf per week"), tr("Train on five days in one week", "Train op vijf dagen in één week"), "5.circle.fill")
        add("marathon", BadgeCategory.Moments, tr("Marathon session", "Marathonsessie"), tr("Train for 90 minutes or longer", "Train 90 minuten of langer"), "timer")
        add("new-year", BadgeCategory.Moments, tr("Fresh start", "Frisse start"), tr("Train in the first week of January", "Train in de eerste week van januari"), "sparkles")
        for (step in kmSteps) {
            add("km-$step", BadgeCategory.Cardio, trf("{1} km", "{1} km", step),
                trf("Cover {1} km on cardio machines", "Leg {1} km af op cardio-apparaten", step),
                "figure.run", km, step.toDouble(), "${LiftrFormat.decimal(min(km, step.toDouble()))} / $step km")
        }
        return list
    }

    /** Rough calorie estimate from duration and body weight (MET ≈ 5 for strength, 7 for cardio). */
    fun estimatedCalories(data: LiftrData, workout: Workout, weightKg: Double?): Double {
        val hours = workout.durationMillis() / 3_600_000.0
        if (hours <= 0) return 0.0
        val entries = data.sortedEntries(workout)
        val cardioShare = entries.count { data.exercise(it.exerciseId)?.kind?.isCardio == true }.toDouble() / max(1, entries.size)
        val met = 5 + 2 * cardioShare
        return Math.round(met * (weightKg ?: 70.0) * min(hours, 4.0)).toDouble()
    }
}
