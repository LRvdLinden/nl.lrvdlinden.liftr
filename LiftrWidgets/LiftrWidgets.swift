import ActivityKit
import SwiftData
import SwiftUI
import WidgetKit

@main
struct LiftrWidgetsBundle: WidgetBundle {
    var body: some Widget {
        WeekWidget()
        LiftrLiveActivity()
    }
}

// MARK: - This week

struct WeekEntry: TimelineEntry {
    let date: Date
    let trained: [Bool]
    let workoutsThisWeek: Int
    let streak: Int
    let lastTitle: String?
    let lastDate: Date?
    let lastSummary: String?
}

struct WeekProvider: TimelineProvider {
    func placeholder(in context: Context) -> WeekEntry {
        WeekEntry(date: Date(), trained: [true, false, true, false, false, false, false], workoutsThisWeek: 2, streak: 6,
                  lastTitle: tr("Legs", "Benen"), lastDate: Date(), lastSummary: "5 \(tr("exercises", "oefeningen"))")
    }

    func getSnapshot(in context: Context, completion: @escaping (WeekEntry) -> Void) {
        Task { @MainActor in completion(context.isPreview ? placeholder(in: context) : load()) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WeekEntry>) -> Void) {
        Task { @MainActor in
            let entry = load()
            // Refresh after midnight so the week dots stay right.
            let tomorrow = Calendar.current.startOfDay(for: Date().addingTimeInterval(86_400)).addingTimeInterval(60)
            completion(Timeline(entries: [entry], policy: .after(tomorrow)))
        }
    }

    @MainActor
    private func load() -> WeekEntry {
        let container = LiftrStore.makeContainer(sync: false, readOnly: true)
        let descriptor = FetchDescriptor<Workout>(sortBy: [SortDescriptor(\.startDate, order: .reverse)])
        let workouts = (try? container.mainContext.fetch(descriptor)) ?? []
        let last = WorkoutStats.finished(workouts).first
        return WeekEntry(date: Date(),
                         trained: WorkoutStats.trainedDaysThisWeek(workouts),
                         workoutsThisWeek: WorkoutStats.workoutsThisWeek(workouts).count,
                         streak: WorkoutStats.weekStreak(workouts),
                         lastTitle: last?.title,
                         lastDate: last?.startDate,
                         lastSummary: last.map { "\($0.sortedEntries.count) \(tr("exercises", "oefeningen")) · \(WeightUnit.current.volume(WorkoutStats.volume($0)))" })
    }
}

struct WeekWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "LiftrWeek", provider: WeekProvider()) { entry in
            WeekWidgetView(entry: entry)
                .containerBackground(for: .widget) { Theme.background }
                .widgetURL(URL(string: "liftr://start"))
        }
        .configurationDisplayName(tr("This week", "Deze week"))
        .description(tr("Your training days, streak and last workout.", "Je trainingsdagen, reeks en laatste training."))
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct WeekWidgetView: View {
    let entry: WeekEntry
    @Environment(\.widgetFamily) private var family

    private var labels: [String] {
        L10n.weekdaySymbols(.veryShort)
    }

    var body: some View {
        switch family {
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                VStack(spacing: 0) {
                    Image(systemName: "flame.fill").font(.caption)
                    Text("\(entry.streak)").font(.title3.weight(.heavy))
                }
            }
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 2) {
                Text("LIFTR").font(.caption2.weight(.heavy))
                Text(trf("{1} this week", "{1} deze week", entry.workoutsThisWeek)).font(.headline)
                Text(trf("{1} weeks in a row", "{1} weken op rij", entry.streak)).font(.caption)
            }
        case .accessoryInline:
            Label(trf("{1} workouts this week", "{1} trainingen deze week", entry.workoutsThisWeek), systemImage: "dumbbell.fill")
        case .systemMedium:
            HStack(spacing: 16) {
                summary
                VStack(alignment: .leading, spacing: 4) {
                    Text(tr("LAST WORKOUT", "LAATSTE TRAINING"))
                        .font(.caption2.weight(.heavy))
                        .foregroundStyle(Theme.muted)
                    if let title = entry.lastTitle, let date = entry.lastDate {
                        Text(title)
                            .font(Theme.heading(18))
                            .textCase(.uppercase)
                            .foregroundStyle(.white)
                            .lineLimit(2)
                        Text(LiftrFormat.shortDay(date))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.accent)
                        if let s = entry.lastSummary {
                            Text(s).font(.caption).foregroundStyle(Theme.muted).lineLimit(1)
                        }
                    } else {
                        Text(tr("No workouts yet", "Nog geen trainingen"))
                            .font(.subheadline)
                            .foregroundStyle(.white)
                    }
                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        default:
            summary
        }
    }

    private var summary: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("LIFTR")
                    .font(Theme.display(16))
                    .foregroundStyle(Theme.accent)
                Spacer()
                Label("\(entry.streak)", systemImage: "flame.fill")
                    .font(.caption.weight(.heavy))
                    .foregroundStyle(entry.streak > 0 ? Theme.accent : Theme.muted)
            }
            Spacer(minLength: 0)
            Text("\(entry.workoutsThisWeek)")
                .font(Theme.display(40))
                .foregroundStyle(.white)
            Text(tr("this week", "deze week"))
                .font(.caption.weight(.bold))
                .textCase(.uppercase)
                .foregroundStyle(Theme.muted)
            HStack(spacing: 3) {
                ForEach(0..<7, id: \.self) { i in
                    VStack(spacing: 2) {
                        Circle()
                            .fill(i < entry.trained.count && entry.trained[i] ? Theme.accent : Theme.cardRaised)
                            .frame(width: 12, height: 12)
                        Text(labels[i]).font(.system(size: 8, weight: .bold)).foregroundStyle(Theme.muted)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }
}

// MARK: - Live Activity

struct LiftrLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: LiftrActivityAttributes.self) { context in
            LockScreenActivityView(context: context)
                .activityBackgroundTint(Theme.background)
                .activitySystemActionForegroundColor(Theme.accent)
                .widgetURL(URL(string: "liftr://workout"))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label {
                        Text(context.state.exerciseName).lineLimit(1)
                    } icon: {
                        Image(systemName: "dumbbell.fill").foregroundStyle(Theme.accent)
                    }
                    .font(.headline)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("\(context.state.setsDone)/\(context.state.totalSets)")
                        .font(.headline.monospacedDigit())
                        .foregroundStyle(Theme.accent)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    if let end = context.state.restEnd, end > Date() {
                        HStack {
                            Text(tr("Rest", "Rust")).foregroundStyle(.secondary)
                            Spacer()
                            Text(timerInterval: Date()...end, countsDown: true)
                                .font(.title2.monospacedDigit().weight(.heavy))
                                .foregroundStyle(Theme.accent)
                                .multilineTextAlignment(.trailing)
                        }
                    } else {
                        HStack {
                            Text(context.attributes.workoutTitle).foregroundStyle(.secondary)
                            Spacer()
                            Text(context.attributes.startDate, style: .timer)
                                .font(.title3.monospacedDigit().weight(.bold))
                                .multilineTextAlignment(.trailing)
                        }
                    }
                }
            } compactLeading: {
                Image(systemName: "dumbbell.fill").foregroundStyle(Theme.accent)
            } compactTrailing: {
                if let end = context.state.restEnd, end > Date() {
                    Text(timerInterval: Date()...end, countsDown: true)
                        .monospacedDigit()
                        .frame(width: 44)
                        .foregroundStyle(Theme.accent)
                } else {
                    Text("\(context.state.setsDone)/\(context.state.totalSets)")
                        .monospacedDigit()
                        .foregroundStyle(Theme.accent)
                }
            } minimal: {
                Image(systemName: "dumbbell.fill").foregroundStyle(Theme.accent)
            }
            .widgetURL(URL(string: "liftr://workout"))
        }
    }
}

struct LockScreenActivityView: View {
    let context: ActivityViewContext<LiftrActivityAttributes>

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle().fill(Theme.accentGradient)
                Image(systemName: "dumbbell.fill").font(.title3.weight(.bold)).foregroundStyle(.white)
            }
            .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text(context.state.exerciseName)
                    .font(Theme.heading(18))
                    .textCase(.uppercase)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(trf("{1} of {2} sets", "{1} van {2} sets", context.state.setsDone, context.state.totalSets))
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                if let end = context.state.restEnd, end > Date() {
                    Text(tr("REST", "RUST")).font(.caption2.weight(.heavy)).foregroundStyle(Theme.muted)
                    Text(timerInterval: Date()...end, countsDown: true)
                        .font(Theme.display(30).monospacedDigit())
                        .foregroundStyle(Theme.accent)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 90, alignment: .trailing)
                } else {
                    Text(tr("TIME", "TIJD")).font(.caption2.weight(.heavy)).foregroundStyle(Theme.muted)
                    Text(context.attributes.startDate, style: .timer)
                        .font(Theme.display(26).monospacedDigit())
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 100, alignment: .trailing)
                }
            }
        }
        .padding(16)
    }
}
