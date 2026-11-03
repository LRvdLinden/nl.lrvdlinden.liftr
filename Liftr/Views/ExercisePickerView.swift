import SwiftData
import SwiftUI

/// Choose an exercise: search, filter by muscle group, favourites first, or create a new one.
struct ExercisePickerView: View {
    var onPick: (Exercise) -> Void
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \Exercise.name) private var exercises: [Exercise]
    @State private var search = ""
    @State private var muscle: Muscle?
    @State private var creating = false

    private var filtered: [Exercise] {
        exercises
            .filter { !$0.isArchived }
            .filter { muscle == nil || $0.muscle == muscle }
            .filter { search.isEmpty || $0.name.localizedCaseInsensitiveContains(search) }
            .sorted { a, b in
                if a.isFavorite != b.isFavorite { return a.isFavorite }
                return a.name.localizedCompare(b.name) == .orderedAscending
            }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ScrollView(.horizontal) {
                        HStack(spacing: 8) {
                            chip(nil, title: tr("All", "Alles"))
                            ForEach(Muscle.allCases) { m in chip(m, title: m.title) }
                        }
                        .padding(.vertical, 4)
                    }
                    .scrollIndicators(.hidden)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
                }
                Section {
                    ForEach(filtered) { exercise in
                        Button {
                            onPick(exercise)
                            dismiss()
                        } label: {
                            ExerciseListRow(exercise: exercise, showsLastTime: true)
                        }
                        .listRowBackground(Theme.card)
                    }
                    if filtered.isEmpty {
                        Button {
                            creating = true
                        } label: {
                            Label(search.isEmpty ? tr("New exercise", "Nieuwe oefening")
                                                 : trf("Create “{1}”", "Maak “{1}” aan", search),
                                  systemImage: "plus.circle.fill")
                                .foregroundStyle(Theme.accent)
                        }
                        .listRowBackground(Theme.card)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .searchable(text: $search, prompt: tr("Search exercise", "Zoek oefening"))
            .navigationTitle(tr("Add exercise", "Oefening toevoegen"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("Cancel", "Annuleren")) { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { creating = true } label: { Image(systemName: "plus") }
                        .accessibilityLabel(tr("New exercise", "Nieuwe oefening"))
                }
            }
            .sheet(isPresented: $creating) {
                ExerciseEditView(exercise: nil, initialName: search, initialMuscle: muscle) { created in
                    onPick(created)
                    dismiss()
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private func chip(_ m: Muscle?, title: String) -> some View {
        let selected = muscle == m
        return Button {
            muscle = m
        } label: {
            Text(title)
                .font(.subheadline.weight(.bold))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.card), in: Capsule())
                .foregroundStyle(selected ? .white : Theme.muted)
        }
        .buttonStyle(.plain)
    }
}

struct ExerciseListRow: View {
    let exercise: Exercise
    /// Shows what you did last time (weight × reps per set) under the name.
    var showsLastTime = false
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"

    var body: some View {
        HStack(spacing: 12) {
            MuscleIcon(muscle: exercise.muscle, size: 38)
            VStack(alignment: .leading, spacing: 2) {
                Text(exercise.name)
                    .font(.headline)
                    .foregroundStyle(.white)
                Text("\(exercise.muscle.title) · \(exercise.kind.title)")
                    .font(.caption)
                    .foregroundStyle(Theme.muted)
                if showsLastTime, let last = WorkoutStats.previousSets(for: exercise, excluding: nil) {
                    Text("\(LiftrFormat.shortDay(last.date)): \(WorkoutStats.setsList(last.sets, unit: WeightUnit(rawValue: unitRaw) ?? .kg, cardio: exercise.kind.isCardio))")
                        .font(.caption.weight(.semibold).monospacedDigit())
                        .foregroundStyle(Theme.accent)
                        .lineLimit(2)
                }
            }
            Spacer()
            if exercise.isFavorite {
                Image(systemName: "star.fill").foregroundStyle(Theme.accent).font(.caption)
            }
        }
        .padding(.vertical, 2)
    }
}
