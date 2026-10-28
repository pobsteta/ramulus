# Ramulus — brief

> **Brouillon du 28 octobre 2026**, rédigé par l'orchestrateur à partir de la session du jour (essai à l'aveugle sur
> les fiches DSF, choix du nom). **À critiquer par le `contradicteur`, puis à amender et valider par Pascal
> (porte n° 1).** Les propositions à trancher sont marquées **[à trancher]**.

## Ma réponse d'abord

**2026-10-28, Pascal** (question de départ, recopiée de la session) :

> pourrait-on envisager avec l'IA embarquée de noter à partir de plusieurs photos, selon la méthode ARCHI ou
> DEPERIS, à toi de me dire, un arbre pris en photo en forêt ?

Choix fait le même jour : **DEPERIS d'abord**. Le protocole est visuel, depuis le sol, sur le houppier seul, avec
deux notes ordinales et un abaque. ARCHI demande de lire l'architecture et une dynamique, avec des clés par essence,
et se prête mal à la photo en peuplement dense. **[à trancher : ARCHI en tranche ultérieure, ou hors périmètre]**

## Le problème, et pour qui (Abstraction)

- **Problème** : la notation DEPERIS demande un œil formé, et deux notateurs s'écartent couramment d'une classe. La
  note ne laisse aucune trace visuelle : on ne peut ni la revoir au bureau, ni comparer d'une année à l'autre sur la
  même image. **[à étayer : variabilité entre notateurs mesurée par le DSF, à chercher par l'`acquereur`]**
- **Bénéficiaires** **[à trancher, ordre proposé]** :
  1. le **gestionnaire forestier** qui décide d'une coupe sanitaire, d'un report de coupe ou d'un renouvellement ;
  2. le **correspondant-observateur** du DSF, pour qui Ramulus serait un outil de saisie et d'harmonisation ;
  3. le **propriétaire**, en sortie dérivée (bilan de peuplement).
- **Coût complet aujourd'hui** : **[à compléter par Pascal : temps par arbre et par placette, nombre de placettes
  par an, coût d'une erreur de classement]**.

## Ce que fait Ramulus

- **En une phrase** : application Android de terrain, hors ligne, qui aide à noter l'état du houppier selon
  DEPERIS à partir de plusieurs photos de l'arbre, garde les photos avec la note, et rend le bilan du peuplement.
- **Les gestes sur le terrain** : l'opérateur choisit l'essence ; il prend 2 à 4 photos du houppier autour de
  l'arbre, guidé (distance, contre-jour) ; il voit la fiche de référence de l'essence ; il pose MB et MR ou MA, ou
  valide ce que l'IA propose ; la classe s'affiche ; il passe à l'arbre suivant. Le bilan du peuplement se tient à
  jour.
- **Ce qu'elle produit** **[à trancher : destinataire]** : un GeoPackage des arbres notés (position, essence, notes,
  classe, proposition de l'IA, photos), à verser dans **nemeton** (indicateurs de santé du peuplement, comme Groma
  pour l'inventaire) ou dans **sommieR** (registre de suivi).

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

1. **Où tourne l'IA** **[à trancher, ADR à écrire]** : un modèle embarqué (rapide, hors ligne, à entraîner) ou un
   modèle distant (meilleur tout de suite, mais réseau et envoi de photos). Proposition : notation manuelle d'abord,
   collecte des photos notées, puis modèle embarqué entraîné sur ces données.
2. **Données d'entraînement** : les 250 photos des fiches suffisent pour tester, pas pour entraîner. Il faut des
   milliers d'arbres notés par des observateurs formés. **[à trancher : partenariat DSF / ONF / CNPF ?]**
3. **Droits sur les photos des fiches** : on n'en garde que les notes (fixtures) ; les images ne sont pas versées
   au dépôt.
4. **Quatre incohérences** relevées dans les fiches (pin d'Alep, pin laricio, deux pins sylvestres) : à signaler au
   DSF. **[à trancher : qui écrit]**
5. **Position de l'arbre** : RTK comme Marculus et Surculus, ou GNSS du téléphone suffit-il ? **[à trancher]**
