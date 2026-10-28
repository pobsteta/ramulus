# Ramulus

[![CI](https://github.com/pobsteta/ramulus/actions/workflows/ci.yml/badge.svg)](https://github.com/pobsteta/ramulus/actions/workflows/ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-min%2026%20%C2%B7%20cible%2036-3DDC84?logo=android&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
[![Licence](https://img.shields.io/badge/licence-GPL--3.0-blue)](LICENSE)

Application Android de **notation de l'état des houppiers selon le protocole DEPERIS** du Département de la santé
des forêts (DSF), hors ligne. Pour chaque arbre, l'opérateur photographie le houppier sous plusieurs angles, pose la
mortalité de branches (MB) et le manque de ramification (MR, feuillus) ou d'aiguilles (MA, résineux) ; la classe A à
F se lit sur l'abaque, et le peuplement est dit dégradé au-delà de 20 % d'arbres D, E ou F. Une IA embarquée
**propose** les notes ; l'opérateur **décide**.

Le nom fait la famille avec [Marculus](https://github.com/pobsteta/marculus) (le petit marteau qui désigne l'arbre)
et [Surculus](https://github.com/pobsteta/surculus) (la jeune pousse) : *ramulus*, le petit rameau, celui qu'on
regarde pour noter MB et MR. Ramulus fait partie de la plateforme **Néméton**, à côté de Marculus, Surculus et
[Groma](https://github.com/pobsteta/groma).

> **État : cadrage (tranche 0).** Brief et spec en brouillon, à valider par Pascal (porte n° 1). Le domaine DEPERIS
> (notes, abaque, bilan de peuplement, référentiel des 19 essences) est écrit et testé, avec un premier écran de
> notation manuelle. Ni photo, ni GNSS, ni IA, ni export pour l'instant : voir
> [`pilotage/kanban.md`](pilotage/kanban.md).

## Pourquoi une IA, et jusqu'où

Un premier essai à l'aveugle (28 octobre 2026) sur les 250 photos notées des fiches du DSF, avec un modèle généraliste
sans entraînement : **54 % de classes identiques, 97 % à une classe près**, mais une notation trop clémente (la
mortalité de branches est sous-estimée) et des arbres morts mal reconnus sur une seule photo. Détail :
[`docs/essais/2026-10-28-essai-ia-a-l-aveugle.md`](docs/essais/2026-10-28-essai-ia-a-l-aveugle.md).

D'où la règle de Ramulus ([ADR 0001](docs/adr/0001-ia-propose-operateur-decide.md)) : l'IA aide, elle ne note pas
seule. Chaque notation garde la proposition de l'IA à côté de la note retenue, ce qui constitue au passage le jeu de
données pour entraîner le modèle embarqué.

## Développer

```sh
./gradlew :core:test            # domaine DEPERIS, sans Android
./gradlew :app:assembleDebug    # APK de debug
```

La commande complète de la CI est dans [`.claude/test-command`](.claude/test-command).

## Sources

- Fiches essences DEPERIS du DSF, versions 2025 : liens dans
  [`docs/referentiels/deperis/essences.csv`](docs/referentiels/deperis/essences.csv).
- [La méthode DEPERIS](https://agriculture.gouv.fr/la-methode-deperis-comment-quantifier-et-mesurer-letat-de-sante-dune-foret-et-son-evolution),
  ministère de l'Agriculture.

## Licence

GPL-3.0-or-later, comme Marculus, Surculus et Groma.
