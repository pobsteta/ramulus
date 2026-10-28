# ADR 0003 — Échange avec Nemeton : la tournée et le paquet rendu

- **Statut :** proposée (2026-10-28), à critiquer par le `contradicteur`, puis à trancher par Pascal ; brief vers
  Nemeton à écrire ensuite (`pilotage/briefs/vers-nemeton/`)
- **Décideur :** Pascal

## Contexte

Brief validé : le plan d'échantillonnage est fait sur Nemeton ; **Nemeton fait foi** et importe le paquet arbre par
arbre ; **sommieR reçoit de Nemeton** le bilan par unité de gestion. Groma a déjà un contrat avec nemeton sur le
même modèle (`groma/briefs/vers-nemeton/2026-10-12-inventaire-video.md`) : GeoPackage EPSG:2154, couche
`placettes` issue du plan (colonnes de plan cachées, jamais modifiées), couches écrites par l'application, schéma
JSON comme source unique, import par une fonction de nemeton qui valide puis fusionne par `plot_id`.

## Décision proposée

1. **Un seul fichier, aller et retour** : `deperis.gpkg`, EPSG:2154, écrit par Nemeton (fonction proposée
   `creer_tournee_deperis()`), complété par Ramulus, relu par Nemeton (`importer_deperis()`). Pas de second format.
2. **Couches** :
   - `placettes` (POINT, point prévu) : colonnes du plan nemeton (`plot_id`, `wgt`, `ip`, `stratum`…) jamais
     modifiées ; paramètres de campagne `nb_arbres`, `regle_choix` (défaut `kraft_1_3`), `saison` (`ete`/`hiver`),
     `collecte_photos` (booléen), `contre_notation` (booléen) ; `etat` (`a_faire`/`notee`) écrit par Ramulus ;
   - `centres` (POINT) : position relevée, précision, source GNSS, date, opérateur ;
   - `notations` (POINT ou distance-azimut, ADR 0004) : `uuid`, `plot_id`, `rang`, `essence`, `mb`, `second`,
     `critere` (`MR`/`MA`), `classe`, `operateur`, `horodatage`, `photos`, `contre_notation` (booléen), et les
     colonnes réservées de l'IA (ADR 0001), vides en tranche 1.
3. **Schéma JSON** source unique dans le dépôt Ramulus (`docs/formats/deperis-v1.schema.json`), copié dans nemeton,
   avec des cas communs (`fixtures/echange/`) rejoués en Kotlin et en R, comme Groma et Surculus.
4. **Nemeton recalcule la classe** à l'import par l'abaque et refuse un paquet où elle diffère des notes.
5. **sommieR** ne lit jamais le paquet : il reçoit de Nemeton le bilan par unité de gestion (brief vers
   nemetonshiny ou sommieR à écrire après ce contrat).

## Crible FRANCE

- **Frugale** : un fichier, un format ouvert, déjà lu par QField (repli possible sur la même tournée).
- **Responsable** : une seule source qui fait foi (Nemeton), pas de double saisie.
- **Anticiper** : colonnes de l'IA réservées dès la v1 ; version du schéma dans le nom (`v1`).
- **Novateur** : reprend le contrat éprouvé de Groma au lieu d'en inventer un.
- **Coût** : deux fonctions dans nemeton, un module dans nemetonshiny ; coût porté par Pascal dans ces dépôts.
- **Écologique** : sans objet notable.

## Valeurs sacrifiées

- L'autonomie de Ramulus : sans tournée de Nemeton, pas de campagne (l'écran de notation libre de la tranche 0
  restera pour l'essai et la formation).
- La lecture directe par sommieR : un intermédiaire de plus.

## Options écartées

- **sommieR prépare et reçoit** (modèle Surculus) : écarté, Nemeton fait foi (Pascal, 2026-10-28).
- **Un paquet propre à Ramulus (zip JSON) distinct de la tournée** : écarté, deux formats à tenir.

## Ouvert

- Qui écrit la tournée, nemeton ou sommieR : brief validé « à trancher par Pascal » ; cette ADR propose nemeton.
- Où vont les photos : dans le dossier de la tournée, à côté du GeoPackage (comme les rasters de Groma) ; leur
  devenir chez Nemeton est à trancher.
