"""Lecture des légendes des photos des fiches DSF et rattachement aux ids du fixture.

Pur Python : se teste sans PDF. Le rattachement se fait par la légende (notes imprimées), jamais par le rang
seul (piège de la fiche 126314, p. 3 : l'ordre des ids n'est pas l'ordre gauche-droite).
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

# « MB = 1 », « Mortalité de branches (MB)= 0 », « MR = 0 », « MA = 2 », « DEPERIS = B ».
_RE_MB = re.compile(r"\(?\bMB\b\)?\s*[=:]\s*(\d)")
_RE_SEC = re.compile(r"\(?\bM[RA]\b\)?\s*[=:]\s*(\d)")
_RE_CL = re.compile(r"\bDEPERIS\b\s*[=:]\s*(Mort|[A-F0])\b")


@dataclass
class Legende:
    mb: int | None
    second: int | None
    classe: str | None
    texte: str


def lire_legende(texte: str) -> Legende:
    """Lit MB, MR/MA et la classe imprimés ; ce qui n'est pas imprimé reste None.

    « DEPERIS = 0 » (fiche Sapin, arbre de référence) est lu A et « DEPERIS = Mort » est lu F, comme le dit
    docs/referentiels/deperis/README.md.
    """
    t = " ".join(texte.split())
    mb = _RE_MB.search(t)
    sec = _RE_SEC.search(t)
    cl = _RE_CL.search(t)
    c = cl.group(1) if cl else None
    if c == "0":
        c = "A"
    elif c == "Mort":   # case MB 5 / MR-MA 5 de l'abaque, classée F (README du référentiel)
        c = "F"
    return Legende(int(mb.group(1)) if mb else None, int(sec.group(1)) if sec else None, c, t)


@dataclass
class Rattachement:
    id: str
    image: int                  # indice de l'image sur la page (ordre de lecture)
    methode: str                # « légende », « légende, puis rang parmi légendes identiques », « rang »
    ecart: list[str] = field(default_factory=list)


def compatible(fx: dict, lg: Legende) -> bool:
    """La légende lue ne contredit pas le fixture (une valeur absente d'un côté ne contredit rien)."""
    for cle_fx, val in (("mb", lg.mb), ("second", lg.second), ("classe", lg.classe)):
        if fx[cle_fx] is not None and val is not None and fx[cle_fx] != val:
            return False
    return True


def _signature(fx: dict, lg: Legende) -> int:
    """Nombre de valeurs qui concordent (plus il y en a, plus le rattachement est sûr)."""
    return sum(1 for k, v in (("mb", lg.mb), ("second", lg.second), ("classe", lg.classe))
               if v is not None and fx[k] == v)


def ecarts(fx: dict, lg: Legende) -> list[str]:
    out = []
    noms = {"mb": "MB", "second": "MR/MA", "classe": "classe"}
    for k, v in (("mb", lg.mb), ("second", lg.second), ("classe", lg.classe)):
        if fx[k] is None and v is not None:
            out.append(f"{noms[k]} imprimé {v} sur la fiche, null dans le fixture")
        elif fx[k] is not None and v is None:
            out.append(f"{noms[k]} {fx[k]} dans le fixture, non lu sur la fiche")
        elif fx[k] is not None and v is not None and fx[k] != v:
            out.append(f"{noms[k]} {fx[k]} dans le fixture, {v} sur la fiche")
    return out


def imprime(lg: Legende) -> bool:
    return any(v is not None for v in (lg.mb, lg.second, lg.classe))


@dataclass
class NonRattachee:
    image: int
    motif: str


def rattacher(photos_fx: list[dict], legendes: list[Legende]) -> tuple[list[Rattachement], list[NonRattachee]]:
    """Rattache les images d'une page (dans l'ordre de lecture) aux ids du fixture de cette page.

    1. Pour chaque image, les ids compatibles avec sa légende ; une image sans aucun id compatible (photo
       « DEPERIS = Mort » absente du fixture, par exemple) n'est pas rattachée, et on le dit.
    2. On fixe d'abord les rattachements uniques des images à légende imprimée (en propageant).
    3. Restent des groupes d'images et d'ids de légende identique : rattachés par rang (ordre de lecture des images,
       ordre des ids), et on l'écrit.
    4. Les images sans note lue prennent, par rang, les ids restants ; celles en surnombre (dessins) ne sont pas
       rattachées.
    Lève ValueError si un id du fixture reste sans image.
    """
    poss = {i: [j for j, fx in enumerate(photos_fx) if compatible(fx, lg)] for i, lg in enumerate(legendes)}
    non: list[NonRattachee] = [NonRattachee(i, f"aucun id du fixture compatible avec la légende {legendes[i].texte!r}")
                               for i, p in poss.items() if not p]
    poss = {i: p for i, p in poss.items() if p}
    muettes = [i for i in sorted(poss) if not imprime(legendes[i])]
    poss = {i: p for i, p in poss.items() if imprime(legendes[i])}
    choisi: dict[int, tuple[int, str]] = {}
    libres = set(range(len(photos_fx)))
    progres = True
    while progres:
        progres = False
        for i in list(poss):
            if i in choisi:
                continue
            cands = [j for j in poss[i] if j in libres]
            # préférer les ids qui concordent sur le plus de valeurs imprimées
            if cands:
                meilleur = max(_signature(photos_fx[j], legendes[i]) for j in cands)
                cands = [j for j in cands if _signature(photos_fx[j], legendes[i]) == meilleur]
            if len(cands) == 1:
                choisi[i] = (cands[0], "légende")
                libres.discard(cands[0])
                progres = True
            elif not cands:
                raise ValueError(f"image {i} : plus d'id libre compatible")
    # groupes ambigus : rang
    for i in sorted(i for i in poss if i not in choisi):
        cands = sorted(j for j in poss[i] if j in libres)
        if not cands:
            raise ValueError(f"image {i} : rattachement impossible")
        choisi[i] = (cands[0], "légende, puis rang parmi légendes identiques")
        libres.discard(cands[0])
    # images sans note lue : ids restants, par rang
    for i in muettes:
        if libres:
            j = min(libres)
            choisi[i] = (j, "rang (aucune note lue sous l'image)")
            libres.discard(j)
        else:
            non.append(NonRattachee(i, "image sans légende en surnombre (dessin ou illustration)"))
    if libres:
        raise ValueError(f"ids sans image : {[photos_fx[j]['id'] for j in sorted(libres)]}")
    ratt = [Rattachement(photos_fx[j]["id"], i, m, ecarts(photos_fx[j], legendes[i]))
            for i, (j, m) in sorted(choisi.items())]
    return ratt, sorted(non, key=lambda n: n.image)
