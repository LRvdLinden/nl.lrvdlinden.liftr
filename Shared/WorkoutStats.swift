import Foundation

/// Calculations on workouts: volume, estimated max, records, streaks and badges.
enum WorkoutStats {
    /// Estimated one-rep max (Epley).
    static func oneRepMax(weight: Double, reps: Int) -> Double {
        guard weight > 0, reps > 0 else { return 0 }
        if reps == 1 { return weight }
        return weight * (1 + Double(reps) / 30)
    }

    static func countedSets(_ entry: WorkoutEntry) -> [SetRecord] {
        entry.sortedSets.filter { $0.isDone || $0.reps > 0 || $0.durationSeconds > 0 }
    }

    static func volume(_ workout: Workout) -> Double {
        workout.sortedEntries.reduce(0) { sum, entry in
            sum + countedSets(entry).filter { $0.kind != .warmup }.reduce(0) { $0 + $1.volume }
        }
    }

    static func setCount(_ workout: Workout) -> Int {
        workout.sortedEntries.reduce(0) { $0 + countedSets($1).count }
    }

    static func repCount(_ workout: Workout) -> Int {
        workout.sortedEntries.reduce(0) { sum, entry in sum + countedSets(entry).reduce(0) { $0 + $1.reps } }
    }

    /// The sets done for this exercise in the most recent finished workout (excluding `excluding`).
    static func previousSets(for exercise: Exercise, excluding workout: Workout?) -> (date: Date, sets: [SetRecord])? {
        let entries = (exercise.entries ?? []).filter { entry in
            guard let w = entry.workout, w.endDate != nil else { return false }
            if let workout, w.id == workout.id { return false }
            return !countedSets(entry).isEmpty
        }
        guard let last = entries.max(by: { ($0.workout?.startDate ?? .distantPast) < ($1.workout?.startDate ?? .distantPast) }),
              let date = last.workout?.startDate else { return nil }
        return (date, countedSets(last))
    }

    /// "3 × 12 · 40 kg" style summary of a list of sets.
    static func summary(_ sets: [SetRecord], unit: WeightUnit, cardio: Bool = false) -> String {
        guard !sets.isEmpty else { return "–" }
        if cardio {
            let minutes = sets.reduce(0) { $0 + $1.durationSeconds } / 60
            let km = sets.reduce(0) { $0 + $1.distanceMeters } / 1000
            var parts = ["\(minutes) min"]
            if km > 0 { parts.append("\(LiftrFormat.decimal(km, maxFraction: 2)) km") }
            return parts.joined(separator: " · ")
        }
        let reps = Set(sets.map(\.reps))
        let weights = Set(sets.map(\.weight))
        if reps.count == 1, weights.count == 1, let r = reps.first, let w = weights.first {
            let weight = w > 0 ? " · \(unit.format(w))" : ""
            return "\(sets.count) × \(r)\(weight)"
        }
        return sets.map { set in
            set.weight > 0 ? "\(unit.number(set.weight))×\(set.reps)" : "\(set.reps)"
        }.joined(separator: ", ") + (weights.contains { $0 > 0 } ? " \(unit.symbol)" : "")
    }

    struct ExercisePoint: Identifiable {
        let id = UUID()
        let date: Date
        let topWeight: Double
        let oneRepMax: Double
        let volume: Double
        let bestReps: Int
    }

    /// One point per finished workout in which the exercise was done.
    static func history(for exercise: Exercise) -> [ExercisePoint] {
        (exercise.entries ?? []).compactMap { entry -> ExercisePoint? in
            guard let workout = entry.workout, workout.endDate != nil else { return nil }
            let sets = countedSets(entry).filter { $0.kind != .warmup }
            guard !sets.isEmpty else { return nil }
            return ExercisePoint(date: workout.startDate,
                                 topWeight: sets.map(\.weight).max() ?? 0,
                                 oneRepMax: sets.map { oneRepMax(weight: $0.weight, reps: $0.reps) }.max() ?? 0,
                                 volume: sets.reduce(0) { $0 + $1.volume },
                                 bestReps: sets.map(\.reps).max() ?? 0)
        }
        .sorted { $0.date < $1.date }
    }

    struct Record {
        let heaviest: Double
        let bestOneRepMax: Double
        let mostReps: Int
        let bestVolume: Double
    }

    static func records(for exercise: Exercise) -> Record? {
        let points = history(for: exercise)
        guard !points.isEmpty else { return nil }
        return Record(heaviest: points.map(\.topWeight).max() ?? 0,
                      bestOneRepMax: points.map(\.oneRepMax).max() ?? 0,
                      mostReps: points.map(\.bestReps).max() ?? 0,
                      bestVolume: points.map(\.volume).max() ?? 0)
    }

    /// Exercises in this workout that beat their previous best weight or estimated max.
    static func personalRecords(in workout: Workout) -> [String] {
        workout.sortedEntries.compactMap { entry -> String? in
            guard let exercise = entry.exercise, !exercise.kind.isCardio else { return nil }
            let sets = countedSets(entry).filter { $0.kind != .warmup }
            let best = sets.map { oneRepMax(weight: $0.weight, reps: $0.reps) }.max() ?? 0
            guard best > 0 else { return nil }
            let earlier = (exercise.entries ?? []).filter {
                guard let w = $0.workout, w.endDate != nil, w.id != workout.id else { return false }
                return w.startDate < workout.startDate
            }
            guard !earlier.isEmpty else { return nil }
            let previousBest = earlier.flatMap { countedSets($0) }.map { oneRepMax(weight: $0.weight, reps: $0.reps) }.max() ?? 0
            return best > previousBest ? exercise.name : nil
        }
    }

    // MARK: Calendar

    static var calendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.firstWeekday = 2 // Monday
        cal.minimumDaysInFirstWeek = 4
        cal.locale = L10n.locale
        return cal
    }

    static func finished(_ workouts: [Workout]) -> [Workout] {
        workouts.filter { $0.endDate != nil }
    }

    /// Consecutive weeks (Monday–Sunday) with at least one workout. This week counts once trained;
    /// an empty current week does not break the streak yet.
    static func weekStreak(_ workouts: [Workout], now: Date = Date()) -> Int {
        let cal = calendar
        let weeks = Set(finished(workouts).compactMap { cal.dateInterval(of: .weekOfYear, for: $0.startDate)?.start })
        guard var week = cal.dateInterval(of: .weekOfYear, for: now)?.start else { return 0 }
        var streak = 0
        if !weeks.contains(week) {
            guard let previous = cal.date(byAdding: .weekOfYear, value: -1, to: week) else { return 0 }
            week = previous
        }
        while weeks.contains(week) {
            streak += 1
            guard let previous = cal.date(byAdding: .weekOfYear, value: -1, to: week) else { break }
            week = previous
        }
        return streak
    }

    /// Which days of the current week (Mon…Sun) had a workout.
    static func trainedDaysThisWeek(_ workouts: [Workout], now: Date = Date()) -> [Bool] {
        let cal = calendar
        guard let start = cal.dateInterval(of: .weekOfYear, for: now)?.start else { return Array(repeating: false, count: 7) }
        return (0..<7).map { offset in
            guard let day = cal.date(byAdding: .day, value: offset, to: start) else { return false }
            return finished(workouts).contains { cal.isDate($0.startDate, inSameDayAs: day) }
        }
    }

    static func workoutsThisWeek(_ workouts: [Workout], now: Date = Date()) -> [Workout] {
        let cal = calendar
        guard let interval = cal.dateInterval(of: .weekOfYear, for: now) else { return [] }
        return finished(workouts).filter { interval.contains($0.startDate) }
    }

    static func workoutsThisMonth(_ workouts: [Workout], now: Date = Date()) -> [Workout] {
        let cal = calendar
        guard let interval = cal.dateInterval(of: .month, for: now) else { return [] }
        return finished(workouts).filter { interval.contains($0.startDate) }
    }

    struct WeekCount: Identifiable {
        let id = UUID()
        let weekStart: Date
        let count: Int
        let volume: Double
    }

    static func weeklyCounts(_ workouts: [Workout], weeks: Int = 12, now: Date = Date()) -> [WeekCount] {
        let cal = calendar
        guard let thisWeek = cal.dateInterval(of: .weekOfYear, for: now)?.start else { return [] }
        let done = finished(workouts)
        return (0..<weeks).reversed().compactMap { back in
            guard let start = cal.date(byAdding: .weekOfYear, value: -back, to: thisWeek),
                  let end = cal.date(byAdding: .weekOfYear, value: 1, to: start) else { return nil }
            let inWeek = done.filter { $0.startDate >= start && $0.startDate < end }
            return WeekCount(weekStart: start, count: inWeek.count, volume: inWeek.reduce(0) { $0 + volume($1) })
        }
    }

    // MARK: Badges

    struct Badge: Identifiable {
        let id: String
        let title: String
        let detail: String
        let symbol: String
        let achieved: Bool
    }

    static func badges(_ workouts: [Workout]) -> [Badge] {
        let done = finished(workouts)
        let count = done.count
        let totalVolume = done.reduce(0) { $0 + volume($1) }
        let streak = weekStreak(workouts)
        var list: [Badge] = []
        for n in [1, 10, 25, 50, 100, 250] {
            list.append(Badge(id: "workouts-\(n)",
                              title: n == 1 ? tr("First workout", "Eerste training") : trf("{1} workouts", "{1} trainingen", n),
                              detail: n == 1 ? tr("Finish your first workout", "Rond je eerste training af")
                                             : trf("Finish {1} workouts", "Rond {1} trainingen af", n),
                              symbol: n >= 100 ? "trophy.fill" : "medal.fill",
                              achieved: count >= n))
        }
        for w in [4, 12, 26] {
            list.append(Badge(id: "streak-\(w)",
                              title: trf("{1} weeks in a row", "{1} weken op rij", w),
                              detail: trf("Train every week for {1} weeks", "Train {1} weken lang elke week", w),
                              symbol: "flame.fill",
                              achieved: streak >= w))
        }
        for t in [1, 10, 100] {
            list.append(Badge(id: "volume-\(t)",
                              title: trf("{1} tonnes lifted", "{1} ton getild", t),
                              detail: trf("Lift {1} tonnes in total", "Til in totaal {1} ton", t),
                              symbol: "scalemass.fill",
                              achieved: totalVolume >= Double(t) * 1000))
        }
        return list
    }
}
