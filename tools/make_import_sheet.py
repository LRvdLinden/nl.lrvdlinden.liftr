#!/usr/bin/env python3
"""Builds the downloadable import sheets for liftr.nu (website/downloads/).

One sheet to fill in (a row per training day, a column per exercise) and one with instructions.
The fill-in sheet ships empty on purpose: example rows would be imported as real workouts.
Run: python3 tools/make_import_sheet.py
"""
import os
from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "website", "downloads")

ORANGE = "FF6B00"
DARK = "111111"
LIGHT = "FFF1E6"

TEXT = {
    "nl": dict(
        file="liftr-importsheet.xlsx",
        sheet="Trainingen", help_sheet="Uitleg",
        date="Datum", note="Notitie",
        exercises=["Leg press", "Chest press", "Lat pulldown", "Seated row", "Shoulder press", "Leg curl (zittend)",
                   "Leg extension", "Abductor (benen naar buiten)", "Adductor (benen naar binnen)", "Buikcrunch (apparaat)"],
        title="Liftr importsheet",
        intro=[
            "Vul op het werkblad ‘Trainingen’ per trainingsdag één rij in: de datum, en per oefening wat je deed.",
            "Laat een cel leeg als je die oefening niet deed. Voeg gerust kolommen toe of hernoem ze: de kopregel is de naam van de oefening.",
            "Importeren: zet het bestand in Bestanden of iCloud Drive, open Liftr → Instellingen → Importeren → Spreadsheet → Kies bestand.",
            "Je ziet eerst wat Liftr ervan maakt; pas als je op Importeer tikt worden de trainingen opgeslagen.",
            "Geïmporteerde trainingen komen in je geschiedenis en grafieken, maar worden niet naar Apple Gezondheid geschreven.",
        ],
        table_head=("Dit typ je in een cel", "Zo komt het in Liftr"),
        rows=[
            ("Datum, bijv. 3-11-2026 of 3 nov", "De dag van de training. Zonder jaartal rekent Liftr het jaar terug vanaf de onderste rij."),
            ("45", "45 kg, met het standaard aantal sets en herhalingen (bij importeren instelbaar, standaard 3 × 10)."),
            ("27,5  of  27.5", "27,5 kg."),
            ("27 (3×10)", "3 sets van 10 herhalingen met 27 kg."),
            ("18 (4×5)", "4 sets van 5 herhalingen met 18 kg."),
            ("25 / 18", "Twee sets: één met 25 kg en één met 18 kg."),
            ("36/41/45/52", "Vier sets, elk met zijn eigen gewicht."),
            ("23 x 2", "Twee sets met 23 kg."),
            ("27 (rechte 3×10)", "3 × 10 met 27 kg, ‘rechte’ als notitie bij de oefening."),
            ("Notitie-kolom", "Komt als notitie bij de training, bijv. ‘Periode 1’ of ‘schema A’."),
        ],
        other="Liever per set? Een sheet met de kolommen Datum, Oefening, Sets, Herhalingen en Gewicht werkt ook – net als een CSV-export uit Liftr.",
        site="liftr.nu",
    ),
    "en": dict(
        file="liftr-import-sheet.xlsx",
        sheet="Workouts", help_sheet="How to",
        date="Date", note="Note",
        exercises=["Leg press", "Chest press machine", "Lat pulldown", "Seated row machine", "Shoulder press machine",
                   "Seated leg curl", "Leg extension", "Hip abduction", "Hip adduction", "Ab crunch machine"],
        title="Liftr import sheet",
        intro=[
            "On the ‘Workouts’ sheet, fill in one row per training day: the date, and what you did per exercise.",
            "Leave a cell empty if you didn’t do that exercise. Add or rename columns as you like: the header is the exercise name.",
            "To import: save the file to Files or iCloud Drive, open Liftr → Settings → Import → Spreadsheet → Choose file.",
            "You first see what Liftr makes of it; workouts are only saved when you tap Import.",
            "Imported workouts go into your history and charts, but are not written to Apple Health.",
        ],
        table_head=("Type this in a cell", "This is what Liftr makes of it"),
        rows=[
            ("Date, e.g. 2026-11-03 or 3 Nov", "The day of the workout. Without a year, Liftr counts back from the bottom row."),
            ("45", "45 kg, with the default sets and reps (adjustable when importing, 3 × 10 by default)."),
            ("27.5  or  27,5", "27.5 kg."),
            ("27 (3×10)", "3 sets of 10 reps at 27 kg."),
            ("18 (4×5)", "4 sets of 5 reps at 18 kg."),
            ("25 / 18", "Two sets: one at 25 kg and one at 18 kg."),
            ("36/41/45/52", "Four sets, each with its own weight."),
            ("23 x 2", "Two sets at 23 kg."),
            ("27 (wide grip 3×10)", "3 × 10 at 27 kg, ‘wide grip’ as a note on the exercise."),
            ("Note column", "Added as a note to the workout, e.g. ‘Block 1’ or ‘Routine A’."),
        ],
        other="Prefer one row per set? A sheet with the columns Date, Exercise, Sets, Reps and Weight works too – as does a CSV export from Liftr.",
        site="liftr.nu",
    ),
}


def build(lang, t):
    wb = Workbook()
    ws = wb.active
    ws.title = t["sheet"]
    header = [t["date"], t["note"]] + t["exercises"]
    thin = Side(style="thin", color="E3E3E3")
    for col, name in enumerate(header, 1):
        c = ws.cell(row=1, column=col, value=name)
        c.font = Font(bold=True, color="FFFFFF" if col > 2 else "FFFFFF", name="Arial", size=11)
        c.fill = PatternFill("solid", fgColor=ORANGE if col > 2 else DARK)
        c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        ws.column_dimensions[get_column_letter(col)].width = 14 if col == 1 else (16 if col == 2 else 15)
    ws.row_dimensions[1].height = 42
    for r in range(2, 302):
        ws.cell(row=r, column=1).number_format = "DD-MM-YYYY" if lang == "nl" else "YYYY-MM-DD"
        for col in range(1, len(header) + 1):
            c = ws.cell(row=r, column=col)
            c.border = Border(bottom=thin, right=thin)
            c.alignment = Alignment(horizontal="center")
            if col > 2 and r % 2 == 0:
                c.fill = PatternFill("solid", fgColor="FAFAFA")
    ws.freeze_panes = "B2"

    help_ = wb.create_sheet(t["help_sheet"])
    help_.column_dimensions["A"].width = 34
    help_.column_dimensions["B"].width = 90
    help_["A1"] = t["title"]
    help_["A1"].font = Font(bold=True, size=18, color=ORANGE, name="Arial")
    row = 3
    for line in t["intro"]:
        help_.cell(row=row, column=1, value="•").alignment = Alignment(horizontal="right", vertical="top")
        c = help_.cell(row=row, column=2, value=line)
        c.alignment = Alignment(wrap_text=True, vertical="top")
        row += 1
    row += 1
    for col, name in enumerate(t["table_head"], 1):
        c = help_.cell(row=row, column=col, value=name)
        c.font = Font(bold=True, color="FFFFFF", name="Arial")
        c.fill = PatternFill("solid", fgColor=ORANGE)
    row += 1
    for a, b in t["rows"]:
        ca = help_.cell(row=row, column=1, value=a)
        ca.font = Font(name="Menlo", bold=True)
        ca.fill = PatternFill("solid", fgColor=LIGHT)
        help_.cell(row=row, column=2, value=b).alignment = Alignment(wrap_text=True, vertical="top")
        row += 1
    row += 1
    help_.cell(row=row, column=2, value=t["other"]).alignment = Alignment(wrap_text=True)
    row += 2
    help_.cell(row=row, column=2, value=t["site"]).font = Font(color=ORANGE, bold=True)

    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, t["file"])
    wb.save(path)
    print("wrote", os.path.relpath(path, ROOT))


for lang, t in TEXT.items():
    build(lang, t)
