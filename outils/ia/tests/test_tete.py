"""Tests de la tête : abaque, vote, découpage par fiche, mesures. Lancement : voir outils/ia/README.md."""
import numpy as np
import pytest

from sources import ABAQUE
from tete import (Exemple, Regle, candidats, charger_abaque, classe, mesurer, predire_lofo, voter)

AB = charger_abaque(ABAQUE)


# ---------------------------------------------------------------- abaque (docs/referentiels/deperis/abaque.csv)

@pytest.mark.parametrize("mb,second,attendu", [
    (0, 0, "A"), (0, 1, "B"), (1, 0, "B"), (1, 1, "B"), (2, 1, "C"), (2, 2, "D"),
    (3, 1, "D"), (3, 3, "E"), (3, 4, "F"), (4, 2, "E"), (4, 3, "F"), (0, 5, "F"), (5, 0, "F"), (5, 5, "F"),
])
def test_abaque_lu_dans_le_referentiel(mb, second, attendu):
    assert classe(AB, mb, second) == attendu


def test_abaque_refuse_une_note_hors_bornes():
    with pytest.raises(ValueError):
        classe(AB, 6, 0)
    with pytest.raises(ValueError):
        classe(AB, 0, -1)


def test_les_quatre_incoherences_connues_restent_incoherentes():
    # Fixture : 16_08 (1,3,C), 17_08 (0,3,E), 19_07 (2,3,E), 19_08 (4,1,F) ; l'abaque dit autre chose.
    for mb, sec, imprimee in [(1, 3, "C"), (0, 3, "E"), (2, 3, "E"), (4, 1, "F")]:
        assert classe(AB, mb, sec) != imprimee


# ---------------------------------------------------------------- vote

def test_vote_la_classe_vient_de_l_abaque_et_non_d_un_vote_de_classes():
    # Trois voisins : (0,2)=C, (2,0)=C, (2,2)=D. Médianes : MB 2, MR 2 -> abaque D, alors qu'un vote de classes dirait C.
    sims = np.array([0.9, 0.8, 0.7])
    pmb, psec, c, conf = voter(sims, np.array([0, 2, 2]), np.array([2, 0, 2]), Regle(k=3, ponderee=False), AB)
    assert (pmb, psec, c) == (2, 2, "D")
    assert conf == pytest.approx(1 / 3)


def test_vote_ne_garde_que_les_k_plus_proches():
    sims = np.array([0.1, 0.95, 0.9, 0.2])
    mb = np.array([5, 0, 0, 5])
    sec = np.array([5, 0, 0, 5])
    assert voter(sims, mb, sec, Regle(k=2), AB)[:3] == (0, 0, "A")


def test_moyenne_arrondie_demi_vers_le_haut():
    sims = np.array([0.5, 0.5])
    pmb, psec, _, _ = voter(sims, np.array([2, 3]), np.array([0, 1]), Regle(k=2, agregat="moyenne", ponderee=False), AB)
    assert (pmb, psec) == (3, 1)


def test_mediane_ponderee_suit_le_poids():
    sims = np.array([1.0, -0.9, -0.9])  # poids ~1, ~0.05, ~0.05
    pmb, _, _, _ = voter(sims, np.array([4, 0, 0]), np.array([0, 0, 0]), Regle(k=3), AB)
    assert pmb == 4


def test_vote_sans_voisin_refuse():
    with pytest.raises(ValueError):
        voter(np.array([]), np.array([]), np.array([]), Regle(k=3), AB)


# ---------------------------------------------------------------- découpage par fiche

def _ex(id_, fiche, essence, saison="TOUTES", type_="RESINEUX", mb=0, sec=0, cl="A", amorce=True):
    return Exemple(id_, fiche, essence, saison, type_, mb, sec, cl, amorce)


def _jeu():
    return [
        _ex("a1", "02", "Chêne", "ETE", "FEUILLU", 0, 0, "A"),
        _ex("a2", "02", "Chêne", "ETE", "FEUILLU", 3, 3, "E"),
        _ex("b1", "03", "Chêne", "HIVER", "FEUILLU", 3, 3, "E"),
        _ex("b2", "03", "Chêne", "HIVER", "FEUILLU", 0, 0, "A"),
        _ex("c1", "00", "Cèdre", mb=None, sec=None, cl="B", amorce=False),
        _ex("c2", "00", "Cèdre", mb=1, sec=1, cl="B"),
        _ex("d1", "09", "Douglas", mb=5, sec=5, cl="F"),
    ]


def test_aucune_photo_de_la_meme_fiche_ne_vote():
    ex = _jeu()
    for i, e in enumerate(ex):
        for perim in ("essence", "essence_saison", "type", "tout"):
            assert all(ex[j].fiche != e.fiche for j in candidats(ex, i, perim, exclure_fiche=e.fiche))


def test_vote_dans_la_meme_essence_seulement():
    ex = _jeu()
    c = candidats(ex, 0, "essence", exclure_fiche="02")
    assert sorted(ex[j].id for j in c) == ["b1", "b2"]


def test_photo_hors_amorce_ne_vote_jamais():
    ex = _jeu()
    c = candidats(ex, 6, "tout", exclure_fiche="09")
    assert "c1" not in [ex[j].id for j in c]


def test_repli_dit_quand_l_essence_n_a_qu_une_fiche():
    ex = _jeu()
    vec = np.eye(7) + 0.01
    preds = predire_lofo(ex, vec, Regle(k=1), AB, repli="type")
    assert preds[5].perimetre_utilise == "type"          # Cèdre : seule fiche 00 -> repli sur les résineux
    assert preds[5].voisins == ["d1"]
    assert preds[0].perimetre_utilise == "essence"
    sans_repli = predire_lofo(ex, vec, Regle(k=1), AB, repli=None)
    assert 5 not in sans_repli and 6 not in sans_repli


def test_la_photo_la_plus_proche_hors_fiche_gagne():
    ex = _jeu()
    vec = np.zeros((7, 3))
    vec[:, 2] = 0.1
    vec[0] = [1, 0, 0.1]    # a1 (A) proche de b2 (A)
    vec[3] = [1, 0.1, 0.1]
    vec[1] = [0, 1, 0.1]    # a2 (E) proche de b1 (E)
    vec[2] = [0.1, 1, 0.1]
    preds = predire_lofo(ex, vec, Regle(k=1), AB)
    assert preds[0].voisins == ["b2"] and preds[0].classe == "A"
    assert preds[1].voisins == ["b1"] and preds[1].classe == "E"


# ---------------------------------------------------------------- mesures

def test_mesures_comptent_juste():
    ex = _jeu()
    from tete import Prediction
    preds = {
        0: Prediction("a1", 0, 0, "A", 1, "essence", []),   # exacte
        1: Prediction("a2", 2, 2, "D", 1, "essence", []),   # E -> D : à un près, sous, D-E-F trouvé
        6: Prediction("d1", 3, 3, "E", 1, "type", []),      # F -> E : F manqué
        4: Prediction("c1", 0, 0, "A", 1, "type", []),      # B -> A ; MB inconnu : hors erreur MB
    }
    m = mesurer(ex, preds)
    assert m["n"] == 4 and m["exacte"] == 1 and m["a_un_pres"] == 4
    assert m["sous"] == 3 and m["sur"] == 0
    assert m["n_def"] == 2 and m["rappel_def"] == 2
    assert m["n_f"] == 1 and m["rappel_f"] == 0
    assert m["n_mb"] == 3 and m["erreur_mb"] == pytest.approx((0 + 1 + 2) / 3)
    assert sum(map(sum, m["confusion"])) == 4
