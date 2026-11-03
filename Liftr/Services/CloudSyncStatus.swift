import CloudKit
import CoreData
import Foundation
import Observation

/// Shows whether iCloud sync works: the iCloud account state plus the last sync event.
@MainActor
@Observable
final class CloudSyncStatus {
    static let shared = CloudSyncStatus()

    enum State: Equatable {
        case checking
        case synced(Date?)
        case syncing
        case noAccount
        case restricted
        case unavailable
        case failed(String)
        case localOnly
    }

    private(set) var state: State = .checking
    private(set) var lastSync: Date? = UserDefaults.standard.object(forKey: "cloud.lastSync") as? Date
    private var observer: NSObjectProtocol?
    /// Called after every successful sync (e.g. to re-plan reminders when a Watch workout arrives).
    var onSync: (() -> Void)?

    private init() {
        // SwiftData syncs through NSPersistentCloudKitContainer, which posts these events.
        observer = NotificationCenter.default.addObserver(forName: NSPersistentCloudKitContainer.eventChangedNotification,
                                                          object: nil, queue: .main) { [weak self] note in
            guard let event = note.userInfo?[NSPersistentCloudKitContainer.eventNotificationUserInfoKey]
                    as? NSPersistentCloudKitContainer.Event else { return }
            let succeeded = event.succeeded
            let ended = event.endDate
            let message = event.error?.localizedDescription
            MainActor.assumeIsolated {
                self?.handle(ended: ended, succeeded: succeeded, error: message)
            }
        }
    }

    private func handle(ended: Date?, succeeded: Bool, error: String?) {
        guard state != .noAccount, state != .restricted else { return }
        if ended == nil {
            state = .syncing
        } else if succeeded {
            lastSync = ended
            UserDefaults.standard.set(ended, forKey: "cloud.lastSync")
            state = .synced(ended)
            onSync?()
        } else {
            state = .failed(error ?? tr("Unknown error", "Onbekende fout"))
        }
    }

    func refresh() async {
        guard LiftrStore.usesCloudKit else {
            state = .localOnly
            return
        }
        do {
            let status = try await CKContainer(identifier: LiftrStore.cloudContainer).accountStatus()
            switch status {
            case .available:
                if state == .checking || state == .noAccount || state == .restricted || state == .unavailable {
                    state = .synced(lastSync)
                }
            case .noAccount: state = .noAccount
            case .restricted: state = .restricted
            case .temporarilyUnavailable: state = .unavailable
            default: state = .unavailable
            }
        } catch {
            state = .unavailable
        }
    }

    // MARK: Display

    var title: String {
        switch state {
        case .checking: tr("Checking iCloud…", "iCloud controleren…")
        case .synced: tr("Synced with iCloud", "Gesynchroniseerd met iCloud")
        case .syncing: tr("Syncing…", "Bezig met synchroniseren…")
        case .noAccount: tr("iCloud is off", "iCloud staat uit")
        case .restricted: tr("iCloud is restricted", "iCloud is beperkt")
        case .unavailable: tr("iCloud temporarily unavailable", "iCloud tijdelijk niet bereikbaar")
        case .failed: tr("Sync problem", "Probleem met synchroniseren")
        case .localOnly: tr("Saved on this device only", "Alleen op dit apparaat bewaard")
        }
    }

    var detail: String {
        switch state {
        case .checking, .syncing:
            return ""
        case .synced(let date):
            guard let date else { return tr("Your workouts are kept in your iCloud.", "Je trainingen worden in je iCloud bewaard.") }
            let when = date.formatted(.relative(presentation: .named).locale(L10n.locale))
            return tr("Last synced \(when)", "Laatst gesynchroniseerd \(when)")
        case .noAccount:
            return tr("Sign in to iCloud in Settings to sync with your iPad and Apple Watch. Your workouts are saved on this device.",
                      "Log in bij iCloud in Instellingen om te synchroniseren met je iPad en Apple Watch. Je trainingen staan nu alleen op dit apparaat.")
        case .restricted:
            return tr("iCloud is blocked on this device (e.g. Screen Time).", "iCloud is geblokkeerd op dit apparaat (bijv. Schermtijd).")
        case .unavailable:
            return tr("Liftr tries again automatically.", "Liftr probeert het automatisch opnieuw.")
        case .failed(let message):
            return message
        case .localOnly:
            return tr("iCloud could not be started. Restart Liftr to try again.", "iCloud kon niet starten. Start Liftr opnieuw om het nog eens te proberen.")
        }
    }

    var symbol: String {
        switch state {
        case .checking, .syncing: "arrow.triangle.2.circlepath.icloud"
        case .synced: "checkmark.icloud.fill"
        case .noAccount, .restricted, .localOnly: "icloud.slash"
        case .unavailable, .failed: "exclamationmark.icloud"
        }
    }

    var isHealthy: Bool {
        switch state {
        case .synced, .syncing, .checking: true
        default: false
        }
    }
}
