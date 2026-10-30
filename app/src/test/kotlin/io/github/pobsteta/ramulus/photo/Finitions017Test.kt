package io.github.pobsteta.ramulus.photo

import io.github.pobsteta.ramulus.core.photo.RegimePhotos
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mission 017 : PV1 lue à un seul endroit, couleurs par le thème, état de la capture hors de l'écran. */
class Finitions017Test {
    private val sources = File("src/main/kotlin/io/github/pobsteta/ramulus")

    private fun ecrans() = listOf("tournee/Ecrans.kt", "photo/EcranPhoto.kt", "notation/EcranNotation.kt").map { File(sources, it) }

    @Test
    fun `la fourchette de vues vient du regime, pas d'un litteral`() {
        assertEquals("${RegimePhotos.COLLECTE.min} à ${RegimePhotos.COLLECTE.max}", fourchetteDeVues(RegimePhotos.COLLECTE))
        assertEquals("1", fourchetteDeVues(RegimePhotos.ORDINAIRE))
        assertTrue(rappelDeConvention(RegimePhotos.COLLECTE).any { fourchetteDeVues(RegimePhotos.COLLECTE) in it })
        for (f in ecrans()) {
            assertTrue(f.exists(), f.path)
            assertFalse("2 à 4" in f.readText(), "PV1 écrite en dur dans ${f.name}")
        }
    }

    @Test
    fun `les ecrans n'ont ni Color Gray ni Ambre ni couleur litterale`() {
        for (f in ecrans()) {
            val t = f.readText()
            assertFalse("Color.Gray" in t, f.name)
            assertFalse(Regex("\\bAmbre\\b").containsMatchIn(t), f.name)
            assertFalse("Color(0x" in t, f.name)
        }
    }

    @Test
    fun `la capture se suit d'un bout a l'autre et garde son verdict`() {
        val s = SuiviCapture()
        assertEquals(EtatCapture(), s.etat.value)
        s.commence()
        assertTrue(s.etat.value.enCours)
        s.echoue("Photo non gardée : disque plein")
        assertFalse(s.etat.value.enCours)
        assertEquals("Photo non gardée : disque plein", s.etat.value.message)
        s.commence()
        assertNull(s.etat.value.message)
        s.reussit()
        assertEquals(EtatCapture(), s.etat.value)
    }

    @Test
    fun `une capture interrompue par la rotation ne dit rien`() {
        val s = SuiviCapture()
        s.commence()
        s.interrompue()
        assertFalse(s.etat.value.enCours)
        assertNull(s.etat.value.message)
    }
}
