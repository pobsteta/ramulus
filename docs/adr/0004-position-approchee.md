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

## Ajout du 2026-10-28 (Pascal, après la mission 001)

- **Correction de citation (G2).** Les chiffres « 6,1 m en moyenne, 13,4 m au CEP95 » cités plus haut sont la ligne
  « All » du tableau 4 de Purfürst 2022. Cette ligne inclut le récepteur géodésique de référence Geo 7X. Pour les
  téléphones seuls, la moyenne est de 6,50 m et le CEP95 va de 13,38 m (multifréquence) à 13,64 m (monofréquence).
  Le pire téléphone, le Xcover 4, a un CEP95 de 41,99 m. Source : `docs/referentiels/gnss-telephone.yaml`. La phrase
  d'origine est gardée, et cet ajout la corrige. Surculus, qui porte la même citation (ADR 0003), est prévenu par un
  brief.
- **Seuil de 30 m (G1).** Il est gardé comme **signal sans blocage**. Il reste **provisoire** jusqu'à la mesure de
  Pascal : 5 relevés du même piquet sous couvert, avec le téléphone de l'opérateur.
- **Règle de choix (K1).** Kraft 1 à 3 par défaut, d'après ICP partie IV, § 4.1 p. 6. Le DSF dit « dominant ou
  codominant » ; cet écart est écrit dans `docs/referentiels/kraft.yaml`.

## Ajout du 2026-10-29 (Pascal, mission 018) — on ne peint pas les arbres

Rien de ce qui précède n'est effacé ; ceci le corrige.

- **Décision de Pascal :** « Non on ne peint pas de numéro, les arbres sont incrémentés automatiquement depuis le nord
  dans le sens horaire d'une montre, de façon automatique. » Le « numéro à la peinture » de l'arbitrage du 2026-10-28
  tombe : le **rang**, attribué par l'application, suffit à rattacher un arbre.
- **Le rang** : le premier arbre noté d'une placette porte le rang 1, puis chaque arbre enregistré fait passer au
  suivant, jusqu'à `nb_arbres`. L'opérateur ne le choisit pas. Il peut rouvrir un arbre déjà noté pour le corriger :
  l'arbre garde son rang. Provisoire : une correction écrit une nouvelle ligne du même rang, signalée `doublon_arbre` au
  rendu ; le remplacement de la ligne est l'objet de la mission 015, et la règle de la ligne qui fait foi sera décidée
  par Pascal.
- **`repere` reste dans `deperis-v1`, facultatif (NULL permis).** Il désigne un repère physique qui existe déjà sur
  l'arbre (plaque, ancienne marque), jamais demandé à l'opérateur : si la tournée de départ en porte un pour cet arbre,
  Ramulus le recopie dans la notation ; sinon il écrit NULL. La suppression de la colonne avant le gel du format est une
  question posée à Pascal.
- **`doublon_arbre`** : même `plot_id` et même `rang` ; ou même `plot_id` et même `repere` si les deux `repere` sont non
  NULL. Deux NULL ne sont pas le même arbre.
- **Ce que ça coûte :** le suivi d'un arbre d'une année à l'autre repose sur le rang et la reproductibilité du tour
  d'horizon (même nord, même sens, même règle de choix), non plus sur une marque sur l'arbre. Si un arbre disparaît ou
  si la règle de choix change, les rangs se décalent. C'est le prix de ne rien peindre ; Pascal l'assume.

## Ajout du 2026-10-30 (Pascal, mission 015) — corriger, c'est remplacer

Rien de ce qui précède n'est effacé ; ceci remplace le « Provisoire » de la rubrique du 2026-10-29.

- **Décision de Pascal (2026-10-30, « je valide tes réponses », sur la recommandation de l'orchestrateur) :** une
  correction **remplace** la ligne ; elle n'ajoute pas une seconde ligne « dont la plus récente ferait foi ».
- **Note d'arbre.** Enregistrer de nouveau un arbre rouvert écrit une note de **nouvel `uuid`**, même `plot_id`, même
  `rang`, et retire l'ancienne de la copie de travail, dans une seule transaction. Le journal ajoute une ligne `notation`
  (ou `contre_notation`) dont `remplace_uuid` est la ligne du journal de l'ancienne note ; rien n'y est effacé.
- **Photos.** Chaque vue de l'ancienne note passe à la nouvelle **sous le même numéro** (`photos/<nouvel uuid>_<n>.jpg`,
  même SHA-256), sauf si l'opérateur la reprend : la photo reprise prend ce numéro, l'ancienne sort du paquet (gardée
  sur l'appareil). Chaque vue de la nouvelle note a sa ligne `photo` au journal, rattachée à la nouvelle note, avec
  `remplace_uuid` = la ligne de la vue de même numéro : un `.ramsync` restauré garde le lien photo ↔ note.
- **Centre.** Relever de nouveau remplace l'ancien de la même façon : au plus un centre par placette.
- **Placette close.** Une placette « notee » se corrige (arbre ou centre) de la même façon, et reste close
  (arbitrage de l'orchestrateur, par délégation de Pascal, 2026-10-30).
- **`doublon_arbre`** reste un contrôle du rendu ; il signale un vrai doublon (deux lignes du même rang dans un paquet,
  par exemple après l'union de deux opérateurs), jamais une correction.
- **Ce que ça coûte :** la copie de travail ne garde pas l'historique des notes ; il est dans le journal seul (et dans
  son `.ramsync`). Une correction concurrente reste un conflit, signalé, jamais résolu (ADR 0005, l. 33).

## Sources

- Note DGAL/SDQSPV/2018-433 du DSF, « Quantifier l'état de santé de la forêt, méthode simplifiée » :
  <https://agriculture.gouv.fr/telecharger/90879>.
- Arbitrages de Pascal, journal du 2026-10-28.
- Manuel ICP Forests, partie IV, § 5.2.12 et 6.1.2.1.
- Purfürst 2022, PMC8838512, tableau 4 (cité par Surculus, ADR 0003).

## Question de contrôle

Cette décision tient-elle nos six valeurs, et que sommes-nous prêts à payer pour cela ?
