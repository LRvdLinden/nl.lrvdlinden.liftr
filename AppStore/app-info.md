# Liftr – App-informatie voor App Store Connect

## Algemene informatie

| Veld | Waarde |
|---|---|
| Naam (nl-NL / en-US) | Liftr: Sportschool Logboek / Liftr: Gym Workout Log |
| Bundle ID | `nl.lrvdlinden.liftr` (Watch: `nl.lrvdlinden.liftr.watchkitapp`) |
| SKU | `nl.lrvdlinden.liftr` |
| Apple ID | TODO: staat in App Store Connect na het aanmaken van de app |
| Versie | 1.0.0 |
| Primaire taal | Nederlands (nl-NL) |
| App-talen | 33: Nederlands, Engels en 31 andere Europese talen (bg, ca, cs, da, de, el, es, et, fi, fr, ga, hr, hu, is, it, lt, lv, mk, mt, nb, pl, pt-PT, ro, ru, sk, sl, sq, sr, sv, tr, uk) |
| Store-teksten | nl-NL en en-US |
| Primaire categorie | **Health & Fitness**: een logboek voor krachttraining, met Apple Health |
| Secundaire categorie | **Sports**: training in de sportschool |
| Platforms | iPhone en iPad (iOS/iPadOS 17+), Apple Watch (watchOS 10+) |
| Copyright | © 2026 Léon van der Linden |
| Support-URL | https://liftr.nu/ (EN: https://liftr.nu/en/) |
| Marketing-URL | https://liftr.nu/ (EN: https://liftr.nu/en/) |
| Privacybeleid-URL | https://liftr.nu/privacy/ (EN: https://liftr.nu/en/privacy/) |
| Prijs | Gratis, geen in-app aankopen |

### Content Rights
Liftr toont geen content van derden. Alleen bij een sportschoolpas laat de app het officiële icoon van de website van de gekozen keten zien. Daarnaast toont de app de naam van de keten, alleen op de pas en in de kiezer, zodat de gebruiker zijn eigen pas herkent. Er wordt geen content van derden verspreid of verkocht.
**Antwoord in ASC:** "No, it does not contain, show, or access third-party content". Vind je de logo's toch derde-partij-content, kies dan "Yes" en vermeld dat het om het merkicoon van de eigen sportschool van de gebruiker gaat, voor herkenning van de pas.

### Licentieovereenkomst
Standaard EULA van Apple.

### Leeftijdsclassificatie (vragenlijst)
Alle vragen: **None / No**.
- Geen geweld, seksuele inhoud, grof taalgebruik, gokken, drugs of horror.
- Geen onbeperkte webtoegang, geen chat of user-generated content, geen contests.
- Medische of behandelinformatie: **No**. De app logt alleen trainingen en geeft geen medisch advies.
- **Resultaat: 4+**

## App Privacy (privacylabel)

**Data Not Collected.**

- Geen account, geen eigen server, geen analytics, geen crash-SDK, geen advertenties, geen tracking. `NSPrivacyTracking` staat op false en `NSPrivacyCollectedDataTypes` is leeg in `PrivacyInfo.xcprivacy`.
- **Opslag:** gegevens staan op het apparaat en synchroniseren via de eigen **privé**-iCloud-database van de gebruiker (CloudKit, private database). De ontwikkelaar kan deze gegevens niet inzien, dus volgens Apple telt dit niet als "collected".
- **Apple Health:** wordt lokaal gelezen en geschreven en verlaat het apparaat niet via de ontwikkelaar.
- **Camera en foto's:** worden alleen op het apparaat gebruikt om de code van een pas te lezen.
- **Sportschoollogo's:** de app haalt het icoon op van de website van de keten. Dat is een gewoon webverzoek zonder gegevens over de gebruiker.
- **Geïmporteerde bestanden:** worden alleen lokaal gelezen.
- **Derde-partij-SDK's:** geen.

## Privacybeleid
Zie `metadata/privacy/privacy-policy-nl.txt` en `-en.txt`. Dezelfde tekst staat op https://liftr.nu/privacy/. Er is geen tvOS-app, dus het Apple TV-privacyveld is niet van toepassing.

## App Accessibility (Accessibility Nutrition Labels)
Alleen geclaimd wat in de code is nagelopen.

| Functie | iPhone | iPad | Apple Watch | Toelichting |
|---|---|---|---|---|
| Dark Interface | ✅ claimed | ✅ claimed | ✅ claimed | De app is altijd donker (`preferredColorScheme(.dark)`) |
| VoiceOver | not claimed | not claimed | not claimed | Standaard SwiftUI-controls werken; nog niet volledig getest met VoiceOver |
| Voice Control | not claimed | not claimed | – | Niet getest |
| Larger Text | not claimed | not claimed | not claimed | Koppen gebruiken vaste groottes (`Theme.display`) |
| Differentiate Without Color Alone | not claimed | not claimed | not claimed | Afgeronde sets hebben een vinkje, maar dat is niet overal nagelopen |
| Sufficient Contrast | not claimed | not claimed | not claimed | Niet gemeten |
| Reduced Motion | not claimed | not claimed | not claimed | De pas draait altijd om met een animatie |
| Captions / Audio Descriptions | n.v.t. | n.v.t. | n.v.t. | Geen video of audio |
