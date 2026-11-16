# Mission 006 — Écrans tournée → placette → notation

## Mission

**En une phrase (quoi, et pourquoi) :** enchaîner sur le téléphone les écrans d'une tournée :
- ouvrir la tournée ;
- choisir une placette ;
- relever son centre ;
- noter ses arbres par rang, dans le tour d'horizon, avec le numéro peint ;
- clore la placette ;
- rendre le `.zip`.

Le but : qu'un opérateur note une placette entière en forêt, et que Nemeton relise le paquet.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | API `EchangeDeperis` (004), `JournalRepository` (005), composant de centre (007), référentiels (001), thème existant |
| A-t-il les moyens ? | oui, après la fusion de 004, 013 et 007 | vérifié par l'orchestrateur avant le lancement |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `afficheur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :**
  - les photos (008) ;
  - l'IA (ADR 0002) ;
  - la fusion de plusieurs opérateurs à l'écran et la mesure du temps de saisie (009) ;
  - les voies de sauvegarde hors de l'appareil (011).

## Décisions qui s'imposent

- **Décisions de Pascal (2026-10-28) :**
  - **La classe ne se saisit pas** : elle s'affiche, calculée par l'abaque, après MB et MR/MA.
  - **Le critère suit l'essence** : MR pour un feuillu, MA pour un résineux.
  - **Contre-notation** (`tournee.nature = contre_notation`) : elle n'affiche jamais une première note ; le centre du
    premier opérateur est fourni en lecture seule.
  - **SA1** : une saison de campagne qui ne correspond pas à la date du jour donne un avertissement, sans blocage.
  - **SA2** : le charme et le chêne liège hors été sont notés avec la fiche d'été, et un avertissement s'affiche.
  - **K1** : la règle de choix et le tour d'horizon (« depuis le nord, sens horaire, Kraft 1 à 3 ») sont rappelés sur
    l'écran de placette.
  - **T5** : la tournée ouverte est **copiée** dans le stockage de l'application, et la copie de départ est gardée
    intacte ; un rendu « signalé » écrit quand même le `.zip`, et un refus affiche ses motifs en clair.
- **Arbitrages de l'orchestrateur, par délégation de Pascal du 2026-10-28** (inscrits au journal) :
  - **Ouvrir une tournée :** par le sélecteur de documents d'Android (`ACTION_OPEN_DOCUMENT`), sur un `deperis.gpkg`.
  - **Opérateur :** son nom est saisi une fois, au premier lancement ; il sert pour le journal et pour `rendre`.
  - **Ce que fait chaque saisie :** elle passe par `EchangeDeperis` (écriture dans la copie de travail) **et** par le
    journal (`JournalRepository.saisir`, types `notation`, `contre_notation`, `centre`, `placette_notee`) ; le
    journal est sauvegardé selon la cadence de 005.
  - **Rendu :** le `.zip` est écrit dans `Documents/Ramulus/` (MediaStore), et son nom affiché.
  - **Écran :** pensé pour la forêt, avec des gants, au soleil et d'une main : grandes cibles, notes de 0 à 5 en
    boutons, aucune saisie clavier sauf le numéro peint et le nom de l'opérateur, thème clair ou sombre existant.

## Harnais

- **Peut :** écrire dans `:app` (écrans, navigation, ViewModels) ; ajouter `:data` aux dépendances de `:app` ; ajouter
  des tests JVM des ViewModels et un test Compose s'il tourne en JVM.
- **Ne doit pas :**
  - écrire dans le GeoPackage autrement que par `EchangeDeperis` ;
  - afficher une classe saisie ;
  - envoyer quoi que ce soit au réseau ;
  - modifier `:core` ou le format (une lacune remonte à l'orchestrateur) ;
  - toucher à `main`.
- **Sources autorisées :**
  - `docs/spec.md`, `docs/formats/deperis-v1.md` ;
  - les fiches 004, 005 et 007 ;
  - `docs/referentiels/` ;
  - le code de `:core` et de `:data` ;
  - le code de `:app` existant (`EcranNotation.kt`, thème) ;
  - Surculus `app/` (navigation, sauvegarde) au commit 3c01f4b.

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** les seuils de 30 m et de 15 jours (provisoires, repris du format).
- **Mesurée par :** Pascal, sur le terrain.
- **Gel :** avant la première campagne.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 004, 013 et 007, fusionnées ; part de `main`.
- **Ordre de fusion :** après 013 et 007.
- **PR en brouillon jusqu'à :** —
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :**
  - les ViewModels sont testés en JVM : enchaînement, classe calculée, avertissements SA1 et SA2, contre-notation
    sans première note ;
  - `.claude/test-command` est vert ;
  - l'APK de debug s'installe.

## Terrain

- **Échantillon que Pascal vérifiera :**
  - ouvrir la tournée d'exemple A1 ;
  - relever le centre de P001 ;
  - noter ses 4 arbres ;
  - clore la placette ;
  - rendre ;
  - ouvrir le `.zip` dans QField.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la PR.
- **Qui tranche :** l'orchestrateur, par délégation ; chaque arbitrage est inscrit au journal.

## Comment saura-t-on que ça marche ?

Pascal note la placette P001 d'A1 sur son téléphone, puis QField ouvre le `.zip` rendu avec ses notes et la classe de
l'abaque.
