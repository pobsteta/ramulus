# Mission 026 — Fabriquer et mesurer l'amorce de l'IA embarquée à partir des photos du DSF

## Mission

**En une phrase (quoi, et pourquoi) :** extraire hors du dépôt les 250 photos notées des fiches DSF, en tirer avec
l'extracteur MobileNetV3-Small les vecteurs de l'amorce, mesurer celle-ci en validation croisée par fiche laissée de
côté, et produire les fichiers que l'application embarquera, pour que l'intégration (mission 027) parte d'une amorce
mesurée (ADR 0007).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | ADR 0007 (avec l'ajout du 2026-10-30) ; rapport `docs/essais/2026-10-30-moyens-ia-embarquee.md` (§ 5 : procédure d'extraction) |
| A-t-il les moyens ? | à vérifier en premier | réseau vers agriculture.gouv.fr et vers le modèle MediaPipe ; Python 3 avec numpy, PyMuPDF ou pdfplumber, `ai-edge-litert` ou `tflite-runtime` |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** opus
- **Ce qui ne lui est pas demandé :** l'intégration dans l'application (027) ; l'apprentissage sur l'appareil (027).

## Décisions qui s'imposent

ADR 0007, rubrique « Ajout du 2026-10-30 », en particulier : amorce sur les photos DSF, tête par plus proches voisins,
vote dans la même essence ; tracé vert masqué ou photo recadrée ; quatre incohérences connues (`16_08`, `17_08`,
`19_07`, `19_08`) exclues ; photos à notes nulles exclues de l'amorce et listées ; seuil et mesure de l'ADR 0002.
L'extracteur : `mobilenet_v3_small` LiteRT de MediaPipe (Apache-2.0), **en version « embedder »** si elle existe (un
vecteur), sinon l'avant-dernière couche ; choix écrit et sourcé.

## Ce qu'il rend

1. **Photos** sous `~/dev/donnees-ramulus/dsf-2025/photos/<id>.<ext>`, rattachées par la légende et non par le rang
   (piège de la fiche 126314, p. 3), avec un manifeste (id, fichier, SHA-256, page, notes) ; dix photos tirées au
   hasard montrées dans le rendu (chemins) pour le contrôle de Pascal ; écarts listés, jamais corrigés.
2. **Script** reproductible dans le dépôt (`outils/ia/amorce.py` ou équivalent), avec ses dépendances écrites ; aucune
   photo ni aucun poids de modèle commité.
3. **Mesure** en validation croisée par fiche laissée de côté : classe exacte, à un près, erreur sur MB, rappel D-E-F
   et F, par essence et saison quand l'effectif le permet ; comparée aux 54 % et 97 % de l'essai du 2026-10-28 ; écrite
   dans `docs/essais/2026-10-30-amorce-ia.md` avec la fiche modèle (données, droits, mesures, limites).
4. **Fichiers à embarquer** (hors dépôt si lourds, sinon dans `app/src/main/assets/ia/`) : l'extracteur `.tflite`, les
   vecteurs de l'amorce avec essence, saison et notes, et leur empreinte ; leur taille.

## Harnais

- **Peut :** télécharger les PDF et le modèle dans `~/dev/donnees-ramulus/` ; installer des paquets Python dans un
  environnement virtuel hors dépôt ; écrire le script, ses tests et le rapport dans sa branche.
- **Ne doit pas :** commiter une photo du DSF ; envoyer une photo à un service distant ; modifier `fixtures/fiches-dsf-2025.json`
  (un écart se liste) ; toucher à l'application ; toucher à `main`.
- **Sources autorisées :** ADR 0001, 0002 et 0007 ; rapports d'essai du 2026-10-28 et du 2026-10-30 ;
  `fixtures/fiches-dsf-2025.json` ; `docs/referentiels/deperis/` (abaque, essences) ; documentation MediaPipe et LiteRT.

## Valeur provisoire (chiffre, réglage)

- `k` des plus proches voisins et la règle de vote : provisoires, choisis par la validation croisée, nommés dans le
  rapport.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** ADR 0007 acceptée. Branche `mission/026-amorce-ia-dsf` depuis `origin/main`.
- **CI rouge attendue :** aucune ; le script a ses tests (vote, abaque, découpage par fiche) lancés hors de la commande
  Gradle, la commande à lancer est écrite.

## Tests

- **Comment saura-t-on que c'est juste :** le manifeste compte les photos réellement extraites (le nombre vient de la
  commande) ; aucune photo d'une fiche ne sert à prédire une photo de la même fiche ; la classe vient de l'abaque ; la
  mesure est rejouable par une commande.

## Terrain

- **Échantillon que Pascal vérifiera :** les dix photos tirées au hasard face à leur page et à leur note.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Qui tranche :** l'orchestrateur, par délégation ; ce qui touche au sens (seuil, droits) : Pascal.

## Comment saura-t-on que ça marche ?

Un chiffre de l'amorce, mesuré sans fuite, est écrit à côté de celui de l'essai, et 027 n'a plus qu'à embarquer.
