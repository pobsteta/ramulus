"""Amorce de l'IA embarquée (mission 026, ADR 0007) : de bout en bout, rejouable.

    ~/dev/donnees-ramulus/venv/bin/python outils/ia/amorce.py [étape ...]

Étapes, dans l'ordre (toutes par défaut) : telecharger, extraire, vecteurs, mesurer, exporter.
Tout ce qui est produit va sous ~/dev/donnees-ramulus/ (RAMULUS_DONNEES) ; rien dans le dépôt.
"""
from __future__ import annotations

import json
import sys
import warnings

warnings.filterwarnings("ignore", category=FutureWarning)

ETAPES = ("telecharger", "extraire", "vecteurs", "mesurer", "exporter")


def main(etapes: list[str]) -> None:
    for e in etapes:
        if e not in ETAPES:
            raise SystemExit(f"étape inconnue : {e} (attendu : {', '.join(ETAPES)})")
    for e in [x for x in ETAPES if x in etapes]:
        print(f"--- {e}")
        if e == "telecharger":
            from sources import telecharger
            telecharger()
        elif e == "extraire":
            from extraction import extraire
            extraire()
        elif e == "vecteurs":
            from vecteurs import calculer
            calculer()
        elif e == "mesurer":
            from mesure import MESURE, mesurer_tout, resume
            res = mesurer_tout()
            MESURE.write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")
            print(resume(res))
        elif e == "exporter":
            from exporter import exporter
            exporter()


if __name__ == "__main__":
    main(sys.argv[1:] or list(ETAPES))
