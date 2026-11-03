import SwiftUI
import WidgetKit

@main
struct LiftrComplicationsBundle: WidgetBundle {
    var body: some Widget {
        StartComplication()
    }
}

struct StartEntry: TimelineEntry {
    let date: Date
}

struct StartProvider: TimelineProvider {
    func placeholder(in context: Context) -> StartEntry { StartEntry(date: Date()) }

    func getSnapshot(in context: Context, completion: @escaping (StartEntry) -> Void) {
        completion(StartEntry(date: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<StartEntry>) -> Void) {
        completion(Timeline(entries: [StartEntry(date: Date())], policy: .never))
    }
}

/// Watch face complication that opens Liftr to start a workout.
struct StartComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "LiftrStart", provider: StartProvider()) { _ in
            StartComplicationView()
                .containerBackground(for: .widget) { Color.clear }
        }
        .configurationDisplayName("Liftr")
        .description(tr("Start a workout from your watch face.", "Start een training vanaf je wijzerplaat."))
        .supportedFamilies([.accessoryCircular, .accessoryCorner, .accessoryRectangular, .accessoryInline])
    }
}

struct StartComplicationView: View {
    @Environment(\.widgetFamily) private var family

    var body: some View {
        switch family {
        case .accessoryRectangular:
            HStack {
                Image(systemName: "dumbbell.fill").font(.title2).foregroundStyle(Theme.accent)
                VStack(alignment: .leading) {
                    Text("LIFTR").font(.headline.weight(.heavy))
                    Text(tr("Start workout", "Start training")).font(.caption)
                }
            }
        case .accessoryInline:
            Label(tr("Start workout", "Start training"), systemImage: "dumbbell.fill")
        case .accessoryCorner:
            Image(systemName: "dumbbell.fill")
                .font(.title2)
                .foregroundStyle(Theme.accent)
                .widgetLabel("Liftr")
        default:
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "dumbbell.fill")
                    .font(.title2)
                    .foregroundStyle(Theme.accent)
            }
        }
    }
}
