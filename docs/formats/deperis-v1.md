# Format `deperis.gpkg`, version 1

Le fichier d'échange entre **Nemeton**, qui prépare la tournée DEPERIS et importe ce qui revient, et **Ramulus**, qui
la complète sur le terrain. Décisions : [ADR 0003](../adr/0003-echange-avec-nemeton.md) (contrat) et
[ADR 0004](../adr/0004-position-approchee.md) (position approchée) ; arbitrages de Pascal du 2026-10-28 sur le plan de
la mission 002 (Q1 à Q13, cités ci-dessous) et décisions de Pascal du 2026-10-28 sur la PR n° 5 (citées « Pascal,
2026-10-28 »). Ce document ne décide rien qu'une de ces sources n'ait décidé.

**Décisions de Pascal du 2026-10-28 sur la PR n° 5** :

1. Les six choix annoncés dans la PR sont validés : les tables et colonnes inconnues sont ignorées dans toutes les
   versions, et gardées telles quelles ; sept motifs ajoutés au plan (`pas_un_geopackage`, `version_illisible`,
   `structure`, `type_incoherent`, `ia_incomplete`, `uuid_duplique`, `rattachement_invalide`) ; les cas R1d, R10c et
   R11 ; le conflit de fusion, jamais tranché en silence ; le site synthétique en Côte-d'Or et le délai de
   contre-notation de 15 jours dans les exemples, **provisoire** ; `user_version` de 10200 à 10499.
2. Dans un fichier N+1, une valeur nouvelle d'une colonne connue est **refusée** (voir « Version »).
3. Le « même arbre » du signalement `doublon_arbre` : même `plot_id` et même `rang`, **ou** même `plot_id` et même
   `repere`.
4. **Tous les textes en Unicode NFC**, vérifiés par le contrôle de référence (`texte_non_nfc`, cas R12).
5. **Conflit de fusion traité comme Surculus** (`sursync-v1.md`, l. 63) : les deux versions sont écartées, le conflit
   est signalé (`uuid_contenu_different`), le reste de l'union est gardé (voir « Paquet rendu », cas S3). Cette
   décision remplace la précédente, qui refusait toute l'union.

- **Schéma** (source unique, copiée dans Nemeton, ADR 0003 § 7) : [`deperis-v1.schema.json`](deperis-v1.schema.json).
- **Cas communs**, à rejouer en Kotlin et en R : [`fixtures/echange/`](../../fixtures/echange/README.md).
- **Contrôle de référence** (Python, bibliothèque standard et `jsonschema`) : `fixtures/echange/deperis_v1.py`.

## Repère et coordonnées

**EPSG:2154 (RGF93 v1 / Lambert-93), X = est, Y = nord, en mètres.** Le repère est **lu** dans
`gpkg_spatial_ref_sys` (`organization` = `EPSG`, `organization_coordsys_id` = `2154`) par la colonne `srs_id` de
`gpkg_geometry_columns`, jamais supposé (Surculus ADR 0007, « Repère »). Une colonne géométrique dans un autre repère
est refusée (`repere_hors_2154`).

« En France » veut dire **dans l'emprise d'EPSG:2154** : France métropolitaine et Corse, de −9,86° à 10,38° de
longitude et de 41,15° à 51,56° de latitude (domaine d'emploi EPSG lu dans la base de PROJ). Les DOM sont hors de la
v1 (Pascal, Q3). Le lecteur repasse chaque point en longitude et latitude et le refuse hors de l'emprise
(`hors_france`) : c'est ce qui attrape X et Y inversés, des degrés écrits à la place des mètres, ou la placette en
Somalie.

## Règles du fichier

| Règle | Source |
|---|---|
| OGC GeoPackage : `application_id` = `0x47504B47` (« GPKG »), `user_version` de 10200 à 10499 (1.2 à 1.4) ; Ramulus ne change jamais ceux qu'a écrits Nemeton. Les exemples portent 10400, comme le GeoPackage d'exemple de Groma écrit par GDAL | OGC 12-128 Req. 2 ; Groma `exemples/inventaire_video/` ; Pascal, 2026-10-28 |
| Nemeton écrit avec GDAL : `GEOMETRY_NAME=geom`, `FID=fid` (INTEGER PRIMARY KEY) pour toutes les tables | Groma § 2.2 ; ADR 0003 § 1 |
| **`SPATIAL_INDEX=NO` sur `placettes` et `centres`** : ni table `rtree_*`, ni déclencheur R*Tree, ni ligne `gpkg_rtree_index` dans `gpkg_extensions`. Les déclencheurs de GDAL appellent `ST_IsEmpty`, `ST_MinX`… sur tout `UPDATE`, et un SQLite qui ne les déclare pas échoue (« no such function: ST_IsEmpty ») | Groma § 2.2 ; ADR 0003 § 1 ; cas R5 |
| Ramulus écrit avec sqlite-jdbc, jamais avec le SQLite d'Android | Surculus ADR 0011 ; ADR 0003 § 1 |
| `notations` et `contre_notations` sont des tables **`attributes`** : `data_type = 'attributes'` dans `gpkg_contents`, aucune ligne dans `gpkg_geometry_columns` | ADR 0004 ; OGC 12-128 |
| Aucune colonne nommée `date` (mot réservé), quelle que soit la casse : `date_releve`, `date_notation`… | Groma § 2.1 ; cas R9 |
| Horodatages : type déclaré `DATETIME`, texte ISO 8601 en UTC suffixé `Z`, millisecondes facultatives : `2027-07-06T09:12:00.000Z` (forme de GDAL) | OGC 12-128 ; Surculus (instants UTC) |
| Booléens : type déclaré `BOOLEAN`, valeurs 0 ou 1 | OGC 12-128 ; pilote GPKG de GDAL |
| **Tous les textes** en UTF-8, forme Unicode NFC : noms de tables et de colonnes, et valeurs (y compris le texte JSON de `photos`). Un texte en forme décomposée (NFD, « e » suivi de l'accent, comme l'écrivent certains outils) est refusé (`texte_non_nfc`, cas R12). Les essences se comparent au caractère près, accents compris (« Mélèze ») | Pascal, 2026-10-28 (NFC) ; Q11 (essence par son nom exact) |
| **Nemeton crée toutes les tables**, vides côté Ramulus ; Ramulus insère et met à jour, il ne crée ni ne supprime aucune table ni colonne | Q13 |
| Une table ou une colonne que le lecteur ne connaît pas est **ignorée, et gardée telle quelle** quand Ramulus réécrit le fichier, dans toutes les versions | ADR 0003 § 2 ; Q1 ; Pascal, 2026-10-28 |
| Les tables propres à GDAL ou à QGIS (`gpkg_ogr_contents`, `layer_styles`…) sont permises et ignorées | pilote GPKG de GDAL |

## Version

**Une seule**, au niveau du fichier : `tournee.version_schema`, un entier, **1** pour ce format ; il n'existe pas de
version 0 (comme `.sursync`, Surculus `docs/formats/sursync-v1.md`). Un lecteur de version N lit un fichier N+1 **en
ignorant les tables et les colonnes nouvelles** (ADR 0003 § 2, Surculus ADR 0007, « Version » ; Pascal, Q1). Les
colonnes connues gardent leurs règles : **une valeur nouvelle d'une colonne connue est refusée** (une saison
« PRINTEMPS », une `source` « gnss_externe »), même dans un fichier N+1 (Pascal, 2026-10-28). Conséquence : **étendre
une liste de valeurs demande une version majeure** du format, que Ramulus et Nemeton publient ensemble ; une version
N+1 ne peut qu'ajouter des tables ou des colonnes.
Une version absente, non entière, nulle ou négative, ou une table `tournee` qui n'a pas exactement une ligne, est
illisible (`version_illisible`). C'est l'inverse du `.ramsync` (ADR 0005), qui refuse N+1 : le `.ramsync` est une
sauvegarde que seul Ramulus relit, `deperis.gpkg` passe d'un logiciel à l'autre.

## Tables

### `tournee` — table `attributes`, une ligne

| Colonne | Type | Écrite par | Règle |
|---|---|---|---|
| `fid` | INTEGER | Nemeton | clé |
| `version_schema` | INTEGER | Nemeton | ≥ 1 ; 1 ici |
| `code_tournee` | TEXT | Nemeton | lettres, chiffres, `.`, `_`, `-` ; nomme le `.zip` rendu ; jamais de nom de propriétaire ni d'agent (ADR 0005) ; proposé, retenu (Q5) |
| `nature` | TEXT | Nemeton | `premiere` ou `contre_notation` (Q5) |
| `prepare_le` | DATETIME | Nemeton | |
| `version_nemeton` | TEXT | Nemeton | non vide |
| `delai_contre_notation_jours` | INTEGER | Nemeton | délai maximal entre les deux passages, en jours, ≥ 1 ou vide. **Valeur provisoire** : non fixée par Pascal, gel avant la publication du format (fiche 002, Q6) ; aucun contrôle n'en dépend en v1 |
| `operateur` | TEXT | Ramulus | **l'opérateur qui rend** ; vide à la préparation (Q4) |
| `version_ramulus` | TEXT | Ramulus | vide à la préparation |

### `placettes` — couche `features`, POINT prévu, EPSG:2154, `SPATIAL_INDEX=NO`

| Colonne | Type | Écrite par | Règle |
|---|---|---|---|
| `fid` | INTEGER | Nemeton | clé |
| `geom` | POINT | Nemeton | point prévu du plan ; jamais modifié |
| `plot_id` | TEXT | Nemeton | non vide, unique |
| `wgt`, `ip` | REAL | Nemeton | colonnes de plan (Groma, schéma `inventaire_video`, couche `placettes`, origine « plan ») |
| `stratum`, `strat_*` | TEXT | Nemeton | colonnes de plan (idem) |
| `nb_arbres` | INTEGER | Nemeton | ≥ 1 ; le `rang` d'une note ne le dépasse pas |
| `regle_choix` | TEXT | Nemeton | texte libre (Q7) ; défaut « Kraft 1 à 3, tour d'horizon depuis le nord, sens horaire » (ADR 0004) |
| `saison` | TEXT | Nemeton | `ETE` ou `HIVER` (`Saison` de `:core`) |
| `collecte_photos` | BOOLEAN | Nemeton | 1 : 2 à 4 photos par arbre (spec § 1.5) |
| `contre_notation` | BOOLEAN | Nemeton | 1 : placette reprise dans une tournée de contre-notation |
| `etat` | TEXT | Ramulus | vide à la préparation, `notee` à la clôture ; aucun autre état (Q8) |

**Toute colonne que ce tableau ne nomme pas est une colonne de plan.** Ramulus ne touche à rien d'autre qu'`etat` :
à l'import, Nemeton compare la couche rendue à celle qu'il a écrite (noms et types des colonnes, `plot_id`, point
prévu, valeurs) et refuse tout écart (`plan_modifie`, ADR 0003 § 6).

### `centres` — couche `features`, POINT relevé, EPSG:2154, `SPATIAL_INDEX=NO`

| Colonne | Type | Règle |
|---|---|---|
| `fid` | INTEGER | clé |
| `geom` | POINT | position moyennée au GNSS du téléphone (ADR 0004) |
| `uuid` | TEXT | UUID v7 en minuscules, unique ; sert à la fusion (Q9) |
| `plot_id` | TEXT | présent dans `placettes` |
| `precision_m` | REAL | précision horizontale estimée, en mètres, ≥ 0 |
| `nb_mesures` | INTEGER | ≥ 1 |
| `source` | TEXT | `telephone`, ou `rtk` si un récepteur est branché (ADR 0004) |
| `date_releve` | DATETIME | |
| `operateur` | TEXT | non vide |

Écrite par Ramulus dans une tournée de première notation. Un centre à plus de **30 m** du point prévu est **signalé**
(`centre_eloigne`) : seuil provisoire proposé par l'ADR 0004, non tranché par Pascal. Dans une tournée de
contre-notation, Nemeton y recopie le centre relevé par le premier opérateur, **en lecture** : le second opérateur
ne relève pas de nouveau centre (Q12), et un centre modifié est refusé (`plan_modifie`).

### `notations` et `contre_notations` — tables `attributes`, mêmes colonnes

| Colonne | Type | Règle |
|---|---|---|
| `fid` | INTEGER | clé |
| `uuid` | TEXT | UUID v7 (RFC 9562) en minuscules, unique ; union sur l'`uuid` (ADR 0005) |
| `plot_id` | TEXT | présent dans `placettes` |
| `rang` | INTEGER | position dans le tour d'horizon depuis le nord, sens horaire ; de 1 à `nb_arbres` (ADR 0004) |
| `repere` | TEXT | numéro peint sur l'arbre ; texte, « 12b » permis (Q10) |
| `essence` | TEXT | l'une des 19 essences de [`essences.csv`](../referentiels/deperis/essences.csv), nom exact (Q11, spec § 4) |
| `type` | TEXT | `FEUILLU` ou `RESINEUX`, celui de l'essence dans `essences.csv` |
| `mb` | INTEGER | 0 à 5 |
| `second` | INTEGER | 0 à 5 : MR pour un feuillu, MA pour un résineux |
| `critere` | TEXT | `MR` si feuillu, `MA` si résineux (fiches DSF 2025, page 1) |
| `classe` | TEXT | `A` à `F`, **égale à l'abaque** appliqué à `mb` et `second` ([`abaque.csv`](../referentiels/deperis/abaque.csv)) ; calculée, jamais saisie ; Nemeton la recalcule à l'import |
| `saison` | TEXT | `ETE` ou `HIVER` ; non comparée à celle de la placette (conduite à tenir à trancher par Pascal, spec § 4) |
| `date_notation` | DATETIME | |
| `operateur` | TEXT | non vide ; chaque ligne garde le sien après fusion (Q4) |
| `photos` | TEXT | texte JSON : `[]`, ou `[{"nom":"photos/<uuid>_1.jpg","sha256":"<64 hex minuscules>"}, …]`, où `<uuid>` est celui de la ligne et le numéro part de 1 (Q2) |
| `ia_premiere_mb`, `ia_premiere_second` | INTEGER | première note de l'opérateur avant de voir l'IA (ADR 0001) |
| `ia_mb`, `ia_second` | INTEGER | proposition de l'IA |
| `ia_confiance` | REAL | 0 à 1 |
| `ia_modele` | TEXT | modèle et version |

Les six colonnes de l'IA sont **toutes vides ou toutes remplies** (`ia_incomplete`) ; vides en tranche 1 (spec § 4).

`notations` n'existe que dans une tournée de première notation ; `contre_notations` que dans une tournée de
contre-notation. Deux notations du même arbre (même `plot_id` et même `rang`, ou même `plot_id` et même `repere` : Pascal,
2026-10-28) dans `notations` sont **signalées** avant de rendre (`doublon_arbre`, ADR 0003 § 5). Le même rang ou le
même repère sur deux placettes différentes n'est pas un doublon.

## Tournée de contre-notation

Nemeton l'écrit pour le second opérateur, à part (ADR 0003 § 4) : `nature = contre_notation`, un autre
`code_tournee`, les seules placettes marquées `contre_notation`, leurs centres en lecture (Q12), et une table
`contre_notations` vide. **Aucune première note ne s'y trouve** : ni table `notations`, ni colonne `mb`, `second`,
`critere`, `classe` ou `ia_*` hors de `contre_notations`, ni photo (`notations_dans_contre_notation`). La première
note fait foi dans le bilan.

## Paquet rendu

Un `.zip` nommé d'après `code_tournee` (`EXEMPLE-2027-01.zip`), qui contient `deperis.gpkg` à la racine et le dossier
`photos/`. Chaque photo citée par une note est présente avec la bonne empreinte SHA-256, et chaque fichier de
`photos/` est cité (cas R10a à R10c, Pascal, 2026-10-28 ; forme de `photos` retenue par Pascal, Q2 ; `photo_absente`,
`empreinte_photo_differente`, `photo_non_citee`).

Plusieurs opérateurs : union sur l'`uuid` des centres et des notes avant de rendre ; `tournee.operateur` est
l'opérateur qui rend (Q4). Deux lignes de même `uuid` et de contenu différent forment un **conflit**, traité comme dans
`.sursync` (Surculus `docs/formats/sursync-v1.md`, l. 63 : « même `uuid` deux fois, contenu différent |
`uuid_contenu_different` | les deux versions refusées, les autres restaurées » ; notre ADR 0005, l. 33 : « deux
corrections concurrentes signalées comme conflit » ; Pascal, 2026-10-28) :

- **les deux versions sont écartées** de l'union, aucune n'est choisie ;
- **le conflit est signalé** (`uuid_contenu_different`, statut « signalé »), avec l'`uuid` de chaque ligne écartée ;
- **le reste de l'union est gardé**, et contrôlé comme tout paquet : son statut est le sien, plus ce signalement
  (cas S3).

Une photo appartient à la note qui la cite (`photos/<uuid>_<n>.jpg`, Q2) : les photos d'une note écartée sont écartées
avec elle (sinon l'union porterait des photos citées par aucune note). Deux photos de même nom et de contenu différent
sont un conflit sur la note qui les cite : les deux versions de la photo, la note et ses autres photos sont écartées,
avec le même motif. Chaque paquet, seul, reste conforme et garde ses deux versions : c'est à l'opérateur de dire
quelle note fait foi, et de rendre de nouveau. Deux lignes de même `uuid` et de contenu identique ne sont gardées
qu'une fois.

## Import par Nemeton

Tout ou rien ; rejouable sans doublon (union sur l'`uuid`) ; refus d'un paquet dont ce que Nemeton a écrit a changé,
ou dont une classe diffère de l'abaque (ADR 0003 § 6). Les autres refus et les signalements sont ceux du catalogue
ci-dessous.

## Instantané

JSON Schema ne lit pas un fichier SQLite : on valide un **instantané** JSON du GeoPackage, que Ramulus (Kotlin), Nemeton
(R) et le contrôle de référence (`deperis_v1.instantane()`) construisent de la même façon :

```json
{
  "application_id": 1196444487,
  "user_version": 10400,
  "version_schema": 1,
  "nature": "premiere",
  "tables": {
    "placettes": {
      "data_type": "features",
      "geometrie": { "colonne": "geom", "type": "POINT", "srs": { "organisation": "EPSG", "code": 2154 } },
      "index_spatial": false,
      "cle_primaire": "fid",
      "colonnes": { "fid": "INTEGER", "geom": "POINT", "plot_id": "TEXT" },
      "lignes": [ { "fid": 1, "geom": { "x": 856560.23, "y": 6670351.34 }, "plot_id": "P001" } ]
    }
  },
  "photos_du_paquet": [ { "nom": "photos/<uuid>_1.jpg", "sha256": "<64 hex>" } ]
}
```

- `application_id`, `user_version` : les `PRAGMA` du même nom.
- `version_schema`, `nature` : repris de la ligne de `tournee` s'il y en a exactement une, sinon `null`.
- `tables` : une entrée par ligne de `gpkg_contents`, avec son `data_type` ; `geometrie` lue dans
  `gpkg_geometry_columns` (type en majuscules) et `gpkg_spatial_ref_sys` (`organization`,
  `organization_coordsys_id`), `null` pour une table `attributes` ; `index_spatial` vrai s'il existe une ligne
  `gpkg_rtree_index` de `gpkg_extensions` pour la table, ou une table ou un déclencheur dont le nom commence par
  `rtree_<table>_` ; `cle_primaire`, la colonne de clé si elle est unique ; `colonnes`, le nom et le type déclaré en
  majuscules (`PRAGMA table_info`), dans l'ordre de la table ; `lignes`, dans l'ordre de la clé.
- Valeurs : telles que SQLite les rend (entier, réel, texte, `null`) ; la géométrie devient `{"x", "y"}` (X = est,
  Y = nord), `null` si vide ; le texte JSON de `photos` est lu comme un tableau, et laissé en texte s'il n'est pas du
  JSON (le schéma le refuse alors).
- `photos_du_paquet` : pour un `.zip` seulement, chaque fichier de `photos/`, trié par nom, avec son SHA-256.

Le schéma porte, sur chaque sous-schéma qui fonde un refus, le **motif** (`x-motif`) ; un écart sans motif est un
défaut de `structure`. Ce que JSON Schema ne sait pas dire (unicité, rattachement, emprise point par point,
signalements, photos, comparaison avec la tournée de départ, fusion) est fait par le lecteur ; le contrôle de
référence en donne une version exécutable, et les cas communs le résultat attendu.

## Catalogue des motifs

Le même catalogue est dans le schéma (`x-motifs`) ; un test vérifie qu'ils disent la même chose.

| Motif | Résultat | Quand |
|---|---|---|
| `pas_un_geopackage` | refusé | `application_id` ou `user_version` hors GeoPackage 1.2 à 1.4 |
| `version_illisible` | refusé | `version_schema` absente, non entière, ≤ 0 ; `tournee` sans ligne unique |
| `structure` | refusé | table, colonne, type déclaré ou valeur non conforme, sans motif plus précis |
| `index_spatial` | refusé | index spatial sur `placettes` ou `centres` (R5) |
| `repere_hors_2154` | refusé | colonne géométrique hors EPSG:2154 (R11) |
| `hors_france` | refusé | point hors de l'emprise d'EPSG:2154 (R3, R11) |
| `colonne_date` | refusé | colonne nommée `date` (R9) |
| `notations_dans_contre_notation` | refusé | première note dans une tournée de contre-notation (R6) |
| `essence_hors_referentiel` | refusé | essence hors des 19 (R7) |
| `type_incoherent` | refusé | type différent de celui de l'essence |
| `critere_incoherent` | refusé | MR pour un résineux, MA pour un feuillu (R8b) |
| `note_hors_bornes` | refusé | note hors de 0 à 5 (R8a) |
| `classe_differente_abaque` | refusé | classe différente de l'abaque (R2) |
| `ia_incomplete` | refusé | colonnes de l'IA ni toutes vides ni toutes remplies |
| `uuid_duplique` | refusé | même `uuid` deux fois dans une table |
| `rattachement_invalide` | refusé | `plot_id` absent de `placettes`, ou `rang` > `nb_arbres` |
| `plan_modifie` | refusé | ce que Nemeton a écrit est revenu changé (R1) |
| `photo_absente` | refusé | photo citée absente du `.zip` (R10a) |
| `empreinte_photo_differente` | refusé | SHA-256 différent de celui cité (R10b) |
| `photo_non_citee` | refusé | fichier de `photos/` cité par aucune note (R10c) |
| `texte_non_nfc` | refusé | nom de table, de colonne ou valeur texte qui n'est pas en Unicode NFC (R12) |
| `doublon_arbre` | signalé | deux notations du même arbre hors contre-notation (S1) |
| `centre_eloigne` | signalé | centre à plus de 30 m du point prévu, seuil provisoire (S2) |
| `uuid_contenu_different` | signalé | à la fusion, même `uuid` (ou même photo) avec deux contenus différents : les deux versions écartées, le reste de l'union gardé (S3 ; Surculus `sursync-v1.md`, l. 63 ; Pascal, 2026-10-28) |

`texte_non_nfc` et `uuid_contenu_different` ne sont pas dans le schéma lui-même : JSON Schema ne sait dire ni la forme
NFC d'un texte, ni ce qui se passe entre deux fichiers. Le lecteur les contrôle (`deperis_v1.textes_non_nfc`,
`deperis_v1.controler_fusion`) ; ils sont au catalogue `x-motifs` comme les autres. `texte_non_nfc` porte sur les
tables de l'instantané, c'est-à-dire celles de `gpkg_contents` : les tables système `gpkg_*` (et `rtree_*`) ne sont
pas contrôlées, l'instantané ne les porte pas.

Un paquet est **refusé** s'il porte au moins un motif de refus, **signalé** s'il ne porte que des signalements,
**accepté** sinon.

## D'où vient chaque règle reprise

| Règle | Lu dans |
|---|---|
| `geom`, `fid`, `SPATIAL_INDEX=NO`, déclencheurs R*Tree et SQLite d'Android | Groma, `briefs/vers-nemeton/2026-10-12-inventaire-video.md`, § 2.2 |
| `date` mot réservé ; colonnes de plan `wgt`, `ip`, `stratum`, `strat_*`, jamais modifiées | Groma, même brief, § 2.1 ; `core/src/groma/donnees/inventaire_video.schema.json` (types) |
| Version unique au niveau du fichier, N lit N+1 ; repère lu dans `gpkg_spatial_ref_sys` ; tables enregistrées dans `gpkg_contents` (`features` ou `attributes`), clé INTEGER | Surculus `docs/adr/0007-*`, « Règles » (Version, Repère, Conformité GeoPackage) |
| Écriture avec sqlite-jdbc ; comparaison des fichiers par leur contenu, jamais par empreinte | Surculus `docs/adr/0011-*`, « Règles » |
| UUID v7 ; union sur l'`uuid` ; conflit jamais résolu en silence | Surculus `docs/adr/0005-*`, « Règles » ; notre ADR 0005 |
| Version entière à partir de 1 ; motifs en codes lus par les tests ; motif `uuid_contenu_different` ; empreinte SHA-256 en 64 chiffres hexadécimaux minuscules ; nom de fichier par le code de la tournée | Surculus `docs/formats/sursync-v1.md` |

## Limites de la version 1

- Le délai de contre-notation et le seuil de 30 m sont **provisoires** (non tranchés par Pascal).
- L'extension Schema d'OGC (`gpkg_data_columns`, `gpkg_data_column_constraints`), que Surculus ADR 0007 utilise pour
  faire voyager les valeurs permises avec le fichier, n'est pas reprise ici : l'ADR 0003 ne la nomme pas.
- Aucune règle ne compare la `saison` d'une note à celle de sa placette, ni la date de la contre-notation au délai :
  à trancher par Pascal (spec § 4).
