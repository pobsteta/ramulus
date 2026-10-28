# Mission 005 — Plan du `dev` (tenir)

> Rendu par le `dev` (opus) le 2026-10-28, sans écrire de fichier ; recopié ici par l'orchestrateur. À valider par
> Pascal (Q1 à Q14) avant toute écriture de code. « S. » = Surculus `docs/adr/0005-stockage-et-sauvegarde.md`
> (commit 3c01f4b, identique à b9cf9d6) ; « R. » = notre `docs/adr/0005-*.md`.

**Redite :** tenir sur le téléphone un journal Room où chaque saisie s'ajoute sans jamais être modifiée ni effacée,
et l'écrire régulièrement dans un `.ramsync` qu'on peut copier hors du téléphone et relire, règles reprises ligne à
ligne de l'ADR 0005 de Surculus, pour qu'aucune note de l'opérateur ne se perde avec le téléphone.

## 1. Tables du journal (Room, nouveau module `:data`, base v1)

### `journal` — une ligne par saisie, ajout seul (S. l. 46 ; R. l. 32)

| Colonne | Type | Règle | Dans R. l. 32 ? |
|---|---|---|---|
| `uuid` | TEXT, clé primaire | UUID v7 en minuscules (RFC 9562) | oui |
| `objet_uuid` | TEXT, non nul | notation, contre-notation ou photo = un objet | oui |
| `type` | TEXT, non nul | liste fermée, dont `annulation` | `annulation` seul ; reste : **Q3** |
| `valeur` | TEXT, nullable | JSON recopié au caractère près (sursync-v1 l. 20-21) | **non : Q1** |
| `remplace_uuid` | TEXT, nullable | ligne corrigée ou annulée | oui |
| `operateur` | TEXT, non nul | finalité : traçabilité, pas contrôle (S. l. 59) | oui |
| `appareil` | TEXT, non nul | 8 hex opaques, tirés une fois par installation (sursync-v1 l. 74) | oui |
| `sequence` | INTEGER, non nul | compteur local monotone | oui |
| `saisi_le_utc_ms` | INTEGER, non nul | ms Unix UTC | oui |
| `monotone_ms` | INTEGER, non nul | `elapsedRealtime` | oui |
| `heure_gnss_ms` | INTEGER, nullable | heure du dernier fix | **non : Q2** |
| `demarrage` | INTEGER, non nul | numéro de démarrage | **non : Q2** |
| `age_fix_ms` | INTEGER, nullable | âge du fix | **non : Q2** |
| `code_tournee` | TEXT, non nul, non vide | `saisir` refuse le vide (S. l. 47) ; contre-notation = autre tournée (deperis-v1 l. 159-160) | **non : Q2** |

Garanties :
- **Ajout seul** : DAO en `@Insert(ABORT)` et `SELECT` seulement. Déclencheurs `BEFORE UPDATE` et `BEFORE DELETE … RAISE(ABORT)`, recréés à chaque ouverture si absents (S. l. 46, R. l. 32).
- **Durabilité** : WAL, `synchronous=FULL`, une transaction par saisie (S. l. 35).
- **Migrations** : `exportSchema`, schémas dans `data/schemas/`, aucune migration destructive (S. l. 36).
- **Index** : sur l'ordre total (S. l. 48) et sur `remplace_uuid`.

### Les autres tables
- **`refus_journal`**, ajout seul : seconde version d'un `uuid` au contenu différent, motif `uuid_contenu_different` (sursync-v1 l. 44-46 et 63 ; deperis-v1 l. 20-22). Forme non écrite dans Surculus : **Q5**.
- **`appareil`**, une seule ligne : identifiant, prochaine séquence, dernier démarrage. Une restauration sur un téléphone effacé donne un nouvel identifiant ; les lignes restaurées gardent leur origine (sursync-v1 l. 95).
- **`sauvegardes`**, ajout seul : une ligne par fichier écrit. Sert à trois règles : rotation des 5 derniers fichiers par tournée et « jamais sans saisie nouvelle » (S. l. 50 et 55) ; envoi réussi inscrit (S. l. 51) ; compte des saisies jamais sorties (S. l. 53). Ligne du journal ou table à part : **Q6**.

### Calculé dans `:core`, sans Android
- **Ordre total** : comparateur (`saisi_le_utc_ms`, `appareil`, `sequence`, `uuid`) (S. l. 48).
- **Conflits** : même `remplace_uuid` sans que l'une remplace l'autre ; signalés, jamais résolus (**Q4**).
- **Projection** : état courant par (`code_tournee`, `objet_uuid`, `type`) (S. l. 47) (**Q7**).

## 2. `.ramsync` v1, écart par écart avec `sursync-v1`

**Repris sans changement :**
- gzip de JSON Lines UTF-8 ; en-tête, puis saisies dans l'ordre total, puis pied SHA-256 calculé sur les octets décompressés ;
- `valeur` recopiée au caractère près ;
- un lecteur N lit N-1 et N, refuse N+1 ;
- motifs : `version_illisible`, `fichier_tronque`, `ligne_illisible`, `empreinte_differente`, `ligne_autre_tournee`, `uuid_contenu_different`, cumulables ;
- un fichier abîmé n'est jamais refusé en entier ;
- restauration par union sur l'`uuid`, idempotente, sans écraser ;
- suffixes `-2`, `-3` pour un nom déjà pris ; 5 fichiers gardés par tournée ;
- jamais de sauvegarde sans saisie nouvelle ; fichier non chiffré.

| # | `sursync-v1` | `.ramsync` v1 proposé | Source |
|---|---|---|---|
| E1 | `"format": "sursync"` | `"format": "ramsync"` | R. l. 35 |
| E2 | `version_sommier` | `version_nemeton`, ou rien | deperis-v1 l. 82 ; **Q8** |
| E3 | `type` ∈ {controle, pointage, etat, annulation, saut_horloge} | liste de Ramulus | **Q3** |
| E4 | 13 champs | les mêmes si Q2 = oui | **Q2** |
| E5 | lignes à `code_tournee = ''` antérieures à la base v3 | sans objet (base née en v1, code vide refusé) | S. l. 46-47 |
| E6 | sauvegarde en fin de placette, constat, vide, intervention | fin de placette seulement, + 20 saisies / 5 min monotones | R. l. 34 ; fiche (provisoire) |
| E7 | `Documents/Surculus/`, `Pictures/Surculus/<code>/` | `Documents/Ramulus/` ; photos : 008 (ADR 0006 : `Documents/Ramulus/` + `.nomedia`, écart à Surculus signalé) | R. l. 34 ; ADR 0006 |
| E8 | lu par sommieR | lu par Ramulus seul, lisible avec gzip et JSON | deperis-v1 l. 68-69 |
| E9 | `$id` surculus | `$id` ramulus | — |
| E10 | `<code>-<appareil>-<horodatage>.sursync` | `<code>-<appareil>-<yyyyMMddTHHmmssZ>.ramsync`, code assaini, jamais de nom d'opérateur | S. l. 59 ; R. l. 36 |
| E11 | cas SV-01 à SV-55 | exemples `fixtures/ramsync/` (dev) ; acceptation par le `testeur` | fiche |

Le contenu de `valeur` par type reste opaque pour le format ; il sera fixé par 006 et 008 (**Q9**).

## 3. Dépendances reprises de Surculus (`gradle/libs.versions.toml`, 3c01f4b)

| Entrée | Version | Lignes |
|---|---|---|
| plugin `android-library` | 9.4.1 (déjà la nôtre) | l. 46 |
| plugin `ksp` | 2.3.12 | l. 7-9, 22, 49 |
| `room-runtime`, `room-ktx`, `room-compiler` | 2.8.1 | l. 10, 24, 64-66 |
| `room-testing` | 2.8.1 | l. 17, 80 |
| `robolectric` | 4.17 | l. 17, 40, 81 |
| `androidx-test-core` | 1.7.0 | l. 17, 41, 82 |
| `junit4` (pour Robolectric) | 4.13.2 | l. 17, 42, 83 |

- Déjà présents chez nous : coroutines, Gson, JUnit Jupiter.
- Non repris : sqlite-jdbc, desugar, DataStore.
- Chemin des schémas Room : `ksp { arg("room.schemaLocation", …) }` (**Q10**).

## 4. Tests (30)

**`:core`, JUnit 5 :**
- **UUID et ordre** :
  1. un UUID v7 est conforme et croissant ;
  2. l'ordre total ne dépend pas de l'ordre d'arrivée ;
  3. départage dans l'ordre : instant, appareil, séquence, uuid.
- **Conflits** :
  4. deux corrections du même `remplace_uuid` font un conflit, et les deux lignes restent ;
  5. une correction de correction n'est pas un conflit ;
  6. annulation et correction concurrentes : conflit (selon Q4).
- **Écriture et relecture** :
  7. l'aller-retour `.ramsync` redonne les lignes au caractère près ;
  8. un octet changé donne `empreinte_differente`, et tout reste lu ;
  9. un fichier tronqué donne `fichier_tronque`, et les lignes d'avant la coupure sont lues ;
  10. une ligne fautive donne `ligne_illisible`, et les autres sont lues ;
  11. une ligne d'une autre tournée donne `ligne_autre_tournee` ;
  12. même uuid, contenu différent : les deux sont refusées ; contenu identique : la seconde est ignorée.
- **Versions** :
  13. un fichier v2 lu par un lecteur v1 donne `version_future` ;
  14. un fichier v1 est lu par un lecteur v2 (test N-1) ;
  15. version invalide, fichier vide, fichier non gzip ou code vide dans l'en-tête donnent `version_illisible`.
- **Nom de fichier** :
  16. il suit le motif, avec le code assaini ;
  17. **aucun nom d'opérateur** n'y figure ;
  18. un nom déjà pris reçoit `-2`, puis `-3`.
- **Rotation et cadence** :
  19. la rotation se fait par tournée ;
  20. la cadence suit le premier des trois déclencheurs (fin de placette, 20 saisies, 5 minutes), jamais sans saisie nouvelle.
- **Fichiers d'exemple** :
  21. les fichiers d'exemple donnent les signalements attendus ;
  22. ils sont valides pour le schéma.

**`:data`, Robolectric et Room (Q11) :**
- **Ajout seul** :
  23. un UPDATE ou un DELETE direct échoue sur les trois tables en ajout seul ;
  24. le DAO n'a ni `@Update`, ni `@Delete`, ni requête UPDATE, DELETE ou REPLACE.
- **Base** :
  25. le journal est en `synchronous` FULL et en WAL ;
  26. un code vide est refusé, et la séquence croît strictement même après réouverture ;
  27. le schéma `1.json` est exporté, sans repli destructif.
- **Restauration** :
  28. union : une ligne identique est ignorée, une ligne différente part au refus, les lignes locales restent ;
  29. la restauration est idempotente ;
  30. aller-retour complet : saisir, sauvegarder, vider, restaurer redonne un journal identique.

**Sur le téléphone :** MediaStore `IS_PENDING` (avec repli `.part` sous API 26 à 28), par un test instrumenté, en dette si la CI n'a pas d'émulateur (**Q12**).

## 5. Étapes (après validation)

1. Créer la branche `mission/005-journal-et-sauvegarde` depuis `origin/main`.
2. Ajouter au catalogue les entrées sourcées, et le module `:data` à `settings.gradle.kts`.
3. `:core/journal` (`LigneJournal`, `UuidV7`, `OrdreTotal`, `Conflits`) : tests 1 à 6.
4. `:core/sauvegarde` (écrivain, lecteur, signalements, nom de fichier, rotation, cadence) : tests 7 à 20.
5. `docs/formats/ramsync-v1.md`, son schéma et `fixtures/ramsync/` (gzip reproductible) : tests 21 et 22.
6. `:data` (entités, DAO, base, `JournalRepository`, `SauvegardeMediaStore`) : tests 23 à 30.
7. Commande de tests verte, puis PR en français. Aucun écran.

## 6. Questions pour Pascal

- **Q1.** Notre ADR 0005 (l. 32) ne cite pas la colonne `valeur`, pourtant indispensable. Proposition : la reprendre de S. l. 46 et corriger l'ADR par un ajout.
- **Q2.** Reprend-on `heure_gnss_ms`, `demarrage`, `age_fix_ms` et `code_tournee` (S. l. 46) ? Proposition : oui. Si oui, reprend-on aussi la règle « Temps » (S. l. 49 : saut d'horloge de plus de 60 s noté par une ligne `saut_horloge`, jour civil en Europe/Paris) ?
- **Q3.** Quelle est la liste fermée des `type` ? Les sources ne donnent que `annulation` et les objets notation, contre-notation et photo. Faut-il ajouter `centre`, `placette_notee`, `saut_horloge` ?
- **Q4.** Un conflit, ce sont deux lignes ou plus avec le même `remplace_uuid`, sans que l'une remplace l'autre. Une annulation et une correction concurrentes forment-elles un conflit ? Lecture littérale de S. l. 48 : oui.
- **Q5.** Reprend-on la table `refus_journal`, avec les colonnes de `journal` plus `motif` et `recu_le_utc_ms` ?
- **Q6.** Les envois réussis (S. l. 51) et la réponse « copié / pas copié » (S. l. 53) : lignes du journal, ou table `sauvegardes` à part ?
- **Q7.** La projection de l'état courant (S. l. 47) : dans 005, ou dans 006 et 009 ?
- **Q8.** Que met-on dans l'en-tête du `.ramsync` à la place de `version_sommier` : `version_nemeton`, rien, ou aussi `version_ramulus` ?
- **Q9.** La `valeur` d'une notation porte-t-elle la classe ? Proposition : MB et le second critère seulement, la classe recalculée par l'abaque, et ce point fixé par 006.
- **Q10.** Le `dev` peut-il lire `surculus/data/build.gradle.kts` et `FormatSursync.kt` (hors sources autorisées), pour le réglage KSP et Room et le choix de Gson ?
- **Q11.** Les tests de `:data` ne sont pas joués par `.claude/test-command`. Il faut y ajouter `:data:testDebugUnitTest`, ce que fait l'orchestrateur.
- **Q12.** Les trois voies hors du téléphone (S. l. 51), le signal à l'ouverture de journée (S. l. 53) et le test MediaStore instrumenté sortent-ils de 005 ? Proposition : oui, chacun dans une mission à venir.
- **Q13.** L'échantillon terrain demande un écran et un bouton « restaurer », qui relèvent de 006. Ajoute-t-on un point d'entrée minimal dans 005, ou reporte-t-on l'échantillon après 006, le test 30 tenant lieu de preuve en attendant ?
- **Q14.** `dataExtractionRules` (S. l. 58) n'est pas repris par notre ADR 0005 ; le manifeste n'a que `allowBackup="false"`. C'est noté, hors de 005.
