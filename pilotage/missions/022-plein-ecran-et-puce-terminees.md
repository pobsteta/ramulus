# Mission 022 — Plein écran dans Paramètres et puce « Terminées » à côté de la recherche

## Mission

**En une phrase (quoi, et pourquoi) :** ajouter à Ramulus le réglage « Plein écran » dans Paramètres et la puce
« Terminées » à droite de la zone de recherche des tournées, repris de Surculus, parce que Pascal veut le même accueil
et les mêmes paramètres que Surculus, Marculus et Groma.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | Surculus `origin/main` 62ac182 : mission 044 (plein écran), mission 041 (puce à côté de la recherche) |
| A-t-il les moyens ? | oui, vérifié le 2026-10-30 | Surculus lisible en local (`/home/pascal/dev/surculus`) ; `main` 00d8c24 ; téléphone de Pascal branché |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `afficheur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** la correction par remplacement (015) ; les écrans de notation et de photo (017).

## Décisions qui s'imposent

- **Pascal, 2026-10-30 :** « Ajoute dans paramètres le mode plein écran » ; « à droite de la barre de recherche des
  tournées il n'y a pas le bouton "Terminées" ».
- **Arbitrages de l'orchestrateur, par délégation de Pascal du 2026-10-28 (reprise de Surculus) :**
  - **Plein écran** : un interrupteur « Plein écran » dans Paramètres, **faux par défaut**, gardé sur le téléphone
    (les réglages de 019) ; appliqué tout de suite et à chaque lancement : barres système masquées, elles reviennent
    un instant au glissement (`WindowInsetsControllerCompat`, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), comme
    `surculus/MainActivity.kt` (mission 044).
  - **Puce « Terminées (n) »** à droite de la recherche, sur la même ligne, comme `surculus/tournee/EcranTournees.kt`
    (mission 041) et Groma (`EcranProjets.kt`) :
    - par défaut, la liste montre les tournées à faire ; la puce cochée (« ✓ ») montre les terminées ;
    - la recherche dit combien elle trouve dans l'autre liste (« 1 tournée terminée correspond »), comme Surculus ;
    - en vue kanban, pas de puce : toutes les tournées sont dans leurs colonnes ;
    - un message dit « Aucune tournée terminée » ou « Aucune tournée trouvée » quand la liste affichée est vide.
  - Cela remplace l'affichage actuel des deux en-têtes « À faire » et « Terminées » l'un sous l'autre.

## Harnais

- **Peut :** modifier `:app` (`MainActivity`, `accueil/`, réglages de 019) ; ajouter des tests JVM de la logique pure
  (répartition, message vide, compte de l'autre liste, réglage gardé) ; lire Surculus et Groma.
- **Ne doit pas :** toucher aux écrans de notation, au contrôleur de tournée hors lecture, au format ; toucher à `main`.
- **Sources autorisées :** fiche 019 ; le code de `:app` ; Surculus (`MainActivity.kt`, `tournee/EcranTournees.kt`,
  `tournee/ReglagesAffichage.kt`, `parametres/EcranParametres.kt` et leurs tests) ; Groma `EcranProjets.kt`.

## Valeur provisoire (chiffre, réglage)

- aucune

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** `main` 00d8c24. Indépendante de 015 (fichiers d'accueil seulement) ; si 015 est fusionnée avant, se
  mettre à jour de `main` par fusion.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** tests JVM de la répartition, du message vide, du compte de l'autre liste et
  du réglage plein écran (faux par défaut, gardé) ; `.claude/test-command` vert. Pas de `testeur` : reprise d'un
  comportement d'écran éprouvé dans Surculus, vérifiée sur le téléphone par l'orchestrateur (arbitrage inscrit au
  journal).

## Terrain

- **Échantillon que Pascal vérifiera :** dans Paramètres, activer le plein écran : les barres disparaissent, reviennent
  au glissement, et restent masquées après relance ; sur l'accueil, la puce « Terminées (0) » à droite de la recherche.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Qui tranche :** l'orchestrateur, par délégation ; chaque arbitrage est inscrit au journal.

## Comment saura-t-on que ça marche ?

Sur le téléphone de Pascal, l'accueil et Paramètres ont la puce et l'interrupteur de Surculus, et le plein écran
survit à une relance.
