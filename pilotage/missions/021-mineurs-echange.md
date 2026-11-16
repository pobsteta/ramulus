# Mission 021 — Lever les réserves mineures laissées par les relectures de l'échange

## Mission

**En une phrase (quoi, et pourquoi) :** lever, une à une, les réserves non bloquantes que les relectures de 004, 006,
007, 013 et 015 ont renvoyées à plus tard (documentation, garde-fous, précisions de source), pour qu'un successeur ne
trouve ni trou de documentation ni défaut silencieux dans l'échange et le journal.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | chaque réserve est nommée ci-dessous avec sa relecture d'origine (`pilotage/journal.md`) |
| A-t-il les moyens ? | oui, vérifié le 2026-10-30 | `main` 0d383f4 ; Surculus lisible en local (`git -C /home/pascal/dev/surculus show origin/main:…`) |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** les écrans (017) ; le rangement des photos orphelines et la confirmation à
  l'abandon d'un arbre (questions posées à Pascal, pas tranchées) ; tout changement de version d'un format.

## Réserves à lever

1. **004 (relecteur, 2026-10-29) :** dans la liste des conflits d'une union, une notation écartée ne porte pas son
   `plot_id` (cas S3) ; et un `plot_id` peut être joint par « / » dans un message. Donner le `plot_id` de chaque ligne
   écartée, et un séparateur qui ne peut pas apparaître dans un `plot_id` (ou une liste).
2. **007 (relecteur, 2026-10-29) :** KDoc de `Lambert93` : écrire « non vérifié » à côté des citations ALG0001 et
   ALG0003 de la note IGN NTG_71, qui n'a pas été lue (voir la fiche 007, point 5). Ne rien inventer de la note.
3. **013 (relecteur, 2026-10-29) :** reprendre de Surculus le garde-fou `verifierFumee` (lire son ADR 0011 et son
   code) ; partout où Ramulus écrit « ADR 0011 », préciser « ADR 0011 de Surculus ».
4. **006 (orchestrateur, 2026-10-29) :** écrire dans `docs/formats/ramsync-v1.md` le contenu de `valeur` d'une ligne
   `notation`, `contre_notation`, `photo` et `centre`, tel que le code l'écrit aujourd'hui (sans le changer) ; et écrire
   la règle « une tournée de même code ouverte de nouveau : l'ancienne copie est gardée », là où elle est appliquée
   (spec, rubrique datée « Ajout du 2026-10-30 »).
5. **015 (relecteur, 2026-10-30) :** documenter au catalogue de `docs/formats/deperis-v1.md` l'emploi de
   `rattachement_invalide` et `uuid_duplique` à l'écriture d'une correction (note à corriger absente, changement de
   rang ou de placette) ; rendre **abstraites** `Journal.saisir(…, remplaceUuid)` et `Journal.lignes()` dans
   `Ports.kt`, pour qu'un futur journal ne perde pas `remplace_uuid` en silence (les faux journaux des tests les
   implémentent ; ne pas modifier les assertions des tests de 020, seulement leur faux journal si la compilation
   l'exige).

## Harnais

- **Peut :** modifier `:core/echange`, `:app/tournee/Ports.kt` et les journaux qui l'implémentent, la KDoc de
  `Lambert93`, le build de fumée pour `verifierFumee` ; ajouter des rubriques datées aux formats et à la spec ; ajouter
  des tests.
- **Ne doit pas :** changer la version d'un format, un motif refusé en signalé ou l'inverse, une assertion d'un test du
  `testeur` ; toucher aux écrans ; toucher à `main`.
- **Sources autorisées :** journal des relectures citées ; fiches 004, 006, 007, 013 et 015 ; `docs/formats/` ;
  `docs/spec.md` ; le code de `:core`, `:data` et `:app` ; Surculus (ADR 0011, `verifierFumee`).

## Valeur provisoire (chiffre, réglage)

- aucune

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** `main` 0d383f4. En parallèle de 017 (fichiers distincts).
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** un test par réserve de code (1, 3, 5) ; le contrôle de référence Python et
  le Kotlin donnent les mêmes conflits avec `plot_id` ; `.claude/test-command` vert, code de sortie réel de Gradle.

## Terrain

- **Échantillon que Pascal vérifiera :** aucun ; la relecture suffit.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Qui tranche :** l'orchestrateur, par délégation ; chaque arbitrage est inscrit au journal.

## Comment saura-t-on que ça marche ?

Les cinq réserves sont closes au journal, chacune avec son commit, et la commande de tests est verte.
