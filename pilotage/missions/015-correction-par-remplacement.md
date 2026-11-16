# Mission 015 — Corriger un arbre ou un centre par remplacement

## Mission

**En une phrase (quoi, et pourquoi) :** faire qu'une note d'arbre corrigée, ou un centre relevé de nouveau, remplace
l'ancienne ligne dans la copie de travail de la tournée, le journal gardant les deux, pour que le paquet rendu porte
une seule ligne par arbre et par centre, sans faux `doublon_arbre`.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | décision de Pascal ci-dessous ; ADR 0004 (ajout du 2026-10-29) et 0005 ; `docs/formats/deperis-v1.md` et `ramsync-v1.md` |
| A-t-il les moyens ? | oui, vérifié le 2026-10-30 | 004 à 008, 018 et 019 fusionnées ; `main` 00d8c24 construit et lancé sur le SM-S918B |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** opus (le remplacement touche aux données de l'opérateur et au journal)
- **Ce qui ne lui est pas demandé :**
  - les mineurs de l'échange (mission 021) ;
  - les finitions des écrans (mission 017) ;
  - les tests d'acceptation, écrits en parallèle par le `testeur` (mission 020), sans lire ce code.

## Décisions qui s'imposent

- **Pascal, 2026-10-30** (« je valide tes réponses », sur la recommandation de l'orchestrateur) : une correction
  **remplace** la ligne ; elle n'ajoute pas une seconde ligne « dont la plus récente ferait foi ».
- **Arbitrages de l'orchestrateur, par délégation de Pascal du 2026-10-28 :**
  - **Note d'arbre.** Rouvrir un arbre noté et l'enregistrer de nouveau :
    - écrit une note **de nouvel `uuid`**, même `plot_id`, même `rang` ;
    - retire l'ancienne note de la copie de travail (le GeoPackage de la tournée sur l'appareil) ;
    - ajoute au journal une ligne `notation` (ou `contre_notation`) dont `remplace_uuid` est l'`uuid` de l'ancienne ;
      le journal reste en ajout seul, rien n'y est effacé.
  - **Photos d'une note corrigée.** Celles de l'ancienne note sont gardées et passent à la nouvelle : renommées
    `photos/<nouvel uuid>_<n>.jpg` dans la copie de travail, citées par la nouvelle note avec leur SHA-256 (inchangé),
    sauf si l'opérateur les reprend. Le paquet rendu reste conforme à R10a à R10c.
  - **Centre.** Relever de nouveau le centre d'une placette remplace l'ancien de la même façon (nouvel `uuid`, ancien
    retiré de la copie de travail, ligne `centre` au journal avec `remplace_uuid`). Il y a toujours au plus un centre
    par placette dans la copie de travail. L'écran propose « Relever de nouveau » quand un centre existe.
  - **`doublon_arbre`** reste un contrôle du rendu : il signale désormais un vrai doublon (deux lignes du même rang
    dans un paquet, par exemple après l'union de deux opérateurs), jamais une correction.
  - **Placette rendue.** Une correction après le rendu retire la marque `rendue`, comme toute écriture (019).
  - Une correction concurrente reste un conflit, signalé, jamais résolu (ADR 0005, l. 33 ; `ramsync-v1`, Q4).

## Harnais

- **Peut :**
  - modifier `:core` (écriture de la tournée), `:data` (journal : seulement ajouter des lignes) et `:app`
    (`ControleurTournee`, `Modele`, écran des arbres et du centre) ;
  - ajouter une rubrique datée « Ajout du 2026-10-30 » à l'ADR 0004 et à la spec, sans rien effacer, qui remplace le
    « Provisoire » du 2026-10-29 ;
  - préciser dans `docs/formats/deperis-v1.md` que `doublon_arbre` ne vient plus d'une correction.
- **Ne doit pas :**
  - modifier ou supprimer une ligne du journal ;
  - changer la version des formats ni les cas communs existants (un besoin remonte à l'orchestrateur) ;
  - lire ou modifier les tests du `testeur` (mission 020) avant leur fusion dans sa branche par l'orchestrateur ;
  - toucher à `main`.
- **Sources autorisées :** ADR 0001, 0004, 0005 et 0006 ; `docs/spec.md` ; `docs/formats/deperis-v1.*` et
  `ramsync-v1.*` ; les fiches 004 à 008, 018 et 019 ; le code de `:core`, de `:data` et de `:app`.

## Valeur provisoire (chiffre, réglage)

- aucune

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** `main` 00d8c24. Part de `origin/main`.
- **Ordre de fusion :** 020 (tests, rouges) fusionnée dans la branche de 015 par l'orchestrateur, puis 015 vers `main`.
- **PR en brouillon jusqu'à :** l'arrivée des tests de 020 dans la branche, verts.
- **CI rouge attendue :** aucune sur 015 ; les tests de 020 sont marqués selon leur fiche jusqu'à 015.

## Tests

- **Comment saura-t-on que c'est juste :**
  - noter un arbre, le rouvrir, le corriger : la copie de travail a une seule note pour ce rang, de nouvel `uuid` ;
  - le journal a deux lignes, la seconde avec `remplace_uuid` ; aucune ligne mise à jour ou supprimée ;
  - les photos de l'ancienne note sont citées par la nouvelle, et le rendu passe R10a à R10c ;
  - un centre relevé deux fois : une seule ligne `centres` pour la placette ;
  - le rendu d'une placette corrigée ne signale aucun `doublon_arbre` ;
  - les tests de 020 passent sans retouche ;
  - `.claude/test-command` est vert.

## Terrain

- **Échantillon que Pascal vérifiera :** noter P001, rouvrir l'arbre 2, changer MB, enregistrer ; relever de nouveau
  le centre ; rendre le `.zip` et l'ouvrir dans QField : 4 notes, 1 centre, aucun doublon.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la PR.
- **Qui tranche :** l'orchestrateur, par délégation ; chaque arbitrage est inscrit au journal.

## Comment saura-t-on que ça marche ?

Sur le téléphone de Pascal, un arbre corrigé et un centre relevé de nouveau ne laissent qu'une ligne chacun dans le
paquet rendu.
