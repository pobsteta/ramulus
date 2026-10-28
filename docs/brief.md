# Ramulus — brief

> **Brouillon du 28 octobre 2026, amendé le même jour** d'après la réponse d'abord et les arbitrages de Pascal
> (journal). **À relire par Pascal, puis à critiquer par le `contradicteur` (porte n° 1).** Ce que Pascal n'a pas
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

- **En une phrase** : application Android de terrain, hors ligne, qui aide à noter l'état du houppier selon
  DEPERIS à partir de plusieurs photos de l'arbre, garde les photos avec la note, et rend le bilan du peuplement.
- **Les gestes sur le terrain** : l'opérateur choisit l'essence ; il prend 2 à 4 photos du houppier autour de
  l'arbre, guidé (distance, contre-jour) ; il voit la fiche de référence de l'essence ; il pose MB et MR ou MA, ou
  valide ce que l'IA propose ; la classe s'affiche ; il passe à l'arbre suivant. Le bilan du peuplement se tient à
  jour.
- **D'où viennent les arbres à noter** : d'un **plan d'échantillonnage fait sur Nemeton** (réponse d'abord). Forme
  retenue (Pascal, 2026-10-28) : **des placettes tirées par Nemeton, et une règle de choix des arbres** sur chaque
  placette (les N arbres dominants ou codominants les plus proches du centre ; N et la règle sont à sourcer dans les
  protocoles du DSF). Qui écrit la tournée que reçoit Ramulus, nemeton ou sommieR : à trancher par Pascal.
- **Ce qu'elle produit** : les arbres notés (position, essence, notes, classe, photos), **renvoyés vers sommieR et
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
2. **Données d'entraînement** : les 250 photos des fiches suffisent pour tester, pas pour entraîner. Il faut des
   milliers d'arbres notés par des observateurs formés. Partenariat (DSF, ONF, CNPF) : à trancher par Pascal.
3. **Droits sur les photos des fiches** : on n'en garde que les notes (fixtures) ; les images ne sont pas versées
   au dépôt.
4. **Quatre incohérences** relevées dans les fiches (pin d'Alep, pin laricio, deux pins sylvestres) : à signaler au
   DSF ; qui écrit : à trancher par Pascal.
5. **Position** : centre de placette au RTK comme Marculus et Surculus, ou GNSS du téléphone ; arbres repérés par
   distance et azimut depuis le centre ? À trancher par Pascal.
