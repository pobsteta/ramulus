# ADR 0001 — L'IA propose, l'opérateur décide

- **Statut :** proposée (2026-10-28), critiquée par le `contradicteur` le même jour, amendée d'après les arbitrages de
  Pascal ; à trancher
- **Décideur :** Pascal

## Contexte

Essai du 2026-10-28 ([rapport](../essais/2026-10-28-essai-ia-a-l-aveugle.md)) : sur 237 photos des fiches DSF, un
modèle généraliste trouve la même classe DEPERIS dans 54 % des cas et à une classe près dans 97 % ; il sous-note
(87 sous-notations pour 23 sur-notations) et ne reconnaît que 11 arbres morts sur 26. La variabilité entre
notateurs humains n'est pas chiffrée par le DSF pour DEPERIS ; ICP Forests exige qu'au moins 70 % des notations
répétées s'écartent de moins de ±10 % de défoliation (manuel, partie IV, § 6.1.2). Ramulus la mesurera par une
contre-notation à l'aveugle de 5 % des placettes (brief).

La tranche 1 se fait **sans IA** (Pascal, 2026-10-28) : cette ADR fixe la règle pour le moment où l'IA arrivera.

## Décision proposée

1. **La note enregistrée est toujours celle d'un opérateur.** L'IA ne remplit jamais seule MB ni MR/MA.
2. **La proposition de l'IA est gardée à côté de la note** (`PropositionIa` : notes, confiance, modèle), avec
   l'origine `OPERATEUR_AIDE_IA`. On ne garde pas de proposition pour une notation faite sans aide.
3. **La classe se calcule** par l'abaque à partir des notes retenues ; elle ne se saisit pas et ne vient jamais
   directement de l'IA.
4. **La proposition s'affiche après la note de l'opérateur** (Pascal, 2026-10-28) : l'opérateur pose MB et MR ou MA,
   puis voit ce que l'IA proposait, et peut corriger. On garde sa première note, la proposition et la note finale :
   c'est la parade contre l'ancrage, et c'est ce qui donne de la valeur aux couples comme données.

## Conséquences

- Valeurs sacrifiées : un peu de temps par arbre (deux regards au lieu d'un) ; la commodité d'une note pré-remplie.
- Chaque notation aidée produit un couple (proposition, note retenue) : mesure continue de l'IA sur le terrain, et
  jeu de données pour entraîner le modèle embarqué.
- Où tourne l'IA : embarquée, entraînée après la collecte de la tranche 1 (Pascal, 2026-10-28) ; l'ADR 0002 l'écrira
  au crible FRANCE.

## Options écartées

- **IA seule, opérateur en contrôle par sondage** : écartée tant que l'IA rate plus d'un arbre mort sur deux.
- **IA au tri seulement** (sain / dégradé pour orienter le passage) : non retenue, mais reste une option de repli si
  l'IA ne progresse pas sur les notes (un seul faux dégradé sur 62 arbres sains dans l'essai).
- **IA au bureau seulement** (contrôle après coup sur photos) : non retenue ; l'échantillon de photos le permettrait.
- **Proposition affichée avant la note** : écartée, à cause de l'ancrage.
