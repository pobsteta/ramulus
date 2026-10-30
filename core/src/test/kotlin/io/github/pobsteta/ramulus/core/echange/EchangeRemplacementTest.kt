package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.sql.DriverManager

/**
 * Mission 015 : corriger une note ou relever de nouveau un centre **remplace** la ligne dans la copie de travail
 * (`remplacerNotation`, `remplacerCentre`). Une seule ligne par arbre et par centre ; aucun `doublon_arbre` fabriqué.
 *
 * Coordonnées : EPSG:2154, X = est, Y = nord, en mètres. Le site synthétique des fixtures est en Côte-d'Or.
 */
class EchangeRemplacementTest {
    private val communs = File("../fixtures/echange")
    private val depart1 = File(communs, "A1-tournee-depart/deperis.gpkg").canonicalFile
    private val depart2 = File(communs, "A2-tournee-contre-notation/deperis.gpkg").canonicalFile
    private val rendu3 = File(communs, "A3-rendu/EXEMPLE-2027-01.zip").canonicalFile

    private fun json(s: String): JsonObject = JsonParser.parseString(s).asJsonObject
    private fun attendu(): JsonObject = json(File(rendu3.parentFile, "EXEMPLE-2027-01.instantane.json").readText())
    private fun lignes(inst: JsonObject, table: String): List<JsonObject> =
        inst.getAsJsonObject("tables").getAsJsonObject(table)?.getAsJsonArray("lignes")?.map { it.asJsonObject } ?: emptyList()
    private fun copie(source: File, dans: File, nom: String): File = File(dans, nom).also { source.copyTo(it, overwrite = true) }
    private fun inst(f: File): JsonObject = json(EchangeDeperis.instantane(f))
    private fun motifs(verdict: String): Set<String> = json(verdict).getAsJsonArray("motifs").map { it.asString }.toSet()

    private fun note(uuid: String, plot: String = "P001", rang: Int = 2, mb: Int = 1, second: Int = 1) = JsonObject().apply {
        addProperty("uuid", uuid); addProperty("plot_id", plot); addProperty("rang", rang)
        addProperty("essence", "Hêtre"); addProperty("type", "FEUILLU"); addProperty("mb", mb); addProperty("second", second)
        addProperty("critere", "MR"); addProperty("saison", "ETE"); addProperty("date_notation", "2027-07-06T09:00:00.000Z")
        addProperty("operateur", "operateur-A"); add("photos", JsonArray())
    }

    /** Un centre de P001 près du point prévu de la fixture (Côte-d'Or), X = est, Y = nord, EPSG:2154. */
    private fun centre(uuid: String, x: Double, y: Double, plot: String = "P001") = JsonObject().apply {
        addProperty("uuid", uuid); addProperty("plot_id", plot); addProperty("precision_m", 4.0); addProperty("nb_mesures", 10)
        addProperty("source", "telephone"); addProperty("date_releve", "2027-07-06T08:00:00.000Z"); addProperty("operateur", "operateur-A")
        add("geom", JsonObject().apply { addProperty("x", x); addProperty("y", y) })
    }

    private fun prevu(f: File, plot: String): Pair<Double, Double> {
        val g = lignes(inst(f), "placettes").first { it["plot_id"].asString == plot }.getAsJsonObject("geom")
        return g["x"].asDouble to g["y"].asDouble
    }

    private val U1 = "01a70000-0000-7000-8000-000000000001"
    private val U2 = "01a70000-0000-7000-8000-000000000002"
    private val U3 = "01a70000-0000-7000-8000-000000000003"

    @Test
    fun `corriger une note la remplace, une seule ligne pour ce rang, de nouvel uuid et de nouvelles notes`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        EchangeDeperis.enregistrerNotation(travail, note(U1, rang = 1).toString())
        EchangeDeperis.enregistrerNotation(travail, note(U2, rang = 2, mb = 1).toString())
        val ecrite = json(EchangeDeperis.remplacerNotation(travail, U2, note(U3, rang = 2, mb = 4).toString()))
        assertEquals(U3, ecrite["uuid"].asString)
        val notes = lignes(inst(travail), "notations")
        assertEquals(listOf(U1, U3), notes.map { it["uuid"].asString }.sorted())
        val rang2 = notes.single { it["rang"].asInt == 2 }
        assertEquals(4, rang2["mb"].asInt)
        assertEquals(ecrite["classe"].asString, rang2["classe"].asString, "la classe se recalcule par l'abaque")
        assertFalse("doublon_arbre" in motifs(EchangeDeperis.ouvrir(travail)), "une correction n'est pas un doublon")
    }

    @Test
    fun `deux notes du meme rang ecrites sans remplacement restent un doublon_arbre`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        EchangeDeperis.enregistrerNotation(travail, note(U1, rang = 2).toString())
        EchangeDeperis.enregistrerNotation(travail, note(U2, rang = 2).toString())
        assertTrue("doublon_arbre" in motifs(EchangeDeperis.ouvrir(travail)))
    }

    @Test
    fun `une correction refusee ne retire rien et laisse le fichier intact`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        EchangeDeperis.enregistrerNotation(travail, note(U1, rang = 2).toString())
        val octets = travail.readBytes()
        val cas = listOf(
            Triple(U1, note(U1, rang = 2, mb = 3), "uuid_duplique"), // même uuid : pas une nouvelle note
            Triple(U1, note(U2, rang = 3), "rattachement_invalide"), // autre rang
            Triple(U1, note(U2, plot = "P002", rang = 2), "rattachement_invalide"), // autre placette
            Triple(U3, note(U2, rang = 2), "rattachement_invalide"), // note à corriger absente
            Triple(U1, note(U2, rang = 2, mb = 9), "note_hors_bornes"), // le contrôle habituel joue
        )
        for ((ancien, nouvelle, motif) in cas) {
            val e = assertThrows(EchangeRefuse::class.java) { EchangeDeperis.remplacerNotation(travail, ancien, nouvelle.toString()) }
            assertEquals(listOf(motif), e.motifs, e.message)
            assertArrayEquals(octets, travail.readBytes(), "aucun octet écrit ($motif)")
        }
        assertEquals(listOf(U1), lignes(inst(travail), "notations").map { it["uuid"].asString })
    }

    @Test
    fun `en contre-notation la correction remplace dans contre_notations`(@TempDir tmp: File) {
        val travail = copie(depart2, tmp, "travail.gpkg")
        val plot = lignes(inst(travail), "placettes").first()["plot_id"].asString
        EchangeDeperis.enregistrerNotation(travail, note(U1, plot = plot, rang = 1).toString())
        EchangeDeperis.remplacerNotation(travail, U1, note(U2, plot = plot, rang = 1, mb = 2).toString())
        val notes = lignes(inst(travail), "contre_notations")
        assertEquals(listOf(U2), notes.map { it["uuid"].asString })
        assertEquals(2, notes.single()["mb"].asInt)
    }

    @Test
    fun `relever de nouveau un centre le remplace et rend l'uuid retire`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        val (x, y) = prevu(travail, "P001")
        assertEquals("[]", EchangeDeperis.remplacerCentre(travail, centre(U1, x + 3.0, y - 2.0).toString()), "premier relevé")
        val retires = JsonParser.parseString(EchangeDeperis.remplacerCentre(travail, centre(U2, x + 1.0, y + 1.0).toString())).asJsonArray
        assertEquals(listOf(U1), retires.map { it.asString })
        val centres = lignes(inst(travail), "centres")
        assertEquals(1, centres.size, "un seul centre pour la placette")
        assertEquals(U2, centres.single()["uuid"].asString)
        assertEquals(x + 1.0, centres.single().getAsJsonObject("geom")["x"].asDouble, 1e-6, "X = est, la seconde position")
        assertEquals(y + 1.0, centres.single().getAsJsonObject("geom")["y"].asDouble, 1e-6, "Y = nord, la seconde position")
    }

    @Test
    fun `remplacer un centre ne touche pas celui d'une autre placette et recalcule l'emprise`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        val (x1, y1) = prevu(travail, "P001")
        val (x2, y2) = prevu(travail, "P002")
        EchangeDeperis.enregistrerCentre(travail, centre(U1, x1 + 20.0, y1 + 20.0).toString())
        EchangeDeperis.enregistrerCentre(travail, centre(U2, x2, y2, plot = "P002").toString())
        EchangeDeperis.remplacerCentre(travail, centre(U3, x1, y1).toString())
        val centres = lignes(inst(travail), "centres").associate { it["plot_id"].asString to it["uuid"].asString }
        assertEquals(mapOf("P001" to U3, "P002" to U2), centres)
        DriverManager.getConnection("jdbc:sqlite:${travail.path}").use { cnx ->
            cnx.createStatement().executeQuery("SELECT min_x, max_x, min_y, max_y FROM gpkg_contents WHERE table_name = 'centres'").use { rs ->
                assertTrue(rs.next())
                assertEquals(minOf(x1, x2), rs.getDouble(1), 1e-6)
                assertEquals(maxOf(x1, x2), rs.getDouble(2), 1e-6, "le point retiré (X + 20 m) ne porte plus l'emprise")
                assertEquals(minOf(y1, y2), rs.getDouble(3), 1e-6)
                assertEquals(maxOf(y1, y2), rs.getDouble(4), 1e-6)
            }
        }
    }

    @Test
    fun `un centre de remplacement hors de France est refuse et l'ancien reste`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        val (x, y) = prevu(travail, "P001")
        EchangeDeperis.enregistrerCentre(travail, centre(U1, x, y).toString())
        val octets = travail.readBytes()
        // X et Y inversés : le point tombe hors de l'emprise de la France métropolitaine en EPSG:2154.
        val e = assertThrows(EchangeRefuse::class.java) { EchangeDeperis.remplacerCentre(travail, centre(U2, y, x).toString()) }
        assertTrue("hors_france" in e.motifs, e.message)
        assertArrayEquals(octets, travail.readBytes())
        assertEquals(listOf(U1), lignes(inst(travail), "centres").map { it["uuid"].asString })
    }

    @Test
    fun `en contre-notation le centre reste en lecture, meme par remplacement`(@TempDir tmp: File) {
        val travail = copie(depart2, tmp, "travail.gpkg")
        val c = lignes(inst(travail), "centres").first()
        val g = c.getAsJsonObject("geom")
        val e = assertThrows(EchangeRefuse::class.java) {
            EchangeDeperis.remplacerCentre(travail, centre(U3, g["x"].asDouble, g["y"].asDouble, plot = c["plot_id"].asString).toString())
        }
        assertEquals(listOf("plan_modifie"), e.motifs)
    }
}
