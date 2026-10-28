package io.github.pobsteta.ramulus.data

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.github.pobsteta.ramulus.core.journal.TypeLigne
import io.github.pobsteta.ramulus.data.journal.JournalRepository
import io.github.pobsteta.ramulus.data.journal.RamulusDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test 25 rejoué avant l'API 30 (relecture 005, aller-retour 1/3, S1) : sans `execPerConnectionSQL`, `synchronous=FULL`
 * doit tout de même tenir sur la connexion qui écrit. Sous Robolectric en SDK 28, une connexion WAL s'ouvre en
 * `synchronous=0` : le test échoue si le PRAGMA n'atteint pas la connexion qui écrit. Les connexions de lecture, qui
 * gardent le réglage WAL d'Android, ne sont pas vérifiées ici. Les tests de [JournalBaseTest] tournent en SDK 36.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JournalBaseSdk28Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nom = "journal-test-sdk28.db"
    private lateinit var db: RamulusDatabase

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

    // 25, en SDK 28
    @Test
    fun `avant l'API 30 le journal est en synchronous FULL et en WAL`() {
        assertEquals(28, Build.VERSION.SDK_INT)
        val base = db.openHelper.writableDatabase
        assertFalse("SDK 28 : le chemin sans execPerConnectionSQL est bien celui testé", base.isExecPerConnectionSQLSupported)
        JournalRepository(db, HorlogesFactices()).saisir("T1", "OBJ", TypeLigne.NOTATION, "{}", "op1")
        verifierSynchronousFullEtWal(base, autresConnexions = false)
    }
}
