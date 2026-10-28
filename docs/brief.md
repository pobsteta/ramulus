# Ramulus — brief

> **Rédigé le 28 octobre 2026** d'après la réponse d'abord et les arbitrages de Pascal, critiqué par le
> `contradicteur` et **validé par Pascal le même jour (porte n° 1)** (journal). Ce que Pascal n'a pas
> tranché s'écrit « à trancher par Pascal ».

## Ma réponse d'abord

**2026-10-28, Pascal** (réponse à « Qu'attends-tu de Ramulus, et pourquoi ? », recopiée mot pour mot) :

> J'attends de ramulus, la capcité à noter des arbres in situ et de renvoyer ces informations vers sommieR et
> Nemeton. Nemeton et/ou sommieR doivent préparer le répérage des arbres à noter via un plan d'échantillonnage fait
> sur Nemeton

Question de départ de la session, même jour :

> pourrait-on envisager avec l'IA embarquée de noter à partir de plusieurs photos, selon la méthode ARCHI ou
> DEPERIS, à toi de me dire, un arbre pris en photo en forêt ?

Choix fait le même jour : **DEPERIS d'abord**. ARCHI est **hors de la première version**, à réexaminer après le modèle
embarqué (Pascal, 2026-10-28).

## Le problème, et pour qui (Abstraction)

- **Problème** (réponse d'abord) : noter des arbres in situ, sur des arbres repérés à l'avance par un plan
  d'échantillonnage de Nemeton, et rendre ces notes à Nemeton et à sommieR. Aujourd'hui la note ne laisse aucune
  trace visuelle, ne se revoit pas au bureau et ne revient à aucun des deux outils. **[à étayer : variabilité entre
  notateurs mesurée par le DSF, à chercher par l'`acquereur`]**
- **Bénéficiaires** (ordre retenu par Pascal, 2026-10-28) :
  1. le **gestionnaire forestier** qui décide d'une coupe sanitaire, d'un report de coupe ou d'un renouvellement ;
  2. le **correspondant-observateur** du DSF, pour qui Ramulus serait un outil de saisie et d'harmonisation ;
  3. le **propriétaire**, en sortie dérivée (bilan de peuplement).
- **Coût complet aujourd'hui** : inconnu.

## Ce que fait Ramulus

- **En une phrase** : application Android de terrain, hors ligne, qui fait noter in situ l'état du houppier selon
  DEPERIS sur les arbres d'un plan d'échantillonnage de Nemeton, et rend les notes et le bilan du peuplement à
  Nemeton. **La note d'abord ; la photo sur un échantillon** (Pascal, 2026-10-28, après la critique du
  contradicteur) : une photo par arbre, 2 à 4 sur une part des placettes, pour constituer le jeu d'entraînement.
- **Les gestes sur le terrain** : l'opérateur ouvre la tournée et rejoint la placette ; il choisit les arbres selon
  la règle de la campagne ; pour chaque arbre il pose MB et MR ou MA (la fiche de référence du DSF s'ouvre en lien),
  la classe s'affiche ; il prend la ou les photos demandées ; il passe à l'arbre suivant. Le bilan du peuplement se
  tient à jour. Quand l'IA viendra, sa proposition ne s'affichera qu'**après** la note de l'opérateur (ADR 0001).
- **D'où viennent les arbres à noter** : d'un **plan d'échantillonnage fait sur Nemeton** (réponse d'abord). Forme
  retenue (Pascal, 2026-10-28) : **des placettes tirées par Nemeton, et une règle de choix des arbres** sur chaque
  placette. Le DSF ne fixe pas cette règle (note DGAL 2018-433 : « 20 à 50 arbres répartis en placettes ») : **le
  nombre d'arbres et la règle sont des paramètres de la campagne**, écrits par Nemeton dans la tournée (Pascal,
  2026-10-28) ; défaut proposé : classes de Kraft 1 à 3 (manuel ICP Forests, partie IV). Brief à écrire vers
  Nemeton : sait-il tirer ce plan ? La tournée est écrite par Nemeton (Pascal, 2026-10-28, ADR 0003).
- **Contrôle de qualité** (Pascal, 2026-10-28) : **5 % des placettes renotées à l'aveugle** par un second opérateur,
  comme le pratique ICP Forests, pour mesurer l'accord entre notateurs, que le DSF ne chiffre pas.
- **Ce qu'elle produit** : les arbres notés (placette, rang, repère peint, essence, notes, classe, photos), **renvoyés vers sommieR et
  Nemeton** (réponse d'abord). **Nemeton fait foi** (Pascal, 2026-10-28) : il importe le paquet arbre par arbre,
  puisqu'il a fait le plan ; **sommieR reçoit de Nemeton** le bilan par unité de gestion pour le sommier.

## Ce que Ramulus ne fait pas

- Il ne **diagnostique pas la cause** du dépérissement : DEPERIS quantifie, il n'explique pas (fiche DSF, principe).
- Il ne **note pas seul** : la proposition de l'IA n'est jamais enregistrée comme note (ADR 0001).
- Il ne mesure pas le **déficit foliaire** du réseau RSSDF / Renecofor, même s'il figure sur les fiches.

## Ce qu'on sait déjà (essai du 28 octobre 2026)

Sur 237 photos des fiches du DSF, un modèle généraliste sans entraînement trouve la même classe dans 54 % des cas,
à une classe près dans 97 %. Il reconnaît 55 arbres sains sur 62, mais seulement 75 % des dégradés et 11 arbres morts
sur 26 ; il sous-estime la mortalité de branches. Rapport :
[`docs/essais/2026-10-28-essai-ia-a-l-aveugle.md`](essais/2026-10-28-essai-ia-a-l-aveugle.md).

## Ce qui reste ouvert

1. **Où tourne l'IA** — tranché par Pascal le 2026-10-28 : notation manuelle d'abord (tranche 1 sans IA), avec
   collecte des photos notées ; un modèle embarqué, entraîné sur ces données, ensuite. ADR 0002 à écrire.
2. **Données d'entraînement — le modèle embarqué est un pari.** Les 250 photos des fiches suffisent pour tester,
   pas pour entraîner. L'opérateur note l'arbre entier vu sur place, pas ce que montrent les photos : une note n'est
   pas une étiquette d'image. Le seul travail publié trouvé a utilisé environ 2 100 images (Kälin et al., WSL,
   bioRxiv 441733). Il faut des milliers d'arbres notés selon une convention de prise de vue. Partenariat (DSF, ONF, CNPF) : à trancher par Pascal.
3. **Droits sur les fiches** : la licence etalab-2.0 couvre le texte de la page du ministère, pas les PDF ni leurs
   photos. Le dépôt n'en garde que les notes (fixtures) ; l'application ouvre la fiche en lien. Proposés par le
   contradicteur et **non retenus par Pascal** le 2026-10-28 : demander l'accord du DSF pour un usage hors ligne ;
   un état « non noté » avec motif codé (absent, abattu, mort déjà noté, essence hors fiches) et une règle de
   remplacement dans le plan.
4. **Quatre incohérences** relevées dans les fiches (pin d'Alep, pin laricio, deux pins sylvestres) : à signaler au
   DSF ; qui écrit : à trancher par Pascal.
5. **Position** — tranché par Pascal le 2026-10-28 (ADR 0004) : centre au GNSS du téléphone, matérialisé par un
   piquet ; arbres sans position propre, pris dans l'ordre d'un tour d'horizon depuis le nord et numérotés à la
   peinture, pour suivre les mêmes arbres d'une année à l'autre.
