package io.github.pobsteta.ramulus.data.journal

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base du journal (ADR 0005 ; Surculus ADR 0005, l. 35-36 et 46) :
 * - WAL et `synchronous=FULL` : une saisie validée est durable, même si la batterie lâche ;
 * - `exportSchema = true`, schémas dans `data/schemas/` ; **aucun repli destructif** : une migration absente ou une
 *   base plus récente fait échouer l'ouverture, jamais effacer le journal ;
 * - trois tables en ajout seul (`journal`, `refus_journal`, `sauvegardes`), gardées par des déclencheurs
 *   `BEFORE UPDATE`, `BEFORE DELETE` et `BEFORE INSERT` (contre `INSERT OR REPLACE`), recréés à chaque ouverture
 *   s'ils manquent.
 */
@Database(
    entities = [LigneEntity::class, RefusEntity::class, SauvegardeEntity::class, AppareilEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RamulusDatabase : RoomDatabase() {
    abstract fun journal(): JournalDao
    abstract fun refus(): RefusJournalDao
    abstract fun sauvegardes(): SauvegardeDao
    abstract fun appareil(): AppareilDao

    companion object {
        const val NOM = "ramulus.db"

        /** Tables en ajout seul, avec leur clé primaire. */
        val TABLES_AJOUT_SEUL = mapOf("journal" to "uuid", "refus_journal" to "id", "sauvegardes" to "id")

        /** Ouvre la base. `nom` nul : base en mémoire (tests). */
        fun ouvrir(context: Context, nom: String? = NOM, constructeur: Builder<RamulusDatabase>.() -> Unit = {}): RamulusDatabase {
            val b = if (nom == null) Room.inMemoryDatabaseBuilder(context, RamulusDatabase::class.java)
            else Room.databaseBuilder(context, RamulusDatabase::class.java, nom)
            return b.setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(Garde)
                .apply(constructeur)
                .build()
        }

        /** Déclencheurs d'ajout seul (mise à jour, suppression, remplacement) et `synchronous=FULL`, à chaque ouverture. */
        internal object Garde : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                // En WAL, Android tient plusieurs connexions : une instruction en lecture seule, comme ce PRAGMA, peut
                // partir sur une connexion de lecture. À partir de l'API 30, execPerConnectionSQL le pose sur toutes,
                // présentes et à venir ; avant, il est posé au mieux, Android appliquant par ailleurs son réglage WAL
                // (db_wal_sync_mode) à chaque connexion : à vérifier sur appareil (test instrumenté, plan 005, Q12).
                if (db.isExecPerConnectionSQLSupported) {
                    db.execPerConnectionSQL("PRAGMA synchronous = FULL", null)
                } else {
                    db.query("PRAGMA synchronous = FULL").use { it.moveToFirst() }
                }
                for ((t, cle) in TABLES_AJOUT_SEUL) {
                    db.execSQL(
                        "CREATE TRIGGER IF NOT EXISTS ${t}_sans_mise_a_jour BEFORE UPDATE ON $t " +
                            "BEGIN SELECT RAISE(ABORT, '$t : table en ajout seul, mise à jour refusée'); END",
                    )
                    db.execSQL(
                        "CREATE TRIGGER IF NOT EXISTS ${t}_sans_suppression BEFORE DELETE ON $t " +
                            "BEGIN SELECT RAISE(ABORT, '$t : table en ajout seul, suppression refusée'); END",
                    )
                    // INSERT OR REPLACE efface la ligne en conflit sans déclencher BEFORE DELETE (sauf
                    // recursive_triggers) : une insertion sur une clé déjà présente est refusée avant toute résolution.
                    db.execSQL(
                        "CREATE TRIGGER IF NOT EXISTS ${t}_sans_remplacement BEFORE INSERT ON $t " +
                            "WHEN EXISTS (SELECT 1 FROM $t WHERE $cle = NEW.$cle) " +
                            "BEGIN SELECT RAISE(ABORT, '$t : table en ajout seul, remplacement refusé'); END",
                    )
                }
            }
        }
    }
}
