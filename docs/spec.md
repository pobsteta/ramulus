# Ramulus — spécification

> **Rédigée le 28 octobre 2026 d'après le brief validé par Pascal le même jour**, critiquée par le `contradicteur`
> et amendée d'après les arbitrages de Pascal (journal). **Validée par Pascal le 2026-10-28 (porte n° 1).** Ce
> qu'il n'a pas tranché s'écrit « à trancher par Pascal » et renvoie à une ADR.

## 1. Le parcours sur le terrain

1. **Recevoir la tournée.** Au bureau, Nemeton tire le plan d'échantillonnage et **écrit la tournée** DEPERIS (un
   GeoPackage, ADR 0003 ; Pascal, 2026-10-28). L'opérateur la copie sur le téléphone ; Ramulus l'ouvre hors ligne.
2. **Aller à la placette.** Carte des placettes à faire, guidage jusqu'au point prévu, centre relevé au GNSS du
   téléphone, moyenné, et matérialisé par un piquet à la première visite (ADR 0004).
3. **Choisir les arbres.** Ramulus rappelle la règle de la campagne (nombre d'arbres, règle de choix, par défaut
   classes de Kraft 1 à 3) ; les arbres sont pris dans l'ordre d'un **tour d'horizon depuis le nord, sens horaire** ; chaque
   arbre a son rang et un **numéro peint** saisi dans la tournée (ADR 0004), sans position propre.
4. **Noter chaque arbre.** Essence (une des 19 essences du référentiel ; un arbre d'une autre essence n'est pas
   noté, § 4), MB, puis MR ou MA ; la classe s'affiche par l'abaque. La fiche de référence du DSF s'ouvre en lien, pour la saison de la
   campagne.
5. **Photographier selon le régime de la placette**, et selon la convention de prise de vue (ADR 0006). Une photo
   du houppier par arbre ; 2 à 4 photos par arbre sur les placettes que le plan a marquées « collecte ». Contrôles
   à la prise, signalés sans bloquer : flou, contre-jour, houppier coupé (Pascal, 2026-10-28).
6. **Clore la placette.** Bilan de la placette (classes, part de D-E-F) ; la placette passe « notée ». Sur les
   placettes marquées « contre-notation », un second opérateur note à partir d'une **tournée à part, sans les
   premières notes**, dans un délai maximal à fixer par Pascal ; la première note fait foi (ADR 0003).
7. **Rendre la tournée.** Ramulus vérifie la tournée (§ 5), fusionne les opérateurs, et produit le `.zip` rendu à
   Nemeton (GeoPackage et photos). La sauvegarde hors de l'appareil suit l'ADR 0005 (trois voies, comme Surculus).

## 2. Les données

Couches et colonnes : ADR 0003 (seule source). En bref :

| Donnée | Qui l'écrit | Où |
| --- | --- | --- |
| Version du fichier, préparation, opérateur | Nemeton, puis Ramulus | table `tournee` |
| Placettes du plan, colonnes de plan jamais modifiées, paramètres de campagne (`nb_arbres`, `regle_choix`, `saison`, `collecte_photos`, `contre_notation`) | Nemeton | couche `placettes` |
| État de la placette | Ramulus | `placettes.etat` |
| Centre relevé | Ramulus | couche `centres` |
| Arbre noté : `uuid`, rang, numéro peint (`repere`), essence, type, MB, MR ou MA, classe, saison, date, opérateur, photos ; colonnes réservées de l'IA (première note, proposition) | Ramulus | table `notations`, sans géométrie |
| Contre-notation | Ramulus, second opérateur | table `contre_notations`, tournée à part |
| Journal des saisies (ajout seul, union sur l'UUID) | Ramulus | base Room, sauvegardée (ADR 0005) |
| Photos | Ramulus | dossier `photos/` du `.zip`, nom et empreinte dans `notations` |

**Ajout du 2026-10-29 (Pascal, mission 018).** On ne peint pas les arbres : le rang est attribué par l'application dans
le tour d'horizon (depuis le nord, sens horaire), sans saisie. `repere` reste dans le format, facultatif : un repère
physique déjà sur l'arbre et porté par la tournée de départ y est recopié, sinon NULL (ADR 0004, rubrique du même jour).
Le § 1 et le tableau ci-dessus restent lus avec cette correction.

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
  l'opérateur passe au suivant selon la règle. Écart assumé aux codes de remplacement d'ICP Forests (partie IV,
  § 4.1, 5.2.12).
- Fiches DSF embarquées pour un usage hors ligne : non retenu ; la fiche s'ouvre en lien.
- **Une essence hors des 19 fiches n'est pas notée** (proposition d'accepter ces arbres non retenue par Pascal,
  2026-10-28).
- Saison de la campagne qui ne correspond pas à la date du jour : **avertir sans bloquer**, en enregistrant la
  saison et la date (Pascal, 2026-10-28, SA1). La page du ministère dit que MB et MR/MA s'observent en toute saison.
- Essence sans fiche pour la saison (le **charme** et le **chêne liège** n'ont qu'une fiche d'été) : la note est
  prise avec la fiche d'été et un avertissement (Pascal, 2026-10-28, SA2 ; `docs/referentiels/saison-deperis.yaml`).

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
  guides) : **avant la tranche 1** (ADR 0006).

## 7. Décisions structurantes (ADR)

| ADR | Objet | État |
| --- | --- | --- |
| 0001 | L'IA propose, l'opérateur décide ; proposition affichée après la note | acceptée |
| 0002 | IA embarquée après la collecte ; seuil mesuré par placette et campagne | acceptée |
| 0003 | Échange avec Nemeton : tournée écrite par Nemeton, règles de Groma, import tout ou rien, contre-notation à part | acceptée |
| 0004 | Position approchée : centre au GNSS et au piquet, arbres par rang et numéro peint | acceptée |
| 0005 | Stack et reprises de Surculus (journal, sauvegarde, sqlite-jdbc) | acceptée |
| 0006 | Photos : convention de prise de vue ; jeu détenu par le projet Nemeton, Etalab 2.0 | acceptée |

Brief à écrire vers Nemeton après l'acceptation de l'ADR 0003 : sait-il tirer ce plan et écrire cette tournée ?

## 8. Tranches

- **Tranche 0 (faite, PR n° 1)** : domaine DEPERIS, écran de notation manuelle en session.
- **Tranche 1** (après les ADR 0003 à 0006 et la convention de prise de vue) : tournée Nemeton, guidage, notation
  par placette, photos selon le régime et la convention, journal et sauvegarde, contre-notation, paquet rendu,
  mesure du temps de saisie.
- **Tranche 2** : essai IA n° 2 sur les fiches, puis modèle embarqué entraîné sur la collecte (ADR 0002) ;
  proposition affichée après la note.
