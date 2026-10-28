# Format `.ramsync`, version 1

Sauvegarde du journal des saisies d'une tournée de Ramulus ([ADR 0005](../adr/0005-stack-et-reprises.md)). Format
ouvert : n'importe quel outil qui sait lire du gzip et du JSON peut le lire (test du successeur). Seul Ramulus le
relit : il n'est pas un format d'échange avec Nemeton (c'est `deperis.gpkg`, [deperis-v1](deperis-v1.md)).

Repris **ligne à ligne** de `sursync-v1` de Surculus (`docs/formats/sursync-v1.md`, commit 3c01f4b) ; les écarts
sont listés plus bas, chacun avec sa source. Arbitrages de Pascal sur le plan de la mission 005 (2026-10-28, Q1 à
Q14) : cités « Q… ». Schéma : [`ramsync-v1.schema.json`](ramsync-v1.schema.json). Exemples :
[`fixtures/ramsync/`](../../fixtures/ramsync/README.md).

## Structure

Un fichier gzip dont le contenu est du **JSON Lines** (UTF-8, une ligne JSON par ligne, chaque ligne terminée par
`\n`) :

1. **En-tête** : `format` (`"ramsync"`), `version` (entier, 1 ici), `code_tournee`, `version_nemeton` (version de
   Nemeton qui a préparé la tournée, `tournee.version_nemeton` de deperis-v1, ou `null` si elle n'est pas connue),
   `version_ramulus` (version de l'application qui écrit) (Q8), `appareil` (identifiant **opaque** de l'appareil qui
   écrit), `cree_le_utc_ms`, `nb_lignes`.
2. **Saisies**, dans l'ordre total du journal (`saisi_le_utc_ms`, `appareil`, `sequence`, `uuid`) : les treize champs
   d'une ligne du journal, tels quels (voir « Saisie »). La tournée de la ligne est celle de l'en-tête : un fichier
   est celui d'**une seule tournée**. Le champ `code_tournee` d'une saisie est **facultatif** : l'application ne
   l'écrit pas ; absent, la ligne prend le code de l'en-tête ; présent et différent de celui de l'en-tête, la ligne
   est refusée (`ligne_autre_tournee`). Un en-tête dont `code_tournee` est vide n'est pas lu (`version_illisible`).
3. **Pied** : `{"sha256": "<64 chiffres hexadécimaux minuscules>"}`, SHA-256 des octets **décompressés** de toutes
   les lignes précédentes (en-tête et saisies, fins de ligne `\n` comprises).

Le journal est en ajout seul : le fichier contient toutes les lignes, y compris les annulations et les lignes
`saut_horloge`, sans les interpréter. L'état courant se recalcule depuis les lignes (projection de `:core`, Q7).

## Saisie

| Champ | Type | Règle |
|---|---|---|
| `uuid` | texte | UUID v7 en minuscules (RFC 9562) quand Ramulus l'écrit |
| `objet_uuid` | texte | l'objet saisi : une notation, une contre-notation, une photo = un objet (ADR 0005) |
| `type` | texte | liste fermée (Q3) : `notation`, `contre_notation`, `photo`, `centre`, `placette_notee`, `annulation`, `saut_horloge` |
| `valeur` | texte ou `null` | **texte** JSON de la valeur, recopié au caractère près (c'est ce qui garantit une restauration « à la ligne près ») ; opaque pour ce format, son contenu par type est fixé par les missions qui saisissent (006, 008) ; une notation n'y porte pas de classe, qui se calcule par l'abaque (Q1, Q9) |
| `remplace_uuid` | texte ou `null` | la ligne corrigée ou annulée |
| `operateur` | texte | traçabilité des saisies, non contrôle de l'agent |
| `appareil` | texte | appareil d'origine de la saisie, jamais réécrit |
| `sequence` | entier | compteur local monotone de l'appareil d'origine |
| `saisi_le_utc_ms` | entier | heure du téléphone |
| `heure_gnss_ms` | entier ou `null` | heure du dernier fix GNSS, s'il est connu (Q2) |
| `monotone_ms` | entier | `SystemClock.elapsedRealtime`, valable dans un même démarrage |
| `demarrage` | entier | numéro de démarrage du téléphone (Q2) |
| `age_fix_ms` | entier ou `null` | âge du fix au moment de la saisie, sur l'horloge monotone ; nul si aucun fix (Q2) |

Les instants sont des **millisecondes depuis l'époque Unix, UTC** (suffixe `_ms`).

Deux lignes ou plus qui remplacent la même ligne (deux corrections, ou une annulation et une correction : Q4) sont un
**conflit** : signalé, jamais résolu ; toutes les lignes restent.

## Version

La version courante est 1. Un lecteur de version *N* lit les versions *N*-1 et *N* et **refuse** les autres :

| Fichier | Résultat |
|---|---|
| version *N*+1 ou plus | refusé en entier, motif `version_future`, message « mettre l'application à jour » |
| version absente, non entière, nulle ou négative, en-tête illisible (champ absent ou mal typé, `format` autre que `ramsync`), fichier de 0 octet ou non gzip | refusé en entier, motif `version_illisible` |
| version inférieure à *N*-1 | refusé, motif `version_illisible` |

Il n'existe pas de version 0 : le format v1 est le premier. La lecture de *N*-1 se teste avec un fichier v1 lu par
un lecteur de version 2 (paramètre de test). C'est l'inverse de `deperis.gpkg`, qui lit N+1 : le `.ramsync` est une
sauvegarde que seul Ramulus relit.

## Limites de la version 1

- **Seules les saisies sont sauvegardées**, pas les lignes refusées du journal (`refus_journal` : la seconde version
  d'un `uuid` reçue avec un contenu différent). Une restauration redonne donc les mêmes saisies, mais pas l'anomalie
  `uuid_contenu_different` d'un journal qui en portait une (comme `sursync-v1`).
- **Une tournée par fichier** : le journal porte la tournée de chaque ligne ; la sauvegarde ne contient que les lignes
  de la tournée de l'en-tête. Une contre-notation est une autre tournée (deperis-v1) : elle a ses propres fichiers.

## Un fichier abîmé n'est pas refusé en entier

Seuls les refus ci-dessus rejettent le fichier. Sinon les lignes saines sont restaurées et chaque défaut est
signalé (codes lus par les tests ; les messages affichés ne le sont pas) :

| Défaut | Signalement | Effet |
|---|---|---|
| gzip coupé, ou pied absent | `fichier_tronque` | lignes lisibles en entier avant la coupure restaurées |
| ligne JSON illisible ou incomplète (champ absent ou mal typé, type hors liste) | `ligne_illisible` | ligne refusée, les autres restaurées |
| pied présent mais faux | `empreinte_differente` | toutes les lignes lisibles restaurées, avertissement visible |
| ligne dont `code_tournee` est un autre code que celui de l'en-tête | `ligne_autre_tournee` | ligne refusée (jamais rattachée à la tournée du fichier), les autres restaurées |
| `code_tournee` d'une ligne qui n'est pas un texte | `ligne_illisible` | ligne refusée, les autres restaurées |
| même `uuid` deux fois, contenu différent | `uuid_contenu_different` | les deux versions refusées, les autres restaurées |
| même `uuid` deux fois, à l'identique | aucun | la seconde est ignorée |

Les signalements se cumulent (un fichier tronqué n'a plus de pied et signale aussi `fichier_tronque`).

## Nom du fichier

`<code-tournée>-<appareil>-<horodatage>.ramsync`, par exemple `EXEMPLE-2027-01-a3f9c2d1-20270706T080000Z.ramsync`.

- `<code-tournée>` : le code de la tournée, assaini (accents retirés, tout caractère hors lettres, chiffres, `.`,
  `_`, `-` remplacé par `_`) ; **jamais de nom de propriétaire ni d'opérateur** ;
- `<appareil>` : identifiant **opaque** de 8 caractères hexadécimaux, tiré une fois par installation ;
- `<horodatage>` : `yyyyMMddTHHmmssZ`, UTC, **sans `:`**.

Un nom déjà pris n'est **jamais écrasé** : le nouveau reçoit un suffixe (`...Z-2.ramsync`, `-3`...). On garde les
5 dernières sauvegardes **de chaque tournée** ; la sixième supprime la plus ancienne de la même tournée, jamais
celle d'une autre.

## Quand et où

- Écrit sous `Documents/Ramulus/` du stockage partagé par MediaStore avec `IS_PENDING` (fichier invisible tant
  qu'il n'est pas complet). Android 8 et 9 : dossier public `Documents/Ramulus/` (permission
  `WRITE_EXTERNAL_STORAGE`, limitée à l'API 28), nom `.part` renommé une fois complet.
- Déclenché à chaque fin de placette, **et** toutes les **20 saisies** ou les **5 minutes** (horloge monotone), au
  premier des deux. **Jamais sans saisie nouvelle** depuis la dernière sauvegarde de la tournée. Ces deux nombres
  sont **provisoires** (repris de Surculus, non mesurés pour Ramulus) : Pascal les mesure à l'essai d'une tournée
  complète, gel avant la mission 009 (fiche 005).
- Chaque fichier écrit est inscrit à la table `sauvegardes` du journal (en ajout seul, Q6).
- Les trois voies hors de l'appareil (carte SD ou clé USB, téléphone du binôme, réseau) et le signal à l'ouverture
  de la journée suivront dans leurs missions (Q12) ; les photos, dans la mission 008 (ADR 0006).

## Restauration

**Union sur l'`uuid`** dans le journal local, jamais d'écrasement : les lignes locales absentes du fichier restent,
une ligne déjà présente à l'identique est ignorée, une ligne présente avec un autre contenu est gardée à la table
`refus_journal` (motif `uuid_contenu_different`, une seule fois) et la ligne locale reste ; la restauration est
idempotente. Les lignes gardent leur `appareil` et leur `sequence` d'origine, et prennent la tournée de l'en-tête du
fichier : la restauration d'une tournée ne touche à aucune ligne d'une autre. Un conflit (deux corrections
concurrentes d'une même ligne) reste un conflit. Un fichier refusé en entier ne touche à rien.

## Confidentialité

Non chiffré, pour que le successeur le lise. Il porte l'`operateur` de chaque saisie et les horodatages
(traçabilité des saisies, non contrôle de l'agent : ADR 0005 ; Surculus ADR 0005, l. 59).

## Décisions

Décisions de Pascal du 2026-10-28, à la relecture de la mission 005 (aller-retour 1/3,
[fiche 005](../../pilotage/missions/005-journal-et-sauvegarde.md)) : ces choix du `dev`, que ni Surculus ADR 0005 ni
notre ADR 0005 ne tranchaient, sont validés tels que le code les applique. Ils sont cités « C… ».

| # | Décision |
|---|---|
| C1 | Une annulation retire la ligne visée sans faire revenir la version remplacée ; l'annulation d'une annulation ne fait rien revenir non plus. |
| C2 | Deux lignes du même objet, sans remplacement entre elles, forment un **conflit** (signalé, jamais résolu, comme ci-dessus). |
| C3 | Un `remplace_uuid` qui vise une ligne d'une autre tournée est sans effet. |
| C4 | La ligne `saut_horloge` a une `valeur` nulle ; son `objet_uuid` est la ligne elle-même ; elle porte l'`operateur` et la tournée de la saisie qui a révélé le saut. |
| C5 | Avant la première sauvegarde d'une tournée, le repère des 5 minutes est la première saisie ; après un redémarrage du téléphone, la sauvegarde est due tout de suite. |
| C6 | La table `sauvegardes` a une colonne `voie` ; la table `appareil` n'a pas de colonne « dernier démarrage ». |
| C7 | Dans l'en-tête, `version_ramulus` est obligatoire et `version_nemeton` peut être nulle ; restent aussi les gardes contre un `uuid` ou un `objet_uuid` vide et contre une annulation sans `remplace_uuid`. |

## Écarts à `sursync-v1`

| # | `sursync-v1` | `ramsync-v1` | Source |
|---|---|---|---|
| E1 | `"format": "sursync"` | `"format": "ramsync"` | ADR 0005 (format `.ramsync`) |
| E2 | `version_sommier` | `version_nemeton` et `version_ramulus` | Q8 ; deperis-v1, table `tournee` |
| E3 | `type` ∈ {`controle`, `pointage`, `etat`, `annulation`, `saut_horloge`} | liste de Ramulus | Q3 |
| E4 | treize champs | les mêmes | Q1, Q2 |
| E5 | lignes à `code_tournee = ''` antérieures à la base v3, absentes des sauvegardes | sans objet : la base de Ramulus naît en v1 et refuse un code vide | Surculus ADR 0005, l. 46-47 |
| E6 | sauvegarde en fin de placette, de constat, de vide, d'intervention | en fin de placette, + 20 saisies ou 5 minutes | ADR 0005 ; fiche 005 (provisoire) |
| E7 | `Documents/Surculus/`, photos sous `Pictures/Surculus/<code>/` | `Documents/Ramulus/` ; photos : mission 008 | ADR 0005 ; ADR 0006 |
| E8 | lu par sommieR | lu par Ramulus seul, lisible avec gzip et JSON | deperis-v1, « Version » |
| E9 | `$id` de Surculus | `$id` de Ramulus | — |
| E10 | `.sursync` | `.ramsync` ; jamais de nom d'opérateur | ADR 0005 (confidentialité) |
| E11 | cas SV-01 à SV-55 | exemples `fixtures/ramsync/` ; acceptation par le `testeur` à venir | fiche 005 |
