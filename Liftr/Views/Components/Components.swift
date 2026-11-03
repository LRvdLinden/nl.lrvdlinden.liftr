import SwiftUI

/// Dark scroll page with a big heading, used by every tab.
struct LiftrPage<Content: View>: View {
    let title: String
    var subtitle: String? = nil
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 2) {
                    if let subtitle {
                        Text(subtitle)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Theme.accent)
                            .textCase(.uppercase)
                    }
                    Text(title)
                        .font(Theme.display(40))
                        .textCase(.uppercase)
                        .foregroundStyle(.white)
                }
                .padding(.top, 8)
                content
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
            .frame(maxWidth: 900)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .background(Theme.background.ignoresSafeArea())
    }
}

struct SectionHeader: View {
    let title: String
    var action: String? = nil
    var onAction: (() -> Void)? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title).liftrTitle(20)
            Spacer()
            if let action, let onAction {
                Button(action, action: onAction)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.accent)
            }
        }
        .padding(.top, 6)
    }
}

/// Small stat tile: big number with a label underneath.
struct StatTile: View {
    let value: String
    let label: String
    var symbol: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let symbol {
                Image(systemName: symbol)
                    .font(.headline)
                    .foregroundStyle(Theme.accent)
            }
            Text(value)
                .font(Theme.display(28))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Theme.muted)
                .textCase(.uppercase)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .liftrCard(padding: 14, radius: 18)
    }
}

struct MuscleIcon: View {
    let muscle: Muscle
    var size: CGFloat = 40

    var body: some View {
        Image(systemName: muscle.symbol)
            .font(.system(size: size * 0.45, weight: .semibold))
            .foregroundStyle(Theme.accent)
            .frame(width: size, height: size)
            .background(Theme.accent.opacity(0.14), in: RoundedRectangle(cornerRadius: size * 0.3, style: .continuous))
    }
}

/// Text field for numbers that accepts both "42,5" and "42.5" and writes through on every keystroke.
struct NumberField: View {
    @Binding var value: Double
    var placeholder: String = "0"
    var decimals = true
    var focus: FocusState<FieldFocus?>.Binding
    var id: FieldFocus

    @State private var text = ""

    var body: some View {
        TextField(placeholder, text: $text)
            .keyboardType(decimals ? .decimalPad : .numberPad)
            .multilineTextAlignment(.center)
            .font(.system(.title3, design: .rounded).weight(.bold))
            .monospacedDigit()
            .focused(focus, equals: id)
            .onAppear { text = format(value) }
            .onChange(of: text) { _, new in
                if let parsed = LiftrFormat.parse(new) {
                    if parsed != value { value = parsed }
                } else if new.isEmpty, value != 0 {
                    value = 0
                }
            }
            .onChange(of: value) { _, new in
                if LiftrFormat.parse(text) != new { text = format(new) }
            }
    }

    private func format(_ v: Double) -> String {
        if v == 0 { return "" }
        return decimals ? LiftrFormat.decimal(v, maxFraction: 2) : String(Int(v))
    }
}

/// Which number field has the keyboard (set + field).
struct FieldFocus: Hashable {
    enum Field: Hashable { case weight, reps, minutes, km }
    let setID: UUID
    let field: Field
}

/// Monday-to-Sunday dots for the current week.
struct WeekDots: View {
    let trained: [Bool]

    private var labels: [String] {
        L10n.isDutch ? ["M", "D", "W", "D", "V", "Z", "Z"] : ["M", "T", "W", "T", "F", "S", "S"]
    }

    private var todayIndex: Int {
        (Calendar.current.component(.weekday, from: Date()) + 5) % 7
    }

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<7, id: \.self) { i in
                VStack(spacing: 6) {
                    ZStack {
                        Circle()
                            .fill(trained[safe: i] == true ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.cardRaised))
                        if trained[safe: i] == true {
                            Image(systemName: "checkmark")
                                .font(.system(size: 13, weight: .heavy))
                                .foregroundStyle(.white)
                        }
                    }
                    .frame(width: 34, height: 34)
                    .overlay(Circle().strokeBorder(i == todayIndex ? Theme.accent : .clear, lineWidth: 2))
                    Text(labels[i])
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(i == todayIndex ? Theme.accent : Theme.muted)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}

extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
