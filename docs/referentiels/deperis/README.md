# Référentiel DEPERIS

Tout vient des **fiches essences DEPERIS du Département de la santé des forêts, versions 2025** (une par essence et,
pour certaines, par saison). On garde les liens et les tables, pas les PDF (ignorés par git, comme dans Surculus).

| Fichier | Contenu | Source dans la fiche |
| --- | --- | --- |
| `abaque.csv` | classe A à F pour chaque couple MB (ligne) × MR ou MA (colonne) | page 1, encadré « Note synthétique DEPERIS » |
| `essences.csv` | 19 essences, type (feuillu / résineux), liens vers les fiches d'été, d'hiver ou toutes saisons | page « La méthode DEPERIS » du ministère, liste des fiches |

Notes de 0 à 5 et leurs bornes (0–5, 6–25, 26–50, 51–75, 76–95, 96–100 %) et seuils de peuplement (plus de 20 %
d'arbres D, E, F ; plus de 20 % d'arbres E, F) : page 1 de chaque fiche, reportés dans `core/.../deperis/`.

Points de lecture :

- La case MB = 5, MR/MA = 5 porte « Mort » ; Ramulus la classe F, avec `Abaque.estMort`.
- La fiche Sapin imprime « DEPERIS = 0 » pour l'arbre de référence ; Ramulus le lit A.
- Le mélèze, résineux caduc, est noté au manque d'aiguilles (MA) comme sur sa fiche.
- Le DSF ne publie pas de fiche d'hiver pour le charme.
- Quatre photos des fiches ont des sous-notes qui ne donnent pas la classe imprimée (voir
  `fixtures/fiches-dsf-2025.json`, `incoherences_connues`).

Page de présentation : <https://agriculture.gouv.fr/la-methode-deperis-comment-quantifier-et-mesurer-letat-de-sante-dune-foret-et-son-evolution>.
