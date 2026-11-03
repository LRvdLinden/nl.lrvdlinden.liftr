#!/usr/bin/env python3
"""Checks tools/translations/<lang>.json against source.json and builds everything the app reads:

  Shared/Localization/liftr-<lang>.json   tables loaded by L10n (Shared/L10n.swift)
  Shared/InfoPlist.xcstrings              permission prompts + app name in every language
  MoreAppsCard.swift / AppInfoFooter.swift  MoreAppsText.all and AppInfoText.all for every language
  tools/gen_xcodeproj.py                  knownRegions

Run after adding texts:  python3 tools/extract_strings.py  → translate new keys →  python3 tools/build_translations.py
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)

LANGS = ["bg", "ca", "cs", "da", "de", "el", "es", "et", "fi", "fr", "ga", "hr", "hu", "is", "it", "lt", "lv",
         "mk", "mt", "nb", "pl", "pt-PT", "ro", "ru", "sk", "sl", "sq", "sr", "sv", "tr", "uk"]

source = json.load(open("tools/translations/source.json", encoding="utf-8"))
keys = [e["key"] for e in source]
by_key = {e["key"]: e for e in source}
placeholder = re.compile(r"\{\d+\}")

tables, problems = {}, []
for lang in LANGS:
    path = f"tools/translations/{lang}.json"
    if not os.path.exists(path):
        problems.append(f"{lang}: file missing")
        continue
    data = json.load(open(path, encoding="utf-8"))
    missing = [k for k in keys if not str(data.get(k, "")).strip()]
    if missing:
        problems.append(f"{lang}: {len(missing)} missing, e.g. {missing[:3]}")
    for k in keys:
        if k in data and sorted(placeholder.findall(data[k])) != sorted(placeholder.findall(by_key[k]["en"])):
            problems.append(f"{lang}: placeholder mismatch in {k!r}: {data[k]!r}")
    tables[lang] = {k: data[k] for k in keys if str(data.get(k, "")).strip()}

for p in problems:
    print("!!", p)
if problems and "--force" not in sys.argv:
    sys.exit("Fix the problems above (or pass --force to build with English fallbacks).")

# 1. Tables for L10n
os.makedirs("Shared/Localization", exist_ok=True)
for lang, table in tables.items():
    with open(f"Shared/Localization/liftr-{lang}.json", "w", encoding="utf-8") as f:
        json.dump(table, f, ensure_ascii=False, separators=(",", ":"), sort_keys=True)

# 2. InfoPlist.xcstrings
plist_path = "Shared/InfoPlist.xcstrings"
plist = json.load(open(plist_path, encoding="utf-8"))
for name, entry in plist["strings"].items():
    locs = entry["localizations"]
    for lang, table in tables.items():
        value = "Liftr" if name in ("CFBundleDisplayName", "CFBundleName") else table.get("plist." + name)
        if value:
            locs[lang] = {"stringUnit": {"state": "translated", "value": value}}
    entry["localizations"] = dict(sorted(locs.items()))
with open(plist_path, "w", encoding="utf-8") as f:
    json.dump(plist, f, ensure_ascii=False, indent=2)


def swift(s):
    return s.replace("\\", "\\\\").replace('"', '\\"')


# 3. MoreAppsText.all — keep the original Homey Store texts, add every other language
card_path = "Liftr/Views/Components/MoreAppsCard.swift"
card = open(card_path, encoding="utf-8").read()
existing = {m.group(1): m.group(0) for m in re.finditer(
    r'"([a-zA-Z-]+)": \.init\(section: "[^"]*", title: "[^"]*",\s*subtitle: "[^"]*", action: "[^"]*"\)', card)}
if "no" in existing and "nb" not in existing:
    existing["nb"] = existing.pop("no").replace('"no":', '"nb":', 1)
lines = []
for lang in ["nl", "en"] + LANGS:
    if lang in existing:
        lines.append("        " + re.sub(r"\s+", " ", existing[lang]) + ",")
    elif lang in tables:
        t = tables[lang]
        lines.append(f'        "{lang}": .init(section: "{swift(t["moreapps.section"])}", title: "{swift(t["moreapps.title"])}", '
                     f'subtitle: "{swift(t["moreapps.subtitle"])}", action: "{swift(t["moreapps.action"])}"),')
card = re.sub(r"(static let all: \[String: MoreAppsText\] = \[\n)(.*?)(\n    \])",
              lambda m: m.group(1) + "\n".join(lines) + m.group(3), card, flags=re.S)
open(card_path, "w", encoding="utf-8").write(card)

# 4. AppInfoText.all ("Version")
footer_path = "Liftr/Views/Components/AppInfoFooter.swift"
footer = open(footer_path, encoding="utf-8").read()
entries = ['        "nl": .init(version: "Versie"),', '        "en": .init(version: "Version"),']
entries += [f'        "{lang}": .init(version: "{swift(t["appinfo.version"])}"),' for lang, t in tables.items()]
footer = re.sub(r"(static let all: \[String: AppInfoText\] = \[\n)(.*?)(\n    \])",
                lambda m: m.group(1) + "\n".join(entries) + m.group(3), footer, flags=re.S)
open(footer_path, "w", encoding="utf-8").write(footer)

# 5. knownRegions in the project generator
gen_path = "tools/gen_xcodeproj.py"
gen = open(gen_path, encoding="utf-8").read()
regions = ", ".join(f'"{r}"' for r in ["en", "Base", "nl"] + LANGS)
gen = re.sub(r'"knownRegions": \[.*?\],', f'"knownRegions": [{regions}],', gen, flags=re.S)
open(gen_path, "w", encoding="utf-8").write(gen)

print(f"Built {len(tables)} languages + en/nl, {len(keys)} strings each.")
