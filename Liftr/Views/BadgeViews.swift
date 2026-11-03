import SwiftData
import SwiftUI

/// A badge you can flip: the medal on the front; on the back when you earned it and your name,
/// or how far along you are.
struct BadgeView: View {
    let badge: WorkoutStats.Badge
    var large = false

    @AppStorage(UserProfile.nameKey) private var name = ""
    @State private var angle = 0.0

    private var width: CGFloat { large ? 150 : 104 }
    private var height: CGFloat { large ? 190 : 150 }

    var body: some View {
        FlipBadge(angle: angle, front: front, back: back)
            .frame(width: width, height: height)
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .onTapGesture {
                withAnimation(.spring(duration: 0.55, bounce: 0.2)) { angle += 180 }
            }
            .sensoryFeedback(.selection, trigger: angle)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(accessibilityText)
            .accessibilityAddTraits(.isButton)
            .accessibilityHint(tr("Flips the badge", "Draait de badge om"))
    }

    // MARK: Faces

    private var front: some View {
        VStack(spacing: large ? 12 : 8) {
            ZStack {
                Circle()
                    .fill(badge.achieved ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.cardRaised))
                if badge.achieved {
                    Circle().strokeBorder(.white.opacity(0.35), lineWidth: 2).padding(4)
                } else if badge.progress > 0 {
                    Circle()
                        .trim(from: 0, to: badge.progress)
                        .stroke(Theme.accent, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .padding(2)
                }
                Image(systemName: badge.symbol)
                    .font(.system(size: large ? 34 : 26, weight: .bold))
                    .foregroundStyle(badge.achieved ? .white : Theme.muted.opacity(0.6))
            }
            .frame(width: large ? 84 : 64, height: large ? 84 : 64)
            .shadow(color: badge.achieved ? Theme.accent.opacity(0.45) : .clear, radius: 10, y: 4)
            Text(badge.title)
                .font((large ? Font.subheadline : .caption).weight(.bold))
                .foregroundStyle(badge.achieved ? .white : Theme.muted)
                .multilineTextAlignment(.center)
                .lineLimit(2, reservesSpace: true)
                .minimumScaleFactor(0.8)
        }
        .padding(.horizontal, 8)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous)
            .strokeBorder(badge.achieved ? Theme.accent.opacity(0.35) : Theme.line))
        .opacity(badge.achieved ? 1 : 0.75)
    }

    private var back: some View {
        VStack(spacing: large ? 8 : 5) {
            if let date = badge.date {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: large ? 26 : 20))
                    .foregroundStyle(.white)
                Text(tr("EARNED", "BEHAALD"))
                    .font(.system(size: large ? 11 : 9, weight: .heavy))
                    .tracking(1)
                    .foregroundStyle(.white.opacity(0.85))
                Text(date.formatted(.dateTime.day().month(.abbreviated).year().locale(L10n.locale)))
                    .font(.system(size: large ? 17 : 13, weight: .heavy, design: .rounded))
                    .foregroundStyle(.white)
                    .minimumScaleFactor(0.7)
                    .lineLimit(1)
                if !name.trimmingCharacters(in: .whitespaces).isEmpty {
                    Text(trf("by {1}", "door {1}", name.trimmingCharacters(in: .whitespaces)))
                        .font(.system(size: large ? 13 : 11, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.9))
                        .lineLimit(2)
                        .minimumScaleFactor(0.7)
                        .multilineTextAlignment(.center)
                }
            } else {
                Text(badge.detail)
                    .font(.system(size: large ? 13 : 11, weight: .semibold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .lineLimit(4)
                    .minimumScaleFactor(0.75)
                if !badge.progressText.isEmpty {
                    ProgressView(value: badge.progress)
                        .tint(Theme.accent)
                        .padding(.horizontal, 4)
                    Text(badge.progressText)
                        .font(.system(size: large ? 12 : 10, weight: .bold).monospacedDigit())
                        .foregroundStyle(Theme.muted)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                } else {
                    Text(tr("Not earned yet", "Nog niet behaald"))
                        .font(.system(size: large ? 12 : 10, weight: .bold))
                        .foregroundStyle(Theme.muted)
                }
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(badge.achieved ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.cardRaised))
        )
    }

    private var accessibilityText: String {
        if let date = badge.date {
            let when = date.formatted(date: .long, time: .omitted)
            return "\(badge.title). " + trf("Earned on {1}", "Behaald op {1}", when)
        }
        return "\(badge.title). \(badge.detail). \(badge.progressText)"
    }
}

/// Front and back of a badge, turning around the vertical axis.
private struct FlipBadge<Front: View, Back: View>: View, Animatable {
    var angle: Double
    let front: Front
    let back: Back

    var animatableData: Double {
        get { angle }
        set { angle = newValue }
    }

    private var showingBack: Bool {
        let a = angle.truncatingRemainder(dividingBy: 360)
        return a >= 90 && a < 270
    }

    var body: some View {
        ZStack {
            front.opacity(showingBack ? 0 : 1)
            back
                .rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0))
                .opacity(showingBack ? 1 : 0)
        }
        .rotation3DEffect(.degrees(angle), axis: (x: 0, y: 1, z: 0), perspective: 0.5)
    }
}

/// Every badge, grouped, with how many you have.
struct BadgesView: View {
    @Query(sort: \Workout.startDate, order: .reverse) private var workouts: [Workout]

    var body: some View {
        let badges = WorkoutStats.badges(workouts)
        let earned = badges.filter(\.achieved).count
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(trf("{1} of {2} earned", "{1} van {2} behaald", earned, badges.count))
                        .font(Theme.heading(22))
                        .textCase(.uppercase)
                        .foregroundStyle(.white)
                    ProgressView(value: Double(earned), total: Double(max(1, badges.count)))
                        .tint(Theme.accent)
                    Text(tr("Tap a badge to flip it.", "Tik op een badge om hem om te draaien."))
                        .font(.footnote)
                        .foregroundStyle(Theme.muted)
                }
                ForEach(WorkoutStats.Badge.Category.allCases) { category in
                    let items = badges.filter { $0.category == category }
                    if !items.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text(category.title).liftrTitle(18)
                                Spacer()
                                Text("\(items.filter(\.achieved).count)/\(items.count)")
                                    .font(.subheadline.weight(.bold).monospacedDigit())
                                    .foregroundStyle(Theme.muted)
                            }
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 12)], spacing: 12) {
                                ForEach(items) { BadgeView(badge: $0, large: true) }
                            }
                        }
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: 900)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle(tr("Badges", "Badges"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.background, for: .navigationBar)
        .toolbar(.visible, for: .navigationBar)
    }
}
