# Mission 003 — Tests d'acceptation de l'échange

## Mission

**En une phrase (quoi, et pourquoi) :** écrire, depuis la spec et le format `deperis-v1` et sans lire le code de
la mission 004, les tests d'acceptation de la lecture et de l'écriture de la tournée, pour que le module GeoPackage
ne s'évalue pas lui-même.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | spec § 5, ADR 0003-0004, format 002 |
| A-t-il les moyens ? | **non** tant que 002 n'est pas fusionnée | attend 002 (« planifié ») |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `testeur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** l'implémentation ; lire le code de 004.

## Harnais

- **Peut :** écrire des tests JVM (JUnit 5) dans un module de test et des cas dans `fixtures/echange/`.
- **Ne doit pas :** lire le code de 004 ; modifier le format (une lacune remonte à l'orchestrateur).
- **Sources autorisées :** `docs/spec.md`, ADR 0003 et 0004, `docs/formats/deperis-v1.*`, `fixtures/`.

## Valeur provisoire (chiffre, réglage)

- aucune

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 002 (fusionnée).
- **Ordre de fusion :** 002 → 003 → 004.
- **PR en brouillon jusqu'à :** —
- **CI rouge attendue :** les tests de 003 sont rouges jusqu'à la fusion de 004 (rouge justifiée ; marqués
  `@Disabled` avec renvoi à 004, ou dans une source set lancée par 004 : au choix du relecteur).

## Tests

- **Comment saura-t-on que c'est juste :** chaque critère d'acceptation de la spec § 5 touchant l'échange (2, 3, 4,
  5, 6) a au moins un test, et chaque cas refusé du format au moins un.

## Terrain

- **Échantillon que Pascal vérifiera :** la liste des tests, lisible en français.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la PR.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

La mission 004 passe de rouge à vert sur ces tests sans qu'on les retouche.
