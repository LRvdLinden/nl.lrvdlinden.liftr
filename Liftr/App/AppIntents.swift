import AppIntents
import Foundation

/// Lets Siri, Shortcuts and the Action button start a workout.
struct StartWorkoutIntent: AppIntent {
    static var title: LocalizedStringResource = "Start workout"
    static var description = IntentDescription("Opens Liftr and starts a new workout.")
    static var openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        AppRouter.shared.pendingStart = true
        return .result()
    }
}

struct LiftrShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: StartWorkoutIntent(),
                    phrases: ["Start a workout in \(.applicationName)",
                              "Start training in \(.applicationName)",
                              "Begin een training in \(.applicationName)"],
                    shortTitle: "Start workout",
                    systemImageName: "dumbbell.fill")
    }
}

/// Small bridge between intents / widget links and the UI.
@MainActor
final class AppRouter: ObservableObject {
    static let shared = AppRouter()
    @Published var pendingStart = false
}
