"""Mesure de l'amorce en validation croisée par fiche laissée de côté (ADR 0007, mesures de l'ADR 0002).

- Une fiche laissée de côté à la fois : aucune de ses photos ne vote pour une photo de cette fiche.
- Le réglage (k, agrégat, périmètre) est choisi SANS la fiche testée : validation croisée imbriquée (pour chaque
  fiche testée, on choisit le réglage par une validation croisée par fiche sur les 24 autres). Le réglage final
  embarqué est celui que la même procédure choisit sur les 25 fiches.
- Votent seulement les photos de l'amorce (notes non nulles, hors incohérences connues). Sont mesurées toutes les
  photos dont la classe imprimée est cohérente (les quatre incohérences sont mises à part), référence : la classe
  imprimée dans le fixture.
- Une référence sans image (médiane des notes du même périmètre) dit ce que l'image apporte.

Écrit `mesure.json` à côté des vecteurs ; le rapport `docs/essais/2026-10-30-amorce-ia.md` en reprend les chiffres.
"""
from __future__ import annotations

import csv
import itertools
import json
import sys

import numpy as np

from sources import ABAQUE, DSF, ESSENCES, charger_fixture, numero_fiche
from tete import (CLASSES, REPLIS, Exemple, Jeu, Prediction, Regle, charger_abaque, mesurer, predire_lofo,
                  voter)
from vecteurs import VECTEURS

GRILLE = [Regle(k, agr, True, per) for k, agr, per in itertools.product(
    (1, 3, 5, 7, 9, 15), ("mediane", "moyenne"), ("essence_saison", "essence", "type", "tout"))]
MESURE = DSF / "mesure.json"


def charger_jeu() -> tuple[Jeu, list[Exemple]]:
    fx = charger_fixture()
    incoh = set(fx["incoherences_connues"])
    with open(ESSENCES, encoding="utf-8") as f:
        types = {r["essence"]: r["type"] for r in csv.DictReader(f, delimiter=";")}
    z = np.load(VECTEURS)
    ordre = {i: n for n, i in enumerate(z["ids"].tolist())}
    ex = []
    for p in fx["photos"]:
        ex.append(Exemple(p["id"], numero_fiche(p["id"]), p["essence"], p["saison"], types[p["essence"]],
                          p["mb"], p["second"], p["classe"],
                          p["mb"] is not None and p["second"] is not None and p["id"] not in incoh))
    vec = z["vecteurs"][[ordre[e.id] for e in ex]]
    return Jeu(ex, vec), ex


def _score(m: dict) -> tuple:
    # classe exacte d'abord (le seuil de l'ADR 0002 est un accord de classe), puis à un près
    return (m["exacte"] / m["n"], m["a_un_pres"] / m["n"]) if m["n"] else (0, 0)


def choisir(jeu: Jeu, abaque, fiches_exclues: set[str], evaluees: list[int]) -> Regle:
    """Réglage qui maximise la classe exacte en validation croisée par fiche, sans les `fiches_exclues`.

    En cas d'égalité : le plus petit k, puis l'ordre de la grille (médiane avant moyenne, périmètre le plus
    étroit d'abord, conforme à l'ADR 0007).
    """
    sur = [i for i in evaluees if jeu.fiche[i] not in fiches_exclues]
    meilleur, score = None, None
    for r in GRILLE:
        p = predire_lofo(jeu, None, r, abaque, sur=sur, fiches_exclues=fiches_exclues, replis=REPLIS[r.perimetre])
        s = _score(mesurer(jeu.ex, p))
        if score is None or s > score:
            meilleur, score = r, s
    return meilleur


def diagnostics(jeu: Jeu, ex: list[Exemple], abaque, evaluees: list[int]) -> dict:
    """Contrôles de la chaîne et de la fuite.

    - L'extracteur reconnaît-il l'essence ? (plus proche voisin, photo seule laissée de côté.) S'il ne la reconnaît
      pas, la chaîne est suspecte ; s'il la reconnaît, les vecteurs portent de l'information d'image.
    - Chiffre AVEC FUITE (photo seule laissée de côté, les photos de la même fiche votent) : ce que l'on aurait
      publié sans la règle de la fiche laissée de côté. Donné pour mesurer la fuite, jamais comme résultat.
    """
    s = jeu.sims.copy()
    np.fill_diagonal(s, -9.0)
    ppv = s.argmax(1)
    meme_fiche = jeu.fiche[:, None] == jeu.fiche[None, :]
    ppv2 = np.where(meme_fiche, -9.0, s).argmax(1)
    deux_fiches = [i for i in range(len(ex)) if len(set(jeu.fiche[jeu.essence == jeu.essence[i]])) > 1]
    out = {
        "ppv_meme_essence": float(np.mean(jeu.essence[ppv] == jeu.essence)),
        "ppv_meme_fiche": float(np.mean(jeu.fiche[ppv] == jeu.fiche)),
        "ppv_meme_essence_hors_fiche_essences_a_deux_fiches": float(np.mean(
            [jeu.essence[ppv2[i]] == jeu.essence[i] for i in deux_fiches])),
        "n_essences_a_deux_fiches": len(deux_fiches),
        "avec_fuite": {},
    }
    for k in (1, 3, 9):
        preds = {}
        for i in evaluees:
            m = jeu.amorce.copy()
            m[i] = False
            m &= jeu.essence == jeu.essence[i]
            c = np.flatnonzero(m)
            pmb, psec, pc, conf = voter(jeu.sims[i, c], jeu.mb[c], jeu.sec[c], Regle(k, "moyenne"), abaque)
            preds[i] = Prediction(ex[i].id, pmb, psec, pc, conf, "essence", [])
        out["avec_fuite"][f"k={k}, moyenne pondérée, même essence"] = mesurer(ex, preds)
    return out


def mesurer_tout() -> dict:
    abaque = charger_abaque(ABAQUE)
    jeu, ex = charger_jeu()
    fx = charger_fixture()
    incoh = set(fx["incoherences_connues"])
    evaluees = [i for i, e in enumerate(ex) if e.id not in incoh]
    fiches = sorted(set(jeu.fiche.tolist()))

    # 1. validation croisée imbriquée : réglage choisi sans la fiche testée
    preds, choix = {}, {}
    for f in fiches:
        r = choisir(jeu, abaque, {f}, evaluees)
        choix[f] = r.nom()
        sur = [i for i in evaluees if jeu.fiche[i] == f]
        preds.update(predire_lofo(jeu, None, r, abaque, sur=sur, replis=REPLIS[r.perimetre]))
    # 2. réglage final (sur les 25 fiches) et grille entière, pour information (optimiste : choisi sur le test)
    final = choisir(jeu, abaque, set(), evaluees)
    grille = []
    for r in GRILLE:
        p = predire_lofo(jeu, None, r, abaque, sur=evaluees, replis=REPLIS[r.perimetre])
        m = mesurer(ex, p)
        grille.append({"regle": r.nom(), "n": m["n"], "exacte": m["exacte"], "a_un_pres": m["a_un_pres"],
                       "rappel_def": m["rappel_def"], "rappel_f": m["rappel_f"]})
    # 3. référence sans image, même chaîne de périmètres que le réglage final
    ref = predire_lofo(jeu, None, Regle(1, "mediane", False, "essence"), abaque, sur=evaluees,
                       replis=REPLIS["essence"], sans_image=True)

    def ventiler(p: dict, cle) -> dict:
        groupes: dict[str, dict] = {}
        for i in p:
            groupes.setdefault(cle(ex[i]), {})[i] = p[i]
        return {g: mesurer(ex, q) for g, q in sorted(groupes.items())}

    groupe = lambda e: ("feuillus hiver" if e.saison == "HIVER" else
                        "feuillus été" if e.type == "FEUILLU" else "résineux")
    # sous-ensemble comparable à l'essai du 2026-10-28 : 237 photos (fiche 13 Hêtre été exclue), classe imprimée
    comparable = [i for i, e in enumerate(ex) if e.fiche != "13"]
    preds_comp = {}
    for f in fiches:
        if f == "13":
            continue
        r = choisir(jeu, abaque, {f}, evaluees)
        sur = [i for i in comparable if jeu.fiche[i] == f]
        preds_comp.update(predire_lofo(jeu, None, r, abaque, sur=sur, replis=REPLIS[r.perimetre]))

    perim = {}
    for i, p in preds.items():
        perim[p.perimetre_utilise] = perim.get(p.perimetre_utilise, 0) + 1
    strict = {i: p for i, p in preds.items() if p.perimetre_utilise in ("essence", "essence_saison")}
    res = {
        "n_amorce": int(jeu.amorce.sum()),
        "amorce_par_classe": {c: int(sum(1 for e in ex if e.dans_amorce and e.classe == c)) for c in CLASSES},
        "hors_amorce": {e.id: ("incohérence connue" if e.id in incoh else "notes nulles dans le fixture")
                        for e in ex if not e.dans_amorce},
        "reglage_final": final.nom(),
        "reglages_par_fiche": choix,
        "imbriquee": mesurer(ex, preds),
        "imbriquee_par_groupe": ventiler(preds, groupe),
        "imbriquee_par_essence": ventiler(preds, lambda e: e.essence),
        "imbriquee_par_saison": ventiler(preds, lambda e: e.saison),
        "imbriquee_perimetre_utilise": perim,
        "imbriquee_meme_essence_seulement": mesurer(ex, strict),
        "imbriquee_comparable_essai_237": mesurer(ex, preds_comp),
        "sans_image": mesurer(ex, ref),
        "sans_image_par_groupe": ventiler(ref, groupe),
        "sans_image_comparable_237": mesurer(ex, {i: v for i, v in predire_lofo(
            jeu, None, Regle(1, "mediane", False, "essence"), abaque, sur=comparable,
            replis=REPLIS["essence"], sans_image=True).items()}),
        "grille_optimiste": grille,
        "diagnostics": diagnostics(jeu, ex, abaque, evaluees),
        "predictions": {p.id: {"mb": p.mb, "second": p.second, "classe": p.classe, "confiance": round(p.confiance, 3),
                               "perimetre": p.perimetre_utilise, "classe_fiche": ex[i].classe}
                        for i, p in sorted(preds.items())},
    }
    return res


def resume(res: dict) -> str:
    def ligne(nom, m):
        n = m["n"]
        mb = f"{m['erreur_mb']:.2f}" if m.get("erreur_mb") is not None else "-"
        return (f"{nom:<38} n={n:3d}  exacte {m['exacte']:3d} ({m['exacte']/n:5.1%})  à un près {m['a_un_pres']:3d} "
                f"({m['a_un_pres']/n:5.1%})  err. MB {mb}  D-E-F {m['rappel_def']}/{m['n_def']}  "
                f"F {m['rappel_f']}/{m['n_f']}  sous/sur {m['sous']}/{m['sur']}")
    out = [f"amorce : {res['n_amorce']} photos {res['amorce_par_classe']} ; réglage final : {res['reglage_final']}",
           ligne("imbriquée (toutes)", res["imbriquee"]),
           ligne("imbriquée, même essence seulement", res["imbriquee_meme_essence_seulement"]),
           ligne("imbriquée, comparable essai (237)", res["imbriquee_comparable_essai_237"]),
           ligne("sans image (toutes)", res["sans_image"]),
           ligne("sans image, comparable essai (237)", res["sans_image_comparable_237"])]
    for g, m in res["imbriquee_par_groupe"].items():
        out.append(ligne(f"  imbriquée {g}", m))
    for g, m in res["sans_image_par_groupe"].items():
        out.append(ligne(f"  sans image {g}", m))
    out.append(f"périmètres utilisés : {res['imbriquee_perimetre_utilise']}")
    d = res["diagnostics"]
    out.append(f"contrôle : plus proche voisin de même essence {d['ppv_meme_essence']:.1%}, de même fiche "
               f"{d['ppv_meme_fiche']:.1%} ; même essence hors fiche (essences à deux fiches, "
               f"n={d['n_essences_a_deux_fiches']}) {d['ppv_meme_essence_hors_fiche_essences_a_deux_fiches']:.1%}")
    for nom, m in d["avec_fuite"].items():
        out.append(ligne(f"AVEC FUITE {nom}", m))
    return "\n".join(out)


if __name__ == "__main__":
    res = mesurer_tout()
    MESURE.write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")
    print(resume(res))
    print(f"-> {MESURE}")
    sys.exit(0)
