# Essai du 28 octobre 2026 : notation DEPERIS à l'aveugle par une IA généraliste

**Question** : un modèle de vision généraliste, sans entraînement ni fiche de référence, retrouve-t-il la classe
DEPERIS d'un arbre sur une photo ?

**Protocole** : les 250 photos notées des 25 fiches essences du DSF (2025) ont été extraites sans leurs étiquettes.
Pour chacune, le modèle (Claude, session du 28 octobre) a posé MB et MR ou MA ; la classe vient de l'abaque. Les
notes du DSF ont été relevées ensuite dans les PDF et rattachées photo par photo, avec vérification visuelle de
chaque page. Les 13 photos de la fiche Hêtre été sont exclues des chiffres : vues avec leurs notes lors d'un premier
essai.

**Résultats (237 photos)**

| Mesure | Résultat |
| --- | --- |
| Classe identique | 127 (54 %) |
| À une classe près | 229 (97 %) |
| Sains (A, B) reconnus sains | 55 / 62 ; 1 seul classé dégradé |
| Dégradés (D, E, F) reconnus dégradés | 79 / 106 (75 %) |
| Arbres F trouvés | 11 / 26 |
| Sous-notations / sur-notations | 87 / 23 |
| Biais moyen sur MB / sur MR-MA | −0,32 / −0,11 point |
| Feuillus été / résineux / feuillus hiver (classe identique) | 62 % / 52 % / 42 % |

Matrice de confusion (lignes : DSF ; colonnes : IA)

|   | A | B | C | D | E | F |
| --- | --- | --- | --- | --- | --- | --- |
| A | 8 | 12 | · | · | · | · |
| B | 11 | 24 | 6 | · | · | 1 |
| C | · | 20 | 47 | 1 | 1 | · |
| D | · | · | 27 | 32 | 2 | · |
| E | · | · | · | 14 | 5 | · |
| F | · | · | · | 6 | 9 | 11 |

**Enseignements**

1. Notation trop clémente, surtout sur la mortalité de branches (D noté C : 27 fois).
2. Les arbres morts se confondent avec les houppiers voisins sur une seule photo, ou avec des rameaux nus en hiver :
   il faut plusieurs vues et isoler le houppier visé.
3. Le tri sain / dégradé est déjà fiable : utile pour orienter le passage d'un notateur.
4. Il faut la référence de l'essence : la transparence normale d'un chêne n'est pas celle d'un hêtre.
5. Quatre incohérences dans les fiches elles-mêmes (sous-notes et classe imprimée en désaccord).

Page de résultats avec les 250 photos (privée, à partager par Pascal) : artefact
« DEPERIS : notation par IA à l'aveugle des fiches essences DSF », publié le 2026-10-28.
