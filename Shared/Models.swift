import Foundation
import SwiftData

// All models sync through iCloud (CloudKit), so every property has a default value,
// relationships are optional and there are no unique constraints.

enum Muscle: String, CaseIterable, Identifiable, Codable {
    case chest, back, shoulders, arms, legs, glutes, core, cardio, fullBody

    var id: String { rawValue }

    var title: String {
        switch self {
        case .chest: tr("Chest", "Borst")
        case .back: tr("Back", "Rug")
        case .shoulders: tr("Shoulders", "Schouders")
        case .arms: tr("Arms", "Armen")
        case .legs: tr("Legs", "Benen")
        case .glutes: tr("Glutes", "Billen")
        case .core: tr("Core", "Core")
        case .cardio: tr("Cardio", "Cardio")
        case .fullBody: tr("Full body", "Hele lichaam")
        }
    }

    var symbol: String {
        switch self {
        case .chest: "figure.strengthtraining.traditional"
        case .back: "figure.rower"
        case .shoulders: "figure.arms.open"
        case .arms: "dumbbell.fill"
        case .legs: "figure.step.training"
        case .glutes: "figure.cross.training"
        case .core: "figure.core.training"
        case .cardio: "figure.run"
        case .fullBody: "figure.mixed.cardio"
        }
    }
}

enum ExerciseKind: String, CaseIterable, Identifiable, Codable {
    case machine, freeWeight, cable, bodyweight, cardio

    var id: String { rawValue }

    var title: String {
        switch self {
        case .machine: tr("Machine", "Apparaat")
        case .freeWeight: tr("Free weight", "Vrij gewicht")
        case .cable: tr("Cable", "Kabel")
        case .bodyweight: tr("Bodyweight", "Lichaamsgewicht")
        case .cardio: tr("Cardio", "Cardio")
        }
    }

    var isCardio: Bool { self == .cardio }
}

enum SetKind: String, CaseIterable, Identifiable, Codable {
    case warmup, work, drop, failure

    var id: String { rawValue }

    var title: String {
        switch self {
        case .warmup: tr("Warm-up", "Warming-up")
        case .work: tr("Working set", "Werkset")
        case .drop: tr("Drop set", "Dropset")
        case .failure: tr("To failure", "Tot falen")
        }
    }

    var badge: String {
        switch self {
        case .warmup: "W"
        case .work: ""
        case .drop: "D"
        case .failure: "F"
        }
    }
}

@Model
final class Exercise {
    var id: UUID = UUID()
    var name: String = ""
    /// Set for exercises from the built-in list, used to merge duplicates from several devices.
    var seedID: String? = nil
    var muscleRaw: String = Muscle.chest.rawValue
    var kindRaw: String = ExerciseKind.machine.rawValue
    var isFavorite: Bool = false
    var isArchived: Bool = false
    var notes: String = ""
    var createdAt: Date = Date()

    @Relationship(deleteRule: .nullify, inverse: \WorkoutEntry.exercise)
    var entries: [WorkoutEntry]? = []

    @Relationship(deleteRule: .nullify, inverse: \RoutineItem.exercise)
    var routineItems: [RoutineItem]? = []

    init(name: String, muscle: Muscle, kind: ExerciseKind, seedID: String? = nil) {
        self.id = UUID()
        self.name = name
        self.muscleRaw = muscle.rawValue
        self.kindRaw = kind.rawValue
        self.seedID = seedID
        self.createdAt = Date()
    }

    var muscle: Muscle {
        get { Muscle(rawValue: muscleRaw) ?? .fullBody }
        set { muscleRaw = newValue.rawValue }
    }

    var kind: ExerciseKind {
        get { ExerciseKind(rawValue: kindRaw) ?? .machine }
        set { kindRaw = newValue.rawValue }
    }
}

@Model
final class Workout {
    var id: UUID = UUID()
    var title: String = ""
    var startDate: Date = Date()
    /// nil while the workout is still running.
    var endDate: Date? = nil
    var notes: String = ""
    /// UUID of the matching workout in Apple Health.
    var healthKitUUID: String? = nil
    var activeCalories: Double = 0
    var averageHeartRate: Double = 0
    /// "iphone", "watch" or "import".
    var source: String = "iphone"

    @Relationship(deleteRule: .cascade, inverse: \WorkoutEntry.workout)
    var entries: [WorkoutEntry]? = []

    init(title: String, startDate: Date = Date(), source: String = "iphone") {
        self.id = UUID()
        self.title = title
        self.startDate = startDate
        self.source = source
    }

    var isActive: Bool { endDate == nil }

    var sortedEntries: [WorkoutEntry] {
        (entries ?? []).sorted { $0.order < $1.order }
    }

    var duration: TimeInterval {
        (endDate ?? Date()).timeIntervalSince(startDate)
    }
}

@Model
final class WorkoutEntry {
    var id: UUID = UUID()
    var order: Int = 0
    var notes: String = ""
    var exercise: Exercise? = nil
    var workout: Workout? = nil

    @Relationship(deleteRule: .cascade, inverse: \SetRecord.entry)
    var sets: [SetRecord]? = []

    init(order: Int, exercise: Exercise?) {
        self.id = UUID()
        self.order = order
        self.exercise = exercise
    }

    var sortedSets: [SetRecord] {
        (sets ?? []).sorted { $0.order < $1.order }
    }
}

@Model
final class SetRecord {
    var id: UUID = UUID()
    var order: Int = 0
    var reps: Int = 0
    /// Weight per repetition, always stored in kilograms.
    var weight: Double = 0
    var kindRaw: String = SetKind.work.rawValue
    var isDone: Bool = false
    var rpe: Int = 0
    /// Cardio: duration in seconds and distance in metres.
    var durationSeconds: Int = 0
    var distanceMeters: Double = 0
    var completedAt: Date? = nil
    var entry: WorkoutEntry? = nil

    init(order: Int, reps: Int = 0, weight: Double = 0, kind: SetKind = .work) {
        self.id = UUID()
        self.order = order
        self.reps = reps
        self.weight = weight
        self.kindRaw = kind.rawValue
    }

    var kind: SetKind {
        get { SetKind(rawValue: kindRaw) ?? .work }
        set { kindRaw = newValue.rawValue }
    }

    var volume: Double { Double(reps) * weight }
}

@Model
final class Routine {
    var id: UUID = UUID()
    var name: String = ""
    var notes: String = ""
    var createdAt: Date = Date()

    @Relationship(deleteRule: .cascade, inverse: \RoutineItem.routine)
    var items: [RoutineItem]? = []

    init(name: String) {
        self.id = UUID()
        self.name = name
        self.createdAt = Date()
    }

    var sortedItems: [RoutineItem] {
        (items ?? []).sorted { $0.order < $1.order }
    }
}

@Model
final class RoutineItem {
    var id: UUID = UUID()
    var order: Int = 0
    var targetSets: Int = 3
    var targetReps: Int = 10
    var targetWeight: Double = 0
    var exercise: Exercise? = nil
    var routine: Routine? = nil

    init(order: Int, exercise: Exercise?, sets: Int = 3, reps: Int = 10, weight: Double = 0) {
        self.id = UUID()
        self.order = order
        self.exercise = exercise
        self.targetSets = sets
        self.targetReps = reps
        self.targetWeight = weight
    }
}

/// Weight and body fat entered in Liftr (also written to Apple Health when allowed).
@Model
final class BodyMeasurement {
    var id: UUID = UUID()
    var date: Date = Date()
    var weightKg: Double = 0
    var bodyFatPercent: Double = 0

    init(date: Date, weightKg: Double, bodyFatPercent: Double = 0) {
        self.id = UUID()
        self.date = date
        self.weightKg = weightKg
        self.bodyFatPercent = bodyFatPercent
    }
}

/// The gym membership pass: its access code shown as QR or barcode at the entrance.
@Model
final class GymPass {
    var id: UUID = UUID()
    var gymName: String = ""
    /// Known chain from GymTemplates (logo, colour); nil for another gym.
    var templateID: String? = nil
    /// Card colour, e.g. "#FB7819".
    var colorHex: String = "#FF6B00"
    var memberName: String = ""
    /// Encrypted end to end in iCloud: only your own devices hold the key.
    @Attribute(.allowsCloudEncryption) var code: String = ""
    var formatRaw: String = BarcodeFormat.qr.rawValue
    var note: String = ""
    var createdAt: Date = Date()
    /// PNG of a 2D code, rendered on iPhone/iPad for the Watch (which has no Core Image).
    @Attribute(.externalStorage) var codeImage: Data? = nil

    init(gymName: String, code: String, format: BarcodeFormat) {
        self.id = UUID()
        self.gymName = gymName
        self.code = code
        self.formatRaw = format.rawValue
        self.createdAt = Date()
    }

    var format: BarcodeFormat {
        get { BarcodeFormat(rawValue: formatRaw) ?? .qr }
        set { formatRaw = newValue.rawValue }
    }
}

enum LiftrSchema {
    static let models: [any PersistentModel.Type] = [
        Exercise.self, Workout.self, WorkoutEntry.self, SetRecord.self,
        Routine.self, RoutineItem.self, BodyMeasurement.self, GymPass.self,
    ]
}
