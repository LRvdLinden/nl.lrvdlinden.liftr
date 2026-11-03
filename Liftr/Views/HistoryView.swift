import SwiftData
import SwiftUI

/// Month calendar with a dot on every training day; tap a day to see what was done.
struct HistoryView: View {
    @Query(sort: \Workout.startDate, order: .reverse) private var workouts: [Workout]
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"
    @State private var month = Date()
    @State private var selected = Date()

    private var unit: WeightUnit { WeightUnit(rawValue: unitRaw) ?? .kg }
    private var cal: Calendar { WorkoutStats.calendar }
    private var done: [Workout] { WorkoutStats.finished(workouts) }

    var body: some View {
        NavigationStack {
            LiftrPage(title: tr("History", "Geschiedenis"),
                      subtitle: tr("\(done.count) workouts in total", "\(done.count) trainingen in totaal")) {
                calendarCard
                let dayWorkouts = done.filter { cal.isDate($0.startDate, inSameDayAs: selected) }
                SectionHeader(title: LiftrFormat.day(selected))
                if dayWorkouts.isEmpty {
                    Text(tr("No workout on this day.", "Geen training op deze dag."))
                        .font(.subheadline)
                        .foregroundStyle(Theme.muted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .liftrCard()
                } else {
                    ForEach(dayWorkouts) { workout in
                        NavigationLink(value: workout) {
                            WorkoutRow(workout: workout, unit: unit)
                        }
                        .buttonStyle(.plain)
                    }
                }
                let monthWorkouts = done.filter { cal.isDate($0.startDate, equalTo: month, toGranularity: .month) }
                if !monthWorkouts.isEmpty {
                    SectionHeader(title: month.formatted(.dateTime.month(.wide).year().locale(L10n.locale)))
                    ForEach(monthWorkouts) { workout in
                        NavigationLink(value: workout) {
                            CompactWorkoutRow(workout: workout, unit: unit)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Workout.self) { WorkoutDetailView(workout: $0) }
        }
    }

    private var calendarCard: some View {
        let days = monthDays()
        let trainedDays = Set(done.map { cal.startOfDay(for: $0.startDate) })
        let symbols = L10n.isDutch ? ["MA", "DI", "WO", "DO", "VR", "ZA", "ZO"] : ["MO", "TU", "WE", "TH", "FR", "SA", "SU"]
        return VStack(spacing: 14) {
            HStack {
                Button { shiftMonth(-1) } label: { Image(systemName: "chevron.left") }
                Spacer()
                Text(month.formatted(.dateTime.month(.wide).year().locale(L10n.locale)))
                    .liftrTitle(20)
                Spacer()
                Button { shiftMonth(1) } label: { Image(systemName: "chevron.right") }
            }
            .font(.headline)
            .foregroundStyle(Theme.accent)

            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 8) {
                ForEach(symbols, id: \.self) { s in
                    Text(s).font(.caption2.weight(.heavy)).foregroundStyle(Theme.muted)
                }
                ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                    if let day {
                        let trained = trainedDays.contains(cal.startOfDay(for: day))
                        let isSelected = cal.isDate(day, inSameDayAs: selected)
                        let isToday = cal.isDateInToday(day)
                        Button {
                            selected = day
                        } label: {
                            VStack(spacing: 3) {
                                Text("\(cal.component(.day, from: day))")
                                    .font(.subheadline.weight(trained || isToday ? .heavy : .medium))
                                    .foregroundStyle(isSelected ? .white : (isToday ? Theme.accent : .white.opacity(0.9)))
                                Circle()
                                    .fill(trained ? (isSelected ? Color.white : Theme.accent) : .clear)
                                    .frame(width: 6, height: 6)
                            }
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .background(isSelected ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Color.clear),
                                        in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    } else {
                        Color.clear.frame(height: 44)
                    }
                }
            }
        }
        .liftrCard()
    }

    private func shiftMonth(_ delta: Int) {
        if let next = cal.date(byAdding: .month, value: delta, to: month) { month = next }
    }

    /// Days of the shown month, with leading blanks so Monday is the first column.
    private func monthDays() -> [Date?] {
        guard let interval = cal.dateInterval(of: .month, for: month) else { return [] }
        let firstWeekday = cal.component(.weekday, from: interval.start) // 1 = Sunday
        let leading = (firstWeekday + 5) % 7
        let count = cal.range(of: .day, in: .month, for: month)?.count ?? 30
        var days: [Date?] = Array(repeating: nil, count: leading)
        for d in 0..<count {
            days.append(cal.date(byAdding: .day, value: d, to: interval.start))
        }
        return days
    }
}

struct CompactWorkoutRow: View {
    let workout: Workout
    let unit: WeightUnit

    var body: some View {
        HStack(spacing: 12) {
            VStack(spacing: 0) {
                Text(workout.startDate.formatted(.dateTime.day().locale(L10n.locale)))
                    .font(Theme.display(22))
                    .foregroundStyle(.white)
                Text(workout.startDate.formatted(.dateTime.weekday(.abbreviated).locale(L10n.locale)))
                    .font(.caption2.weight(.bold))
                    .textCase(.uppercase)
                    .foregroundStyle(Theme.accent)
            }
            .frame(width: 44)
            VStack(alignment: .leading, spacing: 2) {
                Text(workout.title)
                    .font(.headline)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text("\(workout.sortedEntries.count) \(tr("exercises", "oefeningen")) · \(unit.volume(WorkoutStats.volume(workout)))")
                    .font(.caption)
                    .foregroundStyle(Theme.muted)
            }
            Spacer()
            Image(systemName: "chevron.right").foregroundStyle(Theme.muted)
        }
        .liftrCard(padding: 12, radius: 16)
    }
}
