"""Diagnostic de mise en page : images (bbox) et lignes de texte « MB / MR / MA / DEPERIS » par page citée.

Sert à vérifier que la lecture des légendes tient sur les 25 fiches ; n'écrit rien.
"""
import sys

import pymupdf as fitz

from sources import PDF, charger_fixture, numero_fiche

fx = charger_fixture()
pages = sorted({(numero_fiche(p["id"]), p["page"]) for p in fx["photos"]})
filtre = set(sys.argv[1:])
for nn, page in pages:
    if filtre and nn not in filtre:
        continue
    doc = fitz.open(PDF / f"{nn}.pdf")
    pg = doc[page - 1]
    print(f"=== {nn} p.{page} ({pg.rect.width:.0f}x{pg.rect.height:.0f})")
    for info in pg.get_image_info(xrefs=True):
        x0, y0, x1, y1 = info["bbox"]
        print(f"  IMG xref={info['xref']} bbox=({x0:.0f},{y0:.0f},{x1:.0f},{y1:.0f}) h={y1-y0:.0f} "
              f"natif={info['width']}x{info['height']}")
    for b in pg.get_text("dict")["blocks"]:
        for l in b.get("lines", []):
            t = "".join(s["text"] for s in l["spans"]).strip()
            if any(m in t for m in ("MB", "MR", "MA", "DEPERIS", "Arbre", "DF", "ref", "réf")):
                x0, y0, x1, y1 = l["bbox"]
                print(f"  TXT ({x0:.0f},{y0:.0f},{x1:.0f},{y1:.0f}) {t}")
    # dessins verts
    verts = 0
    for d in pg.get_drawings():
        c = d.get("color")
        if c and c[1] > 0.6 and c[0] < 0.7 and c[2] < 0.8 and c[1] > c[0] + 0.15:
            verts += 1
    print(f"  dessins verts : {verts}")
