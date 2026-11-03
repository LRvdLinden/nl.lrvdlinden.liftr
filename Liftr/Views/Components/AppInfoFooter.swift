import SwiftUI
import UIKit

/// App icon, "Version: 1.0.0 (1)" and "© <year> Liftr" at the very bottom of Settings.
struct AppInfoFooter: View {
    @ScaledMetric(relativeTo: .body) private var iconSize: CGFloat = 64

    private let info = Bundle.main.infoDictionary ?? [:]
    private var appName: String {
        info["CFBundleDisplayName"] as? String ?? info["CFBundleName"] as? String ?? ""
    }
    private var version: String { info["CFBundleShortVersionString"] as? String ?? "–" }
    private var build: String { info["CFBundleVersion"] as? String ?? "–" }
    /// String(...) avoids "2.026" from number formatting.
    private var year: String { String(Calendar.current.component(.year, from: .now)) }

    var body: some View {
        VStack(spacing: 10) {
            if let icon = Self.appIcon {
                Image(uiImage: icon)
                    .resizable()
                    .frame(width: iconSize, height: iconSize)
                    .clipShape(RoundedRectangle(cornerRadius: iconSize * 0.2237, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: iconSize * 0.2237, style: .continuous)
                        .strokeBorder(.primary.opacity(0.08), lineWidth: 0.5))
                    .accessibilityHidden(true)
                    .padding(.bottom, 6)
            }
            Text("\(AppInfoText.current.version): \(version) (\(build))")
            Text("© \(year) \(appName)")
        }
        .font(.subheadline)
        .foregroundStyle(.secondary)
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .accessibilityElement(children: .combine)
    }

    /// The "AppIconDisplay" image set first, otherwise the primary icon from the bundle.
    static var appIcon: UIImage? {
        if let img = UIImage(named: "AppIconDisplay") { return img }
        guard let icons = Bundle.main.infoDictionary?["CFBundleIcons"] as? [String: Any],
              let primary = icons["CFBundlePrimaryIcon"] as? [String: Any],
              let files = primary["CFBundleIconFiles"] as? [String],
              let name = files.last else { return nil }
        return UIImage(named: name)
    }
}

struct AppInfoText {
    let version: String
    static var current: AppInfoText { all[L10n.language] ?? all["en"]! }
    static let all: [String: AppInfoText] = [
        "nl": .init(version: "Versie"),
        "en": .init(version: "Version"),
        "bg": .init(version: "Версия"),
        "ca": .init(version: "Versió"),
        "cs": .init(version: "Verze"),
        "da": .init(version: "Version"),
        "de": .init(version: "Version"),
        "el": .init(version: "Έκδοση"),
        "es": .init(version: "Versión"),
        "et": .init(version: "Versioon"),
        "fi": .init(version: "Versio"),
        "fr": .init(version: "Version"),
        "ga": .init(version: "Leagan"),
        "hr": .init(version: "Verzija"),
        "hu": .init(version: "Verzió"),
        "is": .init(version: "Útgáfa"),
        "it": .init(version: "Versione"),
        "lt": .init(version: "Versija"),
        "lv": .init(version: "Versija"),
        "mk": .init(version: "Верзија"),
        "mt": .init(version: "Verżjoni"),
        "nb": .init(version: "Versjon"),
        "pl": .init(version: "Wersja"),
        "pt-PT": .init(version: "Versão"),
        "ro": .init(version: "Versiune"),
        "ru": .init(version: "Версия"),
        "sk": .init(version: "Verzia"),
        "sl": .init(version: "Različica"),
        "sq": .init(version: "Versioni"),
        "sr": .init(version: "Верзија"),
        "sv": .init(version: "Version"),
        "tr": .init(version: "Sürüm"),
        "uk": .init(version: "Версія"),
    ]
}
