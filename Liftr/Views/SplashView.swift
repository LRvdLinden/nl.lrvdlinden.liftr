import SwiftUI

/// Short animated splash after the launch screen: the same logo on the same background (so the
/// hand-over is seamless), then the wordmark, then a fade into the app. About one second.
struct SplashView: View {
    var onFinished: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var showWordmark = false
    @State private var logoScale = 1.0

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            RadialGradient(colors: [Theme.accent.opacity(showWordmark ? 0.28 : 0), .clear],
                           center: .center, startRadius: 10, endRadius: 360)
                .ignoresSafeArea()
            VStack(spacing: 18) {
                Image("LaunchLogo")
                    .resizable()
                    .frame(width: 120, height: 120)
                    .scaleEffect(logoScale)
                    .shadow(color: Theme.accent.opacity(showWordmark ? 0.5 : 0), radius: 24, y: 8)
                Text("LIFTR")
                    .font(Theme.display(44))
                    .tracking(4)
                    .foregroundStyle(.white)
                    .opacity(showWordmark ? 1 : 0)
                    .offset(y: showWordmark || reduceMotion ? 0 : 14)
            }
            // The launch screen centres the logo; keep it at the same spot when the wordmark appears.
            .offset(y: 31)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Liftr")
        .task {
            withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .spring(duration: 0.5, bounce: 0.35)) {
                showWordmark = true
                if !reduceMotion { logoScale = 1.06 }
            }
            try? await Task.sleep(for: .milliseconds(reduceMotion ? 450 : 850))
            onFinished()
        }
    }
}
