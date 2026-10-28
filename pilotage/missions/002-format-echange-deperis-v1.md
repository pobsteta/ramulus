# Mission 002 — Format d'échange `deperis-v1`

## Mission

**En une phrase (quoi, et pourquoi) :** écrire le schéma JSON, la description et les cas communs du fichier
`deperis.gpkg` d'après les ADR 0003 et 0004, pour que Ramulus et Nemeton lisent et écrivent exactement le même
fichier.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | modèles : schéma de Groma, format `.sursync` et ADR 0007 de Surculus |
| A-t-il les moyens ? | oui | Python (module `sqlite3`) pour écrire les GeoPackages d'exemple selon OGC 12-128, `ogrinfo` en CI pour les valider (M1, option b, Pascal le 2026-10-28) ; tests `:core` |

**Temps de la main :** **tenir** — le format engage Ramulus et Nemeton pour longtemps ; l'agent rend d'abord un
plan (couches, colonnes, types, domaines, cas), relu par l'orchestrateur et Pascal avant d'écrire.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** opus
- **Ce qui ne lui est pas demandé :** le code de lecture et d'écriture sur l'appareil (004) ; les fonctions de
  Nemeton (brief à part).

## Harnais

- **Peut aussi :** ajouter à `.github/workflows/ci.yml` l'installation de GDAL et une étape qui passe chaque
  GeoPackage d'exemple à `ogrinfo` (M1, option b).
- **Peut :** écrire `docs/formats/deperis-v1.md`, `docs/formats/deperis-v1.schema.json`, `fixtures/echange/`
  (tournée de départ, tournée de contre-notation, paquet rendu, cas refusés) et le script qui génère les
  GeoPackages d'exemple.
- **Ne doit pas :** changer une décision des ADR 0003 et 0004 (une contradiction remonte à Pascal) ; inventer une
  essence, une classe ou une règle ; toucher à `main`.
- **Sources autorisées :** ADR 0001 à 0006 ; `docs/spec.md` § 2 ; `docs/referentiels/deperis/` ;
  `groma/briefs/vers-nemeton/2026-10-12-inventaire-video.md` § 2.1-2.3 ; Surculus `docs/formats/sursync-v1.md`,
  `docs/adr/0007-*`, `0011-*` ; OGC 12-128 (GeoPackage, tables `attributes`) ; pilote GPKG de GDAL.

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** délai maximal de contre-notation (non fixé par Pascal) : colonne présente, valeur
  « provisoire » dans les exemples.
- **Mesurée par :** Pascal (décision), avant la mission 006.
- **Gel :** avant la publication du format v1.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune ; part de `main` après fusion de la PR n° 1.
- **Ordre de fusion :** 002 → 003 → 004.
- **PR en brouillon jusqu'à :** relecture du plan par Pascal.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** les GeoPackages d'exemple valident contre le schéma ; `SPATIAL_INDEX=NO`
  sur toute couche modifiée par Ramulus ; `notations` et `contre_notations` en tables `attributes` ; la tournée de
  contre-notation ne contient aucune première note ; chaque cas refusé dit pourquoi (colonnes de plan changées,
  classe différente de l'abaque, coordonnée hors de France) ; une version N+1 est **lue** en ignorant les
  champs nouveaux (ADR 0003 § 2, Pascal le 2026-10-28, Q1 du plan).

## Terrain

- **Échantillon que Pascal vérifiera :** la tournée d'exemple ouverte dans QField et dans R (`sf::st_read`).

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la remise du plan, puis à la PR.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Le brief vers Nemeton peut renvoyer à ce schéma sans rien ajouter, et la mission 003 écrit ses tests sans
question.
