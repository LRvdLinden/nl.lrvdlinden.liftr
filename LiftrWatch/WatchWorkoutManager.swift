import Foundation
import HealthKit
import Observation
import SwiftData
import WatchKit

/// Live strength workout on the Apple Watch: heart rate and calories from the sensors, sets logged
/// on the wrist, saved to Apple Health and (through iCloud) to Liftr on iPhone and iPad.
@MainActor
@Observable
final class WatchWorkoutManager: NSObject {
    static let shared = WatchWorkoutManager()

    let store = HKHealthStore()
    private var session: HKWorkoutSession?
    private var builder: HKLiveWorkoutBuilder?

    var workout: Workout?
    var isRunning = false
    var isPaused = false
    var startDate: Date?
    var heartRate: Double = 0
    var averageHeartRate: Double = 0
    var activeCalories: Double = 0
    var restEnd: Date?
    var finished: Workout?

    var restSeconds: Int {
        let value = UserDefaults.standard.integer(forKey: "rest.default")
        return value > 0 ? value : 90
    }

    func requestAuthorization() async {
        guard HKHealthStore.isHealthDataAvailable() else { return }
        let share: Set<HKSampleType> = [HKObjectType.workoutType(), HKQuantityType(.activeEnergyBurned), HKQuantityType(.heartRate)]
        let read: Set<HKObjectType> = [HKObjectType.workoutType(), HKQuantityType(.activeEnergyBurned), HKQuantityType(.heartRate),
                                       HKQuantityType(.bodyMass)]
        try? await store.requestAuthorization(toShare: share, read: read)
    }

    // MARK: Start

    func start(context: ModelContext, routine: Routine? = nil) async {
        guard workout == nil else { return }
        await requestAuthorization()

        let new = Workout(title: routine?.name ?? tr("Workout", "Training"), source: "watch")
        context.insert(new)
        if let routine {
            for (index, item) in routine.sortedItems.enumerated() {
                guard let exercise = item.exercise else { continue }
                let entry = WorkoutEntry(order: index, exercise: exercise)
                entry.workout = new
                context.insert(entry)
                let previous = WorkoutStats.previousSets(for: exercise, excluding: new)?.sets.filter { $0.kind != .warmup }
                for n in 0..<max(1, item.targetSets) {
                    let template = previous.flatMap { n < $0.count ? $0[n] : $0.last }
                    let set = SetRecord(order: n, reps: template?.reps ?? item.targetReps, weight: template?.weight ?? item.targetWeight)
                    set.entry = entry
                    context.insert(set)
                }
            }
        }
        try? context.save()
        workout = new
        startDate = new.startDate

        let configuration = HKWorkoutConfiguration()
        configuration.activityType = .traditionalStrengthTraining
        configuration.locationType = .indoor
        do {
            let session = try HKWorkoutSession(healthStore: store, configuration: configuration)
            let builder = session.associatedWorkoutBuilder()
            builder.dataSource = HKLiveWorkoutDataSource(healthStore: store, workoutConfiguration: configuration)
            session.delegate = self
            builder.delegate = self
            self.session = session
            self.builder = builder
            session.startActivity(with: new.startDate)
            try await withCheckedThrowingContinuation { (c: CheckedContinuation<Void, Error>) in
                builder.beginCollection(withStart: new.startDate) { _, error in
                    if let error { c.resume(throwing: error) } else { c.resume() }
                }
            }
            isRunning = true
        } catch {
            // Without Health the workout is still logged in Liftr.
            print("Watch workout session failed: \(error)")
            isRunning = true
        }
    }

    // MARK: Sets

    @discardableResult
    func add(_ exercise: Exercise, context: ModelContext) -> WorkoutEntry? {
        guard let workout else { return nil }
        let order = (workout.entries ?? []).map(\.order).max().map { $0 + 1 } ?? 0
        let entry = WorkoutEntry(order: order, exercise: exercise)
        entry.workout = workout
        context.insert(entry)
        let previous = WorkoutStats.previousSets(for: exercise, excluding: workout)?.sets ?? []
        let templates = previous.isEmpty ? [SetRecord(order: 0, reps: 10, weight: 0)] : previous
        for (n, old) in templates.enumerated() {
            let set = SetRecord(order: n, reps: old.reps, weight: old.weight, kind: old.kind)
            set.durationSeconds = exercise.kind.isCardio ? max(old.durationSeconds, 600) : 0
            set.entry = entry
            context.insert(set)
        }
        try? context.save()
        return entry
    }

    func complete(_ set: SetRecord, context: ModelContext) {
        set.isDone = true
        set.completedAt = Date()
        try? context.save()
        WKInterfaceDevice.current().play(.success)
        startRest()
    }

    func addSet(to entry: WorkoutEntry, context: ModelContext) -> SetRecord {
        let last = entry.sortedSets.last
        let set = SetRecord(order: (last?.order ?? -1) + 1, reps: last?.reps ?? 10, weight: last?.weight ?? 0)
        set.entry = entry
        context.insert(set)
        try? context.save()
        return set
    }

    // MARK: Rest

    private var restTask: Task<Void, Never>?

    func startRest() {
        let end = Date().addingTimeInterval(TimeInterval(restSeconds))
        restEnd = end
        restTask?.cancel()
        restTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(max(0, end.timeIntervalSinceNow)))
            guard !Task.isCancelled else { return }
            WKInterfaceDevice.current().play(.notification)
            try? await Task.sleep(for: .seconds(1))
            WKInterfaceDevice.current().play(.notification)
            if self.restEnd == end { self.restEnd = nil }
        }
    }

    func stopRest() {
        restTask?.cancel()
        restEnd = nil
    }

    // MARK: Pause / end

    func togglePause() {
        guard let session else { isPaused.toggle(); return }
        if session.state == .running { session.pause() } else { session.resume() }
    }

    func end(context: ModelContext) async {
        guard let workout else { return }
        stopRest()
        let end = Date()
        workout.endDate = end
        for entry in workout.entries ?? [] {
            for set in entry.sets ?? [] where !set.isDone { context.delete(set) }
        }
        try? context.save()
        for entry in workout.entries ?? [] where (entry.sets ?? []).isEmpty { context.delete(entry) }

        if let session, let builder {
            session.end()
            do {
                try await withCheckedThrowingContinuation { (c: CheckedContinuation<Void, Error>) in
                    builder.endCollection(withEnd: end) { _, error in
                        if let error { c.resume(throwing: error) } else { c.resume() }
                    }
                }
                let metadata: [String: Any] = [
                    HKMetadataKeyIndoorWorkout: true,
                    "LiftrSets": WorkoutStats.setCount(workout),
                    "LiftrReps": WorkoutStats.repCount(workout),
                    "LiftrVolumeKg": WorkoutStats.volume(workout).rounded(),
                ]
                try await withCheckedThrowingContinuation { (c: CheckedContinuation<Void, Error>) in
                    builder.addMetadata(metadata) { _, error in
                        if let error { c.resume(throwing: error) } else { c.resume() }
                    }
                }
                let saved: HKWorkout? = try await withCheckedThrowingContinuation { c in
                    builder.finishWorkout { workout, error in
                        if let error { c.resume(throwing: error) } else { c.resume(returning: workout) }
                    }
                }
                if let saved { workout.healthKitUUID = saved.uuid.uuidString }
            } catch {
                print("Finishing watch workout failed: \(error)")
            }
        }
        workout.activeCalories = activeCalories.rounded()
        workout.averageHeartRate = averageHeartRate.rounded()
        try? context.save()

        finished = workout
        reset()
    }

    func discard(context: ModelContext) {
        if let workout { context.delete(workout) }
        try? context.save()
        session?.end()
        builder?.discardWorkout()
        reset()
    }

    private func reset() {
        session = nil
        builder = nil
        workout = nil
        isRunning = false
        isPaused = false
        startDate = nil
        heartRate = 0
        averageHeartRate = 0
        activeCalories = 0
        restEnd = nil
    }

    fileprivate func update(_ statistics: HKStatistics?) {
        guard let statistics else { return }
        switch statistics.quantityType {
        case HKQuantityType(.heartRate):
            let unit = HKUnit.count().unitDivided(by: .minute())
            heartRate = statistics.mostRecentQuantity()?.doubleValue(for: unit) ?? heartRate
            averageHeartRate = statistics.averageQuantity()?.doubleValue(for: unit) ?? averageHeartRate
        case HKQuantityType(.activeEnergyBurned):
            activeCalories = statistics.sumQuantity()?.doubleValue(for: .kilocalorie()) ?? activeCalories
        default:
            break
        }
    }
}

extension WatchWorkoutManager: HKWorkoutSessionDelegate {
    nonisolated func workoutSession(_ workoutSession: HKWorkoutSession, didChangeTo toState: HKWorkoutSessionState,
                                    from fromState: HKWorkoutSessionState, date: Date) {
        Task { @MainActor in
            self.isPaused = toState == .paused
        }
    }

    nonisolated func workoutSession(_ workoutSession: HKWorkoutSession, didFailWithError error: Error) {
        print("Workout session error: \(error)")
    }
}

extension WatchWorkoutManager: HKLiveWorkoutBuilderDelegate {
    nonisolated func workoutBuilderDidCollectEvent(_ workoutBuilder: HKLiveWorkoutBuilder) {}

    nonisolated func workoutBuilder(_ workoutBuilder: HKLiveWorkoutBuilder, didCollectDataOf collectedTypes: Set<HKSampleType>) {
        for type in collectedTypes {
            guard let quantityType = type as? HKQuantityType else { continue }
            let statistics = workoutBuilder.statistics(for: quantityType)
            Task { @MainActor in
                self.update(statistics)
            }
        }
    }
}
