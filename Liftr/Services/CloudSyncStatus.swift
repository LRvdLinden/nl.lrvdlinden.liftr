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
        /// iCloud is busy or there was a conflict; CloudKit retries by itself.
        case retrying
        case offline
        case quotaFull
        /// The CloudKit schema/container isn't set up for this build (e.g. Production schema not deployed).
        case setup
        case failed
        case localOnly
    }

    /// Technical details of the last failed sync (CloudKit error codes), shown on request.
    private(set) var technicalDetails: String?

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
            let error = event.error as NSError?
            MainActor.assumeIsolated {
                self?.handle(ended: ended, succeeded: succeeded, error: error)
            }
        }
    }

    private func handle(ended: Date?, succeeded: Bool, error: NSError?) {
        guard state != .noAccount, state != .restricted else { return }
        if ended == nil {
            if state != .setup, state != .quotaFull { state = .syncing }
        } else if succeeded {
            lastSync = ended
            UserDefaults.standard.set(ended, forKey: "cloud.lastSync")
            technicalDetails = nil
            state = .synced(ended)
            onSync?()
        } else {
            let analysis = Self.analyze(error)
            technicalDetails = analysis.details
            state = analysis.state
            print("Liftr iCloud sync failed: \(analysis.details)")
        }
    }

    // MARK: Error analysis

    /// Reads the CloudKit error behind a failed sync, including the per-record errors of a partial failure.
    static func analyze(_ error: NSError?) -> (state: State, details: String) {
        guard let error else { return (.failed, "unknown") }
        var codes: [CKError.Code] = []
        var messages: [String] = []
        collect(error, into: &codes, messages: &messages)

        let counted = Dictionary(grouping: codes, by: { $0 }).map { "\(name($0.key)) ×\($0.value.count)" }.sorted()
        var details = "\(error.domain) \(error.code)"
        if !counted.isEmpty { details += " – " + counted.joined(separator: ", ") }
        if let first = messages.first(where: { !$0.isEmpty }) { details += "\n" + first }

        let set = Set(codes)
        let state: State
        if set.contains(.quotaExceeded) {
            state = .quotaFull
        } else if set.contains(.notAuthenticated) {
            state = .noAccount
        } else if !set.isDisjoint(with: [.invalidArguments, .serverRejectedRequest, .permissionFailure, .badContainer,
                                         .missingEntitlement, .unknownItem, .incompatibleVersion, .constraintViolation]) {
            state = .setup
        } else if !set.isDisjoint(with: [.networkUnavailable, .networkFailure]) {
            state = .offline
        } else if codes.isEmpty || set.isSubset(of: [.serverRecordChanged, .zoneBusy, .requestRateLimited, .serviceUnavailable,
                                                    .batchRequestFailed, .limitExceeded, .operationCancelled, .changeTokenExpired,
                                                    .partialFailure, .zoneNotFound, .userDeletedZone, .assetFileModified]) {
            state = .retrying
        } else {
            state = .failed
        }
        return (state, details)
    }

    private static func collect(_ error: NSError, into codes: inout [CKError.Code], messages: inout [String], depth: Int = 0) {
        guard depth < 5 else { return }
        if error.domain == CKErrorDomain, let code = CKError.Code(rawValue: error.code) {
            if code != .partialFailure { codes.append(code) }
            messages.append(error.localizedDescription)
            if let partial = error.userInfo[CKPartialErrorsByItemIDKey] as? [AnyHashable: Error] {
                for item in partial.values { collect(item as NSError, into: &codes, messages: &messages, depth: depth + 1) }
            }
        }
        if let underlying = error.userInfo[NSUnderlyingErrorKey] as? NSError {
            collect(underlying, into: &codes, messages: &messages, depth: depth + 1)
        }
    }

    private static func name(_ code: CKError.Code) -> String {
        switch code {
        case .partialFailure: "partialFailure"
        case .serverRecordChanged: "serverRecordChanged"
        case .invalidArguments: "invalidArguments"
        case .serverRejectedRequest: "serverRejectedRequest"
        case .permissionFailure: "permissionFailure"
        case .unknownItem: "unknownItem"
        case .quotaExceeded: "quotaExceeded"
        case .networkUnavailable: "networkUnavailable"
        case .networkFailure: "networkFailure"
        case .notAuthenticated: "notAuthenticated"
        case .zoneBusy: "zoneBusy"
        case .zoneNotFound: "zoneNotFound"
        case .requestRateLimited: "requestRateLimited"
        case .serviceUnavailable: "serviceUnavailable"
        case .batchRequestFailed: "batchRequestFailed"
        case .limitExceeded: "limitExceeded"
        case .badContainer: "badContainer"
        case .missingEntitlement: "missingEntitlement"
        case .constraintViolation: "constraintViolation"
        case .incompatibleVersion: "incompatibleVersion"
        case .assetFileNotFound: "assetFileNotFound"
        case .assetFileModified: "assetFileModified"
        case .operationCancelled: "operationCancelled"
        case .changeTokenExpired: "changeTokenExpired"
        case .userDeletedZone: "userDeletedZone"
        default: "code \(code.rawValue)"
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
        case .retrying: tr("Syncing…", "Bezig met synchroniseren…")
        case .offline: tr("Waiting for internet", "Wacht op internet")
        case .quotaFull: tr("iCloud storage is full", "iCloud-opslag is vol")
        case .setup: tr("iCloud isn't set up yet", "iCloud is nog niet ingesteld")
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
            return trf("Last synced {1}", "Laatst gesynchroniseerd {1}", when)
        case .noAccount:
            return tr("Sign in to iCloud in Settings to sync with your iPad and Apple Watch. Your workouts are saved on this device.",
                      "Log in bij iCloud in Instellingen om te synchroniseren met je iPad en Apple Watch. Je trainingen staan nu alleen op dit apparaat.")
        case .restricted:
            return tr("iCloud is blocked on this device (e.g. Screen Time).", "iCloud is geblokkeerd op dit apparaat (bijv. Schermtijd).")
        case .unavailable:
            return tr("Liftr tries again automatically.", "Liftr probeert het automatisch opnieuw.")
        case .retrying:
            return tr("iCloud is busy. Liftr tries again automatically; your workouts are safe on this device.",
                      "iCloud is even bezig. Liftr probeert het automatisch opnieuw; je trainingen staan veilig op dit apparaat.")
        case .offline:
            return tr("Your workouts sync as soon as you're online again.", "Je trainingen synchroniseren zodra je weer online bent.")
        case .quotaFull:
            return tr("Free up space in iCloud to keep syncing. Your workouts are safe on this device.",
                      "Maak ruimte vrij in iCloud om te blijven synchroniseren. Je trainingen staan veilig op dit apparaat.")
        case .setup:
            return tr("This version of Liftr can't save to iCloud yet. Your workouts are safe on this device and sync once this is fixed.",
                      "Deze versie van Liftr kan nog niet in iCloud opslaan. Je trainingen staan veilig op dit apparaat en synchroniseren zodra dit is opgelost.")
        case .failed:
            return tr("Liftr tries again later. Your workouts are safe on this device.",
                      "Liftr probeert het later opnieuw. Je trainingen staan veilig op dit apparaat.")
        case .localOnly:
            return tr("iCloud could not be started. Restart Liftr to try again.", "iCloud kon niet starten. Start Liftr opnieuw om het nog eens te proberen.")
        }
    }

    var symbol: String {
        switch state {
        case .checking, .syncing: "arrow.triangle.2.circlepath.icloud"
        case .synced: "checkmark.icloud.fill"
        case .noAccount, .restricted, .localOnly: "icloud.slash"
        case .retrying: "arrow.triangle.2.circlepath.icloud"
        case .offline: "icloud.slash"
        case .unavailable, .failed, .quotaFull, .setup: "exclamationmark.icloud"
        }
    }

    var isHealthy: Bool {
        switch state {
        case .synced, .syncing, .checking, .retrying, .offline: true
        default: false
        }
    }
}
