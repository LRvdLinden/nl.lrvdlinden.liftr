import Foundation
import SwiftData

/// Turns workout notes (as kept in the Notes app) into Liftr workouts.
///
/// Understands lines such as:
///   3 november 2026            ← a date starts a new workout
///   Leg press 3x12 40 kg
///   Chest press: 3 x 10 x 35kg
///   Lat pulldown 40kg 12, 45kg 10, 45kg 8
///   Loopband 20 min 3,2 km
enum NotesImporter {
    struct ParsedSet: Identifiable, Hashable {
        let id = UUID()
        var reps: Int
        var weightKg: Double
        var minutes: Double = 0
        var km: Double = 0
    }

    struct ParsedLine: Identifiable, Hashable {
        let id = UUID()
        var name: String
        var sets: [ParsedSet]
        var original: String
        var include = true
    }

    struct ParsedDay: Identifiable, Hashable {
        let id = UUID()
        var date: Date
        var lines: [ParsedLine]
    }

    // MARK: Parsing

    static func parse(_ text: String, defaultDate: Date, unit: WeightUnit) -> [ParsedDay] {
        var days: [ParsedDay] = []
        var current = ParsedDay(date: defaultDate, lines: [])

        for raw in text.components(separatedBy: .newlines) {
            let line = raw.trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "-•*·– \t"))
            guard !line.isEmpty else { continue }

            if let date = parseDate(line) {
                if !current.lines.isEmpty { days.append(current) }
                current = ParsedDay(date: date, lines: [])
                continue
            }
            if let parsed = parseExercise(line, unit: unit) {
                current.lines.append(parsed)
            }
        }
        if !current.lines.isEmpty { days.append(current) }
        return days
    }

    static func parseExercise(_ line: String, unit: WeightUnit) -> ParsedLine? {
        let lower = line.lowercased()
        // Name: everything before the first digit.
        guard let firstDigit = line.firstIndex(where: \.isNumber) else { return nil }
        var name = String(line[..<firstDigit])
            .trimmingCharacters(in: CharacterSet(charactersIn: " :-–—=,.\t"))
        guard !name.isEmpty, name.rangeOfCharacter(from: .letters) != nil else { return nil }
        name = name.prefix(1).uppercased() + name.dropFirst()
        let rest = String(lower[firstDigit...])

        let toKg: (Double, String?) -> Double = { value, suffix in
            if let suffix, suffix.hasPrefix("lb") || suffix.hasPrefix("pound") { return value / 2.204_622_6 }
            if suffix == nil, unit == .lb { return value / 2.204_622_6 }
            return value
        }

        // Cardio: "20 min", "3,2 km"
        let minutes = firstMatch(#"(\d+(?:[.,]\d+)?)\s*(?:min|minuten|minutes|m\b)"#, in: rest).flatMap { LiftrFormat.parse($0[0]) }
        let km = firstMatch(#"(\d+(?:[.,]\d+)?)\s*km"#, in: rest).flatMap { LiftrFormat.parse($0[0]) }
        if (minutes ?? 0) > 0 || (km ?? 0) > 0, !rest.contains("x"), !rest.contains("×") {
            return ParsedLine(name: name, sets: [ParsedSet(reps: 0, weightKg: 0, minutes: minutes ?? 0, km: km ?? 0)], original: line)
        }

        // "3 x 10 x 35 kg"
        if let m = firstMatch(#"(\d+)\s*[x×]\s*(\d+)\s*[x×@]\s*(\d+(?:[.,]\d+)?)\s*(kg|kilo|lbs?)?"#, in: rest),
           let sets = Int(m[0]), let reps = Int(m[1]), let w = LiftrFormat.parse(m[2]), sets > 0, sets <= 20 {
            let kg = toKg(w, m[3].isEmpty ? nil : m[3])
            return ParsedLine(name: name, sets: Array(repeating: ParsedSet(reps: reps, weightKg: kg), count: sets).map {
                ParsedSet(reps: $0.reps, weightKg: $0.weightKg)
            }, original: line)
        }

        // "3x12 40 kg" or "40 kg 3x12"
        if let m = firstMatch(#"(\d+)\s*[x×]\s*(\d+)"#, in: rest), let sets = Int(m[0]), let reps = Int(m[1]), sets > 0, sets <= 20 {
            let weight = firstMatch(#"(\d+(?:[.,]\d+)?)\s*(kg|kilo|lbs?)"#, in: rest)
            let kg = weight.flatMap { w in LiftrFormat.parse(w[0]).map { toKg($0, w[1]) } } ?? 0
            return ParsedLine(name: name, sets: (0..<sets).map { _ in ParsedSet(reps: reps, weightKg: kg) }, original: line)
        }

        // "12 herhalingen 40 kg" → one set
        if let reps = firstMatch(#"(\d+)\s*(?:herh|reps?|keer)"#, in: rest).flatMap({ Int($0[0]) }) {
            let weight = firstMatch(#"(\d+(?:[.,]\d+)?)\s*(kg|kilo|lbs?)"#, in: rest)
            let kg = weight.flatMap { w in LiftrFormat.parse(w[0]).map { toKg($0, w[1]) } } ?? 0
            return ParsedLine(name: name, sets: [ParsedSet(reps: reps, weightKg: kg)], original: line)
        }
        // "40kg 12, 45kg 10" or "40 x 12, 45 x 10" (weight × reps per set)
        let pairs = allMatches(#"(\d+(?:[.,]\d+)?)\s*(?:(kg|kilo|lbs?)\s*[x×*]?|[x×*])\s*(\d+)"#, in: rest)
        let perSet = pairs.compactMap { m -> ParsedSet? in
            guard let w = LiftrFormat.parse(m[0]), let r = Int(m[2]), r > 0, r < 200 else { return nil }
            return ParsedSet(reps: r, weightKg: toKg(w, m[1].isEmpty ? nil : m[1]))
        }
        if !perSet.isEmpty, rest.contains(",") || rest.contains(";") || rest.contains("kg") {
            return ParsedLine(name: name, sets: perSet, original: line)
        }

        return nil
    }

    private static let months: [String: Int] = [
        "jan": 1, "januari": 1, "january": 1, "feb": 2, "februari": 2, "february": 2, "mrt": 3, "maart": 3, "mar": 3, "march": 3,
        "apr": 4, "april": 4, "mei": 5, "may": 5, "jun": 6, "juni": 6, "june": 6, "jul": 7, "juli": 7, "july": 7,
        "aug": 8, "augustus": 8, "august": 8, "sep": 9, "sept": 9, "september": 9, "okt": 10, "oktober": 10, "oct": 10, "october": 10,
        "nov": 11, "november": 11, "dec": 12, "december": 12,
    ]

    /// Recognises "3-11-2026", "03/11/26", "3-11", "3 november 2026", "ma 3 nov".
    static func parseDate(_ line: String) -> Date? {
        let lower = line.lowercased()
        let cal = Calendar(identifier: .gregorian)
        let thisYear = cal.component(.year, from: Date())

        func make(_ d: Int, _ m: Int, _ y: Int?) -> Date? {
            var year = y ?? thisYear
            if year < 100 { year += 2000 }
            guard (1...31).contains(d), (1...12).contains(m) else { return nil }
            var c = DateComponents(year: year, month: m, day: d, hour: 18)
            c.calendar = cal
            guard let date = cal.date(from: c) else { return nil }
            // Without a year, a date in the future means last year.
            if y == nil, date > Date().addingTimeInterval(86_400) {
                return cal.date(byAdding: .year, value: -1, to: date)
            }
            return date
        }

        // The whole line should mostly be a date (no exercise with numbers).
        if let m = firstMatch(#"^(?:[a-z]+\.?,?\s+)?(\d{1,2})[-/.](\d{1,2})(?:[-/.](\d{2,4}))?\s*:?$"#, in: lower),
           let d = Int(m[0]), let mo = Int(m[1]) {
            return make(d, mo, Int(m[2]))
        }
        if let m = firstMatch(#"^(?:[a-z]+\.?,?\s+)?(\d{1,2})\s+([a-z]+)\.?(?:\s+(\d{4}))?\s*:?$"#, in: lower),
           let d = Int(m[0]), let mo = months[m[1]] {
            return make(d, mo, Int(m[2]))
        }
        if let m = firstMatch(#"^(\d{4})-(\d{2})-(\d{2})"#, in: lower), let y = Int(m[0]), let mo = Int(m[1]), let d = Int(m[2]) {
            return make(d, mo, y)
        }
        return nil
    }

    // MARK: Regex helpers (NSRegularExpression works on iOS 17 and watchOS 10)

    private static func firstMatch(_ pattern: String, in text: String) -> [String]? {
        allMatches(pattern, in: text).first
    }

    private static func allMatches(_ pattern: String, in text: String) -> [[String]] {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return [] }
        let ns = text as NSString
        return regex.matches(in: text, range: NSRange(location: 0, length: ns.length)).map { match in
            (1..<match.numberOfRanges).map { i in
                let r = match.range(at: i)
                return r.location == NSNotFound ? "" : ns.substring(with: r)
            }
        }
    }

    // MARK: Import

    /// Creates the workouts. Exercises are matched by name; unknown names become new exercises.
    @MainActor
    static func importDays(_ days: [ParsedDay], context: ModelContext) -> Int {
        var exercises = (try? context.fetch(FetchDescriptor<Exercise>())) ?? []
        var created = 0
        for day in days {
            let lines = day.lines.filter { $0.include && !$0.sets.isEmpty }
            guard !lines.isEmpty else { continue }
            let workout = Workout(title: tr("Imported workout", "Geïmporteerde training"), startDate: day.date, source: "import")
            workout.endDate = day.date.addingTimeInterval(60 * 60)
            context.insert(workout)
            for (index, line) in lines.enumerated() {
                let exercise = match(line.name, in: exercises) ?? {
                    let isCardio = line.sets.allSatisfy { $0.reps == 0 && ($0.minutes > 0 || $0.km > 0) }
                    let new = Exercise(name: line.name, muscle: isCardio ? .cardio : .fullBody, kind: isCardio ? .cardio : .machine)
                    context.insert(new)
                    exercises.append(new)
                    return new
                }()
                let entry = WorkoutEntry(order: index, exercise: exercise)
                entry.workout = workout
                context.insert(entry)
                for (n, parsed) in line.sets.enumerated() {
                    let set = SetRecord(order: n, reps: parsed.reps, weight: parsed.weightKg)
                    set.durationSeconds = Int(parsed.minutes * 60)
                    set.distanceMeters = parsed.km * 1000
                    set.isDone = true
                    set.completedAt = workout.startDate
                    set.entry = entry
                    context.insert(set)
                }
            }
            created += 1
        }
        try? context.save()
        return created
    }

    static func normalized(_ s: String) -> String {
        s.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
            .components(separatedBy: CharacterSet.alphanumerics.inverted).joined()
    }

    static func match(_ name: String, in exercises: [Exercise]) -> Exercise? {
        let key = normalized(name)
        guard !key.isEmpty else { return nil }
        if let exact = exercises.first(where: { normalized($0.name) == key }) { return exact }
        if let seed = ExerciseLibrary.seeds.first(where: { normalized($0.en) == key || normalized($0.nl) == key }),
           let found = exercises.first(where: { $0.seedID == seed.id }) {
            return found
        }
        return exercises.first { ex in
            let n = normalized(ex.name)
            return n.count >= 5 && key.count >= 5 && (n.hasPrefix(key) || key.hasPrefix(n))
        }
    }
}
