# ADR 0007 — IA embarquée amorcée sur les photos du DSF, qui apprend sur l'appareil des notes validées

- **Date :** 2026-10-30
- **Statut :** acceptée par Pascal (2026-10-30), après critique du `contradicteur` (journal du 2026-10-30)
- **Proposée par :** Claude (orchestrateur), à partir des mots de Pascal
- **Tranchée par :** Pascal (principe) ; les points ouverts ci-dessous restent à trancher
- **Modifie :** ADR 0002 (le calendrier : une proposition dès maintenant, sans attendre deux campagnes). L'ADR 0002
  n'est pas effacée : son protocole de mesure reste celui qui décidera si la proposition sort du statut « essai ».

## La décision en une phrase

Ramulus embarque dès maintenant un modèle qui propose une classe DEPERIS à partir des 1 à 4 photos d'un arbre ; il est
amorcé sur les photos notées des fiches du DSF et s'ajuste, sur l'appareil seulement, aux notes validées par les
opérateurs ; la proposition s'affiche après la note de l'opérateur (ADR 0001).

## Les mots de Pascal

- 2026-10-30 : « livre une version avec l'IA qui propose une classification à partir des photos, qu'il y en est 1 ou
  4 ».
- 2026-10-30, à la question « où doit-elle tourner ? » : « embarque un modèle IA fabriquée à partir des photos du DSF
  et qui s'autocorrige au fur et à mesure des prises de photos et des notations validées par les utilisateurs ».
- 2026-10-30, à la question « quand l'opérateur voit-il la proposition ? » : après sa note (ADR 0001).

## Contexte

- Aucun modèle DEPERIS n'existe ; 250 photos notées dans 25 fiches (fixtures `fiches-dsf-2025.json`, notes seules) ;
  c'est trop peu pour entraîner un modèle de zéro (Kälin et al. : environ 2 100 images).
- Essai à l'aveugle du 2026-10-28 (modèle généraliste distant) : 54 % de classes identiques, 97 % à une classe près.
- Les photos ne quittent pas l'appareil (CLAUDE.md, ADR 0006) : l'apprentissage local respecte cette règle.

## Décision détaillée (proposée)

1. **Un extracteur pré-entraîné, gelé**, petit et sous licence libre (MobileNet ou EfficientNet-Lite, poids
   ImageNet ; choix sourcé par l'`acquereur`), exécuté sur le téléphone (LiteRT/TensorFlow Lite).
2. **Une tête de classement amorcée** sur les vecteurs des photos du DSF, avec leurs notes (MB, et MR/MA quand la fiche
   les imprime) ; la classe est toujours calculée par l'abaque depuis les notes proposées (ADR 0001, point 3).
3. **Plusieurs photos** : la proposition combine les vecteurs des 1 à 4 vues de l'arbre (moyenne), avec une confiance.
4. **Apprentissage sur l'appareil** : chaque arbre noté et validé ajoute ses vecteurs et ses notes à une mémoire
   locale ; la tête est recalculée sur le téléphone (plus proches voisins ou centroïdes pondérés, sans réseau). Les
   photos et les vecteurs restent sur l'appareil.
5. **Après la note** : l'opérateur note, valide, puis voit la proposition et peut corriger ; on garde sa première note,
   la proposition, le modèle et la note finale (colonnes `ia_*` de `deperis-v1`).
6. **Statut « essai »** affiché à l'écran tant que le protocole de l'ADR 0002 n'a pas mesuré le modèle au-dessus du
   seuil fixé par Pascal.

## Points ouverts, à trancher par Pascal

- **Droits sur les photos du DSF.** La licence etalab-2.0 couvre le texte de la page du ministère, pas les PDF ni leurs
  photos (brief, point 3) ; Pascal n'a pas retenu, le 2026-10-28, de demander l'accord du DSF pour un usage hors
  ligne. Entraîner un modèle sur ces photos et le distribuer dans l'APK est un usage nouveau.
- **Ce que vaut la mesure** : un modèle amorcé et corrigé sur les notes des mêmes opérateurs ne peut pas être mesuré
  sur ces notes ; les placettes de contre-notation restent hors apprentissage (ADR 0002, point 2).
- **Divergence entre téléphones** : chaque appareil apprend seul ; deux opérateurs peuvent avoir deux modèles.

## Le crible FRANCE

| Lettre | Réponse | Ce que ça coûte | Prêts à payer ? |
|---|---|---|---|
| **F** | Un petit extracteur gelé et une tête simple, sur le téléphone | quelques Mo dans l'APK | à mesurer |
| **R** | L'opérateur reste maître de la note ; le DSF reste maître de ses photos | droits à clarifier | à trancher par Pascal |
| **A** | Si la proposition est mauvaise, elle reste « essai » et l'opérateur la corrige ; on peut la masquer | une aide qui peut induire en erreur | à trancher |
| **N** | IA distante (Claude) et IA au bureau, proposées le 2026-10-30, non retenues par Pascal | — | — |
| **C** | Calcul sur le téléphone à chaque arbre ; mémoire locale qui grossit | batterie, stockage | à mesurer |
| **É** | Le modèle appris sur un téléphone pourrait servir aux autres | non prévu ici | — |

## Ajout du 2026-10-30 : décisions de Pascal après la critique du `contradicteur`

Le texte ci-dessus est gardé ; cet ajout le complète et le précise.

- **Droits (Pascal, « Comme prévu »)** : l'amorce se fait sur les photos des fiches DSF, avec une tête par plus proches
  voisins, sans demande préalable au DSF. Les mentions légales du ministère réservent les photos (« Toutes les photos,
  illustrations et vidéos diffusées sur ce site sont la propriété du ministère » ; etalab-2.0 couvre les textes) :
  **Pascal assume ce risque**. Les photos ne sont jamais commitées ; seuls les vecteurs et les notes entrent dans l'APK.
- **Seuil (ADR 0002, fixé avant l'amorce)** : la proposition perd l'étiquette « essai » quand l'IA s'accorde avec
  l'opérateur au moins autant que deux opérateurs entre eux, mesuré sur les placettes de contre-notation, essence par
  essence et saison par saison. D'ici là : « essai » partout.
- **Bilan de peuplement** : calculé sur la **note finale** ; première note et proposition gardées dans les colonnes
  `ia_*`, pour recalculer sans IA.
- **Affichage** : après la note, **à chaque arbre**, classe proposée, notes MB et MR/MA, confiance, mention « essai ».
- **Arbitrages de l'orchestrateur, par délégation** (critique du `contradicteur`) :
  - le téléphone n'apprend que de la **première note** de l'opérateur, jamais de la note finale ;
  - les placettes de **contre-notation** ne servent jamais à l'apprentissage ;
  - la mesure de l'ADR 0002 porte sur l'**amorce figée** ; l'ajustement local est un confort, non mesuré, dit comme tel ;
  - avant toute intégration, l'amorce est mesurée en **validation croisée par fiche laissée de côté** ; le chiffre est
    publié à côté des 54 % de l'essai du 2026-10-28, qui venaient d'un autre modèle ;
  - le **tracé vert** des fiches est masqué ou les photos sont recadrées avant l'extraction des vecteurs ;
  - les **quatre incohérences connues** des fiches sont exclues de l'amorce (listées, jamais corrigées) ;
  - le vote ne compare qu'à des arbres de **la même essence** ;
  - l'**identité du modèle** (`ia_modele`) porte l'empreinte de l'amorce, celle de la mémoire locale et le nombre
    d'exemples ;
  - la **mémoire locale** est jetable et reconstructible depuis les notes et les photos présentes, liée à l'empreinte
    de l'extracteur, et ne sort pas de l'appareil ;
  - une **fiche modèle** (données d'amorce, droits, mesures, limites par essence et saison) accompagne l'APK.

## Valeurs sacrifiées

- **La sécurité juridique de la diffusion** : assumée par Pascal.
- **La justesse de la note finale** : un ancrage résiduel reste possible même après la note (Dratsch et al., 2023).
- **La mesurabilité de ce qui tourne** : chaque téléphone a son modèle ; seule l'amorce figée est mesurée.
- **L'identité du modèle d'un téléphone à l'autre.**

## Sources

Kälin et al., bioRxiv 441733 v2 ; mentions légales d'agriculture.gouv.fr ; CRPA L321-2 ; CPI L122-5-3 ; Perdomo et
al., « Performative Prediction », ICML 2020 ; Dratsch et al., Radiology 2023 ; rapport
`docs/essais/2026-10-30-moyens-ia-embarquee.md`.
