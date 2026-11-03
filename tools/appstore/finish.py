#!/usr/bin/env python3
"""Flattens every App Store PNG to RGB (no alpha) and checks sizes, counts and metadata limits."""
import os, sys
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
AS = os.path.join(ROOT, "AppStore")
SIZES = {
    "iphone-6.3": {(1206, 2622)}, "iphone-6.1": {(1179, 2556)}, "ipad-13": {(2064, 2752), (2048, 2732)},
    "watch": {(422, 514), (416, 496), (410, 502), (396, 484), (368, 448), (312, 390)},
    "marketing": {(5244, 2950), (3840, 1646), (3840, 2560), (1920, 1280)},
}
LIMITS = {"name.txt": 30, "subtitle.txt": 30, "promotional_text.txt": 170, "description.txt": 4000, "release_notes.txt": 4000}
problems, count = [], 0

for base, _, files in os.walk(AS):
    for f in files:
        if not f.endswith(".png"):
            continue
        path = os.path.join(base, f)
        im = Image.open(path)
        if im.mode != "RGB":
            im.convert("RGB").save(path)
            im = Image.open(path)
        kind = next((k for k in SIZES if f"/{k}" in path), None)
        if kind and im.size not in SIZES[kind]:
            problems.append(f"{os.path.relpath(path, AS)}: {im.size}")
        count += 1

for locale in ("nl-NL", "en-US"):
    for device in ("iphone-6.3", "iphone-6.1", "ipad-13"):
        d = os.path.join(AS, "screenshots", locale, device)
        sizes = {}
        for f in os.listdir(d):
            sizes.setdefault(f.rsplit("_", 1)[-1], []).append(f)
        for s, fs in sizes.items():
            if len(fs) > 10:
                problems.append(f"{locale}/{device}: {len(fs)} screenshots of {s}")
    m = os.path.join(AS, "metadata", locale)
    for f, limit in LIMITS.items():
        n = len(open(os.path.join(m, f), encoding="utf-8").read().strip())
        if n > limit:
            problems.append(f"{locale}/{f}: {n} > {limit}")
    kw = open(os.path.join(m, "keywords.txt"), encoding="utf-8").read().strip()
    if len(kw.encode()) > 100 or ", " in kw:
        problems.append(f"{locale}/keywords.txt: {len(kw.encode())} bytes")
    title = (open(os.path.join(m, "name.txt")).read() + " " + open(os.path.join(m, "subtitle.txt")).read()).lower()
    for word in kw.split(","):
        if word in title.replace(",", " ").split():
            problems.append(f"{locale}/keywords.txt: '{word}' already in name/subtitle")

print(f"{count} PNG files checked")
for p in problems:
    print("!!", p)
sys.exit(1 if problems else 0)
