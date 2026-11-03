#!/usr/bin/env python3
"""Collects every translatable text into tools/translations/source.json:
tr()/trf() calls, built-in exercise names, the More apps card, the footer and Info.plist texts."""
import glob, json, os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)
lit = r'"((?:[^"\\]|\\.)*)"'
call = re.compile(r'\btrf?\(\s*' + lit + r'\s*,\s*' + lit, re.S)
entries, seen = [], {}


def unescape(s):
    return s.encode().decode("unicode_escape").encode("latin-1").decode("utf-8") if "\\" in s else s


def add(key, en, nl, context):
    if key in seen:
        if context not in seen[key]["context"]:
            seen[key]["context"] += "; " + context
        return
    item = {"key": key, "en": en, "nl": nl, "context": context}
    seen[key] = item
    entries.append(item)


for path in sorted(glob.glob("**/*.swift", recursive=True)):
    if path.startswith("tools/"):
        continue
    src = open(path, encoding="utf-8").read()
    for m in call.finditer(src):
        en, nl = unescape(m.group(1)), unescape(m.group(2))
        add(en, en, nl, os.path.basename(path))

lib = open("Shared/ExerciseLibrary.swift", encoding="utf-8").read()
for m in re.finditer(r'\.init\(id: "([^"]+)", en: "([^"]+)", nl: "([^"]+)", muscle: \.(\w+), kind: \.(\w+)\)', lib):
    add("exercise." + m.group(1), m.group(2), m.group(3), f"gym exercise name ({m.group(4)}, {m.group(5)})")

more = [("moreapps.section", "Discover", "Ontdekken", "settings section header above the developer card"),
        ("moreapps.title", "More apps from the developer", "Meer apps van de ontwikkelaar", "card title"),
        ("moreapps.subtitle", "Apps for iPhone, iPad, Apple TV and Homey", "Apps voor iPhone, iPad, Apple TV en Homey", "card subtitle (Homey is a product name)"),
        ("moreapps.action", "View", "Bekijk", "small button on the card"),
        ("appinfo.version", "Version", "Versie", "footer: 'Version: 1.0.0 (3)'")]
for k, en, nl, c in more:
    add(k, en, nl, c)

plist = json.load(open("Shared/InfoPlist.xcstrings", encoding="utf-8"))
for k, v in plist["strings"].items():
    if k in ("CFBundleDisplayName", "CFBundleName"):
        continue
    loc = v["localizations"]
    add("plist." + k, loc["en"]["stringUnit"]["value"], loc["nl"]["stringUnit"]["value"], "iOS permission prompt text")

json.dump(entries, open("tools/translations/source.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(len(entries), "strings")
