import Foundation
import SwiftData

/// Built-in list of the machines and exercises found in big gym chains: selectorised and plate-loaded
/// machines, smith machine, cables, free weights, functional area and cardio.
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
        .init(id: "hack-squat", en: "Hack squat", nl: "Hack squat", muscle: .legs, kind: .machine),
        .init(id: "pendulum-squat", en: "Pendulum squat", nl: "Pendulum squat", muscle: .legs, kind: .machine),
        .init(id: "standing-leg-curl", en: "Standing leg curl", nl: "Leg curl (staand)", muscle: .legs, kind: .machine),
        .init(id: "seated-calf-raise", en: "Seated calf raise", nl: "Kuitheffen (zittend)", muscle: .legs, kind: .machine),
        .init(id: "leg-press-calf", en: "Calf press on leg press", nl: "Kuitdrukken op leg press", muscle: .legs, kind: .machine),
        .init(id: "hip-thrust-machine", en: "Hip thrust machine", nl: "Hip thrust (apparaat)", muscle: .glutes, kind: .machine),
        .init(id: "cable-kickback", en: "Cable glute kickback", nl: "Kickback aan de kabel", muscle: .glutes, kind: .cable),
        .init(id: "cable-abduction", en: "Cable hip abduction", nl: "Abductie aan de kabel", muscle: .glutes, kind: .cable),
        .init(id: "glute-bridge", en: "Glute bridge", nl: "Glute bridge", muscle: .glutes, kind: .bodyweight),
        .init(id: "front-squat", en: "Front squat", nl: "Front squat", muscle: .legs, kind: .freeWeight),
        .init(id: "sumo-squat", en: "Sumo squat", nl: "Sumo squat", muscle: .legs, kind: .freeWeight),
        .init(id: "bulgarian-split-squat", en: "Bulgarian split squat", nl: "Bulgaarse split squat", muscle: .legs, kind: .freeWeight),
        .init(id: "step-up", en: "Step-ups", nl: "Step-ups", muscle: .legs, kind: .freeWeight),
        .init(id: "smith-lunge", en: "Smith machine lunge", nl: "Lunges in smith machine", muscle: .legs, kind: .machine),
        .init(id: "sumo-deadlift", en: "Sumo deadlift", nl: "Sumo deadlift", muscle: .legs, kind: .freeWeight),
        .init(id: "bodyweight-squat", en: "Bodyweight squat", nl: "Squat (lichaamsgewicht)", muscle: .legs, kind: .bodyweight),
        // Chest
        .init(id: "chest-press", en: "Chest press machine", nl: "Chest press", muscle: .chest, kind: .machine),
        .init(id: "bench-press", en: "Bench press", nl: "Bankdrukken", muscle: .chest, kind: .freeWeight),
        .init(id: "incline-db-press", en: "Incline dumbbell press", nl: "Schuin bankdrukken (dumbbells)", muscle: .chest, kind: .freeWeight),
        .init(id: "pec-deck", en: "Pec deck (butterfly)", nl: "Butterfly (pec deck)", muscle: .chest, kind: .machine),
        .init(id: "cable-fly", en: "Cable fly", nl: "Cable fly", muscle: .chest, kind: .cable),
        .init(id: "push-up", en: "Push-ups", nl: "Push-ups", muscle: .chest, kind: .bodyweight),
        .init(id: "incline-chest-press", en: "Incline chest press machine", nl: "Incline chest press", muscle: .chest, kind: .machine),
        .init(id: "decline-chest-press", en: "Decline chest press machine", nl: "Decline chest press", muscle: .chest, kind: .machine),
        .init(id: "incline-bench-press", en: "Incline bench press", nl: "Schuin bankdrukken (halter)", muscle: .chest, kind: .freeWeight),
        .init(id: "db-bench-press", en: "Dumbbell bench press", nl: "Bankdrukken (dumbbells)", muscle: .chest, kind: .freeWeight),
        .init(id: "smith-bench-press", en: "Smith machine bench press", nl: "Bankdrukken in smith machine", muscle: .chest, kind: .machine),
        .init(id: "db-fly", en: "Dumbbell fly", nl: "Flyes (dumbbells)", muscle: .chest, kind: .freeWeight),
        .init(id: "low-cable-fly", en: "Low-to-high cable fly", nl: "Cable fly (van laag naar hoog)", muscle: .chest, kind: .cable),
        .init(id: "chest-dips", en: "Chest dips", nl: "Dips (borst)", muscle: .chest, kind: .bodyweight),
        // Back
        .init(id: "lat-pulldown", en: "Lat pulldown", nl: "Lat pulldown", muscle: .back, kind: .cable),
        .init(id: "seated-row", en: "Seated row machine", nl: "Seated row", muscle: .back, kind: .machine),
        .init(id: "cable-row", en: "Cable row", nl: "Roeien aan de kabel", muscle: .back, kind: .cable),
        .init(id: "assisted-pull-up", en: "Assisted pull-up", nl: "Assisted pull-up", muscle: .back, kind: .machine),
        .init(id: "back-extension", en: "Back extension", nl: "Rugstrekken", muscle: .back, kind: .bodyweight),
        .init(id: "deadlift", en: "Deadlift", nl: "Deadlift", muscle: .back, kind: .freeWeight),
        .init(id: "db-row", en: "Dumbbell row", nl: "Roeien met dumbbell", muscle: .back, kind: .freeWeight),
        .init(id: "close-grip-pulldown", en: "Close-grip pulldown", nl: "Pulldown (smalle greep)", muscle: .back, kind: .cable),
        .init(id: "high-row", en: "High row machine", nl: "High row", muscle: .back, kind: .machine),
        .init(id: "low-row", en: "Low row machine", nl: "Low row", muscle: .back, kind: .machine),
        .init(id: "t-bar-row", en: "T-bar row", nl: "T-bar row", muscle: .back, kind: .freeWeight),
        .init(id: "barbell-row", en: "Barbell row", nl: "Roeien met halter", muscle: .back, kind: .freeWeight),
        .init(id: "straight-arm-pulldown", en: "Straight-arm pulldown", nl: "Pulldown met gestrekte armen", muscle: .back, kind: .cable),
        .init(id: "pull-up", en: "Pull-ups", nl: "Pull-ups", muscle: .back, kind: .bodyweight),
        .init(id: "chin-up", en: "Chin-ups", nl: "Chin-ups", muscle: .back, kind: .bodyweight),
        .init(id: "lower-back-machine", en: "Lower back machine", nl: "Lower back (apparaat)", muscle: .back, kind: .machine),
        .init(id: "db-shrug", en: "Shrugs", nl: "Shrugs", muscle: .back, kind: .freeWeight),
        // Shoulders
        .init(id: "shoulder-press", en: "Shoulder press machine", nl: "Shoulder press", muscle: .shoulders, kind: .machine),
        .init(id: "db-shoulder-press", en: "Dumbbell shoulder press", nl: "Schouderdrukken (dumbbells)", muscle: .shoulders, kind: .freeWeight),
        .init(id: "lateral-raise", en: "Lateral raise", nl: "Zijwaarts heffen", muscle: .shoulders, kind: .freeWeight),
        .init(id: "rear-delt-fly", en: "Rear delt fly", nl: "Reverse butterfly", muscle: .shoulders, kind: .machine),
        .init(id: "face-pull", en: "Face pull", nl: "Face pull", muscle: .shoulders, kind: .cable),
        .init(id: "lateral-raise-machine", en: "Lateral raise machine", nl: "Zijwaarts heffen (apparaat)", muscle: .shoulders, kind: .machine),
        .init(id: "cable-lateral-raise", en: "Cable lateral raise", nl: "Zijwaarts heffen aan de kabel", muscle: .shoulders, kind: .cable),
        .init(id: "front-raise", en: "Front raise", nl: "Voorwaarts heffen", muscle: .shoulders, kind: .freeWeight),
        .init(id: "arnold-press", en: "Arnold press", nl: "Arnold press", muscle: .shoulders, kind: .freeWeight),
        .init(id: "overhead-press", en: "Barbell overhead press", nl: "Overhead press (halter)", muscle: .shoulders, kind: .freeWeight),
        .init(id: "smith-shoulder-press", en: "Smith machine shoulder press", nl: "Schouderdrukken in smith machine", muscle: .shoulders, kind: .machine),
        .init(id: "upright-row", en: "Upright row", nl: "Upright row", muscle: .shoulders, kind: .freeWeight),
        .init(id: "db-rear-delt-fly", en: "Bent-over rear delt fly", nl: "Reverse fly (dumbbells)", muscle: .shoulders, kind: .freeWeight),
        // Arms
        .init(id: "biceps-curl-machine", en: "Biceps curl machine", nl: "Bicepscurl (apparaat)", muscle: .arms, kind: .machine),
        .init(id: "db-curl", en: "Dumbbell curl", nl: "Bicepscurl (dumbbells)", muscle: .arms, kind: .freeWeight),
        .init(id: "hammer-curl", en: "Hammer curl", nl: "Hammer curl", muscle: .arms, kind: .freeWeight),
        .init(id: "cable-curl", en: "Cable curl", nl: "Bicepscurl aan de kabel", muscle: .arms, kind: .cable),
        .init(id: "triceps-pushdown", en: "Triceps pushdown", nl: "Triceps pushdown", muscle: .arms, kind: .cable),
        .init(id: "triceps-machine", en: "Triceps extension machine", nl: "Triceps extension (apparaat)", muscle: .arms, kind: .machine),
        .init(id: "assisted-dips", en: "Assisted dips", nl: "Assisted dips", muscle: .arms, kind: .machine),
        .init(id: "preacher-curl", en: "Preacher curl", nl: "Preacher curl", muscle: .arms, kind: .machine),
        .init(id: "ez-bar-curl", en: "EZ-bar curl", nl: "Bicepscurl (EZ-stang)", muscle: .arms, kind: .freeWeight),
        .init(id: "concentration-curl", en: "Concentration curl", nl: "Concentratiecurl", muscle: .arms, kind: .freeWeight),
        .init(id: "rope-pushdown", en: "Rope triceps pushdown", nl: "Triceps pushdown (touw)", muscle: .arms, kind: .cable),
        .init(id: "overhead-triceps-cable", en: "Overhead cable triceps extension", nl: "Triceps extension boven het hoofd (kabel)", muscle: .arms, kind: .cable),
        .init(id: "skull-crusher", en: "Skull crusher", nl: "Skull crusher", muscle: .arms, kind: .freeWeight),
        .init(id: "triceps-kickback", en: "Triceps kickback", nl: "Triceps kickback", muscle: .arms, kind: .freeWeight),
        .init(id: "close-grip-bench", en: "Close-grip bench press", nl: "Bankdrukken (smalle greep)", muscle: .arms, kind: .freeWeight),
        .init(id: "bench-dips", en: "Bench dips", nl: "Bankdips", muscle: .arms, kind: .bodyweight),
        // Core
        .init(id: "ab-crunch-machine", en: "Ab crunch machine", nl: "Buikcrunch (apparaat)", muscle: .core, kind: .machine),
        .init(id: "cable-crunch", en: "Cable crunch", nl: "Crunch aan de kabel", muscle: .core, kind: .cable),
        .init(id: "plank", en: "Plank", nl: "Plank", muscle: .core, kind: .bodyweight),
        .init(id: "leg-raise", en: "Hanging leg raise", nl: "Benen heffen (hangend)", muscle: .core, kind: .bodyweight),
        .init(id: "russian-twist", en: "Russian twist", nl: "Russian twist", muscle: .core, kind: .bodyweight),
        .init(id: "rotary-torso", en: "Rotary torso machine", nl: "Rotary torso (apparaat)", muscle: .core, kind: .machine),
        .init(id: "side-plank", en: "Side plank", nl: "Zijplank", muscle: .core, kind: .bodyweight),
        .init(id: "crunch", en: "Crunches", nl: "Crunches", muscle: .core, kind: .bodyweight),
        .init(id: "sit-up", en: "Sit-ups", nl: "Sit-ups", muscle: .core, kind: .bodyweight),
        .init(id: "bicycle-crunch", en: "Bicycle crunch", nl: "Fietscrunch", muscle: .core, kind: .bodyweight),
        .init(id: "captains-chair", en: "Knee raise (captain's chair)", nl: "Knieheffen (dipstation)", muscle: .core, kind: .bodyweight),
        .init(id: "ab-wheel", en: "Ab wheel rollout", nl: "Ab wheel", muscle: .core, kind: .bodyweight),
        .init(id: "mountain-climbers", en: "Mountain climbers", nl: "Mountain climbers", muscle: .core, kind: .bodyweight),
        .init(id: "cable-woodchop", en: "Cable woodchop", nl: "Woodchop aan de kabel", muscle: .core, kind: .cable),
        .init(id: "dead-bug", en: "Dead bug", nl: "Dead bug", muscle: .core, kind: .bodyweight),
        // Full body / functional
        .init(id: "kettlebell-swing", en: "Kettlebell swing", nl: "Kettlebell swing", muscle: .fullBody, kind: .freeWeight),
        .init(id: "kettlebell-goblet-lunge", en: "Kettlebell lunge", nl: "Lunge met kettlebell", muscle: .fullBody, kind: .freeWeight),
        .init(id: "thruster", en: "Thrusters", nl: "Thrusters", muscle: .fullBody, kind: .freeWeight),
        .init(id: "burpee", en: "Burpees", nl: "Burpees", muscle: .fullBody, kind: .bodyweight),
        .init(id: "box-jump", en: "Box jumps", nl: "Box jumps", muscle: .fullBody, kind: .bodyweight),
        .init(id: "battle-ropes", en: "Battle ropes", nl: "Battle ropes", muscle: .fullBody, kind: .bodyweight),
        .init(id: "farmers-walk", en: "Farmer's walk", nl: "Farmer's walk", muscle: .fullBody, kind: .freeWeight),
        .init(id: "medicine-ball-slam", en: "Medicine ball slam", nl: "Medicijnbal slam", muscle: .fullBody, kind: .freeWeight),
        .init(id: "sled-push", en: "Sled push", nl: "Slee duwen", muscle: .fullBody, kind: .freeWeight),
        .init(id: "trx-row", en: "Suspension trainer row", nl: "Roeien met suspension trainer", muscle: .fullBody, kind: .bodyweight),
        // Cardio
        .init(id: "treadmill", en: "Treadmill", nl: "Loopband", muscle: .cardio, kind: .cardio),
        .init(id: "cross-trainer", en: "Cross trainer", nl: "Crosstrainer", muscle: .cardio, kind: .cardio),
        .init(id: "exercise-bike", en: "Exercise bike", nl: "Hometrainer (fiets)", muscle: .cardio, kind: .cardio),
        .init(id: "rowing-machine", en: "Rowing machine", nl: "Roeimachine", muscle: .cardio, kind: .cardio),
        .init(id: "stair-climber", en: "Stair climber", nl: "Traploper", muscle: .cardio, kind: .cardio),
        .init(id: "recumbent-bike", en: "Recumbent bike", nl: "Ligfiets", muscle: .cardio, kind: .cardio),
        .init(id: "spin-bike", en: "Indoor cycling bike", nl: "Spinningfiets", muscle: .cardio, kind: .cardio),
        .init(id: "stepper", en: "Stepper", nl: "Stepper", muscle: .cardio, kind: .cardio),
        .init(id: "incline-walk", en: "Incline treadmill walk", nl: "Wandelen op de loopband (helling)", muscle: .cardio, kind: .cardio),
        .init(id: "ski-erg", en: "Ski ergometer", nl: "Ski-ergometer", muscle: .cardio, kind: .cardio),
        .init(id: "air-bike", en: "Air bike", nl: "Air bike", muscle: .cardio, kind: .cardio),
        .init(id: "jump-rope", en: "Jump rope", nl: "Touwtjespringen", muscle: .cardio, kind: .cardio),
    ]

    /// Adds the built-in exercises that are missing, then merges duplicates that appear when
    /// several devices seeded before iCloud had synced.
    @MainActor
    static func prepare(_ context: ModelContext) {
        let existing = (try? context.fetch(FetchDescriptor<Exercise>())) ?? []
        let known = Set(existing.compactMap(\.seedID))
        // Exercises can only be archived, not deleted, so every missing built-in is simply added.
        // New app versions bring new exercises this way.
        for seed in seeds where !known.contains(seed.id) {
            let exercise = Exercise(name: tr(seed.en, seed.nl), muscle: seed.muscle, kind: seed.kind, seedID: seed.id)
            exercise.createdAt = Date(timeIntervalSince1970: 0)
            context.insert(exercise)
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
