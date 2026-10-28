package io.github.pobsteta.ramulus.core.session

import io.github.pobsteta.ramulus.core.deperis.Classe
import io.github.pobsteta.ramulus.core.deperis.Essences
import io.github.pobsteta.ramulus.core.deperis.Note
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionNotationTest {

    private val hetre = Essences.parNom("Hêtre")!!

    @Test
    fun `pas de classe tant qu une note manque, et pas d enregistrement`() {
        val s = SessionNotation(hetre).avecMb(Note(1))
        assertNull(s.classeEnCours)
        assertEquals(s, s.enregistrer("u"))
    }

    @Test
    fun `enregistrer remet l arbre suivant a zero`() {
        val s = SessionNotation(hetre).avecMb(Note(2)).avecSecond(Note(3))
        assertEquals(Classe.D, s.classeEnCours)
        val apres = s.enregistrer("u1")
        assertEquals(1, apres.notees.size)
        assertEquals(Classe.D, apres.notees.single().classe)
        assertNull(apres.mb)
        assertNull(apres.second)
    }

    @Test
    fun `le bilan suit les arbres notes`() {
        var s = SessionNotation(hetre)
        for ((mb, sec) in listOf(0 to 1, 0 to 0, 2 to 3, 1 to 1)) {
            s = s.avecMb(Note(mb)).avecSecond(Note(sec)).enregistrer()
        }
        assertEquals(4, s.bilan.effectif)
        assertTrue(s.bilan.estDegrade) // 1 sur 4 = 25 %
        assertEquals(3, s.annulerDernier().bilan.effectif)
    }

    @Test
    fun `changer d essence efface l arbre en cours, pas la session`() {
        val s = SessionNotation(hetre).avecMb(Note(0)).avecSecond(Note(0)).enregistrer().avecMb(Note(3))
        val sapin = s.avecEssence(Essences.parNom("Sapin")!!)
        assertNull(sapin.mb)
        assertEquals(1, sapin.notees.size)
    }
}
