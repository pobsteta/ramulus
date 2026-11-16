# Mission 025 — Réunir les moyens de l'IA embarquée : photos notées du DSF et extracteur libre

## Mission

**En une phrase (quoi, et pourquoi) :** réunir, hors du dépôt, les 250 photos notées des fiches DSF 2025 rattachées à
leurs notes, et choisir en comparant plusieurs sources un extracteur d'images pré-entraîné, petit, sous licence libre et
exécutable sur Android, pour que la mission d'IA embarquée (ADR 0007) parte avec ses moyens.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | `fixtures/fiches-dsf-2025.json` (id, page, notes) ; `docs/referentiels/deperis/essences.csv` (liens des PDF) ; essai du 2026-10-28 |
| A-t-il les moyens ? | à vérifier dans la session | réseau vers agriculture.gouv.fr et vers les dépôts de modèles ; outils d'extraction d'images de PDF |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `acquereur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** entraîner un modèle ; écrire du code d'application ; trancher les droits.

## Ce qu'il rend

1. **Photos** : les 250 photos extraites des PDF, nommées par l'`id` de `fiches-dsf-2025.json`, dans
   `~/dev/donnees-ramulus/dsf-2025/` (**hors du dépôt**, jamais commitées), avec un manifeste (id, fichier, SHA-256,
   page, notes) ; les écarts au rattachement listés, jamais corrigés en silence (les quatre incohérences connues
   restent listées).
2. **Droits** : ce que disent les PDF, la page du ministère et la licence etalab-2.0 sur la réutilisation des photos,
   cité mot pour mot avec l'URL ; aucune conclusion à la place de Pascal.
3. **Extracteur** : au moins trois candidats (par exemple MobileNetV3-Small, EfficientNet-Lite0, un autre), chacun avec
   licence des poids et du code, taille, format LiteRT/TFLite disponible ou conversion nécessaire, source citée ;
   une recommandation argumentée.
4. **Moteur Android** : LiteRT (ou TensorFlow Lite) : licence, coordonnées Maven, taille ajoutée à l'APK, compatibilité
   minSdk 26, source citée.

## Harnais

- **Peut :** télécharger les PDF du DSF et les poids de modèles dans `~/dev/donnees-ramulus/` ; écrire un rapport
  `docs/essais/2026-10-30-moyens-ia-embarquee.md` dans sa branche.
- **Ne doit pas :** commiter une photo ou un poids de modèle ; envoyer une photo à un service distant ; trancher les
  droits ; toucher à `main`.
- **Sources autorisées :** les PDF du DSF, la page DEPERIS du ministère, la licence etalab-2.0, les dépôts officiels des
  modèles (TensorFlow Hub/Kaggle Models, Google AI Edge, PyTorch), la documentation LiteRT.

## Valeur provisoire (chiffre, réglage)

- aucune

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune. Branche `mission/025-sources-ia-embarquee` depuis `origin/main`.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** le manifeste a 250 lignes, chaque photo a sa note et son SHA-256 ; chaque
  affirmation sur une licence cite sa source ; trois extracteurs au moins sont comparés.

## Terrain

- **Échantillon que Pascal vérifiera :** dix photos tirées au hasard, avec la page du PDF et la note : elles collent.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur ; les droits : Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.

## Comment saura-t-on que ça marche ?

La mission de développement de l'IA embarquée peut partir sans aller chercher elle-même ses données ni son modèle.
