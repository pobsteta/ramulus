package io.github.pobsteta.ramulus.core.deperis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NoteTest {

    @Test
    fun `bornes de la fiche`() {
        val attendu = mapOf(0 to 0, 5 to 0, 6 to 1, 25 to 1, 26 to 2, 50 to 2, 51 to 3, 75 to 3, 76 to 4, 95 to 4, 96 to 5, 100 to 5)
        for ((pourcent, note) in attendu) assertEquals(note, Note.depuisPourcentage(pourcent).valeur, "$pourcent %")
    }

    @Test
    fun `chaque pourcentage a exactement une note`() {
        for (p in 0..100) {
            val n = Note.depuisPourcentage(p)
            assert(p in n.pourcentMin..n.pourcentMax)
        }
    }

    @Test
    fun `hors bornes refuse`() {
        assertThrows<IllegalArgumentException> { Note(-1) }
        assertThrows<IllegalArgumentException> { Note(6) }
        assertThrows<IllegalArgumentException> { Note.depuisPourcentage(101) }
    }

    @Test
    fun `intensites`() {
        assertEquals("Absence ou trace", Note(0).intensite)
        assertEquals("Total ou presque", Note(5).intensite)
    }
}
