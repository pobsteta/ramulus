# Équipe de Ramulus

> Produit par l'orchestrateur avec `/mission` le 2026-10-28, d'après le brief et la spec validés (porte n° 1).
> **Validée par Pascal le 2026-10-28 (porte n° 2), telle que proposée.** Règle de staffing : `imperatrice:methode`.

- **Statut :** validée
- **Validée par :** Pascal, 2026-10-28

## Plafond humain (celui de Surculus)

- **PR ouvertes en même temps, au plus :** 3
- **Missions de code en parallèle, au plus :** 2
- **PR de pilotage** (journal, kanban, fiches) : lâchées, hors plafond, sauf si elles portent une décision de Pascal.
- **Rythme de relecture de Pascal :** un créneau par jour.

## Missions de la tranche 1

Ordre : d'abord les moyens (référentiels, format d'échange, tests), puis un parcours complet minimal, puis
l'enrichissement.

| N° | Mission en une phrase | Agent | Modèle | 3 questions | Main | Dépend de |
|---|---|---|---|---|---|---|
| 001 | Sourcer les référentiels de la tranche 1 (Kraft, saison, prise de vue, précision GNSS sous couvert) pour que les règles de terrain ne soient pas inventées | `acquereur` | sonnet | oui · oui · oui | appuyer | — |
| 002 | Écrire le format d'échange `deperis-v1` (schéma JSON, description, cas communs) d'après les ADR 0003 et 0004, pour que Ramulus et Nemeton lisent le même fichier | `dev` | opus | oui · oui · oui | **tenir** (format durable) | — |
| 003 | Écrire, depuis la spec et le schéma, sans lire le code, les tests d'acceptation de l'échange | `testeur` | sonnet | oui · oui · non → après 002 | appuyer | 002 |
| 004 | Lire une tournée et écrire les notations dans le GeoPackage avec sqlite-jdbc (reprise Surculus ADR 0011), pour compléter la tournée sur l'appareil | `dev` | sonnet | oui · oui · non → après 002, 003 | appuyer | 002, 003 |
| 005 | Tenir le journal en ajout seul et la sauvegarde `.ramsync` (reprise Surculus ADR 0005), pour qu'aucune note ne se perde avec le téléphone | `dev` | opus | oui · oui · oui | **tenir** (données) | — |
| 006 | Enchaîner les écrans tournée → placette → notation (tour d'horizon, rang, numéro peint, contre-notation sans les premières notes), pour noter une placette entière | `afficheur` | sonnet | oui · oui · non → après 001, 004 | appuyer | 001, 004 |
| 007 | Relever le centre au GNSS du téléphone, moyenné, et signaler un écart au point prévu, pour rattacher la placette | `dev` | sonnet | oui · oui · non → après 001 (seuil) | appuyer | 001 |
| 008 | Prendre les photos selon le régime de la placette et la convention, sans galerie ni EXIF publié, avec contrôles signalés, pour constituer le jeu | `dev` | sonnet | oui · non → après 001 · oui | appuyer (plan d'abord) | 001, 005 |
| 009 | Rendre la tournée : fusion des opérateurs, vérifications, `.zip`, mesure du temps de saisie, pour que Nemeton importe sans perte | `dev` | sonnet | oui · oui · non → après 004, 005 | appuyer | 004, 005 |

Hors agents : **brief vers Nemeton** (`creer_tournee_deperis()`, `importer_deperis()`), écrit par l'orchestrateur
après la mission 002 et envoyé par Pascal ; **signalement au DSF** des quatre incohérences : par Pascal (décidé le 2026-10-28).

## Agents mobilisés et pourquoi

- `acquereur` (1) : aucune règle de terrain sans source (Kraft, saison, prise de vue, GNSS).
- `dev` (6) : format, GeoPackage, journal, GNSS, photos, rendu ; `opus` pour le format et le journal, qui engagent
  la durée.
- `testeur` (1, puis un test d'acceptation à chaque mission de développement) : le travail ne s'auto-évalue pas.
- `afficheur` (1) : les écrans de terrain (gants, soleil, une main).
- `relecteur` : à chaque PR.
- Ni `archiviste`, ni `analyste` : aucune mission ne les demande en tranche 1.

## Premier cycle

001 (`acquereur`) et 002 (`dev`, tenue) en parallèle, puis 003 (`testeur`). Le reste attend en « planifié ».
