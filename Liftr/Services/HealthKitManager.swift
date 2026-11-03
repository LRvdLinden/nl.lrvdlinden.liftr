import Foundation
import HealthKit
import Observation

/// Everything Liftr reads from and writes to Apple Health on iPhone and iPad.
@MainActor
@Observable
final class HealthKitManager {
    static let shared = HealthKitManager()

    let store = HKHealthStore()
    var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }
    private(set) var hasRequested = UserDefaults.standard.bool(forKey: "health.requested")

    // Latest values for the dashboard.
    private(set) var latestWeightKg: Double?
    private(set) var latestWeightDate: Date?
    private(set) var latestBodyFat: Double?
    private(set) var latestLeanMassKg: Double?
    private(set) var restingHeartRate: Double?
    private(set) var heightCm: Double?
    private(set) var weightHistory: [(date: Date, kg: Double)] = []
    private(set) var bodyFatHistory: [(date: Date, percent: Double)] = []

    var writeWorkouts: Bool {
        get { UserDefaults.standard.object(forKey: "health.saveWorkouts") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "health.saveWorkouts") }
    }

    var writeBody: Bool {
        get { UserDefaults.standard.object(forKey: "health.saveBody") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "health.saveBody") }
    }

    private let bodyMass = HKQuantityType(.bodyMass)
    private let bodyFat = HKQuantityType(.bodyFatPercentage)
    private let leanMass = HKQuantityType(.leanBodyMass)
    private let height = HKQuantityType(.height)
    private let restingHR = HKQuantityType(.restingHeartRate)
    private let heartRate = HKQuantityType(.heartRate)
    private let activeEnergy = HKQuantityType(.activeEnergyBurned)
    private let distanceWalkRun = HKQuantityType(.distanceWalkingRunning)
    private let distanceCycling = HKQuantityType(.distanceCycling)

    private var shareTypes: Set<HKSampleType> {
        [HKObjectType.workoutType(), activeEnergy, bodyMass, bodyFat, leanMass, distanceWalkRun, distanceCycling, heartRate]
    }

    private var readTypes: Set<HKObjectType> {
        [HKObjectType.workoutType(), activeEnergy, bodyMass, bodyFat, leanMass, height, restingHR, heartRate,
         distanceWalkRun, distanceCycling]
    }

    func requestAuthorization() async {
        guard isAvailable else { return }
        do {
            try await store.requestAuthorization(toShare: shareTypes, read: readTypes)
        } catch {
            print("Health authorization failed: \(error)")
        }
        hasRequested = true
        UserDefaults.standard.set(true, forKey: "health.requested")
        await refresh()
    }

    /// Whether Liftr may write workouts (Health only tells us about write access).
    var canWriteWorkouts: Bool {
        isAvailable && store.authorizationStatus(for: HKObjectType.workoutType()) == .sharingAuthorized
    }

    func refresh() async {
        guard isAvailable, hasRequested else { return }
        let weights = await samples(bodyMass, unit: .gramUnit(with: .kilo), limit: 365)
        weightHistory = weights.map { (date: $0.date, kg: $0.value) }.sorted { $0.date < $1.date }
        latestWeightKg = weights.first?.value
        latestWeightDate = weights.first?.date

        let fats = await samples(bodyFat, unit: .percent(), limit: 365)
        bodyFatHistory = fats.map { (date: $0.date, percent: $0.value * 100) }.sorted { $0.date < $1.date }
        latestBodyFat = fats.first.map { $0.value * 100 }

        latestLeanMassKg = await samples(leanMass, unit: .gramUnit(with: .kilo), limit: 1).first?.value
        restingHeartRate = await samples(restingHR, unit: HKUnit.count().unitDivided(by: .minute()), limit: 1).first?.value
        heightCm = await samples(height, unit: .meterUnit(with: .centi), limit: 1).first?.value
    }

    private func samples(_ type: HKQuantityType, unit: HKUnit, limit: Int) async -> [(date: Date, value: Double)] {
        let descriptor = HKSampleQueryDescriptor(predicates: [.quantitySample(type: type)],
                                                 sortDescriptors: [SortDescriptor(\.endDate, order: .reverse)],
                                                 limit: limit)
        guard let results = try? await descriptor.result(for: store) else { return [] }
        return results.map { (date: $0.endDate, value: $0.quantity.doubleValue(for: unit)) }
    }

    // MARK: Body

    func saveBody(weightKg: Double, bodyFatPercent: Double?, date: Date) async {
        guard isAvailable, writeBody else { return }
        var objects: [HKObject] = []
        if weightKg > 0 {
            objects.append(HKQuantitySample(type: bodyMass,
                                            quantity: HKQuantity(unit: .gramUnit(with: .kilo), doubleValue: weightKg),
                                            start: date, end: date))
        }
        if let fat = bodyFatPercent, fat > 0 {
            objects.append(HKQuantitySample(type: bodyFat,
                                            quantity: HKQuantity(unit: .percent(), doubleValue: fat / 100),
                                            start: date, end: date))
            if weightKg > 0 {
                objects.append(HKQuantitySample(type: leanMass,
                                                quantity: HKQuantity(unit: .gramUnit(with: .kilo), doubleValue: weightKg * (1 - fat / 100)),
                                                start: date, end: date))
            }
        }
        guard !objects.isEmpty else { return }
        do {
            try await store.save(objects)
        } catch {
            print("Saving body data failed: \(error)")
        }
        await refresh()
    }

    // MARK: Workouts

    /// Saves a finished Liftr workout to Health. Returns the Health workout's UUID.
    func saveWorkout(_ workout: Workout) async -> String? {
        guard isAvailable, writeWorkouts, let end = workout.endDate else { return nil }
        let entries = workout.sortedEntries
        let cardioEntries = entries.filter { $0.exercise?.kind.isCardio == true }
        let allCardio = !entries.isEmpty && cardioEntries.count == entries.count

        let configuration = HKWorkoutConfiguration()
        configuration.activityType = allCardio ? cardioActivity(for: cardioEntries.first?.exercise) : .traditionalStrengthTraining
        configuration.locationType = .indoor

        let builder = HKWorkoutBuilder(healthStore: store, configuration: configuration, device: .local())
        let start = workout.startDate
        do {
            try await withCheckedThrowingContinuation { (c: CheckedContinuation<Void, Error>) in
                builder.beginCollection(withStart: start) { _, error in
                    if let error { c.resume(throwing: error) } else { c.resume() }
                }
            }

            var samples: [HKSample] = []
            let kcal = estimatedCalories(workout)
            if kcal > 0 {
                samples.append(HKQuantitySample(type: activeEnergy,
                                                quantity: HKQuantity(unit: .kilocalorie(), doubleValue: kcal),
                                                start: start, end: end))
            }
            let meters = cardioEntries.flatMap { WorkoutStats.countedSets($0) }.reduce(0) { $0 + $1.distanceMeters }
            if meters > 0 {
                let bikes: Set<String> = ["exercise-bike", "recumbent-bike", "spin-bike", "air-bike"]
                let isBike = cardioEntries.contains { bikes.contains($0.exercise?.seedID ?? "") }
                samples.append(HKQuantitySample(type: isBike ? distanceCycling : distanceWalkRun,
                                                quantity: HKQuantity(unit: .meter(), doubleValue: meters),
                                                start: start, end: end))
            }
            if !samples.isEmpty {
                try await withCheckedThrowingContinuation { (c: CheckedContinuation<Void, Error>) in
                    builder.add(samples) { _, error in
                        if let error { c.resume(throwing: error) } else { c.resume() }
                    }
                }
            }

            var metadata: [String: Any] = [
                HKMetadataKeyIndoorWorkout: true,
                "LiftrSets": WorkoutStats.setCount(workout),
                "LiftrReps": WorkoutStats.repCount(workout),
                "LiftrVolumeKg": WorkoutStats.volume(workout).rounded(),
                "LiftrExercises": entries.compactMap { $0.exercise?.name }.joined(separator: ", "),
            ]
            if !workout.title.isEmpty { metadata[HKMetadataKeyWorkoutBrandName] = "Liftr – \(workout.title)" }
            try await withCheckedThrowingContinuation { (c: CheckedContinuation<Void, Error>) in
                builder.addMetadata(metadata) { _, error in
                    if let error { c.resume(throwing: error) } else { c.resume() }
                }
            }

            try await withCheckedThrowingContinuation { (c: CheckedContinuation<Void, Error>) in
                builder.endCollection(withEnd: end) { _, error in
                    if let error { c.resume(throwing: error) } else { c.resume() }
                }
            }
            let saved: HKWorkout? = try await withCheckedThrowingContinuation { c in
                builder.finishWorkout { workout, error in
                    if let error { c.resume(throwing: error) } else { c.resume(returning: workout) }
                }
            }
            workout.activeCalories = kcal
            return saved?.uuid.uuidString
        } catch {
            print("Saving workout to Health failed: \(error)")
            builder.discardWorkout()
            return nil
        }
    }

    /// Removes the matching workout from Health (only workouts Liftr saved itself).
    func deleteWorkout(uuid: String) async {
        guard isAvailable, let id = UUID(uuidString: uuid) else { return }
        let descriptor = HKSampleQueryDescriptor(predicates: [.workout(HKQuery.predicateForObject(with: id))],
                                                 sortDescriptors: [], limit: 1)
        guard let found = try? await descriptor.result(for: store), let workout = found.first else { return }
        try? await store.delete(workout)
    }

    private func cardioActivity(for exercise: Exercise?) -> HKWorkoutActivityType {
        switch exercise?.seedID {
        case "treadmill": .running
        case "incline-walk": .walking
        case "exercise-bike", "recumbent-bike", "spin-bike", "air-bike": .cycling
        case "stepper": .stairClimbing
        case "ski-erg": .crossCountrySkiing
        case "jump-rope": .jumpRope
        case "rowing-machine": .rowing
        case "stair-climber": .stairClimbing
        case "cross-trainer": .elliptical
        default: .mixedCardio
        }
    }

    /// Rough estimate from duration and body weight (MET ≈ 5 for strength, 7 for cardio).
    func estimatedCalories(_ workout: Workout) -> Double {
        let hours = workout.duration / 3600
        guard hours > 0 else { return 0 }
        let weight = latestWeightKg ?? 70
        let cardioShare = Double(workout.sortedEntries.filter { $0.exercise?.kind.isCardio == true }.count)
            / Double(max(1, workout.sortedEntries.count))
        let met = 5 + 2 * cardioShare
        return (met * weight * min(hours, 4)).rounded()
    }
}
