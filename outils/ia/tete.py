"""Tête de l'amorce : abaque DEPERIS, plus proches voisins, vote, validation croisée par fiche laissée de côté.

Mission 026, ADR 0007. Pur numpy : ni PDF ni modèle ici, pour que tout se teste sans données.

La classe n'est jamais votée : on propose MB et MR/MA, la classe vient de l'abaque (ADR 0001, point 3).
"""
from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path

import numpy as np

CLASSES = "ABCDEF"


def charger_abaque(chemin: Path) -> list[list[str]]:
    """abaque.csv -> table[mb][second] = classe (6 x 6)."""
    with open(chemin, encoding="utf-8") as f:
        lignes = list(csv.reader(f, delimiter=";"))
    entete, corps = lignes[0], lignes[1:]
    if entete != ["mb"] + [f"second_{i}" for i in range(6)]:
        raise ValueError(f"en-tête d'abaque inattendu : {entete}")
    table = [[""] * 6 for _ in range(6)]
    for ligne in corps:
        mb = int(ligne[0])
        for s in range(6):
            c = ligne[1 + s]
            if c not in CLASSES:
                raise ValueError(f"classe inconnue {c!r} dans l'abaque")
            table[mb][s] = c
    if any(c == "" for r in table for c in r):
        raise ValueError("abaque incomplet")
    return table


def classe(abaque: list[list[str]], mb: int, second: int) -> str:
    if not (0 <= mb <= 5 and 0 <= second <= 5):
        raise ValueError(f"note hors bornes : MB {mb}, second {second}")
    return abaque[mb][second]


def rang(c: str) -> int:
    return CLASSES.index(c)


# --------------------------------------------------------------------------------------------------------------
# Vote

@dataclass(frozen=True)
class Regle:
    """Règle de vote, provisoire (fiche 026, « Valeur provisoire ») : choisie par la validation croisée."""

    k: int
    agregat: str = "mediane"     # "mediane" ou "moyenne" (moyenne pondérée arrondie)
    ponderee: bool = True        # poids = similarité cosinus ramenée à [0, 1]
    perimetre: str = "essence"   # "essence" (ADR 0007), "essence_saison", "type", "tout"

    def nom(self) -> str:
        return f"k={self.k}, {self.agregat}{' pondérée' if self.ponderee else ''}, périmètre {self.perimetre}"


def _mediane_ponderee(valeurs: np.ndarray, poids: np.ndarray) -> float:
    ordre = np.argsort(valeurs, kind="stable")
    v, p = valeurs[ordre], poids[ordre]
    cumul = np.cumsum(p)
    return float(v[np.searchsorted(cumul, cumul[-1] / 2.0)])


def _arrondi(x: float) -> int:
    # Arrondi « demi vers le haut » explicite (np.round arrondit au pair : 2,5 -> 2), borné à 0..5.
    return int(min(5, max(0, np.floor(x + 0.5))))


def voter(sims: np.ndarray, mb: np.ndarray, second: np.ndarray, regle: Regle,
          abaque: list[list[str]]) -> tuple[int, int, str, float]:
    """Vote sur des voisins déjà filtrés (même périmètre). Rend (mb, second, classe, confiance).

    `sims` : similarités cosinus des candidats ; on garde les k plus grandes.
    Confiance : part (pondérée) des voisins retenus dont la classe d'abaque égale la classe proposée.
    """
    if len(sims) == 0:
        raise ValueError("aucun voisin")
    k = min(regle.k, len(sims))
    idx = np.argsort(-sims, kind="stable")[:k]
    s, m, r = sims[idx], mb[idx], second[idx]
    poids = np.clip((s + 1.0) / 2.0, 1e-6, None) if regle.ponderee else np.ones(k)
    if regle.agregat == "mediane":
        pmb, psec = _arrondi(_mediane_ponderee(m, poids)), _arrondi(_mediane_ponderee(r, poids))
    elif regle.agregat == "moyenne":
        pmb, psec = _arrondi(np.average(m, weights=poids)), _arrondi(np.average(r, weights=poids))
    else:
        raise ValueError(regle.agregat)
    c = classe(abaque, pmb, psec)
    classes_voisins = np.array([classe(abaque, int(a), int(b)) for a, b in zip(m, r)])
    confiance = float(poids[classes_voisins == c].sum() / poids.sum())
    return pmb, psec, c, confiance


# --------------------------------------------------------------------------------------------------------------
# Validation croisée par fiche laissée de côté

@dataclass
class Exemple:
    id: str
    fiche: str
    essence: str
    saison: str
    type: str          # FEUILLU / RESINEUX
    mb: int | None
    second: int | None
    classe: str        # classe imprimée (fixture)
    dans_amorce: bool  # notes non nulles et hors incohérences connues


def candidats(ex: list[Exemple], i: int, perimetre: str, exclure_fiche: str | None) -> np.ndarray:
    """Indices des exemples de l'amorce pouvant voter pour l'exemple i.

    `exclure_fiche` : aucune photo de cette fiche ne vote (validation croisée par fiche laissée de côté).
    """
    q = ex[i]
    garde = []
    for j, e in enumerate(ex):
        if j == i or not e.dans_amorce:
            continue
        if exclure_fiche is not None and e.fiche == exclure_fiche:
            continue
        if perimetre == "essence" and e.essence != q.essence:
            continue
        if perimetre == "essence_saison" and (e.essence != q.essence or e.saison != q.saison):
            continue
        if perimetre == "type" and e.type != q.type:
            continue
        garde.append(j)
    return np.array(garde, dtype=int)


@dataclass
class Prediction:
    id: str
    mb: int
    second: int
    classe: str
    confiance: float
    perimetre_utilise: str   # périmètre réellement utilisé (repli dit, jamais caché)
    voisins: list[str]


class Jeu:
    """Exemples et vecteurs normalisés, avec les attributs en tableaux pour filtrer vite."""

    def __init__(self, ex: list[Exemple], vecteurs: np.ndarray):
        self.ex = ex
        v = np.asarray(vecteurs, dtype=np.float64)
        v = v / np.linalg.norm(v, axis=1, keepdims=True)
        self.sims = v @ v.T
        self.fiche = np.array([e.fiche for e in ex])
        self.essence = np.array([e.essence for e in ex])
        self.saison = np.array([e.saison for e in ex])
        self.type = np.array([e.type for e in ex])
        self.amorce = np.array([e.dans_amorce for e in ex])
        self.mb = np.array([e.mb if e.mb is not None else -1 for e in ex])
        self.sec = np.array([e.second if e.second is not None else -1 for e in ex])

    def masque(self, i: int, perimetre: str, fiches_exclues: set[str]) -> np.ndarray:
        m = self.amorce.copy()
        m[i] = False
        m &= ~np.isin(self.fiche, list(fiches_exclues | {self.fiche[i]}))
        if perimetre == "essence":
            m &= self.essence == self.essence[i]
        elif perimetre == "essence_saison":
            m &= (self.essence == self.essence[i]) & (self.saison == self.saison[i])
        elif perimetre == "type":
            m &= self.type == self.type[i]
        elif perimetre != "tout":
            raise ValueError(perimetre)
        return m


REPLIS = {"essence_saison": ["essence", "type"], "essence": ["type"], "type": ["tout"], "tout": []}


def predire_lofo(ex: list[Exemple] | Jeu, vecteurs: np.ndarray | None, regle: Regle, abaque,
                 repli: str | None = "type", sur: list[int] | None = None,
                 fiches_exclues: set[str] = frozenset(), replis: list[str] | None = None,
                 sans_image: bool = False) -> dict[int, Prediction]:
    """Prédit chaque exemple sans aucune photo de sa fiche (ni des `fiches_exclues`) parmi les votants.

    Si le périmètre ne laisse aucun voisin (essence à une seule fiche), on passe aux périmètres de repli, dans
    l'ordre, et on l'inscrit dans la prédiction ; `repli=None` et `replis=None` : pas de prédiction.
    `sans_image=True` : référence sans image, tous les exemples du périmètre votent avec le même poids.
    """
    jeu = ex if isinstance(ex, Jeu) else Jeu(ex, vecteurs)
    chaine = [regle.perimetre] + (replis if replis is not None else ([repli] if repli else []))
    preds: dict[int, Prediction] = {}
    for i in (sur if sur is not None else range(len(jeu.ex))):
        for perim in chaine:
            c = np.flatnonzero(jeu.masque(i, perim, set(fiches_exclues)))
            if len(c):
                break
        else:
            continue
        assert not np.any(jeu.fiche[c] == jeu.fiche[i]), "fuite : une photo de la même fiche vote"
        sims = np.zeros(len(c)) if sans_image else jeu.sims[i, c]
        r = Regle(len(c), regle.agregat, False, perim) if sans_image else regle
        pmb, psec, pc, conf = voter(sims, jeu.mb[c], jeu.sec[c], r, abaque)
        k = min(r.k, len(c))
        top = c[np.argsort(-sims, kind="stable")[:k]]
        preds[i] = Prediction(jeu.ex[i].id, pmb, psec, pc, conf, perim, [jeu.ex[j].id for j in top])
    return preds


# --------------------------------------------------------------------------------------------------------------
# Mesures (ADR 0002, point 3)

def mesurer(ex: list[Exemple], preds: dict[int, Prediction]) -> dict:
    idx = sorted(preds)
    n = len(idx)
    if n == 0:
        return {"n": 0}
    vrai = np.array([rang(ex[i].classe) for i in idx])
    prop = np.array([rang(preds[i].classe) for i in idx])
    ecart = prop - vrai
    res = {
        "n": n,
        "exacte": int((ecart == 0).sum()),
        "a_un_pres": int((np.abs(ecart) <= 1).sum()),
        "sous": int((ecart < 0).sum()),
        "sur": int((ecart > 0).sum()),
    }
    avec_mb = [i for i in idx if ex[i].mb is not None]
    res["n_mb"] = len(avec_mb)
    res["erreur_mb"] = float(np.mean([abs(preds[i].mb - ex[i].mb) for i in avec_mb])) if avec_mb else None
    res["biais_mb"] = float(np.mean([preds[i].mb - ex[i].mb for i in avec_mb])) if avec_mb else None
    def_ = [i for i in idx if ex[i].classe in "DEF"]
    f = [i for i in idx if ex[i].classe == "F"]
    res["n_def"] = len(def_)
    res["rappel_def"] = int(sum(preds[i].classe in "DEF" for i in def_))
    res["n_f"] = len(f)
    res["rappel_f"] = int(sum(preds[i].classe == "F" for i in f))
    sains = [i for i in idx if ex[i].classe in "AB"]
    res["n_ab"] = len(sains)
    res["ab_dits_degrades"] = int(sum(preds[i].classe in "DEF" for i in sains))
    mat = np.zeros((6, 6), dtype=int)
    for a, b in zip(vrai, prop):
        mat[a, b] += 1
    res["confusion"] = mat.tolist()
    return res
