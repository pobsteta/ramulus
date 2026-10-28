"""Passe chaque GeoPackage d'exemple à ogrinfo (GDAL) : la preuve qu'un lecteur tiers les ouvre (M1, option b).

    python3 fixtures/echange/verifier_ogrinfo.py

Pour chaque fichier de fixtures/echange/*/attendu.json (un .zip est lu par /vsizip/) :
1. ogrinfo l'ouvre en lecture seule, sans ERROR ;
2. il liste exactement les tables de l'instantané : « Point » pour placettes et centres, « None » pour les tables
   sans géométrie (tournee, notations, contre_notations) ;
3. pour chaque table, le nombre d'entités est celui de l'instantané, et le repère d'une couche est celui lu dans
   gpkg_spatial_ref_sys (EPSG:2154, sauf le cas R11).

Lancé en CI après l'installation de GDAL (gdal-bin) ; rend 1 au premier écart, avec la sortie d'ogrinfo.
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

ICI = Path(__file__).resolve().parent
LIGNE_COUCHE = re.compile(r"^\s*\d+: (\S+)(?: \((.+)\))?\s*$")
NB_ENTITES = re.compile(r"^Feature Count: (\d+)\s*$", re.M)


def ogrinfo(*args: str) -> str:
    r = subprocess.run(["ogrinfo", "-ro", *args], capture_output=True, text=True)
    sortie = r.stdout + r.stderr
    if r.returncode != 0 or "ERROR" in sortie:
        raise AssertionError(f"ogrinfo {' '.join(args)} a échoué (code {r.returncode}) :\n{sortie}")
    return sortie


def source(chemin: Path) -> str:
    return f"/vsizip/{chemin}/deperis.gpkg" if chemin.suffix == ".zip" else str(chemin)


def verifier(chemin: Path, inst: dict) -> list[str]:
    src = source(chemin)
    couches = {}
    for ligne in ogrinfo(src).splitlines():
        m = LIGNE_COUCHE.match(ligne)
        if m:
            couches[m.group(1)] = m.group(2) or "None"
    attendues = {
        nom: ("Point" if t["geometrie"] else "None") for nom, t in inst["tables"].items()
    }
    ecarts = []
    if couches != attendues:
        ecarts.append(f"couches lues {couches}, attendues {attendues}")
    for nom, t in inst["tables"].items():
        resume = ogrinfo("-so", src, nom)
        m = NB_ENTITES.search(resume)
        if not m or int(m.group(1)) != len(t["lignes"]):
            ecarts.append(f"{nom} : {m.group(1) if m else '?'} entités au lieu de {len(t['lignes'])}")
        if t["geometrie"]:
            code = t["geometrie"]["srs"]["code"]
            if not re.search(rf'EPSG["\s,:]*{code}\b', resume):
                ecarts.append(f"{nom} : repère EPSG:{code} non reconnu par GDAL\n{resume}")
    return ecarts


def main() -> int:
    nb, echecs = 0, 0
    for attendu in sorted(ICI.glob("*/attendu.json")):
        a = json.loads(attendu.read_text(encoding="utf-8"))
        for f in a["fichiers"]:
            chemin = attendu.parent / f
            inst = json.loads((attendu.parent / (f.rsplit(".", 1)[0] + ".instantane.json")).read_text(encoding="utf-8"))
            nb += 1
            try:
                ecarts = verifier(chemin.resolve(), inst)
            except AssertionError as e:
                ecarts = [str(e)]
            if ecarts:
                echecs += 1
                print(f"ÉCHEC {a['cas']} {f}")
                for e in ecarts:
                    print(f"    {e}")
            else:
                print(f"ok    {a['cas']} {f}")
    print(f"{nb} fichiers passés à ogrinfo, {echecs} en échec")
    return 1 if echecs or nb == 0 else 0


if __name__ == "__main__":
    sys.exit(main())
