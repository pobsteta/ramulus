# Mission 001 — Référentiels de la tranche 1

## Mission

**En une phrase (quoi, et pourquoi) :** sourcer dans `docs/referentiels/` les classes de Kraft, la saison de
notation DEPERIS, les conventions de prise de vue du houppier et la précision du GNSS d'un téléphone sous couvert,
pour que les règles de terrain de la tranche 1 ne soient pas inventées.

**Redite par l'agent (cycle 2, reportée par l'orchestrateur) :** sourcer dans `docs/referentiels/` Kraft, saison DEPERIS,
prise de vue du houppier et précision GNSS d'un téléphone sous couvert, pour éclairer la règle de choix par défaut et
la conduite saison/date (006), le seuil de 30 m (007) et la convention de photo (008).

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | sources nommées ci-dessous |
| A-t-il les moyens ? | oui | accès web, dossier `docs/referentiels/` |

**Temps de la main :** appuyer — documentaire, mais il fixe des valeurs reprises par 006, 007 et 008.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `acquereur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** écrire du code ; trancher une valeur (il la propose, sourcée).

## Harnais

- **Peut :** écrire dans `docs/referentiels/` seulement (un fichier par sujet, avec page et lien exacts).
- **Ne doit pas :** inventer une règle ; recopier un PDF (références seulement, comme les fiches DSF) ; toucher à
  `main`.
- **Sources autorisées :** manuel ICP Forests, partie IV (2020), § 4.1, 5.2.12, 6.1 (Kraft, contrôle, formulaire
  PHOT) ; note DGAL/SDQSPV/2018-433 du DSF ; fiches DEPERIS 2025 (`docs/referentiels/deperis/essences.csv`) ;
  Kälin et al., bioRxiv 441733 ; Purfürst 2022 (PMC8838512) et Surculus `docs/adr/0003-gnss-et-reperes.md` ;
  `docs/brief.md`, `docs/spec.md` § 6, ADR 0004 et 0006.

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** seuil d'écart au point prévu de 30 m (ADR 0004) ; convention de prise de vue de l'ADR
  0006 (règles marquées « non sourcées »).
- **Mesurée par :** cette mission (sources publiées) ; un essai sur le terrain par Pascal ensuite.
- **Gel :** avant la mission 007 (seuil) et la mission 008 (convention).

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune ; part de `main` après fusion de la PR n° 1.
- **Ordre de fusion :** indépendante.
- **PR en brouillon jusqu'à :** —
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** chaque règle a sa source (titre, page ou paragraphe, lien) ; les
  règles sans source sont dites « non sourcées » ; une table « règle → mission qui l'utilise ».

## Terrain

- **Échantillon que Pascal vérifiera :** la définition de Kraft 1 à 3 et la règle « saison qui ne correspond pas à
  la date », lues sur une placette.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la remise du premier fichier.
- **Qui tranche :** Pascal (valeurs proposées : seuil, conduite saison/date, convention).

## Comment saura-t-on que ça marche ?

Les fiches 006, 007 et 008 citent ces fichiers au lieu d'une valeur « à sourcer ».

## Remarques du relecteur — aller-retour 1/3 (2026-10-28, commit 6a14a96)

Verdict : **à reprendre**. Sources vérifiées sur pièces, toutes exactes ; seules ces corrections sont demandées,
sans toucher au fond ni aux valeurs à trancher :

1. **Bloquant** : `docs/referentiels/regles-missions.csv` est mal formé. L'en-tête a 6 champs, séparés par `;` ; des
   champs contiennent « ; » sans guillemets (lignes 2, 3, 8, 10, 12, 14 et 18 : 7 champs ; ligne 9 : 9 champs). Mettre
   les champs entre guillemets ou remplacer « ; » par « / » dans le texte : chaque ligne doit avoir exactement 6 champs.
2. `kraft.yaml` (notes_icp, § 5.2.12) : « les "classes 31-39" de Kraft 1 à 3 » laisse croire à des classes de Kraft ;
   ce sont des codes RM (removal/mortality) d'arbres de Kraft 1 à 3. Reformuler.
3. `gnss-telephone.yaml` : remplacer le chemin local de l'ADR 0003 de Surculus par l'URL du dépôt
   (github.com/pobsteta/surculus) avec le commit lu.
4. `regles-missions.csv` : retirer la réserve « 006 (rôle à confirmer…) » ; `pilotage/equipe.md` dit que 006 dépend
   de 001.
