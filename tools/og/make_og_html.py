import sys, importlib.util
spec = importlib.util.spec_from_file_location("bw", "" + __import__("os").path.join(__import__("os").path.dirname(__import__("os").path.dirname(__import__("os").path.abspath(__file__))), "build_website.py") + "")
bw = importlib.util.module_from_spec(spec); spec.loader.exec_module(bw)
for lang in ("nl", "en"):
    t = bw.T[lang]; s = t["screens"]
    sc = bw.screens(s, "," if lang == "nl" else ".")
    h1 = t["h1"]
    sub = {"nl": "Sportschool-logboek voor iPhone, iPad & Apple Watch", "en": "Gym logbook for iPhone, iPad & Apple Watch"}[lang]
    chips = {"nl": ["Sets & gewicht", "Rusttimer", "Voortgang", "Gezondheid"], "en": ["Sets & weight", "Rest timer", "Progress", "Apple Health"]}[lang]
    html = f'''<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="http://localhost:8765/assets/css/site.css">
<style>
body{{margin:0;width:1200px;height:630px;overflow:hidden;}}
.og{{position:relative;width:1200px;height:630px;overflow:hidden;isolation:isolate;display:flex;align-items:center;padding:0 0 0 70px}}
.og .hero-bg::after{{display:none}}
.og h1{{font-size:88px;line-height:.92;max-width:680px}} .og h1 .outline{{display:block;-webkit-text-stroke:3px #fff}}
.og .brand{{display:flex;align-items:center;gap:14px;margin-bottom:26px}}
.og .brand img{{width:64px;height:64px;border-radius:15px}}
.og .brand b{{font-family:var(--display);font-weight:900;font-size:40px}}
.og p{{max-width:700px;font-size:22px;font-weight:600;margin:26px 0 22px;color:rgba(255,255,255,.95)}}
.og .chips{{display:flex;gap:10px;flex-wrap:wrap;max-width:620px}}
.og .chips span{{background:rgba(0,0,0,.28);border-radius:99px;padding:8px 16px;font-weight:700;font-size:18px;box-shadow:inset 0 0 0 1px rgba(255,255,255,.18)}}
.og .dev{{position:absolute;right:70px;top:46px;perspective:1400px}}
.og .phone{{width:250px;transform:rotateY(-14deg) rotateX(6deg) rotateZ(3deg)}}
.og .url{{position:absolute;left:70px;bottom:34px;font-weight:700;font-size:20px;color:rgba(255,255,255,.85)}}
</style></head><body><div class="og hero">
<div class="hero-bg"><div class="hero-grid"></div>{bw.PLATES}</div>
<div><div class="brand"><img src="http://localhost:8765/assets/img/appicon-512.png"><b>LIFTR</b></div>
<h1>{h1}</h1><p>{sub}</p><div class="chips">{"".join(f"<span>{c}</span>" for c in chips)}</div></div>
<div class="dev"><div class="phone"><div class="glass">{sc["today"]}</div><div class="island"></div></div></div>
<div class="url">liftr.nu</div></div></body></html>'''
    open(__import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)), f"og-{lang}.html"), "w").write(html)
