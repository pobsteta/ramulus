# Exemples du format `.ramsync` v1

Format : [`docs/formats/ramsync-v1.md`](../../docs/formats/ramsync-v1.md) ; schéma :
[`ramsync-v1.schema.json`](../../docs/formats/ramsync-v1.schema.json). Exemples écrits par le `dev` (mission 005) ;
les cas d'acceptation du `testeur` viendront à côté.

- `generer.py` écrit tous les fichiers, avec la bibliothèque standard de Python (gzip, json, hashlib) : un `.ramsync`
  se lit et s'écrit sans Ramulus. Le gzip est reproductible (date nulle) : `python3 fixtures/ramsync/generer.py`
  redonne les mêmes octets.
- `attendus.json` : ce que doit conclure un lecteur de version 1 sur chaque fichier (motif de refus, signalements,
  lignes lues, refusées, ignorées). Rejoué par `FixturesRamsyncTest` (`:core`).
- `exemple-v1.jsonl` : le contenu décompressé de `exemple-v1.ramsync`, lisible tel quel.

| Fichier | Ce qu'il montre | Attendu |
|---|---|---|
| `exemple-v1.ramsync` | huit saisies d'une tournée : centre (avec fix GNSS), notation, photo, correction, annulation, saisie venue d'un autre appareil, saut d'horloge d'une heure, placette notée | lu, sans signalement |
| `exemple-v1-tronque.ramsync` | gzip coupé (60 derniers octets) | `fichier_tronque`, les 7 lignes d'avant la coupure lues |
| `exemple-v1-empreinte-fausse.ramsync` | un octet changé, pied d'origine | `empreinte_differente`, les 8 lignes lues |
| `exemple-v1-ligne-illisible.ramsync` | une ligne coupée, une ligne au type hors liste | `ligne_illisible`, 6 lues, 2 refusées |
| `exemple-v1-autre-tournee.ramsync` | une ligne qui porte un autre `code_tournee` | `ligne_autre_tournee`, 7 lues, 1 refusée |
| `exemple-v1-uuid-double.ramsync` | même `uuid` à l'identique, et au contenu différent | `uuid_contenu_different`, la copie identique ignorée, les deux versions différentes refusées |
| `exemple-v2.ramsync` | version 2 | refusé en entier, `version_future` |
| `exemple-vide.ramsync` | 0 octet | refusé en entier, `version_illisible` |

## Données

- Tournée `EXEMPLE-2027-01` (le code des cas de `fixtures/echange/`), le 6 juillet 2027 à partir de 07:00 UTC.
- Opérateurs `operateur-A` et `operateur-B` : identifiants d'exemple, sans nom d'agent. Appareils `a3f9c2d1` et
  `0b5e11aa`, opaques.
- Les `valeur` sont des textes JSON **d'exemple** (`{"exemple":1}`, et une chaîne accentuée avec guillemets pour
  l'aller-retour au caractère près) : leur contenu par type sera fixé par les missions 006 et 008 ; une notation n'y
  portera pas de classe (plan 005, Q9).
