package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile

/**
 * Tests propres de la mission 004, qui appellent `EchangeDeperis` directement (typé, sans la réflexion de 003) et
 * couvrent ce que les tests d'acceptation ne disent pas : le code `classe_saisie`, un fichier illisible, la fusion
 * avec un centre en conflit (F1), un rendu refusé qui n'écrase rien, un rendu qui ne touche pas la tournée de travail.
 */
class EchangeDeperisTest {
    private val communs = File("../fixtures/echange")
    private val depart1 = File(communs, "A1-tournee-depart/deperis.gpkg").canonicalFile
    private val depart2 = File(communs, "A2-tournee-contre-notation/deperis.gpkg").canonicalFile
    private val rendu3 = File(communs, "A3-rendu/EXEMPLE-2027-01.zip").canonicalFile

    private fun json(s: String): JsonObject = JsonParser.parseString(s).asJsonObject

    private fun attendu(): JsonObject = json(File(rendu3.parentFile, "EXEMPLE-2027-01.instantane.json").readText())

    private fun lignes(inst: JsonObject, table: String): List<JsonObject> =
        inst.getAsJsonObject("tables").getAsJsonObject(table).getAsJsonArray("lignes").map { it.asJsonObject }

    private fun saisie(l: JsonObject): String = l.deepCopy().also { it.remove("fid"); it.remove("classe") }.toString()

    private fun copie(source: File, dans: File, nom: String): File = File(dans, nom).also { source.copyTo(it, overwrite = true) }

    private fun motifs(verdict: String): Set<String> = json(verdict).getAsJsonArray("motifs").map { it.asString }.toSet()

    private fun statut(verdict: String): String = json(verdict)["verdict"].asString

    /** La saisie de A3 pour P001 : un centre, ses notes, la clôture, dans une copie de la tournée de départ. */
    private fun saisirP001(travail: File, operateur: String? = null, precision: Double? = null) {
        val inst = attendu()
        val centre = lignes(inst, "centres").first { it["plot_id"].asString == "P001" }.deepCopy()
        centre.remove("fid")
        if (precision != null) centre.addProperty("precision_m", precision)
        EchangeDeperis.enregistrerCentre(travail, centre.toString())
        for (n in lignes(inst, "notations").filter { it["plot_id"].asString == "P001" }) {
            val note = n.deepCopy()
            if (operateur != null) note.addProperty("operateur", operateur)
            note.add("photos", JsonArray())
            EchangeDeperis.enregistrerNotation(travail, saisie(note))
        }
        EchangeDeperis.cloturerPlacette(travail, "P001")
    }

    @Test
    fun `toute classe fournie est refusee avec le code classe_saisie, juste ou fausse, et le fichier reste intact`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        val octets = travail.readBytes()
        val note = lignes(attendu(), "notations").first()
        for (classe in listOf(note["classe"].asString, "F")) {
            val fournie = note.deepCopy().also { it.remove("fid"); it.addProperty("classe", classe) }
            val e = assertThrows(EchangeRefuse::class.java) { EchangeDeperis.enregistrerNotation(travail, fournie.toString()) }
            assertEquals(listOf("classe_saisie"), e.motifs)
            assertTrue(e.message!!.startsWith("classe_saisie"), e.message)
        }
        assertArrayEquals(octets, travail.readBytes(), "aucun octet écrit")
    }

    @Test
    fun `une classe a null n est pas une classe fournie`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        val note = lignes(attendu(), "notations").first().deepCopy().also { it.remove("fid"); it.add("classe", com.google.gson.JsonNull.INSTANCE) }
        val enregistree = json(EchangeDeperis.enregistrerNotation(travail, note.toString()))
        assertEquals(lignes(attendu(), "notations").first()["classe"].asString, enregistree["classe"].asString)
    }

    @Test
    fun `une classe vide ou faite de blancs compte comme absente et est calculee, sans refus`(@TempDir tmp: File) {
        val attendue = lignes(attendu(), "notations").first()
        for ((i, vide) in listOf("", " ", "  ", "\t \n").withIndex()) {
            val travail = copie(depart1, tmp, "travail$i.gpkg")
            val note = attendue.deepCopy().also { it.remove("fid"); it.addProperty("classe", vide) }
            val enregistree = json(EchangeDeperis.enregistrerNotation(travail, note.toString()))
            assertEquals(attendue["classe"].asString, enregistree["classe"].asString, "classe \"$vide\" : calculée par l'abaque")
            val relue = lignes(json(EchangeDeperis.instantane(travail)), "notations").single()
            assertEquals(attendue["classe"].asString, relue["classe"].asString)
        }
    }

    @Test
    fun `une classe remplie de lettres entourees de blancs reste une classe fournie, refusee`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        val octets = travail.readBytes()
        val note = lignes(attendu(), "notations").first().deepCopy().also { it.remove("fid"); it.addProperty("classe", " D ") }
        val e = assertThrows(EchangeRefuse::class.java) { EchangeDeperis.enregistrerNotation(travail, note.toString()) }
        assertEquals(listOf("classe_saisie"), e.motifs)
        assertArrayEquals(octets, travail.readBytes())
    }

    @Test
    fun `une ecriture sur une tournee qui porte deja un motif de refus est refusee et le fichier reste intact`(@TempDir tmp: File) {
        // R9 : colonne de type date, tournée déjà refusée à l'ouverture (règle stricte, Pascal : Q1 de 004).
        val refusee = File(communs, "R9-colonne-date/deperis.gpkg").canonicalFile
        val travail = copie(refusee, tmp, "travail.gpkg")
        val octets = travail.readBytes()
        val ouverture = EchangeDeperis.ouvrir(travail)
        assertEquals("refuse", statut(ouverture))
        val motif = motifs(ouverture).single()

        val noteValide = saisie(lignes(attendu(), "notations").first())
        val ecritures = listOf<() -> Unit>(
            { EchangeDeperis.enregistrerNotation(travail, noteValide) },
            { EchangeDeperis.cloturerPlacette(travail, "P001") },
        )
        for (ecriture in ecritures) {
            val e = assertThrows(EchangeRefuse::class.java) { ecriture() }
            assertTrue(motif in e.motifs, "le refus porte le motif de la tournée ($motif) : ${e.message}")
            assertArrayEquals(octets, travail.readBytes(), "aucun octet écrit")
        }
    }

    @Test
    fun `une colonne inconnue dans une saisie est refusee (structure), pas ignoree`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        val note = json(saisie(lignes(attendu(), "notations").first())).also { it.addProperty("couleur", "rouge") }
        val e = assertThrows(EchangeRefuse::class.java) { EchangeDeperis.enregistrerNotation(travail, note.toString()) }
        assertEquals(listOf("structure"), e.motifs)
    }

    @Test
    fun `cloturer une placette inconnue est refuse (rattachement_invalide) et ne change rien`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        val octets = travail.readBytes()
        val e = assertThrows(EchangeRefuse::class.java) { EchangeDeperis.cloturerPlacette(travail, "P999") }
        assertEquals(listOf("rattachement_invalide"), e.motifs)
        assertArrayEquals(octets, travail.readBytes())
    }

    @Test
    fun `un fichier qui n est pas un GeoPackage est refuse a l ouverture (pas_un_geopackage), sans exception`(@TempDir tmp: File) {
        val faux = File(tmp, "deperis.gpkg").also { it.writeText("ceci n'est pas une base SQLite") }
        val v = EchangeDeperis.ouvrir(faux)
        assertEquals("refuse", statut(v))
        assertEquals(setOf("pas_un_geopackage"), motifs(v))
        val zipFaux = File(tmp, "EXEMPLE.zip").also { it.writeText("pas un zip") }
        assertEquals(setOf("pas_un_geopackage"), motifs(EchangeDeperis.controlerRendu(zipFaux, depart1)))
    }

    @Test
    fun `le centre releve est ecrit dans l emprise de gpkg_contents, X est et Y nord`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        saisirP001(travail)
        val centre = lignes(attendu(), "centres").first { it["plot_id"].asString == "P001" }["geom"].asJsonObject
        Gpkg.ouvrir(travail, lectureSeule = true).use { cnx ->
            cnx.createStatement().use { st ->
                st.executeQuery("SELECT min_x, min_y, max_x, max_y FROM gpkg_contents WHERE table_name = 'centres'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(centre["x"].asDouble, rs.getDouble(1), 0.0)
                    assertEquals(centre["y"].asDouble, rs.getDouble(2), 0.0)
                    assertEquals(centre["x"].asDouble, rs.getDouble(3), 0.0)
                    assertEquals(centre["y"].asDouble, rs.getDouble(4), 0.0)
                }
            }
        }
        assertEquals("accepte", statut(EchangeDeperis.ouvrir(travail)), "la tournée en cours reste lisible")
    }

    @Test
    fun `rendre ne modifie pas la tournee de travail et donne deux fois le meme zip, octet pour octet`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        saisirP001(travail)
        val racine = File(tmp, "appareil").also { it.mkdirs() }
        val avant = travail.readBytes()
        val un = File(tmp, "un.zip")
        val deux = File(tmp, "deux.zip")
        EchangeDeperis.rendre(travail, depart1, racine, "operateur-A", "exemple", un)
        EchangeDeperis.rendre(travail, depart1, racine, "operateur-A", "exemple", deux)
        assertArrayEquals(avant, travail.readBytes(), "la tournée de travail n'est pas touchée par le rendu")
        assertArrayEquals(un.readBytes(), deux.readBytes())
        ZipFile(un).use { z -> assertEquals(listOf("deperis.gpkg"), z.entries().asSequence().map { it.name }.toList()) }
    }

    @Test
    fun `un rendu refuse n ecrit rien et laisse en place le zip deja rendu`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        val inst = attendu()
        // Une note qui cite une photo que l'appareil n'a pas : photo_absente.
        val note = lignes(inst, "notations").first { it["photos"].asJsonArray.size() > 0 }
        EchangeDeperis.enregistrerNotation(travail, saisie(note))
        val sortie = File(tmp, "EXEMPLE-2027-01.zip").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val v = EchangeDeperis.rendre(travail, depart1, File(tmp, "vide").also { it.mkdirs() }, "operateur-A", "exemple", sortie)
        assertEquals("refuse", statut(v))
        assertEquals(setOf("photo_absente"), motifs(v))
        assertArrayEquals(byteArrayOf(1, 2, 3), sortie.readBytes())
    }

    @Test
    fun `un rendu dont l operateur est saisi en NFD est ecrit en NFC`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp, "travail.gpkg")
        saisirP001(travail)
        val nfd = java.text.Normalizer.normalize("opérateur-é", java.text.Normalizer.Form.NFD)
        val sortie = File(tmp, "r.zip")
        val v = EchangeDeperis.rendre(travail, depart1, File(tmp, "vide").also { it.mkdirs() }, nfd, "exemple", sortie)
        assertEquals("accepte", statut(v))
        val t = lignes(json(EchangeDeperis.instantane(sortie)), "tournee").single()
        assertEquals(java.text.Normalizer.normalize(nfd, java.text.Normalizer.Form.NFC), t["operateur"].asString)
    }

    @Test
    fun `F1 - un centre en conflit est ecarte, la placette notee est gardee avec ses notes, le conflit est signale`(@TempDir tmp: File) {
        // Deux opérateurs relèvent le centre de P001 (même uuid, deux précisions) et gardent les mêmes notes.
        val a = copie(depart1, tmp, "a.gpkg")
        val b = copie(depart1, tmp, "b.gpkg")
        saisirP001(a, precision = 2.0)
        saisirP001(b, precision = 3.5)
        val racine = File(tmp, "vide").also { it.mkdirs() }
        val za = File(tmp, "a.zip")
        val zb = File(tmp, "b.zip")
        assertEquals("accepte", statut(EchangeDeperis.rendre(a, depart1, racine, "operateur-A", "exemple", za)))
        assertEquals("accepte", statut(EchangeDeperis.rendre(b, depart1, racine, "operateur-B", "exemple", zb)))

        val sortie = File(tmp, "union.zip")
        val v = EchangeDeperis.fusionner(listOf(za, zb), depart1, "operateur-A", "exemple", sortie)

        assertEquals("signale", statut(v))
        assertEquals(setOf("uuid_contenu_different"), motifs(v))
        val ecartes = json(v).getAsJsonArray("ecartes").map { it.asJsonObject["objet"].asString }
        assertEquals(listOf("centres"), ecartes)
        assertEquals(listOf("P001"), json(v).getAsJsonArray("placettes_signalees").map { it.asString }, "placette signalée")
        assertEquals("P001", json(v).getAsJsonArray("ecartes").single().asJsonObject["plot_id"].asString)
        assertTrue(json(v).getAsJsonArray("messages").any { it.asString.contains("placette P001 signalée") })
        assertTrue(sortie.isFile, "un signalement n'empêche pas d'écrire le paquet")
        val union = json(EchangeDeperis.instantane(sortie))
        assertTrue(lignes(union, "centres").isEmpty(), "ni l'une ni l'autre version du centre")
        val notesP001 = lignes(union, "notations").filter { it["plot_id"].asString == "P001" }
        assertEquals(4, notesP001.size, "les quatre notes de P001 sont gardées")
        assertEquals("notee", lignes(union, "placettes").first { it["plot_id"].asString == "P001" }["etat"].asString)
    }

    @Test
    fun `F1 - la placette au centre ecarte est signalee quel que soit son etat`(@TempDir tmp: File) {
        // Deux opérateurs relèvent le centre de P001 sans le clore : l'état n'est pas « notee », la placette est signalée.
        val a = copie(depart1, tmp, "a.gpkg")
        val b = copie(depart1, tmp, "b.gpkg")
        val centre = lignes(attendu(), "centres").first { it["plot_id"].asString == "P001" }.deepCopy().also { it.remove("fid") }
        EchangeDeperis.enregistrerCentre(a, centre.deepCopy().also { it.addProperty("precision_m", 2.0) }.toString())
        EchangeDeperis.enregistrerCentre(b, centre.deepCopy().also { it.addProperty("precision_m", 3.5) }.toString())
        val racine = File(tmp, "vide").also { it.mkdirs() }
        val za = File(tmp, "a.zip")
        val zb = File(tmp, "b.zip")
        EchangeDeperis.rendre(a, depart1, racine, "operateur-A", "exemple", za)
        EchangeDeperis.rendre(b, depart1, racine, "operateur-B", "exemple", zb)
        val sortie = File(tmp, "union.zip")
        val v = EchangeDeperis.fusionner(listOf(za, zb), depart1, "operateur-A", "exemple", sortie)
        assertEquals(listOf("P001"), json(v).getAsJsonArray("placettes_signalees").map { it.asString })
        val etat = lignes(json(EchangeDeperis.instantane(sortie)), "placettes").first { it["plot_id"].asString == "P001" }["etat"]
        assertTrue(etat == null || etat.isJsonNull, "P001 n'a jamais été close : son état reste vide")
    }

    @Test
    fun `la fusion d un paquet refuse n ecrit rien`(@TempDir tmp: File) {
        val sortie = File(tmp, "union.zip")
        val faux = File(tmp, "faux.zip").also { it.writeText("pas un zip") }
        val v = EchangeDeperis.fusionner(listOf(faux), depart1, "operateur-A", "exemple", sortie)
        assertEquals("refuse", statut(v))
        assertFalse(sortie.exists())
    }

    @Test
    fun `la contre-notation ne recopie ni ne modifie le centre du premier operateur`(@TempDir tmp: File) {
        val travail = copie(depart2, tmp, "travail.gpkg")
        val avant = json(EchangeDeperis.instantane(travail))
        val e = assertThrows(EchangeRefuse::class.java) {
            EchangeDeperis.enregistrerCentre(travail, """{"uuid":"01a70000-0000-7000-8000-0000000000aa","plot_id":"P002","geom":{"x":856859.0,"y":6670361.0},"precision_m":2.0,"nb_mesures":10,"source":"telephone","date_releve":"2027-07-06T08:00:00.000Z","operateur":"operateur-C"}""")
        }
        assertEquals(listOf("plan_modifie"), e.motifs)
        assertNull(premiereDifference(avant, json(EchangeDeperis.instantane(travail))))
    }
}
