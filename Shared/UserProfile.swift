import Foundation

/// The user's own name, asked on the welcome screen (or later in Settings).
/// Used in greetings and on the back of earned badges.
enum UserProfile {
    static let nameKey = "user.name"

    static var name: String {
        (UserDefaults.standard.string(forKey: nameKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// The first word of the name, for greetings ("Léonie" from "Léonie de Vries").
    static var firstName: String {
        name.split(separator: " ").first.map(String.init) ?? ""
    }
}
