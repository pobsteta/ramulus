# Mission 018 — Noter sans numéro peint : le rang suffit

## Mission

**En une phrase (quoi, et pourquoi) :** retirer de Ramulus la saisie du numéro peint et donner à chaque arbre un rang
attribué automatiquement dans le tour d'horizon (depuis le nord, sens horaire), parce que Pascal a décidé le
2026-10-29 qu'on ne peint pas les arbres.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | décision de Pascal ci-dessous ; ADR 0004 ; format `deperis-v1` ; écrans de 006 |
| A-t-il les moyens ? | oui, vérifié le 2026-10-29 | 004 et 006 fusionnées ; `.claude/test-command` vert sur `main` (890ad03) |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** les photos (008) ; supprimer la colonne `repere` du format.

## Décisions qui s'imposent

- **Pascal, 2026-10-29 :** « Non on ne peint pas de numéro, les arbres sont incrémentés automatiquement depuis le nord
  dans le sens horaire d'une montre, de façon automatique. »
- **Arbitrages de l'orchestrateur, par délégation de Pascal du 2026-10-28 :**
  - **La colonne `repere` reste dans `deperis-v1`, mais devient facultative** (NULL permis). Ramulus l'écrit à NULL.
    On ne la supprime pas : elle figure dans 38 cas communs, dans le contrôle de référence Python et dans les tests
    de 003. Sa suppression avant le gel du format est une question posée à Pascal.
  - **Le rang est attribué par l'application :** le premier arbre noté d'une placette porte le rang 1, puis chaque
    arbre enregistré fait passer au rang suivant, jusqu'à `nb_arbres`. Plus de boutons − / + pour choisir le rang.
    On peut rouvrir un arbre déjà noté pour le corriger, et il garde son rang.
  - **`doublon_arbre`** : le « même arbre » est le même `plot_id` et le même `rang`. La règle « ou même `repere` »
    ne vaut que si les deux `repere` sont non NULL.
  - **L'écran** rappelle la règle de choix de la tournée (`regle_choix`, par défaut « Kraft 1 à 3, tour d'horizon
    depuis le nord, sens horaire ») au-dessus du rang : « Arbre 3 sur 4 — continuez le tour depuis le nord, sens
    horaire ».

## Harnais

- **Peut :**
  - modifier `:app` (écran des arbres, `ControleurTournee`, `Modele`, `Avertissements`) ;
  - rendre `repere` facultatif dans `:core/echange` (lecture, écriture, contrôle), dans `docs/formats/deperis-v1.md`
    et `.schema.json`, et dans `fixtures/echange/deperis_v1.py` ;
  - ajouter un cas commun « notation sans repère », accepté ;
  - ajouter une rubrique datée « Ajout du 2026-10-29 » à l'ADR 0004 et à la spec, sans rien effacer.
- **Ne doit pas :**
  - supprimer la colonne `repere` ;
  - changer la version du format ;
  - modifier les cas existants autrement que pour les rendre conformes à la règle `doublon_arbre` ci-dessus ;
  - toucher à `main`.
- **Sources autorisées :** ADR 0003 et 0004 ; `docs/spec.md` ; `docs/formats/deperis-v1.*` ;
  `docs/referentiels/kraft.yaml` ; les fiches 004 et 006 ; le code de `:core`, de `:data` et de `:app`.

## Valeur provisoire (chiffre, réglage)

- aucune

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 006 (fusionnée). Part de `main` (890ad03).
- **Ordre de fusion :** 008 → 018. 018 se met à jour de `main` par fusion après celle de 008 (conflits attendus dans
  `Ecrans.kt` et `ControleurTournee.kt`).
- **PR en brouillon jusqu'à :** la fusion de 008.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :**
  - une placette de 4 arbres se note sans aucune saisie au clavier, et les rangs sont 1, 2, 3, 4 ;
  - un arbre corrigé garde son rang ;
  - un GeoPackage dont `repere` est NULL est accepté (Kotlin et Python) ;
  - un `repere` non NULL est toujours lu (compatibilité avec les cas existants) ;
  - `doublon_arbre` sur le même rang, et pas sur deux `repere` NULL ;
  - `.claude/test-command` est vert.

## Terrain

- **Échantillon que Pascal vérifiera :** noter les 4 arbres de P001 sans taper de numéro ; voir « arbre 1 sur 4 »
  jusqu'à « 4 sur 4 » ; rendre le `.zip`.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis l'orchestrateur, par délégation de Pascal du 2026-10-28.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** à la PR.
- **Qui tranche :** l'orchestrateur, par délégation ; chaque arbitrage est inscrit au journal.

## Comment saura-t-on que ça marche ?

Sur le téléphone de Pascal, la placette P001 se note jusqu'au rendu sans qu'on demande de numéro peint.
