package io.github.pobsteta.ramulus.core.deperis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class AbaqueTest {

    /** Les 36 cases de l'abaque, relues dans docs/referentiels/deperis/abaque.csv (recopie de la fiche, page 1). */
    @Test
    fun `les 36 cases suivent le referentiel`() {
        val lignes = File("../docs/referentiels/deperis/abaque.csv").readLines().drop(1).filter { it.isNotBlank() }
        assertEquals(6, lignes.size)
        for (ligne in lignes) {
            val c = ligne.split(';')
            val mb = c[0].toInt()
            for (second in 0..5) {
                assertEquals(Classe.valueOf(c[second + 1]), Abaque.classe(mb, second), "MB=$mb, second=$second")
            }
        }
    }

    @Test
    fun `l abaque ne decroit jamais quand une note augmente`() {
        for (mb in 0..5) for (s in 0..5) {
            val c = Abaque.classe(mb, s)
            if (mb < 5) assertTrue(Abaque.classe(mb + 1, s) >= c, "MB $mb→${mb + 1}, second $s")
            if (s < 5) assertTrue(Abaque.classe(mb, s + 1) >= c, "MB $mb, second $s→${s + 1}")
        }
    }

    @Test
    fun `seule la case 5-5 est l arbre mort`() {
        for (mb in Note.TOUTES) for (s in Note.TOUTES) {
            assertEquals(mb.valeur == 5 && s.valeur == 5, Abaque.estMort(mb, s))
        }
        assertFalse(Abaque.estMort(Note(5), Note(0)))
    }

    @Test
    fun `exemples des fiches`() {
        // Chêne sessile été, fiche 2025 p. 1 : MB = 2, MR = 4 → E ; p. 5 : MB = 5, MR = 0 → F.
        assertEquals(Classe.E, Abaque.classe(2, 4))
        assertEquals(Classe.F, Abaque.classe(5, 0))
        // Sapin, fiche 2025 p. 4 : MB = 1, MA = 2 → C.
        assertEquals(Classe.C, Abaque.classe(1, 2))
    }
}
