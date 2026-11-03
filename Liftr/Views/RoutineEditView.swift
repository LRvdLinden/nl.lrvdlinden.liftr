import SwiftData
import SwiftUI

/// Create or edit a routine: a name and a list of exercises with target sets × reps.
struct RoutineEditView: View {
    let routine: Routine?

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"
    @State private var name = ""
    @State private var items: [Draft] = []
    @State private var showPicker = false
    @State private var loaded = false
    @State private var confirmDelete = false

    private var unit: WeightUnit { WeightUnit(rawValue: unitRaw) ?? .kg }

    struct Draft: Identifiable {
        let id = UUID()
        var exercise: Exercise
        var sets: Int
        var reps: Int
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(tr("Name, e.g. Monday: legs", "Naam, bijv. Maandag: benen"), text: $name)
                        .font(.headline)
                }
                .listRowBackground(Theme.card)

                Section(tr("Exercises", "Oefeningen")) {
                    ForEach($items) { $item in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(item.exercise.name).font(.headline)
                            if item.exercise.kind.isCardio {
                                Text(tr("Cardio", "Cardio")).font(.caption).foregroundStyle(Theme.muted)
                            } else {
                                HStack {
                                    Stepper("\(item.sets) sets", value: $item.sets, in: 1...10)
                                }
                                HStack {
                                    Stepper(tr("\(item.reps) reps", "\(item.reps) herhalingen"), value: $item.reps, in: 1...50)
                                }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .onMove { items.move(fromOffsets: $0, toOffset: $1) }
                    .onDelete { items.remove(atOffsets: $0) }

                    Button {
                        showPicker = true
                    } label: {
                        Label(tr("Add exercise", "Oefening toevoegen"), systemImage: "plus.circle.fill")
                            .foregroundStyle(Theme.accent)
                    }
                }
                .listRowBackground(Theme.card)

                if routine != nil {
                    Section {
                        Button(tr("Delete routine", "Schema verwijderen"), role: .destructive) { confirmDelete = true }
                    }
                    .listRowBackground(Theme.card)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .environment(\.editMode, .constant(.active))
            .navigationTitle(routine == nil ? tr("New routine", "Nieuw schema") : tr("Edit routine", "Schema wijzigen"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("Cancel", "Annuleren")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(tr("Save", "Bewaar")) { save() }
                        .fontWeight(.bold)
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || items.isEmpty)
                }
            }
            .sheet(isPresented: $showPicker) {
                ExercisePickerView { exercise in
                    items.append(Draft(exercise: exercise, sets: 3, reps: 10))
                }
            }
            .confirmationDialog(tr("Delete this routine?", "Schema verwijderen?"), isPresented: $confirmDelete, titleVisibility: .visible) {
                Button(tr("Delete", "Verwijderen"), role: .destructive) {
                    if let routine { context.delete(routine) }
                    try? context.save()
                    dismiss()
                }
            }
            .onAppear {
                guard !loaded else { return }
                loaded = true
                if let routine {
                    name = routine.name
                    items = routine.sortedItems.compactMap { item in
                        item.exercise.map { Draft(exercise: $0, sets: item.targetSets, reps: item.targetReps) }
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private func save() {
        let target = routine ?? {
            let new = Routine(name: name)
            context.insert(new)
            return new
        }()
        target.name = name.trimmingCharacters(in: .whitespaces)
        for old in target.items ?? [] { context.delete(old) }
        for (index, draft) in items.enumerated() {
            let item = RoutineItem(order: index, exercise: draft.exercise, sets: draft.sets, reps: draft.reps)
            item.routine = target
            context.insert(item)
        }
        try? context.save()
        dismiss()
    }
}
