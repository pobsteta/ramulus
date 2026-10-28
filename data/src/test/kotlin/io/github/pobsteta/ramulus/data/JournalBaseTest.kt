package io.github.pobsteta.ramulus.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.pobsteta.ramulus.core.journal.TypeLigne
import io.github.pobsteta.ramulus.core.journal.UuidV7
import io.github.pobsteta.ramulus.data.journal.JournalDao
import io.github.pobsteta.ramulus.data.journal.JournalRepository
import io.github.pobsteta.ramulus.data.journal.LigneEntity
import io.github.pobsteta.ramulus.data.journal.RamulusDatabase
import io.github.pobsteta.ramulus.data.journal.RefusJournalDao
import io.github.pobsteta.ramulus.data.journal.SauvegardeDao
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class JournalBaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nom = "journal-test.db"
    private lateinit var db: RamulusDatabase
    private val horloges = HorlogesFactices()

    @Before
    fun ouvrir() {
        context.deleteDatabase(nom)
        db = RamulusDatabase.ouvrir(context, nom) { allowMainThreadQueries() }
    }

    @After
    fun fermer() {
        db.close()
        context.deleteDatabase(nom)
    }

    private fun repo() = JournalRepository(db, horloges)

    private fun echoue(sql: String) {
        try {
            db.openHelper.writableDatabase.execSQL(sql)
            fail("aurait dû échouer : $sql")
        } catch (e: SQLiteException) {
            // attendu : déclencheur RAISE(ABORT)
        }
    }

    // 23
    @Test
    fun `une mise a jour ou une suppression directe echoue sur les trois tables en ajout seul`() {
        val r = repo()
        val l = r.saisir("T1", "OBJ", TypeLigne.NOTATION, "{\"mb\":1}", "op1")
        val sauvegarde = io.github.pobsteta.ramulus.data.sauvegarde.SauvegardeRepository(
            db, r, io.github.pobsteta.ramulus.data.sauvegarde.DestinationDossier(File(context.cacheDir, "sv23")),
            horloges, "0.1.0",
        )
        assertNotNull(sauvegarde.sauvegarder("T1", null))
        // Une restauration d'une ligne au même uuid et au contenu différent remplit refus_journal.
        val autre = l.copy(valeur = "{\"mb\":2}")
        val octets = io.github.pobsteta.ramulus.core.sauvegarde.EcritureRamsync.ecrire(
            "T1", null, "0.1.0", "0000beef", 0, listOf(autre),
        )
        assertEquals(1, r.restaurer(octets).refusees)

        val avant = mapOf(
            "journal" to r.toutes(), "refus_journal" to r.refus(), "sauvegardes" to db.sauvegardes().toutes(),
        )
        for ((t, cle) in RamulusDatabase.TABLES_AJOUT_SEUL) {
            echoue("UPDATE $t SET code_tournee = 'X'")
            echoue("DELETE FROM $t")
            echoue("INSERT OR REPLACE INTO $t SELECT * FROM $t")
            echoue("REPLACE INTO $t SELECT * FROM $t WHERE $cle IS NOT NULL")
        }
        val remplacement = "INSERT OR REPLACE INTO journal (uuid, objet_uuid, type, valeur, operateur, appareil, " +
            "sequence, saisi_le_utc_ms, monotone_ms, demarrage, code_tournee) " +
            "VALUES ('${l.uuid}', 'OBJ', 'notation', '{\"mb\":5}', 'op1', 'a0000000', 9, 0, 0, 1, 'T1')"
        echoue(remplacement)
        // Sans recursive_triggers (réglage par défaut de SQLite), REPLACE efface la ligne en conflit sans déclencher
        // BEFORE DELETE : seul le déclencheur BEFORE INSERT l'arrête. On le vérifie sur la connexion qui écrit.
        val w = db.openHelper.writableDatabase
        w.beginTransaction()
        try {
            w.execSQL("PRAGMA recursive_triggers = OFF")
            w.query("PRAGMA recursive_triggers").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            for ((t, _) in RamulusDatabase.TABLES_AJOUT_SEUL) {
                try {
                    w.execSQL("INSERT OR REPLACE INTO $t SELECT * FROM $t")
                    fail("$t : un remplacement aurait dû échouer")
                } catch (e: SQLiteException) {
                    assertTrue(e.message, e.message!!.contains("remplacement refusé"))
                }
            }
        } finally {
            w.endTransaction()
        }
        assertEquals(avant["journal"], r.toutes())
        assertEquals(listOf(l), r.toutes())
        assertEquals(avant["refus_journal"], r.refus())
        assertEquals(1, r.refus().size)
        assertEquals(avant["sauvegardes"], db.sauvegardes().toutes())
        assertEquals(1, db.sauvegardes().toutes().size)

        // Les déclencheurs reviennent à la réouverture s'ils ont disparu.
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER journal_sans_suppression")
        db.close()
        db = RamulusDatabase.ouvrir(context, nom) { allowMainThreadQueries() }
        echoue("DELETE FROM journal")
        assertEquals(1, repo().toutes().size)
    }

    // 24
    @Test
    fun `les DAO en ajout seul n ont ni Update, ni Delete, ni requete UPDATE, DELETE ou REPLACE`() {
        for (dao in listOf(JournalDao::class.java, RefusJournalDao::class.java, SauvegardeDao::class.java)) {
            for (m in dao.declaredMethods) {
                assertFalse("${dao.simpleName}.${m.name}", m.isAnnotationPresent(Update::class.java))
                assertFalse("${dao.simpleName}.${m.name}", m.isAnnotationPresent(Delete::class.java))
                assertFalse("${dao.simpleName}.${m.name}", m.isAnnotationPresent(Upsert::class.java))
                val sql = m.getAnnotation(Query::class.java)?.value?.uppercase() ?: continue
                for (mot in listOf("UPDATE", "DELETE", "REPLACE", "INSERT", "DROP")) {
                    assertFalse("${dao.simpleName}.${m.name} : $sql", Regex("\\b$mot\\b").containsMatchIn(sql))
                }
            }
        }
        // Et l'insertion n'écrase pas : un uuid déjà présent est refusé.
        val l = repo().saisir("T1", "OBJ", TypeLigne.NOTATION, "{}", "op1")
        try {
            db.journal().inserer(LigneEntity.de(l.copy(valeur = "{\"autre\":1}")))
            fail("un uuid déjà présent ne s'écrase pas")
        } catch (e: SQLiteConstraintException) {
            // attendu
        }
        assertEquals("{}", repo().toutes().single().valeur)
    }

    // 25
    @Test
    fun `le journal est en synchronous FULL et en WAL`() {
        val base = db.openHelper.writableDatabase
        repo().saisir("T1", "OBJ", TypeLigne.NOTATION, "{}", "op1")
        verifierSynchronousFullEtWal(base)
    }

    // 26
    @Test
    fun `un code vide est refuse, et la sequence croit strictement meme apres reouverture`() {
        val r = repo()
        for (code in listOf("", "  ")) {
            assertTrue(runCatching { r.saisir(code, "OBJ", TypeLigne.NOTATION, "{}", "op1") }.isFailure)
        }
        assertTrue("saut_horloge n'est écrit que par le journal",
            runCatching { r.saisir("T1", "OBJ", TypeLigne.SAUT_HORLOGE, null, "op1") }.isFailure)
        assertTrue("type hors liste", runCatching { r.saisir("T1", "OBJ", "classe", "{}", "op1") }.isFailure)
        assertTrue("annulation sans cible", runCatching { r.saisir("T1", "OBJ", TypeLigne.ANNULATION, null, "op1") }.isFailure)
        assertTrue(r.toutes().isEmpty())

        val avant = (1..3).map { horloges.avancer(1_000); r.saisir("T1", "OBJ$it", TypeLigne.NOTATION, "{}", "op1") }
        db.close()
        db = RamulusDatabase.ouvrir(context, nom) { allowMainThreadQueries() }
        val r2 = repo()
        val apres = (4..5).map { horloges.avancer(1_000); r2.saisir("T1", "OBJ$it", TypeLigne.NOTATION, "{}", "op1") }
        val sequences = (avant + apres).map { it.sequence }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), sequences)
        assertEquals(1, (avant + apres).map { it.appareil }.toSet().size)
        assertTrue(Regex("^[0-9a-f]{8}$").matches(avant[0].appareil))
        assertTrue((avant + apres).all { UuidV7.estValide(it.uuid) })
    }

    @Test
    fun `un saut d horloge de plus de 60 s est note par une ligne saut_horloge, l heure brute gardee`() {
        val r = repo()
        r.saisir("T1", "A", TypeLigne.NOTATION, "{}", "op1")
        horloges.avancer(30_000)
        r.saisir("T1", "B", TypeLigne.NOTATION, "{}", "op1")
        // L'heure du téléphone recule de deux heures, l'horloge monotone avance de 10 s.
        horloges.utc -= 7_200_000
        horloges.mono += 10_000
        val c = r.saisir("T1", "C", TypeLigne.NOTATION, "{}", "op1")
        val toutes = r.toutes().sortedBy { it.sequence }
        assertEquals(listOf("notation", "notation", "saut_horloge", "notation"), toutes.map { it.type })
        assertEquals(horloges.utc, c.saisiLeUtcMs)
        assertEquals(toutes[2].sequence + 1, c.sequence)
        // Nouveau démarrage : rien à comparer, pas de saut.
        horloges.dem = 2
        horloges.mono = 5_000
        horloges.utc += 86_400_000
        r.saisir("T1", "D", TypeLigne.NOTATION, "{}", "op1")
        assertEquals(1, r.toutes().count { it.type == TypeLigne.SAUT_HORLOGE })
    }

    // 27
    @Test
    fun `le schema 1 est exporte et une base plus recente n est jamais effacee`() {
        val schema = File("schemas/io.github.pobsteta.ramulus.data.journal.RamulusDatabase/1.json")
        assertTrue("schéma exporté : ${schema.absolutePath}", schema.isFile)
        val texte = schema.readText()
        for (t in listOf("journal", "refus_journal", "sauvegardes", "appareil")) {
            assertTrue(t, texte.contains("\"tableName\": \"$t\""))
        }
        // Une base de version 2 (venue d'une version plus récente de l'application) : l'ouverture échoue,
        // le journal reste.
        repo().saisir("T1", "OBJ", TypeLigne.NOTATION, "{}", "op1")
        db.openHelper.writableDatabase.execSQL("PRAGMA user_version = 2")
        db.close()
        val reouverte = RamulusDatabase.ouvrir(context, nom) { allowMainThreadQueries() }
        try {
            reouverte.openHelper.writableDatabase
            fail("une base plus récente ne s'ouvre pas, et ne s'efface pas")
        } catch (e: IllegalStateException) {
            // attendu : pas de repli destructif
        } finally {
            reouverte.close()
        }
        val brute = android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath(nom).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        )
        brute.rawQuery("SELECT COUNT(*) FROM journal", null).use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        brute.close()
        db = RamulusDatabase.ouvrir(context, "autre-$nom") { allowMainThreadQueries() }
    }
}

/**
 * Test 25 : `synchronous=FULL` (2) sur la connexion qui écrit et, si [autresConnexions], sur les autres ; le journal
 * en WAL. Avant l'API 30, les connexions de lecture gardent le réglage WAL d'Android : elles ne sont pas vérifiées.
 */
internal fun verifierSynchronousFullEtWal(base: SupportSQLiteDatabase, autresConnexions: Boolean = true) {
    // Dans une transaction : la connexion qui écrit. Hors transaction : une connexion de lecture, s'il y en a.
    base.beginTransaction()
    try {
        base.query("PRAGMA synchronous").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("synchronous = FULL (2) sur la connexion qui écrit", 2, c.getInt(0))
        }
    } finally {
        base.endTransaction()
    }
    if (autresConnexions) {
        base.query("PRAGMA synchronous").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("synchronous = FULL (2) sur les autres connexions", 2, c.getInt(0))
        }
    }
    base.query("PRAGMA journal_mode").use { c ->
        assertTrue(c.moveToFirst())
        assertEquals("wal", c.getString(0).lowercase())
    }
}
