import SwiftData
import SwiftUI

/// Paste workout notes, check what Liftr understood, then import.
struct ImportView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"
    @State private var text = ""
    @State private var defaultDate = Date()
    @State private var days: [NotesImporter.ParsedDay] = []
    @State private var analysed = false
    @State private var imported: Int?

    private var unit: WeightUnit { WeightUnit(rawValue: unitRaw) ?? .kg }

    private let example = """
    3-11-2026
    Leg press 3x12 40 kg
    Chest press 3 x 10 x 25 kg
    Lat pulldown 30kg 12, 35kg 10, 35kg 8
    Loopband 20 min 2,5 km
    """

    var body: some View {
        NavigationStack {
            Form {
                if !analysed {
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
                    }
                    .listRowBackground(Theme.card)
                } else if days.isEmpty {
                    Section {
                        Text(tr("Liftr couldn't find any exercises. Use lines like “Leg press 3x12 40 kg”.",
                                "Liftr vond geen oefeningen. Gebruik regels als “Leg press 3x12 40 kg”."))
                            .foregroundStyle(Theme.muted)
                    }
                    .listRowBackground(Theme.card)
                } else {
                    ForEach($days) { $day in
                        Section {
                            DatePicker(tr("Date", "Datum"), selection: $day.date, displayedComponents: .date)
                            ForEach($day.lines) { $line in
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        TextField(tr("Exercise", "Oefening"), text: $line.name)
                                            .font(.headline)
                                        Toggle("", isOn: $line.include).labelsHidden()
                                    }
                                    Text(describe(line))
                                        .font(.subheadline.monospacedDigit())
                                        .foregroundStyle(Theme.accent)
                                    Text(line.original)
                                        .font(.caption)
                                        .foregroundStyle(Theme.muted)
                                }
                                .opacity(line.include ? 1 : 0.45)
                            }
                        } header: {
                            Text(LiftrFormat.day(day.date))
                        }
                        .listRowBackground(Theme.card)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(tr("Import", "Importeren"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(analysed ? tr("Back", "Terug") : tr("Cancel", "Annuleren")) {
                        if analysed { analysed = false } else { dismiss() }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if analysed {
                        Button(tr("Import", "Importeer")) {
                            imported = NotesImporter.importDays(days, context: context)
                        }
                        .fontWeight(.bold)
                        .disabled(days.isEmpty)
                    } else {
                        Button(tr("Analyse", "Analyseer")) {
                            days = NotesImporter.parse(text, defaultDate: defaultDate, unit: unit)
                            analysed = true
                        }
                        .fontWeight(.bold)
                        .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
            }
            .alert(tr("Imported", "Geïmporteerd"), isPresented: Binding(get: { imported != nil }, set: { if !$0 { imported = nil } })) {
                Button("OK") { dismiss() }
            } message: {
                Text(tr("\(imported ?? 0) workouts added to your history.", "\(imported ?? 0) trainingen toegevoegd aan je geschiedenis."))
            }
        }
        .preferredColorScheme(.dark)
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
