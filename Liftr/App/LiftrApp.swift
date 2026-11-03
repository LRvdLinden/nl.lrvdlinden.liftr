import SwiftData
import SwiftUI

@main
struct LiftrApp: App {
    let container = LiftrStore.makeContainer()

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
                .tint(Theme.accent)
        }
        .modelContainer(container)
    }
}
