# Mission 010 — Amender `deperis-v1` : classe saisie et centre en conflit

## Mission

**En une phrase (quoi, et pourquoi) :** écrire dans le format `deperis-v1` les deux décisions de Pascal du
2026-10-28 : le motif `classe_saisie`, et la placette « notee » dont le centre est écarté pour conflit, gardée et
signalée. Le but : que Ramulus (004), Nemeton et le contrôle de référence suivent la même règle.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | décisions écrites ci-dessous ; format, générateurs et contrôle de référence de 002 |
| A-t-il les moyens ? | oui, vérifié dans la session le 2026-10-28 | Python 3, `ogrinfo`, `.claude/test-command` présents |

**Temps de la main :** appuyer. Le format dure, mais les deux règles sont déjà tranchées par Pascal : l'agent écrit,
il ne décide pas.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** le code de l'appareil (004) ; resserrer les tests d'acceptation de 003 (le
  `testeur`, ensuite).

## Les décisions à écrire (Pascal, 2026-10-28, journal)

1. **T2 bis, motif `classe_saisie`.** Une note qui arrive avec une classe déjà remplie, juste ou fausse, est refusée
   **à l'enregistrement par Ramulus** avec ce motif : la classe ne se saisit pas, elle se calcule par l'abaque. Le
   contrôle d'un paquet rendu ne change pas : `classe_differente_abaque` (R2).
2. **F1, centre en conflit.** À la fusion des paquets de deux opérateurs, un centre arrive avec deux contenus
   différents ; ses deux versions sont écartées (`uuid_contenu_different`). La placette « notee » et ses notes sont
   **gardées**, et la placette est **signalée** : l'opérateur dit quel centre fait foi, puis rend de nouveau.

## Harnais

- **Peut :**
  - modifier `docs/formats/deperis-v1.md` et `deperis-v1.schema.json` ;
  - dans `fixtures/echange/` : `deperis_v1.py`, `generer.py`, `generer_complements.py`, le README et de nouveaux cas
    (au moins un cas de fusion F1) ;
  - dans `CasCommunsLusTest.kt` (garde-fou de 003), **seulement** les deux nombres attendus (24 motifs, 36 cas) et
    leur message.
- **Ne doit pas :**
  - toucher à une autre assertion des tests de 003 ;
  - inventer une règle au-delà des deux décisions (une lacune remonte à l'orchestrateur) ;
  - toucher à `main`.
- **Sources autorisées :**
  - `docs/formats/deperis-v1.*` ;
  - `fixtures/echange/` ;
  - `pilotage/journal.md` (décisions du 2026-10-28) ;
  - Surculus `docs/formats/sursync-v1.md` (l. 63, au commit 3c01f4b).

## Valeur provisoire (chiffre, réglage)

- aucune

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune (002 et 003 sont fusionnées) ; part de `main`.
- **Ordre de fusion :** **010 → 004**. 004 part en parallèle et se rebase sur 010.
- **PR en brouillon jusqu'à :** —
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :**
  - les tests Python passent, avec un cas F1 ;
  - `ogrinfo` ouvre tous les fichiers ;
  - le garde-fou compte le nouveau motif et les nouveaux cas ;
  - `.claude/test-command` est vert (les tests de 003 restent sautés jusqu'à 004).

## Terrain

- **Échantillon que Pascal vérifiera :** le cas F1, lu dans QField. On doit y voir la placette signalée, ses notes
  présentes et aucun centre.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la PR.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

004 implémente la fusion et le refus d'une classe fournie en suivant le format, sans question nouvelle. Le `testeur`
resserre ensuite le test « classe fournie » sur `classe_saisie`.
