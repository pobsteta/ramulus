# Mission 016 — Icône « R » gothique

## Mission

**En une phrase (quoi, et pourquoi) :** donner à Ramulus une icône adaptative « R » en New Rocker, générée comme le
« S » de Surculus (missions 015 et 017 de Surculus). Le but : reconnaître Ramulus parmi ses sœurs Marculus et
Surculus.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | reprise à l'identique de Surculus : `app/icone/generer_icone.py`, `res/mipmap-*`, `docs/dependances.md` l. 39 |
| A-t-il les moyens ? | oui, vérifié le 2026-10-29 | Surculus en local ; Python 3 ; la police New Rocker se télécharge depuis `google/fonts` (OFL-1.1) |

**Temps de la main :** appuyer. C'est une demande de Pascal du 2026-10-29 : « un R gothique comme le S de Surculus ou
le M de Marculus ».

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** les écrans (006) ; changer le thème ou les couleurs de l'application.

## Harnais

- **Peut :**
  - écrire `app/icone/generer_icone.py`, repris de Surculus avec la lettre « R » ;
  - écrire les `res/mipmap-*` et `mipmap-anydpi-v26/ic_launcher*.xml` (avant-plan, fond, monochrome) ;
  - régler la couleur de fond `ic_launcher_background` ;
  - mettre à jour le manifeste si l'icône n'y est pas déclarée ;
  - ajouter une ligne à `docs/dependances.md` (la police et Pillow, absents de l'APK), et la police au `.gitignore`.
- **Ne doit pas :**
  - versionner la police ;
  - ajouter une dépendance à l'APK ;
  - toucher à `main`.
- **Sources autorisées :** Surculus au commit 3c01f4b : `app/icone/`, `app/src/main/res/mipmap-*`,
  `app/src/main/res/values*/` (couleur de fond), `docs/dependances.md`, `.gitignore`.

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** la couleur de fond, celle de Surculus, sauf si Surculus la tire de Marculus. Dans ce cas, la
  même règle s'applique.
- **Mesurée par :** Pascal, à l'écran d'accueil du téléphone.
- **Gel :** avant la première publication.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune ; part de `main`.
- **Ordre de fusion :** indépendante.
- **PR en brouillon jusqu'à :** —
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :**
  - `.claude/test-command` est vert (le lint vérifie les icônes) ;
  - le script se relance et produit les mêmes PNG.

## Terrain

- **Échantillon que Pascal vérifiera :** l'icône sur l'écran d'accueil de son téléphone, en thème clair, sombre et
  monochrome.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la PR.
- **Qui tranche :** l'orchestrateur, par délégation.

## Comment saura-t-on que ça marche ?

Sur le téléphone de Pascal, Ramulus porte un « R » gothique, à côté du « S » de Surculus et du « M » de Marculus.
