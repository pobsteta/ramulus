"""Cas complémentaires du testeur (mission 003) : les motifs du catalogue de deperis-v1 qu'aucun cas de
fixtures/echange/ ne couvre (`pas_un_geopackage`, `version_illisible`, `structure`, `type_incoherent`,
`ia_incomplete`, `uuid_duplique`, `rattachement_invalide`).

    python3 -I fixtures/echange-complements/generer.py

Chaque cas part du rendu A3 (fixtures/echange/A3-rendu), y change une seule chose, et s'écrit au même format que les
cas communs (un dossier, un .zip, son instantané, `attendu.json`). Le motif attendu est celui que le testeur a voulu ;
le script vérifie que le contrôle de référence (fixtures/echange/deperis_v1.py) conclut pareil, sinon il s'arrête.
Les cas ne sont pas dans fixtures/echange/ : ce dossier est tenu par la mission 002 (et son generer.py). Pascal
tranche s'ils y entrent.
"""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

ICI = Path(__file__).resolve().parent
ECHANGE = ICI.parent / "echange"
sys.path.insert(0, str(ECHANGE))
import deperis_v1 as d  # noqa: E402

DEPART = ECHANGE / "A1-tournee-depart" / "deperis.gpkg"
RENDU = ECHANGE / "A3-rendu" / "EXEMPLE-2027-01.zip"
DERNIERE_MODIF = "2027-07-08T00:00:00.000Z"


def notes(inst):
    return inst["tables"]["notations"]["lignes"]


def pas_un_geopackage(inst):
    inst["application_id"] = 0


def version_illisible(inst):
    inst["tables"]["tournee"]["lignes"][0]["version_schema"] = 0
    inst["version_schema"] = 0


def structure(inst):
    notes(inst)[0]["saison"] = "PRINTEMPS"


def type_incoherent(inst):  # Chêne sessile (feuillu) déclaré résineux, avec le critère MA qui va avec ce type
    notes(inst)[0]["type"] = "RESINEUX"
    notes(inst)[0]["critere"] = "MA"


def ia_incomplete(inst):
    notes(inst)[0]["ia_mb"] = 2


def uuid_duplique(inst):
    copie = copy.deepcopy(notes(inst)[0])
    copie["fid"] = len(notes(inst)) + 1
    copie["plot_id"], copie["rang"], copie["repere"] = "P003", 3, "98"  # place libre : pas de doublon_arbre
    notes(inst).append(copie)


def rattachement_plot_inconnu(inst):
    notes(inst)[0]["plot_id"] = "P999"


def rattachement_rang_trop_grand(inst):  # P001 a nb_arbres = 4
    notes(inst)[0]["rang"] = 5


CAS = [
    ("X1-pas-un-geopackage", "pas_un_geopackage", pas_un_geopackage, "application_id différent de « GPKG »"),
    ("X2-version-illisible", "version_illisible", version_illisible, "version_schema = 0 (il n'y a pas de version 0)"),
    ("X3-valeur-hors-liste", "structure", structure, "saison « PRINTEMPS » dans une note (liste : ETE, HIVER)"),
    ("X4-type-incoherent", "type_incoherent", type_incoherent, "Chêne sessile déclaré RESINEUX (critère MA)"),
    ("X5-ia-incomplete", "ia_incomplete", ia_incomplete, "ia_mb remplie, les cinq autres colonnes de l'IA vides"),
    ("X6-uuid-duplique", "uuid_duplique", uuid_duplique, "même uuid sur deux lignes de notations"),
    ("X7a-plot-inconnu", "rattachement_invalide", rattachement_plot_inconnu, "plot_id P999 absent de placettes"),
    ("X7b-rang-trop-grand", "rattachement_invalide", rattachement_rang_trop_grand, "rang 5 pour nb_arbres = 4"),
]


def main():
    depart = d.lire_paquet(DEPART)
    rendu = d.lire_paquet(RENDU)
    for nom, motif, mutation, titre in CAS:
        inst = copy.deepcopy(rendu.inst)
        inst.pop("photos_du_paquet", None)
        mutation(inst)
        cible = ICI / nom
        cible.mkdir(exist_ok=True)
        d.ecrire_paquet(cible / "EXEMPLE-2027-01.zip", inst, DERNIERE_MODIF, rendu.photos)
        paquet = d.lire_paquet(cible / "EXEMPLE-2027-01.zip")
        sans_depart = nom.startswith("X2")  # la version change aussi la ligne de tournee : plan_modifie en plus
        r = d.controler(paquet, None if sans_depart else depart)
        if r.statut != "refuse" or r.motifs != [motif]:
            sys.exit(f"{nom} : le contrôle de référence dit {r.statut} {r.motifs}, voulu refuse [{motif}]")
        (cible / "EXEMPLE-2027-01.instantane.json").write_text(
            json.dumps(paquet.inst, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        attendu = {
            "cas": nom.split("-")[0], "titre": titre, "source": "mission 003 (testeur) ; docs/formats/deperis-v1.md",
            "lecteur": "nemeton", "fichiers": ["EXEMPLE-2027-01.zip"],
            "depart": None if sans_depart else "../../echange/A1-tournee-depart/deperis.gpkg", "attendu": "refuse", "motifs": [motif],
            "fusion": None, "notes": [],
        }
        (cible / "attendu.json").write_text(json.dumps(attendu, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"{attendu['cas']:5} refuse   {motif:24} {titre}")


if __name__ == "__main__":
    main()
