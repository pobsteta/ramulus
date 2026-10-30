"""Extraction des 250 photos des fiches DSF, rattachées par la légende, avec manifeste. Mission 026.

Les octets natifs de chaque image sont écrits sans ré-encodage dans `~/dev/donnees-ramulus/dsf-2025/photos/`.
La rotation d'affichage dans le PDF est notée au manifeste (colonne `rotation`), appliquée seulement au moment de
calculer les vecteurs. Le tracé « houppier fonctionnel » (vert, rouge sur certaines fiches) est cherché comme
dessin vectoriel sur la page (colonne `trace_vectoriel`) et comme pixels dans l'image native (colonne `pixels_trace`) : voir le rapport.
"""
from __future__ import annotations

import csv
import io
import random
import sys
from dataclasses import dataclass

import numpy as np
import pymupdf
from PIL import Image

from legendes import Legende, lire_legende, rattacher
from sources import DSF, PDF, PHOTOS, charger_fixture, numero_fiche, sha256, urls_par_fiche

HAUTEUR_MIN = 150      # points : écarte logos, pictogrammes et dessins (rapport 2026-10-30, § 5)
LARGEUR_MIN = 100
ECART_LEGENDE = 120    # points sous l'image où chercher la légende
MANIFESTE = DSF / "manifeste.csv"
ECARTS = DSF / "ecarts.csv"
COLONNES = ["id", "fichier", "sha256", "page", "mb", "second", "classe", "pdf_source", "url", "bbox", "rotation",
            "largeur", "hauteur", "legende_lue", "methode_rattachement", "trace_vectoriel", "pixels_trace",
            "dans_amorce", "motif_hors_amorce", "ecart"]


@dataclass
class ImagePage:
    xref: int
    bbox: tuple[float, float, float, float]
    rotation: int        # degrés, sens horaire, pour remettre l'image native dans le sens d'affichage
    legende: Legende


def _rotation(transform) -> int:
    """Rotation d'affichage déduite de la matrice (a, b, c, d, e, f) du placement de l'image."""
    a, b, c, d = transform[:4]
    if abs(b) + abs(c) < 0.01 * (abs(a) + abs(d)):    # termes croisés négligeables : pas de quart de tour
        return 180 if a < 0 and d < 0 else 0
    # Quart de tour : sens déduit du signe de b (vérifié à l'œil sur 01_01 et 04_01, voir rapport).
    return 90 if b > 0 else 270


def _lignes(page) -> list[tuple[tuple[float, float, float, float], str]]:
    out = []
    for b in page.get_text("dict")["blocks"]:
        for l in b.get("lines", []):
            t = "".join(s["text"] for s in l["spans"]).strip()
            if t:
                out.append((tuple(l["bbox"]), t))
    return out


def _dans(bb, img, marge=2.0) -> bool:
    return bb[0] >= img[0] - marge and bb[2] <= img[2] + marge and bb[1] >= img[1] - marge and bb[3] <= img[3] + marge


def _legende_sous(img, autres, lignes) -> str:
    """Lignes situées sous l'image, dans sa colonne, avant toute autre image."""
    x0, y0, x1, y1 = img
    limite = y1 + ECART_LEGENDE
    for o in autres:
        if o is img:
            continue
        chevauche = min(x1, o[2]) - max(x0, o[0]) > 20
        if chevauche and o[1] >= y1 - 5:
            limite = min(limite, o[1])
    garde = []
    for bb, t in lignes:
        cx = bb[0]
        if not (x0 - 25 <= cx <= x1 - 10):
            continue
        if y1 - 8 <= bb[1] <= limite:
            garde.append((bb[1], bb[0], t))
    garde.sort()
    return " ".join(t for _, _, t in garde)


def couleur_trait(c) -> str:
    """« vert », « rouge » ou « » pour une couleur de trait RVB (0 à 1)."""
    if not c or len(c) != 3:
        return ""
    r, g, b = c
    if g > 0.5 and g > r + 0.15 and g > b + 0.05:
        return "vert"
    if r > 0.5 and r > g + 0.25 and r > b + 0.15:
        return "rouge"
    return ""


def _trace_vectoriel(page, img) -> str:
    """Couleur du trait de couleur dessiné en vectoriel par-dessus cette image dans le PDF (« » si aucun)."""
    r = pymupdf.Rect(img)
    for d in page.get_drawings():
        coul = couleur_trait(d.get("color"))
        if coul and r.intersects(d["rect"]):
            return coul
    return ""


def pixels_trace(octets: bytes) -> tuple[float, float]:
    """Part des pixels du vert vif clair et du rouge vif des tracés, dans l'image native, en pour mille.

    Indice seulement (des feuilles d'automne sont rouges) : il désigne les images à regarder à l'œil pour dire si
    un tracé est incrusté dans la photo elle-même.
    """
    im = Image.open(io.BytesIO(octets)).convert("RGB")
    im.thumbnail((400, 400))
    a = np.asarray(im).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    vert = (g > 200) & (g - r > 60) & (g - b > 40) & (r > 60)
    rouge = (r > 200) & (r - g > 120) & (r - b > 100)
    return float(vert.mean() * 1000), float(rouge.mean() * 1000)


def _recouvrement(a, b) -> float:
    """Aire commune rapportée à la plus petite des deux boîtes."""
    l, t, r, bt = max(a[0], b[0]), max(a[1], b[1]), min(a[2], b[2]), min(a[3], b[3])
    if r <= l or bt <= t:
        return 0.0
    aire = lambda x: (x[2] - x[0]) * (x[3] - x[1])
    return (r - l) * (bt - t) / min(aire(a), aire(b))


def dedoublonner(infos: list[dict], rect_page) -> list[dict]:
    """Une photo, un objet.

    - Même image (xref) placée deux fois (fiche 23, p. 3 : une copie déborde de la page) : on garde la
      placement entièrement dans la page, à défaut le plus petit.
    - Deux images superposées au même endroit (fiche 04, p. 4 : deux versions de la même photo) : on garde la
      dernière dessinée, celle qu'on voit.
    """
    ordre = {id(i): n for n, i in enumerate(infos)}
    par_xref: dict[int, dict] = {}
    for i in infos:
        x0, y0, x1, y1 = i["bbox"]
        cle = (x0 >= rect_page.x0 and y0 >= rect_page.y0 and x1 <= rect_page.x1 and y1 <= rect_page.y1,
               -(x1 - x0) * (y1 - y0))
        garde = par_xref.get(i["xref"])
        if garde is None or cle > garde[0]:
            par_xref[i["xref"]] = (cle, i)
    uniques = sorted((i for _, i in par_xref.values()), key=lambda i: ordre[id(i)])
    out: list[dict] = []
    for i in uniques:   # dans l'ordre de dessin : la dernière dessinée recouvre les autres
        out = [o for o in out if _recouvrement(o["bbox"], i["bbox"]) < 0.9]
        out.append(i)
    return out


def images_de_page(doc, page_no: int) -> list[ImagePage]:
    page = doc[page_no - 1]
    infos = []
    for info in page.get_image_info(xrefs=True):   # dans l'ordre de dessin
        x0, y0, x1, y1 = info["bbox"]
        if y1 - y0 < HAUTEUR_MIN or x1 - x0 < LARGEUR_MIN:
            continue
        infos.append(info)
    infos = dedoublonner(infos, page.rect)
    # ordre de lecture : rangées (haut en bas), puis gauche à droite
    infos.sort(key=lambda i: i["bbox"][1])
    rangees: list[list[dict]] = []
    for i in infos:
        if rangees and abs(i["bbox"][1] - rangees[-1][0]["bbox"][1]) < 80:
            rangees[-1].append(i)
        else:
            rangees.append([i])
    ordre = [i for r in rangees for i in sorted(r, key=lambda i: i["bbox"][0])]
    lignes = [(bb, t) for bb, t in _lignes(page)]
    bbs = [tuple(i["bbox"]) for i in ordre]
    lignes = [(bb, t) for bb, t in lignes if not any(_dans(bb, b) for b in bbs)]
    out = []
    for i, bb in zip(ordre, bbs):
        out.append(ImagePage(i["xref"], bb, _rotation(i["transform"]), lire_legende(_legende_sous(bb, bbs, lignes))))
    return out


def extraire(ecrire: bool = True, graine: int = 26) -> list[dict]:
    fixture = charger_fixture()
    incoh = set(fixture["incoherences_connues"])
    urls = urls_par_fiche(fixture)
    par_page: dict[tuple[str, int], list[dict]] = {}
    for p in fixture["photos"]:
        par_page.setdefault((numero_fiche(p["id"]), p["page"]), []).append(p)
    PHOTOS.mkdir(parents=True, exist_ok=True)
    lignes, erreurs, non_rattachees = [], [], []
    docs = {}
    for (nn, pg), photos_fx in sorted(par_page.items()):
        doc = docs.setdefault(nn, pymupdf.open(PDF / f"{nn}.pdf"))
        imgs = images_de_page(doc, pg)
        try:
            ratt, non = rattacher(photos_fx, [im.legende for im in imgs])
        except ValueError as e:
            erreurs.append(f"fiche {nn} p.{pg} : {e} ; légendes lues : {[im.legende.texte for im in imgs]}")
            continue
        for n in non:
            non_rattachees.append([f"{nn}.pdf", pg, ";".join(f"{v:.1f}" for v in imgs[n.image].bbox), n.motif])
        page = doc[pg - 1]
        par_id = {p["id"]: p for p in photos_fx}
        for r in ratt:
            im = imgs[r.image]
            fx = par_id[r.id]
            ext = doc.extract_image(im.xref)
            octets, extension = ext["image"], ext["ext"]
            if extension == "jpx":
                extension = "jp2"
            fichier = PHOTOS / f"{r.id}.{extension}"
            if ecrire:
                fichier.write_bytes(octets)
            motif = []
            if r.id in incoh:
                motif.append("incohérence connue des fiches")
            if fx["mb"] is None or fx["second"] is None:
                motif.append("notes nulles dans le fixture")
            lignes.append({
                "id": r.id, "fichier": fichier.name, "sha256": sha256(fichier) if ecrire else "",
                "page": pg, "mb": "" if fx["mb"] is None else fx["mb"],
                "second": "" if fx["second"] is None else fx["second"], "classe": fx["classe"],
                "pdf_source": f"{nn}.pdf", "url": urls[nn],
                "bbox": ";".join(f"{v:.1f}" for v in im.bbox), "rotation": im.rotation,
                "largeur": ext["width"], "hauteur": ext["height"],
                "legende_lue": im.legende.texte, "methode_rattachement": r.methode,
                "trace_vectoriel": _trace_vectoriel(page, im.bbox),
                "pixels_trace": "{:.2f};{:.2f}".format(*pixels_trace(octets)),
                "dans_amorce": int(not motif), "motif_hors_amorce": " ; ".join(motif),
                "ecart": " ; ".join(r.ecart),
            })
    if erreurs:
        print("\n".join(erreurs), file=sys.stderr)
        raise SystemExit(f"{len(erreurs)} page(s) sans rattachement complet : rien n'est validé")
    lignes.sort(key=lambda l: l["id"])
    ids_fx = {p["id"] for p in fixture["photos"]}
    ids = [l["id"] for l in lignes]
    assert len(ids) == len(set(ids)), "id rattaché deux fois"
    assert set(ids) == ids_fx, f"ids manquants : {sorted(ids_fx - set(ids))}"
    if ecrire:
        orphelins = sorted({f.name for f in PHOTOS.iterdir()} - {l["fichier"] for l in lignes})
        assert not orphelins, f"fichiers orphelins : {orphelins}"
        with open(MANIFESTE, "w", encoding="utf-8", newline="") as f:
            w = csv.DictWriter(f, COLONNES)
            w.writeheader()
            w.writerows(lignes)
        with open(ECARTS, "w", encoding="utf-8", newline="") as f:
            w = csv.writer(f)
            w.writerow(["id", "page", "ecart"])
            for l in lignes:
                if l["ecart"] or l["id"] in incoh:
                    texte = l["ecart"] or ""
                    if l["id"] in incoh:
                        texte = (texte + " ; " if texte else "") + "incohérence connue (sous-notes et classe)"
                    w.writerow([l["id"], l["page"], texte])
        with open(DSF / "images-non-rattachees.csv", "w", encoding="utf-8", newline="") as f:
            w = csv.writer(f)
            w.writerow(["pdf", "page", "bbox", "motif"])
            w.writerows(non_rattachees)
    for n in non_rattachees:
        print("non rattachée :", *n)
    rng = random.Random(graine)
    tirage = sorted(rng.sample(ids, 10))
    print(f"{len(lignes)} photos extraites et rattachées ; manifeste {MANIFESTE}")
    print(f"tirage de contrôle (graine {graine}) : {' '.join(tirage)}")
    return lignes


if __name__ == "__main__":
    extraire(ecrire="--a-blanc" not in sys.argv)
