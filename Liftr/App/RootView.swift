import SwiftData
import SwiftUI

struct RootView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("onboarding.done") private var onboardingDone = false
    @State private var controller = WorkoutController.shared
    @StateObject private var router = AppRouter.shared
    @State private var tab = 0

    var body: some View {
        TabView(selection: $tab) {
            HomeView()
                .tabItem { Label(tr("Today", "Vandaag"), systemImage: "flame.fill") }
                .tag(0)
            HistoryView()
                .tabItem { Label(tr("History", "Geschiedenis"), systemImage: "calendar") }
                .tag(1)
            StatsView()
                .tabItem { Label(tr("Progress", "Voortgang"), systemImage: "chart.line.uptrend.xyaxis") }
                .tag(2)
            LibraryView()
                .tabItem { Label(tr("Library", "Bibliotheek"), systemImage: "dumbbell.fill") }
                .tag(3)
            SettingsView()
                .tabItem { Label(tr("Settings", "Instellingen"), systemImage: "gearshape.fill") }
                .tag(4)
        }
        .environment(controller)
        .fullScreenCover(isPresented: $controller.isPresented) {
            if let workout = controller.active {
                WorkoutSessionView(workout: workout)
                    .environment(controller)
                    .preferredColorScheme(.dark)
            }
        }
        .sheet(item: $controller.finished) { workout in
            WorkoutSummaryView(workout: workout)
                .preferredColorScheme(.dark)
        }
        .fullScreenCover(isPresented: Binding(get: { !onboardingDone }, set: { if !$0 { onboardingDone = true } })) {
            OnboardingView { onboardingDone = true }
                .preferredColorScheme(.dark)
        }
        .reviewPrompt(isCalm: onboardingDone && !controller.isPresented && controller.finished == nil)
        .task {
            ExerciseLibrary.prepare(context)
            controller.restore(context)
            await CloudSyncStatus.shared.refresh()
            ReminderScheduler.reschedule(context)
            await HealthKitManager.shared.refresh()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                ExerciseLibrary.mergeDuplicates(context)
                try? context.save()
                Task { await CloudSyncStatus.shared.refresh() }
                ReminderScheduler.reschedule(context)
            }
        }
        .onChange(of: router.pendingStart) { _, start in
            guard start else { return }
            router.pendingStart = false
            guard onboardingDone else { return }
            controller.start(context)
        }
        .onOpenURL { url in
            // liftr://start from widgets
            if url.host == "start", onboardingDone { controller.start(context) }
            if url.host == "workout", controller.active != nil { controller.isPresented = true }
        }
    }
}
