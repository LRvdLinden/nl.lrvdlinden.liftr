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

                    if !routines.isEmpty {
                        Section(tr("Routines", "Schema's")) {
                            ForEach(routines) { routine in
                                Button {
                                    start(routine)
                                } label: {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(routine.name).font(.headline)
                                        Text(tr("\(routine.sortedItems.count) exercises", "\(routine.sortedItems.count) oefeningen"))
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
                            Text(tr("\(week) this week", "\(week) deze week"))
                            Spacer()
                            Text(tr("\(WorkoutStats.weekStreak(workouts)) wk", "\(WorkoutStats.weekStreak(workouts)) wk"))
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
                Text("\(WorkoutStats.setCount(workout)) sets · \(WeightUnit.current.volume(WorkoutStats.volume(workout)))")
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
