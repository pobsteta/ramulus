package io.github.pobsteta.ramulus.tournee

import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.deperis.Essences
import io.github.pobsteta.ramulus.core.deperis.Note
import io.github.pobsteta.ramulus.core.echange.EchangeRefuse
import io.github.pobsteta.ramulus.core.gnss.CentreReleve
import io.github.pobsteta.ramulus.core.gnss.PointLambert93
import io.github.pobsteta.ramulus.core.journal.LigneJournal
import io.github.pobsteta.ramulus.core.journal.Projection
import io.github.pobsteta.ramulus.core.journal.TypeLigne
import io.github.pobsteta.ramulus.core.photo.Empreinte
import io.github.pobsteta.ramulus.core.sauvegarde.EcritureRamsync
import io.github.pobsteta.ramulus.core.sauvegarde.LectureRamsync
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Journal en ajout seul, en mémoire : chaque ligne a son `uuid`, sa `remplace_uuid`, et n'est jamais touchée. */
private class JournalEnMemoire : Journal {
    val lignes = mutableListOf<LigneJournal>()
    /** Copie de chaque ligne au moment de son ajout, pour vérifier qu'aucune n'est modifiée ensuite. */
    val instantanes = mutableListOf<LigneJournal>()
    override fun saisir(codeTournee: String, objetUuid: String, type: String, valeur: String, operateur: String) =
        saisir(codeTournee, objetUuid, type, valeur, operateur, null)
    override fun saisir(codeTournee: String, objetUuid: String, type: String, valeur: String, operateur: String, remplaceUuid: String?) {
        val n = lignes.size + 1L
        val l = LigneJournal(
            uuid = "ligne-%04d".format(n), objetUuid = objetUuid, type = type, valeur = valeur, remplaceUuid = remplaceUuid,
            operateur = operateur, appareil = "0000abcd", sequence = n, saisiLeUtcMs = n, heureGnssMs = null, monotoneMs = n,
            demarrage = 1, ageFixMs = null, codeTournee = codeTournee,
        )
        lignes += l
        instantanes += l.copy()
    }
    override fun lignes(codeTournee: String): List<LigneJournal> = lignes.filter { it.codeTournee == codeTournee }
    override fun sauvegarderSiDue(codeTournee: String, versionNemeton: String?, finDePlacette: Boolean): String? = null
}

/**
 * Mission 015 : une note d'arbre corrigée, ou un centre relevé de nouveau, **remplace** l'ancienne ligne dans la copie
 * de travail ; le journal garde les deux (la seconde avec `remplace_uuid`). Coordonnées : EPSG:2154, X = est, Y = nord,
 * en mètres ; les placettes de la fixture sont en Côte-d'Or.
 */
class CorrectionParRemplacementTest {
    private lateinit var tmp: File
    private val maintenant: Instant = Instant.parse("2027-07-06T09:12:00Z")
    private val journal = JournalEnMemoire()
    private var refus = false
    private val echange = object : Echange by EchangeReel {
        override fun remplacerNotation(gpkg: File, ancienUuid: String, notation: String): String {
            if (refus) throw EchangeRefuse(listOf("note_hors_bornes"), "refus d'essai")
            return EchangeReel.remplacerNotation(gpkg, ancienUuid, notation)
        }
    }

    @BeforeTest
    fun preparer() {
        tmp = Files.createTempDirectory("ramulus-correction").toFile()
    }

    @AfterTest
    fun nettoyer() {
        tmp.deleteRecursively()
    }

    private val code = "EXEMPLE-2027-01"
    private val dossierTournee get() = File(tmp, "tournees/$code")
    private val racinePhotos get() = File(dossierTournee, "photos/photos")
    private val travail get() = File(dossierTournee, "travail/deperis.gpkg")

    private fun jpeg(graine: Int): ByteArray {
        val img = BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 24) for (y in 0 until 24) img.setRGB(x, y, (x * 10 + y * graine) and 0xFFFFFF)
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    }

    private fun controleur() = ControleurTournee(
        racine = File(tmp, "tournees"), echange = echange, journal = journal, stock = object : StockOperateur {
            override fun lire() = "operateur-A"
            override fun ecrire(nom: String) {}
        },
        export = object : ExportRendu {
            override fun exporter(zip: File, nomSouhaite: String): String {
                zip.copyTo(File(File(tmp, "documents").also { it.mkdirs() }, nomSouhaite), overwrite = true)
                return nomSouhaite
            }
        },
        versionRamulus = "test", horloge = { maintenant },
    ).also { it.demarrer() }

    private fun centre(p: PlacetteLue, dx: Double, dy: Double) =
        CentreReleve(PointLambert93(p.prevu.xMetres + dx, p.prevu.yMetres + dy), 4.5, 10, maintenant, 1.0)

    private fun ouvrir(plot: String): ControleurTournee {
        val c = controleur()
        c.ouvrirTournee(File("../fixtures/echange/A1-tournee-depart/deperis.gpkg").inputStream())
        c.choisirPlacette(plot)
        c.enregistrerCentre(plot, centre(c.etat.tournee!!.placette(plot)!!, 3.0, -2.0), null)
        c.versArbres(plot)
        return c
    }

    private fun noter(c: ControleurTournee, mb: Int, second: Int = 1) {
        c.choisirEssence(Essences.toutes.first { it.nom == "Chêne sessile" })
        c.choisirMb(Note(mb))
        c.choisirSecond(Note(second))
    }

    private fun placette(c: ControleurTournee, plot: String) = c.etat.tournee!!.placette(plot)!!
    private fun valeur(l: LigneJournal) = JsonParser.parseString(l.valeur).asJsonObject
    private fun fichiersPhotos() = racinePhotos.listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun `corriger un arbre laisse une seule note pour ce rang, de nouvel uuid, et le journal garde les deux lignes`() {
        val c = ouvrir("P003")
        noter(c, 1); c.enregistrerArbre()
        noter(c, 2); c.enregistrerArbre()
        val ancienne = placette(c, "P003").arbres.single { it.rang == 2 }
        c.rouvrirArbre(2)
        assertEquals(ancienne.uuid, c.etat.arbre.remplace)
        assertTrue(c.avertissementsArbre().any { it.startsWith("Correction du rang 2") }, c.avertissementsArbre().toString())
        c.choisirMb(Note(4))
        c.enregistrerArbre()
        assertNull(c.etat.erreur)

        val rang2 = placette(c, "P003").arbres.filter { it.rang == 2 }
        assertEquals(1, rang2.size, "une seule note pour le rang 2")
        assertNotEquals(ancienne.uuid, rang2.single().uuid, "nouvel uuid")
        assertEquals(4, rang2.single().mb)
        assertEquals(2, placette(c, "P003").arbres.size)

        val notations = journal.lignes.filter { it.type == TypeLigne.NOTATION && valeur(it)["rang"].asInt == 2 }
        assertEquals(2, notations.size)
        assertEquals(ancienne.uuid, notations[0].objetUuid)
        assertEquals(rang2.single().uuid, notations[1].objetUuid)
        assertEquals(notations[0].uuid, notations[1].remplaceUuid, "la seconde remplace la première")
        assertEquals(journal.instantanes, journal.lignes, "aucune ligne modifiée ni supprimée")

        // La projection de 005 ne voit plus que la note corrigée.
        val courantes = Projection.calculer(journal.lignes).values.filter { it.cle.type == TypeLigne.NOTATION }.map { it.cle.objetUuid }.toSet()
        assertEquals(placette(c, "P003").arbres.map { it.uuid }.toSet(), courantes)
    }

    @Test
    fun `les photos gardees passent a la note corrigee, renommees, meme SHA-256, et le rendu est sans doublon ni motif photo`() {
        val c = ouvrir("P001") // placette « collecte » : 2 à 4 vues
        noter(c, 1)
        c.ajouterPhoto(jpeg(1), emptyList()); c.ajouterPhoto(jpeg(2), emptyList())
        c.enregistrerArbre()
        val ancienne = placette(c, "P001").arbres.single()
        val empreintes = (1..2).map { Empreinte.sha256(File(racinePhotos, "${ancienne.uuid}_$it.jpg").readBytes()) }

        c.rouvrirArbre(1)
        assertEquals(ancienne.photos, c.etat.arbre.photosGardees)
        c.choisirMb(Note(3))
        c.enregistrerArbre()
        assertNull(c.etat.erreur)

        val nouvelle = placette(c, "P001").arbres.single()
        assertEquals((1..2).map { "photos/${nouvelle.uuid}_$it.jpg" }, nouvelle.photos)
        assertEquals((1..2).map { "${nouvelle.uuid}_$it.jpg" }, fichiersPhotos(), "les fichiers de l'ancienne note sont partis")
        assertEquals(empreintes, (1..2).map { Empreinte.sha256(File(racinePhotos, "${nouvelle.uuid}_$it.jpg").readBytes()) })

        // Le journal : chaque photo gardée remplace sa ligne d'origine.
        val photos = journal.lignes.filter { it.type == TypeLigne.PHOTO }
        assertEquals(4, photos.size)
        val (avant, apres) = photos.take(2) to photos.drop(2)
        assertTrue(avant.all { valeur(it)["note_uuid"].asString == ancienne.uuid })
        assertTrue(apres.all { valeur(it)["note_uuid"].asString == nouvelle.uuid })
        assertEquals(avant.map { it.uuid }, apres.map { it.remplaceUuid })
        assertEquals(empreintes, apres.map { valeur(it)["sha256"].asString })

        c.cloturerPlacette("P001")
        c.rendre()
        val r = c.etat.rendu!!
        assertFalse(r.refuse, "${r.verdict} ${r.motifs} ${r.messages}")
        assertTrue(r.messages.none { it.startsWith("doublon_arbre") || it.contains("photo") }, r.messages.toString())
        ZipFile(File(tmp, "documents/$code.zip")).use { z ->
            val dans = z.entries().asSequence().map { it.name }.filter { it.startsWith("photos/") }.sorted().toList()
            assertEquals(nouvelle.photos, dans)
        }
    }

    @Test
    fun `reprendre une photo remplace la vue de meme numero, l'ancienne quitte le paquet sans etre effacee`() {
        val c = ouvrir("P003") // placette ordinaire : une vue
        noter(c, 1)
        c.ajouterPhoto(jpeg(1), emptyList())
        c.enregistrerArbre()
        val ancienne = placette(c, "P003").arbres.single()
        c.rouvrirArbre(1)
        assertFalse(c.ajouterPhoto(jpeg(5), emptyList()), "la vue gardée compte : le maximum est atteint")
        c.reprendrePhoto(1)
        assertTrue(c.ajouterPhoto(jpeg(5), emptyList()), "une reprise ne compte pas en plus")
        assertEquals(1, c.etat.arbre.nbPhotos)
        c.enregistrerArbre()
        assertNull(c.etat.erreur)

        val nouvelle = placette(c, "P003").arbres.single()
        assertEquals(listOf("photos/${nouvelle.uuid}_1.jpg"), nouvelle.photos)
        assertEquals(listOf("${nouvelle.uuid}_1.jpg"), fichiersPhotos())
        assertEquals(Empreinte.sha256(jpeg(5)), Empreinte.sha256(File(racinePhotos, "${nouvelle.uuid}_1.jpg").readBytes()))
        assertTrue(File(dossierTournee, "photos-remplacees/${ancienne.uuid}_1.jpg").isFile, "l'ancienne photo reste sur l'appareil")

        val photos = journal.lignes.filter { it.type == TypeLigne.PHOTO }
        assertEquals(2, photos.size)
        assertEquals(photos[0].uuid, photos[1].remplaceUuid, "la vue reprise remplace la vue de même numéro au journal")
        assertTrue(journal.lignes.none { it.type == TypeLigne.ANNULATION })

        c.cloturerPlacette("P003")
        c.rendre()
        assertTrue(c.etat.rendu!!.motifs.isEmpty(), c.etat.rendu!!.messages.toString())
    }

    @Test
    fun `sur une placette collecte, reprendre la vue 2 garde les vues 1 et 3 a leur numero`() {
        val c = ouvrir("P001")
        noter(c, 1)
        for (g in 1..3) c.ajouterPhoto(jpeg(g), emptyList())
        c.enregistrerArbre()
        val ancienne = placette(c, "P001").arbres.single()
        val avant = (1..3).map { Empreinte.sha256(File(racinePhotos, "${ancienne.uuid}_$it.jpg").readBytes()) }
        c.rouvrirArbre(1)
        c.reprendrePhoto(2)
        c.ajouterPhoto(jpeg(7), emptyList())
        c.reprendrePhoto(2)
        c.ajouterPhoto(jpeg(8), emptyList()) // reprise de la reprise : la dernière fait foi
        c.ajouterPhoto(jpeg(9), emptyList()) // une quatrième vue ajoutée
        assertEquals(4, c.etat.arbre.nbPhotos)
        c.enregistrerArbre()
        assertNull(c.etat.erreur)
        val nouvelle = placette(c, "P001").arbres.single()
        val apres = (1..4).map { Empreinte.sha256(File(racinePhotos, "${nouvelle.uuid}_$it.jpg").readBytes()) }
        assertEquals(listOf(avant[0], Empreinte.sha256(jpeg(8)), avant[2], Empreinte.sha256(jpeg(9))), apres)
        assertEquals((1..4).map { "${nouvelle.uuid}_$it.jpg" }, fichiersPhotos())
        val photos = journal.lignes.filter { it.type == TypeLigne.PHOTO }
        assertEquals(photos.take(3).map { it.uuid } + listOf(null), photos.drop(3).map { it.remplaceUuid })
    }

    @Test
    fun `apres sauvegarde et restauration du ramsync, chaque photo courante pointe vers la note courante`() {
        val c = ouvrir("P001")
        noter(c, 1)
        c.ajouterPhoto(jpeg(1), emptyList()); c.ajouterPhoto(jpeg(2), emptyList())
        c.enregistrerArbre()
        c.rouvrirArbre(1)
        c.reprendrePhoto(2); c.ajouterPhoto(jpeg(6), emptyList())
        c.choisirMb(Note(3))
        c.enregistrerArbre()
        val note = placette(c, "P001").arbres.single()

        val octets = EcritureRamsync.ecrire(code, null, "test", "0000abcd", 99, journal.lignes)
        val lu = LectureRamsync.lire(octets)
        assertTrue(lu.accepte)
        assertEquals(journal.lignes, lu.lignes, "les lignes reviennent à l'identique")
        val courant = Projection.calculer(lu.lignes).values
        assertTrue(courant.none { it.enConflit })
        val notes = courant.filter { it.cle.type == TypeLigne.NOTATION }.map { it.cle.objetUuid }
        assertEquals(listOf(note.uuid), notes, "seule la note corrigée est courante")
        val photos = courant.filter { it.cle.type == TypeLigne.PHOTO }.map { valeur(it.courante!!) }
        assertEquals(note.photos, photos.map { it["nom"].asString }.sorted())
        assertTrue(photos.all { it["note_uuid"].asString == note.uuid }, "aucune photo courante ne pointe vers l'ancienne note")
        assertEquals(
            note.photos.map { Empreinte.sha256(File(racinePhotos, it.removePrefix("photos/")).readBytes()) },
            photos.sortedBy { it["nom"].asString }.map { it["sha256"].asString },
        )
    }

    @Test
    fun `une placette close se corrige, arbre et centre, par remplacement, et reste close`() {
        val c = ouvrir("P003")
        noter(c, 1); c.enregistrerArbre()
        noter(c, 2); c.enregistrerArbre()
        c.cloturerPlacette("P003")
        assertTrue(placette(c, "P003").estNotee)
        c.choisirPlacette("P003"); c.versArbres("P003")
        // Pas de nouvel arbre sur une placette close.
        noter(c, 0); c.enregistrerArbre()
        assertEquals(2, placette(c, "P003").arbres.size)
        c.rouvrirArbre(2)
        assertTrue(c.etat.arbre.rouvert)
        c.choisirMb(Note(5))
        c.enregistrerArbre()
        assertNull(c.etat.erreur)
        val p = placette(c, "P003")
        assertEquals(listOf(1, 2), p.arbres.map { it.rang })
        assertEquals(5, p.arbres.single { it.rang == 2 }.mb)
        assertTrue(p.estNotee, "la placette reste close")

        c.retour(); c.versCentre("P003")
        c.enregistrerCentre("P003", centre(p, 2.0, 2.0), null)
        assertNull(c.etat.erreur)
        assertTrue(placette(c, "P003").estNotee)
        assertEquals(p.prevu.xMetres + 2.0, placette(c, "P003").centre!!.x, 1e-6)
        val centres = journal.lignes.filter { it.type == TypeLigne.CENTRE }
        assertEquals(centres[0].uuid, centres[1].remplaceUuid)
    }

    @Test
    fun `une correction refusee ne retire rien, ne journalise rien et ne laisse aucune photo nommee`() {
        val c = ouvrir("P003")
        noter(c, 1)
        c.ajouterPhoto(jpeg(1), emptyList())
        c.enregistrerArbre()
        val ancienne = placette(c, "P003").arbres.single()
        val nbLignes = journal.lignes.size
        c.rouvrirArbre(1)
        c.choisirMb(Note(5))
        refus = true
        c.enregistrerArbre()
        assertTrue(c.etat.erreur!!.startsWith("Refusé"), c.etat.erreur)
        assertEquals(listOf(ancienne), placette(c, "P003").arbres, "l'ancienne note reste")
        assertEquals(listOf("${ancienne.uuid}_1.jpg"), fichiersPhotos(), "l'ancienne photo reste, la copie est défaite")
        assertEquals(nbLignes, journal.lignes.size)
        assertTrue(c.etat.arbre.rouvert, "l'opérateur peut enregistrer de nouveau")
        refus = false
        c.enregistrerArbre()
        assertNull(c.etat.erreur)
        assertEquals(1, placette(c, "P003").arbres.size)
    }

    @Test
    fun `un centre releve deux fois laisse un seul centre, le second, et le journal a deux lignes centre`() {
        val c = ouvrir("P003")
        val p = placette(c, "P003")
        val premier = assertNotNull(p.centre)
        c.retour()
        c.versCentre("P003")
        c.enregistrerCentre("P003", centre(p, -1.0, 4.0), null)
        assertNull(c.etat.erreur)
        val second = assertNotNull(placette(c, "P003").centre)
        assertNotEquals(premier.uuid, second.uuid)
        assertEquals(p.prevu.xMetres - 1.0, second.x, 1e-6, "X = est, la seconde position")
        assertEquals(p.prevu.yMetres + 4.0, second.y, 1e-6, "Y = nord, la seconde position")
        val inst = JsonParser.parseString(EchangeReel.instantane(travail)).asJsonObject
        val centres = inst.getAsJsonObject("tables").getAsJsonObject("centres").getAsJsonArray("lignes")
            .map { it.asJsonObject }.filter { it["plot_id"].asString == "P003" }
        assertEquals(1, centres.size)

        val lignes = journal.lignes.filter { it.type == TypeLigne.CENTRE }
        assertEquals(2, lignes.size)
        assertNull(lignes[0].remplaceUuid)
        assertEquals(lignes[0].uuid, lignes[1].remplaceUuid)
        assertEquals(listOf(premier.uuid, second.uuid), lignes.map { it.objetUuid })
    }

    @Test
    fun `une correction apres le rendu retire la marque rendue`() {
        val c = ouvrir("P003")
        noter(c, 1); c.enregistrerArbre()
        c.rendre()
        val marque = File(dossierTournee, "rendue")
        assertTrue(marque.exists())
        c.choisirPlacette("P003"); c.versArbres("P003")
        c.rouvrirArbre(1); c.choisirMb(Note(2)); c.enregistrerArbre()
        assertFalse(marque.exists())
    }
}
