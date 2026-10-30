# Brief de Ramulus vers Nemeton — écrire et importer une tournée DEPERIS (`deperis-v1`)

- **Émetteur :** Ramulus (orchestrateur), d'après l'ADR 0003 (acceptée par Pascal le 2026-10-28, porte n° 1)
- **Cible :** Nemeton (paquet R), puis nemetonshiny
- **Source de vérité :** le format est décrit dans Ramulus, `docs/formats/deperis-v1.md`, avec son schéma
  `deperis-v1.schema.json` et les cas communs de `fixtures/echange/`. Ce brief **renvoie** à ces fichiers, il ne les
  recopie pas : en cas de désaccord, ce sont eux qui font foi.
- **État :** brouillon, à envoyer par Pascal **après la fusion de la mission 010 de Ramulus**. Cette mission ajoute
  au format le motif `classe_saisie` et la règle de la placette dont le centre est en conflit.

## Ce que Nemeton fait foi

L'ADR 0003 de Ramulus en fixe le partage. **Nemeton fait foi** : il tire le plan d'échantillonnage, écrit la tournée,
importe les notes, puis rend à sommieR le bilan par unité de gestion. sommieR ne lit jamais la tournée.

## 1. `creer_tournee_deperis()` : écrire la tournée

- **Le fichier.** Nemeton écrit `deperis.gpkg` en EPSG:2154 ; les DOM sont hors de la v1. Il reprend les options GDAL
  de Groma (brief Groma vers Nemeton, 2026-10-12, § 2.1-2.2), avec `SPATIAL_INDEX=NO` sur les couches.
- **Les tables.** Nemeton écrit :
  - `tournee` (une ligne : code, nature, saison, `regle_choix`, `version_nemeton`…) ;
  - `placettes` (le point prévu, et les colonnes du plan : `plot_id`, `wgt`, `ip`, `stratum`…, que Ramulus ne
    modifie jamais) ;
  - les couches vides `centres` et `notations`.

  Colonnes et règles : `deperis-v1.md`, « Tables ».
- **La règle de choix des arbres** est un paramètre de campagne, dans `regle_choix`. Par défaut : **Kraft 1 à 3**,
  pris dans l'ordre d'un **tour d'horizon depuis le nord, dans le sens horaire** (Pascal, 2026-10-28, K1 ; ADR 0004 de
  Ramulus).
- **La contre-notation** se fait dans une **tournée à part**, sans couche `notations`. Le centre du premier opérateur
  y est fourni en lecture seule (`deperis-v1.md`, « Tournée de contre-notation »). La contre-notation porte sur 5 %
  des placettes, à l'aveugle.

## 2. `importer_deperis()` : importer le paquet rendu

Ramulus rend un `.zip` qui contient la tournée complétée et ses photos. Nemeton l'importe selon les règles suivantes :
- **tout ou rien**, et **rejouable sans doublon** : l'union se fait sur l'`uuid` ;
- **refus** si une valeur que Nemeton a écrite a changé (`plan_modifie`), ou si une classe diffère de celle de
  l'abaque : Nemeton **recalcule la classe** ;
- les autres refus et signalements sont ceux du **catalogue des motifs** (`deperis-v1.md`) ;
- un paquet « signalé » s'importe avec ses signalements, et seul un refus bloque l'import.

## 3. Tests

Rejouer en R les cas communs de `fixtures/echange/`, comme Groma et Surculus, en copiant le schéma de Ramulus
(source unique). Pour chaque cas, `attendu.json` donne le verdict et les motifs attendus.

## Questions pour Nemeton (à poser par Pascal)

1. Les noms de fonction proposés, `creer_tournee_deperis()` et `importer_deperis()`, conviennent-ils au paquet
   Nemeton ?
2. Où le schéma copié vit-il dans Nemeton, et qui le met à jour à chaque version du format ?
3. Le bilan par unité de gestion vers sommieR : à quel moment le produire, et sous quelle forme ?
