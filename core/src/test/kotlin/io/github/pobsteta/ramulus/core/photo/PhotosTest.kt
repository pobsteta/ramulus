package io.github.pobsteta.ramulus.core.photo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.echange.EchangeDeperis
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO

class PhotosTest {
    // --- Nommage, empreinte, colonne ------------------------------------------------------------------------------

    @Test
    fun `une photo se nomme photos slash uuid underscore n point jpg, n partant de 1`() {
        val uuid = "01a70000-0000-7000-8000-000000000001"
        assertEquals("photos/${uuid}_1.jpg", NomsPhotos.nom(uuid, 1))
        assertEquals("${uuid}_4.jpg", NomsPhotos.fichier(uuid, 4))
        assertThrows(IllegalArgumentException::class.java) { NomsPhotos.nom(uuid, 0) }
    }

    @Test
    fun `le SHA-256 est celui du standard, en 64 hexadecimaux minuscules`() {
        // Vecteurs du FIPS 180-4.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Empreinte.sha256("abc".toByteArray()))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Empreinte.sha256(ByteArray(0)))
        assertEquals(Empreinte.sha256("abc".toByteArray()), Empreinte.sha256("abc".byteInputStream()))
    }

    @Test
    fun `la colonne photos porte nom et empreinte, et se vide en tableau vide`() {
        assertEquals("[]", NomsPhotos.colonne(emptyList()).toString())
        val c = NomsPhotos.colonne(listOf(PhotoGardee("photos/u_1.jpg", "ab")))
        assertEquals("""[{"nom":"photos/u_1.jpg","sha256":"ab"}]""", c.toString())
    }

    @Test
    fun `le regime est une photo sur une placette ordinaire, deux a quatre sur une collecte`() {
        assertEquals(1, RegimePhotos.de(false).min)
        assertEquals(1, RegimePhotos.de(false).max)
        assertEquals(2, RegimePhotos.de(true).min)
        assertEquals(4, RegimePhotos.de(true).max)
    }

    // --- Contrôles d'image, sur des images fabriquées ------------------------------------------------------------

    private fun image(l: Int, h: Int, f: (Int, Int) -> Int) = ImageGris(l, h, IntArray(l * h) { f(it % l, it / l) })

    @Test
    fun `une image unie est floue, un damier fin net`() {
        val unie = image(64, 64) { _, _ -> 128 }
        val damier = image(64, 64) { x, y -> if ((x + y) % 2 == 0) 0 else 255 }
        assertEquals(0.0, ControlesImage.mesurer(unie).varianceLaplacien, 1e-9)
        assertTrue(ControlesImage.mesurer(damier).varianceLaplacien > ControlesImage.SEUIL_FLOU)
        assertEquals(listOf(SignalementImage.FLOU), ControlesImage.controler(unie))
        assertFalse(SignalementImage.FLOU in ControlesImage.controler(damier))
    }

    @Test
    fun `un flou de boite fait tomber la variance du laplacien`() {
        val net = image(64, 64) { x, y -> if ((x / 2 + y / 2) % 2 == 0) 40 else 220 }
        val flou = image(64, 64) { x, y ->
            var s = 0
            var n = 0
            for (dy in -3..3) for (dx in -3..3) {
                val xx = (x + dx).coerceIn(0, 63)
                val yy = (y + dy).coerceIn(0, 63)
                s += net[xx, yy]; n++
            }
            s / n
        }
        assertTrue(ControlesImage.mesurer(flou).varianceLaplacien < ControlesImage.mesurer(net).varianceLaplacien / 10)
    }

    @Test
    fun `moitie ciel brule moitie houppier sombre est signale contre-jour, un gris moyen non`() {
        val contreJour = image(100, 100) { _, y -> if (y < 50) 250 else 20 }
        val m = ControlesImage.mesurer(contreJour)
        assertEquals(0.5, m.partClaire, 1e-9)
        assertEquals(0.5, m.partSombre, 1e-9)
        assertTrue(SignalementImage.CONTRE_JOUR in ControlesImage.signalements(m))

        val moyen = image(100, 100) { x, y -> if ((x + y) % 2 == 0) 100 else 160 }
        assertFalse(SignalementImage.CONTRE_JOUR in ControlesImage.controler(moyen))
        // Du ciel sans masse sombre (ou l'inverse) n'est pas un contre-jour.
        assertFalse(SignalementImage.CONTRE_JOUR in ControlesImage.controler(image(100, 100) { _, y -> if (y < 50) 250 else 128 }))
        assertFalse(SignalementImage.CONTRE_JOUR in ControlesImage.controler(image(100, 100) { _, y -> if (y < 50) 128 else 20 }))
    }

    @Test
    fun `la luminance vient de l ARGB, sans tenir compte de l alpha`() {
        val g = ImageGris.depuisArgb(3, 1, intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0x80FF0000.toInt()))
        assertEquals(0, g[0, 0])
        assertEquals(255, g[1, 0])
        assertEquals(76, g[2, 0]) // rouge pur : 299 * 255 / 1000
    }

    @Test
    fun `on reduit sans jamais agrandir, en gardant les proportions`() {
        assertEquals(2048 to 1536, ControlesImage.dimensionsReduites(4000, 3000, 2048))
        assertEquals(1000 to 500, ControlesImage.dimensionsReduites(1000, 500, 2048))
        assertEquals(500 to 2048, ControlesImage.dimensionsReduites(1000, 4096, 2048))
    }

    // --- JPEG sans EXIF -------------------------------------------------------------------------------------------

    private fun jpegAvecExif(): ByteArray {
        val propre = jpegPropre()
        val exif = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + ByteArray(20) { 7 }
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0, (exif.size + 2).toByte()) + exif
        // Le segment s'insère juste après le SOI.
        return propre.copyOfRange(0, 2) + segment + propre.copyOfRange(2, propre.size)
    }

    private fun jpegPropre(): ByteArray {
        val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 32) for (y in 0 until 32) img.setRGB(x, y, if ((x + y) % 2 == 0) 0xFF0000 else 0x00FF00)
        val sortie = ByteArrayOutputStream()
        assertTrue(ImageIO.write(img, "jpg", sortie))
        return sortie.toByteArray()
    }

    @Test
    fun `un JPEG ecrit par ImageIO n a pas d EXIF, un JPEG avec EXIF en est lave`() {
        val propre = jpegPropre()
        assertTrue(JpegSansExif.estUnJpeg(propre))
        assertFalse(JpegSansExif.contientExif(propre))

        val sale = jpegAvecExif()
        assertTrue(JpegSansExif.contientExif(sale))
        val lave = JpegSansExif.retirer(sale)
        assertFalse(JpegSansExif.contientExif(lave))
        assertFalse(JpegSansExif.contientMetadonnees(lave))
        assertArrayEquals(propre, lave, "le reste du fichier est recopié tel quel")
        // Le fichier lavé reste un JPEG lisible.
        assertEquals(32, ImageIO.read(lave.inputStream()).width)
        // Un fichier propre n'est pas touché.
        assertTrue(propre === JpegSansExif.retirer(propre))
    }

    @Test
    fun `ce qui n est pas un JPEG est refuse`() {
        assertThrows(IllegalArgumentException::class.java) { JpegSansExif.retirer(byteArrayOf(1, 2, 3, 4, 5)) }
    }

    // --- Un rendu avec photos passe le contrôle (aucun R10a, R10b, R10c) -----------------------------------------

    @Test
    fun `un rendu dont les photos sont celles que citent les notes ne leve ni R10a, ni R10b, ni R10c`(@TempDir tmp: File) {
        val communs = File("../fixtures/echange")
        val depart = File(communs, "A1-tournee-depart/deperis.gpkg").canonicalFile
        val attendu = JsonParser.parseString(File(communs, "A3-rendu/EXEMPLE-2027-01.instantane.json").readText()).asJsonObject
        fun lignes(table: String) = attendu.getAsJsonObject("tables").getAsJsonObject(table).getAsJsonArray("lignes").map { it.asJsonObject }

        val travail = File(tmp, "travail.gpkg").also { depart.copyTo(it) }
        val racine = File(tmp, "appareil")
        File(racine, "photos").mkdirs()

        val centre = lignes("centres").first { it["plot_id"].asString == "P001" }.deepCopy()
        centre.remove("fid")
        EchangeDeperis.enregistrerCentre(travail, centre.toString())

        var nbPhotos = 0
        for (n in lignes("notations").filter { it["plot_id"].asString == "P001" }) {
            val uuid = n["uuid"].asString
            // Deux vues pour la première note, une pour les autres : le nom et l'empreinte sont ceux de nos fonctions.
            val gardees = (1..(if (nbPhotos == 0) 2 else 1)).map { k ->
                val octets = jpegPropre() + ByteArray(k) { it.toByte() } // contenu distinct par vue
                val fichier = File(racine, NomsPhotos.nom(uuid, k))
                fichier.writeBytes(octets)
                nbPhotos++
                PhotoGardee(NomsPhotos.nom(uuid, k), Empreinte.sha256(fichier.inputStream()))
            }
            val note: JsonObject = n.deepCopy().also { it.remove("fid"); it.remove("classe") }
            note.add("photos", NomsPhotos.colonne(gardees))
            EchangeDeperis.enregistrerNotation(travail, note.toString())
        }
        EchangeDeperis.cloturerPlacette(travail, "P001")

        val sortie = File(tmp, "rendu.zip")
        val verdict = JsonParser.parseString(EchangeDeperis.rendre(travail, depart, racine, "operateur-A", "test", sortie)).asJsonObject
        val motifs = verdict.getAsJsonArray("motifs").map { it.asString }
        assertTrue(motifs.none { it.startsWith("photo") || it == "empreinte_photo_differente" }, "motifs : $motifs")
        assertTrue(verdict["verdict"].asString != "refuse", "verdict : $verdict")
        assertTrue(sortie.exists())
        ZipFile(sortie).use { z ->
            val dedans = z.entries().asSequence().map { it.name }.filter { it.startsWith("photos/") }.toSet()
            assertEquals(nbPhotos, dedans.size)
        }
    }

    @Test
    fun `colonne reste un tableau JSON lisible par le format`() {
        val a: JsonArray = NomsPhotos.colonne(listOf(PhotoGardee("photos/u_1.jpg", "00")))
        assertEquals("photos/u_1.jpg", a[0].asJsonObject["nom"].asString)
    }
}
