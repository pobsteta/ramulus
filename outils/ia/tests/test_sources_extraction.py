"""Tests des sources (fixture, référentiel) et des fonctions pures de l'extraction et du prétraitement."""
import numpy as np
import pytest

from sources import charger_fixture, numero_fiche, urls_par_fiche

pymupdf = pytest.importorskip("pymupdf")
from extraction import _recouvrement, _rotation, couleur_trait, dedoublonner  # noqa: E402
from vecteurs import _combler, fiches_a_trace_incruste, masque_trace  # noqa: E402


def test_fixture_250_photos_25_fiches_ids_uniques():
    fx = charger_fixture()
    ids = [p["id"] for p in fx["photos"]]
    assert len(ids) == 250 == len(set(ids))
    assert sorted({numero_fiche(i) for i in ids}) == [f"{n:02d}" for n in range(25)]
    assert fx["incoherences_connues"] == ["dsf2025-16_08", "dsf2025-17_08", "dsf2025-19_07", "dsf2025-19_08"]


def test_urls_lues_par_essence_et_saison():
    urls = urls_par_fiche(charger_fixture())
    assert len(urls) == 25 and len(set(urls.values())) == 25
    assert urls["02"] == "https://agriculture.gouv.fr/telecharger/126314"   # Chêne pédonculé été (rapport 025)
    assert urls["03"] == "https://agriculture.gouv.fr/telecharger/126315"   # Chêne pédonculé hiver
    assert urls["15"] == "https://agriculture.gouv.fr/telecharger/150910"   # Pin à crochets
    # Écart listé : Chêne liège « TOUTES » dans le fixture, lien unique rangé en colonne été dans le CSV.
    assert urls["01"] == "https://agriculture.gouv.fr/telecharger/149149"


def test_numero_fiche():
    assert numero_fiche("dsf2025-02_01") == "02"
    with pytest.raises(ValueError):
        numero_fiche("autre-02_01")


@pytest.mark.parametrize("transform,attendu", [
    ((241.34, 0.0, 0.0, 299.86, 45.5, 57.0), 0),
    ((241.34, 2e-5, -3e-5, 299.86, 45.5, 57.0), 0),          # termes croisés minuscules : pas de rotation
    ((-0.0, 320.6, -240.4, -0.0, 285.1, 47.4), 90),           # fiche 01 p. 3 (vérifié à l'œil)
    ((0.0, -291.75, 233.4, -0.0, 45.0, 743.6), 270),          # fiche 04 p. 3 (vérifié à l'œil)
    ((-200.0, 0.0, 0.0, -300.0, 0, 0), 180),
])
def test_rotation(transform, attendu):
    assert _rotation(transform) == attendu


def test_couleur_trait():
    assert couleur_trait((0.4, 0.9, 0.5)) == "vert"
    assert couleur_trait((1.0, 0.0, 0.0)) == "rouge"
    assert couleur_trait((0.0, 0.0, 0.0)) == ""
    assert couleur_trait(None) == ""


def test_dedoublonner_garde_le_placement_dans_la_page_et_l_image_du_dessus():
    page = pymupdf.Rect(0, 0, 595, 842)
    infos = [
        {"xref": 47, "bbox": (45, 57, 287, 357)},
        {"xref": 47, "bbox": (-8, 71, 296, 448)},       # même image, copie qui déborde (fiche 23 p. 3)
        {"xref": 75, "bbox": (313, 45, 524, 342)},
        {"xref": 76, "bbox": (313, 45, 524, 342)},      # dessinée par-dessus (fiche 04 p. 4)
    ]
    out = dedoublonner(infos, page)
    assert [(i["xref"], tuple(i["bbox"])) for i in out] == [(47, (45, 57, 287, 357)), (76, (313, 45, 524, 342))]


def test_recouvrement():
    assert _recouvrement((0, 0, 10, 10), (0, 0, 10, 10)) == 1
    assert _recouvrement((0, 0, 10, 10), (20, 20, 30, 30)) == 0
    assert _recouvrement((0, 0, 10, 10), (5, 0, 15, 10)) == pytest.approx(0.5)


def test_masque_trace_prend_le_rouge_et_le_vert_purs_pas_le_ciel():
    a = np.zeros((1, 4, 3), dtype=np.uint8)
    a[0, 0] = (230, 20, 20)      # contour rouge
    a[0, 1] = (40, 150, 50)      # ligne verte de la fiche 24
    a[0, 2] = (180, 200, 230)    # ciel
    a[0, 3] = (110, 120, 90)     # feuillage terne
    assert masque_trace(a).tolist() == [[True, True, False, False]]


def test_combler_n_utilise_que_les_pixels_non_masques():
    a = np.full((20, 20, 3), 100, dtype=np.uint8)
    a[8:12, 8:12] = (255, 0, 0)
    m = np.zeros((20, 20), dtype=bool)
    m[8:12, 8:12] = True
    out = _combler(a, m)
    assert (out[8:12, 8:12] == 100).all()
    assert (out[~m] == 100).all()


def test_fiches_a_trace_incruste():
    man = [{"pdf_source": "01.pdf", "trace_vectoriel": "vert"}, {"pdf_source": "01.pdf", "trace_vectoriel": ""},
           {"pdf_source": "23.pdf", "trace_vectoriel": ""}, {"pdf_source": "23.pdf", "trace_vectoriel": ""}]
    assert fiches_a_trace_incruste(man) == {"23.pdf"}
