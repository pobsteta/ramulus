package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Garde-fou du jeu de cas, JAMAIS désactivé (il ne dépend pas de 004) : il vérifie que les cas lus par les tests
 * d'acceptation couvrent tout le catalogue du format. Un vert ici ne prouve RIEN sur le module GeoPackage.
 */
class CasCommunsLusTest {

    private val cas = Cas.tous()

    @Test
    fun `chaque motif du catalogue du format a au moins un cas`() {
        val schema = JsonParser.parseString(File("../docs/formats/deperis-v1.schema.json").readText()).asJsonObject
        val catalogue = schema.getAsJsonObject("x-motifs").keySet()
        assertEquals(25, catalogue.size, "motifs du catalogue (docs/formats/deperis-v1.md, tableau « Catalogue des motifs »)")
        val couverts = cas.flatMap { it.motifs }.toSet()
        assertEquals(emptySet<String>(), catalogue - couverts, "motifs sans cas")
        assertEquals(emptySet<String>(), couverts - catalogue, "motifs de cas inconnus du catalogue")
    }

    @Test
    fun `les cas sont tous lus, avec leurs fichiers et leurs instantanes`() {
        assertEquals(38, cas.size, "28 cas de 002 + 8 cas complémentaires X1 à X7b de 003 + F1 et T2 de 010, tous dans fixtures/echange")
        assertEquals(cas.size, cas.map { it.id }.toSet().size, "identifiants de cas distincts")
        for (c in cas) {
            assertTrue(c.fichiers.isNotEmpty(), c.id)
            for (f in c.fichiers) {
                assertTrue(f.isFile, "${c.id} : $f")
                assertTrue(File(f.parentFile, f.nameWithoutExtension + ".instantane.json").isFile, "${c.id} : instantané de $f")
            }
            c.depart?.let { assertTrue(it.isFile, "${c.id} : départ $it") }
            assertTrue(c.lecteur in setOf("ramulus", "nemeton"), "${c.id} : lecteur ${c.lecteur}")
            assertTrue(c.statut in setOf("accepte", "signale", "refuse"), "${c.id} : ${c.statut}")
        }
    }

    @Test
    fun `un cas refuse ou signale porte un motif, un cas accepte n en porte aucun, hors fusion`() {
        for (c in cas) {
            when (c.statut) {
                "accepte" -> assertEquals(emptySet<String>(), c.motifs, c.id)
                else -> assertTrue(c.motifs.isNotEmpty(), c.id)
            }
        }
    }
}
