import Foundation

/// Liftr speaks Dutch and English. The language follows the app language chosen by the system
/// (Settings → Liftr → Language); everything else falls back to English.
enum L10n {
    static let language: String = {
        let first = Bundle.main.preferredLocalizations.first ?? "en"
        return first.hasPrefix("nl") ? "nl" : "en"
    }()

    static var isDutch: Bool { language == "nl" }

    static var locale: Locale { Locale(identifier: isDutch ? "nl_NL" : "en_GB") }
}

/// Returns the English or Dutch text, whichever matches the app language.
func tr(_ en: String, _ nl: String) -> String {
    L10n.isDutch ? nl : en
}
