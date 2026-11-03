import SwiftData
import SwiftUI

struct HomeView: View {
    @Environment(\.modelContext) private var context
    @Environment(WorkoutController.self) private var controller
    @Query(sort: \Workout.startDate, order: .reverse) private var workouts: [Workout]
    @Query(sort: \Routine.createdAt) private var routines: [Routine]
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"
    @State private var health = HealthKitManager.shared
    @State private var showBody = false
    @State private var newRoutine = false
    @State private var editing: Routine?
    @State private var showBadges = false
    @AppStorage(UserProfile.nameKey) private var userName = ""

    private var unit: WeightUnit { WeightUnit(rawValue: unitRaw) ?? .kg }
    private var done: [Workout] { WorkoutStats.finished(workouts) }
    private var firstName: String { userName.trimmingCharacters(in: .whitespaces).split(separator: " ").first.map(String.init) ?? "" }

    var body: some View {
        NavigationStack {
            LiftrPage(title: tr("Today", "Vandaag"), subtitle: LiftrFormat.day(Date())) {
                GymPassButton()
            } content: {
                startCard
                weekCard
                statsRow
                if let last = done.first { lastWorkout(last) }
                routinesSection
                bodyCard
                badgesSection
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Workout.self) { WorkoutDetailView(workout: $0) }
            .navigationDestination(isPresented: $showBadges) { BadgesView() }
            .sheet(isPresented: $showBody) { BodyMeasurementView() }
            .sheet(isPresented: $newRoutine) { RoutineEditView(routine: nil) }
            .sheet(item: $editing) { RoutineEditView(routine: $0) }
        }
    }

    // MARK: Start

    private var startCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let active = controller.active {
                Text(tr("Workout in progress", "Training bezig"))
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.white.opacity(0.85))
                    .textCase(.uppercase)
                Text(active.title)
                    .font(Theme.display(30))
                    .textCase(.uppercase)
                    .foregroundStyle(.white)
                Text(active.startDate, style: .timer)
                    .font(.title3.monospacedDigit().weight(.semibold))
                    .foregroundStyle(.white)
                Button(tr("Continue", "Ga verder")) { controller.isPresented = true }
                    .buttonStyle(WhiteButtonStyle())
            } else {
                Text(firstName.isEmpty ? tr("Ready to train?", "Klaar om te trainen?")
                                       : trf("Ready to train, {1}?", "Klaar om te trainen, {1}?", firstName))
                    .font(Theme.display(30))
                    .textCase(.uppercase)
                    .foregroundStyle(.white)
                Text(tr("Start an empty workout and add exercises as you go.",
                        "Start een lege training en voeg oefeningen toe terwijl je bezig bent."))
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.85))
                Button {
                    controller.start(context)
                } label: {
                    Label(tr("Start workout", "Start training"), systemImage: "play.fill")
                }
                .buttonStyle(WhiteButtonStyle())
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(Theme.accentGradient, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(alignment: .topTrailing) {
            Image(systemName: "dumbbell.fill")
                .font(.system(size: 80, weight: .bold))
                .foregroundStyle(.white.opacity(0.12))
                .rotationEffect(.degrees(-30))
                .padding(10)
                .allowsHitTesting(false)
        }
    }

    // MARK: Week

    private var weekCard: some View {
        let streak = WorkoutStats.weekStreak(workouts)
        return VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(tr("This week", "Deze week")).liftrTitle(18)
                    Text(weekRange)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.muted)
                }
                Spacer()
                Label(trf("{1} wk streak", "{1} wk op rij", streak), systemImage: "flame.fill")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(streak > 0 ? Theme.accent : Theme.muted)
            }
            WeekDots(trained: WorkoutStats.trainedDaysThisWeek(workouts))
        }
        .liftrCard()
    }

    /// "3 – 9 Nov"
    private var weekRange: String {
        guard let interval = WorkoutStats.calendar.dateInterval(of: .weekOfYear, for: Date()) else { return "" }
        let end = interval.end.addingTimeInterval(-1)
        let style = Date.FormatStyle.dateTime.day().month(.abbreviated).locale(L10n.locale)
        return "\(interval.start.formatted(style)) – \(end.formatted(style))"
    }

    private var statsRow: some View {
        let week = WorkoutStats.workoutsThisWeek(workouts)
        let month = WorkoutStats.workoutsThisMonth(workouts)
        return HStack(spacing: 12) {
            StatTile(value: "\(week.count)", label: tr("Workouts this week", "Trainingen deze week"), symbol: "calendar")
            StatTile(value: "\(month.count)", label: tr("This month", "Deze maand"), symbol: "calendar.badge.checkmark")
            StatTile(value: unit.volume(week.reduce(0) { $0 + WorkoutStats.volume($1) }),
                     label: tr("Lifted this week", "Getild deze week"), symbol: "scalemass.fill")
        }
    }

    // MARK: Last workout

    private func lastWorkout(_ workout: Workout) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: tr("Last workout", "Laatste training"))
            NavigationLink(value: workout) {
                WorkoutRow(workout: workout, unit: unit)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: Routines

    private var routinesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: tr("My routines", "Mijn schema's"), action: tr("New", "Nieuw")) { newRoutine = true }
            if routines.isEmpty {
                Button {
                    newRoutine = true
                } label: {
                    HStack(spacing: 14) {
                        Image(systemName: "list.bullet.rectangle.portrait")
                            .font(.title2)
                            .foregroundStyle(Theme.accent)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(tr("Make a routine", "Maak een schema"))
                                .font(.headline)
                                .foregroundStyle(.white)
                            Text(tr("E.g. “Monday: legs” – start it with one tap.",
                                    "Bijv. “Maandag: benen” – start hem met één tik."))
                                .font(.subheadline)
                                .foregroundStyle(Theme.muted)
                        }
                        Spacer()
                    }
                    .liftrCard()
                }
                .buttonStyle(.plain)
            } else {
                ScrollView(.horizontal) {
                    HStack(spacing: 12) {
                        ForEach(routines) { routine in
                            RoutineCard(routine: routine) {
                                controller.start(context, routine: routine)
                            } onEdit: {
                                editing = routine
                            }
                        }
                    }
                }
                .scrollIndicators(.hidden)
                .disabled(controller.active != nil)
            }
        }
    }

    // MARK: Body

    private var bodyCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: tr("Body", "Lichaam"), action: tr("Add", "Toevoegen")) { showBody = true }
            HStack(spacing: 12) {
                StatTile(value: health.latestWeightKg.map { unit.format($0) } ?? "–",
                         label: tr("Weight", "Gewicht"), symbol: "scalemass")
                StatTile(value: health.latestBodyFat.map { "\(LiftrFormat.decimal($0))%" } ?? "–",
                         label: tr("Body fat", "Vetpercentage"), symbol: "percent")
                StatTile(value: health.restingHeartRate.map { "\(Int($0))" } ?? "–",
                         label: tr("Resting heart rate", "Rusthartslag"), symbol: "heart.fill")
            }
        }
    }

    // MARK: Badges

    private var badgesSection: some View {
        let badges = WorkoutStats.badges(workouts)
        let earned = badges.filter(\.achieved).sorted { ($0.date ?? .distantPast) > ($1.date ?? .distantPast) }
        let next = badges.filter { !$0.achieved }.sorted { $0.progress > $1.progress }
        return VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: trf("Badges {1}/{2}", "Badges {1}/{2}", earned.count, badges.count),
                          action: tr("All", "Alle")) { showBadges = true }
            ScrollView(.horizontal) {
                HStack(spacing: 12) {
                    ForEach(Array((earned + next).prefix(14))) { badge in
                        BadgeView(badge: badge)
                    }
                }
                .padding(.vertical, 2)
            }
            .scrollIndicators(.hidden)
        }
    }
}

struct WhiteButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.heading(18))
            .textCase(.uppercase)
            .foregroundStyle(Theme.accentDeep)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(.white, in: Capsule())
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}

struct WorkoutRow: View {
    let workout: Workout
    let unit: WeightUnit

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(workout.title)
                        .font(Theme.heading(20))
                        .textCase(.uppercase)
                        .foregroundStyle(.white)
                    Text("\(LiftrFormat.day(workout.startDate)) · \(LiftrFormat.time(workout.startDate))")
                        .font(.caption)
                        .foregroundStyle(Theme.muted)
                }
                Spacer()
                if workout.source == "watch" {
                    Image(systemName: "applewatch").foregroundStyle(Theme.muted)
                }
                if workout.healthKitUUID != nil {
                    Image(systemName: "heart.fill").foregroundStyle(.pink)
                }
                Image(systemName: "chevron.right").foregroundStyle(Theme.muted)
            }
            HStack(spacing: 16) {
                Label(LiftrFormat.longDuration(workout.duration), systemImage: "clock")
                Label(trf("{1} sets", "{1} sets", WorkoutStats.setCount(workout)), systemImage: "square.stack.3d.up")
                Label(unit.volume(WorkoutStats.volume(workout)), systemImage: "scalemass")
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(Theme.muted)
            let names = workout.sortedEntries.compactMap { $0.exercise?.name }
            if !names.isEmpty {
                Text(names.joined(separator: " · "))
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.85))
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .liftrCard()
    }
}

struct RoutineCard: View {
    let routine: Routine
    var onStart: () -> Void
    var onEdit: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(routine.name)
                    .font(Theme.heading(18))
                    .textCase(.uppercase)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Spacer()
                Button(action: onEdit) {
                    Image(systemName: "ellipsis.circle").foregroundStyle(Theme.muted)
                }
            }
            Text(trf("{1} exercises", "{1} oefeningen", routine.sortedItems.count))
                .font(.caption.weight(.semibold))
                .foregroundStyle(Theme.muted)
            Text(routine.sortedItems.compactMap { $0.exercise?.name }.joined(separator: ", "))
                .font(.caption)
                .foregroundStyle(.white.opacity(0.8))
                .lineLimit(2, reservesSpace: true)
            Button(action: onStart) {
                Label(tr("Start", "Start"), systemImage: "play.fill")
                    .font(.subheadline.weight(.heavy))
                    .textCase(.uppercase)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .background(Theme.accentGradient, in: Capsule())
                    .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
        }
        .frame(width: 220)
        .liftrCard(padding: 14)
    }
}

