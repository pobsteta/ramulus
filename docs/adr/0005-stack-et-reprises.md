# ADR 0005 — Stack et reprises : la pile de Surculus, son journal et sa sauvegarde

- **Date :** 2026-10-28
- **Statut :** proposée (reprise acceptée sur le principe par Pascal le 2026-10-28)
- **Proposée par :** Claude (orchestrateur)
- **Tranchée par :** Pascal

## La décision en une phrase

Ramulus reprend la pile de Surculus (Kotlin, Jetpack Compose, Room pour le journal, sqlite-jdbc pour le
GeoPackage, minSdk 26, cible 36) et sa sauvegarde en trois voies (ADR 0005 de Surculus), avec un journal en ajout
seul dont la fusion est une union sur l'UUID, jamais « la dernière écriture gagne ».

## Contexte

- Pascal, 2026-10-28 : reprendre telles quelles les décisions de Groma et de Surculus.
- Surculus ADR 0004 (stack), 0005 (stockage et sauvegarde), 0011 (GeoPackage sur appareil) ; versions dans
  `gradle/libs.versions.toml`, reprises par la PR n° 1.
- Contradicteur (2026-10-28) : `terrain-journal` fusionne en « la dernière écriture gagne » (Surculus ADR 0008),
  ce qui perdrait une notation ou une contre-notation.

## Ce qui est repris

| Élément | Repris de | Adaptation |
|---|---|---|
| Kotlin, Compose, Material 3, minSdk 26, cible 36 | Surculus ADR 0004 | aucune |
| Journal Room en ajout seul, `synchronous=FULL`, pas de migration destructive | Surculus ADR 0005 | une notation = un UUID |
| Sauvegarde : carte SD ou clé USB, téléphone du binôme, envoi quand le réseau passe ; photos écrites dès la prise par MediaStore | Surculus ADR 0005 | fichier `.ramsync` |
| GeoPackage avec sqlite-jdbc et ses natifs Android | Surculus ADR 0011 | aucune |
| Thème commun Néméton | Surculus mission 038 | déjà fait (PR n° 1) |
| Bibliothèques `terrain-gnss`, `terrain-style` | Surculus ADR 0008 | quand publiées |
| **Non repris** : `terrain-journal` | — | fusion « dernière écriture gagne » incompatible |

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

## Sources

- Surculus, `docs/adr/0004`, `0005`, `0008`, `0011` ; `gradle/libs.versions.toml` (commit b9cf9d6).

## Question de contrôle

Cette décision tient-elle nos six valeurs, et que sommes-nous prêts à payer pour cela ?
