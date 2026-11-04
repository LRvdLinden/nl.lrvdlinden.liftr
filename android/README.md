# Liftr voor Android

Native Android-versie van Liftr (Kotlin + Jetpack Compose), geport van de iOS/watchOS-app in deze repo.
Eén APK voor telefoon en tablet (`:app`) en een losse, optionele APK voor Wear OS (`:wear`).
Geen Play Store, geen account, geen cloud: alle gegevens staan op het toestel zelf.

## Modules

| Module  | Wat                                                                                   |
|---------|----------------------------------------------------------------------------------------|
| `:core` | Gedeeld: Room-database, modellen, statistieken en badges, vertalingen (`tr`/`trf`), barcodes (ZXing), importers (notities, .xlsx, .csv), CSV-export, Wear-sync-formaat |
| `:app`  | Telefoon/tablet: alle schermen, rusttimer, herinneringen, Health Connect, widget, sportschoolpassen (camera + ML Kit), Wear-koppeling |
| `:wear` | Wear OS: training starten, sets loggen, rusttimer, hartslag, pas tonen; stuurt afgeronde trainingen naar de telefoon |

De teksten komen uit dezelfde tabellen als iOS: tijdens de build kopieert Gradle `Shared/Localization/*.json`
naar de assets (en `Shared/GymLogos/*.png`), dus vertalingen en logo's blijven vanzelf gelijk met iOS.

## Bouwen

Er wordt alleen in GitHub Actions gebouwd (`.github/workflows/android.yml`): bij elke push naar `main` die
`android/**`, `Shared/Localization/**` of `Shared/GymLogos/**` raakt, of handmatig via *Run workflow*.
De run levert twee artifacts:

- `liftr-android-apk` → `app-release.apk` (telefoon/tablet)
- `liftr-wear-apk` → `wear-release.apk` (horloge; mag mislukken zonder dat de telefoon-APK faalt)

Lokaal kan ook: `gradle -p android :app:assembleRelease` (Gradle 8.10, JDK 17, Android SDK 35).

## APK installeren op de telefoon

1. Download het artifact `liftr-android-apk` bij de laatste groene run (Actions → Android) en pak de zip uit.
2. Zet `app-release.apk` op de telefoon (bijv. via Google Drive, mail of USB) en tik erop.
3. Android vraagt om **installeren uit onbekende bronnen** toe te staan voor de app waarmee je de APK opent
   (Instellingen → Apps → *die app* → Onbekende apps installeren → Toestaan). Daarna: Installeren.
4. Updates: een nieuwere APK die met dezelfde sleutel is ondertekend installeer je gewoon over de oude heen;
   je gegevens blijven staan.

Health Connect: op Android 14+ zit het in het systeem; op Android 9–13 installeer je de app *Health Connect*
uit de Play Store (Liftr werkt ook zonder, dan wordt er niets gedeeld).

## Wear OS-APK installeren

Een losse Wear-APK kan niet via de telefoon-APK mee-installeren als je zonder Play Store werkt. Gebruik `adb`:

1. Horloge: Instellingen → Systeem → Info → tik 7× op *Buildnummer* → Ontwikkelaarsopties → **ADB-foutopsporing**
   en **Foutopsporing via wifi** aan. Noteer het IP-adres en de poort.
2. Computer (met Android platform-tools):
   ```
   adb pair <ip>:<koppelpoort>      # koppelcode van het horloge invullen
   adb connect <ip>:<poort>
   adb -s <ip>:<poort> install wear-release.apk
   ```
3. De horloge-app moet met **dezelfde sleutel** ondertekend zijn als de telefoon-app en dezelfde package-naam
   hebben (`nl.lrvdlinden.liftr`), anders praten ze niet met elkaar. De workflow zorgt daarvoor.

Open daarna Liftr één keer op de telefoon: die stuurt oefeningen, schema's, passen en je laatste sets naar het
horloge. Trainingen die je op het horloge afrondt komen automatisch in Liftr op de telefoon (en van daaruit in
Health Connect). Hiervoor moet het horloge met de telefoon gekoppeld zijn (Wear OS-app / Google Play-services).

## Ondertekening (keystore)

Alle release-APK's worden ondertekend met één vaste sleutel, zodat updates over elkaar heen installeren.
De workflow leest die uit vier **repository secrets** (GitHub → Settings → Secrets and variables → Actions):

| Secret                    | Inhoud                                            |
|---------------------------|---------------------------------------------------|
| `LIFTR_KEYSTORE_BASE64`   | de keystore (`liftr-release.jks`) als base64       |
| `LIFTR_KEYSTORE_PASSWORD` | wachtwoord van de keystore                        |
| `LIFTR_KEY_PASSWORD`      | wachtwoord van de sleutel (hier gelijk aan het keystore-wachtwoord) |
| `LIFTR_KEY_ALIAS`         | `liftr`                                           |

Zolang deze secrets ontbreken ondertekent de workflow met een **tijdelijke** sleutel (met een waarschuwing in de
run). Zo'n APK installeert wel, maar latere APK's kunnen er niet overheen geïnstalleerd worden (eerst
verwijderen = gegevens kwijt) en telefoon en horloge herkennen elkaar niet. Zet de secrets dus vóór je Liftr
echt gaat gebruiken. De keystore en wachtwoorden staan nooit in de repo; bewaar de keystore goed: zonder
die sleutel kun je geen updates meer uitbrengen.

## Wat werkt (zelfde gedrag en teksten als iOS)

Vandaag (week, statistieken, laatste training, schema's, lichaam, badges, pasknop), training met
"VORIGE"-kolom en vooraf ingevulde sets, vorige trainingen, rusttimer in de app én als melding met aftellende
klok, afronden/weggooien met samenvatting, geschiedenis met maandkalender en bewerken/verwijderen,
voortgangsgrafieken (zwaarste set, geschat 1RM, volume, weken, gewicht) met periodes en scrubben,
bibliotheek met zoeken/favorieten/eigen oefeningen/archief en schema-editor, 56 badges met behaalde datum en
omdraai-animatie, welkomstschermen met naam, sportschoolpassen (kiezen uit ketens met logo en kleur, of een
andere sportschool met eigen kleur; code typen, scannen met de camera of uit een screenshot lezen; omdraaien
naar QR/barcode op volle helderheid), importeren uit notities en Excel/CSV met voorbeeld en bewerken,
imports beheren en alles wissen, herinneringen (trainingsdagen, inactiviteit, weekoverzicht), CSV-export via
het deelmenu, privacypagina, "Meer apps van de ontwikkelaar", app-info, Health Connect (trainingen,
calorieën, afstand, gewicht en vetpercentage schrijven; gewicht, vet, vetvrije massa en rusthartslag lezen),
homescreen-widget "Deze week", Wear OS-app. Alle 33 talen van iOS.

## Niet ondersteund of anders dan op iOS

- **Geen iCloud / synchronisatie tussen apparaten**: gegevens staan alleen op de telefoon (en het horloge).
  Maak af en toe een CSV-export als back-up; die kun je later weer importeren.
- **Live Activity / Dynamic Island** → vervangen door een melding met aftellende rusttimer.
- **Siri, Opdrachten en de Actieknop** (App Intents) zijn er niet.
- **Review-vraag** (App Store) is weggelaten: zonder Play Store is er niets om te beoordelen.
- **Widget**: één homescreen-widget (week, aantal deze week, reeks); geen lockscreen-widgets of complicaties.
- **Wear OS**: gewicht en herhalingen met −/+ in plaats van de Digital Crown; hartslag via de hartslagsensor
  van het horloge (geen Health Services-trainingssessie); het horloge schrijft zelf niets naar Health Connect,
  de telefoon doet dat zodra de training binnenkomt; geen eigen oefeningen aanmaken op het horloge.
- **Lettertype**: het systeemlettertype *sans-serif-condensed* (vet) in plaats van SF Compact Condensed.
- Teksten die alleen op Android bestaan (bijv. over Health Connect) zijn Nederlands en Engels; in andere talen
  verschijnt daar Engels.
- Niet getest op een echt toestel: alles is alleen gecompileerd in GitHub Actions.
