import CoreTransferable
import Foundation
import UniformTypeIdentifiers

/// All sets as a CSV file (date, workout, exercise, set, reps, weight…), for the share sheet.
struct CSVExport: Transferable {
    /// Built up front on the main actor; the share sheet only gets the finished text.
    let text: String

    @MainActor
    init(workouts: [Workout]) {
        text = Self.csv(workouts)
    }

    static var transferRepresentation: some TransferRepresentation {
        DataRepresentation(exportedContentType: .commaSeparatedText) { export in
            Data(export.text.utf8)
        }
        .suggestedFileName("Liftr-export.csv")
    }

    @MainActor
    private static func csv(_ workouts: [Workout]) -> String {
        var rows = ["date;workout;exercise;muscle;set;type;reps;weight_kg;duration_min;distance_km;done"]
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate, .withTime, .withColonSeparatorInTime]
        func clean(_ s: String) -> String { s.replacingOccurrences(of: ";", with: ",").replacingOccurrences(of: "\n", with: " ") }
        for workout in workouts.filter({ $0.endDate != nil }).sorted(by: { $0.startDate < $1.startDate }) {
            for entry in workout.sortedEntries {
                for set in entry.sortedSets {
                    rows.append([
                        formatter.string(from: workout.startDate),
                        clean(workout.title),
                        clean(entry.exercise?.name ?? ""),
                        entry.exercise?.muscle.rawValue ?? "",
                        String(set.order + 1),
                        set.kind.rawValue,
                        String(set.reps),
                        String(format: "%.2f", set.weight),
                        String(format: "%.1f", Double(set.durationSeconds) / 60),
                        String(format: "%.2f", set.distanceMeters / 1000),
                        set.isDone ? "1" : "0",
                    ].joined(separator: ";"))
                }
            }
        }
        return rows.joined(separator: "\n")
    }
}
