# Mission 005 — Journal en ajout seul et sauvegarde `.ramsync`

## Mission

**En une phrase (quoi, et pourquoi) :** tenir sur l'appareil le journal des saisies en ajout seul (Room) et sa
sauvegarde `.ramsync`, repris ligne à ligne de l'ADR 0005 de Surculus, pour qu'aucune note ne se perde avec le
téléphone.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui | reprise : Surculus ADR 0005 et `sursync-v1` ; notre ADR 0005 |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui, vérifié dans la session le 2026-10-28 | Surculus lisible en local (`/home/pascal/dev/surculus`, commit 3c01f4b) ; Android SDK, Java et `.claude/test-command` présents ; Room et KSP pas encore dans `gradle/libs.versions.toml` : à reprendre de Surculus (ADR 0005, « Contexte ») |

**Temps de la main :** **tenir** : le journal porte les données de l'opérateur et le format `.ramsync` engage la
durée (lecture N-1). L'agent rend d'abord un **plan**, que Pascal valide avant toute écriture de code.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** opus
- **Ce qui ne lui est pas demandé :** les écrans (006) ; le GeoPackage de la tournée (004) ; le paquet rendu et la
  fusion des opérateurs (009) ; les photos elles-mêmes (008), dont seule l'entrée au journal le concerne.

## Harnais

- **Peut :** au premier passage, écrire **seulement** le plan (`pilotage/missions/005-plan.md`) ; après validation
  par Pascal : le module du journal, ses tests, le format `docs/formats/ramsync-v1.md` et son schéma, des cas dans
  `fixtures/ramsync/`, les dépendances Room et KSP reprises de Surculus (versions comprises).
- **Ne doit pas :** inventer une règle absente de Surculus ADR 0005 ou de notre ADR 0005 (un écart remonte à
  l'orchestrateur, qui le pose à Pascal) ; permettre une mise à jour ou une suppression d'une ligne du journal ;
  écrire un nom d'opérateur dans un nom de fichier ; toucher à `main`.
- **Sources autorisées :** notre `docs/adr/0005-*.md` et `0006-*.md` (photos) ; `docs/spec.md` ; Surculus
  `docs/adr/0005-stockage-et-sauvegarde.md`, `docs/formats/sursync-v1.md` et son schéma, `gradle/libs.versions.toml`,
  `app/build.gradle.kts`, `data/build.gradle.kts` et `FormatSursync.kt` (Q10), au commit 3c01f4b ; `docs/formats/deperis-v1.md` (uuid, motif `uuid_contenu_different`).

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** fréquence de sauvegarde (fin de placette, au plus tard toutes les 20 saisies ou
  5 minutes), reprise de Surculus ADR 0005, l. 50-53, non mesurée pour Ramulus.
- **Mesurée par :** Pascal, à l'essai de terrain d'une tournée complète (après 006).
- **Gel :** avant la mission 009 (rendu).

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune ; part de `main` après la fusion de 001 et 002.
- **Ordre de fusion :** indépendante ; avant 008 et 009, qui en dépendent.
- **PR en brouillon jusqu'à :** —
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :**
  - une mise à jour ou une suppression d'une ligne du journal échoue (DAO et déclencheurs) ;
  - l'ordre total est déterministe ; deux corrections concurrentes du même objet sont signalées comme conflit ;
  - un `.ramsync` écrit puis relu redonne le journal à l'identique ; N-1 lu, N+1 refusé ; un fichier abîmé est lu
    ligne à ligne jusqu'à la casse, la casse signalée ;
  - le pied SHA-256 détecte une altération ;
  - aucun nom d'opérateur dans les noms de fichiers ;
  - les tests sont écrits par l'agent et rejoués en JVM ; le `testeur` y ajoutera ses tests d'acceptation.

## Terrain

- **Échantillon que Pascal vérifiera (après 006, Q13 ; d'ici là le test 30 fait foi) :** saisir quelques notes, copier le `.ramsync` de `Documents/Ramulus/` sur une
  clé ou un second téléphone, effacer les données de l'application, restaurer : les notes reviennent.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la remise du plan, puis à la PR.
- **Qui tranche :** Pascal (plan ; tout écart à Surculus ADR 0005).

## Comment saura-t-on que ça marche ?

La restauration de l'échantillon terrain redonne toutes les notes, et 009 lit le journal sans le modifier.

## Plan validé par Pascal (2026-10-28)

Plan : [`005-plan.md`](005-plan.md), validé avec les propositions de Q1 à Q14 :

- **Q1** : la colonne `valeur` est reprise ; l'orchestrateur complète notre ADR 0005 par un ajout.
- **Q2** : sont repris `heure_gnss_ms`, `demarrage`, `age_fix_ms`, `code_tournee` et la règle « Temps » (saut
  d'horloge de plus de 60 s noté par `saut_horloge`, jour civil Europe/Paris).
- **Q3** : `type` ∈ {`notation`, `contre_notation`, `photo`, `centre`, `placette_notee`, `annulation`, `saut_horloge`}.
- **Q4** : une annulation et une correction concurrentes de la même ligne forment un conflit, signalé, jamais résolu.
- **Q5** : la table `refus_journal` est reprise (colonnes du journal, plus `motif` et `recu_le_utc_ms`), en ajout seul.
- **Q6** : une table `sauvegardes` à part, en ajout seul.
- **Q7** : la projection est dans 005, dans `:core`.
- **Q8** : l'en-tête du `.ramsync` porte `version_nemeton` et `version_ramulus`.
- **Q9** : `valeur` reste opaque pour 005 ; une notation n'y portera pas de classe (contenu fixé par 006).
- **Q10** : lecture permise de `surculus/data/build.gradle.kts` et `FormatSursync.kt`.
- **Q11** : l'orchestrateur ajoute `:data:testDebugUnitTest` à `.claude/test-command` sur la branche de 005.
- **Q12** : hors de 005, chacun dans une mission à venir : les trois voies hors de l'appareil, le signal à
  l'ouverture de la journée et le test MediaStore instrumenté.
- **Q13** : pas d'écran ; l'échantillon terrain attend 006.
- **Q14** : `dataExtractionRules` fait l'objet d'une mission à part.

**Harnais, deuxième passage :** réaliser le plan ainsi tranché.
