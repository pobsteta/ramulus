package io.github.pobsteta.ramulus.tournee

import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.deperis.Abaque
import io.github.pobsteta.ramulus.core.deperis.Essences
import io.github.pobsteta.ramulus.core.deperis.Note
import io.github.pobsteta.ramulus.core.deperis.Saison
import io.github.pobsteta.ramulus.core.echange.EchangeDeperis
import io.github.pobsteta.ramulus.core.echange.EchangeRefuse
import io.github.pobsteta.ramulus.core.gnss.CentreReleve
import io.github.pobsteta.ramulus.core.gnss.EcartAuPointPrevu
import io.github.pobsteta.ramulus.core.gnss.PointLambert93
import io.github.pobsteta.ramulus.core.journal.TypeLigne
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Une saisie vue par le journal factice. */
private data class Saisie(val code: String, val objet: String, val type: String, val valeur: String, val operateur: String)

private class JournalFactice : Journal {
    val saisies = mutableListOf<Saisie>()
    val sauvegardes = mutableListOf<Boolean>()
    var echoue = false
    override fun saisir(codeTournee: String, objetUuid: String, type: String, valeur: String, operateur: String) {
        if (echoue) error("disque plein")
        saisies += Saisie(codeTournee, objetUuid, type, valeur, operateur)
    }
    override fun sauvegarderSiDue(codeTournee: String, versionNemeton: String?, finDePlacette: Boolean): String? {
        sauvegardes += finDePlacette
        return null
    }
}

private class StockFactice(var nom: String? = null) : StockOperateur {
    override fun lire() = nom
    override fun ecrire(nom: String) { this.nom = nom }
}

private class ExportFactice(val dossier: File) : ExportRendu {
    val exportes = mutableListOf<String>()
    override fun exporter(zip: File, nomSouhaite: String): String {
        zip.copyTo(File(dossier, nomSouhaite), overwrite = true)
        exportes += nomSouhaite
        return nomSouhaite
    }
}

/** L'échange réel, dont on peut forcer un refus d'écriture ou un verdict de rendu. */
private class EchangeTruque : Echange by EchangeReel {
    var refuserNotation: EchangeRefuse? = null
    /** Un repère physique que la copie de départ porterait pour l'arbre de rang 2 de P001. */
    var repereDepart: String? = null
    override fun instantane(gpkg: File): String {
        val brut = EchangeReel.instantane(gpkg)
        val r = repereDepart
        if (r == null || gpkg.invariantSeparatorsPath.contains("/depart/").not()) return brut
        val racine = JsonParser.parseString(brut).asJsonObject
        val ligne = com.google.gson.JsonObject().apply {
            addProperty("uuid", "depart-2"); addProperty("plot_id", "P001"); addProperty("rang", 2); addProperty("repere", r)
            addProperty("essence", "Hêtre"); addProperty("mb", 1); addProperty("second", 1)
        }
        racine.getAsJsonObject("tables").getAsJsonObject("notations").getAsJsonArray("lignes").add(ligne)
        return racine.toString()
    }
    var rendu: ((File) -> String)? = null
    override fun enregistrerNotation(gpkg: File, notation: String): String {
        refuserNotation?.let { throw it }
        return EchangeReel.enregistrerNotation(gpkg, notation)
    }
    override fun rendre(gpkg: File, depart: File, racinePhotos: File, operateur: String, versionRamulus: String, sortie: File): String =
        rendu?.invoke(sortie) ?: EchangeReel.rendre(gpkg, depart, racinePhotos, operateur, versionRamulus, sortie)
}

class ControleurTourneeTest {
    private lateinit var tmp: File
    private val journal = JournalFactice()
    private val stock = StockFactice("operateur-A")
    private lateinit var export: ExportFactice
    private val echange = EchangeTruque()
    private var maintenant: Instant = Instant.parse("2027-07-06T09:12:00Z")

    private fun fixture(cas: String) = File("../fixtures/echange/$cas/deperis.gpkg").also { assertTrue(it.exists(), "fixture absente : $it") }

    @BeforeTest
    fun preparer() {
        tmp = Files.createTempDirectory("ramulus-app").toFile()
        export = ExportFactice(File(tmp, "documents").also { it.mkdirs() })
    }

    @AfterTest
    fun nettoyer() {
        tmp.deleteRecursively()
    }

    private fun controleur() = ControleurTournee(
        racine = File(tmp, "tournees"), echange = echange, journal = journal, stock = stock, export = export,
        versionRamulus = "test", horloge = { maintenant },
    ).also { it.demarrer() }

    private fun ouvrir(c: ControleurTournee, gpkg: File) = c.ouvrirTournee(gpkg.inputStream())

    private fun centreProche(plot: PlacetteLue) = CentreReleve(
        position = PointLambert93(plot.prevu.xMetres + 3.0, plot.prevu.yMetres - 2.0),
        precisionM = 4.5, nbMesures = 10, dateReleve = maintenant, dispersionM = 1.0,
    )

    private fun noter(c: ControleurTournee, essence: String, mb: Int, second: Int) {
        c.choisirEssence(Essences.toutes.first { it.nom == essence })
        c.choisirMb(Note(mb))
        c.choisirSecond(Note(second))
        c.enregistrerArbre()
    }

    // --- Départ -------------------------------------------------------------------------------------------------

    @Test
    fun `au premier lancement le nom de l'operateur est demande une seule fois`() {
        stock.nom = null
        val c = controleur()
        assertEquals(Ecran.Operateur, c.etat.ecran)
        c.definirOperateur("   ")
        assertEquals(Ecran.Operateur, c.etat.ecran)
        assertNotNull(c.etat.erreur)
        c.definirOperateur("  Hélène Dupont ")
        assertEquals(Ecran.Accueil, c.etat.ecran)
        assertEquals("Hélène Dupont", stock.nom)
        assertEquals(Ecran.Accueil, controleur().etat.ecran) // le lancement suivant ne le redemande pas
    }

    @Test
    fun `un fichier refuse affiche ses motifs en clair et ne garde rien`() {
        val c = controleur()
        val pasUnGpkg = File(tmp, "x.gpkg").also { it.writeText("ce n'est pas un GeoPackage") }
        ouvrir(c, pasUnGpkg)
        assertEquals(Ecran.Accueil, c.etat.ecran)
        assertTrue(c.etat.erreur!!.contains("GeoPackage"), c.etat.erreur)
        assertNull(c.etat.tournee)
        assertTrue(c.etat.enCours.isEmpty())
        assertEquals(emptyList(), File(tmp, "tournees").listFiles { f -> f.isDirectory && f.name != "ouverture" }.orEmpty().toList())
    }

    @Test
    fun `ouvrir une tournee la copie et garde la copie de depart intacte jusqu'au rendu`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        assertEquals(Ecran.Placettes, c.etat.ecran)
        val t = c.etat.tournee!!
        assertEquals("EXEMPLE-2027-01", t.code)
        assertEquals(listOf("P001", "P002", "P003"), t.placettes.map { it.plotId })
        val depart = File(tmp, "tournees/EXEMPLE-2027-01/depart/deperis.gpkg")
        val avant = depart.readBytes()
        assertContentEquals(fixture("A1-tournee-depart").readBytes(), avant)

        c.choisirPlacette("P001")
        c.enregistrerCentre("P001", centreProche(t.placette("P001")!!), null)
        c.versArbres("P001")
        noter(c, "Chêne sessile", 2, 1)
        c.cloturerPlacette("P001")
        c.rendre()
        assertContentEquals(avant, depart.readBytes(), "la copie de départ n'a pas bougé")
    }

    @Test
    fun `rouvrir la meme tournee reprend le travail sans l'ecraser`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        c.enregistrerCentre("P001", centreProche(c.etat.tournee!!.placette("P001")!!), null)
        ouvrir(c, fixture("A1-tournee-depart"))
        assertNotNull(c.etat.tournee!!.placette("P001")!!.centre, "le centre déjà relevé est resté")
        assertTrue(c.etat.avertissements.any { it.contains("repris") })
        c.retour()
        c.retour()
        assertEquals(listOf(TourneeEnCours("EXEMPLE-2027-01", "premiere", 3, 0)), c.etat.enCours)
    }

    // --- Une placette de bout en bout ---------------------------------------------------------------------------

    @Test
    fun `une placette entiere de la tournee A1 se note, se clot et se rend`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        val ordre = mutableListOf<Ecran>(c.etat.ecran)

        c.choisirPlacette("P001"); ordre += c.etat.ecran
        c.versCentre("P001"); ordre += c.etat.ecran
        c.enregistrerCentre("P001", centreProche(c.etat.tournee!!.placette("P001")!!), EcartAuPointPrevu(3.6, false)); ordre += c.etat.ecran
        c.versArbres("P001"); ordre += c.etat.ecran

        val essences = listOf("Chêne sessile", "Hêtre", "Chêne sessile", "Frêne")
        val notes = listOf(0 to 0, 3 to 2, 5 to 5, 1 to 4)
        for ((i, e) in essences.withIndex()) {
            assertEquals(i + 1, c.etat.arbre.rang, "les arbres se notent dans l'ordre du tour d'horizon")
            noter(c, e, notes[i].first, notes[i].second)
            assertNull(c.etat.erreur)
        }
        val p = c.etat.tournee!!.placette("P001")!!
        assertEquals(4, p.arbres.size)
        assertEquals(Statut.EN_COURS, p.statut)
        for ((i, a) in p.arbres.withIndex()) {
            assertEquals(Abaque.classe(notes[i].first, notes[i].second), a.classe, "la classe vient de l'abaque")
        }

        c.cloturerPlacette("P001"); ordre += c.etat.ecran
        assertEquals(Statut.NOTEE, c.etat.tournee!!.placette("P001")!!.statut)
        c.versRendu()
        c.rendre(); ordre += c.etat.ecran

        assertEquals(
            listOf(Ecran.Placettes, Ecran.Placette("P001"), Ecran.Centre("P001"), Ecran.Placette("P001"), Ecran.Arbres("P001"), Ecran.Placettes, Ecran.Rendu),
            ordre,
        )

        // Le journal : centre, quatre notations, placette notée, dans cet ordre, sans classe.
        assertEquals(
            listOf(TypeLigne.CENTRE) + List(4) { TypeLigne.NOTATION } + TypeLigne.PLACETTE_NOTEE,
            journal.saisies.map { it.type },
        )
        assertTrue(journal.saisies.all { it.operateur == "operateur-A" && it.code == "EXEMPLE-2027-01" })
        assertTrue(journal.saisies.none { "classe" in it.valeur })
        assertEquals(true, journal.sauvegardes.last(), "la fin de placette demande la sauvegarde")

        // Le rendu : le .zip est déposé, et Nemeton le relit avec les notes et la classe de l'abaque.
        val r = c.etat.rendu!!
        assertFalse(r.refuse, r.toString())
        assertEquals("EXEMPLE-2027-01.zip", r.nomZip)
        val zip = File(export.dossier, "EXEMPLE-2027-01.zip")
        val inst = JsonParser.parseString(EchangeDeperis.instantane(zip)).asJsonObject.getAsJsonObject("tables")
        val lignes = inst.getAsJsonObject("notations").getAsJsonArray("lignes").map { it.asJsonObject }
        assertEquals(4, lignes.size)
        for ((i, l) in lignes.withIndex()) {
            assertEquals(Abaque.classe(notes[i].first, notes[i].second).name, l["classe"].asString)
            assertEquals(i + 1, l["rang"].asInt)
            assertEquals("operateur-A", l["operateur"].asString)
        }
        assertEquals("notee", inst.getAsJsonObject("placettes").getAsJsonArray("lignes")[0].asJsonObject["etat"].asString)
        assertEquals(1, inst.getAsJsonObject("centres").getAsJsonArray("lignes").size())
        assertEquals("operateur-A", inst.getAsJsonObject("tournee").getAsJsonArray("lignes")[0].asJsonObject["operateur"].asString)
    }

    @Test
    fun `le second critere suit l'essence, MR pour un feuillu et MA pour un resineux`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        c.versArbres("P001")
        noter(c, "Chêne sessile", 1, 2)
        noter(c, "Épicéa commun", 1, 2)
        val criteres = JsonParser.parseString(EchangeDeperis.instantane(File(tmp, "tournees/EXEMPLE-2027-01/travail/deperis.gpkg")))
            .asJsonObject.getAsJsonObject("tables").getAsJsonObject("notations").getAsJsonArray("lignes").map { it.asJsonObject["critere"].asString }
        assertEquals(listOf("MR", "MA"), criteres)
        // Changer pour une essence de l'autre type efface la note de l'autre critère.
        c.choisirEssence(Essences.toutes.first { it.nom == "Hêtre" })
        c.choisirSecond(Note(4))
        c.choisirEssence(Essences.toutes.first { it.nom == "Douglas" })
        assertNull(c.etat.arbre.second)
    }

    @Test
    fun `l'enregistrement exige l'essence et les deux notes, et aucun numero peint`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        c.versArbres("P001")
        c.choisirEssence(Essences.toutes.first())
        c.choisirMb(Note(1))
        c.enregistrerArbre()
        assertFalse(c.etat.arbre.complet)
        assertTrue(c.etat.tournee!!.placette("P001")!!.arbres.isEmpty())
        assertTrue(journal.saisies.isEmpty())
        c.choisirSecond(Note(1))
        assertTrue(c.etat.arbre.complet, "essence et deux notes suffisent")
        c.enregistrerArbre()
        assertEquals(1, c.etat.tournee!!.placette("P001")!!.arbres.size)
    }

    @Test
    fun `une ecriture refusee n'ecrit rien au journal et le dit en clair`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        c.versArbres("P001")
        echange.refuserNotation = EchangeRefuse(listOf("note_hors_bornes"), "mb = 7")
        noter(c, "Hêtre", 1, 1)
        assertTrue(c.etat.erreur!!.contains("0 et 5"), c.etat.erreur)
        assertTrue(journal.saisies.isEmpty())
        assertTrue(c.etat.tournee!!.placette("P001")!!.arbres.isEmpty())
    }

    @Test
    fun `un journal en panne n'efface pas la note et le signale`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        c.versArbres("P001")
        journal.echoue = true
        noter(c, "Hêtre", 1, 1)
        assertEquals(1, c.etat.tournee!!.placette("P001")!!.arbres.size)
        assertTrue(c.etat.avertissements.any { it.contains("Journal non écrit") })
    }

    @Test
    fun `un arbre rouvert garde son rang et sa correction est signalee sans bloquer`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        c.versArbres("P001")
        noter(c, "Hêtre", 1, 1)
        noter(c, "Hêtre", 2, 2)
        assertEquals(3, c.etat.arbre.rang)
        c.rouvrirArbre(1)
        assertEquals(1, c.etat.arbre.rang, "l'arbre rouvert garde son rang")
        assertEquals(Note(1), c.etat.arbre.mb)
        val avert = c.avertissementsArbre()
        assertTrue(avert.any { it.contains("rang 1") }, avert.toString())
        c.choisirMb(Note(3))
        c.enregistrerArbre()
        assertNull(c.etat.erreur)
        assertEquals(3, c.etat.arbre.rang, "après la correction, on reprend au premier rang sans note")
        val rangs = c.etat.tournee!!.placette("P001")!!.arbres.map { it.rang }
        assertEquals(listOf(1, 1, 2), rangs)
    }

    @Test
    fun `un repere physique de la tournee de depart est recopie, sinon NULL`() {
        echange.repereDepart = "PL-7"
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        c.versArbres("P001")
        noter(c, "Hêtre", 1, 1)
        noter(c, "Hêtre", 2, 2)
        noter(c, "Hêtre", 3, 3)
        val par = c.etat.tournee!!.placette("P001")!!.arbres.associate { it.rang to it.repere }
        assertEquals(null, par[1])
        assertEquals("PL-7", par[2], "le repère du départ est recopié dans la notation du même rang")
        assertEquals(null, par[3])
    }

    @Test
    fun `apres le dernier arbre la placette est complete et une saisie de plus ne fabrique pas de correction`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        c.versArbres("P001")
        for (i in 1..4) noter(c, "Hêtre", i, 1)
        var p = c.etat.tournee!!.placette("P001")!!
        assertTrue(p.complete)
        assertEquals(4, p.nbRangsNotes)
        assertFalse(c.etat.arbre.rouvert, "plus de formulaire : l'écran affiche « Placette complète »")
        // Une saisie de plus ne fait rien.
        c.choisirEssence(Essences.toutes.first { it.nom == "Hêtre" }); c.choisirMb(Note(0)); c.choisirSecond(Note(0))
        c.enregistrerArbre()
        p = c.etat.tournee!!.placette("P001")!!
        assertEquals(4, p.arbres.size, "aucune ligne fabriquée")
        // Une correction voulue passe par la réouverture.
        c.rouvrirArbre(4)
        assertTrue(c.etat.arbre.rouvert)
        c.choisirMb(Note(5))
        c.enregistrerArbre()
        assertFalse(c.etat.arbre.rouvert, "après la correction, on retombe sur la placette complète")
    }

    @Test
    fun `l'avancement et la liste comptent les rangs distincts, pas les lignes`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        c.versArbres("P001")
        noter(c, "Hêtre", 1, 1)
        noter(c, "Hêtre", 2, 2)
        c.rouvrirArbre(1); c.choisirMb(Note(4)); c.enregistrerArbre()
        c.rouvrirArbre(1); c.choisirMb(Note(3)); c.enregistrerArbre()
        val p = c.etat.tournee!!.placette("P001")!!
        assertEquals(4, p.arbres.size, "quatre lignes")
        assertEquals(2, p.nbRangsNotes, "deux arbres")
        assertFalse(p.complete)
        assertEquals(listOf(1, 2), p.arbresRetenus.map { it.rang })
        assertEquals(3, p.arbresRetenus[0].mb, "la dernière ligne du rang 1")
        assertEquals(setOf(1), p.rangsCorriges)
        c.cloturerPlacette("P001")
        assertTrue(journal.saisies.last().valeur.contains("\"nb_arbres_notes\":2"), journal.saisies.last().valeur)
    }

    @Test
    fun `une placette de quatre arbres se note sans aucun numero peint, rangs 1 a 4 et repere NULL`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        c.versArbres("P001")
        for (i in 1..4) {
            assertEquals(i, c.etat.arbre.rang)
            noter(c, "Hêtre", i % 6, 1)
            assertNull(c.etat.erreur)
        }
        val p = c.etat.tournee!!.placette("P001")!!
        assertEquals(listOf(1, 2, 3, 4), p.arbres.map { it.rang })
        assertTrue(p.arbres.all { it.repere == null }, "aucun numéro peint n'est écrit")
        assertTrue(journal.saisies.none { "repere" in it.valeur }, "le journal ne porte pas de numéro peint")
    }

    // --- Rendu ---------------------------------------------------------------------------------------------------

    @Test
    fun `un rendu signale ecrit quand meme le zip`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        echange.rendu = { sortie ->
            sortie.writeText("zip")
            """{"verdict":"signale","motifs":["centre_eloigne"],"messages":["centre_eloigne : P001 à 42 m"]}"""
        }
        c.rendre()
        val r = c.etat.rendu!!
        assertEquals("signale", r.verdict)
        assertEquals("EXEMPLE-2027-01.zip", r.nomZip)
        assertEquals(listOf("EXEMPLE-2027-01.zip"), export.exportes)
        assertEquals(listOf("Un centre est loin du point prévu."), r.motifs)
    }

    @Test
    fun `un rendu refuse n'ecrit rien et affiche ses motifs en clair`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        echange.rendu = { """{"verdict":"refuse","motifs":["classe_differente_abaque"],"messages":["classe_differente_abaque : arbre 3"]}""" }
        c.rendre()
        val r = c.etat.rendu!!
        assertTrue(r.refuse)
        assertNull(r.nomZip)
        assertTrue(export.exportes.isEmpty())
        assertEquals(listOf("Une classe n'est pas celle de l'abaque."), r.motifs)
        assertEquals(Ecran.Rendu, c.etat.ecran)
    }

    // --- Contre-notation -----------------------------------------------------------------------------------------

    @Test
    fun `en contre-notation le centre est en lecture et les notes vont dans contre_notations`() {
        val c = controleur()
        ouvrir(c, fixture("A2-tournee-contre-notation"))
        val t = c.etat.tournee!!
        assertTrue(t.estContreNotation)
        val p = t.placette("P002")!!
        assertNotNull(p.centre, "le centre du premier opérateur est fourni")
        assertEquals(Statut.CENTRE_RELEVE, p.statut)

        c.choisirPlacette("P002")
        c.versCentre("P002")
        assertEquals(Ecran.Placette("P002"), c.etat.ecran, "pas d'écran de relevé du centre")
        c.enregistrerCentre("P002", centreProche(p), null)
        assertTrue(journal.saisies.isEmpty(), "aucun second centre")

        c.versArbres("P002")
        noter(c, "Épicéa commun", 2, 3)
        assertEquals(listOf(TypeLigne.CONTRE_NOTATION), journal.saisies.map { it.type })
        assertEquals(1, c.etat.tournee!!.placette("P002")!!.arbres.size)
        val tables = JsonParser.parseString(EchangeDeperis.instantane(File(tmp, "tournees/EXEMPLE-2027-01-CN/travail/deperis.gpkg")))
            .asJsonObject.getAsJsonObject("tables")
        assertEquals(1, tables.getAsJsonObject("contre_notations").getAsJsonArray("lignes").size())
        assertFalse(tables.has("notations"))
    }

    @Test
    fun `la contre-notation n'affiche jamais une premiere note`() {
        val instantane = """
            {"tables": {
              "tournee": {"lignes": [{"code_tournee": "CN-1", "nature": "contre_notation", "version_nemeton": "x", "prepare_le": null}]},
              "placettes": {"lignes": [{"plot_id": "P1", "geom": {"x": 1.0, "y": 2.0}, "nb_arbres": 2, "saison": "ETE", "etat": null}]},
              "notations": {"lignes": [{"uuid": "u1", "plot_id": "P1", "rang": 1, "repere": "1", "essence": "Hêtre", "mb": 4, "second": 4, "classe": "E"}]},
              "contre_notations": {"lignes": []}
            }}
        """.trimIndent()
        val t = LectureTournee.depuis(instantane)
        assertTrue(t.placette("P1")!!.arbres.isEmpty())
        assertEquals(Statut.A_FAIRE, t.placette("P1")!!.statut)
        // Dans une tournée de première notation, la même ligne se lit.
        val premiere = LectureTournee.depuis(instantane.replace("contre_notation\"", "premiere\""))
        assertEquals(1, premiere.placette("P1")!!.arbres.size)
    }

    // --- Saisons -------------------------------------------------------------------------------------------------

    @Test
    fun `SA1 une saison qui ne correspond pas a la date avertit sans bloquer`() {
        assertNull(Avertissements.saisonEtDate(Saison.ETE, LocalDate.of(2027, 7, 6)))
        assertNull(Avertissements.saisonEtDate(Saison.HIVER, LocalDate.of(2027, 1, 15)))
        assertTrue(Avertissements.saisonEtDate(Saison.ETE, LocalDate.of(2027, 1, 15))!!.contains("janvier"))
        assertTrue(Avertissements.saisonEtDate(Saison.HIVER, LocalDate.of(2027, 7, 6))!!.contains("juillet"))
        // Les mois de transition ne donnent aucun avertissement (calendrier provisoire).
        assertNull(Avertissements.saisonEtDate(Saison.ETE, LocalDate.of(2027, 10, 29)))
        assertNull(Avertissements.saisonEtDate(Saison.HIVER, LocalDate.of(2027, 4, 2)))

        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        assertNull(c.avertissementSaison("P001"))
        maintenant = Instant.parse("2027-01-20T10:00:00Z")
        assertTrue(c.avertissementSaison("P001")!!.contains("janvier"))
        // La note reste possible, avec la date du jour.
        c.versArbres("P001")
        noter(c, "Hêtre", 1, 1)
        assertEquals(1, c.etat.tournee!!.placette("P001")!!.arbres.size)
    }

    @Test
    fun `SA2 le charme et le chene liege hors ete sont notes avec la fiche d'ete et un avertissement`() {
        val charme = Essences.toutes.first { it.nom == "Charme" }
        val liege = Essences.toutes.first { it.nom == "Chêne liège" }
        val sessile = Essences.toutes.first { it.nom == "Chêne sessile" }
        assertNotNull(Avertissements.ficheManquante(charme, Saison.HIVER))
        assertNotNull(Avertissements.ficheManquante(liege, Saison.HIVER))
        assertNull(Avertissements.ficheManquante(charme, Saison.ETE))
        assertNull(Avertissements.ficheManquante(sessile, Saison.HIVER))
        assertEquals(Saison.ETE, Avertissements.saisonDeLaFiche(charme, Saison.HIVER))
        assertEquals(Saison.HIVER, Avertissements.saisonDeLaFiche(sessile, Saison.HIVER))

        // De bout en bout, sur une placette d'hiver.
        val hiver = File(tmp, "hiver.gpkg")
        fixture("A1-tournee-depart").copyTo(hiver)
        DriverManager.getConnection("jdbc:sqlite:${hiver.path}").use { cnx ->
            cnx.createStatement().use { it.executeUpdate("UPDATE placettes SET saison = 'HIVER' WHERE plot_id = 'P001'") }
        }
        val c = controleur()
        ouvrir(c, hiver)
        assertEquals(Saison.HIVER, c.etat.tournee!!.placette("P001")!!.saison)
        c.versArbres("P001")
        c.choisirEssence(charme)
        assertTrue(c.avertissementsArbre().any { it.contains("fiche d'été") }, c.avertissementsArbre().toString())
        c.choisirMb(Note(1)); c.choisirSecond(Note(1))
        c.enregistrerArbre()
        assertNull(c.etat.erreur)
        assertTrue(journal.saisies.single().valeur.contains("\"saison\":\"ETE\""), journal.saisies.single().valeur)
    }

    // --- Accueil (mission 019) -------------------------------------------------------------------------------------

    @Test
    fun `une tournee rendue passe dans Terminees, une tournee refusee ou jamais rendue reste a faire`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        c.retour()
        assertFalse(c.etat.enCours.single().rendue)
        ouvrir(c, fixture("A1-tournee-depart"))
        echange.rendu = { """{"verdict":"refuse","motifs":["classe_differente_abaque"],"messages":["x"]}""" }
        c.rendre()
        c.retour(); c.retour()
        assertFalse(c.etat.enCours.single().rendue, "un rendu refusé ne termine pas la tournée")
        echange.rendu = { sortie -> sortie.writeText("zip"); """{"verdict":"ok","motifs":[],"messages":[]}""" }
        c.versRendu(); c.rendre()
        c.retour(); c.retour()
        assertEquals(Ecran.Accueil, c.etat.ecran)
        assertTrue(c.etat.enCours.single().rendue)
        assertTrue(controleur().etat.enCours.single().rendue, "la marque survit au redémarrage")
    }

    @Test
    fun `Parametres s'ouvre et ramene a l'accueil, le nom se change sans quitter l'ecran`() {
        val c = controleur()
        c.versParametres()
        assertEquals(Ecran.Parametres, c.etat.ecran)
        c.renommerOperateur("   ")
        assertNotNull(c.etat.erreur)
        assertEquals("operateur-A", stock.nom)
        c.renommerOperateur(" Claire Martin ")
        assertEquals(Ecran.Parametres, c.etat.ecran)
        assertEquals("Claire Martin", c.etat.operateur)
        assertEquals("Claire Martin", stock.nom)
        c.retour()
        assertEquals(Ecran.Accueil, c.etat.ecran)
    }

    @Test
    fun `l'ecran initial n'est jamais vide, avant comme apres la lecture du nom`() {
        // Avant demarrer() : l'état par défaut montre l'accueil (bandeau, menu), pas du vide.
        assertEquals(Vue.ACCUEIL, vueDe(EtatTournee()))
        stock.nom = null
        assertEquals(Vue.NOM, vueDe(controleur().etat))
        stock.nom = "operateur-A"
        assertEquals(Vue.ACCUEIL, vueDe(controleur().etat))
    }

    @Test
    fun `toute combinaison d'ecran et de tournee donne une vue, jamais du vide`() {
        val c = controleur()
        ouvrir(c, fixture("A1-tournee-depart"))
        val t = c.etat.tournee!!
        val plot = t.placettes.first().plotId
        val ecrans = listOf(
            Ecran.Operateur, Ecran.Accueil, Ecran.Parametres, Ecran.Placettes, Ecran.Rendu,
            Ecran.Placette(plot), Ecran.Centre(plot), Ecran.Arbres(plot), Ecran.Placette("inconnue"), Ecran.Arbres("inconnue"),
        )
        for (e in ecrans) {
            assertNotNull(vueDe(EtatTournee(ecran = e, tournee = t)), "avec tournée : $e")
            // Sans tournée, les écrans de tournée retombent sur l'accueil.
            val sans = vueDe(EtatTournee(ecran = e, tournee = null))
            assertTrue(sans == Vue.NOM || sans == Vue.ACCUEIL || sans == Vue.PARAMETRES, "sans tournée : $e -> $sans")
        }
        assertEquals(Vue.PLACETTES, vueDe(EtatTournee(ecran = Ecran.Placette("inconnue"), tournee = t)))
        assertEquals(Vue.PLACETTE, vueDe(EtatTournee(ecran = Ecran.Placette(plot), tournee = t)))
    }
}
