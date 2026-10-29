# Mission 019 — Un accueil comme Marculus, Groma et Surculus

## Mission

**En une phrase (quoi, et pourquoi) :** donner à Ramulus l'écran d'ouverture commun aux applications de terrain de
Pascal (bandeau vert avec la bascule kanban, menu hamburger avec Paramètres et À propos, recherche des tournées,
liste des tournées à faire et terminées), pour que l'opérateur passe d'une application à l'autre sans réapprendre.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | demande de Pascal ci-dessous ; Surculus, Marculus et `terrain-style` lisibles en local |
| A-t-il les moyens ? | oui, vérifié le 2026-10-29 | `~/dev/surculus` (b73c493), `~/dev/marculus` (33a254a), `~/dev/terrain` (d74d2f3) ; JitPack joignable (Surculus l'utilise) |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `afficheur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :**
  - les écrans de la placette et de la notation, sauf les couleurs, qui viennent du thème ;
  - les photos (008) ;
  - le numéro peint (018).

## Décisions qui s'imposent

- **Demande de Pascal (2026-10-29), mot pour mot :** « Quand on ouvre ramulus on doit avoir la même présentation que
  Marculus, Groma et Surculus, c'est à dire : un bandeau vert avec le bouton vue kanban, un menu hamburger avec les
  sous-menus qui vont bien dont A propos qui donne le numéro de version et la licence, paramètres qui regroupent tous
  les paramètres (GNSS, thème, etc...), en dessous la zone de recherhce des tournées, et en dessous, la liste des
  tournées à faire/terminées »
- **Style :**
  - `terrain-style` au commit bb2b519 (tag v0.7.1 de `pobsteta/terrain`, publié par JitPack), comme Surculus : voir
    `~/dev/surculus/gradle/libs.versions.toml` (l. 16, 38, 77), `settings.gradle.kts` et le thème de Surculus ;
  - c'est la palette de Marculus, par décision de Pascal (Marculus ADR 0005, révision du 2026-10-24) ;
  - bandeau en `primary` (VertVif `#3E7A40`), texte blanc.
- **Arbitrages de l'orchestrateur, par délégation de Pascal du 2026-10-28 :**
  - **Le menu est un hamburger (☰) à gauche du bandeau**, comme Pascal le demande, même si Surculus et Marculus ont
    un menu « ⋮ » : l'écart est signalé à Pascal. Ses entrées :
    1. Ouvrir une tournée (le `.gpkg`, comme aujourd'hui) ;
    2. Paramètres ;
    3. À propos.
  - **La bascule liste / kanban est un bouton du bandeau, à droite**, comme dans Surculus et Marculus. Le kanban
    reprend les **cinq colonnes communes** de Surculus et Marculus (`EtatKanban` de Surculus : Proposée, Validée,
    Planifiée, Réalisée, Abandonnée), avec la même règle de statut minimal :
    - rendue → Réalisée ;
    - au moins une placette notée → Planifiée ;
    - sinon Proposée.
  - **La liste** a deux parties : « À faire » (tournées non rendues), puis « Terminées » (rendues). Chaque ligne
    porte le code de la tournée, sa nature (notation ou contre-notation) et l'avancement (« 3 placettes sur 12 »).
    Toucher une ligne reprend la tournée là où elle en était.
  - **La recherche** filtre la liste et le kanban sur le code de la tournée, sans tenir compte des accents ni de la
    casse.
  - **À propos :**
    - le nom, la version (`versionName` et `versionCode`) et la licence du dépôt (lire `LICENSE`) ;
    - le protocole suivi : « DEPERIS, DSF, fiches 2025 » ;
    - le lien vers le dépôt, protégé contre `ActivityNotFoundException`.
  - **Paramètres :** un seul écran, en sections.
    - **Opérateur :** le nom, modifiable.
    - **Thème :** Système / Clair / Sombre, comme Marculus (ADR 0005 de Marculus). Système par défaut.
    - **GNSS :**
      - nombre de mesures moyennées (10) ;
      - seuil d'écart au point prévu (30 m) ;
      - ce sont des valeurs **provisoires** de 007, modifiables, et l'écran le dit.
    - **Sauvegarde :** la date de la dernière sauvegarde `.ramsync`, en lecture seule.
    - Si 008 est fusionnée avant toi : **Photos**, avec les seuils de flou et de contre-jour (provisoires).
  - **L'écran d'ouverture n'est jamais vide.** Au premier lancement sur le téléphone de Pascal, un écran vide est
    apparu (journal du 2026-10-29, écart sur 006). L'écran initial doit toujours afficher quelque chose : le nom
    à saisir, ou l'accueil. Un test le vérifie. Ce point est repris de 017.

## Harnais

- **Peut :**
  - modifier `:app` (accueil, thème, paramètres, À propos, `MainActivity`) ;
  - ajouter `terrain-style` au catalogue, avec sa source et sa licence dans `docs/dependances.md` ;
  - garder les réglages dans les `SharedPreferences` de l'application ;
  - lire le code de Surculus, de Marculus et de `terrain` (lecture seule).
- **Ne doit pas :**
  - modifier `:core/echange` ou le format ;
  - recopier du code de Surculus sans dire d'où il vient (commentaire avec le fichier et le commit) ;
  - inventer une couleur hors de `terrain-style` ;
  - toucher à `main`, à Surculus, à Marculus ou à `terrain`.
- **Sources autorisées :**
  - Surculus au commit b73c493 : `app/src/main/kotlin/io/github/pobsteta/surculus/tournee/EcranTournees.kt`,
    `EtatKanban.kt`, `VueKanban.kt`, le thème et le catalogue ;
  - Marculus au commit 33a254a : `ui/contextes/ListeContextesScreen.kt`, `ui/parametres/ParametresScreen.kt`,
    `docs/adr/0005-theme-systeme-clair-sombre.md` ;
  - `~/dev/terrain/README.md` ;
  - les fiches 006 et 007 ;
  - le code de `:app`.

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** 10 mesures et 30 m (reprises de 007), réglables dans Paramètres.
- **Mesurée par :** Pascal, sur le terrain.
- **Gel :** avant la première campagne.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 006 (fusionnée). Part de `main` (890ad03).
- **Ordre de fusion :** 008 → 018 → 019. 019 se met à jour de `main` par fusion (conflits attendus dans `Ecrans.kt`).
- **PR en brouillon jusqu'à :** la fusion de 018.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :**
  - la recherche (accents, casse) ;
  - la répartition « À faire » / « Terminées » ;
  - les colonnes du kanban et la règle de statut minimal ;
  - les réglages lus et écrits, avec les valeurs par défaut ;
  - le contenu d'À propos (version et licence) ;
  - l'écran initial jamais vide ;
  - `.claude/test-command` est vert.

## Terrain

- **Échantillon que Pascal vérifiera :**
  - ouvrir Ramulus et voir le bandeau vert, ☰ et la bascule kanban ;
  - chercher « EXEMPLE » ;
  - ouvrir À propos, puis Paramètres et passer en thème Sombre ;
  - comparer côte à côte avec Surculus et Marculus sur le même téléphone.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la PR.
- **Qui tranche :** l'orchestrateur, par délégation ; chaque arbitrage est inscrit au journal.

## Comment saura-t-on que ça marche ?

Pascal ouvre Ramulus, Surculus et Marculus l'un après l'autre et reconnaît la même maison.
