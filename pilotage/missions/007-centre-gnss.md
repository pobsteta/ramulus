# Mission 007 — Relever le centre au GNSS du téléphone

## Mission

**En une phrase (quoi, et pourquoi) :** relever le centre de la placette au GNSS du téléphone, en moyennant plusieurs
mesures, et signaler sans bloquer un écart de plus de 30 m au point prévu. Le but : rattacher la placette (ADR 0004).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | reprise de Surculus `app/…/gnss/` (`SourcePosition.kt`) ; `docs/referentiels/gnss-telephone.yaml` ; ADR 0004 |
| A-t-il les moyens ? | oui, vérifié le 2026-10-28 | référentiel 001 fusionné ; Surculus en local |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :**
  - le RTK (une seule source ici : `telephone`) ;
  - l'écran de placette (006), qui intègre le composant ;
  - l'écriture dans le GeoPackage (`EchangeDeperis.enregistrerCentre`, 004).

## Harnais

- **Peut :**
  - écrire dans `:core` la moyenne des mesures, la conversion WGS84 → Lambert-93 (le `Lambert93` de 004 sert à
    contrôler l'emprise, et la conversion doit être testée sur un point connu de l'IGN) et l'écart au point prévu,
    avec leurs tests JVM ;
  - écrire dans `:app` une `SourcePosition` (LocationManager, fournisseur GPS) reprise de Surculus, et un composant
    Compose « Relever le centre » ;
  - ajouter les permissions de localisation au manifeste.
- **Ne doit pas :**
  - envoyer une position au réseau ;
  - bloquer l'opérateur sur l'écart ;
  - inventer un seuil (30 m, **provisoire**, ADR 0004 et décision G1) ;
  - toucher à `main`.
- **Sources autorisées :**
  - ADR 0004 ; `docs/referentiels/gnss-telephone.yaml` ; `docs/formats/deperis-v1.md` (table `centres`) ;
  - Surculus, au commit 3c01f4b : `app/src/main/kotlin/…/gnss/`, `docs/adr/0003-gnss-et-reperes.md` ;
  - la page IGN des paramètres de Lambert-93 (EPSG:2154), pour la conversion et son test.

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** le seuil de 30 m (G1) ; le nombre de mesures moyennées, repris de Surculus s'il y est fixé.
  Sinon, c'est une valeur provisoire nommée.
- **Mesurée par :** Pascal, avec 5 relevés d'un même piquet sous couvert.
- **Gel :** avant la première campagne.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune ; part de `main`.
- **Ordre de fusion :** avant 006.
- **PR en brouillon jusqu'à :** —
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :**
  - un point IGN connu est converti en Lambert-93 au mètre près ;
  - la moyenne, la précision et `nb_mesures` sont justes ;
  - un écart de plus de 30 m est signalé, et un écart de 30 m ou moins ne l'est pas ;
  - une position hors de France est refusée (la placette en Somalie) ;
  - `.claude/test-command` est vert.

## Terrain

- **Échantillon que Pascal vérifiera :** relever un piquet ; voir la précision, le nombre de mesures et l'écart au
  point prévu.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la PR.
- **Qui tranche :** l'orchestrateur, par délégation ; chaque arbitrage est inscrit au journal.

## Comment saura-t-on que ça marche ?

Sur le téléphone, le centre relevé tombe à quelques mètres du piquet, et `centres` du `.zip` rendu le porte.
