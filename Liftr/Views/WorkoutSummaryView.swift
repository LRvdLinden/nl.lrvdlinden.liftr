import SwiftUI

/// Shown after finishing a workout.
struct WorkoutSummaryView: View {
    let workout: Workout
    @Environment(\.dismiss) private var dismiss
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"

    private var unit: WeightUnit { WeightUnit(rawValue: unitRaw) ?? .kg }

    var body: some View {
        let records = WorkoutStats.personalRecords(in: workout)
        ScrollView {
            VStack(spacing: 20) {
                ZStack {
                    Circle().fill(Theme.accent.opacity(0.15)).frame(width: 150, height: 150)
                    Circle().fill(Theme.accentGradient).frame(width: 104, height: 104)
                    Image(systemName: "checkmark")
                        .font(.system(size: 48, weight: .heavy))
                        .foregroundStyle(.white)
                }
                .padding(.top, 30)

                VStack(spacing: 6) {
                    Text(tr("Well done!", "Goed gedaan!"))
                        .font(Theme.display(42))
                        .textCase(.uppercase)
                        .foregroundStyle(.white)
                    Text(workout.title)
                        .font(.headline)
                        .foregroundStyle(Theme.muted)
                }

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    StatTile(value: LiftrFormat.longDuration(workout.duration), label: tr("Duration", "Duur"), symbol: "clock")
                    StatTile(value: unit.volume(WorkoutStats.volume(workout)), label: tr("Volume", "Volume"), symbol: "scalemass.fill")
                    StatTile(value: "\(WorkoutStats.setCount(workout))", label: tr("Sets", "Sets"), symbol: "square.stack.3d.up.fill")
                    StatTile(value: "\(Int(workout.activeCalories))", label: "kcal", symbol: "flame.fill")
                }

                if !records.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Label(tr("New records", "Nieuwe records"), systemImage: "trophy.fill")
                            .font(Theme.heading(20))
                            .textCase(.uppercase)
                            .foregroundStyle(Theme.accent)
                        ForEach(records, id: \.self) { name in
                            Text("🏆 \(name)")
                                .font(.headline)
                                .foregroundStyle(.white)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .liftrCard()
                }

                if workout.healthKitUUID != nil {
                    Label(tr("Saved in Apple Health", "Opgeslagen in Apple Gezondheid"), systemImage: "heart.fill")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.pink)
                }

                Button(tr("Close", "Sluiten")) { dismiss() }
                    .buttonStyle(LiftrButtonStyle())
                    .padding(.top, 8)
            }
            .padding(20)
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.background.ignoresSafeArea())
    }
}
