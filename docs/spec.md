# Ramulus — spécification

> **Rédigée le 28 octobre 2026 d'après le brief validé par Pascal le même jour** (`docs/brief.md`, journal). À
> critiquer par le `contradicteur`, puis à valider par Pascal : **porte n° 1**. Ce que Pascal n'a pas tranché
> s'écrit « à trancher par Pascal » et renvoie à une ADR.

## 1. Le parcours sur le terrain

1. **Recevoir la tournée.** Au bureau, Nemeton tire le plan d'échantillonnage et écrit la tournée DEPERIS (un
   GeoPackage, ADR 0003). L'opérateur la copie sur le téléphone ; Ramulus l'ouvre hors ligne.
2. **Aller à la placette.** Carte des placettes à faire, guidage jusqu'au point prévu, position du centre relevée
   (GNSS du téléphone ou RTK : ADR 0004, à trancher par Pascal).
3. **Choisir les arbres.** Ramulus rappelle la règle de la campagne (nombre d'arbres, règle de choix, par défaut
   classes de Kraft 1 à 3) ; l'opérateur désigne chaque arbre retenu, numéroté dans l'ordre de la règle.
4. **Noter chaque arbre.** Essence (liste du référentiel ; essence hors des 19 fiches : voir § 4), MB, puis MR ou
   MA ; la classe s'affiche par l'abaque. La fiche de référence du DSF s'ouvre en lien, pour la saison de la
   campagne.
5. **Photographier selon le régime de la placette.** Une photo du houppier par arbre ; 2 à 4 photos par arbre sur
   les placettes que le plan a marquées « collecte ». Contrôles à la prise : flou, contre-jour, houppier coupé.
6. **Clore la placette.** Bilan de la placette (classes, part de D-E-F) ; la placette passe « notée ». Sur les
   placettes marquées « contre-notation », un second opérateur note sans voir la première notation.
7. **Rendre la tournée.** Ramulus vérifie la tournée (§ 5), fusionne les opérateurs, et produit le paquet rendu à
   Nemeton.

## 2. Les données

| Donnée | Qui l'écrit | Où |
| --- | --- | --- |
| Placettes du plan (point prévu, colonnes du plan, paramètres de campagne) | Nemeton, jamais modifiées par Ramulus | couche `placettes` de la tournée |
| Paramètres de campagne : `nb_arbres`, `regle_choix`, `saison`, `collecte_photos`, `contre_notation` | Nemeton | colonnes de `placettes` |
| Centre relevé, date, opérateur, état de la placette | Ramulus | couche `centres` |
| Arbre noté : `uuid`, `plot_id`, rang, position (distance, azimut ou point), essence, MB, MR ou MA, classe, opérateur, horodatage, photos | Ramulus | couche `notations` |
| Proposition de l'IA (tranche ultérieure) : notes, confiance, modèle, et première note de l'opérateur | Ramulus | colonnes de `notations` (ADR 0001) |
| Journal des saisies (une saisie = un UUID, ajout seul) | Ramulus | base locale, sauvegardée |
| Photos | Ramulus | dossier de la tournée, nom et empreinte dans `notations` |

La classe est toujours **recalculée** par l'abaque à l'import par Nemeton : une classe rendue qui ne correspond pas
aux notes est une erreur.

## 3. Les règles (acquises, `core/`)

Notes de 0 à 5 et leurs bornes ; second critère MR pour les feuillus, MA pour les résineux ; abaque ; états A-B
sains, C intermédiaire, D-E-F dégradés, E-F très dégradés ; peuplement dégradé au-delà de 20 % de D-E-F, très
dégradé au-delà de 20 % de E-F (comparaison stricte). Source : fiches DSF 2025, page 1 ;
`docs/referentiels/deperis/`. Tests : `core/src/test/`, dont les 250 photos notées des fiches.

## 4. Hors périmètre

- Diagnostic de la cause, déficit foliaire RSSDF, ARCHI (v1).
- IA en tranche 1 (Pascal, 2026-10-28) : aucune proposition n'est affichée ; l'écran et le format gardent la place
  de l'ADR 0001.
- État « non noté » avec motif codé et règle de remplacement : proposés par le contradicteur, **non retenus** par
  Pascal (2026-10-28). Conséquence à connaître : un arbre de la règle absent ou abattu n'a pas de saisie dédiée ;
  l'opérateur passe au suivant selon la règle.
- Fiches DSF embarquées pour un usage hors ligne : non retenu ; la fiche s'ouvre en lien.
- Une essence hors des 19 fiches se note sans fiche de référence, essence saisie dans la liste de la tournée
  **[à trancher par Pascal : accepter ou refuser ces arbres]**.

## 5. Critères d'acceptation

1. Les 250 photos notées des fiches DSF redonnent leur classe par l'abaque, hors les quatre incohérences connues
   (fait, `FichesDsfTest`).
2. Une tournée de Nemeton s'ouvre sans réseau, et le paquet rendu se relit par Nemeton sans perte d'attribut
   (ADR 0003 ; cas communs dans `fixtures/`, rejoués en R et en Kotlin comme Groma et Surculus).
3. Une placette française tombe en France ; une coordonnée inversée est refusée.
4. Deux opérateurs sur la même tournée : la fusion garde toutes les saisies (union sur l'UUID) ; deux notations du
   même arbre hors contre-notation sont signalées avant de rendre.
5. Une placette « contre-notation » n'affiche jamais la première notation au second opérateur.
6. Une classe rendue est toujours celle de l'abaque ; la note d'IA (plus tard) n'est jamais la note enregistrée.
7. Temps de saisie mesuré dès la tranche 1, par arbre et par placette, avec et sans photos (critique du
   contradicteur : coût des photos pour l'opérateur).

## 6. Référentiels à acquérir (`acquereur`)

- Classes sociales de Kraft (1 à 5) et définition « dominant / codominant » : manuel ICP Forests, partie IV.
- Saison et période de notation DEPERIS recommandées, et règle « même saison d'une année à l'autre » : note DGAL
  2018-433 et fiches DSF.
- Conventions de prise de vue du houppier pour l'estimation sur image (Kälin et al., WSL ; ICP Forests, photo
  guides).

## 7. Décisions structurantes (ADR)

| ADR | Objet | État |
| --- | --- | --- |
| 0001 | L'IA propose, l'opérateur décide ; proposition affichée après la note | proposée, amendée |
| 0002 | IA embarquée, entraînée après la collecte de la tranche 1 | proposée |
| 0003 | Échange avec Nemeton : tournée et paquet rendu (GeoPackage) ; Nemeton fait foi, sommieR reçoit le bilan de Nemeton | proposée |
| 0004 | Position : centre et arbres (GNSS du téléphone, RTK, distance et azimut) | à écrire, à trancher par Pascal |
| 0005 | Stack : celle de Surculus (Kotlin, Compose, Room, minSdk 26), bibliothèques communes `terrain-*` | à écrire |

## 8. Tranches

- **Tranche 0 (faite, PR n° 1)** : domaine DEPERIS, écran de notation manuelle en session.
- **Tranche 1** : tournée Nemeton, guidage, notation par placette, photos selon le régime, journal, contre-notation,
  paquet rendu, mesure du temps de saisie.
- **Tranche 2** : essai IA n° 2 sur les fiches, puis modèle embarqué entraîné sur la collecte (ADR 0002) ;
  proposition affichée après la note.
