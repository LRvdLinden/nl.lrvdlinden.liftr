import Charts
import SwiftUI

struct InsightPoint: Identifiable {
    let id = UUID()
    let date: Date
    let value: Double
}

enum InsightRange: String, CaseIterable, Identifiable {
    case month, threeMonths, sixMonths, year, all

    var id: String { rawValue }

    var label: String {
        switch self {
        case .month: tr("1M", "1M")
        case .threeMonths: tr("3M", "3M")
        case .sixMonths: tr("6M", "6M")
        case .year: tr("1Y", "1J")
        case .all: tr("All", "Alles")
        }
    }

    var start: Date? {
        let cal = Calendar.current
        switch self {
        case .month: return cal.date(byAdding: .month, value: -1, to: Date())
        case .threeMonths: return cal.date(byAdding: .month, value: -3, to: Date())
        case .sixMonths: return cal.date(byAdding: .month, value: -6, to: Date())
        case .year: return cal.date(byAdding: .year, value: -1, to: Date())
        case .all: return nil
        }
    }
}

/// Insights-style chart: big value on top, drag a finger over the graph to read any point,
/// pick a period, and min / average / max underneath.
struct InsightChart: View {
    enum Style { case line, bar }

    let title: String
    let unit: String
    let points: [InsightPoint]
    var style: Style = .line
    var decimals = 1
    var defaultRange: InsightRange = .threeMonths
    /// Line charts start the y-axis near the data (weight); bars always start at 0.
    var zeroBased = false

    @State private var range: InsightRange?
    @State private var selectedDate: Date?

    private var activeRange: InsightRange { range ?? defaultRange }

    private var visible: [InsightPoint] {
        let sorted = points.sorted { $0.date < $1.date }
        guard let start = activeRange.start else { return sorted }
        return sorted.filter { $0.date >= start }
    }

    private var selected: InsightPoint? {
        guard let selectedDate else { return nil }
        return visible.min { abs($0.date.timeIntervalSince(selectedDate)) < abs($1.date.timeIntervalSince(selectedDate)) }
    }

    private func format(_ v: Double) -> String {
        LiftrFormat.decimal(v, maxFraction: decimals)
    }

    var body: some View {
        let data = visible
        VStack(alignment: .leading, spacing: 12) {
            header(data)
            if data.isEmpty {
                Text(tr("No data in this period.", "Geen gegevens in deze periode."))
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity, minHeight: 180)
            } else {
                chart(data)
                    .frame(height: 210)
            }
            rangePicker
            if !data.isEmpty { footer(data) }
        }
        .liftrCard()
    }

    // MARK: Header

    private func header(_ data: [InsightPoint]) -> some View {
        let shown = selected ?? data.last
        let first = data.first?.value ?? 0
        let change = (data.last?.value ?? 0) - first
        return VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption.weight(.heavy))
                .textCase(.uppercase)
                .foregroundStyle(Theme.muted)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(shown.map { format($0.value) } ?? "–")
                    .font(Theme.display(36).monospacedDigit())
                    .foregroundStyle(.white)
                    .contentTransition(.numericText())
                Text(unit)
                    .font(.headline)
                    .foregroundStyle(Theme.muted)
                Spacer()
                if selected == nil, data.count > 1, style == .line, change != 0 {
                    Label((change > 0 ? "+" : "−") + format(abs(change)),
                          systemImage: change > 0 ? "arrow.up.right" : "arrow.down.right")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.accent)
                }
            }
            Text(shown.map { LiftrFormat.day($0.date) } ?? " ")
                .font(.caption.weight(.semibold))
                .foregroundStyle(selected == nil ? Theme.muted : Theme.accent)
        }
        .animation(.easeOut(duration: 0.15), value: selected?.id)
    }

    // MARK: Chart

    private func chart(_ data: [InsightPoint]) -> some View {
        let values = data.map(\.value)
        let low = values.min() ?? 0
        let high = values.max() ?? 1
        let pad = max((high - low) * 0.15, high == 0 ? 1 : high * 0.02)
        let domain: ClosedRange<Double> = (style == .bar || zeroBased) ? 0...(high + pad) : (low - pad)...(high + pad)

        return Chart {
            ForEach(data) { point in
                if style == .bar {
                    BarMark(x: .value("Date", point.date, unit: .day), y: .value("Value", point.value))
                        .foregroundStyle(selected == nil || selected?.id == point.id
                                         ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.accent.opacity(0.35)))
                        .cornerRadius(4)
                } else {
                    AreaMark(x: .value("Date", point.date),
                             yStart: .value("Base", domain.lowerBound),
                             yEnd: .value("Value", point.value))
                        .foregroundStyle(LinearGradient(colors: [Theme.accent.opacity(0.45), Theme.accent.opacity(0.02)],
                                                        startPoint: .top, endPoint: .bottom))
                        .interpolationMethod(.catmullRom)
                    LineMark(x: .value("Date", point.date), y: .value("Value", point.value))
                        .foregroundStyle(Theme.accent)
                        .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                        .interpolationMethod(.catmullRom)
                }
            }
            if let selected {
                RuleMark(x: .value("Date", selected.date, unit: style == .bar ? .day : .second))
                    .foregroundStyle(.white.opacity(0.35))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 3]))
                if style == .line {
                    PointMark(x: .value("Date", selected.date), y: .value("Value", selected.value))
                        .foregroundStyle(.white)
                        .symbolSize(70)
                    PointMark(x: .value("Date", selected.date), y: .value("Value", selected.value))
                        .foregroundStyle(Theme.accent)
                        .symbolSize(30)
                }
            } else if style == .line, let last = data.last {
                PointMark(x: .value("Date", last.date), y: .value("Value", last.value))
                    .foregroundStyle(Theme.accent)
                    .symbolSize(40)
            }
        }
        .chartYScale(domain: domain)
        .chartXSelection(value: $selectedDate)
        .chartYAxis {
            AxisMarks(position: .trailing, values: .automatic(desiredCount: 4)) { _ in
                AxisGridLine().foregroundStyle(Theme.line)
                AxisValueLabel().foregroundStyle(Theme.muted)
            }
        }
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                AxisValueLabel(format: activeRange == .year || activeRange == .all
                               ? .dateTime.month(.abbreviated).locale(L10n.locale)
                               : .dateTime.day().month(.abbreviated).locale(L10n.locale))
                    .foregroundStyle(Theme.muted)
            }
        }
        .sensoryFeedback(.selection, trigger: selected?.id)
    }

    // MARK: Period + stats

    private var rangePicker: some View {
        HStack(spacing: 6) {
            ForEach(InsightRange.allCases) { r in
                let on = r == activeRange
                Button {
                    withAnimation(.easeInOut(duration: 0.25)) { range = r }
                } label: {
                    Text(r.label)
                        .font(.caption.weight(.heavy))
                        .frame(maxWidth: .infinity, minHeight: 30)
                        .background(on ? AnyShapeStyle(Theme.accent.opacity(0.2)) : AnyShapeStyle(Theme.cardRaised),
                                    in: Capsule())
                        .foregroundStyle(on ? Theme.accent : Theme.muted)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func footer(_ data: [InsightPoint]) -> some View {
        let values = data.map(\.value)
        let avg = values.reduce(0, +) / Double(max(1, values.count))
        return HStack {
            stat(tr("Min", "Min"), values.min() ?? 0)
            Divider().frame(height: 28).overlay(Theme.line)
            stat(tr("Average", "Gemiddeld"), avg)
            Divider().frame(height: 28).overlay(Theme.line)
            stat(tr("Max", "Max"), values.max() ?? 0)
        }
    }

    private func stat(_ label: String, _ value: Double) -> some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.caption2.weight(.heavy))
                .textCase(.uppercase)
                .foregroundStyle(Theme.muted)
            Text("\(format(value)) \(unit)")
                .font(.subheadline.weight(.bold).monospacedDigit())
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
    }
}
