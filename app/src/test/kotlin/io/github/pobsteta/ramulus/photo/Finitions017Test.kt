package io.github.pobsteta.ramulus.photo

import io.github.pobsteta.ramulus.core.photo.RegimePhotos
import androidx.compose.ui.graphics.toArgb
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
        val t1 = s.commence()
        assertTrue(s.etat.value.enCours)
        s.echoue(t1, "Photo non gardée : disque plein")
        assertFalse(s.etat.value.enCours)
        assertEquals("Photo non gardée : disque plein", s.etat.value.message)
        val t2 = s.commence()
        assertNull(s.etat.value.message)
        s.reussit(t2)
        assertEquals(EtatCapture(), s.etat.value)
    }

    @Test
    fun `une capture interrompue par la rotation ne dit rien`() {
        val s = SuiviCapture()
        val t = s.commence()
        s.interrompue(t)
        assertFalse(s.etat.value.enCours)
        assertNull(s.etat.value.message)
    }

    @Test
    fun `capture commencee puis ecran recree sans retour le bouton revient`() {
        val s = SuiviCapture()
        val perdue = s.commence()
        assertTrue(s.etat.value.enCours)
        s.entree() // rotation : CameraX ne rend jamais son verdict
        assertFalse(s.etat.value.enCours)
        // Un verdict tardif de l'ancienne capture ne regrise pas le bouton ni n'efface la nouvelle.
        val neuve = s.commence()
        s.echoue(perdue, "vieux message")
        assertTrue(s.etat.value.enCours)
        assertNull(s.etat.value.message)
        s.reussit(neuve)
        assertFalse(s.etat.value.enCours)
    }

    @Test
    fun `le message d'echec ne suit pas l'arbre suivant`() {
        val s = SuiviCapture()
        s.echoue(s.commence(), "Photo non gardée")
        s.entree() // une rotation garde le message
        assertEquals("Photo non gardée", s.etat.value.message)
        s.quitte() // quitter l'écran photo le remet à zéro
        assertEquals(EtatCapture(), s.etat.value)
    }

    private fun luminance(argb: Int): Double {
        fun lin(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(argb shr 16 and 255) + 0.7152 * lin(argb shr 8 and 255) + 0.0722 * lin(argb and 255)
    }

    private fun contraste(a: Int, b: Int): Double {
        val (x, y) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (x + 0.05) / (y + 0.05)
    }

    @Test
    fun `l'ambre a verifier est lisible sur la surface des deux themes`() {
        val surfaceClair = 0xFFFEF7FF.toInt()
        val surfaceSombre = 0xFF141218.toInt()
        val clair = io.github.pobsteta.ramulus.ui.theme.ambreAVerifier(sombre = false).toArgb()
        val sombre = io.github.pobsteta.ramulus.ui.theme.ambreAVerifier(sombre = true).toArgb()
        assertTrue(contraste(clair, surfaceClair) >= 4.5, "clair ${contraste(clair, surfaceClair)}")
        assertTrue(contraste(sombre, surfaceSombre) >= 4.5, "sombre ${contraste(sombre, surfaceSombre)}")
    }
}
