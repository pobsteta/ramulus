"""Fichiers que l'application embarquera (mission 027), écrits hors dépôt dans `~/dev/donnees-ramulus/ia-embarquee/`.

- `mobilenet_v3_small.tflite` : l'extracteur, copié tel quel (empreinte vérifiée) ;
- `amorce-vecteurs.f32` : N x 1024 flottants 32 bits petit-boutistes, normalisés L2, ligne i = exemple i ;
- `amorce-exemples.csv` : rang, id, fiche, essence, saison, type, mb, second, classe (de l'abaque) ;
- `amorce.json` : format, extracteur (URL, SHA-256), prétraitement, réglage de la tête, empreinte de l'amorce.

L'empreinte de l'amorce (SHA-256 des deux fichiers, dans l'ordre vecteurs puis exemples) est celle que `ia_modele`
portera (ADR 0007, ajout du 2026-10-30).
"""
from __future__ import annotations

import csv
import hashlib
import json
import shutil
from datetime import date

import numpy as np

from mesure import MESURE, charger_jeu
from sources import ABAQUE, DONNEES, EXTRACTEUR, URL_EXTRACTEUR, sha256
from tete import REPLIS, charger_abaque, classe
from vecteurs import TAILLE

SORTIE = DONNEES / "ia-embarquee"


def exporter() -> dict:
    abaque = charger_abaque(ABAQUE)
    jeu, ex = charger_jeu()
    mesure = json.loads(MESURE.read_text(encoding="utf-8"))
    garde = [i for i, e in enumerate(ex) if e.dans_amorce]
    for i in garde:
        assert classe(abaque, ex[i].mb, ex[i].second) == ex[i].classe, f"{ex[i].id} : abaque et fiche divergent"
    SORTIE.mkdir(parents=True, exist_ok=True)
    z = np.load(DONNEES / "dsf-2025" / "vecteurs.npz")
    assert str(z["extracteur_sha256"]) == sha256(EXTRACTEUR), "vecteurs calculés avec un autre extracteur"
    ordre = {i: n for n, i in enumerate(z["ids"].tolist())}
    vec = z["vecteurs"][[ordre[ex[i].id] for i in garde]].astype("<f4")
    vec /= np.linalg.norm(vec, axis=1, keepdims=True)
    (SORTIE / "amorce-vecteurs.f32").write_bytes(vec.tobytes(order="C"))
    with open(SORTIE / "amorce-exemples.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["rang", "id", "fiche", "essence", "saison", "type", "mb", "second", "classe"])
        for n, i in enumerate(garde):
            e = ex[i]
            w.writerow([n, e.id, e.fiche, e.essence, e.saison, e.type, e.mb, e.second,
                        classe(abaque, e.mb, e.second)])
    shutil.copyfile(EXTRACTEUR, SORTIE / EXTRACTEUR.name)
    h = hashlib.sha256()
    for nom in ("amorce-vecteurs.f32", "amorce-exemples.csv"):
        h.update((SORTIE / nom).read_bytes())
    meta = {
        "format": "ramulus-amorce-ia/1",
        "date": date.today().isoformat(),
        "source": "Photos des fiches essences DEPERIS du DSF, versions 2025 ; notes du fixture "
                  "fixtures/fiches-dsf-2025.json ; classe par docs/referentiels/deperis/abaque.csv",
        "droits": "Photos propriété du ministère (mentions légales d'agriculture.gouv.fr) ; usage assumé par "
                  "Pascal (ADR 0007, ajout du 2026-10-30). Aucune photo ici : vecteurs et notes seulement.",
        "statut": "essai (ADR 0002 / 0007)",
        "extracteur": {"fichier": EXTRACTEUR.name, "url": URL_EXTRACTEUR, "sha256": sha256(EXTRACTEUR),
                       "licence": "Apache License. Version 2.0 (métadonnées du fichier)",
                       "sortie": "feature_vector", "dimension": int(vec.shape[1])},
        "pretraitement": {"rvb": True, "orientation": "appliquée", "redimensionnement": f"{TAILLE}x{TAILLE}, "
                          "image entière, bilinéaire, sans recadrage", "echelle": "valeur / 255, entre 0 et 1",
                          "normalisation_sortie": "L2"},
        "tete": {"methode": "plus proches voisins, similarité cosinus", "reglage": mesure["reglage_final"],
                 "replis": REPLIS, "classe": "abaque appliqué aux notes proposées (MB, MR/MA)",
                 "confiance": "part pondérée des voisins retenus dont la classe d'abaque égale la classe proposée",
                 "plusieurs_photos": "moyenne des vecteurs des vues, renormalisée (ADR 0007, point 3 ; non mesuré)"},
        "exemples": len(garde),
        "fichiers": {n: {"octets": (SORTIE / n).stat().st_size, "sha256": sha256(SORTIE / n)}
                     for n in ("amorce-vecteurs.f32", "amorce-exemples.csv", EXTRACTEUR.name)},
        "empreinte_amorce": h.hexdigest(),
    }
    (SORTIE / "amorce.json").write_text(json.dumps(meta, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(garde)} exemples -> {SORTIE}")
    for n, d in meta["fichiers"].items():
        print(f"  {n:<28} {d['octets']:>9} octets  {d['sha256']}")
    print(f"  empreinte de l'amorce : {meta['empreinte_amorce']}")
    return meta


if __name__ == "__main__":
    exporter()
