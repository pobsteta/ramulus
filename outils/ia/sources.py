"""Sources de l'amorce : fiches DSF (fixture, référentiel) et téléchargements hors dépôt.

Mission 026, ADR 0007. Rien de ce qui est téléchargé n'entre dans le dépôt : tout va sous
`~/dev/donnees-ramulus/` (variable d'environnement RAMULUS_DONNEES pour le changer).
"""
from __future__ import annotations

import csv
import hashlib
import json
import os
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

DEPOT = Path(__file__).resolve().parents[2]
FIXTURE = DEPOT / "fixtures" / "fiches-dsf-2025.json"
ESSENCES = DEPOT / "docs" / "referentiels" / "deperis" / "essences.csv"
ABAQUE = DEPOT / "docs" / "referentiels" / "deperis" / "abaque.csv"

DONNEES = Path(os.environ.get("RAMULUS_DONNEES", Path.home() / "dev" / "donnees-ramulus"))
DSF = DONNEES / "dsf-2025"
PDF = DSF / "pdf"
PHOTOS = DSF / "photos"
MODELES = DONNEES / "modeles"

# Extracteur retenu (ADR 0007, rapport 2026-10-30 § 3, candidat A) : version « image_embedder » de MediaPipe,
# qui sort directement un vecteur (pas besoin de couper l'avant-dernière couche d'un classifieur).
URL_EXTRACTEUR = (
    "https://storage.googleapis.com/mediapipe-models/image_embedder/"
    "mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite"
)
EXTRACTEUR = MODELES / "mobilenet_v3_small.tflite"

_COLONNE_SAISON = {"ETE": "fiche_ete", "HIVER": "fiche_hiver", "TOUTES": "fiche_toutes_saisons"}


def charger_fixture(chemin: Path = FIXTURE) -> dict:
    with open(chemin, encoding="utf-8") as f:
        return json.load(f)


def numero_fiche(id_photo: str) -> str:
    """`dsf2025-02_01` -> `02` (numéro de fiche, 00 à 24)."""
    prefixe, reste = id_photo.split("-", 1)
    if prefixe != "dsf2025" or "_" not in reste:
        raise ValueError(f"id inattendu : {id_photo}")
    return reste.split("_", 1)[0]


def urls_par_fiche(fixture: dict, essences_csv: Path = ESSENCES) -> dict[str, str]:
    """Numéro de fiche -> URL du PDF, lue dans essences.csv par (essence, saison) du fixture.

    Le rattachement passe par l'essence et la saison écrites dans le fixture, jamais par un rang supposé.
    """
    with open(essences_csv, encoding="utf-8") as f:
        ess = {r["essence"]: r for r in csv.DictReader(f, delimiter=";")}
    urls: dict[str, str] = {}
    for p in fixture["photos"]:
        nn = numero_fiche(p["id"])
        ligne = ess[p["essence"]]
        url = ligne[_COLONNE_SAISON[p["saison"]]]
        if not url and p["saison"] == "TOUTES":
            # Écart listé (rapport 026) : le fixture dit « TOUTES » pour une essence dont le CSV range l'unique
            # lien dans une colonne saisonnière (Chêne liège). Un seul lien : pas d'ambiguïté, on le prend.
            liens = [ligne[c] for c in _COLONNE_SAISON.values() if ligne[c]]
            if len(liens) == 1:
                url = liens[0]
        if not url:
            raise ValueError(f"pas de fiche {p['saison']} pour {p['essence']} ({p['id']})")
        if urls.setdefault(nn, url) != url:
            raise ValueError(f"fiche {nn} : deux URL ({urls[nn]} et {url})")
    return urls


def sha256(chemin: Path) -> str:
    h = hashlib.sha256()
    with open(chemin, "rb") as f:
        for bloc in iter(lambda: f.read(1 << 20), b""):
            h.update(bloc)
    return h.hexdigest()


def _telecharger(url: str, cible: Path) -> None:
    cible.parent.mkdir(parents=True, exist_ok=True)
    tmp = cible.with_suffix(cible.suffix + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": "ramulus-outils-ia/1"})
    with urllib.request.urlopen(req, timeout=120) as r, open(tmp, "wb") as f:
        while bloc := r.read(1 << 16):
            f.write(bloc)
    tmp.replace(cible)


def telecharger(force: bool = False) -> None:
    """Télécharge les 25 PDF et l'extracteur ; écrit SHA256SUMS et la date de téléchargement."""
    fixture = charger_fixture()
    urls = urls_par_fiche(fixture)
    PDF.mkdir(parents=True, exist_ok=True)
    lignes = []
    for nn, url in sorted(urls.items()):
        cible = PDF / f"{nn}.pdf"
        if force or not cible.exists():
            _telecharger(url, cible)
        with open(cible, "rb") as f:
            if f.read(5) != b"%PDF-":
                raise RuntimeError(f"{cible} n'est pas un PDF")
        lignes.append(f"{sha256(cible)}  {nn}.pdf  {url}")
    (PDF / "SHA256SUMS").write_text("\n".join(lignes) + "\n", encoding="utf-8")
    if force or not EXTRACTEUR.exists():
        _telecharger(URL_EXTRACTEUR, EXTRACTEUR)
    (MODELES / "SHA256SUMS").write_text(f"{sha256(EXTRACTEUR)}  {EXTRACTEUR.name}  {URL_EXTRACTEUR}\n",
                                        encoding="utf-8")
    (DSF / "TELECHARGEMENT").write_text(datetime.now(timezone.utc).isoformat() + "\n", encoding="utf-8")
    print(f"{len(lignes)} PDF dans {PDF} ; extracteur {EXTRACTEUR} ({EXTRACTEUR.stat().st_size} octets)")


if __name__ == "__main__":
    telecharger()
