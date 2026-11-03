import SwiftUI

/// Privacy statement, reachable from Settings.
struct PrivacyView: View {
    private struct Item: Identifiable {
        let id = UUID()
        let symbol: String
        let title: String
        let text: String
    }

    private var items: [Item] {
        [
            Item(symbol: "lock.shield.fill",
                 title: tr("Your data stays yours", "Jouw gegevens blijven van jou"),
                 text: tr("Liftr has no account, no server and no advertising. Your workouts, exercises, routines, body measurements and gym pass are stored on your device and in your own private iCloud.",
                          "Liftr heeft geen account, geen server en geen advertenties. Je trainingen, oefeningen, schema's, lichaamsmetingen en sportschoolpas staan op je apparaat en in je eigen privé-iCloud.")),
            Item(symbol: "heart.fill",
                 title: tr("Apple Health", "Apple Gezondheid"),
                 text: tr("Only with your permission, Liftr reads your weight, body composition and resting heart rate, and saves workouts, calories, distance and weight. Health data never leaves your devices through Liftr and is never used for advertising. You can change access at any time in the Health app.",
                          "Alleen met jouw toestemming leest Liftr je gewicht, lichaamssamenstelling en rusthartslag, en bewaart het trainingen, calorieën, afstand en gewicht. Gezondheidsgegevens verlaten je apparaten nooit via Liftr en worden nooit voor advertenties gebruikt. Je wijzigt de toegang altijd in de Gezondheid-app.")),
            Item(symbol: "icloud.fill",
                 title: tr("iCloud", "iCloud"),
                 text: tr("Syncing between your iPhone, iPad and Apple Watch goes through your private iCloud database. The developer cannot see this data. The code of your gym pass is end-to-end encrypted.",
                          "Synchroniseren tussen je iPhone, iPad en Apple Watch gaat via je privé-iCloud-database. De ontwikkelaar kan deze gegevens niet zien. De code van je sportschoolpas is end-to-end versleuteld.")),
            Item(symbol: "camera.fill",
                 title: tr("Camera and photos", "Camera en foto's"),
                 text: tr("The camera and your photos are only used to read the code of your gym pass. Images are not saved or sent anywhere.",
                          "De camera en je foto's worden alleen gebruikt om de code van je sportschoolpas te lezen. Afbeeldingen worden niet bewaard of verstuurd.")),
            Item(symbol: "bell.fill",
                 title: tr("Notifications", "Meldingen"),
                 text: tr("Reminders and the rest timer are planned on your device. There is no push server.",
                          "Herinneringen en de rusttimer worden op je apparaat ingepland. Er is geen pushserver.")),
            Item(symbol: "eye.slash.fill",
                 title: tr("No tracking", "Geen tracking"),
                 text: tr("Liftr contains no analytics, no third-party code and no tracking of any kind.",
                          "Liftr bevat geen analytics, geen code van derden en geen enkele vorm van tracking.")),
            Item(symbol: "trash.fill",
                 title: tr("Deleting your data", "Je gegevens verwijderen"),
                 text: tr("Delete workouts in the app. To remove everything, delete Liftr and remove its data in Settings → your name → iCloud → Manage Storage.",
                          "Verwijder trainingen in de app. Om alles te wissen verwijder je Liftr en haal je de gegevens weg via Instellingen → je naam → iCloud → Beheer opslag.")),
        ]
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(tr("Privacy", "Privacy"))
                    .font(Theme.display(40))
                    .textCase(.uppercase)
                    .foregroundStyle(.white)
                Text(tr("Liftr is built to keep your training private.", "Liftr is gemaakt om je training privé te houden."))
                    .font(.title3)
                    .foregroundStyle(Theme.muted)
                    .padding(.bottom, 6)

                ForEach(items) { item in
                    HStack(alignment: .top, spacing: 14) {
                        Image(systemName: item.symbol)
                            .font(.title3)
                            .foregroundStyle(Theme.accent)
                            .frame(width: 32)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.title).font(.headline).foregroundStyle(.white)
                            Text(item.text)
                                .font(.subheadline)
                                .foregroundStyle(Theme.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .liftrCard()
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text(tr("Questions?", "Vragen?")).font(.headline).foregroundStyle(.white)
                    Link("lrvdlinden.app", destination: Brand.url)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.accent)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .liftrCard()

                Text(tr("Last updated: November 2026", "Laatst bijgewerkt: november 2026"))
                    .font(.caption)
                    .foregroundStyle(Theme.muted)
                    .padding(.top, 4)
            }
            .padding(16)
            .frame(maxWidth: 760)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
    }
}
