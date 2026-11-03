#if DEBUG
import CloudKit
import CoreData
import Foundation
import SwiftData

/// Developer-only text, Dutch or English (Debug builds only, not in the translation tables).
func devText(_ en: String, _ nl: String) -> String { L10n.isDutch ? nl : en }

/// Development only: pushes the complete SwiftData model to the CloudKit **Development** schema, so every
/// record type (CD_Exercise, CD_Workout, CD_GymPass, …) exists before any data is saved.
/// After that: CloudKit Console → Development → Deploy Schema Changes… to make it available in Production
/// (TestFlight / App Store builds).
///
/// Runs once per build number in Debug builds (run from Xcode). Release builds never include this.
enum CloudSchema {
    private static var buildKey: String {
        "cloud.schemaInitialized." + (Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0")
    }

    static var isDone: Bool { UserDefaults.standard.bool(forKey: buildKey) }

    /// Starts in the background; reports the outcome to CloudSyncStatus.
    @MainActor
    static func initializeIfNeeded(force: Bool = false) {
        guard LiftrStore.usesCloudKit, force || !isDone else { return }
        CloudSyncStatus.shared.schemaMessage = devText("Creating iCloud schema…", "iCloud-schema aanmaken…")
        Task.detached(priority: .utility) {
            let status = try? await CKContainer(identifier: LiftrStore.cloudContainer).accountStatus()
            guard status == .available else {
                await MainActor.run {
                    CloudSyncStatus.shared.schemaMessage = devText(
                        "Can't create the iCloud schema: this device isn't signed in to iCloud (or iCloud is off for Liftr). Simulator: Settings → Sign in to your iPhone. Then run again.",
                        "Kan het iCloud-schema niet aanmaken: dit apparaat is niet bij iCloud ingelogd (of iCloud staat uit voor Liftr). Simulator: Instellingen → Log in op je iPhone. Daarna opnieuw draaien.")
                }
                return
            }
            let result: Result<Void, Error> = Result { try initialize() }
            await MainActor.run {
                switch result {
                case .success:
                    UserDefaults.standard.set(true, forKey: buildKey)
                    CloudSyncStatus.shared.schemaMessage = devText("iCloud schema created in Development. Deploy it to Production in the CloudKit Console.",
                                                              "iCloud-schema aangemaakt in Development. Zet het in de CloudKit Console door naar Production.")
                    print("Liftr: CloudKit schema initialized")
                case .failure(let error):
                    let analysis = CloudSyncStatus.analyze(error as NSError)
                    CloudSyncStatus.shared.schemaMessage = devText("Creating the iCloud schema failed. Tap for technical details.",
                                                              "iCloud-schema aanmaken mislukt. Tik voor technische details.")
                    CloudSyncStatus.shared.reportSchemaFailure(analysis.details)
                    print("Liftr: CloudKit schema initialization failed: \(error)")
                }
            }
        }
    }

    /// Apple's recommended way for SwiftData: load the same model in an NSPersistentCloudKitContainer
    /// (on a throwaway store file) and call initializeCloudKitSchema().
    private static func initialize() throws {
        guard let model = NSManagedObjectModel.makeManagedObjectModel(for: LiftrSchema.models) else {
            throw NSError(domain: "Liftr", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not build the managed object model"])
        }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("LiftrSchema-\(UUID().uuidString).sqlite")
        let description = NSPersistentStoreDescription(url: url)
        description.cloudKitContainerOptions = NSPersistentCloudKitContainerOptions(containerIdentifier: LiftrStore.cloudContainer)
        description.shouldAddStoreAsynchronously = false
        description.setOption(true as NSNumber, forKey: NSPersistentHistoryTrackingKey)

        let container = NSPersistentCloudKitContainer(name: "LiftrSchema", managedObjectModel: model)
        container.persistentStoreDescriptions = [description]
        var loadError: Error?
        container.loadPersistentStores { _, error in loadError = error }
        if let loadError { throw loadError }

        try container.initializeCloudKitSchema(options: [])

        for store in container.persistentStoreCoordinator.persistentStores {
            try? container.persistentStoreCoordinator.remove(store)
        }
        for suffix in ["", "-shm", "-wal"] {
            try? FileManager.default.removeItem(at: URL(fileURLWithPath: url.path + suffix))
        }
    }
}
#endif
