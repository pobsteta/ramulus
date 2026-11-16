# Mission 020 — Tests d'acceptation de la correction par remplacement

## Mission

**En une phrase (quoi, et pourquoi) :** écrire, depuis la fiche 015, la spec et les formats, et sans lire le code de
la mission 015, les tests d'acceptation de la correction d'un arbre et d'un centre par remplacement, pour que 015 ne
s'évalue pas elle-même.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | fiche 015 (« Décisions qui s'imposent ») ; formats ; précédents 003 et 014 |
| A-t-il les moyens ? | oui, vérifié le 2026-10-30 | `main` 00d8c24 : `ControleurTournee` (`rouvrirArbre`, `enregistrerArbre`, relevé du centre), lecture du GeoPackage dans `:core/echange`, journal dans `:data` |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `testeur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** l'implémentation ; lire la branche `mission/015-*`.

## Ce qu'il teste

Les règles de la rubrique « Décisions qui s'imposent » de la fiche 015, par l'interface publique **déjà sur `main`**
(le contrôleur de tournée, le lecteur du GeoPackage, le journal), en boîte noire :

1. Un arbre noté puis rouvert et enregistré de nouveau : une seule note pour ce `plot_id` et ce `rang` dans la copie
   de travail, d'`uuid` différent de la première, avec les nouvelles notes.
2. Le journal porte deux lignes pour cet arbre ; la seconde a `remplace_uuid` = `uuid` de la première ; aucune ligne
   n'est modifiée ni supprimée.
3. Les photos de la note d'origine sont citées par la note corrigée (`photos/<nouvel uuid>_<n>.jpg`, même SHA-256), et
   le paquet rendu ne lève ni `photo_absente`, ni `empreinte_photo_differente`, ni `photo_non_citee`.
4. Un centre relevé deux fois : une seule ligne `centres` pour la placette, la seconde position ; le journal a deux
   lignes `centre`, la seconde avec `remplace_uuid`.
5. Le rendu d'une placette dont un arbre a été corrigé ne signale aucun `doublon_arbre`.
6. Deux lignes de même rang venues de deux opérateurs (union) sont toujours signalées `doublon_arbre` (non-régression).
7. Une correction après le rendu retire la marque « rendue ».

## Harnais

- **Peut :** écrire des tests JVM dans `:app/src/testFumee` ou `:core`/`:data` selon ce qu'ils touchent ; des cas dans
  `fixtures/` si besoin.
- **Ne doit pas :** lire le code de 015 ; modifier le code de production ; modifier un format (une lacune remonte à
  l'orchestrateur) ; toucher à `main`.
- **Sources autorisées :** `pilotage/missions/015-correction-par-remplacement.md` ; `docs/spec.md` ; ADR 0004, 0005
  et 0006 ; `docs/formats/deperis-v1.*` et `ramsync-v1.*` ; le code de `main` 00d8c24 (interfaces publiques et
  tests existants, pour savoir appeler).

## Valeur provisoire (chiffre, réglage)

- aucune

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** `main` 00d8c24. Part de `origin/main`, branche `mission/020-tests-correction`.
- **Ordre de fusion :** l'orchestrateur fusionne 020 dans la branche de 015 ; pas de PR séparée vers `main`.
- **CI rouge attendue :** sur `main`, les tests 1 à 5 et 7 sont rouges tant que 015 n'est pas fusionnée. Les garder
  hors de `.claude/test-command` par le même mécanisme que 003 (variable d'environnement ou étiquette), avec renvoi à
  015, et le dire dans le rendu ; le test 6 doit être vert dès maintenant.

## Tests

- **Comment saura-t-on que c'est juste :** chaque règle 1 à 7 a au moins un test au nom lisible en français ; sur
  `main`, les tests de 015 échouent pour la bonne raison (message lu, cité dans le rendu).

## Terrain

- **Échantillon que Pascal vérifiera :** la liste des tests, lisible en français.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, avec la PR de 015.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Qui tranche :** l'orchestrateur, par délégation de Pascal du 2026-10-28.

## Comment saura-t-on que ça marche ?

015 passe ces tests sans qu'on les retouche.
