# Kanban de Ramulus

> Cinq colonnes (fiche n° 8). Chaque carte porte l'Homme (qui), la Mission (quoi, quand) et les Moyens (comment,
> avec quoi), et renvoie à sa fiche dans `pilotage/missions/`. Tenu à jour par l'orchestrateur à chaque fin de cycle.
>
> **Dernier cycle : 2** (2026-10-28). Chaque carte qui change de colonne porte « depuis le cycle N ».

## Bac à sable

- **ARCHI** (diagnostic architectural, C. Drénou) : tranche ultérieure ou hors périmètre, à trancher au brief.
- **Deuxième essai IA** : même jeu, avec la fiche de référence de l'essence fournie et une consigne contre la
  clémence sur MB ; mesurer le gain avant d'écrire l'ADR 0002.
- **Bibliothèques communes** (`pobsteta/terrain`) : `terrain-gnss`, `terrain-journal`, `terrain-style` à consommer
  plutôt qu'à recopier.

## Planifié

- **004, 006 à 009** : fiches à écrire au cycle 3 (voir `equipe.md`).
- **010** (à ficher) · amender `deperis-v1` : placette « notee » au centre en conflit gardée et signalée (F1, Pascal
  2026-10-28) · depuis le cycle 2.
  Avec le motif `classe_saisie` (classe fournie refusée), puis test de 003 resserré par le `testeur`.
- **011** (à ficher) · voies de sauvegarde hors appareil, signal du matin, test instrumenté MediaStore et
  `synchronous` sur Android 8 à 10 (Q12, S2) · depuis le cycle 2.
- **Orchestrateur** : fiche 004 (interface `EchangeDeperis`, CI avec `RAMULUS_ECHANGE_EXIGER=1`) ; brief vers Nemeton.
- **012** (à ficher) · `dataExtractionRules` (Q14) · depuis le cycle 2.
- **Pascal** : ouvrir l'issue vers Surculus (citation de Purfürst, brief
  `briefs/vers-surculus/2026-10-28-ramulus-citation-purfurst.md`, note déposée dans `~/dev/briefs/vers-surculus/`) ; et le brief sur le trou `INSERT OR REPLACE`.
- **Pascal** : signaler au DSF les quatre incohérences des fiches (pin d'Alep, pin laricio, deux pins sylvestres) ;
  relevé : `docs/essais/2026-10-28-essai-ia-a-l-aveugle.md`.

## En cours

_Rien._

## En revue

- **005** `dev` (opus, tenue) · journal en ajout seul et sauvegarde `.ramsync` ·
  [fiche](missions/005-journal-et-sauvegarde.md) · PR n° 11, prête (aller-retour 1/3) · depuis le cycle 2.

## Fait

- **003** `testeur` · tests d'acceptation de l'échange · PR n° 10 fusionnée par Pascal le 2026-10-28 ·
  depuis le cycle 2.
- **Pilotage du cycle 2** · PR n° 9 fusionnée par Pascal le 2026-10-28 · depuis le cycle 2.
- **001** `acquereur` · référentiels de la tranche 1 · PR n° 7 fusionnée par Pascal le 2026-10-28 · sept valeurs
  tranchées par Pascal le 2026-10-28 (K1, SA1, SA2, G1, G2, PV1, PV2) · depuis le cycle 2.
- **002** `dev` · format d'échange `deperis-v1` · PR n° 5 fusionnée par Pascal le 2026-10-28 · placette « notee »
  au centre en conflit tranchée (F1), à écrire par 010 · depuis le cycle 2.
- **Journal de 002** · PR n° 6 fusionnée par Pascal le 2026-10-28 · depuis le cycle 2.
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
- **cycle 2** (2026-10-28) · pour un **correspondant-observateur DSF** : les règles de terrain sourcées
  (`docs/referentiels/` : Kraft, saison, distance et lumière de prise de vue, précision GNSS sous couvert) et le paquet
  d'exemple `fixtures/echange/A3-rendu/EXEMPLE-2027-01.zip`, qu'il ouvre dans QField. De plus que le cycle 1 : un
  premier objet à confronter au terrain, là où le cycle 1 ne livrait rien d'utilisable. Proposée par l'orchestrateur,
  retenue par Pascal le 2026-10-28.
