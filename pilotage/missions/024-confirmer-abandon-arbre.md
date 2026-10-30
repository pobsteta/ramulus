# Mission 024 — Confirmer avant d'abandonner l'arbre en cours et ses photos

## Mission

**En une phrase (quoi, et pourquoi) :** demander « Abandonner l'arbre en cours et ses photos ? » avant qu'un toucher
sur un autre arbre de la liste efface les notes et les photos de l'arbre en cours, parce qu'avec des gants un toucher
malheureux ne doit rien faire perdre.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | relecture de 015, point 3 ; `rouvrirArbre` dans `ControleurTournee.kt` |
| A-t-il les moyens ? | oui, après 017 | mêmes écrans que 017 |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `afficheur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** le stockage des photos (023).

## Décisions qui s'imposent

- **Pascal, 2026-10-30 :** « oui aux trois » : une confirmation avant d'abandonner l'arbre en cours et ses photos.
- **Arbitrages de l'orchestrateur, par délégation :** la confirmation ne s'affiche que si l'arbre en cours a au moins
  une note saisie ou une photo ; deux grands boutons (« Continuer cet arbre » par défaut, « Abandonner »), utilisables
  avec des gants (48 dp au moins) ; rien n'est effacé tant que l'opérateur n'a pas choisi « Abandonner ».

## Harnais

- **Peut :** modifier l'écran des arbres et le contrôleur (`rouvrirArbre`) ; ajouter des tests JVM.
- **Ne doit pas :** changer la règle de remplacement de 015 ; toucher au format ; toucher à `main`.
- **Sources autorisées :** fiches 015, 017 et 018 ; le code de `:app`.

## Valeur provisoire (chiffre, réglage)

- aucune

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 017 (mêmes écrans). Part de `main` après la fusion de 017.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** un test JVM : rouvrir un autre arbre avec un arbre en cours entamé ne
  change rien tant que l'abandon n'est pas confirmé ; confirmé, l'arbre en cours et ses photos en attente sont
  abandonnés ; arbre en cours vide : pas de question. `.claude/test-command` vert, code de sortie réel.

## Terrain

- **Échantillon que Pascal vérifiera :** noter MB d'un arbre, prendre une photo, toucher un autre arbre : la question
  apparaît ; « Continuer » garde tout.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Qui tranche :** l'orchestrateur, par délégation ; chaque arbitrage est inscrit au journal.

## Comment saura-t-on que ça marche ?

Sur le téléphone de Pascal, un toucher sur un autre arbre ne fait plus rien perdre sans confirmation.
