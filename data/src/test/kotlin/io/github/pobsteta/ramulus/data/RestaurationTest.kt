package io.github.pobsteta.ramulus.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.pobsteta.ramulus.core.journal.LigneJournal
import io.github.pobsteta.ramulus.core.journal.TypeLigne
import io.github.pobsteta.ramulus.core.sauvegarde.EcritureRamsync
import io.github.pobsteta.ramulus.core.sauvegarde.MotifRefus
import io.github.pobsteta.ramulus.core.sauvegarde.NomFichier
import io.github.pobsteta.ramulus.core.sauvegarde.Signalement
import io.github.pobsteta.ramulus.data.journal.JournalRepository
import io.github.pobsteta.ramulus.data.journal.RamulusDatabase
import io.github.pobsteta.ramulus.data.journal.RefusEntity
import io.github.pobsteta.ramulus.data.sauvegarde.DestinationDossier
import io.github.pobsteta.ramulus.data.sauvegarde.SauvegardeRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RestaurationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val bases = mutableListOf<RamulusDatabase>()
    private val horloges = HorlogesFactices()
    private val code = "DEPERIS-21-2027"
    private lateinit var dossier: File

    @Before
    fun preparer() {
        dossier = File(context.cacheDir, "Documents/Ramulus").also { it.deleteRecursively() }
    }

    @After
    fun fermer() = bases.forEach { it.close() }

    private fun base() = RamulusDatabase.ouvrir(context, null) { allowMainThreadQueries() }.also { bases += it }

    /** Quelques saisies d'une placette : notations, correction, annulation, photo, accents dans la valeur. */
    private fun saisirPlacette(r: JournalRepository, tournee: String = code): List<LigneJournal> {
        val n1 = r.saisir(tournee, "ARBRE-1", TypeLigne.NOTATION, "{\"mb\":1,\"ma\":0}", "Éloïse", heureGnssMs = horloges.utc, ageFixMs = 800)
        horloges.avancer(20_000)
        val n2 = r.saisir(tournee, "ARBRE-2", TypeLigne.NOTATION, "{\"mb\":3,\"mr\":2,\"essence\":\"Mélèze\"}", "Éloïse")
        horloges.avancer(20_000)
        val corr = r.saisir(tournee, "ARBRE-2", TypeLigne.NOTATION, "{\"mb\":2,\"mr\":2,\"essence\":\"Mélèze\"}", "Éloïse", remplaceUuid = n2.uuid)
        horloges.avancer(20_000)
        val photo = r.saisir(tournee, "PHOTO-1", TypeLigne.PHOTO, "{\"sha256\":\"00\"}", "Éloïse")
        horloges.avancer(20_000)
        val annul = r.saisir(tournee, "PHOTO-1", TypeLigne.ANNULATION, null, "Éloïse", remplaceUuid = photo.uuid)
        horloges.avancer(20_000)
        val fin = r.saisir(tournee, "PLACETTE-7", TypeLigne.PLACETTE_NOTEE, "{}", "Éloïse")
        return listOf(n1, n2, corr, photo, annul, fin)
    }

    // 28
    @Test
    fun `union sur l uuid - identique ignoree, differente au refus, locales gardees`() {
        val source = JournalRepository(base(), horloges)
        val saisies = saisirPlacette(source)
        val octets = EcritureRamsync.ecrire(code, "0.4.0", "0.1.0", source.appareil(), horloges.utc, saisies)

        val cible = JournalRepository(base(), horloges)
        val locale = cible.saisir(code, "ARBRE-9", TypeLigne.NOTATION, "{\"mb\":0}", "Paul")
        // La cible a déjà la première saisie à l'identique, et la deuxième avec un autre contenu.
        val cibleDb = bases.last()
        cibleDb.journal().inserer(io.github.pobsteta.ramulus.data.journal.LigneEntity.de(saisies[0]))
        val divergente = saisies[1].copy(valeur = "{\"mb\":4}")
        cibleDb.journal().inserer(io.github.pobsteta.ramulus.data.journal.LigneEntity.de(divergente))

        val r = cible.restaurer(octets)
        assertTrue(r.lecture.accepte)
        assertEquals(4, r.ajoutees)
        assertEquals(1, r.ignorees)
        assertEquals(1, r.refusees)
        val journal = cible.toutes()
        assertTrue("la ligne locale reste", locale in journal)
        assertTrue("la version locale divergente reste", divergente in journal)
        assertFalse(saisies[1] in journal)
        assertEquals(7, journal.size)
        val refus = cible.refus().single()
        assertEquals(saisies[1], refus.versLigne())
        assertEquals(RefusEntity.UUID_CONTENU_DIFFERENT, refus.motif)
        // Les lignes restaurées gardent leur appareil et leur séquence d'origine.
        val restauree = journal.single { it.uuid == saisies[2].uuid }
        assertEquals(saisies[2], restauree)
        assertEquals(source.appareil(), restauree.appareil)
    }

    // 29
    @Test
    fun `la restauration est idempotente`() {
        val source = JournalRepository(base(), horloges)
        val saisies = saisirPlacette(source)
        val octets = EcritureRamsync.ecrire(code, null, "0.1.0", source.appareil(), horloges.utc, saisies)
        val cible = JournalRepository(base(), horloges)
        bases.last().journal().inserer(
            io.github.pobsteta.ramulus.data.journal.LigneEntity.de(saisies[3].copy(operateur = "autre")),
        )
        val premiere = cible.restaurer(octets)
        val etat1 = cible.toutes() to cible.refus().map { it.versLigne() }
        val seconde = cible.restaurer(octets)
        val etat2 = cible.toutes() to cible.refus().map { it.versLigne() }
        assertEquals(5, premiere.ajoutees)
        assertEquals(0, seconde.ajoutees)
        assertEquals(5, seconde.ignorees)
        assertEquals(etat1, etat2)
        assertEquals(1, cible.refus().size)
    }

    @Test
    fun `un fichier refuse en entier ne touche a rien, un fichier abime restaure ses lignes saines`() {
        val source = JournalRepository(base(), horloges)
        val saisies = saisirPlacette(source)
        val cible = JournalRepository(base(), horloges)
        val v2 = EcritureRamsync.ecrire(code, null, "0.1.0", source.appareil(), 0, saisies, version = 2)
        val r = cible.restaurer(v2)
        assertEquals(MotifRefus.VERSION_FUTURE, r.lecture.motif)
        assertTrue(cible.toutes().isEmpty())

        val octets = EcritureRamsync.ecrire(code, null, "0.1.0", source.appareil(), 0, saisies)
        val abime = cible.restaurer(octets.copyOf(octets.size - 12))
        assertTrue(Signalement.FICHIER_TRONQUE in abime.lecture.signalements)
        assertEquals(abime.lecture.lignes.size, cible.toutes().size)
    }

    // 30
    @Test
    fun `saisir, sauvegarder, vider, restaurer redonne un journal identique`() {
        val db1 = base()
        val journal1 = JournalRepository(db1, horloges)
        val sauvegarde1 = SauvegardeRepository(db1, journal1, DestinationDossier(dossier), horloges, "0.1.0")
        val saisies = saisirPlacette(journal1) + saisirPlacette(journal1, "AUTRE-TOURNEE")
        val nom = sauvegarde1.sauvegarderSiDue(code, "0.4.0", finDePlacette = true)
        assertNotNull(nom)
        assertEquals(setOf(nom), dossier.list()!!.toSet())
        assertFalse("aucun nom d'opérateur dans le nom", nom!!.contains("Elo", ignoreCase = true))
        assertTrue(nom.startsWith("$code-${journal1.appareil()}-"))
        assertNull("jamais sans saisie nouvelle", sauvegarde1.sauvegarderSiDue(code, "0.4.0", finDePlacette = true))

        // Téléphone effacé : nouvelle base, nouvel appareil ; on restaure le fichier copié.
        val copie = File(dossier, nom).readBytes()
        val appareil1 = journal1.appareil()
        db1.close()
        val journal2 = JournalRepository(base(), horloges)
        val r = journal2.restaurer(copie)
        assertTrue(r.lecture.accepte)
        assertEquals(emptySet<Signalement>(), r.lecture.signalements)
        val attendu = saisies.filter { it.codeTournee == code }.sortedWith(io.github.pobsteta.ramulus.core.journal.OrdreTotal)
        assertEquals(attendu, journal2.lignes(code))
        assertTrue("une autre tournée n'est pas dans la sauvegarde", journal2.lignes("AUTRE-TOURNEE").isEmpty())
        // Le nouvel appareil a son propre identifiant ; une nouvelle saisie ne réutilise pas l'origine.
        val nouvelle = journal2.saisir(code, "ARBRE-3", TypeLigne.NOTATION, "{}", "Éloïse")
        assertTrue(nouvelle.appareil != appareil1)
        assertEquals(1L, nouvelle.sequence)
    }

    @Test
    fun `cadence et rotation - 20 saisies, 5 minutes, 5 fichiers gardes par tournee, jamais d ecrasement`() {
        val db1 = base()
        val journal = JournalRepository(db1, horloges)
        val sauvegarde = SauvegardeRepository(db1, journal, DestinationDossier(dossier), horloges, "0.1.0")
        // Première saisie : repère de la cadence, rien n'est dû avant 5 minutes ou 20 saisies.
        journal.saisir(code, "A0", TypeLigne.NOTATION, "{}", "op")
        assertNull(sauvegarde.sauvegarderSiDue(code, null, finDePlacette = false))
        repeat(18) { journal.saisir(code, "A${it + 1}", TypeLigne.NOTATION, "{}", "op") }
        assertNull("19 saisies, même instant", sauvegarde.sauvegarderSiDue(code, null, false))
        journal.saisir(code, "A19", TypeLigne.NOTATION, "{}", "op")
        val premier = sauvegarde.sauvegarderSiDue(code, null, false)
        assertNotNull("20 saisies", premier)
        // Même seconde, nouvelle saisie, fin de placette : nom suffixé -2, le premier n'est pas écrasé.
        journal.saisir(code, "B", TypeLigne.NOTATION, "{}", "op")
        val second = sauvegarde.sauvegarderSiDue(code, null, true)
        assertEquals(premier!!.removeSuffix(".ramsync") + "-2.ramsync", second)
        // 5 minutes monotones après la dernière sauvegarde, avec une saisie nouvelle.
        journal.saisir(code, "C", TypeLigne.NOTATION, "{}", "op")
        horloges.avancer(299_999)
        assertNull(sauvegarde.sauvegarderSiDue(code, null, false))
        horloges.avancer(1)
        assertNotNull(sauvegarde.sauvegarderSiDue(code, null, false))
        // Une autre tournée dans le même dossier.
        journal.saisir("AUTRE", "Z", TypeLigne.NOTATION, "{}", "op")
        val autre = sauvegarde.sauvegarder("AUTRE", null)!!
        repeat(5) {
            horloges.avancer(1_000)
            journal.saisir(code, "D$it", TypeLigne.NOTATION, "{}", "op")
            sauvegarde.sauvegarderSiDue(code, null, true)
        }
        val presents = dossier.list()!!.toSet()
        assertEquals(6, presents.size)
        assertTrue("jamais une autre tournée", autre in presents)
        assertFalse("la plus ancienne de la tournée est partie", premier in presents)
        assertEquals(9, db1.sauvegardes().toutes().size)
        assertTrue(presents.all { NomFichier.lire(it) != null })
    }
}
