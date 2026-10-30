# Mission 013 — Natifs Android de sqlite-jdbc

## Mission

**En une phrase (quoi, et pourquoi) :** empaqueter sqlite-jdbc dans l'APK, comme le fait Surculus (ADR 0011) : natifs
du classifieur `natives-android` extraits vers `jniLibs`, *core library desugaring*, règles R8. Le but :
qu'`EchangeDeperis` (004) ouvre et écrive une tournée sur le téléphone, et pas seulement sur la JVM.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | reprise de Surculus `app/build.gradle.kts` (tâche `ExtraireNativesSqlite`, l. 11-60, l. 139-146) et ADR 0011 |
| A-t-il les moyens ? | oui, vérifié le 2026-10-28 | Surculus en local (3c01f4b), Maven Central joignable, Android SDK ; émulateur présent dans le SDK (`emulator`) |

**Temps de la main :** appuyer, avec une **reprise à l'identique**.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** les écrans (006) ; le GNSS (007).

## Harnais

- **Peut :**
  - modifier `app/build.gradle.kts`, `gradle/libs.versions.toml` (desugar, avec sa source Surculus) et
    `app/proguard-rules.pro` ;
  - ajouter un test instrumenté de fumée (ouvrir A1 et écrire une note avec `EchangeDeperis`) dans
    `app/src/androidTest`, et le lancer sur un émulateur si le SDK en a un ;
  - ajouter `:data` aux dépendances de `:app`, si c'est nécessaire au test.
- **Ne doit pas :** changer de bibliothèque SQLite ; toucher `:core` hors besoin d'empaquetage ; toucher à `main`.
- **Sources autorisées :** Surculus, au commit 3c01f4b : `app/build.gradle.kts`, `app/proguard-rules.pro`,
  `docs/adr/0011-*`, `gradle/libs.versions.toml` ; notre ADR 0005.

## Valeur provisoire (chiffre, réglage)

- aucune

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 004 (sqlite-jdbc au catalogue et `EchangeDeperis`) ; part de `main` après la fusion de 004.
- **Ordre de fusion :** 013 avant 006.
- **PR en brouillon jusqu'à :** —
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :**
  - l'APK de debug **et** l'APK de release contiennent `lib/<abi>/libsqlitejdbc.so` (vérifié par `unzip -l`) ;
  - `.claude/test-command` est vert ;
  - le test de fumée passe sur un émulateur s'il a pu tourner ; sinon, c'est dit.

## Terrain

- **Échantillon que Pascal vérifiera :** sur le téléphone, avec 006, l'ouverture d'une tournée ne plante pas.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28 (« jusqu'à un APK
  testable, sans rien me demander »).
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la PR.
- **Qui tranche :** l'orchestrateur, par délégation ; chaque arbitrage est inscrit au journal.

## Comment saura-t-on que ça marche ?

L'application installée ouvre `A1-tournee-depart/deperis.gpkg` sur le téléphone de Pascal.
