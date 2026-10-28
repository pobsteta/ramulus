# Mission 014 — Adapter les tests de 003 aux cas de saisie et à `classe_saisie`

## Mission

**En une phrase (quoi, et pourquoi) :** adapter les tests d'acceptation de 003 aux cas de saisie. Concrètement :
- le test générique saute les cas qui portent `enregistrement`, et rejoue à la place leurs saisies par
  `enregistrerNotation` ;
- le test « classe fournie » exige le motif `classe_saisie` ;
- une classe vide est acceptée et calculée.

Le but : que 004 passe au vert sur le format amendé par 010, sans fausse alarme.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | ses propres tests (003), le format amendé par 010, le README des cas |
| A-t-il les moyens ? | oui, vérifié le 2026-10-28 | branche de 010 poussée (`mission/010-amendement-deperis-v1`) ; `.claude/test-command` |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `testeur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** lire le code de 004 (`core/src/main/…/echange/`) ; modifier le format.

## Décisions de Pascal (2026-10-28)

- **T2 bis** : une classe fournie est refusée avec le motif `classe_saisie`.
- **Q1 de 010** : le cas T2 est un cas de saisie, à rejouer par saisie, pas par ouverture du fichier.
- **Q4 de 010** : une classe vide (`""`) compte comme absente : elle est calculée par l'abaque, sans refus.

## Harnais

- **Peut :** modifier les tests de `core/src/test/kotlin/…/echange/` et `PasserelleEchange.kt`.
- **Ne doit pas :**
  - lire le code de 004 ;
  - affaiblir une assertion existante (chaque retrait se justifie par une décision ci-dessus) ;
  - toucher au format ou aux cas ;
  - toucher à `main`.
- **Sources autorisées :** `docs/formats/deperis-v1.*` et `fixtures/echange/` **de la branche de 010** ; la spec ;
  ses tests de 003.

## Valeur provisoire (chiffre, réglage)

- aucune

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 010. La branche part de `mission/010-amendement-deperis-v1` et se rebase sur `main` après la fusion
  de 010.
- **Ordre de fusion :** 010 → 014 → 004.
- **PR en brouillon jusqu'à :** la fusion de 010.
- **CI rouge attendue :** aucune. Les tests restent sautés tant que 004 n'est pas fusionnée.

## Tests

- **Comment saura-t-on que c'est juste :**
  - `.claude/test-command` est vert ;
  - le rouge forcé (`RAMULUS_ECHANGE_EXIGER=1`) n'échoue que pour « 004 non livrée » ;
  - aucun cas à `enregistrement` n'est ouvert comme fichier par le test générique ;
  - le test « classe fournie » exige `classe_saisie`.

## Terrain

- **Échantillon que Pascal vérifiera :** la liste des tests, en français.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la PR.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Rebasée sur 010 et sur 014, la CI de 004 est verte avec `RAMULUS_ECHANGE_EXIGER=1`.
