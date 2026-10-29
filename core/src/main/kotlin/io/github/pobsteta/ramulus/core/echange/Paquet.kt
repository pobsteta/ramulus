package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Une photo d'un paquet : son nom (`photos/<uuid>_<n>.jpg`), son empreinte SHA-256, et de quoi la relire. */
internal class PhotoPaquet(val nom: String, val sha256: String, val ouvrir: () -> InputStream)

/**
 * Un paquet lu : l'instantané de son GeoPackage et, pour un `.zip` ou une racine de photos, ses photos.
 * Le GeoPackage est gardé dans un fichier (copie temporaire si le paquet est un `.zip`), à effacer avec [close].
 */
internal class PaquetDeperis(
    val inst: JsonObject,
    val photos: Map<String, PhotoPaquet>?,
    val gpkg: File,
    private val temporaire: Boolean,
) : AutoCloseable {
    override fun close() {
        if (temporaire) gpkg.delete()
    }
}

internal object Paquets {
    const val NOM_GPKG = "deperis.gpkg"
    const val DOSSIER_PHOTOS = "photos/"

    fun sha256(flux: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val tampon = ByteArray(64 * 1024)
        flux.use { while (true) { val n = it.read(tampon); if (n < 0) break; md.update(tampon, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Lit un `deperis.gpkg` seul ou un `.zip` rendu (`deperis.gpkg` et `photos/`). Lève [PasUnGeopackage] sinon. */
    fun lire(fichier: File): PaquetDeperis =
        if (fichier.name.endsWith(".zip", ignoreCase = true)) lireZip(fichier) else lireGpkg(fichier)

    private fun lireGpkg(fichier: File): PaquetDeperis {
        val inst = instantaneDe(fichier, null)
        return PaquetDeperis(inst, null, fichier, temporaire = false)
    }

    private fun instantaneDe(gpkg: File, photos: List<Pair<String, String>>?): JsonObject {
        try {
            Gpkg.ouvrir(gpkg, lectureSeule = true).use { return Instantane.lire(it, photos) }
        } catch (e: PasUnGeopackage) {
            throw e
        } catch (e: java.sql.SQLException) {
            throw PasUnGeopackage("fichier SQLite illisible : ${e.message}", e)
        }
    }

    private fun lireZip(zip: File): PaquetDeperis {
        val temp = File.createTempFile("deperis", ".gpkg")
        try {
            val photos = LinkedHashMap<String, PhotoPaquet>()
            try {
                ZipFile(zip).use { z ->
                    val entree = z.getEntry(NOM_GPKG) ?: throw PasUnGeopackage("$NOM_GPKG absent de ${zip.name}")
                    z.getInputStream(entree).use { src -> temp.outputStream().use { src.copyTo(it) } }
                    for (e in z.entries().asSequence().filter { it.name.startsWith(DOSSIER_PHOTOS) && !it.isDirectory }.sortedBy { it.name }) {
                        val nom = e.name
                        val ouvrir = { ZipFile(zip).let { zf -> FluxZip(zf, zf.getInputStream(zf.getEntry(nom))) } }
                        photos[nom] = PhotoPaquet(nom, sha256(z.getInputStream(e)), ouvrir)
                    }
                }
            } catch (e: java.util.zip.ZipException) {
                throw PasUnGeopackage("${zip.name} n'est pas un .zip lisible : ${e.message}", e)
            }
            val inst = instantaneDe(temp, photos.values.map { it.nom to it.sha256 })
            return PaquetDeperis(inst, photos, temp, temporaire = true)
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
    }

    /** Les photos d'un dossier `racine/photos/` de l'appareil : nom `photos/<fichier>`, empreinte du fichier. */
    fun photosDeLAppareil(racine: File): Map<String, PhotoPaquet> {
        val dossier = File(racine, "photos")
        val fichiers = (dossier.listFiles() ?: emptyArray()).filter { it.isFile }.sortedBy { it.name }
        return fichiers.associate { f ->
            val nom = DOSSIER_PHOTOS + f.name
            nom to PhotoPaquet(nom, sha256(f.inputStream()), { f.inputStream() })
        }
    }

    /**
     * Écrit le `.zip` rendu : `deperis.gpkg` d'abord, puis les photos par nom. Les entrées portent une date fixe et le
     * fichier est écrit en entier avant d'être mis à sa place : un paquet refusé ou interrompu n'écrase rien.
     */
    fun ecrireZip(gpkg: File, photos: Collection<PhotoPaquet>, sortie: File) {
        sortie.absoluteFile.parentFile?.mkdirs()
        val temp = File(sortie.absoluteFile.parentFile, sortie.name + ".tmp")
        try {
            ZipOutputStream(temp.outputStream().buffered()).use { z ->
                fun entree(nom: String): ZipEntry = ZipEntry(nom).also { it.time = DATE_FIXE }
                z.putNextEntry(entree(NOM_GPKG))
                gpkg.inputStream().use { it.copyTo(z) }
                z.closeEntry()
                for (p in photos.sortedBy { it.nom }) {
                    z.putNextEntry(entree(p.nom))
                    p.ouvrir().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
            java.nio.file.Files.move(
                temp.toPath(), sortie.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temp.delete()
        }
    }

    /** 1er janvier 2027, minuit, dans le fuseau local : le zip ne dépend pas de l'heure à laquelle on l'écrit. */
    private val DATE_FIXE: Long = Calendar.getInstance().also { it.clear(); it.set(2027, Calendar.JANUARY, 1, 0, 0, 0) }.timeInMillis
}

/** Un flux d'entrée de zip qui ferme aussi le `ZipFile` qui l'a ouvert. */
private class FluxZip(private val zip: ZipFile, private val flux: InputStream) : InputStream() {
    override fun read(): Int = flux.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = flux.read(b, off, len)
    override fun close() {
        flux.close()
        zip.close()
    }
}
