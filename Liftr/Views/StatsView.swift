import Charts
import SwiftData
import SwiftUI

/// Progress: per exercise (weight, estimated max, volume, records), per week, and the body.
struct StatsView: View {
    @Query(sort: \Workout.startDate, order: .reverse) private var workouts: [Workout]
    @Query(sort: \Exercise.name) private var exercises: [Exercise]
    @Query(sort: \BodyMeasurement.date) private var measurements: [BodyMeasurement]
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"
    @State private var health = HealthKitManager.shared
    @State private var mode = 0
    @State private var exerciseID: UUID?
    @State private var metric = 0
    @State private var showBody = false

    private var unit: WeightUnit { WeightUnit(rawValue: unitRaw) ?? .kg }

    /// Exercises that have been done at least once, most used first.
    private var used: [Exercise] {
        exercises
            .filter { !WorkoutStats.history(for: $0).isEmpty }
            .sorted { ($0.entries?.count ?? 0) > ($1.entries?.count ?? 0) }
    }

    private var selected: Exercise? {
        used.first { $0.id == exerciseID } ?? used.first
    }

    var body: some View {
        NavigationStack {
            LiftrPage(title: tr("Progress", "Voortgang")) {
                Picker("", selection: $mode) {
                    Text(tr("Exercises", "Oefeningen")).tag(0)
                    Text(tr("Weeks", "Weken")).tag(1)
                    Text(tr("Body", "Lichaam")).tag(2)
                }
                .pickerStyle(.segmented)

                switch mode {
                case 0: exerciseSection
                case 1: weeksSection
                default: bodySection
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $showBody) { BodyMeasurementView() }
        }
    }

    // MARK: Exercises

    @ViewBuilder
    private var exerciseSection: some View {
        if let exercise = selected {
            Menu {
                ForEach(used) { ex in
                    Button(ex.name) { exerciseID = ex.id }
                }
            } label: {
                HStack {
                    MuscleIcon(muscle: exercise.muscle, size: 40)
                    Text(exercise.name)
                        .font(Theme.heading(22))
                        .textCase(.uppercase)
                        .foregroundStyle(.white)
                    Spacer()
                    Image(systemName: "chevron.up.chevron.down").foregroundStyle(Theme.accent)
                }
                .liftrCard(padding: 12)
            }

            let points = WorkoutStats.history(for: exercise)
            if exercise.kind.isCardio {
                Text(tr("Charts are for strength exercises. Cardio sessions are in your history.",
                        "Grafieken zijn er voor krachtoefeningen. Cardio staat in je geschiedenis."))
                    .foregroundStyle(Theme.muted)
                    .liftrCard()
            } else {
                Picker("", selection: $metric) {
                    Text(tr("Heaviest", "Zwaarste")).tag(0)
                    Text(tr("Est. max", "Geschat max")).tag(1)
                    Text("Volume").tag(2)
                }
                .pickerStyle(.segmented)

                VStack(alignment: .leading, spacing: 8) {
                    Text(metricTitle).font(.caption.weight(.bold)).foregroundStyle(Theme.muted).textCase(.uppercase)
                    Chart(points) { point in
                        let value = unit.fromKg(metricValue(point))
                        if metric == 2 {
                            BarMark(x: .value("Date", point.date, unit: .day), y: .value(unit.symbol, value))
                                .foregroundStyle(Theme.accentGradient)
                                .cornerRadius(4)
                        } else {
                            AreaMark(x: .value("Date", point.date), y: .value(unit.symbol, value))
                                .foregroundStyle(LinearGradient(colors: [Theme.accent.opacity(0.35), .clear],
                                                                startPoint: .top, endPoint: .bottom))
                                .interpolationMethod(.monotone)
                            LineMark(x: .value("Date", point.date), y: .value(unit.symbol, value))
                                .foregroundStyle(Theme.accent)
                                .lineStyle(StrokeStyle(lineWidth: 3, lineCap: .round))
                                .interpolationMethod(.monotone)
                            PointMark(x: .value("Date", point.date), y: .value(unit.symbol, value))
                                .foregroundStyle(Theme.accent)
                                .symbolSize(40)
                        }
                    }
                    .chartYAxis {
                        AxisMarks(position: .leading) { _ in
                            AxisGridLine().foregroundStyle(Theme.line)
                            AxisValueLabel().foregroundStyle(Theme.muted)
                        }
                    }
                    .chartXAxis {
                        AxisMarks { _ in
                            AxisValueLabel(format: .dateTime.day().month(.abbreviated)).foregroundStyle(Theme.muted)
                        }
                    }
                    .frame(height: 240)
                }
                .liftrCard()

                if let record = WorkoutStats.records(for: exercise) {
                    SectionHeader(title: tr("Records", "Records"))
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        StatTile(value: unit.format(record.heaviest), label: tr("Heaviest", "Zwaarste"), symbol: "trophy.fill")
                        StatTile(value: unit.format(record.bestOneRepMax), label: tr("Est. 1-rep max", "Geschat 1RM"), symbol: "bolt.fill")
                        StatTile(value: "\(record.mostReps)", label: tr("Most reps", "Meeste herh."), symbol: "repeat")
                        StatTile(value: unit.volume(record.bestVolume), label: tr("Best volume", "Beste volume"), symbol: "scalemass.fill")
                    }
                }
            }

            SectionHeader(title: tr("Sessions", "Sessies"))
            ForEach(points.reversed()) { point in
                HStack {
                    Text(LiftrFormat.day(point.date)).foregroundStyle(.white)
                    Spacer()
                    if !exercise.kind.isCardio {
                        Text("\(unit.format(point.topWeight)) · \(point.bestReps)×")
                            .font(.subheadline.monospacedDigit().weight(.semibold))
                            .foregroundStyle(Theme.accent)
                    }
                }
                .font(.subheadline)
                .liftrCard(padding: 12, radius: 14)
            }
        } else {
            emptyState
        }
    }

    private var metricTitle: String {
        switch metric {
        case 0: tr("Heaviest set per workout", "Zwaarste set per training")
        case 1: tr("Estimated one-rep max", "Geschat maximum (1 herhaling)")
        default: tr("Total volume per workout (weight × reps)", "Totaal volume per training (gewicht × herhalingen)")
        }
    }

    private func metricValue(_ p: WorkoutStats.ExercisePoint) -> Double {
        switch metric {
        case 0: p.topWeight
        case 1: p.oneRepMax
        default: p.volume
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "chart.xyaxis.line")
                .font(.system(size: 44))
                .foregroundStyle(Theme.accent)
            Text(tr("No data yet", "Nog geen gegevens"))
                .liftrTitle(22)
            Text(tr("Finish a workout and your progress shows up here.",
                    "Rond een training af en je vooruitgang verschijnt hier."))
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.muted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
        .liftrCard()
    }

    // MARK: Weeks

    @ViewBuilder
    private var weeksSection: some View {
        let weeks = WorkoutStats.weeklyCounts(workouts)
        VStack(alignment: .leading, spacing: 8) {
            Text(tr("Workouts per week", "Trainingen per week"))
                .font(.caption.weight(.bold)).foregroundStyle(Theme.muted).textCase(.uppercase)
            Chart(weeks) { week in
                BarMark(x: .value("Week", week.weekStart, unit: .weekOfYear), y: .value("#", week.count))
                    .foregroundStyle(Theme.accentGradient)
                    .cornerRadius(5)
            }
            .chartYAxis {
                AxisMarks(position: .leading) { _ in
                    AxisGridLine().foregroundStyle(Theme.line)
                    AxisValueLabel().foregroundStyle(Theme.muted)
                }
            }
            .chartXAxis {
                AxisMarks(values: .stride(by: .weekOfYear, count: 2)) { _ in
                    AxisValueLabel(format: .dateTime.day().month(.abbreviated)).foregroundStyle(Theme.muted)
                }
            }
            .frame(height: 200)
        }
        .liftrCard()

        VStack(alignment: .leading, spacing: 8) {
            Text(tr("Volume per week", "Volume per week") + " (\(unit.symbol))")
                .font(.caption.weight(.bold)).foregroundStyle(Theme.muted).textCase(.uppercase)
            Chart(weeks) { week in
                LineMark(x: .value("Week", week.weekStart), y: .value(unit.symbol, unit.fromKg(week.volume)))
                    .foregroundStyle(Theme.accent)
                    .lineStyle(StrokeStyle(lineWidth: 3))
                    .interpolationMethod(.monotone)
                PointMark(x: .value("Week", week.weekStart), y: .value(unit.symbol, unit.fromKg(week.volume)))
                    .foregroundStyle(Theme.accent)
            }
            .chartYAxis {
                AxisMarks(position: .leading) { _ in
                    AxisGridLine().foregroundStyle(Theme.line)
                    AxisValueLabel().foregroundStyle(Theme.muted)
                }
            }
            .chartXAxis {
                AxisMarks(values: .stride(by: .weekOfYear, count: 2)) { _ in
                    AxisValueLabel(format: .dateTime.day().month(.abbreviated)).foregroundStyle(Theme.muted)
                }
            }
            .frame(height: 200)
        }
        .liftrCard()

        let done = WorkoutStats.finished(workouts)
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            StatTile(value: "\(done.count)", label: tr("Workouts in total", "Trainingen totaal"), symbol: "figure.strengthtraining.traditional")
            StatTile(value: "\(WorkoutStats.weekStreak(workouts))", label: tr("Weeks in a row", "Weken op rij"), symbol: "flame.fill")
            StatTile(value: unit.volume(done.reduce(0) { $0 + WorkoutStats.volume($1) }), label: tr("Lifted in total", "Totaal getild"), symbol: "scalemass.fill")
            StatTile(value: LiftrFormat.longDuration(done.reduce(0) { $0 + $1.duration }), label: tr("Time trained", "Tijd getraind"), symbol: "clock.fill")
        }
    }

    // MARK: Body

    /// Weight points: Apple Health when available, otherwise what was entered in Liftr.
    private var weightPoints: [(date: Date, kg: Double)] {
        if !health.weightHistory.isEmpty { return health.weightHistory }
        return measurements.filter { $0.weightKg > 0 }.map { (date: $0.date, kg: $0.weightKg) }
    }

    private var fatPoints: [(date: Date, percent: Double)] {
        if !health.bodyFatHistory.isEmpty { return health.bodyFatHistory }
        return measurements.filter { $0.bodyFatPercent > 0 }.map { (date: $0.date, percent: $0.bodyFatPercent) }
    }

    @ViewBuilder
    private var bodySection: some View {
        Button {
            showBody = true
        } label: {
            Label(tr("Log weight", "Gewicht invoeren"), systemImage: "plus")
        }
        .buttonStyle(LiftrButtonStyle())

        let weights = weightPoints
        if weights.isEmpty {
            Text(tr("No weight yet. Log it here or connect a smart scale to Apple Health.",
                    "Nog geen gewicht. Voer het hier in of koppel een slimme weegschaal aan Apple Gezondheid."))
                .foregroundStyle(Theme.muted)
                .liftrCard()
        } else {
            HStack(spacing: 12) {
                StatTile(value: unit.format(weights.last!.kg), label: tr("Now", "Nu"), symbol: "scalemass")
                let first = weights.first!.kg
                let change = weights.last!.kg - first
                StatTile(value: (change >= 0 ? "+" : "−") + unit.format(abs(change)),
                         label: tr("Since \(LiftrFormat.shortDay(weights.first!.date))", "Sinds \(LiftrFormat.shortDay(weights.first!.date))"),
                         symbol: change <= 0 ? "arrow.down.right" : "arrow.up.right")
            }
            bodyChart(title: tr("Weight", "Gewicht") + " (\(unit.symbol))",
                      points: weights.map { ($0.date, unit.fromKg($0.kg)) })
        }
        let fats = fatPoints
        if !fats.isEmpty {
            bodyChart(title: tr("Body fat (%)", "Vetpercentage (%)"), points: fats.map { ($0.date, $0.percent) })
        }
        if let lean = health.latestLeanMassKg {
            StatTile(value: unit.format(lean), label: tr("Lean body mass", "Vetvrije massa"), symbol: "figure.arms.open")
        }
    }

    private func bodyChart(title: String, points: [(Date, Double)]) -> some View {
        let values = points.map(\.1)
        let low = (values.min() ?? 0) - 1
        let high = (values.max() ?? 1) + 1
        return VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.caption.weight(.bold)).foregroundStyle(Theme.muted).textCase(.uppercase)
            Chart(Array(points.enumerated()), id: \.offset) { _, point in
                LineMark(x: .value("Date", point.0), y: .value("Value", point.1))
                    .foregroundStyle(Theme.accent)
                    .lineStyle(StrokeStyle(lineWidth: 3, lineCap: .round))
                    .interpolationMethod(.monotone)
                PointMark(x: .value("Date", point.0), y: .value("Value", point.1))
                    .foregroundStyle(Theme.accent)
                    .symbolSize(30)
            }
            .chartYScale(domain: low...high)
            .chartYAxis {
                AxisMarks(position: .leading) { _ in
                    AxisGridLine().foregroundStyle(Theme.line)
                    AxisValueLabel().foregroundStyle(Theme.muted)
                }
            }
            .chartXAxis {
                AxisMarks { _ in
                    AxisValueLabel(format: .dateTime.day().month(.abbreviated)).foregroundStyle(Theme.muted)
                }
            }
            .frame(height: 200)
        }
        .liftrCard()
    }
}
