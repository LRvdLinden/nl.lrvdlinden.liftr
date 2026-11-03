import SwiftData
import SwiftUI

/// Exercises (grouped by muscle) and routines.
struct LibraryView: View {
    @Environment(\.modelContext) private var context
    @Environment(WorkoutController.self) private var controller
    @Query(sort: \Exercise.name) private var exercises: [Exercise]
    @Query(sort: \Routine.createdAt) private var routines: [Routine]
    @State private var mode = 0
    @State private var search = ""
    @State private var showArchived = false
    @State private var newExercise = false
    @State private var newRoutine = false
    @State private var editingRoutine: Routine?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("", selection: $mode) {
                        Text(tr("Exercises", "Oefeningen")).tag(0)
                        Text(tr("Routines", "Schema's")).tag(1)
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }
                if mode == 0 { exerciseList } else { routineList }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(tr("Library", "Bibliotheek"))
            .searchable(text: $search, prompt: tr("Search", "Zoeken"))
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        if mode == 0 { newExercise = true } else { newRoutine = true }
                    } label: { Image(systemName: "plus") }
                    .accessibilityLabel(mode == 0 ? tr("New exercise", "Nieuwe oefening") : tr("New routine", "Nieuw schema"))
                }
            }
            .navigationDestination(for: Exercise.self) { ExerciseDetailView(exercise: $0) }
            .sheet(isPresented: $newExercise) { ExerciseEditView(exercise: nil) }
            .sheet(isPresented: $newRoutine) { RoutineEditView(routine: nil) }
            .sheet(item: $editingRoutine) { RoutineEditView(routine: $0) }
        }
    }

    @ViewBuilder
    private var exerciseList: some View {
        let visible = exercises.filter { (showArchived || !$0.isArchived) && (search.isEmpty || $0.name.localizedCaseInsensitiveContains(search)) }
        let favorites = visible.filter(\.isFavorite)
        if !favorites.isEmpty {
            Section(tr("Favourites", "Favorieten")) {
                ForEach(favorites) { row($0) }
            }
        }
        ForEach(Muscle.allCases) { muscle in
            let items = visible.filter { $0.muscle == muscle }
            if !items.isEmpty {
                Section(muscle.title) {
                    ForEach(items) { row($0) }
                }
            }
        }
        Section {
            Toggle(tr("Show archived", "Toon gearchiveerd"), isOn: $showArchived)
                .listRowBackground(Theme.card)
        }
    }

    private func row(_ exercise: Exercise) -> some View {
        NavigationLink(value: exercise) {
            ExerciseListRow(exercise: exercise, showsLastTime: true)
                .opacity(exercise.isArchived ? 0.5 : 1)
        }
        .listRowBackground(Theme.card)
        .swipeActions(edge: .leading) {
            Button {
                exercise.isFavorite.toggle()
            } label: {
                Label(tr("Favourite", "Favoriet"), systemImage: exercise.isFavorite ? "star.slash" : "star.fill")
            }
            .tint(Theme.accent)
        }
    }

    @ViewBuilder
    private var routineList: some View {
        let visible = routines.filter { search.isEmpty || $0.name.localizedCaseInsensitiveContains(search) }
        if visible.isEmpty {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text(tr("No routines yet", "Nog geen schema's")).font(.headline)
                    Text(tr("A routine is a fixed set of exercises, like “Monday: legs”. Start it with one tap and Liftr fills in what you did last time.",
                            "Een schema is een vaste set oefeningen, zoals “Maandag: benen”. Start hem met één tik en Liftr vult in wat je vorige keer deed."))
                        .font(.subheadline)
                        .foregroundStyle(Theme.muted)
                    Button(tr("New routine", "Nieuw schema")) { newRoutine = true }
                        .buttonStyle(LiftrButtonStyle())
                        .padding(.top, 6)
                }
                .padding(.vertical, 6)
                .listRowBackground(Theme.card)
            }
        } else {
            Section {
                ForEach(visible) { routine in
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(routine.name).font(.headline).foregroundStyle(.white)
                            Text(routine.sortedItems.compactMap { $0.exercise?.name }.joined(separator: ", "))
                                .font(.caption)
                                .foregroundStyle(Theme.muted)
                                .lineLimit(2)
                        }
                        Spacer()
                        Button {
                            controller.start(context, routine: routine)
                        } label: {
                            Image(systemName: "play.fill")
                                .foregroundStyle(.white)
                                .frame(width: 40, height: 40)
                                .background(Theme.accentGradient, in: Circle())
                        }
                        .buttonStyle(.plain)
                        .disabled(controller.active != nil)
                        .accessibilityLabel(tr("Start", "Start"))
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { editingRoutine = routine }
                    .listRowBackground(Theme.card)
                }
                .onDelete { offsets in
                    for i in offsets { context.delete(visible[i]) }
                    try? context.save()
                }
            }
        }
    }
}

/// Create or edit an exercise.
struct ExerciseEditView: View {
    let exercise: Exercise?
    var initialName = ""
    var initialMuscle: Muscle? = nil
    var onCreate: ((Exercise) -> Void)? = nil

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var muscle: Muscle = .chest
    @State private var kind: ExerciseKind = .machine
    @State private var notes = ""
    @State private var loaded = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(tr("Name", "Naam"), text: $name)
                    Picker(tr("Muscle group", "Spiergroep"), selection: $muscle) {
                        ForEach(Muscle.allCases) { Text($0.title).tag($0) }
                    }
                    Picker(tr("Type", "Soort"), selection: $kind) {
                        ForEach(ExerciseKind.allCases) { Text($0.title).tag($0) }
                    }
                }
                .listRowBackground(Theme.card)
                Section(tr("Notes", "Notities")) {
                    TextField(tr("E.g. seat position, grip", "Bijv. stoelstand, greep"), text: $notes, axis: .vertical)
                }
                .listRowBackground(Theme.card)
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(exercise == nil ? tr("New exercise", "Nieuwe oefening") : tr("Edit exercise", "Oefening wijzigen"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("Cancel", "Annuleren")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(tr("Save", "Bewaar")) { save() }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                        .fontWeight(.bold)
                }
            }
            .onAppear {
                guard !loaded else { return }
                loaded = true
                if let exercise {
                    name = exercise.name
                    muscle = exercise.muscle
                    kind = exercise.kind
                    notes = exercise.notes
                } else {
                    name = initialName
                    muscle = initialMuscle ?? .chest
                    if muscle == .cardio { kind = .cardio }
                }
            }
            .onChange(of: muscle) { _, m in if m == .cardio { kind = .cardio } }
        }
        .preferredColorScheme(.dark)
    }

    private func save() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        if let exercise {
            exercise.name = trimmed
            exercise.muscle = muscle
            exercise.kind = kind
            exercise.notes = notes
            try? context.save()
            dismiss()
        } else {
            let new = Exercise(name: trimmed, muscle: muscle, kind: kind)
            new.notes = notes
            context.insert(new)
            try? context.save()
            dismiss()
            onCreate?(new)
        }
    }
}

/// One exercise: records, recent sessions, edit and archive.
struct ExerciseDetailView: View {
    @Bindable var exercise: Exercise
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"
    @State private var editing = false

    private var unit: WeightUnit { WeightUnit(rawValue: unitRaw) ?? .kg }

    var body: some View {
        let points = WorkoutStats.history(for: exercise)
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 14) {
                    MuscleIcon(muscle: exercise.muscle, size: 54)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(exercise.name)
                            .font(Theme.display(28))
                            .textCase(.uppercase)
                            .foregroundStyle(.white)
                        Text("\(exercise.muscle.title) · \(exercise.kind.title)")
                            .font(.subheadline)
                            .foregroundStyle(Theme.muted)
                    }
                }
                if !exercise.notes.isEmpty {
                    Text(exercise.notes)
                        .foregroundStyle(Theme.muted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .liftrCard()
                }
                if let record = WorkoutStats.records(for: exercise), !exercise.kind.isCardio {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        StatTile(value: unit.format(record.heaviest), label: tr("Heaviest", "Zwaarste"), symbol: "trophy.fill")
                        StatTile(value: unit.format(record.bestOneRepMax), label: tr("Est. 1-rep max", "Geschat 1RM"), symbol: "bolt.fill")
                    }
                }
                SectionHeader(title: tr("Sessions", "Sessies"))
                if points.isEmpty {
                    Text(tr("Not done yet.", "Nog niet gedaan."))
                        .foregroundStyle(Theme.muted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .liftrCard()
                }
                ForEach((exercise.entries ?? [])
                    .filter { $0.workout?.endDate != nil }
                    .sorted { ($0.workout?.startDate ?? .distantPast) > ($1.workout?.startDate ?? .distantPast) }) { entry in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(LiftrFormat.day(entry.workout?.startDate ?? Date()))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(Theme.accent)
                            .textCase(.uppercase)
                        Text(WorkoutStats.summary(WorkoutStats.countedSets(entry), unit: unit, cardio: exercise.kind.isCardio))
                            .font(.body.monospacedDigit())
                            .foregroundStyle(.white)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .liftrCard(padding: 12, radius: 14)
                }
            }
            .padding(16)
            .frame(maxWidth: 760)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button { editing = true } label: { Label(tr("Edit", "Wijzig"), systemImage: "pencil") }
                    Button { exercise.isFavorite.toggle() } label: {
                        Label(exercise.isFavorite ? tr("Remove favourite", "Geen favoriet meer") : tr("Favourite", "Favoriet"),
                              systemImage: exercise.isFavorite ? "star.slash" : "star")
                    }
                    Button { exercise.isArchived.toggle() } label: {
                        Label(exercise.isArchived ? tr("Restore", "Terugzetten") : tr("Archive", "Archiveren"),
                              systemImage: "archivebox")
                    }
                } label: { Image(systemName: "ellipsis.circle") }
            }
        }
        .sheet(isPresented: $editing) { ExerciseEditView(exercise: exercise) }
    }
}
