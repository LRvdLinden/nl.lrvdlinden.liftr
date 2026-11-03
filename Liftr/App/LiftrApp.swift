import SwiftData
import SwiftUI

@main
struct LiftrApp: App {
    let container: ModelContainer

    init() {
        let container = LiftrStore.makeContainer()
        self.container = container
        CloudSyncStatus.shared.onSync = {
            ReminderScheduler.reschedule(container.mainContext)
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
                .tint(Theme.accent)
        }
        .modelContainer(container)
    }
}
