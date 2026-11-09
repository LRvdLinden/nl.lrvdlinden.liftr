#!/usr/bin/env python3
"""Social media banners for Liftr (NL + EN): Instagram/Facebook post 1:1 and 4:5, story/reels, Facebook and
LinkedIn cover. Reuses the App Store screen recreations (tools/appstore/build_assets.py).

  python3 tools/social/build_social.py && node tools/social/render.js
Output: social/liftr-<date>/<nl|en>/<channel>-<size>.png
"""
import datetime, importlib.util, json, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
spec = importlib.util.spec_from_file_location("ba", os.path.join(ROOT, "tools/appstore/build_assets.py"))
ba = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ba)
e = ba.e

DATE = datetime.date.today().isoformat()
BUILD = os.path.join(ROOT, "tools/social/_build")
OUT = os.path.join(ROOT, "social", f"liftr-{DATE}")

CSS = """
.phone2.ph{position:absolute;left:auto;width:var(--w);aspect-ratio:440/956;border-radius:calc(var(--w)*.124);padding:calc(var(--w)*.022);
  box-shadow:0 0 0 calc(var(--w)*.0066) #4a4a50,0 0 0 calc(var(--w)*.013) #141416,0 calc(var(--w)*.08) calc(var(--w)*.16) rgba(0,0,0,.55)}
.phone2.ph .glass{border-radius:calc(var(--w)*.103)}
.phone2.ph .island{top:calc(var(--w)*.045);height:calc(var(--w)*.043);border-radius:99px}
.brand{position:absolute;display:flex;align-items:center;gap:.5em;color:#fff;font-family:var(--display);font-weight:900;letter-spacing:.04em;text-transform:uppercase}
.brand img{width:1.9em;height:1.9em;border-radius:.45em;box-shadow:0 .2em .6em rgba(0,0,0,.35)}
.cta{position:absolute;display:inline-flex;align-items:center;gap:.5em;padding:.55em 1.1em;border-radius:99px;background:#fff;color:#111;
  font-family:var(--font);font-weight:800;box-shadow:0 .3em 1em rgba(0,0,0,.25)}
.cta i{font-style:normal;color:#ff6b00}
.plat{position:absolute;font-family:var(--font);font-weight:700;color:rgba(255,255,255,.85);letter-spacing:.02em}
"""

T = {
    "nl": dict(
        platforms="iPhone · iPad · Apple Watch",
        square=("Elke set", "telt.", "Log sets, herhalingen en gewicht – en zie hoe je sterker wordt."),
        portrait=("Zie je", "groei.", "Grafieken, records en badges bij elke training."),
        story=("Weg met", "notities.", "Je sportschoollogboek op iPhone, iPad en Apple Watch."),
        cover=("Je sportschool", "logboek.", "Sets, gewicht en voortgang – op iPhone, iPad en Apple Watch."),
    ),
    "en": dict(
        platforms="iPhone · iPad · Apple Watch",
        square=("Every set", "counts.", "Log sets, reps and weight – and see yourself get stronger."),
        portrait=("Watch yourself", "grow.", "Charts, records and badges with every workout."),
        story=("Ditch the", "notes.", "Your gym logbook on iPhone, iPad and Apple Watch."),
        cover=("Your gym", "logbook.", "Sets, weight and progress – on iPhone, iPad and Apple Watch."),
    ),
}


def phone(screen, w, left, top, rotate=0):
    return (f'<div class="phone2 ph" style="--w:{w}px;left:{left}px;top:{top}px;transform:rotate({rotate}deg)">'
            f'<div class="glass">{screen.replace(" is-on", "")}</div><div class="island"></div></div>')


def brand(x, y, size):
    return f'<div class="brand" style="left:{x}px;top:{y}px;font-size:{size}px"><img src="{ba.ICON}" alt="">Liftr</div>'


def cta(x, y, size, right=False):
    pos = f"right:{x}px" if right else f"left:{x}px"
    return f'<div class="cta" style="{pos};top:{y}px;font-size:{size}px"><i>→</i>liftr.nu</div>'


def text(x, y, w, h1, l1, l2, sub, subsize, gap=24):
    return (f'<div class="txt" style="left:{x}px;right:auto;width:{w}px;top:{y}px"><h1 style="font-size:{h1}px">{e(l1)}'
            f'<span class="outline" style="-webkit-text-stroke:{max(2, h1 // 32)}px #fff">{e(l2)}</span></h1>'
            f'<p style="font-size:{subsize}px;margin-top:{gap}px">{e(sub)}</p></div>')


def build(lang):
    t = T[lang]
    sc = ba.screens_for(lang)
    pages = {}
    # Instagram/Facebook post 1:1 — logging
    l1, l2, sub = t["square"]
    pages["instagram-post-1080x1080"] = (1080, 1080, ba.bg() + brand(72, 64, 34) + text(72, 170, 470, 112, l1, l2, sub, 30)
        + cta(72, 930, 30) + phone(sc["workout"], 400, 610, 150, 4))
    # Instagram/Facebook post 4:5 — progress
    l1, l2, sub = t["portrait"]
    pages["instagram-post-1080x1350"] = (1080, 1350, ba.bg() + brand(72, 64, 34) + text(72, 160, 936, 124, l1, l2, sub, 32)
        + phone(sc["progress"], 470, 305, 560) + cta(72, 1236, 30, right=True))
    # Story / Reels 1080x1920 — safe zone 250 px top and bottom
    l1, l2, sub = t["story"]
    pages["story-1080x1920"] = (1080, 1920, ba.bg() + brand(80, 270, 40) + text(80, 380, 920, 150, l1, l2, sub, 38, 30)
        + cta(80, 830, 40) + phone(sc["today"], 560, 260, 960))
    # Facebook cover 1640x624 — important content in the middle
    l1, l2, sub = t["cover"]
    pages["facebook-cover-1640x624"] = (1640, 624, ba.bg() + brand(260, 84, 34) + text(260, 175, 620, 78, l1, l2, sub, 23, 16)
        + cta(260, 520, 26) + phone(sc["today"], 230, 980, 70, -4) + phone(sc["progress"], 230, 1180, 40, 4))
    # LinkedIn cover 1584x396 — profile photo sits bottom left, keep text right of it
    pages["linkedin-cover-1584x396"] = (1584, 396, ba.bg() + brand(560, 48, 26) + text(560, 104, 560, 54, l1, l2, sub, 18, 10) + cta(560, 300, 18)
        + phone(sc["workout"], 170, 1170, 40, -4) + phone(sc["progress"], 170, 1330, 20, 4))
    jobs = []
    for name, (w, h, body) in pages.items():
        path = os.path.join(BUILD, f"{lang}-{name}.html")
        with open(path, "w", encoding="utf-8") as f:
            f.write(ba.page(body, CSS))
        jobs.append({"html": path, "out": os.path.join(OUT, lang, name + ".png"), "width": w, "height": h})
    return jobs


def main():
    os.makedirs(BUILD, exist_ok=True)
    jobs = build("nl") + build("en")
    with open(os.path.join(BUILD, "jobs.json"), "w") as f:
        json.dump(jobs, f, indent=1)
    print(len(jobs), "banners →", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
