"""Tests du format deperis-v1 : schéma, cas communs, contrôle de référence, fusion.

    python3 -m unittest discover -s fixtures/echange/tests -v

Coordonnées : EPSG:2154, X = est, Y = nord, en mètres ; (longitude, latitude) en degrés, dans cet ordre.
"""

from __future__ import annotations

import contextlib
import copy
import csv
import json
import sqlite3
import struct
import sys
import tempfile
import unicodedata
import unittest
import zipfile
from pathlib import Path

ICI = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ICI))

import deperis_v1 as d  # noqa: E402
import generer  # noqa: E402

SCHEMA = d.charger_schema()
REFERENTIELS = d.RACINE / "docs" / "referentiels" / "deperis"
DOC = d.RACINE / "docs" / "formats" / "deperis-v1.md"
FICHES = json.loads((d.RACINE / "fixtures" / "fiches-dsf-2025.json").read_text(encoding="utf-8"))


def attendus() -> list[tuple[Path, dict]]:
    return [(p.parent, json.loads(p.read_text(encoding="utf-8"))) for p in sorted(ICI.glob("*/attendu.json"))]


def attendu(cas: str) -> tuple[Path, dict]:
    return next((dossier, a) for dossier, a in attendus() if a["cas"] == cas)


def paquet(cas: str, i: int = 0) -> d.Paquet:
    dossier, a = attendu(cas)
    return d.lire_paquet(dossier / a["fichiers"][i])


def octets_gpkg(chemin: Path) -> bytes:
    if chemin.suffix == ".zip":
        with zipfile.ZipFile(chemin) as z:
            return z.read(d.NOM_GPKG)
    return chemin.read_bytes()


@contextlib.contextmanager
def connexion(chemin: Path):
    """Le GeoPackage du fichier (ou du .zip), ouvert en mémoire, et toujours refermé."""
    cnx = sqlite3.connect(":memory:")
    try:
        cnx.deserialize(octets_gpkg(chemin))
        yield cnx
    finally:
        cnx.close()


def motifs(inst: dict, depart: d.Paquet | None = None, photos=None) -> list[str]:
    return d.controler(d.Paquet(inst, photos), depart, SCHEMA).motifs


class Referentiels(unittest.TestCase):
    """Le schéma dit la même chose que docs/referentiels/deperis/ : rien n'est inventé ni recopié à la main."""

    def setUp(self):
        with open(REFERENTIELS / "essences.csv", encoding="utf-8", newline="") as f:
            self.essences = [(r["essence"], r["type"]) for r in csv.DictReader(f, delimiter=";")]
        with open(REFERENTIELS / "abaque.csv", encoding="utf-8", newline="") as f:
            self.abaque = {(int(r["mb"]), s): r[f"second_{s}"] for r in csv.DictReader(f, delimiter=";")
                           for s in range(6)}

    def test_19_essences_au_caractere_pres(self):
        enum = SCHEMA["$defs"]["essence"]["enum"]
        self.assertEqual(19, len(enum))
        self.assertEqual([e for e, _ in self.essences], enum)
        for e in enum:
            self.assertEqual(unicodedata.normalize("NFC", e), e, e)

    def test_type_de_chaque_essence(self):
        paires = [(b["properties"]["essence"]["const"], b["properties"]["type"]["const"])
                  for b in SCHEMA["$defs"]["essence_type"]["anyOf"]]
        self.assertEqual(self.essences, paires)

    def test_abaque_36_cases(self):
        cases = {(b["properties"]["mb"]["const"], b["properties"]["second"]["const"]): b["properties"]["classe"]["const"]
                 for b in SCHEMA["$defs"]["abaque"]["anyOf"]}
        self.assertEqual(36, len(cases))
        self.assertEqual(self.abaque, cases)

    def test_listes_generees_non_retouchees(self):
        self.assertEqual(generer.schema_a_jour(), d.SCHEMA.read_text(encoding="utf-8"))


class Catalogue(unittest.TestCase):

    def test_chaque_x_motif_est_au_catalogue_et_documente(self):
        utilises = set()

        def parcourir(x):
            if isinstance(x, dict):
                if "x-motif" in x:
                    utilises.add(x["x-motif"])
                for v in x.values():
                    parcourir(v)
            elif isinstance(x, list):
                for v in x:
                    parcourir(v)

        parcourir({k: v for k, v in SCHEMA.items() if k != "x-motifs"})
        catalogue = d.motifs_du_catalogue(SCHEMA)
        self.assertLessEqual(utilises, set(catalogue))
        doc = DOC.read_text(encoding="utf-8")
        for code, m in catalogue.items():
            self.assertIn(m["statut"], ("refuse", "signale"))
            self.assertIn(f"`{code}`", doc, f"motif {code} absent de {DOC.name}")

    def test_chaque_cas_du_readme(self):
        readme = (ICI / "README.md").read_text(encoding="utf-8")
        for dossier, a in attendus():
            self.assertIn(dossier.name, readme)


class Lambert93(unittest.TestCase):
    """EPSG:2154 : X = est, Y = nord, en mètres. Références calculées avec pyproj 3.8.0 (PROJ 9.8.1) le 2026-10-28."""

    def test_origine(self):
        x, y = d.lonlat_vers_l93(3.0, 46.5)
        self.assertAlmostEqual(700000.0, x, places=6)
        self.assertAlmostEqual(6600000.0, y, places=6)

    def test_paris_au_millimetre(self):
        x, y = d.lonlat_vers_l93(2.3522, 48.8566)  # (lon, lat) en degrés
        self.assertAlmostEqual(652469.0227, x, delta=0.001)
        self.assertAlmostEqual(6862035.2594, y, delta=0.001)

    def test_aller_retour(self):
        for lon, lat in [(5.065, 47.115), (-4.5, 48.4), (9.4, 42.0), (7.7, 48.6), (-1.7, 43.4)]:
            x, y = d.lonlat_vers_l93(lon, lat)
            lon2, lat2 = d.l93_vers_lonlat(x, y)
            self.assertAlmostEqual(lon, lon2, places=9)
            self.assertAlmostEqual(lat, lat2, places=9)

    def test_une_placette_francaise_tombe_en_france(self):
        for pid, lon, lat, *_ in generer.PLACETTES:
            p = generer.point_prevu(pid)
            self.assertTrue(d.en_france(p["x"], p["y"]), pid)
            lon2, lat2 = d.l93_vers_lonlat(p["x"], p["y"])
            self.assertAlmostEqual(lon, lon2, places=6)  # au centimètre près (arrondi des X, Y)
            self.assertAlmostEqual(lat, lat2, places=6)
            # Côte-d'Or : entre Dijon et Beaune, à l'est de 4,9° et au nord de 47,0°.
            self.assertTrue(4.9 < lon2 < 5.3 and 47.0 < lat2 < 47.3)

    def test_mogadiscio_et_les_erreurs_d_ordre_tombent_hors_de_france(self):
        x, y = d.lonlat_vers_l93(45.3418, 2.0469)
        self.assertFalse(d.en_france(x, y))
        p = generer.point_prevu("P001")
        self.assertFalse(d.en_france(p["y"], p["x"]), "X et Y inversés")
        self.assertFalse(d.en_france(5.065, 47.115), "degrés écrits à la place des mètres")
        self.assertFalse(d.en_france(47.115, 5.065), "degrés inversés")
        self.assertFalse(d.en_france(float("nan"), 6.6e6))

    def test_corse_et_brest_dans_l_emprise(self):
        self.assertTrue(d.en_france(*d.lonlat_vers_l93(9.15, 42.0)))  # Corse
        self.assertTrue(d.en_france(*d.lonlat_vers_l93(-4.49, 48.39)))  # Brest

    def test_bornes_du_schema(self):
        xmin, ymin, xmax, ymax = d.bornes_projetees_2154()
        p = SCHEMA["$defs"]["point_l93"]["properties"]
        self.assertLessEqual(p["x"]["minimum"], xmin)
        self.assertGreaterEqual(p["x"]["maximum"], xmax)
        self.assertLessEqual(p["y"]["minimum"], ymin)
        self.assertGreaterEqual(p["y"]["maximum"], ymax)
        for borne, calcul in [(p["x"]["minimum"], xmin), (p["x"]["maximum"], xmax), (p["y"]["minimum"], ymin),
                              (p["y"]["maximum"], ymax)]:
            self.assertLess(abs(borne - calcul), 1.0)


class Geometrie(unittest.TestCase):

    def test_aller_retour_point(self):
        b = d.point_vers_blob(856563.43, 6670349.24, 2154)
        self.assertEqual(b"GP", b[:2])
        self.assertEqual({"x": 856563.43, "y": 6670349.24, "srs_id": 2154}, d.blob_vers_point(b))

    def test_gros_boutiste_avec_enveloppe(self):
        x, y = 700000.0, 6600000.0
        blob = b"GP" + bytes([0, 0b0010]) + struct.pack(">i", 2154) + struct.pack(">4d", x, x, y, y) \
            + struct.pack(">BIdd", 0, 1, x, y)
        self.assertEqual({"x": x, "y": y, "srs_id": 2154}, d.blob_vers_point(blob))

    def test_point_vide(self):
        blob = b"GP" + bytes([0, 0x11]) + struct.pack("<i", 2154) + struct.pack("<BIdd", 1, 1, float("nan"),
                                                                                float("nan"))
        self.assertIsNone(d.blob_vers_point(blob))

    def test_pas_un_point(self):
        with self.assertRaises(ValueError):
            d.blob_vers_point(b"GP" + bytes([0, 1]) + struct.pack("<i", 2154) + struct.pack("<BI", 1, 3))
        with self.assertRaises(ValueError):
            d.blob_vers_point(b"XX\x00\x01\x00\x00\x00\x00")


class Identifiants(unittest.TestCase):

    def test_uuid7(self):
        a = d.uuid7_deterministe(1_783_000_000_000, "a")
        b = d.uuid7_deterministe(1_783_000_000_001, "a")
        self.assertRegex(a, d.UUID7)
        self.assertLess(a, b, "un UUID v7 suit l'ordre du temps")
        self.assertEqual(a, d.uuid7_deterministe(1_783_000_000_000, "a"))
        self.assertEqual(f"{1_783_000_000_000:012x}", a.replace("-", "")[:12])


class Cas(unittest.TestCase):
    """Chaque cas commun donne, au contrôle de référence, le résultat et les motifs de son attendu.json."""

    def test_il_y_a_tous_les_cas_du_plan(self):
        ids = {a["cas"] for _, a in attendus()}
        attendus_plan = {"A1", "A2", "A3", "A4", "A5", "A6", "S1", "S2", "R1a", "R1b", "R1c", "R1d", "R2", "R3a",
                         "R3b", "R5a", "R5b", "R6", "R7", "R8a", "R8b", "R9", "R10a", "R10b", "R10c", "R11", "R12",
                         "R13"}
        self.assertEqual(attendus_plan, ids)

    def test_resultat_et_motifs(self):
        """Sans fusion : chaque fichier conclut comme attendu. Avec fusion : chaque fichier seul conclut comme
        `fusion.chaque_fichier`, et leur union comme attendu."""
        for dossier, a in attendus():
            depart = d.lire_paquet(dossier / a["depart"]) if a["depart"] else None
            fusion = a["fusion"]
            seul = (fusion["chaque_fichier"], []) if fusion else (a["attendu"], a["motifs"])
            for f in a["fichiers"]:
                with self.subTest(cas=a["cas"], fichier=f):
                    r = d.controler(d.lire_paquet(dossier / f), depart, SCHEMA)
                    self.assertEqual(seul, (r.statut, r.motifs), "\n".join(r.messages))
            if fusion:
                with self.subTest(cas=a["cas"], fusion=True):
                    paquets = [d.lire_paquet(dossier / f) for f in a["fichiers"]]
                    r, _ = d.controler_fusion(paquets, fusion["operateur"], fusion["version_ramulus"], depart, SCHEMA)
                    self.assertEqual((a["attendu"], a["motifs"]), (r.statut, r.motifs), "\n".join(r.messages))

    def test_acceptes_et_signales_valides_contre_le_schema(self):
        for dossier, a in attendus():
            if a["attendu"] == "refuse" and not a["fusion"]:
                continue
            for f in a["fichiers"]:
                with self.subTest(cas=a["cas"]):
                    self.assertEqual([], d.erreurs_schema(d.lire_paquet(dossier / f).inst, SCHEMA))

    def test_chaque_refus_dit_pourquoi(self):
        for _, a in attendus():
            if a["attendu"] == "refuse":
                self.assertTrue(a["motifs"], a["cas"])
                cat = d.motifs_du_catalogue(SCHEMA)
                self.assertTrue(any(cat[m]["statut"] == "refuse" for m in a["motifs"]), a["cas"])

    def test_instantane_depose_egal_au_fichier(self):
        for dossier, a in attendus():
            for f in a["fichiers"]:
                with self.subTest(cas=a["cas"], fichier=f):
                    depose = json.loads((dossier / generer.instantane_nom(f)).read_text(encoding="utf-8"))
                    self.assertEqual(depose, d.lire_paquet(dossier / f).inst)

    def test_regeneration_identique_par_le_contenu(self):
        """Comparaison par le contenu, jamais par l'empreinte du fichier (Surculus, ADR 0011)."""
        with tempfile.TemporaryDirectory() as tmp:
            nouveaux = generer.generer(Path(tmp), schema=False)
            for a in nouveaux:
                dossier = next(dd for dd, aa in attendus() if aa["cas"] == a["cas"])
                self.assertEqual(json.loads((dossier / "attendu.json").read_text(encoding="utf-8")), a)
                for f in a["fichiers"]:
                    with self.subTest(cas=a["cas"], fichier=f):
                        self.assertEqual(d.lire_paquet(dossier / f).inst,
                                         d.lire_paquet(Path(tmp) / dossier.name / f).inst)
                        if f.endswith(".zip"):
                            self.assertEqual(d.lire_paquet(dossier / f).photos,
                                             d.lire_paquet(Path(tmp) / dossier.name / f).photos)

    def test_attendu_decrit_les_notes_du_fichier(self):
        for dossier, a in attendus():
            lignes = []
            for f in a["fichiers"]:
                inst = d.lire_paquet(dossier / f).inst
                for t in d.TABLES_NOTES:
                    for l in inst["tables"].get(t, {}).get("lignes", []):
                        lignes.append((f, t, l["uuid"], l["essence"], l["mb"], l["second"], l["classe"]))
            notes = [(n["fichier"], n["table"], n["uuid"], n["essence"], n["mb"], n["second"], n["classe"])
                     for n in a["notes"]]
            self.assertEqual(sorted(lignes), sorted(notes), a["cas"])

    def test_notes_reprises_des_fiches_dsf_hors_incoherences(self):
        photos = {p["id"]: p for p in FICHES["photos"]}
        for _, a in attendus():
            if a["attendu"] == "refuse":
                continue
            for n in a["notes"]:
                with self.subTest(cas=a["cas"], uuid=n["uuid"]):
                    self.assertIsNotNone(n["photo_dsf"])
                    self.assertNotIn(n["photo_dsf"], FICHES["incoherences_connues"])
                    p = photos[n["photo_dsf"]]
                    self.assertEqual((p["essence"], p["mb"], p["second"], p["classe"]),
                                     (n["essence"], n["mb"], n["second"], n["classe"]))


class GeoPackage(unittest.TestCase):
    """Le fichier est un GeoPackage (OGC 12-128) tel que l'ADR 0003 le veut."""

    def fichiers(self):
        for dossier, a in attendus():
            for f in a["fichiers"]:
                yield a, dossier / f

    def test_en_tete_et_integrite(self):
        for a, chemin in self.fichiers():
            with self.subTest(cas=a["cas"], fichier=chemin.name):
                cnx = self.enterContext(connexion(chemin))
                self.assertEqual(d.APPLICATION_ID_GPKG, cnx.execute("PRAGMA application_id").fetchone()[0])
                self.assertEqual(10400, cnx.execute("PRAGMA user_version").fetchone()[0])
                self.assertEqual("ok", cnx.execute("PRAGMA integrity_check").fetchone()[0])
                self.assertEqual([], cnx.execute("PRAGMA foreign_key_check").fetchall())
                srs = {r[0] for r in cnx.execute("SELECT srs_id FROM gpkg_spatial_ref_sys")}
                self.assertLessEqual({-1, 0, 4326}, srs)
                for (t,) in cnx.execute("SELECT table_name FROM gpkg_contents"):
                    cle = [c[1] for c in cnx.execute(f'PRAGMA table_info("{t}")') if c[5]]
                    self.assertEqual(["fid"], cle, t)

    def test_tables_de_notes_sans_geometrie(self):
        for a, chemin in self.fichiers():
            cnx = self.enterContext(connexion(chemin))
            for t in d.TABLES_NOTES:
                ligne = cnx.execute("SELECT data_type FROM gpkg_contents WHERE table_name = ?", (t,)).fetchone()
                if ligne is None:
                    continue
                self.assertEqual("attributes", ligne[0], (a["cas"], t))
                self.assertIsNone(cnx.execute("SELECT 1 FROM gpkg_geometry_columns WHERE table_name = ?",
                                              (t,)).fetchone())

    def test_repere_2154_lu_dans_gpkg_spatial_ref_sys(self):
        _, a1 = attendu("A1")
        cnx = self.enterContext(connexion(attendu("A1")[0] / "deperis.gpkg"))
        for t in ("placettes", "centres"):
            org, code = cnx.execute(
                "SELECT s.organization, s.organization_coordsys_id FROM gpkg_geometry_columns g "
                "JOIN gpkg_spatial_ref_sys s USING (srs_id) WHERE g.table_name = ?", (t,)).fetchone()
            self.assertEqual(("EPSG", 2154), (org, code))

    def test_sans_index_spatial_une_mise_a_jour_passe_sans_fonction_spatiale(self):
        """SPATIAL_INDEX=NO : un SQLite sans ST_IsEmpty (celui d'Android) peut écrire etat (Groma § 2.2)."""
        for cas in ("A1", "A2", "A6"):
            dossier, a = attendu(cas)
            cnx = self.enterContext(connexion(dossier / a["fichiers"][0]))
            maitre = [r[0] for r in cnx.execute("SELECT name FROM sqlite_master")]
            self.assertFalse([n for n in maitre if n.startswith(("rtree_placettes", "rtree_centres"))], cas)
            cnx.execute("UPDATE placettes SET etat = 'notee'")
            cnx.execute("UPDATE centres SET operateur = 'x'")

    def test_avec_index_spatial_la_meme_mise_a_jour_echoue(self):
        """Ce que les cas R5 refusent : le déclencheur R*Tree appelle ST_IsEmpty, inconnue de ce SQLite."""
        for cas, table in (("R5a", "placettes"), ("R5b", "centres")):
            dossier, a = attendu(cas)
            cnx = self.enterContext(connexion(dossier / a["fichiers"][0]))
            with self.assertRaisesRegex(sqlite3.OperationalError, "ST_IsEmpty"):
                cnx.execute(f"UPDATE {table} SET fid = fid + 1000")


class ContreNotation(unittest.TestCase):
    """La tournée de contre-notation ne contient aucune première note (ADR 0003 § 4, spec § 5, critère 5)."""

    def setUp(self):
        self.dossier, self.a = attendu("A2")
        self.cnx = self.enterContext(connexion(self.dossier / "deperis.gpkg"))

    def test_aucune_table_notations(self):
        noms = {r[0] for r in self.cnx.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}
        self.assertNotIn("notations", noms)
        self.assertIn("contre_notations", noms)
        self.assertEqual(0, self.cnx.execute("SELECT count(*) FROM contre_notations").fetchone()[0])

    def test_aucune_colonne_de_note_hors_contre_notations(self):
        for (t,) in self.cnx.execute("SELECT name FROM sqlite_master WHERE type = 'table'"):
            if t == "contre_notations":
                continue
            cols = {c[1] for c in self.cnx.execute(f'PRAGMA table_info("{t}")')}
            self.assertFalse(cols & {"mb", "second", "classe", "critere"}, t)

    def test_aucune_photo(self):
        self.assertEqual(["deperis.gpkg"], self.a["fichiers"])

    def test_seules_les_placettes_marquees(self):
        self.assertEqual([("P002", 1)], self.cnx.execute("SELECT plot_id, contre_notation FROM placettes").fetchall())

    def test_centre_du_premier_operateur_fourni(self):
        a3 = paquet("A3").inst["tables"]["centres"]["lignes"]
        a2 = paquet("A2").inst["tables"]["centres"]["lignes"]
        attendu_p2 = [dict(c, fid=1) for c in a3 if c["plot_id"] == "P002"]
        self.assertEqual(attendu_p2, a2)

    def test_une_colonne_de_note_dans_placettes_est_refusee(self):
        inst = copy.deepcopy(paquet("A2").inst)
        generer._ajouter_colonne(inst["tables"]["placettes"], "classe", "TEXT", "D")
        self.assertEqual(["notations_dans_contre_notation"], motifs(inst))

    def test_une_table_inconnue_avec_mb_est_refusee(self):
        inst = copy.deepcopy(paquet("A2").inst)
        inst["tables"]["copie"] = generer.table("attributes", {"fid": "INTEGER", "mb": "INTEGER"},
                                                [{"fid": 1, "mb": 2}], False)
        self.assertEqual(["notations_dans_contre_notation"], motifs(inst))


class VersionSuivante(unittest.TestCase):
    """Un lecteur v1 lit N+1 en ignorant les champs nouveaux (ADR 0003 § 2 ; Q1)."""

    def test_lue_en_ignorant_et_sans_perdre(self):
        inst = paquet("A6").inst
        self.assertEqual(2, inst["version_schema"])
        self.assertEqual([], d.erreurs_schema(inst, SCHEMA))
        self.assertIn("table_future", inst["tables"])
        self.assertIn("champ_futur", inst["tables"]["notations"]["colonnes"])

    def test_version_illisible(self):
        for valeur in (0, -1, "1", 1.5, None):
            inst = copy.deepcopy(paquet("A1").inst)
            inst["tables"]["tournee"]["lignes"][0]["version_schema"] = valeur
            inst["version_schema"] = valeur
            with self.subTest(valeur=valeur):
                self.assertIn("version_illisible", motifs(inst))

    def test_tournee_sans_ligne(self):
        inst = copy.deepcopy(paquet("A1").inst)
        inst["tables"]["tournee"]["lignes"] = []
        inst["version_schema"] = inst["nature"] = None
        self.assertIn("version_illisible", motifs(inst))

    def test_valeur_nouvelle_d_un_champ_connu_refusee(self):
        """Pascal, 2026-10-28 : dans un fichier N+1, une valeur nouvelle d'une colonne connue est refusée ; étendre
        une liste demande donc une version majeure."""
        inst = copy.deepcopy(paquet("A6").inst)
        inst["tables"]["placettes"]["lignes"][0]["saison"] = "PRINTEMPS"
        self.assertEqual(["structure"], motifs(inst))

    def test_valeur_nouvelle_dans_une_table_connue_d_un_rendu_n_plus_1(self):
        inst = copy.deepcopy(paquet("A3").inst)
        inst["version_schema"] = 2
        inst["tables"]["tournee"]["lignes"][0]["version_schema"] = 2
        next(l for l in inst["tables"]["centres"]["lignes"] if l["plot_id"] == "P001")["source"] = "gnss_externe"
        self.assertEqual(["structure"], motifs(inst))


class Controles(unittest.TestCase):
    """Les contrôles un à un, sur des copies de A3 modifiées à la main."""

    def setUp(self):
        self.a3 = paquet("A3")
        self.a1 = paquet("A1")
        self.inst = copy.deepcopy(self.a3.inst)

    def note(self, plot_id="P001", rang=1):
        return next(l for l in self.inst["tables"]["notations"]["lignes"]
                    if l["plot_id"] == plot_id and l["rang"] == rang)

    def test_a3_accepte(self):
        r = d.controler(self.a3, self.a1, SCHEMA)
        self.assertEqual(("accepte", []), (r.statut, r.motifs))

    def test_toutes_les_cases_de_l_abaque(self):
        for (mb, s), c in generer.lire_abaque().items():
            n = self.note()
            n.update(mb=mb, second=s, classe=c)
            self.assertEqual([], motifs(self.inst), (mb, s, c))
            n["classe"] = "A" if c != "A" else "B"
            self.assertEqual(["classe_differente_abaque"], motifs(self.inst), (mb, s))

    def test_type_incoherent(self):
        n = self.note("P002", 1)  # Sapin
        n.update(type="FEUILLU", critere="MR")
        self.assertEqual(["type_incoherent"], motifs(self.inst))

    def test_ia_toute_vide_ou_toute_remplie(self):
        n = self.note()
        n["ia_mb"] = 2
        self.assertEqual(["ia_incomplete"], motifs(self.inst))
        n.update(ia_premiere_mb=2, ia_premiere_second=3, ia_mb=2, ia_second=2, ia_confiance=0.7,
                 ia_modele="exemple-0")
        self.assertEqual([], motifs(self.inst))

    def test_horodatage_sans_z_refuse(self):
        self.note()["date_notation"] = "2027-07-06T08:10:00"
        self.assertEqual(["structure"], motifs(self.inst))

    def test_photos_illisibles(self):
        self.note()["photos"] = "pas du JSON"
        self.assertEqual(["structure"], motifs(self.inst))

    def test_uuid_duplique(self):
        self.note("P001", 2)["uuid"] = self.note("P001", 1)["uuid"]
        self.assertIn("uuid_duplique", motifs(self.inst))

    def test_rattachement(self):
        self.note()["plot_id"] = "P999"
        self.assertEqual(["rattachement_invalide"], motifs(self.inst))
        self.inst = copy.deepcopy(self.a3.inst)
        self.note()["rang"] = 5  # nb_arbres = 4
        self.assertEqual(["rattachement_invalide"], motifs(self.inst))

    def test_doublon_par_le_repere(self):
        """Même arbre : même plot_id et même rang, ou même plot_id et même repere (Pascal, 2026-10-28)."""
        self.note("P001", 2)["repere"] = self.note("P001", 1)["repere"]
        self.assertEqual(["doublon_arbre"], motifs(self.inst))

    def test_meme_rang_ou_meme_repere_sur_deux_placettes_n_est_pas_un_doublon(self):
        self.note("P003", 1)["repere"] = self.note("P001", 1)["repere"]  # rang 1 des deux côtés, repère « 1 »
        self.assertEqual([], motifs(self.inst))

    def test_texte_non_nfc_dans_une_valeur(self):
        """Tous les textes en Unicode NFC (Pascal, 2026-10-28), vérifiés partout, pas seulement sur l'essence."""
        self.note()["operateur"] = unicodedata.normalize("NFD", "opérateur-A")
        self.assertEqual(["texte_non_nfc"], motifs(self.inst))

    def test_essence_non_nfc_porte_les_deux_motifs(self):
        n = self.note("P002", 2)  # Mélèze
        n["essence"] = unicodedata.normalize("NFD", n["essence"])
        self.assertNotEqual("Mélèze", n["essence"])
        self.assertEqual(["essence_hors_referentiel", "texte_non_nfc"], motifs(self.inst))

    def test_nom_de_colonne_et_texte_json_non_nfc(self):
        self.inst["tables"]["autre"] = generer.table(
            "attributes", {"fid": "INTEGER", unicodedata.normalize("NFD", "remarqué"): "TEXT"}, [], False)
        self.assertEqual(["texte_non_nfc"], motifs(self.inst))
        self.inst = copy.deepcopy(self.a3.inst)
        self.note()["photos"][0]["nom"] = unicodedata.normalize("NFD", "photos/é.jpg")
        self.assertIn("texte_non_nfc", motifs(self.inst))

    def test_textes_accentues_en_nfc_acceptes(self):
        self.note()["repere"] = "12é"
        self.assertEqual([], motifs(self.inst))

    def test_seuil_du_centre_strict(self):
        p = next(l for l in self.inst["tables"]["placettes"]["lignes"] if l["plot_id"] == "P001")["geom"]
        c = next(l for l in self.inst["tables"]["centres"]["lignes"] if l["plot_id"] == "P001")
        c["geom"] = {"x": p["x"], "y": p["y"] + d.SEUIL_CENTRE_ELOIGNE_M}
        self.assertEqual([], motifs(self.inst))
        c["geom"]["y"] += 0.01
        self.assertEqual(["centre_eloigne"], motifs(self.inst))

    def test_colonne_date_dans_une_table_inconnue(self):
        self.inst["tables"]["autre"] = generer.table("attributes", {"fid": "INTEGER", "Date": "TEXT"}, [], False)
        self.assertEqual(["colonne_date"], motifs(self.inst))

    def test_champs_nemeton_de_tournee_proteges(self):
        self.inst["tables"]["tournee"]["lignes"][0]["prepare_le"] = "2027-06-16T08:00:00.000Z"
        self.assertEqual(["plan_modifie"], motifs(self.inst, self.a1))

    def test_point_prevu_deplace(self):
        self.inst["tables"]["placettes"]["lignes"][0]["geom"]["x"] += 1.0
        self.assertEqual(["plan_modifie"], motifs(self.inst, self.a1))

    def test_etat_et_operateur_libres(self):
        self.inst["tables"]["placettes"]["lignes"][0]["etat"] = None
        self.inst["tables"]["tournee"]["lignes"][0]["operateur"] = "operateur-B"
        self.assertEqual([], motifs(self.inst, self.a1))

    def test_pas_un_geopackage(self):
        self.inst["application_id"] = 0
        self.assertEqual(["pas_un_geopackage"], motifs(self.inst))


class Fusion(unittest.TestCase):
    """Union sur l'uuid (ADR 0003 § 5 ; spec § 5, critère 4) : complète, sans doublon, rejouable."""

    def setUp(self):
        dossier, self.a = attendu("A5")
        self.p1 = d.lire_paquet(dossier / self.a["fichiers"][0])
        self.p2 = d.lire_paquet(dossier / self.a["fichiers"][1])
        self.a3 = d.lire_paquet(dossier / self.a["fusion"]["resultat"])
        self.op, self.v = self.a["fusion"]["operateur"], self.a["fusion"]["version_ramulus"]

    def test_union_egale_a3(self):
        f = d.fusionner([self.p1, self.p2], self.op, self.v)
        self.assertEqual(self.a3.inst, f.inst)
        self.assertEqual(self.a3.photos, f.photos)

    def test_ordre_indifferent(self):
        self.assertEqual(d.fusionner([self.p2, self.p1], self.op, self.v).inst, self.a3.inst)

    def test_rejouable_sans_doublon(self):
        f = d.fusionner([self.a3, self.p1, self.p2, self.p1], self.op, self.v)
        self.assertEqual(self.a3.inst, f.inst)

    def test_fusion_ecrite_et_relue(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = d.fusionner([self.p1, self.p2], self.op, self.v)
            chemin = Path(tmp) / "f.zip"
            d.ecrire_paquet(chemin, f.inst, generer.RENDU_LE, f.photos)
            relu = d.lire_paquet(chemin)
            self.assertEqual(self.a3.inst, relu.inst)
            self.assertEqual("accepte", d.controler(relu, paquet("A1"), SCHEMA).statut)

    def test_chaque_ligne_garde_son_operateur(self):
        f = d.fusionner([self.p1, self.p2], self.op, self.v)
        ops = {l["plot_id"]: l["operateur"] for l in f.inst["tables"]["notations"]["lignes"]}
        self.assertEqual({"P001": "operateur-A", "P002": "operateur-B", "P003": "operateur-B"}, ops)

    def test_conflit_jamais_tranche_en_silence(self):
        autre = copy.deepcopy(self.p1)
        autre.inst["tables"]["notations"]["lignes"][0]["classe"] = "E"
        with self.assertRaises(d.ConflitUuid):
            d.fusionner([self.p1, autre], self.op, self.v)
        r, fusion = d.controler_fusion([self.p1, autre], self.op, self.v, paquet("A1"), SCHEMA)
        self.assertEqual(("refuse", ["uuid_contenu_different"], None), (r.statut, r.motifs, fusion))
        self.assertIn("classe", r.messages[0])

    def test_conflit_de_photo(self):
        autre = copy.deepcopy(self.p1)
        nom = sorted(autre.photos)[0]
        autre.photos[nom] = autre.photos[nom] + b"\x00"
        r, _ = d.controler_fusion([self.p1, autre], self.op, self.v, paquet("A1"), SCHEMA)
        self.assertEqual(("refuse", ["uuid_contenu_different"]), (r.statut, r.motifs))

    def test_conflit_dans_le_cas_commun_r13_quel_que_soit_l_ordre(self):
        dossier, a = attendu("R13")
        p1, p2 = (d.lire_paquet(dossier / f) for f in a["fichiers"])
        for ordre in ([p1, p2], [p2, p1]):
            r, fusion = d.controler_fusion(ordre, self.op, self.v, paquet("A1"), SCHEMA)
            self.assertEqual(("refuse", ["uuid_contenu_different"], None), (r.statut, r.motifs, fusion))

    def test_fusion_sans_conflit_rend_le_paquet_controle(self):
        r, fusion = d.controler_fusion([self.p1, self.p2], self.op, self.v, paquet("A1"), SCHEMA)
        self.assertEqual(("accepte", []), (r.statut, r.motifs))
        self.assertEqual(self.a3.inst, fusion.inst)


class Ecriture(unittest.TestCase):

    def test_aller_retour_ecriture_lecture(self):
        with tempfile.TemporaryDirectory() as tmp:
            for cas in ("A1", "A2", "A6", "R5a", "R11"):
                p = paquet(cas)
                chemin = Path(tmp) / f"{cas}.gpkg"
                d.ecrire_paquet(chemin, p.inst, "2027-01-01T00:00:00.000Z")
                self.assertEqual(p.inst, d.lire_paquet(chemin).inst, cas)

    def test_photos_ecrites_en_texte_json(self):
        dossier, a = attendu("A3")
        cnx = self.enterContext(connexion(dossier / a["fichiers"][0]))
        textes = [r[0] for r in cnx.execute("SELECT photos FROM notations ORDER BY fid")]
        self.assertTrue(all(isinstance(t, str) for t in textes))
        self.assertIn("[]", textes)
        premiere = json.loads(textes[0])
        self.assertEqual({"nom", "sha256"}, set(premiere[0]))

    def test_accents_intacts(self):
        dossier, a = attendu("A3")
        cnx = self.enterContext(connexion(dossier / a["fichiers"][0]))
        essences = {r[0] for r in cnx.execute("SELECT essence FROM notations")}
        self.assertIn("Chêne sessile", essences)
        self.assertIn("Mélèze", essences)
        self.assertIn("Cèdre", essences)


if __name__ == "__main__":
    unittest.main()
