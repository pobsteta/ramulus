# Outils de l'amorce IA (mission 026, ADR 0007)

Fabrique et mesure l'amorce de l'IA embarquée à partir des photos notées des fiches DEPERIS du DSF. Tout ce qui est
produit va **hors dépôt**, sous `~/dev/donnees-ramulus/` (ou `$RAMULUS_DONNEES`) : PDF, photos, vecteurs, modèle.
Aucune photo n'est envoyée à un service distant. Résultats et limites : `docs/essais/2026-10-30-amorce-ia.md`.

## Installer

```sh
python3 -m venv ~/dev/donnees-ramulus/venv
~/dev/donnees-ramulus/venv/bin/pip install -r outils/ia/requirements.txt
```

## Rejouer

```sh
~/dev/donnees-ramulus/venv/bin/python outils/ia/amorce.py              # les cinq étapes
~/dev/donnees-ramulus/venv/bin/python outils/ia/amorce.py mesurer      # une étape
```

| Étape | Module | Produit (sous `~/dev/donnees-ramulus/`) |
| --- | --- | --- |
| `telecharger` | `sources.py` | `dsf-2025/pdf/NN.pdf` + `SHA256SUMS` ; `modeles/mobilenet_v3_small.tflite` |
| `extraire` | `extraction.py`, `legendes.py` | `dsf-2025/photos/<id>.jpeg` (octets natifs), `manifeste.csv`, `ecarts.csv`, `images-non-rattachees.csv` |
| `vecteurs` | `vecteurs.py` | `dsf-2025/vecteurs.npz` (250 x 1024) |
| `mesurer` | `mesure.py`, `tete.py` | `dsf-2025/mesure.json` (validation croisée par fiche laissée de côté) |
| `exporter` | `exporter.py` | `ia-embarquee/` : extracteur, vecteurs et exemples de l'amorce, `amorce.json` |

`diag.py` affiche la mise en page des pages citées (images, légendes, dessins verts) ; il n'écrit rien.

## Tests

Hors de la commande Gradle (`.claude/test-command`) :

```sh
~/dev/donnees-ramulus/venv/bin/python -m pytest -q -p no:cacheprovider outils/ia/tests
```

Ils couvrent l'abaque (lu dans `docs/referentiels/deperis/abaque.csv`), le vote, le découpage par fiche (aucune photo
d'une fiche ne vote pour une photo de la même fiche), la lecture des légendes et le rattachement (piège de la fiche
126314, p. 3), la rotation, le dédoublonnage, le masquage des tracés incrustés. Ils ne demandent ni PDF ni modèle.
