package nl.lrvdlinden.liftr.core

import java.time.format.DateTimeFormatter
import java.util.Locale

/** All sets as CSV (same columns as CSVExport.swift). */
object CsvExport {
    fun csv(data: LiftrData): String {
        val rows = mutableListOf("date;workout;exercise;muscle;set;type;reps;weight_kg;duration_min;distance_km;done")
        val iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
        fun clean(s: String) = s.replace(";", ",").replace("\n", " ")
        for (workout in data.finished.sortedBy { it.startDate }) {
            for (entry in data.sortedEntries(workout)) {
                val exercise = data.exercise(entry.exerciseId)
                for (set in data.sortedSets(entry)) {
                    rows += listOf(
                        iso.format(LiftrFormat.zoned(workout.startDate)),
                        clean(workout.title),
                        clean(exercise?.name ?: ""),
                        exercise?.muscle?.raw ?: "",
                        (set.order + 1).toString(),
                        set.kind.raw,
                        set.reps.toString(),
                        String.format(Locale.ROOT, "%.2f", set.weight),
                        String.format(Locale.ROOT, "%.1f", set.durationSeconds / 60.0),
                        String.format(Locale.ROOT, "%.2f", set.distanceMeters / 1000),
                        if (set.isDone) "1" else "0",
                    ).joinToString(";")
                }
            }
        }
        return rows.joinToString("\n")
    }
}
