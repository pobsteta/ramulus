# ADR 0001 — L'IA propose, l'opérateur décide

- **Statut :** proposée (2026-10-28), à soumettre au `contradicteur` puis à Pascal
- **Décideur :** Pascal

## Contexte

Essai du 2026-10-28 ([rapport](../essais/2026-10-28-essai-ia-a-l-aveugle.md)) : sur 237 photos des fiches DSF, un
modèle généraliste trouve la même classe DEPERIS dans 54 % des cas et à une classe près dans 97 % ; il sous-note
(87 sous-notations pour 23 sur-notations) et ne reconnaît que 11 arbres morts sur 26. La variabilité entre
notateurs humains est elle aussi de l'ordre d'une classe.

## Décision proposée

1. **La note enregistrée est toujours celle d'un opérateur.** L'IA ne remplit jamais seule MB ni MR/MA.
2. **La proposition de l'IA est gardée à côté de la note** (`PropositionIa` : notes, confiance, modèle), avec
   l'origine `OPERATEUR_AIDE_IA`. On ne garde pas de proposition pour une notation faite sans aide.
3. **La classe se calcule** par l'abaque à partir des notes retenues ; elle ne se saisit pas et ne vient jamais
   directement de l'IA.

## Conséquences

- Chaque notation aidée produit un couple (proposition, note retenue) : mesure continue de l'IA sur le terrain, et
  jeu de données pour entraîner le modèle embarqué.
- Risque d'**ancrage** : l'opérateur suit la proposition. Parade proposée **[à trancher]** : montrer la proposition
  après une première note de l'opérateur, ou seulement sur demande.
- La décision ne dit pas **où** tourne l'IA (embarquée ou distante) : ADR 0002 à écrire.

## Options écartées

- **IA seule, opérateur en contrôle par sondage** : écartée tant que l'IA rate plus d'un arbre mort sur deux.
- **Pas d'IA** : écartée, l'aide au tri sain / dégradé est déjà utile (un seul faux dégradé sur 62 arbres sains).
