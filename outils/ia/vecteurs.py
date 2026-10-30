"""Vecteurs de l'amorce : prétraitement des photos et extracteur MobileNetV3-Small (MediaPipe image_embedder).

Prétraitement (à reproduire à l'identique dans l'application, mission 027) :
1. décodage en RVB 8 bits ;
2. rotation d'affichage du PDF (colonne `rotation` du manifeste ; sur le téléphone, l'orientation de la photo) ;
3. fiches à tracé incrusté seulement (23 Chêne vert, 24 Hêtre hiver) : masquage des traits rouges et verts,
   comblés par les pixels voisins non masqués (rien sur le téléphone : ses photos n'ont pas de tracé) ;
4. redimensionnement de l'image entière à 224 x 224, bilinéaire, sans recadrage (le houppier est souvent au bord) ;
5. valeurs ramenées entre 0 et 1 (métadonnées du modèle : « min is [0.0] and max is [1.0] ») ;
6. sortie `feature_vector` de 1024 valeurs, normalisée L2 (similarité cosinus = produit scalaire).
"""
from __future__ import annotations

import csv
import sys

import numpy as np
from PIL import Image, ImageFilter

from sources import DSF, EXTRACTEUR, PHOTOS, sha256

TAILLE = 224
VECTEURS = DSF / "vecteurs.npz"


def masque_trace(a: np.ndarray) -> np.ndarray:
    """Pixels des tracés incrustés : rouge des contours (halos rosés du JPEG compris), vert pur de la ligne du
    houppier fonctionnel. Règle de couleur calibrée sur les fiches 23 et 24, appliquée à elles seules : ailleurs
    elle prendrait du feuillage (31 pour mille sur dsf2025-02_04)."""
    r, g, b = (a[..., i].astype(int) for i in range(3))
    rouge = (r > 110) & (r - g > 45) & (r - b > 35)
    vert = (g - r > 40) & (g - b > 40) & (r < 70)
    return rouge | vert


def _somme_boite(x: np.ndarray, rayon: int) -> np.ndarray:
    """Somme de x sur une fenêtre carrée de côté 2 * rayon + 1 (bords tronqués), par image intégrale."""
    h, w = x.shape
    ii = np.zeros((h + 1, w + 1), dtype=np.float64)
    ii[1:, 1:] = np.cumsum(np.cumsum(x, axis=0), axis=1)
    y0 = np.clip(np.arange(h) - rayon, 0, h)
    y1 = np.clip(np.arange(h) + rayon + 1, 0, h)
    x0 = np.clip(np.arange(w) - rayon, 0, w)
    x1 = np.clip(np.arange(w) + rayon + 1, 0, w)
    return (ii[y1][:, x1] - ii[y0][:, x1] - ii[y1][:, x0] + ii[y0][:, x0]).astype(np.float32)


def _combler(a: np.ndarray, masque: np.ndarray) -> np.ndarray:
    """Comble les pixels masqués par la moyenne des pixels non masqués voisins (convolution normalisée), en
    élargissant le rayon jusqu'à tout couvrir. Aucun pixel masqué ne sert au comblement."""
    valide = (~masque).astype(np.float32)
    out = a.astype(np.float32).copy()
    reste = masque.copy()
    rayon = 3
    while reste.any() and rayon <= 96:
        flou = lambda x: _somme_boite(x, rayon)
        poids = flou(valide)
        for c in range(3):
            somme = flou((out[..., c] * valide).astype(np.float32))
            ok = reste & (poids > 0.5)
            out[..., c][ok] = somme[ok] / poids[ok]
        reste &= ~(poids > 0.5)
        rayon *= 2
    return np.clip(out + 0.5, 0, 255).astype(np.uint8)


def masquer(im: Image.Image) -> tuple[Image.Image, float]:
    """Masque les tracés incrustés (dilatés de 3 px) et les comble. Rend l'image et la part masquée."""
    a = np.asarray(im)
    m = Image.fromarray((masque_trace(a) * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(7))
    mm = np.asarray(m) > 0
    if not mm.any():
        return im, 0.0
    return Image.fromarray(_combler(a, mm)), float(mm.mean())


def pretraiter(chemin, rotation: int, masquer_trace: bool) -> tuple[np.ndarray, float]:
    im = Image.open(chemin).convert("RGB")
    if rotation:
        im = im.rotate(-rotation, expand=True)   # rotation horaire de `rotation` degrés
    part = 0.0
    if masquer_trace:
        im, part = masquer(im)
    im = im.resize((TAILLE, TAILLE), Image.Resampling.BILINEAR)
    return (np.asarray(im, dtype=np.float32) / 255.0)[None, ...], part


class Extracteur:
    def __init__(self, chemin=EXTRACTEUR):
        from ai_edge_litert.interpreter import Interpreter
        self.it = Interpreter(model_path=str(chemin))
        self.it.allocate_tensors()
        self.entree = self.it.get_input_details()[0]["index"]
        self.sortie = self.it.get_output_details()[0]["index"]
        self.empreinte = sha256(chemin)

    def __call__(self, x: np.ndarray) -> np.ndarray:
        self.it.set_tensor(self.entree, x)
        self.it.invoke()
        v = self.it.get_tensor(self.sortie)[0].astype(np.float32)
        return v / np.linalg.norm(v)


def fiches_a_trace_incruste(manifeste: list[dict]) -> set[str]:
    """Fiches dont aucune photo ne porte de tracé vectoriel : le tracé y est dans les pixels (vérifié à l'œil)."""
    par_fiche: dict[str, list[str]] = {}
    for l in manifeste:
        par_fiche.setdefault(l["pdf_source"], []).append(l["trace_vectoriel"])
    return {f for f, t in par_fiche.items() if not any(t)}


def calculer() -> None:
    with open(DSF / "manifeste.csv", encoding="utf-8") as f:
        man = list(csv.DictReader(f))
    incruste = fiches_a_trace_incruste(man)
    ext = Extracteur()
    ids, vec, parts = [], [], []
    for l in man:
        x, part = pretraiter(PHOTOS / l["fichier"], int(l["rotation"]), l["pdf_source"] in incruste)
        ids.append(l["id"])
        vec.append(ext(x))
        parts.append(part)
    np.savez(VECTEURS, ids=np.array(ids), vecteurs=np.stack(vec), part_masquee=np.array(parts),
             extracteur_sha256=np.array(ext.empreinte))
    masquees = [(i, p) for i, p in zip(ids, parts) if p > 0]
    print(f"{len(ids)} vecteurs de {vec[0].shape[0]} valeurs -> {VECTEURS}")
    print(f"fiches à tracé incrusté, masquées : {sorted(incruste)} ; {len(masquees)} photos, part masquée "
          f"de {min(p for _, p in masquees):.2%} à {max(p for _, p in masquees):.2%}" if masquees else "aucun masquage")


if __name__ == "__main__":
    if "--apercu" in sys.argv:
        # aperçu du prétraitement (avant redimensionnement) pour contrôle à l'œil : photos/../controle/
        with open(DSF / "manifeste.csv", encoding="utf-8") as f:
            man = {l["id"]: l for l in csv.DictReader(f)}
        incruste = fiches_a_trace_incruste(list(man.values()))
        for i in sys.argv[sys.argv.index("--apercu") + 1:]:
            l = man[i]
            im = Image.open(PHOTOS / l["fichier"]).convert("RGB").rotate(-int(l["rotation"]), expand=True)
            if l["pdf_source"] in incruste:
                im, _ = masquer(im)
            im.thumbnail((400, 400))
            im.save(DSF / "controle" / f"apercu-{i}.png")
    else:
        calculer()
