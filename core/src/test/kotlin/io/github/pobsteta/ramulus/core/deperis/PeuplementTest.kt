package io.github.pobsteta.ramulus.core.deperis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeuplementTest {

    private fun bilan(s: String) = BilanPeuplement.de(s.map { Classe.valueOf(it.toString()) })

    @Test
    fun `vingt pour cent tout juste n est pas degrade`() {
        val b = bilan("ABBBD") // 1 sur 5 = 20 %
        assertEquals(1, b.degrades)
        assertFalse(b.estDegrade)
    }

    @Test
    fun `au dela de vingt pour cent le peuplement est degrade`() {
        val b = bilan("ABBDD") // 40 %
        assertTrue(b.estDegrade)
        assertFalse(b.estTresDegrade)
    }

    @Test
    fun `tres degrade compte E et F seulement`() {
        val b = bilan("AAAEF") // 40 % de E, F
        assertTrue(b.estDegrade)
        assertTrue(b.estTresDegrade)
        assertEquals(2, b.tresDegrades)
    }

    @Test
    fun `C n est ni sain ni degrade`() {
        val b = bilan("CCCCC")
        assertFalse(b.estDegrade)
        assertEquals(0, b.degrades)
    }

    @Test
    fun `peuplement vide`() {
        val b = BilanPeuplement.de(emptyList())
        assertEquals(0, b.effectif)
        assertFalse(b.estDegrade)
        assertEquals(0.0, b.partDegrades)
    }
}
