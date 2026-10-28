"""Génère les cas communs du format deperis-v1 (fixtures/echange/) et les listes du schéma tirées des référentiels.

    python3 fixtures/echange/generer.py

1. Réécrit dans docs/formats/deperis-v1.schema.json les « $defs » essence, essence_type et abaque, à partir de
   docs/referentiels/deperis/essences.csv et abaque.csv (seules sources ; rien n'est saisi à la main).
2. Écrit chaque cas dans fixtures/echange/<cas>/ : le ou les fichiers (deperis.gpkg ou .zip rendu), leur instantané
   JSON (<nom>.instantane.json) et attendu.json (résultat attendu d'un lecteur conforme, et pourquoi).

Les GeoPackages sont écrits avec le module sqlite3 de Python selon OGC 12-128 (M1, option b, Pascal le
2026-10-28) ; la CI les passe à ogrinfo (fixtures/echange/verifier_ogrinfo.py).

Données d'exemple : trois placettes synthétiques en Côte-d'Or (aucune forêt nommée) ; les notes sont reprises telles
quelles de photos des fiches DSF 2025 (fixtures/fiches-dsf-2025.json, identifiant cité dans attendu.json), hors des
quatre incohérences connues. Elles ne disent rien de l'état réel du lieu.
"""

from __future__ import annotations

import base64
import copy
import csv
import json
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

ICI = Path(__file__).resolve().parent
sys.path.insert(0, str(ICI))

import deperis_v1 as d  # noqa: E402

REFERENTIELS = d.RACINE / "docs" / "referentiels" / "deperis"
FICHES_DSF = d.RACINE / "fixtures" / "fiches-dsf-2025.json"

CODE = "EXEMPLE-2027-01"
CODE_CN = "EXEMPLE-2027-01-CN"
VERSION_NEMETON = "exemple"
VERSION_RAMULUS = "exemple"
REGLE = "Kraft 1 à 3, tour d'horizon depuis le nord, sens horaire"
DELAI_PROVISOIRE = 15  # jours ; valeur d'exemple PROVISOIRE, non fixée par Pascal (fiche 002)

# Petite image JPEG 8 x 8 (produite une fois avec Pillow, gardée telle quelle pour que les empreintes ne dépendent
# d'aucune bibliothèque) ; chaque photo y ajoute un segment de commentaire avec son nom, pour avoir des empreintes
# distinctes.
JPEG_BASE = base64.b64decode(
    "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDABALDA4MChAODQ4SERATGCgaGBYWGDEjJR0oOjM9PDkzODdASFxOQERXRTc4UG1RV19iZ2hnPk1xeXBk"
    "eFxlZ2P/2wBDARESEhgVGC8aGi9jQjhCY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2P/wAARCAAIAAgD"
    "ASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKB"
    "kaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZ"
    "mqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQF"
    "BgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5"
    "OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX"
    "2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwCvRRRXMYn/2Q=="
)


def jpeg(nom: str) -> bytes:
    texte = f"Ramulus, photo d'exemple {nom}".encode()
    return JPEG_BASE[:2] + b"\xff\xfe" + (len(texte) + 2).to_bytes(2, "big") + texte + JPEG_BASE[2:]


# --------------------------------------------------------------------------------------------------------------
# Référentiels -> schéma
# --------------------------------------------------------------------------------------------------------------

def lire_essences() -> list[tuple[str, str]]:
    with open(REFERENTIELS / "essences.csv", encoding="utf-8", newline="") as f:
        return [(r["essence"].strip(), r["type"].strip()) for r in csv.DictReader(f, delimiter=";")]


def lire_abaque() -> dict[tuple[int, int], str]:
    with open(REFERENTIELS / "abaque.csv", encoding="utf-8", newline="") as f:
        lignes = list(csv.DictReader(f, delimiter=";"))
    return {(int(r["mb"]), s): r[f"second_{s}"].strip() for r in lignes for s in range(6)}


def defs_referentiels() -> dict:
    essences = lire_essences()
    abaque = lire_abaque()
    return {
        "essence": {
            "description": "Les 19 essences des fiches DEPERIS du DSF, nom exact (accents compris, Unicode NFC). "
                           "Généré depuis docs/referentiels/deperis/essences.csv.",
            "x-motif": "essence_hors_referentiel",
            "enum": [nom for nom, _ in essences],
        },
        "essence_type": {
            "description": "Type de chaque essence. Généré depuis docs/referentiels/deperis/essences.csv.",
            "x-motif": "type_incoherent",
            "anyOf": [
                {"properties": {"essence": {"const": nom}, "type": {"const": typ}}} for nom, typ in essences
            ],
        },
        "abaque": {
            "description": "Classe = abaque(MB, MR ou MA), 36 cases (fiches DSF 2025, page 1). "
                           "Généré depuis docs/referentiels/deperis/abaque.csv.",
            "x-motif": "classe_differente_abaque",
            "anyOf": [
                {"properties": {"mb": {"const": mb}, "second": {"const": s}, "classe": {"const": c}}}
                for (mb, s), c in sorted(abaque.items())
            ],
        },
    }


def formater(valeur, retrait: int = 0, debut: int = 0, largeur: int = 118) -> str:
    """JSON indenté de deux espaces ; un objet ou un tableau qui tient sur sa ligne y est écrit en entier.

    `retrait` : indentation de la ligne courante ; `debut` : colonne où commence la valeur sur cette ligne.
    """
    compact = json.dumps(valeur, ensure_ascii=False, separators=(", ", ": "))
    if not isinstance(valeur, (dict, list)) or not valeur or debut + len(compact) <= largeur:
        return compact
    marge = retrait + 2
    if isinstance(valeur, dict):
        lignes = []
        for k, v in valeur.items():
            cle = json.dumps(k, ensure_ascii=False) + ": "
            lignes.append(" " * marge + cle + formater(v, marge, marge + len(cle), largeur))
        return "{\n" + ",\n".join(lignes) + "\n" + " " * retrait + "}"
    lignes = [" " * marge + formater(v, marge, marge, largeur) for v in valeur]
    return "[\n" + ",\n".join(lignes) + "\n" + " " * retrait + "]"


def schema_a_jour() -> str:
    schema = json.loads(d.SCHEMA.read_text(encoding="utf-8"))
    schema["$defs"].update(defs_referentiels())
    return formater(schema) + "\n"


# --------------------------------------------------------------------------------------------------------------
# Données d'exemple
# --------------------------------------------------------------------------------------------------------------

DSF = {p["id"]: p for p in json.loads(FICHES_DSF.read_text(encoding="utf-8"))["photos"]}
TYPES = dict(lire_essences())
CRITERE = {"FEUILLU": "MR", "RESINEUX": "MA"}

# (plot_id, longitude, latitude) en degrés, et (wgt, ip, stratum, strat_type) : colonnes de plan d'exemple.
PLACETTES = [
    ("P001", 5.0650, 47.1150, 12.5, 0.08, "S1", "feuillus"),
    ("P002", 5.0690, 47.1150, 9.75, 0.10, "S2", "resineux"),
    ("P003", 5.0650, 47.1180, 12.5, 0.08, "S1", "melange"),
]
COLLECTE = {"P001": 1}
CONTRE_NOTATION = {"P002": 1}

# Centre relevé : décalage (est, nord) en mètres depuis le point prévu, précision, opérateur, heure UTC.
CENTRES = {
    "P001": (3.2, -2.1, 5.8, "operateur-A", "2027-07-06T08:02:00.000Z"),
    "P002": (-4.0, 1.5, 7.2, "operateur-B", "2027-07-06T12:58:00.000Z"),
    "P003": (2.0, 5.5, 4.9, "operateur-B", "2027-07-07T07:01:00.000Z"),
}

# Arbres notés : (plot_id, rang, repere, photo DSF dont les notes sont reprises, opérateur, heure UTC, nb de photos).
ARBRES = [
    ("P001", 1, "1", "dsf2025-06_06", "operateur-A", "2027-07-06T08:10:00.000Z", 2),
    ("P001", 2, "2", "dsf2025-13_05", "operateur-A", "2027-07-06T08:14:00.000Z", 2),
    ("P001", 3, "3", "dsf2025-08_06", "operateur-A", "2027-07-06T08:19:00.000Z", 2),
    ("P001", 4, "4", "dsf2025-06_01", "operateur-A", "2027-07-06T08:23:00.000Z", 2),
    ("P002", 1, "11", "dsf2025-20_09", "operateur-B", "2027-07-06T13:05:00.000Z", 1),
    ("P002", 2, "12", "dsf2025-14_12", "operateur-B", "2027-07-06T13:09:00.000Z", 0),
    ("P002", 3, "12b", "dsf2025-00_02", "operateur-B", "2027-07-06T13:12:00.000Z", 0),
    ("P003", 1, "21", "dsf2025-13_12", "operateur-B", "2027-07-07T07:08:00.000Z", 0),
    ("P003", 2, "22", "dsf2025-14_08", "operateur-B", "2027-07-07T07:12:00.000Z", 0),
]
# Contre-notation de P002 par un second opérateur, une semaine plus tard (autres photos DSF des mêmes essences).
CONTRE = [
    ("P002", 1, "11", "dsf2025-20_08", "operateur-C", "2027-07-13T09:05:00.000Z", 0),
    ("P002", 2, "12", "dsf2025-14_09", "operateur-C", "2027-07-13T09:10:00.000Z", 0),
    ("P002", 3, "12b", "dsf2025-00_02", "operateur-C", "2027-07-13T09:14:00.000Z", 0),
]

PREPARE_LE = "2027-06-15T08:00:00.000Z"
PREPARE_LE_CN = "2027-07-08T08:00:00.000Z"
RENDU_LE = "2027-07-07T16:00:00.000Z"
RENDU_LE_CN = "2027-07-13T16:00:00.000Z"


def ms(horodatage: str) -> int:
    return int(datetime.strptime(horodatage, "%Y-%m-%dT%H:%M:%S.%fZ").replace(tzinfo=timezone.utc).timestamp() * 1000)


def uuid(horodatage: str, graine: str) -> str:
    return d.uuid7_deterministe(ms(horodatage), graine)


COLONNES_TOURNEE = {
    "fid": "INTEGER", "version_schema": "INTEGER", "code_tournee": "TEXT", "nature": "TEXT",
    "prepare_le": "DATETIME", "version_nemeton": "TEXT", "delai_contre_notation_jours": "INTEGER",
    "operateur": "TEXT", "version_ramulus": "TEXT",
}
COLONNES_PLACETTES = {
    "fid": "INTEGER", "geom": "POINT", "plot_id": "TEXT", "wgt": "REAL", "ip": "REAL", "stratum": "TEXT",
    "strat_type": "TEXT", "nb_arbres": "INTEGER", "regle_choix": "TEXT", "saison": "TEXT",
    "collecte_photos": "BOOLEAN", "contre_notation": "BOOLEAN", "etat": "TEXT",
}
COLONNES_CENTRES = {
    "fid": "INTEGER", "geom": "POINT", "uuid": "TEXT", "plot_id": "TEXT", "precision_m": "REAL",
    "nb_mesures": "INTEGER", "source": "TEXT", "date_releve": "DATETIME", "operateur": "TEXT",
}
COLONNES_NOTES = {
    "fid": "INTEGER", "uuid": "TEXT", "plot_id": "TEXT", "rang": "INTEGER", "repere": "TEXT", "essence": "TEXT",
    "type": "TEXT", "mb": "INTEGER", "second": "INTEGER", "critere": "TEXT", "classe": "TEXT", "saison": "TEXT",
    "date_notation": "DATETIME", "operateur": "TEXT", "photos": "TEXT", "ia_premiere_mb": "INTEGER",
    "ia_premiere_second": "INTEGER", "ia_mb": "INTEGER", "ia_second": "INTEGER", "ia_confiance": "REAL",
    "ia_modele": "TEXT",
}
L93 = {"organisation": "EPSG", "code": 2154}


def table(data_type: str, colonnes: dict, lignes: list, geometrie: bool) -> dict:
    return {
        "data_type": data_type,
        "geometrie": {"colonne": "geom", "type": "POINT", "srs": dict(L93)} if geometrie else None,
        "index_spatial": False,
        "cle_primaire": "fid",
        "colonnes": dict(colonnes),
        "lignes": lignes,
    }


def point_prevu(plot_id: str) -> dict:
    for pid, lon, lat, *_ in PLACETTES:
        if pid == plot_id:
            x, y = d.lonlat_vers_l93(lon, lat)
            return {"x": round(x, 2), "y": round(y, 2)}
    raise KeyError(plot_id)


def tournee_depart() -> dict:
    """A1 : la tournée telle que Nemeton l'écrit (Q13 : toutes les tables, vides côté Ramulus)."""
    placettes = []
    for fid, (pid, _lon, _lat, wgt, ip, stratum, strat_type) in enumerate(PLACETTES, start=1):
        placettes.append({
            "fid": fid, "geom": point_prevu(pid), "plot_id": pid, "wgt": wgt, "ip": ip, "stratum": stratum,
            "strat_type": strat_type, "nb_arbres": 4, "regle_choix": REGLE, "saison": "ETE",
            "collecte_photos": COLLECTE.get(pid, 0), "contre_notation": CONTRE_NOTATION.get(pid, 0), "etat": None,
        })
    tournee = {
        "fid": 1, "version_schema": 1, "code_tournee": CODE, "nature": "premiere", "prepare_le": PREPARE_LE,
        "version_nemeton": VERSION_NEMETON, "delai_contre_notation_jours": DELAI_PROVISOIRE, "operateur": None,
        "version_ramulus": None,
    }
    return {
        "application_id": d.APPLICATION_ID_GPKG,
        "user_version": d.USER_VERSION_1_4,
        "version_schema": 1,
        "nature": "premiere",
        "tables": {
            "tournee": table("attributes", COLONNES_TOURNEE, [tournee], False),
            "placettes": table("features", COLONNES_PLACETTES, placettes, True),
            "centres": table("features", COLONNES_CENTRES, [], True),
            "notations": table("attributes", COLONNES_NOTES, [], False),
        },
    }


def ligne_centre(plot_id: str) -> dict:
    de, dn, precision, operateur, quand = CENTRES[plot_id]
    p = point_prevu(plot_id)
    return {
        "geom": {"x": round(p["x"] + de, 2), "y": round(p["y"] + dn, 2)},
        "uuid": uuid(quand, f"centre-{plot_id}"), "plot_id": plot_id, "precision_m": precision, "nb_mesures": 60,
        "source": "telephone", "date_releve": quand, "operateur": operateur,
    }


def ligne_note(arbre: tuple, table_nom: str) -> tuple[dict, dict[str, bytes]]:
    plot_id, rang, repere, dsf, operateur, quand, nb_photos = arbre
    p = DSF[dsf]
    u = uuid(quand, f"{table_nom}-{plot_id}-{rang}")
    photos = {}
    liste = []
    for i in range(1, nb_photos + 1):
        nom = f"photos/{u}_{i}.jpg"
        photos[nom] = jpeg(nom)
        liste.append({"nom": nom, "sha256": d.sha256(photos[nom])})
    typ = TYPES[p["essence"]]
    ligne = {
        "uuid": u, "plot_id": plot_id, "rang": rang, "repere": repere, "essence": p["essence"], "type": typ,
        "mb": p["mb"], "second": p["second"], "critere": CRITERE[typ], "classe": p["classe"], "saison": "ETE",
        "date_notation": quand, "operateur": operateur, "photos": liste, "ia_premiere_mb": None,
        "ia_premiere_second": None, "ia_mb": None, "ia_second": None, "ia_confiance": None, "ia_modele": None,
    }
    return ligne, photos


def numeroter(lignes: list[dict], colonnes: dict) -> list[dict]:
    """fid dans l'ordre des uuid (v7 : ordre du temps), colonnes dans l'ordre de la table."""
    tri = sorted(lignes, key=lambda l: l["uuid"])
    return [{c: (l | {"fid": i}).get(c) for c in colonnes} for i, l in enumerate(tri, start=1)]


SOURCES_DSF: dict[str, str] = {}  # uuid -> identifiant de la photo DSF dont les notes sont reprises


def rendu(plots: list[str], operateur: str) -> d.Paquet:
    """Tournée de départ complétée par Ramulus pour les placettes `plots` (A3 si toutes, A5 sinon)."""
    inst = tournee_depart()
    t = inst["tables"]
    t["tournee"]["lignes"][0].update(operateur=operateur, version_ramulus=VERSION_RAMULUS)
    for ligne in t["placettes"]["lignes"]:
        if ligne["plot_id"] in plots:
            ligne["etat"] = "notee"
    t["centres"]["lignes"] = numeroter([ligne_centre(p) for p in plots], COLONNES_CENTRES)
    photos: dict[str, bytes] = {}
    notes = []
    for arbre in ARBRES:
        if arbre[0] in plots:
            ligne, ph = ligne_note(arbre, "notations")
            SOURCES_DSF[ligne["uuid"]] = arbre[3]
            notes.append(ligne)
            photos.update(ph)
    t["notations"]["lignes"] = numeroter(notes, COLONNES_NOTES)
    inst["photos_du_paquet"] = [{"nom": n, "sha256": d.sha256(o)} for n, o in sorted(photos.items())]
    return d.Paquet(inst, photos)


def tournee_contre_notation(premier_rendu: d.Paquet) -> dict:
    """A2 : écrite par Nemeton pour le second opérateur ; centre du premier fourni en lecture (Q12)."""
    inst = tournee_depart()
    t = inst["tables"]
    inst["nature"] = "contre_notation"
    t["tournee"]["lignes"][0].update(code_tournee=CODE_CN, nature="contre_notation", prepare_le=PREPARE_LE_CN)
    gardees = [l for l in t["placettes"]["lignes"] if l["contre_notation"] == 1]
    t["placettes"]["lignes"] = [dict(l, fid=i) for i, l in enumerate(gardees, start=1)]
    ids = {l["plot_id"] for l in gardees}
    centres = [c for c in premier_rendu.inst["tables"]["centres"]["lignes"] if c["plot_id"] in ids]
    t["centres"]["lignes"] = [dict(c, fid=i) for i, c in enumerate(centres, start=1)]
    del t["notations"]
    t["contre_notations"] = table("attributes", COLONNES_NOTES, [], False)
    return inst


def rendu_contre_notation(depart_cn: dict) -> d.Paquet:
    inst = copy.deepcopy(depart_cn)
    t = inst["tables"]
    t["tournee"]["lignes"][0].update(operateur="operateur-C", version_ramulus=VERSION_RAMULUS)
    for ligne in t["placettes"]["lignes"]:
        ligne["etat"] = "notee"
    notes, photos = [], {}
    for arbre in CONTRE:
        ligne, ph = ligne_note(arbre, "contre_notations")
        SOURCES_DSF[ligne["uuid"]] = arbre[3]
        notes.append(ligne)
        photos.update(ph)
    t["contre_notations"]["lignes"] = numeroter(notes, COLONNES_NOTES)
    inst["photos_du_paquet"] = [{"nom": n, "sha256": d.sha256(o)} for n, o in sorted(photos.items())]
    return d.Paquet(inst, photos)


# --------------------------------------------------------------------------------------------------------------
# Les cas
# --------------------------------------------------------------------------------------------------------------

def _note(inst: dict, plot_id: str, rang: int, table_nom: str = "notations") -> dict:
    return next(l for l in inst["tables"][table_nom]["lignes"] if l["plot_id"] == plot_id and l["rang"] == rang)


def _centre(inst: dict, plot_id: str) -> dict:
    return next(l for l in inst["tables"]["centres"]["lignes"] if l["plot_id"] == plot_id)


def _variante(paquet: d.Paquet, modifier) -> d.Paquet:
    inst = copy.deepcopy(paquet.inst)
    photos = dict(paquet.photos) if paquet.photos is not None else None
    sortie = modifier(inst, photos)
    if sortie is not None:
        photos = sortie
    if photos is not None:
        inst["photos_du_paquet"] = [{"nom": n, "sha256": d.sha256(o)} for n, o in sorted(photos.items())]
    return d.Paquet(inst, photos)


def _renommer_colonne(t: dict, ancien: str, nouveau: str) -> None:
    t["colonnes"] = {(nouveau if k == ancien else k): v for k, v in t["colonnes"].items()}
    t["lignes"] = [{(nouveau if k == ancien else k): v for k, v in l.items()} for l in t["lignes"]]


def _supprimer_colonne(t: dict, nom: str) -> None:
    del t["colonnes"][nom]
    for l in t["lignes"]:
        del l[nom]


def _ajouter_colonne(t: dict, nom: str, typ: str, valeur=None) -> None:
    t["colonnes"][nom] = typ
    for l in t["lignes"]:
        l[nom] = valeur


def cas() -> list[dict]:
    """Chaque cas : dossier, fichiers {nom: Paquet}, et ce qu'un lecteur conforme doit en conclure."""
    SOURCES_DSF.clear()
    a1 = d.Paquet(tournee_depart())
    a3 = rendu(["P001", "P002", "P003"], "operateur-A")
    a2 = d.Paquet(tournee_contre_notation(a3))
    a4 = rendu_contre_notation(a2.inst)
    op1 = rendu(["P001"], "operateur-A")
    op2 = rendu(["P002", "P003"], "operateur-B")
    zip_a3, zip_a4 = f"{CODE}.zip", f"{CODE_CN}.zip"
    depart_a1 = "../A1-tournee-depart/deperis.gpkg"
    depart_a2 = "../A2-tournee-contre-notation/deperis.gpkg"

    def a6(inst, _):
        inst["version_schema"] = 2
        inst["tables"]["tournee"]["lignes"][0]["version_schema"] = 2
        _ajouter_colonne(inst["tables"]["tournee"], "champ_futur", "TEXT", "valeur d'une version future")
        _ajouter_colonne(inst["tables"]["notations"], "champ_futur", "REAL")
        inst["tables"]["table_future"] = table("attributes", {"fid": "INTEGER", "libelle": "TEXT"},
                                               [{"fid": 1, "libelle": "table d'une version future"}], False)

    def s1(inst, photos):
        doublon = copy.deepcopy(_note(inst, "P003", 2))
        doublon["uuid"] = uuid("2027-07-07T07:30:00.000Z", "notations-P003-2-doublon")
        doublon["date_notation"] = "2027-07-07T07:30:00.000Z"
        SOURCES_DSF[doublon["uuid"]] = SOURCES_DSF[_note(inst, "P003", 2)["uuid"]]
        lignes = inst["tables"]["notations"]["lignes"] + [doublon]
        inst["tables"]["notations"]["lignes"] = numeroter(lignes, COLONNES_NOTES)

    def s2(inst, _):
        _centre(inst, "P003")["geom"]["y"] += 45.0

    def r1a(inst, _):
        inst["tables"]["placettes"]["lignes"][0]["wgt"] = 13.0

    def r1b(inst, _):
        _renommer_colonne(inst["tables"]["placettes"], "stratum", "strate")

    def r1c(inst, _):
        _supprimer_colonne(inst["tables"]["placettes"], "wgt")

    def r1d(inst, _):
        _centre(inst, "P002")["geom"]["x"] += 2.0

    def r2(inst, _):
        n = _note(inst, "P001", 2)
        n.update(mb=3, second=1, classe="C")  # l'abaque donne D

    def r3a(inst, _):
        x, y = d.lonlat_vers_l93(45.3418, 2.0469)  # Mogadiscio (lon, lat) projeté en Lambert-93
        _centre(inst, "P003")["geom"] = {"x": round(x, 2), "y": round(y, 2)}

    def r3b(inst, _):
        g = _centre(inst, "P003")["geom"]
        g["x"], g["y"] = g["y"], g["x"]

    def r5(table_nom):
        def f(inst, _):
            inst["tables"][table_nom]["index_spatial"] = True
        return f

    def r6(inst, _):
        notes = [copy.deepcopy(l) for l in a3.inst["tables"]["notations"]["lignes"] if l["plot_id"] == "P002"]
        for l in notes:
            l["photos"] = []
        inst["tables"]["notations"] = table("attributes", COLONNES_NOTES, numeroter(notes, COLONNES_NOTES), False)

    def r7(inst, _):
        n = _note(inst, "P003", 1)
        n["essence"] = "Robinier"  # pas de fiche DEPERIS : hors des 19 (spec § 4)

    def r8a(inst, _):
        n = _note(inst, "P001", 4)
        n["mb"] = 6

    def r8b(inst, _):
        n = _note(inst, "P002", 1)
        n["critere"] = "MR"  # Sapin, résineux : MA attendu

    def r9(inst, _):
        _ajouter_colonne(inst["tables"]["centres"], "date", "DATETIME")

    def r10a(_inst, photos):
        del photos[sorted(photos)[0]]

    def r10b(_inst, photos):
        nom = sorted(photos)[0]
        photos[nom] = photos[nom] + b"\x00"

    def r10c(inst, photos):
        nom = f"photos/{_note(inst, 'P003', 1)['uuid']}_1.jpg"
        photos[nom] = jpeg(nom)

    def r11(inst, _):
        t = inst["tables"]["placettes"]
        t["geometrie"]["srs"] = {"organisation": "EPSG", "code": 4326}
        for l, (_pid, lon, lat, *_r) in zip(t["lignes"], PLACETTES):
            l["geom"] = {"x": lon, "y": lat}

    v = _variante
    return [
        dict(cas="A1", dossier="A1-tournee-depart", fichiers={"deperis.gpkg": a1}, lecteur="ramulus",
             attendu="accepte", motifs=[], source="ADR 0003 § 1-3 ; Q13",
             titre="Tournée de départ telle que Nemeton l'écrit : toutes les tables, centres et notations vides"),
        dict(cas="A2", dossier="A2-tournee-contre-notation", fichiers={"deperis.gpkg": a2}, lecteur="ramulus",
             attendu="accepte", motifs=[], source="ADR 0003 § 4 ; Q12",
             titre="Tournée de contre-notation : placette P002 seule, centre du premier opérateur en lecture, "
                   "contre_notations vide, aucune table notations ni colonne de note"),
        dict(cas="A3", dossier="A3-rendu", fichiers={zip_a3: a3}, lecteur="nemeton", depart=depart_a1,
             attendu="accepte", motifs=[], source="ADR 0003 § 5-6",
             titre="Paquet rendu : trois placettes notées par deux opérateurs, centres, notations, photos"),
        dict(cas="A4", dossier="A4-rendu-contre-notation", fichiers={zip_a4: a4}, lecteur="nemeton",
             depart=depart_a2, attendu="accepte", motifs=[], source="ADR 0003 § 4",
             titre="Contre-notation rendue : contre_notations remplie, centre fourni intact"),
        dict(cas="A5", dossier="A5-fusion-deux-operateurs",
             fichiers={f"{CODE}-1.zip": op1, f"{CODE}-2.zip": op2}, lecteur="nemeton", depart=depart_a1,
             attendu="accepte", motifs=[], source="ADR 0003 § 5 ; spec § 5, critère 4 ; Q4",
             titre="Deux paquets partiels (P001 ; P002 et P003) : leur union sur l'uuid redonne A3, "
                   "quel que soit l'ordre, et se rejoue sans doublon",
             fusion={"operateur": "operateur-A", "version_ramulus": VERSION_RAMULUS,
                     "resultat": f"../A3-rendu/{zip_a3}"}),
        dict(cas="A6", dossier="A6-version-suivante", fichiers={"deperis.gpkg": v(a1, a6)}, lecteur="ramulus",
             attendu="accepte", motifs=[], source="ADR 0003 § 2 ; Q1",
             titre="Version 2 avec une colonne et une table inconnues : lue en les ignorant"),
        dict(cas="S1", dossier="S1-doublon-arbre", fichiers={zip_a3: v(a3, s1)}, lecteur="nemeton",
             depart=depart_a1, attendu="signale", motifs=["doublon_arbre"], source="ADR 0003 § 5",
             titre="Deux notations du même arbre (P003, rang 2, repère 22) hors contre-notation"),
        dict(cas="S2", dossier="S2-centre-eloigne", fichiers={zip_a3: v(a3, s2)}, lecteur="nemeton",
             depart=depart_a1, attendu="signale", motifs=["centre_eloigne"], source="ADR 0004 (30 m, provisoire)",
             titre="Centre de P003 relevé à plus de 30 m du point prévu"),
        dict(cas="R1a", dossier="R1a-plan-valeur-modifiee", fichiers={zip_a3: v(a3, r1a)}, lecteur="nemeton",
             depart=depart_a1, attendu="refuse", motifs=["plan_modifie"], source="ADR 0003 § 6",
             titre="Colonne de plan changée : wgt de P001"),
        dict(cas="R1b", dossier="R1b-plan-colonne-renommee", fichiers={zip_a3: v(a3, r1b)}, lecteur="nemeton",
             depart=depart_a1, attendu="refuse", motifs=["plan_modifie"], source="ADR 0003 § 6",
             titre="Colonne de plan renommée : stratum devenue strate"),
        dict(cas="R1c", dossier="R1c-plan-colonne-supprimee", fichiers={zip_a3: v(a3, r1c)}, lecteur="nemeton",
             depart=depart_a1, attendu="refuse", motifs=["plan_modifie"], source="ADR 0003 § 6",
             titre="Colonne de plan supprimée : wgt"),
        dict(cas="R1d", dossier="R1d-centre-contre-notation-modifie", fichiers={zip_a4: v(a4, r1d)},
             lecteur="nemeton", depart=depart_a2, attendu="refuse", motifs=["plan_modifie"],
             source="ADR 0003 § 6 ; Q12",
             titre="Contre-notation dont le centre, fourni en lecture, revient déplacé de 2 m"),
        dict(cas="R2", dossier="R2-classe-differente-abaque", fichiers={zip_a3: v(a3, r2)}, lecteur="nemeton",
             depart=depart_a1, attendu="refuse", motifs=["classe_differente_abaque"], source="ADR 0003 § 6",
             titre="MB = 3, MR = 1, classe C rendue ; l'abaque donne D"),
        dict(cas="R3a", dossier="R3a-centre-mogadiscio", fichiers={zip_a3: v(a3, r3a)}, lecteur="nemeton",
             depart=depart_a1, attendu="refuse", motifs=["hors_france"], source="ADR 0004 ; spec § 5, critère 3",
             titre="Centre de P003 à Mogadiscio (45,3418° E, 2,0469° N), hors de l'emprise d'EPSG:2154"),
        dict(cas="R3b", dossier="R3b-centre-xy-inverses", fichiers={zip_a3: v(a3, r3b)}, lecteur="nemeton",
             depart=depart_a1, attendu="refuse", motifs=["hors_france"], source="spec § 5, critère 3",
             titre="Centre de P003 écrit avec X et Y inversés"),
        dict(cas="R5a", dossier="R5a-index-spatial-placettes", fichiers={"deperis.gpkg": v(a1, r5("placettes"))},
             lecteur="ramulus", attendu="refuse", motifs=["index_spatial"],
             source="ADR 0003 § 1 ; Groma, brief vers Nemeton, § 2.2",
             titre="Index spatial R*Tree sur placettes (SPATIAL_INDEX oublié)"),
        dict(cas="R5b", dossier="R5b-index-spatial-centres", fichiers={"deperis.gpkg": v(a1, r5("centres"))},
             lecteur="ramulus", attendu="refuse", motifs=["index_spatial"],
             source="ADR 0003 § 1 ; Groma, brief vers Nemeton, § 2.2",
             titre="Index spatial R*Tree sur centres (SPATIAL_INDEX oublié)"),
        dict(cas="R6", dossier="R6-contre-notation-avec-notations", fichiers={"deperis.gpkg": v(a2, r6)},
             lecteur="ramulus", attendu="refuse", motifs=["notations_dans_contre_notation"],
             source="ADR 0003 § 4", titre="Tournée de contre-notation qui porte les premières notes"),
        dict(cas="R7", dossier="R7-essence-hors-referentiel", fichiers={zip_a3: v(a3, r7)}, lecteur="nemeton",
             depart=depart_a1, attendu="refuse", motifs=["essence_hors_referentiel"], source="spec § 4 ; Q11",
             titre="Essence « Robinier », sans fiche DEPERIS"),
        dict(cas="R8a", dossier="R8a-note-hors-bornes", fichiers={zip_a3: v(a3, r8a)}, lecteur="nemeton",
             depart=depart_a1, attendu="refuse", motifs=["note_hors_bornes"], source="fiches DSF 2025, page 1",
             titre="MB = 6"),
        dict(cas="R8b", dossier="R8b-critere-incoherent", fichiers={zip_a3: v(a3, r8b)}, lecteur="nemeton",
             depart=depart_a1, attendu="refuse", motifs=["critere_incoherent"], source="fiches DSF 2025, page 1",
             titre="Sapin (résineux) noté au critère MR"),
        dict(cas="R9", dossier="R9-colonne-date", fichiers={"deperis.gpkg": v(a1, r9)}, lecteur="ramulus",
             attendu="refuse", motifs=["colonne_date"], source="Groma, brief vers Nemeton, § 2.1",
             titre="Colonne nommée date dans centres"),
        dict(cas="R10a", dossier="R10a-photo-absente", fichiers={zip_a3: v(a3, r10a)}, lecteur="nemeton",
             depart=depart_a1, attendu="refuse", motifs=["photo_absente"], source="ADR 0003 § 5 (proposé)",
             titre="Photo citée, absente du .zip"),
        dict(cas="R10b", dossier="R10b-empreinte-photo-differente", fichiers={zip_a3: v(a3, r10b)},
             lecteur="nemeton", depart=depart_a1, attendu="refuse", motifs=["empreinte_photo_differente"],
             source="ADR 0003 § 5 (proposé)", titre="Photo dont l'empreinte SHA-256 n'est pas celle citée"),
        dict(cas="R10c", dossier="R10c-photo-non-citee", fichiers={zip_a3: v(a3, r10c)}, lecteur="nemeton",
             depart=depart_a1, attendu="refuse", motifs=["photo_non_citee"], source="ADR 0003 § 5 (proposé)",
             titre="Photo du .zip citée par aucune notation"),
        dict(cas="R11", dossier="R11-repere-autre-que-2154", fichiers={"deperis.gpkg": v(a1, r11)},
             lecteur="ramulus", attendu="refuse", motifs=["hors_france", "repere_hors_2154"],
             source="ADR 0003 § 1 (repère lu, jamais supposé ; proposé)",
             titre="Placettes écrites en WGS 84 (EPSG:4326, degrés) au lieu d'EPSG:2154"),
    ]


# --------------------------------------------------------------------------------------------------------------
# Écriture
# --------------------------------------------------------------------------------------------------------------

def _source_dsf(ligne: dict) -> str | None:
    """Photo DSF dont la ligne reprend les notes telles quelles ; None si un cas les a modifiées."""
    ident = SOURCES_DSF.get(ligne["uuid"])
    if ident is None:
        return None
    p = DSF[ident]
    intacte = (ligne["essence"], ligne["mb"], ligne["second"], ligne["classe"]) == (
        p["essence"], p["mb"], p["second"], p["classe"]) and ligne["type"] == TYPES.get(p["essence"]) \
        and ligne["critere"] == CRITERE[ligne["type"]]
    return ident if intacte else None


def notes_attendues(paquet: d.Paquet, fichier: str) -> list[dict]:
    sortie = []
    for table_nom in d.TABLES_NOTES:
        for l in paquet.inst["tables"].get(table_nom, {}).get("lignes", []):
            sortie.append({
                "fichier": fichier, "table": table_nom, "uuid": l["uuid"], "plot_id": l["plot_id"],
                "rang": l["rang"], "repere": l["repere"], "essence": l["essence"], "type": l["type"],
                "mb": l["mb"], "second": l["second"], "critere": l["critere"], "classe": l["classe"],
                "photo_dsf": _source_dsf(l),
            })
    return sortie


def derniere_modif(paquet: d.Paquet) -> str:
    t = paquet.inst["tables"]["tournee"]["lignes"][0]
    if t["operateur"] is None:
        return t["prepare_le"]
    return RENDU_LE_CN if t["nature"] == "contre_notation" else RENDU_LE


def ecrire_json(chemin: Path, valeur) -> None:
    chemin.write_text(json.dumps(valeur, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")


def instantane_nom(fichier: str) -> str:
    return fichier.rsplit(".", 1)[0] + ".instantane.json"


def generer(dossier: Path = ICI, schema: bool = True) -> list[dict]:
    """Écrit les cas dans `dossier` (fixtures/echange par défaut) ; rend la liste des attendus."""
    if schema:
        d.SCHEMA.write_text(schema_a_jour(), encoding="utf-8")
    attendus = []
    for c in cas():
        cible = Path(dossier) / c["dossier"]
        if cible.exists():
            shutil.rmtree(cible)
        cible.mkdir(parents=True)
        notes = []
        for nom, paquet in c["fichiers"].items():
            d.ecrire_paquet(cible / nom, paquet.inst, derniere_modif(paquet), paquet.photos)
            relu = d.lire_paquet(cible / nom)
            ecrire_json(cible / instantane_nom(nom), relu.inst)
            notes += notes_attendues(paquet, nom)
        attendu = {
            "cas": c["cas"],
            "titre": c["titre"],
            "source": c["source"],
            "lecteur": c["lecteur"],
            "fichiers": list(c["fichiers"]),
            "depart": c.get("depart"),
            "attendu": c["attendu"],
            "motifs": sorted(c["motifs"]),
            "fusion": c.get("fusion"),
            "notes": notes,
        }
        ecrire_json(cible / "attendu.json", attendu)
        attendus.append(attendu)
    return attendus


if __name__ == "__main__":
    for a in generer():
        print(f"{a['cas']:5} {a['attendu']:8} {','.join(a['motifs']) or '-':32} {a['titre']}")
