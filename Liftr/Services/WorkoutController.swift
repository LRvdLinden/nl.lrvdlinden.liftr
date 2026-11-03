import ActivityKit
import Foundation
import Observation
import SwiftData
import UserNotifications
import WidgetKit

/// Runs the workout in progress on iPhone/iPad: creating it, the rest timer, the Live Activity
/// and finishing (saving to Health). The workout itself is stored right away, so nothing is lost
/// when the app is closed mid-workout.
@MainActor
@Observable
final class WorkoutController {
    static let shared = WorkoutController()

    var active: Workout?
    var isPresented = false
    var restEnd: Date?
    var restTotal: TimeInterval = 90
    /// Summary shown after finishing.
    var finished: Workout?

    private var activity: Activity<LiftrActivityAttributes>?

    var defaultRest: Int {
        let value = UserDefaults.standard.integer(forKey: "rest.default")
        return value > 0 ? value : 90
    }

    var autoRest: Bool {
        UserDefaults.standard.object(forKey: "rest.auto") as? Bool ?? true
    }

    // MARK: Start / resume

    /// Picks up a workout that was still running when the app was closed.
    func restore(_ context: ModelContext) {
        guard active == nil else { return }
        let descriptor = FetchDescriptor<Workout>(sortBy: [SortDescriptor(\.startDate, order: .reverse)])
        let running = ((try? context.fetch(descriptor)) ?? []).filter { $0.endDate == nil && $0.source != "watch" }
        if let workout = running.first {
            // Older than 12 hours: close it at its last completed set.
            if Date().timeIntervalSince(workout.startDate) > 12 * 3600 {
                let last = workout.sortedEntries.flatMap { $0.sortedSets }.compactMap(\.completedAt).max()
                workout.endDate = last ?? workout.startDate.addingTimeInterval(3600)
                try? context.save()
            } else {
                active = workout
            }
        }
    }

    func start(_ context: ModelContext, routine: Routine? = nil) {
        if let active {
            self.active = active
            isPresented = true
            return
        }
        let title = routine?.name ?? defaultTitle()
        let workout = Workout(title: title)
        context.insert(workout)
        if let routine {
            for (index, item) in routine.sortedItems.enumerated() {
                guard let exercise = item.exercise else { continue }
                let entry = WorkoutEntry(order: index, exercise: exercise)
                entry.workout = workout
                context.insert(entry)
                let previous = WorkoutStats.previousSets(for: exercise, excluding: workout)?.sets
                    .filter { $0.kind != .warmup }
                for n in 0..<max(1, item.targetSets) {
                    let template = previous.flatMap { n < $0.count ? $0[n] : $0.last }
                    let set = SetRecord(order: n,
                                        reps: template?.reps ?? item.targetReps,
                                        weight: template?.weight ?? item.targetWeight)
                    if exercise.kind.isCardio {
                        set.durationSeconds = template?.durationSeconds ?? 15 * 60
                        set.distanceMeters = template?.distanceMeters ?? 0
                    }
                    set.entry = entry
                    context.insert(set)
                }
            }
        }
        try? context.save()
        active = workout
        isPresented = true
        startActivity()
    }

    private func defaultTitle() -> String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5..<12: return tr("Morning workout", "Ochtendtraining")
        case 12..<18: return tr("Afternoon workout", "Middagtraining")
        default: return tr("Evening workout", "Avondtraining")
        }
    }

    /// Adds an exercise, pre-filled with what was done last time.
    @discardableResult
    func add(_ exercise: Exercise, to workout: Workout, context: ModelContext) -> WorkoutEntry {
        let order = (workout.entries ?? []).map(\.order).max().map { $0 + 1 } ?? 0
        let entry = WorkoutEntry(order: order, exercise: exercise)
        entry.workout = workout
        context.insert(entry)
        let previous = WorkoutStats.previousSets(for: exercise, excluding: workout)?.sets ?? []
        if previous.isEmpty {
            let set = SetRecord(order: 0, reps: exercise.kind.isCardio ? 0 : 10, weight: 0)
            if exercise.kind.isCardio { set.durationSeconds = 15 * 60 }
            set.entry = entry
            context.insert(set)
        } else {
            for (n, old) in previous.enumerated() {
                let set = SetRecord(order: n, reps: old.reps, weight: old.weight, kind: old.kind)
                set.durationSeconds = old.durationSeconds
                set.distanceMeters = old.distanceMeters
                set.entry = entry
                context.insert(set)
            }
        }
        try? context.save()
        updateActivity()
        return entry
    }

    func addSet(to entry: WorkoutEntry, context: ModelContext) {
        let last = entry.sortedSets.last
        let set = SetRecord(order: (last?.order ?? -1) + 1, reps: last?.reps ?? 10, weight: last?.weight ?? 0)
        set.durationSeconds = last?.durationSeconds ?? 0
        set.distanceMeters = last?.distanceMeters ?? 0
        set.entry = entry
        context.insert(set)
        try? context.save()
        updateActivity()
    }

    func toggle(_ set: SetRecord, context: ModelContext) {
        set.isDone.toggle()
        set.completedAt = set.isDone ? Date() : nil
        try? context.save()
        if set.isDone, autoRest, set.entry?.exercise?.kind.isCardio != true {
            startRest(seconds: defaultRest)
        } else {
            updateActivity()
        }
    }

    // MARK: Rest timer

    func startRest(seconds: Int) {
        restTotal = TimeInterval(seconds)
        restEnd = Date().addingTimeInterval(TimeInterval(seconds))
        scheduleRestNotification()
        updateActivity()
    }

    func addRest(_ seconds: Int) {
        guard let end = restEnd else { return }
        let newEnd = max(Date(), end).addingTimeInterval(TimeInterval(seconds))
        restTotal += TimeInterval(seconds)
        restEnd = newEnd
        scheduleRestNotification()
        updateActivity()
    }

    func stopRest() {
        restEnd = nil
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["rest"])
        updateActivity()
    }

    private func scheduleRestNotification() {
        guard let end = restEnd else { return }
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: ["rest"])
        Task {
            let granted = (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
            guard granted else { return }
            let content = UNMutableNotificationContent()
            content.title = tr("Rest is over", "Rust voorbij")
            content.body = tr("Time for your next set 💪", "Tijd voor je volgende set 💪")
            content.sound = .default
            content.interruptionLevel = .timeSensitive
            let interval = max(1, end.timeIntervalSinceNow)
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
            try? await center.add(UNNotificationRequest(identifier: "rest", content: content, trigger: trigger))
        }
    }

    // MARK: Finish

    func finish(_ context: ModelContext) async {
        guard let workout = active else { return }
        stopRest()
        workout.endDate = Date()
        // Drop sets that were never filled in and exercises without sets.
        for entry in workout.entries ?? [] {
            for set in entry.sets ?? [] where !set.isDone && set.reps == 0 && set.durationSeconds == 0 {
                context.delete(set)
            }
        }
        try? context.save()
        for entry in workout.entries ?? [] where (entry.sets ?? []).isEmpty {
            context.delete(entry)
        }
        try? context.save()

        let health = HealthKitManager.shared
        if !(workout.entries ?? []).isEmpty, let uuid = await health.saveWorkout(workout) {
            workout.healthKitUUID = uuid
        }
        if workout.activeCalories == 0 { workout.activeCalories = health.estimatedCalories(workout) }
        try? context.save()

        endActivity()
        active = nil
        isPresented = false
        finished = workout
        WidgetCenter.shared.reloadAllTimelines()
    }

    func discard(_ context: ModelContext) {
        guard let workout = active else { return }
        stopRest()
        context.delete(workout)
        try? context.save()
        endActivity()
        active = nil
        isPresented = false
        WidgetCenter.shared.reloadAllTimelines()
    }

    // MARK: Live Activity

    private func state() -> LiftrActivityAttributes.ContentState {
        let entries = active?.sortedEntries ?? []
        let sets = entries.flatMap { $0.sortedSets }
        let current = entries.first { entry in entry.sortedSets.contains { !$0.isDone } } ?? entries.last
        return .init(exerciseName: current?.exercise?.name ?? tr("Workout", "Training"),
                     setsDone: sets.filter(\.isDone).count,
                     totalSets: sets.count,
                     restEnd: restEnd.flatMap { $0 > Date() ? $0 : nil })
    }

    private func startActivity() {
        guard let workout = active, ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        endActivity()
        let attributes = LiftrActivityAttributes(workoutTitle: workout.title, startDate: workout.startDate)
        activity = try? Activity.request(attributes: attributes,
                                         content: ActivityContent(state: state(), staleDate: nil))
    }

    func updateActivity() {
        guard active != nil else { return }
        if activity == nil {
            startActivity()
            return
        }
        let content = ActivityContent(state: state(), staleDate: nil)
        Task { await activity?.update(content) }
    }

    private func endActivity() {
        let current = activity
        activity = nil
        Task {
            await current?.end(nil, dismissalPolicy: .immediate)
            for other in Activity<LiftrActivityAttributes>.activities {
                await other.end(nil, dismissalPolicy: .immediate)
            }
        }
    }
}
