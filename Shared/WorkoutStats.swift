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

    /// Every set: "40×12 · 42,5×12 · 42,5×10 kg" (cardio: minutes and km).
    static func setsList(_ sets: [SetRecord], unit: WeightUnit, cardio: Bool = false) -> String {
        guard !sets.isEmpty else { return "–" }
        if cardio { return summary(sets, unit: unit, cardio: true) }
        let parts = sets.map { $0.weight > 0 ? "\(unit.number($0.weight))×\($0.reps)" : "\($0.reps)×" }
        return parts.joined(separator: " · ") + (sets.contains { $0.weight > 0 } ? " \(unit.symbol)" : "")
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
        enum Category: String, CaseIterable, Identifiable {
            case workouts, streak, volume, sets, records, strength, variety, moments, cardio
            var id: String { rawValue }
            var title: String {
                switch self {
                case .workouts: tr("Workouts", "Trainingen")
                case .streak: tr("Streaks", "Reeksen")
                case .volume: tr("Lifted", "Getild")
                case .sets: tr("Sets & reps", "Sets & herhalingen")
                case .records: tr("Records", "Records")
                case .strength: tr("Strength", "Kracht")
                case .variety: tr("Variety", "Afwisseling")
                case .moments: tr("Moments", "Momenten")
                case .cardio: tr("Cardio", "Cardio")
                }
            }
        }

        let id: String
        let category: Category
        let title: String
        let detail: String
        let symbol: String
        /// When it was earned (the workout that crossed the line); nil while not earned yet.
        let date: Date?
        /// 0…1 towards the goal.
        let progress: Double
        /// "37 / 50" style progress, empty for one-off badges.
        let progressText: String

        var achieved: Bool { date != nil }
    }

    /// All badges with the date each was earned, worked out from the workout history
    /// (imported history counts too, with its original dates).
    static func badges(_ workouts: [Workout], now: Date = Date()) -> [Badge] {
        let done = finished(workouts).sorted { $0.startDate < $1.startDate }
        let cal = calendar

        // Running totals, recording the first date each threshold is crossed.
        var reached: [String: Date] = [:]
        func mark(_ id: String, _ date: Date) { if reached[id] == nil { reached[id] = date } }

        let workoutSteps = [1, 5, 10, 25, 50, 100, 250, 500]
        let tonneSteps = [1, 5, 10, 25, 50, 100, 250, 500]
        let setSteps = [100, 500, 1000, 2500, 5000]
        let repSteps = [1000, 5000, 10000, 25000, 50000]
        let recordSteps = [1, 10, 25, 50, 100]
        let heavySteps = [50, 100, 150, 200]
        let varietySteps = [5, 15, 30, 50]
        let kmSteps = [10, 50, 100, 250]
        let streakSteps = [2, 4, 8, 12, 26, 52]

        var volumeKg = 0.0, sets = 0, reps = 0, records = 0, heaviest = 0.0, km = 0.0
        var exercisesDone = Set<UUID>()
        var bestOneRM: [UUID: Double] = [:]
        var weekdaysByWeek: [Date: Set<Int>] = [:]
        var longest: TimeInterval = 0

        for (index, workout) in done.enumerated() {
            let date = workout.startDate
            let n = index + 1
            for step in workoutSteps where n >= step { mark("workouts-\(step)", date) }

            var workoutVolume = 0.0
            for entry in workout.sortedEntries {
                let counted = countedSets(entry)
                guard let exercise = entry.exercise, !counted.isEmpty else { continue }
                exercisesDone.insert(exercise.id)
                let working = counted.filter { $0.kind != .warmup }
                sets += counted.count
                reps += counted.reduce(0) { $0 + $1.reps }
                workoutVolume += working.reduce(0) { $0 + $1.volume }
                km += counted.reduce(0) { $0 + $1.distanceMeters } / 1000
                heaviest = max(heaviest, working.map(\.weight).max() ?? 0)
                if !exercise.kind.isCardio {
                    let best = working.map { oneRepMax(weight: $0.weight, reps: $0.reps) }.max() ?? 0
                    if let previous = bestOneRM[exercise.id] {
                        if best > previous { records += 1; bestOneRM[exercise.id] = best }
                    } else if best > 0 {
                        bestOneRM[exercise.id] = best
                    }
                }
            }
            volumeKg += workoutVolume

            for step in tonneSteps where volumeKg >= Double(step) * 1000 { mark("volume-\(step)", date) }
            for step in setSteps where sets >= step { mark("sets-\(step)", date) }
            for step in repSteps where reps >= step { mark("reps-\(step)", date) }
            for step in recordSteps where records >= step { mark("records-\(step)", date) }
            for step in heavySteps where heaviest >= Double(step) { mark("heavy-\(step)", date) }
            for step in varietySteps where exercisesDone.count >= step { mark("variety-\(step)", date) }
            for step in kmSteps where km >= Double(step) { mark("km-\(step)", date) }

            // Moments
            let hour = cal.component(.hour, from: date)
            if hour < 7 { mark("early", date) }
            if hour >= 21 { mark("late", date) }
            if let end = workout.endDate {
                longest = max(longest, end.timeIntervalSince(date))
                if end.timeIntervalSince(date) >= 90 * 60, !workout.isImported { mark("marathon", date) }
            }
            if workoutVolume >= 10_000 { mark("big-day", date) }
            if let week = cal.dateInterval(of: .weekOfYear, for: date)?.start {
                let weekday = cal.component(.weekday, from: date)
                weekdaysByWeek[week, default: []].insert(weekday)
                if weekdaysByWeek[week]!.isSuperset(of: [1, 7]) { mark("weekend", date) }
                if weekdaysByWeek[week]!.count >= 5 { mark("five-a-week", date) }
            }
            if cal.component(.month, from: date) == 1, cal.component(.day, from: date) <= 7 { mark("new-year", date) }
        }

        // Week streaks: walk the weeks in order and note when a run first reaches each length.
        let weekStarts = Set(done.compactMap { cal.dateInterval(of: .weekOfYear, for: $0.startDate)?.start }).sorted()
        var run = 0
        var previousWeek: Date?
        var bestRun = 0
        for week in weekStarts {
            if let previousWeek, cal.date(byAdding: .weekOfYear, value: 1, to: previousWeek) == week { run += 1 } else { run = 1 }
            previousWeek = week
            bestRun = max(bestRun, run)
            let firstInWeek = done.first { cal.dateInterval(of: .weekOfYear, for: $0.startDate)?.start == week }?.startDate ?? week
            for step in streakSteps where run >= step { mark("streak-\(step)", firstInWeek) }
        }

        func progress(_ value: Double, _ goal: Double) -> Double { goal > 0 ? min(1, value / goal) : 0 }
        func count(_ value: Int, _ goal: Int) -> String { "\(min(value, goal).formatted()) / \(goal.formatted())" }

        var list: [Badge] = []
        func add(_ id: String, _ category: Badge.Category, _ title: String, _ detail: String, _ symbol: String,
                 _ value: Double = 0, _ goal: Double = 1, _ text: String = "") {
            list.append(Badge(id: id, category: category, title: title, detail: detail, symbol: symbol,
                              date: reached[id], progress: reached[id] != nil ? 1 : progress(value, goal), progressText: text))
        }

        for step in workoutSteps {
            add("workouts-\(step)", .workouts,
                step == 1 ? tr("First workout", "Eerste training") : trf("{1} workouts", "{1} trainingen", step),
                step == 1 ? tr("Finish your first workout", "Rond je eerste training af") : trf("Finish {1} workouts", "Rond {1} trainingen af", step),
                step >= 100 ? "trophy.fill" : "medal.fill", Double(done.count), Double(step), count(done.count, step))
        }
        for step in streakSteps {
            add("streak-\(step)", .streak, trf("{1} weeks in a row", "{1} weken op rij", step),
                trf("Train every week for {1} weeks", "Train {1} weken lang elke week", step),
                "flame.fill", Double(bestRun), Double(step), count(bestRun, step))
        }
        for step in tonneSteps {
            add("volume-\(step)", .volume, trf("{1} tonnes lifted", "{1} ton getild", step),
                trf("Lift {1} tonnes in total", "Til in totaal {1} ton", step),
                "scalemass.fill", volumeKg, Double(step) * 1000,
                "\(LiftrFormat.decimal(min(volumeKg / 1000, Double(step)))) / \(step) t")
        }
        for step in setSteps {
            add("sets-\(step)", .sets, trf("{1} sets", "{1} sets", step.formatted()),
                trf("Complete {1} sets", "Voltooi {1} sets", step.formatted()),
                "square.stack.3d.up.fill", Double(sets), Double(step), count(sets, step))
        }
        for step in repSteps {
            add("reps-\(step)", .sets, trf("{1} reps", "{1} herhalingen", step.formatted()),
                trf("Do {1} reps in total", "Doe in totaal {1} herhalingen", step.formatted()),
                "repeat", Double(reps), Double(step), count(reps, step))
        }
        for step in recordSteps {
            add("records-\(step)", .records,
                step == 1 ? tr("First record", "Eerste record") : trf("{1} records", "{1} records", step),
                step == 1 ? tr("Beat your best on an exercise", "Verbeter je beste prestatie op een oefening")
                          : trf("Set {1} personal records", "Zet {1} persoonlijke records neer", step),
                "star.fill", Double(records), Double(step), count(records, step))
        }
        for step in heavySteps {
            add("heavy-\(step)", .strength, trf("{1} kg club", "{1} kg-club", step),
                trf("Do a set with {1} kg or more", "Doe een set met {1} kg of meer", step),
                "dumbbell.fill", heaviest, Double(step),
                "\(LiftrFormat.decimal(min(heaviest, Double(step)))) / \(step) kg")
        }
        add("big-day", .strength, tr("10 tonne day", "Dag van 10 ton"),
            tr("Lift 10 tonnes in one workout", "Til 10 ton in één training"), "bolt.fill")
        for step in varietySteps {
            add("variety-\(step)", .variety, trf("{1} exercises", "{1} oefeningen", step),
                trf("Try {1} different exercises", "Probeer {1} verschillende oefeningen", step),
                "square.grid.3x3.fill", Double(exercisesDone.count), Double(step), count(exercisesDone.count, step))
        }
        add("early", .moments, tr("Early bird", "Vroege vogel"), tr("Start a workout before 7:00", "Begin een training voor 7:00"), "sunrise.fill")
        add("late", .moments, tr("Night owl", "Nachtuil"), tr("Start a workout after 21:00", "Begin een training na 21:00"), "moon.stars.fill")
        add("weekend", .moments, tr("Weekend warrior", "Weekendstrijder"), tr("Train on Saturday and Sunday of the same week", "Train op zaterdag én zondag van dezelfde week"), "calendar.badge.checkmark")
        add("five-a-week", .moments, tr("Five a week", "Vijf per week"), tr("Train on five days in one week", "Train op vijf dagen in één week"), "5.circle.fill")
        add("marathon", .moments, tr("Marathon session", "Marathonsessie"), tr("Train for 90 minutes or longer", "Train 90 minuten of langer"), "timer")
        add("new-year", .moments, tr("Fresh start", "Frisse start"), tr("Train in the first week of January", "Train in de eerste week van januari"), "sparkles")
        for step in kmSteps {
            add("km-\(step)", .cardio, trf("{1} km", "{1} km", step),
                trf("Cover {1} km on cardio machines", "Leg {1} km af op cardio-apparaten", step),
                "figure.run", km, Double(step), "\(LiftrFormat.decimal(min(km, Double(step)))) / \(step) km")
        }
        return list
    }
}
