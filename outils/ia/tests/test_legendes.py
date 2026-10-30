"""Tests de la lecture des légendes et du rattachement (sans PDF)."""
import pytest

from legendes import lire_legende, rattacher


def fx(id_, mb, sec, cl):
    return {"id": id_, "mb": mb, "second": sec, "classe": cl}


@pytest.mark.parametrize("texte,attendu", [
    ("MB = 1 MR = 0 DEPERIS = B DF = 10 %", (1, 0, "B")),
    ("Arbre de référence Mortalité de branches (MB)= 0 Manque de ramifications (MR) = 0 DEPERIS = A", (0, 0, "A")),
    ("Mortalité de branches (MB) = 1 Manque d’aiguilles (MA) = 1 DEPERIS = B", (1, 1, "B")),
    ("MB = 0 MA = 0 DEPERIS = 0 Déficit foliaire = 0%", (0, 0, "A")),         # fiche Sapin : 0 lu A
    ("MB = 5 MR = 5 DEPERIS = Mort DF = 100%", (5, 5, "F")),                  # case « Mort » : F
    ("DEPERIS : D", (None, None, "D")),
    ("MB = 2 (en comptant le branches rouges) MR = 1 DEPERIS = C DF = 40 %", (2, 1, "C")),
    ("", (None, None, None)),
])
def test_lire_legende(texte, attendu):
    lg = lire_legende(texte)
    assert (lg.mb, lg.second, lg.classe) == attendu


def test_le_df_n_est_pas_pris_pour_une_note():
    lg = lire_legende("Déficit foliaire (DF) = 0 % MB = 3 MA = 2 DEPERIS = D")
    assert (lg.mb, lg.second) == (3, 2)


def test_piege_fiche_126314_page_3_l_ordre_des_ids_n_est_pas_l_ordre_gauche_droite():
    # Images dans l'ordre de lecture : à gauche l'arbre de référence (A), à droite MB 1 / MR 0 / B.
    photos = [fx("dsf2025-02_01", 1, 0, "B"), fx("dsf2025-02_02", None, None, "A")]
    legendes = [lire_legende("Arbre de référence Mortalité de branches (MB)= 0 Manque de ramifications (MR) = 0 "
                             "DEPERIS = A"), lire_legende("MB = 1 MR = 0 DEPERIS = B DF = 10 %")]
    ratt, non = rattacher(photos, legendes)
    assert {r.id: r.image for r in ratt} == {"dsf2025-02_02": 0, "dsf2025-02_01": 1}
    assert not non
    ecart = next(r for r in ratt if r.id == "dsf2025-02_02").ecart
    assert ecart == ["MB imprimé 0 sur la fiche, null dans le fixture",
                     "MR/MA imprimé 0 sur la fiche, null dans le fixture"]


def test_legendes_identiques_rattachees_par_rang_et_dites():
    photos = [fx("x_03", 0, 2, "C"), fx("x_04", 0, 2, "C"), fx("x_05", 1, 1, "B")]
    legendes = [lire_legende("MB = 1 MR = 1 DEPERIS = B"), lire_legende("MB = 0 MR = 2 DEPERIS = C"),
                lire_legende("MB = 0 MR = 2 DEPERIS = C")]
    ratt, _ = rattacher(photos, legendes)
    par_id = {r.id: r for r in ratt}
    assert par_id["x_05"].image == 0 and par_id["x_05"].methode == "légende"
    assert (par_id["x_03"].image, par_id["x_04"].image) == (1, 2)
    assert par_id["x_03"].methode == "légende, puis rang parmi légendes identiques"


def test_photo_imprimee_absente_du_fixture_non_rattachee_et_dite():
    photos = [fx("y_05", 1, 2, "C"), fx("y_06", 4, 3, "F"), fx("y_07", 5, 5, "F")]
    legendes = [lire_legende("MB = 1 MR = 2 DEPERIS = C"), lire_legende("MB = 1 MR = 3 DEPERIS = D"),
                lire_legende("MB = 4 MR = 3 DEPERIS = F"), lire_legende("MB = 5 MR = 5 DEPERIS = Mort")]
    ratt, non = rattacher(photos, legendes)
    assert {r.id: r.image for r in ratt} == {"y_05": 0, "y_06": 2, "y_07": 3}
    assert [n.image for n in non] == [1] and "aucun id du fixture compatible" in non[0].motif


def test_dessin_sans_legende_en_surnombre_non_rattache():
    photos = [fx("z_01", None, None, "A"), fx("z_02", 0, 1, "B")]
    legendes = [lire_legende(""), lire_legende("Arbre de référence MB = 0 MA = 0 DEPERIS = 0"),
                lire_legende("MB = 0 MA = 1 DEPERIS = B")]
    ratt, non = rattacher(photos, legendes)
    assert {r.id: r.image for r in ratt} == {"z_01": 1, "z_02": 2}
    assert [n.image for n in non] == [0]


def test_image_muette_prend_l_id_restant_et_le_dit():
    photos = [fx("w_09", 4, 4, "F"), fx("w_10", 5, 0, "F")]
    legendes = [lire_legende("MB = 4 MR = 4 DEPERIS = F"), lire_legende("")]
    ratt, _ = rattacher(photos, legendes)
    r = next(r for r in ratt if r.id == "w_10")
    assert r.image == 1 and r.methode.startswith("rang")
    assert "classe F dans le fixture, non lu sur la fiche" in r.ecart


def test_id_sans_image_refuse():
    with pytest.raises(ValueError):
        rattacher([fx("v_01", 0, 0, "A"), fx("v_02", 1, 1, "B")], [lire_legende("MB = 0 MR = 0 DEPERIS = A")])


def test_incoherence_connue_rattachee_par_sa_legende_sans_correction():
    # dsf2025-17_08 : MB 0 / MA 3 / E imprimé (l'abaque dirait D) : rattachée telle quelle, sans écart.
    photos = [fx("dsf2025-17_05", 0, 3, "D"), fx("dsf2025-17_08", 0, 3, "E")]
    legendes = [lire_legende("MB = 0 MA = 3 DEPERIS = D"), lire_legende("MB = 0 MA = 3 DEPERIS = E")]
    ratt, _ = rattacher(photos, legendes)
    assert {r.id: r.image for r in ratt} == {"dsf2025-17_05": 0, "dsf2025-17_08": 1}
    assert all(r.methode == "légende" and not r.ecart for r in ratt)
