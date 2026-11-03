#!/usr/bin/env python3
"""App Store images for Liftr (nl-NL + en-US): iPhone 6.3"/6.1", iPad 13", Apple Watch and the
header / search-result marketing assets.

    python3 tools/appstore/build_assets.py      # writes HTML + a render manifest to tools/appstore/_build
    node tools/appstore/render.js               # renders the PNGs into AppStore/
    python3 tools/appstore/finish.py            # flattens to RGB and validates sizes

The app screens are HTML recreations of the SwiftUI screens (same texts, colours and layout),
shared with the website (tools/build_website.py).
"""
import html, importlib.util, json, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BUILD = os.path.join(ROOT, "tools/appstore/_build")
OUT = os.path.join(ROOT, "AppStore")
CSS = "file://" + os.path.join(ROOT, "website/assets/css/site.css")
ICON = "file://" + os.path.join(ROOT, "website/assets/img/appicon-512.png")

spec = importlib.util.spec_from_file_location("bw", os.path.join(ROOT, "tools/build_website.py"))
bw = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bw)
e = html.escape

LOCALES = {"nl-NL": "nl", "en-US": "en"}

HEAD = {
    "nl": dict(
        iphone=[("today", "Elke herhaling", "telt.", "Je sportschool-logboek voor iPhone, iPad en Apple Watch"),
                ("workout", "Log in", "seconden.", "Wat je vorige keer deed staat al klaar"),
                ("rest", "Rust op je", "vergrendelscherm.", "Rusttimer met Live Activity en Dynamic Island"),
                ("progress", "Zie je", "groei.", "Zwaarste set, geschat maximum en volume"),
                ("history", "Elke training", "terug.", "Tik een datum en zie wat je deed"),
                ("pass", "Je pas bij", "de deur.", "QR- of barcode voor de toegang, ook op je Watch"),
                ("import", "Weg met", "notities.", "Plak je oude notities, Liftr maakt er trainingen van")],
        ipad=[(("today", "workout"), "Groot op", "je iPad.", "Vandaag en je training naast elkaar"),
              (("progress", "history"), "Al je", "voortgang.", "Grafieken en je trainingskalender"),
              (("pass", "import"), "Alles bij", "de hand.", "Je sportschoolpas en importeren uit notities")],
        mk_title="Elke herhaling", mk_outline="telt.", mk_sub="Sportschool-logboek voor iPhone, iPad en Apple Watch",
        mk_chips=["Sets & gewicht", "Rusttimer", "Voortgang", "Gezondheid", "33 talen"],
        watch=dict(start="START", routines="Schema's", r1="Benen", r2="Push", r3="Rug & armen", ex="oefeningen",
                   week="2 deze week", streak="6 wk", rest="RUST", skip="Overslaan", time="Tijd", sets="sets",
                   pause="Pauze", finish="Klaar", discard="Weggooien", pass_title="Mijn sportschool"),
    ),
    "en": dict(
        iphone=[("today", "Every rep", "counts.", "Your gym logbook for iPhone, iPad and Apple Watch"),
                ("workout", "Log in", "seconds.", "What you did last time is already filled in"),
                ("rest", "Rest on your", "Lock Screen.", "Rest timer with Live Activity and Dynamic Island"),
                ("progress", "Watch yourself", "grow.", "Heaviest set, estimated max and volume"),
                ("history", "Every workout,", "by date.", "Tap a day and see what you did"),
                ("pass", "Your pass at", "the door.", "Access QR code or barcode, on your Watch too"),
                ("import", "No more", "notes.", "Paste your old notes, Liftr turns them into workouts")],
        ipad=[(("today", "workout"), "Big on", "your iPad.", "Today and your workout side by side"),
              (("progress", "history"), "All your", "progress.", "Charts and your training calendar"),
              (("pass", "import"), "Everything", "at hand.", "Your gym pass and importing from notes")],
        mk_title="Every rep", mk_outline="counts.", mk_sub="Gym logbook for iPhone, iPad and Apple Watch",
        mk_chips=["Sets & weight", "Rest timer", "Progress", "Apple Health", "33 languages"],
        watch=dict(start="START", routines="Routines", r1="Legs", r2="Push", r3="Back & arms", ex="exercises",
                   week="2 this week", streak="6 wk", rest="REST", skip="Skip", time="Time", sets="sets",
                   pause="Pause", finish="Finish", discard="Discard", pass_title="My gym"),
    ),
}

BASE_CSS = """
html,body{margin:0;width:100vw;height:100vh;overflow:hidden;background:#0a0a0c}
.bg{position:absolute;inset:0;background:radial-gradient(120% 70% at 15% 0%,#ff8a1f 0%,#f05a00 38%,#8a2a00 72%,#0a0a0c 100%);overflow:hidden}
.bg .hero-grid{position:absolute;inset:0;background-image:linear-gradient(rgba(255,255,255,.06) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.06) 1px,transparent 1px);background-size:5vw 5vw;mask-image:radial-gradient(80% 50% at 30% 20%,#000,transparent)}
.bg .hero-plates{position:absolute;right:-18vw;top:-14vw;width:70vw;opacity:.14;animation:none}
.txt{position:absolute;left:7vw;right:7vw;color:#fff}
.txt .k{font-family:var(--font);font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:rgba(255,255,255,.85)}
.txt h1{font-family:var(--display);font-weight:900;text-transform:uppercase;line-height:.92;margin:0;letter-spacing:-.01em}
.txt h1 .outline{display:block;color:transparent;-webkit-text-stroke:.45vw #fff}
.txt p{font-family:var(--font);font-weight:600;margin:0;color:rgba(255,255,255,.92)}
.phone2{position:absolute;left:50%;aspect-ratio:440/956;background:#1b1b1d;border-radius:9.4vw;padding:1.7vw;box-shadow:0 0 0 .5vw #4a4a50,0 0 0 1vw #141416,0 6vw 12vw rgba(0,0,0,.55)}
.phone2 .glass{position:relative;width:100%;height:100%;border-radius:7.8vw;overflow:hidden;background:#0e0e10;container-type:inline-size}
.phone2 .island{position:absolute;top:3.4vw;left:50%;width:27%;height:3.3vw;transform:translateX(-50%);border-radius:3vw;background:#000;z-index:5}
.ipad2{position:absolute;left:50%;transform:translateX(-50%);background:#1b1b1d;border-radius:4.2vw;padding:2.2vw;box-shadow:0 0 0 .35vw #4a4a50,0 5vw 10vw rgba(0,0,0,.55)}
.ipad2 .glass{width:100%;height:100%;border-radius:2.4vw;overflow:hidden;background:#0e0e10;display:grid;grid-template-columns:19% 1fr 1fr;gap:1.2vw;padding:1.6vw;box-sizing:border-box}
.ipad2 .side{display:flex;flex-direction:column;gap:.6vw;font-family:var(--font);color:#bbb;font-weight:600;font-size:1.25vw;padding-top:1vw}
.ipad2 .side b{font-family:var(--display);font-weight:900;font-size:2.4vw;color:#fff;margin-bottom:1vw;letter-spacing:.02em}
.ipad2 .side div{padding:.8vw 1vw;border-radius:1vw}
.ipad2 .side div.on{background:rgba(255,107,0,.18);color:#ff8a1f}
.panel{position:relative;container-type:inline-size;border-radius:1.6vw;overflow:hidden;background:#0e0e10}
.panel .scr{padding-top:7cqw}
.panel .scr.lock{padding-top:14cqw}
.panel .scr .tabbar{display:none}
"""


def page(body, extra_css=""):
    return f"""<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="{CSS}">
<style>{BASE_CSS}{extra_css}</style></head><body>{body}</body></html>"""


def bg():
    return f'<div class="bg"><div class="hero-grid"></div>{bw.PLATES}</div>'


def screens_for(lang):
    sc = bw.screens(bw.T[lang]["screens"], "," if lang == "nl" else ".")
    return {"today": sc["today"], "workout": sc["workout"], "rest": sc["rest"], "progress": sc["progress"],
            "history": sc["history"], "pass": sc["pass_"], "import": sc["imp"], "watch_set": sc["watch"]}


def iphone_html(screen, line1, line2, sub):
    body = f"""{bg()}
<div class="txt" style="top:9vw"><h1 style="font-size:12.6vw">{e(line1)}<span class="outline">{e(line2)}</span></h1>
<p style="font-size:4.1vw;margin-top:3.4vw;max-width:80vw">{e(sub)}</p></div>
<div class="phone2" style="width:76vw;top:62vw;transform:translateX(-50%)"><div class="glass">{screen.replace(' is-on', '')}</div><div class="island"></div></div>"""
    return page(body)


def ipad_html(lang, pair, line1, line2, sub, scr):
    tabs = bw.T[lang]["screens"]["tabs"]
    active = {"today": 0, "progress": 2, "pass": 0}[pair[0]]
    side = "".join(f'<div class="{"on" if i == active else ""}">{e(t)}</div>' for i, t in enumerate(tabs))
    body = f"""{bg()}
<div class="txt" style="top:6vw;text-align:center"><h1 style="font-size:8.4vw">{e(line1)} <span class="outline" style="display:inline">{e(line2)}</span></h1>
<p style="font-size:2.6vw;margin-top:2vw">{e(sub)}</p></div>
<div class="ipad2" style="width:88vw;height:109vw;top:30vw"><div class="glass"><div class="side"><b>LIFTR</b>{side}</div>
<div class="panel">{scr[pair[0]].replace(' is-on', '')}</div><div class="panel">{scr[pair[1]].replace(' is-on', '')}</div></div></div>"""
    return page(body)


def watch_screens(lang, scr):
    w = HEAD[lang]["watch"]
    s = bw.T[lang]["screens"]
    start = f"""<div class="scr watch" style="text-align:left;gap:3.5cqw">
<div class="go" style="text-align:center;font-family:var(--display);font-weight:900;font-size:9cqw">▶ {e(w["start"])}</div>
<div style="color:#999;font-size:5.5cqw;font-weight:700;margin-top:2cqw">{e(w["routines"])}</div>
{"".join(f'<div style="background:#1c1c1e;border-radius:6cqw;padding:3.5cqw 5cqw"><b style="font-size:7cqw">{e(r)}</b><div style="color:#999;font-size:5cqw">{n} {e(w["ex"])}</div></div>' for r, n in ((w["r1"], 6), (w["r2"], 5)))}
<div style="font-size:5.5cqw;color:#ccc">🔥 {e(w["week"])} · {e(w["streak"])}</div></div>"""
    metrics = f"""<div class="scr watch" style="text-align:left;gap:2cqw;padding-top:12cqw">
<div style="font-family:ui-rounded,system-ui;font-weight:800;font-size:22cqw;color:#ff8a1f;font-variant-numeric:tabular-nums;line-height:1">24:18</div>
<div style="color:#ff375f;font-weight:700;font-size:10cqw">♥ 128 BPM</div>
<div style="font-weight:700;font-size:10cqw">🔥 214 kcal</div>
<div style="font-weight:700;font-size:10cqw">▦ 9 {e(w["sets"])}</div></div>"""
    rest = f"""<div class="scr watch" style="justify-content:center;gap:4cqw">
<div style="color:#999;font-weight:800;font-size:7cqw">{e(w["rest"])}</div>
<div style="font-family:ui-rounded,system-ui;font-weight:800;font-size:30cqw;color:#ff8a1f;line-height:1;font-variant-numeric:tabular-nums">0:47</div>
<div class="go">{e(w["skip"])}</div></div>"""
    passs = f"""<div class="scr watch" style="background:#fff;justify-content:center;padding:6cqw">{bw.qr_svg(5)}</div>"""
    return [("start", start), ("set", scr["watch_set"]), ("rest", rest), ("metrics", metrics), ("pass", passs)]


def watch_html(screen):
    return page(f'<div style="position:absolute;inset:0;container-type:inline-size">{screen}</div>',
                ".scr.watch{padding:8cqw 7cqw!important} .scr.watch .qr{width:100%}")


def marketing_html(lang, scr, wide):
    h = HEAD[lang]
    chips = "".join(f'<span>{e(c)}</span>' for c in h["mk_chips"])
    # wide = 3840x1646 banner; otherwise 16:9 / 3:2 canvas. Sizes in vh so every canvas scales.
    phone_h = 86 if wide else 74
    body = f"""{bg()}
<div class="txt" style="top:50%;transform:translateY(-50%);left:6vw;right:auto;width:{46 if wide else 50}vw">
<div style="display:flex;align-items:center;gap:1.6vh;margin-bottom:3vh"><img src="{ICON}" style="width:9vh;height:9vh;border-radius:2.1vh"><b style="font-family:var(--display);font-weight:900;font-size:6vh">LIFTR</b></div>
<h1 style="font-size:{15 if wide else 12}vh">{e(h["mk_title"])}<span class="outline" style="-webkit-text-stroke-width:.5vh">{e(h["mk_outline"])}</span></h1>
<p style="font-size:3.4vh;margin-top:3vh">{e(h["mk_sub"])}</p>
<div style="display:flex;flex-wrap:wrap;gap:1.2vh;margin-top:3vh">{"".join(f'<span style="font-family:var(--font);font-weight:700;font-size:2.4vh;padding:1vh 2vh;border-radius:99px;background:rgba(0,0,0,.28);box-shadow:inset 0 0 0 1px rgba(255,255,255,.2)">{e(c)}</span>' for c in h["mk_chips"])}</div></div>
<div style="position:absolute;right:{6 if wide else 5}vw;top:50%;transform:translateY(-50%);display:flex;align-items:center;gap:3vh;perspective:2000px">
  <div class="phone2" style="position:relative;left:auto;height:{phone_h}vh;width:auto;padding:1.4vh;border-radius:7.6vh;box-shadow:0 0 0 .4vh #4a4a50,0 0 0 .8vh #141416,0 5vh 10vh rgba(0,0,0,.55);transform:rotateY(-10deg) rotateZ(2deg)"><div class="glass" style="border-radius:6.4vh">{scr["workout"]}</div><div class="island" style="top:2.6vh;height:2.6vh;border-radius:2vh"></div></div>
  <div class="phone2" style="position:relative;left:auto;height:{phone_h * .88}vh;width:auto;padding:1.3vh;border-radius:6.8vh;box-shadow:0 0 0 .4vh #4a4a50,0 0 0 .8vh #141416,0 5vh 10vh rgba(0,0,0,.55);transform:rotateY(-10deg) rotateZ(2deg)"><div class="glass" style="border-radius:5.6vh">{scr["progress"]}</div><div class="island" style="top:2.3vh;height:2.3vh;border-radius:2vh"></div></div>
</div>"""
    return page(body, ".phone2 .glass{width:auto;height:100%;aspect-ratio:420/936}")


def main():
    os.makedirs(BUILD, exist_ok=True)
    jobs = []

    def job(name, htmltext, w, h, out, dsf=1):
        path = os.path.join(BUILD, name + ".html")
        open(path, "w", encoding="utf-8").write(htmltext)
        jobs.append({"html": path, "width": w, "height": h, "dsf": dsf, "out": os.path.join(OUT, out)})

    for locale, lang in LOCALES.items():
        scr = screens_for(lang)
        h = HEAD[lang]
        for i, (key, l1, l2, sub) in enumerate(h["iphone"], 1):
            doc = iphone_html(scr[key], l1, l2, sub)
            job(f"{locale}-iphone-{i}", doc, 1206, 2622, f"screenshots/{locale}/iphone-6.3/{i:02d}_{key}_1206x2622.png")
            job(f"{locale}-iphone61-{i}", doc, 1179, 2556, f"screenshots/{locale}/iphone-6.1/{i:02d}_{key}_1179x2556.png")
        for i, (pair, l1, l2, sub) in enumerate(h["ipad"], 1):
            doc = ipad_html(lang, pair, l1, l2, sub, scr)
            slug = "-".join(pair)
            job(f"{locale}-ipad-{i}", doc, 1032, 1376, f"screenshots/{locale}/ipad-13/{i:02d}_{slug}_2064x2752.png", 2)
            job(f"{locale}-ipad2-{i}", doc, 1024, 1366, f"screenshots/{locale}/ipad-13/{i:02d}_{slug}_2048x2732.png", 2)
        for i, (key, screen) in enumerate(watch_screens(lang, scr), 1):
            doc = watch_html(screen)
            for w, hh in ((422, 514), (416, 496), (410, 502), (396, 484), (368, 448), (312, 390)):
                job(f"{locale}-watch-{i}-{w}", doc, w, hh, f"screenshots/{locale}/watch/{w}x{hh}/{i:02d}_{key}_{w}x{hh}.png")
        job(f"{locale}-header-a", marketing_html(lang, scr, False), 2622, 1475, f"marketing/{locale}/header_5244x2950.png", 2)
        job(f"{locale}-header-b", marketing_html(lang, scr, True), 1920, 823, f"marketing/{locale}/header_3840x1646.png", 2)
        job(f"{locale}-search-a", marketing_html(lang, scr, False), 2622, 1475, f"marketing/{locale}/search_5244x2950.png", 2)
        job(f"{locale}-search-b", marketing_html(lang, scr, False), 1920, 1280, f"marketing/{locale}/search_3840x2560.png", 2)
        job(f"{locale}-search-c", marketing_html(lang, scr, False), 1920, 1280, f"marketing/{locale}/search_1920x1280.png", 1)

    json.dump(jobs, open(os.path.join(BUILD, "jobs.json"), "w"), indent=1)
    print(len(jobs), "images to render")


if __name__ == "__main__":
    main()
