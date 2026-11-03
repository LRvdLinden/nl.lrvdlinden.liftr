import SwiftUI

/// LRvdLinden house style (from lrvdlinden.app): accent blue, ink, surface, line
/// and muted text, each with its dark-mode value.
enum Brand {
    static let url = URL(string: "https://lrvdlinden.app")!
    static let accent = Color(light: 0x2747E8, dark: 0x8A9DFF)
    static let accentSoft = Color(light: 0xE6EBFF, dark: 0x1D2547)
    static let onAccent = Color(light: 0xFFFFFF, dark: 0x0D1018)
    static let ink = Color(light: 0x121726, dark: 0xE9ECF4)
    static let muted = Color(light: 0x5D6578, dark: 0x98A0B3)
    static let surface = Color(light: 0xFFFFFF, dark: 0x161B27)
    static let line = Color(light: 0xDDE2EC, dark: 0x262D3D)
    /// The logo tile is always the dark ink, with the blue dot.
    static let logoTile = Color(red: 0x12 / 255, green: 0x17 / 255, blue: 0x26 / 255)
    static let logoDot = Color(red: 0x4C / 255, green: 0x6B / 255, blue: 0xFF / 255)
}

/// The "LR" mark from lrvdlinden.app, drawn natively (512 × 512 design grid).
struct LRLogo: View {
    var size: CGFloat = 60

    var body: some View {
        Canvas { ctx, canvas in
            let s = canvas.width / 512
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: y * s) }

            ctx.fill(Path(roundedRect: CGRect(origin: .zero, size: canvas), cornerRadius: 116 * s, style: .continuous),
                     with: .color(Brand.logoTile))

            var letters = Path()
            // L
            letters.move(to: p(132, 146)); letters.addLine(to: p(132, 370)); letters.addLine(to: p(232, 370))
            // R: stem + bowl
            letters.move(to: p(292, 370)); letters.addLine(to: p(292, 146)); letters.addLine(to: p(340, 146))
            letters.addArc(center: p(340, 208), radius: 62 * s, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false)
            letters.addLine(to: p(292, 270))
            // R: leg
            letters.move(to: p(338, 270)); letters.addLine(to: p(390, 370))
            ctx.stroke(letters, with: .color(.white),
                       style: StrokeStyle(lineWidth: 46 * s, lineCap: .round, lineJoin: .round))

            let dot = Path(ellipseIn: CGRect(x: (404 - 34) * s, y: (126 - 34) * s, width: 68 * s, height: 68 * s))
            ctx.fill(dot, with: .color(Brand.logoDot))
            ctx.stroke(dot, with: .color(Brand.logoTile), lineWidth: 14 * s)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// "More apps from the developer" card for the bottom of a settings screen.
/// Opens lrvdlinden.app. Texts follow the app's language (10 languages).
struct MoreAppsCard: View {
    @ScaledMetric(relativeTo: .body) private var logoSize: CGFloat = 60

    private var text: MoreAppsText { MoreAppsText.current }

    var body: some View {
        Link(destination: Brand.url) {
            HStack(spacing: 14) {
                LRLogo(size: logoSize)
                    .overlay(RoundedRectangle(cornerRadius: logoSize * 116 / 512, style: .continuous)
                        .strokeBorder(Brand.line, lineWidth: 1))
                VStack(alignment: .leading, spacing: 3) {
                    Text(text.title)
                        .font(.headline)
                        .foregroundStyle(Brand.accent)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(text.subtitle)
                        .font(.subheadline)
                        .foregroundStyle(Brand.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 8)
                VStack(spacing: 4) {
                    Text(text.action)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Brand.onAccent)
                        .padding(.horizontal, 14).padding(.vertical, 6)
                        .background(Brand.accent, in: Capsule())
                    Text("lrvdlinden.app")
                        .font(.caption2)
                        .foregroundStyle(Brand.muted)
                        .lineLimit(1)
                }
            }
            .padding(16)
            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(Brand.line, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(text.title). \(text.subtitle)")
        .accessibilityHint("lrvdlinden.app")
        .accessibilityAddTraits(.isLink)
    }
}

/// Card texts per language; picks the app's language, English when unknown.
struct MoreAppsText {
    let section: String
    let title: String
    let subtitle: String
    let action: String

    static var current: MoreAppsText { all[L10n.language] ?? all["en"]! }

    static let all: [String: MoreAppsText] = [
        "nl": .init(section: "Ontdekken", title: "Meer apps van de ontwikkelaar", subtitle: "Apps voor iPhone, iPad, Apple TV en Homey", action: "Bekijk"),
        "en": .init(section: "Discover", title: "More apps from the developer", subtitle: "Apps for iPhone, iPad, Apple TV and Homey", action: "View"),
        "bg": .init(section: "Открий", title: "Още приложения от разработчика", subtitle: "Приложения за iPhone, iPad, Apple TV и Homey", action: "Виж"),
        "ca": .init(section: "Descobreix", title: "Més apps del desenvolupador", subtitle: "Apps per a iPhone, iPad, Apple TV i Homey", action: "Mostra"),
        "cs": .init(section: "Objevuj", title: "Další aplikace od vývojáře", subtitle: "Aplikace pro iPhone, iPad, Apple TV a Homey", action: "Zobrazit"),
        "da": .init(section: "Opdag", title: "Flere apps fra udvikleren", subtitle: "Apps til iPhone, iPad, Apple TV og Homey", action: "Vis"),
        "de": .init(section: "Entdecken", title: "Weitere Apps des Entwicklers", subtitle: "Apps für iPhone, iPad, Apple TV und Homey", action: "Ansehen"),
        "el": .init(section: "Ανακάλυψε", title: "Περισσότερες εφαρμογές από τον προγραμματιστή", subtitle: "Εφαρμογές για iPhone, iPad, Apple TV και Homey", action: "Προβολή"),
        "es": .init(section: "Descubrir", title: "Más apps del desarrollador", subtitle: "Apps para iPhone, iPad, Apple TV y Homey", action: "Ver"),
        "et": .init(section: "Avasta", title: "Arendaja teised rakendused", subtitle: "Rakendused iPhone'ile, iPadile, Apple TV-le ja Homeyle", action: "Vaata"),
        "fi": .init(section: "Löydä", title: "Lisää kehittäjän appeja", subtitle: "Appeja iPhonelle, iPadille, Apple TV:lle ja Homeylle", action: "Näytä"),
        "fr": .init(section: "Découvrir", title: "Plus d’apps du développeur", subtitle: "Apps pour iPhone, iPad, Apple TV et Homey", action: "Voir"),
        "ga": .init(section: "Faigh amach", title: "Tuilleadh aipeanna ón bhforbróir", subtitle: "Aipeanna do iPhone, iPad, Apple TV agus Homey", action: "Féach"),
        "hr": .init(section: "Otkrij", title: "Još aplikacija od developera", subtitle: "Aplikacije za iPhone, iPad, Apple TV i Homey", action: "Pogledaj"),
        "hu": .init(section: "Felfedezés", title: "További appok a fejlesztőtől", subtitle: "Appok iPhone-ra, iPadre, Apple TV-re és Homey-ra", action: "Megnézem"),
        "is": .init(section: "Uppgötva", title: "Fleiri öpp frá forritaranum", subtitle: "Öpp fyrir iPhone, iPad, Apple TV og Homey", action: "Skoða"),
        "it": .init(section: "Scopri", title: "Altre app dello sviluppatore", subtitle: "App per iPhone, iPad, Apple TV e Homey", action: "Vedi"),
        "lt": .init(section: "Atrask", title: "Daugiau kūrėjo programėlių", subtitle: "Programėlės iPhone, iPad, Apple TV ir Homey", action: "Žiūrėti"),
        "lv": .init(section: "Atklāj", title: "Citas izstrādātāja lietotnes", subtitle: "Lietotnes iPhone, iPad, Apple TV un Homey", action: "Skatīt"),
        "mk": .init(section: "Откриј", title: "Уште апликации од развивачот", subtitle: "Апликации за iPhone, iPad, Apple TV и Homey", action: "Види"),
        "mt": .init(section: "Skopri", title: "Aktar apps mill-iżviluppatur", subtitle: "Apps għal iPhone, iPad, Apple TV u Homey", action: "Ara"),
        "nb": .init(section: "Oppdag", title: "Flere apper fra utvikleren", subtitle: "Apper for iPhone, iPad, Apple TV og Homey", action: "Vis"),
        "pl": .init(section: "Odkrywaj", title: "Więcej aplikacji od dewelopera", subtitle: "Aplikacje na iPhone’a, iPada, Apple TV i Homey", action: "Zobacz"),
        "pt-PT": .init(section: "Descobrir", title: "Mais apps do programador", subtitle: "Apps para iPhone, iPad, Apple TV e Homey", action: "Ver"),
        "ro": .init(section: "Descoperă", title: "Mai multe aplicații de la dezvoltator", subtitle: "Aplicații pentru iPhone, iPad, Apple TV și Homey", action: "Vezi"),
        "ru": .init(section: "Интересное", title: "Другие приложения разработчика", subtitle: "Приложения для iPhone, iPad, Apple TV и Homey", action: "Смотреть"),
        "sk": .init(section: "Objavuj", title: "Ďalšie aplikácie od vývojára", subtitle: "Aplikácie pre iPhone, iPad, Apple TV a Homey", action: "Zobraziť"),
        "sl": .init(section: "Odkrij", title: "Več aplikacij razvijalca", subtitle: "Aplikacije za iPhone, iPad, Apple TV in Homey", action: "Poglej"),
        "sq": .init(section: "Zbulo", title: "Më shumë aplikacione nga zhvilluesi", subtitle: "Aplikacione për iPhone, iPad, Apple TV dhe Homey", action: "Shiko"),
        "sr": .init(section: "Откриј", title: "Још апликација од програмера", subtitle: "Апликације за iPhone, iPad, Apple TV и Homey", action: "Погледај"),
        "sv": .init(section: "Upptäck", title: "Fler appar från utvecklaren", subtitle: "Appar för iPhone, iPad, Apple TV och Homey", action: "Visa"),
        "tr": .init(section: "Keşfet", title: "Geliştiricinin diğer uygulamaları", subtitle: "iPhone, iPad, Apple TV ve Homey için uygulamalar", action: "Göster"),
        "uk": .init(section: "Цікаве", title: "Інші програми розробника", subtitle: "Програми для iPhone, iPad, Apple TV і Homey", action: "Переглянути"),
    ]
}

#Preview {
    VStack(spacing: 20) {
        MoreAppsCard()
        MoreAppsCard().environment(\.colorScheme, .dark)
    }
    .padding()
}
