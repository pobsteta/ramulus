package io.github.pobsteta.ramulus.core.deperis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EssencesTest {

    @Test
    fun `dix-neuf essences, chacune avec au moins une fiche`() {
        assertEquals(19, Essences.toutes.size)
        assertEquals(Essences.toutes.size, Essences.toutes.map { it.nom }.toSet().size)
    }

    @Test
    fun `le meleze est note au manque d aiguilles`() {
        assertEquals(TypeEssence.RESINEUX, Essences.parNom("Mélèze")!!.type)
    }

    @Test
    fun `fiche selon la saison`() {
        val hetre = Essences.parNom("hêtre")!!
        assertNotNull(hetre.fiche(Saison.HIVER))
        val charme = Essences.parNom("Charme")!!
        assertNull(charme.fiche(Saison.HIVER), "le DSF ne publie pas de fiche d'hiver pour le charme")
        val sapin = Essences.parNom("Sapin")!!
        assertEquals(sapin.fiche(Saison.ETE), sapin.fiche(Saison.HIVER))
    }
}
