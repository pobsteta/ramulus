# Ramulus — spécification (brouillon)

> **Brouillon du 28 octobre 2026. Ne fait pas foi avant la validation du brief (porte n° 1).** Leçon de Surculus
> (journal du 2026-10-15) : la spec suit le brief, elle ne le précède pas. Ce brouillon sert à cadrer le squelette ;
> seules les parties marquées **acquis** sont codées.

## 1. Domaine DEPERIS — acquis

Source : fiches essences DEPERIS du DSF, 2025, page 1 ; référentiels dans `docs/referentiels/deperis/`.

| Élément | Règle | Code |
| --- | --- | --- |
| Note d'un critère | entier de 0 à 5 ; bornes 0–5, 6–25, 26–50, 51–75, 76–95, 96–100 % | `core/deperis/Note.kt` |
| Second critère | MR pour les feuillus, MA pour les résineux (mélèze compris) | `Notation.kt` |
| Classe | lue sur l'abaque MB × MR/MA, jamais saisie ; case 5 × 5 = arbre mort | `Abaque.kt`, `abaque.csv` |
| États | A, B sains ; C intermédiaire ; D, E, F dégradés ; E, F très dégradés | `Classe.kt` |
| Peuplement | dégradé si **plus de** 20 % de D, E, F ; très dégradé si plus de 20 % de E, F | `Peuplement.kt` |
| Essences | 19 essences du DSF, avec leurs fiches d'été, d'hiver ou toutes saisons | `essences.csv`, `Essences.kt` |

Tests : `core/src/test/` ; les 250 photos notées des fiches (`fixtures/fiches-dsf-2025.json`) redonnent la classe
imprimée par l'abaque, hors quatre incohérences connues des fiches.

## 2. Écran de notation — acquis (tranche 0)

Choix de l'essence et de la saison ; lien vers la fiche de référence ; deux rangées de six gros boutons (MB, puis MR
ou MA), avec les bornes en pourcentage ; classe en grand, colorée ; enregistrer, annuler le dernier ; bilan du
peuplement. Session en mémoire.

## 3. Prise de vue — proposé

- 2 à 4 photos par arbre, autour du tronc ; au moins une avec tout le houppier fonctionnel dans le cadre.
- Contrôles à la prise : flou, contre-jour (ciel blanc dominant), houppier coupé par le cadre.
- Les photos restent dans le dossier de la tournée ; leur nom et leur empreinte vont au journal.

## 4. Aide de l'IA — proposé (ADR 0001)

- L'IA reçoit les photos de l'arbre, l'essence, la saison ; elle rend MB, MR/MA et une confiance.
- L'écran montre la proposition **à côté** des boutons, jamais à leur place ; l'opérateur valide ou corrige.
- La notation enregistre la note retenue **et** la proposition (`PropositionIa`), pour mesurer l'écart et entraîner.
- Correction connue du premier essai : biais de clémence sur MB ; l'essai suivant fournira la fiche de référence de
  l'essence au modèle.
- **[à trancher, ADR 0002]** modèle embarqué (LiteRT / TFLite), distant, ou les deux.

## 5. Journal, position, export — proposé

- Journal en ajout seul, une notation = un UUID, fusion entre opérateurs par union (repris de Marculus et Surculus).
- Position de l'arbre (GNSS ; RTK optionnel par `terrain-gnss`).
- Export GeoPackage en Lambert-93 : arbres notés, notes, classe, proposition de l'IA, photos. **[destinataire à
  trancher : nemeton ou sommieR]**
