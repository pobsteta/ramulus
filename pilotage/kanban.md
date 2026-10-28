# Kanban de Ramulus

> Cinq colonnes (fiche n° 8). Chaque carte porte l'Homme (qui), la Mission (quoi, quand) et les Moyens (comment,
> avec quoi), et renvoie à sa fiche dans `pilotage/missions/`. Tenu à jour par l'orchestrateur à chaque fin de cycle.
>
> **Dernier cycle : 1** (2026-10-28). Chaque carte qui change de colonne porte « depuis le cycle N ».

## Bac à sable

- **ARCHI** (diagnostic architectural, C. Drénou) : tranche ultérieure ou hors périmètre, à trancher au brief.
- **Deuxième essai IA** : même jeu, avec la fiche de référence de l'essence fournie et une consigne contre la
  clémence sur MB ; mesurer le gain avant d'écrire l'ADR 0002.
- **Bibliothèques communes** (`pobsteta/terrain`) : `terrain-gnss`, `terrain-journal`, `terrain-style` à consommer
  plutôt qu'à recopier.

## Planifié

- **001** `acquereur` · référentiels de la tranche 1 · [fiche](missions/001-referentiels-tranche-1.md) · **bloquée**
  depuis le cycle 1 : sources injoignables (réseau de l'environnement), à relancer quand Pascal aura ouvert les hôtes
  ou déposé les PDF.
- **003** `testeur` · tests d'acceptation de l'échange, après 002 · [fiche](missions/003-tests-acceptation-echange.md)
- **004 à 009** : fiches à écrire au cycle suivant (voir `equipe.md`).
- **Orchestrateur** : brief vers Nemeton après 002.
- **Pascal** : signaler au DSF les quatre incohérences des fiches (pin d'Alep, pin laricio, deux pins sylvestres) ;
  relevé : `docs/essais/2026-10-28-essai-ia-a-l-aveugle.md`.

## En cours

_Rien._

## En revue

- **002** `dev` (opus, tenue) · format d'échange `deperis-v1` · [fiche](missions/002-format-echange-deperis-v1.md) ·
  **plan** à relire par Pascal : [`missions/002-plan.md`](missions/002-plan.md) (Q1 à Q13, moyens M1 et M2) · depuis
  le cycle 1.

## Fait

- **Socle** (orchestrateur) : PR n° 1 fusionnée par Pascal le 2026-10-28 (porte n° 3) · depuis le cycle 1.
- **Porte n° 2** (2026-10-28) : équipe validée telle que proposée (`pilotage/equipe.md`).
- **Porte n° 1** (2026-10-28) : brief et spec validés, ADR 0001 à 0006 acceptées.

## Livraisons

> Une ligne par cycle : ce qu'un bénéficiaire du brief peut prendre en main, ou la mauvaise nouvelle et ce qu'elle
> nous apprend.
- **cycle 1** (2026-10-28) · **mauvaise nouvelle** : rien d'utilisable par un bénéficiaire. Ce qu'elle nous apprend :
  à `/mission`, la question « a-t-il les moyens ? » a reçu « oui » pour 001 et 002 sans vérifier l'environnement
  (réseau fermé aux sources, Groma et Surculus absents, GDAL absent). Règle proposée : vérifier les moyens dans la
  session avant de répondre « oui ».
