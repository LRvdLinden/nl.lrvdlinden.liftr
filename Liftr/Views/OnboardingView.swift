import SwiftUI

/// First-launch welcome: three short pages, ending with the Apple Health connection.
struct OnboardingView: View {
    var onDone: () -> Void
    @State private var page = 0
    @State private var connecting = false

    private struct Page {
        let symbol: String
        let title: String
        let text: String
    }

    private var pages: [Page] {
        [
            Page(symbol: "dumbbell.fill",
                 title: tr("Every rep counts", "Elke herhaling telt"),
                 text: tr("Log your exercises, sets, reps and weight in seconds — no more notes.",
                          "Leg je oefeningen, sets, herhalingen en gewicht vast in een paar tikken. Geen notities meer.")),
            Page(symbol: "chart.line.uptrend.xyaxis",
                 title: tr("See your progress", "Zie je vooruitgang"),
                 text: tr("Liftr shows what you did last time, your records and how much stronger you're getting.",
                          "Liftr laat zien wat je vorige keer deed, je records en hoeveel sterker je wordt.")),
            Page(symbol: "heart.fill",
                 title: tr("Works with Apple Health", "Werkt met Apple Gezondheid"),
                 text: tr("Workouts, calories and weight go to Apple Health. Train with your Apple Watch for heart rate too.",
                          "Trainingen, calorieën en gewicht gaan naar Apple Gezondheid. Train met je Apple Watch voor ook je hartslag.")),
        ]
    }

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Text("LIFTR")
                        .font(Theme.display(28))
                        .foregroundStyle(Theme.accent)
                    Spacer()
                    if page < pages.count - 1 {
                        Button(tr("Skip", "Overslaan")) { withAnimation { page = pages.count - 1 } }
                            .foregroundStyle(Theme.muted)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 12)

                TabView(selection: $page) {
                    ForEach(pages.indices, id: \.self) { i in
                        VStack(spacing: 28) {
                            Spacer()
                            ZStack {
                                Circle().fill(Theme.accent.opacity(0.15)).frame(width: 190, height: 190)
                                Circle().fill(Theme.accentGradient).frame(width: 130, height: 130)
                                Image(systemName: pages[i].symbol)
                                    .font(.system(size: 54, weight: .bold))
                                    .foregroundStyle(.white)
                            }
                            VStack(spacing: 14) {
                                Text(pages[i].title)
                                    .font(Theme.display(38))
                                    .textCase(.uppercase)
                                    .multilineTextAlignment(.center)
                                    .foregroundStyle(.white)
                                Text(pages[i].text)
                                    .font(.title3)
                                    .multilineTextAlignment(.center)
                                    .foregroundStyle(Theme.muted)
                                    .padding(.horizontal, 12)
                            }
                            Spacer()
                        }
                        .padding(.horizontal, 24)
                        .frame(maxWidth: 560)
                        .tag(i)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .always))

                VStack(spacing: 12) {
                    if page == pages.count - 1 {
                        Button {
                            connecting = true
                            Task {
                                await HealthKitManager.shared.requestAuthorization()
                                connecting = false
                                onDone()
                            }
                        } label: {
                            HStack {
                                if connecting { ProgressView().tint(.white) }
                                Text(tr("Connect Apple Health", "Koppel Apple Gezondheid"))
                            }
                        }
                        .buttonStyle(LiftrButtonStyle())
                        .disabled(connecting)

                        Button(tr("Not now", "Nu niet")) { onDone() }
                            .buttonStyle(LiftrButtonStyle(filled: false))
                    } else {
                        Button(tr("Next", "Volgende")) { withAnimation { page += 1 } }
                            .buttonStyle(LiftrButtonStyle())
                    }
                }
                .frame(maxWidth: 480)
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
            }
        }
        .interactiveDismissDisabled()
    }
}
