import Foundation
import SwiftData

/// Built-in list of common gym exercises (machines, free weights, cables, cardio).
enum ExerciseLibrary {
    struct Seed {
        let id: String
        let en: String
        let nl: String
        let muscle: Muscle
        let kind: ExerciseKind
    }

    static let seeds: [Seed] = [
        // Legs & glutes
        .init(id: "leg-press", en: "Leg press", nl: "Leg press", muscle: .legs, kind: .machine),
        .init(id: "leg-extension", en: "Leg extension", nl: "Leg extension", muscle: .legs, kind: .machine),
        .init(id: "seated-leg-curl", en: "Seated leg curl", nl: "Leg curl (zittend)", muscle: .legs, kind: .machine),
        .init(id: "lying-leg-curl", en: "Lying leg curl", nl: "Leg curl (liggend)", muscle: .legs, kind: .machine),
        .init(id: "hip-abduction", en: "Hip abduction", nl: "Abductor (benen naar buiten)", muscle: .glutes, kind: .machine),
        .init(id: "hip-adduction", en: "Hip adduction", nl: "Adductor (benen naar binnen)", muscle: .legs, kind: .machine),
        .init(id: "glute-kickback", en: "Glute kickback machine", nl: "Glute kickback", muscle: .glutes, kind: .machine),
        .init(id: "hip-thrust", en: "Hip thrust", nl: "Hip thrust", muscle: .glutes, kind: .freeWeight),
        .init(id: "barbell-squat", en: "Barbell squat", nl: "Squat met halter", muscle: .legs, kind: .freeWeight),
        .init(id: "smith-squat", en: "Smith machine squat", nl: "Squat in smith machine", muscle: .legs, kind: .machine),
        .init(id: "goblet-squat", en: "Goblet squat", nl: "Goblet squat", muscle: .legs, kind: .freeWeight),
        .init(id: "lunges", en: "Lunges", nl: "Lunges", muscle: .legs, kind: .freeWeight),
        .init(id: "romanian-deadlift", en: "Romanian deadlift", nl: "Roemeense deadlift", muscle: .legs, kind: .freeWeight),
        .init(id: "calf-raise", en: "Calf raise", nl: "Kuitheffen", muscle: .legs, kind: .machine),
        // Chest
        .init(id: "chest-press", en: "Chest press machine", nl: "Chest press", muscle: .chest, kind: .machine),
        .init(id: "bench-press", en: "Bench press", nl: "Bankdrukken", muscle: .chest, kind: .freeWeight),
        .init(id: "incline-db-press", en: "Incline dumbbell press", nl: "Schuin bankdrukken (dumbbells)", muscle: .chest, kind: .freeWeight),
        .init(id: "pec-deck", en: "Pec deck (butterfly)", nl: "Butterfly (pec deck)", muscle: .chest, kind: .machine),
        .init(id: "cable-fly", en: "Cable fly", nl: "Cable fly", muscle: .chest, kind: .cable),
        .init(id: "push-up", en: "Push-ups", nl: "Push-ups", muscle: .chest, kind: .bodyweight),
        // Back
        .init(id: "lat-pulldown", en: "Lat pulldown", nl: "Lat pulldown", muscle: .back, kind: .cable),
        .init(id: "seated-row", en: "Seated row machine", nl: "Seated row", muscle: .back, kind: .machine),
        .init(id: "cable-row", en: "Cable row", nl: "Roeien aan de kabel", muscle: .back, kind: .cable),
        .init(id: "assisted-pull-up", en: "Assisted pull-up", nl: "Assisted pull-up", muscle: .back, kind: .machine),
        .init(id: "back-extension", en: "Back extension", nl: "Rugstrekken", muscle: .back, kind: .bodyweight),
        .init(id: "deadlift", en: "Deadlift", nl: "Deadlift", muscle: .back, kind: .freeWeight),
        .init(id: "db-row", en: "Dumbbell row", nl: "Roeien met dumbbell", muscle: .back, kind: .freeWeight),
        // Shoulders
        .init(id: "shoulder-press", en: "Shoulder press machine", nl: "Shoulder press", muscle: .shoulders, kind: .machine),
        .init(id: "db-shoulder-press", en: "Dumbbell shoulder press", nl: "Schouderdrukken (dumbbells)", muscle: .shoulders, kind: .freeWeight),
        .init(id: "lateral-raise", en: "Lateral raise", nl: "Zijwaarts heffen", muscle: .shoulders, kind: .freeWeight),
        .init(id: "rear-delt-fly", en: "Rear delt fly", nl: "Reverse butterfly", muscle: .shoulders, kind: .machine),
        .init(id: "face-pull", en: "Face pull", nl: "Face pull", muscle: .shoulders, kind: .cable),
        // Arms
        .init(id: "biceps-curl-machine", en: "Biceps curl machine", nl: "Bicepscurl (apparaat)", muscle: .arms, kind: .machine),
        .init(id: "db-curl", en: "Dumbbell curl", nl: "Bicepscurl (dumbbells)", muscle: .arms, kind: .freeWeight),
        .init(id: "hammer-curl", en: "Hammer curl", nl: "Hammer curl", muscle: .arms, kind: .freeWeight),
        .init(id: "cable-curl", en: "Cable curl", nl: "Bicepscurl aan de kabel", muscle: .arms, kind: .cable),
        .init(id: "triceps-pushdown", en: "Triceps pushdown", nl: "Triceps pushdown", muscle: .arms, kind: .cable),
        .init(id: "triceps-machine", en: "Triceps extension machine", nl: "Triceps extension (apparaat)", muscle: .arms, kind: .machine),
        .init(id: "assisted-dips", en: "Assisted dips", nl: "Assisted dips", muscle: .arms, kind: .machine),
        // Core
        .init(id: "ab-crunch-machine", en: "Ab crunch machine", nl: "Buikcrunch (apparaat)", muscle: .core, kind: .machine),
        .init(id: "cable-crunch", en: "Cable crunch", nl: "Crunch aan de kabel", muscle: .core, kind: .cable),
        .init(id: "plank", en: "Plank", nl: "Plank", muscle: .core, kind: .bodyweight),
        .init(id: "leg-raise", en: "Hanging leg raise", nl: "Benen heffen (hangend)", muscle: .core, kind: .bodyweight),
        .init(id: "russian-twist", en: "Russian twist", nl: "Russian twist", muscle: .core, kind: .bodyweight),
        // Cardio
        .init(id: "treadmill", en: "Treadmill", nl: "Loopband", muscle: .cardio, kind: .cardio),
        .init(id: "cross-trainer", en: "Cross trainer", nl: "Crosstrainer", muscle: .cardio, kind: .cardio),
        .init(id: "exercise-bike", en: "Exercise bike", nl: "Hometrainer (fiets)", muscle: .cardio, kind: .cardio),
        .init(id: "rowing-machine", en: "Rowing machine", nl: "Roeimachine", muscle: .cardio, kind: .cardio),
        .init(id: "stair-climber", en: "Stair climber", nl: "Traploper", muscle: .cardio, kind: .cardio),
    ]

    /// Adds the built-in exercises that are missing, then merges duplicates that appear when
    /// several devices seeded before iCloud had synced.
    @MainActor
    static func prepare(_ context: ModelContext) {
        let existing = (try? context.fetch(FetchDescriptor<Exercise>())) ?? []
        let known = Set(existing.compactMap(\.seedID))
        let seededKey = "library.seeded.v1"
        if !UserDefaults.standard.bool(forKey: seededKey) || existing.isEmpty {
            for seed in seeds where !known.contains(seed.id) {
                let exercise = Exercise(name: tr(seed.en, seed.nl), muscle: seed.muscle, kind: seed.kind, seedID: seed.id)
                exercise.createdAt = Date(timeIntervalSince1970: 0)
                context.insert(exercise)
            }
            UserDefaults.standard.set(true, forKey: seededKey)
        }
        mergeDuplicates(context)
        try? context.save()
    }

    @MainActor
    static func mergeDuplicates(_ context: ModelContext) {
        let all = (try? context.fetch(FetchDescriptor<Exercise>())) ?? []
        let groups = Dictionary(grouping: all.filter { $0.seedID != nil }, by: { $0.seedID! })
        for (_, group) in groups where group.count > 1 {
            // Same choice on every device: oldest first, then by id.
            let sorted = group.sorted {
                $0.createdAt != $1.createdAt ? $0.createdAt < $1.createdAt : $0.id.uuidString < $1.id.uuidString
            }
            let keep = sorted[0]
            for duplicate in sorted.dropFirst() {
                for entry in duplicate.entries ?? [] { entry.exercise = keep }
                for item in duplicate.routineItems ?? [] { item.exercise = keep }
                if duplicate.isFavorite { keep.isFavorite = true }
                context.delete(duplicate)
            }
        }
    }
}
