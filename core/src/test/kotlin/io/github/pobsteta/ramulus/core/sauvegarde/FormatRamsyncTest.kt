package io.github.pobsteta.ramulus.core.sauvegarde

import io.github.pobsteta.ramulus.core.journal.LigneJournal
import io.github.pobsteta.ramulus.core.journal.OrdreTotal
import io.github.pobsteta.ramulus.core.journal.TypeLigne
import io.github.pobsteta.ramulus.core.journal.ligne
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

class FormatRamsyncTest {
    private val code = "DEPERIS-21-2027"

    /** Lignes aux valeurs difficiles : accents, guillemets, retour à la ligne, nombres, champs nuls. */
    private val lignes: List<LigneJournal> = listOf(
        ligne("u3", saisiLe = 3_000, sequence = 3, valeur = "{\"mb\": 2, \"note\":\"Mélèze \\\"tordu\\\"\\n\"}"),
        ligne("u1", saisiLe = 1_000, sequence = 1, heureGnss = 999, ageFix = 1_200, operateur = "Éloïse"),
        ligne("u2", saisiLe = 2_000, sequence = 2, type = TypeLigne.ANNULATION, valeur = null, remplace = "u1"),
        ligne("u4", saisiLe = 2_000, sequence = 1, appareil = "b0000000", valeur = "  {\"a\" :1.50}  "),
    )

    private fun ecrire(l: Collection<LigneJournal> = lignes, version: Int = 1) =
        EcritureRamsync.ecrire(code, "0.4.0", "0.1.0", "a3f9c2d1", 1_800_000_000_000, l, version)

    private fun decompresser(o: ByteArray) = GZIPInputStream(ByteArrayInputStream(o)).readBytes()

    /** Recompresse un contenu retouché (texte décompressé). */
    private fun gz(texte: String) = EcritureRamsync.compresser(texte.toByteArray(Charsets.UTF_8))

    // 7
    @Test
    fun `l aller-retour redonne les lignes au caractere pres, dans l ordre total`() {
        val octets = ecrire()
        val r = LectureRamsync.lire(octets)
        assertTrue(r.accepte)
        assertEquals(emptySet<Signalement>(), r.signalements)
        assertEquals(OrdreTotal.trier(lignes), r.lignes)
        assertEquals(EnTeteRamsync(1, code, "0.4.0", "0.1.0", "a3f9c2d1", 1_800_000_000_000, 4), r.entete)
        // Le gzip est reproductible : même journal, mêmes octets, quel que soit l'ordre reçu.
        assertArrayEquals(octets, ecrire(lignes.reversed()))
        // Ligne 1 : en-tête ; dernière : pied.
        val texte = String(decompresser(octets), Charsets.UTF_8).split("\n")
        assertTrue(texte[0].startsWith("{\"format\":\"ramsync\",\"version\":1,"))
        assertTrue(texte[5].matches(Regex("^\\{\"sha256\":\"[0-9a-f]{64}\"}$")))
        assertEquals("", texte[6], "chaque ligne se termine par \\n")
        assertFalse(texte.drop(1).take(4).any { "code_tournee" in it }, "les saisies ne répètent pas la tournée")
    }

    @Test
    fun `l ecriture refuse une ligne d une autre tournee et un code vide`() {
        val autre = ligne("x", tournee = "AUTRE")
        assertTrue(runCatching { ecrire(lignes + autre) }.isFailure)
        assertTrue(runCatching {
            EcritureRamsync.ecrire(" ", null, "0.1.0", "a3f9c2d1", 0, emptyList())
        }.isFailure)
    }

    // 8
    @Test
    fun `un octet change donne empreinte_differente et tout reste lu`() {
        val texte = String(decompresser(ecrire()), Charsets.UTF_8)
        val altere = texte.replace("\"op1\"", "\"op2\"")
        val r = LectureRamsync.lire(gz(altere))
        assertTrue(r.accepte)
        assertEquals(setOf(Signalement.EMPREINTE_DIFFERENTE), r.signalements)
        assertEquals(4, r.lignes.size)
        assertEquals(0, r.lignesRefusees)
    }

    // 9
    @Test
    fun `un fichier tronque donne fichier_tronque et garde les lignes d avant la coupure`() {
        val octets = ecrire()
        // Gzip coupé au milieu.
        val coupe = LectureRamsync.lire(octets.copyOf(octets.size - 30))
        assertTrue(coupe.accepte)
        assertTrue(Signalement.FICHIER_TRONQUE in coupe.signalements)
        // Contenu coupé au milieu de la troisième saisie, gzip complet : deux lignes saines, la coupée ignorée.
        val texte = String(decompresser(octets), Charsets.UTF_8)
        val lignesTexte = texte.split("\n")
        val tronque = lignesTexte.take(3).joinToString("\n") + "\n" + lignesTexte[3].take(20)
        val r = LectureRamsync.lire(gz(tronque))
        assertEquals(setOf(Signalement.FICHIER_TRONQUE), r.signalements)
        assertEquals(OrdreTotal.trier(lignes).take(2), r.lignes)
    }

    // 10
    @Test
    fun `une ligne fautive donne ligne_illisible et les autres sont lues`() {
        val l = String(decompresser(ecrire()), Charsets.UTF_8).split("\n").toMutableList()
        l[1] = "{pas du json"
        l[2] = l[2].replace("\"type\":\"annulation\"", "\"type\":\"inconnu\"")
        l[3] = l[3].replace("\"sequence\":1,", "\"sequence\":\"1\",")
        val r = LectureRamsync.lire(gz(l.joinToString("\n")))
        assertTrue(r.accepte)
        assertTrue(Signalement.LIGNE_ILLISIBLE in r.signalements)
        assertEquals(3, r.lignesRefusees)
        assertEquals(listOf(OrdreTotal.trier(lignes)[3]), r.lignes)
        assertEquals(listOf("u2", "u4"), r.uuidRefuses)
    }

    @Test
    fun `un champ absent rend la ligne illisible`() {
        val l = String(decompresser(ecrire()), Charsets.UTF_8).split("\n").toMutableList()
        l[1] = l[1].replace(",\"age_fix_ms\":1200", "")
        val r = LectureRamsync.lire(gz(l.joinToString("\n")))
        assertTrue(Signalement.LIGNE_ILLISIBLE in r.signalements)
        assertEquals(3, r.lignes.size)
    }

    // 11
    @Test
    fun `une ligne d une autre tournee donne ligne_autre_tournee`() {
        val l = String(decompresser(ecrire()), Charsets.UTF_8).split("\n").toMutableList()
        l[1] = l[1].dropLast(1) + ",\"code_tournee\":\"AUTRE\"}"
        l[2] = l[2].dropLast(1) + ",\"code_tournee\":\"$code\"}"
        val r = LectureRamsync.lire(gz(l.joinToString("\n")))
        assertTrue(r.accepte)
        assertTrue(Signalement.LIGNE_AUTRE_TOURNEE in r.signalements)
        assertEquals(listOf("u1"), r.uuidRefuses)
        assertEquals(3, r.lignes.size, "une ligne qui porte le code de l'en-tête est gardée")
        assertTrue(r.lignes.all { it.codeTournee == code })
    }

    // 12
    @Test
    fun `meme uuid au contenu different refuse les deux, a l identique la seconde est ignoree`() {
        val l = String(decompresser(ecrire()), Charsets.UTF_8).split("\n").toMutableList()
        val doublonIdentique = l[1]
        val doublonDifferent = l[2].replace("\"op1\"", "\"op9\"")
        l.add(5, doublonIdentique)
        l.add(6, doublonDifferent)
        val r = LectureRamsync.lire(gz(l.joinToString("\n")))
        assertTrue(r.accepte)
        assertTrue(Signalement.UUID_CONTENU_DIFFERENT in r.signalements)
        assertEquals(1, r.lignesIgnorees)
        assertEquals(2, r.lignesRefusees)
        assertEquals(listOf("u2"), r.uuidRefuses)
        assertEquals(setOf("u1", "u3", "u4"), r.lignes.map { it.uuid }.toSet())
    }

    // 13
    @Test
    fun `un fichier v2 lu par un lecteur v1 est refuse version_future`() {
        val r = LectureRamsync.lire(ecrire(version = 2), versionCourante = 1)
        assertEquals(MotifRefus.VERSION_FUTURE, r.motif)
        assertTrue(r.lignes.isEmpty())
        assertNull(r.entete)
    }

    // 14
    @Test
    fun `un fichier v1 est lu par un lecteur v2, pas par un lecteur v3`() {
        val r = LectureRamsync.lire(ecrire(version = 1), versionCourante = 2)
        assertTrue(r.accepte)
        assertEquals(OrdreTotal.trier(lignes), r.lignes)
        assertEquals(MotifRefus.VERSION_ILLISIBLE, LectureRamsync.lire(ecrire(version = 1), versionCourante = 3).motif)
    }

    // 15
    @Test
    fun `version invalide, fichier vide, non gzip ou code vide donnent version_illisible`() {
        val texte = String(decompresser(ecrire()), Charsets.UTF_8)
        val cas = mapOf(
            "0 octet" to ByteArray(0),
            "non gzip" to texte.toByteArray(),
            "gzip vide" to gz(""),
            "version absente" to gz(texte.replace("\"version\":1,", "")),
            "version texte" to gz(texte.replace("\"version\":1,", "\"version\":\"1\",")),
            "version décimale" to gz(texte.replace("\"version\":1,", "\"version\":1.5,")),
            "version nulle" to gz(texte.replace("\"version\":1,", "\"version\":0,")),
            "version négative" to gz(texte.replace("\"version\":1,", "\"version\":-1,")),
            "code vide" to gz(texte.replace("\"code_tournee\":\"$code\"", "\"code_tournee\":\"\"")),
            "autre format" to gz(texte.replace("\"format\":\"ramsync\"", "\"format\":\"sursync\"")),
            "en-tête illisible" to gz("{oups\n" + texte.substringAfter("\n")),
        )
        for ((nom, octets) in cas) {
            assertEquals(MotifRefus.VERSION_ILLISIBLE, LectureRamsync.lire(octets).motif, nom)
        }
        // « 1.0 » est un entier écrit en décimal : lu.
        assertTrue(LectureRamsync.lire(gz(texte.replace("\"version\":1,", "\"version\":1.0,"))).accepte)
    }

    @Test
    fun `le pied detecte une alteration de l en-tete aussi, et un pied absent signale la troncature`() {
        val l = String(decompresser(ecrire()), Charsets.UTF_8).split("\n").toMutableList()
        val enteteAltere = l.toMutableList().also { it[0] = it[0].replace("\"0.1.0\"", "\"0.1.1\"") }
        assertEquals(
            setOf(Signalement.EMPREINTE_DIFFERENTE),
            LectureRamsync.lire(gz(enteteAltere.joinToString("\n"))).signalements,
        )
        val sansPied = l.toMutableList().also { it.removeAt(5) }
        val r = LectureRamsync.lire(gz(sansPied.joinToString("\n")))
        assertEquals(setOf(Signalement.FICHIER_TRONQUE), r.signalements)
        assertEquals(4, r.lignes.size)
    }
}
