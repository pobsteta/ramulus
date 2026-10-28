"""Lecture, écriture et contrôle de référence du fichier d'échange `deperis.gpkg`, version 1.

Contrat : docs/formats/deperis-v1.md ; schéma : docs/formats/deperis-v1.schema.json.

Ce module n'est pas le code de l'appareil (mission 004, Kotlin et sqlite-jdbc) ni celui de Nemeton (R) : il sert à
écrire les cas communs de fixtures/echange/ et à dire, pour chacun, ce qu'un lecteur conforme doit en conclure.
Il n'utilise que la bibliothèque standard de Python (sqlite3, OGC 12-128 écrit à la main) et `jsonschema`.

Coordonnées : EPSG:2154 (RGF93 v1 / Lambert-93), X = est, Y = nord, en mètres. Les longitudes et latitudes sont en
degrés décimaux, dans cet ordre (lon, lat), sur l'ellipsoïde GRS 1980 (RGF93 v1, confondu ici avec WGS 84).
"""

from __future__ import annotations

import copy
import hashlib
import io
import json
import math
import re
import sqlite3
import struct
import unicodedata
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

RACINE = Path(__file__).resolve().parents[2]
SCHEMA = RACINE / "docs" / "formats" / "deperis-v1.schema.json"

NOM_GPKG = "deperis.gpkg"
APPLICATION_ID_GPKG = 0x47504B47  # « GPKG », OGC 12-128 Req. 2
USER_VERSION_1_4 = 10400  # OGC 12-128r19 (1.4) ; GDAL écrit 10400 (exemple de Groma lu le 2026-10-28)

# Emprise d'EPSG:2154 (domaine d'emploi EPSG « France - onshore and offshore, mainland and Corsica »), en degrés,
# (ouest, sud, est, nord). Lue dans la base EPSG de PROJ par pyproj le 2026-10-28.
EMPRISE_2154_LONLAT = (-9.86, 41.15, 10.38, 51.56)

# Seuil d'écart entre le centre relevé et le point prévu au-delà duquel le centre est signalé (ADR 0004).
# Valeur PROVISOIRE proposée à Pascal, non tranchée.
SEUIL_CENTRE_ELOIGNE_M = 30.0

TABLES_NOTES = ("notations", "contre_notations")


# --------------------------------------------------------------------------------------------------------------
# Lambert-93 (EPSG:2154) : conique conforme de Lambert à deux parallèles (EPSG, méthode 9802)
# --------------------------------------------------------------------------------------------------------------

_A = 6378137.0
_F = 1 / 298.257222101
_E = math.sqrt(2 * _F - _F * _F)
_LAT0, _LAT1, _LAT2, _LON0 = map(math.radians, (46.5, 49.0, 44.0, 3.0))
_X0, _Y0 = 700000.0, 6600000.0


def _m(phi: float) -> float:
    return math.cos(phi) / math.sqrt(1 - (_E * math.sin(phi)) ** 2)


def _t(phi: float) -> float:
    es = _E * math.sin(phi)
    return math.tan(math.pi / 4 - phi / 2) / ((1 - es) / (1 + es)) ** (_E / 2)


_N = (math.log(_m(_LAT1)) - math.log(_m(_LAT2))) / (math.log(_t(_LAT1)) - math.log(_t(_LAT2)))
_FF = _m(_LAT1) / (_N * _t(_LAT1) ** _N)
_R0 = _A * _FF * _t(_LAT0) ** _N


def lonlat_vers_l93(lon: float, lat: float) -> tuple[float, float]:
    """(longitude, latitude) en degrés -> (X est, Y nord) en mètres, EPSG:2154."""
    r = _A * _FF * _t(math.radians(lat)) ** _N
    theta = _N * (math.radians(lon) - _LON0)
    return _X0 + r * math.sin(theta), _Y0 + _R0 - r * math.cos(theta)


def l93_vers_lonlat(x: float, y: float) -> tuple[float, float]:
    """(X est, Y nord) en mètres, EPSG:2154 -> (longitude, latitude) en degrés."""
    dx, dy = x - _X0, _R0 - (y - _Y0)
    r = math.copysign(math.hypot(dx, dy), _N)
    if r == 0:
        return math.degrees(_LON0), math.copysign(90.0, _N)
    t = (r / (_A * _FF)) ** (1 / _N)
    theta = math.atan2(dx, dy)
    lon = theta / _N + _LON0
    phi = math.pi / 2 - 2 * math.atan(t)
    for _ in range(20):
        es = _E * math.sin(phi)
        suivant = math.pi / 2 - 2 * math.atan(t * ((1 - es) / (1 + es)) ** (_E / 2))
        if abs(suivant - phi) < 1e-14:
            phi = suivant
            break
        phi = suivant
    return math.degrees(lon), math.degrees(phi)


def en_france(x: float, y: float) -> bool:
    """Vrai si le point (X est, Y nord, EPSG:2154) tombe dans l'emprise d'EPSG:2154 (décision de Pascal, Q3)."""
    if not (math.isfinite(x) and math.isfinite(y)):
        return False
    lon, lat = l93_vers_lonlat(x, y)
    ouest, sud, est, nord = EMPRISE_2154_LONLAT
    return ouest <= lon <= est and sud <= lat <= nord


def bornes_projetees_2154(pas: int = 400) -> tuple[float, float, float, float]:
    """Plus petit rectangle (xmin, ymin, xmax, ymax) en EPSG:2154 qui contient l'emprise, bords échantillonnés."""
    ouest, sud, est, nord = EMPRISE_2154_LONLAT
    pts = []
    for i in range(pas + 1):
        f = i / pas
        lon = ouest + f * (est - ouest)
        lat = sud + f * (nord - sud)
        pts += [lonlat_vers_l93(lon, sud), lonlat_vers_l93(lon, nord), lonlat_vers_l93(ouest, lat),
                lonlat_vers_l93(est, lat)]
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    return min(xs), min(ys), max(xs), max(ys)


def distance_m(a: dict, b: dict) -> float:
    return math.hypot(a["x"] - b["x"], a["y"] - b["y"])


# --------------------------------------------------------------------------------------------------------------
# Géométrie GeoPackage (OGC 12-128, § 2.1.3) : en-tête « GP » + WKB ; seuls les points sont utiles ici
# --------------------------------------------------------------------------------------------------------------

def point_vers_blob(x: float, y: float, srs_id: int) -> bytes:
    """Point 2D en petit-boutiste, sans enveloppe (comme GDAL l'écrit pour un point)."""
    return b"GP" + bytes([0, 0x01]) + struct.pack("<i", srs_id) + struct.pack("<BIdd", 1, 1, x, y)


def blob_vers_point(blob: bytes) -> dict | None:
    """Rend {"x", "y", "srs_id"} ; None pour une géométrie vide ; lève ValueError sinon."""
    if blob is None:
        return None
    if len(blob) < 8 or blob[:2] != b"GP":
        raise ValueError("géométrie sans en-tête GeoPackage")
    drapeaux = blob[3]
    bo = "<" if drapeaux & 0x01 else ">"
    srs_id = struct.unpack(bo + "i", blob[4:8])[0]
    taille_env = {0: 0, 1: 32, 2: 48, 3: 48, 4: 64}.get((drapeaux >> 1) & 0x07)
    if taille_env is None:
        raise ValueError("indicateur d'enveloppe invalide")
    if drapeaux & 0x10:
        return None
    wkb = blob[8 + taille_env:]
    wbo = "<" if wkb[0] == 1 else ">"
    type_geom = struct.unpack(wbo + "I", wkb[1:5])[0]
    if type_geom != 1:
        raise ValueError(f"géométrie de type WKB {type_geom}, POINT attendu")
    x, y = struct.unpack(wbo + "dd", wkb[5:21])
    if math.isnan(x) and math.isnan(y):
        return None
    return {"x": x, "y": y, "srs_id": srs_id}


# --------------------------------------------------------------------------------------------------------------
# Repères (gpkg_spatial_ref_sys)
# --------------------------------------------------------------------------------------------------------------

WKT_2154 = (
    'PROJCS["RGF93 v1 / Lambert-93",GEOGCS["RGF93 v1",DATUM["Reseau_Geodesique_Francais_1993_v1",'
    'SPHEROID["GRS 1980",6378137,298.257222101,AUTHORITY["EPSG","7019"]],AUTHORITY["EPSG","6171"]],'
    'PRIMEM["Greenwich",0,AUTHORITY["EPSG","8901"]],UNIT["degree",0.0174532925199433,AUTHORITY["EPSG","9122"]],'
    'AUTHORITY["EPSG","4171"]],PROJECTION["Lambert_Conformal_Conic_2SP"],PARAMETER["latitude_of_origin",46.5],'
    'PARAMETER["central_meridian",3],PARAMETER["standard_parallel_1",49],PARAMETER["standard_parallel_2",44],'
    'PARAMETER["false_easting",700000],PARAMETER["false_northing",6600000],UNIT["metre",1,AUTHORITY["EPSG","9001"]],'
    'AXIS["Easting",EAST],AXIS["Northing",NORTH],AUTHORITY["EPSG","2154"]]'
)
WKT_4326 = (
    'GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563,AUTHORITY["EPSG","7030"]],'
    'AUTHORITY["EPSG","6326"]],PRIMEM["Greenwich",0,AUTHORITY["EPSG","8901"]],'
    'UNIT["degree",0.0174532925199433,AUTHORITY["EPSG","9122"]],AXIS["Latitude",NORTH],AXIS["Longitude",EAST],'
    'AUTHORITY["EPSG","4326"]]'
)
# srs_id -> (srs_name, organization, organization_coordsys_id, definition) ; les trois premières lignes sont exigées
# par OGC 12-128 (Req. 11).
REPERES = {
    -1: ("Undefined Cartesian SRS", "NONE", -1, "undefined"),
    0: ("Undefined geographic SRS", "NONE", 0, "undefined"),
    4326: ("WGS 84 geodetic", "EPSG", 4326, WKT_4326),
    2154: ("RGF93 v1 / Lambert-93", "EPSG", 2154, WKT_2154),
}


# --------------------------------------------------------------------------------------------------------------
# Instantané : la projection JSON d'un GeoPackage que valide le schéma
# --------------------------------------------------------------------------------------------------------------

COLONNES_JSON = {"photos"}  # colonnes TEXT qui portent un texte JSON, lu tel quel dans l'instantané


def _q(nom: str) -> str:
    return '"' + nom.replace('"', '""') + '"'


def instantane(cnx: sqlite3.Connection, photos_du_paquet: list[dict] | None = None) -> dict:
    """Projette le GeoPackage ouvert en un objet JSON, à valider contre docs/formats/deperis-v1.schema.json."""
    lire = cnx.execute
    reperes = {
        r[0]: {"organisation": r[1], "code": r[2]}
        for r in lire("SELECT srs_id, organization, organization_coordsys_id FROM gpkg_spatial_ref_sys")
    }
    geometries = {
        r[0]: (r[1], r[2], r[3])
        for r in lire("SELECT table_name, column_name, geometry_type_name, srs_id FROM gpkg_geometry_columns")
    }
    try:
        rtree_ext = {r[0] for r in lire(
            "SELECT table_name FROM gpkg_extensions WHERE extension_name = 'gpkg_rtree_index'")}
    except sqlite3.OperationalError:
        rtree_ext = set()
    maitre = list(lire("SELECT type, name, tbl_name FROM sqlite_master"))
    tables = {}
    for nom, data_type in lire("SELECT table_name, data_type FROM gpkg_contents ORDER BY table_name"):
        info = list(lire(f"PRAGMA table_info({_q(nom)})"))
        colonnes = {c[1]: (c[2] or "").upper() for c in info}
        cle = [c[1] for c in info if c[5]]
        geo = geometries.get(nom)
        geometrie = None
        if geo is not None:
            geometrie = {"colonne": geo[0], "type": geo[1].upper(), "srs": reperes.get(geo[2])}
        prefixe = f"rtree_{nom}_"
        index = nom in rtree_ext or any(
            n.startswith(prefixe) and t in ("table", "trigger") for t, n, _ in maitre)
        lignes = []
        ordre = f" ORDER BY {_q(cle[0])}" if len(cle) == 1 else ""
        curseur = lire(f"SELECT * FROM {_q(nom)}{ordre}")
        noms = [d[0] for d in curseur.description]
        for valeurs in curseur:
            ligne = {}
            for k, v in zip(noms, valeurs):
                if geometrie is not None and k == geometrie["colonne"]:
                    try:
                        p = blob_vers_point(v)
                        v = None if p is None else {"x": p["x"], "y": p["y"]}
                    except ValueError as e:
                        v = f"géométrie illisible : {e}"
                elif k in COLONNES_JSON and isinstance(v, str):
                    try:
                        v = json.loads(v)
                    except json.JSONDecodeError:
                        pass  # gardé en texte : le schéma, qui attend un tableau, le refusera
                elif isinstance(v, bytes):
                    v = v.hex()
                ligne[k] = v
            lignes.append(ligne)
        tables[nom] = {
            "data_type": data_type,
            "geometrie": geometrie,
            "index_spatial": index,
            "cle_primaire": cle[0] if len(cle) == 1 else None,
            "colonnes": colonnes,
            "lignes": lignes,
        }
    tournee = tables.get("tournee", {}).get("lignes", [])
    resultat = {
        "application_id": lire("PRAGMA application_id").fetchone()[0],
        "user_version": lire("PRAGMA user_version").fetchone()[0],
        "version_schema": tournee[0].get("version_schema") if len(tournee) == 1 else None,
        "nature": tournee[0].get("nature") if len(tournee) == 1 else None,
        "tables": tables,
    }
    if photos_du_paquet is not None:
        resultat["photos_du_paquet"] = photos_du_paquet
    return resultat


# --------------------------------------------------------------------------------------------------------------
# Écriture d'un GeoPackage à partir d'un instantané (OGC 12-128, avec le module sqlite3 : M1, option b)
# --------------------------------------------------------------------------------------------------------------

def _ddl_noyau(cnx: sqlite3.Connection) -> None:
    cnx.executescript("""
        CREATE TABLE gpkg_spatial_ref_sys (
          srs_name TEXT NOT NULL, srs_id INTEGER NOT NULL PRIMARY KEY, organization TEXT NOT NULL,
          organization_coordsys_id INTEGER NOT NULL, definition TEXT NOT NULL, description TEXT);
        CREATE TABLE gpkg_contents (
          table_name TEXT NOT NULL PRIMARY KEY, data_type TEXT NOT NULL, identifier TEXT UNIQUE,
          description TEXT DEFAULT '', last_change DATETIME NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
          min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE, srs_id INTEGER,
          CONSTRAINT fk_gc_r_srs_id FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id));
        CREATE TABLE gpkg_geometry_columns (
          table_name TEXT NOT NULL, column_name TEXT NOT NULL, geometry_type_name TEXT NOT NULL,
          srs_id INTEGER NOT NULL, z TINYINT NOT NULL, m TINYINT NOT NULL,
          CONSTRAINT pk_geom_cols PRIMARY KEY (table_name, column_name),
          CONSTRAINT uk_gc_table_name UNIQUE (table_name),
          CONSTRAINT fk_gc_tn FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name),
          CONSTRAINT fk_gc_srs FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys (srs_id));
        CREATE TABLE gpkg_extensions (
          table_name TEXT, column_name TEXT, extension_name TEXT NOT NULL, definition TEXT NOT NULL,
          scope TEXT NOT NULL, CONSTRAINT ge_tce UNIQUE (table_name, column_name, extension_name));
    """)


def _code_vers_srs_id(srs: dict | None) -> int:
    if srs is None:
        return 0
    for srs_id, (_, org, code, _) in REPERES.items():
        if org == srs["organisation"] and code == srs["code"]:
            return srs_id
    raise ValueError(f"repère inconnu de l'écrivain : {srs}")


def ecrire_gpkg(chemin: Path, inst: dict, derniere_modif: str) -> None:
    """Écrit `inst` (même forme que `instantane()`) dans un GeoPackage neuf ; `derniere_modif` fixe last_change."""
    chemin = Path(chemin)
    if chemin.exists():
        chemin.unlink()
    cnx = sqlite3.connect(chemin)
    try:
        cnx.execute(f"PRAGMA application_id = {int(inst['application_id'])}")
        cnx.execute(f"PRAGMA user_version = {int(inst['user_version'])}")
        _ddl_noyau(cnx)
        utilises = {0, -1, 4326} | {
            _code_vers_srs_id(t["geometrie"]["srs"]) for t in inst["tables"].values() if t["geometrie"]}
        for srs_id in sorted(utilises):
            nom, org, code, wkt = REPERES[srs_id]
            cnx.execute("INSERT INTO gpkg_spatial_ref_sys VALUES (?,?,?,?,?,NULL)", (nom, srs_id, org, code, wkt))
        for nom, t in inst["tables"].items():
            _ecrire_table(cnx, nom, t, derniere_modif)
        cnx.commit()
        cnx.execute("VACUUM")
    finally:
        cnx.close()


def _ecrire_table(cnx: sqlite3.Connection, nom: str, t: dict, derniere_modif: str) -> None:
    geo = t["geometrie"]
    cle = t.get("cle_primaire")
    definitions = []
    for col, typ in t["colonnes"].items():
        if col == cle:
            definitions.append(f"{_q(col)} INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL")
        else:
            definitions.append(f"{_q(col)} {typ}")
    cnx.execute(f"CREATE TABLE {_q(nom)} ({', '.join(definitions)})")
    srs_id = _code_vers_srs_id(geo["srs"]) if geo else 0
    xs, ys = [], []
    for ligne in t["lignes"]:
        valeurs = []
        for col in t["colonnes"]:
            v = ligne.get(col)
            if geo and col == geo["colonne"] and v is not None:
                xs.append(v["x"])
                ys.append(v["y"])
                v = point_vers_blob(v["x"], v["y"], srs_id)
            elif col in COLONNES_JSON and isinstance(v, list):
                v = json.dumps(v, ensure_ascii=False, separators=(",", ":"))
            valeurs.append(v)
        place = ",".join("?" * len(valeurs))
        cnx.execute(f"INSERT INTO {_q(nom)} ({','.join(map(_q, t['colonnes']))}) VALUES ({place})", valeurs)
    bornes = (min(xs), min(ys), max(xs), max(ys)) if xs else (None, None, None, None)
    cnx.execute("INSERT INTO gpkg_contents VALUES (?,?,?,'',?,?,?,?,?,?)",
                (nom, t["data_type"], nom, derniere_modif, *bornes, srs_id))
    if geo:
        cnx.execute("INSERT INTO gpkg_geometry_columns VALUES (?,?,?,?,0,0)",
                    (nom, geo["colonne"], geo["type"], srs_id))
        if t.get("index_spatial"):
            _ajouter_rtree(cnx, nom, geo["colonne"], cle)


def _ajouter_rtree(cnx: sqlite3.Connection, table: str, col: str, cle: str) -> None:
    """Index spatial comme GDAL l'écrit (extension gpkg_rtree_index), pour les cas refusés R5 seulement.

    Les déclencheurs appellent ST_IsEmpty, ST_MinX… : c'est ce qui casse toute mise à jour par un SQLite qui ne
    les déclare pas (Groma, brief vers Nemeton, § 2.2). On les crée après avoir rempli l'index à la main.
    """
    rt = f"rtree_{table}_{col}"
    cnx.execute(f"CREATE VIRTUAL TABLE {_q(rt)} USING rtree(id, minx, maxx, miny, maxy)")
    for fid, blob in cnx.execute(f"SELECT {_q(cle)}, {_q(col)} FROM {_q(table)}").fetchall():
        p = blob_vers_point(blob)
        if p is not None:
            cnx.execute(f"INSERT INTO {_q(rt)} VALUES (?,?,?,?,?)", (fid, p["x"], p["x"], p["y"], p["y"]))
    cnx.execute("INSERT INTO gpkg_extensions VALUES (?,?,'gpkg_rtree_index',"
                "'http://www.geopackage.org/spec120/#extension_rtree','write-only')", (table, col))
    T, C, K, R = _q(table), _q(col), _q(cle), _q(rt)
    cnx.execute(
        f'CREATE TRIGGER {_q(rt + "_insert")} AFTER INSERT ON {T} WHEN (new.{C} NOT NULL AND NOT ST_IsEmpty(NEW.{C})) '
        f"BEGIN INSERT OR REPLACE INTO {R} VALUES (NEW.{K}, ST_MinX(NEW.{C}), ST_MaxX(NEW.{C}), "
        f"ST_MinY(NEW.{C}), ST_MaxY(NEW.{C})); END")
    cnx.execute(
        f'CREATE TRIGGER {_q(rt + "_update4")} AFTER UPDATE ON {T} WHEN OLD.{K} != NEW.{K} AND '
        f"(NEW.{C} ISNULL OR ST_IsEmpty(NEW.{C})) BEGIN DELETE FROM {R} WHERE id IN (OLD.{K}, NEW.{K}); END")
    cnx.execute(
        f'CREATE TRIGGER {_q(rt + "_delete")} AFTER DELETE ON {T} WHEN old.{C} NOT NULL '
        f"BEGIN DELETE FROM {R} WHERE id = OLD.{K}; END")


# --------------------------------------------------------------------------------------------------------------
# Paquets : un deperis.gpkg seul (tournée de départ) ou un .zip rendu (deperis.gpkg + photos/)
# --------------------------------------------------------------------------------------------------------------

def sha256(octets: bytes) -> str:
    return hashlib.sha256(octets).hexdigest()


@dataclass
class Paquet:
    """Un paquet lu : l'instantané de son GeoPackage et, pour un .zip, ses photos (nom -> octets)."""
    inst: dict
    photos: dict[str, bytes] | None = None


def _ouvrir_octets(octets: bytes, photos: dict[str, bytes] | None) -> Paquet:
    cnx = sqlite3.connect(":memory:")
    try:
        cnx.deserialize(octets)
        liste = None
        if photos is not None:
            liste = [{"nom": n, "sha256": sha256(o)} for n, o in sorted(photos.items())]
        return Paquet(instantane(cnx, liste), photos)
    finally:
        cnx.close()


def lire_paquet(chemin: Path) -> Paquet:
    """Lit un deperis.gpkg ou un .zip rendu, sans rien écrire sur le disque."""
    chemin = Path(chemin)
    if chemin.suffix == ".zip":
        with zipfile.ZipFile(chemin) as z:
            octets = z.read(NOM_GPKG)
            photos = {n: z.read(n) for n in z.namelist() if n.startswith("photos/") and not n.endswith("/")}
        return _ouvrir_octets(octets, photos)
    return _ouvrir_octets(chemin.read_bytes(), None)


def ecrire_paquet(chemin: Path, inst: dict, derniere_modif: str, photos: dict[str, bytes] | None = None) -> None:
    """Écrit un deperis.gpkg (photos None) ou un .zip rendu déterministe (dates des entrées fixées)."""
    chemin = Path(chemin)
    if photos is None:
        ecrire_gpkg(chemin, inst, derniere_modif)
        return
    temp = chemin.with_suffix(".tmp.gpkg")
    try:
        ecrire_gpkg(temp, inst, derniere_modif)
        octets_gpkg = temp.read_bytes()
    finally:
        temp.unlink(missing_ok=True)
    tampon = io.BytesIO()
    with zipfile.ZipFile(tampon, "w", zipfile.ZIP_DEFLATED) as z:
        for nom, octets in [(NOM_GPKG, octets_gpkg)] + sorted(photos.items()):
            info = zipfile.ZipInfo(nom, date_time=(2027, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, octets)
    chemin.write_bytes(tampon.getvalue())


# --------------------------------------------------------------------------------------------------------------
# Contrôle : ce que conclut un lecteur conforme (Ramulus à l'ouverture, Nemeton à l'import)
# --------------------------------------------------------------------------------------------------------------

def charger_schema() -> dict:
    return json.loads(SCHEMA.read_text(encoding="utf-8"))


def motifs_du_catalogue(schema: dict | None = None) -> dict[str, dict]:
    return (schema or charger_schema())["x-motifs"]


def erreurs_schema(inst: dict, schema: dict | None = None) -> list[tuple[str, str]]:
    """Valide l'instantané ; rend (motif, message) pour chaque erreur, motif lu dans le `x-motif` du sous-schéma."""
    import jsonschema

    schema = schema or charger_schema()
    validateur = jsonschema.Draft202012Validator(schema)
    sortie = []
    for e in validateur.iter_errors(inst):
        motif = e.schema.get("x-motif", "structure") if isinstance(e.schema, dict) else "structure"
        chemin = "/".join(str(p) for p in e.absolute_path)
        message = e.message if len(e.message) <= 200 else e.message[:200] + "…"
        sortie.append((motif, f"{chemin} : {message}"))
    return sorted(sortie)


@dataclass
class Resultat:
    statut: str  # « accepte », « signale » ou « refuse »
    motifs: list[str]
    messages: list[str] = field(default_factory=list)


def _lignes(inst: dict, table: str) -> list[dict]:
    t = inst["tables"].get(table)
    return t["lignes"] if isinstance(t, dict) else []


def _est_2154(inst: dict, table: str) -> bool:
    geo = inst["tables"].get(table, {}).get("geometrie")
    return bool(geo) and geo.get("srs") == {"organisation": "EPSG", "code": 2154}


def _point(v) -> bool:
    return isinstance(v, dict) and isinstance(v.get("x"), (int, float)) and isinstance(v.get("y"), (int, float))


def controler(paquet: Paquet, depart: Paquet | None = None, schema: dict | None = None) -> Resultat:
    """Contrôle un paquet ; `depart` est la tournée telle que Nemeton l'a écrite (import d'un rendu)."""
    schema = schema or charger_schema()
    catalogue = motifs_du_catalogue(schema)
    inst = paquet.inst
    motifs: set[str] = set()
    messages: list[str] = []

    def ajouter(motif: str, message: str) -> None:
        motifs.add(motif)
        messages.append(f"{motif} : {message}")

    for motif, message in erreurs_schema(inst, schema):
        ajouter(motif, message)

    # Placettes et centres en France : emprise d'EPSG:2154 (Q3), point par point, après projection inverse.
    hors_france: set[int] = set()
    for table in ("placettes", "centres"):
        if not _est_2154(inst, table):
            continue
        for i, ligne in enumerate(_lignes(inst, table)):
            g = ligne.get("geom")
            if _point(g) and not en_france(g["x"], g["y"]):
                ajouter("hors_france", f"{table}, ligne {i + 1} : X={g['x']} Y={g['y']} hors de l'emprise EPSG:2154")
                if table == "centres":
                    hors_france.add(i)

    # Unicité et rattachement.
    placettes = {}
    for ligne in _lignes(inst, "placettes"):
        pid = ligne.get("plot_id")
        if pid in placettes:
            ajouter("structure", f"placettes : plot_id {pid!r} en double")
        placettes[pid] = ligne
    for table in ("centres",) + TABLES_NOTES:
        vus = set()
        for ligne in _lignes(inst, table):
            u = ligne.get("uuid")
            if u in vus:
                ajouter("uuid_duplique", f"{table} : uuid {u} en double")
            vus.add(u)
            p = placettes.get(ligne.get("plot_id"))
            if p is None:
                ajouter("rattachement_invalide", f"{table} : plot_id {ligne.get('plot_id')!r} absent de placettes")
            elif table in TABLES_NOTES and isinstance(ligne.get("rang"), int) and isinstance(p.get("nb_arbres"), int) \
                    and ligne["rang"] > p["nb_arbres"]:
                ajouter("rattachement_invalide", f"{table} : rang {ligne['rang']} > nb_arbres {p['nb_arbres']} "
                                                 f"de {ligne['plot_id']}")

    # Signalés : deux notations du même arbre hors contre-notation (ADR 0003 § 5) ; centre loin du point prévu.
    arbres: dict[tuple, str] = {}
    for ligne in _lignes(inst, "notations"):
        for cle in (("rang", ligne.get("rang")), ("repere", ligne.get("repere"))):
            k = (ligne.get("plot_id"),) + cle
            if k in arbres and arbres[k] != ligne.get("uuid"):
                ajouter("doublon_arbre", f"notations : {k[0]} {k[1]} {k[2]} noté deux fois "
                                         f"({arbres[k]}, {ligne.get('uuid')})")
            arbres.setdefault(k, ligne.get("uuid"))
    if _est_2154(inst, "centres") and _est_2154(inst, "placettes"):
        for i, c in enumerate(_lignes(inst, "centres")):
            p = placettes.get(c.get("plot_id"))
            if i in hors_france or p is None or not (_point(c.get("geom")) and _point(p.get("geom"))):
                continue
            d = distance_m(c["geom"], p["geom"])
            if d > SEUIL_CENTRE_ELOIGNE_M:
                ajouter("centre_eloigne", f"centres : {c['plot_id']} à {d:.1f} m du point prévu "
                                          f"(seuil provisoire {SEUIL_CENTRE_ELOIGNE_M:.0f} m)")

    # Tous les textes en Unicode NFC (Pascal, 2026-10-28) : noms de tables et de colonnes, valeurs.
    for message in textes_non_nfc(inst):
        ajouter("texte_non_nfc", message)

    if paquet.photos is not None:
        _controler_photos(inst, paquet.photos, ajouter)
    if depart is not None:
        _controler_plan(inst, depart.inst, ajouter)

    inconnus = motifs - set(catalogue)
    if inconnus:
        raise AssertionError(f"motifs hors du catalogue du schéma : {sorted(inconnus)}")
    return Resultat(_statut(motifs, catalogue), sorted(motifs), messages)


def _non_nfc(texte: str) -> bool:
    return unicodedata.normalize("NFC", texte) != texte


def textes_non_nfc(inst: dict) -> list[str]:
    """Chaque texte de l'instantané qui n'est pas en forme NFC : nom de table, nom de colonne, valeur (y compris dans
    le texte JSON de `photos`). Rend un message par texte fautif, avec la table, la ligne et la colonne.

    Les tables système `gpkg_*` (et les `rtree_*`) ne sont pas contrôlées : l'instantané ne les porte pas, seules
    les tables de `gpkg_contents` y sont."""
    sortie = []

    def parcourir(v, ou: str) -> None:
        if isinstance(v, str):
            if _non_nfc(v):
                sortie.append(f"{ou} : {v!r} n'est pas en Unicode NFC")
        elif isinstance(v, list):
            for i, x in enumerate(v):
                parcourir(x, f"{ou}[{i}]")
        elif isinstance(v, dict):
            for k, x in v.items():
                parcourir(x, f"{ou}.{k}")

    for nom, t in inst.get("tables", {}).items():
        if _non_nfc(nom):
            sortie.append(f"nom de table {nom!r} n'est pas en Unicode NFC")
        if not isinstance(t, dict):
            continue
        for col in t.get("colonnes", {}):
            if _non_nfc(col):
                sortie.append(f"{nom} : nom de colonne {col!r} n'est pas en Unicode NFC")
        geo = (t.get("geometrie") or {}).get("colonne")
        for i, ligne in enumerate(t.get("lignes", [])):
            for col, v in ligne.items():
                if col != geo:
                    parcourir(v, f"{nom}, ligne {i + 1}, {col}")
    return sortie


def _controler_photos(inst: dict, photos: dict[str, bytes], ajouter) -> None:
    citees = {}
    for table in TABLES_NOTES:
        for ligne in _lignes(inst, table):
            liste = ligne.get("photos")
            if isinstance(liste, list):
                for p in liste:
                    if isinstance(p, dict):
                        citees[p.get("nom")] = p.get("sha256")
    for nom, empreinte in citees.items():
        if nom not in photos:
            ajouter("photo_absente", f"{nom} citée mais absente du paquet")
        elif sha256(photos[nom]) != empreinte:
            ajouter("empreinte_photo_differente", f"{nom} : SHA-256 {sha256(photos[nom])} au lieu de {empreinte}")
    for nom in photos:
        if nom not in citees:
            ajouter("photo_non_citee", f"{nom} présente dans le paquet mais citée par aucune notation")


# Ce que Ramulus peut modifier dans ce que Nemeton a écrit (ADR 0003 § 2-3).
ECRIT_PAR_RAMULUS = {"tournee": {"operateur", "version_ramulus"}, "placettes": {"etat"}}


def _controler_plan(inst: dict, depart: dict, ajouter) -> None:
    """Ce que Nemeton a écrit revient intact : placettes (sauf `etat`), champs Nemeton de `tournee`, et, dans une
    contre-notation, les centres fournis en lecture (Q12)."""
    a_comparer = ["tournee", "placettes"]
    if depart.get("nature") == "contre_notation":
        a_comparer.append("centres")
    for table in a_comparer:
        t0, t1 = depart["tables"].get(table), inst["tables"].get(table)
        if t0 is None or t1 is None:
            if t0 is not t1:
                ajouter("plan_modifie", f"{table} présente d'un côté seulement")
            continue
        libres = ECRIT_PAR_RAMULUS.get(table, set())
        c0 = {k: v for k, v in t0["colonnes"].items() if k not in libres}
        c1 = {k: v for k, v in t1["colonnes"].items() if k not in libres}
        if c0 != c1:
            manquantes = sorted(set(c0) - set(c1))
            nouvelles = sorted(set(c1) - set(c0))
            ajouter("plan_modifie", f"{table} : colonnes changées (disparues {manquantes}, apparues {nouvelles})")
            continue
        if t0["geometrie"] != t1["geometrie"]:
            ajouter("plan_modifie", f"{table} : géométrie ou repère changés")

        def cle(ligne):
            return ligne.get("plot_id") if table == "placettes" else ligne.get("uuid", ligne.get("fid"))

        l0 = {cle(l): {k: v for k, v in l.items() if k not in libres} for l in t0["lignes"]}
        l1 = {cle(l): {k: v for k, v in l.items() if k not in libres} for l in t1["lignes"]}
        if set(l0) != set(l1):
            ajouter("plan_modifie", f"{table} : lignes ajoutées ou retirées ({sorted(map(str, set(l0) ^ set(l1)))})")
        for k in sorted(set(l0) & set(l1), key=str):
            if l0[k] != l1[k]:
                ecarts = sorted(c for c in l0[k] if l0[k].get(c) != l1[k].get(c))
                ajouter("plan_modifie", f"{table} {k} : {ecarts} modifiés")


# --------------------------------------------------------------------------------------------------------------
# Fusion de paquets rendus : union sur l'uuid (ADR 0003 § 5), rejouable
# --------------------------------------------------------------------------------------------------------------

MOTIF_CONFLIT = "uuid_contenu_different"


@dataclass(frozen=True)
class Conflit:
    """Une version écartée de l'union : `objet` est une table (`centres`, `notations`, `contre_notations`) ou
    `photos` ; `cle` est l'uuid de la ligne ou le nom de la photo ; `message` dit pourquoi."""
    objet: str
    cle: str
    message: str


def fusionner(paquets: list[Paquet], operateur: str, version_ramulus: str) -> tuple[Paquet, list[Conflit]]:
    """Union sur l'`uuid` des centres et des notes, `etat` « notee » dès qu'un paquet l'a, photos réunies.

    `operateur` et `version_ramulus` sont ceux de qui rend (Q4) ; chaque ligne garde son propre `operateur`.
    Le résultat ne dépend pas de l'ordre des paquets, et fusionner un résultat avec l'un de ses paquets le redonne.

    Conflit, traité comme Surculus (`docs/formats/sursync-v1.md`, l. 63 : « les deux versions refusées, les autres
    restaurées » ; Pascal, 2026-10-28) : un même `uuid` avec deux contenus différents n'entre pas dans l'union, ni
    aucune de ses versions ; le reste de l'union est gardé, et chaque version écartée est rendue dans la liste des
    conflits (jamais en silence). Une photo appartient à la note qui la cite (`photos/<uuid>_<n>.jpg`, Q2) : les
    photos d'une note écartée sont écartées avec elle, et deux photos de même nom au contenu différent sont un
    conflit sur la note qui les cite, écartée avec toutes ses photos.
    """
    tables = [t for t in ("centres",) + TABLES_NOTES if t in paquets[0].inst["tables"]]
    versions: dict[str, dict[str, list[dict]]] = {t: {} for t in tables}
    for table in tables:
        for p in paquets:
            for ligne in _lignes(p.inst, table):
                contenu = {k: v for k, v in ligne.items() if k != "fid"}
                vus = versions[table].setdefault(contenu["uuid"], [])
                if contenu not in vus:
                    vus.append(contenu)
    photos_versions: dict[str, list[bytes]] = {}
    for p in paquets:
        for nom, octets in (p.photos or {}).items():
            vus = photos_versions.setdefault(nom, [])
            if octets not in vus:
                vus.append(octets)

    conflits: list[Conflit] = []
    ecartes: dict[str, set[str]] = {t: set() for t in tables}
    for table in tables:
        for u, vus in versions[table].items():
            if len(vus) > 1:
                ecarts = sorted({k for a in vus for b in vus for k in set(a) | set(b) if a.get(k) != b.get(k)})
                conflits.append(Conflit(table, u, f"{table} : uuid {u} porte {len(vus)} contenus différents "
                                                  f"(colonnes {ecarts}) ; aucune version gardée"))
                ecartes[table].add(u)
    photos_en_conflit = {nom for nom, vus in photos_versions.items() if len(vus) > 1}
    for nom in sorted(photos_en_conflit):
        conflits.append(Conflit("photos", nom, f"photo {nom} : {len(photos_versions[nom])} contenus différents ; "
                                               f"aucune version gardée, ni la note qui la cite"))

    def citees(contenu: dict) -> set[str]:
        liste = contenu.get("photos")
        return {ph.get("nom") for ph in liste if isinstance(ph, dict)} if isinstance(liste, list) else set()

    for table in TABLES_NOTES:
        for u, vus in versions.get(table, {}).items():
            noms = set().union(*(citees(c) for c in vus))
            if u not in ecartes[table] and noms & photos_en_conflit:
                ecartes[table].add(u)
                conflits.append(Conflit(table, u, f"{table} : uuid {u} écartée avec sa photo en conflit "
                                                  f"({sorted(noms & photos_en_conflit)})"))
    photos_ecartees = set(photos_en_conflit)
    for table in TABLES_NOTES:
        for u in ecartes.get(table, ()):
            for c in versions[table][u]:
                photos_ecartees |= citees(c)
    for nom in sorted(photos_ecartees - photos_en_conflit):
        if nom in photos_versions:
            conflits.append(Conflit("photos", nom, f"photo {nom} écartée avec la note qui la cite"))

    base = copy.deepcopy(paquets[0].inst)
    for table in tables:
        gardes = {u: vus[0] for u, vus in versions[table].items() if u not in ecartes[table]}
        cols = list(base["tables"][table]["colonnes"])
        base["tables"][table]["lignes"] = [{c: ({"fid": fid} | gardes[u]).get(c) for c in cols}
                                           for fid, u in enumerate(sorted(gardes), start=1)]
    notees = {l["plot_id"] for p in paquets for l in _lignes(p.inst, "placettes") if l.get("etat") == "notee"}
    for ligne in base["tables"]["placettes"]["lignes"]:
        ligne["etat"] = "notee" if ligne["plot_id"] in notees else None
    t = base["tables"]["tournee"]["lignes"][0]
    t["operateur"], t["version_ramulus"] = operateur, version_ramulus
    photos = {nom: vus[0] for nom, vus in sorted(photos_versions.items()) if nom not in photos_ecartees}
    base.pop("photos_du_paquet", None)
    base["photos_du_paquet"] = [{"nom": n, "sha256": sha256(o)} for n, o in sorted(photos.items())]
    conflits.sort(key=lambda c: (c.objet, c.cle))
    return Paquet(base, photos), conflits


def _statut(motifs: set[str] | list[str], catalogue: dict[str, dict]) -> str:
    if any(catalogue[m]["statut"] == "refuse" for m in motifs):
        return "refuse"
    return "signale" if motifs else "accepte"


def controler_fusion(paquets: list[Paquet], operateur: str, version_ramulus: str, depart: Paquet | None = None,
                     schema: dict | None = None) -> tuple[Resultat, Paquet, list[Conflit]]:
    """Fusionne des paquets rendus puis contrôle l'union, comme Nemeton à l'import de plusieurs paquets.

    Un conflit (même uuid, ou même photo, avec deux contenus) écarte les versions en conflit et garde le reste de
    l'union ; il est **signalé** (`uuid_contenu_different`), avec un message par version écartée. Le statut de
    l'union est celui de ses propres motifs, plus ce signalement.
    """
    schema = schema or charger_schema()
    fusion, conflits = fusionner(paquets, operateur, version_ramulus)
    r = controler(fusion, depart, schema)
    if conflits:
        motifs = set(r.motifs) | {MOTIF_CONFLIT}
        messages = r.messages + [f"{MOTIF_CONFLIT} : {c.message}" for c in conflits]
        r = Resultat(_statut(motifs, motifs_du_catalogue(schema)), sorted(motifs), messages)
    return r, fusion, conflits


# --------------------------------------------------------------------------------------------------------------
# Identifiants
# --------------------------------------------------------------------------------------------------------------

UUID7 = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")


def uuid7_deterministe(ms_unix: int, graine: int) -> str:
    """UUID v7 (RFC 9562) dont les bits « aléatoires » viennent de `graine` : stable d'une génération à l'autre."""
    alea = hashlib.sha256(f"ramulus-exemple-{graine}".encode()).digest()
    rand_a = int.from_bytes(alea[:2], "big") & 0x0FFF
    rand_b = int.from_bytes(alea[2:10], "big") & ((1 << 62) - 1)
    valeur = (ms_unix & ((1 << 48) - 1)) << 80 | 0x7 << 76 | rand_a << 64 | 0b10 << 62 | rand_b
    h = f"{valeur:032x}"
    return f"{h[:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:]}"
