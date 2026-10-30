# Kanban de Ramulus

> Cinq colonnes (fiche n° 8). Chaque carte porte l'Homme (qui), la Mission (quoi, quand) et les Moyens (comment,
> avec quoi), et renvoie à sa fiche dans `pilotage/missions/`. Tenu à jour par l'orchestrateur à chaque fin de cycle.
>
> **Dernier cycle : 5** (2026-10-30). Chaque carte qui change de colonne porte « depuis le cycle N ».

## Bac à sable

- **ARCHI** (diagnostic architectural, C. Drénou) : tranche ultérieure ou hors périmètre, à trancher au brief.
- **Deuxième essai IA** : même jeu, avec la fiche de référence de l'essence fournie et une consigne contre la
  clémence sur MB ; mesurer le gain avant d'écrire l'ADR 0002.
- **Bibliothèques communes** (`pobsteta/terrain`) : `terrain-style` consommé depuis 019 ; `terrain-gnss` et
  `terrain-journal` à consommer plutôt qu'à recopier.

## Planifié

- **Pascal** : trois questions du 2026-10-30 : rangement des photos orphelines après un plantage (R10c) ; confirmation
  avant d'abandonner l'arbre en cours et ses photos ; stockage des photos, privé ou `Documents/Ramulus/` (ADR 0006) ·
  depuis le cycle 4.
- **009** (à ficher) · rendu complet : fusion des opérateurs, mesure du temps de saisie · depuis le cycle 2.
- **011** (à ficher) · voies de sauvegarde hors appareil, signal du matin, test instrumenté MediaStore et
  `synchronous` sur Android 8 à 10 (Q12, S2) · depuis le cycle 2.
- **012** (à ficher) · `dataExtractionRules` (Q14) · depuis le cycle 2.
- **Orchestrateur** : brief vers Nemeton.
- **`testeur`** : resserrer le test « classe fournie » de 003 sur `classe_saisie`, après 010.
- **Pascal** : ouvrir l'issue vers Surculus (citation de Purfürst, brief
  `briefs/vers-surculus/2026-10-28-ramulus-citation-purfurst.md`) ; et le brief sur le trou `INSERT OR REPLACE`.
- **Pascal** : signaler au DSF les quatre incohérences des fiches (pin d'Alep, pin laricio, deux pins sylvestres) ;
  relevé : `docs/essais/2026-10-28-essai-ia-a-l-aveugle.md`.
- **Pascal** : confirmer le calendrier SA1 provisoire (été mai-septembre, hiver novembre-mars).

## En cours

- **017** `afficheur` (sonnet) · finitions des écrans et réserves de 008 · [fiche](missions/017-finitions-ecrans.md) ·
  depuis le cycle 5.
- **021** `dev` (sonnet) · réserves mineures de l'échange (004, 006, 007, 013, 015) ·
  [fiche](missions/021-mineurs-echange.md) · depuis le cycle 5.

## En revue

_Rien._

## Fait

- **022** `afficheur` · plein écran dans Paramètres et puce « Terminées » · PR n° 25, fusionnée par délégation le
  2026-10-30 · depuis le cycle 5.
- **015** `dev` (+ **020** `testeur`) · corriger un arbre ou un centre par remplacement · PR n° 24, fusionnée par
  délégation le 2026-10-30, essai de Pascal sur le téléphone · depuis le cycle 5.
- **019** `afficheur` · accueil commun (bandeau vert, kanban, menu ⋮, recherche, tournées) · PR n° 22 fusionnée par
  délégation le 2026-10-30, essai à froid sur le SM-S918B · depuis le cycle 4.
- **018** `dev` · noter sans numéro peint, le rang suffit · PR n° 21 · depuis le cycle 4.
- **008** `dev` · photos du houppier (CameraX, sans EXIF, hors galerie) · PR n° 20 · depuis le cycle 4.
- **006** `afficheur` · écrans tournée → placette → notation · PR n° 19 · depuis le cycle 4.
- **013** `dev` · natifs Android de sqlite-jdbc · PR n° 18 · depuis le cycle 4.
- **016** · icône « R » gothique · PR n° 17 · depuis le cycle 4.
- **007** `dev` · centre au GNSS du téléphone · depuis le cycle 4.
- **004** `dev` · lire et écrire la tournée dans le GeoPackage · PR n° 15 · depuis le cycle 4.
- **014** `testeur` · cas de saisie des tests de 003 · PR n° 14 · depuis le cycle 4.
- **010** `dev` · amender `deperis-v1` (`classe_saisie`, centre en conflit) · depuis le cycle 4.
- **005** `dev` · journal en ajout seul et sauvegarde `.ramsync` · PR n° 11 fusionnée par Pascal le 2026-10-28 ·
  depuis le cycle 3.
- **003** `testeur` · tests d'acceptation de l'échange · PR n° 10 fusionnée par Pascal le 2026-10-28 ·
  depuis le cycle 2.
- **Pilotage du cycle 2** · PR n° 9 fusionnée par Pascal le 2026-10-28 · depuis le cycle 2.
- **001** `acquereur` · référentiels de la tranche 1 · PR n° 7 · depuis le cycle 2.
- **002** `dev` · format d'échange `deperis-v1` · PR n° 5 · depuis le cycle 2.
- **Journal de 002** · PR n° 6 · depuis le cycle 2.
- **Socle** (orchestrateur) : PR n° 1 · depuis le cycle 1.
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
