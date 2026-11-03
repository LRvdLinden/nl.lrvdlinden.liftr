import SwiftData
import SwiftUI

/// A finished workout: everything that was done, editable afterwards.
struct WorkoutDetailView: View {
    @Bindable var workout: Workout
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Environment(WorkoutController.self) private var controller
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"
    @FocusState private var focus: FieldFocus?
    @State private var showPicker = false
    @State private var confirmDelete = false
    @State private var editing = false

    private var unit: WeightUnit { WeightUnit(rawValue: unitRaw) ?? .kg }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(LiftrFormat.day(workout.startDate))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.accent)
                        .textCase(.uppercase)
                    if editing {
                        TextField(tr("Name", "Naam"), text: $workout.title)
                            .font(Theme.display(32))
                            .textCase(.uppercase)
                    } else {
                        Text(workout.title)
                            .font(Theme.display(34))
                            .textCase(.uppercase)
                            .foregroundStyle(.white)
                    }
                }

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 12)], spacing: 12) {
                    StatTile(value: LiftrFormat.longDuration(workout.duration), label: tr("Duration", "Duur"), symbol: "clock")
                    StatTile(value: unit.volume(WorkoutStats.volume(workout)), label: "Volume", symbol: "scalemass.fill")
                    StatTile(value: "\(WorkoutStats.setCount(workout))", label: "Sets", symbol: "square.stack.3d.up.fill")
                    if workout.activeCalories > 0 {
                        StatTile(value: "\(Int(workout.activeCalories))", label: "kcal", symbol: "flame.fill")
                    }
                    if workout.averageHeartRate > 0 {
                        StatTile(value: "\(Int(workout.averageHeartRate))", label: tr("Avg. heart rate", "Gem. hartslag"), symbol: "heart.fill")
                    }
                }

                if editing {
                    DatePicker(tr("Start", "Start"), selection: $workout.startDate)
                        .liftrCard()
                    TextField(tr("Notes", "Notities"), text: $workout.notes, axis: .vertical)
                        .liftrCard()
                    ForEach(workout.sortedEntries) { entry in
                        EntryCard(entry: entry, workout: workout, unit: unit, focus: $focus) { set in
                            set.isDone.toggle()
                            set.completedAt = set.isDone ? (set.completedAt ?? workout.startDate) : nil
                        }
                    }
                    Button {
                        showPicker = true
                    } label: {
                        Label(tr("Add exercise", "Oefening toevoegen"), systemImage: "plus")
                    }
                    .buttonStyle(LiftrButtonStyle(filled: false))
                } else {
                    if !workout.notes.isEmpty {
                        Text(workout.notes)
                            .foregroundStyle(Theme.muted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .liftrCard()
                    }
                    ForEach(workout.sortedEntries) { entry in
                        readOnlyEntry(entry)
                    }
                }

                if workout.healthKitUUID != nil {
                    Label(tr("Saved in Apple Health", "Opgeslagen in Apple Gezondheid"), systemImage: "heart.fill")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.pink)
                }

                Button(role: .destructive) {
                    confirmDelete = true
                } label: {
                    Text(tr("Delete workout", "Training verwijderen"))
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                }
                .foregroundStyle(.red.opacity(0.85))
                .padding(.top, 8)
            }
            .padding(16)
            .frame(maxWidth: 760)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Theme.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button(editing ? tr("Done", "Klaar") : tr("Edit", "Wijzig")) {
                    if editing { try? context.save() }
                    editing.toggle()
                }
                .fontWeight(.bold)
            }
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(tr("Done", "Klaar")) { focus = nil }.fontWeight(.bold)
            }
        }
        .sheet(isPresented: $showPicker) {
            ExercisePickerView { exercise in
                controller.add(exercise, to: workout, context: context)
            }
        }
        .confirmationDialog(tr("Delete this workout?", "Training verwijderen?"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(tr("Delete", "Verwijderen"), role: .destructive) {
                let uuid = workout.healthKitUUID
                let target = workout
                // Leave the screen first, so nothing reads the deleted workout.
                dismiss()
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(450))
                    context.delete(target)
                    try? context.save()
                    if let uuid { await HealthKitManager.shared.deleteWorkout(uuid: uuid) }
                }
            }
        } message: {
            Text(tr("Also removed from Apple Health if Liftr saved it there.",
                    "Wordt ook uit Apple Gezondheid gehaald als Liftr hem daar heeft opgeslagen."))
        }
    }

    private func readOnlyEntry(_ entry: WorkoutEntry) -> some View {
        let cardio = entry.exercise?.kind.isCardio == true
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                MuscleIcon(muscle: entry.exercise?.muscle ?? .fullBody, size: 38)
                Text(entry.exercise?.name ?? tr("Exercise", "Oefening"))
                    .font(Theme.heading(19))
                    .textCase(.uppercase)
                    .foregroundStyle(.white)
                Spacer()
            }
            ForEach(Array(WorkoutStats.countedSets(entry).enumerated()), id: \.element.id) { index, set in
                HStack {
                    Text(set.kind.badge.isEmpty ? "\(index + 1)" : set.kind.badge)
                        .font(.subheadline.weight(.heavy))
                        .foregroundStyle(Theme.muted)
                        .frame(width: 28, alignment: .leading)
                    if cardio {
                        Text("\(set.durationSeconds / 60) min")
                        if set.distanceMeters > 0 {
                            Text("· \(LiftrFormat.decimal(set.distanceMeters / 1000, maxFraction: 2)) km")
                        }
                    } else {
                        Text("\(set.reps) × \(set.weight > 0 ? unit.format(set.weight) : tr("bodyweight", "lichaamsgewicht"))")
                    }
                    Spacer()
                    if set.isDone {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.success)
                    }
                }
                .font(.body.monospacedDigit())
                .foregroundStyle(.white)
            }
            if !entry.notes.isEmpty {
                Text(entry.notes).font(.footnote).foregroundStyle(Theme.muted)
            }
        }
        .liftrCard()
    }
}
