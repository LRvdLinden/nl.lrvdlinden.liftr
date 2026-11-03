import Foundation
import SwiftData

enum LiftrStore {
    static let appGroup = "group.nl.lrvdlinden.liftr"
    static let cloudContainer = "iCloud.nl.lrvdlinden.liftr"

    /// The app's container: synced through the private iCloud database, stored in the app group so
    /// widgets can read it. Falls back to a local store when iCloud is not available.
    static func makeContainer(sync: Bool = true, readOnly: Bool = false) -> ModelContainer {
        let schema = Schema(LiftrSchema.models)
        if sync {
            let config = ModelConfiguration("Liftr", schema: schema, allowsSave: !readOnly,
                                            groupContainer: .identifier(appGroup),
                                            cloudKitDatabase: .private(cloudContainer))
            if let container = try? ModelContainer(for: schema, configurations: [config]) {
                return container
            }
        }
        let local = ModelConfiguration("Liftr", schema: schema, allowsSave: !readOnly,
                                       groupContainer: .identifier(appGroup),
                                       cloudKitDatabase: .none)
        if let container = try? ModelContainer(for: schema, configurations: [local]) {
            return container
        }
        let memory = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        // In-memory always works; this is a last resort so the app never crashes on launch.
        return try! ModelContainer(for: schema, configurations: [memory])
    }
}
