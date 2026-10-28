# ADR 0006 — Photos : convention de prise de vue, détenteur, licence, sortie de l'appareil

- **Date :** 2026-10-28
- **Statut :** proposée ; trois choix restent à trancher par Pascal (marqués)
- **Proposée par :** Claude (orchestrateur), après critique du `contradicteur`
- **Tranchée par :** Pascal

## La décision en une phrase

Avant la tranche 1, Ramulus fixe une convention de prise de vue sourcée ; les photos restent la propriété de
**[à trancher par Pascal]**, sous licence **[à trancher par Pascal]**, et ne sortent de l'appareil que dans le
paquet rendu à Nemeton et par la sauvegarde, pour servir à l'entraînement selon **[à trancher par Pascal]**.

## Contexte

- Brief validé : la note d'abord, photo sur échantillon (1 par arbre, 2 à 4 sur les placettes « collecte »).
- Pascal, 2026-10-28 : convention de prise de vue et ADR photos **avant** la tranche 1.
- `CLAUDE.md` : aucune photo n'est envoyée à un service distant sans décision de Pascal en ADR.
- L'entraînement (ADR 0002) oblige les photos à quitter l'appareil.

## Convention de prise de vue (proposition, à sourcer par l'`acquereur`)

- Depuis le sol, à une distance où tout le houppier fonctionnel tient dans le cadre (de l'ordre d'une hauteur
  d'arbre), soleil dans le dos, ni contre-jour ni ciel blanc dominant.
- Photo 1 : la même direction d'une année à l'autre (par exemple vers le centre de la placette) ; photos 2 à 4
  (placettes « collecte ») : à 90° les unes des autres.
- Horodatage, position et azimut de visée enregistrés avec la photo.
- Contrôles à la prise (Pascal, 2026-10-28) : flou, contre-jour, houppier coupé, signalés sans bloquer.
- Sources à vérifier : manuel ICP Forests, partie IV (photo guides) ; protocole d'images de Kälin et al. (WSL).

## Choix à trancher par Pascal

1. **Détenteur** : l'employeur de l'opérateur, Pascal, ou le projet Nemeton.
2. **Licence** du jeu de photos notées : ouverte (Etalab 2.0 ou CC BY 4.0) pour qu'il serve à d'autres, ou fermée.
3. **Entraînement** : où les photos sont-elles rassemblées (Nemeton, un stockage de Pascal) et qui entraîne.

## Le crible FRANCE

| Lettre | Réponse | Ce que ça coûte | Prêts à payer ? |
|---|---|---|---|
| **F** | Une convention simple, signalée sans bloquer | un peu de temps par arbre | à mesurer (spec, critère 7) |
| **R** | Le détenteur répond des photos | à désigner | à trancher |
| **A** | Visages, véhicules, propriétés privées dans le champ : à flouter ou à écarter avant toute diffusion | traitement avant partage | à trancher |
| **N** | Drone, photos hémisphériques : non examinés en v1 | — | dit |
| **C** | Stockage des photos (quelques Mo par arbre) | volume à prévoir chez Nemeton | à chiffrer |
| **É** | Un jeu ouvert servirait la recherche | dépend de la licence | à trancher |

## Valeurs sacrifiées

- **« Les photos ne quittent pas l'appareil »** : elles en sortent pour l'entraînement, dans les limites de cette
  ADR.

## Sources

- Manuel ICP Forests, partie IV ; Kälin et al., bioRxiv 441733 ; `CLAUDE.md` de Ramulus (règle des photos).

## Question de contrôle

Cette décision tient-elle nos six valeurs, et que sommes-nous prêts à payer pour cela ?
