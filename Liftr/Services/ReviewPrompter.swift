import SwiftUI
import StoreKit

/// When Liftr may ask for an App Store rating (per device, stored in UserDefaults – not synced):
/// the first time 4 hours after first use, then at most once a week, and only at a calm moment
/// (a few seconds after opening; never during onboarding or a running workout).
enum ReviewPrompter {
    private static let firstUseKey = "review.firstUseDate"
    private static let lastAskKey = "review.lastRequestDate"
    static let firstDelay: TimeInterval = 4 * 60 * 60
    static let interval: TimeInterval = 7 * 24 * 60 * 60

    static func registerUse(now: Date = .now) {
        let d = UserDefaults.standard
        if d.object(forKey: firstUseKey) == nil { d.set(now, forKey: firstUseKey) }
    }

    static func isEligible(now: Date = .now) -> Bool {
        let d = UserDefaults.standard
        guard let first = d.object(forKey: firstUseKey) as? Date,
              now.timeIntervalSince(first) >= firstDelay else { return false }
        if let last = d.object(forKey: lastAskKey) as? Date,
           now.timeIntervalSince(last) < interval { return false }
        return true
    }

    static func markRequested(now: Date = .now) {
        UserDefaults.standard.set(now, forKey: lastAskKey)
    }
}

struct ReviewPromptModifier: ViewModifier {
    let isCalm: Bool
    @Environment(\.requestReview) private var requestReview
    @Environment(\.scenePhase) private var scenePhase
    @State private var task: Task<Void, Never>?

    func body(content: Content) -> some View {
        content
            .onAppear { ReviewPrompter.registerUse() }
            .onChange(of: scenePhase) { _, phase in
                phase == .active ? schedule() : task?.cancel()
            }
            .onChange(of: isCalm) { _, calm in calm ? schedule() : task?.cancel() }
            .task { schedule() }
    }

    private func schedule() {
        task?.cancel()
        guard isCalm, ReviewPrompter.isEligible() else { return }
        task = Task { @MainActor in
            try? await Task.sleep(for: .seconds(4))
            guard !Task.isCancelled, isCalm, scenePhase == .active,
                  ReviewPrompter.isEligible() else { return }
            ReviewPrompter.markRequested()
            requestReview()
        }
    }
}

extension View {
    func reviewPrompt(isCalm: Bool) -> some View {
        modifier(ReviewPromptModifier(isCalm: isCalm))
    }
}
