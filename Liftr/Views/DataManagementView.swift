import SwiftData
import SwiftUI
import WidgetKit

/// Delete one import again, or wipe everything Liftr stores.
struct DataManagementView: View {
    @Environment(\.modelContext) private var context
    @Environment(WorkoutController.self) private var controller
    @Query(sort: \Workout.startDate, order: .reverse) private var workouts: [Workout]

    @State private var deletingBatch: ImportBatch?
    @State private var confirmWipe = false
    @State private var working = false
    @State private var done: String?

    /// The workouts of one import (same file, same moment).
    struct ImportBatch: Identifiable, Hashable {
        let id: String          // the workout source string
        let label: String
        let date: Date?
        let count: Int
        let first: Date
        let last: Date
    }

    private var batches: [ImportBatch] {
        Dictionary(grouping: workouts.filter(\.isImported), by: \.source)
            .map { source, list in
                let info = list[0].importBatch
                let dates = list.map(\.startDate)
                return ImportBatch(id: source,
                                   label: (info?.label).flatMap { $0.isEmpty ? nil : $0 } ?? tr("Earlier import", "Eerdere import"),
                                   date: info?.date, count: list.count,
                                   first: dates.min() ?? Date(), last: dates.max() ?? Date())
            }
            .sorted { ($0.date ?? .distantPast) > ($1.date ?? .distantPast) }
    }

    var body: some View {
        List {
            Section {
                if batches.isEmpty {
                    Text(tr("No imports yet.", "Nog geen imports."))
                        .foregroundStyle(Theme.muted)
                }
                ForEach(batches) { batch in
                    HStack(spacing: 12) {
                        Image(systemName: batch.label.lowercased().hasSuffix(".csv") || batch.label.lowercased().hasSuffix(".xlsx")
                              ? "tablecells" : "note.text")
                            .foregroundStyle(Theme.accent)
                            .frame(width: 28)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(batch.label)
                                .font(.headline)
                                .lineLimit(1)
                            Text(trf("{1} workouts · {2} – {3}", "{1} trainingen · {2} – {3}",
                                     batch.count, LiftrFormat.shortDay(batch.first), LiftrFormat.shortDay(batch.last)))
                                .font(.caption)
                                .foregroundStyle(Theme.muted)
                            if let date = batch.date {
                                Text(trf("Imported {1}", "Geïmporteerd op {1}", date.formatted(date: .abbreviated, time: .shortened)))
                                    .font(.caption2)
                                    .foregroundStyle(Theme.muted)
                            }
                        }
                        Spacer()
                        Button(role: .destructive) {
                            deletingBatch = batch
                        } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.borderless)
                        .tint(.red)
                        .accessibilityLabel(tr("Delete import", "Import verwijderen"))
                    }
                }
            } header: {
                Text(tr("Imports", "Imports"))
            } footer: {
                Text(tr("Deleting an import removes only the workouts from that file. Workouts you logged in Liftr stay.",
                        "Een import verwijderen haalt alleen de trainingen uit dat bestand weg. Trainingen die je in Liftr hebt gelogd blijven staan."))
            }
            .listRowBackground(Theme.card)

            Section {
                Button(role: .destructive) {
                    confirmWipe = true
                } label: {
                    Label(tr("Delete all data", "Alle gegevens wissen"), systemImage: "trash.fill")
                        .foregroundStyle(.red)
                }
                .disabled(working)
            } footer: {
                Text(tr("Deletes all workouts, routines, body measurements, gym passes and your own exercises from this iPhone, and through iCloud from your iPad and Apple Watch too. Your settings stay.",
                        "Wist alle trainingen, schema's, lichaamsmetingen, sportschoolpassen en je eigen oefeningen van deze iPhone, en via iCloud ook van je iPad en Apple Watch. Je instellingen blijven staan."))
            }
            .listRowBackground(Theme.card)
        }
        .scrollContentBackground(.hidden)
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle(tr("Manage data", "Gegevens beheren"))
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(tr("Delete this import?", "Deze import verwijderen?"),
                            isPresented: Binding(get: { deletingBatch != nil }, set: { if !$0 { deletingBatch = nil } }),
                            titleVisibility: .visible, presenting: deletingBatch) { batch in
            Button(trf("Delete {1} workouts", "{1} trainingen verwijderen", batch.count), role: .destructive) {
                deleteImport(batch)
            }
            Button(tr("Cancel", "Annuleren"), role: .cancel) {}
        } message: { batch in
            Text(batch.label)
        }
        .confirmationDialog(tr("Delete all data?", "Alle gegevens wissen?"), isPresented: $confirmWipe, titleVisibility: .visible) {
            Button(tr("Delete all data", "Alle gegevens wissen"), role: .destructive) {
                Task { await wipe(alsoHealth: false) }
            }
            Button(tr("Delete all, also from Apple Health", "Alles wissen, ook uit Apple Gezondheid"), role: .destructive) {
                Task { await wipe(alsoHealth: true) }
            }
            Button(tr("Cancel", "Annuleren"), role: .cancel) {}
        } message: {
            Text(tr("This can't be undone.", "Dit kan niet ongedaan worden gemaakt."))
        }
        .alert(tr("Done", "Klaar"), isPresented: Binding(get: { done != nil }, set: { if !$0 { done = nil } })) {
            Button("OK") {}
        } message: {
            Text(done ?? "")
        }
        .overlay {
            if working { ProgressView().controlSize(.large) }
        }
    }

    // MARK: Deleting

    private func deleteImport(_ batch: ImportBatch) {
        let list = workouts.filter { $0.source == batch.id }
        for workout in list { context.delete(workout) }
        try? context.save()
        refreshAfterChange()
        done = trf("{1} imported workouts deleted.", "{1} geïmporteerde trainingen verwijderd.", list.count)
    }

    private func wipe(alsoHealth: Bool) async {
        working = true
        defer { working = false }
        if controller.active != nil { controller.discard(context) }

        let all = (try? context.fetch(FetchDescriptor<Workout>())) ?? []
        if alsoHealth {
            for uuid in all.compactMap(\.healthKitUUID) {
                await HealthKitManager.shared.deleteWorkout(uuid: uuid)
            }
        }
        for workout in all { context.delete(workout) }
        for routine in (try? context.fetch(FetchDescriptor<Routine>())) ?? [] { context.delete(routine) }
        for item in (try? context.fetch(FetchDescriptor<BodyMeasurement>())) ?? [] { context.delete(item) }
        for pass in (try? context.fetch(FetchDescriptor<GymPass>())) ?? [] { context.delete(pass) }
        for exercise in (try? context.fetch(FetchDescriptor<Exercise>())) ?? [] {
            if exercise.seedID == nil {
                context.delete(exercise)
            } else {
                exercise.isFavorite = false
                exercise.isArchived = false
                exercise.notes = ""
            }
        }
        try? context.save()
        ExerciseLibrary.prepare(context)
        refreshAfterChange()
        done = tr("All data has been deleted.", "Alle gegevens zijn gewist.")
    }

    private func refreshAfterChange() {
        ReminderScheduler.reschedule(context)
        WidgetCenter.shared.reloadAllTimelines()
    }
}
