package io.github.pobsteta.ramulus.core.deperis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NotationTest {

    private val ia = PropositionIa(Note(1), Note(2), 0.7, "ramulus-essai-0")

    @Test
    fun `la classe se deduit toujours de l abaque`() {
        val n = Notation("u1", "Hêtre", TypeEssence.FEUILLU, Note(2), Note(3), Origine.OPERATEUR)
        assertEquals(Classe.D, n.classe)
        assertNull(n.ecartIa)
    }

    @Test
    fun `ecart negatif quand l IA est plus clemente`() {
        // L'IA propose C (1, 2) ; l'opérateur retient D (2, 3) : la faute la plus fréquente de l'essai du 2026-10-28.
        val n = Notation("u2", "Hêtre", TypeEssence.FEUILLU, Note(2), Note(3), Origine.OPERATEUR_AIDE_IA, ia)
        assertEquals(-1, n.ecartIa)
    }

    @Test
    fun `une notation aidee garde la proposition et seulement elle`() {
        assertThrows<IllegalArgumentException> {
            Notation("u3", "Hêtre", TypeEssence.FEUILLU, Note(0), Note(0), Origine.OPERATEUR_AIDE_IA, null)
        }
        assertThrows<IllegalArgumentException> {
            Notation("u4", "Hêtre", TypeEssence.FEUILLU, Note(0), Note(0), Origine.OPERATEUR, ia)
        }
    }

    @Test
    fun `second critere selon le type`() {
        assertEquals(SecondCritere.MR, TypeEssence.FEUILLU.secondCritere)
        assertEquals(SecondCritere.MA, TypeEssence.RESINEUX.secondCritere)
    }
}
