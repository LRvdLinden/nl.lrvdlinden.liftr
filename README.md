# Liftr

Sportschool-logboek voor iPhone, iPad en Apple Watch. Houd per training bij welke oefeningen je deed, hoeveel sets, herhalingen en welk gewicht per herhaling – en zie hoe je sterker wordt. Volledig gekoppeld aan Apple Gezondheid.

| | |
|---|---|
| Bundle ID | `nl.lrvdlinden.liftr` (Watch: `.watchkitapp`, widgets: `.widgets`) |
| Versie | 1.0.0 (build 5) |
| Platforms | iOS/iPadOS 17+, watchOS 10+ |
| Talen | 33 Europese talen: Engels, Nederlands, Duits, Frans, Spaans, Italiaans, Portugees (PT), Catalaans, Roemeens, Grieks, Zweeds, Deens, Noors, IJslands, Fins, Pools, Tsjechisch, Slowaaks, Sloveens, Kroatisch, Servisch, Macedonisch, Bulgaars, Hongaars, Ests, Lets, Litouws, Russisch, Oekraïens, Turks, Iers, Maltees, Albanees |
| Team | EU3UY476T6 |

## Functies

**iPhone & iPad**
- **Sportschoolpas** – op Vandaag onder "Start training": je QR- of barcode voor de toegang, schermvullend op wit met maximale helderheid. Toevoegen door te scannen, uit een screenshot of door het nummer te typen (EAN, Code 128, Code 39, ITF, QR, PDF417, Aztec). Ook op de Apple Watch. Code versleuteld in iCloud.
- **Herinneringen** (lokale meldingen, geen server) – vaste trainingsdagen met tijdstip, na X dagen niet getraind, weekoverzicht op zondag 20:00. Dagen waarop al getraind is (ook op Watch/iPad) worden overgeslagen.
- **Vandaag** – training starten of hervatten, weekoverzicht met trainingsdagen, weken-op-rij, volume, laatste training, schema's, lichaamsgegevens en badges.
- **Trainen** – per oefening sets met gewicht en herhalingen, *wat je vorige keer deed* staat erbij en wordt vooraf ingevuld. Toetsenbordbalk met −/+ 2,5 kg en −/+ 1 herhaling, set afvinken, setsoort (warming-up, dropset, tot falen), notitie per oefening. Cardio met minuten en kilometers.
- **Rusttimer** – start automatisch na een set, ±15 s, melding als de app op de achtergrond staat.
- **Live Activity** – oefening, sets en rusttimer op het toegangsscherm en in het Dynamic Island.
- **Geschiedenis** – maandkalender met een stip op elke trainingsdag; tik een datum voor alles wat je die dag deed. Trainingen achteraf aanpassen of verwijderen.
- **Voortgang** – grafieken in Insights-stijl (grote waarde bovenaan, met je vinger over de grafiek scrollen voor elk punt, periode 1M/3M/6M/1J/Alles, min/gemiddeld/max): per oefening van zwaarste set, geschat 1RM en volume, plus records. Trainingen en volume per week. Gewicht en vetpercentage (uit Gezondheid of zelf ingevoerd).
- **Bibliotheek** – ±125 standaard oefeningen (apparaten, plate-loaded, smith machine, kabel, vrije gewichten, functional, cardio) per spiergroep, eigen oefeningen, favorieten, archiveren. Schema's (bijv. "Maandag: benen") die je met één tik start.
- **Importeren uit notities** – plak je oude notities; Liftr herkent regels als `Leg press 3x12 40 kg`, `Chest press 3 x 10 x 35kg`, `Lat pulldown 30kg 12, 35kg 10`, `Loopband 20 min 2,5 km` en datums als `3-11-2026` of `maandag 3 november`. Je controleert en past aan vóór het importeren.
- **Exporteren** als CSV.
- **Privacy** – privacypagina in Instellingen.
- **Siri / Opdrachten** – "Start een training in Liftr".
- **Widgets** – deze week (klein/middel) en toegangsscherm-widgets.

**Apple Watch**
- Training starten (leeg of vanuit een schema) zonder iPhone in de buurt.
- Live hartslag, calorieën en tijd.
- Gewicht en herhalingen met de Digital Crown, set met één tik afvinken.
- Rusttimer met tik op de pols.
- Complicatie om direct te starten.

**Apple Gezondheid**
- Schrijft: workouts (krachttraining; loopband/fiets/roeien/crosstrainer als cardio), actieve energie, afstand, gewicht, vetpercentage, vetvrije massa. Op de Watch ook hartslag via de live workout.
- Leest: gewicht, vetpercentage, vetvrije massa, lengte, rusthartslag.
- Sets, herhalingen en volume gaan als metadata mee met de workout.

**Synchronisatie** – Instellingen toont de iCloud-status (gesynchroniseerd + tijdstip, iCloud uit, probleem). SwiftData + CloudKit (privé iCloud-database `iCloud.nl.lrvdlinden.liftr`) tussen iPhone, iPad en Watch. Widgets lezen via App Group `group.nl.lrvdlinden.liftr`.

## Project

```
Liftr/               iPhone/iPad-app
LiftrWatch/          Apple Watch-app
LiftrWidgets/        widgets + Live Activity
LiftrWatchWidgets/   Watch-complicatie
Shared/              modellen, opslag, statistiek, thema (alle targets)
Config/              Info.plist's en entitlements
tools/               gen_xcodeproj.py, make_icon.py
```

### Vertalingen

Teksten staan in de code als `tr("English", "Nederlands")` (of `trf` met `{1}`-plaatshouders). Overige talen komen uit `Shared/Localization/liftr-<taal>.json`.

1. `python3 tools/extract_strings.py` – verzamelt alle teksten in `tools/translations/source.json`
2. nieuwe sleutels vertalen in `tools/translations/<taal>.json`
3. `python3 tools/build_translations.py` – controleert (ontbrekend, plaatshouders) en bouwt de tabellen, Info.plist-teksten, MoreAppsCard en footer

Het Xcode-project wordt gegenereerd met `python3 tools/gen_xcodeproj.py` (alleen nodig als targets of build-instellingen veranderen; Swift-bestanden worden automatisch opgepakt via gesynchroniseerde mappen).

### Eenmalig in Apple Developer

1. App ID `nl.lrvdlinden.liftr` (+ `.watchkitapp`, `.widgets`, `.watchkitapp.widgets`) met HealthKit, iCloud (CloudKit) en App Groups – Xcode doet dit automatisch met *Automatically manage signing*.
2. iCloud-container `iCloud.nl.lrvdlinden.liftr` en App Group `group.nl.lrvdlinden.liftr`.
3. Na de eerste testversie: in het CloudKit Dashboard het schema naar **Production** zetten vóór de App Store-release.

## Standaard onderdelen

- "Meer apps van de ontwikkelaar"-kaart en app-info-footer onderaan Instellingen.
- Review-vraag: eerste keer 4 uur na eerste gebruik, daarna hooguit 1× per week, alleen op een rustig moment (niet tijdens welkom of een training).
