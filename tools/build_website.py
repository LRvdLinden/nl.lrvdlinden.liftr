#!/usr/bin/env python3
"""Builds the static site for liftr.nu into website/ (Dutch at /, English at /en/).

    python3 tools/build_website.py

Upload the contents of website/ to the web root. Exercise names come from Shared/ExerciseLibrary.swift.
"""
import html, json, os, random, re, datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "website")
DOMAIN = "https://liftr.nu"
TODAY = datetime.date.today().isoformat()
# Fill in once Liftr is live, e.g. "https://apps.apple.com/nl/app/liftr/id1234567890".
# While empty, the site shows "Coming soon to the App Store" (never on the og-images).
APP_STORE_URL = ""

lib = open(os.path.join(ROOT, "Shared/ExerciseLibrary.swift"), encoding="utf-8").read()
SEEDS = [dict(id=m[0], en=m[1], nl=m[2], muscle=m[3], kind=m[4]) for m in
         re.findall(r'\.init\(id: "([^"]+)", en: "([^"]+)", nl: "([^"]+)", muscle: \.(\w+), kind: \.(\w+)\)', lib)]
COUNT = {}
for s in SEEDS:
    COUNT[s["muscle"]] = COUNT.get(s["muscle"], 0) + 1

LANG_NAMES = ["English", "Nederlands", "Deutsch", "Français", "Español", "Italiano", "Português", "Català", "Română", "Ελληνικά",
              "Svenska", "Dansk", "Norsk", "Íslenska", "Suomi", "Polski", "Čeština", "Slovenčina", "Slovenščina", "Hrvatski",
              "Српски", "Македонски", "Български", "Magyar", "Eesti", "Latviešu", "Lietuvių", "Русский", "Українська", "Türkçe",
              "Gaeilge", "Malti", "Shqip"]
N_LANGS = len(LANG_NAMES)

e = html.escape

T = {
    "nl": dict(
        locale="nl_NL", alt_locale="en_GB", hreflang="nl", prefix="", other="/en/", other_label="EN",
        title="Liftr – sportschool-logboek voor iPhone en Apple Watch",
        desc="Houd elke training bij: oefeningen, sets, herhalingen en gewicht. Met rusttimer, voortgangsgrafieken, sportschoolpas en Apple Gezondheid.",
        og_alt="Liftr – elke herhaling telt. Sportschool-logboek voor iPhone, iPad en Apple Watch.",
        nav=[("#functies", "Functies"), ("#apparaten", "Apparaten"), ("#gezondheid", "Gezondheid"), ("#privacy", "Privacy")],
        soon="Download in de App Store", soon_pre="Binnenkort in de App Store", features_btn="Bekijk de functies", nav_cta="Bekijk de app",
        eyebrow="Sportschool-logboek voor iPhone, iPad & Apple Watch",
        h1='Elke herhaling. <span class="outline">Telt.</span>',
        lead="Weg met de notities-app. Liftr houdt per training bij wat je deed – oefening, sets, herhalingen en gewicht – en laat zien hoe je sterker wordt. Met rusttimer, je sportschoolpas en Apple Gezondheid.",
        stats=[("oefeningen", len(SEEDS), "+", True), ("talen", N_LANGS, "", False), ("accounts nodig", 0, "", False), ("privé", 100, "%", False)],
        notes=[("Rust voorbij", "Tijd voor je volgende set 💪"), ("🏆 Nieuw record", "Chest press · 47,5 kg × 10"),
               ("Opgeslagen in Gezondheid", "Krachttraining · 52 min · 318 kcal"), ("Tijd voor je training", "Houd je reeks van 6 weken vast 🔥")],
        ticker="Oefeningen",
        f_kicker="De app", f_h2='Gemaakt voor<br>de <span class="grad">vloer</span>.',
        f_sub="Alles wat je tussen twee sets nodig hebt. Groot, snel en met één hand te bedienen.",
        steps=[
            ("workout", "01", "Log in seconden", "Sets, herhalingen en gewicht per oefening. Wat je vorige keer deed staat er al ingevuld – aanpassen met −/+ 2,5 kg en afvinken.", "#ff6b00"),
            ("rest", "02", "Rusttimer & Live Activity", "Na elke set start de rusttimer. Op je toegangsscherm en in het Dynamic Island zie je oefening, sets en rust – ook met je telefoon in je zak.", "#ff8a1f"),
            ("progress", "03", "Zie je groei", "Grafieken van je zwaarste set, geschat maximum en volume. Schuif met je vinger over de lijn voor elke training, kies 1M tot alles, met min, gemiddelde en max.", "#ffb15c"),
            ("history", "04", "Terug in de tijd", "Een kalender met een stip op elke trainingsdag. Tik een datum aan en zie precies wat je die dag deed.", "#33cc73"),
            ("pass", "05", "Je passen bij de deur", "Tik rechtsboven op Vandaag op het ronde pasknopje. Kies je sportschool met logo en huiskleur, voeg zoveel passen toe als je wilt en de kaart draait om naar je QR- of barcode. Schermvullend, helderheid op max – ook op je Apple Watch.", "#ffffff"),
            ("import", "06", "Nooit meer notities", "Plak je oude notities en Liftr maakt er trainingen van: “Leg press 3x12 40 kg” wordt netjes een oefening met sets.", "#ff375f"),
        ],
        m_kicker="Bibliotheek", m_h2=f'{len(SEEDS)}+ oefeningen.<br><span class="outline">Klaar</span> voor gebruik.',
        m_sub="Apparaten, smith machine, kabels, vrije gewichten, functional en cardio. Mist er een? Voeg hem in een paar tikken toe.",
        muscles=[("legs", "Benen & billen", ["legs", "glutes"], "#ff6b00"), ("chest", "Borst", ["chest"], "#ff375f"),
                 ("back", "Rug", ["back"], "#3b82f6"), ("shoulders", "Schouders", ["shoulders"], "#a855f7"),
                 ("arms", "Armen", ["arms"], "#f59e0b"), ("core", "Core", ["core"], "#14b8a6"),
                 ("full", "Functional", ["fullBody"], "#ef4444"), ("cardio", "Cardio", ["cardio"], "#33cc73")],
        exercises_word="oefeningen",
        d_kicker="Apparaten", d_h2='iPhone, iPad<br>én <span class="grad">Apple Watch</span>.',
        d_sub="Start je training op je pols, zonder telefoon. Hartslag en calorieën live, gewicht en herhalingen met de Digital Crown. Alles synchroniseert via je eigen iCloud.",
        h_kicker="Apple Gezondheid", h_h2='Volledig<br>gekoppeld.',
        h_sub="Liftr werkt samen met de Gezondheid-app – alleen met jouw toestemming.",
        health=[("Trainingen", "Krachttraining en cardio met duur, calorieën en afstand.", "#ff6b00"),
                ("Hartslag", "Live op je Apple Watch tijdens het trainen.", "#ff375f"),
                ("Gewicht & lichaam", "Gewicht, vetpercentage en vetvrije massa – ook van je slimme weegschaal.", "#33cc73"),
                ("Herinneringen", "Vaste trainingsdagen, na een paar dagen rust en een weekoverzicht op zondag.", "#ffb15c")],
        p_kicker="Privacy", p_h2='Jouw training.<br><span class="outline">Jouw</span> data.',
        privacy=[("🔒", "Geen account", "Geen inlog, geen server. Je gegevens staan op je apparaat en in je eigen iCloud."),
                 ("🙈", "Geen tracking", "Geen analytics, geen advertenties, geen code van derden."),
                 ("🔐", "Versleuteld", "De code van je sportschoolpas is end-to-end versleuteld in iCloud.")],
        privacy_link="Lees de privacyverklaring",
        l_kicker="Talen", l_h2=f'{N_LANGS} talen.<br>Heel <span class="grad">Europa</span>.',
        final_h2='Klaar voor<br>je volgende set?', final_p="Liftr komt binnenkort naar de App Store voor iPhone, iPad en Apple Watch – in 33 talen.",
        footer_privacy="Privacy", footer_more="Meer apps",
        screens=dict(today="Vandaag", date="dinsdag 3 november", ready="Klaar om te trainen?", start="▶ START TRAINING",
                     week="Deze week", streak="6 wk op rij", w_label="Trainingen", m_label="Deze maand", v_label="Getild",
                     tabs=["Vandaag", "Historie", "Voortgang", "Bibliotheek", "Instellingen"],
                     legs="Benen", last="Vorige keer: 3 × 12 · 40 kg", set="SET", reps="HERH", rest="RUST", skip="⏭",
                     lp="Leg press", lc="Leg curl (zittend)", la_sets="3 van 9 sets", heaviest="Zwaarste set", latest="Laatste: 3 nov",
                     chips=["1M", "3M", "6M", "1J", "Alles"], hist="Geschiedenis", month="November 2026", wd=["MA", "DI", "WO", "DO", "VR", "ZA", "ZO"],
                     pass_title="Mijn sportschool", pass_hint="Schermhelderheid staat op maximaal", pass_tap="Tik om te tonen bij de ingang", pass_wallet="Mijn passen", pass_two="Boulderhal", pass_label="Sportschoolpas", pass_flip="Tik op een pas om hem om te draaien", last_w="Laatste training",
                     imp="Importeren", imp_note="3-11-2026\nLeg press 3x12 40 kg\nChest press 3 x 10 x 25 kg\nLat pulldown 30kg 12, 35kg 10\nLoopband 20 min 2,5 km",
                     imp_rows=[("Leg press", "3 × 12 · 40 kg"), ("Chest press", "3 × 10 · 25 kg"), ("Lat pulldown", "2 sets"), ("Loopband", "20 min · 2,5 km")],
                     watch_set="Set 2 van 3", watch_done="Set klaar", progress="Voortgang"),
    ),
    "en": dict(
        locale="en_GB", alt_locale="nl_NL", hreflang="en", prefix="en/", other="/", other_label="NL",
        title="Liftr – your gym logbook for iPhone, iPad & Apple Watch",
        desc="Track every workout: exercises, sets, reps and weight. Rest timer, progress charts, gym pass and Apple Health. For iPhone, iPad and Apple Watch.",
        og_alt="Liftr – every rep counts. Gym logbook for iPhone, iPad and Apple Watch.",
        nav=[("#features", "Features"), ("#devices", "Devices"), ("#health", "Health"), ("#privacy", "Privacy")],
        soon="Download on the App Store", soon_pre="Coming soon to the App Store", features_btn="See the features", nav_cta="See the app",
        eyebrow="Gym logbook for iPhone, iPad & Apple Watch",
        h1='Every rep. <span class="outline">Counts.</span>',
        lead="Ditch the notes app. Liftr logs what you did in every workout – exercise, sets, reps and weight – and shows how you're getting stronger. With a rest timer, your gym pass and Apple Health.",
        stats=[("exercises", len(SEEDS), "+", True), ("languages", N_LANGS, "", False), ("accounts needed", 0, "", False), ("private", 100, "%", False)],
        notes=[("Rest is over", "Time for your next set 💪"), ("🏆 New record", "Chest press · 47.5 kg × 10"),
               ("Saved in Health", "Strength training · 52 min · 318 kcal"), ("Time to train", "Keep your 6-week streak going 🔥")],
        ticker="Exercises",
        f_kicker="The app", f_h2='Made for<br>the <span class="grad">gym floor</span>.',
        f_sub="Everything you need between two sets. Big, fast and easy to use with one hand.",
        steps=[
            ("workout", "01", "Log in seconds", "Sets, reps and weight for every exercise. What you did last time is already filled in – adjust with −/+ 2.5 kg and tick it off.", "#ff6b00"),
            ("rest", "02", "Rest timer & Live Activity", "The rest timer starts after every set. Your Lock Screen and Dynamic Island show exercise, sets and rest – even with your phone in your pocket.", "#ff8a1f"),
            ("progress", "03", "Watch yourself grow", "Charts of your heaviest set, estimated max and volume. Slide your finger along the line for any workout, pick 1M to all time, with min, average and max.", "#ffb15c"),
            ("history", "04", "Back in time", "A calendar with a dot on every training day. Tap a date and see exactly what you did that day.", "#33cc73"),
            ("pass", "05", "Your passes at the door", "Tap the round pass button at the top right of Today. Pick your gym with its logo and colours, add as many passes as you like, and the card flips over to your QR code or barcode. Full screen, brightness up – on your Apple Watch too.", "#ffffff"),
            ("import", "06", "No more notes", "Paste your old notes and Liftr turns them into workouts: “Leg press 3x12 40 kg” becomes a proper exercise with sets.", "#ff375f"),
        ],
        m_kicker="Library", m_h2=f'{len(SEEDS)}+ exercises.<br><span class="outline">Ready</span> to go.',
        m_sub="Machines, smith machine, cables, free weights, functional and cardio. Missing one? Add it in a few taps.",
        muscles=[("legs", "Legs & glutes", ["legs", "glutes"], "#ff6b00"), ("chest", "Chest", ["chest"], "#ff375f"),
                 ("back", "Back", ["back"], "#3b82f6"), ("shoulders", "Shoulders", ["shoulders"], "#a855f7"),
                 ("arms", "Arms", ["arms"], "#f59e0b"), ("core", "Core", ["core"], "#14b8a6"),
                 ("full", "Functional", ["fullBody"], "#ef4444"), ("cardio", "Cardio", ["cardio"], "#33cc73")],
        exercises_word="exercises",
        d_kicker="Devices", d_h2='iPhone, iPad<br>and <span class="grad">Apple Watch</span>.',
        d_sub="Start your workout on your wrist, no phone needed. Live heart rate and calories, weight and reps with the Digital Crown. Everything syncs through your own iCloud.",
        h_kicker="Apple Health", h_h2='Fully<br>connected.',
        h_sub="Liftr works with the Health app – only with your permission.",
        health=[("Workouts", "Strength and cardio with duration, calories and distance.", "#ff6b00"),
                ("Heart rate", "Live on your Apple Watch while you train.", "#ff375f"),
                ("Weight & body", "Weight, body fat and lean mass – also from your smart scale.", "#33cc73"),
                ("Reminders", "Fixed training days, a nudge after a few rest days and a weekly summary on Sunday.", "#ffb15c")],
        p_kicker="Privacy", p_h2='Your training.<br><span class="outline">Your</span> data.',
        privacy=[("🔒", "No account", "No sign-in, no server. Your data lives on your device and in your own iCloud."),
                 ("🙈", "No tracking", "No analytics, no ads, no third-party code."),
                 ("🔐", "Encrypted", "Your gym pass code is end-to-end encrypted in iCloud.")],
        privacy_link="Read the privacy policy",
        l_kicker="Languages", l_h2=f'{N_LANGS} languages.<br>All of <span class="grad">Europe</span>.',
        final_h2='Ready for<br>your next set?', final_p="Liftr is coming soon to the App Store for iPhone, iPad and Apple Watch – in 33 languages.",
        footer_privacy="Privacy", footer_more="More apps",
        screens=dict(today="Today", date="Tuesday 3 November", ready="Ready to train?", start="▶ START WORKOUT",
                     week="This week", streak="6 wk streak", w_label="Workouts", m_label="This month", v_label="Lifted",
                     tabs=["Today", "History", "Progress", "Library", "Settings"],
                     legs="Legs", last="Last time: 3 × 12 · 40 kg", set="SET", reps="REPS", rest="REST", skip="⏭",
                     lp="Leg press", lc="Seated leg curl", la_sets="3 of 9 sets", heaviest="Heaviest set", latest="Latest: 3 Nov",
                     chips=["1M", "3M", "6M", "1Y", "All"], hist="History", month="November 2026", wd=["MO", "TU", "WE", "TH", "FR", "SA", "SU"],
                     pass_title="My gym", pass_hint="Screen brightness is turned up", pass_tap="Tap to show at the entrance", pass_wallet="My passes", pass_two="Climbing gym", pass_label="Gym pass", pass_flip="Tap a pass to flip it over", last_w="Last workout",
                     imp="Import", imp_note="3-11-2026\nLeg press 3x12 40 kg\nChest press 3 x 10 x 25 kg\nLat pulldown 30kg 12, 35kg 10\nTreadmill 20 min 2.5 km",
                     imp_rows=[("Leg press", "3 × 12 · 40 kg"), ("Chest press", "3 × 10 · 25 kg"), ("Lat pulldown", "2 sets"), ("Treadmill", "20 min · 2.5 km")],
                     watch_set="Set 2 of 3", watch_done="Set done", progress="Progress"),
    ),
}

DUMBBELL = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11"/></svg>'
ICONS = {
    "legs": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 2v7l-2 5 1 8M15 2v7l2 5-1 8M7 22h3M14 22h3"/></svg>',
    "chest": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 6c3-1 5 0 8 2 3-2 5-3 8-2v6c0 4-4 6-8 6s-8-2-8-6z"/><path d="M12 8v10"/></svg>',
    "back": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v20M7 5l5 3 5-3M6 10l6 3 6-3M7 15l5 3 5-3"/></svg>',
    "shoulders": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="5" r="2.5"/><path d="M3 13c0-3 3-5 9-5s9 2 9 5M7 10v11M17 10v11"/></svg>',
    "arms": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 20c0-6 2-9 5-11l2-5h3l-1 5c3 0 6 2 6 6 0 3-3 5-7 5z"/></svg>',
    "core": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="6" y="3" width="12" height="18" rx="4"/><path d="M12 3v18M6 9h12M6 15h12"/></svg>',
    "full": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2L4 14h7l-1 8 9-12h-7z"/></svg>',
    "cardio": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12h4l2-5 4 10 2-5h6"/></svg>',
}
HEART = '<svg class="heart" viewBox="0 0 24 24" fill="currentColor"><path d="M12 21s-7.5-4.6-9.6-9.3C.9 8.3 3 4.5 6.7 4.5c2.1 0 3.6 1.2 5.3 3.1 1.7-1.9 3.2-3.1 5.3-3.1 3.7 0 5.8 3.8 4.3 7.2C19.5 16.4 12 21 12 21z"/></svg>'
PLATES = '<svg class="hero-plates" viewBox="0 0 200 200" aria-hidden="true"><g fill="none" stroke="#fff"><circle cx="100" cy="100" r="96" stroke-width="6"/><circle cx="100" cy="100" r="76" stroke-width="2" stroke-dasharray="4 8"/><circle cx="100" cy="100" r="54" stroke-width="10"/><circle cx="100" cy="100" r="14" stroke-width="6"/></g></svg>'


def qr_svg(seed=7, n=25):
    """Decorative QR-style pattern (not a real code)."""
    rnd = random.Random(seed)
    cells = []
    def finder(x, y):
        return [(x + i, y + j) for i in range(7) for j in range(7)
                if i in (0, 6) or j in (0, 6) or (2 <= i <= 4 and 2 <= j <= 4)]
    reserved = set()
    for fx, fy in ((0, 0), (n - 7, 0), (0, n - 7)):
        for i in range(-1, 8):
            for j in range(-1, 8):
                reserved.add((fx + i, fy + j))
        cells += finder(fx, fy)
    for x in range(n):
        for y in range(n):
            if (x, y) not in reserved and rnd.random() < 0.48:
                cells.append((x, y))
    rects = "".join(f'<rect x="{x}" y="{y}" width="1.02" height="1.02"/>' for x, y in cells)
    return f'<svg class="qr" viewBox="-2 -2 {n + 4} {n + 4}" aria-hidden="true"><rect x="-2" y="-2" width="{n + 4}" height="{n + 4}" fill="#fff"/><g fill="#111">{rects}</g></svg>'


def chart_svg():
    pts = [32.5, 35, 35, 37.5, 37.5, 40, 40, 42.5, 45, 45, 47.5, 50, 50, 52.5]
    w, h = 300, 140
    lo, hi = 30, 55
    xy = [(i * w / (len(pts) - 1), h - (p - lo) / (hi - lo) * h) for i, p in enumerate(pts)]
    line = "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in xy)
    area = line + f" L{w},{h} L0,{h} Z"
    lx, ly = xy[-1]
    return f'''<svg class="chart" viewBox="0 0 {w} {h}" preserveAspectRatio="none" aria-hidden="true">
<defs><linearGradient id="cg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ff6b00" stop-opacity=".5"/><stop offset="1" stop-color="#ff6b00" stop-opacity="0"/></linearGradient></defs>
<g stroke="rgba(255,255,255,.08)"><path d="M0 35H300M0 70H300M0 105H300"/></g>
<path d="{area}" fill="url(#cg)"/><path d="{line}" fill="none" stroke="#ff6b00" stroke-width="3" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke"/>
<circle cx="{lx:.1f}" cy="{ly:.1f}" r="5" fill="#ff6b00" stroke="#fff" stroke-width="2"/></svg>'''


def screens(s, unit_dec):
    tabs = "".join(f'<div class="{ "on" if i == 0 else ""}"><i></i>{e(t)}</div>' for i, t in enumerate(s["tabs"]))
    dots_on = [True, False, True, False, False, False, False]
    dots = "".join(f'<div><i class="{"on" if on else ""}"></i>{d[0]}<br>{n}</div>'
                   for on, d, n in zip(dots_on, s["wd"], range(2, 9)))
    today = f'''<div class="scr" data-screen="today">
  <div style="display:flex;align-items:center;justify-content:space-between"><div><div class="t-sub">{e(s["date"])}</div><div class="t-big">{e(s["today"])}</div></div>
    <div style="width:12cqw;height:12cqw;border-radius:50%;background:#FB7819;display:grid;place-items:center;box-shadow:0 1.5cqw 4cqw rgba(251,120,25,.45)"><svg viewBox="0 0 24 24" style="width:6cqw" fill="#fff"><rect x="3" y="5" width="18" height="14" rx="3"/><rect x="6" y="9" width="7" height="2" rx="1" fill="#FB7819"/><rect x="6" y="13" width="10" height="2" rx="1" fill="#FB7819"/></svg></div></div>
  <div class="hot"><div class="t-mid">{e(s["ready"])}</div><div class="wbtn">{e(s["start"])}</div></div>
  <div class="c"><div class="muted" style="font-size:2.6cqw;font-weight:800;text-transform:uppercase">{e(s["last_w"])}</div><div class="t-mid" style="font-size:4.6cqw;margin-top:1cqw">{e(s["legs"])}</div><div class="muted" style="font-size:3cqw">52 min · 18 sets · 8,4 t</div></div>
  <div class="c"><div style="display:flex;justify-content:space-between;margin-bottom:2.4cqw"><b class="t-mid" style="font-size:4.6cqw">{e(s["week"])}</b><span class="acc" style="font-weight:800;font-size:3cqw">🔥 {e(s["streak"])}</span></div><div class="dots">{dots}</div></div>
  <div class="row3"><div class="tile"><b>2</b><small>{e(s["w_label"])}</small></div><div class="tile"><b>9</b><small>{e(s["m_label"])}</small></div><div class="tile"><b>8,4 t</b><small>{e(s["v_label"])}</small></div></div>
  <div class="tabbar">{tabs}</div>
</div>'''
    sets = "".join(f'<div class="set {"done" if i < 2 else ""}"><span>{i + 1}</span><span>{w}</span><span>12</span><span class="ok">✓</span></div>'
                   for i, w in enumerate(["40", "42,5" if unit_dec == "," else "42.5", "42,5" if unit_dec == "," else "42.5"]))
    workout = f'''<div class="scr" data-screen="workout">
  <div style="display:flex;justify-content:space-between;align-items:baseline"><div class="t-big">{e(s["legs"])}</div><span class="muted" style="font-weight:700">24:18</span></div>
  <div class="c"><div class="t-mid" style="font-size:5cqw">{e(s["lp"])}</div><div class="acc" style="font-size:2.9cqw;margin:1cqw 0 3cqw">{e(s["last"])}</div>
    <div class="colh"><span>{e(s["set"])}</span><span>KG</span><span>{e(s["reps"])}</span><span>✓</span></div><div class="sets">{sets}</div></div>
  <div class="c"><div class="t-mid" style="font-size:5cqw">{e(s["lc"])}</div><div class="colh" style="margin-top:3cqw"><span>{e(s["set"])}</span><span>KG</span><span>{e(s["reps"])}</span><span>✓</span></div>
    <div class="sets" style="margin-top:1.8cqw"><div class="set"><span>1</span><span>30</span><span>12</span><span class="ok">✓</span></div></div></div>
  <div class="rest"><div class="ringt"></div><div><div class="muted" style="font-size:2.4cqw;font-weight:800">{e(s["rest"])}</div><b>1:12</b></div><span class="pill">+15</span></div>
</div>'''
    rest = f'''<div class="scr lock" data-screen="rest">
  <div class="date">{e(s["date"])}</div><div class="clock">18:42</div>
  <div class="la"><div class="ic">{DUMBBELL.replace('stroke="currentColor"', 'stroke="#fff"')}</div><div><b>{e(s["lp"])}</b><div class="muted" style="font-size:3cqw">{e(s["la_sets"])}</div></div>
    <div class="tm"><div class="muted" style="font-size:2.4cqw;font-weight:800">{e(s["rest"])}</div><b>0:47</b></div></div>
</div>'''
    chips = "".join(f'<span class="{"on" if i == 1 else ""}">{e(c)}</span>' for i, c in enumerate(s["chips"]))
    val = "52,5" if unit_dec == "," else "52.5"
    progress = f'''<div class="scr" data-screen="progress">
  <div class="t-big">{e(s["progress"])}</div>
  <div class="c"><div class="muted" style="font-size:2.6cqw;font-weight:800;text-transform:uppercase">{e(s["heaviest"])} · {e(s["lp"])}</div>
    <div style="display:flex;align-items:baseline;gap:2cqw"><span class="t-big" style="font-size:12cqw">{val}</span><span class="muted" style="font-weight:700">kg</span><span class="acc" style="margin-left:auto;font-weight:800">↗ +20</span></div>
    <div class="muted" style="font-size:2.8cqw;margin-bottom:2cqw">{e(s["latest"])}</div>{chart_svg()}<div class="chips" style="margin-top:3cqw">{chips}</div></div>
  <div class="row3"><div class="tile"><b>32,5</b><small>Min</small></div><div class="tile"><b>43</b><small>Ø</small></div><div class="tile"><b>{val}</b><small>Max</small></div></div>
</div>'''.replace("32,5", "32,5" if unit_dec == "," else "32.5")
    trained = {2, 4, 6, 9, 11, 13, 16, 18, 20, 23, 25, 27, 30, 3}
    cal = "".join(f'<span class="h">{d}</span>' for d in s["wd"]) + "<span></span><span></span>"
    cal += "".join(f'<span class="{"tr" if d in trained else ""}{" sel" if d == 3 else ""}">{d}</span>' for d in range(1, 31))
    history = f'''<div class="scr" data-screen="history">
  <div class="t-big">{e(s["hist"])}</div>
  <div class="c"><div class="t-mid" style="text-align:center;font-size:4.8cqw;margin-bottom:3cqw">{e(s["month"])}</div><div class="cal">{cal}</div></div>
  <div class="c"><div class="t-mid" style="font-size:4.6cqw">{e(s["legs"])}</div><div class="muted" style="font-size:3cqw">52 min · 18 sets · 8,4 t</div><div style="font-size:3.2cqw;margin-top:1.4cqw">{e(s["lp"])} · {e(s["lc"])} · Hip thrust</div></div>
</div>'''
    def card_head(name, mono, bg, ink):
        return (f'<div style="display:flex;align-items:center;gap:2.4cqw">'
                f'<div style="width:9cqw;height:9cqw;border-radius:2.2cqw;background:rgba(255,255,255,.18);display:grid;place-items:center;font-weight:900;font-size:3.6cqw;color:{ink}">{mono}</div>'
                f'<b style="font-size:4.4cqw;color:{ink};flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{e(name)}</b>'
                f'<span style="font-size:2.2cqw;font-weight:800;letter-spacing:.06em;text-transform:uppercase;color:{ink};opacity:.72">{e(s["pass_label"])}</span></div>')
    passs = f'''<div class="scr" data-screen="pass">
  <div style="display:flex;align-items:center;justify-content:space-between"><div class="t-big">{e(s["pass_wallet"])}</div>
    <div style="width:10cqw;height:10cqw;border-radius:50%;background:#2a2a2a;display:grid;place-items:center;color:#FF6B00;font-weight:900;font-size:6cqw">+</div></div>
  <div style="background:#FB7819;border-radius:5cqw;padding:4.5cqw;box-shadow:0 2cqw 6cqw rgba(251,120,25,.35)">
    {card_head(s["pass_title"], "MS", "#FB7819", "#fff")}
    <div style="background:#fff;border-radius:3.5cqw;margin-top:3.5cqw;padding:3.5cqw;display:grid;justify-items:center;gap:1.6cqw">
      <div style="width:44cqw">{qr_svg()}</div>
      <div style="font-family:ui-monospace,Menlo,monospace;font-weight:700;font-size:3.6cqw;color:#333">8 7192 4402 1187</div></div>
  </div>
  <div style="background:#5E2D91;border-radius:5cqw;padding:4.5cqw;height:30cqw;display:flex;flex-direction:column;justify-content:space-between">
    {card_head(s["pass_two"], "B", "#5E2D91", "#fff")}
    <div style="display:flex;justify-content:space-between;align-items:flex-end;color:#fff"><div><div style="font-weight:700;font-size:3.2cqw">Sanne de Vries</div>
      <div style="font-family:ui-monospace,Menlo,monospace;font-size:3.2cqw;opacity:.9">2041 7730 5512</div></div><span style="opacity:.6;font-size:4cqw">⟲</span></div>
  </div>
  <div class="muted" style="font-size:2.8cqw;text-align:center">⟲ {e(s["pass_flip"])} · ☀ {e(s["pass_hint"])}</div>
</div>'''
    rows = "".join(f"<div><b>{e(a)}</b><span>{e(b)}</span></div>" for a, b in s["imp_rows"])
    imp = f'''<div class="scr" data-screen="import">
  <div class="t-big">{e(s["imp"])}</div><div class="note">{e(s["imp_note"])}</div>
  <div style="text-align:center;font-size:6cqw" class="acc">↓</div><div class="parsed">{rows}</div>
</div>'''
    pad = f'''<div class="scr pad">
  <div class="side"><div class="t-big" style="font-size:4cqw;padding:0 0 1cqw">Liftr</div>{"".join(f'<div class="{"on" if i == 2 else ""}">{e(t)}</div>' for i, t in enumerate(s["tabs"]))}</div>
  <div class="main"><div class="t-big">{e(s["progress"])}</div>
    <div class="c"><div class="muted" style="font-size:1.3cqw;font-weight:800;text-transform:uppercase">{e(s["heaviest"])} · {e(s["lp"])}</div>
      <div style="display:flex;align-items:baseline;gap:1cqw"><span class="t-big" style="font-size:5cqw">{val}</span><span class="muted">kg</span></div>{chart_svg()}</div>
    <div class="row3"><div class="tile"><b>9</b><small>{e(s["m_label"])}</small></div><div class="tile"><b>8,4 t</b><small>{e(s["v_label"])}</small></div><div class="tile"><b>6</b><small>{e(s["streak"])}</small></div></div>
  </div>
</div>'''
    watch = f'''<div class="scr watch">
  <div class="t-sub">{e(s["lp"])}</div><div class="muted" style="font-size:5cqw">{e(s["watch_set"])}</div>
  <div class="vals"><div class="f"><small>KG</small><b>45</b></div><div><small>{e(s["reps"])}</small><b>12</b></div></div>
  <div class="go">✓ {e(s["watch_done"])}</div><div class="hr">♥ 128 BPM</div>
</div>'''
    return dict(today=today, workout=workout, rest=rest, progress=progress, history=history, pass_=passs, imp=imp, pad=pad, watch=watch)


# Dutch is the default. A browser without Dutch in its languages goes to the English page, unless the
# visitor picked Dutch themselves (stored in localStorage, no cookie) or it's a crawler / link preview.
LANG_REDIRECT = """<script>(function(){try{
if(localStorage.getItem("liftr-lang")==="nl")return;
if(/bot|crawl|spider|slurp|lighthouse|preview|facebookexternalhit|whatsapp|linkedin|twitter|telegram|slack|discord|skype/i.test(navigator.userAgent))return;
var l=((navigator.languages&&navigator.languages.length)?navigator.languages:[navigator.language||""]).join(",").toLowerCase();
if(!/(^|,)nl/.test(l))location.replace("/en/"+location.pathname.replace(/^\\/+/,"")+location.search+location.hash);
}catch(e){}})();</script>
"""


def page_head(lang, t, path, title, desc, og_slug, jsonld):
    url = f"{DOMAIN}/{path}"
    nl_url = f"{DOMAIN}/{path[3:] if path.startswith('en/') else path}"
    en_url = f"{DOMAIN}/en/{path[3:] if path.startswith('en/') else path}"
    og = f"{DOMAIN}/og/{og_slug}-{lang}.png"
    depth = path.count("/")
    rel = "../" * depth
    return f'''<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{e(title)}</title>
<meta name="description" content="{e(desc)}">
<meta name="theme-color" content="#ff6b00">
<meta name="robots" content="index, follow, max-image-preview:large">
<link rel="canonical" href="{url}">
<link rel="alternate" hreflang="nl" href="{nl_url}">
<link rel="alternate" hreflang="en" href="{en_url}">
<link rel="alternate" hreflang="x-default" href="{nl_url}">
<meta property="og:type" content="website">
<meta property="og:site_name" content="Liftr">
<meta property="og:title" content="{e(title)}">
<meta property="og:description" content="{e(desc)}">
<meta property="og:url" content="{url}">
<meta property="og:locale" content="{t["locale"]}">
<meta property="og:locale:alternate" content="{t["alt_locale"]}">
<meta property="og:image" content="{og}">
<meta property="og:image:type" content="image/png">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="{e(t["og_alt"])}">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="{e(title)}">
<meta name="twitter:description" content="{e(desc)}">
<meta name="twitter:image" content="{og}">
<meta name="twitter:image:alt" content="{e(t["og_alt"])}">
<link rel="icon" href="/favicon.ico" sizes="any">
<link rel="icon" type="image/png" sizes="32x32" href="/assets/img/appicon-32.png">
<link rel="apple-touch-icon" href="/assets/img/appicon-180.png">
<link rel="manifest" href="/site.webmanifest">
<link rel="preload" href="/assets/fonts/overpass-latin-900-normal.woff2" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="/assets/css/site.css?v=13">
{LANG_REDIRECT if lang == "nl" else ""}<script type="application/ld+json">{json.dumps(jsonld, ensure_ascii=False)}</script>
</head>'''


def nav_html(t, lang, home_rel, other=None):
    other = other or t["other"]
    links = "".join(f'<a href="{home_rel}{h}">{e(l)}</a>' for h, l in t["nav"])
    return f'''<header class="nav" id="nav">
  <a class="brand" href="{home_rel or './'}" aria-label="Liftr"><img src="/assets/img/appicon-180.png" alt="" width="36" height="36"><b>LIFTR</b></a>
  <nav class="links" aria-label="Menu">{links}<a class="lang" data-setlang="{"en" if lang == "nl" else "nl"}" href="{other}" hreflang="{"en" if lang == "nl" else "nl"}" lang="{"en" if lang == "nl" else "nl"}">{t["other_label"]}</a></nav>
  <a class="btn btn-orange btn-sm" href="{APP_STORE_URL or home_rel + '#download'}">{e(t["soon"] if APP_STORE_URL else t["soon_pre"])}</a>
</header>'''


def footer_html(t, lang, other=None):
    other = other or t["other"]
    pr = "/privacy/" if lang == "nl" else "/en/privacy/"
    home = "/" if lang == "nl" else "/en/"
    return f'''<footer class="footer"><div class="footer-inner">
  <span>© <span data-year>2026</span> Liftr</span>
  <nav><a href="{home}">Liftr</a><a href="{pr}">{e(t["footer_privacy"])}</a><a href="https://lrvdlinden.app">{e(t["footer_more"])}</a><a data-setlang="{"en" if lang == "nl" else "nl"}" href="{other}">{"English" if lang == "nl" else "Nederlands"}</a></nav>
</div></footer>'''


def jsonld_home(lang, t):
    url = f"{DOMAIN}/" + ("" if lang == "nl" else "en/")
    return {"@context": "https://schema.org", "@graph": [
        {"@type": "WebSite", "@id": f"{DOMAIN}/#website", "url": f"{DOMAIN}/", "name": "Liftr", "inLanguage": ["nl", "en"],
         "publisher": {"@id": f"{DOMAIN}/#publisher"}},
        {"@type": "Organization", "@id": f"{DOMAIN}/#publisher", "name": "LRvdLinden", "url": "https://lrvdlinden.app",
         "logo": f"{DOMAIN}/assets/img/appicon-512.png"},
        {"@type": "MobileApplication", "@id": f"{DOMAIN}/#app", "name": "Liftr", "url": url,
         "operatingSystem": "iOS 17, iPadOS 17, watchOS 10", "applicationCategory": "HealthApplication",
         "description": t["desc"], "image": f"{DOMAIN}/assets/img/appicon-512.png",
         "inLanguage": ["en", "nl", "de", "fr", "es", "it", "pt-PT", "ca", "ro", "el", "sv", "da", "nb", "is", "fi", "pl", "cs", "sk",
                        "sl", "hr", "sr", "mk", "bg", "hu", "et", "lv", "lt", "ru", "uk", "tr", "ga", "mt", "sq"],
         "publisher": {"@id": f"{DOMAIN}/#publisher"}},
    ]}


def build_home(lang):
    t = T[lang]
    s = t["screens"]
    sc = screens(s, "," if lang == "nl" else ".")
    path = "" if lang == "nl" else "en/"
    ids = dict(features="functies" if lang == "nl" else "features", devices="apparaten" if lang == "nl" else "devices",
               health="gezondheid" if lang == "nl" else "health")
    head = page_head(lang, t, path, t["title"], t["desc"], "home", jsonld_home(lang, t))
    stats = "".join(f'<div class="{"s-hot" if hot else ""}"><dt>{e(label)}</dt><dd data-count="{n}" data-suffix="{suf}">{n}{suf}</dd></div>'
                    for label, n, suf, hot in t["stats"])
    names = [x[lang] for x in SEEDS]
    random.Random(4).shuffle(names)
    ticks = "".join(f'<span class="tick">{e(n)}</span>' for n in names[:40])
    steps = "".join(f'''<article class="step" data-step="{k}" data-glow="{c}55">
      <span class="step-ico" style="--c:{c if c != "#ffffff" else "#ff6b00"}">{num}</span><h3>{e(h)}</h3><p>{e(p)}</p></article>''' for k, num, h, p, c in t["steps"])
    keymap = {"pass": "pass_", "import": "imp"}
    feat_screens = "".join(sc[keymap.get(k, k)].replace('class="scr', 'class="scr' + (" is-on" if i == 0 else ""), 1)
                           for i, k in enumerate(["workout", "rest", "progress", "history", "pass", "import"]))
    muscles = "".join(f'''<div class="mus" style="--c:{c}" data-reveal>{ICONS[key]}<b>{e(name)}</b><span>{sum(COUNT.get(m, 0) for m in ms)} {e(t["exercises_word"])}</span><em>{sum(COUNT.get(m, 0) for m in ms)}</em></div>'''
                      for key, name, ms, c in t["muscles"])
    health = "".join(f'<li style="--c:{c}"><i>✓</i><span><b>{e(a)}</b>{e(b)}</span></li>' for a, b, c in t["health"])
    privacy = "".join(f'<div class="card" data-reveal><div class="emo">{em}</div><h3>{e(a)}</h3><p>{e(b)}</p></div>' for em, a, b in t["privacy"])
    langs = "".join(f"<span>{e(n)}</span>" for n in LANG_NAMES)
    pr_link = "/privacy/" if lang == "nl" else "/en/privacy/"
    notes_js = json.dumps([{"icon": "/assets/img/appicon-180.png", "title": a, "body": b} for a, b in t["notes"]], ensure_ascii=False)
    apple = '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M16.4 12.6c0-2.5 2-3.7 2.1-3.8-1.2-1.7-3-1.9-3.6-2-1.5-.2-3 .9-3.8.9-.8 0-2-.9-3.3-.9-1.7 0-3.3 1-4.2 2.5-1.8 3.1-.5 7.7 1.3 10.2.9 1.2 1.9 2.6 3.2 2.6 1.3-.1 1.8-.8 3.3-.8 1.6 0 2 .8 3.3.8 1.4 0 2.3-1.3 3.1-2.5 1-1.4 1.4-2.8 1.4-2.9 0 0-2.8-1.1-2.8-4.1zM13.9 5.1c.7-.8 1.2-2 1-3.1-1 0-2.2.7-2.9 1.5-.6.7-1.2 1.9-1 3 1.1.1 2.2-.6 2.9-1.4z"/></svg>'
    def store_btn(cls):
        if APP_STORE_URL:
            return f'<a class="btn {cls}" href="{APP_STORE_URL}">{apple}<span>{e(t["soon"])}</span></a>'
        return f'<a class="btn {cls}" href="#download">{apple}<span>{e(t["soon_pre"])}</span></a>'
    body = f'''
<body>
<a class="skip" href="#main">{"Naar inhoud" if lang == "nl" else "Skip to content"}</a>
{nav_html(t, lang, "")}
<main id="main">
  <section class="hero">
    <div class="hero-bg" aria-hidden="true"><div class="hero-grid"></div>{PLATES}<div class="pulse"><i></i><i></i><i></i><i></i></div></div>
    <div class="hero-inner">
      <div class="hero-copy">
        <p class="eyebrow"><span class="dot"></span>{e(t["eyebrow"])}</p>
        <h1>{t["h1"]}</h1>
        <p class="lead">{e(t["lead"])}</p>
        <div class="cta">{store_btn("btn-white")}<a class="btn btn-ghost" href="#{ids["features"]}">{e(t["features_btn"])}</a></div>
        <dl class="stats">{stats}</dl>
      </div>
      <div class="hero-device">
        <div class="phone tilt" id="heroPhone"><div class="glass">{sc["today"]}</div><div class="island"></div></div>
        <div class="notes" id="notes" aria-hidden="true"></div>
      </div>
    </div>
    <div class="scroll-hint" aria-hidden="true"><span></span></div>
  </section>

  <div class="ticker" aria-label="{e(t["ticker"])}"><div class="ticker-label">{DUMBBELL.replace('<svg ', '<svg width="20" height="20" ')}{e(t["ticker"])}</div><div class="ticker-track"><div class="ticker-move">{ticks}{ticks}</div></div></div>

  <section class="features" id="{ids["features"]}">
    <div class="sec-head" data-reveal><p class="kicker">{e(t["f_kicker"])}</p><h2>{t["f_h2"]}</h2><p class="sub">{e(t["f_sub"])}</p></div>
    <div class="scrolly">
      <div class="scrolly-device"><div class="phone"><div class="glass" id="featureGlass">{feat_screens}</div><div class="island"></div></div></div>
      <div class="steps">{steps}</div>
    </div>
  </section>

  <section>
    <div class="sec-head" data-reveal><p class="kicker">{e(t["m_kicker"])}</p><h2>{t["m_h2"]}</h2><p class="sub">{e(t["m_sub"])}</p></div>
    <div class="muscles">{muscles}</div>
  </section>

  <section class="devices" id="{ids["devices"]}">
    <div class="sec-head center" data-reveal><p class="kicker">{e(t["d_kicker"])}</p><h2>{t["d_h2"]}</h2><p class="sub">{e(t["d_sub"])}</p></div>
    <div class="stage" data-reveal>
      <div class="ipad-frame"><div class="glass">{sc["pad"]}</div></div>
      <div class="phone"><div class="glass">{sc["workout"].replace('data-screen="workout"', '')}</div><div class="island"></div></div>
      <div class="watch-frame"><div class="watch-band top"></div><div class="watch-band bottom"></div><div class="watch-case"><div class="watch-glass">{sc["watch"]}</div></div><div class="watch-crown"></div></div>
    </div>
  </section>

  <section class="split" id="{ids["health"]}">
    <div data-reveal><p class="kicker">{e(t["h_kicker"])}</p><h2>{t["h_h2"]}</h2><p class="sub" style="color:var(--muted);font-size:1.08rem;margin-top:1.1rem">{e(t["h_sub"])}</p><ul>{health}</ul></div>
    <div class="health-visual" aria-hidden="true"><div class="ring r1"></div><div class="ring r2"></div><div class="ring r3"></div>{HEART}</div>
  </section>

  <section id="privacy">
    <div class="sec-head" data-reveal><p class="kicker">{e(t["p_kicker"])}</p><h2>{t["p_h2"]}</h2><p class="sub"><a href="{pr_link}" style="color:var(--orange-hot);font-weight:600">{e(t["privacy_link"])} →</a></p></div>
    <div class="cards">{privacy}</div>
  </section>

  <section>
    <div class="sec-head center" data-reveal><p class="kicker">{e(t["l_kicker"])}</p><h2>{t["l_h2"]}</h2></div>
    <div class="langs" data-reveal>{langs}</div>
  </section>

  <section class="final" id="download">
    <img src="/assets/img/appicon-512.png" alt="Liftr app icon" width="128" height="128" loading="lazy">
    <h2>{t["final_h2"]}</h2><p>{e(t["final_p"])}</p>
    <div class="cta center">{store_btn("btn-orange")}</div>
  </section>
</main>
{footer_html(t, lang)}
<script>window.LIFTR_NOTES = {notes_js};</script>
<script src="/assets/js/site.js" defer></script>
</body>
</html>
'''
    write(os.path.join(path, "index.html"), head + body)


PRIVACY = {
    "nl": dict(title="Privacyverklaring | Liftr", h1="Privacy", desc="Hoe Liftr met je gegevens omgaat: geen account, geen server, geen tracking. Je trainingen staan op je apparaat en in je eigen iCloud.",
               lead="Liftr is gemaakt om je training privé te houden. Laatst bijgewerkt: 3 november 2026.",
               sections=[
                   ("Welke gegevens", "Liftr bewaart wat je zelf invoert: trainingen, oefeningen, sets, herhalingen, gewichten, schema's, lichaamsmetingen, notities en de code van je sportschoolpas. Er is geen account en Liftr heeft geen eigen server."),
                   ("Opslag en iCloud", "Je gegevens staan op je apparaat. Als iCloud aan staat, synchroniseren ze via je eigen privé-iCloud-database tussen je iPhone, iPad en Apple Watch. De ontwikkelaar kan deze gegevens niet inzien. De code van je sportschoolpas wordt end-to-end versleuteld opgeslagen."),
                   ("Apple Gezondheid", "Alleen met jouw toestemming leest Liftr je gewicht, vetpercentage, vetvrije massa, lengte, hartslag en rusthartslag, en bewaart het trainingen, actieve energie, afstand, gewicht en vetpercentage in Apple Gezondheid. Gezondheidsgegevens worden niet gedeeld met derden, niet voor advertenties gebruikt en verlaten je apparaten niet via Liftr. Je wijzigt de toegang altijd in de Gezondheid-app."),
                   ("Camera en foto's", "De camera en je fotobibliotheek worden alleen gebruikt om de QR- of barcode van je sportschoolpas te lezen. Afbeeldingen worden niet bewaard of verstuurd."),
                   ("Meldingen", "Herinneringen en de rusttimer zijn lokale meldingen die op je apparaat worden ingepland. Er is geen pushserver."),
                   ("Geen tracking", "Liftr bevat geen analytics, geen advertenties, geen code van derden en geen enkele vorm van tracking. Deze website gebruikt geen cookies en geen trackers."),
                   ("Je gegevens verwijderen", "Verwijder trainingen in de app. Om alles te wissen verwijder je Liftr van je apparaten en haal je de gegevens weg via Instellingen → je naam → iCloud → Beheer opslag → Liftr."),
                   ("Contact", 'Vragen over privacy? Neem contact op via <a href="https://lrvdlinden.app">lrvdlinden.app</a>.'),
               ]),
    "en": dict(title="Privacy policy | Liftr", h1="Privacy", desc="How Liftr handles your data: no account, no server, no tracking. Your workouts stay on your device and in your own iCloud.",
               lead="Liftr is built to keep your training private. Last updated: 3 November 2026.",
               sections=[
                   ("What data", "Liftr stores what you enter yourself: workouts, exercises, sets, reps, weights, routines, body measurements, notes and the code of your gym pass. There is no account and Liftr has no server of its own."),
                   ("Storage and iCloud", "Your data is stored on your device. With iCloud turned on, it syncs between your iPhone, iPad and Apple Watch through your own private iCloud database. The developer cannot see this data. The code of your gym pass is stored end-to-end encrypted."),
                   ("Apple Health", "Only with your permission, Liftr reads your weight, body fat, lean body mass, height, heart rate and resting heart rate, and saves workouts, active energy, distance, weight and body fat to Apple Health. Health data is never shared with third parties, never used for advertising and never leaves your devices through Liftr. You can change access at any time in the Health app."),
                   ("Camera and photos", "The camera and your photo library are only used to read the QR code or barcode of your gym pass. Images are not saved or sent anywhere."),
                   ("Notifications", "Reminders and the rest timer are local notifications planned on your device. There is no push server."),
                   ("No tracking", "Liftr contains no analytics, no advertising, no third-party code and no tracking of any kind. This website uses no cookies and no trackers."),
                   ("Deleting your data", "Delete workouts in the app. To remove everything, delete Liftr from your devices and remove its data in Settings → your name → iCloud → Manage Storage → Liftr."),
                   ("Contact", 'Questions about privacy? Get in touch via <a href="https://lrvdlinden.app">lrvdlinden.app</a>.'),
               ]),
}


def build_privacy(lang):
    t, p = T[lang], PRIVACY[lang]
    path = "privacy/" if lang == "nl" else "en/privacy/"
    ld = {"@context": "https://schema.org", "@type": "WebPage", "name": p["title"], "url": f"{DOMAIN}/{path}", "inLanguage": lang,
          "isPartOf": {"@id": f"{DOMAIN}/#website"}}
    head = page_head(lang, t, path, p["title"], p["desc"], "home", ld)
    secs = "".join(f"<section><h2>{e(h)}</h2><p>{body}</p></section>" for h, body in p["sections"])
    home = "/" if lang == "nl" else "/en/"
    other = "/en/privacy/" if lang == "nl" else "/privacy/"
    body = f'''
<body>
{nav_html(t, lang, home, other)}
<main class="legal" id="main"><h1>{e(p["h1"])}</h1><p class="lead">{e(p["lead"])}</p>{secs}</main>
{footer_html(t, lang, other)}
<script src="/assets/js/site.js" defer></script>
</body>
</html>
'''
    write(os.path.join(path, "index.html"), head + body)


def write(rel, text):
    full = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as f:
        f.write(text)


def build_meta():
    pages = [("", "en/"), ("privacy/", "en/privacy/")]
    urls = ""
    for nl, en in pages:
        for loc in (nl, en):
            urls += f'''  <url>
    <loc>{DOMAIN}/{loc}</loc>
    <lastmod>{TODAY}</lastmod>
    <xhtml:link rel="alternate" hreflang="nl" href="{DOMAIN}/{nl}"/>
    <xhtml:link rel="alternate" hreflang="en" href="{DOMAIN}/{en}"/>
    <xhtml:link rel="alternate" hreflang="x-default" href="{DOMAIN}/{nl}"/>
  </url>
'''
    write("sitemap.xml", f'''<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
{urls}</urlset>
''')
    write("robots.txt", f"User-agent: *\nAllow: /\n\nSitemap: {DOMAIN}/sitemap.xml\n")
    write("site.webmanifest", json.dumps({
        "name": "Liftr", "short_name": "Liftr", "start_url": "/", "display": "standalone",
        "background_color": "#0a0a0c", "theme_color": "#ff6b00",
        "icons": [{"src": "/assets/img/appicon-192.png", "sizes": "192x192", "type": "image/png"},
                  {"src": "/assets/img/appicon-512.png", "sizes": "512x512", "type": "image/png"}]}, indent=2))
    write("404.html", '''<!doctype html>
<html lang="nl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Niet gevonden | Liftr</title><meta name="robots" content="noindex"><link rel="icon" href="/favicon.ico">
<link rel="stylesheet" href="/assets/css/site.css?v=13"></head>
<body><main class="legal" style="text-align:center"><h1>404</h1><p class="lead">Deze pagina bestaat niet. · This page doesn't exist.</p>
<p><a class="btn btn-orange" href="/">liftr.nu</a></p></main></body></html>
''')
    write(".htaccess", '''ErrorDocument 404 /404.html
RewriteEngine On
RewriteCond %{HTTPS} off [OR]
RewriteCond %{HTTP_HOST} ^www\\. [NC]
RewriteRule ^ https://liftr.nu%{REQUEST_URI} [L,R=301]
<IfModule mod_expires.c>
  ExpiresActive On
  ExpiresByType text/css "access plus 1 week"
  ExpiresByType application/javascript "access plus 1 week"
  ExpiresByType image/png "access plus 1 month"
  ExpiresByType font/woff2 "access plus 1 year"
</IfModule>
AddType application/manifest+json .webmanifest
AddType font/woff2 .woff2
''')


if __name__ == "__main__":
    for lang in ("nl", "en"):
        build_home(lang)
        build_privacy(lang)
    build_meta()
    print("website/ built:", len(SEEDS), "exercises")
