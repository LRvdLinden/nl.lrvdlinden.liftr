# Liftr – App Store-pakket (1.0.0)

Alles voor App Store Connect, in **nl-NL** (primaire taal) en **en-US**.

## Afbeeldingen
Nagemaakt in HTML/CSS op basis van de SwiftUI-schermen (dezelfde teksten, kleuren en layout), gerenderd op de exacte pixelmaten, RGB zonder alfakanaal.

| Map | Maat (px) | Aantal per taal |
|---|---|---|
| `screenshots/<taal>/iphone-6.3` | 1206 × 2622 | 7 |
| `screenshots/<taal>/iphone-6.1` | 1179 × 2556 | 7 |
| `screenshots/<taal>/ipad-13` | 2064 × 2752 en 2048 × 2732 | 3 per maat |
| `screenshots/<taal>/watch/<maat>` | 422×514, 416×496, 410×502, 396×484, 368×448, 312×390 | 5 per maat (alleen het scherm, zonder kader) |
| `marketing/<taal>` | header 5244×2950 en 3840×1646 · search 5244×2950, 3840×2560 en 1920×1280 | 5 |

**Talen:** de app zelf ondersteunt 33 talen. Afbeeldingen en store-teksten zijn alleen gemaakt in Nederlands en Engels. App Store Connect toont de Engelse set ook in landen waar je geen eigen screenshots hebt.

Volgorde iPhone: 1 Vandaag · 2 Training loggen · 3 Rusttimer (Live Activity) · 4 Voortgang · 5 Geschiedenis · 6 Sportschoolpassen · 7 Importeren.

## Teksten
`metadata/` is opgebouwd zoals fastlane deliver het verwacht: naam, subtitel, promotietekst, beschrijving, trefwoorden, release notes en URL's per taal, plus copyright en categorieën. In `app-info.md` staan de algemene info, het privacylabel, de leeftijdsclassificatie en de toegankelijkheid.

## Uploaden – checklist
1. App aanmaken in App Store Connect: bundle ID `nl.lrvdlinden.liftr`, SKU `nl.lrvdlinden.liftr`, primaire taal Nederlands.
2. App-informatie: categorieën, content rights en leeftijdsclassificatie volgens `app-info.md`.
3. App Privacy: **Data Not Collected**; privacybeleid-URL https://liftr.nu/privacy/.
4. Versie 1.0.0, per taal (nl-NL en en-US): teksten uit `metadata/<taal>/`, screenshots uit `screenshots/<taal>/` (iPhone 6.3", iPad 13", Apple Watch).
5. Toegankelijkheid: alleen Dark Interface claimen.
6. Build uit TestFlight kiezen en ter beoordeling indienen.
7. Na livegang: `APP_STORE_URL` invullen in `tools/build_website.py` en de website opnieuw uploaden.

## Opnieuw maken
```
python3 tools/appstore/build_assets.py && node tools/appstore/render.js && python3 tools/appstore/finish.py
```

**Open punt:** de Apple ID (die verschijnt pas na het aanmaken in App Store Connect).
