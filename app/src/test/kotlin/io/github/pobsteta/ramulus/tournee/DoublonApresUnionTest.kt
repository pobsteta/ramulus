package io.github.pobsteta.ramulus.tournee

import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.echange.EchangeDeperis
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Mission 020, règle 6 (non-régression, VERT dès maintenant, jamais sauté) : deux lignes du même rang venues de deux
 * opérateurs, réunies par l'union sur l'`uuid`, restent signalées `doublon_arbre`. Source : fiche 015 (« `doublon_arbre`
 * reste un contrôle du rendu : il signale désormais un vrai doublon, par exemple après l'union de deux opérateurs ») ;
 * `docs/formats/deperis-v1.md` (« deux notations du même arbre […] sont signalées »).
 *
 * Deux opérateurs notent chacun le rang 1 de P001 de la même tournée, chacun sur son téléphone (deux contrôleurs, deux
 * copies de travail), rendent leur `.zip`, puis Nemeton les réunit. Ni l'un ni l'autre n'a corrigé quoi que ce soit.
 */
class DoublonApresUnionTest {
    private lateinit var tmp: File

    @BeforeTest
    fun preparer() {
        tmp = Banc.dossierTemporaire()
    }

    @AfterTest
    fun nettoyer() {
        tmp.deleteRecursively()
    }

    private fun operateurQuiNoteLeRang1(nom: String, mb: Int): File {
        val b = Banc(File(tmp, nom).also { it.mkdirs() }, operateur = nom)
        val c = b.ouvrir("A1-tournee-depart")
        c.choisirPlacette("P001")
        c.versArbres("P001")
        b.noter("Hêtre", mb, 1)
        val zip = File(b.tmp, "$nom.zip")
        val verdict = JsonParser.parseString(
            io.github.pobsteta.ramulus.tournee.EchangeReel.rendre(b.travail, b.depart, b.racinePhotos, nom, "test", zip),
        ).asJsonObject
        assertNotEquals("refuse", verdict["verdict"].asString, "le paquet de $nom doit être rendable seul : $verdict")
        assertTrue(zip.exists())
        return zip
    }

    @Test
    fun `deux operateurs qui notent le meme rang donnent un doublon_arbre apres l'union`() {
        val a = operateurQuiNoteLeRang1("operateur-A", 1)
        val b = operateurQuiNoteLeRang1("operateur-B", 3)
        val depart = File(tmp, "operateur-A/tournees/EXEMPLE-2027-01/depart/deperis.gpkg")

        val sortie = File(tmp, "union.zip")
        val verdict = JsonParser.parseString(EchangeDeperis.fusionner(listOf(a, b), depart, "operateur-A", "test", sortie)).asJsonObject

        assertEquals("signale", verdict["verdict"].asString, verdict.toString())
        val motifs = verdict.getAsJsonArray("motifs").map { it.asString }.toSet()
        assertTrue("doublon_arbre" in motifs, "deux lignes du même rang, deux uuid, deux opérateurs : $motifs")
        // L'union garde les deux lignes (aucune n'est « la correction » de l'autre).
        val inst = JsonParser.parseString(EchangeDeperis.instantane(sortie)).asJsonObject.getAsJsonObject("tables")
        val rang1 = inst.getAsJsonObject("notations").getAsJsonArray("lignes").map { it.asJsonObject }
            .filter { it["plot_id"].asString == "P001" && it["rang"].asInt == 1 }
        assertEquals(2, rang1.size)
        assertEquals(setOf("operateur-A", "operateur-B"), rang1.map { it["operateur"].asString }.toSet())
    }

    @Test
    fun `le cas commun S1 reste signale doublon_arbre`() {
        val zip = File("../fixtures/echange/S1-doublon-arbre/EXEMPLE-2027-01.zip")
        val depart = File("../fixtures/echange/A1-tournee-depart/deperis.gpkg")
        val verdict = JsonParser.parseString(EchangeDeperis.controlerRendu(zip, depart)).asJsonObject
        assertEquals("signale", verdict["verdict"].asString)
        assertEquals(setOf("doublon_arbre"), verdict.getAsJsonArray("motifs").map { it.asString }.toSet())
    }
}
