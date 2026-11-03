import SwiftData
import SwiftUI
import UniformTypeIdentifiers

/// Import old workouts: paste notes, or pick a spreadsheet (.xlsx / .csv). Check what Liftr understood, then import.
/// Imported workouts go into history and charts, but never into Apple Health.
struct ImportView: View {
    enum Mode: Hashable { case notes, file }

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"
    @Query private var exercises: [Exercise]

    @State private var mode: Mode = .notes
    @State private var text = ""
    @State private var defaultDate = Date()
    @State private var days: [NotesImporter.ParsedDay] = []
    @State private var analysed = false
    @State private var result: NotesImporter.ImportResult?

    // Spreadsheet
    @State private var picking = false
    @State private var fileName = ""
    @State private var tables: [SpreadsheetImporter.Table] = []
    @State private var tableIndex = 0
    @State private var options = SpreadsheetImporter.Options()
    @State private var exerciseColumns: [String] = []
    @State private var renames: [String: String] = [:]
    @State private var skipped: [String] = []
    @State private var loadError: String?

    private var unit: WeightUnit { WeightUnit(rawValue: unitRaw) ?? .kg }

    private let example = """
    3-11-2026
    Leg press 3x12 40 kg
    Chest press 3 x 10 x 25 kg
    Lat pulldown 30kg 12, 35kg 10, 35kg 8
    Loopband 20 min 2,5 km
    """

    /// The empty sheet to fill in, with instructions, on liftr.nu.
    private static var importSheetURL: URL {
        URL(string: L10n.isDutch ? "https://liftr.nu/#importsheet" : "https://liftr.nu/en/#import-sheet")!
    }

    private static let fileTypes: [UTType] = [
        UTType("org.openxmlformats.spreadsheetml.sheet") ?? .spreadsheet,
        .commaSeparatedText, .tabSeparatedText, .plainText,
    ]

    var body: some View {
        NavigationStack {
            Form {
                if !analysed {
                    Section {
                        Picker(tr("Import from", "Importeren uit"), selection: $mode) {
                            Text(tr("Notes", "Notities")).tag(Mode.notes)
                            Text(tr("Spreadsheet", "Spreadsheet")).tag(Mode.file)
                        }
                        .pickerStyle(.segmented)
                    }
                    .listRowBackground(Color.clear)

                    if mode == .notes { notesInput } else { fileInput }
                } else if mode == .file {
                    fileSettings
                    preview
                } else {
                    preview
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(tr("Import", "Importeren"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbar }
            .fileImporter(isPresented: $picking, allowedContentTypes: Self.fileTypes) { picked in
                switch picked {
                case .success(let url): load(url)
                case .failure(let error): loadError = error.localizedDescription
                }
            }
            .alert(tr("Imported", "Geïmporteerd"), isPresented: Binding(get: { result != nil }, set: { if !$0 { result = nil } })) {
                Button("OK") { dismiss() }
            } message: {
                Text(resultMessage)
            }
            .alert(tr("Can't read file", "Bestand niet gelezen"), isPresented: Binding(get: { loadError != nil }, set: { if !$0 { loadError = nil } })) {
                Button("OK") {}
            } message: {
                Text(loadError ?? "")
            }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: Input

    @ViewBuilder private var notesInput: some View {
        Section {
            TextEditor(text: $text)
                .frame(minHeight: 220)
                .font(.body.monospaced())
                .overlay(alignment: .topLeading) {
                    if text.isEmpty {
                        Text(example)
                            .font(.body.monospaced())
                            .foregroundStyle(Theme.muted.opacity(0.6))
                            .padding(.top, 8)
                            .padding(.leading, 5)
                            .allowsHitTesting(false)
                    }
                }
            Button {
                if let clip = UIPasteboard.general.string { text = clip }
            } label: {
                Label(tr("Paste", "Plakken"), systemImage: "doc.on.clipboard")
            }
        } header: {
            Text(tr("Your notes", "Je notities"))
        } footer: {
            Text(tr("Copy the note from the Notes app and paste it here. A line with a date starts a new workout; lines without a date before them get the date below.",
                    "Kopieer de notitie uit de Notities-app en plak hem hier. Een regel met een datum begint een nieuwe training; regels zonder datum ervoor krijgen de datum hieronder."))
        }
        .listRowBackground(Theme.card)

        Section {
            DatePicker(tr("Date without heading", "Datum zonder kopje"), selection: $defaultDate, in: ...Date(), displayedComponents: .date)
        } footer: {
            healthFooter
        }
        .listRowBackground(Theme.card)
    }

    @ViewBuilder private var fileInput: some View {
        Section {
            Button {
                picking = true
            } label: {
                Label(tr("Choose file", "Kies bestand"), systemImage: "tablecells")
                    .font(.headline)
            }
            Link(destination: Self.importSheetURL) {
                Label(tr("Download the import sheet", "Download de importsheet"), systemImage: "arrow.down.doc")
            }
        } header: {
            Text(tr("Excel or CSV", "Excel of CSV"))
        } footer: {
            VStack(alignment: .leading, spacing: 8) {
                Text(tr("One row per training day with a date column and a column per exercise, e.g. “Date | Leg press | Chest press” with “45”, “27 (3×10)” or “25 / 18” in the cells. A file with one row per set (date, exercise, reps, weight) works too, as does a Liftr export.",
                        "Eén rij per trainingsdag met een datumkolom en een kolom per oefening, bijvoorbeeld “Datum | Leg press | Chest press” met “45”, “27 (3×10)” of “25 / 18” in de cellen. Een bestand met één rij per set (datum, oefening, herhalingen, gewicht) kan ook, net als een Liftr-export."))
                healthFooter
            }
        }
        .listRowBackground(Theme.card)
    }

    private var healthFooter: some View {
        Label(tr("Imported workouts go into your history and charts, but are not written to Apple Health.",
                 "Geïmporteerde trainingen komen in je geschiedenis en grafieken, maar worden niet naar Apple Gezondheid geschreven."),
              systemImage: "heart.slash")
    }

    // MARK: File settings

    @ViewBuilder private var fileSettings: some View {
        Section {
            if tables.count > 1 {
                Picker(tr("Sheet", "Werkblad"), selection: $tableIndex) {
                    ForEach(tables.indices, id: \.self) { Text(tables[$0].name).tag($0) }
                }
            }
            Stepper(value: $options.defaultSets, in: 1...10) {
                LabeledContent(tr("Sets per weight", "Sets per gewicht"), value: "\(options.defaultSets)")
            }
            Stepper(value: $options.defaultReps, in: 1...50) {
                LabeledContent(tr("Reps per set", "Herhalingen per set"), value: "\(options.defaultReps)")
            }
        } header: {
            Text(fileName)
        } footer: {
            Text(tr("Used when a cell only holds a weight. A cell like “27 (3×10)” keeps its own sets and reps.",
                    "Gebruikt als een cel alleen een gewicht bevat. Een cel als “27 (3×10)” houdt zijn eigen sets en herhalingen."))
        }
        .listRowBackground(Theme.card)
        .onChange(of: options) { _, _ in reparse() }
        .onChange(of: tableIndex) { _, _ in reparse() }

        if !exerciseColumns.isEmpty {
            Section {
                ForEach(exerciseColumns, id: \.self) { column in
                    VStack(alignment: .leading, spacing: 2) {
                        TextField(column, text: Binding(
                            get: { renames[column] ?? column },
                            set: { renames[column] = $0 }
                        ))
                        .font(.headline)
                        Text(matchDescription(for: renames[column] ?? column))
                            .font(.caption)
                            .foregroundStyle(Theme.muted)
                    }
                }
            } header: {
                Text(tr("Exercises", "Oefeningen"))
            } footer: {
                Text(tr("Rename a column to link it to an exercise you already have.",
                        "Geef een kolom een andere naam om hem aan een oefening te koppelen die je al hebt."))
            }
            .listRowBackground(Theme.card)
        }

        if !skipped.isEmpty {
            Section {
                DisclosureGroup(trf("{1} cells not read", "{1} cellen niet gelezen", skipped.count)) {
                    ForEach(skipped, id: \.self) { Text($0).font(.caption.monospaced()) }
                }
            }
            .listRowBackground(Theme.card)
        }
    }

    private func matchDescription(for name: String) -> String {
        if let found = NotesImporter.match(name, in: exercises) {
            return trf("Linked to {1}", "Gekoppeld aan {1}", found.name)
        }
        return tr("New exercise", "Nieuwe oefening")
    }

    // MARK: Preview

    @ViewBuilder private var preview: some View {
        if days.isEmpty {
            Section {
                Text(mode == .file
                     ? tr("Liftr couldn't find a date column with exercises in this file.",
                          "Liftr vond in dit bestand geen datumkolom met oefeningen.")
                     : tr("Liftr couldn't find any exercises. Use lines like “Leg press 3x12 40 kg”.",
                          "Liftr vond geen oefeningen. Gebruik regels als “Leg press 3x12 40 kg”."))
                    .foregroundStyle(Theme.muted)
            }
            .listRowBackground(Theme.card)
        } else {
            Section {
                Label(trf("{1} workouts from {2} to {3}", "{1} trainingen van {2} tot {3}",
                          days.count, LiftrFormat.day(days.map(\.date).min() ?? Date()), LiftrFormat.day(days.map(\.date).max() ?? Date())),
                      systemImage: "calendar")
                    .font(.subheadline.weight(.semibold))
            } footer: {
                healthFooter
            }
            .listRowBackground(Theme.card)

            ForEach($days) { $day in
                Section {
                    DatePicker(tr("Date", "Datum"), selection: $day.date, displayedComponents: .date)
                    ForEach($day.lines) { $line in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                TextField(tr("Exercise", "Oefening"), text: $line.name)
                                    .font(.headline)
                                    .disabled(mode == .file)
                                Toggle("", isOn: $line.include).labelsHidden()
                            }
                            Text(describe(line))
                                .font(.subheadline.monospacedDigit())
                                .foregroundStyle(Theme.accent)
                            Text(line.note.isEmpty ? line.original : line.original + " · " + line.note)
                                .font(.caption)
                                .foregroundStyle(Theme.muted)
                        }
                        .opacity(line.include ? 1 : 0.45)
                    }
                } header: {
                    Text(day.note.isEmpty ? LiftrFormat.day(day.date) : LiftrFormat.day(day.date) + " · " + day.note)
                }
                .listRowBackground(Theme.card)
            }
        }
    }

    // MARK: Toolbar

    @ToolbarContentBuilder private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button(analysed ? (mode == .file ? tr("Other file", "Ander bestand") : tr("Edit text", "Tekst wijzigen"))
                            : tr("Cancel", "Annuleren")) {
                if analysed {
                    analysed = false
                    if mode == .file { picking = true }
                } else {
                    dismiss()
                }
            }
        }
        ToolbarItem(placement: .confirmationAction) {
            if analysed {
                Button(tr("Import", "Importeer")) {
                    result = NotesImporter.importDays(renamed(days), context: context)
                }
                .fontWeight(.bold)
                .disabled(days.isEmpty)
            } else if mode == .notes {
                Button(tr("Analyse", "Analyseer")) {
                    days = NotesImporter.parse(text, defaultDate: defaultDate, unit: unit)
                    analysed = true
                }
                .fontWeight(.bold)
                .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
    }

    private var resultMessage: String {
        guard let result else { return "" }
        let added = trf("{1} workouts added to your history.", "{1} trainingen toegevoegd aan je geschiedenis.", result.added)
        guard result.skipped > 0 else { return added }
        return added + " " + trf("{1} were already imported and were skipped.", "{1} waren al eerder geïmporteerd en zijn overgeslagen.", result.skipped)
    }

    // MARK: Spreadsheet

    private func load(_ url: URL) {
        do {
            tables = try SpreadsheetImporter.load(url)
            fileName = url.lastPathComponent
            renames = [:]
            // Start on the sheet Liftr understands best.
            let scores = tables.map { SpreadsheetImporter.parse($0, options: options, unit: unit).days.count }
            tableIndex = scores.firstIndex(of: scores.max() ?? 0) ?? 0
            reparse()
            analysed = true
        } catch {
            loadError = (error as? LocalizedError)?.errorDescription ?? SpreadsheetImporter.LoadError.unreadable.errorDescription
        }
    }

    private func reparse() {
        guard tables.indices.contains(tableIndex) else { return }
        let parsed = SpreadsheetImporter.parse(tables[tableIndex], options: options, unit: unit)
        days = parsed.days
        exerciseColumns = parsed.exerciseColumns
        skipped = parsed.skipped
    }

    private func renamed(_ days: [NotesImporter.ParsedDay]) -> [NotesImporter.ParsedDay] {
        guard mode == .file, !renames.isEmpty else { return days }
        return days.map { day in
            var day = day
            for i in day.lines.indices {
                if let new = renames[day.lines[i].name]?.trimmingCharacters(in: .whitespaces), !new.isEmpty {
                    day.lines[i].name = new
                }
            }
            return day
        }
    }

    private func describe(_ line: NotesImporter.ParsedLine) -> String {
        if line.sets.allSatisfy({ $0.reps == 0 }) {
            let minutes = line.sets.reduce(0) { $0 + $1.minutes }
            let km = line.sets.reduce(0) { $0 + $1.km }
            return "\(LiftrFormat.decimal(minutes)) min" + (km > 0 ? " · \(LiftrFormat.decimal(km, maxFraction: 2)) km" : "")
        }
        return line.sets.map { set in
            set.weightKg > 0 ? "\(set.reps) × \(unit.format(set.weightKg))" : "\(set.reps) ×"
        }.joined(separator: ", ")
    }
}
