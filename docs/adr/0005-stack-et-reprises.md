# ADR 0005 — Stack et reprises : la pile de Surculus, son journal et sa sauvegarde

- **Date :** 2026-10-28
- **Statut :** proposée (reprise acceptée sur le principe par Pascal le 2026-10-28)
- **Proposée par :** Claude (orchestrateur)
- **Tranchée par :** Pascal

## La décision en une phrase

Ramulus reprend la pile de Surculus (Kotlin, Jetpack Compose, Room pour le journal, sqlite-jdbc pour le
GeoPackage, minSdk 26, cible 36) et son journal et sa sauvegarde ligne à ligne (ADR 0005 de Surculus) ; de la
bibliothèque `terrain`, seul le style est repris, les autres briques attendant l'ADR 0012 de Surculus.

## Contexte

- Pascal, 2026-10-28 : reprendre telles quelles les décisions de Groma et de Surculus.
- Surculus ADR 0004 (stack), 0005 (stockage et sauvegarde), 0011 (GeoPackage sur appareil) ; minSdk et cible :
  `surculus/app/build.gradle.kts` (l. 66-67). Versions : la PR n° 1 de Ramulus n'a repris que Kotlin, AGP,
  Compose, coroutines, JUnit et Gson ; Room, KSP, sqlite-jdbc et desugar seront repris de Surculus par la mission
  qui en aura besoin.
- Surculus ADR 0008 porte **sur le style seul** (`terrain-style`, publiée) ; les briques GNSS, journal et
  GeoPackage de `terrain` sont renvoyées à une ADR 0012 de Surculus, pas encore écrite (contradicteur,
  2026-10-28, vérifié par l'orchestrateur).
- Le journal de Marculus fusionne en « la dernière écriture gagne » ; Surculus l'a remplacé (ADR 0005).

## Ce qui est repris

| Élément | Repris de | Adaptation |
|---|---|---|
| Kotlin, Compose, Material 3 | Surculus ADR 0004 | aucune |
| minSdk 26, cible 36 | Surculus `app/build.gradle.kts` | aucune |
| Journal Room en ajout seul : une ligne par saisie (`uuid` v7, `objet_uuid`, `type` dont `annulation`, `remplace_uuid`, `operateur`, `appareil`, `sequence`, horodatages UTC et monotone) ; aucune mise à jour ni suppression (DAO et déclencheurs) ; `synchronous=FULL` ; pas de migration destructive | Surculus ADR 0005, l. 46 | une notation, une contre-notation, une photo = un objet |
| Ordre total déterministe ; deux corrections concurrentes **signalées comme conflit** | Surculus ADR 0005, l. 48 | aucune |
| Sauvegarde : à chaque fin de placette et au plus tard toutes les 20 saisies ou 5 minutes ; MediaStore `IS_PENDING` sous `Documents/Ramulus/` ; trois voies hors de l'appareil (carte SD ou clé USB, binôme, réseau) ; signalée sans bloquer à l'ouverture de la journée | Surculus ADR 0005, l. 50-53 | aucune |
| Format `.ramsync` : JSON Lines gzip, pied SHA-256, lit N-1 et refuse N+1, fichier abîmé lu ligne à ligne | Surculus ADR 0005, l. 55 ; `docs/formats/sursync-v1.md` | schéma propre à Ramulus |
| Confidentialité : fichiers nommés par le code de la tournée, sans nom d'agent ; finalité du champ `operateur` écrite ; employeurs informés | Surculus ADR 0005, l. 59 et 84-85 | aussi pour les photos (ADR 0006) |
| GeoPackage avec sqlite-jdbc et ses natifs Android | Surculus ADR 0011 | aucune |
| Thème : couleurs de Marculus v0.65.0, choix Système / Clair / Sombre | Surculus ADR 0008, amendements des 24 et 27 octobre | déjà fait sans le choix (PR n° 1) |
| **Non repris pour l'instant** : `terrain-gnss`, `terrain-journal`, `terrain-gpkg` | — | en attente de l'ADR 0012 de Surculus |

## Le crible FRANCE

| Lettre | Réponse | Ce que ça coûte | Prêts à payer ? |
|---|---|---|---|
| **F** | Rien de neuf à inventer | dépendre des choix de Surculus | oui |
| **R** | Un mainteneur pour trois applications sœurs | charge concentrée sur Pascal | oui, mêmes réflexes partout |
| **A** | Une faille trouvée dans Surculus vaut pour Ramulus : la corriger aux deux endroits | double correction tant que les briques ne sont pas en bibliothèque | oui |
| **N** | Autres piles (Flutter, web) non examinées | — | non examinées, dit |
| **C** | Aucune dépense nouvelle | — | — |
| **É** | Réutilise Surculus ; pousse à publier les briques communes | — | — |

## Valeurs sacrifiées

- **L'examen d'autres piles** : on ne les a pas regardées ; la cohérence de la famille passe avant.
- **L'autonomie vis-à-vis de Surculus** : les briques communes attendent son ADR 0012 ; d'ici là, copies signalées.

## Sources

- Surculus, `docs/adr/0004`, `0005`, `0008`, `0011` ; `gradle/libs.versions.toml` (commit b9cf9d6).

## Question de contrôle

Cette décision tient-elle nos six valeurs, et que sommes-nous prêts à payer pour cela ?
