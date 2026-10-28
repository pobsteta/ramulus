# ADR 0003 — Échange avec Nemeton : une tournée GeoPackage écrite par Nemeton, complétée par Ramulus, importée tout ou rien

- **Date :** 2026-10-28
- **Statut :** proposée, amendée après critique du `contradicteur` et arbitrages de Pascal (2026-10-28)
- **Proposée par :** Claude (orchestrateur)
- **Tranchée par :** Pascal

## La décision en une phrase

Nemeton écrit la tournée `deperis.gpkg` (EPSG:2154) à partir de son plan d'échantillonnage, selon les règles
d'écriture déjà éprouvées par Groma ; Ramulus la complète avec sqlite-jdbc comme Surculus, et la rend dans un `.zip`
avec ses photos ; Nemeton l'importe tout ou rien, de façon rejouable, en recalculant la classe ; la contre-notation
voyage dans une tournée et une couche à part.

## Contexte

- Brief validé : plan fait sur Nemeton, **Nemeton fait foi**, sommieR reçoit de Nemeton le bilan par unité de
  gestion. Pascal, 2026-10-28 : **Nemeton écrit la tournée** ; les décisions de Groma et de Surculus sont
  **reprises telles quelles**.
- Ce que Groma a appris : `groma/briefs/vers-nemeton/2026-10-12-inventaire-video.md`, § 2.1 à 2.3 (options GDAL,
  déclencheurs R*Tree et SQLite d'Android, `date` mot réservé, colonnes de plan contrôlées ; la convergence des
  méridiens ne sert plus, faute d'azimuts : ADR 0004).
- Ce que Surculus a tranché : ADR 0011 (sqlite-jdbc sur l'appareil), ADR 0007 (version au niveau du fichier,
  repère lu dans `gpkg_spatial_ref_sys`), ADR 0005 (sauvegarde).
- Options envisagées : sommieR prépare et reçoit (modèle Surculus) ; paquet propre à Ramulus (JSON) ; QField seul.

## Le contrat

1. **Fichier** : `deperis.gpkg`, EPSG:2154 lu dans `gpkg_spatial_ref_sys` ; écrit par Nemeton avec
   `GEOMETRY_NAME=geom`, `FID=fid`, **`SPATIAL_INDEX=NO`** sur toute couche que Ramulus modifie (Groma § 2.2).
   Ramulus écrit avec **sqlite-jdbc** (Surculus ADR 0011), jamais avec le SQLite d'Android.
2. **Version** : une seule, au niveau du fichier, dans une table `tournee` (version du schéma, `prepare_le`,
   `version_nemeton` ; `operateur`, `version_ramulus` écrits par Ramulus) ; un lecteur de version N lit N+1 en
   ignorant les champs nouveaux (Surculus ADR 0007).
3. **Couches** :
   - `placettes` (POINT prévu) : colonnes du plan Nemeton (`plot_id`, `wgt`, `ip`, `stratum`…), **jamais
     modifiées**, contrôlées à l'import ; paramètres de campagne `nb_arbres`, `regle_choix` (défaut `kraft_1_3`),
     `saison`, `collecte_photos`, `contre_notation` ; `etat` écrit par Ramulus ;
   - `centres` (POINT relevé au GNSS du téléphone, moyenné ; ADR 0004) : `plot_id`, précision, nombre de mesures,
     source, `date_releve`, opérateur ;
   - `notations` (**table d'attributs, sans géométrie** ; ADR 0004) : `uuid`, `plot_id`, `rang`, `essence`, `type` (feuillu / résineux), `mb`, `second`, `critere`, `classe`, `saison`,
     `date_notation`, `operateur`, `photos` (noms et empreintes), colonnes réservées de l'IA (ADR 0001 :
     première note, proposition, confiance, modèle), vides en tranche 1.
4. **Contre-notation** : Nemeton écrit pour le second opérateur une **tournée à part**, sans couche `notations` ;
   il la rend dans une table **`contre_notations`** (mêmes colonnes, sans géométrie). Délai maximal entre les deux passages : à fixer par Pascal
   (ICP Forests : « très près de la date » du premier). **La première note fait foi** dans le bilan.
5. **Rendu** : un `.zip` contenant `deperis.gpkg` et le dossier `photos/` ; plusieurs opérateurs : fusion par
   union sur l'`uuid` avant de rendre ; deux notations du même arbre hors contre-notation signalées.
6. **Import par Nemeton** (fonction proposée `importer_deperis()`) : tout ou rien ; rejouable sans doublon
   (fusion par `uuid`) ; refuse un paquet dont les colonnes de plan ont changé, ou dont une classe diffère de
   l'abaque appliqué aux notes.
7. **Schéma** : source unique `docs/formats/deperis-v1.schema.json` dans Ramulus, copiée dans Nemeton ; cas
   communs `fixtures/echange/` rejoués en Kotlin et en R.
8. **sommieR** ne lit jamais la tournée : il reçoit de Nemeton le bilan par unité de gestion.

## Le crible FRANCE

| Lettre | Réponse | Ce que ça coûte | Prêts à payer ? |
|---|---|---|---|
| **F** | Un fichier, un format ouvert (OGC GeoPackage), lisible par QField | règles d'écriture strictes | oui |
| **R** | Nemeton écrit et importe (Pascal, dans nemeton et nemetonshiny) ; Ramulus complète | deux fonctions et un module côté Nemeton | à confirmer par Pascal (brief vers Nemeton) |
| **A** | Version de fichier, import rejouable, contrôle des colonnes de plan, test d'écriture sur appareil | tests en deux langages | oui |
| **N** | sommieR, JSON propre, QField seul : écartés ci-dessous | — | — |
| **C** | Reprise de Groma et Surculus au lieu d'un contrat neuf | dépendance à leurs choix | oui |
| **É** | Réutilise l'écrivain GeoPackage de Surculus et le contrat de Groma ; le schéma servira à d'autres | — | — |

## Valeurs sacrifiées

- **L'autonomie de Ramulus** : sans tournée de Nemeton, pas de campagne (l'écran de notation libre reste pour la
  formation et l'essai).
- **La lecture directe par sommieR** : un intermédiaire de plus.
- **Le repli QField** : QField lit la tournée mais ne calcule pas la classe ; une tournée remplie dans QField doit
  être recalculée par Nemeton (l'import le fait, point 6).

## Options écartées

- **sommieR prépare et reçoit** : Nemeton fait foi (Pascal).
- **Paquet JSON propre à Ramulus** : deux formats à tenir.
- **Contre-notation dans la couche `notations`** : non aveugle (contradicteur).

## Sources

- Groma, `briefs/vers-nemeton/2026-10-12-inventaire-video.md`, § 2.1-2.3.
- Surculus, `docs/adr/0005`, `0007`, `0011`.
- OGC GeoPackage Encoding Standard 1.3 (OGC 12-128r18).
- Manuel ICP Forests, partie IV, § 6.1.2.1.

## Question de contrôle

Cette décision tient-elle nos six valeurs, et que sommes-nous prêts à payer pour cela ?
