# ADR 0006 — Photos : convention de prise de vue, détenteur, licence, sortie de l'appareil

- **Date :** 2026-10-28
- **Statut :** proposée ; trois choix restent à trancher par Pascal (marqués)
- **Proposée par :** Claude (orchestrateur), après critique du `contradicteur`
- **Tranchée par :** Pascal

## La décision en une phrase

Avant la tranche 1, Ramulus fixe une convention de prise de vue dont chaque règle dit sa source ; les photos ne
sortent de l'appareil que dans le paquet rendu à Nemeton et par la sauvegarde, sans EXIF dans le jeu publié, et
leurs droits (cession, producteur, licence, entraînement) sont tranchés par Pascal avant la collecte.

## Contexte

- Brief validé : la note d'abord, photo sur échantillon (1 par arbre, 2 à 4 sur les placettes « collecte »).
- Pascal, 2026-10-28 : convention de prise de vue et ADR photos **avant** la tranche 1.
- `CLAUDE.md` : aucune photo n'est envoyée à un service distant sans décision de Pascal en ADR.
- L'entraînement (ADR 0002) oblige les photos à quitter l'appareil.

## Convention de prise de vue (proposition ; chaque règle dit si elle est sourcée)

| Règle | Source |
|---|---|
| Une photo par arbre, cadrée sur le houppier entier | Kälin et al. (une photo par arbre) — sourcée |
| Observer depuis environ une hauteur d'arbre, en plein jour | ICP Forests, partie IV, § 6.1.4 (pour l'observation, pas pour la photo) — sourcée par analogie |
| Éviter le contre-jour : il fait surestimer la défoliation des résineux | Kälin et al. — sourcée |
| Photos 2 à 4 à 90° les unes des autres (placettes « collecte ») | non sourcée |
| Même direction d'une année à l'autre | non sourcée |
| Saison feuillée pour les photos | ICP Forests (milieu ou fin de l'été) ; à trancher (ADR 0002) |
| Horodatage, position et azimut de visée gardés dans le GeoPackage ; **EXIF retiré** des photos versées au jeu | règle de confidentialité (ci-dessous) |
| Contrôles à la prise (flou, contre-jour, houppier coupé), signalés sans bloquer | Pascal, 2026-10-28 |

Les « photo guides » d'ICP Forests sont des photos de référence pour former les notateurs (§ 4.1, 6.1.3), pas un
protocole de prise de vue : l'`acquereur` lira le formulaire PHOT d'ICP avant la tranche 1.

## Droits et confidentialité

- **Auteur** : le droit d'auteur, s'il existe sur une photo technique, naît chez l'opérateur qui photographie ; le
  contrat de travail ne le transfère pas sans cession écrite (CPI L111-1). Le cas des agents publics (ONF) n'est pas
  vérifié.
- **Producteur du jeu** : celui qui investit dans la constitution du jeu peut détenir un droit de producteur de base
  de données (CPI L341-1, non vérifié).
- **Licence** : Etalab 2.0 (compatible CC BY) suppose que le donneur garantisse l'absence de droits de tiers et
  signale les données personnelles.
- **Opérateur** : position et heure attachées à une photo sont des données de localisation d'un salarié (RGPD,
  art. 4.1) ; on reprend la règle de Surculus (ADR 0005) : finalité écrite (traçabilité de la note, non le contrôle
  de l'agent), fichiers sans nom d'agent, employeurs informés.
- **Biens et personnes visibles** : visages et plaques floutés ou photos écartées avant toute diffusion ; un
  propriétaire peut s'opposer à un usage de l'image de son bien qui lui cause un trouble anormal (Cass. ass. plén.,
  7 mai 2004) : le jeu publié ne dit jamais quelle forêt est « dégradée » sans l'accord de Nemeton.
- **Stockage sur l'appareil** : photos sous `Documents/Ramulus/` (MediaStore), avec un `.nomedia`, pour qu'aucune
  galerie ne les indexe ni ne les envoie dans un nuage ; vérifié par un test sur appareil.

## Choix à trancher par Pascal

1. **Cession** : faire signer une cession des droits des opérateurs (à qui), ou s'en passer.
2. **Producteur du jeu** : Pascal, le projet Nemeton, ou l'employeur.
3. **Licence du jeu** : ouverte (Etalab 2.0 ou CC BY 4.0) ou fermée.
4. **Entraînement** : où les photos sont rassemblées, et qui entraîne.

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
- **La vie privée de l'opérateur** : réduite par la règle de confidentialité, pas supprimée.
- **Le temps de nettoyer le jeu** (floutage, EXIF) avant de l'ouvrir.

## Sources

- Manuel ICP Forests, partie IV (2020), § 4.1, 6.1.3, 6.1.4 ; Kälin et al., bioRxiv 441733 ; CPI L111-1 ;
  RGPD art. 4.1 ; Cass. ass. plén., 7 mai 2004 ; licence Etalab 2.0 (data.gouv.fr) ; Surculus ADR 0005 ;
  `CLAUDE.md` de Ramulus.

## Question de contrôle

Cette décision tient-elle nos six valeurs, et que sommes-nous prêts à payer pour cela ?
