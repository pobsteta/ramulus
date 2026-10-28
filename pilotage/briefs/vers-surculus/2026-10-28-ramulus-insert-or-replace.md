# Brief de Ramulus vers Surculus — `INSERT OR REPLACE` contourne l'ajout seul

- **Émetteur :** Ramulus (orchestrateur), trouvé par le `dev` de la mission 005, confirmé par le relecteur, 2026-10-28
- **Cible :** Surculus, journal Room en ajout seul (ADR 0005, l. 46 : « aucune mise à jour ni suppression (DAO et
  déclencheurs) »)

## Constat
Avec le réglage par défaut de SQLite (`recursive_triggers` à OFF), un `INSERT OR REPLACE` (ou `REPLACE`) sur une clé
déjà présente **efface la ligne existante sans passer par le déclencheur `BEFORE DELETE`**. Un journal protégé
seulement par `BEFORE UPDATE` et `BEFORE DELETE` peut donc perdre une ligne. Ramulus l'a vérifié avec `sqlite3`, puis
sous Robolectric par un test délibérément cassé.

Ramulus n'a **pas** lu le code de Surculus : le trou n'y est pas constaté, seulement possible.

## Correction de Ramulus
Un déclencheur `BEFORE INSERT … WHEN EXISTS (SELECT 1 … WHERE uuid = NEW.uuid) BEGIN SELECT RAISE(ABORT, …); END`
sur chaque table en ajout seul, recréé à chaque ouverture. Un test le prouve avec `recursive_triggers` à OFF.
Ramulus : `data/src/main/kotlin/io/github/pobsteta/ramulus/data/journal/RamulusDatabase.kt` (PR n° 11).

## Demande
Vérifier si les déclencheurs du journal de Surculus couvrent `INSERT OR REPLACE`, et, sinon, ajouter la même garde
et son test.
