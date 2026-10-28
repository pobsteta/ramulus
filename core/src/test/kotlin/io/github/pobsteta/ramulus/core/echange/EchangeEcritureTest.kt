package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.deperis.Abaque
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.text.Normalizer

/**
 * L'ÉCRITURE : Ramulus complète la tournée de Nemeton (centres, notes), la clôture et la rend. Critères 2 à 6 de la
 * spec § 5. Le paquet rendu doit être, en instantané, EXACTEMENT celui des cas communs (A3, A4), et tout ce que
 * Ramulus refuse d'écrire ne change pas le fichier.
 *
 * Désactivés jusqu'à la mission 004 (`ActiveeParLaMission004`). Pour les voir échouer : `RAMULUS_ECHANGE_EXIGER=1`.
 * Non couvert ici : le fonctionnement hors réseau et l'écriture avec sqlite-jdbc plutôt que le SQLite d'Android
 * (ils ne se voient pas sur la JVM de `:core`) ; voir le rendu de la mission.
 */
@ExtendWith(ActiveeParLaMission004::class)
class EchangeEcritureTest {

    private val communs = File("../fixtures/echange")
    private val depart1 = File(communs, "A1-tournee-depart/deperis.gpkg").canonicalFile
    private val depart2 = File(communs, "A2-tournee-contre-notation/deperis.gpkg").canonicalFile
    private val departFuture = File(communs, "A6-version-suivante/deperis.gpkg").canonicalFile
    private val rendu3 = File(communs, "A3-rendu/EXEMPLE-2027-01.zip").canonicalFile
    private val rendu4 = File(communs, "A4-rendu-contre-notation/EXEMPLE-2027-01-CN.zip").canonicalFile

    // ---------------------------------------------------------------------------------------------------------
    // Outils
    // ---------------------------------------------------------------------------------------------------------

    private fun copie(source: File, dans: File, nom: String = "travail.gpkg"): File =
        File(dans, nom).also { source.copyTo(it, overwrite = true) }

    private fun lignes(inst: JsonObject, table: String): List<JsonObject> =
        inst.getAsJsonObject("tables").getAsJsonObject(table).getAsJsonArray("lignes").map { it.asJsonObject }

    private fun sansFid(ligne: JsonObject): JsonObject = ligne.deepCopy().also { it.remove("fid") }

    private fun saisie(ligne: JsonObject): JsonObject = sansFid(ligne).also { it.remove("classe") }

    /** Rejoue la saisie d'un opérateur dans une copie de `depart`, avec les lignes de l'instantané attendu. */
    private fun saisir(travail: File, attendu: JsonObject, centres: Boolean) {
        val table = if ("notations" in attendu.getAsJsonObject("tables").keySet()) "notations" else "contre_notations"
        if (centres) lignes(attendu, "centres").forEach { PasserelleEchange.enregistrerCentre(travail, sansFid(it)) }
        for (n in lignes(attendu, table)) {
            val enregistree = PasserelleEchange.enregistrerNotation(travail, saisie(n))
            assertEquals(n["classe"].asString, enregistree["classe"].asString, "classe de ${n["uuid"]}")
        }
        lignes(attendu, "placettes").filter { it["etat"].asString == "notee" }
            .forEach { PasserelleEchange.cloturerPlacette(travail, it["plot_id"].asString) }
    }

    private fun tournee(inst: JsonObject): JsonObject = lignes(inst, "tournee").single()

    // ---------------------------------------------------------------------------------------------------------
    // Le cas nominal : de la tournée de départ au paquet rendu
    // ---------------------------------------------------------------------------------------------------------

    @Test
    fun `A1 vers A3 - la tournee de depart saisie, cloturee et rendue donne exactement le paquet de A3`(@TempDir tmp: File) {
        val attendu = Cas.instantaneAttendu(rendu3)
        val travail = copie(depart1, tmp)
        val racine = File(tmp, "appareil").also { extrairePhotos(rendu3, it) }
        saisir(travail, attendu, centres = true)
        val sortie = File(tmp, "EXEMPLE-2027-01.zip")

        val v = PasserelleEchange.rendre(
            travail, depart1, racine, tournee(attendu)["operateur"].asString, tournee(attendu)["version_ramulus"].asString, sortie,
        )

        assertEquals("accepte", v.statut)
        assertEquals(emptySet<String>(), v.motifs)
        assertNull(premiereDifference(attendu, PasserelleEchange.instantane(sortie)), "paquet rendu relu")
        assertEquals(emptySet<String>(), PasserelleEchange.controlerRendu(sortie, depart1).motifs)
    }

    @Test
    fun `A2 vers A4 - la contre-notation va dans contre_notations, jamais dans notations, et le centre reste celui du premier operateur`(@TempDir tmp: File) {
        val attendu = Cas.instantaneAttendu(rendu4)
        val travail = copie(depart2, tmp)
        val racine = File(tmp, "appareil").also { extrairePhotos(rendu4, it) }
        saisir(travail, attendu, centres = false)
        val sortie = File(tmp, "EXEMPLE-2027-01-CN.zip")

        val v = PasserelleEchange.rendre(
            travail, depart2, racine, tournee(attendu)["operateur"].asString, tournee(attendu)["version_ramulus"].asString, sortie,
        )

        assertEquals("accepte", v.statut)
        assertEquals(emptySet<String>(), v.motifs)
        val rendu = PasserelleEchange.instantane(sortie)
        assertEquals(setOf("centres", "contre_notations", "placettes", "tournee"), rendu.getAsJsonObject("tables").keySet())
        assertNull(premiereDifference(attendu, rendu), "paquet de contre-notation relu")
    }

    @Test
    fun `Ramulus ne modifie dans placettes que etat, et dans tournee que operateur et version_ramulus`(@TempDir tmp: File) {
        val attendu = Cas.instantaneAttendu(rendu3)
        val travail = copie(depart1, tmp)
        saisir(travail, attendu, centres = true)
        val sortie = File(tmp, "r.zip")
        PasserelleEchange.rendre(travail, depart1, File(tmp, "appareil").also { extrairePhotos(rendu3, it) }, "operateur-A", "exemple", sortie)

        val avant = PasserelleEchange.instantane(depart1)
        val apres = PasserelleEchange.instantane(sortie)
        assertEquals(avant["application_id"], apres["application_id"])
        assertEquals(avant["user_version"], apres["user_version"], "user_version écrit par Nemeton, jamais changé")
        fun sans(l: List<JsonObject>, vararg cles: String) = l.map { it.deepCopy().also { o -> cles.forEach(o::remove) } }
        assertEquals(sans(lignes(avant, "placettes"), "etat"), sans(lignes(apres, "placettes"), "etat"))
        assertEquals(sans(lignes(avant, "tournee"), "operateur", "version_ramulus"), sans(lignes(apres, "tournee"), "operateur", "version_ramulus"))
        assertEquals(avant.getAsJsonObject("tables").getAsJsonObject("placettes")["colonnes"], apres.getAsJsonObject("tables").getAsJsonObject("placettes")["colonnes"])
        assertEquals(setOf("notee"), lignes(apres, "placettes").map { it["etat"].asString }.toSet())
        assertEquals("operateur-A", tournee(apres)["operateur"].asString)
        assertEquals("exemple", tournee(apres)["version_ramulus"].asString)
    }

    @Test
    fun `rendre deux fois la meme tournee donne deux fois le meme paquet`(@TempDir tmp: File) {
        val attendu = Cas.instantaneAttendu(rendu3)
        val travail = copie(depart1, tmp)
        val racine = File(tmp, "appareil").also { extrairePhotos(rendu3, it) }
        saisir(travail, attendu, centres = true)
        val un = File(tmp, "un.zip")
        val deux = File(tmp, "deux.zip")
        PasserelleEchange.rendre(travail, depart1, racine, "operateur-A", "exemple", un)
        PasserelleEchange.rendre(travail, depart1, racine, "operateur-A", "exemple", deux)
        assertNull(premiereDifference(PasserelleEchange.instantane(un), PasserelleEchange.instantane(deux)))
    }

    // ---------------------------------------------------------------------------------------------------------
    // Critère 6 : la classe est celle de l'abaque
    // ---------------------------------------------------------------------------------------------------------

    private fun noteVierge(essence: String, type: String, critere: String, mb: Int, second: Int, i: Int): JsonObject =
        JsonParser.parseString(
            """{"uuid":"01a70000-0000-7000-8000-%012x","plot_id":"P001","rang":${1 + i % 4},"repere":"t$i","essence":"$essence",
            "type":"$type","mb":$mb,"second":$second,"critere":"$critere","saison":"ETE","date_notation":"2027-07-06T08:10:00.000Z",
            "operateur":"operateur-A","photos":[],"ia_premiere_mb":null,"ia_premiere_second":null,"ia_mb":null,"ia_second":null,
            "ia_confiance":null,"ia_modele":null}""".format(i),
        ).asJsonObject

    @TestFactory
    fun `la classe enregistree est celle de l abaque pour les 36 couples de notes, feuillus et resineux`(@TempDir tmp: File): List<DynamicTest> {
        val travail = copie(depart1, tmp)
        var i = 0
        return listOf(Triple("Chêne sessile", "FEUILLU", "MR"), Triple("Sapin", "RESINEUX", "MA")).flatMap { (essence, type, critere) ->
            (0..5).flatMap { mb ->
                (0..5).map { second ->
                    val n = ++i
                    dynamicTest("$essence MB $mb, $critere $second") {
                        val ligne = PasserelleEchange.enregistrerNotation(travail, noteVierge(essence, type, critere, mb, second, n))
                        assertEquals(Abaque.classe(mb, second).name, ligne["classe"].asString)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Les refus à l'écriture : le code du motif dans le message, et le fichier intact
    // ---------------------------------------------------------------------------------------------------------

    /** `motif` : le code du catalogue que le message d'erreur doit citer (`null` : on exige le refus seul ; plus aucun refus sans code depuis T2 bis). */
    private class Refus(val nom: String, val motif: String?, val nouvelle: JsonObject.() -> JsonObject)

    private val premiereNote: JsonObject by lazy { saisie(lignes(Cas.instantaneAttendu(rendu3), "notations").first()) }

    /** La classe que l'abaque donne à `premiereNote` (celle de A3) : la fournir quand même doit être refusé. */
    private val classeJuste: String by lazy { lignes(Cas.instantaneAttendu(rendu3), "notations").first()["classe"].asString }

    private fun JsonObject.avec(cle: String, valeur: Any?): JsonObject = deepCopy().also {
        when (valeur) {
            null -> it.add(cle, com.google.gson.JsonNull.INSTANCE)
            is Number -> it.addProperty(cle, valeur)
            is String -> it.addProperty(cle, valeur)
            is JsonArray -> it.add(cle, valeur)
            else -> error("type")
        }
    }

    private val refusDeNotes = listOf(
        Refus("MB à 6 (hors de 0 à 5)", "note_hors_bornes") { avec("mb", 6) },
        Refus("MR à -1", "note_hors_bornes") { avec("second", -1) },
        Refus("essence hors des 19 (Platane)", "essence_hors_referentiel") { avec("essence", "Platane") },
        Refus("essence sans ses accents (Cedre)", "essence_hors_referentiel") { avec("essence", "Cedre").avec("type", "RESINEUX").avec("critere", "MA") },
        Refus("feuillu déclaré résineux", "type_incoherent") { avec("type", "RESINEUX").avec("critere", "MA") },
        Refus("MA pour un feuillu", "critere_incoherent") { avec("critere", "MA") },
        // Pascal, 2026-10-28 (T2 bis) : la classe ne se saisit pas, qu'elle soit juste ou fausse : classe_saisie.
        Refus("classe fournie, fausse (la classe ne se saisit pas)", "classe_saisie") { avec("classe", if (classeJuste == "A") "B" else "A") },
        Refus("classe fournie, juste (la classe ne se saisit pas)", "classe_saisie") { avec("classe", classeJuste) },
        Refus("colonnes de l'IA à moitié remplies", "ia_incomplete") { avec("ia_mb", 2) },
        Refus("placette inconnue de la tournée", "rattachement_invalide") { avec("plot_id", "P999") },
        Refus("rang au-delà de nb_arbres (4)", "rattachement_invalide") { avec("rang", 5) },
    )

    @TestFactory
    fun `une note invalide est refusee avec le code du motif et laisse le fichier intact`(@TempDir tmp: File): List<DynamicTest> =
        refusDeNotes.mapIndexed { i, r ->
            dynamicTest("${r.motif ?: "refus sans code fixé"} : ${r.nom}") {
                val travail = copie(depart1, tmp, "travail$i.gpkg")
                val avant = PasserelleEchange.instantane(travail)
                val e = runCatching { PasserelleEchange.enregistrerNotation(travail, r.nouvelle(premiereNote)) }.exceptionOrNull()
                assertTrue(e != null, "aucune exception : la note a été acceptée")
                r.motif?.let { assertTrue(it in (e?.message ?: ""), "le message doit citer $it : ${e?.message}") }
                if (r.motif == "classe_saisie") {
                    assertFalse("classe_differente_abaque" in (e?.message ?: ""), "classe_differente_abaque est le motif de la lecture d'un paquet (R2), pas de l'enregistrement : ${e?.message}")
                }
                assertNull(premiereDifference(avant, PasserelleEchange.instantane(travail)), "fichier modifié malgré le refus")
            }
        }

    // Pascal, 2026-10-28 (Q4 de la mission 010 ; aller-retour 1/3 de 014) : une classe absente, JSON null, vide ("")
    // ou faite de blancs ("  ") compte comme absente : calculée par l'abaque, sans refus (et sans classe_saisie).
    private class ClasseNonSaisie(val nom: String, val note: JsonObject.() -> JsonObject, val cle: Boolean)

    @TestFactory
    fun `une classe absente, null, vide ou de blancs est acceptee et calculee par l abaque`(@TempDir tmp: File): List<DynamicTest> =
        listOf(
            ClasseNonSaisie("absente", { this }, false),
            ClasseNonSaisie("null", { avec("classe", null) }, true),
            ClasseNonSaisie("vide", { avec("classe", "") }, true),
            ClasseNonSaisie("de blancs", { avec("classe", "  ") }, true),
        ).map { c ->
            dynamicTest("classe ${c.nom} : acceptée, classe calculée = $classeJuste") {
                val travail = copie(depart1, tmp, "classe-${c.nom.replace(' ', '-')}.gpkg")
                val note = c.note(premiereNote)
                assertEquals(c.cle, note.has("classe"), "le test suppose la clé ${if (c.cle) "présente" else "absente"}")
                val ligne = runCatching { PasserelleEchange.enregistrerNotation(travail, note) }
                    .getOrElse { fail("classe ${c.nom} refusée (elle compte comme absente) : ${it.message}") }
                assertEquals(classeJuste, ligne["classe"].asString, "classe rendue par l'enregistrement")
                val relue = lignes(PasserelleEchange.instantane(travail), "notations").single()
                assertEquals(classeJuste, relue["classe"].asString, "classe relue dans le fichier")
            }
        }

    @TestFactory
    fun `une classe vide est calculee pour les 36 couples de notes, feuillus et resineux`(@TempDir tmp: File): List<DynamicTest> {
        val travail = copie(depart1, tmp)
        var i = 100
        return listOf(Triple("Chêne sessile", "FEUILLU", "MR"), Triple("Sapin", "RESINEUX", "MA")).flatMap { (essence, type, critere) ->
            (0..5).flatMap { mb ->
                (0..5).map { second ->
                    val n = ++i
                    dynamicTest("$essence MB $mb, $critere $second, classe vide") {
                        val ligne = PasserelleEchange.enregistrerNotation(travail, noteVierge(essence, type, critere, mb, second, n).avec("classe", ""))
                        assertEquals(Abaque.classe(mb, second).name, ligne["classe"].asString)
                    }
                }
            }
        }
    }

    // Pascal, 2026-10-28 (T1) : un texte saisi en Unicode décomposé est normalisé en NFC à l'enregistrement, sans refus.
    // (Le refus texte_non_nfc reste celui de la LECTURE d'un fichier rendu : cas R12, EchangeCasCommunsTest.)
    @Test
    fun `un texte saisi en Unicode decompose (NFD) est enregistre et relu en NFC, sans refus`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp)
        val nfd = Normalizer.normalize("opérateur-é", Normalizer.Form.NFD)
        val nfc = Normalizer.normalize(nfd, Normalizer.Form.NFC)
        assertFalse(nfd == nfc, "le test suppose deux écritures différentes")

        val ligne = PasserelleEchange.enregistrerNotation(travail, premiereNote.avec("operateur", nfd).avec("repere", Normalizer.normalize("é", Normalizer.Form.NFD)))

        assertEquals(nfc, ligne["operateur"].asString)
        assertEquals("é", ligne["repere"].asString)
        val relue = lignes(PasserelleEchange.instantane(travail), "notations").single()
        assertEquals(nfc, relue["operateur"].asString, "opérateur relu dans le fichier")
        assertEquals("é", relue["repere"].asString, "repère relu dans le fichier")
        assertTrue(Normalizer.isNormalized(relue["operateur"].asString, Normalizer.Form.NFC))
    }

    private fun centre(x: Double, y: Double): JsonObject = sansFid(lignes(Cas.instantaneAttendu(rendu3), "centres").first()).also {
        it.add("geom", JsonParser.parseString("""{"x":$x,"y":$y}""").asJsonObject)
    }

    @TestFactory
    fun `un centre hors de France est refuse - le piege de la Somalie`(@TempDir tmp: File): List<DynamicTest> = listOf(
        // Valeurs de R3a, R3b (fixtures/echange) : Mogadiscio projeté en Lambert-93 ; X et Y du centre de P001 permutés.
        Triple("Mogadiscio (45,3418° E, 2,0469° N) projeté en Lambert-93", 6552517.43, 2808067.4),
        Triple("X et Y inversés (nord en X, est en Y)", 6670349.24, 856563.43),
        Triple("degrés écrits à la place des mètres (5,065° E, 47,115° N)", 5.065, 47.115),
    ).mapIndexed { i, (nom, x, y) ->
        dynamicTest("hors_france : $nom") {
            val travail = copie(depart1, tmp, "centre$i.gpkg")
            val avant = PasserelleEchange.instantane(travail)
            val e = runCatching { PasserelleEchange.enregistrerCentre(travail, centre(x, y)) }.exceptionOrNull()
            assertTrue(e != null, "aucune exception : le centre a été accepté")
            assertTrue("hors_france" in (e?.message ?: ""), "le message doit citer hors_france : ${e?.message}")
            assertNull(premiereDifference(avant, PasserelleEchange.instantane(travail)))
        }
    }

    @Test
    fun `un centre de France est accepte et relu au meme endroit - X est l est, Y le nord`(@TempDir tmp: File) {
        val travail = copie(depart1, tmp)
        PasserelleEchange.enregistrerCentre(travail, centre(856563.43, 6670349.24))
        val geom = lignes(PasserelleEchange.instantane(travail), "centres").single()["geom"].asJsonObject
        assertEquals(856563.43, geom["x"].asDouble, 0.0)
        assertEquals(6670349.24, geom["y"].asDouble, 0.0)
    }

    @Test
    fun `le second operateur ne releve pas de nouveau centre - un centre modifie est refuse (plan_modifie)`(@TempDir tmp: File) {
        val travail = copie(depart2, tmp)
        val avant = PasserelleEchange.instantane(travail)
        val e = runCatching { PasserelleEchange.enregistrerCentre(travail, centre(856859.0, 6670361.0)) }.exceptionOrNull()
        assertTrue(e != null, "aucune exception")
        assertTrue("plan_modifie" in (e?.message ?: ""), e?.message)
        assertNull(premiereDifference(avant, PasserelleEchange.instantane(travail)))
    }

    // ---------------------------------------------------------------------------------------------------------
    // Critère 5 : la contre-notation n'écrit jamais dans notations
    // ---------------------------------------------------------------------------------------------------------

    @Test
    fun `une note saisie dans une tournee de contre-notation est enregistree dans contre_notations`(@TempDir tmp: File) {
        val travail = copie(depart2, tmp)
        val note = lignes(Cas.instantaneAttendu(rendu4), "contre_notations").first()
        PasserelleEchange.enregistrerNotation(travail, saisie(note))
        val inst = PasserelleEchange.instantane(travail)
        assertEquals(1, lignes(inst, "contre_notations").size)
        assertFalse("notations" in inst.getAsJsonObject("tables").keySet(), "Ramulus ne crée aucune table (Q13)")
    }

    // ---------------------------------------------------------------------------------------------------------
    // Critère 4 : deux notations du même arbre sont signalées avant de rendre, sans bloquer
    // ---------------------------------------------------------------------------------------------------------

    @Test
    fun `deux notations du meme arbre sont signalees avant de rendre (doublon_arbre), et le paquet est quand meme ecrit`(@TempDir tmp: File) {
        val attendu = Cas.instantaneAttendu(rendu3)
        val travail = copie(depart1, tmp)
        val racine = File(tmp, "appareil").also { extrairePhotos(rendu3, it) }
        saisir(travail, attendu, centres = true)
        // Même placette, même rang que la première note, autre uuid, sans photo.
        val seconde = saisie(lignes(attendu, "notations").first())
            .avec("uuid", "01a70000-0000-7000-8000-00000000d0d0").avec("repere", "77").avec("photos", JsonArray())
        PasserelleEchange.enregistrerNotation(travail, seconde)
        val sortie = File(tmp, "r.zip")

        val v = PasserelleEchange.rendre(travail, depart1, racine, "operateur-A", "exemple", sortie)

        assertEquals("signale", v.statut)
        assertEquals(setOf("doublon_arbre"), v.motifs)
        assertTrue(sortie.isFile, "un signalement ne bloque pas le rendu")
    }

    // ---------------------------------------------------------------------------------------------------------
    // Les photos : le paquet n'est pas rendu si elles ne correspondent pas aux notes (R10a à R10c, côté Ramulus)
    // ---------------------------------------------------------------------------------------------------------

    private fun rendreAvecPhotos(tmp: File, abimer: (File) -> Unit): Pair<Verdict, File> {
        val attendu = Cas.instantaneAttendu(rendu3)
        val travail = copie(depart1, tmp)
        val racine = File(tmp, "appareil").also { extrairePhotos(rendu3, it) }
        abimer(File(racine, "photos"))
        saisir(travail, attendu, centres = true)
        val sortie = File(tmp, "r.zip")
        return PasserelleEchange.rendre(travail, depart1, racine, "operateur-A", "exemple", sortie) to sortie
    }

    @Test
    fun `une photo citee mais absente de l appareil interdit le rendu (photo_absente)`(@TempDir tmp: File) {
        val (v, sortie) = rendreAvecPhotos(tmp) { dossier -> dossier.listFiles()!!.sortedBy { it.name }.first().delete() }
        assertEquals("refuse", v.statut)
        assertEquals(setOf("photo_absente"), v.motifs)
        assertFalse(sortie.exists(), "aucun paquet refusé n'est écrit")
    }

    @Test
    fun `une photo dont le contenu a change interdit le rendu (empreinte_photo_differente)`(@TempDir tmp: File) {
        val (v, sortie) = rendreAvecPhotos(tmp) { dossier ->
            val f = dossier.listFiles()!!.sortedBy { it.name }.first()
            f.writeBytes(f.readBytes() + byteArrayOf(0))
        }
        assertEquals("refuse", v.statut)
        assertEquals(setOf("empreinte_photo_differente"), v.motifs)
        assertFalse(sortie.exists())
    }

    @Test
    fun `une photo que aucune note ne cite interdit le rendu (photo_non_citee)`(@TempDir tmp: File) {
        val (v, sortie) = rendreAvecPhotos(tmp) { dossier ->
            File(dossier, "01a70000-0000-7000-8000-00000000f0f0_1.jpg").writeBytes(byteArrayOf(1, 2, 3))
        }
        assertEquals("refuse", v.statut)
        assertEquals(setOf("photo_non_citee"), v.motifs)
        assertFalse(sortie.exists())
    }

    // ---------------------------------------------------------------------------------------------------------
    // A6 : une tournée d'une version suivante est lue, complétée, et ce qu'on ne connaît pas est gardé tel quel
    // ---------------------------------------------------------------------------------------------------------

    @Test
    fun `une tournee de version suivante est completee et rendue sans perdre la table ni les colonnes inconnues`(@TempDir tmp: File) {
        val travail = copie(departFuture, tmp)
        val avant = PasserelleEchange.instantane(departFuture)
        // Une note sans photo sur P003 (collecte_photos = 0), tirée de A3.
        val p3 = lignes(Cas.instantaneAttendu(rendu3), "notations").first { it["plot_id"].asString == "P003" }
        PasserelleEchange.enregistrerNotation(travail, saisie(p3).avec("photos", JsonArray()))
        PasserelleEchange.cloturerPlacette(travail, "P003")
        val sortie = File(tmp, "r.zip")

        val v = PasserelleEchange.rendre(travail, departFuture, File(tmp, "appareil").also { it.mkdirs() }, "operateur-A", "exemple", sortie)

        assertEquals("accepte", v.statut)
        assertEquals(emptySet<String>(), v.motifs)
        val apres = PasserelleEchange.instantane(sortie)
        assertEquals(2, apres["version_schema"].asInt, "Ramulus ne change jamais la version écrite par Nemeton")
        assertNull(premiereDifference(avant.getAsJsonObject("tables")["table_future"], apres.getAsJsonObject("tables")["table_future"]), "table inconnue gardée")
        val colonnes = apres.getAsJsonObject("tables").getAsJsonObject("notations").getAsJsonObject("colonnes")
        assertTrue("champ_futur" in colonnes.keySet(), "colonne inconnue de notations gardée")
        assertEquals(
            lignes(avant, "tournee").single()["champ_futur"],
            tournee(apres)["champ_futur"],
            "valeur de la colonne inconnue de tournee",
        )
        assertEquals(avant.getAsJsonObject("tables").getAsJsonObject("tournee")["colonnes"].asJsonObject["champ_futur"],
            apres.getAsJsonObject("tables").getAsJsonObject("tournee")["colonnes"].asJsonObject["champ_futur"])
    }
}
