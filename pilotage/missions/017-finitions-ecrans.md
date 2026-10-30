# Mission 017 — Finitions des écrans de terrain

## Mission

**En une phrase (quoi, et pourquoi) :** lever les réserves laissées par la relecture de 008 sur les écrans de notation
et de photo (valeur PV1 en dur, couleurs fixes, état de l'écran photo, mémoire et fil de la capture), pour que
l'opérateur en forêt ait des écrans lisibles et sûrs en plein soleil comme en thème sombre.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | réserves listées ci-dessous, chacune avec son lieu ; `terrain-style` (019) |
| A-t-il les moyens ? | oui, vérifié le 2026-10-30 | `main` 00d8c24 ; téléphone de Pascal (SM-S918B) branché à l'essai |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `afficheur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** la correction par remplacement (015) ; l'accueil (019, fait) ; le format.

## Réserves à lever (relecture de 008, 2026-10-29)

1. **PV1 en dur** (`Ecrans.kt`, vers la l. 283 sur 00d8c24) : le « 2 à 4 vues » doit venir du référentiel ou d'une
   constante nommée et unique, marquée provisoire (PV1), pas d'un littéral dans l'écran.
2. **Couleurs fixes** `Ambre` et `Color.Gray` : passer par le thème `terrain-style` (rôles Material 3), lisibles en
   clair et en sombre.
3. **État de `EcranPhoto`** : l'état de capture survit à une rotation et à un retour d'arrière-plan ; aucun état
   Compose n'est écrit hors du fil principal (même cause que l'écart de 006, levé pour l'accueil en 019).
4. **Purge des photos** hors du fil principal.
5. **Mémoire de la capture** : mesurer sur le SM-S918B (plusieurs photos de suite sur une placette « collecte ») et
   rendre la mesure ; libérer les tampons après écriture.

## Harnais

- **Peut :** modifier `:app` (écrans de notation et de photo, thème) ; ajouter des tests JVM ; lire Surculus pour les
  rôles de couleur de `terrain-style`.
- **Ne doit pas :** changer une règle de prise de vue ou un seuil ; toucher au format ; toucher à `main`.
- **Sources autorisées :** fiches 006, 008 et 019 ; ADR 0006 ; `docs/referentiels/prise-de-vue-houppier.yaml` ; le
  code de `:app` ; `terrain-style` (bb2b519).

## Valeur provisoire (chiffre, réglage)

- PV1 reste provisoire ; la mission ne la change pas, elle la nomme.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 015 (mêmes fichiers d'écran). Part de `main` après la fusion de 015.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** une recherche de `Color.Gray` et de `Ambre` dans les écrans ne trouve plus
  rien ; un test lit PV1 à un seul endroit ; un test d'état de `EcranPhoto` ; `.claude/test-command` est vert. Le
  `testeur` peut être appelé par l'orchestrateur si la relecture le juge nécessaire.

## Terrain

- **Échantillon que Pascal vérifiera :** noter et photographier deux arbres en thème sombre puis clair ; tourner le
  téléphone pendant la prise de vue.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Qui tranche :** l'orchestrateur, par délégation ; chaque arbitrage est inscrit au journal.

## Comment saura-t-on que ça marche ?

Sur le téléphone de Pascal, les écrans de notation et de photo sont lisibles dans les deux thèmes et ne perdent rien à
la rotation.
