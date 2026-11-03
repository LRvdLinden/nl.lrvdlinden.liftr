import SwiftData
import SwiftUI

@main
struct LiftrWatchApp: App {
    let container = LiftrStore.makeContainer()

    var body: some Scene {
        WindowGroup {
            WatchRootView()
                .tint(Theme.accent)
        }
        .modelContainer(container)
    }
}

struct WatchRootView: View {
    @Environment(\.modelContext) private var context
    @State private var manager = WatchWorkoutManager.shared
    @Query(sort: \Routine.createdAt) private var routines: [Routine]
    @Query(sort: \Workout.startDate, order: .reverse) private var workouts: [Workout]
    @Query(sort: \GymPass.createdAt) private var passes: [GymPass]
    @State private var starting = false

    var body: some View {
        NavigationStack {
            if manager.workout != nil {
                WatchWorkoutView()
            } else {
                List {
                    Button {
                        start(nil)
                    } label: {
                        HStack {
                            Image(systemName: "play.fill")
                            Text(tr("START", "START"))
                                .font(Theme.heading(20))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                    }
                    .listRowBackground(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Theme.accentGradient))
                    .disabled(starting)

                    if let pass = passes.first {
                        NavigationLink {
                            WatchPassView(pass: pass)
                        } label: {
                            HStack {
                                Image(systemName: "qrcode")
                                    .foregroundStyle(Color.ink(on: pass.colorHex))
                                    .frame(width: 28, height: 28)
                                    .background(Color(hex: pass.colorHex), in: RoundedRectangle(cornerRadius: 7))
                                Text(pass.displayName)
                            }
                        }
                    }

                    if !routines.isEmpty {
                        Section(tr("Routines", "Schema's")) {
                            ForEach(routines) { routine in
                                Button {
                                    start(routine)
                                } label: {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(routine.name).font(.headline)
                                        Text(trf("{1} exercises", "{1} oefeningen", routine.sortedItems.count))
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    }

                    let week = WorkoutStats.workoutsThisWeek(workouts).count
                    Section {
                        HStack {
                            Image(systemName: "flame.fill").foregroundStyle(Theme.accent)
                            Text(trf("{1} this week", "{1} deze week", week))
                            Spacer()
                            Text(trf("{1} wk", "{1} wk", WorkoutStats.weekStreak(workouts)))
                                .foregroundStyle(.secondary)
                        }
                        .font(.footnote)
                    }
                }
                .navigationTitle("Liftr")
            }
        }
        .sheet(item: $manager.finished) { workout in
            WatchSummaryView(workout: workout)
        }
        .task {
            ExerciseLibrary.prepare(context)
        }
    }

    private func start(_ routine: Routine?) {
        starting = true
        Task {
            await manager.start(context: context, routine: routine)
            starting = false
        }
    }
}

struct WatchSummaryView: View {
    let workout: Workout
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 40))
                    .foregroundStyle(Theme.accent)
                Text(tr("Well done!", "Goed gedaan!"))
                    .font(Theme.heading(22))
                    .textCase(.uppercase)
                Text(LiftrFormat.longDuration(workout.duration)).font(.headline)
                Text(trf("{1} sets", "{1} sets", WorkoutStats.setCount(workout)) + " · " + WeightUnit.current.volume(WorkoutStats.volume(workout)))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                if workout.activeCalories > 0 {
                    Text("\(Int(workout.activeCalories)) kcal").font(.footnote).foregroundStyle(.secondary)
                }
                Button(tr("Close", "Sluiten")) { dismiss() }
                    .tint(Theme.accent)
                    .padding(.top, 6)
            }
        }
    }
}

/// The gym pass on the wrist: white background, code as large as the screen allows.
struct WatchPassView: View {
    let pass: GymPass

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()
            VStack(spacing: 4) {
                BarcodeView(value: pass.code, format: pass.format, codeImage: pass.codeImage)
                    .padding(pass.format.isLinear ? 2 : 6)
                if pass.format.isLinear {
                    Text(pass.code)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundStyle(.black)
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                }
            }
        }
        .navigationTitle(pass.displayName)
    }
}
