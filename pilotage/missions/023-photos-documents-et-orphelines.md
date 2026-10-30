# Mission 023 — Photos sous `Documents/Ramulus/` et rangement des photos orphelines

## Mission

**En une phrase (quoi, et pourquoi) :** ranger les photos sous `Documents/Ramulus/` avec un `.nomedia`, comme le dit
l'ADR 0006, et ranger à part, à l'ouverture d'une tournée, toute photo que plus aucune note ne cite, pour que les
photos survivent à une désinstallation et qu'un plantage ne bloque jamais le rendu.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | ADR 0006 (stockage) ; `.ramsync` et `.zip` déjà écrits sous `Documents/Ramulus/` (MediaStore) ; Surculus (photos, `Documents/`) |
| A-t-il les moyens ? | oui, vérifié le 2026-10-30 | `main` 0d383f4 ; téléphone SM-S918B pour l'essai (orchestrateur) |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** la confirmation à l'abandon d'un arbre (024) ; les écrans (017).

## Décisions qui s'imposent

- **Pascal, 2026-10-30 :** « oui aux trois, Documents/Ramulus ».
  - **Stockage** : photos sous `Documents/Ramulus/<code_tournee>/photos/` (ou forme proche, à justifier), avec un
    `.nomedia` ; aucune galerie ne les indexe ; aucune n'est envoyée hors de l'appareil (ADR 0006, CLAUDE.md).
  - **Photos orphelines** : à l'ouverture d'une tournée, toute photo de `photos/` que plus aucune note de la copie de
    travail ne cite est déplacée dans un dossier à part sur l'appareil (`photos-remplacees/`, de 015), jamais
    effacée ; le rendu n'est plus refusé `photo_non_citee` pour cette cause.
- **Arbitrages de l'orchestrateur, par délégation :**
  - Les photos déjà prises dans l'espace privé (tournées en cours sur le téléphone de Pascal) sont **déplacées** au
    premier lancement, sans perte, SHA-256 inchangé ; un test le prouve.
  - Android 8 et 9 (minSdk 26) : suivre ce que fait déjà Ramulus pour `.ramsync` et `.zip` ; ne pas demander de
    permission nouvelle sans le remonter.
  - Une ligne de l'ADR 0006 « Ajout du 2026-10-30 » dit que l'écart provisoire de 008 est levé.

## Harnais

- **Peut :** modifier le stockage des photos et la purge dans `:app` ; ajouter le rangement des orphelines ; ajouter
  des tests JVM ; ajouter une rubrique datée à l'ADR 0006.
- **Ne doit pas :** effacer une photo ; écrire une photo en galerie ; changer le format ; toucher aux écrans de 017 ;
  toucher à `main`.
- **Sources autorisées :** ADR 0005 et 0006 ; fiches 008 et 015 ; `docs/formats/deperis-v1.md` (R10a à R10c) ; le code
  de `:app`, `:core`, `:data` ; Surculus.

## Valeur provisoire (chiffre, réglage)

- aucune

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 021 (plafond de deux missions de code). Part de `main` après la fusion de 021 et 017.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** une photo prise est écrite sous `Documents/Ramulus/` avec `.nomedia` ; une
  photo orpheline est déplacée à l'ouverture et le rendu passe R10a à R10c ; la migration garde chaque photo et son
  SHA-256 ; `.claude/test-command` vert, code de sortie réel.

## Terrain

- **Échantillon que Pascal vérifiera :** photographier un arbre, ne pas la voir dans la galerie, la voir dans
  `Documents/Ramulus/` avec un gestionnaire de fichiers ; rendre la tournée.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Qui tranche :** l'orchestrateur, par délégation ; chaque arbitrage est inscrit au journal.

## Comment saura-t-on que ça marche ?

Sur le téléphone de Pascal, les photos sont sous `Documents/Ramulus/`, absentes de la galerie, et la tournée se rend.
