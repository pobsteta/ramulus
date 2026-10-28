package io.github.pobsteta.ramulus.core.sauvegarde

import io.github.pobsteta.ramulus.core.journal.ligne
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NomFichierTest {
    // 2026-10-14T09:05:07Z
    private val t = 1_791_968_707_000L

    // 16
    @Test
    fun `le nom suit le motif, avec le code assaini et l heure UTC sans deux-points`() {
        assertEquals("DEPERIS-21-2027-a3f9c2d1-20261014T090507Z.ramsync", NomFichier.nom("DEPERIS-21-2027", "a3f9c2d1", t))
        assertEquals("Foret_de_Citeaux_2027-a3f9c2d1-20261014T090507Z.ramsync",
            NomFichier.nom("Forêt de Cîteaux/2027", "a3f9c2d1", t))
        assertFalse(':' in NomFichier.nom("A:B", "a3f9c2d1", t))
        assertTrue(runCatching { NomFichier.nom("A", "A3F9C2D1", t) }.isFailure, "appareil non opaque refusé")
        assertTrue(runCatching { NomFichier.nom("A", "a3f9c2d", t) }.isFailure)
        assertTrue(runCatching { NomFichier.nom("", "a3f9c2d1", t) }.isFailure)
    }

    // 17
    @Test
    fun `aucun nom d operateur dans le nom du fichier`() {
        val operateurs = listOf("Jean Dupont", "Éloïse Martin", "dupont.j")
        val lignes = operateurs.mapIndexed { i, op -> ligne("u$i", operateur = op, sequence = i.toLong()) }
        val octets = EcritureRamsync.ecrire("DEPERIS-21-2027", null, "0.1.0", "a3f9c2d1", t, lignes)
        val entete = LectureRamsync.lire(octets).entete!!
        // Le nom ne se construit qu'avec l'en-tête : code, appareil opaque, heure. Ni opérateur, ni propriétaire.
        val nom = NomFichier.nom(entete.codeTournee, entete.appareil, entete.creeLeUtcMs)
        for (op in operateurs) {
            assertFalse(nom.contains(op, ignoreCase = true), nom)
            assertFalse(nom.contains(NomFichier.assainir(op), ignoreCase = true), nom)
            op.split(' ', '.').forEach { morceau -> assertFalse(nom.contains(NomFichier.assainir(morceau), ignoreCase = true)) }
        }
        // Et la signature n'a pas de place pour lui : code, appareil, heure.
        val signature = NomFichier::class.java.methods.single { it.name == "nom" }
        assertEquals(listOf(String::class.java, String::class.java, Long::class.javaPrimitiveType), signature.parameterTypes.toList())
    }

    // 18
    @Test
    fun `un nom deja pris recoit -2 puis -3, jamais d ecrasement`() {
        val nom = NomFichier.nom("T", "a3f9c2d1", t)
        assertEquals(nom, NomFichier.libre(nom, emptySet()))
        val n2 = NomFichier.libre(nom, setOf(nom))
        assertEquals("T-a3f9c2d1-20261014T090507Z-2.ramsync", n2)
        assertEquals("T-a3f9c2d1-20261014T090507Z-3.ramsync", NomFichier.libre(nom, setOf(nom, n2)))
        assertEquals(NomFichier.Morceaux("T", "a3f9c2d1", "20261014T090507Z", 2), NomFichier.lire(n2))
    }

    // 19
    @Test
    fun `la rotation garde les 5 dernieres de la meme tournee, jamais celles d une autre`() {
        val a = (0 until 7).map { i -> NomFichier.nom("FORET-A", "a3f9c2d1", t + i * 60_000L) }
        val aMemeSeconde = NomFichier.libre(a[6], setOf(a[6]))
        val b = (0 until 3).map { i -> NomFichier.nom("FORET-A-B", "a3f9c2d1", t - i * 60_000L) }
        val inconnu = "notes.txt"
        val presents = (a + aMemeSeconde + b + inconnu).shuffled(java.util.Random(1))
        assertEquals(a.take(3), Rotation.aSupprimer(presents, "FORET-A"),
            "8 fichiers de la tournée : les 3 plus anciens partent, le -2 de la même seconde est le plus récent")
        assertTrue(Rotation.aSupprimer(presents, "FORET-A-B").isEmpty())
        assertTrue(Rotation.aSupprimer(presents, "AUTRE").isEmpty())
        // Une autre appareil, même tournée : c'est la même tournée.
        val autreAppareil = NomFichier.nom("FORET-A", "0000beef", t + 3_600_000L)
        assertEquals(a.take(4), Rotation.aSupprimer(presents + autreAppareil, "FORET-A"))
    }

    // 20
    @Test
    fun `la cadence suit le premier des declencheurs, jamais sans saisie nouvelle`() {
        fun due(n: Int, fin: Boolean = false, repere: Long? = 0, repDem: Long? = 1, now: Long = 0, dem: Long = 1) =
            Cadence.estDue(n, fin, repere, repDem, now, dem)
        // Jamais sans saisie nouvelle, même en fin de placette ou après longtemps.
        assertFalse(due(0, fin = true, now = 3_600_000))
        // Fin de placette.
        assertTrue(due(1, fin = true))
        // 20 saisies.
        assertFalse(due(19, now = 299_999))
        assertTrue(due(20))
        // 5 minutes monotones.
        assertFalse(due(1, now = 299_999))
        assertTrue(due(1, now = 300_000))
        // Redémarrage depuis le repère, ou repère inconnu : due.
        assertTrue(due(1, repDem = 1, dem = 2, now = 10))
        assertTrue(due(1, repere = null, repDem = null))
        assertEquals(20, Cadence.N_SAISIES)
        assertEquals(5, Cadence.M_MINUTES)
    }
}
