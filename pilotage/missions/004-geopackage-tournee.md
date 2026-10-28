# Mission 004 — Lire et écrire la tournée dans le GeoPackage

## Mission

**En une phrase (quoi, et pourquoi) :** lire une tournée `deperis-v1` et y écrire centres et notations avec
sqlite-jdbc (reprise de Surculus ADR 0011), puis fusionner et rendre le paquet. Le but : compléter la tournée sur
l'appareil et passer au vert les tests d'acceptation de 003 sans les retoucher.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | format `deperis-v1`, interface fixée par les tests de 003, Surculus ADR 0011 |
| A-t-il les moyens ? | oui, vérifié dans la session le 2026-10-28 | tests de 003 sur `main` ; sqlite-jdbc 3.53.4.0 au catalogue de Surculus (l. 27, 69) ; Maven Central joignable ; Surculus en local (3c01f4b) |

**Temps de la main :** appuyer. L'interface et les règles sont fixées ; seule la réalisation est confiée.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :**
  - les écrans (006) ;
  - le GNSS (007) et les photos (008), sauf leur contrôle dans le paquet tel que le format le décrit ;
  - le journal (005, déjà fusionnée) ;
  - modifier le format (010).

## Décisions qui s'imposent (Pascal, 2026-10-28)

- **T4 :** l'interface est celle que les tests de 003 appellent (`PasserelleEchange.kt`), reprise telle quelle :
  - un `object io.github.pobsteta.ramulus.core.echange.EchangeDeperis` à fonctions `@JvmStatic` ;
  - des résultats en texte JSON ;
  - une écriture refusée lève une exception dont le message porte le code du motif, et le fichier reste intact.
- **T1 :** un texte tapé en Unicode décomposé est normalisé en NFC à l'enregistrement.
- **T2 :** toute classe fournie est refusée, juste ou fausse. Après 010, le code de ce refus est `classe_saisie`.
- **T5 :**
  - Ramulus garde une copie intacte de la tournée de départ ;
  - un rendu « signalé » écrit quand même le `.zip` ;
  - seul un refus bloque l'écriture du `.zip`.
- **F1 (écrit par 010) :** à la fusion, une placette « notee » dont le centre est en conflit est gardée avec ses
  notes, et signalée.
- La classe enregistrée vient toujours de l'abaque (`Abaque` de `:core`) ; une coordonnée française tombe en France.

## Harnais

- **Peut :**
  - écrire le code de l'échange dans `:core` ;
  - ajouter sqlite-jdbc (et desugar si `:app` l'exige) au catalogue, avec sa source (Surculus
    `gradle/libs.versions.toml` l. 27 et 69) ;
  - ajouter à la CI l'exécution de `:core:test` avec `RAMULUS_ECHANGE_EXIGER=1`, pour qu'aucun test de 003 ne reste
    sauté sans bruit (condition du relecteur de 003).
- **Ne doit pas :**
  - retoucher un test de 003 (une lacune remonte à l'orchestrateur) ;
  - modifier le format ;
  - toucher à `main`.
- **Sources autorisées :**
  - `docs/formats/deperis-v1.*` ;
  - `fixtures/echange/` ;
  - les tests de `core/src/test/kotlin/…/echange/` ;
  - `docs/spec.md` ; ADR 0003, 0004 et 0005 ;
  - Surculus, au commit 3c01f4b : `docs/adr/0011-ecriture-geopackage-sur-appareil.md`, `gradle/libs.versions.toml`,
    et son code d'écriture GeoPackage ;
  - Groma `android/groma-core/build.gradle.kts` (version de sqlite-jdbc).

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** les seuils de 30 m et de 15 jours (contre-notation), repris du format, restent
  **provisoires**.
- **Mesurée par :** Pascal, sur le terrain.
- **Gel :** avant 007 (30 m) ; avant la première campagne (15 jours).

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 002 et 003 (fusionnées) ; **010** (format amendé), qui part en parallèle. 004 part de `main` et se
  rebase sur `main` après la fusion de 010.
- **Ordre de fusion :** 010 → 004.
- **PR en brouillon jusqu'à :** la fusion de 010.
- **CI rouge attendue :** aucune après le rebase sur 010. Avant, le cas F1 et le motif `classe_saisie` ne sont pas
  encore dans `main`.

## Tests

- **Comment saura-t-on que c'est juste :**
  - les 22 tests de 003, sautés aujourd'hui, passent au vert sans retouche ;
  - les 183 tests du rouge forcé passent aussi au vert avec `RAMULUS_ECHANGE_EXIGER=1` ;
  - les tests propres de l'agent passent ;
  - `.claude/test-command` est vert.

## Terrain

- **Échantillon que Pascal vérifiera :** compléter A1 en A3 par la fonction `rendre`, puis ouvrir le `.zip` dans
  QField : trois points en Côte-d'Or, les notes et la classe de l'abaque.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la PR.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

La CI de 004 est verte avec `RAMULUS_ECHANGE_EXIGER=1`, et Nemeton relit le paquet rendu (cas communs rejoués en R).
