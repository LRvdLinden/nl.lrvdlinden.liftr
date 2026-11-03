import SwiftData
import SwiftUI
import WatchKit

/// During a workout: swipe between live metrics, the set logger and controls.
struct WatchWorkoutView: View {
    @State private var manager = WatchWorkoutManager.shared
    @State private var page = 1

    var body: some View {
        TabView(selection: $page) {
            WatchControlsView().tag(0)
            WatchLogView().tag(1)
            WatchMetricsView().tag(2)
        }
        .tabViewStyle(.page)
        .navigationBarBackButtonHidden()
        .overlay {
            if manager.restEnd != nil {
                WatchRestView()
            }
        }
    }
}

struct WatchMetricsView: View {
    @State private var manager = WatchWorkoutManager.shared

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            VStack(alignment: .leading, spacing: 4) {
                Text(LiftrFormat.duration(context.date.timeIntervalSince(manager.startDate ?? context.date)))
                    .font(.system(size: 40, weight: .heavy, design: .rounded).monospacedDigit())
                    .foregroundStyle(Theme.accent)
                Label("\(Int(manager.heartRate)) BPM", systemImage: "heart.fill")
                    .font(.title3.monospacedDigit().weight(.semibold))
                    .foregroundStyle(.pink)
                Label("\(Int(manager.activeCalories)) kcal", systemImage: "flame.fill")
                    .font(.title3.monospacedDigit().weight(.semibold))
                if let workout = manager.workout {
                    Label("\(WorkoutStats.setCount(workout)) sets", systemImage: "square.stack.3d.up.fill")
                        .font(.title3.monospacedDigit().weight(.semibold))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 4)
        }
    }
}

struct WatchControlsView: View {
    @Environment(\.modelContext) private var context
    @State private var manager = WatchWorkoutManager.shared
    @State private var ending = false
    @State private var confirmDiscard = false

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                Button {
                    manager.togglePause()
                } label: {
                    VStack {
                        Image(systemName: manager.isPaused ? "play.fill" : "pause.fill").font(.title2)
                        Text(manager.isPaused ? tr("Resume", "Hervat") : tr("Pause", "Pauze")).font(.caption2)
                    }
                }
                .tint(.yellow)
                Button {
                    ending = true
                    Task {
                        await manager.end(context: context)
                        ending = false
                    }
                } label: {
                    VStack {
                        Image(systemName: "checkmark").font(.title2)
                        Text(tr("Finish", "Klaar")).font(.caption2)
                    }
                }
                .tint(Theme.accent)
                .disabled(ending)
            }
            Button(role: .destructive) {
                confirmDiscard = true
            } label: {
                Text(tr("Discard", "Weggooien")).font(.footnote)
            }
        }
        .confirmationDialog(tr("Discard this workout?", "Training weggooien?"), isPresented: $confirmDiscard) {
            Button(tr("Discard", "Weggooien"), role: .destructive) { manager.discard(context: context) }
        }
    }
}

/// Current exercise and set: rows of reps and weight, the Digital Crown changes the selected value.
struct WatchLogView: View {
    @Environment(\.modelContext) private var context
    @State private var manager = WatchWorkoutManager.shared
    @State private var entryID: UUID?
    @State private var showPicker = false

    private var entries: [WorkoutEntry] { manager.workout?.sortedEntries ?? [] }

    private var entry: WorkoutEntry? {
        entries.first { $0.id == entryID } ?? entries.first { e in e.sortedSets.contains { !$0.isDone } } ?? entries.last
    }

    var body: some View {
        Group {
            if let entry {
                WatchSetEditor(entry: entry, onPickExercise: { showPicker = true })
                    .id(entry.id)
            } else {
                VStack(spacing: 10) {
                    Text(tr("Choose your first exercise", "Kies je eerste oefening"))
                        .font(.headline)
                        .multilineTextAlignment(.center)
                    Button {
                        showPicker = true
                    } label: {
                        Label(tr("Exercise", "Oefening"), systemImage: "plus")
                    }
                    .tint(Theme.accent)
                }
            }
        }
        .sheet(isPresented: $showPicker) {
            WatchExercisePicker(entries: entries) { picked in
                if let existing = entries.first(where: { $0.id == picked }) {
                    entryID = existing.id
                }
            } onNew: { exercise in
                entryID = manager.add(exercise, context: context)?.id
            }
        }
    }
}

struct WatchSetEditor: View {
    let entry: WorkoutEntry
    var onPickExercise: () -> Void
    @Environment(\.modelContext) private var context
    @State private var manager = WatchWorkoutManager.shared
    @State private var reps: Double = 10
    @State private var weight: Double = 0
    @FocusState private var focused: Field?
    @State private var currentSetID: UUID?

    enum Field { case reps, weight }

    private var unit: WeightUnit { WeightUnit.current }
    private var currentSet: SetRecord? {
        entry.sortedSets.first { $0.id == currentSetID } ?? entry.sortedSets.first { !$0.isDone }
    }

    var body: some View {
        let sets = entry.sortedSets
        let doneCount = sets.filter(\.isDone).count
        VStack(spacing: 6) {
            Button(action: onPickExercise) {
                HStack(spacing: 4) {
                    Text(entry.exercise?.name ?? tr("Exercise", "Oefening"))
                        .font(Theme.heading(16))
                        .textCase(.uppercase)
                        .lineLimit(1)
                    Image(systemName: "chevron.down").font(.caption2)
                }
                .foregroundStyle(Theme.accent)
            }
            .buttonStyle(.plain)

            if let set = currentSet {
                Text(tr("Set \(set.order + 1) of \(sets.count)", "Set \(set.order + 1) van \(sets.count)"))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                HStack(spacing: 6) {
                    valueBox(title: unit.symbol.uppercased(), value: LiftrFormat.decimal(weight), field: .weight)
                        .focusable()
                        .focused($focused, equals: .weight)
                        .digitalCrownRotation($weight, from: 0, through: 500, by: unit.step,
                                              sensitivity: .low, isContinuous: false, isHapticFeedbackEnabled: true)
                    valueBox(title: tr("REPS", "HERH"), value: "\(Int(reps))", field: .reps)
                        .focusable()
                        .focused($focused, equals: .reps)
                        .digitalCrownRotation($reps, from: 0, through: 100, by: 1,
                                              sensitivity: .medium, isContinuous: false, isHapticFeedbackEnabled: true)
                }
                Button {
                    set.reps = Int(reps)
                    set.weight = unit.toKg(weight)
                    manager.complete(set, context: context)
                    currentSetID = nil
                    load()
                } label: {
                    Label(tr("Set done", "Set klaar"), systemImage: "checkmark")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                }
                .tint(Theme.accent)
            } else {
                Text(tr("\(doneCount) sets done", "\(doneCount) sets gedaan"))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button {
                    let set = manager.addSet(to: entry, context: context)
                    currentSetID = set.id
                    load()
                } label: {
                    Label(tr("Extra set", "Extra set"), systemImage: "plus")
                }
                .tint(Theme.accent)
                Button(action: onPickExercise) {
                    Label(tr("Next exercise", "Volgende oefening"), systemImage: "arrow.right")
                }
            }
        }
        .onAppear {
            load()
            focused = .weight
        }
    }

    private func valueBox(title: String, value: String, field: Field) -> some View {
        VStack(spacing: 0) {
            Text(title).font(.system(size: 10, weight: .heavy)).foregroundStyle(.secondary)
            Text(value)
                .font(.system(size: 26, weight: .heavy, design: .rounded).monospacedDigit())
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 10, style: .continuous)
            .strokeBorder(focused == field ? Theme.accent : Color.gray.opacity(0.4), lineWidth: 2))
        .onTapGesture { focused = field }
    }

    private func load() {
        guard let set = currentSet else { return }
        reps = Double(set.reps)
        weight = (unit.fromKg(set.weight) * 10).rounded() / 10
    }
}

struct WatchExercisePicker: View {
    let entries: [WorkoutEntry]
    var onPickEntry: (UUID) -> Void
    var onNew: (Exercise) -> Void
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \Exercise.name) private var exercises: [Exercise]

    var body: some View {
        List {
            if !entries.isEmpty {
                Section(tr("In this workout", "In deze training")) {
                    ForEach(entries) { entry in
                        Button {
                            onPickEntry(entry.id)
                            dismiss()
                        } label: {
                            HStack {
                                Text(entry.exercise?.name ?? "–")
                                Spacer()
                                if entry.sortedSets.allSatisfy(\.isDone) {
                                    Image(systemName: "checkmark").foregroundStyle(Theme.accent)
                                }
                            }
                        }
                    }
                }
            }
            ForEach(Muscle.allCases) { muscle in
                let items = exercises.filter { $0.muscle == muscle && !$0.isArchived }
                    .sorted { $0.isFavorite && !$1.isFavorite }
                if !items.isEmpty {
                    Section(muscle.title) {
                        ForEach(items) { exercise in
                            Button {
                                onNew(exercise)
                                dismiss()
                            } label: {
                                Text(exercise.name)
                            }
                        }
                    }
                }
            }
        }
    }
}

struct WatchRestView: View {
    @State private var manager = WatchWorkoutManager.shared

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let remaining = max(0, (manager.restEnd ?? context.date).timeIntervalSince(context.date))
            VStack(spacing: 6) {
                Text(tr("REST", "RUST"))
                    .font(.caption.weight(.heavy))
                    .foregroundStyle(.secondary)
                Text(LiftrFormat.duration(remaining))
                    .font(.system(size: 44, weight: .heavy, design: .rounded).monospacedDigit())
                    .foregroundStyle(Theme.accent)
                Button(tr("Skip", "Overslaan")) { manager.stopRest() }
                    .tint(Theme.accent)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(.black.opacity(0.92))
        }
    }
}
