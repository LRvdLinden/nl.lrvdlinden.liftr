import Foundation

/// Turns a spreadsheet (.xlsx, .csv) of past workouts into Liftr workouts.
///
/// Two layouts are understood:
/// - **Per date** (one row per training day, one column per exercise):
///   `Periode | Datum | Leg press | Chest press …` with cells such as `45`, `27 (3×10)`, `25 / 18`, `23 x 2`.
///   A cell with only a weight gets the default number of sets and reps.
/// - **Per set** (one row per set or exercise), like Liftr's own CSV export:
///   `date | exercise | sets | reps | weight …`.
///
/// Dates without a year ("15 mei") get their year from the order of the rows: the last row is the most
/// recent date up to today, and going up, a later month means the year before.
enum SpreadsheetImporter {
    typealias Day = NotesImporter.ParsedDay
    typealias Line = NotesImporter.ParsedLine
    typealias ParsedSet = NotesImporter.ParsedSet

    struct Options: Equatable {
        var defaultSets = 3
        var defaultReps = 10
    }

    struct Table {
        var name: String
        var rows: [[String]]
    }

    enum Layout { case perDate, perSet }

    struct Result {
        var days: [Day]
        var layout: Layout
        /// Column headers read as exercises (per-date layout).
        var exerciseColumns: [String]
        /// Cells that could not be read, e.g. "B12: ??".
        var skipped: [String]
    }

    enum LoadError: LocalizedError {
        case unreadable
        var errorDescription: String? {
            tr("This file can't be read. Use an Excel file (.xlsx) or a CSV file.",
               "Dit bestand kan niet gelezen worden. Gebruik een Excel-bestand (.xlsx) of een CSV-bestand.")
        }
    }

    // MARK: Loading

    static func load(_ url: URL) throws -> [Table] {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let data = try Data(contentsOf: url)
        if data.starts(with: [0x50, 0x4B]) {
            let sheets = try XLSXReader.sheets(from: data)
            let tables = sheets.map { Table(name: $0.name, rows: $0.rows) }.filter { !$0.rows.isEmpty }
            guard !tables.isEmpty else { throw LoadError.unreadable }
            return tables
        }
        guard let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) else {
            throw LoadError.unreadable
        }
        let rows = csvRows(text)
        guard !rows.isEmpty else { throw LoadError.unreadable }
        return [Table(name: url.deletingPathExtension().lastPathComponent, rows: rows)]
    }

    /// CSV with ";", "," or tab as separator (whichever the header uses most) and "quoted, fields".
    static func csvRows(_ text: String) -> [[String]] {
        let clean = text.replacingOccurrences(of: "\u{FEFF}", with: "")
        let firstLine = clean.prefix { $0 != "\n" && $0 != "\r" }
        let candidates: [Character] = [";", "\t", ","]
        let separator: Character = candidates.max { a, b in
            firstLine.filter { $0 == a }.count < firstLine.filter { $0 == b }.count
        } ?? ";"
        var rows: [[String]] = []
        var row: [String] = []
        var field = ""
        var quoted = false
        var iterator = Array(clean).makeIterator()
        var pending: Character? = nil
        while let ch = pending ?? iterator.next() {
            pending = nil
            if quoted {
                if ch == "\"" {
                    if let next = iterator.next() {
                        if next == "\"" { field.append("\"") } else { quoted = false; pending = next }
                    } else { quoted = false }
                } else { field.append(ch) }
                continue
            }
            switch ch {
            case "\"": quoted = true
            case separator: row.append(field); field = ""
            case "\n", "\r", "\r\n":
                row.append(field); field = ""
                if row.contains(where: { !$0.trimmingCharacters(in: .whitespaces).isEmpty }) { rows.append(row) }
                row = []
            default: field.append(ch)
            }
        }
        row.append(field)
        if row.contains(where: { !$0.trimmingCharacters(in: .whitespaces).isEmpty }) { rows.append(row) }
        return rows.map { $0.map { $0.trimmingCharacters(in: .whitespaces) } }
    }

    // MARK: Parsing

    static func parse(_ table: Table, options: Options, unit: WeightUnit, today: Date = Date()) -> Result {
        let rows = table.rows
        // Header: the first row (of the first ten) with at least two filled cells.
        guard let headerIndex = rows.prefix(10).firstIndex(where: { $0.filter { !$0.isEmpty }.count >= 2 }) else {
            return Result(days: [], layout: .perDate, exerciseColumns: [], skipped: [])
        }
        let header = rows[headerIndex].map { $0.trimmingCharacters(in: .whitespaces) }
        let body = Array(rows.dropFirst(headerIndex + 1))
        let keys = header.map(key)

        let dateColumn = keys.firstIndex { ["date", "datum", "dag", "day"].contains($0) }
            ?? bestDateColumn(body, columns: header.count)
        guard let dateColumn else { return Result(days: [], layout: .perDate, exerciseColumns: [], skipped: []) }

        if let exerciseColumn = keys.firstIndex(where: { ["exercise", "oefening", "naam", "name", "apparaat", "machine"].contains($0) }) {
            return parsePerSet(header: keys, body: body, dateColumn: dateColumn, exerciseColumn: exerciseColumn,
                               headerRow: headerIndex, options: options, unit: unit, today: today)
        }
        return parsePerDate(header: header, body: body, dateColumn: dateColumn, headerRow: headerIndex,
                            options: options, unit: unit, today: today)
    }

    /// Lower case letters and digits only: "Weight (kg)" → "weightkg".
    private static func key(_ s: String) -> String {
        s.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
            .lowercased().filter { $0.isLetter || $0.isNumber }
    }

    private static let metaColumns: Set<String> = [
        "periode", "period", "week", "notitie", "notities", "notes", "note", "opmerking", "opmerkingen",
        "comment", "comments", "fase", "phase", "blok", "block", "workout", "training", "titel", "title", "tijd", "time",
    ]

    private static func bestDateColumn(_ body: [[String]], columns: Int) -> Int? {
        let scores = (0..<columns).map { c in body.prefix(50).filter { row in c < row.count && dayMonth(row[c]) != nil }.count }
        guard let best = scores.indices.max(by: { scores[$0] < scores[$1] }), scores[best] > 0 else { return nil }
        return best
    }

    private static func columnName(_ index: Int) -> String {
        var n = index + 1
        var name = ""
        while n > 0 {
            let r = (n - 1) % 26
            name = String(UnicodeScalar(65 + r)!) + name
            n = (n - 1) / 26
        }
        return name
    }

    // MARK: Per date (one column per exercise)

    private static func parsePerDate(header: [String], body: [[String]], dateColumn: Int, headerRow: Int,
                                     options: Options, unit: WeightUnit, today: Date) -> Result {
        let meta = header.indices.filter { $0 != dateColumn && metaColumns.contains(key(header[$0])) }
        let exerciseColumns = header.indices.filter { c in
            c != dateColumn && !meta.contains(c) && !header[c].isEmpty
                // A column where no cell holds a number is text, not an exercise.
                && body.contains { row in c < row.count && row[c].contains(where: \.isNumber) }
        }

        var raw: [(dayMonth: DayMonth, row: Int)] = []
        for (i, row) in body.enumerated() {
            guard dateColumn < row.count, let dm = dayMonth(row[dateColumn]) else { continue }
            raw.append((dm, i))
        }
        let dates = resolveYears(raw.map(\.dayMonth), today: today)

        var days: [Day] = []
        var skipped: [String] = []
        for (n, item) in raw.enumerated() {
            let row = body[item.row]
            var lines: [Line] = []
            for c in exerciseColumns where c < row.count && !row[c].isEmpty {
                if let parsed = parseCell(row[c], options: options, unit: unit) {
                    var line = Line(name: header[c], sets: parsed.sets, original: "\(header[c]): \(row[c])")
                    line.note = parsed.note
                    lines.append(line)
                } else {
                    skipped.append("\(columnName(c))\(headerRow + item.row + 2): \(row[c])")
                }
            }
            guard !lines.isEmpty else { continue }
            var day = Day(date: dates[n], lines: lines)
            day.note = meta.compactMap { c in c < row.count && !row[c].isEmpty ? row[c] : nil }.joined(separator: " · ")
            days.append(day)
        }
        return Result(days: days, layout: .perDate, exerciseColumns: exerciseColumns.map { header[$0] }, skipped: skipped)
    }

    /// One cell: `45`, `27.3`, `27 (rechte 3×10)`, `18 (4×5)`, `25 / 18`, `36/41/45/52`, `23 x 2`, `36 (op)`.
    static func parseCell(_ text: String, options: Options, unit: WeightUnit) -> (sets: [ParsedSet], note: String)? {
        var s = text.lowercased().replacingOccurrences(of: "×", with: "x").replacingOccurrences(of: "*", with: "x")
        var note: [String] = []
        var setsCount: Int? = nil
        var reps: Int? = nil

        // "(rechte 3×10)": sets × reps plus words as a note.
        for inner in matches(#"\(([^)]*)\)"#, in: s).map({ $0[0] }) {
            var words = inner
            if let m = matches(#"(\d+)\s*x\s*(\d+)"#, in: inner).first, let a = Int(m[0]), let b = Int(m[1]) {
                setsCount = a
                reps = b
                words = words.replacingOccurrences(of: #"\d+\s*x\s*\d+"#, with: "", options: .regularExpression)
            }
            let cleaned = words.trimmingCharacters(in: .whitespacesAndNewlines.union(.punctuationCharacters))
            if !cleaned.isEmpty { note.append(cleaned) }
        }
        s = s.replacingOccurrences(of: #"\([^)]*\)"#, with: " ", options: .regularExpression)
        let lbs = s.contains("lb")
        s = s.replacingOccurrences(of: #"(kg|kilo|lbs?)"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)

        let toKg: (Double) -> Double = { value in
            let kg = (lbs || unit == .lb) ? value / 2.204_622_6 : value
            return (kg * 100).rounded() / 100
        }

        // "23 x 2": a weight and a count. Up to 6 it is the number of sets, more is the number of reps.
        if let m = matches(#"^(\d+(?:[.,]\d+)?)\s*x\s*(\d+)$"#, in: s).first,
           let w = LiftrFormat.parse(m[0]), let count = Int(m[1]), count > 0 {
            let n = count <= 6 ? count : (setsCount ?? options.defaultSets)
            let r = count <= 6 ? (reps ?? options.defaultReps) : count
            return ((0..<n).map { _ in ParsedSet(reps: r, weightKg: toKg(w)) }, note.joined(separator: ", "))
        }

        // "25 / 18" or "36/41/45/52": one weight per set, or a single weight for all sets.
        let parts = s.split(whereSeparator: { $0 == "/" || $0 == "-" || $0 == "+" || $0 == ";" })
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        let weights = parts.compactMap { part -> Double? in
            guard let value = LiftrFormat.parse(part), value >= 0, value < 1000 else { return nil }
            return value
        }
        guard !weights.isEmpty, weights.count == parts.count else { return nil }
        if weights.count == 1 {
            let n = setsCount ?? options.defaultSets
            return ((0..<max(1, n)).map { _ in ParsedSet(reps: reps ?? options.defaultReps, weightKg: toKg(weights[0])) },
                    note.joined(separator: ", "))
        }
        return (weights.map { ParsedSet(reps: reps ?? options.defaultReps, weightKg: toKg($0)) }, note.joined(separator: ", "))
    }

    // MARK: Per set (one row per set or exercise)

    private static func parsePerSet(header: [String], body: [[String]], dateColumn: Int, exerciseColumn: Int, headerRow: Int,
                                    options: Options, unit: WeightUnit, today: Date) -> Result {
        func column(_ names: [String]) -> Int? { header.firstIndex { names.contains($0) } }
        let setsColumn = column(["sets", "aantalsets", "series"])
        let repsColumn = column(["reps", "herhalingen", "herh", "repetitions", "rep", "aantal"])
        let kgColumn = column(["weightkg", "gewichtkg", "kg", "kilo"])
        let lbColumn = column(["weightlb", "weightlbs", "lb", "lbs"])
        let weightColumn = kgColumn ?? lbColumn ?? column(["weight", "gewicht", "load"])
        let minutesColumn = column(["durationmin", "duur", "duurmin", "minuten", "min", "minutes", "duration"])
        let kmColumn = column(["distancekm", "afstand", "afstandkm", "km", "distance"])
        let titleColumn = column(["workout", "training", "titel", "title"])

        func cell(_ row: [String], _ c: Int?) -> String {
            guard let c, c < row.count else { return "" }
            return row[c]
        }

        var raw: [(dayMonth: DayMonth, row: Int)] = []
        for (i, row) in body.enumerated() {
            guard let dm = dayMonth(cell(row, dateColumn)), !cell(row, exerciseColumn).isEmpty else { continue }
            raw.append((dm, i))
        }
        let dates = resolveYears(raw.map(\.dayMonth), today: today)

        var days: [Day] = []
        var skipped: [String] = []
        for (n, item) in raw.enumerated() {
            let row = body[item.row]
            let date = dates[n]
            let title = cell(row, titleColumn)
            let name = cell(row, exerciseColumn)

            let weightText = cell(row, weightColumn)
            var weight = LiftrFormat.parse(weightText.lowercased().replacingOccurrences(of: #"[^0-9.,]"#, with: "", options: .regularExpression)) ?? 0
            let inPounds = (lbColumn != nil && weightColumn == lbColumn) || weightText.lowercased().contains("lb")
                || (kgColumn == nil && lbColumn == nil && unit == .lb)
            if inPounds { weight /= 2.204_622_6 }
            weight = (weight * 100).rounded() / 100
            let reps = Int(LiftrFormat.parse(cell(row, repsColumn)) ?? 0)
            let count = setsColumn != nil ? Int(LiftrFormat.parse(cell(row, setsColumn)) ?? 1) : 1
            let minutes = LiftrFormat.parse(cell(row, minutesColumn)) ?? 0
            let km = LiftrFormat.parse(cell(row, kmColumn)) ?? 0
            guard weight > 0 || reps > 0 || minutes > 0 || km > 0 else {
                skipped.append("\(headerRow + item.row + 2): \(name)")
                continue
            }
            let isCardio = reps == 0 && (minutes > 0 || km > 0)
            let one = ParsedSet(reps: isCardio ? 0 : (reps > 0 ? reps : options.defaultReps), weightKg: weight,
                          minutes: minutes, km: km)
            let sets = (0..<max(1, min(count, 20))).map { _ in ParsedSet(reps: one.reps, weightKg: one.weightKg, minutes: one.minutes, km: one.km) }

            let dayIndex: Int
            if let found = days.firstIndex(where: { Calendar.current.isDate($0.date, inSameDayAs: date) && $0.note == title }) {
                dayIndex = found
            } else {
                days.append(Day(date: date, lines: [], note: title))
                dayIndex = days.count - 1
            }
            if let lineIndex = days[dayIndex].lines.lastIndex(where: { $0.name == name }),
               lineIndex == days[dayIndex].lines.count - 1 {
                days[dayIndex].lines[lineIndex].sets += sets
                days[dayIndex].lines[lineIndex].original += " · " + summary(sets)
            } else {
                days[dayIndex].lines.append(Line(name: name, sets: sets, original: summary(sets)))
            }
        }
        return Result(days: days, layout: .perSet, exerciseColumns: [], skipped: skipped)
    }

    private static func summary(_ sets: [ParsedSet]) -> String {
        sets.map { set in
            if set.reps == 0 { return "\(LiftrFormat.decimal(set.minutes)) min" }
            return set.weightKg > 0 ? "\(set.reps) × \(LiftrFormat.decimal(set.weightKg, maxFraction: 2)) kg" : "\(set.reps) ×"
        }.joined(separator: ", ")
    }

    // MARK: Dates

    struct DayMonth: Equatable {
        var day: Int
        var month: Int
        var year: Int?
        var hour = 18
        var minute = 0
    }

    private static let months: [String: Int] = [
        "jan": 1, "januari": 1, "january": 1, "feb": 2, "februari": 2, "february": 2, "mrt": 3, "maa": 3, "maart": 3,
        "mar": 3, "march": 3, "apr": 4, "april": 4, "mei": 5, "may": 5, "jun": 6, "juni": 6, "june": 6, "jul": 7,
        "juli": 7, "july": 7, "aug": 8, "augustus": 8, "august": 8, "sep": 9, "sept": 9, "september": 9, "okt": 10,
        "oktober": 10, "oct": 10, "october": 10, "nov": 11, "november": 11, "dec": 12, "december": 12,
    ]

    /// "15 mei", "15 mei 2025", "ma 15 mei", "15-5", "15-05-2025", "2025-05-15T18:00", or an Excel date number.
    static func dayMonth(_ text: String) -> DayMonth? {
        let s = text.lowercased().trimmingCharacters(in: .whitespaces)
        guard !s.isEmpty else { return nil }

        if let m = matches(#"^(\d{4})-(\d{1,2})-(\d{1,2})(?:[t\s](\d{1,2}):(\d{2}))?"#, in: s).first,
           let y = Int(m[0]), let mo = Int(m[1]), let d = Int(m[2]) {
            return valid(DayMonth(day: d, month: mo, year: y, hour: Int(m[3]) ?? 18, minute: Int(m[4]) ?? 0))
        }
        if let m = matches(#"^(?:[a-z]+\.?,?\s+)?(\d{1,2})[-/.](\d{1,2})(?:[-/.](\d{2,4}))?$"#, in: s).first,
           let d = Int(m[0]), let mo = Int(m[1]) {
            return valid(DayMonth(day: d, month: mo, year: Int(m[2]).map { $0 < 100 ? $0 + 2000 : $0 }))
        }
        if let m = matches(#"^(?:[a-z]+\.?,?\s+)?(\d{1,2})\s+([a-z]+)\.?(?:\s+(\d{4}))?$"#, in: s).first,
           let d = Int(m[0]), let mo = months[m[1]] ?? months[String(m[1].prefix(3))] {
            return valid(DayMonth(day: d, month: mo, year: Int(m[2])))
        }
        // Excel stores dates as days since 30 December 1899.
        if let serial = Double(s), serial > 20_000, serial < 80_000 {
            let date = Date(timeIntervalSince1970: (serial - 25_569) * 86_400)
            var utc = Calendar(identifier: .gregorian)
            utc.timeZone = TimeZone(identifier: "UTC")!
            let c = utc.dateComponents([.year, .month, .day, .hour, .minute], from: date)
            let hasTime = serial.truncatingRemainder(dividingBy: 1) != 0
            return DayMonth(day: c.day ?? 1, month: c.month ?? 1, year: c.year,
                            hour: hasTime ? (c.hour ?? 18) : 18, minute: hasTime ? (c.minute ?? 0) : 0)
        }
        return nil
    }

    private static func valid(_ dm: DayMonth) -> DayMonth? {
        (1...31).contains(dm.day) && (1...12).contains(dm.month) ? dm : nil
    }

    /// Fills in missing years from the order of the rows (oldest at the top or at the bottom both work).
    static func resolveYears(_ items: [DayMonth], today: Date) -> [Date] {
        let cal = Calendar.current
        let thisYear = cal.component(.year, from: today)
        func date(_ dm: DayMonth, _ year: Int) -> Date {
            cal.date(from: DateComponents(year: year, month: dm.month, day: dm.day, hour: dm.hour, minute: dm.minute)) ?? today
        }
        func order(_ dm: DayMonth) -> Int { dm.month * 100 + dm.day }
        guard !items.isEmpty else { return [] }

        // Newest first or oldest first? Look at the rows that do have a year, else assume oldest first.
        let withYear = items.compactMap { $0.year != nil ? date($0, $0.year!) : nil }
        let newestFirst = withYear.count >= 2 && withYear.first! > withYear.last!
        let indices = newestFirst ? Array(items.indices) : Array(items.indices.reversed())

        var years = [Int](repeating: thisYear, count: items.count)
        var previous: (dm: DayMonth, year: Int)? = nil
        for i in indices {  // walking from the newest row back in time
            let dm = items[i]
            var year: Int
            if let y = dm.year {
                year = y
            } else if let previous {
                year = previous.year
                if order(dm) > order(previous.dm) { year -= 1 }
            } else {
                year = thisYear
                if date(dm, year) > today.addingTimeInterval(86_400) { year -= 1 }
            }
            years[i] = year
            previous = (dm, year)
        }
        return items.indices.map { date(items[$0], years[$0]) }
    }

    // MARK: Regex

    private static func matches(_ pattern: String, in text: String) -> [[String]] {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return [] }
        let ns = text as NSString
        return regex.matches(in: text, range: NSRange(location: 0, length: ns.length)).map { match in
            (1..<match.numberOfRanges).map { i in
                let r = match.range(at: i)
                return r.location == NSNotFound ? "" : ns.substring(with: r)
            }
        }
    }
}
