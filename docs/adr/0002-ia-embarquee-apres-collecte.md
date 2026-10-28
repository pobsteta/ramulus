# ADR 0002 — IA embarquée, entraînée après la collecte, livrée au-dessus d'un seuil mesuré par placette

- **Date :** 2026-10-28
- **Statut :** proposée, amendée après critique du `contradicteur` (2026-10-28)
- **Proposée par :** Claude (orchestrateur)
- **Tranchée par :** Pascal

## La décision en une phrase

La tranche 1 se fait sans IA et collecte des photos selon une convention fixée d'avance (ADR 0006) ; un modèle
tournant sur le téléphone est ensuite entraîné sur cette collecte, et n'est livré que s'il atteint, sur un jeu de
test séparé par placette et par campagne, un seuil fixé par Pascal avant l'entraînement.

## Contexte

- Pascal (2026-10-28) : notation manuelle d'abord, collecte, modèle embarqué ensuite.
- Essai à l'aveugle du 2026-10-28 (modèle généraliste distant, photos des fiches DSF) : 54 % de classes
  identiques, 97 % à une classe près ; cas favorable (photos choisies, publiques).
- Kälin et al. (WSL) : environ 2 100 images ; erreur moyenne de 5,5 % de défoliation quand la référence est la
  moyenne de six experts notant sur image, 14,8 % quand elle vient du terrain. **La référence fait le résultat.**
- Une note de l'arbre entier n'est pas une étiquette de photo (contradicteur, 2026-10-28).
- Options envisagées : IA distante dès la tranche 1 ; IA au tri seulement ; IA au bureau ; pas d'IA du tout.

## Protocole de mesure (fixé avant toute collecte)

1. **Découpage** : le test est tenu à part **par placette** (jamais une photo d'un arbre dans l'entraînement et une
   autre dans le test) et **par campagne** (la dernière campagne mise à part, pour mesurer la tenue d'une année à
   l'autre).
2. **Référence** : la note retenue par l'opérateur ; sur les placettes de contre-notation, l'accord entre les deux
   opérateurs donne le plafond atteignable.
3. **Mesures** : classe exacte ; classe à un près ; erreur sur MB ; rappel des arbres D-E-F et des arbres F.
4. **Seuil de livraison** : à fixer par Pascal avant l'entraînement — proposition : « le modèle s'accorde avec
   l'opérateur au moins autant que deux opérateurs entre eux », mesuré sur les placettes de contre-notation.
5. **Repli** : sous le seuil, l'IA se limite au tri sain / dégradé, si son rappel des D-E-F atteint un seuil, lui
   aussi fixé par Pascal ; sinon, pas d'IA.

## Le crible FRANCE

| Lettre | Réponse | Ce que ça coûte | Prêts à payer ? |
|---|---|---|---|
| **F** | Pas d'IA tant qu'elle n'apporte rien ; un petit modèle sur le téléphone | rien en tranche 1 | oui |
| **R** | L'opérateur reste maître de la note (ADR 0001) ; **qui fournit les milliers d'arbres notés n'est pas tranché** | un partenariat à trouver | à trancher par Pascal |
| **A** | Si le seuil n'est jamais atteint : repli au tri, puis pas d'IA ; le format garde la place de la proposition | une collecte qui peut ne servir à rien | oui, risque dit |
| **N** | IA distante, au tri, au bureau, pas d'IA : examinées (options écartées ci-dessous, ADR 0001) | — | — |
| **C** | Temps de collecte (photos sur échantillon) et d'entraînement ; mesuré en tranche 1 (spec, critère 7) | non chiffré | à mesurer |
| **É** | Le jeu de données réutilisable par d'autres, si sa licence le permet (ADR 0006) | — | — |

## Valeurs sacrifiées

- **La qualité immédiate** d'un grand modèle distant : refusée pour rester hors ligne et garder les photos.
- **Le temps de l'opérateur** pendant la collecte, sans retour d'IA en échange.
- **L'assurance que la collecte serve** : elle peut ne jamais atteindre le seuil ; on l'accepte, la note du
  gestionnaire reste servie de toute façon.
- **« Les photos ne quittent pas l'appareil »** : l'entraînement les en fait sortir, selon l'ADR 0006.

## Options écartées

- **IA distante dès la tranche 1** : réseau absent en forêt, photos envoyées à un tiers.
- **IA au bureau seulement** : non retenue par Pascal (ADR 0001) ; reste possible sur l'échantillon de photos.
- **Pas d'IA du tout** : Pascal veut l'IA embarquée à terme.

## Sources

- Kälin et al., « Defoliation estimation of forest trees from ground-level images », bioRxiv 441733,
  <https://www.biorxiv.org/content/10.1101/441733v2.full>.
- Essai du 2026-10-28 : `docs/essais/2026-10-28-essai-ia-a-l-aveugle.md`.
- Manuel ICP Forests, partie IV, § 6.1.2 (contrôle de qualité).

## Question de contrôle

Cette décision tient-elle nos six valeurs, et que sommes-nous prêts à payer pour cela ?
