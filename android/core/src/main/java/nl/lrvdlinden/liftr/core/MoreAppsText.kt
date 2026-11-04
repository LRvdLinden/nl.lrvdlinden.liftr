package nl.lrvdlinden.liftr.core

/** Texts of the "More apps from the developer" card and the app-info footer, per language (from MoreAppsCard.swift / AppInfoFooter.swift). */
data class MoreAppsText(val section: String, val title: String, val subtitle: String, val action: String, val version: String) {
    companion object {
        val current: MoreAppsText get() = all[L10n.language] ?: all.getValue("en")

        val all: Map<String, MoreAppsText> = mapOf(
        "nl" to MoreAppsText("Ontdekken", "Meer apps van de ontwikkelaar", "Apps voor iPhone, iPad, Apple TV en Homey", "Bekijk", "Versie"),
        "en" to MoreAppsText("Discover", "More apps from the developer", "Apps for iPhone, iPad, Apple TV and Homey", "View", "Version"),
        "bg" to MoreAppsText("Открий", "Още приложения от разработчика", "Приложения за iPhone, iPad, Apple TV и Homey", "Виж", "Версия"),
        "ca" to MoreAppsText("Descobreix", "Més apps del desenvolupador", "Apps per a iPhone, iPad, Apple TV i Homey", "Mostra", "Versió"),
        "cs" to MoreAppsText("Objevuj", "Další aplikace od vývojáře", "Aplikace pro iPhone, iPad, Apple TV a Homey", "Zobrazit", "Verze"),
        "da" to MoreAppsText("Opdag", "Flere apps fra udvikleren", "Apps til iPhone, iPad, Apple TV og Homey", "Vis", "Version"),
        "de" to MoreAppsText("Entdecken", "Weitere Apps des Entwicklers", "Apps für iPhone, iPad, Apple TV und Homey", "Ansehen", "Version"),
        "el" to MoreAppsText("Ανακάλυψε", "Περισσότερες εφαρμογές από τον προγραμματιστή", "Εφαρμογές για iPhone, iPad, Apple TV και Homey", "Προβολή", "Έκδοση"),
        "es" to MoreAppsText("Descubrir", "Más apps del desarrollador", "Apps para iPhone, iPad, Apple TV y Homey", "Ver", "Versión"),
        "et" to MoreAppsText("Avasta", "Arendaja teised rakendused", "Rakendused iPhone'ile, iPadile, Apple TV-le ja Homeyle", "Vaata", "Versioon"),
        "fi" to MoreAppsText("Löydä", "Lisää kehittäjän appeja", "Appeja iPhonelle, iPadille, Apple TV:lle ja Homeylle", "Näytä", "Versio"),
        "fr" to MoreAppsText("Découvrir", "Plus d’apps du développeur", "Apps pour iPhone, iPad, Apple TV et Homey", "Voir", "Version"),
        "ga" to MoreAppsText("Faigh amach", "Tuilleadh aipeanna ón bhforbróir", "Aipeanna do iPhone, iPad, Apple TV agus Homey", "Féach", "Leagan"),
        "hr" to MoreAppsText("Otkrij", "Još aplikacija od developera", "Aplikacije za iPhone, iPad, Apple TV i Homey", "Pogledaj", "Verzija"),
        "hu" to MoreAppsText("Felfedezés", "További appok a fejlesztőtől", "Appok iPhone-ra, iPadre, Apple TV-re és Homey-ra", "Megnézem", "Verzió"),
        "is" to MoreAppsText("Uppgötva", "Fleiri öpp frá forritaranum", "Öpp fyrir iPhone, iPad, Apple TV og Homey", "Skoða", "Útgáfa"),
        "it" to MoreAppsText("Scopri", "Altre app dello sviluppatore", "App per iPhone, iPad, Apple TV e Homey", "Vedi", "Versione"),
        "lt" to MoreAppsText("Atrask", "Daugiau kūrėjo programėlių", "Programėlės iPhone, iPad, Apple TV ir Homey", "Žiūrėti", "Versija"),
        "lv" to MoreAppsText("Atklāj", "Citas izstrādātāja lietotnes", "Lietotnes iPhone, iPad, Apple TV un Homey", "Skatīt", "Versija"),
        "mk" to MoreAppsText("Откриј", "Уште апликации од развивачот", "Апликации за iPhone, iPad, Apple TV и Homey", "Види", "Верзија"),
        "mt" to MoreAppsText("Skopri", "Aktar apps mill-iżviluppatur", "Apps għal iPhone, iPad, Apple TV u Homey", "Ara", "Verżjoni"),
        "nb" to MoreAppsText("Oppdag", "Flere apper fra utvikleren", "Apper for iPhone, iPad, Apple TV og Homey", "Vis", "Versjon"),
        "pl" to MoreAppsText("Odkrywaj", "Więcej aplikacji od dewelopera", "Aplikacje na iPhone’a, iPada, Apple TV i Homey", "Zobacz", "Wersja"),
        "pt-PT" to MoreAppsText("Descobrir", "Mais apps do programador", "Apps para iPhone, iPad, Apple TV e Homey", "Ver", "Versão"),
        "ro" to MoreAppsText("Descoperă", "Mai multe aplicații de la dezvoltator", "Aplicații pentru iPhone, iPad, Apple TV și Homey", "Vezi", "Versiune"),
        "ru" to MoreAppsText("Интересное", "Другие приложения разработчика", "Приложения для iPhone, iPad, Apple TV и Homey", "Смотреть", "Версия"),
        "sk" to MoreAppsText("Objavuj", "Ďalšie aplikácie od vývojára", "Aplikácie pre iPhone, iPad, Apple TV a Homey", "Zobraziť", "Verzia"),
        "sl" to MoreAppsText("Odkrij", "Več aplikacij razvijalca", "Aplikacije za iPhone, iPad, Apple TV in Homey", "Poglej", "Različica"),
        "sq" to MoreAppsText("Zbulo", "Më shumë aplikacione nga zhvilluesi", "Aplikacione për iPhone, iPad, Apple TV dhe Homey", "Shiko", "Versioni"),
        "sr" to MoreAppsText("Откриј", "Још апликација од програмера", "Апликације за iPhone, iPad, Apple TV и Homey", "Погледај", "Верзија"),
        "sv" to MoreAppsText("Upptäck", "Fler appar från utvecklaren", "Appar för iPhone, iPad, Apple TV och Homey", "Visa", "Version"),
        "tr" to MoreAppsText("Keşfet", "Geliştiricinin diğer uygulamaları", "iPhone, iPad, Apple TV ve Homey için uygulamalar", "Göster", "Sürüm"),
        "uk" to MoreAppsText("Цікаве", "Інші програми розробника", "Програми для iPhone, iPad, Apple TV і Homey", "Переглянути", "Версія"),
        )
    }
}
