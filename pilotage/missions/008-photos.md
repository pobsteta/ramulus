# Mission 008 — Photographier le houppier

## Mission

**En une phrase (quoi, et pourquoi) :** prendre dans Ramulus les photos du houppier de chaque arbre noté, selon le
régime de la placette (1 photo, ou 2 à 4 sur une placette « collecte ») et la convention de l'ADR 0006, sans galerie
ni EXIF, avec des contrôles signalés sans bloquer. Le but : constituer le jeu de photos notées qui entraînera l'IA
(ADR 0002).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | ADR 0006 ; `docs/referentiels/prise-de-vue-houppier.yaml` ; format `deperis-v1` (`photos`, R10a à R10c) ; écrans de 006 |
| A-t-il les moyens ? | oui, vérifié le 2026-10-29 | 001, 004, 005 et 007 fusionnées, 006 en relecture ; Google Maven joignable pour CameraX ; téléphone de Pascal branché |

**Temps de la main :** appuyer. Pascal a demandé le 2026-10-29 : « Il faut bien prendre une photo dans
l'application ? »

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :**
  - l'IA ;
  - la sortie des photos hors de l'appareil autrement que dans le `.zip` rendu (004) ;
  - le floutage des visages et des plaques, qui se fait côté jeu publié, chez Nemeton.

## Décisions qui s'imposent

- **Décisions de Pascal (2026-10-28, ADR 0006) :**
  - Une photo du houppier par arbre ; **2 à 4 à 90°** sur une placette `collecte_photos = 1` (valeur **provisoire**,
    PV1).
  - Photos stockées sous `Documents/Ramulus/` avec un `.nomedia` : aucune galerie ne les indexe.
  - Contrôles à la prise (flou, contre-jour, houppier coupé), **signalés sans bloquer**.
  - Aucune photo n'est envoyée à un service distant.
- **Format `deperis-v1` :**
  - chaque photo est nommée `photos/<uuid de la note>_<n>.jpg`, `n` partant de 1 ;
  - la colonne `photos` porte nom et SHA-256 ;
  - le `.zip` rendu contient chaque photo citée et rien d'autre (R10a à R10c).
- **Arbitrages de l'orchestrateur, par délégation de Pascal du 2026-10-28 :**
  - **Prise de vue dans Ramulus avec CameraX** (Apache-2.0), et jamais par l'appareil photo du téléphone
    (`ACTION_IMAGE_CAPTURE`), qui peut ranger la photo en galerie ou l'envoyer dans un nuage.
  - **JPEG écrit sans EXIF.** Position, heure et saison sont déjà dans le GeoPackage (ADR 0006).
  - **La note d'abord** : la photo vient après la saisie des notes de l'arbre, sur le même écran. Elle n'est **pas
    obligatoire** pour enregistrer l'arbre ; un arbre sans photo est signalé à l'écran (« photo manquante »).
  - **Contrôles :**
    - le flou est mesuré (variance du laplacien) ;
    - le contre-jour est estimé par l'histogramme ;
    - le houppier coupé est seulement un rappel de cadrage à l'écran ;
    - les seuils sont **provisoires**, nommés, et signalés sans blocage.
  - **Rappel de la convention à l'écran** : une hauteur d'arbre de distance, houppier entier, éviter le contre-jour ;
    sur une placette « collecte », 2 à 4 vues à 90°.

## Harnais

- **Peut :**
  - écrire dans `:app` la prise de vue, son stockage et ses contrôles ;
  - écrire dans `:core` les contrôles d'image testables en JVM et le calcul du SHA-256 ;
  - brancher la photo sur l'écran des arbres de 006, et passer `photos` à `enregistrerNotation` ;
  - ajouter CameraX au catalogue, avec sa source et sa licence dans `docs/dependances.md` ;
  - ajouter la permission caméra.
- **Ne doit pas :**
  - écrire une photo en galerie ;
  - garder un EXIF ;
  - bloquer l'opérateur ;
  - modifier `:core/echange` ou le format (une lacune remonte à l'orchestrateur) ;
  - toucher à `main`.
- **Sources autorisées :**
  - ADR 0002 et 0006 ;
  - `docs/referentiels/prise-de-vue-houppier.yaml` ;
  - `docs/formats/deperis-v1.md` ;
  - les fiches 004, 005 et 006 ;
  - le code de `:core`, de `:data` et de `:app` ;
  - la documentation de CameraX.

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :**
  - 2 à 4 vues à 90° (PV1) ;
  - les seuils de flou et de contre-jour ;
  - la résolution et la qualité JPEG retenues.
- **Mesurée par :** Pascal, sur le terrain, avant le gel de la mission 008.
- **Gel :** avant la première campagne de collecte.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 006 (écrans) ; part de la branche `mission/006-ecrans-tournee-notation`, et se met à jour de `main`
  par fusion après la fusion de 006.
- **Ordre de fusion :** 006 → 008.
- **PR en brouillon jusqu'à :** la fusion de 006.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :**
  - en JVM, les contrôles de flou et de contre-jour sur des images fabriquées ;
  - le SHA-256 ;
  - le nommage `photos/<uuid>_<n>.jpg` ;
  - un JPEG écrit sans EXIF ;
  - un rendu avec photos passe le contrôle (aucun R10a, R10b ni R10c) ;
  - `.claude/test-command` est vert.

## Terrain

- **Échantillon que Pascal vérifiera :**
  - noter un arbre, prendre sa photo ;
  - vérifier qu'elle n'apparaît pas dans la galerie ;
  - rendre le `.zip` ;
  - l'ouvrir dans QField et voir la photo citée par la note.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la PR.
- **Qui tranche :** l'orchestrateur, par délégation ; chaque arbitrage est inscrit au journal.

## Comment saura-t-on que ça marche ?

Sur le téléphone de Pascal, la photo d'un arbre noté entre dans le `.zip` rendu, sans passer par la galerie.
