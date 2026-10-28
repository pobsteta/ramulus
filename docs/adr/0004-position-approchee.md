# ADR 0004 — Position approchée : le centre au GNSS du téléphone et au piquet, les arbres par leur rang et un numéro peint

- **Date :** 2026-10-28
- **Statut :** acceptée par Pascal (2026-10-28, porte n° 1), après critique du `contradicteur`
- **Proposée par :** Claude (orchestrateur)
- **Tranchée par :** Pascal

## La décision en une phrase

Ramulus relève le centre de chaque placette au GNSS du téléphone, moyenné, et le centre est matérialisé par un
piquet ; chaque arbre noté, sans position propre, est pris dans l'ordre d'un tour d'horizon depuis le nord (sens
horaire) parmi les arbres de la règle, et porte un numéro peint saisi dans la tournée, pour retrouver les mêmes
arbres l'année suivante et à la contre-notation.

## Contexte

- Brief validé : placettes tirées par Nemeton, règle de choix des arbres en paramètre de campagne ; Nemeton fait
  foi et calcule le bilan par placette et par unité de gestion.
- Options présentées à Pascal le 2026-10-28 : GNSS du téléphone et distance-azimut par arbre ; RTK obligatoire comme
  Groma ; position approchée (retenue).
- Le bilan DEPERIS d'un peuplement ne demande que les classes des arbres d'une placette rattachée à son unité de
  gestion (note DGAL 2018-433 : placettes de 20 arbres) : la position de chaque arbre n'y entre pas.

## Arbitrages après la critique (Pascal, 2026-10-28)

- **État et suivi** : position approchée gardée, avec un **repère léger** : piquet au centre, **numéro à la
  peinture** sur chaque arbre noté (colonne `repere` de `notations`). Le suivi des mêmes arbres et la contre-notation
  sur les mêmes arbres tiennent.
- **Ordre** : `regle_choix` porte un ordre fixe, **tour d'horizon depuis le nord dans le sens horaire**, parmi les
  arbres de Kraft 1 à 3 par défaut ; le rang est la position dans ce tour.
- **Seuil de 30 m** : simple signal d'erreur grossière ; il ne détecte pas un changement de lot d'arbres (erreur
  GNSS sous couvert : 6,1 m en moyenne, 13,4 m au CEP95, Purfürst 2022, repris de Surculus ADR 0003) ; c'est le
  piquet et le numéro peint qui le font.

## Conséquences sur le contrat (ADR 0003)

- `centres` (POINT) : position moyennée, précision horizontale, nombre de mesures, source (`telephone`, ou `rtk` si
  un récepteur est branché), `date_releve`.
- `notations` et `contre_notations` deviennent des **tables d'attributs sans géométrie** (GeoPackage, table
  `attributes`, variante par défaut de GDAL), rattachées par `plot_id`, `rang` et `repere` (numéro peint) ; une
  fixture vérifie qu'elles se relisent en R et dans QField ; plus de distance, d'azimut, ni de convergence des méridiens.
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

- **La position de chaque arbre** : on retrouve l'arbre par son numéro peint, pas par ses coordonnées ; un numéro
  effacé se refait par le tour d'horizon depuis le piquet.
- **Le temps** de peindre un numéro et de planter un piquet à la première visite.
- **La mesure de la mortalité sans repère** : un arbre mort reste repérable par son numéro ; un arbre abattu et
  remplacé par la règle sort de la série (état « non noté » non retenu, spec § 4).
- **Le rapprochement avec Groma** (inventaire arbre par arbre) : impossible sans position.

## Sources

- Note DGAL/SDQSPV/2018-433 du DSF, « Quantifier l'état de santé de la forêt, méthode simplifiée » :
  <https://agriculture.gouv.fr/telecharger/90879>.
- Arbitrages de Pascal, journal du 2026-10-28.
- Manuel ICP Forests, partie IV, § 5.2.12 et 6.1.2.1.
- Purfürst 2022, PMC8838512, tableau 4 (cité par Surculus, ADR 0003).

## Question de contrôle

Cette décision tient-elle nos six valeurs, et que sommes-nous prêts à payer pour cela ?
