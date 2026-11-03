# Liftr

Sportschool-logboek voor iPhone, iPad en Apple Watch. Houd per training bij welke oefeningen je deed, hoeveel sets, herhalingen en welk gewicht per herhaling – en zie hoe je sterker wordt. Volledig gekoppeld aan Apple Gezondheid.

| | |
|---|---|
| Bundle ID | `nl.lrvdlinden.liftr` (Watch: `.watchkitapp`, widgets: `.widgets`) |
| Versie | 1.0.0 (build 1) |
| Platforms | iOS/iPadOS 17+, watchOS 10+ |
| Talen | Nederlands, Engels |
| Team | EU3UY476T6 |

## Functies

**iPhone & iPad**
- **Vandaag** – training starten of hervatten, weekoverzicht met trainingsdagen, weken-op-rij, volume, laatste training, schema's, lichaamsgegevens en badges.
- **Trainen** – per oefening sets met gewicht en herhalingen, *wat je vorige keer deed* staat erbij en wordt vooraf ingevuld. Toetsenbordbalk met −/+ 2,5 kg en −/+ 1 herhaling, set afvinken, setsoort (warming-up, dropset, tot falen), notitie per oefening. Cardio met minuten en kilometers.
- **Rusttimer** – start automatisch na een set, ±15 s, melding als de app op de achtergrond staat.
- **Live Activity** – oefening, sets en rusttimer op het toegangsscherm en in het Dynamic Island.
- **Geschiedenis** – maandkalender met een stip op elke trainingsdag; tik een datum voor alles wat je die dag deed. Trainingen achteraf aanpassen of verwijderen.
- **Voortgang** – per oefening grafieken van zwaarste set, geschat 1RM en volume, plus records. Trainingen en volume per week. Gewicht en vetpercentage (uit Gezondheid of zelf ingevoerd).
- **Bibliotheek** – ±50 standaard oefeningen (apparaten, vrije gewichten, kabel, cardio) per spiergroep, eigen oefeningen, favorieten, archiveren. Schema's (bijv. "Maandag: benen") die je met één tik start.
- **Importeren uit notities** – plak je oude notities; Liftr herkent regels als `Leg press 3x12 40 kg`, `Chest press 3 x 10 x 35kg`, `Lat pulldown 30kg 12, 35kg 10`, `Loopband 20 min 2,5 km` en datums als `3-11-2026` of `maandag 3 november`. Je controleert en past aan vóór het importeren.
- **Exporteren** als CSV.
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

**Synchronisatie** – SwiftData + CloudKit (privé iCloud-database `iCloud.nl.lrvdlinden.liftr`) tussen iPhone, iPad en Watch. Widgets lezen via App Group `group.nl.lrvdlinden.liftr`.

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

Het Xcode-project wordt gegenereerd met `python3 tools/gen_xcodeproj.py` (alleen nodig als targets of build-instellingen veranderen; Swift-bestanden worden automatisch opgepakt via gesynchroniseerde mappen).

### Eenmalig in Apple Developer

1. App ID `nl.lrvdlinden.liftr` (+ `.watchkitapp`, `.widgets`, `.watchkitapp.widgets`) met HealthKit, iCloud (CloudKit) en App Groups – Xcode doet dit automatisch met *Automatically manage signing*.
2. iCloud-container `iCloud.nl.lrvdlinden.liftr` en App Group `group.nl.lrvdlinden.liftr`.
3. Na de eerste testversie: in het CloudKit Dashboard het schema naar **Production** zetten vóór de App Store-release.

## Standaard onderdelen

- "Meer apps van de ontwikkelaar"-kaart en app-info-footer onderaan Instellingen.
- Review-vraag: eerste keer 4 uur na eerste gebruik, daarna hooguit 1× per week, alleen op een rustig moment (niet tijdens welkom of een training).
