# Kanban de Ramulus

> Cinq colonnes (fiche n° 8). Chaque carte porte l'Homme (qui), la Mission (quoi, quand) et les Moyens (comment,
> avec quoi), et renvoie à sa fiche dans `pilotage/missions/`. Tenu à jour par l'orchestrateur à chaque fin de cycle.

## Bac à sable

- **ARCHI** (diagnostic architectural, C. Drénou) : tranche ultérieure ou hors périmètre, à trancher au brief.
- **Signaler au DSF** les quatre incohérences des fiches (pin d'Alep, pin laricio, deux pins sylvestres).
- **Deuxième essai IA** : même jeu, avec la fiche de référence de l'essence fournie et une consigne contre la
  clémence sur MB ; mesurer le gain avant d'écrire l'ADR 0002.
- **Bibliothèques communes** (`pobsteta/terrain`) : `terrain-gnss`, `terrain-journal`, `terrain-style` à consommer
  plutôt qu'à recopier.

## Planifié

- **Orchestrateur** : premier cycle (001 et 002 en parallèle, puis 003), à lancer dans une session où le plugin
  `imperatrice` est chargé (option a du 2026-10-28).
- **001** `acquereur` · référentiels de la tranche 1 · [fiche](missions/001-referentiels-tranche-1.md)
- **002** `dev` (opus, tenue) · format d'échange `deperis-v1` · [fiche](missions/002-format-echange-deperis-v1.md)
- **003** `testeur` · tests d'acceptation de l'échange, après 002 · [fiche](missions/003-tests-acceptation-echange.md)
- **004 à 009** : fiches à écrire au cycle suivant (voir `equipe.md`).
- **Orchestrateur** : brief vers Nemeton après 002.

## En cours

_Rien._

## En revue

- **Socle** (orchestrateur, 2026-10-28) : brief et spec en brouillon, ADR 0001, référentiel DEPERIS, domaine
  `:core` testé, écran de notation manuelle, CI. Fiche : [`missions/000-socle.md`](missions/000-socle.md).

## Fait

- **Porte n° 2** (2026-10-28) : équipe validée telle que proposée (`pilotage/equipe.md`).
- **Porte n° 1** (2026-10-28) : brief et spec validés, ADR 0001 à 0006 acceptées.
