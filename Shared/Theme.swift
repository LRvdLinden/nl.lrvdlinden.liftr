import SwiftUI

/// Liftr's look: near-black surfaces, one loud orange, heavy condensed headings.
enum Theme {
    static let accent = Color(red: 1.0, green: 0.42, blue: 0.0)          // #FF6B00
    static let accentDeep = Color(red: 0.90, green: 0.33, blue: 0.0)     // #E65400
    static let background = Color(red: 0.055, green: 0.055, blue: 0.063) // #0E0E10
    static let card = Color(red: 0.106, green: 0.106, blue: 0.118)       // #1B1B1E
    static let cardRaised = Color(red: 0.153, green: 0.153, blue: 0.165) // #27272A
    static let line = Color.white.opacity(0.08)
    static let text = Color.white
    static let muted = Color(red: 0.62, green: 0.62, blue: 0.66)
    static let success = Color(red: 0.20, green: 0.80, blue: 0.45)

    static let accentGradient = LinearGradient(colors: [accent, accentDeep],
                                               startPoint: .topLeading, endPoint: .bottomTrailing)

    /// Big, heavy, condensed heading in capitals.
    static func display(_ size: CGFloat) -> Font {
        .system(size: size, weight: .black).width(.condensed)
    }

    static func heading(_ size: CGFloat = 20) -> Font {
        .system(size: size, weight: .heavy).width(.condensed)
    }
}

extension View {
    /// Rounded dark card.
    func liftrCard(padding: CGFloat = 16, radius: CGFloat = 20) -> some View {
        self.padding(padding)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
    }

    /// Section title in Liftr style: condensed, capitals.
    func liftrTitle(_ size: CGFloat = 22) -> some View {
        self.font(Theme.heading(size)).textCase(.uppercase).foregroundStyle(Theme.text)
    }
}

/// Big orange call-to-action.
struct LiftrButtonStyle: ButtonStyle {
    var filled = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.heading(18))
            .textCase(.uppercase)
            .foregroundStyle(filled ? Color.white : Theme.accent)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 15)
            .padding(.horizontal, 16)
            .background {
                if filled {
                    Capsule().fill(Theme.accentGradient)
                } else {
                    Capsule().strokeBorder(Theme.accent, lineWidth: 2)
                }
            }
            .opacity(configuration.isPressed ? 0.75 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}
