# Mission 002 — Plan du format `deperis-v1` (tenir)

> Rendu par l'agent `dev` le 2026-10-28, sans modifier de fichier (fiche en « tenir »). Recopié ici par
> l'orchestrateur pour la relecture de Pascal. **Rien n'est écrit tant que Pascal n'a pas tranché Q1 à Q13 et réglé
> M1 et M2.** Fiche : [`002-format-echange-deperis-v1.md`](002-format-echange-deperis-v1.md).

## Redite de l'agent

Écrire le contrat du fichier `deperis.gpkg` (description, schéma JSON, cas d'exemple acceptés et refusés), tiré tel
quel des ADR 0003 et 0004, pour que Ramulus (sqlite-jdbc) et Nemeton (R/GDAL) lisent et écrivent exactement le même
fichier sans rien ajouter chacun de son côté.

## Réserves de l'agent

1. **Groma et Surculus introuvables** (HTTP 403 sur `pobsteta/groma` et `pobsteta/surculus`, pas de `~/dev`). Ce qui
   vient d'eux est repris d'après les ADR 0003 et 0005 de Ramulus et marqué **[à vérifier]**.
2. **GDAL absent** (ni `ogr2ogr`, ni `osgeo` ; installation pip expirée) ; R absent. Présents : Python 3, sqlite3
   (SQLite 3.45.1), `jsonschema`.
3. PR n° 1 non fusionnée au moment du plan. *Levé depuis : fusionnée le 2026-10-28 (618f1ad).*

## A. Fichiers à produire

- `docs/formats/deperis-v1.md` : description en français ; pour chaque table, rôle, qui écrit, qui lit, colonnes,
  règles, cas.
- `docs/formats/deperis-v1.schema.json` : JSON Schema (draft 2020-12) d'un instantané JSON du GeoPackage (tables,
  `data_type` de `gpkg_contents`, géométrie, repère, index spatial, lignes) ; extension `x-gpkg` pour ce que JSON
  Schema ne dit pas (type SQLite déclaré, `SPATIAL_INDEX`).
- `fixtures/echange/generer.py` : génère les GeoPackages d'exemple, en fait l'instantané et le valide.
- `fixtures/echange/README.md` : liste des cas, résultat attendu (accepté, signalé, refusé) et motif.
- Les listes du schéma (19 essences, 36 couples de l'abaque) sont générées depuis `docs/referentiels/deperis/*.csv` ;
  un test vérifie que schéma et CSV disent la même chose.

## B. Règles du fichier (ADR 0003 § 1-2)

- `deperis.gpkg`, OGC GeoPackage, `application_id` = `GPKG` ; `user_version` (1.3 ou 1.4) **[à vérifier, Groma]**.
- Repère EPSG:2154 lu dans `gpkg_spatial_ref_sys`, jamais supposé ; X = est, Y = nord, en mètres, écrit en tête du
  document et à chaque colonne géométrique.
- `GEOMETRY_NAME=geom`, `FID=fid` (INTEGER PRIMARY KEY).
- `SPATIAL_INDEX=NO` sur `placettes` et `centres` : ni table `rtree_*`, ni déclencheur R*Tree (cas R5).
- Aucune colonne `date` (mot réservé, Groma) : `date_releve`, `date_notation`…
- Dates : `DATETIME`, ISO 8601 UTC suffixé `Z` (`2026-11-04T09:12:00.000Z`) ; `DATE` pour une date seule.
- Booléens : `BOOLEAN`, 0 ou 1.
- Tables sans géométrie en `attributes` (`data_type='attributes'`, rien dans `gpkg_geometry_columns`).
- **Proposé : Nemeton crée toutes les tables, vides** ; Ramulus insère et met à jour `etat`, sans créer de table
  (Q13).

## C. Tables et colonnes

**`tournee`** (attributes, une ligne) : `fid` ; `version_schema` INTEGER = 1 [forme à vérifier, Surculus ADR 0007] ;
`prepare_le` DATETIME ; `version_nemeton` TEXT ; `operateur` TEXT (Ramulus, Q4) ; `version_ramulus` TEXT (Ramulus) ;
*proposés* : `code_tournee` TEXT, `nature` ∈ {`premiere`, `contre_notation`} (Q5), `delai_contre_notation_jours`
INTEGER, valeur provisoire (Q6).

**`placettes`** (features, POINT prévu, EPSG:2154, `SPATIAL_INDEX=NO`)
- Colonnes de plan, jamais modifiées, contrôlées à l'import : `plot_id` TEXT unique non nul, `wgt` REAL, `ip` REAL,
  `stratum` TEXT — types et liste complète **[à vérifier, Groma § 2.1]**. Proposé : toute colonne non nommée par le
  schéma est une colonne de plan, contrôlée aussi.
- Paramètres de campagne (Nemeton) : `nb_arbres` INTEGER ≥ 1 ; `regle_choix` TEXT, défaut « Kraft 1 à 3, tour
  d'horizon depuis le nord, sens horaire » (Q7) ; `saison` ∈ {`ETE`, `HIVER`} (enum `Saison` de `:core`) ;
  `collecte_photos` BOOLEAN ; `contre_notation` BOOLEAN.
- `etat` TEXT (Ramulus) : NULL à la préparation, `notee` à la clôture (Q8).

**`centres`** (features, POINT relevé, EPSG:2154, `SPATIAL_INDEX=NO`, Ramulus) : `fid`, `geom` (moyenne GNSS) ;
*proposé* `uuid` TEXT (Q9) ; `plot_id` TEXT existant dans `placettes` ; `precision_m` REAL ≥ 0 ; `nb_mesures`
INTEGER ≥ 1 ; `source` ∈ {`telephone`, `rtk`} (ADR 0004) ; `date_releve` DATETIME ; `operateur` TEXT.

**`notations`** (attributes, tournée de départ seulement, Ramulus)
- `fid`, `uuid` TEXT (UUID v7 minuscule, unique, ADR 0005) ; `plot_id` TEXT ; `rang` INTEGER 1..`nb_arbres` ;
  `repere` (numéro peint) INTEGER ou TEXT (Q10).
- `essence` TEXT : l'un des 19 noms exacts de `essences.csv`, aucun code inventé (Q11) ; `type` ∈ {`FEUILLU`,
  `RESINEUX`}, cohérent avec l'essence.
- `mb` 0..5 ; `second` 0..5 ; `critere` ∈ {`MR`, `MA`} (MR si feuillu, MA si résineux) ; `classe` ∈ {A..F} =
  `abaque(mb, second)` (36 cas).
- `saison` ; `date_notation` DATETIME ; `operateur` TEXT.
- `photos` TEXT : tableau JSON `[{"nom":"photos/<uuid>_1.jpg","sha256":"<64 hex>"}]` ou `[]` [forme à vérifier,
  Surculus].
- Colonnes IA (ADR 0001), NULL en tranche 1, toutes nulles ou toutes remplies : `ia_premiere_mb`,
  `ia_premiere_second`, `ia_mb`, `ia_second`, `ia_confiance` (0..1), `ia_modele`.

**`contre_notations`** : mêmes colonnes, tournée de contre-notation seulement.

## D. Tournée de contre-notation

`nature = contre_notation` ; seules les placettes marquées `contre_notation` ; `centres` présent (vide ou rempli :
Q12) ; `contre_notations` vide ; **aucune table `notations`, aucune `mb`/`second`/`classe` hors `contre_notations`,
aucune photo**. Imposé par le schéma (`if nature = contre_notation then not contains notations`).

## E. Rendu

`.zip` nommé d'après `code_tournee` (ADR 0005), contenant `deperis.gpkg` et `photos/` ; chaque photo citée présente
avec la bonne empreinte SHA-256, et réciproquement.

## F. Cas dans `fixtures/echange/`

Données : trois placettes en France métropolitaine (WGS84 lon/lat converties en EPSG:2154, vérifiées dans
l'emprise) ; site à proposer par Pascal. Notes reprises telles quelles de photos de `fixtures/fiches-dsf-2025.json`
(identifiant cité), hors des quatre incohérences : Chêne sessile, Hêtre (MR), Sapin, Mélèze (MA), un arbre mort
(5/5, F) et un arbre A.

| Cas | Contenu | Attendu |
|---|---|---|
| A1 `tournee-depart` | telle qu'écrite par Nemeton | accepté |
| A2 `tournee-contre-notation` | | accepté |
| A3 `rendu` | `etat=notee`, centres, notations, photos de synthèse | accepté |
| A4 `rendu-contre-notation` | | accepté |
| A5 `fusion-deux-operateurs` | deux paquets partiels, union par `uuid`, rejouable | accepté |
| A6 `version-suivante` | `version_schema=2` + colonne inconnue, lue en l'ignorant (ADR 0003, Q1) | accepté |
| S1 | deux notations du même arbre hors contre-notation (ADR 0003 § 5) | signalé |
| S2 | centre à plus de 30 m du point prévu (ADR 0004, provisoire) | signalé |
| R1 | colonne de plan changée, renommée ou supprimée (ADR 0003 § 6) | refusé |
| R2 | classe ≠ abaque (mb=3, second=1, classe C au lieu de D) | refusé |
| R3 | centre hors de France (Mogadiscio) ; X↔Y inversés (spec § 5.3) | refusé |
| ~~R4~~ | ~~version N+1~~ — retiré : Pascal suit l'ADR 0003, N+1 est lu (voir A6 et Q1) | — |
| R5 | index spatial sur `placettes` ou `centres` (ADR 0003 § 1) | refusé |
| R6 | contre-notation contenant `notations` (ADR 0003 § 4) | refusé |
| R7 | essence hors des 19 (spec § 4) | refusé |
| R8 | note hors 0..5, critère incohérent avec le type | refusé |
| R9 | colonne `date` (Groma) | refusé |
| R10 | photo citée absente ou empreinte fausse (**proposé par l'agent**) | refusé |

## G. Repris de Groma et Surculus

| Repris | D'où | Lu directement ? |
|---|---|---|
| `geom`, `fid`, `SPATIAL_INDEX=NO`, R*Tree, pas de SQLite d'Android, `date` réservé, colonnes de plan contrôlées | Groma, brief vers Nemeton § 2.1-2.3 | non, via ADR 0003 [à vérifier] |
| Version au niveau du fichier, repère lu, N lit N+1 | Surculus ADR 0007 | non [à vérifier] |
| Écriture sqlite-jdbc | Surculus ADR 0011 | non |
| UUID v7, union sur l'`uuid` | Surculus ADR 0005, via notre ADR 0005 | non |
| Forme du schéma JSON et des cas communs | `sursync-v1.md` | **non, introuvable** : forme proposée par l'agent |

## H. Tests prévus

- Python : fixtures acceptées valides, refusées en échec avec leur motif (R1 et R10 : contrôle du script, pas du
  schéma) ; schéma cohérent avec les référentiels (19, 36) ; placettes d'exemple en France, Mogadiscio hors de France.
- `:core` (JUnit) : lit `fixtures/echange/*/attendu.json` et vérifie classe = `Abaque.classe(mb, second)` et
  essence ∈ `Essences.toutes`.

## Moyens manquants

- **M1 — GDAL** : (a) dans la CI, fixtures générées par GDAL ; (b) GeoPackages écrits avec sqlite3 de Python selon
  OGC 12-128, validés par `ogrinfo` en CI ; (c) fixtures générées par Pascal dans R/sf.
- **M2 — Groma et Surculus** : accès en lecture aux deux dépôts, ou copie des passages cités.
- **M3 — PR n° 1** : *levé, fusionnée le 2026-10-28.*

## Questions pour Pascal

- **Q1** — Contradiction fiche / ADR 0003 : la fiche veut « version N+1 » refusée, l'ADR 0003 § 2 dit qu'un lecteur
  N lit N+1 en ignorant les champs nouveaux (l'inverse du `.ramsync`, ADR 0005). Le plan suit l'ADR (A6). Pour un
  refus, amender l'ADR (par exemple : majeure refusée, mineure lue).
  → **Tranché par Pascal le 2026-10-28 : on suit l'ADR, N+1 est lu.** Fiche 002 corrigée, R4 retiré.
- **Q2** — Valeurs sans référentiel (`etat`, `source`, `nature`, forme de `photos`), tirées des mots de la spec et des
  ADR : les garder ?
- **Q3** — « En France » : EPSG:2154 ne couvre que la métropole et la Corse. DOM hors v1 ? Contrôle par l'emprise
  EPSG:2154 ou par un contour de la France à sourcer ?
- **Q4** — `tournee.operateur` unique alors qu'un rendu fusionne plusieurs opérateurs : liste, ou opérateur qui rend ?
- **Q5** — Ajouter `code_tournee` et `nature` à `tournee` (non nommés par l'ADR) ?
- **Q6** — `delai_contre_notation_jours` dans `tournee`, valeur provisoire : place et unité ?
- **Q7** — `regle_choix` : texte libre, ou valeurs codées après la mission 001 ?
- **Q8** — États de placette : NULL et `notee` seulement, ou un état « commencée » ?
- **Q9** — `uuid` dans `centres` pour fusionner deux opérateurs ?
- **Q10** — `repere` : entier, ou texte (« 12b ») ?
- **Q11** — Essence identifiée par son nom exact (accents compris) plutôt que par un code que le DSF ne publie pas ?
- **Q12** — Contre-notation : le second opérateur relève-t-il le centre, ou reçoit-il celui du premier ?
- **Q13** — Nemeton crée les tables vides (proposé), ou Ramulus les crée ?

### Réponses de Pascal (2026-10-28)

- **Q1** : on suit l'ADR 0003, N+1 est lu (R4 retiré).
- **Q2, Q5, Q6, Q7, Q8, Q9, Q11, Q13** : propositions du plan retenues telles quelles (valeurs `etat`, `source`,
  `nature`, forme de `photos` ; `code_tournee` et `nature` dans `tournee` ; `delai_contre_notation_jours` en jours,
  provisoire ; `regle_choix` en texte libre ; états NULL et `notee` ; `uuid` dans `centres` ; essence par son nom
  exact ; Nemeton crée les tables vides).
- **Q3** : DOM hors de la v1 ; contrôle par l'emprise EPSG:2154.
- **Q4** : `tournee.operateur` = l'opérateur qui rend ; chaque ligne garde son `operateur`.
- **Q10** : `repere` en TEXT.
- **Q12** : dans la contre-notation, le centre relevé par le premier opérateur est fourni en lecture, sans nouveau
  relevé.

Restent avant d'écrire : **M1** (GDAL) et **M2** (Groma, Surculus).

## Hors périmètre, repéré

`Notation.kt` (`:core`) n'a ni `plot_id`, ni `rang`, ni `repere`, ni `saison` : à étendre en mission 004.

## Échantillon terrain (à la PR)

`fixtures/echange/rendu/<code>.zip` ouvert dans QField puis dans R : trois points au bon endroit en France ;
`notations` lue comme table sans géométrie, accents intacts ; classe conforme à la fiche DSF de chaque photo reprise.
