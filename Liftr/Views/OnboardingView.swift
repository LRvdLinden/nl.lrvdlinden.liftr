import SwiftUI

/// First-launch welcome: a few short pages with your name, ending with the Apple Health connection.
struct OnboardingView: View {
    var onDone: () -> Void
    @State private var page = 0
    @State private var connecting = false
    @AppStorage(UserProfile.nameKey) private var name = ""
    @FocusState private var nameFocused: Bool
    /// Landscape on iPhone (and some iPhone Duo poses) has little height: smaller artwork.
    @Environment(\.verticalSizeClass) private var verticalSize
    private var compact: Bool { verticalSize == .compact }

    /// The page that asks for your name.
    private let namePage = 1

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
            Page(symbol: "person.fill",
                 title: tr("What's your name?", "Hoe heet je?"),
                 text: tr("Liftr uses it to greet you and puts it on the back of the badges you earn. You can change it later in Settings.",
                          "Liftr gebruikt hem om je te begroeten en zet hem op de achterkant van de badges die je haalt. Je kunt hem later wijzigen in Instellingen.")),
            Page(symbol: "chart.line.uptrend.xyaxis",
                 title: tr("See your progress", "Zie je vooruitgang"),
                 text: tr("Liftr shows what you did last time, your records and how much stronger you're getting.",
                          "Liftr laat zien wat je vorige keer deed, je records en hoeveel sterker je wordt.")),
            Page(symbol: "heart.fill",
                 title: tr("Apple Health", "Apple Gezondheid"),
                 text: tr("Liftr can save your workouts and weight in Apple Health and read your weight, body fat and heart rate. On the next screen you choose what to allow. You can change this at any time in the Settings app.",
                          "Liftr kan je trainingen en gewicht bewaren in Apple Gezondheid en je gewicht, vetpercentage en hartslag lezen. In het volgende scherm kies je zelf wat je toestaat. Je kunt dit altijd wijzigen in de app Instellingen.")),
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
                        VStack(spacing: compact ? 14 : 28) {
                            Spacer(minLength: 0)
                            ZStack {
                                Circle().fill(Theme.accent.opacity(0.15)).frame(width: compact ? 96 : 190, height: compact ? 96 : 190)
                                Circle().fill(Theme.accentGradient).frame(width: compact ? 66 : 130, height: compact ? 66 : 130)
                                Image(systemName: pages[i].symbol)
                                    .font(.system(size: compact ? 28 : 54, weight: .bold))
                                    .foregroundStyle(.white)
                            }
                            VStack(spacing: 14) {
                                Text(pages[i].title)
                                    .font(Theme.display(compact ? 28 : 38))
                                    .textCase(.uppercase)
                                    .multilineTextAlignment(.center)
                                    .foregroundStyle(.white)
                                Text(pages[i].text)
                                    .font(compact ? .body : .title3)
                                    .multilineTextAlignment(.center)
                                    .foregroundStyle(Theme.muted)
                                    .padding(.horizontal, 12)
                                if i == namePage {
                                    TextField(tr("Your name", "Je naam"), text: $name)
                                        .textContentType(.givenName)
                                        .textInputAutocapitalization(.words)
                                        .autocorrectionDisabled()
                                        .submitLabel(.next)
                                        .focused($nameFocused)
                                        .onSubmit { withAnimation { page += 1 } }
                                        .font(.title2.weight(.semibold))
                                        .multilineTextAlignment(.center)
                                        .foregroundStyle(.white)
                                        .padding(.vertical, 14)
                                        .padding(.horizontal, 16)
                                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                                        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                                            .strokeBorder(nameFocused ? Theme.accent : Theme.line, lineWidth: 1.5))
                                        .padding(.top, 8)
                                }
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 24)
                        .frame(maxWidth: 560)
                        .tag(i)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .always))
                .onChange(of: page) { _, new in
                    nameFocused = new == namePage
                }

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
                                Text(tr("Continue", "Doorgaan"))
                            }
                        }
                        .buttonStyle(LiftrButtonStyle())
                        .disabled(connecting)
                        // Apple guideline 5.1.1(iv): after this explanation the system permission request always
                        // follows; there is deliberately no way to skip or postpone it here.
                    } else {
                        Button(page == namePage && name.trimmingCharacters(in: .whitespaces).isEmpty
                               ? tr("Skip for now", "Later invullen") : tr("Next", "Volgende")) {
                            withAnimation { page += 1 }
                        }
                        .buttonStyle(LiftrButtonStyle(filled: !(page == namePage && name.trimmingCharacters(in: .whitespaces).isEmpty)))
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
