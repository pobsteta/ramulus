package io.github.pobsteta.ramulus.tournee

import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.deperis.Essences
import io.github.pobsteta.ramulus.core.deperis.Note
import io.github.pobsteta.ramulus.core.echange.EchangeDeperis
import io.github.pobsteta.ramulus.core.echange.EchangeRefuse
import io.github.pobsteta.ramulus.core.gnss.CentreReleve
import io.github.pobsteta.ramulus.core.gnss.PointLambert93
import io.github.pobsteta.ramulus.core.photo.Empreinte
import io.github.pobsteta.ramulus.core.photo.JpegSansExif
import io.github.pobsteta.ramulus.core.photo.SignalementImage
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
import kotlin.test.assertTrue

private class JournalMuet : Journal {
    override fun saisir(codeTournee: String, objetUuid: String, type: String, valeur: String, operateur: String) {}
    override fun sauvegarderSiDue(codeTournee: String, versionNemeton: String?, finDePlacette: Boolean): String? = null
}

private class StockFixe : StockOperateur {
    override fun lire() = "operateur-A"
    override fun ecrire(nom: String) {}
}

private class ExportCopie(val dossier: File) : ExportRendu {
    override fun exporter(zip: File, nomSouhaite: String): String {
        zip.copyTo(File(dossier, nomSouhaite), overwrite = true)
        return nomSouhaite
    }
}

/** Les photos de l'arbre en cours (mission 008) : régime, nommage, `.nomedia`, EXIF, « photo manquante », rendu. */
class PhotosTourneeTest {
    private lateinit var tmp: File
    private val maintenant: Instant = Instant.parse("2027-07-06T09:12:00Z")

    @BeforeTest
    fun preparer() {
        tmp = Files.createTempDirectory("ramulus-photos").toFile()
    }

    @AfterTest
    fun nettoyer() {
        tmp.deleteRecursively()
    }

    private fun jpeg(graine: Int, avecExif: Boolean = false): ByteArray {
        val img = BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 24) for (y in 0 until 24) img.setRGB(x, y, (x * 10 + y * graine) and 0xFFFFFF)
        val sortie = ByteArrayOutputStream()
        ImageIO.write(img, "jpg", sortie)
        val propre = sortie.toByteArray()
        if (!avecExif) return propre
        val exif = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + ByteArray(12) { 9 }
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0, (exif.size + 2).toByte()) + exif
        return propre.copyOfRange(0, 2) + segment + propre.copyOfRange(2, propre.size)
    }

    private var refus = false
    private val echange = object : Echange by EchangeReel {
        override fun enregistrerNotation(gpkg: File, notation: String): String {
            if (refus) throw EchangeRefuse(listOf("note_hors_bornes"), "refus d'essai")
            return EchangeReel.enregistrerNotation(gpkg, notation)
        }
    }

    private fun controleur() = ControleurTournee(
        racine = File(tmp, "tournees"), echange = echange, journal = JournalMuet(), stock = StockFixe(),
        export = ExportCopie(File(tmp, "documents").also { it.mkdirs() }), versionRamulus = "test", horloge = { maintenant },
    ).also { it.demarrer() }

    private fun ouvrir(plot: String = "P003"): ControleurTournee {
        val c = controleur()
        c.ouvrirTournee(File("../fixtures/echange/A1-tournee-depart/deperis.gpkg").inputStream())
        val p = c.etat.tournee!!.placette(plot)!!
        c.choisirPlacette(plot)
        c.enregistrerCentre(
            plot,
            CentreReleve(PointLambert93(p.prevu.xMetres + 3.0, p.prevu.yMetres - 2.0), 4.5, 10, maintenant, 1.0),
            null,
        )
        c.versArbres(plot)
        return c
    }

    private fun saisir(c: ControleurTournee, repere: String) {
        c.choisirEssence(Essences.toutes.first { it.nom == "Chêne sessile" })
        c.choisirMb(Note(2))
        c.choisirSecond(Note(1))
        c.changerRepere(repere)
    }

    private val racinePhotos get() = File(tmp, "tournees/EXEMPLE-2027-01/photos/photos")

    @Test
    fun `une photo prise est nommee uuid_1, citee avec son SHA-256, et le dossier porte un nomedia`() {
        val c = ouvrir()
        saisir(c, "12")
        val octets = jpeg(3)
        assertTrue(c.ajouterPhoto(octets, listOf(SignalementImage.FLOU)))
        assertEquals(listOf(SignalementImage.FLOU), c.etat.arbre.photos.single().signalements)
        c.enregistrerArbre()

        val arbre = c.etat.tournee!!.placette("P003")!!.arbres.single()
        val nom = arbre.photos.single()
        assertEquals("photos/${arbre.uuid}_1.jpg", nom)
        val fichier = File(racinePhotos, "${arbre.uuid}_1.jpg")
        assertTrue(fichier.exists())
        assertEquals(Empreinte.sha256(fichier.readBytes()), citeeEnBase(c, arbre.uuid).single().second)
        assertTrue(File(racinePhotos.parentFile, ".nomedia").exists(), "aucune galerie n'indexe ce dossier")
        assertFalse(File(racinePhotos, ".nomedia").exists(), "le .nomedia ne doit pas entrer dans photos/ (photo_non_citee)")
        assertTrue(c.etat.arbre.photos.isEmpty(), "l'arbre suivant repart sans photo")
        assertEquals(emptyList(), File(tmp, "tournees/EXEMPLE-2027-01/photos-en-attente").listFiles().orEmpty().toList())
    }

    private fun citeeEnBase(c: ControleurTournee, uuid: String): List<Pair<String, String>> {
        val inst = JsonParser.parseString(EchangeDeperis.instantane(File(tmp, "tournees/EXEMPLE-2027-01/travail/deperis.gpkg"))).asJsonObject
        val ligne = inst.getAsJsonObject("tables").getAsJsonObject("notations").getAsJsonArray("lignes")
            .map { it.asJsonObject }.first { it["uuid"].asString == uuid }
        return ligne.getAsJsonArray("photos").map { it.asJsonObject["nom"].asString to it.asJsonObject["sha256"].asString }
    }

    @Test
    fun `une placette collecte garde de deux a quatre vues, nommees _1 a _4, et signale une seule vue`() {
        val c = ouvrir("P001")
        assertEquals(2, c.regimePhotos("P001").min)
        assertEquals(4, c.regimePhotos("P001").max)
        saisir(c, "12")
        c.ajouterPhoto(jpeg(1), emptyList())
        assertTrue(c.avertissementsArbre().any { it.startsWith("Photo manquante") }, "1 vue sur 2 à 4")
        for (k in 2..4) assertTrue(c.ajouterPhoto(jpeg(k), emptyList()))
        assertFalse(c.ajouterPhoto(jpeg(9), emptyList()), "une cinquième vue est refusée")
        assertTrue(c.avertissementsArbre().none { it.startsWith("Photo manquante") })
        c.enregistrerArbre()
        val arbre = c.etat.tournee!!.placette("P001")!!.arbres.single()
        assertEquals((1..4).map { "photos/${arbre.uuid}_$it.jpg" }, arbre.photos)
        assertEquals(4, citeeEnBase(c, arbre.uuid).size)
    }

    @Test
    fun `un JPEG qui arrive avec un EXIF est garde sans EXIF`() {
        val c = ouvrir()
        saisir(c, "12")
        val sale = jpeg(5, avecExif = true)
        assertTrue(JpegSansExif.contientExif(sale))
        c.ajouterPhoto(sale, emptyList())
        c.enregistrerArbre()
        val uuid = c.etat.tournee!!.placette("P003")!!.arbres.single().uuid
        assertFalse(JpegSansExif.contientExif(File(racinePhotos, "${uuid}_1.jpg").readBytes()))
    }

    @Test
    fun `une placette ordinaire n'accepte qu'une photo, et ce qui n'est pas un JPEG est refuse`() {
        val c = ouvrir()
        assertEquals(1, c.regimePhotos("P003").max)
        saisir(c, "12")
        assertFalse(c.ajouterPhoto(byteArrayOf(1, 2, 3, 4, 5, 6), emptyList()))
        assertTrue(c.etat.erreur!!.contains("JPEG"))
        assertTrue(c.ajouterPhoto(jpeg(1), emptyList()))
        assertFalse(c.ajouterPhoto(jpeg(2), emptyList()))
        assertEquals(1, c.etat.arbre.photos.size)
    }

    @Test
    fun `sans photo l'arbre s'enregistre quand meme et la photo manquante est signalee`() {
        val c = ouvrir()
        saisir(c, "12")
        assertTrue(c.avertissementsArbre().any { it.startsWith("Photo manquante") })
        c.enregistrerArbre()
        val arbre = c.etat.tournee!!.placette("P003")!!.arbres.single()
        assertEquals(emptyList(), arbre.photos)
        assertFalse(racinePhotos.exists() && racinePhotos.listFiles().orEmpty().any { it.name.endsWith(".jpg") })
        saisir(c, "13")
        c.ajouterPhoto(jpeg(1), emptyList())
        assertTrue(c.avertissementsArbre().none { it.startsWith("Photo manquante") })
    }

    @Test
    fun `retirer une photo la supprime`() {
        val c = ouvrir()
        saisir(c, "12")
        c.ajouterPhoto(jpeg(1), emptyList())
        val fichier = c.fichierEnAttente(c.etat.arbre.photos.single())!!
        assertTrue(fichier.exists())
        c.retirerPhoto(0)
        assertFalse(fichier.exists())
        assertTrue(c.etat.arbre.photos.isEmpty())
    }

    @Test
    fun `un arbre refuse par l'echange ne laisse aucune photo nommee, et la photo en attente reste`() {
        refus = true
        val c = ouvrir()
        saisir(c, "12")
        c.ajouterPhoto(jpeg(1), emptyList())
        c.enregistrerArbre()
        assertTrue(c.etat.erreur!!.startsWith("Refusé"))
        assertEquals(emptyList(), racinePhotos.listFiles().orEmpty().filter { it.name.endsWith(".jpg") })
        assertEquals(1, c.etat.arbre.photos.size, "l'opérateur peut corriger et enregistrer de nouveau")
        refus = false
        c.enregistrerArbre()
        assertEquals(1, c.etat.tournee!!.placette("P003")!!.arbres.single().photos.size)
    }

    @Test
    fun `le zip rendu contient la photo citee par la note et rien d'autre, sans refus de photo`() {
        val c = ouvrir()
        saisir(c, "12")
        c.ajouterPhoto(jpeg(4), listOf(SignalementImage.CONTRE_JOUR))
        c.enregistrerArbre()
        val uuid = c.etat.tournee!!.placette("P003")!!.arbres.single().uuid
        c.cloturerPlacette("P003")
        c.rendre()
        val r = c.etat.rendu!!
        assertTrue(r.motifs.none { it.contains("photo", ignoreCase = true) }, "motifs : ${r.motifs}")
        assertFalse(r.refuse, "verdict : ${r.verdict} ${r.motifs}")
        val zip = File(tmp, "documents/${c.etat.tournee!!.code}.zip")
        ZipFile(zip).use { z ->
            val photos = z.entries().asSequence().map { it.name }.filter { it.startsWith("photos/") }.toList()
            assertEquals(listOf("photos/${uuid}_1.jpg"), photos)
        }
    }
}
