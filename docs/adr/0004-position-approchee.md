# ADR 0004 — Position approchée : le centre de placette au GNSS du téléphone, les arbres par leur rang

- **Date :** 2026-10-28
- **Statut :** proposée d'après l'arbitrage de Pascal du 2026-10-28 (« position approchée suffit »)
- **Proposée par :** Claude (orchestrateur)
- **Tranchée par :** Pascal

## La décision en une phrase

Ramulus relève le centre de chaque placette au GNSS du téléphone, moyenné, avec sa précision, et désigne chaque
arbre noté par son rang dans la règle de la campagne, sans position propre.

## Contexte

- Brief validé : placettes tirées par Nemeton, règle de choix des arbres en paramètre de campagne ; Nemeton fait
  foi et calcule le bilan par placette et par unité de gestion.
- Options présentées à Pascal le 2026-10-28 : GNSS du téléphone et distance-azimut par arbre ; RTK obligatoire comme
  Groma ; position approchée (retenue).
- Le bilan DEPERIS d'un peuplement ne demande que les classes des arbres d'une placette rattachée à son unité de
  gestion (note DGAL 2018-433 : placettes de 20 arbres) : la position de chaque arbre n'y entre pas.

## Conséquences sur le contrat (ADR 0003)

- `centres` (POINT) : position moyennée, précision horizontale, nombre de mesures, source (`telephone`, ou `rtk` si
  un récepteur est branché), `date_releve`.
- `notations` et `contre_notations` deviennent des **tables d'attributs sans géométrie** (GeoPackage, table
  `attributes`), rattachées par `plot_id` et `rang` ; plus de distance, d'azimut, ni de convergence des méridiens.
- Un test vérifie qu'un centre relevé en France tombe en France, et qu'un centre à plus de N mètres du point prévu
  est signalé (N à fixer par Pascal ; proposition : 30 m, de l'ordre de la précision d'un téléphone sous couvert
  forestier, à vérifier par l'`acquereur`).

## Le crible FRANCE

| Lettre | Réponse | Ce que ça coûte | Prêts à payer ? |
|---|---|---|---|
| **F** | Pas de récepteur, pas de boussole, pas de télémètre | — | oui |
| **R** | L'opérateur relève le centre ; Nemeton rattache la placette à l'unité de gestion | — | oui |
| **A** | Centre trop loin du point prévu : signalé ; arbres impossibles à retrouver un à un | voir valeurs sacrifiées | à confirmer par Pascal |
| **N** | Distance-azimut, RTK : présentés, non retenus | — | — |
| **C** | Le plus économe en temps et en matériel | — | oui |
| **É** | Les placettes restent compatibles avec un plan Nemeton ; un relevé plus précis pourra s'ajouter (colonne de source) | — | — |

## Valeurs sacrifiées

- **Le suivi du même arbre d'une année à l'autre** : sans position, on suit la placette, pas l'arbre ; un arbre
  mort, abattu ou remplacé par la règle ne se distingue pas.
- **La contre-notation sur les mêmes arbres** : le second opérateur doit retrouver les arbres par la seule règle et
  le rang ; un écart de choix se confond avec un écart de note. **À confirmer par Pascal**, ou à compenser par un
  repère sur l'arbre (marque, plaquette) noté dans la tournée.
- **Le rapprochement avec Groma** (inventaire arbre par arbre) : impossible sans position.

## Sources

- Note DGAL/SDQSPV/2018-433 du DSF, « Quantifier l'état de santé de la forêt, méthode simplifiée » :
  <https://agriculture.gouv.fr/telecharger/90879>.
- Arbitrage de Pascal, journal du 2026-10-28.

## Question de contrôle

Cette décision tient-elle nos six valeurs, et que sommes-nous prêts à payer pour cela ?
