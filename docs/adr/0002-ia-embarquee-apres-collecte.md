# ADR 0002 — IA embarquée, entraînée après la collecte

- **Statut :** proposée (2026-10-28), à critiquer par le `contradicteur`, puis à trancher par Pascal
- **Décideur :** Pascal

## Contexte

Pascal (2026-10-28) : notation manuelle d'abord, collecte des photos notées, modèle embarqué entraîné ensuite.
L'essai à l'aveugle du même jour (modèle généraliste distant) donne 54 % de classes identiques sur les fiches DSF,
cas favorable (photos choisies, publiques). Le seul travail publié trouvé sur l'estimation de la défoliation par
photos au sol a utilisé environ 2 100 images (Kälin et al., WSL, bioRxiv 441733). Une note de l'arbre entier n'est
pas une étiquette de photo (critique du contradicteur, journal).

## Décision proposée

1. **Tranche 1 sans IA.** Ramulus collecte : une photo par arbre noté, 2 à 4 sur les placettes « collecte ».
2. **Le modèle tourne sur le téléphone**, hors ligne (LiteRT, ex-TensorFlow Lite), quand il existe ; aucune photo
   n'est envoyée à un service distant (règle du `CLAUDE.md`).
3. **Le modèle n'est livré qu'au-dessus d'un seuil mesuré** sur un jeu de test tenu à part (placettes de
   contre-notation comprises) : seuil à fixer par Pascal avant l'entraînement, par exemple « au moins aussi bon que
   l'accord entre deux opérateurs ».
4. **Repli** : si le seuil n'est pas atteint, l'IA se limite au tri sain / dégradé (ADR 0001, options).

## Crible FRANCE

- **Frugale** : pas d'IA tant qu'elle n'apporte rien ; un petit modèle sur le téléphone plutôt qu'un serveur.
- **Responsable** : l'opérateur reste maître de la note (ADR 0001) ; les photos ne quittent pas le terrain sans
  décision.
- **Anticiper** : le format garde dès la tranche 1 la place de la proposition et de la première note.
- **Novateur** : sans objet en tranche 1.
- **Coût** : l'entraînement demande des milliers d'arbres notés ; qui les fournit n'est pas tranché (brief, § ouvert 2).
- **Écologique** : calcul local, pas de transfert massif d'images.

## Valeurs sacrifiées

- La qualité immédiate qu'offrirait un grand modèle distant (meilleur tout de suite, mais réseau et envoi des
  photos).
- Le temps de l'opérateur pendant la collecte, sans retour d'IA en échange.

## Options écartées

- **IA distante dès la tranche 1** : réseau absent en forêt, photos envoyées à un tiers.
- **Pas d'IA du tout** : Pascal veut l'IA embarquée à terme (réponse de départ, 2026-10-28).
