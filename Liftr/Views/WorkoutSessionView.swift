import SwiftData
import SwiftUI

/// The workout in progress: exercises with their sets, what you did last time, and the rest timer.
struct WorkoutSessionView: View {
    @Bindable var workout: Workout
    @Environment(\.modelContext) private var context
    @Environment(WorkoutController.self) private var controller
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"
    @FocusState private var focus: FieldFocus?
    @State private var showPicker = false
    @State private var confirmFinish = false
    @State private var confirmDiscard = false
    @State private var finishing = false
    @State private var editingTitle = false
    @State private var doneTick = 0
    @Query(filter: #Predicate<Workout> { $0.endDate != nil }, sort: \Workout.startDate, order: .reverse)
    private var finished: [Workout]

    /// The last few finished workouts that have exercises, newest first.
    private var recent: [Workout] {
        Array(finished.lazy.filter { $0.id != workout.id && !($0.entries ?? []).isEmpty }.prefix(6))
    }

    private var unit: WeightUnit { WeightUnit(rawValue: unitRaw) ?? .kg }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    if workout.sortedEntries.isEmpty, !recent.isEmpty {
                        PreviousWorkoutsCard(workout: workout, recent: recent, unit: unit)
                    }
                    ForEach(workout.sortedEntries) { entry in
                        EntryCard(entry: entry, workout: workout, unit: unit, focus: $focus, onToggle: { set in
                            controller.toggle(set, context: context)
                            if set.isDone { doneTick += 1 }
                        })
                    }
                    Button {
                        showPicker = true
                    } label: {
                        Label(tr("Add exercise", "Oefening toevoegen"), systemImage: "plus")
                    }
                    .buttonStyle(LiftrButtonStyle(filled: workout.sortedEntries.isEmpty))
                    .padding(.top, 4)

                    if !workout.sortedEntries.isEmpty, !recent.isEmpty {
                        PreviousWorkoutsCard(workout: workout, recent: recent, unit: unit)
                            .padding(.top, 8)
                    }

                    Button(role: .destructive) {
                        confirmDiscard = true
                    } label: {
                        Text(tr("Discard workout", "Training weggooien"))
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                    }
                    .padding(.top, 8)
                    .foregroundStyle(.red.opacity(0.85))
                }
                .padding(16)
                .padding(.bottom, controller.restEnd != nil ? 110 : 20)
                .frame(maxWidth: 760)
                .frame(maxWidth: .infinity)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Theme.background.ignoresSafeArea())
            .overlay(alignment: .bottom) {
                if controller.restEnd != nil {
                    RestTimerBar()
                        .padding(.horizontal, 12)
                        .padding(.bottom, 8)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(.spring(duration: 0.3), value: controller.restEnd)
            .sensoryFeedback(.success, trigger: doneTick)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        controller.isPresented = false
                    } label: {
                        Image(systemName: "chevron.down")
                            .font(.headline)
                    }
                    .accessibilityLabel(tr("Minimise", "Minimaliseren"))
                }
                ToolbarItem(placement: .principal) {
                    Text(workout.startDate, style: .timer)
                        .font(.headline.monospacedDigit())
                        .foregroundStyle(.white)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(tr("Finish", "Afronden")) { confirmFinish = true }
                        .font(.headline)
                        .foregroundStyle(Theme.accent)
                        .disabled(finishing)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    keyboardBar
                }
            }
            .toolbarBackground(Theme.background, for: .navigationBar)
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showPicker) {
                ExercisePickerView { exercise in
                    controller.add(exercise, to: workout, context: context)
                }
            }
            .confirmationDialog(tr("Finish this workout?", "Training afronden?"), isPresented: $confirmFinish, titleVisibility: .visible) {
                Button(tr("Finish and save", "Afronden en opslaan")) {
                    finishing = true
                    Task { await controller.finish(context) }
                }
                Button(tr("Keep going", "Doorgaan"), role: .cancel) {}
            } message: {
                Text(tr("Sets you didn't fill in are left out.", "Sets die je niet hebt ingevuld worden weggelaten."))
            }
            .confirmationDialog(tr("Discard this workout?", "Training weggooien?"), isPresented: $confirmDiscard, titleVisibility: .visible) {
                Button(tr("Discard", "Weggooien"), role: .destructive) { controller.discard(context) }
                Button(tr("Cancel", "Annuleren"), role: .cancel) {}
            }
            .alert(tr("Workout name", "Naam training"), isPresented: $editingTitle) {
                TextField(tr("Name", "Naam"), text: $workout.title)
                Button("OK") { try? context.save() }
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button {
                editingTitle = true
            } label: {
                HStack(spacing: 8) {
                    Text(workout.title)
                        .font(Theme.display(34))
                        .textCase(.uppercase)
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.leading)
                    Image(systemName: "pencil")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.muted)
                }
            }
            .buttonStyle(.plain)
            let sets = workout.sortedEntries.flatMap { $0.sortedSets }
            HStack(spacing: 14) {
                Label(trf("{1}/{2} sets", "{1}/{2} sets", sets.filter(\.isDone).count, sets.count), systemImage: "checkmark.circle")
                Label(unit.volume(WorkoutStats.volume(workout)), systemImage: "scalemass")
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Theme.muted)
        }
    }

    // MARK: Keyboard bar: −/+ for the field being edited

    @ViewBuilder
    private var keyboardBar: some View {
        if let focus, let set = findSet(focus.setID) {
            switch focus.field {
            case .weight:
                Button { adjust(set, weight: -unit.step) } label: { Text("−\(LiftrFormat.decimal(unit.step)) \(unit.symbol)") }
                Button { adjust(set, weight: unit.step) } label: { Text("+\(LiftrFormat.decimal(unit.step)) \(unit.symbol)") }
            case .reps:
                Button { set.reps = max(0, set.reps - 1) } label: { Text(tr("−1 rep", "−1 herh")) }
                Button { set.reps += 1 } label: { Text(tr("+1 rep", "+1 herh")) }
            case .minutes:
                Button { set.durationSeconds = max(0, set.durationSeconds - 60) } label: { Text("−1 min") }
                Button { set.durationSeconds += 60 } label: { Text("+1 min") }
            case .km:
                Button { set.distanceMeters = max(0, set.distanceMeters - 100) } label: { Text("−0,1 km") }
                Button { set.distanceMeters += 100 } label: { Text("+0,1 km") }
            }
        }
        Spacer()
        Button(tr("Done", "Klaar")) { focus = nil }
            .fontWeight(.bold)
    }

    private func adjust(_ set: SetRecord, weight delta: Double) {
        let shown = unit.fromKg(set.weight) + delta
        set.weight = unit.toKg(max(0, shown))
    }

    private func findSet(_ id: UUID) -> SetRecord? {
        for entry in workout.entries ?? [] {
            if let set = (entry.sets ?? []).first(where: { $0.id == id }) { return set }
        }
        return nil
    }
}

// MARK: - Exercise card

struct EntryCard: View {
    @Bindable var entry: WorkoutEntry
    let workout: Workout
    let unit: WeightUnit
    var focus: FocusState<FieldFocus?>.Binding
    var onToggle: (SetRecord) -> Void

    @Environment(\.modelContext) private var context
    @Environment(WorkoutController.self) private var controller
    @State private var showNotes = false

    private var isCardio: Bool { entry.exercise?.kind.isCardio == true }

    /// The same exercise in the last finished workout.
    private var previous: (date: Date, sets: [SetRecord])? {
        entry.exercise.flatMap { WorkoutStats.previousSets(for: $0, excluding: workout) }
    }

    var body: some View {
        let previous = self.previous
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                MuscleIcon(muscle: entry.exercise?.muscle ?? .fullBody, size: 42)
                VStack(alignment: .leading, spacing: 3) {
                    Text(entry.exercise?.name ?? tr("Exercise", "Oefening"))
                        .font(Theme.heading(20))
                        .textCase(.uppercase)
                        .foregroundStyle(.white)
                    if let previous {
                        Text("\(tr("Last time", "Vorige keer")) (\(LiftrFormat.shortDay(previous.date))): \(WorkoutStats.setsList(previous.sets, unit: unit, cardio: isCardio))")
                            .font(.caption)
                            .foregroundStyle(Theme.accent)
                    } else {
                        Text(tr("First time — set your starting point", "Eerste keer — bepaal je startpunt"))
                            .font(.caption)
                            .foregroundStyle(Theme.muted)
                    }
                }
                Spacer()
                Menu {
                    Button { showNotes = true } label: { Label(tr("Note", "Notitie"), systemImage: "note.text") }
                    Button(role: .destructive) {
                        context.delete(entry)
                        try? context.save()
                        controller.updateActivity()
                    } label: { Label(tr("Remove exercise", "Oefening verwijderen"), systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.headline)
                        .foregroundStyle(Theme.muted)
                        .frame(width: 36, height: 36)
                }
            }

            if !entry.notes.isEmpty {
                Text(entry.notes)
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
                    .padding(.horizontal, 4)
            }

            // Column titles
            HStack(spacing: 8) {
                Text(tr("SET", "SET")).frame(width: 34)
                if previous != nil {
                    Text(tr("LAST", "VORIGE")).frame(width: SetRow.previousWidth)
                }
                if isCardio {
                    Text(tr("MIN", "MIN")).frame(maxWidth: .infinity)
                    Text(tr("KM", "KM")).frame(maxWidth: .infinity)
                } else {
                    Text(unit.symbol.uppercased()).frame(maxWidth: .infinity)
                    Text(tr("REPS", "HERH")).frame(maxWidth: .infinity)
                }
                Image(systemName: "checkmark").frame(width: 44)
            }
            .font(.caption2.weight(.heavy))
            .foregroundStyle(Theme.muted)

            ForEach(Array(entry.sortedSets.enumerated()), id: \.element.id) { index, set in
                SetRow(set: set, number: index + 1, unit: unit, cardio: isCardio, focus: focus,
                       previous: previous.map { $0.sets[safe: index] }, onToggle: {
                    onToggle(set)
                })
                .contextMenu {
                    ForEach(SetKind.allCases) { kind in
                        Button {
                            set.kind = kind
                        } label: {
                            Label(kind.title, systemImage: set.kind == kind ? "checkmark" : "")
                        }
                    }
                    Divider()
                    Button(role: .destructive) {
                        context.delete(set)
                        try? context.save()
                        controller.updateActivity()
                    } label: { Label(tr("Delete set", "Set verwijderen"), systemImage: "trash") }
                }
            }

            Button {
                controller.addSet(to: entry, context: context)
            } label: {
                Label(tr("Add set", "Set toevoegen"), systemImage: "plus")
                    .font(.subheadline.weight(.bold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Theme.cardRaised, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
        }
        .liftrCard()
        .alert(tr("Note", "Notitie"), isPresented: $showNotes) {
            TextField(tr("E.g. seat position 4", "Bijv. stoelstand 4"), text: $entry.notes)
            Button("OK") { try? context.save() }
        }
    }
}

struct SetRow: View {
    static let previousWidth: CGFloat = 64

    @Bindable var set: SetRecord
    let number: Int
    let unit: WeightUnit
    let cardio: Bool
    var focus: FocusState<FieldFocus?>.Binding
    /// nil: the exercise was never done before (no column). `.some(nil)`: last time had fewer sets.
    var previous: SetRecord?? = nil
    var onToggle: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Text(set.kind.badge.isEmpty ? "\(number)" : set.kind.badge)
                .font(.headline.weight(.heavy))
                .foregroundStyle(set.kind == .warmup ? Color.yellow : (set.kind == .work ? .white : Theme.accent))
                .frame(width: 34, height: 44)
                .background(Theme.cardRaised, in: RoundedRectangle(cornerRadius: 10, style: .continuous))

            if let previous {
                Button {
                    if let old = previous { copy(old) }
                } label: {
                    Text(previous.map(previousText) ?? "–")
                        .font(.caption.weight(.semibold).monospacedDigit())
                        .foregroundStyle(Theme.muted)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .frame(width: Self.previousWidth, height: 44)
                }
                .buttonStyle(.plain)
                .disabled(previous == nil)
                .accessibilityLabel(previous.map { trf("Last time: {1}. Tap to use", "Vorige keer: {1}. Tik om over te nemen", previousText($0)) } ?? "–")
            }

            if cardio {
                NumberField(value: minutes, placeholder: "0", decimals: true, focus: focus,
                            id: FieldFocus(setID: set.id, field: .minutes))
                    .modifier(FieldBox(done: set.isDone))
                NumberField(value: km, placeholder: "0", decimals: true, focus: focus,
                            id: FieldFocus(setID: set.id, field: .km))
                    .modifier(FieldBox(done: set.isDone))
            } else {
                NumberField(value: weight, placeholder: "0", decimals: true, focus: focus,
                            id: FieldFocus(setID: set.id, field: .weight))
                    .modifier(FieldBox(done: set.isDone))
                NumberField(value: reps, placeholder: "0", decimals: false, focus: focus,
                            id: FieldFocus(setID: set.id, field: .reps))
                    .modifier(FieldBox(done: set.isDone))
            }

            Button(action: onToggle) {
                Image(systemName: "checkmark")
                    .font(.system(size: 18, weight: .heavy))
                    .foregroundStyle(set.isDone ? .white : Theme.muted)
                    .frame(width: 44, height: 44)
                    .background(set.isDone ? AnyShapeStyle(Theme.success) : AnyShapeStyle(Theme.cardRaised),
                                in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(set.isDone ? tr("Set done", "Set gedaan") : tr("Mark set done", "Set afvinken"))
        }
    }

    /// "40×12", or "20′ · 2,5" for cardio.
    private func previousText(_ old: SetRecord) -> String {
        if cardio {
            let minutes = "\(old.durationSeconds / 60)′"
            return old.distanceMeters > 0 ? minutes + " · " + LiftrFormat.decimal(old.distanceMeters / 1000, maxFraction: 1) : minutes
        }
        return old.weight > 0 ? "\(unit.number(old.weight))×\(old.reps)" : "\(old.reps)×"
    }

    private func copy(_ old: SetRecord) {
        set.weight = old.weight
        set.reps = old.reps
        set.durationSeconds = old.durationSeconds
        set.distanceMeters = old.distanceMeters
    }

    private var weight: Binding<Double> {
        Binding(get: { (unit.fromKg(set.weight) * 100).rounded() / 100 },
                set: { set.weight = unit.toKg($0) })
    }

    private var reps: Binding<Double> {
        Binding(get: { Double(set.reps) }, set: { set.reps = max(0, Int($0)) })
    }

    private var minutes: Binding<Double> {
        Binding(get: { Double(set.durationSeconds) / 60 }, set: { set.durationSeconds = Int(($0 * 60).rounded()) })
    }

    private var km: Binding<Double> {
        Binding(get: { set.distanceMeters / 1000 }, set: { set.distanceMeters = $0 * 1000 })
    }
}

private struct FieldBox: ViewModifier {
    let done: Bool

    func body(content: Content) -> some View {
        content
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .background(done ? Theme.success.opacity(0.18) : Theme.cardRaised,
                        in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

// MARK: - Rest timer

struct RestTimerBar: View {
    @Environment(WorkoutController.self) private var controller
    @State private var buzzed = 0

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.5)) { timeline in
            let remaining = max(0, (controller.restEnd ?? timeline.date).timeIntervalSince(timeline.date))
            let progress = controller.restTotal > 0 ? remaining / controller.restTotal : 0
            HStack(spacing: 14) {
                ZStack {
                    Circle().stroke(Theme.cardRaised, lineWidth: 5)
                    Circle()
                        .trim(from: 0, to: progress)
                        .stroke(Theme.accent, style: StrokeStyle(lineWidth: 5, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    Image(systemName: "timer").font(.subheadline.weight(.bold)).foregroundStyle(Theme.accent)
                }
                .frame(width: 44, height: 44)

                VStack(alignment: .leading, spacing: 0) {
                    Text(tr("REST", "RUST"))
                        .font(.caption2.weight(.heavy))
                        .foregroundStyle(Theme.muted)
                    Text(LiftrFormat.duration(remaining))
                        .font(Theme.display(30).monospacedDigit())
                        .foregroundStyle(remaining > 0 ? .white : Theme.success)
                }
                Spacer()
                Button("−15") { controller.addRest(-15) }
                    .buttonStyle(RestButtonStyle())
                Button("+15") { controller.addRest(15) }
                    .buttonStyle(RestButtonStyle())
                Button {
                    controller.stopRest()
                } label: {
                    Image(systemName: "forward.end.fill")
                }
                .buttonStyle(RestButtonStyle(accent: true))
                .accessibilityLabel(tr("Skip rest", "Rust overslaan"))
            }
            .padding(12)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .background(Theme.card.opacity(0.9), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .onChange(of: remaining <= 0) { _, over in
                if over {
                    buzzed += 1
                    Task {
                        try? await Task.sleep(for: .seconds(2))
                        if let end = controller.restEnd, end <= Date() { controller.stopRest() }
                    }
                }
            }
        }
        .sensoryFeedback(.warning, trigger: buzzed)
    }
}

private struct RestButtonStyle: ButtonStyle {
    var accent = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.heavy))
            .foregroundStyle(.white)
            .frame(minWidth: 44, minHeight: 40)
            .background(accent ? Theme.accent : Theme.cardRaised, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

// MARK: - Previous workouts

/// What you did in your last workouts, per exercise with every set (weight × reps).
/// Tap an exercise to add it with those sets filled in, or add them all.
struct PreviousWorkoutsCard: View {
    let workout: Workout
    let recent: [Workout]
    let unit: WeightUnit

    @Environment(\.modelContext) private var context
    @Environment(WorkoutController.self) private var controller
    @State private var selectedID: UUID?

    private var shown: Workout? { recent.first { $0.id == selectedID } ?? recent.first }
    private var addedExercises: Set<UUID> { Set(workout.sortedEntries.compactMap { $0.exercise?.id }) }

    var body: some View {
        if let shown {
            let entries = shown.sortedEntries.filter { $0.exercise != nil && !WorkoutStats.countedSets($0).isEmpty }
            let missing = entries.filter { !addedExercises.contains($0.exercise!.id) }
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text(tr("Previous workouts", "Vorige trainingen"))
                        .font(Theme.heading(18))
                        .textCase(.uppercase)
                        .foregroundStyle(.white)
                    Spacer()
                    if missing.count > 1 {
                        Button(tr("Add all", "Alles toevoegen")) {
                            for entry in missing {
                                if let exercise = entry.exercise { _ = controller.add(exercise, to: workout, context: context) }
                            }
                        }
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.accent)
                    }
                }

                if recent.count > 1 {
                    ScrollView(.horizontal) {
                        HStack(spacing: 8) {
                            ForEach(recent) { w in
                                let on = w.id == shown.id
                                Button {
                                    selectedID = w.id
                                } label: {
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(LiftrFormat.shortDay(w.startDate)).font(.caption.weight(.heavy))
                                        Text(w.title).font(.caption2).lineLimit(1)
                                    }
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 7)
                                    .foregroundStyle(on ? .white : Theme.muted)
                                    .background(on ? AnyShapeStyle(Theme.accent) : AnyShapeStyle(Theme.cardRaised),
                                                in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .scrollIndicators(.hidden)
                }

                ForEach(entries) { entry in
                    let exercise = entry.exercise!
                    let added = addedExercises.contains(exercise.id)
                    Button {
                        guard !added else { return }
                        _ = controller.add(exercise, to: workout, context: context)
                    } label: {
                        HStack(alignment: .top, spacing: 10) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(exercise.name)
                                    .font(.subheadline.weight(.bold))
                                    .foregroundStyle(.white)
                                Text(setsText(entry))
                                    .font(.footnote.weight(.semibold).monospacedDigit())
                                    .foregroundStyle(Theme.accent)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            Spacer(minLength: 4)
                            Image(systemName: added ? "checkmark.circle.fill" : "plus.circle.fill")
                                .font(.title3)
                                .foregroundStyle(added ? Theme.success : Theme.accent)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.cardRaised, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .opacity(added ? 0.55 : 1)
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint(added ? "" : tr("Adds this exercise with these sets", "Voegt deze oefening toe met deze sets"))
                }
            }
            .liftrCard()
        }
    }

    private func setsText(_ entry: WorkoutEntry) -> String {
        WorkoutStats.setsList(WorkoutStats.countedSets(entry), unit: unit, cardio: entry.exercise?.kind.isCardio == true)
    }
}
